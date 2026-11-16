package app.stocklist.app.feature.auth

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.ErrorText
import app.stocklist.app.core.designsystem.FormField
import app.stocklist.app.core.designsystem.LoadingButton
import app.stocklist.app.core.designsystem.PasswordField
import app.stocklist.app.core.util.Validators
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    var username by mutableStateOf("")
    var code by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var done by mutableStateOf(false)
        private set

    fun requestCode() {
        if (username.isBlank()) {
            error = tr("Enter your username")
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            val r = auth.forgotPassword(username)
            loading = false
            if (r is ApiResult.Failure) error = r.error.message
            else message = tr("If this is an owner account with an email address, a reset code has been sent to it. Staff members: please ask the business owner to reset your password.")
        }
    }

    fun reset() {
        val pwError = Validators.newPassword(newPassword, username.ifBlank { null })
        if (code.isBlank() || pwError != null) {
            error = if (code.isBlank()) tr("Enter the reset code from the email") else pwError
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            val r = auth.resetPassword(code, newPassword)
            loading = false
            if (r is ApiResult.Failure) error = r.error.message else done = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, vm: ForgotPasswordViewModel = hiltViewModel()) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("Reset password")) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back")) }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (vm.done) {
                Text(tr("Your password has been changed. Sign in with the new password."))
                LoadingButton(tr("Back to sign in"), false, onBack)
                return@Column
            }
            Text(tr("1. Request a reset code"), style = MaterialTheme.typography.titleMedium)
            FormField(vm.username, { vm.username = it.trim() }, tr("Username"),
                capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done, onDone = vm::requestCode)
            LoadingButton(tr("Send reset code"), vm.loading, vm::requestCode)
            vm.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(tr("2. Enter the code and a new password"), style = MaterialTheme.typography.titleMedium)
            FormField(vm.code, { vm.code = it.trim() }, tr("Reset code"), capitalization = KeyboardCapitalization.None)
            PasswordField(vm.newPassword, { vm.newPassword = it }, tr("New password"), onDone = vm::reset)
            ErrorText(vm.error)
            LoadingButton(tr("Set new password"), vm.loading, vm::reset)
        }
    }
}
