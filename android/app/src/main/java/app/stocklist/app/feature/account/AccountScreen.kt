package app.stocklist.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.stateIn
import app.stocklist.app.core.designsystem.ConfirmDialog
import app.stocklist.app.core.designsystem.LabelValue
import app.stocklist.app.core.designsystem.SectionCard
import app.stocklist.app.data.remote.MeDto
import app.stocklist.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val auth: AuthRepository,
    sync: app.stocklist.app.data.sync.SyncRepository,
) : ViewModel() {
    /** Changes saved on this phone that the server has not accepted yet (signing out deletes them). */
    val unsent = sync.unsentCount.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), 0)
    val unsentDetail = sync.unsentChanges.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        app.stocklist.app.data.sync.UnsentChanges.NONE)

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(user: MeDto, onBack: () -> Unit, onChangePassword: () -> Unit, vm: AccountViewModel = hiltViewModel()) {
    var confirmLogout by remember { mutableStateOf(false) }
    val unsent by vm.unsent.collectAsStateWithLifecycle()
    val unsentDetail by vm.unsentDetail.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("My account") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("Signed in as") {
                LabelValue("Name", user.fullName)
                LabelValue("Username", user.username)
                LabelValue("Role", if (user.isOwner) "Owner" else "Staff")
                LabelValue("Business", user.businessName)
            }
            OutlinedButton(onClick = onChangePassword, modifier = Modifier.fillMaxWidth()) { Text("Change password") }
            OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        }
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "Sign out?",
            message = if (unsent > 0) {
                unsentDetail.signOutMessage()
            } else {
                "This phone's local copy of the business data will be removed. You can sign in again any time."
            },
            confirmLabel = if (unsent > 0) "Sign out and delete them" else "Sign out",
            onConfirm = { confirmLogout = false; vm.logout() },
            onDismiss = { confirmLogout = false },
        )
    }
}
