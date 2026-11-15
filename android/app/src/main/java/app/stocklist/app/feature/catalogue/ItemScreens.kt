package app.stocklist.app.feature.catalogue

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
import androidx.compose.material3.Text
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

fun List<UnitOption>.label(id: String?): String = firstOrNull { it.id == id }?.let { "${it.name} (${it.code})" } ?: "-"
fun List<UnitOption>.code(id: String?): String = firstOrNull { it.id == id }?.code ?: ""

// ------------------------------------------------------------------------------------------- list

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemListViewModel @Inject constructor(repo: ItemRepository, units: UnitRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val category = MutableStateFlow("")
    val showInactive = MutableStateFlow(false)
    val items = combine(query, category, showInactive) { q, c, i -> Triple(q, c, i) }
        .flatMapLatest { (q, c, i) -> repo.observe(q, c, i) }
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
    val cats by vm.categories.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Items") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }, actions = {
                if (access.has(Permission.ITEM_ADD)) {
                    TextButton(onClick = onAdd) {
                        Icon(Icons.Filled.Add, null)
                        Text("Add item", style = MaterialTheme.typography.titleMedium)
                    }
                }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(q, { vm.query.value = it }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Search name or category") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = inactive, onClick = { vm.showInactive.value = !inactive }, label = { Text("Show inactive") })
                FilterChip(selected = cat.isEmpty(), onClick = { vm.category.value = "" }, label = { Text("All categories") })
                cats.forEach { c -> FilterChip(selected = cat == c, onClick = { vm.category.value = c }, label = { Text(c) }) }
            }
            val l = list
            when {
                l == null -> LoadingState()
                l.isEmpty() -> EmptyState(if (q.isBlank() && cat.isEmpty()) "No items yet." else "No item matches the search.")
                else -> LazyColumn {
                    items(l, key = { it.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.name) },
                            supportingContent = {
                                Column {
                                    Text(listOfNotNull(item.category, units.code(item.defaultUnitId),
                                        item.latestPurchasePrice?.let { "Buy ${Decimals.rupees(it)}" },
                                        item.latestSellingPrice?.let { "Sell ${Decimals.rupees(it)}" }).joinToString(" · "))
                                    SyncBadge(item.syncState, item.syncError)
                                }
                            },
                            trailingContent = { if (!item.active) Text("Inactive", color = MaterialTheme.colorScheme.error) },
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
        TopAppBar(title = { Text("Item") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                val i = item
                if (i != null && access.has(Permission.ITEM_EDIT)) IconButton(onClick = { onEdit(i.id) }) { Icon(Icons.Filled.Edit, "Edit") }
                if (i != null && access.has(Permission.ITEM_DELETE)) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
            })
    }) { padding ->
        val i = item
        if (i == null) { Column(Modifier.padding(padding)) { EmptyState("This item is no longer available.") }; return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SyncBadge(i.syncState, i.syncError)
            SectionCard(i.name) {
                LabelValue("Category", i.category)
                LabelValue("Default unit", units.label(i.defaultUnitId))
                LabelValue("Latest purchase price", i.latestPurchasePrice?.let { Decimals.rupees(it) })
                LabelValue("Latest selling price", i.latestSellingPrice?.let { Decimals.rupees(it) })
                LabelValue("Last purchased", i.lastPurchaseDate.displayDate())
                LabelValue("Preferred supplier", suppliers.firstOrNull { it.id == i.preferredSupplierId }?.name)
                LabelValue("Notes", i.notes)
                LabelValue("Status", if (i.active) "Active" else "Inactive")
                LabelValue("Added by", personName(names, i.createdBy, vm.me))
                LabelValue("Last changed by", personName(names, i.updatedBy, vm.me))
            }
            if (access.canViewPurchases) {
                OutlinedButton(onClick = { onHistory(i.id) }, modifier = Modifier.fillMaxWidth()) { Text("Purchase history of this item") }
            }
            if (access.canViewExpiry) {
                OutlinedButton(onClick = { onBatches(i.id) }, modifier = Modifier.fillMaxWidth()) { Text("Batches and expiry dates") }
            }
            if (access.has(Permission.ITEM_EDIT)) {
                OutlinedButton(onClick = { vm.setActive(!i.active) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (i.active) "Deactivate item" else "Reactivate item")
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Delete item?",
            "The item is removed from the catalogue for everyone. Existing lists keep the item name they were saved with.",
            "Delete", onConfirm = { confirmDelete = false; vm.delete(onBack) }, onDismiss = { confirmDelete = false })
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
    var name by mutableStateOf("")
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
                name = it.name; category = it.category.orEmpty(); unitId = it.defaultUnitId
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

    fun save(onDone: () -> Unit) {
        errors.clear()
        if (name.isBlank()) errors["name"] = "Item name is required"
        else if (name.trim().length > 150) errors["name"] = "At most 150 characters"
        if (category.trim().length > 80) errors["category"] = "At most 80 characters"
        fun price(field: String, v: String, label: String) = try { Decimals.parse(v, label, 4, true) } catch (e: IllegalArgumentException) {
            errors[field] = e.message ?: "Invalid"; null
        }
        val purchase = price("purchase", purchasePrice, "Purchase price")
        val selling = price("selling", sellingPrice, "Selling price")
        if (errors.isNotEmpty() || saving) return
        saving = true
        viewModelScope.launch {
            if (repo.nameInUse(name, id)) {
                errors["name"] = "Another item already has this name"
                saving = false
                return@launch
            }
            repo.save(id, ItemInput(name.trim(), category.trim().ifEmpty { null }, unitId, Decimals.format(purchase),
                Decimals.format(selling), supplierId, notes.trim().ifEmpty { null }, active))
            saving = false
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(access: Access, onDone: () -> Unit, vm: ItemEditViewModel = hiltViewModel()) {
    val units by vm.units.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var addUnit by remember { mutableStateOf(false) }
    val isNew = vm.id == null
    // Same rules the server applies: entering prices on a new item, changing the catalogue selling price later.
    val canPurchase = access.has(Permission.PURCHASE_PRICE_ENTER)
    val canSelling = if (isNew) access.has(Permission.SELLING_PRICE_ENTER) else access.has(Permission.CATALOG_SELLING_PRICE_CHANGE)
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (isNew) "Add item" else "Edit item") },
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                TextButton(onClick = { vm.save(onDone) }, enabled = vm.loaded && !vm.saving) {
                    Text("Save", style = MaterialTheme.typography.titleMedium)
                }
            })
    }) { padding ->
        if (!vm.loaded) { Column(Modifier.padding(padding)) { LoadingState() }; return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FormField(vm.name, { vm.name = it }, "Item name *", vm.errors["name"], capitalization = KeyboardCapitalization.Words)
            FormField(vm.category, { vm.category = it }, "Category", vm.errors["category"], capitalization = KeyboardCapitalization.Words)
            val suggestions = categories.filter { it.contains(vm.category.trim(), true) && !it.equals(vm.category.trim(), true) }.take(6)
            if (suggestions.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { c -> FilterChip(selected = false, onClick = { vm.category = c }, label = { Text(c) }) }
                }
            }
            Row {
                DropdownField("Default unit *", vm.unitId, units.map { Choice(it.id, "${it.name} (${it.code})") },
                    { it?.let { u -> vm.unitId = u } }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { addUnit = true }, modifier = Modifier.padding(top = 8.dp)) { Text("New unit") }
            }
            FormField(vm.purchasePrice, { vm.purchasePrice = it }, "Latest purchase price (₹)", vm.errors["purchase"],
                keyboardType = KeyboardType.Decimal, enabled = canPurchase)
            FormField(vm.sellingPrice, { vm.sellingPrice = it }, "Latest selling price (₹)", vm.errors["selling"],
                keyboardType = KeyboardType.Decimal, enabled = canSelling)
            if (!canPurchase || !canSelling) {
                Text("Price fields you are not allowed to change are locked.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownField("Preferred supplier", vm.supplierId,
                listOf(Choice(null, "None")) + suppliers.map { Choice(it.id, it.name) }, { vm.supplierId = it })
            FormField(vm.notes, { vm.notes = it }, "Notes", singleLine = false)
            ErrorText(if (vm.errors.isNotEmpty()) "Please correct the highlighted fields." else null)
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
        title = { Text("New unit") },
        text = {
            Column {
                FormField(code, { code = it }, "Short code (e.g. TRAY)", capitalization = KeyboardCapitalization.Characters)
                FormField(name, { name = it }, "Name (e.g. Tray)", capitalization = KeyboardCapitalization.Words)
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { error = onAdd(code, name); if (error == null) onDismiss() } }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
