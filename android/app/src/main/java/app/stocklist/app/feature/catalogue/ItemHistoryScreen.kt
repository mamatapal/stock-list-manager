package app.stocklist.app.feature.catalogue

import app.stocklist.app.core.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.EmptyState
import app.stocklist.app.core.designsystem.LabelValue
import app.stocklist.app.core.designsystem.LoadingState
import app.stocklist.app.core.designsystem.SectionCard
import app.stocklist.app.data.repository.HistoryRepository
import app.stocklist.app.data.repository.ItemRepository
import app.stocklist.app.domain.catalogue.Decimals
import app.stocklist.app.domain.catalogue.PurchaseCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemHistoryViewModel @Inject constructor(history: HistoryRepository, items: ItemRepository, handle: SavedStateHandle) : ViewModel() {
    private val id: String = checkNotNull(handle["id"])
    val item = items.observeOne(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val from = MutableStateFlow<LocalDate?>(null)
    val to = MutableStateFlow<LocalDate?>(null)
    val summary = combine(from, to) { f, t -> f to t }.flatMapLatest { (f, t) -> history.item(id, f, t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Every time the item was bought: date, supplier, invoice, quantity and the prices of that purchase. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemHistoryScreen(onBack: () -> Unit, onOpenPurchase: (String) -> Unit, onPdf: ((app.stocklist.app.feature.pdf.PdfJob) -> Unit)? = null,
                      vm: ItemHistoryViewModel = hiltViewModel()) {
    val item by vm.item.collectAsStateWithLifecycle()
    val s by vm.summary.collectAsStateWithLifecycle()
    val from by vm.from.collectAsStateWithLifecycle()
    val to by vm.to.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("Purchase history")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
            actions = {
                val it0 = item
                if (it0 != null && onPdf != null) IconButton(onClick = { onPdf(app.stocklist.app.feature.pdf.PdfJob.Item(it0.id, from, to)) }) { Icon(Icons.Filled.PictureAsPdf, "PDF") }
            })
    }) { padding ->
        val x = s
        if (x == null) { Column(Modifier.padding(padding)) { LoadingState() }; return@Scaffold }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(item?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    Row {
                        DateField(tr("From"), from, { vm.from.value = it }, Modifier.weight(1f), clearable = true)
                        Spacer(Modifier.width(8.dp))
                        DateField(tr("To"), to, { vm.to.value = it }, Modifier.weight(1f), clearable = true)
                    }
                    if (x.rows.isNotEmpty()) {
                        SectionCard(tr("Summary")) {
                            LabelValue(tr("Purchases"), x.purchaseCount.toString())
                            LabelValue(tr("Total bought"), x.totalQuantity?.let { "${Decimals.format(it)} ${x.unitCode.orEmpty()}" }
                                ?: tr("Bought in different units"))
                            LabelValue(tr("Total cost"), Decimals.rupees(x.totalCost.toPlainString()))
                            LabelValue(tr("Unit price paid"), if (x.lowestUnitPrice == null) "-" else
                                tr("%1\$s to %2\$s", Decimals.rupees(Decimals.money(x.lowestUnitPrice)), Decimals.rupees(Decimals.money(x.highestUnitPrice))))
                        }
                        Text(tr("Cancelled purchases are listed but not counted. Prices are as billed at the time."),
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
            if (x.rows.isEmpty()) item { EmptyState(if (from != null || to != null) tr("This item has not been bought in these dates.") else tr("This item has not been bought yet.")) }
            items(x.rows, key = { it.purchaseLineId }) { r ->
                ListItem(
                    headlineContent = { Text("${r.purchaseDate.displayDate()} · ${r.supplierName.orEmpty()}") },
                    supportingContent = {
                        Column {
                            Text("${r.quantity} ${r.unitCode}" + (r.purchasePrice?.let {
                                if (r.priceBasis == PurchaseCalculator.TOTAL) tr(" · total %1\$s (%2\$s per unit)", Decimals.rupees(it), Decimals.rupees(r.purchaseUnitPrice))
                                else " × ${Decimals.rupees(it)}"
                            } ?: tr(" · no price")) + (r.sellingPrice?.let { tr(" · sell %1\$s", Decimals.rupees(it)) } ?: ""))
                            Text(listOfNotNull(r.invoiceNo?.let { tr("Invoice %1\$s", it) }, purchaseStatusLabel(r.status)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (r.status == "CANCELLED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    trailingContent = { Text(Decimals.rupees(r.totalCost)) },
                    modifier = Modifier.fillMaxWidth().clickable { onOpenPurchase(r.purchaseId) },
                )
                HorizontalDivider()
            }
        }
    }
}
