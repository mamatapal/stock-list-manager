package app.stocklist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import app.stocklist.app.data.remote.ItemInput
import app.stocklist.app.data.remote.MeDto
import app.stocklist.app.data.remote.PersonDto
import app.stocklist.app.data.remote.SupplierInput
import app.stocklist.app.data.repository.PurchaseHeaderDraft
import app.stocklist.app.data.repository.PurchaseLineDraft
import app.stocklist.app.data.repository.ReceiveDraft
import app.stocklist.app.domain.catalogue.BuiltInUnits
import app.stocklist.app.domain.catalogue.ExpiryCalculator
import app.stocklist.app.domain.catalogue.ExpiryCalculator.Filter
import app.stocklist.app.domain.catalogue.PurchaseCalculator
import app.stocklist.app.domain.permission.Permission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

/** Batches, expiry lists, item- and supplier-wise history and names, on the phone and across two phones. */
@RunWith(AndroidJUnit4::class)
class BatchHistoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: FakeServer
    private lateinit var owner: Phone
    private lateinit var staff: Phone
    private val staffDefaults = Permission.entries.filter { it.staffDefault }.map { it.name }
    private val pcs = BuiltInUnits.PCS
    private val today = LocalDate.of(2026, 11, 14)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        server = FakeServer()
        server.people += PersonDto("owner", "Meena (owner)", "OWNER", true)
        server.people += PersonDto("staff1", "Ravi", "STAFF", true)
        owner = Phone(server, MeDto("owner", "owner", "Meena", "OWNER", "biz-1", "Shop", false, emptyList()), context)
        staff = Phone(server, MeDto("staff1", "staff1", "Ravi", "STAFF", "biz-1", "Shop", false, staffDefaults), context)
    }

    @After
    fun tearDown() { owner.close(); staff.close() }

    private suspend fun catalogue(): Triple<String, String, String> {
        val sup = owner.suppliers.save(null, SupplierInput("Medico Distributors"))
        val a = owner.items.save(null, ItemInput("Cough Syrup", null, pcs))
        val b = owner.items.save(null, ItemInput("Vitamin C", null, pcs))
        owner.sync.syncNow(); staff.sync.syncNow()
        return Triple(sup, a, b)
    }

    /** A completed purchase, received with the bill, two lines with the given expiry dates. */
    private suspend fun buy(phone: Phone, sup: String, a: String, b: String, expA: String, expB: String, date: String = "2026-11-01"): Pair<String, List<String>> {
        val la = PurchaseLineDraft(itemId = a, itemName = "Cough Syrup", unitId = pcs, quantity = "10", purchasePrice = "50",
            receivedNow = "10", batchNo = "CS-1", expiryDate = expA)
        val lb = PurchaseLineDraft(itemId = b, itemName = "Vitamin C", unitId = pcs, quantity = "20", purchasePrice = "5",
            receivedNow = "20", batchNo = "VC-9", expiryDate = expB)
        val d = LocalDate.parse(date)
        val id = phone.purchases.save(null, PurchaseHeaderDraft(sup, "INV-$date", d, d, null, ""), listOf(la, lb), true, true,
            phone.me.userId, false)
        return id to listOf(la.id, lb.id)
    }

    @Test
    fun goodsRecordedOfflineAppearInTheExpiryListsAndKeepTheirIdAfterSync() = runBlocking<Unit> {
        val (sup, a, b) = catalogue()
        server.offline = true
        val (id, _) = buy(staff, sup, a, b, "2026-11-13", "2026-12-20")
        val expired = staff.batches.observe(Filter.EXPIRED, today).first()
        assertEquals(listOf("CS-1"), expired.map { it.batchNo })
        assertTrue("waiting to sync", expired.single().pending)
        assertEquals(id, expired.single().purchaseId)
        val localId = expired.single().id

        server.offline = false
        staff.sync.syncNow()
        val after = staff.batches.observe(Filter.EXPIRED, today).first().single()
        assertEquals("the server's batch replaces the local one", localId, after.id)
        assertFalse(after.pending)

        owner.sync.syncNow()
        assertEquals(listOf("VC-9"), owner.batches.observe(Filter.WITHIN_90, today).first().map { it.batchNo })
        assertEquals(2, owner.batches.observe(Filter.ALL, today).first().size)
    }

    @Test
    fun expiryFiltersOnThePhone() = runBlocking<Unit> {
        val (sup, a, b) = catalogue()
        buy(owner, sup, a, b, "2026-11-14", "2026-11-21")       // today, +7
        buy(owner, sup, a, b, "2026-12-14", "2027-02-12", "2026-11-02") // +30, +90
        buy(owner, sup, a, b, "2027-02-13", "2026-10-01", "2026-11-03") // +91, expired 44 days ago
        val counts = owner.batches.observeCounts(today).first()
        assertEquals(1, counts[Filter.EXPIRED])
        assertEquals(2, counts[Filter.WITHIN_7])
        assertEquals(3, counts[Filter.WITHIN_30])
        assertEquals(4, counts[Filter.WITHIN_90])
        assertEquals(6, counts[Filter.ALL])
        assertEquals("soonest first", listOf("2026-11-14", "2026-11-21", "2026-12-14"),
            owner.batches.observe(Filter.WITHIN_30, today).first().map { it.expiryDate })
        assertEquals(2, owner.batches.count(Filter.WITHIN_7, today))
    }

    @Test
    fun correctedToZeroAndCancelledStockLeavesTheExpiryLists() = runBlocking<Unit> {
        val (sup, a, b) = catalogue()
        val (id, lines) = buy(owner, sup, a, b, "2026-11-10", "2026-11-12")
        val syrup = owner.db.purchases().deliveryLines(id).first { it.purchaseLineId == lines[0] }
        owner.purchases.correct(id, syrup.id, "-10", "Damaged", "owner")
        val tracked = owner.batches.observe(null, today).first().first { it.batchNo == "CS-1" }
        assertEquals("0", tracked.quantity)
        assertEquals("10", tracked.receivedQuantity)
        assertEquals(listOf("VC-9"), owner.batches.observe(Filter.EXPIRED, today).first().map { it.batchNo })

        // Only a purchase the server knows is kept as "cancelled"; an unsent one is simply discarded.
        owner.sync.syncNow()
        assertEquals("the server agrees: corrected to zero", "0",
            owner.batches.observe(null, today).first().first { it.batchNo == "CS-1" }.quantity)
        owner.purchases.deleteOrCancel(id, "Wrong supplier")
        assertTrue(owner.batches.observe(Filter.EXPIRED, today).first().isEmpty())
        assertTrue(owner.batches.observe(null, today).first().isEmpty())
        assertEquals(2, owner.batches.observe(null, today, includeCancelled = true).first().size)
    }

    @Test
    fun itemAndSupplierHistoryKeepBilledQuantitiesAndPrices() = runBlocking<Unit> {
        val (sup, a, b) = catalogue()
        val history = app.stocklist.app.data.repository.HistoryRepository(owner.db, server.apiFor { owner.me })
        buy(owner, sup, a, b, "2027-01-01", "2027-01-01", "2026-09-01")
        val l2 = PurchaseLineDraft(itemId = a, itemName = "Cough Syrup", unitId = pcs, quantity = "4", purchasePrice = "220",
            priceBasis = PurchaseCalculator.TOTAL)
        owner.purchases.save(null, PurchaseHeaderDraft(sup, "B-2", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), null, ""),
            listOf(l2), true, false, "owner", true)
        owner.purchases.save(null, PurchaseHeaderDraft(sup, "DRAFT", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), null, ""),
            listOf(l2.copy(id = java.util.UUID.randomUUID().toString())), false, false, "owner", true)
        owner.items.save(a, ItemInput("Cough Syrup 100ml (new pack)", null, pcs, "99", "120"))

        val h = history.item(a, null, null).first()
        assertEquals("drafts are not history", 2, h.rows.size)
        assertEquals(listOf("B-2", "INV-2026-09-01"), h.rows.map { it.invoiceNo })
        assertEquals("Cough Syrup", h.rows.last().itemName)
        assertEquals("50", h.rows.last().purchasePrice)
        assertEquals("10", h.rows.last().quantity)
        assertEquals("220", h.rows.first().purchasePrice)
        assertEquals("55", h.rows.first().purchaseUnitPrice)
        assertEquals(0, h.totalQuantity!!.compareTo(BigDecimal(14)))
        assertEquals(0, h.totalCost.compareTo(BigDecimal("720.00")))
        assertEquals(0, h.lowestUnitPrice!!.compareTo(BigDecimal(50)))
        assertEquals(0, h.highestUnitPrice!!.compareTo(BigDecimal(55)))
        assertEquals(listOf("B-2"), history.item(a, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).first().rows.map { it.invoiceNo })

        val s = history.supplier(sup, null, null).first()
        assertEquals(2, s.purchaseCount)
        assertEquals(0, s.totalCost.compareTo(BigDecimal("820.00")))
        assertEquals("2026-09-01", s.firstDate)
        assertEquals("2026-10-01", s.lastDate)
    }

    @Test
    fun batchesLeadBackToTheirPurchaseAndNamesShowWhoDidIt() = runBlocking<Unit> {
        val (sup, a, b) = catalogue()
        val (id, lines) = buy(staff, sup, a, b, "2027-01-01", "2027-02-01")
        staff.sync.syncNow(); owner.sync.syncNow()
        val batch = owner.batches.observe(Filter.ALL, today).first().first { it.batchNo == "CS-1" }
        assertEquals(id, batch.purchaseId)
        assertEquals(lines[0], batch.purchaseLineId)
        assertEquals("staff1", batch.receivedBy)
        assertEquals("staff1", owner.purchases.get(id)!!.createdBy)
        val names = owner.people.names().first()
        assertEquals("Ravi", names["staff1"])
        assertEquals("You", app.stocklist.app.feature.catalogue.personName(names, "owner", "owner"))
        assertEquals("A former user", app.stocklist.app.feature.catalogue.personName(names, "gone", "owner"))
    }

    @Test
    fun withoutExpiryPermissionNoBatchesAreKept() = runBlocking<Unit> {
        val (sup, a, b) = catalogue()
        buy(owner, sup, a, b, "2027-01-01", "2027-02-01")
        owner.sync.syncNow(); staff.sync.syncNow()
        assertEquals(2, staff.batches.observe(null, today).first().size)
        staff.me = staff.me.copy(permissions = staffDefaults - "EXPIRY_VIEW")
        staff.sync.syncNow()
        assertTrue(staff.batches.observe(null, today).first().isEmpty())
        assertNull(staff.db.batches().get("anything"))
    }
}
