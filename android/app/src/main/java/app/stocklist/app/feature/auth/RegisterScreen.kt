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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import app.stocklist.app.data.repository.RegisterForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Two steps: the business details (printed on PDFs), then the owner's account. */
@HiltViewModel
class RegisterViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    var step by mutableStateOf(1)
        private set
    var businessName by mutableStateOf("")
    var address by mutableStateOf("")
    var city by mutableStateOf("")
    var state by mutableStateOf("")
    var pinCode by mutableStateOf("")
    var gstin by mutableStateOf("")
    var ownerName by mutableStateOf("")
    var mobile by mutableStateOf("")
    var email by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var confirm by mutableStateOf("")
    val errors = mutableStateMapOf<String, String>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private fun check(field: String, message: String?) {
        if (message != null) errors[field] = message else errors.remove(field)
    }

    fun next() {
        check("businessName", Validators.required(businessName, tr("Business name")))
        check("address", Validators.required(address, tr("Address")))
        check("city", Validators.required(city, tr("City")))
        check("state", Validators.required(state, tr("State")))
        check("pinCode", Validators.pinCode(pinCode))
        check("gstin", Validators.gstin(gstin))
        if (errors.isEmpty()) step = 2
    }

    fun back() {
        step = 1
    }

    fun submit() {
        check("ownerName", Validators.required(ownerName, tr("Your name")))
        check("mobile", Validators.mobile(mobile))
        check("email", Validators.email(email))
        check("username", Validators.username(username))
        check("password", Validators.newPassword(password, username))
        check("confirm", if (confirm != password) tr("Passwords do not match") else null)
        if (errors.isNotEmpty()) return
        loading = true
        error = null
        viewModelScope.launch {
            val r = auth.register(
                RegisterForm(businessName, ownerName, username, mobile, email, password, address, city, state,
                    pinCode, gstin),
            )
            loading = false
            if (r is ApiResult.Failure) {
                error = r.error.message
                r.error.fieldErrors.forEach { (field, msg) -> errors[field] = msg }
                if (r.error.fieldErrors.keys.any { it in setOf("businessName", "address", "city", "state", "pinCode", "gstin") }) {
                    step = 1
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onBack: () -> Unit, vm: RegisterViewModel = hiltViewModel()) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (vm.step == 1) tr("Your business (1 of 2)") else tr("Owner account (2 of 2)")) },
            navigationIcon = {
                IconButton(onClick = { if (vm.step == 2) vm.back() else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val e = vm.errors
            if (vm.step == 1) {
                Text(tr("These details appear on your PDFs. You can change them later."),
                    style = MaterialTheme.typography.bodyMedium)
                FormField(vm.businessName, { vm.businessName = it }, tr("Business name"), e["businessName"],
                    capitalization = KeyboardCapitalization.Words)
                FormField(vm.address, { vm.address = it }, tr("Address"), e["address"], singleLine = false)
                FormField(vm.city, { vm.city = it }, tr("City"), e["city"], capitalization = KeyboardCapitalization.Words)
                FormField(vm.state, { vm.state = it }, tr("State"), e["state"], capitalization = KeyboardCapitalization.Words)
                FormField(vm.pinCode, { vm.pinCode = it.filter(Char::isDigit).take(6) }, tr("PIN code"), e["pinCode"],
                    keyboardType = KeyboardType.Number)
                FormField(vm.gstin, { vm.gstin = it.uppercase().take(15) }, tr("GST number (optional)"), e["gstin"],
                    capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done, onDone = vm::next)
                LoadingButton(tr("Next"), loading = false, onClick = vm::next)
            } else {
                FormField(vm.ownerName, { vm.ownerName = it }, tr("Your full name"), e["ownerName"],
                    capitalization = KeyboardCapitalization.Words)
                FormField(vm.mobile, { vm.mobile = it.filter(Char::isDigit).take(10) }, tr("Mobile number"), e["mobile"],
                    keyboardType = KeyboardType.Phone)
                FormField(vm.email, { vm.email = it.trim() }, tr("Email"), e["email"], keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None)
                FormField(vm.username, { vm.username = it.trim() }, tr("Choose a username"), e["username"],
                    capitalization = KeyboardCapitalization.None)
                PasswordField(vm.password, { vm.password = it }, tr("Password (at least 8 characters)"), e["password"],
                    imeAction = ImeAction.Next)
                PasswordField(vm.confirm, { vm.confirm = it }, tr("Confirm password"), e["confirm"], onDone = vm::submit)
                ErrorText(vm.error)
                LoadingButton(tr("Create business"), vm.loading, vm::submit)
            }
        }
    }
}
