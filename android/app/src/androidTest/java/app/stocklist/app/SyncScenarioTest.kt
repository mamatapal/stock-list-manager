package app.stocklist.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import app.stocklist.app.data.local.OutboxEntity
import app.stocklist.app.data.local.SyncState
import app.stocklist.app.data.remote.ItemInput
import app.stocklist.app.data.remote.MeDto
import app.stocklist.app.data.remote.SupplierInput
import app.stocklist.app.data.repository.PurchaseHeaderDraft
import app.stocklist.app.data.repository.PurchaseLineDraft
import app.stocklist.app.data.sync.EntityTypes
import app.stocklist.app.data.sync.SyncRepository
import app.stocklist.app.data.sync.UnsentChanges
import app.stocklist.app.domain.catalogue.BuiltInUnits
import app.stocklist.app.domain.permission.Permission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Phase 5: two installations (owner on phone A, staff on phone B) sharing one server, with the internet
 * switched off, replies lost after the server saved a change, and server errors.
 */
@RunWith(AndroidJUnit4::class)
class SyncScenarioTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: FakeServer
    private lateinit var phoneA: Phone
    private lateinit var phoneB: Phone
    private val staffDefaults = Permission.entries.filter { it.staffDefault }.map { it.name }
    private val pcs = BuiltInUnits.PCS

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        server = FakeServer()
        phoneA = Phone(server, MeDto("owner", "owner", "Owner", "OWNER", "biz-1", "Shop", false, emptyList()), context)
        phoneB = Phone(server, MeDto("staff1", "staff1", "Staff", "STAFF", "biz-1", "Shop", false, staffDefaults), context)
    }

    @After
    fun tearDown() { phoneA.close(); phoneB.close() }

    private fun header(sup: String, invoice: String) = LocalDate.of(2026, 11, 14).let { PurchaseHeaderDraft(sup, invoice, it, it, null, "") }
    private fun line(item: String, qty: String) = PurchaseLineDraft(itemId = item, itemName = "Soap", unitId = pcs, quantity = qty,
        purchasePrice = "20", sellingPrice = "25", receivedNow = qty)

    private fun serverPurchases() = server.records().mapNotNull { it.purchase }.filter { !it.deleted }

    @Test
    fun ownerAndStaffShareDataWithOfflineWorkAndALostReply() = runBlocking<Unit> {
        // 1. Owner creates a supplier on phone A.
        val sup = phoneA.suppliers.save(null, SupplierInput("Kumar Traders", city = "Pune"))
        val soap = phoneA.items.save(null, ItemInput("Soap", null, pcs, "20", "25"))
        assertTrue(phoneA.sync.syncNow())

        // 2. Staff sees it on phone B after sync.
        assertTrue(phoneB.sync.syncNow())
        assertEquals("Kumar Traders", phoneB.suppliers.observe("", false).first().single { it.id == sup }.name)

        // 3. Staff records a purchase (goods received) on phone B.
        val p1 = phoneB.purchases.save(null, header(sup, "K-1"), listOf(line(soap, "10")), complete = true, receiveNow = true,
            createdBy = "staff1", showProfit = false)
        assertTrue(phoneB.sync.syncNow())

        // 4. Owner sees it on phone A.
        assertTrue(phoneA.sync.syncNow())
        val onA = phoneA.purchases.observeOne(p1).first()!!
        assertEquals("K-1", onA.purchase.invoiceNo)
        assertEquals("RECEIVED", onA.purchase.status)
        assertEquals(1, onA.deliveries.size)

        // 5. Staff goes offline and records another purchase; it is saved on the phone.
        server.offline = true
        val p2 = phoneB.purchases.save(null, header(sup, "K-2"), listOf(line(soap, "5")), true, true, "staff1", false)
        assertFalse(phoneB.sync.syncNow())
        assertEquals(1, phoneB.db.outbox().count())
        assertEquals(SyncState.PENDING, phoneB.purchases.get(p2)!!.syncState)
        assertNull(server.record(p2))

        // 6. Internet returns, but the first reply is lost after the server saved the purchase.
        server.offline = false
        server.loseResponses = 1
        phoneB.sync.syncNow()
        assertEquals("the phone did not hear back, so it keeps the change", 1, phoneB.db.outbox().count())
        assertNotNull("but the server already has it", server.record(p2))
        assertTrue(phoneB.sync.syncNow())
        assertEquals(0, phoneB.db.outbox().count())
        val opId = server.received.last { it.entityId == p2 }.opId
        assertEquals("the same change was sent twice", 2, server.received.count { it.opId == opId })

        // ... and recorded once.
        assertEquals(2, serverPurchases().size)
        assertEquals(1, server.record(p2)!!.purchase!!.deliveries.size)
        assertEquals(SyncState.SYNCED, phoneB.purchases.get(p2)!!.syncState)
        assertTrue(phoneA.sync.syncNow())
        assertEquals(listOf("K-1", "K-2"), listOf(p1, p2).map { phoneA.purchases.get(it)!!.invoiceNo })
        assertEquals(1, phoneA.purchases.observeOne(p2).first()!!.deliveries.size)
    }

    @Test
    fun serverErrorsNeverDropAPurchase() = runBlocking<Unit> {
        val sup = phoneA.suppliers.save(null, SupplierInput("Kumar Traders"))
        val soap = phoneA.items.save(null, ItemInput("Soap", null, pcs, "20", "25"))
        phoneA.sync.syncNow()
        server.retryWhen = { it.entityType == "PURCHASE" }
        val p = phoneA.purchases.save(null, header(sup, "K-9"), listOf(line(soap, "3")), true, true, "owner", true)
        val other = phoneA.suppliers.save(null, SupplierInput("Second supplier"))

        repeat(SyncRepository.MAX_SERVER_RETRIES - 1) { phoneA.sync.syncNow() }
        assertEquals("still waiting to be sent", "PENDING", phoneA.db.outbox().all().single().state)
        assertNotNull("other changes are not held up", server.record(other))
        phoneA.sync.syncNow()
        val op = phoneA.db.outbox().all().single()
        assertEquals("after five server errors it waits for the user", "FAILED", op.state)
        assertEquals(SyncRepository.MAX_SERVER_RETRIES, op.serverRetries)
        assertEquals(SyncState.FAILED, phoneA.purchases.get(p)!!.syncState)
        assertNull(server.record(p))

        server.retryWhen = null
        phoneA.sync.tryAgain(p)
        assertEquals(0, phoneA.db.outbox().count())
        assertEquals(1, serverPurchases().size)
        assertEquals(SyncState.SYNCED, phoneA.purchases.get(p)!!.syncState)
    }

    @Test
    fun editAgainstADeletionKeepMineRestoresIt() = runBlocking<Unit> {
        val sup = phoneA.suppliers.save(null, SupplierInput("Kumar Traders"))
        phoneA.sync.syncNow(); phoneB.sync.syncNow()
        server.offline = true
        phoneB.suppliers.save(sup, SupplierInput("Kumar Traders", mobile = "9800000000"))
        server.offline = false
        phoneA.suppliers.delete(sup); phoneA.sync.syncNow()

        phoneB.sync.syncNow()
        val conflict = phoneB.db.outbox().all().single()
        assertEquals("CONFLICT", conflict.state)
        assertTrue(conflict.conflictJson!!.contains("\"deleted\":true"))
        assertNotNull("the edit is not lost", phoneB.suppliers.observeOne(sup).first())

        phoneB.sync.keepMine(sup)
        assertEquals(0, phoneB.db.outbox().count())
        val back = server.record(sup)!!.supplier!!
        assertFalse(back.deleted)
        assertEquals("9800000000", back.mobile)
        phoneA.sync.syncNow()
        assertEquals("9800000000", phoneA.suppliers.observeOne(sup).first()!!.mobile)
    }

    @Test
    fun editAgainstADeletionUseTheirsRemovesIt() = runBlocking<Unit> {
        val sup = phoneA.suppliers.save(null, SupplierInput("Kumar Traders"))
        phoneA.sync.syncNow(); phoneB.sync.syncNow()
        phoneA.suppliers.delete(sup); phoneA.sync.syncNow()
        // phone B edits before it has heard of the deletion
        server.offline = true
        phoneB.suppliers.save(sup, SupplierInput("Kumar Traders", city = "Mumbai"))
        server.offline = false
        phoneB.sync.syncNow()
        assertEquals("CONFLICT", phoneB.db.outbox().all().single().state)
        phoneB.sync.useTheirs(sup)
        assertEquals(0, phoneB.db.outbox().count())
        assertNull(phoneB.suppliers.observeOne(sup).first())
        assertTrue(server.record(sup)!!.supplier!!.deleted)
    }

    @Test
    fun afterTheServerIsRestoredPhonesDownloadTheRestoredDataAgain() = runBlocking<Unit> {
        val kept = phoneA.suppliers.save(null, SupplierInput("In the backup"))
        val lost = phoneA.suppliers.save(null, SupplierInput("Made after the backup"))
        phoneA.sync.syncNow(); phoneB.sync.syncNow()
        assertEquals(2, phoneB.suppliers.observe("", true).first().size)

        // Phone A records something offline while the server is being restored.
        server.offline = true
        val unsent = phoneA.suppliers.save(null, SupplierInput("Saved offline during the restore"))
        server.offline = false
        server.restoreWithout(lost)

        assertTrue(phoneA.sync.syncNow())
        assertEquals("the unsent change is sent, the lost record is gone",
            setOf(kept, unsent), phoneA.suppliers.observe("", true).first().map { it.id }.toSet())
        assertTrue(phoneB.sync.syncNow())
        assertEquals(setOf(kept, unsent), phoneB.suppliers.observe("", true).first().map { it.id }.toSet())
        assertNotNull(server.record(unsent))
    }

    @Test
    fun unsentChangesCountPurchasesSeparately() {
        fun op(type: String) = OutboxEntity(java.util.UUID.randomUUID().toString(), type, "x", "UPSERT", true, null, 0)
        val u = UnsentChanges.of(listOf(op(EntityTypes.PURCHASE), op(EntityTypes.DELIVERY), op(EntityTypes.DELIVERY), op(EntityTypes.SUPPLIER)))
        assertEquals(UnsentChanges(1, 2, 1), u)
        assertTrue(u.hasFinancial)
        assertEquals("1 purchase change(s), 2 goods-received record(s), 1 other change(s)", u.describe())
        assertTrue(UnsentChanges.of(emptyList()).isEmpty)
        assertFalse(UnsentChanges.of(listOf(op(EntityTypes.ITEM))).hasFinancial)
    }
}
