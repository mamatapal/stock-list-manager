package app.stocklist.supplier.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.supplier.BuildConfig
import app.stocklist.supplier.core.designsystem.ConfirmDialog
import app.stocklist.supplier.core.designsystem.ErrorText
import app.stocklist.supplier.core.designsystem.LabelValue
import app.stocklist.supplier.core.designsystem.PasswordField
import app.stocklist.supplier.core.designsystem.SectionCard
import app.stocklist.supplier.core.i18n.Bilingual
import app.stocklist.supplier.core.i18n.LanguageSwitch
import app.stocklist.supplier.core.i18n.Text
import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.core.session.SessionState
import app.stocklist.supplier.core.session.SessionStore
import app.stocklist.supplier.core.util.Validators
import app.stocklist.supplier.data.remote.ApiResult
import app.stocklist.supplier.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val account: AccountRepository,
    session: SessionStore,
) : ViewModel() {
    val state = session.state
    var changing by mutableStateOf(false)
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var done by mutableStateOf(false)
        private set

    fun changePassword(current: String, new: String, again: String) {
        if (saving) return
        error = when {
            current.isEmpty() -> tr("Enter your current password")
            else -> Validators.password(new) ?: Validators.confirm(new, again)
        }
        if (error != null) return
        saving = true
        viewModelScope.launch {
            val r = account.changePassword(current, new)
            saving = false
            if (r is ApiResult.Failure) error = r.error.message
            else { changing = false; done = true }
        }
    }

    fun signOut() {
        viewModelScope.launch { account.logout() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val me = (state as? SessionState.SignedIn)?.me
    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) {
        ConfirmDialog(
            title = tr("Sign out?"),
            message = tr("You will need your email and password to sign in again."),
            confirmLabel = tr("Sign out"),
            onConfirm = { confirmSignOut = false; vm.signOut() },
            onDismiss = { confirmSignOut = false },
        )
    }
    if (vm.changing) ChangePasswordDialog(vm)
    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("Settings"), maxLines = 1) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Bilingual.oneLine(tr("Back")))
            }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(tr("Language")) {
                Text(tr("Screens show both languages; the chosen one comes first."),
                    style = MaterialTheme.typography.bodySmall)
                LanguageSwitch()
            }
            if (me != null) {
                SectionCard(tr("Account")) {
                    LabelValue(tr("Name"), me.user.fullName)
                    LabelValue(tr("Email"), me.user.email)
                    if (vm.done) Text(tr("Password changed. Other phones have been signed out."),
                        color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = { vm.changing = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Change password"))
                    }
                    OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Sign out"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("Supplier Management, version %1\$s", BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChangePasswordDialog(vm: SettingsViewModel) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!vm.saving) vm.changing = false },
        title = { Text(tr("Change password")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it }, label = tr("Current password"), imeAction = ImeAction.Next)
                PasswordField(new, { new = it }, label = tr("New password (at least 8 characters)"), imeAction = ImeAction.Next)
                PasswordField(again, { again = it }, label = tr("Password again"),
                    onDone = { vm.changePassword(current, new, again) })
                ErrorText(vm.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.changePassword(current, new, again) }, enabled = !vm.saving) {
                Text(if (vm.saving) tr("Saving...") else tr("Save"))
            }
        },
        dismissButton = { TextButton(onClick = { vm.changing = false }, enabled = !vm.saving) { Text(tr("Cancel")) } },
    )
}
