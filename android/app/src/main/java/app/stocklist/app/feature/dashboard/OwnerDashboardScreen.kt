package app.stocklist.app.feature.dashboard

import app.stocklist.app.core.i18n.Bilingual

import app.stocklist.app.core.i18n.tr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import app.stocklist.app.core.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.ErrorText
import app.stocklist.app.core.designsystem.LabelValue
import app.stocklist.app.core.designsystem.LoadingState
import app.stocklist.app.core.designsystem.SectionCard
import app.stocklist.app.core.util.Dates
import app.stocklist.app.data.remote.ActivityEntryDto
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.MeDto
import app.stocklist.app.data.remote.StaffDto
import app.stocklist.app.data.repository.ActivityRepository
import app.stocklist.app.data.repository.StaffRepository
import app.stocklist.app.domain.permission.PermissionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OwnerDashboardViewModel @Inject constructor(
    private val staffRepo: StaffRepository,
    private val activityRepo: ActivityRepository,
) : ViewModel() {
    var staff by mutableStateOf<List<StaffDto>?>(null)
        private set
    var activity by mutableStateOf<List<ActivityEntryDto>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            val s = async { staffRepo.list() }
            val a = async { activityRepo.page(0, 5) }
            when (val r = s.await()) {
                is ApiResult.Ok -> staff = r.value
                is ApiResult.Failure -> error = r.error.message
            }
            when (val r = a.await()) {
                is ApiResult.Ok -> activity = r.value.items
                is ApiResult.Failure -> error = error ?: r.error.message
            }
            loading = false
        }
    }
}

/**
 * Owner home: sync status, lists, items, suppliers, staff, business profile and recent activity.
 * Purchases, expiry and profit cards are added in the phases that build them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboardScreen(
    user: MeDto,
    onBusiness: () -> Unit,
    onStaff: () -> Unit,
    onActivity: () -> Unit,
    onAccount: () -> Unit,
    work: @Composable () -> Unit = {},
    vm: OwnerDashboardViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.load() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(user.businessName) }, actions = {
            IconButton(onClick = vm::load, enabled = !vm.loading) { Icon(Icons.Filled.Refresh, Bilingual.oneLine(tr("Refresh"))) }
            IconButton(onClick = onAccount) { Icon(Icons.Filled.AccountCircle, Bilingual.oneLine(tr("My account"))) }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tr("Hello, %1\$s", user.fullName), style = MaterialTheme.typography.titleLarge)
            Text(tr("Owner · full access"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            work()
            ErrorText(vm.error)

            val staff = vm.staff
            if (staff == null && vm.loading) {
                Column(Modifier.padding(vertical = 24.dp)) { LoadingState() }
            } else if (staff != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(tr("Active staff"), staff.count { it.isActive }.toString(), Modifier.weight(1f))
                    StatCard(tr("Inactive staff"), staff.count { !it.isActive }.toString(), Modifier.weight(1f))
                }
            }

            DashboardTile(Icons.Filled.Group, tr("Staff"),
                if (staff.isNullOrEmpty()) tr("Add staff so they can sign in on their own phones")
                else tr("Accounts, permissions and signed-in phones"), onStaff)
            DashboardTile(Icons.Filled.Store, tr("Business profile"), tr("Details printed on your PDFs"), onBusiness)

            SectionCard(tr("Recent activity")) {
                val items = vm.activity
                when {
                    items == null && vm.loading -> Text(tr("Loading..."))
                    items.isNullOrEmpty() -> Text(tr("No activity yet."))
                    else -> items.forEach { e ->
                        LabelValue(Dates.dateTime(e.createdAt), "${e.username ?: "Someone"}: ${PermissionSummary.actionLabel(e.action)}")
                    }
                }
                TextButton(onClick = onActivity) {
                    Icon(Icons.Filled.History, null)
                    Text(tr("  Full activity log"))
                }
            }
        }
    }
}
