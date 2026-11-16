package app.stocklist.app.feature.catalogue

import app.stocklist.app.core.i18n.Bilingual

import app.stocklist.app.core.i18n.tr
import app.stocklist.app.core.i18n.trMessage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import app.stocklist.app.core.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.EmptyState
import app.stocklist.app.core.util.Dates
import app.stocklist.app.data.local.OutboxEntity
import app.stocklist.app.data.sync.EntityTypes
import app.stocklist.app.data.sync.SyncRepository
import app.stocklist.app.data.sync.SyncScheduler
import app.stocklist.app.feature.dashboard.DashboardTile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val sync: SyncRepository,
    private val scheduler: SyncScheduler,
    batches: app.stocklist.app.data.repository.BatchRepository,
    network: app.stocklist.app.data.sync.NetworkMonitor,
) : ViewModel() {
    val online = network.online
    val outbox = sync.outboxItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val unsent = sync.unsentChanges.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
        app.stocklist.app.data.sync.UnsentChanges.NONE)
    fun tryAgain(id: String) { viewModelScope.launch { sync.tryAgain(id) } }
    val expiryCounts = batches.observeCounts(java.time.LocalDate.now())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val status = sync.status
    val pending = sync.pendingCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val problems = sync.problems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lastSync = sync.lastSync.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun syncNow() { viewModelScope.launch { sync.syncNow() } }
    fun keepMine(id: String) { viewModelScope.launch { sync.keepMine(id) } }
    fun useTheirs(id: String) { viewModelScope.launch { sync.useTheirs(id) } }
    fun discard(id: String) { viewModelScope.launch { sync.discard(id) } }

    init {
        scheduler.startPeriodic()
        syncNow()
    }
}

/** Dashboard block: sync status plus the supplier, item and list tiles this user may open. */
@Composable
fun WorkSection(access: Access, onSuppliers: () -> Unit, onItems: () -> Unit, onLists: () -> Unit, onProblems: () -> Unit,
                onPurchases: () -> Unit = {}, onExpiry: (String) -> Unit = {}, onBatches: () -> Unit = {},
                onReports: () -> Unit = {},
                vm: SyncViewModel = hiltViewModel()) {
    val status by vm.status.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val problems by vm.problems.collectAsStateWithLifecycle()
    val last by vm.lastSync.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val expiryCounts by vm.expiryCounts.collectAsStateWithLifecycle()
    if (access.canViewExpiry) ExpiryAlertCard(expiryCounts) { onExpiry(it.name) }
    Card(colors = CardDefaults.cardColors(
        containerColor = if (problems.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
    )) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    status.running -> tr("Syncing...")
                    !online || status.offline -> if (pending > 0) tr("Offline: %1\$s change(s) saved on this phone, sent when online", pending)
                        else tr("Offline: you can keep working; changes are saved on this phone")
                    pending > 0 -> tr("%1\$s change(s) waiting to sync", pending)
                    else -> tr("Up to date")
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(tr("Last synced: %1\$s", last?.toLongOrNull()?.let { Dates.dateTime(Instant.ofEpochMilli(it).toString()) } ?: tr("never")),
                style = MaterialTheme.typography.bodySmall)
            if (online && !status.offline) status.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::syncNow, enabled = !status.running) { Text(tr("Sync now")) }
                TextButton(onClick = onProblems) { Text(if (problems.isNotEmpty()) tr("%1\$s need attention", problems.size) else tr("Details")) }
            }
        }
    }
    if (access.canViewLists) DashboardTile(Icons.AutoMirrored.Filled.ListAlt, tr("Out-of-stock lists"), tr("Create, edit and share lists"), onLists)
    if (access.canViewPurchases) DashboardTile(Icons.Filled.ReceiptLong, tr("Purchases"), tr("Bills, goods received and deliveries"), onPurchases)
    if (access.canViewReports) DashboardTile(Icons.Filled.Assessment, tr("Reports"),
        if (access.canViewProfitReport) tr("Purchases, supplier totals, item prices, expected profit") else tr("Purchases, supplier totals, item prices"), onReports)
    if (access.canViewExpiry) {
        DashboardTile(Icons.Filled.EventBusy, tr("Expiry"), tr("Expired and soon-to-expire stock"), { onExpiry("WITHIN_30") })
        DashboardTile(Icons.Filled.Inventory, tr("Batches"), tr("Every batch received, with its purchase"), onBatches)
    }
    if (access.canViewItems) DashboardTile(Icons.Filled.Inventory2, tr("Items"), tr("Catalogue, units and latest prices"), onItems)
    if (access.canViewSuppliers) DashboardTile(Icons.Filled.LocalShipping, tr("Suppliers"), tr("Contacts of the people you buy from"), onSuppliers)
}

private val labelJson = Json { ignoreUnknownKeys = true }

private fun OutboxEntity.label(): String {
    val kind = when (entityType) {
        EntityTypes.SUPPLIER -> tr("Supplier"); EntityTypes.ITEM -> tr("Item"); EntityTypes.UNIT -> tr("Unit")
        EntityTypes.PURCHASE -> tr("Purchase"); EntityTypes.DELIVERY -> tr("Goods received"); else -> tr("List")
    }
    val name = payloadJson?.let {
        runCatching {
            val o = labelJson.parseToJsonElement(it).jsonObject
            (o["name"] ?: o["title"] ?: o["invoiceNo"]?.let { inv -> kotlinx.serialization.json.JsonPrimitive(tr("invoice %1\$s", inv.jsonPrimitive.content)) }
                ?: o["receivedDate"])?.jsonPrimitive?.content
        }.getOrNull()
    }
    return if (name != null) "$kind: $name" else if (action == "DELETE") tr("%1\$s (deletion)", kind) else kind
}

/** What a conflict means for this kind of record, so the choice is an informed one. */
private fun conflictHelp(p: OutboxEntity): String {
    val deleted = p.conflictJson?.let {
        runCatching { labelJson.parseToJsonElement(it).jsonObject["deleted"]?.jsonPrimitive?.content == "true" }.getOrNull()
    } == true
    val cancelled = p.conflictJson?.contains("\"status\":\"CANCELLED\"") == true
    return when {
        deleted -> tr("Someone deleted this on another phone after you changed it. Keep mine brings it back with your changes; Use theirs removes it from this phone.")
        cancelled -> tr("Someone cancelled this purchase on another phone. A cancelled purchase cannot be changed: choose Use theirs. Your edit is shown below so you can note it.")
        p.entityType == EntityTypes.PURCHASE -> tr("Someone else changed this purchase after you started. Keep mine saves your version on top; the other version is kept in the purchase's history either way.")
        else -> tr("Someone else changed this on another phone after you started. Keep mine saves your version on top of theirs; Use theirs drops your change.")
    }
}

private fun OutboxEntity.isFinancial() = entityType == EntityTypes.PURCHASE || entityType == EntityTypes.DELIVERY

private data class PendingChoice(val title: String, val text: String, val confirm: String, val action: () -> Unit)

/** Sync status: connection, last sync, what is waiting, and the changes that need a decision. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncProblemsScreen(onBack: () -> Unit, vm: SyncViewModel = hiltViewModel()) {
    val problems by vm.problems.collectAsStateWithLifecycle()
    val outbox by vm.outbox.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val last by vm.lastSync.collectAsStateWithLifecycle()
    val unsent by vm.unsent.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<PendingChoice?>(null) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("Sync")) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Bilingual.oneLine(tr("Back"))) }
        }, actions = {
            TextButton(onClick = vm::syncNow, enabled = !status.running && online) { Text(tr("Sync now")) }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(when {
                            status.running -> tr("Syncing...")
                            !online -> tr("No internet connection")
                            status.lastError != null -> tr("Last attempt had a problem")
                            unsent.isEmpty -> tr("Everything is synced")
                            else -> tr("Waiting to sync")
                        }, style = MaterialTheme.typography.titleMedium)
                        Text(tr("Last successful sync: %1\$s", last?.toLongOrNull()?.let { Dates.dateTime(Instant.ofEpochMilli(it).toString()) } ?: tr("never")))
                        Text(if (unsent.isEmpty) tr("No unsent changes on this phone.") else tr("Not yet on the server: %1\$s.", unsent.describe()))
                        if (online) status.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Text(tr("Changes are saved on this phone first and sent automatically when there is internet. Sending the same change twice never creates a duplicate."), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (problems.isNotEmpty()) item { Text(tr("Needs your decision"), style = MaterialTheme.typography.titleMedium) }
            items(problems, key = { it.opId }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.label(), style = MaterialTheme.typography.titleSmall)
                        when {
                            p.state == "CONFLICT" -> {
                                Text(conflictHelp(p), style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { vm.keepMine(p.entityId) }) { Text(tr("Keep mine")) }
                                    OutlinedButton(onClick = {
                                        confirm = PendingChoice(tr("Use the other version?"),
                                            tr("Your change to %1\$s will be removed from this phone.", p.label()) +
                                                if (p.isFinancial()) tr(" This is a purchase record: make sure the other version is correct.") else "",
                                            tr("Use theirs")) { vm.useTheirs(p.entityId) }
                                    }) { Text(tr("Use theirs")) }
                                }
                            }
                            p.serverRetries > 0 -> {
                                Text(p.lastError?.let { trMessage(it) } ?: tr("The server could not process this change."), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error)
                                Text(tr("It is kept safely on this phone."), style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { vm.tryAgain(p.entityId) }) { Text(tr("Try again")) }
                            }
                            else -> {
                                Text(p.lastError?.let { trMessage(it) } ?: tr("The server did not accept this change."), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error)
                                Text(tr("Correct the record and save it again, or discard this change."), style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = {
                                    confirm = PendingChoice(if (p.isFinancial()) tr("Discard this purchase record?") else tr("Discard this change?"),
                                        tr("%1\$s will be removed from this phone and will not be sent. ", p.label()) +
                                            if (p.isFinancial()) tr("It is a purchase or goods-received record and cannot be recovered afterwards.")
                                            else tr("This cannot be undone."),
                                        tr("Discard")) { vm.discard(p.entityId) }
                                }) { Text(tr("Discard my change")) }
                            }
                        }
                    }
                }
            }
            val waiting = outbox.filter { it.state == "PENDING" }
            if (waiting.isNotEmpty()) item { Text(tr("Waiting to send (%1\$s)", waiting.size), style = MaterialTheme.typography.titleMedium) }
            items(waiting, key = { "w" + it.opId }) { p ->
                Column(Modifier.fillMaxWidth()) {
                    Text(p.label(), style = MaterialTheme.typography.bodyMedium)
                    Text(listOfNotNull(
                        Dates.dateTime(Instant.ofEpochMilli(p.createdAt).toString()),
                        p.attempts.takeIf { it > 0 }?.let { tr("tried %1\$s time(s)", it) },
                        p.lastError?.takeIf { p.attempts > 0 },
                    ).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    confirm?.let { c ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(c.title) }, text = { Text(c.text) },
            confirmButton = { TextButton(onClick = { confirm = null; c.action() }) { Text(c.confirm, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("Keep it")) } })
    }
}
