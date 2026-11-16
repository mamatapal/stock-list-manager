package app.stocklist.app.feature.catalogue

import app.stocklist.app.core.i18n.BilingualName
import app.stocklist.app.core.designsystem.BilingualNameFields
import app.stocklist.app.core.i18n.Bilingual

import app.stocklist.app.core.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Switch
import app.stocklist.app.core.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import app.stocklist.app.core.util.Validators
import app.stocklist.app.data.local.SupplierEntity
import app.stocklist.app.data.remote.SupplierInput
import app.stocklist.app.data.repository.SupplierRepository
import app.stocklist.app.data.sync.toInput
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

// ------------------------------------------------------------------------------------------- list

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SupplierListViewModel @Inject constructor(repo: SupplierRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val showInactive = MutableStateFlow(false)
    val suppliers = combine(query, showInactive) { q, i -> q to i }
        .flatMapLatest { (q, i) -> repo.observe(q, i) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierListScreen(access: Access, onBack: () -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit,
                       vm: SupplierListViewModel = hiltViewModel()) {
    val list by vm.suppliers.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val inactive by vm.showInactive.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(tr("Suppliers")) }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Bilingual.oneLine(tr("Back"))) }
            }, actions = {
                if (access.has(Permission.SUPPLIER_ADD)) {
                    TextButton(onClick = onAdd) {
                        Icon(Icons.Filled.Add, null)
                        Text(tr("Add supplier"), style = MaterialTheme.typography.titleMedium)
                    }
                }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(q, { vm.query.value = it }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(tr("Search name, city, phone"), maxLines = 1) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp)) {
                FilterChip(selected = inactive, onClick = { vm.showInactive.value = !inactive }, label = { Text(tr("Show inactive"), maxLines = 1) })
            }
            val l = list
            when {
                l == null -> LoadingState()
                l.isEmpty() -> EmptyState(if (q.isBlank()) tr("No suppliers yet.") else tr("No supplier matches \"%1\$s\".", q))
                else -> LazyColumn {
                    items(l, key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.name) },
                            supportingContent = {
                                Column {
                                    Text(listOfNotNull(s.city, s.mobile, s.contactPerson).joinToString(" · ").ifEmpty { "-" })
                                    SyncBadge(s.syncState, s.syncError)
                                }
                            },
                            trailingContent = { if (!s.active) Text(tr("Inactive"), color = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable { onOpen(s.id) },
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
class SupplierDetailViewModel @Inject constructor(
    private val repo: SupplierRepository,
    history: app.stocklist.app.data.repository.HistoryRepository,
    people: app.stocklist.app.data.repository.PeopleRepository,
    private val session: app.stocklist.app.core.session.SessionStore,
    handle: SavedStateHandle,
) : ViewModel() {
    private val id: String = checkNotNull(handle["id"])
    val supplier = repo.observeOne(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val from = MutableStateFlow<java.time.LocalDate?>(null)
    val to = MutableStateFlow<java.time.LocalDate?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val history = combine(from, to) { f, t -> f to t }.flatMapLatest { (f, t) -> history.supplier(id, f, t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val names = people.names().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val me: String? get() = (session.state.value as? app.stocklist.app.core.session.SessionState.SignedIn)?.user?.userId
    var message by mutableStateOf<String?>(null)

    fun setActive(active: Boolean) = viewModelScope.launch {
        val s = repo.observeOne(id).first() ?: return@launch
        repo.save(id, s.toInput().copy(active = active))
        message = if (active) tr("Supplier reactivated") else tr("Supplier deactivated. It stays in old lists but is hidden from new ones.")
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repo.delete(id)
        onDone()
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierDetailScreen(access: Access, onBack: () -> Unit, onEdit: (String) -> Unit,
                         onOpenPurchase: (String) -> Unit = {}, onAllPurchases: (String) -> Unit = {},
                         onPdf: ((app.stocklist.app.feature.pdf.PdfJob) -> Unit)? = null,
                         vm: SupplierDetailViewModel = hiltViewModel()) {
    val s by vm.supplier.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val hFrom by vm.from.collectAsStateWithLifecycle()
    val hTo by vm.to.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("Supplier")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Bilingual.oneLine(tr("Back"))) } },
            actions = {
                val sup = s
                if (sup != null && onPdf != null && access.canViewPurchases) {
                    IconButton(onClick = { onPdf(app.stocklist.app.feature.pdf.PdfJob.Supplier(sup.id, hFrom, hTo)) }) { Icon(Icons.Filled.PictureAsPdf, Bilingual.oneLine(tr("Purchase report PDF"))) }
                }
                if (sup != null && access.has(Permission.SUPPLIER_EDIT)) {
                    IconButton(onClick = { onEdit(sup.id) }) { Icon(Icons.Filled.Edit, Bilingual.oneLine(tr("Edit"))) }
                }
                if (sup != null && access.has(Permission.SUPPLIER_DELETE)) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, Bilingual.oneLine(tr("Delete"))) }
                }
            })
    }) { padding ->
        val sup = s
        if (sup == null) {
            Column(Modifier.padding(padding)) { EmptyState(tr("This supplier is no longer available.")) }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SyncBadge(sup.syncState, sup.syncError)
            SectionCard(sup.name) {
                LabelValue(tr("Contact person"), sup.contactPerson)
                LabelValue(tr("Mobile"), sup.mobile)
                LabelValue(tr("Other phone"), sup.altPhone)
                LabelValue(tr("Address"), sup.address)
                LabelValue(tr("City"), sup.city)
                LabelValue(tr("GST number"), sup.gstin)
                LabelValue(tr("Notes"), sup.notes)
                LabelValue(tr("Status"), if (sup.active) tr("Active") else tr("Inactive"))
                LabelValue(tr("Added by"), personName(names, sup.createdBy, vm.me))
                LabelValue(tr("Last changed by"), personName(names, sup.updatedBy, vm.me))
            }
            if (access.canViewPurchases) {
                val h = history
                SectionCard(tr("Purchase history")) {
                    Row {
                        DateField(tr("From"), hFrom, { vm.from.value = it }, Modifier.weight(1f), clearable = true)
                        Spacer(Modifier.width(8.dp))
                        DateField(tr("To"), hTo, { vm.to.value = it }, Modifier.weight(1f), clearable = true)
                    }
                    if (h == null || h.purchases.isEmpty()) {
                        Text(if (hFrom != null || hTo != null) tr("No completed purchases from this supplier in these dates.") else tr("No completed purchases from this supplier yet."),
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        LabelValue(tr("Purchases"), h.purchaseCount.toString())
                        LabelValue(tr("Total spent"), app.stocklist.app.domain.catalogue.Decimals.rupees(h.totalCost.toPlainString()))
                        LabelValue(tr("First / last purchase"), "${h.firstDate.displayDate()} / ${h.lastDate.displayDate()}")
                        h.purchases.take(5).forEach { p ->
                            Row(Modifier.fillMaxWidth().clickable { onOpenPurchase(p.id) }.padding(vertical = 6.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("${p.purchaseDate.displayDate()} · ${p.invoiceNo?.let { "#$it" } ?: tr("no invoice no.")}")
                                    Text(purchaseStatusLabel(p.status) + tr(" · %1\$s items", p.itemCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (p.status == "CANCELLED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(app.stocklist.app.domain.catalogue.Decimals.rupees(p.totalCost))
                            }
                        }
                        TextButton(onClick = { onAllPurchases(sup.id) }) { Text(tr("See all purchases from this supplier")) }
                    }
                }
            }
            if (access.has(Permission.SUPPLIER_EDIT)) {
                OutlinedButton(onClick = { vm.setActive(!sup.active) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (sup.active) tr("Deactivate supplier") else tr("Reactivate supplier"))
                }
            }
            vm.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(tr("Delete supplier?"),
            tr("The supplier is removed from the supplier list for everyone. Lists and items that used it keep working. If you may need it again, deactivate it instead."),
            tr("Delete"), onConfirm = { confirmDelete = false; vm.delete(onBack) }, onDismiss = { confirmDelete = false })
    }
}

// ------------------------------------------------------------------------------------------- edit

@HiltViewModel
class SupplierEditViewModel @Inject constructor(
    private val repo: SupplierRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    val id: String? = handle.get<String>("id")
    var loaded by mutableStateOf(id == null)
        private set
    var name by mutableStateOf("")
    var contactPerson by mutableStateOf("")
    var mobile by mutableStateOf("")
    var altPhone by mutableStateOf("")
    var address by mutableStateOf("")
    var city by mutableStateOf("")
    var gstin by mutableStateOf("")
    var notes by mutableStateOf("")
    private var active = true
    val errors = mutableStateMapOf<String, String>()
    var saving by mutableStateOf(false)
        private set

    init {
        if (id != null) viewModelScope.launch {
            repo.observeOne(id).first()?.let {
                name = it.name; contactPerson = it.contactPerson.orEmpty(); mobile = it.mobile.orEmpty()
                altPhone = it.altPhone.orEmpty(); address = it.address.orEmpty(); city = it.city.orEmpty()
                gstin = it.gstin.orEmpty(); notes = it.notes.orEmpty(); active = it.active
            }
            loaded = true
        }
    }

    fun save(onDone: () -> Unit) {
        fun check(f: String, m: String?) { if (m != null) errors[f] = m else errors.remove(f) }
        check("name", Validators.required(name, tr("Supplier name")) ?: if (BilingualName.clean(name).length > 150) tr("At most 150 characters") else null)
        check("mobile", Validators.phone(mobile))
        check("altPhone", Validators.phone(altPhone))
        check("gstin", Validators.gstin(gstin))
        if (errors.isNotEmpty() || saving) return
        saving = true
        viewModelScope.launch {
            repo.save(id, SupplierInput(BilingualName.clean(name), contactPerson.trim().ifEmpty { null }, mobile.trim().ifEmpty { null },
                altPhone.trim().ifEmpty { null }, address.trim().ifEmpty { null }, city.trim().ifEmpty { null },
                gstin.trim().uppercase().ifEmpty { null }, notes.trim().ifEmpty { null }, active))
            saving = false
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierEditScreen(onDone: () -> Unit, vm: SupplierEditViewModel = hiltViewModel()) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (vm.id == null) tr("Add supplier") else tr("Edit supplier")) },
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Bilingual.oneLine(tr("Back"))) } },
            actions = {
                TextButton(onClick = { vm.save(onDone) }, enabled = vm.loaded && !vm.saving) {
                    Text(tr("Save"), style = MaterialTheme.typography.titleMedium)
                }
            })
    }) { padding ->
        if (!vm.loaded) { Column(Modifier.padding(padding)) { LoadingState() }; return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BilingualNameFields(vm.name, { vm.name = it }, title = tr("Supplier name"), required = true, error = vm.errors["name"])
            FormField(vm.contactPerson, { vm.contactPerson = it }, tr("Contact person"), capitalization = KeyboardCapitalization.Words)
            FormField(vm.mobile, { vm.mobile = it }, tr("Mobile number"), vm.errors["mobile"], keyboardType = KeyboardType.Phone)
            FormField(vm.altPhone, { vm.altPhone = it }, tr("Other phone"), vm.errors["altPhone"], keyboardType = KeyboardType.Phone)
            FormField(vm.address, { vm.address = it }, tr("Address"), singleLine = false)
            FormField(vm.city, { vm.city = it }, tr("City"), capitalization = KeyboardCapitalization.Words)
            FormField(vm.gstin, { vm.gstin = it }, tr("GST number"), vm.errors["gstin"], capitalization = KeyboardCapitalization.Characters)
            FormField(vm.notes, { vm.notes = it }, tr("Notes"), singleLine = false)
            ErrorText(if (vm.errors.isNotEmpty()) tr("Please correct the highlighted fields.") else null)
            Text(tr("Saved on this phone straight away, and sent to the server when online."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
