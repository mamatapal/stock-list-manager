package app.stocklist.supplier.feature.network

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.supplier.core.designsystem.ConfirmDialog
import app.stocklist.supplier.core.designsystem.ErrorText
import app.stocklist.supplier.core.designsystem.FormField
import app.stocklist.supplier.core.designsystem.LoadingButton
import app.stocklist.supplier.core.designsystem.SectionCard
import app.stocklist.supplier.core.i18n.Bilingual
import app.stocklist.supplier.core.i18n.Text
import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.core.util.ProductRules
import app.stocklist.supplier.data.remote.ApiResult
import app.stocklist.supplier.data.remote.ProductDto
import app.stocklist.supplier.data.repository.NetworkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which products the catalogue list shows. */
enum class ProductFilter { ALL, AVAILABLE, HIDDEN }

object CatalogueLists {
    /** Products matching the search (English or Hindi name, unit, pack) and the filter, sorted by name. */
    fun of(all: List<ProductDto>, query: String, filter: ProductFilter): List<ProductDto> {
        val q = query.trim().lowercase()
        return all.filter { p ->
            (filter == ProductFilter.ALL || (filter == ProductFilter.AVAILABLE) == p.active) &&
                (q.isEmpty() || listOfNotNull(p.nameEn, p.nameHi, p.unit, p.pack).any { it.lowercase().contains(q) })
        }.sortedBy { it.nameEn.lowercase() }
    }
}

@HiltViewModel
class CatalogueViewModel @Inject constructor(private val repo: NetworkRepository) : ViewModel() {
    var load by mutableStateOf<Load<List<ProductDto>>>(Load.Loading)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            when (val r = repo.products()) {
                is ApiResult.Ok -> { load = Load.Ready(r.value); error = null }
                is ApiResult.Failure -> if (load is Load.Ready) error = r.error.message else load = Load.Failed(r.error)
            }
            busy = false
        }
    }
}

@Composable
fun CatalogueScreen(onBack: () -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit, vm: CatalogueViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.refresh() }
    CatalogueContent(vm.load, vm.busy, vm.error, onBack, vm::refresh, onAdd, onOpen)
}

@Composable
fun CatalogueContent(
    load: Load<List<ProductDto>>,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ProductFilter.ALL) }
    NetworkScaffold(
        title = tr("Product catalogue"), onBack = onBack, busy = busy, onRefresh = onRefresh,
        actions = {
            IconButton(onClick = onAdd, modifier = Modifier.testTag("add")) {
                Icon(Icons.Filled.Add, contentDescription = Bilingual.oneLine(tr("Add product")))
            }
        },
    ) {
        LoadContent(load, onRetry = onRefresh) { all ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ErrorText(error)
                Text(tr("Connected shops see your available products and prices. Shops choose themselves which of their items each product matches; nothing is matched automatically."),
                    style = MaterialTheme.typography.bodySmall)
                FormField(query, { query = it }, label = tr("Search products"), modifier = Modifier.testTag("search"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProductFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = {
                                Text(when (f) {
                                    ProductFilter.ALL -> tr("All")
                                    ProductFilter.AVAILABLE -> tr("Available")
                                    ProductFilter.HIDDEN -> tr("Not available")
                                }, maxLines = 1)
                            },
                        )
                    }
                }
            }
            val shown = CatalogueLists.of(all, query, filter)
            when {
                all.isEmpty() -> app.stocklist.supplier.core.designsystem.EmptyState(
                    tr("Your catalogue is empty. Add the products you sell, with their unit and price."),
                    actionLabel = tr("Add product"), onAction = onAdd,
                )
                shown.isEmpty() -> NothingHere(tr("No products match."))
                else -> LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { p -> ProductCard(p) { onOpen(p.id) } }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(p: ProductDto, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("product-${p.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.nameEn, style = MaterialTheme.typography.titleMedium)
                    p.nameHi?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                StatusChip(
                    if (p.active) tr("Available") else tr("Not available"),
                    if (p.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Text(
                tr("%1\$s per %2\$s", NetworkText.money(p.price), p.unit) +
                    (p.pack?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ================================================================== add or change a product

@HiltViewModel
class ProductFormViewModel @Inject constructor(
    private val repo: NetworkRepository,
    saved: SavedStateHandle,
) : ViewModel() {
    /** null: a new product. */
    val id: String? = saved.get<String>("id")?.takeIf { it.isNotBlank() }

    var load by mutableStateOf<Load<ProductDto?>>(if (id == null) Load.Ready(null) else Load.Loading)
        private set
    var nameEn by mutableStateOf("")
    var nameHi by mutableStateOf("")
    var unit by mutableStateOf("")
    var pack by mutableStateOf("")
    var price by mutableStateOf("")
    var errors by mutableStateOf<ProductRules.Errors?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saved by mutableStateOf(false)
        private set

    /** Loads an existing product (the server has no single-product call; the catalogue is small). */
    fun refresh() {
        if (id == null || busy) return
        busy = true
        viewModelScope.launch {
            when (val r = repo.products()) {
                is ApiResult.Ok -> {
                    val p = r.value.firstOrNull { it.id == id }
                    if (p == null) {
                        load = Load.Failed(app.stocklist.supplier.data.remote.ApiError(404, "NOT_FOUND", tr("Not found")))
                    } else {
                        fill(p)
                    }
                }
                is ApiResult.Failure -> load = Load.Failed(r.error)
            }
            busy = false
        }
    }

    private fun fill(p: ProductDto) {
        load = Load.Ready(p)
        nameEn = p.nameEn
        nameHi = p.nameHi.orEmpty()
        unit = p.unit
        pack = p.pack.orEmpty()
        price = p.price
    }

    fun save() {
        if (busy) return
        val e = ProductRules.check(nameEn, nameHi, unit, pack, price)
        errors = e
        if (!e.none) return
        busy = true
        error = null
        viewModelScope.launch {
            when (val r = repo.saveProduct(id, ProductRules.request(nameEn, nameHi, unit, pack, price))) {
                is ApiResult.Ok -> { fill(r.value); saved = true }
                is ApiResult.Failure -> error = r.error.message
            }
            busy = false
        }
    }

    fun setActive(active: Boolean) {
        if (busy || id == null) return
        busy = true
        error = null
        viewModelScope.launch {
            when (val r = repo.setProductActive(id, active)) {
                is ApiResult.Ok -> fill(r.value)
                is ApiResult.Failure -> error = r.error.message
            }
            busy = false
        }
    }
}

@Composable
fun ProductFormScreen(onDone: () -> Unit, vm: ProductFormViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(vm.saved) { if (vm.saved) onDone() }
    ProductFormContent(
        isNew = vm.id == null,
        load = vm.load,
        fields = ProductFields(vm.nameEn, vm.nameHi, vm.unit, vm.pack, vm.price),
        onChange = { f -> vm.nameEn = f.nameEn; vm.nameHi = f.nameHi; vm.unit = f.unit; vm.pack = f.pack; vm.price = f.price },
        errors = vm.errors,
        busy = vm.busy,
        error = vm.error,
        onBack = onDone,
        onRetry = vm::refresh,
        onSave = vm::save,
        onSetActive = vm::setActive,
    )
}

data class ProductFields(val nameEn: String, val nameHi: String, val unit: String, val pack: String, val price: String)

@Composable
fun ProductFormContent(
    isNew: Boolean,
    load: Load<ProductDto?>,
    fields: ProductFields,
    onChange: (ProductFields) -> Unit,
    errors: ProductRules.Errors?,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onSetActive: (Boolean) -> Unit,
) {
    var confirmActive by rememberSaveable { mutableStateOf<Boolean?>(null) }
    confirmActive?.let { active ->
        ConfirmDialog(
            title = if (active) tr("Make this product available?") else tr("Make this product unavailable?"),
            message = if (active) {
                tr("Connected shops will see it again and can order it.")
            } else {
                tr("Shops will no longer see it or be able to order it. Orders already placed are not changed.")
            },
            confirmLabel = if (active) tr("Make available") else tr("Make unavailable"),
            onConfirm = { confirmActive = null; onSetActive(active) },
            onDismiss = { confirmActive = null },
        )
    }
    NetworkScaffold(title = if (isNew) tr("Add product") else tr("Change product"), onBack = onBack, busy = busy) {
        LoadContent(load, onRetry = onRetry) { product ->
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ErrorText(error)
                FormField(fields.nameEn, { onChange(fields.copy(nameEn = it)) }, label = tr("Product name (English)"),
                    error = errors?.nameEn, modifier = Modifier.testTag("nameEn"))
                FormField(fields.nameHi, { onChange(fields.copy(nameHi = it)) }, label = tr("Product name (Hindi, optional)"),
                    error = errors?.nameHi, modifier = Modifier.testTag("nameHi"))
                FormField(fields.unit, { onChange(fields.copy(unit = it)) }, label = tr("Unit, for example kg, box, piece"),
                    error = errors?.unit, modifier = Modifier.testTag("unit"))
                FormField(fields.pack, { onChange(fields.copy(pack = it)) }, label = tr("Pack, for example 24 pieces per box (optional)"),
                    error = errors?.pack, modifier = Modifier.testTag("pack"))
                FormField(fields.price, { onChange(fields.copy(price = it)) }, label = tr("Price per unit (₹)"),
                    error = errors?.price, keyboardType = KeyboardType.Decimal, modifier = Modifier.testTag("price"))
                Text(tr("Price changes apply to new orders only. Orders already placed keep their price."),
                    style = MaterialTheme.typography.bodySmall)
                LoadingButton(tr("Save"), loading = busy, onClick = onSave, modifier = Modifier.testTag("save"))
                if (product != null) {
                    SectionCard(tr("Availability")) {
                        Text(if (product.active) tr("Available: connected shops can see and order it.")
                        else tr("Not available: shops cannot see or order it."))
                        OutlinedButton(
                            onClick = { confirmActive = !product.active },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().testTag("toggle-active"),
                        ) { Text(if (product.active) tr("Make unavailable") else tr("Make available")) }
                    }
                }
            }
        }
    }
}
