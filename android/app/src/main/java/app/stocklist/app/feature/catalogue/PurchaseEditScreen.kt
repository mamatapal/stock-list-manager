package app.stocklist.app.feature.catalogue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.ConfirmDialog
import app.stocklist.app.core.designsystem.ErrorText
import app.stocklist.app.core.designsystem.FormField
import app.stocklist.app.core.designsystem.LoadingState
import app.stocklist.app.core.session.SessionState
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.data.local.ItemEntity
import app.stocklist.app.data.remote.ItemInput
import app.stocklist.app.data.repository.ItemRepository
import app.stocklist.app.data.repository.PurchaseHeaderDraft
import app.stocklist.app.data.repository.PurchaseLineDraft
import app.stocklist.app.data.repository.PurchaseRepository
import app.stocklist.app.data.repository.StockListRepository
import app.stocklist.app.data.repository.SupplierRepository
import app.stocklist.app.data.repository.UnitRepository
import app.stocklist.app.domain.catalogue.BuiltInUnits
import app.stocklist.app.domain.catalogue.Decimals
import app.stocklist.app.domain.catalogue.PurchaseCalculator
import app.stocklist.app.domain.permission.Permission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PurchaseEditViewModel @Inject constructor(
    private val repo: PurchaseRepository,
    private val itemRepo: ItemRepository,
    private val listRepo: StockListRepository,
    private val session: SessionStore,
    suppliers: SupplierRepository,
    units: UnitRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    val id: String? = handle.get<String>("id")
    private val fromList: String? = handle.get<String>("listId")
    val access = (session.state.value as? SessionState.SignedIn)?.user?.let { Access(it) }
    val units = units.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuiltInUnits.all)
    val suppliers = suppliers.observe("", false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val itemQuery = MutableStateFlow("")
    val catalogue = itemQuery.flatMapLatest { itemRepo.observe(it, "", false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var loaded by mutableStateOf(false)
        private set
    var supplierId by mutableStateOf<String?>(null)
    var invoiceNo by mutableStateOf("")
    var purchaseDate by mutableStateOf(LocalDate.now())
    var receivedDate by mutableStateOf(LocalDate.now())
    var notes by mutableStateOf("")
    var stockListId by mutableStateOf<String?>(null)
    var listTitle by mutableStateOf<String?>(null)
    var receiveNow by mutableStateOf(true)
    var completed by mutableStateOf(false)
        private set
    val lines = mutableStateListOf<PurchaseLineDraft>()
    /** Received so far per line of an existing purchase (those lines cannot be removed or lowered below it). */
    val alreadyReceived = mutableStateMapOf<String, BigDecimal>()
    var error by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)

    private fun can(p: Permission) = access?.has(p) == true
    val canBuyPrice get() = can(Permission.PURCHASE_PRICE_ENTER)
    val canSellPrice get() = can(Permission.SELLING_PRICE_ENTER)
    val canAddItem get() = can(Permission.ITEM_ADD)
    val showProfit get() = access?.canSeeProfit == true

    init {
        viewModelScope.launch {
            if (id != null) {
                repo.get(id)?.let { p ->
                    supplierId = p.supplierId; invoiceNo = p.invoiceNo.orEmpty(); purchaseDate = LocalDate.parse(p.purchaseDate)
                    notes = p.notes.orEmpty(); stockListId = p.stockListId; completed = p.status != "DRAFT"
                    receiveNow = !completed
                    alreadyReceived.putAll(repo.received(id))
                    lines += repo.lines(id).map {
                        PurchaseLineDraft(it.id, it.itemId, it.itemName, it.unitId, it.quantity, it.priceBasis,
                            it.purchasePrice.orEmpty(), it.sellingPrice.orEmpty(), it.quantity, "", "", it.stockListItemId,
                            it.updateCatalog)
                    }
                }
            } else if (fromList != null) {
                prefillFromList(fromList)
            }
            stockListId?.let { listTitle = listRepo.get(it)?.title }
            loaded = true
        }
    }

    /** "Record purchase" from a list: every catalogue item still to come, at its remaining quantity. */
    private suspend fun prefillFromList(listId: String) {
        val list = listRepo.get(listId) ?: return
        stockListId = listId
        supplierId = list.supplierId
        val listLines = listRepo.lines(listId)
        val progress = repo.observeListProgress(kotlinx.coroutines.flow.flowOf(listLines)).first()
        listLines.forEach { l ->
            val remaining = progress[l.id]?.progress?.remaining ?: BigDecimal(l.quantity)
            if (l.itemId == null || remaining.signum() <= 0) return@forEach
            val item = itemRepo.observeOne(l.itemId).first()
            val qty = Decimals.format(remaining) ?: return@forEach
            lines += PurchaseLineDraft(
                itemId = l.itemId, itemName = item?.name ?: l.itemName, unitId = l.unitId, quantity = qty,
                purchasePrice = if (canBuyPrice) item?.latestPurchasePrice.orEmpty() else "",
                sellingPrice = if (canSellPrice) item?.latestSellingPrice.orEmpty() else "",
                receivedNow = qty, stockListItemId = l.id,
            )
        }
    }

    fun addItems(picked: List<ItemEntity>) {
        picked.forEach { item ->
            lines += PurchaseLineDraft(
                itemId = item.id, itemName = item.name, unitId = item.defaultUnitId, quantity = "1",
                purchasePrice = if (canBuyPrice) item.latestPurchasePrice.orEmpty() else "",
                sellingPrice = if (canSellPrice) item.latestSellingPrice.orEmpty() else "",
                receivedNow = "1",
            )
        }
        dirty = true
    }

    /** Creates a catalogue item right here and adds it as a line. Returns an error message or null. */
    suspend fun addNewItem(name: String, category: String, unitId: String): String? {
        val n = name.trim().replace(Regex("\\s+"), " ")
        if (n.isEmpty()) return "Enter the item name"
        if (n.length > 150) return "At most 150 characters"
        if (itemRepo.nameInUse(n, null)) return "An item called \"$n\" already exists. Pick it from the list instead."
        val newId = itemRepo.save(null, ItemInput(n, category.trim().ifEmpty { null }, unitId, null, null, supplierId, null, true))
        lines += PurchaseLineDraft(itemId = newId, itemName = n, unitId = unitId, quantity = "1", receivedNow = "1")
        dirty = true
        return null
    }

    fun update(i: Int, change: (PurchaseLineDraft) -> PurchaseLineDraft) {
        val before = lines[i]
        val after = change(before)
        // Keep "received now" in step with the quantity until the user changes it on purpose.
        lines[i] = if (after.quantity != before.quantity && before.receivedNow == before.quantity) after.copy(receivedNow = after.quantity) else after
        dirty = true
    }

    fun remove(i: Int) { lines.removeAt(i); dirty = true }

    /** Switching basis keeps the money the same: per unit -> total multiplies once; total -> per unit divides once. */
    fun switchBasis(i: Int, basis: String) = update(i) { l ->
        if (l.priceBasis == basis) return@update l
        val qty = Decimals.tryParse(l.quantity, 3, false)
        val price = Decimals.tryParse(l.purchasePrice, 4, true)
        val converted = when {
            qty == null || price == null -> l.purchasePrice
            basis == PurchaseCalculator.TOTAL -> Decimals.money(PurchaseCalculator.line(PurchaseCalculator.PER_UNIT, qty, price, null).cost)
            else -> Decimals.format(PurchaseCalculator.line(PurchaseCalculator.TOTAL, qty, price, null).unitPrice)
        }
        l.copy(priceBasis = basis, purchasePrice = converted.orEmpty())
    }

    fun calc(l: PurchaseLineDraft): PurchaseCalculator.Line? {
        val qty = Decimals.tryParse(l.quantity, 3, false) ?: return null
        val buy = if (l.purchasePrice.isBlank()) null else Decimals.tryParse(l.purchasePrice, PurchaseCalculator.priceScale(l.priceBasis), true) ?: return null
        val sell = if (l.sellingPrice.isBlank()) null else Decimals.tryParse(l.sellingPrice, 4, true) ?: return null
        return PurchaseCalculator.line(l.priceBasis, qty, buy, sell)
    }

    fun totals(): PurchaseCalculator.Totals = PurchaseCalculator.totals(lines.mapNotNull { calc(it) })

    fun save(complete: Boolean, onDone: (String) -> Unit) {
        if (saving) return
        saving = true
        error = null
        viewModelScope.launch {
            try {
                val me = (session.state.value as? SessionState.SignedIn)?.user?.userId
                val saved = repo.save(id, PurchaseHeaderDraft(supplierId, invoiceNo, purchaseDate, receivedDate, stockListId, notes),
                    lines.toList(), complete, receiveNow && !completed, me, showProfit)
                dirty = false
                onDone(saved)
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
fun PurchaseEditScreen(onBack: () -> Unit, onSaved: (String) -> Unit, vm: PurchaseEditViewModel = hiltViewModel()) {
    val units by vm.units.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmComplete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text(if (vm.id == null) "New purchase" else "Edit purchase") },
                navigationIcon = {
                    IconButton(onClick = { if (vm.dirty) confirmLeave = true else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (!vm.completed) {
                        TextButton(onClick = { vm.save(false, onSaved) }, enabled = vm.loaded && !vm.saving) { Text("Save draft") }
                        TextButton(onClick = { confirmComplete = true }, enabled = vm.loaded && !vm.saving) {
                            Text("Complete", style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        TextButton(onClick = { vm.save(true, onSaved) }, enabled = vm.loaded && !vm.saving) {
                            Text("Save", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                })
        },
    ) { padding ->
        if (!vm.loaded) { Column(Modifier.padding(padding)) { LoadingState() }; return@Scaffold }
        val unitChoices = units.map { Choice(it.id, it.code) }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column {
                    DropdownField("Supplier *", vm.supplierId, suppliers.map { Choice(it.id, it.name) }, { vm.supplierId = it; vm.dirty = true })
                    FormField(vm.invoiceNo, { vm.invoiceNo = it; vm.dirty = true }, "Invoice number")
                    DateField("Purchase date", vm.purchaseDate, { it?.let { d -> vm.purchaseDate = d; vm.dirty = true } }, Modifier.fillMaxWidth())
                    vm.listTitle?.let { Text("For list: $it", style = MaterialTheme.typography.bodySmall) }
                    FormField(vm.notes, { vm.notes = it; vm.dirty = true }, "Notes", singleLine = false)
                    if (!vm.completed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Goods received with the bill", style = MaterialTheme.typography.bodyLarge)
                                Text("Records what arrived when you complete the purchase", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = vm.receiveNow, onCheckedChange = { vm.receiveNow = it })
                        }
                        if (vm.receiveNow) {
                            DateField("Received date", vm.receivedDate, { it?.let { d -> vm.receivedDate = d } }, Modifier.fillMaxWidth())
                        }
                    } else {
                        Text("Record later deliveries with \"Receive goods\" on the purchase.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!vm.canBuyPrice || !vm.canSellPrice) {
                        Text("Price fields you are not allowed to enter are locked.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            itemsIndexed(vm.lines, key = { _, l -> l.id }) { i, l -> PurchaseLineEditor(vm, i, l, unitChoices) }
            item {
                Column {
                    if (vm.lines.isEmpty()) {
                        Text("No items yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, null); Text("Add items") }
                    TotalsFooter(vm)
                    ErrorText(vm.error)
                    Spacer(Modifier.heightIn(min = 48.dp))
                }
            }
        }
    }
    if (picking) PurchaseItemPicker(vm, onDismiss = { picking = false })
    if (confirmLeave) ConfirmDialog("Leave without saving?", "Your changes will be lost. Use \"Save draft\" to keep them.",
        "Leave", onConfirm = { confirmLeave = false; onBack() }, onDismiss = { confirmLeave = false })
    if (confirmComplete) ConfirmDialog("Complete this purchase?",
        "It goes into purchase history" + (if (vm.receiveNow) " and the goods are recorded as received" else "") +
            ". Afterwards it can only be edited by someone with permission, and earlier versions are kept.",
        "Complete", onConfirm = { confirmComplete = false; vm.save(true, onSaved) }, onDismiss = { confirmComplete = false })
}

@Composable
private fun TotalsFooter(vm: PurchaseEditViewModel) {
    val t = vm.totals()
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Row { Text("Purchase cost (${vm.lines.size} items)", Modifier.weight(1f)); Text(Decimals.rupees(Decimals.money(t.cost)), fontWeight = FontWeight.Bold) }
            if (vm.showProfit) {
                Row { Text("Expected sales", Modifier.weight(1f)); Text(Decimals.rupees(Decimals.money(t.sales))) }
                Row { Text("Expected gross profit", Modifier.weight(1f)); Text(Decimals.rupees(Decimals.money(t.profit))) }
                Row { Text("Margin / markup", Modifier.weight(1f)); Text("${percentText(t.margin?.toPlainString())} / ${percentText(t.markup?.toPlainString())}") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseLineEditor(vm: PurchaseEditViewModel, i: Int, l: PurchaseLineDraft, units: List<Choice>) {
    val got = vm.alreadyReceived[l.id] ?: BigDecimal.ZERO
    val c = vm.calc(l)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.itemName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (got.signum() == 0) IconButton(onClick = { vm.remove(i) }) { Icon(Icons.Filled.Close, "Remove item") }
            }
            if (got.signum() != 0) Text("Already received ${Decimals.format(got)}", style = MaterialTheme.typography.bodySmall)
            Row {
                OutlinedTextField(l.quantity, { v -> vm.update(i) { it.copy(quantity = v) } }, label = { Text("Quantity") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                DropdownField("Unit", l.unitId, units, { u -> u?.let { vm.update(i) { it.copy(unitId = u) } } }, Modifier.weight(1f),
                    enabled = got.signum() == 0)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(PurchaseCalculator.PER_UNIT to "Price per unit", PurchaseCalculator.TOTAL to "Total amount").forEachIndexed { idx, (b, label) ->
                    SegmentedButton(selected = l.priceBasis == b, onClick = { vm.switchBasis(i, b) }, enabled = vm.canBuyPrice,
                        shape = SegmentedButtonDefaults.itemShape(idx, 2)) { Text(label) }
                }
            }
            Row {
                OutlinedTextField(l.purchasePrice, { v -> vm.update(i) { it.copy(purchasePrice = v) } }, singleLine = true,
                    label = { Text(if (l.priceBasis == PurchaseCalculator.TOTAL) "Total paid (₹)" else "Buy price / unit (₹)") },
                    enabled = vm.canBuyPrice, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(l.sellingPrice, { v -> vm.update(i) { it.copy(sellingPrice = v) } }, singleLine = true,
                    label = { Text("Sell price / unit (₹)") }, enabled = vm.canSellPrice,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            if (c == null) {
                Text("Check the numbers on this line", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(buildString {
                    append("Cost ${Decimals.rupees(Decimals.money(c.cost))}")
                    if (l.priceBasis == PurchaseCalculator.TOTAL && c.unitPrice != null) {
                        append(" (not multiplied) · ${Decimals.rupees(Decimals.money(c.unitPrice))} per unit")
                    }
                    if (vm.showProfit && c.sales != null) {
                        append("\nSales ${Decimals.rupees(Decimals.money(c.sales))} · profit ${Decimals.rupees(Decimals.money(c.profit))}")
                        append(" · margin ${percentText(c.margin?.toPlainString())} · markup ${percentText(c.markup?.toPlainString())}")
                    }
                }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                val sell = Decimals.tryParse(l.sellingPrice, 4, true)
                if (sell != null && c.unitPrice != null && sell < c.unitPrice) {
                    Text("Selling price is below the purchase price", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = l.updateCatalog, onCheckedChange = { v -> vm.update(i) { it.copy(updateCatalog = v) } })
                Text("Update the item's latest prices", style = MaterialTheme.typography.bodySmall)
            }
            if (vm.receiveNow && !vm.completed) {
                OutlinedTextField(l.receivedNow, { v -> vm.update(i) { it.copy(receivedNow = v) } }, singleLine = true,
                    label = { Text("Received now") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                Row {
                    OutlinedTextField(l.batchNo, { v -> vm.update(i) { it.copy(batchNo = v) } }, singleLine = true,
                        label = { Text("Batch no.") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    DateField("Expiry", l.expiryDate.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
                        { d -> vm.update(i) { it.copy(expiryDate = d?.toString().orEmpty()) } }, Modifier.weight(1f), clearable = true)
                }
            }
        }
    }
}

@Composable
private fun PurchaseItemPicker(vm: PurchaseEditViewModel, onDismiss: () -> Unit) {
    val q by vm.itemQuery.collectAsStateWithLifecycle()
    val items by vm.catalogue.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    val chosen = remember { mutableStateMapOf<String, ItemEntity>() }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }
    var newUnit by remember { mutableStateOf(BuiltInUnits.PCS) }
    var newError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New item" else "Add items") },
        text = {
            if (creating) {
                Column {
                    FormField(newName, { newName = it }, "Item name *")
                    FormField(newCategory, { newCategory = it }, "Category")
                    DropdownField("Unit", newUnit, units.map { Choice(it.id, "${it.name} (${it.code})") }, { it?.let { u -> newUnit = u } })
                    ErrorText(newError)
                }
            } else {
                Column {
                    OutlinedTextField(q, { vm.itemQuery.value = it }, singleLine = true, placeholder = { Text("Search items") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(items, key = { it.id }) { item ->
                            Row(Modifier.fillMaxWidth().clickable { if (chosen.remove(item.id) == null) chosen[item.id] = item },
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = item.id in chosen, onCheckedChange = null)
                                Column {
                                    Text(item.name)
                                    val last = listOfNotNull(item.latestPurchasePrice?.let { "buy ${Decimals.rupees(it)}" },
                                        item.latestSellingPrice?.let { "sell ${Decimals.rupees(it)}" }).joinToString(" · ")
                                    if (last.isNotEmpty()) Text(last, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (vm.canAddItem) {
                        TextButton(onClick = { creating = true; newName = q.trim() }) {
                            Text(if (q.isBlank()) "+ Add a new item" else "+ Add \"${q.trim()}\" as a new item")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = {
                    scope.launch {
                        newError = vm.addNewItem(newName, newCategory, newUnit)
                        if (newError == null) { vm.itemQuery.value = ""; onDismiss() }
                    }
                }) { Text("Create and add") }
            } else {
                TextButton(onClick = { vm.addItems(chosen.values.toList()); vm.itemQuery.value = ""; onDismiss() },
                    enabled = chosen.isNotEmpty()) { Text("Add ${chosen.size}") }
            }
        },
        dismissButton = { TextButton(onClick = { if (creating) creating = false else onDismiss() }) { Text(if (creating) "Back" else "Cancel") } },
    )
}
