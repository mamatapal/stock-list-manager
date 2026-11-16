package app.stocklist.app.data.sync

import app.stocklist.app.core.i18n.tr

import app.stocklist.app.data.local.OutboxEntity

/**
 * What would be lost if this phone's database were cleared now. Purchases and goods received are counted
 * separately because they are financial records and must never disappear without the person agreeing to it.
 */
data class UnsentChanges(val purchases: Int, val deliveries: Int, val other: Int) {
    val total: Int get() = purchases + deliveries + other
    val isEmpty: Boolean get() = total == 0
    val hasFinancial: Boolean get() = purchases + deliveries > 0

    fun describe(): String = listOfNotNull(
        purchases.takeIf { it > 0 }?.let { tr("%1\$s purchase change(s)", it) },
        deliveries.takeIf { it > 0 }?.let { tr("%1\$s goods-received record(s)", it) },
        other.takeIf { it > 0 }?.let { tr("%1\$s other change(s)", it) },
    ).joinToString(", ")

    /** Shown when a different person tries to sign in on this phone. */
    fun otherUserMessage(): String =
        tr("This phone still has changes made by the previous user that have not reached the server: %1\$s. Ask them to sign in again with internet so the changes are sent. If you continue, they are deleted from this phone.", describe())

    /** Shown when the current user signs out. */
    fun signOutMessage(): String =
        tr("%1\$s on this phone have not reached the server yet and will be DELETED if you sign out now. Connect to the internet and wait for them to sync first.", describe())

    companion object {
        val NONE = UnsentChanges(0, 0, 0)

        fun of(outbox: List<OutboxEntity>) = UnsentChanges(
            purchases = outbox.count { it.entityType == EntityTypes.PURCHASE },
            deliveries = outbox.count { it.entityType == EntityTypes.DELIVERY },
            other = outbox.count { it.entityType != EntityTypes.PURCHASE && it.entityType != EntityTypes.DELIVERY },
        )
    }
}
