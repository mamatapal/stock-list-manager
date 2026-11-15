package app.stocklist.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.stocklist.app.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Phones that installed the Phase 1 build keep their data when updated (schema 1 -> 2). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsDataAndAddsTables() {
        helper.createDatabase("migration-test", 1).use { db ->
            db.execSQL("INSERT INTO local_meta(`key`, value) VALUES ('business_id', 'biz-1')")
        }
        helper.runMigrationsAndValidate("migration-test", 5, true).use { db ->
            db.query("SELECT value FROM local_meta WHERE `key` = 'business_id'").use {
                it.moveToFirst(); assertEquals("biz-1", it.getString(0))
            }
            listOf("suppliers", "items", "units", "stock_lists", "stock_list_lines", "outbox", "purchases",
                "purchase_lines", "deliveries", "delivery_lines", "batches", "people").forEach { t ->
                db.query("SELECT COUNT(*) FROM $t").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            }
        }
    }

    /** Phones that installed the Phase 2 build keep their lists, and old lines start at "nothing received". */
    @Test
    fun migrate2To3KeepsListsAndAddsPurchases() {
        helper.createDatabase("migration-test-2", 2).use { db ->
            db.execSQL("INSERT INTO stock_lists(id, title, listDate, supplierId, notes, status, receivingStatus, totalAmount, " +
                "createdBy, deleted, serverVersion, syncState, syncError, updatedAt) VALUES ('l1', 'Soap', '2026-11-14', NULL, NULL, " +
                "'SAVED', 'PENDING', '20.00', NULL, 0, 3, 'SYNCED', NULL, 0)")
            db.execSQL("INSERT INTO stock_list_lines(id, listId, itemId, itemName, description, quantity, unitId, priceBasis, " +
                "enteredPrice, lineTotal, sortOrder) VALUES ('x1', 'l1', NULL, 'Soap', NULL, '100', 'u', 'PER_UNIT', '0.2', '20.00', 0)")
        }
        helper.runMigrationsAndValidate("migration-test-2", 5, true).use { db ->
            db.query("SELECT quantity, receivedQuantity, unitMismatch FROM stock_list_lines WHERE id = 'x1'").use {
                it.moveToFirst()
                assertEquals("100", it.getString(0))
                assertEquals("0", it.getString(1))
                assertEquals(0, it.getInt(2))
            }
            db.query("SELECT COUNT(*) FROM purchases").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }

    /** Phase 4 phones keep their unsent changes when updated; the new retry counter starts at zero. */
    @Test
    fun migrate4To5KeepsUnsentChanges() {
        helper.createDatabase("migration-test-4", 4).use { db ->
            db.execSQL("INSERT INTO outbox(opId, entityType, entityId, action, isCreate, payloadJson, createdAt, state, attempts, " +
                "lastError, conflictJson, conflictVersion) VALUES ('op1', 'PURCHASE', 'p1', 'UPSERT', 1, '{}', 1, 'PENDING', 2, " +
                "'offline', NULL, NULL)")
        }
        helper.runMigrationsAndValidate("migration-test-4", 5, true).use { db ->
            db.query("SELECT state, attempts, serverRetries FROM outbox WHERE opId = 'op1'").use {
                it.moveToFirst()
                assertEquals("PENDING", it.getString(0))
                assertEquals(2, it.getInt(1))
                assertEquals(0, it.getInt(2))
            }
        }
    }
}
