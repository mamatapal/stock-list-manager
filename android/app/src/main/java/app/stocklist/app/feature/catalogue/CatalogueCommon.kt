package app.stocklist.app.feature.catalogue

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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
        SyncState.PENDING -> Triple(Icons.Filled.CloudUpload, "Waiting to sync", MaterialTheme.colorScheme.onSurfaceVariant)
        SyncState.FAILED -> Triple(Icons.Filled.ErrorOutline, error ?: "Not accepted by the server", MaterialTheme.colorScheme.error)
        SyncState.CONFLICT -> Triple(Icons.Filled.CloudOff, "Changed by someone else", MaterialTheme.colorScheme.error)
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
    val selected = choices.firstOrNull { it.id == selectedId }?.label ?: ""
    ExposedDropdownMenuBox(expanded = open && enabled, onExpandedChange = { if (enabled) open = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true, enabled = enabled, singleLine = true,
            label = { Text(label) },
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
        label = { Text(label) },
        trailingIcon = {
            Row {
                if (clearable && value != null) TextButton(onClick = { onChange(null) }) { Text("Clear") }
                TextButton(onClick = { open = true }) { Text("Pick") }
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
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
