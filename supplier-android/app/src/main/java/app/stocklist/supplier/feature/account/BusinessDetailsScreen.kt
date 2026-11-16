package app.stocklist.supplier.feature.account

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.supplier.core.designsystem.ConfirmDialog
import app.stocklist.supplier.core.designsystem.ErrorText
import app.stocklist.supplier.core.designsystem.LabelValue
import app.stocklist.supplier.core.designsystem.SectionCard
import app.stocklist.supplier.core.i18n.Bilingual
import app.stocklist.supplier.core.i18n.Text
import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.core.session.SessionState
import app.stocklist.supplier.core.session.SessionStore
import app.stocklist.supplier.data.remote.ApiResult
import app.stocklist.supplier.data.remote.MeDto
import app.stocklist.supplier.data.remote.OrgStatus
import app.stocklist.supplier.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BusinessDetailsViewModel @Inject constructor(
    private val account: AccountRepository,
    session: SessionStore,
) : ViewModel() {
    val state = session.state
    val form = BusinessFormState()
    var editing by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var askToSend by mutableStateOf(false)

    fun startEditing(me: MeDto) {
        form.fill(me.org)
        error = null
        editing = true
    }

    fun cancel() {
        editing = false
        error = null
    }

    /** After a rejection, saving sends the business for review again; ask first. */
    fun save(me: MeDto, confirmed: Boolean = false) {
        if (saving) return
        if (!form.check()) {
            error = tr("Please correct the fields marked in red")
            return
        }
        if (me.org.status == OrgStatus.REJECTED && !confirmed) {
            askToSend = true
            return
        }
        askToSend = false
        saving = true
        error = null
        viewModelScope.launch {
            val r = account.updateProfile(me.org.version, form.toProfile())
            saving = false
            when (r) {
                is ApiResult.Ok -> editing = false
                is ApiResult.Failure -> {
                    fieldOf(r.error.code)?.let { form.addErrors(mapOf(it to r.error.message)) }
                    error = r.error.message
                    if (r.error.code == "STALE_VERSION" || r.error.code == "DETAILS_LOCKED") {
                        account.refreshMe()
                        editing = false
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessDetailsScreen(startEditing: Boolean, onBack: () -> Unit, vm: BusinessDetailsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val me = (state as? SessionState.SignedIn)?.me ?: return
    LaunchedEffect(startEditing) { if (startEditing && me.org.canEditDetails) vm.startEditing(me) }
    val resend = me.org.status == OrgStatus.REJECTED
    if (vm.askToSend) {
        ConfirmDialog(
            title = tr("Send for review again?"),
            message = tr("Your corrected details will be sent to our team for approval again."),
            confirmLabel = tr("Send"),
            onConfirm = { vm.save(me, confirmed = true) },
            onDismiss = { vm.askToSend = false },
        )
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (vm.editing) tr("Correct details") else tr("Business details"), maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = { if (vm.editing) vm.cancel() else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Bilingual.oneLine(tr("Back")))
                }
            },
            actions = {
                when {
                    vm.saving -> CircularProgressIndicator(Modifier.padding(end = 16.dp).size(24.dp), strokeWidth = 2.dp)
                    vm.editing -> TextButton(onClick = { vm.save(me) }) {
                        Text(if (resend) tr("Save and send") else tr("Save"), maxLines = 1)
                    }
                    me.org.canEditDetails -> TextButton(onClick = { vm.startEditing(me) }) {
                        Text(tr("Edit"), maxLines = 1)
                    }
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorText(vm.error)
            if (vm.editing) {
                Text(tr("Your email is your sign-in name and cannot be changed here."),
                    style = MaterialTheme.typography.bodySmall)
                BusinessFields(vm.form, enabled = !vm.saving)
            } else {
                SectionCard(tr("Business details")) {
                    LabelValue(tr("Business name (as registered)"), me.org.legalName)
                    LabelValue(tr("Shop or trade name (optional)"), me.org.tradeName)
                    LabelValue(tr("Contact person"), me.org.contactPerson)
                    LabelValue(tr("Mobile number"), me.org.mobile)
                    LabelValue(tr("Email"), me.org.email)
                    LabelValue(tr("Address"), me.org.address)
                    LabelValue(tr("City"), me.org.city)
                    LabelValue(tr("State"), me.org.state)
                    LabelValue(tr("PIN code"), me.org.pinCode)
                    LabelValue(tr("GSTIN (optional)"), me.org.gstin)
                    LabelValue(tr("Status"), StatusText.title(me.org.status))
                }
                if (!me.org.canEditDetails) {
                    Text(tr("Approved details cannot be changed in the app. Contact the Supplier Administration team if something is wrong."),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
