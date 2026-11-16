package app.stocklist.app.feature.catalogue

import app.stocklist.app.core.i18n.asciiDigits

import app.stocklist.app.core.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.ConfirmDialog
import app.stocklist.app.core.designsystem.EmptyState
import app.stocklist.app.core.designsystem.ErrorText
import app.stocklist.app.core.designsystem.FormField
import app.stocklist.app.core.designsystem.LabelValue
import app.stocklist.app.core.designsystem.LoadingState
import app.stocklist.app.core.designsystem.SectionCard
import app.stocklist.app.core.session.SessionState
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.data.local.ItemEntity
import app.stocklist.app.data.repository.ItemRepository
import app.stocklist.app.data.repository.ListLineDraft
import app.stocklist.app.data.repository.StockListRepository
import app.stocklist.app.data.repository.SupplierRepository
import app.stocklist.app.data.repository.UnitRepository
import app.stocklist.app.domain.catalogue.BuiltInUnits
import app.stocklist.app.domain.catalogue.Decimals
import app.stocklist.app.domain.catalogue.ListCalculator
import app.stocklist.app.domain.permission.Permission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

const val STATUS_DRAFT = "DRAFT"
const val STATUS_SAVED = "SAVED"

fun statusLabel(s: String) = if (s == STATUS_DRAFT) tr("Draft") else tr("Saved")

// ------------------------------------------------------------------------------------------- list of lists

data class ListFilter(val q: String = "", val status: String = "", val supplierId: String = "", val from: LocalDate? = null, val to: LocalDate? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StockListsViewModel @Inject constructor(repo: StockListRepository, suppliers: SupplierRepository) : ViewModel() {
    val filter = MutableStateFlow(ListFilter())
    val lists = filter.flatMapLatest { f ->
        repo.observe(f.q, f.status, f.supplierId, f.from?.toString().orEmpty(), f.to?.toString().orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val suppliers = suppliers.observe("", true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockListsScreen(access: Access, onBack: () -> Unit, onNew: () -> Unit, onOpen: (String) -> Unit,
                     vm: StockListsViewModel = hiltViewModel()) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val f by vm.filter.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    var showDates by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(tr("Out-of-stock lists")) }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) }
            }, actions = {
                if (access.has(Permission.LIST_CREATE)) {
                    TextButton(onClick = onNew) {
                        Icon(Icons.Filled.Add, null)
                        Text(tr("New list"), style = MaterialTheme.typography.titleMedium)
                    }
                }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(f.q, { vm.filter.value = f.copy(q = it) }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(tr("Search title, notes or item")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to tr("All"), STATUS_DRAFT to tr("Drafts"), STATUS_SAVED to tr("Saved")).forEach { (v, l) ->
                    FilterChip(selected = f.status == v, onClick = { vm.filter.value = f.copy(status = v) }, label = { Text(l) })
                }
                FilterChip(selected = f.from != null || f.to != null || showDates, onClick = { showDates = !showDates }, label = { Text(tr("Dates")) })
            }
            DropdownField(tr("Supplier"), f.supplierId, listOf(Choice("", tr("All suppliers"))) + suppliers.map { Choice(it.id, it.name) },
                { vm.filter.value = f.copy(supplierId = it.orEmpty()) }, Modifier.padding(horizontal = 16.dp))
            if (showDates) {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    DateField(tr("From"), f.from, { vm.filter.value = f.copy(from = it) }, Modifier.weight(1f), clearable = true)
                    Spacer(Modifier.width(8.dp))
                    DateField(tr("To"), f.to, { vm.filter.value = f.copy(to = it) }, Modifier.weight(1f), clearable = true)
                }
            }
            val l = lists
            when {
                l == null -> LoadingState()
                l.isEmpty() -> EmptyState(if (f == ListFilter()) tr("No lists yet.") else tr("No list matches these filters."))
                else -> LazyColumn {
                    items(l, key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.title) },
                            supportingContent = {
                                Column {
                                    Text(listOfNotNull(s.listDate.displayDate(), suppliers.firstOrNull { it.id == s.supplierId }?.name,
                                        statusLabel(s.status)).joinToString(" · "))
                                    SyncBadge(s.syncState, s.syncError)
                                }
                            },
                            trailingContent = { Text(Decimals.rupees(s.totalAmount)) },
                            modifier = Modifier.clickable { onOpen(s.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- view one list

@HiltViewModel
class StockListDetailViewModel @Inject constructor(
    private val repo: StockListRepository,
    private val session: SessionStore,
    purchases: app.stocklist.app.data.repository.PurchaseRepository,
    people: app.stocklist.app.data.repository.PeopleRepository,
    suppliers: SupplierRepository,
    units: UnitRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    private val id: String = checkNotNull(handle["id"])
    val list = repo.observeOne(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val names = people.names().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val me: String? get() = (session.state.value as? SessionState.SignedIn)?.user?.userId
    /** Requested / received / remaining per line, including goods recorded on this phone but not yet synced. */
    val progress = purchases.observeListProgress(repo.observeOne(id).map { it?.second.orEmpty() })
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val suppliers = suppliers.observe("", true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val units = units.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuiltInUnits.all)

    fun duplicate(onCreated: (String) -> Unit) = viewModelScope.launch {
        val me = (session.state.value as? SessionState.SignedIn)?.user?.userId
        onCreated(repo.duplicate(id, me))
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.delete(id); onDone() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockListDetailScreen(access: Access, onBack: () -> Unit, onEdit: (String) -> Unit, onOpenCopy: (String) -> Unit,
                          onRecordPurchase: (String) -> Unit = {}, onPdf: ((app.stocklist.app.feature.pdf.PdfJob) -> Unit)? = null,
                          vm: StockListDetailViewModel = hiltViewModel()) {
    val data by vm.list.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("List")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
            actions = {
                val d = data
                if (d != null && onPdf != null) IconButton(onClick = { onPdf(app.stocklist.app.feature.pdf.PdfJob.StockList(d.first.id)) }) { Icon(Icons.Filled.PictureAsPdf, "PDF") }
                if (d != null && access.has(Permission.LIST_CREATE)) IconButton(onClick = { vm.duplicate(onOpenCopy) }) { Icon(Icons.Filled.ContentCopy, tr("Duplicate")) }
                if (d != null && access.has(Permission.LIST_EDIT)) IconButton(onClick = { onEdit(d.first.id) }) { Icon(Icons.Filled.Edit, tr("Edit")) }
                if (d != null && access.has(Permission.LIST_DELETE)) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, tr("Delete")) }
            })
    }) { padding ->
        val d = data
        if (d == null) { Column(Modifier.padding(padding)) { EmptyState(tr("This list is no longer available.")) }; return@Scaffold }
        val (list, lines) = d
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SyncBadge(list.syncState, list.syncError)
                SectionCard(list.title) {
                    LabelValue(tr("Date"), list.listDate.displayDate())
                    LabelValue(tr("Supplier"), suppliers.firstOrNull { it.id == list.supplierId }?.name)
                    LabelValue(tr("Status"), statusLabel(list.status))
                    LabelValue(tr("Notes"), list.notes)
                    LabelValue(tr("Items"), lines.size.toString())
                    LabelValue(tr("Estimated total"), Decimals.rupees(list.totalAmount))
                    val overall = app.stocklist.app.domain.catalogue.ReceivingCalculator.overall(
                        lines.mapNotNull { progress[it.id]?.progress?.status })
                    LabelValue(tr("Receiving"), app.stocklist.app.domain.catalogue.ReceivingCalculator.label(overall))
                    LabelValue(tr("Created by"), personName(names, list.createdBy, vm.me))
                    LabelValue(tr("Last changed by"), personName(names, list.updatedBy, vm.me))
                }
                if (access.has(Permission.PURCHASE_RECORD) && list.status == STATUS_SAVED &&
                    lines.any { l -> l.itemId != null && (progress[l.id]?.progress?.remaining?.signum() ?: 1) > 0 }) {
                    OutlinedButton(onClick = { onRecordPurchase(list.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Record purchase for what is still to come"))
                    }
                }
              }
            }
            items(lines, key = { it.id }) { l ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Text(l.itemName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(Decimals.rupees(l.lineTotal), fontWeight = FontWeight.SemiBold)
                        }
                        Text("${l.quantity} ${units.code(l.unitId)}" + (l.enteredPrice?.let {
                            if (l.priceBasis == ListCalculator.TOTAL) tr(" · total amount %1\$s", Decimals.rupees(it)) else tr(" × %1\$s per unit", Decimals.rupees(it))
                        } ?: tr(" · no price")), style = MaterialTheme.typography.bodySmall)
                        l.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        progress[l.id]?.let { p ->
                            val pr = p.progress
                            Text(tr("Requested %1\$s · received %2\$s · ", l.quantity, Decimals.format(pr.received)) +
                                (if (pr.overBy.signum() > 0) tr("over by %1\$s", Decimals.format(pr.overBy)) else tr("remaining %1\$s", Decimals.format(pr.remaining))) +
                                " · ${app.stocklist.app.domain.catalogue.ReceivingCalculator.label(pr.status)}",
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            if (p.unitMismatch) {
                                Text(tr("A purchase for this item used another unit and is not counted here"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (l.itemId == null && access.has(Permission.PURCHASE_RECORD)) {
                            Text(tr("Not linked to a catalogue item, so it cannot be added to a purchase automatically"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(tr("Delete list?"), tr("The list is deleted for everyone in the business."), tr("Delete"),
            onConfirm = { confirmDelete = false; vm.delete(onBack) }, onDismiss = { confirmDelete = false })
    }
}

// ------------------------------------------------------------------------------------------- create / edit

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StockListEditViewModel @Inject constructor(
    private val repo: StockListRepository,
    private val session: SessionStore,
    items: ItemRepository,
    suppliers: SupplierRepository,
    units: UnitRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    val id: String? = handle.get<String>("id")
    val units = units.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuiltInUnits.all)
    val suppliers = suppliers.observe("", false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val itemQuery = MutableStateFlow("")
    val catalogue = itemQuery.flatMapLatest { items.observe(it, "", false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var loaded by mutableStateOf(id == null)
        private set
    var title by mutableStateOf(tr("Out-of-stock list %1\$s", LocalDate.now().display()))
    var date by mutableStateOf(LocalDate.now())
    var supplierId by mutableStateOf<String?>(null)
    var notes by mutableStateOf("")
    private var status = STATUS_DRAFT
    val lines = mutableStateListOf<ListLineDraft>()
    var error by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)

    init {
        if (id != null) viewModelScope.launch {
            repo.get(id)?.let { l ->
                title = l.title; date = LocalDate.parse(l.listDate); supplierId = l.supplierId; notes = l.notes.orEmpty(); status = l.status
                lines += repo.lines(id).map {
                    ListLineDraft(it.id, it.itemId, it.itemName, it.description.orEmpty(), it.quantity, it.unitId, it.priceBasis, it.enteredPrice.orEmpty())
                }
            }
            loaded = true
        }
    }

    val isDraft get() = status == STATUS_DRAFT

    fun addItems(picked: List<ItemEntity>) {
        picked.forEach { item ->
            lines += ListLineDraft(itemId = item.id, itemName = item.name, quantity = "1", unitId = item.defaultUnitId,
                enteredPrice = item.latestPurchasePrice.orEmpty())
        }
        dirty = true
    }

    fun addFreeLine(name: String) {
        lines += ListLineDraft(itemName = name.trim(), quantity = "1")
        dirty = true
    }

    fun update(index: Int, change: (ListLineDraft) -> ListLineDraft) {
        lines[index] = change(lines[index]); dirty = true
    }

    fun remove(index: Int) { lines.removeAt(index); dirty = true }

    /**
     * Switching price basis keeps the money consistent instead of silently changing the total:
     * per unit -> total multiplies once; total -> per unit divides once.
     */
    fun switchBasis(index: Int, basis: String) = update(index) { l ->
        if (l.priceBasis == basis) return@update l
        val qty = Decimals.tryParse(l.quantity, 3, false)
        val price = Decimals.tryParse(l.enteredPrice, 4, true)
        val converted = when {
            qty == null || price == null -> l.enteredPrice
            basis == ListCalculator.TOTAL -> Decimals.money(ListCalculator.lineTotal(ListCalculator.PER_UNIT, qty, price))
            else -> Decimals.format(ListCalculator.effectiveUnitPrice(ListCalculator.TOTAL, qty, price))
        }
        l.copy(priceBasis = basis, enteredPrice = converted.orEmpty())
    }

    fun lineTotal(l: ListLineDraft): BigDecimal? {
        val qty = Decimals.tryParse(l.quantity, 3, false) ?: return null
        val price = Decimals.tryParse(l.enteredPrice, ListCalculator.priceScale(l.priceBasis), true) ?: return null
        return ListCalculator.lineTotal(l.priceBasis, qty, price)
    }

    fun unitPrice(l: ListLineDraft): BigDecimal? {
        val qty = Decimals.tryParse(l.quantity, 3, false) ?: return null
        val price = Decimals.tryParse(l.enteredPrice, ListCalculator.priceScale(l.priceBasis), true) ?: return null
        return ListCalculator.effectiveUnitPrice(l.priceBasis, qty, price)
    }

    fun total(): BigDecimal? = ListCalculator.listTotal(lines.map { lineTotal(it) })

    fun save(asDraft: Boolean, onDone: (String) -> Unit) {
        if (saving) return
        saving = true
        error = null
        viewModelScope.launch {
            try {
                val me = (session.state.value as? SessionState.SignedIn)?.user?.userId
                val saved = repo.save(id, title, date, supplierId, notes, if (asDraft) STATUS_DRAFT else STATUS_SAVED, lines.toList(), me)
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
fun StockListEditScreen(onBack: () -> Unit, onSaved: (String) -> Unit, vm: StockListEditViewModel = hiltViewModel()) {
    val units by vm.units.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text(if (vm.id == null) tr("New list") else tr("Edit list")) },
                navigationIcon = {
                    IconButton(onClick = { if (vm.dirty) confirmLeave = true else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) }
                },
                actions = {
                    if (vm.id == null || vm.isDraft) {
                        TextButton(onClick = { vm.save(true, onSaved) }, enabled = vm.loaded && !vm.saving) { Text(tr("Save draft")) }
                    }
                    TextButton(onClick = { vm.save(false, onSaved) }, enabled = vm.loaded && !vm.saving) {
                        Text(tr("Save"), style = MaterialTheme.typography.titleMedium)
                    }
                })
        },
    ) { padding ->
        if (!vm.loaded) { Column(Modifier.padding(padding)) { LoadingState() }; return@Scaffold }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              Column {
                FormField(vm.title, { vm.title = it; vm.dirty = true }, tr("List title *"))
                DateField(tr("Date"), vm.date, { it?.let { d -> vm.date = d; vm.dirty = true } }, Modifier.fillMaxWidth())
                DropdownField(tr("Supplier"), vm.supplierId, listOf(Choice(null, tr("Not decided"))) + suppliers.map { Choice(it.id, it.name) },
                    { vm.supplierId = it; vm.dirty = true })
                FormField(vm.notes, { vm.notes = it; vm.dirty = true }, tr("Notes"), singleLine = false)
              }
            }
            itemsIndexed(vm.lines, key = { _, l -> l.id }) { i, l ->
                LineEditor(vm, i, l, units.map { Choice(it.id, it.code) })
            }
            item {
              Column {
                if (vm.lines.isEmpty()) {
                    Text(tr("No items yet. Add the items that are out of stock."), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null); Text(tr("Add items"))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Estimated total (%1\$s items)", vm.lines.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(Decimals.rupees(Decimals.money(vm.total())), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                ErrorText(vm.error)
                Spacer(Modifier.heightIn(min = 48.dp))
              }
            }
        }
    }
    if (picking) ItemPickerDialog(vm, onDismiss = { picking = false })
    if (confirmLeave) {
        ConfirmDialog(tr("Leave without saving?"), tr("Your changes to this list will be lost. Use \"Save draft\" to keep them."),
            tr("Leave"), onConfirm = { confirmLeave = false; onBack() }, onDismiss = { confirmLeave = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineEditor(vm: StockListEditViewModel, i: Int, l: ListLineDraft, units: List<Choice>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(l.itemName, { v -> vm.update(i) { it.copy(itemName = v) } }, label = { Text(tr("Item")) },
                    singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.remove(i) }) { Icon(Icons.Filled.Close, tr("Remove item")) }
            }
            Row {
                OutlinedTextField(l.quantity, { v -> vm.update(i) { it.copy(quantity = asciiDigits(v)) } }, label = { Text(tr("Quantity")) },
                    singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                DropdownField(tr("Unit"), l.unitId, units, { u -> u?.let { vm.update(i) { it.copy(unitId = u) } } }, Modifier.weight(1f))
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(ListCalculator.PER_UNIT to tr("Price per unit"), ListCalculator.TOTAL to tr("Total amount")).forEachIndexed { idx, (b, label) ->
                    SegmentedButton(selected = l.priceBasis == b, onClick = { vm.switchBasis(i, b) },
                        shape = SegmentedButtonDefaults.itemShape(idx, 2)) { Text(label) }
                }
            }
            OutlinedTextField(l.enteredPrice, { v -> vm.update(i) { it.copy(enteredPrice = asciiDigits(v)) } },
                label = { Text(if (l.priceBasis == ListCalculator.TOTAL) tr("Total amount for this item (₹)") else tr("Price per unit (₹)")) },
                singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth())
            val total = vm.lineTotal(l)
            val unit = vm.unitPrice(l)
            Text(
                if (total == null) tr("Line total: -") else if (l.priceBasis == ListCalculator.TOTAL)
                    tr("Line total %1\$s (not multiplied) · %2\$s per unit", Decimals.rupees(Decimals.money(total)), Decimals.rupees(Decimals.money(unit)))
                else tr("Line total %1\$s", Decimals.rupees(Decimals.money(total))),
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(l.description, { v -> vm.update(i) { it.copy(description = v) } }, label = { Text(tr("Note for this item")) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ItemPickerDialog(vm: StockListEditViewModel, onDismiss: () -> Unit) {
    val q by vm.itemQuery.collectAsStateWithLifecycle()
    val items by vm.catalogue.collectAsStateWithLifecycle()
    val chosen = remember { androidx.compose.runtime.mutableStateMapOf<String, ItemEntity>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Add items")) },
        text = {
            Column {
                OutlinedTextField(q, { vm.itemQuery.value = it }, singleLine = true, placeholder = { Text(tr("Search items")) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(items, key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth().clickable { if (chosen.remove(item.id) == null) chosen[item.id] = item },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.id in chosen, onCheckedChange = null)
                            Column {
                                Text(item.name)
                                item.category?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
                if (q.isNotBlank() && items.none { it.name.equals(q.trim(), true) }) {
                    TextButton(onClick = { vm.addFreeLine(q); vm.itemQuery.value = ""; onDismiss() }) {
                        Text(tr("Add \"%1\$s\" without a catalogue item", q.trim()))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.addItems(chosen.values.toList())
                vm.itemQuery.value = ""
                onDismiss()
            }, enabled = chosen.isNotEmpty()) { Text(tr("Add %1\$s", chosen.size)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
