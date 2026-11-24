package app.stocklist.app.feature.catalogue

import app.stocklist.app.core.i18n.Bilingual

import app.stocklist.app.core.i18n.tr
import app.stocklist.app.core.i18n.trMessage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import app.stocklist.app.core.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stocklist.app.data.local.SyncState
import app.stocklist.app.data.remote.MeDto
import app.stocklist.app.domain.permission.Permission
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What the signed-in user may do. Only hides controls; the server checks every change again. */
class Access(val user: MeDto) {
    private val perms = Permission.fromCodes(user.permissions)
    fun has(p: Permission) = user.isOwner || p in perms

    val canViewSuppliers get() = has(Permission.SUPPLIER_VIEW)
    val canViewItems get() = has(Permission.ITEM_VIEW)
    val canViewLists get() = has(Permission.LIST_CREATE) || has(Permission.LIST_EDIT) ||
        has(Permission.LIST_DELETE) || has(Permission.PURCHASE_RECORD)
    val canViewPurchases get() = has(Permission.PURCHASE_HISTORY_VIEW) || has(Permission.PURCHASE_RECORD) ||
        has(Permission.PURCHASE_EDIT) || has(Permission.PURCHASE_DELETE)
    val canSeeProfit get() = has(Permission.EXPECTED_PROFIT_VIEW)
    val canViewExpiry get() = has(Permission.EXPIRY_VIEW)
    val canMakePdf get() = has(Permission.PDF_GENERATE)
    val canSharePdf get() = has(Permission.PDF_SHARE)
    /** Business reports come from the server, which checks these again. */
    val canViewReports get() = has(Permission.PURCHASE_HISTORY_VIEW)
    val canViewProfitReport get() = has(Permission.PROFIT_REPORT_VIEW) && has(Permission.EXPECTED_PROFIT_VIEW)
}

/** Small marker shown on records that are not yet confirmed by the server. */
@Composable
fun SyncBadge(state: String, error: String? = null) {
    val (icon, text, color) = when (state) {
        SyncState.PENDING -> Triple(Icons.Filled.CloudUpload, tr("Waiting to sync"), MaterialTheme.colorScheme.onSurfaceVariant)
        SyncState.FAILED -> Triple(Icons.Filled.ErrorOutline, error?.let { trMessage(it) } ?: tr("Not accepted by the server"), MaterialTheme.colorScheme.error)
        SyncState.CONFLICT -> Triple(Icons.Filled.CloudOff, tr("Changed by someone else"), MaterialTheme.colorScheme.error)
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.padding(end = 4.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

data class Choice(val id: String?, val label: String)

/** Read-only dropdown for picking one of a fixed set of choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    selectedId: String?,
    choices: List<Choice>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val selected = choices.firstOrNull { it.id == selectedId }?.label?.let { Bilingual.oneLine(it) } ?: ""
    ExposedDropdownMenuBox(expanded = open && enabled, onExpandedChange = { if (enabled) open = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true, enabled = enabled, singleLine = true,
            label = { Text(label, maxLines = 1) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { c ->
                DropdownMenuItem(text = { Text(c.label) }, onClick = { onSelect(c.id); open = false })
            }
        }
    }
}

private val displayDate = DateTimeFormatter.ofPattern("dd MMM yyyy")

fun LocalDate.display(): String = format(displayDate)
fun String?.displayDate(): String = this?.let { runCatching { LocalDate.parse(it).display() }.getOrNull() } ?: "-"

/** A field that opens a calendar. [value] is null when "any date" is allowed and nothing is chosen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier, clearable: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value?.display() ?: "", onValueChange = {}, readOnly = true, singleLine = true,
        label = { Text(label, maxLines = 1) },
        trailingIcon = {
            Row {
                if (clearable && value != null) TextButton(onClick = { onChange(null) }) { Text(tr("Clear")) }
                TextButton(onClick = { open = true }) { Text(tr("Pick")) }
            }
        },
        modifier = modifier,
    )
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(tr("Cancel")) } },
        ) { DatePicker(state) }
    }
}

/** A row that is highlighted while chosen (no tick box); used by the pick lists. */
@Composable
fun TapRow(on: Boolean, enabled: Boolean = true, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}

/** Shown when a search finds nothing: says so and, when allowed, offers a button that opens the item page with the name filled in. */
@Composable
fun NoItemFound(query: String, canCreate: Boolean, onCreate: (String) -> Unit) {
    val q = query.trim()
    if (q.isEmpty()) return
    androidx.compose.foundation.layout.Column {
        Text(tr("No item found for %1\$s", q), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp))
        if (canCreate) {
            androidx.compose.material3.Button(onClick = { onCreate(q) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Create item: %1\$s", q)) }
        }
    }
}
