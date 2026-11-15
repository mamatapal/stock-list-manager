package app.stocklist.app.data.repository

import app.stocklist.app.data.local.AppDatabase
import app.stocklist.app.data.local.BatchEntity
import app.stocklist.app.data.local.ItemHistoryRow
import app.stocklist.app.data.local.PurchaseEntity
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.ApiService
import app.stocklist.app.data.remote.RevisionDto
import app.stocklist.app.data.remote.apiCall
import app.stocklist.app.domain.catalogue.ExpiryCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Batch tracking and expiry, read from the phone's database so it works offline. */
@Singleton
class BatchRepository @Inject constructor(private val db: AppDatabase) {
    fun observe(
        expiry: ExpiryCalculator.Filter?, today: LocalDate, q: String = "", itemId: String = "",
        receivedFrom: LocalDate? = null, receivedTo: LocalDate? = null, includeCancelled: Boolean = false,
    ): Flow<List<BatchEntity>> {
        val (from, to) = expiry?.let { ExpiryCalculator.range(it, today) } ?: (null to null)
        return db.batches().observe(expiry != null, includeCancelled && expiry == null, from?.toString().orEmpty(),
            to?.toString().orEmpty(), itemId, receivedFrom?.toString().orEmpty(), receivedTo?.toString().orEmpty(), q.trim())
    }

    fun observeOne(id: String) = db.batches().observeOne(id)
    fun observeForPurchase(purchaseId: String) = db.batches().observeForPurchase(purchaseId)

    /** Number of batches in each expiry list, for the filter chips and the dashboard alert. */
    fun observeCounts(today: LocalDate): Flow<Map<ExpiryCalculator.Filter, Int>> {
        val flows = ExpiryCalculator.Filter.entries.map { f ->
            val (from, to) = ExpiryCalculator.range(f, today)
            db.batches().observeCount(from?.toString().orEmpty(), to?.toString().orEmpty()).map { f to it }
        }
        return combine(flows) { pairs -> pairs.toMap() }
    }

    suspend fun count(filter: ExpiryCalculator.Filter, today: LocalDate): Int {
        val (from, to) = ExpiryCalculator.range(filter, today)
        return db.batches().count(from?.toString().orEmpty(), to?.toString().orEmpty())
    }
}

data class ItemHistorySummary(
    val rows: List<ItemHistoryRow>,
    val purchaseCount: Int,
    /** Null when the purchases used more than one unit (quantities in different units are not added up). */
    val totalQuantity: BigDecimal?,
    val unitCode: String?,
    val totalCost: BigDecimal,
    val lowestUnitPrice: BigDecimal?,
    val highestUnitPrice: BigDecimal?,
)

data class SupplierHistorySummary(
    val purchases: List<PurchaseEntity>,
    val purchaseCount: Int,
    val totalCost: BigDecimal,
    val firstDate: String?,
    val lastDate: String?,
)

/**
 * Supplier-wise and item-wise purchase history from the phone's database. Same rules as the server: drafts are
 * not history; cancelled purchases are listed but not counted; quantities and prices are the purchase lines'
 * own snapshots.
 */
@Singleton
class HistoryRepository @Inject constructor(private val db: AppDatabase, private val api: ApiService) {

    fun item(itemId: String, from: LocalDate?, to: LocalDate?): Flow<ItemHistorySummary> =
        db.purchases().observeItemHistory(itemId, from?.toString().orEmpty(), to?.toString().orEmpty()).map { rows ->
            val counted = rows.filter { it.status != "CANCELLED" }
            val oneUnit = counted.map { it.unitId }.distinct().size <= 1
            val prices = counted.mapNotNull { it.purchaseUnitPrice?.let(::BigDecimal) }
            ItemHistorySummary(
                rows, counted.map { it.purchaseId }.distinct().size,
                if (oneUnit) counted.fold(BigDecimal.ZERO) { a, r -> a.add(BigDecimal(r.quantity)) } else null,
                counted.firstOrNull()?.unitCode,
                counted.fold(BigDecimal.ZERO) { a, r -> r.totalCost?.let { a.add(BigDecimal(it)) } ?: a },
                prices.minOrNull(), prices.maxOrNull(),
            )
        }

    fun supplier(supplierId: String, from: LocalDate?, to: LocalDate?): Flow<SupplierHistorySummary> =
        db.purchases().observeSupplierHistory(supplierId, from?.toString().orEmpty(), to?.toString().orEmpty()).map { ps ->
            val counted = ps.filter { it.status != "CANCELLED" }
            SupplierHistorySummary(
                ps, counted.size,
                counted.fold(BigDecimal.ZERO) { a, p -> p.totalCost?.let { a.add(BigDecimal(it)) } ?: a },
                counted.minOfOrNull { it.purchaseDate }, counted.maxOfOrNull { it.purchaseDate },
            )
        }

    /** Earlier versions of a completed purchase. Needs a connection (kept only on the server). */
    suspend fun revisions(purchaseId: String): ApiResult<List<RevisionDto>> = apiCall { api.purchaseRevisions(purchaseId) }
}

/** Names of the owner and staff, for "created by" labels. */
@Singleton
class PeopleRepository @Inject constructor(private val db: AppDatabase) {
    fun names(): Flow<Map<String, String>> = db.people().observeAll().map { ps -> ps.associate { it.id to it.fullName } }
}
