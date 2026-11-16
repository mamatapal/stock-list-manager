package app.stocklist.app.feature.pdf

import app.stocklist.app.core.i18n.trMessage

import app.stocklist.app.core.i18n.trKey

import app.stocklist.app.core.i18n.tr

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.EmptyState
import app.stocklist.app.core.designsystem.LoadingState
import app.stocklist.app.core.pdf.PdfFormat
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.ApiService
import app.stocklist.app.data.remote.ExpectedProfitReportDto
import app.stocklist.app.data.remote.ItemPricesDto
import app.stocklist.app.data.remote.ProfitRowDto
import app.stocklist.app.data.remote.PurchaseReportDto
import app.stocklist.app.data.remote.SupplierTotalsDto
import app.stocklist.app.data.remote.apiCall
import app.stocklist.app.feature.catalogue.DateField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Preset periods. India's financial year runs from 1 April to 31 March. */
enum class Preset(private val englishLabel: String) {
    THIS_MONTH(trKey("This month")), LAST_MONTH(trKey("Last month")), LAST_3_MONTHS(trKey("Last 3 months")), FINANCIAL_YEAR(trKey("This financial year"));

    val label: String get() = trMessage(englishLabel)

    fun range(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        THIS_MONTH -> today.withDayOfMonth(1) to today
        LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1).let { it to it.withDayOfMonth(it.lengthOfMonth()) }
        LAST_3_MONTHS -> today.minusMonths(2).withDayOfMonth(1) to today
        FINANCIAL_YEAR -> LocalDate.of(if (today.monthValue >= 4) today.year else today.year - 1, 4, 1) to today
    }
}

sealed interface ReportState {
    data object Loading : ReportState
    data class Loaded(val value: Any) : ReportState
    data class Failed(val message: String) : ReportState
}

@HiltViewModel
class ReportsViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    val kind = MutableStateFlow(ReportKind.PURCHASES)
    val from = MutableStateFlow(Preset.THIS_MONTH.range(LocalDate.now()).first)
    val to = MutableStateFlow(LocalDate.now())
    val state = MutableStateFlow<ReportState>(ReportState.Loading)
    private var running: Job? = null

    init { load() }

    fun setKind(k: ReportKind) { kind.value = k; load() }
    fun setPreset(p: Preset) { p.range(LocalDate.now()).let { from.value = it.first; to.value = it.second }; load() }
    fun setFrom(d: LocalDate?) { d?.let { from.value = it; load() } }
    fun setTo(d: LocalDate?) { d?.let { to.value = it; load() } }

    fun load() {
        running?.cancel()
        state.value = ReportState.Loading
        val f = from.value.toString()
        val t = to.value.toString()
        running = viewModelScope.launch {
            val r: ApiResult<Any> = when (kind.value) {
                ReportKind.PURCHASES -> apiCall { api.reportPurchases(f, t) }
                ReportKind.SUPPLIER_TOTALS -> apiCall { api.reportSupplierTotals(f, t) }
                ReportKind.ITEM_PRICES -> apiCall { api.reportItemPrices(f, t) }
                ReportKind.EXPECTED_PROFIT -> apiCall { api.reportExpectedProfit(f, t) }
            }
            state.value = when (r) {
                is ApiResult.Ok -> ReportState.Loaded(r.value)
                is ApiResult.Failure -> ReportState.Failed(
                    if (r.error.isNetwork) tr("Reports are worked out on the server from every phone's purchases, so they need an internet connection. PDFs of lists, purchases, supplier and item history and expiry work offline.")
                    else r.error.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(canProfitReport: Boolean, onBack: () -> Unit, onPdf: ((PdfJob) -> Unit)?, vm: ReportsViewModel = hiltViewModel()) {
    val kind by vm.kind.collectAsStateWithLifecycle()
    val from by vm.from.collectAsStateWithLifecycle()
    val to by vm.to.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("Reports")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
            actions = {
                if (onPdf != null && state is ReportState.Loaded) {
                    TextButton(onClick = { onPdf(PdfJob.Report(kind, from, to)) }) {
                        Icon(Icons.Filled.PictureAsPdf, null); Text(" PDF", style = MaterialTheme.typography.titleMedium)
                    }
                }
            })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportKind.entries.filter { it != ReportKind.EXPECTED_PROFIT || canProfitReport }.forEach { k ->
                        FilterChip(selected = k == kind, onClick = { vm.setKind(k) }, label = { Text(trMessage(k.title)) })
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Preset.entries.forEach { p ->
                        val (pf, pt) = p.range(LocalDate.now())
                        FilterChip(selected = pf == from && pt == to, onClick = { vm.setPreset(p) }, label = { Text(p.label) })
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(tr("From"), from, vm::setFrom, Modifier.weight(1f))
                    DateField(tr("To"), to, vm::setTo, Modifier.weight(1f))
                }
                if (to.isBefore(from)) Text(tr("The end date is before the start date."), color = MaterialTheme.colorScheme.error)
            }
            when (val s = state) {
                ReportState.Loading -> item { LoadingState() }
                is ReportState.Failed -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EmptyState(s.message)
                        OutlinedButton(onClick = vm::load) { Text(tr("Try again")) }
                    }
                }
                is ReportState.Loaded -> when (val v = s.value) {
                    is PurchaseReportDto -> purchases(v)
                    is SupplierTotalsDto -> suppliers(v)
                    is ItemPricesDto -> itemPrices(v)
                    is ExpectedProfitReportDto -> profit(v)
                }
            }
            item { Text(" ") }
        }
    }
}

@Composable
private fun Summary(lines: List<Pair<String, String>>, note: String? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEach { (k, v) ->
                Row { Text(k, Modifier.weight(1f)); Text(v, fontWeight = FontWeight.SemiBold) }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun EstimateBanner(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReportRow(title: String, detail: String, amount: String, extra: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); Text(amount, fontWeight = FontWeight.SemiBold) }
        Text(detail, style = MaterialTheme.typography.bodySmall)
        extra?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

private fun LazyListScope.purchases(r: PurchaseReportDto) {
    item {
        Summary(listOfNotNull(
            tr("Purchases") to r.purchaseCount.toString(),
            tr("Total cost") to PdfFormat.money(r.totalCost),
            if (r.profitIncluded) tr("Expected profit") to PdfFormat.money(r.expectedProfit) else null,
            if (r.cancelledCount > 0) tr("Cancelled (not counted)") to r.cancelledCount.toString() else null,
        ))
    }
    if (r.profitIncluded) item { EstimateBanner(r.profitNote ?: PdfDocuments.PROFIT_NOTE) }
    if (r.rows.isEmpty()) item { EmptyState(tr("No purchases in this period.")) }
    items(r.rows, key = { it.purchaseId }) { p ->
        ReportRow(p.supplierName ?: "-",
            listOfNotNull(PdfFormat.date(p.purchaseDate), p.invoiceNo, PdfDocuments.purchaseStatus(p.status)).joinToString("  ·  "),
            PdfFormat.money(p.totalCost),
            if (r.profitIncluded && p.status != "CANCELLED") tr("Expected profit %1\$s", PdfFormat.money(p.expectedProfit)) else null)
    }
}

private fun LazyListScope.suppliers(r: SupplierTotalsDto) {
    item { Summary(listOf(tr("Suppliers") to r.rows.size.toString(), tr("Purchases") to r.purchaseCount.toString(), tr("Total cost") to PdfFormat.money(r.totalCost))) }
    if (r.profitIncluded) item { EstimateBanner(r.profitNote ?: PdfDocuments.PROFIT_NOTE) }
    if (r.rows.isEmpty()) item { EmptyState(tr("No purchases in this period.")) }
    items(r.rows, key = { it.supplierId }) { s ->
        ReportRow(s.supplierName ?: "-",
            tr("%1\$s purchase(s)  ·  %2\$s of total  ·  last %3\$s", s.purchaseCount, PdfFormat.percent(s.sharePercent), PdfFormat.date(s.lastPurchaseDate)),
            PdfFormat.money(s.totalCost), if (r.profitIncluded) tr("Expected profit %1\$s", PdfFormat.money(s.expectedProfit)) else null)
    }
}

private fun LazyListScope.itemPrices(r: ItemPricesDto) {
    if (r.rows.isEmpty()) item { EmptyState(tr("No purchases in this period.")) }
    items(r.rows, key = { it.itemId + it.unitCode }) { i ->
        ReportRow("${i.itemName} (${i.unitCode})",
            tr("Lowest %1\$s  ·  highest %2\$s  ·  average %3\$s\nLatest %4\$s on %5\$s from %6\$s  ·  bought %7\$s", PdfFormat.unitPrice(i.lowestUnitPrice), PdfFormat.unitPrice(i.highestUnitPrice), PdfFormat.unitPrice(i.averageUnitPrice), PdfFormat.unitPrice(i.latestUnitPrice), PdfFormat.date(i.latestDate), i.latestSupplierName ?: "-", PdfFormat.qty(i.totalQuantity, i.unitCode)),
            PdfFormat.money(i.totalCost),
            if (r.profitIncluded) tr("Selling price %1\$s  ·  expected profit %2\$s", PdfFormat.unitPrice(i.latestSellingPrice), PdfFormat.money(i.expectedProfit)) else null)
    }
}

private fun LazyListScope.profit(r: ExpectedProfitReportDto) {
    item { EstimateBanner(r.note.ifBlank { PdfDocuments.PROFIT_NOTE }) }
    item {
        Summary(listOf(
            tr("Purchases") to r.purchaseCount.toString(), tr("Total cost") to PdfFormat.money(r.totalCost),
            tr("Cost without selling price") to PdfFormat.money(r.unpricedCost), tr("Expected sales") to PdfFormat.money(r.expectedSales),
            tr("Expected profit") to PdfFormat.money(r.expectedProfit), tr("Expected margin") to PdfFormat.percent(r.marginPercent),
        ))
    }
    fun rows(title: String, list: List<ProfitRowDto>) {
        item { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        if (list.isEmpty()) item { Text(tr("Nothing in this period."), style = MaterialTheme.typography.bodySmall) }
        items(list, key = { title + it.id }) { p ->
            ReportRow(p.name ?: "-", tr("Cost %1\$s  ·  expected sales %2\$s  ·  margin %3\$s", PdfFormat.money(p.cost), PdfFormat.money(p.expectedSales), PdfFormat.percent(p.marginPercent)), PdfFormat.money(p.expectedProfit))
        }
    }
    rows(tr("By supplier"), r.bySupplier)
    rows(tr("By item"), r.byItem)
}
