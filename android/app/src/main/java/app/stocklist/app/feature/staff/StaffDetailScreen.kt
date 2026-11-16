package app.stocklist.app.feature.staff

import app.stocklist.app.core.i18n.Bilingual

import app.stocklist.app.core.i18n.tr

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import app.stocklist.app.core.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.stocklist.app.core.util.ShareCredentials
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.ConfirmDialog
import app.stocklist.app.core.designsystem.ErrorState
import app.stocklist.app.core.designsystem.ErrorText
import app.stocklist.app.core.designsystem.FormField
import app.stocklist.app.core.designsystem.LabelValue
import app.stocklist.app.core.designsystem.LoadingButton
import app.stocklist.app.core.designsystem.LoadingState
import app.stocklist.app.core.designsystem.ReauthDialog
import app.stocklist.app.core.designsystem.SectionCard
import app.stocklist.app.core.designsystem.StepUpViewModel
import app.stocklist.app.core.session.SessionState
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.core.util.Dates
import app.stocklist.app.core.util.Validators
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.SessionInfoDto
import app.stocklist.app.data.remote.StaffDto
import app.stocklist.app.data.repository.AuthRepository
import app.stocklist.app.data.repository.StaffRepository
import app.stocklist.app.domain.permission.Permission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StaffDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: StaffRepository,
    session: SessionStore,
    auth: AuthRepository,
) : StepUpViewModel(auth) {
    val staffId: String = checkNotNull(savedState["id"])
    val businessName: String = (session.state.value as? SessionState.SignedIn)?.user?.businessName.orEmpty()
    val grantable: Set<Permission> = (session.state.value as? SessionState.SignedIn)?.user?.let { me ->
        if (me.isOwner) Permission.entries.toSet() else Permission.fromCodes(me.permissions)
    } ?: emptySet()

    var staff by mutableStateOf<StaffDto?>(null)
        private set
    var savedPermissions by mutableStateOf<Set<Permission>>(emptySet())
        private set
    var permissions by mutableStateOf<Set<Permission>>(emptySet())
        private set
    var sessions by mutableStateOf<List<SessionInfoDto>>(emptyList())
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var temporaryPassword by mutableStateOf<String?>(null)

    var editing by mutableStateOf(false)
        private set
    var fullName by mutableStateOf("")
    var mobile by mutableStateOf("")
    var fieldError by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        loadError = null
        viewModelScope.launch {
            val s = repo.get(staffId)
            val p = repo.permissions(staffId)
            val sess = repo.sessions(staffId)
            if (s is ApiResult.Ok && p is ApiResult.Ok) {
                staff = s.value
                savedPermissions = Permission.fromCodes(p.value.permissions)
                permissions = savedPermissions
                sessions = (sess as? ApiResult.Ok)?.value.orEmpty()
            } else {
                loadError = ((s as? ApiResult.Failure) ?: (p as ApiResult.Failure)).error.message
            }
        }
    }

    val permissionsChanged: Boolean get() = permissions != savedPermissions

    fun toggle(p: Permission, on: Boolean) {
        permissions = if (on) permissions + p else permissions - p
    }

    fun savePermissions() {
        act({ repo.setPermissions(staffId, permissions) }, { savePermissions() }) { r ->
            savedPermissions = Permission.fromCodes(r.permissions)
            permissions = savedPermissions
            message = tr("Permissions saved. They apply on %1\$s's phone straight away.", staff?.fullName)
        }
    }

    fun setActive(active: Boolean) {
        act({ repo.setActive(staffId, active) }, { setActive(active) }) { r ->
            staff = r
            if (!active) sessions = emptyList()
            message = if (active) tr("Account activated") else tr("Account deactivated and signed out on all phones")
        }
    }

    fun resetPassword() {
        act({ repo.resetPassword(staffId) }, { resetPassword() }) { r ->
            temporaryPassword = r.temporaryPassword
            sessions = emptyList()
        }
    }

    fun revokeSessions() {
        act({ repo.revokeSessions(staffId) }, { revokeSessions() }) {
            sessions = emptyList()
            message = tr("Signed out on all phones")
        }
    }

    fun startEdit() {
        val s = staff ?: return
        fullName = s.fullName
        mobile = s.mobile.orEmpty()
        fieldError = null
        editing = true
    }

    fun cancelEdit() {
        editing = false
    }

    val hasChanges: Boolean get() = editing || permissionsChanged

    /** The single Save button (top right): saves edited details first, then changed permissions. */
    fun saveAll() {
        if (editing) {
            fieldError = Validators.required(fullName, tr("Full name")) ?: Validators.mobile(mobile, required = false)
            if (fieldError != null) return
            act({ repo.update(staffId, fullName, mobile) }, { saveAll() }) { r ->
                staff = r
                editing = false
                message = tr("Details saved")
                if (permissionsChanged) savePermissions()
            }
        } else if (permissionsChanged) {
            savePermissions()
        }
    }

    private fun <T> act(call: suspend () -> ApiResult<T>, retry: () -> Unit, onOk: (T) -> Unit) {
        busy = true
        error = null
        viewModelScope.launch {
            val r = call()
            busy = false
            if (needsStepUp(r, retry)) return@launch
            when (r) {
                is ApiResult.Ok -> onOk(r.value)
                is ApiResult.Failure -> error = r.error.message
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffDetailScreen(onBack: () -> Unit, vm: StaffDetailViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(Bilingual.twoLines(it)); vm.message = null } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(vm.staff?.fullName ?: tr("Staff member")) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Bilingual.oneLine(tr("Back"))) }
                },
                actions = {
                    if (vm.staff != null) {
                        TextButton(onClick = vm::saveAll, enabled = vm.hasChanges && !vm.busy) {
                            Text(if (vm.busy) tr("Saving...") else tr("Save"), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                },
            )
        },
    ) { padding ->
        val s = vm.staff
        when {
            s == null && vm.loadError != null -> Column(Modifier.padding(padding)) { ErrorState(vm.loadError!!, vm::load) }
            s == null -> Column(Modifier.padding(padding)) { LoadingState() }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp).imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorText(vm.error)
                SectionCard(tr("Details")) {
                    if (vm.editing) {
                        FormField(vm.fullName, { vm.fullName = it }, tr("Full name"), capitalization = KeyboardCapitalization.Words)
                        FormField(vm.mobile, { vm.mobile = it.filter(Char::isDigit).take(10) }, tr("Mobile (optional)"),
                            keyboardType = KeyboardType.Phone)
                        ErrorText(vm.fieldError)
                        Text(tr("Tap Save at the top right to apply."), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = vm::cancelEdit) { Text(tr("Cancel")) }
                    } else {
                        LabelValue(tr("Username"), s.username)
                        LabelValue(tr("Mobile"), s.mobile)
                        LabelValue(tr("Status"), if (s.isActive) tr("Active") else tr("Inactive"))
                        LabelValue(tr("Created"), Dates.date(s.createdAt))
                        LabelValue(tr("Last sign-in"), Dates.dateTime(s.lastLoginAt))
                        if (s.mustChangePassword) {
                            Text(tr("Has not yet chosen their own password."), style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = vm::startEdit, enabled = !vm.busy) { Text(tr("Edit details")) }
                    }
                }
                SectionCard(tr("Access")) {
                    OutlinedButton(
                        onClick = { confirm = if (s.isActive) "deactivate" else "activate" },
                        enabled = !vm.busy, modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (s.isActive) tr("Deactivate account") else tr("Activate account")) }
                    OutlinedButton(onClick = { confirm = "reset" }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Reset password"))
                    }
                }
                SectionCard(tr("Permissions")) {
                    if (vm.permissionsChanged) {
                        Text(tr("Unsaved changes - tap Save at the top right."), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    PermissionEditor(vm.permissions, vm.grantable, enabled = !vm.busy, onToggle = vm::toggle)
                }
                SectionCard(tr("Signed-in phones")) {
                    if (vm.sessions.isEmpty()) {
                        Text(tr("Not signed in on any phone."))
                    } else {
                        vm.sessions.forEach { sess ->
                            LabelValue(sess.deviceName ?: tr("Phone"), tr("since %1\$s", Dates.dateTime(sess.signedInAt)))
                        }
                        OutlinedButton(onClick = { confirm = "signout" }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("Sign out on all phones"))
                        }
                    }
                }
            }
        }
    }

    when (confirm) {
        "deactivate" -> ConfirmDialog(tr("Deactivate account?"),
            tr("%1\$s will be signed out on every phone and cannot sign in until you activate the account again. Their past work stays in the records.", vm.staff?.fullName), tr("Deactivate"),
            { confirm = null; vm.setActive(false) }, { confirm = null })
        "activate" -> ConfirmDialog(tr("Activate account?"), tr("%1\$s will be able to sign in again.", vm.staff?.fullName),
            tr("Activate"), { confirm = null; vm.setActive(true) }, { confirm = null })
        "reset" -> ConfirmDialog(tr("Reset password?"),
            tr("A new temporary password will be created and %1\$s will be signed out on every phone.", vm.staff?.fullName),
            tr("Reset"), { confirm = null; vm.resetPassword() }, { confirm = null })
        "signout" -> ConfirmDialog(tr("Sign out on all phones?"), tr("They can sign in again with their password."),
            tr("Sign out"), { confirm = null; vm.revokeSessions() }, { confirm = null })
    }
    vm.temporaryPassword?.let { temp ->
        val context = LocalContext.current
        val s = vm.staff
        AlertDialog(
            onDismissRequest = {},
            title = { Text(tr("New temporary password")) },
            text = { Text(tr("Give this to %1\$s. It is shown only once:\n\nUsername: %2\$s\nTemporary password: %3\$s", s?.fullName, s?.username, temp)) },
            confirmButton = { TextButton(onClick = { vm.temporaryPassword = null }) { Text(tr("Done")) } },
            dismissButton = {
                if (s != null) {
                    TextButton(onClick = { ShareCredentials.share(context, vm.businessName, s.fullName, s.username, temp) }) {
                        Text(tr("Share"))
                    }
                }
            },
        )
    }
    if (vm.reauth.visible) ReauthDialog(vm.reauth.loading, vm.reauth.error, vm::submitReauth, vm::dismissReauth)
}
