package app.stocklist.supplier.feature.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.supplier.core.designsystem.ConfirmDialog
import app.stocklist.supplier.core.designsystem.ErrorText
import app.stocklist.supplier.core.designsystem.LabelValue
import app.stocklist.supplier.core.designsystem.SectionCard
import app.stocklist.supplier.core.i18n.Text
import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.core.session.SessionState
import app.stocklist.supplier.core.session.SessionStore
import app.stocklist.supplier.data.remote.ApiResult
import app.stocklist.supplier.data.remote.ListingDto
import app.stocklist.supplier.data.remote.OrgDto
import app.stocklist.supplier.data.repository.NetworkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListingViewModel @Inject constructor(
    private val repo: NetworkRepository,
    session: SessionStore,
) : ViewModel() {
    val session = session.state
    var load by mutableStateOf<Load<ListingDto>>(Load.Loading)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            when (val r = repo.listing()) {
                is ApiResult.Ok -> { load = Load.Ready(r.value); error = null }
                is ApiResult.Failure -> if (load is Load.Ready) error = r.error.message else load = Load.Failed(r.error)
            }
            busy = false
        }
    }

    /** Shown only after the server confirms the change. */
    fun setListed(listed: Boolean) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            when (val r = repo.setListing(listed)) {
                is ApiResult.Ok -> load = Load.Ready(r.value)
                is ApiResult.Failure -> error = r.error.message
            }
            busy = false
        }
    }
}

@Composable
fun ListingScreen(onBack: () -> Unit, vm: ListingViewModel = hiltViewModel()) {
    val state by vm.session.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    ListingContent(
        org = (state as? SessionState.SignedIn)?.me?.org,
        load = vm.load,
        busy = vm.busy,
        error = vm.error,
        onBack = onBack,
        onRefresh = vm::refresh,
        onSetListed = vm::setListed,
    )
}

@Composable
fun ListingContent(
    org: OrgDto?,
    load: Load<ListingDto>,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetListed: (Boolean) -> Unit,
) {
    var confirm by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Boolean?>(null) }
    confirm?.let { listed ->
        ConfirmDialog(
            title = if (listed) tr("Show your business to shops?") else tr("Hide your business from shops?"),
            message = if (listed) {
                tr("Shops using Stock List Manager will find your business in their supplier directory and can send you a connection request.")
            } else {
                tr("New shops will not find you. Existing connections and orders are not affected.")
            },
            confirmLabel = if (listed) tr("Show") else tr("Hide"),
            onConfirm = { confirm = null; onSetListed(listed) },
            onDismiss = { confirm = null },
        )
    }
    NetworkScaffold(title = tr("Supplier directory"), onBack = onBack, busy = busy, onRefresh = onRefresh) {
        LoadContent(load, onRetry = onRefresh) { listing ->
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ErrorText(error)
                SectionCard(tr("Directory listing")) {
                    Text(tr("Shops find suppliers in the supplier directory of the Stock List Manager app. Only approved businesses that choose to be listed are shown. Shops send the connection request; you accept or decline it."),
                        style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (listing.listed) tr("Listed: shops can find you") else tr("Not listed: shops cannot find you"),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = listing.listed,
                            onCheckedChange = { confirm = it },
                            enabled = !busy,
                            modifier = Modifier.testTag("listed"),
                        )
                    }
                }
                if (org != null) {
                    SectionCard(tr("What shops see")) {
                        LabelValue(tr("Business name"), org.tradeName?.takeIf { it.isNotBlank() } ?: org.legalName)
                        LabelValue(tr("Business name (as registered)"), org.legalName)
                        LabelValue(tr("City"), org.city)
                        LabelValue(tr("State"), org.state)
                        Text(tr("Your mobile number, email, address and GSTIN are never shown to shops."),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
