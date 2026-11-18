package app.stocklist.supplier.feature.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.supplier.core.designsystem.ConfirmDialog
import app.stocklist.supplier.core.designsystem.ErrorText
import app.stocklist.supplier.core.designsystem.LabelValue
import app.stocklist.supplier.core.designsystem.SectionCard
import app.stocklist.supplier.core.i18n.Text
import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.data.remote.ApiResult
import app.stocklist.supplier.data.remote.ConnectionDto
import app.stocklist.supplier.data.remote.ConnectionStatus
import app.stocklist.supplier.data.repository.NetworkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tabs of the connections screen: new requests, connected shops, and everything that is closed. */
enum class ConnectionTab { REQUESTS, CONNECTED, PAST }

object ConnectionLists {
    fun tabOf(status: String): ConnectionTab = when (status) {
        ConnectionStatus.REQUESTED -> ConnectionTab.REQUESTS
        ConnectionStatus.ACTIVE -> ConnectionTab.CONNECTED
        else -> ConnectionTab.PAST
    }

    /** Connections of one tab, newest request first. */
    fun of(all: List<ConnectionDto>, tab: ConnectionTab): List<ConnectionDto> =
        all.filter { tabOf(it.status) == tab }.sortedByDescending { it.requestedAt ?: "" }
}

@HiltViewModel
class ConnectionsViewModel @Inject constructor(private val repo: NetworkRepository) : ViewModel() {
    var load by mutableStateOf<Load<List<ConnectionDto>>>(Load.Loading)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            when (val r = repo.connections()) {
                is ApiResult.Ok -> { load = Load.Ready(r.value); error = null }
                is ApiResult.Failure -> if (load is Load.Ready) error = r.error.message else load = Load.Failed(r.error)
            }
            busy = false
        }
    }
}

@Composable
fun ConnectionsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: ConnectionsViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.refresh() }
    ConnectionsContent(vm.load, vm.busy, vm.error, onBack, vm::refresh, onOpen)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsContent(
    load: Load<List<ConnectionDto>>,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    NetworkScaffold(title = tr("Shop connections"), onBack = onBack, busy = busy, onRefresh = onRefresh) {
        LoadContent(load, onRetry = onRefresh) { all ->
            val tabs = ConnectionTab.entries
            PrimaryTabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    val count = ConnectionLists.of(all, t).size
                    Tab(selected = tab == i, onClick = { tab = i }, modifier = Modifier.testTag("tab-${t.name}"), text = {
                        Text(
                            when (t) {
                                ConnectionTab.REQUESTS -> tr("Requests (%1\$s)", count)
                                ConnectionTab.CONNECTED -> tr("Connected (%1\$s)", count)
                                ConnectionTab.PAST -> tr("Past (%1\$s)", count)
                            },
                            maxLines = 1,
                        )
                    })
                }
            }
            ErrorText(error)
            val shown = ConnectionLists.of(all, tabs[tab])
            if (shown.isEmpty()) {
                NothingHere(
                    when (tabs[tab]) {
                        ConnectionTab.REQUESTS -> tr("No new connection requests. Shops send requests from their supplier directory.")
                        ConnectionTab.CONNECTED -> tr("No connected shops yet.")
                        ConnectionTab.PAST -> tr("No past connections.")
                    },
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { c -> ConnectionCard(c) { onOpen(c.id) } }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(c: ConnectionDto, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("connection-${c.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.shop.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(NetworkText.connectionStatus(c.status), connectionColor(c.status))
            }
            Text(NetworkText.place("", c.shop.city, c.shop.state).ifEmpty { "-" }, style = MaterialTheme.typography.bodySmall)
            Text(tr("Requested: %1\$s", NetworkText.dateTime(c.requestedAt)), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ================================================================== one connection

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val repo: NetworkRepository,
    saved: SavedStateHandle,
) : ViewModel() {
    private val id: String = checkNotNull(saved["id"])
    var load by mutableStateOf<Load<ConnectionDto>>(Load.Loading)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var done by mutableStateOf<String?>(null)
        private set

    fun refresh() = act(null) { repo.connection(id) }

    fun accept() = act(tr("Connection accepted. The shop can now see your catalogue and send orders.")) { repo.acceptConnection(id) }

    fun decline(reason: String?) = act(tr("Request declined.")) { repo.declineConnection(id, reason) }

    fun end(reason: String?) = act(tr("Connection ended. Orders not yet dispatched were cancelled.")) { repo.endConnection(id, reason) }

    /** One server call at a time: repeated taps are ignored while a call runs. The screen changes only on success. */
    private fun act(successMessage: String?, call: suspend () -> ApiResult<ConnectionDto>) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            when (val r = call()) {
                is ApiResult.Ok -> { load = Load.Ready(r.value); done = successMessage }
                is ApiResult.Failure -> {
                    if (load is Load.Ready) error = r.error.message else load = Load.Failed(r.error)
                    done = null
                }
            }
            busy = false
        }
    }
}

@Composable
fun ConnectionScreen(onBack: () -> Unit, vm: ConnectionViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.refresh() }
    ConnectionContent(vm.load, vm.busy, vm.error, vm.done, onBack, vm::refresh, vm::accept, vm::decline, vm::end)
}

@Composable
fun ConnectionContent(
    load: Load<ConnectionDto>,
    busy: Boolean,
    error: String?,
    done: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAccept: () -> Unit,
    onDecline: (String?) -> Unit,
    onEnd: (String?) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val shopName = (load as? Load.Ready)?.value?.shop?.name.orEmpty()
    when (dialog) {
        "accept" -> ConfirmDialog(
            title = tr("Accept this shop?"),
            message = tr("%1\$s will see your active catalogue products and prices and can send you purchase orders.", shopName),
            confirmLabel = tr("Accept"),
            onConfirm = { dialog = null; onAccept() },
            onDismiss = { dialog = null },
        )
    }
    NetworkScaffold(title = tr("Shop connection"), onBack = onBack, busy = busy, onRefresh = onRefresh) {
        LoadContent(load, onRetry = onRefresh) { c ->
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ErrorText(error)
                done?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("done")) }
                when (dialog) {
                    "decline" -> ReasonPanel(
                        title = tr("Decline this request?"),
                        message = tr("The shop will see that you declined. It can send a new request later."),
                        fieldLabel = tr("Reason (optional)"),
                        confirmLabel = tr("Decline"),
                        required = false,
                        busy = busy,
                        onConfirm = { dialog = null; onDecline(it) },
                        onCancel = { dialog = null },
                    )
                    "end" -> ReasonPanel(
                        title = tr("End this connection?"),
                        message = tr("Orders the shop sent that you have not dispatched yet will be cancelled. Dispatched orders stay open until the shop receives them."),
                        fieldLabel = tr("Reason (optional)"),
                        confirmLabel = tr("End connection"),
                        required = false,
                        busy = busy,
                        onConfirm = { dialog = null; onEnd(it) },
                        onCancel = { dialog = null },
                    )
                }
                SectionCard(c.shop.name) {
                    StatusChip(NetworkText.connectionStatus(c.status), connectionColor(c.status))
                    LabelValue(tr("City"), c.shop.city)
                    LabelValue(tr("State"), c.shop.state)
                    LabelValue(tr("Requested on"), NetworkText.dateTime(c.requestedAt))
                    c.respondedAt?.let { LabelValue(tr("Answered on"), NetworkText.dateTime(it)) }
                    c.endedAt?.let {
                        LabelValue(tr("Ended on"), NetworkText.dateTime(it))
                        LabelValue(tr("Ended by"), NetworkText.actor(c.endedBy ?: ""))
                    }
                    c.statusReason?.takeIf { it.isNotBlank() }?.let { LabelValue(tr("Reason"), it) }
                    Text(tr("Shops share only their name, city and state."), style = MaterialTheme.typography.bodySmall)
                }
                if (dialog == null) when (c.status) {
                    ConnectionStatus.REQUESTED -> {
                        Button(onClick = { dialog = "accept" }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("accept")) {
                            Text(tr("Accept"))
                        }
                        OutlinedButton(onClick = { dialog = "decline" }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("decline")) {
                            Text(tr("Decline"))
                        }
                    }
                    ConnectionStatus.ACTIVE ->
                        OutlinedButton(onClick = { dialog = "end" }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("end")) {
                            Text(tr("End connection"))
                        }
                    else -> Text(tr("This connection is closed. Only the shop can send a new request."),
                        style = MaterialTheme.typography.bodySmall)
                }
                SectionCard(tr("History")) { HistoryList(c.history, isOrder = false) }
            }
        }
    }
}
