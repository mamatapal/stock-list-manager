package app.stocklist.app.data.local

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** Device-level values: the signed-in business id, the sync cursor, the last successful sync time. */
@Entity(tableName = "local_meta")
data class LocalMeta(@PrimaryKey val key: String, val value: String)

@Dao
interface LocalMetaDao {
    @Query("SELECT value FROM local_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(meta: LocalMeta)
}

/**
 * The encrypted local database (SQLCipher). Version 2 adds the business tables and the sync outbox; version 3
 * adds purchases and deliveries.
 * Upgrades are automatic migrations checked against the exported schemas in app/schemas.
 */
@Database(
    entities = [
        LocalMeta::class, SupplierEntity::class, ItemEntity::class, UnitEntity::class, StockListEntity::class,
        StockListLineEntity::class, OutboxEntity::class, PurchaseEntity::class, PurchaseLineEntity::class,
        DeliveryEntity::class, DeliveryLineEntity::class, BatchEntity::class, PersonEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meta(): LocalMetaDao
    abstract fun metaFlow(): MetaFlowDao
    abstract fun suppliers(): SupplierDao
    abstract fun items(): ItemDao
    abstract fun units(): UnitDao
    abstract fun lists(): StockListDao
    abstract fun outbox(): OutboxDao
    abstract fun purchases(): PurchaseDao
    abstract fun batches(): BatchDao
    abstract fun people(): PersonDao
    abstract fun resync(): ResyncDao

    companion object {
        const val NAME = "stocklist.db"
        const val META_BUSINESS_ID = "business_id"
        const val META_SYNC_CURSOR = "sync_cursor"
        const val META_LAST_SYNC = "last_sync_at"
    }
}
