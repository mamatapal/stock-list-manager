package app.stocklist.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import app.stocklist.app.data.local.AppDatabase
import app.stocklist.app.data.local.SyncState
import app.stocklist.app.data.remote.ItemInput
import app.stocklist.app.data.remote.MeDto
import app.stocklist.app.data.remote.SupplierInput
import app.stocklist.app.data.repository.ItemRepository
import app.stocklist.app.data.repository.ListLineDraft
import app.stocklist.app.data.repository.StockListRepository
import app.stocklist.app.data.repository.SupplierRepository
import app.stocklist.app.data.sync.Outbox
import app.stocklist.app.data.sync.SyncRepository
import app.stocklist.app.data.sync.SyncScheduler
import app.stocklist.app.domain.catalogue.BuiltInUnits
import app.stocklist.app.domain.catalogue.ListCalculator
import app.stocklist.app.domain.permission.Permission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** One phone: its own local database, outbox and sync engine, talking to the shared [FakeServer]. */
class Phone(server: FakeServer, var me: MeDto, context: Context) {
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val sync = SyncRepository(db, server.apiFor { me }, json) { me }
    private val scheduler = SyncScheduler(context)
    private val outbox = Outbox(db)
    val suppliers = SupplierRepository(db, outbox, json, scheduler)
    val items = ItemRepository(db, outbox, json, scheduler)
    val lists = StockListRepository(db, outbox, json, scheduler)
    val purchases = app.stocklist.app.data.repository.PurchaseRepository(db, outbox, json, scheduler)
    val batches = app.stocklist.app.data.repository.BatchRepository(db)
    val people = app.stocklist.app.data.repository.PeopleRepository(db)

    fun close() = db.close()
}

@RunWith(AndroidJUnit4::class)
class SyncAndRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: FakeServer
    private lateinit var owner: Phone
    private lateinit var staff: Phone

    private val staffDefaults = Permission.entries.filter { it.staffDefault }.map { it.name }

    private fun user(id: String, role: String, perms: List<String>) =
        MeDto(id, id, id, role, "biz-1", "Shop", false, perms)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        server = FakeServer()
        owner = Phone(server, user("owner", "OWNER", emptyList()), context)
        staff = Phone(server, user("staff1", "STAFF", staffDefaults), context)
    }

    @After
    fun tearDown() {
        owner.close(); staff.close()
    }

    private fun supplier(name: String) = SupplierInput(name, city = "Pune", mobile = "9876543210")

    // ------------------------------------------------------------------------------- offline + visibility

    @Test
    fun offlineChangeIsKeptAndReachesTheOtherPhoneAfterSync() = runBlocking {
        server.offline = true
        val id = owner.suppliers.save(null, supplier("Sharma Traders"))
        assertFalse("sync must report the server unreachable", owner.sync.syncNow())
        assertEquals(SyncState.PENDING, owner.db.suppliers().get(id)!!.syncState)
        assertEquals(1, owner.db.outbox().count())
        assertTrue(owner.sync.status.value.offline)

        server.offline = false
        assertTrue(owner.sync.syncNow())
        val synced = owner.db.suppliers().get(id)!!
        assertEquals(SyncState.SYNCED, synced.syncState)
        assertEquals(0L, synced.serverVersion)
        assertEquals(0, owner.db.outbox().count())

        staff.sync.syncNow()
        assertEquals("Sharma Traders", staff.db.suppliers().get(id)!!.name)
    }

    @Test
    fun severalEditsBeforeSyncAreSentInOrderWithTheRightVersions() = runBlocking {
        val id = owner.suppliers.save(null, supplier("A"))
        owner.suppliers.save(id, supplier("B"))
        owner.suppliers.save(id, supplier("C"))
        assertEquals(3, owner.db.outbox().count())
        owner.sync.syncNow()
        assertEquals(listOf(null, 0L, 1L), server.received.map { it.baseVersion })
        assertEquals("C", server.record(id)!!.supplier!!.name)
        assertEquals(2L, owner.db.suppliers().get(id)!!.serverVersion)
        assertEquals(0, owner.db.outbox().count())
    }

    @Test
    fun resendingTheSameOperationIsNotAppliedTwice() = runBlocking {
        val id = owner.suppliers.save(null, supplier("A"))
        owner.sync.syncNow()
        val op = server.received.single()
        // The phone lost the answer and sends the same op again: the server returns the stored result.
        val again = server.apiFor { owner.me }.syncPush(app.stocklist.app.data.remote.PushRequest(listOf(op)))
        assertEquals("APPLIED", again.results.single().status)
        assertEquals(0L, server.record(id)!!.supplier!!.version)
    }

    // ------------------------------------------------------------------------------- conflicts

    @Test
    fun simultaneousEditsBecomeAConflictAndKeepMineWins() = runBlocking {
        val id = owner.suppliers.save(null, supplier("Original"))
        owner.sync.syncNow(); staff.sync.syncNow()

        staff.suppliers.save(id, supplier("Staff edit")); staff.sync.syncNow()
        owner.suppliers.save(id, supplier("Owner edit")); owner.sync.syncNow()

        assertEquals(SyncState.CONFLICT, owner.db.suppliers().get(id)!!.syncState)
        assertEquals(1, owner.sync.problems.first().size)
        assertEquals("Staff edit", server.record(id)!!.supplier!!.name)

        owner.sync.keepMine(id)
        assertEquals("Owner edit", server.record(id)!!.supplier!!.name)
        assertEquals(SyncState.SYNCED, owner.db.suppliers().get(id)!!.syncState)
        staff.sync.syncNow()
        assertEquals("Owner edit", staff.db.suppliers().get(id)!!.name)
    }

    @Test
    fun useTheirsReplacesTheLocalCopy() = runBlocking {
        val id = owner.suppliers.save(null, supplier("Original"))
        owner.sync.syncNow(); staff.sync.syncNow()
        staff.suppliers.save(id, supplier("Staff edit")); staff.sync.syncNow()
        owner.suppliers.save(id, supplier("Owner edit")); owner.sync.syncNow()

        owner.sync.useTheirs(id)
        assertEquals("Staff edit", owner.db.suppliers().get(id)!!.name)
        assertEquals(0, owner.db.outbox().count())
        assertEquals("Staff edit", server.record(id)!!.supplier!!.name)
    }

    @Test
    fun pullNeverOverwritesAnUnsentLocalChange() = runBlocking {
        val id = owner.suppliers.save(null, supplier("Original"))
        owner.sync.syncNow(); staff.sync.syncNow()
        server.offline = true
        staff.suppliers.save(id, supplier("Typed offline"))
        server.offline = false
        owner.suppliers.save(id, supplier("Owner edit")); owner.sync.syncNow()
        // staff pushes first (conflict), then pulls: the local text must still be there.
        staff.sync.syncNow()
        assertEquals("Typed offline", staff.db.suppliers().get(id)!!.name)
        assertEquals(SyncState.CONFLICT, staff.db.suppliers().get(id)!!.syncState)
    }

    // ------------------------------------------------------------------------------- permissions

    @Test
    fun aChangeTheServerRefusesIsMarkedFailedAndCanBeDiscarded() = runBlocking {
        staff.me = staff.me.copy(permissions = staffDefaults - "SUPPLIER_ADD")
        val id = staff.suppliers.save(null, supplier("Not allowed"))
        staff.sync.syncNow()
        assertNull(server.record(id))
        assertEquals(SyncState.FAILED, staff.db.suppliers().get(id)!!.syncState)
        assertEquals(1, staff.sync.problems.first().size)
        staff.sync.discard(id)
        assertNull(staff.db.suppliers().get(id))
        assertEquals(0, staff.db.outbox().count())
    }

    @Test
    fun correctingARefusedChangeSendsItAgain() = runBlocking {
        val id = owner.items.save(null, ItemInput("Salt", null, BuiltInUnits.PCS, null, null, "no-such-supplier"))
        // FakeServer accepts anything, so make the first attempt fail by sending it as a staff member without ITEM_ADD.
        owner.me = owner.me.copy(role = "STAFF", permissions = listOf("ITEM_VIEW"))
        owner.sync.syncNow()
        assertEquals(SyncState.FAILED, owner.db.items().get(id)!!.syncState)
        owner.me = owner.me.copy(role = "OWNER")
        owner.items.save(id, ItemInput("Salt", null, BuiltInUnits.PCS, null, null, null))
        owner.sync.syncNow()
        assertEquals(SyncState.SYNCED, owner.db.items().get(id)!!.syncState)
        assertEquals(0, owner.db.outbox().count())
        assertEquals(2, server.received.map { it.opId }.distinct().size)
    }

    @Test
    fun staffCannotDeleteWithoutPermissionEvenIfTheAppSendsIt() = runBlocking {
        val id = owner.suppliers.save(null, supplier("Keep me"))
        owner.sync.syncNow(); staff.sync.syncNow()
        staff.suppliers.delete(id)  // the UI hides the button; the server must still refuse
        staff.sync.syncNow()
        assertFalse(server.record(id)!!.supplier!!.deleted)
    }

    @Test
    fun losingViewPermissionRemovesLocalCopiesAndRegainingFetchesThemAgain() = runBlocking {
        val id = owner.suppliers.save(null, supplier("Visible"))
        owner.sync.syncNow(); staff.sync.syncNow()
        assertNotNull(staff.db.suppliers().get(id))

        staff.me = staff.me.copy(permissions = staffDefaults - "SUPPLIER_VIEW")
        staff.sync.syncNow()
        assertNull(staff.db.suppliers().get(id))

        staff.me = staff.me.copy(permissions = staffDefaults)
        staff.sync.syncNow()
        assertEquals("Visible", staff.db.suppliers().get(id)!!.name)
    }

    // ------------------------------------------------------------------------------- deletes

    @Test
    fun deleteReachesTheOtherPhone() = runBlocking {
        val id = owner.suppliers.save(null, supplier("Old"))
        owner.sync.syncNow(); staff.sync.syncNow()
        owner.suppliers.delete(id)
        assertTrue(owner.suppliers.observe("", true).first().none { it.id == id })
        owner.sync.syncNow()
        assertTrue(server.record(id)!!.supplier!!.deleted)
        staff.sync.syncNow()
        assertNull(staff.db.suppliers().get(id))
    }

    @Test
    fun deletingSomethingNeverSyncedLeavesNothingToSend() = runBlocking {
        server.offline = true
        val id = owner.suppliers.save(null, supplier("Typo"))
        owner.suppliers.save(id, supplier("Typo 2"))
        owner.suppliers.delete(id)
        assertNull(owner.db.suppliers().get(id))
        assertEquals(0, owner.db.outbox().count())
    }

    // ------------------------------------------------------------------------------- lists

    private fun line(name: String, qty: String, basis: String, price: String) =
        ListLineDraft(itemName = name, quantity = qty, unitId = BuiltInUnits.all[1].id, priceBasis = basis, enteredPrice = price)

    @Test
    fun listTotalsNeverMultiplyATotalAmountAndMatchTheServer() = runBlocking {
        val id = staff.lists.save(null, "Monday order", LocalDate.of(2026, 11, 16), null, "Urgent", "SAVED",
            listOf(line("Rice", "20", ListCalculator.TOTAL, "2800"), line("Dal", "2.5", ListCalculator.PER_UNIT, "48.40")), "staff1")
        val local = staff.db.lists().get(id)!!
        assertEquals("2921.00", local.totalAmount)
        assertEquals(listOf("2800.00", "121.00"), staff.db.lists().lines(id).map { it.lineTotal })
        staff.sync.syncNow()
        assertEquals("2921.00", server.record(id)!!.list!!.totalAmount)
        owner.sync.syncNow()
        assertEquals("2921.00", owner.db.lists().get(id)!!.totalAmount)
        assertEquals(2, owner.db.lists().lines(id).size)
    }

    @Test
    fun editingLinesAndDuplicatingAList() = runBlocking {
        val id = owner.lists.save(null, "Week 1", LocalDate.now(), null, "", "DRAFT",
            listOf(line("Sugar", "1.5", ListCalculator.PER_UNIT, "45"), line("Oil", "2", ListCalculator.PER_UNIT, "150")), "owner")
        val lines = owner.lists.lines(id)
        val edited = listOf(
            ListLineDraft(lines[0].id, null, "Sugar", "", "2", lines[0].unitId, ListCalculator.PER_UNIT, "45"),
            line("Tea", "1", ListCalculator.TOTAL, "250"),
        ) // Oil removed, Sugar quantity changed, Tea added
        owner.lists.save(id, "Week 1", LocalDate.now(), null, "", "DRAFT", edited, "owner")
        assertEquals("340.00", owner.db.lists().get(id)!!.totalAmount)
        assertEquals(listOf("Sugar", "Tea"), owner.lists.lines(id).map { it.itemName })

        val copy = owner.lists.duplicate(id, "owner")
        assertEquals("Copy of Week 1", owner.db.lists().get(copy)!!.title)
        assertEquals("DRAFT", owner.db.lists().get(copy)!!.status)
        assertEquals("340.00", owner.db.lists().get(copy)!!.totalAmount)
        assertTrue(owner.lists.lines(copy).map { it.id }.intersect(owner.lists.lines(id).map { it.id }.toSet()).isEmpty())

        owner.sync.syncNow(); staff.sync.syncNow()
        assertEquals(2, staff.lists.observe("", "", "", "", "").first().size)
        assertEquals(2, staff.lists.observe("Tea", "", "", "", "").first().size)
        assertEquals(1, staff.lists.observe("Copy", "", "", "", "").first().size)
        assertEquals(listOf("Sugar", "Tea"), staff.lists.lines(copy).map { it.itemName })
    }

    @Test
    fun listSearchAndFilters() = runBlocking {
        val sup = owner.suppliers.save(null, supplier("Gupta"))
        owner.lists.save(null, "Dairy", LocalDate.of(2026, 11, 1), sup, "", "SAVED", listOf(line("Paneer", "1", ListCalculator.PER_UNIT, "300")), "owner")
        owner.lists.save(null, "Snacks", LocalDate.of(2026, 11, 10), null, "", "DRAFT", listOf(line("Chips", "10", ListCalculator.PER_UNIT, "20")), "owner")
        assertEquals(listOf("Dairy"), owner.lists.observe("paneer", "", "", "", "").first().map { it.title })
        assertEquals(listOf("Snacks"), owner.lists.observe("", "DRAFT", "", "", "").first().map { it.title })
        assertEquals(listOf("Dairy"), owner.lists.observe("", "", sup, "", "").first().map { it.title })
        assertEquals(listOf("Snacks"), owner.lists.observe("", "", "", "2026-11-05", "2026-11-30").first().map { it.title })
    }

    @Test
    fun listValidation() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { owner.lists.save(null, " ", LocalDate.now(), null, "", "DRAFT", emptyList(), "owner") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { owner.lists.save(null, "Empty", LocalDate.now(), null, "", "SAVED", emptyList(), "owner") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { owner.lists.save(null, "Bad", LocalDate.now(), null, "", "DRAFT", listOf(line("Rice", "0", ListCalculator.PER_UNIT, "1")), "owner") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { owner.lists.save(null, "Bad", LocalDate.now(), null, "", "DRAFT", listOf(line("Rice", "1", ListCalculator.TOTAL, "1.234")), "owner") }
        }
        // A draft may be empty.
        assertNotNull(owner.lists.save(null, "Empty draft", LocalDate.now(), null, "", "DRAFT", emptyList(), "owner"))
        assertEquals(1, owner.db.outbox().count())
    }

    // ------------------------------------------------------------------------------- items

    @Test
    fun itemsSyncWithPricesAndNamesStayUnique() = runBlocking {
        val id = owner.items.save(null, ItemInput("Basmati Rice", "Grains", BuiltInUnits.all[1].id, "92.5", "110", null))
        assertTrue(owner.items.nameInUse("basmati rice", null))
        assertFalse(owner.items.nameInUse("basmati rice", id))
        owner.sync.syncNow(); staff.sync.syncNow()
        val onStaff = staff.db.items().get(id)!!
        assertEquals("92.5", onStaff.latestPurchasePrice)
        assertEquals(listOf("Grains"), staff.items.categories().first())
    }
}
