package app.stocklist.app.feature.staff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stocklist.app.domain.permission.Permission

/**
 * Checkbox list grouped by area. [grantable] limits what the current user may change
 * (a staff manager can only grant what they hold; the server enforces the same rule).
 */
@Composable
fun PermissionEditor(
    selected: Set<Permission>,
    grantable: Set<Permission>,
    enabled: Boolean,
    onToggle: (Permission, Boolean) -> Unit,
) {
    Column {
        Permission.groups.forEach { group ->
            Text(group, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            if (group == "Sensitive") {
                Text("Off by default. Grant only to people you trust with money and accounts.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Permission.entries.filter { it.group == group }.forEach { p ->
                val canChange = enabled && p in grantable
                val checked = p in selected
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = canChange) { onToggle(p, !checked) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = { onToggle(p, it) }, enabled = canChange)
                    Text(p.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
