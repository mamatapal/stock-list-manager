package app.stocklist.app.feature.auth

import app.stocklist.app.core.i18n.trMessage

import app.stocklist.app.core.i18n.trKey

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.ErrorText
import app.stocklist.app.core.designsystem.FormField
import app.stocklist.app.core.designsystem.LoadingButton
import app.stocklist.app.core.designsystem.PasswordField
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginRole(val code: String, private val englishTitle: String) {
    OWNER("OWNER", trKey("Owner sign-in")),
    STAFF("STAFF", trKey("Staff sign-in"));

    val title: String get() = trMessage(englishTitle)
}

@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Set when signing in would delete another person's unsent changes; the user must decide. */
    var othersChanges by mutableStateOf<String?>(null)
        private set

    fun keepOthersChanges() {
        othersChanges = null
        password = ""
        error = tr("Sign-in cancelled. The other person's changes are still on this phone.")
    }

    fun submit(role: LoginRole, discardOthersChanges: Boolean = false) {
        othersChanges = null
        if (username.isBlank() || password.isEmpty()) {
            error = tr("Enter your username and password")
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            // The password lives only in this screen's memory; it is sent once over HTTPS and never stored.
            val r = auth.login(username, password, role.code, discardOthersChanges)
            loading = false
            if (r is ApiResult.Failure && r.error.code == app.stocklist.app.data.repository.UNSENT_CHANGES) {
                othersChanges = r.error.message // keep the password until the user decides
            } else if (r is ApiResult.Failure) {
                error = r.error.message
                password = ""
            }
            // On success the session state changes and the app moves to the dashboard by itself.
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    role: LoginRole,
    signedOutReason: String?,
    onBack: () -> Unit,
    onForgot: () -> Unit,
    vm: LoginViewModel = hiltViewModel(),
) {
    var staffHelp by remember { mutableStateOf(false) }
    vm.othersChanges?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = vm::keepOthersChanges,
            title = { Text(tr("Unsent changes on this phone")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { vm.submit(role, discardOthersChanges = true) }) {
                    Text(tr("Delete them and sign in"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = vm::keepOthersChanges) { Text(tr("Keep them")) } },
        )
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(role.title) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back")) }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (role == LoginRole.OWNER) tr("Sign in with the username you chose when registering the business.")
                else tr("Sign in with the username and password your shop owner gave you."),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (signedOutReason != null && vm.error == null) {
                Text(signedOutReason, color = MaterialTheme.colorScheme.error)
            }
            FormField(vm.username, { vm.username = it }, tr("Username"), capitalization = KeyboardCapitalization.None)
            PasswordField(vm.password, { vm.password = it }, onDone = { vm.submit(role) })
            ErrorText(vm.error)
            LoadingButton(tr("Sign in"), vm.loading, { vm.submit(role) })
            TextButton(onClick = { if (role == LoginRole.OWNER) onForgot() else staffHelp = true }) {
                Text(tr("Forgot password?"))
            }
        }
    }
    if (staffHelp) {
        AlertDialog(
            onDismissRequest = { staffHelp = false },
            title = { Text(tr("Forgot your password?")) },
            text = {
                Text(tr("Ask your shop owner to reset it. They can do this in the app under Staff, then your name, then Reset password. You will get a temporary password and choose a new one when you sign in."))
            },
            confirmButton = { TextButton(onClick = { staffHelp = false }) { Text("OK") } },
        )
    }
}
