package app.stocklist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import app.stocklist.app.data.local.SyncState
import app.stocklist.app.data.remote.ItemInput
import app.stocklist.app.data.remote.MeDto
import app.stocklist.app.data.remote.SupplierInput
import app.stocklist.app.data.repository.ListLineDraft
import app.stocklist.app.data.repository.PurchaseHeaderDraft
import app.stocklist.app.data.repository.PurchaseLineDraft
import app.stocklist.app.data.repository.ReceiveDraft
import app.stocklist.app.domain.catalogue.BuiltInUnits
import app.stocklist.app.domain.catalogue.ListCalculator
import app.stocklist.app.domain.catalogue.PurchaseCalculator
import app.stocklist.app.domain.permission.Permission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Purchases and deliveries on two phones: offline recording, partial deliveries, duplicates and snapshots. */
@RunWith(AndroidJUnit4::class)
class PurchaseSyncTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: FakeServer
    private lateinit var owner: Phone
    private lateinit var staff: Phone
    private val staffDefaults = Permission.entries.filter { it.staffDefault }.map { it.name }
    private val pcs = BuiltInUnits.PCS

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        server = FakeServer()
        owner = Phone(server, MeDto("owner", "owner", "Owner", "OWNER", "biz-1", "Shop", false, emptyList()), context)
        staff = Phone(server, MeDto("staff1", "staff1", "Staff", "STAFF", "biz-1", "Shop", false, staffDefaults), context)
    }

    @After
    fun tearDown() { owner.close(); staff.close() }

    private suspend fun setupCatalogue(): Pair<String, String> {
        val sup = owner.suppliers.save(null, SupplierInput("Kumar Traders", city = "Pune"))
        val soap = owner.items.save(null, ItemInput("Soap", null, pcs, "20", "25"))
        owner.sync.syncNow(); staff.sync.syncNow()
        return sup to soap
    }

    private fun header(sup: String, date: LocalDate = LocalDate.of(2026, 11, 10), listId: String? = null) =
        PurchaseHeaderDraft(sup, "K-1", date, date, listId, "")

    @Test
    fun completingWithGoodsReceivedIsOneOperation() = runBlocking<Unit> {
        val (sup, soap) = setupCatalogue()
        val line = PurchaseLineDraft(itemId = soap, itemName = "Soap", unitId = pcs, quantity = "100", purchasePrice = "20",
            sellingPrice = "25", receivedNow = "100", batchNo = "B-1", expiryDate = "2027-05-31")
        val id = staff.purchases.save(null, header(sup), listOf(line), complete = true, receiveNow = true, createdBy = "staff1", showProfit = false)
        assertEquals(1, staff.db.outbox().count())
        val local = staff.purchases.observeOne(id).first()!!
        assertEquals("RECEIVED", local.purchase.status)
        assertTrue(local.deliveries.single().pending)
        assertEquals("2000.00", local.purchase.totalCost)
        assertNull("no profit without permission", local.purchase.totalExpectedProfit)

        staff.sync.syncNow()
        val synced = staff.purchases.observeOne(id).first()!!
        assertEquals(SyncState.SYNCED, synced.purchase.syncState)
        assertFalse(synced.deliveries.single().pending)
        assertEquals("B-1", synced.deliveryLines.single().batchNo)
        assertEquals(1, server.record(id)!!.purchase!!.deliveries.size)
        assertNull("profit never reaches this phone", synced.purchase.totalExpectedProfit)

        owner.sync.syncNow()
        val onOwner = owner.purchases.observeOne(id).first()!!
        assertEquals("RECEIVED", onOwner.purchase.status)
        assertEquals("500.00", onOwner.purchase.totalExpectedProfit)
    }

    @Test
    fun partialDeliveriesOfflineAgainstAListThenSync() = runBlocking<Unit> {
        val (sup, soap) = setupCatalogue()
        val listLine = ListLineDraft(itemId = soap, itemName = "Soap", quantity = "100", unitId = pcs,
            priceBasis = ListCalculator.PER_UNIT, enteredPrice = "20")
        val listId = owner.lists.save(null, "Soap order", LocalDate.now(), sup, "", "SAVED", listOf(listLine), "owner")
        owner.sync.syncNow(); staff.sync.syncNow()

        server.offline = true
        val pl = PurchaseLineDraft(itemId = soap, itemName = "Soap", unitId = pcs, quantity = "100", purchasePrice = "20",
            stockListItemId = listLine.id)
        val id = staff.purchases.save(null, header(sup, listId = listId), listOf(pl), true, false, "staff1", false)
        staff.purchases.receive(id, LocalDate.of(2026, 11, 11), "", listOf(ReceiveDraft(pl.id, "60", "", "")), "staff1")
        assertEquals("PARTIAL", staff.purchases.get(id)!!.status)
        val progress = staff.purchases.observeListProgress(kotlinx.coroutines.flow.flowOf(staff.lists.lines(listId)))
            .first()[listLine.id]!!.progress
        assertEquals("100", progress.requested.toPlainString())
        assertEquals(0, progress.received.compareTo(java.math.BigDecimal(60)))
        assertEquals(0, progress.remaining.compareTo(java.math.BigDecimal(40)))

        staff.purchases.receive(id, LocalDate.of(2026, 11, 13), "", listOf(ReceiveDraft(pl.id, "40", "", "")), "staff1")
        assertEquals("RECEIVED", staff.purchases.get(id)!!.status)
        assertEquals(3, staff.db.outbox().count())

        server.offline = false
        assertTrue(staff.sync.syncNow())
        assertEquals(0, staff.db.outbox().count())
        val onServer = server.record(id)!!.purchase!!
        assertEquals("RECEIVED", onServer.status)
        assertEquals("each delivery kept separately", 2, onServer.deliveries.size)
        assertEquals("100", onServer.lines.single().quantity)

        owner.sync.syncNow()
        val onOwner = owner.purchases.observeOne(id).first()!!
        assertEquals(2, onOwner.deliveries.size)
        assertEquals(0, onOwner.progress(onOwner.lines.single()).remaining.signum())
        assertEquals("the requested quantity never changes", "100", owner.lists.lines(listId).single().quantity)
    }

    @Test
    fun aDeliverySentAgainIsRecordedOnce() = runBlocking<Unit> {
        val (sup, soap) = setupCatalogue()
        val pl = PurchaseLineDraft(itemId = soap, itemName = "Soap", unitId = pcs, quantity = "10", purchasePrice = "20")
        val id = owner.purchases.save(null, header(sup), listOf(pl), true, false, "owner", true)
        owner.sync.syncNow()
        owner.purchases.receive(id, LocalDate.now(), "", listOf(ReceiveDraft(pl.id, "4", "", "")), "owner")
        owner.sync.syncNow()
        val deliveryOp = server.received.last()
        // The phone lost the answer and sends the delivery again under a new operation id.
        server.apiFor { owner.me }.syncPush(app.stocklist.app.data.remote.PushRequest(listOf(deliveryOp.copy(opId = java.util.UUID.randomUUID().toString()))))
        assertEquals(1, server.record(id)!!.purchase!!.deliveries.size)
        assertEquals("PARTIAL", server.record(id)!!.purchase!!.status)
    }

    @Test
    fun purchaseHistoryKeepsItsOwnItemNamesAndPrices() = runBlocking<Unit> {
        val (sup, soap) = setupCatalogue()
        val pl = PurchaseLineDraft(itemId = soap, itemName = "Soap", unitId = pcs, quantity = "10", purchasePrice = "18", sellingPrice = "24")
        val id = owner.purchases.save(null, header(sup), listOf(pl), true, false, "owner", true)
        owner.sync.syncNow()
        owner.items.save(soap, ItemInput("Lux Soap 100g", null, pcs, "22", "30"))
        owner.sync.syncNow()
        val line = owner.purchases.lines(id).single()
        assertEquals("Soap", line.itemName)
        assertEquals("18", line.purchasePrice)
        assertEquals("24", line.sellingPrice)
        assertEquals("180.00", line.totalCost)
    }

    @Test
    fun theRulesHoldOnThePhoneToo() = runBlocking<Unit> {
        val (sup, soap) = setupCatalogue()
        val pl = PurchaseLineDraft(itemId = soap, itemName = "Soap", unitId = pcs, quantity = "10", purchasePrice = "20",
            priceBasis = PurchaseCalculator.PER_UNIT)
        val id = owner.purchases.save(null, header(sup), listOf(pl), true, false, "owner", true)
        owner.purchases.receive(id, LocalDate.now(), "", listOf(ReceiveDraft(pl.id, "6", "", "")), "owner")
        // billed below received, removing a received line, going back to draft
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { owner.purchases.save(id, header(sup), listOf(pl.copy(quantity = "5")), true, false, "owner", true) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { owner.purchases.save(id, header(sup), emptyList(), true, false, "owner", true) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { owner.purchases.save(id, header(sup), listOf(pl), false, false, "owner", true) }
        }
        // a correction cannot make the received quantity negative, and needs a reason
        val receipt = owner.db.purchases().deliveryLines(id).single()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { owner.purchases.correct(id, receipt.id, "-7", "x", "owner") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { owner.purchases.correct(id, receipt.id, "-1", " ", "owner") } }
        owner.purchases.correct(id, receipt.id, "-1", "Counted wrong", "owner")
        assertEquals(0, owner.purchases.received(id)[pl.id]!!.compareTo(java.math.BigDecimal(5)))
        // a total amount is not multiplied by the quantity
        val total = PurchaseLineDraft(itemId = soap, itemName = "Soap", unitId = pcs, quantity = "1.5", purchasePrice = "270",
            priceBasis = PurchaseCalculator.TOTAL)
        val id2 = owner.purchases.save(null, header(sup), listOf(total), false, false, "owner", true)
        assertEquals("270.00", owner.purchases.get(id2)!!.totalCost)
        // cancelling needs a reason
        assertThrows(IllegalArgumentException::class.java) { runBlocking { owner.purchases.deleteOrCancel(id, " ") } }
    }

    @Test
    fun cancellingKeepsThePurchaseAndStaffCannotDoIt() = runBlocking<Unit> {
        val (sup, soap) = setupCatalogue()
        val pl = PurchaseLineDraft(itemId = soap, itemName = "Soap", unitId = pcs, quantity = "2", purchasePrice = "20")
        val id = staff.purchases.save(null, header(sup), listOf(pl), true, false, "staff1", false)
        staff.sync.syncNow()
        staff.purchases.deleteOrCancel(id, "Wrong bill")
        staff.sync.syncNow()
        assertEquals("staff lack PURCHASE_DELETE: the server refuses", "PENDING", server.record(id)!!.purchase!!.status)
        assertEquals(SyncState.FAILED, staff.purchases.get(id)!!.syncState)

        owner.sync.syncNow()
        owner.purchases.deleteOrCancel(id, "Wrong bill")
        owner.sync.syncNow()
        assertEquals("CANCELLED", server.record(id)!!.purchase!!.status)
        assertEquals("CANCELLED", owner.purchases.get(id)!!.status)
    }
}
