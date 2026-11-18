package app.stocklist.supplier.feature.network

import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.data.remote.ConnectionStatus
import app.stocklist.supplier.data.remote.OrderStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Words, numbers and dates of the Supplier Network screens, in the app's languages. */
object NetworkText {

    fun connectionStatus(status: String): String = when (status) {
        ConnectionStatus.REQUESTED -> tr("New request")
        ConnectionStatus.ACTIVE -> tr("Connected")
        ConnectionStatus.DECLINED -> tr("Declined")
        ConnectionStatus.CANCELLED -> tr("Request withdrawn")
        ConnectionStatus.ENDED -> tr("Ended")
        else -> tr("Unknown status")
    }

    fun orderStatus(status: String): String = when (status) {
        OrderStatus.SENT -> tr("New order")
        OrderStatus.ACCEPTED -> tr("Accepted")
        OrderStatus.REJECTED -> tr("Rejected")
        OrderStatus.DISPATCHED -> tr("Dispatched")
        OrderStatus.RECEIVED -> tr("Received by the shop")
        OrderStatus.CANCELLED -> tr("Cancelled by the shop")
        OrderStatus.DELIVERY_FAILED -> tr("Delivery failed")
        else -> tr("Unknown status")
    }

    /** What the supplier can do next with an order, or null when nothing (the shop acts, or the order is closed). */
    fun orderNextStep(status: String): String? = when (status) {
        OrderStatus.SENT -> tr("Accept or reject this order.")
        OrderStatus.ACCEPTED -> tr("Send the goods, then mark the order as dispatched.")
        OrderStatus.DISPATCHED -> tr("Waiting for the shop to confirm what it received.")
        else -> null
    }

    /** FULL or SHORT for the whole receipt. */
    fun receipt(status: String?): String? = when (status) {
        "FULL" -> tr("Received in full")
        "SHORT" -> tr("Received short")
        else -> null
    }

    /** FULL, SHORT or NOT_RECEIVED for one line. */
    fun lineReceipt(status: String?): String? = when (status) {
        "FULL" -> tr("Received in full")
        "SHORT" -> tr("Received short")
        "NOT_RECEIVED" -> tr("Not received")
        else -> null
    }

    /** Who made a change, as seen by the supplier. */
    fun actor(actorType: String): String = when (actorType) {
        "SHOP" -> tr("Shop")
        "SUPPLIER" -> tr("You")
        else -> tr("Automatic")
    }

    /** The status a history entry moved to: order and connection statuses share the same entries list. */
    fun eventStatus(status: String, isOrder: Boolean): String = if (isOrder) orderStatus(status) else connectionStatus(status)

    /** A short order number for people (the first 8 characters of the order's id), e.g. #3F2A9C1D. */
    fun orderNumber(id: String): String = "#" + id.replace("-", "").take(8).uppercase()

    /** "Agarwal Stores, Indore, Madhya Pradesh" (city and state only when known). */
    fun place(name: String, city: String?, state: String?): String =
        listOf(name, city, state).filter { !it.isNullOrBlank() }.joinToString(", ")

    /** ₹ and Indian digit grouping (₹1,23,456.50), from the server's decimal string. Unreadable text is shown as is. */
    fun money(amount: String?): String {
        if (amount.isNullOrBlank()) return "-"
        val v = amount.trim()
        val negative = v.startsWith("-")
        val digits = v.removePrefix("-")
        val intPart = digits.substringBefore('.')
        val fraction = digits.substringAfter('.', "")
        if (intPart.isEmpty() || !intPart.all { it.isDigit() } || !fraction.all { it.isDigit() }) return v
        val grouped = if (intPart.length <= 3) intPart else {
            val head = intPart.dropLast(3)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + intPart.takeLast(3)
        }
        return (if (negative) "-" else "") + "₹" + grouped + (if (fraction.isEmpty()) "" else ".$fraction")
    }

    private val dateTime = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")

    /** Server time (ISO-8601) in the phone's time zone, e.g. 18-11-2026 11:47. */
    fun dateTime(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String =
        iso?.let { runCatching { dateTime.format(Instant.parse(it).atZone(zone)) }.getOrNull() } ?: "-"
}
