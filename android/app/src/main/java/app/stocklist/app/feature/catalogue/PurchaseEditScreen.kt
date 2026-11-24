package app.stocklist.app.feature.catalogue

import app.stocklist.app.core.i18n.bi
import app.stocklist.app.core.i18n.BilingualName
import app.stocklist.app.core.designsystem.BilingualNameFields
import app.stocklist.app.core.i18n.Bilingual

import app.stocklist.app.core.i18n.asciiDigits

import app.stocklist.app.core.i18n.tr

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import app.stocklist.app.core.i18n.Text
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
import app.stocklist.app.data.local.StockListLineEntity
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
            }
            stockListId?.let { listTitle = listRepo.get(it)?.title }
            loaded = true
        }
    }

    /** One line of an out-of-stock list that can be bought now: a catalogue item with something still to come. */
    class ListPick(val line: StockListLineEntity, val remaining: BigDecimal, val itemName: String, val alreadyAdded: Boolean)

    /** Saved out-of-stock lists to pick from (newest first, as the lists screen shows them). */
    val pickableLists = listRepo.observe("", "SAVED", "", "", "")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The lines of [listId] that can still be bought. Lines without a catalogue item or with nothing left are left out. */
    suspend fun pickable(listId: String): List<ListPick> {
        val listLines = listRepo.lines(listId)
        val progress = repo.observeListProgress(kotlinx.coroutines.flow.flowOf(listLines)).first()
        return listLines.sortedBy { it.sortOrder }.mapNotNull { l ->
            val remaining = progress[l.id]?.progress?.remaining ?: BigDecimal(l.quantity)
            if (l.itemId == null || remaining.signum() <= 0) return@mapNotNull null
            ListPick(l, remaining, itemRepo.observeOne(l.itemId).first()?.name ?: l.itemName, lines.any { it.stockListItemId == l.id })
        }
    }

    /** Adds the chosen list lines at their remaining quantity; the supplier and the list link are taken from the list when still empty. */
    suspend fun addFromList(listId: String, picked: List<ListPick>) {
        val list = listRepo.get(listId) ?: return
        if (stockListId == null) { stockListId = listId; listTitle = list.title }
        if (supplierId == null) supplierId = list.supplierId
        picked.forEach { p ->
            val l = p.line
            val item = itemRepo.observeOne(l.itemId!!).first()
            val qty = Decimals.format(p.remaining) ?: return@forEach
            lines += PurchaseLineDraft(
                itemId = l.itemId, itemName = item?.name ?: l.itemName, unitId = l.unitId, quantity = qty,
                purchasePrice = if (canBuyPrice) item?.latestPurchasePrice.orEmpty() else "",
                sellingPrice = if (canSellPrice) item?.latestSellingPrice.orEmpty() else "",
                receivedNow = qty, stockListItemId = l.id,
            )
        }
        dirty = true
    }

    /** "Record purchase" from a list opens the chooser on that list; nothing is added until the person confirms. */
    val startList: String? get() = if (id == null) fromList else null

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
        val n = BilingualName.clean(name)
        if (n.isEmpty()) return tr("Enter the item name")
        if (n.length > 150) return tr("At most 150 characters")
        if (itemRepo.nameInUse(n, null)) return tr("An item called \"%1\$s\" already exists. Pick it from the list instead.", n)
        val newId = itemRepo.save(null, ItemInput(n, BilingualName.clean(category).ifEmpty { null }, unitId, null, null, supplierId, null, true))
        lines += PurchaseLineDraft(itemId = newId, itemName = n, unitId = unitId, quantity = "1", receivedNow = "1")
        dirty = true
        return null
    }

    /** An item just created on the item page becomes a line of this purchase. */
    suspend fun addCreatedItem(itemId: String) {
        itemRepo.observeOne(itemId).first()?.let { addItems(listOf(it)) }
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
fun PurchaseEditScreen(
    onBack: () -> Unit, onSaved: (String) -> Unit, newItemId: String? = null, onNewItemHandled: () -> Unit = {},
    onCreateItem: (String) -> Unit = {}, vm: PurchaseEditViewModel = hiltViewModel(),
) {
    // back from the item page with a new item: add it as a line (the purchase being typed is kept in the view model)
    LaunchedEffect(newItemId) { if (newItemId != null) { vm.addCreatedItem(newItemId); onNewItemHandled() } }
    val units by vm.units.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var pickingFromList by remember { mutableStateOf(vm.startList != null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmComplete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(Bilingual.twoLines(it)) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text(if (vm.id == null) tr("New purchase") else tr("Edit purchase")) },
                navigationIcon = {
                    IconButton(onClick = { if (vm.dirty) confirmLeave = true else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Bilingual.oneLine(tr("Back"))) }
                },
                actions = {
                    if (!vm.completed) {
                        TextButton(onClick = { vm.save(false, onSaved) }, enabled = vm.loaded && !vm.saving) { Text(tr("Save draft")) }
                        TextButton(onClick = { confirmComplete = true }, enabled = vm.loaded && !vm.saving) {
                            Text(tr("Complete"), style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        TextButton(onClick = { vm.save(true, onSaved) }, enabled = vm.loaded && !vm.saving) {
                            Text(tr("Save"), style = MaterialTheme.typography.titleMedium)
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
                    DropdownField(tr("Supplier *"), vm.supplierId, suppliers.map { Choice(it.id, it.name.bi()) }, { vm.supplierId = it; vm.dirty = true })
                    FormField(vm.invoiceNo, { vm.invoiceNo = it; vm.dirty = true }, tr("Invoice number"))
                    DateField(tr("Purchase date"), vm.purchaseDate, { it?.let { d -> vm.purchaseDate = d; vm.dirty = true } }, Modifier.fillMaxWidth())
                    vm.listTitle?.let { Text(tr("For list: %1\$s", it), style = MaterialTheme.typography.bodySmall) }
                    FormField(vm.notes, { vm.notes = it; vm.dirty = true }, tr("Notes"), singleLine = false)
                    if (!vm.completed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("Goods received with the bill"), style = MaterialTheme.typography.bodyLarge)
                                Text(tr("Records what arrived when you complete the purchase"), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = vm.receiveNow, onCheckedChange = { vm.receiveNow = it })
                        }
                        if (vm.receiveNow) {
                            DateField(tr("Received date"), vm.receivedDate, { it?.let { d -> vm.receivedDate = d } }, Modifier.fillMaxWidth())
                        }
                    } else {
                        Text(tr("Record later deliveries with \"Receive goods\" on the purchase."), style = MaterialTheme.typography.bodySmall)
                    }
                    if (!vm.canBuyPrice || !vm.canSellPrice) {
                        Text(tr("Price fields you are not allowed to enter are locked."), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            itemsIndexed(vm.lines, key = { _, l -> l.id }) { i, l -> PurchaseLineEditor(vm, i, l, unitChoices) }
            item {
                Column {
                    if (vm.lines.isEmpty()) {
                        Text(tr("No items yet."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, null); Text(tr("Add items")) }
                    val pickLists by vm.pickableLists.collectAsStateWithLifecycle()
                    if (!vm.completed && pickLists.isNotEmpty()) {
                        OutlinedButton(onClick = { pickingFromList = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, null); Text(tr("Pick from out-of-stock list"))
                        }
                    }
                    TotalsFooter(vm)
                    ErrorText(vm.error)
                    Spacer(Modifier.heightIn(min = 48.dp))
                }
            }
        }
    }
    if (picking) PurchaseItemPicker(vm, onCreateItem = onCreateItem, onDismiss = { picking = false })
    if (pickingFromList) PurchaseListPicker(vm, startListId = vm.startList, onDismiss = { pickingFromList = false })
    if (confirmLeave) ConfirmDialog(tr("Leave without saving?"), tr("Your changes will be lost. Use \"Save draft\" to keep them."),
        tr("Leave"), onConfirm = { confirmLeave = false; onBack() }, onDismiss = { confirmLeave = false })
    if (confirmComplete) ConfirmDialog(tr("Complete this purchase?"),
        if (vm.receiveNow) tr("It goes into purchase history and the goods are recorded as received. Afterwards it can only be edited by someone with permission, and earlier versions are kept.")
        else tr("It goes into purchase history. Afterwards it can only be edited by someone with permission, and earlier versions are kept."),
        tr("Complete"), onConfirm = { confirmComplete = false; vm.save(true, onSaved) }, onDismiss = { confirmComplete = false })
}

@Composable
private fun TotalsFooter(vm: PurchaseEditViewModel) {
    val t = vm.totals()
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Row { Text(tr("Purchase cost (%1\$s items)", vm.lines.size), Modifier.weight(1f)); Text(Decimals.rupees(Decimals.money(t.cost)), fontWeight = FontWeight.Bold) }
            if (vm.showProfit) {
                Row { Text(tr("Expected sales"), Modifier.weight(1f)); Text(Decimals.rupees(Decimals.money(t.sales))) }
                Row { Text(tr("Expected gross profit"), Modifier.weight(1f)); Text(Decimals.rupees(Decimals.money(t.profit))) }
                Row { Text(tr("Margin / markup"), Modifier.weight(1f)); Text("${percentText(t.margin?.toPlainString())} / ${percentText(t.markup?.toPlainString())}") }
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
                Text(l.itemName.bi(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (got.signum() == 0) IconButton(onClick = { vm.remove(i) }) { Icon(Icons.Filled.Close, Bilingual.oneLine(tr("Remove item"))) }
            }
            if (got.signum() != 0) Text(tr("Already received %1\$s", Decimals.format(got)), style = MaterialTheme.typography.bodySmall)
            Row {
                OutlinedTextField(l.quantity, { v -> vm.update(i) { it.copy(quantity = asciiDigits(v)) } }, label = { Text(tr("Quantity"), maxLines = 1) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                DropdownField(tr("Unit"), l.unitId, units, { u -> u?.let { vm.update(i) { it.copy(unitId = u) } } }, Modifier.weight(1f),
                    enabled = got.signum() == 0)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(PurchaseCalculator.PER_UNIT to tr("Price per unit"), PurchaseCalculator.TOTAL to tr("Total amount")).forEachIndexed { idx, (b, label) ->
                    SegmentedButton(selected = l.priceBasis == b, onClick = { vm.switchBasis(i, b) }, enabled = vm.canBuyPrice,
                        shape = SegmentedButtonDefaults.itemShape(idx, 2)) { Text(label) }
                }
            }
            Row {
                OutlinedTextField(l.purchasePrice, { v -> vm.update(i) { it.copy(purchasePrice = asciiDigits(v)) } }, singleLine = true,
                    label = { Text(if (l.priceBasis == PurchaseCalculator.TOTAL) tr("Total paid (₹)") else tr("Buy price / unit (₹)"), maxLines = 1) },
                    enabled = vm.canBuyPrice, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(l.sellingPrice, { v -> vm.update(i) { it.copy(sellingPrice = asciiDigits(v)) } }, singleLine = true,
                    label = { Text(tr("Sell price / unit (₹)"), maxLines = 1) }, enabled = vm.canSellPrice,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            if (c == null) {
                Text(tr("Check the numbers on this line"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(buildString {
                    append(tr("Cost %1\$s", Decimals.rupees(Decimals.money(c.cost))))
                    if (l.priceBasis == PurchaseCalculator.TOTAL && c.unitPrice != null) {
                        append(tr(" (not multiplied) · %1\$s per unit", Decimals.rupees(Decimals.money(c.unitPrice))))
                    }
                    if (vm.showProfit && c.sales != null) {
                        append(tr("\nSales %1\$s · profit %2\$s", Decimals.rupees(Decimals.money(c.sales)), Decimals.rupees(Decimals.money(c.profit))))
                        append(tr(" · margin %1\$s · markup %2\$s", percentText(c.margin?.toPlainString()), percentText(c.markup?.toPlainString())))
                    }
                }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                val sell = Decimals.tryParse(l.sellingPrice, 4, true)
                if (sell != null && c.unitPrice != null && sell < c.unitPrice) {
                    Text(tr("Selling price is below the purchase price"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = l.updateCatalog, onCheckedChange = { v -> vm.update(i) { it.copy(updateCatalog = v) } })
                Text(tr("Update the item's latest prices"), style = MaterialTheme.typography.bodySmall)
            }
            if (vm.receiveNow && !vm.completed) {
                OutlinedTextField(l.receivedNow, { v -> vm.update(i) { it.copy(receivedNow = asciiDigits(v)) } }, singleLine = true,
                    label = { Text(tr("Received now"), maxLines = 1) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                Row {
                    OutlinedTextField(l.batchNo, { v -> vm.update(i) { it.copy(batchNo = v) } }, singleLine = true,
                        label = { Text(tr("Batch no."), maxLines = 1) }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    DateField(tr("Expiry"), l.expiryDate.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
                        { d -> vm.update(i) { it.copy(expiryDate = d?.toString().orEmpty()) } }, Modifier.weight(1f), clearable = true)
                }
            }
        }
    }
}

/** Choose an out-of-stock list, tick the lines to buy now, add them to this purchase. Can be used again for another list. */
@Composable
private fun PurchaseListPicker(vm: PurchaseEditViewModel, startListId: String? = null, onDismiss: () -> Unit) {
    val lists by vm.pickableLists.collectAsStateWithLifecycle()
    var listId by remember { mutableStateOf<String?>(null) }
    var picks by remember { mutableStateOf<List<PurchaseEditViewModel.ListPick>?>(null) }
    val chosen = remember { mutableStateMapOf<String, Boolean>() }
    val units by vm.units.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // opened from "Record purchase" on a list: go straight to that list's lines, nothing selected yet
    LaunchedEffect(startListId) {
        if (startListId != null && listId == null) { chosen.clear(); picks = vm.pickable(startListId); listId = startListId }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Pick from out-of-stock list")) },
        text = {
            val id = listId
            val shown = picks
            if (id == null || shown == null) {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(lists, key = { it.id }) { l ->
                        Column(Modifier.fillMaxWidth().clickable {
                            listId = l.id
                            chosen.clear()
                            scope.launch {
                                val found = vm.pickable(l.id)
                                picks = found
                            }
                        }.padding(vertical = 10.dp)) {
                            Text(l.title.bi())
                            Text(l.listDate, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            } else if (shown.isEmpty()) {
                Text(tr("Nothing left to buy on this list"))
            } else {
                Column {
                    val selectable = shown.filter { !it.alreadyAdded }
                    TextButton(onClick = {
                        val all = selectable.all { chosen[it.line.id] == true }
                        selectable.forEach { chosen[it.line.id] = !all }
                    }) { Text(tr("Select all")) }
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(shown, key = { it.line.id }) { p ->
                            val unit = units.firstOrNull { it.id == p.line.unitId }?.code.orEmpty()
                            val on = p.alreadyAdded || chosen[p.line.id] == true
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable(enabled = !p.alreadyAdded) { chosen[p.line.id] = chosen[p.line.id] != true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(p.itemName.bi())
                                    Text(
                                        listOfNotNull(tr("Remaining %1\$s", "${Decimals.format(p.remaining)} $unit".trim()),
                                            if (p.alreadyAdded) tr("Already added") else null).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val id = listId
            val shown = picks
            if (id != null && shown != null) {
                val ticked = shown.filter { !it.alreadyAdded && chosen[it.line.id] == true }
                TextButton(onClick = { scope.launch { vm.addFromList(id, ticked); onDismiss() } }, enabled = ticked.isNotEmpty()) {
                    Text(tr("Add %1\$s", ticked.size))
                }
            }
        },
        dismissButton = { TextButton(onClick = { if (listId != null) { listId = null; picks = null } else onDismiss() }) { Text(if (listId != null) tr("Back") else tr("Cancel")) } },
    )
}

@Composable
private fun PurchaseItemPicker(vm: PurchaseEditViewModel, onCreateItem: (String) -> Unit, onDismiss: () -> Unit) {
    val q by vm.itemQuery.collectAsStateWithLifecycle()
    val items by vm.catalogue.collectAsStateWithLifecycle()
    val chosen = remember { mutableStateMapOf<String, ItemEntity>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Add items")) },
        text = {
            Column {
                OutlinedTextField(q, { vm.itemQuery.value = it }, singleLine = true, placeholder = { Text(tr("Search items"), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(items, key = { it.id }) { item ->
                        TapRow(on = item.id in chosen, onClick = { if (chosen.remove(item.id) == null) chosen[item.id] = item }) {
                            Text(item.name.bi())
                            val last = listOfNotNull(item.latestPurchasePrice?.let { tr("buy %1\$s", Decimals.rupees(it)) },
                                item.latestSellingPrice?.let { tr("sell %1\$s", Decimals.rupees(it)) }).joinToString(" · ")
                            if (last.isNotEmpty()) Text(last, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (items.isEmpty()) NoItemFound(q, vm.canAddItem) { name -> vm.itemQuery.value = ""; onDismiss(); onCreateItem(name) }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.addItems(chosen.values.toList()); vm.itemQuery.value = ""; onDismiss() },
                enabled = chosen.isNotEmpty()) { Text(tr("Add %1\$s", chosen.size)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
