package app.stocklist.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** What was typed on the purchase screen, kept on this phone so a crash or a closed app does not lose it. */
@Serializable
data class PurchaseDraftSnapshot(
    val supplierId: String? = null,
    val invoiceNo: String = "",
    val purchaseDate: String,
    val receivedDate: String,
    val notes: String = "",
    val stockListId: String? = null,
    val listTitle: String? = null,
    val receiveNow: Boolean = true,
    val lines: List<PurchaseLineDraft> = emptyList(),
) {
    /** True when the person has typed or chosen something worth keeping. */
    val hasContent: Boolean get() = lines.isNotEmpty() || supplierId != null || invoiceNo.isNotBlank() || notes.isNotBlank()
}

/** Auto-saved purchase entries, one per signed-in user. Written straight to disk so a crash right after a change keeps it. */
@Singleton
class PurchaseDraftStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("purchase_drafts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(key: String): PurchaseDraftSnapshot? =
        runCatching { prefs.getString(key, null)?.let { json.decodeFromString<PurchaseDraftSnapshot>(it) } }.getOrNull()

    fun save(key: String, draft: PurchaseDraftSnapshot) {
        runCatching { prefs.edit().putString(key, json.encodeToString(draft)).commit() }
    }

    fun clear(key: String) { prefs.edit().remove(key).commit() }
}
