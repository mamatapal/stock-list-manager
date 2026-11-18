package app.stocklist.supplier.feature.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import app.stocklist.supplier.core.designsystem.FormField
import app.stocklist.supplier.core.designsystem.LabelValue
import app.stocklist.supplier.core.designsystem.SectionCard
import app.stocklist.supplier.core.i18n.Text
import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.data.remote.ApiResult
import app.stocklist.supplier.data.remote.OrderDto
import app.stocklist.supplier.data.remote.OrderLineDto
import app.stocklist.supplier.data.remote.OrderStatus
import app.stocklist.supplier.data.repository.NetworkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Groups of the order inbox. */
enum class OrderFilter { TO_DO, DISPATCHED, CLOSED, ALL }

object OrderLists {
    /** New and accepted orders need the supplier; dispatched ones wait for the shop; the rest are closed. */
    fun filterOf(status: String): OrderFilter = when (status) {
        OrderStatus.SENT, OrderStatus.ACCEPTED -> OrderFilter.TO_DO
        OrderStatus.DISPATCHED -> OrderFilter.DISPATCHED
        else -> OrderFilter.CLOSED
    }

    /** Orders of a group matching the search (shop name, city or order number), newest first. */
    fun of(all: List<OrderDto>, query: String, filter: OrderFilter): List<OrderDto> {
        val q = query.trim().lowercase().removePrefix("#")
        return all.filter { o ->
            (filter == OrderFilter.ALL || filterOf(o.status) == filter) &&
                (q.isEmpty() || listOfNotNull(o.shop.name, o.shop.city, NetworkText.orderNumber(o.id).removePrefix("#"))
                    .any { it.lowercase().contains(q) })
        }.sortedByDescending { it.createdAt }
    }
}

@HiltViewModel
class OrdersViewModel @Inject constructor(private val repo: NetworkRepository) : ViewModel() {
    var load by mutableStateOf<Load<List<OrderDto>>>(Load.Loading)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            when (val r = repo.orders()) {
                is ApiResult.Ok -> { load = Load.Ready(r.value); error = null }
                is ApiResult.Failure -> if (load is Load.Ready) error = r.error.message else load = Load.Failed(r.error)
            }
            busy = false
        }
    }
}

@Composable
fun OrdersScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: OrdersViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.refresh() }
    OrdersContent(vm.load, vm.busy, vm.error, onBack, vm::refresh, onOpen)
}

@Composable
fun OrdersContent(
    load: Load<List<OrderDto>>,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(OrderFilter.TO_DO) }
    NetworkScaffold(title = tr("Purchase orders"), onBack = onBack, busy = busy, onRefresh = onRefresh) {
        LoadContent(load, onRetry = onRefresh) { all ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ErrorText(error)
                FormField(query, { query = it }, label = tr("Search by shop or order number"), modifier = Modifier.testTag("search"))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrderFilter.entries.forEach { f ->
                        val count = OrderLists.of(all, "", f).size
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            modifier = Modifier.testTag("filter-${f.name}"),
                            label = {
                                Text(when (f) {
                                    OrderFilter.TO_DO -> tr("To do (%1\$s)", count)
                                    OrderFilter.DISPATCHED -> tr("Dispatched (%1\$s)", count)
                                    OrderFilter.CLOSED -> tr("Closed (%1\$s)", count)
                                    OrderFilter.ALL -> tr("All (%1\$s)", count)
                                }, maxLines = 1)
                            },
                        )
                    }
                }
            }
            val shown = OrderLists.of(all, query, filter)
            if (shown.isEmpty()) {
                NothingHere(if (all.isEmpty()) tr("No orders yet. Connected shops send orders from their Stock List Manager app.")
                else tr("No orders here."))
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { o -> OrderCard(o) { onOpen(o.id) } }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(o: OrderDto, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("order-${o.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.shop.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(NetworkText.orderStatus(o.status), orderColor(o.status))
            }
            Text(NetworkText.orderNumber(o.id) + " · " + NetworkText.dateTime(o.createdAt), style = MaterialTheme.typography.bodySmall)
            Text(tr("%1\$s products · %2\$s", o.lineCount, NetworkText.money(o.totalAmount)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ================================================================== one order

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val repo: NetworkRepository,
    saved: SavedStateHandle,
) : ViewModel() {
    private val id: String = checkNotNull(saved["id"])
    var load by mutableStateOf<Load<OrderDto>>(Load.Loading)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var done by mutableStateOf<String?>(null)
        private set

    fun refresh() = act(null) { repo.order(id) }

    fun accept() = act(tr("Order accepted. Send the goods, then mark it as dispatched.")) { repo.acceptOrder(id) }

    fun reject(reason: String) = act(tr("Order rejected. The shop has been told the reason.")) { repo.rejectOrder(id, reason) }

    fun dispatch(note: String?) = act(tr("Order marked as dispatched. The shop confirms what it receives.")) { repo.dispatchOrder(id, note) }

    /** One server call at a time; the order on screen changes only when the server confirms. */
    private fun act(successMessage: String?, call: suspend () -> ApiResult<OrderDto>) {
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
fun OrderScreen(onBack: () -> Unit, vm: OrderViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.refresh() }
    OrderContent(vm.load, vm.busy, vm.error, vm.done, onBack, vm::refresh, vm::accept, vm::reject, vm::dispatch)
}

@Composable
fun OrderContent(
    load: Load<OrderDto>,
    busy: Boolean,
    error: String?,
    done: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAccept: () -> Unit,
    onReject: (String) -> Unit,
    onDispatch: (String?) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val order = (load as? Load.Ready)?.value
    when (dialog) {
        "accept" -> ConfirmDialog(
            title = tr("Accept this order?"),
            message = tr("You agree to supply these products at the prices shown. The shop will see that you accepted."),
            confirmLabel = tr("Accept order"),
            onConfirm = { dialog = null; onAccept() },
            onDismiss = { dialog = null },
        )
        "reject" -> ReasonDialog(
            title = tr("Reject this order?"),
            message = tr("The shop will see your reason. A rejected order cannot be accepted later."),
            fieldLabel = tr("Reason"),
            confirmLabel = tr("Reject order"),
            required = true,
            onConfirm = { dialog = null; onReject(it) },
            onDismiss = { dialog = null },
        )
        "dispatch" -> ReasonDialog(
            title = tr("Mark as dispatched?"),
            message = tr("Do this when the goods have left. The shop then confirms what it actually received; only that changes its stock."),
            fieldLabel = tr("Note for the shop (optional), e.g. vehicle or bill number"),
            confirmLabel = tr("Mark as dispatched"),
            required = false,
            onConfirm = { dialog = null; onDispatch(it.ifBlank { null }) },
            onDismiss = { dialog = null },
        )
    }
    NetworkScaffold(
        title = order?.let { tr("Order %1\$s", NetworkText.orderNumber(it.id)) } ?: tr("Order"),
        onBack = onBack, busy = busy, onRefresh = onRefresh,
    ) {
        LoadContent(load, onRetry = onRefresh) { o ->
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ErrorText(error)
                done?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("done")) }
                Card(colors = CardDefaults.cardColors(containerColor = orderColor(o.status)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(NetworkText.orderStatus(o.status), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.testTag("status"))
                        NetworkText.receipt(o.receiptStatus)?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        NetworkText.orderNextStep(o.status)?.let { Text(it) }
                        o.statusReason?.takeIf { it.isNotBlank() }?.let { Text(tr("Reason: %1\$s", it)) }
                    }
                }
                when (o.status) {
                    OrderStatus.SENT -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { dialog = "accept" }, enabled = !busy, modifier = Modifier.weight(1f).testTag("accept")) {
                            Text(tr("Accept order"))
                        }
                        OutlinedButton(onClick = { dialog = "reject" }, enabled = !busy, modifier = Modifier.weight(1f).testTag("reject")) {
                            Text(tr("Reject order"))
                        }
                    }
                    OrderStatus.ACCEPTED ->
                        Button(onClick = { dialog = "dispatch" }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("dispatch")) {
                            Text(tr("Mark as dispatched"))
                        }
                    else -> {}
                }
                SectionCard(NetworkText.place(o.shop.name, o.shop.city, o.shop.state)) {
                    LabelValue(tr("Order number"), NetworkText.orderNumber(o.id))
                    LabelValue(tr("Ordered on"), NetworkText.dateTime(o.createdAt))
                    o.acceptedAt?.let { LabelValue(tr("Accepted on"), NetworkText.dateTime(it)) }
                    o.rejectedAt?.let { LabelValue(tr("Rejected on"), NetworkText.dateTime(it)) }
                    o.dispatchedAt?.let { LabelValue(tr("Dispatched on"), NetworkText.dateTime(it)) }
                    o.receivedAt?.let { LabelValue(tr("Received on"), NetworkText.dateTime(it)) }
                    o.cancelledAt?.let { LabelValue(tr("Cancelled on"), NetworkText.dateTime(it)) }
                    o.deliveryFailedAt?.let { LabelValue(tr("Delivery failed on"), NetworkText.dateTime(it)) }
                    o.note?.takeIf { it.isNotBlank() }?.let { LabelValue(tr("Note from the shop"), it) }
                }
                SectionCard(tr("Products (%1\$s)", o.lineCount)) {
                    o.lines.forEachIndexed { i, line ->
                        if (i > 0) HorizontalDivider()
                        OrderLine(line)
                    }
                    HorizontalDivider()
                    Row {
                        Text(tr("Total"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(NetworkText.money(o.totalAmount), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("total"))
                    }
                }
                SectionCard(tr("History")) { HistoryList(o.history, isOrder = true) }
            }
        }
    }
}

@Composable
private fun OrderLine(line: OrderLineDto) {
    Column(Modifier.fillMaxWidth().testTag("line-${line.lineNo}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(line.productNameEn, style = MaterialTheme.typography.bodyLarge)
        line.productNameHi?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(
            "${line.quantity} ${line.unit} × ${NetworkText.money(line.unitPrice)} = ${NetworkText.money(line.lineAmount)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        line.pack?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (line.receivedQuantity != null) {
            Text(tr("Received: %1\$s %2\$s", line.receivedQuantity, line.unit), style = MaterialTheme.typography.bodyMedium)
            line.shortageQuantity?.takeIf { it.toBigDecimalOrNull()?.signum() == 1 }?.let {
                Text(tr("Short: %1\$s %2\$s", it, line.unit), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            NetworkText.lineReceipt(line.receiptStatus)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
