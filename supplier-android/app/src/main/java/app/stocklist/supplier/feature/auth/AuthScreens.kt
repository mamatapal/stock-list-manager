package app.stocklist.supplier.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.supplier.core.designsystem.ErrorText
import app.stocklist.supplier.core.designsystem.FormField
import app.stocklist.supplier.core.designsystem.LoadingButton
import app.stocklist.supplier.core.designsystem.PasswordField
import app.stocklist.supplier.core.i18n.Bilingual
import app.stocklist.supplier.core.i18n.Text
import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.core.util.Validators
import app.stocklist.supplier.data.remote.ApiResult
import app.stocklist.supplier.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ------------------------------------------------------------------ shared

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title, maxLines = 1) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Bilingual.oneLine(tr("Back")))
            }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

// ------------------------------------------------------------------ sign in

@HiltViewModel
class LoginViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** The password was right but the email is not verified yet: offer a new link. */
    var needsVerification by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    fun resendLink() {
        viewModelScope.launch {
            val r = account.resendVerification(email)
            if (r is ApiResult.Failure) error = r.error.message
            else notice = tr("If this email still needs verifying, a new link is on its way. Only the newest link works.")
        }
    }

    fun submit() {
        if (loading) return
        needsVerification = false
        notice = null
        if (email.isBlank() || password.isEmpty()) {
            error = tr("Enter your email and password")
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            // The password lives only in this screen's memory; it is sent once over HTTPS and never stored.
            val r = account.login(email, password)
            loading = false
            if (r is ApiResult.Failure) {
                error = r.error.message
                needsVerification = r.error.code == "EMAIL_NOT_VERIFIED"
                password = ""
            }
            // On success the session changes and the app moves to the home screen by itself.
        }
    }
}

@Composable
fun LoginScreen(signedOutReason: String?, onBack: () -> Unit, onForgot: () -> Unit,
                vm: LoginViewModel = hiltViewModel()) {
    AuthScaffold(tr("Sign in"), onBack) {
        Text(tr("Sign in with the email and password you registered with."), style = MaterialTheme.typography.bodyMedium)
        if (signedOutReason != null && vm.error == null) {
            Text(signedOutReason, color = MaterialTheme.colorScheme.error)
        }
        FormField(vm.email, { vm.email = it }, tr("Email"), keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None)
        PasswordField(vm.password, { vm.password = it }, onDone = vm::submit)
        ErrorText(vm.error)
        vm.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (vm.needsVerification) {
            OutlinedButton(onClick = vm::resendLink, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Send the link again"))
            }
        }
        LoadingButton(tr("Sign in"), vm.loading, vm::submit)
        TextButton(onClick = onForgot) { Text(tr("Forgot password?")) }
    }
}

// ------------------------------------------------------------------ forgot password

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    var email by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var sent by mutableStateOf(false)
        private set

    fun submit() {
        if (loading) return
        error = Validators.email(email)
        if (error != null) return
        loading = true
        viewModelScope.launch {
            val r = account.forgotPassword(email)
            loading = false
            if (r is ApiResult.Failure) error = r.error.message else sent = true
        }
    }
}

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, vm: ForgotPasswordViewModel = hiltViewModel()) {
    AuthScaffold(tr("Forgot password"), onBack) {
        if (vm.sent) {
            Text(tr("If an account exists for this email, we have sent a link to choose a new password. The link works for 1 hour. Open it, choose the new password, then sign in here."))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(tr("Back to sign in")) }
        } else {
            Text(tr("Enter the email you registered with. We will email you a link to choose a new password."),
                style = MaterialTheme.typography.bodyMedium)
            FormField(vm.email, { vm.email = it }, tr("Email"), keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None, onDone = vm::submit)
            ErrorText(vm.error)
            LoadingButton(tr("Send link"), vm.loading, vm::submit)
        }
    }
}

// ------------------------------------------------------------------ after registering

@HiltViewModel
class CheckEmailViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun resend(email: String) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            val r = account.resendVerification(email)
            loading = false
            if (r is ApiResult.Failure) error = r.error.message
            else message = tr("If this email still needs verifying, a new link is on its way. Only the newest link works.")
        }
    }
}

@Composable
fun CheckEmailScreen(email: String, onSignIn: () -> Unit, vm: CheckEmailViewModel = hiltViewModel()) {
    AuthScaffold(tr("Check your email"), onSignIn) {
        Text(tr("We sent a verification link to %1\$s.", email), style = MaterialTheme.typography.titleMedium)
        Text(tr("Open the email on any phone or computer and press Verify. The link works for 24 hours. Then sign in here to see your status."))
        Text(tr("Did not get it? Look in the spam folder, or send the link again."),
            style = MaterialTheme.typography.bodySmall)
        vm.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        ErrorText(vm.error)
        LoadingButton(tr("Sign in"), false, onSignIn)
        OutlinedButton(onClick = { vm.resend(email) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Send the link again"))
        }
    }
}
