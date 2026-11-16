package app.stocklist.app.feature.catalogue

import app.stocklist.app.core.i18n.bi
import app.stocklist.app.core.i18n.Bilingual

import app.stocklist.app.core.i18n.trMessage

import app.stocklist.app.core.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import app.stocklist.app.core.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.EmptyState
import app.stocklist.app.core.designsystem.LabelValue
import app.stocklist.app.core.designsystem.LoadingState
import app.stocklist.app.core.designsystem.SectionCard
import app.stocklist.app.core.session.SessionState
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.data.local.BatchEntity
import app.stocklist.app.data.repository.BatchRepository
import app.stocklist.app.data.repository.PeopleRepository
import app.stocklist.app.domain.catalogue.ExpiryCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** "You", the person's name, or a neutral label for someone no longer listed. */
fun personName(names: Map<String, String>, id: String?, me: String?): String? = when {
    id == null -> null
    id == me -> tr("You")
    else -> names[id] ?: tr("A former user")
}

@Composable
fun expiryColor(expiry: String?, today: LocalDate): Color {
    val d = ExpiryCalculator.daysLeft(expiry?.let(LocalDate::parse), today) ?: return MaterialTheme.colorScheme.onSurfaceVariant
    return when {
        d < 0 -> MaterialTheme.colorScheme.error
        d <= 7 -> Color(0xFFB26A00)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun BatchRow(b: BatchEntity, today: LocalDate, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(b.itemName.bi()) },
        supportingContent = {
            Column {
                Text(listOfNotNull(b.batchNo?.let { tr("Batch %1\$s", it) }, "${b.quantity} ${b.unitCode}", b.supplierName,
                    tr("received %1\$s", b.receivedDate.displayDate())).joinToString(" · "))
                if (b.cancelled) Text(tr("Purchase cancelled"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (b.pending) Text(tr("Waiting to sync"), style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(b.expiryDate.displayDate(), fontWeight = FontWeight.SemiBold)
                Text(ExpiryCalculator.label(b.expiryDate?.let(LocalDate::parse), today), color = expiryColor(b.expiryDate, today),
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

// ------------------------------------------------------------------------------------------- expiry lists

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpiryViewModel @Inject constructor(private val repo: BatchRepository, handle: SavedStateHandle) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    val filter = MutableStateFlow(handle.get<String>("filter")?.let { runCatching { ExpiryCalculator.Filter.valueOf(it) }.getOrNull() }
        ?: ExpiryCalculator.Filter.WITHIN_30)
    val query = MutableStateFlow("")
    val batches = combine(filter, query) { f, q -> f to q }
        .flatMapLatest { (f, q) -> repo.observe(f, today, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val counts = repo.observeCounts(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryScreen(onBack: () -> Unit, onOpenBatch: (String) -> Unit, onBatches: () -> Unit, onPdf: ((app.stocklist.app.feature.pdf.PdfJob) -> Unit)? = null,
                 vm: ExpiryViewModel = hiltViewModel()) {
    val list by vm.batches.collectAsStateWithLifecycle()
    val f by vm.filter.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("Expiry")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Bilingual.oneLine(tr("Back"))) } },
            actions = {
                if (onPdf != null) IconButton(onClick = { onPdf(app.stocklist.app.feature.pdf.PdfJob.Expiry(f)) }) { Icon(Icons.Filled.PictureAsPdf, Bilingual.oneLine(tr("Expiry report PDF"))) }
                TextButton(onClick = onBatches) { Text(tr("All batches")) }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpiryCalculator.Filter.entries.forEach { option ->
                    FilterChip(selected = f == option, onClick = { vm.filter.value = option },
                        label = { Text("${trMessage(option.label)} (${counts[option] ?: 0})", maxLines = 1) })
                }
            }
            OutlinedTextField(q, { vm.query.value = it }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(tr("Search item, batch, supplier or invoice"), maxLines = 1) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Text(tr("Stock that is still there; cancelled purchases are not included. Today: %1\$s", vm.today.display()),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            DailyReminderButton()
            val l = list
            when {
                l == null -> LoadingState()
                l.isEmpty() -> EmptyState(when (f) {
                    ExpiryCalculator.Filter.EXPIRED -> tr("Nothing has expired.")
                    ExpiryCalculator.Filter.ALL -> tr("No received goods have an expiry date yet.")
                    ExpiryCalculator.Filter.WITHIN_7 -> tr("Nothing expires within 7 days.")
                    ExpiryCalculator.Filter.WITHIN_30 -> tr("Nothing expires within 30 days.")
                    ExpiryCalculator.Filter.WITHIN_90 -> tr("Nothing expires within 90 days.")
                })
                else -> LazyColumn { items(l, key = { it.id }) { b -> BatchRow(b, vm.today) { onOpenBatch(b.id) } } }
            }
        }
    }
}

/** Dashboard card: shown only when something has expired or expires within 7 days. */
@Composable
fun ExpiryAlertCard(counts: Map<ExpiryCalculator.Filter, Int>, onOpen: (ExpiryCalculator.Filter) -> Unit) {
    val expired = counts[ExpiryCalculator.Filter.EXPIRED] ?: 0
    val soon = counts[ExpiryCalculator.Filter.WITHIN_7] ?: 0
    if (expired == 0 && soon == 0) return
    Card(Modifier.fillMaxWidth().clickable { onOpen(if (expired > 0) ExpiryCalculator.Filter.EXPIRED else ExpiryCalculator.Filter.WITHIN_7) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tr("Expiry alert"), style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(
                    expired.takeIf { it > 0 }?.let { tr("%1\$s batch(es) expired", it) },
                    soon.takeIf { it > 0 }?.let { tr("%1\$s expire within 7 days", it) },
                ).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- batch tracking

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BatchesViewModel @Inject constructor(private val repo: BatchRepository, handle: SavedStateHandle) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    val itemId: String = handle.get<String>("itemId").orEmpty()
    val query = MutableStateFlow("")
    val from = MutableStateFlow<LocalDate?>(null)
    val to = MutableStateFlow<LocalDate?>(null)
    val includeCancelled = MutableStateFlow(false)
    val batches = combine(query, from, to, includeCancelled) { q, f, t, c -> listOf(q, f, t, c) }
        .flatMapLatest { (q, f, t, c) -> repo.observe(null, today, q as String, itemId, f as LocalDate?, t as LocalDate?, c as Boolean) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchesScreen(onBack: () -> Unit, onOpenBatch: (String) -> Unit, vm: BatchesViewModel = hiltViewModel()) {
    val list by vm.batches.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val from by vm.from.collectAsStateWithLifecycle()
    val to by vm.to.collectAsStateWithLifecycle()
    val cancelled by vm.includeCancelled.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (vm.itemId.isEmpty()) tr("Batches") else tr("Batches of this item")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Bilingual.oneLine(tr("Back"))) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(q, { vm.query.value = it }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(tr("Search item, batch, supplier or invoice"), maxLines = 1) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp)) {
                DateField(tr("Received from"), from, { vm.from.value = it }, Modifier.weight(1f), clearable = true)
                Spacer(Modifier.width(8.dp))
                DateField(tr("Received to"), to, { vm.to.value = it }, Modifier.weight(1f), clearable = true)
            }
            Row(Modifier.padding(horizontal = 16.dp)) {
                FilterChip(selected = cancelled, onClick = { vm.includeCancelled.value = !cancelled },
                    label = { Text(tr("Include cancelled purchases"), maxLines = 1) })
            }
            val l = list
            when {
                l == null -> LoadingState()
                l.isEmpty() -> EmptyState(tr("No batches match."))
                else -> LazyColumn { items(l, key = { it.id }) { b -> BatchRow(b, vm.today) { onOpenBatch(b.id) } } }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- one batch

@HiltViewModel
class BatchDetailViewModel @Inject constructor(
    repo: BatchRepository, people: PeopleRepository, private val session: SessionStore, handle: SavedStateHandle,
) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    val batch = repo.observeOne(checkNotNull(handle["id"])).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val names = people.names().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val me: String? get() = (session.state.value as? SessionState.SignedIn)?.user?.userId
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDetailScreen(access: Access, onBack: () -> Unit, onOpenPurchase: (String) -> Unit, vm: BatchDetailViewModel = hiltViewModel()) {
    val b by vm.batch.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("Batch")) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Bilingual.oneLine(tr("Back"))) } })
    }) { padding ->
        val x = b
        if (x == null) { Column(Modifier.padding(padding)) { EmptyState(tr("This batch is no longer available.")) }; return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ExpiryCalculator.label(x.expiryDate?.let(LocalDate::parse), vm.today), color = expiryColor(x.expiryDate, vm.today),
                style = MaterialTheme.typography.titleMedium)
            SectionCard(x.itemName.bi()) {
                LabelValue(tr("Batch number"), x.batchNo)
                LabelValue(tr("Expiry date"), x.expiryDate.displayDate())
                LabelValue(tr("Quantity left"), "${x.quantity} ${x.unitCode}")
                if (x.receivedQuantity != x.quantity) LabelValue(tr("Originally received"), tr("%1\$s %2\$s (corrected since)", x.receivedQuantity, x.unitCode))
                LabelValue(tr("Received on"), x.receivedDate.displayDate())
                LabelValue(tr("Received by"), personName(names, x.receivedBy, vm.me))
                LabelValue(tr("Supplier"), x.supplierName)
                LabelValue(tr("Invoice"), x.invoiceNo)
                LabelValue(tr("Purchase date"), x.purchaseDate.displayDate())
                if (x.cancelled) LabelValue(tr("Note"), tr("The purchase was cancelled"))
                if (x.pending) LabelValue(tr("Note"), tr("Recorded on this phone, waiting to sync"))
            }
            if (access.canViewPurchases) {
                OutlinedButton(onClick = { onOpenPurchase(x.purchaseId) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Open the original purchase")) }
            } else {
                Text(tr("You do not have permission to open purchases."), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Lets the user turn on the daily expiry notification (Android 13+ asks for permission first). */
@Composable
private fun DailyReminderButton() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var allowed by remember { mutableStateOf(app.stocklist.app.data.sync.ExpiryAlertWorker.canNotify(context)) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { allowed = it }
    if (allowed) {
        Text(tr("Daily expiry reminder is on."), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp))
    } else {
        TextButton(onClick = {
            if (android.os.Build.VERSION.SDK_INT >= 33) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
        }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(tr("Remind me daily about expiring stock")) }
    }
}
