package app.stocklist.app.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** All dates in the app are shown as DD-MM-YYYY, in the phone's time zone. */
object Dates {
    private val DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")

    fun date(iso: String?): String = parse(iso)?.let { DATE.format(it.atZone(ZoneId.systemDefault())) } ?: "-"

    fun dateTime(iso: String?): String =
        parse(iso)?.let { DATE_TIME.format(it.atZone(ZoneId.systemDefault())) } ?: "-"

    private fun parse(iso: String?): Instant? = try {
        iso?.let { Instant.parse(it) }
    } catch (e: Exception) {
        null
    }
}
