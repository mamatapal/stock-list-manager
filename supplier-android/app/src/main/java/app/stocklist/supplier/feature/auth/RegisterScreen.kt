package app.stocklist.supplier.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import app.stocklist.supplier.core.designsystem.PasswordField
import app.stocklist.supplier.core.designsystem.SectionCard
import app.stocklist.supplier.core.i18n.Bilingual
import app.stocklist.supplier.core.i18n.Text
import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.core.util.Validators
import app.stocklist.supplier.data.remote.ApiResult
import app.stocklist.supplier.data.remote.RegisterRequest
import app.stocklist.supplier.data.repository.AccountRepository
import app.stocklist.supplier.feature.account.BusinessFields
import app.stocklist.supplier.feature.account.BusinessFormState
import app.stocklist.supplier.feature.account.fieldOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    val form = BusinessFormState()
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordAgain by mutableStateOf("")
    var emailError by mutableStateOf<String?>(null)
        private set
    var passwordError by mutableStateOf<String?>(null)
        private set
    var againError by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun submit(onDone: (String) -> Unit) {
        if (loading) return
        val formOk = form.check()
        emailError = Validators.email(email)
        passwordError = Validators.password(password)
        againError = Validators.confirm(password, passwordAgain)
        if (!formOk || emailError != null || passwordError != null || againError != null) {
            error = tr("Please correct the fields marked in red")
            return
        }
        error = null
        loading = true
        val p = form.toProfile()
        val mail = email.trim()
        viewModelScope.launch {
            val r = account.register(RegisterRequest(p.legalName, p.tradeName, p.contactPerson, p.mobile, mail,
                password, p.address, p.city, p.state, p.pinCode, p.gstin))
            loading = false
            when (r) {
                is ApiResult.Ok -> {
                    password = ""
                    passwordAgain = ""
                    onDone(mail)
                }
                is ApiResult.Failure -> {
                    when (fieldOf(r.error.code)) {
                        "email" -> emailError = r.error.message
                        "password" -> passwordError = r.error.message
                        null -> {}
                        else -> form.addErrors(mapOf(fieldOf(r.error.code)!! to r.error.message))
                    }
                    error = r.error.message
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onBack: () -> Unit, onRegistered: (String) -> Unit, vm: RegisterViewModel = hiltViewModel()) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Register your business"), maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Bilingual.oneLine(tr("Back")))
                }
            },
            actions = {
                if (vm.loading) {
                    CircularProgressIndicator(Modifier.padding(end = 16.dp).size(24.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { vm.submit(onRegistered) }) { Text(tr("Register"), maxLines = 1) }
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tr("After you register, we email you a link to verify your email address. Then our team checks your business. You can use the app fully once it is approved."),
                style = MaterialTheme.typography.bodyMedium,
            )
            ErrorText(vm.error)
            SectionCard(tr("Business details")) { BusinessFields(vm.form, enabled = !vm.loading) }
            SectionCard(tr("Sign-in details")) {
                FormField(vm.email, { vm.email = it }, tr("Email"), error = vm.emailError,
                    keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None,
                    enabled = !vm.loading)
                PasswordField(vm.password, { vm.password = it }, label = tr("Password (at least 8 characters)"),
                    error = vm.passwordError, imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                PasswordField(vm.passwordAgain, { vm.passwordAgain = it }, label = tr("Password again"),
                    error = vm.againError, onDone = { vm.submit(onRegistered) })
            }
            Text(
                tr("Your email is your sign-in name. We never ask for codes by SMS or email."),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
