package app.stocklist.supplier.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import app.stocklist.supplier.navigation.NetworkRoutes
import androidx.compose.ui.platform.testTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val account: AccountRepository,
    session: SessionStore,
) : ViewModel() {
    val state = session.state
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    /** Asks the server for the current review status (it changes when the email is verified or a decision is made). */
    fun refresh() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            val r = account.refreshMe()
            loading = false
            if (r is ApiResult.Failure) error = r.error.message
        }
    }

    fun resendLink(email: String) {
        viewModelScope.launch {
            val r = account.resendVerification(email)
            notice = if (r is ApiResult.Failure) null else tr("If this email still needs verifying, a new link is on its way. Only the newest link works.")
            if (r is ApiResult.Failure) error = r.error.message
        }
    }
}

/** Title, explanation and colour of each review status. */
object StatusText {
    fun title(status: String): String = when (status) {
        OrgStatus.PENDING_EMAIL_VERIFICATION -> tr("Verify your email")
        OrgStatus.PENDING_APPROVAL -> tr("Waiting for approval")
        OrgStatus.APPROVED -> tr("Approved")
        OrgStatus.REJECTED -> tr("Not approved")
        OrgStatus.SUSPENDED -> tr("Account suspended")
        else -> tr("Unknown status")
    }

    fun explanation(status: String, email: String): String = when (status) {
        OrgStatus.PENDING_EMAIL_VERIFICATION ->
            tr("We sent a link to %1\$s. Open it and press Verify, then tap Refresh here.", email)
        OrgStatus.PENDING_APPROVAL ->
            tr("Your email is verified. Our team is checking your business details. You will get an email when there is a decision.")
        OrgStatus.APPROVED ->
            tr("Your business is approved. Manage your shop connections, product catalogue and orders below.")
        OrgStatus.REJECTED ->
            tr("Your application was not approved. Correct the details and send them again.")
        OrgStatus.SUSPENDED ->
            tr("Your business account is suspended. Please contact the Supplier Administration team.")
        else -> tr("Please update the app.")
    }
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    OrgStatus.APPROVED -> MaterialTheme.colorScheme.primaryContainer
    OrgStatus.REJECTED, OrgStatus.SUSPENDED -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.secondaryContainer
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onDetails: () -> Unit, onCorrect: () -> Unit, onSettings: () -> Unit,
               onNetwork: (String) -> Unit = {}, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val me = (state as? SessionState.SignedIn)?.me ?: return
    LaunchedEffect(Unit) { vm.refresh() }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(me.org.tradeName ?: me.org.legalName, maxLines = 1) },
            actions = {
                IconButton(onClick = vm::refresh, enabled = !vm.loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = Bilingual.oneLine(tr("Refresh")))
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = Bilingual.oneLine(tr("Settings")))
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            ErrorText(vm.error)
            StatusCard(me, onCorrect, vm)
            if (me.org.status == OrgStatus.APPROVED) NetworkSection(onNetwork)
            SectionCard(tr("Your business")) {
                LabelValue(tr("Business name"), me.org.legalName)
                LabelValue(tr("Contact person"), me.org.contactPerson)
                LabelValue(tr("Mobile number"), me.org.mobile)
                LabelValue(tr("Email"), me.org.email)
                OutlinedButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text(tr("All business details")) }
            }
        }
    }
}

@Composable
private fun StatusCard(me: MeDto, onCorrect: () -> Unit, vm: HomeViewModel) {
    val status = me.org.status
    Card(colors = CardDefaults.cardColors(containerColor = statusColor(status)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(StatusText.title(status), style = MaterialTheme.typography.titleLarge)
            Text(StatusText.explanation(status, me.user.email))
            me.org.statusReason?.takeIf { it.isNotBlank() && (status == OrgStatus.REJECTED || status == OrgStatus.SUSPENDED) }
                ?.let { Text(tr("Reason: %1\$s", it), style = MaterialTheme.typography.bodyLarge) }
            vm.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            when (status) {
                OrgStatus.PENDING_EMAIL_VERIFICATION ->
                    OutlinedButton(onClick = { vm.resendLink(me.user.email) }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Send the link again"))
                    }
                OrgStatus.REJECTED ->
                    Button(onClick = onCorrect, modifier = Modifier.fillMaxWidth()) { Text(tr("Correct details")) }
                else -> {}
            }
        }
    }
}

/** Supplier Network: only for approved businesses (the server refuses everyone else as well). */
@Composable
fun NetworkSection(onOpen: (String) -> Unit) {
    SectionCard(tr("Supplier Network")) {
        Text(tr("Shops using Stock List Manager connect with you, see your catalogue and send purchase orders."),
            style = MaterialTheme.typography.bodySmall)
        Button(onClick = { onOpen(NetworkRoutes.ORDERS) }, modifier = Modifier.fillMaxWidth().testTag("open-orders")) {
            Text(tr("Purchase orders"))
        }
        OutlinedButton(onClick = { onOpen(NetworkRoutes.CONNECTIONS) }, modifier = Modifier.fillMaxWidth().testTag("open-connections")) {
            Text(tr("Shop connections"))
        }
        OutlinedButton(onClick = { onOpen(NetworkRoutes.CATALOGUE) }, modifier = Modifier.fillMaxWidth().testTag("open-catalogue")) {
            Text(tr("Product catalogue"))
        }
        OutlinedButton(onClick = { onOpen(NetworkRoutes.LISTING) }, modifier = Modifier.fillMaxWidth().testTag("open-listing")) {
            Text(tr("Supplier directory"))
        }
    }
}
