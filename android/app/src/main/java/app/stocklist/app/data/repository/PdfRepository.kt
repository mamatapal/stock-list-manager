package app.stocklist.app.data.repository

import app.stocklist.app.core.i18n.bi
import app.stocklist.app.core.i18n.tr

import android.content.Context
import app.stocklist.app.core.pdf.BusinessInfo
import app.stocklist.app.core.pdf.ReportDoc
import app.stocklist.app.core.pdf.ReportPdf
import app.stocklist.app.core.session.SessionState
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.data.local.AppDatabase
import app.stocklist.app.data.local.LocalMeta
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.ApiService
import app.stocklist.app.data.remote.BusinessDto
import app.stocklist.app.data.remote.MeDto
import app.stocklist.app.data.remote.apiCall
import app.stocklist.app.domain.catalogue.BuiltInUnits
import app.stocklist.app.domain.permission.Permission
import app.stocklist.app.feature.pdf.PdfContext
import app.stocklist.app.feature.pdf.PdfDocuments
import app.stocklist.app.feature.pdf.PdfJob
import app.stocklist.app.feature.pdf.ReportKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

class PdfException(message: String) : Exception(message)

/** A PDF written to the app's private cache, ready to preview, save, print or share. */
data class PdfFile(val file: File, val title: String, val fileName: String, val pageCount: Int)

/**
 * Makes PDFs. Lists, purchases, supplier/item/date-range reports and expiry reports come from the phone's own
 * database (which only ever holds what this user may see), so they work offline. The business reports come from
 * the server, which checks the permissions itself.
 */
@Singleton
class PdfRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val api: ApiService,
    private val session: SessionStore,
    private val json: Json,
) {
    private fun user(): MeDto = (session.state.value as? SessionState.SignedIn)?.user
        ?: throw PdfException(tr("Sign in again to make PDFs."))

    private fun MeDto.has(p: Permission) = isOwner || p.name in permissions

    /** Business details for the page header; cached so PDFs made offline still have them. */
    suspend fun business(me: MeDto): BusinessInfo {
        val fresh = withTimeoutOrNull(4_000) { (apiCall { api.business() } as? ApiResult.Ok)?.value }
        if (fresh != null) db.meta().put(LocalMeta(META_BUSINESS, json.encodeToString(BusinessDto.serializer(), fresh)))
        val b = fresh ?: db.meta().get(META_BUSINESS)?.let { runCatching { json.decodeFromString(BusinessDto.serializer(), it) }.getOrNull() }
        if (b == null || b.id != me.businessId) return BusinessInfo(me.businessName.bi())
        return BusinessInfo(b.name.bi(), listOf(
            b.address.bi(),
            listOf(b.city.bi(), listOf(b.state.bi(), b.pinCode).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(", "),
            listOf(b.phone.takeIf { it.isNotBlank() }?.let { tr("Phone %1\$s", it) }, b.email.takeIf { it.isNotBlank() }).filterNotNull().joinToString("  ·  "),
            b.gstin?.takeIf { it.isNotBlank() }?.let { tr("GSTIN %1\$s", it) }.orEmpty(),
        ).filter { it.isNotBlank() })
    }

    suspend fun context(me: MeDto = user()): PdfContext = PdfContext(
        business = business(me),
        generatedBy = me.fullName.bi(),
        generatedAt = System.currentTimeMillis(),
        people = db.people().observeAll().first().associate { it.id to it.fullName.bi() },
        showProfit = me.has(Permission.EXPECTED_PROFIT_VIEW),
    )

    suspend fun build(job: PdfJob): ReportDoc {
        val me = user()
        if (!me.has(Permission.PDF_GENERATE)) throw PdfException(tr("You do not have permission to make PDFs."))
        val ctx = context(me)
        return when (job) {
            is PdfJob.StockList -> {
                val list = db.lists().get(job.id)?.takeIf { !it.deleted } ?: throw PdfException(tr("This list is no longer available."))
                val units = BuiltInUnits.all.associate { it.id to it.code } + db.units().observeCustom().first().associate { it.id to it.code }
                PdfDocuments.stockList(ctx, list, db.lists().lines(list.id), list.supplierId?.let { db.suppliers().get(it) }, units)
            }
            is PdfJob.Purchase -> {
                val p = db.purchases().get(job.id)?.takeIf { !it.deleted } ?: throw PdfException(tr("This purchase is no longer available."))
                if (job.sellingOnly) PdfDocuments.purchaseSellingPrices(ctx, p, db.purchases().lines(p.id))
                else PdfDocuments.purchase(ctx, p, db.purchases().lines(p.id), db.purchases().observeDeliveries(p.id).first(),
                    db.purchases().deliveryLines(p.id), db.suppliers().get(p.supplierId))
            }
            is PdfJob.Supplier -> {
                val s = db.suppliers().get(job.id) ?: throw PdfException(tr("This supplier is no longer available."))
                PdfDocuments.supplierReport(ctx, s, db.purchases().observeSupplierHistory(s.id, d(job.from), d(job.to)).first(),
                    job.from, job.to)
            }
            is PdfJob.Item -> {
                val item = db.items().get(job.id) ?: throw PdfException(tr("This item is no longer available."))
                PdfDocuments.itemReport(ctx, item, db.purchases().observeItemHistory(item.id, d(job.from), d(job.to)).first(),
                    job.from, job.to)
            }
            is PdfJob.DateRange -> PdfDocuments.dateRangeReport(ctx,
                db.purchases().observe("", "", "", d(job.from), d(job.to)).first(), job.from, job.to)
            is PdfJob.Expiry -> {
                val today = LocalDate.now()
                val (from, to) = app.stocklist.app.domain.catalogue.ExpiryCalculator.range(job.filter, today)
                PdfDocuments.expiryReport(ctx, db.batches().observe(true, false, d(from), d(to), "", "", "", "").first(),
                    job.filter, today)
            }
            is PdfJob.Report -> report(ctx, job)
        }
    }

    private suspend fun report(ctx: PdfContext, job: PdfJob.Report): ReportDoc {
        val from = job.from.toString()
        val to = job.to.toString()
        val r = when (job.kind) {
            ReportKind.PURCHASES -> apiCall { api.reportPurchases(from, to) }
            ReportKind.SUPPLIER_TOTALS -> apiCall { api.reportSupplierTotals(from, to) }
            ReportKind.ITEM_PRICES -> apiCall { api.reportItemPrices(from, to) }
            ReportKind.EXPECTED_PROFIT -> apiCall { api.reportExpectedProfit(from, to) }
        }
        val value = when (r) {
            is ApiResult.Failure -> throw PdfException(
                if (r.error.isNetwork) tr("Reports need an internet connection. Connect and try again.") else r.error.message)
            is ApiResult.Ok -> r.value
        }
        return when (value) {
            is app.stocklist.app.data.remote.PurchaseReportDto -> PdfDocuments.purchaseReport(ctx, value)
            is app.stocklist.app.data.remote.SupplierTotalsDto -> PdfDocuments.supplierTotals(ctx, value)
            is app.stocklist.app.data.remote.ItemPricesDto -> PdfDocuments.itemPrices(ctx, value)
            is app.stocklist.app.data.remote.ExpectedProfitReportDto -> PdfDocuments.expectedProfit(ctx, value)
            else -> throw PdfException(tr("Unknown report"))
        }
    }

    /** Draws the document into the app's private cache (cache/pdfs), shared only through FileProvider. */
    suspend fun render(doc: ReportDoc): PdfFile = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, PDF_DIR).apply { mkdirs() }
        // Old PDFs are only kept for a day.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val file = File(dir, doc.fileName)
        val result = file.outputStream().use { ReportPdf.write(doc, it) }
        PdfFile(file, doc.title, doc.fileName, result.pageCount)
    }

    suspend fun make(job: PdfJob): PdfFile = render(build(job))

    companion object {
        const val PDF_DIR = "pdfs"
        private const val META_BUSINESS = "business_profile"
        private fun d(x: LocalDate?) = x?.toString().orEmpty()
    }
}
