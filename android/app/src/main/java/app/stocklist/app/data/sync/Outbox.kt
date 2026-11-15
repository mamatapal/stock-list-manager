package app.stocklist.app.data.sync

import app.stocklist.app.data.local.AppDatabase
import app.stocklist.app.data.local.OutboxEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Adds a change to the outbox. Must be called inside the same Room transaction as the local write. */
@Singleton
class Outbox @Inject constructor(private val db: AppDatabase) {

    suspend fun enqueue(entityType: String, entityId: String, action: String, neverOnServer: Boolean, payloadJson: String?) {
        val existing = db.outbox().forEntity(entityId)
        // A refused change that the user has now corrected: replace it rather than queue behind it.
        val failed = existing.firstOrNull { it.state == "FAILED" }
        if (failed != null && existing.size == 1) {
            db.outbox().replacePayload(failed.opId, UUID.randomUUID().toString(), payloadJson, action)
            return
        }
        db.outbox().insert(
            OutboxEntity(
                opId = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                action = action,
                // Only the first change of a record that has never reached the server creates it.
                isCreate = neverOnServer && existing.isEmpty() && action == "UPSERT",
                payloadJson = payloadJson,
                // Strictly increasing, so changes made within the same millisecond keep their order.
                createdAt = maxOf(System.currentTimeMillis(), (db.outbox().lastCreatedAt() ?: 0L) + 1),
            ),
        )
    }
}
