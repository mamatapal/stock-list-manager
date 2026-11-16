package app.stocklist.app.feature.business

import app.stocklist.app.core.i18n.bi
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import app.stocklist.app.core.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.ErrorState
import app.stocklist.app.core.designsystem.ErrorText
import app.stocklist.app.core.designsystem.FormField
import app.stocklist.app.core.designsystem.LabelValue
import app.stocklist.app.core.designsystem.LoadingButton
import app.stocklist.app.core.designsystem.LoadingState
import app.stocklist.app.core.designsystem.ReauthDialog
import app.stocklist.app.core.designsystem.SectionCard
import app.stocklist.app.core.designsystem.StepUpViewModel
import app.stocklist.app.core.util.Dates
import app.stocklist.app.core.util.Validators
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.BusinessDto
import app.stocklist.app.data.remote.UpdateBusinessRequest
import app.stocklist.app.data.repository.AuthRepository
import app.stocklist.app.data.repository.BusinessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BusinessProfileViewModel @Inject constructor(
    private val repo: BusinessRepository,
    auth: AuthRepository,
) : StepUpViewModel(auth) {
    var business by mutableStateOf<BusinessDto?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var editing by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var savedMessage by mutableStateOf<String?>(null)

    var name by mutableStateOf("")
    var address by mutableStateOf("")
    var city by mutableStateOf("")
    var state by mutableStateOf("")
    var pinCode by mutableStateOf("")
    var phone by mutableStateOf("")
    var email by mutableStateOf("")
    var gstin by mutableStateOf("")
    val errors = mutableStateMapOf<String, String>()

    init {
        load()
    }

    fun load() {
        loadError = null
        viewModelScope.launch {
            when (val r = repo.get()) {
                is ApiResult.Ok -> business = r.value
                is ApiResult.Failure -> loadError = r.error.message
            }
        }
    }

    fun startEdit() {
        val b = business ?: return
        name = b.name; address = b.address; city = b.city; state = b.state; pinCode = b.pinCode
        phone = b.phone; email = b.email; gstin = b.gstin.orEmpty()
        errors.clear()
        error = null
        editing = true
    }

    fun cancelEdit() {
        editing = false
    }

    fun save() {
        fun check(f: String, m: String?) { if (m != null) errors[f] = m else errors.remove(f) }
        check("name", Validators.required(name, tr("Business name")))
        check("address", Validators.required(address, tr("Address")))
        check("city", Validators.required(city, tr("City")))
        check("state", Validators.required(state, tr("State")))
        check("pinCode", Validators.pinCode(pinCode))
        check("phone", Validators.mobile(phone))
        check("email", Validators.email(email))
        check("gstin", Validators.gstin(gstin))
        if (errors.isNotEmpty()) return
        saving = true
        error = null
        viewModelScope.launch {
            val r = repo.update(
                UpdateBusinessRequest(name.trim(), address.trim(), city.trim(), state.trim(), pinCode.trim(),
                    phone.trim(), email.trim(), gstin.trim().uppercase().ifBlank { null }),
            )
            saving = false
            if (needsStepUp(r) { save() }) return@launch
            when (r) {
                is ApiResult.Ok -> {
                    business = r.value
                    editing = false
                    savedMessage = tr("Business details saved")
                }
                is ApiResult.Failure -> {
                    error = r.error.message
                    r.error.fieldErrors.forEach { (f, m) -> errors[f] = m }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessProfileScreen(canEdit: Boolean, onBack: () -> Unit, vm: BusinessProfileViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.savedMessage) {
        vm.savedMessage?.let { snackbar.showSnackbar(Bilingual.twoLines(it)); vm.savedMessage = null }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (vm.editing) tr("Edit business") else tr("Business profile")) },
                navigationIcon = {
                    IconButton(onClick = { if (vm.editing) vm.cancelEdit() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Bilingual.oneLine(tr("Back")))
                    }
                },
                actions = {
                    if (vm.editing) {
                        TextButton(onClick = vm::save, enabled = !vm.saving) {
                            Text(if (vm.saving) tr("Saving...") else tr("Save"), style = MaterialTheme.typography.titleMedium)
                        }
                    } else if (canEdit && vm.business != null) {
                        IconButton(onClick = vm::startEdit) { Icon(Icons.Filled.Edit, contentDescription = Bilingual.oneLine(tr("Edit"))) }
                    }
                },
            )
        },
    ) { padding ->
        val b = vm.business
        when {
            b == null && vm.loadError != null -> Column(Modifier.padding(padding)) { ErrorState(vm.loadError!!, vm::load) }
            b == null -> Column(Modifier.padding(padding)) { LoadingState() }
            !vm.editing -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard(b.name) {
                    LabelValue(tr("Address"), b.address.bi())
                    LabelValue(tr("City"), b.city.bi())
                    LabelValue(tr("State"), b.state)
                    LabelValue(tr("PIN code"), b.pinCode)
                    LabelValue(tr("Phone"), b.phone)
                    LabelValue(tr("Email"), b.email)
                    LabelValue(tr("GST number"), b.gstin)
                    LabelValue(tr("Last updated"), Dates.dateTime(b.updatedAt))
                }
                Text(tr("These details are printed on your PDFs. The logo can be added once PDFs are built (Phase 3)."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val e = vm.errors
                FormField(vm.name, { vm.name = it }, tr("Business name"), e["name"], capitalization = KeyboardCapitalization.Words)
                FormField(vm.address, { vm.address = it }, tr("Address"), e["address"], singleLine = false)
                FormField(vm.city, { vm.city = it }, tr("City"), e["city"], capitalization = KeyboardCapitalization.Words)
                FormField(vm.state, { vm.state = it }, tr("State"), e["state"], capitalization = KeyboardCapitalization.Words)
                FormField(vm.pinCode, { vm.pinCode = it.filter(Char::isDigit).take(6) }, tr("PIN code"), e["pinCode"],
                    keyboardType = KeyboardType.Number)
                FormField(vm.phone, { vm.phone = it.filter(Char::isDigit).take(10) }, tr("Phone"), e["phone"],
                    keyboardType = KeyboardType.Phone)
                FormField(vm.email, { vm.email = it.trim() }, tr("Email"), e["email"], keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None)
                FormField(vm.gstin, { vm.gstin = it.uppercase().take(15) }, tr("GST number (optional)"), e["gstin"],
                    capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done, onDone = vm::save)
                ErrorText(vm.error)
            }
        }
    }
    if (vm.reauth.visible) {
        ReauthDialog(vm.reauth.loading, vm.reauth.error, vm::submitReauth, vm::dismissReauth)
    }
}
