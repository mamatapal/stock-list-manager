package app.stocklist.app.feature.catalogue

import app.stocklist.app.core.i18n.bi
import app.stocklist.app.core.i18n.BilingualName
import app.stocklist.app.core.designsystem.BilingualNameFields
import app.stocklist.app.core.i18n.Bilingual

import app.stocklist.app.core.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import app.stocklist.app.core.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import app.stocklist.app.data.local.ItemEntity
import app.stocklist.app.data.remote.ItemInput
import app.stocklist.app.data.repository.ItemRepository
import app.stocklist.app.data.repository.SupplierRepository
import app.stocklist.app.data.repository.UnitRepository
import app.stocklist.app.data.sync.toInput
import app.stocklist.app.domain.catalogue.BuiltInUnits
import app.stocklist.app.domain.catalogue.Decimals
import app.stocklist.app.domain.catalogue.UnitOption
import app.stocklist.app.domain.permission.Permission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

fun List<UnitOption>.label(id: String?): String = firstOrNull { it.id == id }?.let { "${it.displayName} (${it.code})" } ?: "-"
fun List<UnitOption>.code(id: String?): String = firstOrNull { it.id == id }?.code ?: ""

// ------------------------------------------------------------------------------------------- list

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemListViewModel @Inject constructor(repo: ItemRepository, units: UnitRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val category = MutableStateFlow("")
    val showInactive = MutableStateFlow(false)
    /** Only items that have no selling price yet. */
    val noSellPrice = MutableStateFlow(false)
    val items = combine(query, category, showInactive, noSellPrice) { q, c, i, n -> listOf(q, c, i, n) }
        .flatMapLatest { f ->
            val onlyUnpriced = f[3] as Boolean
            repo.observe(f[0] as String, f[1] as String, f[2] as Boolean).map { all ->
                if (onlyUnpriced) all.filter { it.latestSellingPrice.isNullOrBlank() } else all
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories = repo.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val units = units.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuiltInUnits.all)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(access: Access, onBack: () -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit,
                   vm: ItemListViewModel = hiltViewModel()) {
    val list by vm.items.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val cat by vm.category.collectAsStateWithLifecycle()
    val inactive by vm.showInactive.collectAsStateWithLifecycle()
    val noSell by vm.noSellPrice.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(tr("Items")) }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Bilingual.oneLine(tr("Back"))) }
            }, actions = {
                if (access.has(Permission.ITEM_ADD)) {
                    TextButton(onClick = onAdd) {
                        Icon(Icons.Filled.Add, null)
                        Text(tr("Add item"), style = MaterialTheme.typography.titleMedium)
                    }
                }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(q, { vm.query.value = it }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(tr("Search name or category"), maxLines = 1) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = inactive, onClick = { vm.showInactive.value = !inactive }, label = { Text(tr("Show inactive"), maxLines = 1) })
                FilterChip(selected = noSell, onClick = { vm.noSellPrice.value = !noSell }, label = { Text(tr("No selling price"), maxLines = 1) })
                FilterChip(selected = cat.isEmpty(), onClick = { vm.category.value = "" }, label = { Text(tr("All categories"), maxLines = 1) })
                cats.forEach { c -> FilterChip(selected = cat == c, onClick = { vm.category.value = c }, label = { Text(c.bi(), maxLines = 1) }) }
            }
            val l = list
            when {
                l == null -> LoadingState()
                l.isEmpty() -> EmptyState(if (q.isBlank() && cat.isEmpty()) tr("No items yet.") else tr("No item matches the search."))
                else -> LazyColumn {
                    items(l, key = { it.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.name.bi()) },
                            supportingContent = {
                                Column {
                                    Text(listOfNotNull(item.category?.bi(), units.code(item.defaultUnitId),
                                        item.latestPurchasePrice?.let { tr("Buy %1\$s", Decimals.rupees(it)) },
                                        item.latestSellingPrice?.let { tr("Sell %1\$s", Decimals.rupees(it)) }).joinToString(" · "))
                                    SyncBadge(item.syncState, item.syncError)
                                }
                            },
                            trailingContent = { if (!item.active) Text(tr("Inactive"), color = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable { onOpen(item.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- detail

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val repo: ItemRepository,
    suppliers: SupplierRepository,
    units: UnitRepository,
    people: app.stocklist.app.data.repository.PeopleRepository,
    private val session: app.stocklist.app.core.session.SessionStore,
    handle: SavedStateHandle,
) : ViewModel() {
    val names = people.names().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val me: String? get() = (session.state.value as? app.stocklist.app.core.session.SessionState.SignedIn)?.user?.userId
    private val id: String = checkNotNull(handle["id"])
    val item = repo.observeOne(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val units = units.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuiltInUnits.all)
    val suppliers = suppliers.observe("", true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setActive(active: Boolean) = viewModelScope.launch {
        val i = repo.observeOne(id).first() ?: return@launch
        repo.save(id, i.toInput().copy(active = active))
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.delete(id); onDone() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(access: Access, onBack: () -> Unit, onEdit: (String) -> Unit,
                     onHistory: (String) -> Unit = {}, onBatches: (String) -> Unit = {},
                     vm: ItemDetailViewModel = hiltViewModel()) {
    val names by vm.names.collectAsStateWithLifecycle()
    val item by vm.item.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("Item")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Bilingual.oneLine(tr("Back"))) } },
            actions = {
                val i = item
                if (i != null && access.has(Permission.ITEM_EDIT)) IconButton(onClick = { onEdit(i.id) }) { Icon(Icons.Filled.Edit, Bilingual.oneLine(tr("Edit"))) }
                if (i != null && access.has(Permission.ITEM_DELETE)) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, Bilingual.oneLine(tr("Delete"))) }
            })
    }) { padding ->
        val i = item
        if (i == null) { Column(Modifier.padding(padding)) { EmptyState(tr("This item is no longer available.")) }; return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SyncBadge(i.syncState, i.syncError)
            SectionCard(i.name.bi()) {
                LabelValue(tr("Category"), i.category)
                LabelValue(tr("Default unit"), units.label(i.defaultUnitId))
                LabelValue(tr("Latest purchase price"), i.latestPurchasePrice?.let { Decimals.rupees(it) })
                LabelValue(tr("Latest selling price"), i.latestSellingPrice?.let { Decimals.rupees(it) })
                LabelValue(tr("Last purchased"), i.lastPurchaseDate.displayDate())
                LabelValue(tr("Preferred supplier"), suppliers.firstOrNull { it.id == i.preferredSupplierId }?.name)
                LabelValue(tr("Notes"), i.notes?.bi())
                LabelValue(tr("Status"), if (i.active) tr("Active") else tr("Inactive"))
                LabelValue(tr("Added by"), personName(names, i.createdBy, vm.me))
                LabelValue(tr("Last changed by"), personName(names, i.updatedBy, vm.me))
            }
            if (access.canViewPurchases) {
                OutlinedButton(onClick = { onHistory(i.id) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Purchase history of this item")) }
            }
            if (access.canViewExpiry) {
                OutlinedButton(onClick = { onBatches(i.id) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Batches and expiry dates")) }
            }
            if (access.has(Permission.ITEM_EDIT)) {
                OutlinedButton(onClick = { vm.setActive(!i.active) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (i.active) tr("Deactivate item") else tr("Reactivate item"))
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(tr("Delete item?"),
            tr("The item is removed from the catalogue for everyone. Existing lists keep the item name they were saved with."),
            tr("Delete"), onConfirm = { confirmDelete = false; vm.delete(onBack) }, onDismiss = { confirmDelete = false })
    }
}

// ------------------------------------------------------------------------------------------- edit

@HiltViewModel
class ItemEditViewModel @Inject constructor(
    private val repo: ItemRepository,
    private val unitRepo: UnitRepository,
    suppliers: SupplierRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    val id: String? = handle.get<String>("id")
    val units = unitRepo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuiltInUnits.all)
    val suppliers = suppliers.observe("", false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repo.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var loaded by mutableStateOf(id == null)
        private set
    /** Opened from a purchase or list: the typed name is filled in, the short form is shown, and the new item is handed back. */
    val returning: Boolean = handle.get<Boolean>("return") == true
    var name by mutableStateOf(if (id == null) handle.get<String>("name").orEmpty() else "")
    var category by mutableStateOf("")
    var unitId by mutableStateOf(BuiltInUnits.PCS)
    var purchasePrice by mutableStateOf("")
    var sellingPrice by mutableStateOf("")
    var supplierId by mutableStateOf<String?>(null)
    var notes by mutableStateOf("")
    private var active = true
    private var original: ItemEntity? = null
    val errors = mutableStateMapOf<String, String>()
    var saving by mutableStateOf(false)
        private set

    init {
        if (id != null) viewModelScope.launch {
            repo.observeOne(id).first()?.let {
                original = it
                name = BilingualName.withBothSpellings(it.name); category = BilingualName.withBothSpellings(it.category.orEmpty()); unitId = it.defaultUnitId
                purchasePrice = it.latestPurchasePrice.orEmpty(); sellingPrice = it.latestSellingPrice.orEmpty()
                supplierId = it.preferredSupplierId; notes = it.notes.orEmpty(); active = it.active
            }
            loaded = true
        }
    }

    /** Creates a business-specific unit and selects it. Returns an error message, or null when it worked. */
    suspend fun addUnit(code: String, unitName: String): String? = try {
        unitId = unitRepo.create(code, unitName)
        null
    } catch (e: IllegalArgumentException) {
        e.message
    }

    fun save(onDone: () -> Unit, onCreated: (String) -> Unit = {}) {
        errors.clear()
        if (name.isBlank()) errors["name"] = tr("Item name is required")
        else if (BilingualName.clean(name).length > 150) errors["name"] = tr("At most 150 characters")
        if (BilingualName.clean(category).length > 80) errors["category"] = tr("At most 80 characters")
        fun price(field: String, v: String, label: String) = try { Decimals.parse(v, label, 4, true) } catch (e: IllegalArgumentException) {
            errors[field] = e.message ?: tr("Invalid"); null
        }
        val purchase = price("purchase", purchasePrice, tr("Purchase price"))
        val selling = price("selling", sellingPrice, tr("Selling price"))
        if (errors.isNotEmpty() || saving) return
        saving = true
        viewModelScope.launch {
            if (repo.nameInUse(name, id)) {
                errors["name"] = tr("Another item already has this name")
                saving = false
                return@launch
            }
            val savedId = repo.save(id, ItemInput(BilingualName.clean(name), BilingualName.clean(category).ifEmpty { null }, unitId, Decimals.format(purchase),
                Decimals.format(selling), supplierId, notes.trim().ifEmpty { null }, active))
            saving = false
            if (id == null) onCreated(savedId)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(access: Access, onDone: () -> Unit, onCreated: (String) -> Unit = {}, vm: ItemEditViewModel = hiltViewModel()) {
    val units by vm.units.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var addUnit by remember { mutableStateOf(false) }
    val isNew = vm.id == null
    // Same rules the server applies: entering prices on a new item, changing the catalogue selling price later.
    val canPurchase = access.has(Permission.PURCHASE_PRICE_ENTER)
    val canSelling = if (isNew) access.has(Permission.SELLING_PRICE_ENTER) else access.has(Permission.CATALOG_SELLING_PRICE_CHANGE)
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (isNew) tr("Add item") else tr("Edit item")) },
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Bilingual.oneLine(tr("Back"))) } },
            actions = {
                TextButton(onClick = { vm.save(onDone, onCreated) }, enabled = vm.loaded && !vm.saving) {
                    Text(tr("Save"), style = MaterialTheme.typography.titleMedium)
                }
            })
    }) { padding ->
        if (!vm.loaded) { Column(Modifier.padding(padding)) { LoadingState() }; return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BilingualNameFields(vm.name, { vm.name = it }, title = tr("Item name"), required = true, error = vm.errors["name"])
            BilingualNameFields(vm.category, { vm.category = it }, title = tr("Category"), error = vm.errors["category"])
            val typedCategory = BilingualName.key(vm.category)
            val suggestions = categories.filter { it.contains(typedCategory, true) && !it.equals(vm.category.trim(), true) }.take(6)
            if (suggestions.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { c -> FilterChip(selected = false, onClick = { vm.category = c }, label = { Text(c.bi(), maxLines = 1) }) }
                }
            }
            Row {
                DropdownField(tr("Default unit *"), vm.unitId, units.map { Choice(it.id, "${it.displayName} (${it.code})") },
                    { it?.let { u -> vm.unitId = u } }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { addUnit = true }, modifier = Modifier.padding(top = 8.dp)) { Text(tr("New unit")) }
            }
            FormField(vm.purchasePrice, { vm.purchasePrice = it }, tr("Latest purchase price (₹)"), vm.errors["purchase"],
                keyboardType = KeyboardType.Decimal, enabled = canPurchase)
            FormField(vm.sellingPrice, { vm.sellingPrice = it }, tr("Latest selling price (₹)"), vm.errors["selling"],
                keyboardType = KeyboardType.Decimal, enabled = canSelling)
            if (!canPurchase || !canSelling) {
                Text(tr("Price fields you are not allowed to change are locked."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!vm.returning) {
                DropdownField(tr("Preferred supplier"), vm.supplierId,
                    listOf(Choice(null, tr("None"))) + suppliers.map { Choice(it.id, it.name.bi()) }, { vm.supplierId = it })
                FormField(vm.notes, { vm.notes = it }, tr("Notes"), singleLine = false)
            }
            ErrorText(if (vm.errors.isNotEmpty()) tr("Please correct the highlighted fields.") else null)
        }
    }
    if (addUnit) NewUnitDialog(onAdd = vm::addUnit, onDismiss = { addUnit = false })
}

@Composable
fun NewUnitDialog(onAdd: suspend (String, String) -> String?, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("New unit")) },
        text = {
            Column {
                FormField(code, { code = it }, tr("Short code (e.g. TRAY)"), capitalization = KeyboardCapitalization.Characters)
                BilingualNameFields(name, { name = it }, title = tr("Name (e.g. Tray)"))
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { error = onAdd(code, name); if (error == null) onDismiss() } }) { Text(tr("Add")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
