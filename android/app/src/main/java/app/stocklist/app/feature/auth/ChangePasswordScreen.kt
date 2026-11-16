package app.stocklist.app.feature.auth

import app.stocklist.app.core.i18n.Bilingual

import app.stocklist.app.core.i18n.tr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.ErrorText
import app.stocklist.app.core.designsystem.LoadingButton
import app.stocklist.app.core.designsystem.PasswordField
import app.stocklist.app.core.session.SessionState
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.core.util.Validators
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val session: SessionStore,
) : ViewModel() {
    var current by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var confirm by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saved by mutableStateOf(false)
        private set

    fun submit() {
        val username = (session.state.value as? SessionState.SignedIn)?.user?.username
        error = when {
            current.isEmpty() -> tr("Enter your current password")
            Validators.newPassword(newPassword, username) != null -> Validators.newPassword(newPassword, username)
            confirm != newPassword -> tr("Passwords do not match")
            else -> null
        }
        if (error != null) return
        loading = true
        viewModelScope.launch {
            val r = auth.changePassword(current, newPassword)
            loading = false
            if (r is ApiResult.Failure) error = r.error.message else saved = true
        }
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

/** forced = true when a staff member signs in with a temporary password. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(forced: Boolean, onDone: () -> Unit, vm: ChangePasswordViewModel = hiltViewModel()) {
    LaunchedEffect(vm.saved) { if (vm.saved) onDone() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (forced) tr("Set your password") else tr("Change password")) }, navigationIcon = {
            if (!forced) {
                IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Bilingual.oneLine(tr("Back"))) }
            }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (forced) {
                Text(tr("You signed in with a temporary password. Choose your own password to continue."),
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(tr("Other phones signed in to your account will be signed out."),
                    style = MaterialTheme.typography.bodyMedium)
            }
            PasswordField(vm.current, { vm.current = it }, if (forced) tr("Temporary password") else tr("Current password"),
                imeAction = ImeAction.Next)
            PasswordField(vm.newPassword, { vm.newPassword = it }, tr("New password (at least 8 characters)"),
                imeAction = ImeAction.Next)
            PasswordField(vm.confirm, { vm.confirm = it }, tr("Confirm new password"), onDone = vm::submit)
            ErrorText(vm.error)
            LoadingButton(tr("Save password"), vm.loading, vm::submit)
            if (forced) TextButton(onClick = vm::logout) { Text(tr("Sign out")) }
        }
    }
}
