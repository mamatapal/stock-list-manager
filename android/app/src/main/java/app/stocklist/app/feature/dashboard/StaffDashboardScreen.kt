package app.stocklist.app.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stocklist.app.core.designsystem.SectionCard
import app.stocklist.app.data.remote.MeDto
import app.stocklist.app.domain.permission.Permission
import app.stocklist.app.domain.permission.PermissionSummary

/**
 * Staff home: only what this person's permissions allow. No owner-only information (staff counts,
 * activity log, profit) is fetched or shown here, and the server refuses those calls anyway.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffDashboardScreen(
    user: MeDto,
    onBusiness: () -> Unit,
    onStaff: () -> Unit,
    onAccount: () -> Unit,
    onRefresh: () -> Unit,
    work: @Composable () -> Unit = {},
) {
    val perms = Permission.fromCodes(user.permissions)
    Scaffold(topBar = {
        TopAppBar(title = { Text(user.businessName) }, actions = {
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh my permissions") }
            IconButton(onClick = onAccount) { Icon(Icons.Filled.AccountCircle, "My account") }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Hello, ${user.fullName}", style = MaterialTheme.typography.titleLarge)
            Text("Staff", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            work()
            if (Permission.STAFF_MANAGE in perms) {
                DashboardTile(Icons.Filled.Group, "Staff", "You can manage staff accounts", onStaff)
            }
            DashboardTile(Icons.Filled.Store, "Business profile",
                if (Permission.SETTINGS_MANAGE in perms) "View and edit business details" else "Business details", onBusiness)

            SectionCard("What you can do") {
                val grouped = PermissionSummary.grouped(perms)
                if (grouped.isEmpty()) {
                    Text("Your owner has not given you any permissions yet.")
                } else {
                    grouped.forEach { (group, labels) ->
                        Text(group, style = MaterialTheme.typography.titleSmall)
                        Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Suppliers, items, lists and purchases appear here as those parts of the app are released.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
