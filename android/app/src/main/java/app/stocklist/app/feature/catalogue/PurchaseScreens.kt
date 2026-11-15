package app.stocklist.app.feature.catalogue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.EmptyState
import app.stocklist.app.core.designsystem.ErrorText
import app.stocklist.app.core.designsystem.FormField
import app.stocklist.app.core.designsystem.LabelValue
import app.stocklist.app.core.designsystem.LoadingState
import app.stocklist.app.core.designsystem.SectionCard
import app.stocklist.app.core.session.SessionState
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.data.local.DeliveryLineEntity
import app.stocklist.app.data.local.PurchaseEntity
import app.stocklist.app.data.repository.PurchaseRepository
import app.stocklist.app.data.repository.PurchaseView
import app.stocklist.app.data.repository.SupplierRepository
import app.stocklist.app.domain.catalogue.Decimals
import app.stocklist.app.domain.catalogue.PurchaseCalculator
import app.stocklist.app.domain.permission.Permission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

fun purchaseStatusLabel(s: String) = when (s) {
    "DRAFT" -> "Draft"
    "PENDING" -> "Not received yet"
    "PARTIAL" -> "Partially received"
    "RECEIVED" -> "Received"
    "CANCELLED" -> "Cancelled"
    else -> s
}

fun percentText(p: String?) = p?.let { "$it%" } ?: "N/A"

// ------------------------------------------------------------------------------------------- list of purchases

data class PurchaseFilter(val q: String = "", val status: String = "", val supplierId: String = "", val from: LocalDate? = null, val to: LocalDate? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PurchasesViewModel @Inject constructor(repo: PurchaseRepository, suppliers: SupplierRepository, handle: SavedStateHandle) : ViewModel() {
    val filter = MutableStateFlow(PurchaseFilter(supplierId = handle.get<String>("supplierId").orEmpty()))
    val purchases = filter.flatMapLatest { f ->
        repo.observe(f.q, f.status, f.supplierId, f.from?.toString().orEmpty(), f.to?.toString().orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val suppliers = suppliers.observe("", true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(access: Access, onBack: () -> Unit, onNew: () -> Unit, onOpen: (String) -> Unit,
                    onPdf: ((app.stocklist.app.feature.pdf.PdfJob) -> Unit)? = null,
                    vm: PurchasesViewModel = hiltViewModel()) {
    val list by vm.purchases.collectAsStateWithLifecycle()
    val f by vm.filter.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    var showDates by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Purchases") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                // Report of the dates chosen below (one supplier's report when a supplier is picked).
                if (onPdf != null) IconButton(onClick = {
                    onPdf(if (f.supplierId.isNotEmpty()) app.stocklist.app.feature.pdf.PdfJob.Supplier(f.supplierId, f.from, f.to) else app.stocklist.app.feature.pdf.PdfJob.DateRange(f.from, f.to))
                }) { Icon(Icons.Filled.PictureAsPdf, "Purchase report PDF") }
                if (access.has(Permission.PURCHASE_RECORD)) {
                    TextButton(onClick = onNew) {
                        Icon(Icons.Filled.Add, null)
                        Text("New purchase", style = MaterialTheme.typography.titleMedium)
                    }
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(f.q, { vm.filter.value = f.copy(q = it) }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Search invoice, supplier or item") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "All", "DRAFT" to "Drafts", "PENDING" to "Not received", "PARTIAL" to "Partial",
                    "RECEIVED" to "Received", "CANCELLED" to "Cancelled").forEach { (v, l) ->
                    FilterChip(selected = f.status == v, onClick = { vm.filter.value = f.copy(status = v) }, label = { Text(l) })
                }
                FilterChip(selected = f.from != null || f.to != null || showDates, onClick = { showDates = !showDates }, label = { Text("Dates") })
            }
            DropdownField("Supplier", f.supplierId, listOf(Choice("", "All suppliers")) + suppliers.map { Choice(it.id, it.name) },
                { vm.filter.value = f.copy(supplierId = it.orEmpty()) }, Modifier.padding(horizontal = 16.dp))
            if (showDates) {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    DateField("From", f.from, { vm.filter.value = f.copy(from = it) }, Modifier.weight(1f), clearable = true)
                    Spacer(Modifier.width(8.dp))
                    DateField("To", f.to, { vm.filter.value = f.copy(to = it) }, Modifier.weight(1f), clearable = true)
                }
            }
            val l = list
            when {
                l == null -> LoadingState()
                l.isEmpty() -> EmptyState(if (f == PurchaseFilter()) "No purchases yet." else "No purchase matches these filters.")
                else -> LazyColumn {
                    items(l, key = { it.id }) { p -> PurchaseRow(p) { onOpen(p.id) } }
                }
            }
        }
    }
}

@Composable
private fun PurchaseRow(p: PurchaseEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(listOfNotNull(p.supplierName, p.invoiceNo?.let { "#$it" }).joinToString(" · ").ifEmpty { "Purchase" }) },
        supportingContent = {
            Column {
                Text("${p.purchaseDate.displayDate()} · ${p.itemCount} items · ${purchaseStatusLabel(p.status)}" +
                    if (p.overReceived) " · over-received" else "")
                SyncBadge(p.syncState, p.syncError)
            }
        },
        trailingContent = { Text(Decimals.rupees(p.totalCost)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

// ------------------------------------------------------------------------------------------- one purchase

@HiltViewModel
class PurchaseDetailViewModel @Inject constructor(
    private val repo: PurchaseRepository,
    private val session: SessionStore,
    private val history: app.stocklist.app.data.repository.HistoryRepository,
    batches: app.stocklist.app.data.repository.BatchRepository,
    people: app.stocklist.app.data.repository.PeopleRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    private val id: String = checkNotNull(handle["id"])
    val purchase = repo.observeOne(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val batches = batches.observeForPurchase(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val names = people.names().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val today: LocalDate = LocalDate.now()
    var revisions by mutableStateOf<List<app.stocklist.app.data.remote.RevisionDto>?>(null)
    var revisionsError by mutableStateOf<String?>(null)

    /** Earlier versions are kept on the server only, so this needs a connection. */
    fun loadRevisions() = viewModelScope.launch {
        revisionsError = null
        when (val r = history.revisions(id)) {
            is app.stocklist.app.data.remote.ApiResult.Ok -> revisions = r.value
            is app.stocklist.app.data.remote.ApiResult.Failure -> revisionsError = r.error.message
        }
    }
    var error by mutableStateOf<String?>(null)
    val me: String? get() = (session.state.value as? SessionState.SignedIn)?.user?.userId

    fun cancel(reason: String, onDone: () -> Unit) = viewModelScope.launch {
        try { repo.deleteOrCancel(id, reason); onDone() } catch (e: IllegalArgumentException) { error = e.message }
    }

    fun deleteDraft(onDone: () -> Unit) = viewModelScope.launch {
        try { repo.deleteOrCancel(id, null); onDone() } catch (e: IllegalArgumentException) { error = e.message }
    }

    fun correct(receiptLineId: String, quantity: String, reason: String, onDone: () -> Unit) = viewModelScope.launch {
        try { repo.correct(id, receiptLineId, quantity, reason, me); error = null; onDone() } catch (e: IllegalArgumentException) { error = e.message }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailScreen(access: Access, onBack: () -> Unit, onEdit: (String) -> Unit, onReceive: (String) -> Unit,
                         onPdf: ((app.stocklist.app.feature.pdf.PdfJob) -> Unit)? = null,
                         vm: PurchaseDetailViewModel = hiltViewModel()) {
    val view by vm.purchase.collectAsStateWithLifecycle()
    val batches by vm.batches.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    var showRevisions by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var correcting by remember { mutableStateOf<DeliveryLineEntity?>(null) }
    val v = view
    val p = v?.purchase
    val isDraft = p?.status == "DRAFT"
    val completed = p != null && !isDraft && p.status != "CANCELLED"
    val canEdit = p != null && p.status != "CANCELLED" &&
        (access.has(Permission.PURCHASE_EDIT) || (isDraft && access.has(Permission.PURCHASE_RECORD) && p.createdBy == vm.me))
    Scaffold(topBar = {
        TopAppBar(title = { Text("Purchase") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (p != null && onPdf != null) IconButton(onClick = { onPdf(app.stocklist.app.feature.pdf.PdfJob.Purchase(p.id)) }) { Icon(Icons.Filled.PictureAsPdf, "PDF") }
                if (canEdit) IconButton(onClick = { onEdit(p!!.id) }) { Icon(Icons.Filled.Edit, "Edit") }
                if (isDraft && (access.has(Permission.PURCHASE_DELETE) || p?.createdBy == vm.me)) {
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, "Delete draft") }
                }
                if (completed && access.has(Permission.PURCHASE_DELETE)) {
                    IconButton(onClick = { cancelling = true }) { Icon(Icons.Filled.Block, "Cancel purchase") }
                }
            })
    }) { padding ->
        if (v == null || p == null) { Column(Modifier.padding(padding)) { EmptyState("This purchase is no longer available.") }; return@Scaffold }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SyncBadge(p.syncState, p.syncError)
                    if (p.duplicateInvoice) {
                        Text("Warning: another purchase from this supplier has the same invoice number.",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (p.overReceived) {
                        Text("More was received than billed on at least one item.", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    SectionCard(p.supplierName ?: "Purchase") {
                        LabelValue("Invoice number", p.invoiceNo)
                        LabelValue("Purchase date", p.purchaseDate.displayDate())
                        LabelValue("Last received", p.receivedDate.displayDate())
                        LabelValue("Status", purchaseStatusLabel(p.status))
                        p.cancelReason?.let { LabelValue("Cancelled because", it) }
                        LabelValue("Notes", p.notes)
                        LabelValue("Purchase cost", Decimals.rupees(p.totalCost))
                        if (access.canSeeProfit) {
                            LabelValue("Expected sales", Decimals.rupees(p.totalExpectedSales))
                            LabelValue("Expected gross profit", Decimals.rupees(p.totalExpectedProfit))
                            LabelValue("Margin / markup", "${percentText(p.marginPercent)} / ${percentText(p.markupPercent)}")
                        }
                        LabelValue("Created by", personName(names, p.createdBy, vm.me))
                        if (p.completedBy != null) LabelValue("Completed by", personName(names, p.completedBy, vm.me))
                        LabelValue("Last changed by", personName(names, p.updatedBy, vm.me))
                        if (p.revisionNo > 0) LabelValue("Edited after completion", "${p.revisionNo} time(s); earlier versions are kept")
                    }
                    if (p.revisionNo > 0 && access.canViewPurchases) {
                        TextButton(onClick = { showRevisions = true; vm.loadRevisions() }) { Text("Show earlier versions") }
                    }
                    if (completed && access.has(Permission.PURCHASE_RECORD) && p.status != "RECEIVED") {
                        OutlinedButton(onClick = { onReceive(p.id) }, modifier = Modifier.fillMaxWidth()) { Text("Receive goods") }
                    }
                    ErrorText(vm.error)
                    Text("Items", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(v.lines, key = { it.id }) { l ->
                val pr = v.progress(l)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row {
                            Text(l.itemName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(Decimals.rupees(l.totalCost), fontWeight = FontWeight.SemiBold)
                        }
                        Text("Billed ${l.quantity} ${l.unitCode}" + (l.purchasePrice?.let {
                            if (l.priceBasis == PurchaseCalculator.TOTAL) " · total ${Decimals.rupees(it)} (${Decimals.rupees(l.purchaseUnitPrice)} per unit)"
                            else " × ${Decimals.rupees(it)}"
                        } ?: " · no purchase price") + (l.sellingPrice?.let { " · sell ${Decimals.rupees(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall)
                        if (!isDraft) {
                            Text("Received ${Decimals.format(pr.received)} · remaining ${Decimals.format(pr.remaining)}" +
                                (if (pr.overBy.signum() > 0) " · over by ${Decimals.format(pr.overBy)}" else ""),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (access.canSeeProfit && l.expectedSales != null) {
                            Text("Sales ${Decimals.rupees(l.expectedSales)} · profit ${Decimals.rupees(l.expectedProfit)} · " +
                                "margin ${percentText(l.marginPercent)} · markup ${percentText(l.markupPercent)}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (l.sellingPrice != null && l.purchaseUnitPrice != null &&
                            java.math.BigDecimal(l.sellingPrice) < java.math.BigDecimal(l.purchaseUnitPrice)) {
                            Text("Selling price is below the purchase price", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (v.deliveries.isNotEmpty()) {
                item { Text("Deliveries", style = MaterialTheme.typography.titleMedium) }
                items(v.deliveries, key = { it.id }) { d ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Received ${d.receivedDate.displayDate()}" +
                                (personName(names, d.receivedBy, vm.me)?.let { " by $it" } ?: "") +
                                if (d.pending) " · waiting to sync" else "",
                                style = MaterialTheme.typography.titleSmall)
                            d.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            v.deliveryLines.filter { it.deliveryId == d.id }.forEach { dl ->
                                val line = v.lines.firstOrNull { it.id == dl.purchaseLineId }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text((if (dl.kind == "CORRECTION") "Correction: " else "") +
                                            "${line?.itemName ?: "Item"} ${dl.quantity} ${line?.unitCode.orEmpty()}",
                                            style = MaterialTheme.typography.bodyMedium)
                                        val extra = listOfNotNull(dl.batchNo?.let { "batch $it" }, dl.expiryDate?.let { "expires ${it.displayDate()}" },
                                            dl.reason).joinToString(" · ")
                                        if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (dl.kind == "RECEIPT" && !dl.pending && access.has(Permission.PURCHASE_RECORD) && p.status != "CANCELLED") {
                                        TextButton(onClick = { correcting = dl }) { Text("Correct") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (batches.isNotEmpty() && access.canViewExpiry) {
                item { Text("Batches and expiry", style = MaterialTheme.typography.titleMedium) }
                items(batches, key = { "b" + it.id }) { b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${b.itemName} · ${b.quantity} ${b.unitCode}", style = MaterialTheme.typography.bodyMedium)
                            Text(listOfNotNull(b.batchNo?.let { "batch $it" }, "expiry ${b.expiryDate.displayDate()}").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text(app.stocklist.app.domain.catalogue.ExpiryCalculator.label(b.expiryDate?.let(LocalDate::parse), vm.today),
                            color = expiryColor(b.expiryDate, vm.today), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Spacer(Modifier.padding(24.dp)) }
        }
    }
    if (showRevisions) RevisionsDialog(vm, names, onDismiss = { showRevisions = false })
    if (cancelling) ReasonDialog("Cancel purchase?",
        "The purchase stays in history but no longer counts in totals or list receiving. This cannot be undone.",
        "Cancel purchase", onConfirm = { r -> cancelling = false; vm.cancel(r) {} }, onDismiss = { cancelling = false })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete draft?") },
        text = { Text("The draft is removed for everyone.") },
        confirmButton = { TextButton(onClick = { deleting = false; vm.deleteDraft(onBack) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Keep") } })
    correcting?.let { dl ->
        CorrectionDialog(dl, onConfirm = { q, r -> vm.correct(dl.id, q, r) { correcting = null } },
            onDismiss = { correcting = null }, error = vm.error)
    }
}

@Composable
fun ReasonDialog(title: String, message: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column { Text(message); FormField(reason, { reason = it }, "Reason *", singleLine = false) } },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }, enabled = reason.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } })
}

@Composable
private fun CorrectionDialog(dl: DeliveryLineEntity, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit, error: String?) {
    var qty by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Correct a receipt") },
        text = {
            Column {
                Text("Recorded: ${dl.quantity}. The original entry is kept; the correction is added with its reason. " +
                    "Use a minus sign to reduce, e.g. -10.", style = MaterialTheme.typography.bodySmall)
                FormField(qty, { qty = it }, "Correction (e.g. -10)")
                FormField(reason, { reason = it }, "Reason *")
                ErrorText(error)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(qty, reason) }, enabled = qty.isNotBlank() && reason.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } })
}

// ------------------------------------------------------------------------------------------- receive goods later

@HiltViewModel
class ReceiveGoodsViewModel @Inject constructor(
    private val repo: PurchaseRepository,
    private val session: SessionStore,
    handle: SavedStateHandle,
) : ViewModel() {
    val id: String = checkNotNull(handle["id"])
    val purchase = repo.observeOne(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    var date by mutableStateOf(LocalDate.now())
    var notes by mutableStateOf("")
    val quantities = androidx.compose.runtime.mutableStateMapOf<String, String>()
    val batches = androidx.compose.runtime.mutableStateMapOf<String, String>()
    val expiries = androidx.compose.runtime.mutableStateMapOf<String, LocalDate?>()
    var error by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
    private var prefilled = false

    /** Starts every line at its remaining quantity, so the common "everything arrived" case is one tap. */
    fun prefill(v: PurchaseView) {
        if (prefilled) return
        prefilled = true
        v.lines.forEach { quantities[it.id] = Decimals.format(v.progress(it).remaining) ?: "" }
    }

    fun overReceived(v: PurchaseView): List<String> = v.lines.filter { l ->
        val q = Decimals.tryParse(quantities[l.id], 3, true) ?: return@filter false
        q > v.progress(l).remaining
    }.map { it.itemName }

    fun save(onDone: () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                val v = purchase.value ?: return@launch
                repo.receive(id, date, notes, v.lines.map {
                    app.stocklist.app.data.repository.ReceiveDraft(it.id, quantities[it.id].orEmpty(), batches[it.id].orEmpty(),
                        expiries[it.id]?.toString().orEmpty())
                }, (session.state.value as? SessionState.SignedIn)?.user?.userId)
                onDone()
            } catch (e: IllegalArgumentException) {
                error = e.message
            } finally {
                saving = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveGoodsScreen(onBack: () -> Unit, vm: ReceiveGoodsViewModel = hiltViewModel()) {
    val view by vm.purchase.collectAsStateWithLifecycle()
    var confirmOver by remember { mutableStateOf<List<String>>(emptyList()) }
    val v = view
    androidx.compose.runtime.LaunchedEffect(v != null) { v?.let(vm::prefill) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Receive goods") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                TextButton(onClick = {
                    val over = v?.let { vm.overReceived(it) }.orEmpty()
                    if (over.isNotEmpty()) confirmOver = over else vm.save(onBack)
                }, enabled = v != null && !vm.saving) { Text("Save", style = MaterialTheme.typography.titleMedium) }
            })
    }) { padding ->
        if (v == null) { Column(Modifier.padding(padding)) { LoadingState() }; return@Scaffold }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column {
                    Text("${v.purchase.supplierName.orEmpty()} ${v.purchase.invoiceNo?.let { "#$it" }.orEmpty()}",
                        style = MaterialTheme.typography.titleMedium)
                    DateField("Received date", vm.date, { it?.let { d -> vm.date = d } }, Modifier.fillMaxWidth())
                    FormField(vm.notes, { vm.notes = it }, "Notes")
                    Text("Enter what arrived now. Leave 0 for items that did not come.", style = MaterialTheme.typography.bodySmall)
                    ErrorText(vm.error)
                }
            }
            items(v.lines, key = { it.id }) { l ->
                val pr = v.progress(l)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(l.itemName, style = MaterialTheme.typography.titleSmall)
                        Text("Billed ${l.quantity} ${l.unitCode} · received ${Decimals.format(pr.received)} · remaining ${Decimals.format(pr.remaining)}",
                            style = MaterialTheme.typography.bodySmall)
                        FormField(vm.quantities[l.id].orEmpty(), { vm.quantities[l.id] = it }, "Received now (${l.unitCode})",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                        Row {
                            OutlinedTextField(vm.batches[l.id].orEmpty(), { vm.batches[l.id] = it }, label = { Text("Batch no.") },
                                singleLine = true, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            DateField("Expiry", vm.expiries[l.id], { vm.expiries[l.id] = it }, Modifier.weight(1f), clearable = true)
                        }
                    }
                }
            }
            item { Spacer(Modifier.padding(24.dp)) }
        }
    }
    if (confirmOver.isNotEmpty()) {
        AlertDialog(onDismissRequest = { confirmOver = emptyList() }, title = { Text("More than billed?") },
            text = { Text("You are receiving more than is still due for: ${confirmOver.joinToString()}. Suppliers sometimes send " +
                "extra; it will be recorded and the purchase flagged as over-received.") },
            confirmButton = { TextButton(onClick = { confirmOver = emptyList(); vm.save(onBack) }) { Text("Record it") } },
            dismissButton = { TextButton(onClick = { confirmOver = emptyList() }) { Text("Back") } })
    }
}

@Composable
private fun RevisionsDialog(vm: PurchaseDetailViewModel, names: Map<String, String>, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Earlier versions") },
        text = {
            val revs = vm.revisions
            when {
                vm.revisionsError != null -> Text("Could not load: ${vm.revisionsError}. Earlier versions need a connection.")
                revs == null -> Text("Loading...")
                revs.isEmpty() -> Text("No earlier versions.")
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(revs, key = { it.revisionNo }) { r ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text("Before change ${r.revisionNo} · ${app.stocklist.app.core.util.Dates.dateTime(r.changedAt)}",
                                style = MaterialTheme.typography.titleSmall)
                            Text("Changed by ${personName(names, r.changedBy, vm.me) ?: "-"}" + (r.reason?.let { " · reason: $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall)
                            Text("Then: ${r.before.invoiceNo?.let { "#$it · " } ?: ""}${r.before.purchaseDate.displayDate()} · " +
                                "cost ${Decimals.rupees(r.before.totalCost)} · ${purchaseStatusLabel(r.before.status)}",
                                style = MaterialTheme.typography.bodySmall)
                            r.before.lines.forEach { l ->
                                Text("  ${l.itemName}: ${l.quantity} ${l.unitCode}" + (l.purchasePrice?.let { " @ ${Decimals.rupees(it)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
