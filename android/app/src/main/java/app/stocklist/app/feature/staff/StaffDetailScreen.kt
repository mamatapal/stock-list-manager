package app.stocklist.app.feature.staff

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
import androidx.compose.material3.Text
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
            message = "Permissions saved. They apply on ${staff?.fullName}'s phone straight away."
        }
    }

    fun setActive(active: Boolean) {
        act({ repo.setActive(staffId, active) }, { setActive(active) }) { r ->
            staff = r
            if (!active) sessions = emptyList()
            message = if (active) "Account activated" else "Account deactivated and signed out on all phones"
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
            message = "Signed out on all phones"
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
            fieldError = Validators.required(fullName, "Full name") ?: Validators.mobile(mobile, required = false)
            if (fieldError != null) return
            act({ repo.update(staffId, fullName, mobile) }, { saveAll() }) { r ->
                staff = r
                editing = false
                message = "Details saved"
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
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(vm.staff?.fullName ?: "Staff member") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (vm.staff != null) {
                        TextButton(onClick = vm::saveAll, enabled = vm.hasChanges && !vm.busy) {
                            Text(if (vm.busy) "Saving..." else "Save", style = MaterialTheme.typography.titleMedium)
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
                SectionCard("Details") {
                    if (vm.editing) {
                        FormField(vm.fullName, { vm.fullName = it }, "Full name", capitalization = KeyboardCapitalization.Words)
                        FormField(vm.mobile, { vm.mobile = it.filter(Char::isDigit).take(10) }, "Mobile (optional)",
                            keyboardType = KeyboardType.Phone)
                        ErrorText(vm.fieldError)
                        Text("Tap Save at the top right to apply.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = vm::cancelEdit) { Text("Cancel") }
                    } else {
                        LabelValue("Username", s.username)
                        LabelValue("Mobile", s.mobile)
                        LabelValue("Status", if (s.isActive) "Active" else "Inactive")
                        LabelValue("Created", Dates.date(s.createdAt))
                        LabelValue("Last sign-in", Dates.dateTime(s.lastLoginAt))
                        if (s.mustChangePassword) {
                            Text("Has not yet chosen their own password.", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = vm::startEdit, enabled = !vm.busy) { Text("Edit details") }
                    }
                }
                SectionCard("Access") {
                    OutlinedButton(
                        onClick = { confirm = if (s.isActive) "deactivate" else "activate" },
                        enabled = !vm.busy, modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (s.isActive) "Deactivate account" else "Activate account") }
                    OutlinedButton(onClick = { confirm = "reset" }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Reset password")
                    }
                }
                SectionCard("Permissions") {
                    if (vm.permissionsChanged) {
                        Text("Unsaved changes - tap Save at the top right.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    PermissionEditor(vm.permissions, vm.grantable, enabled = !vm.busy, onToggle = vm::toggle)
                }
                SectionCard("Signed-in phones") {
                    if (vm.sessions.isEmpty()) {
                        Text("Not signed in on any phone.")
                    } else {
                        vm.sessions.forEach { sess ->
                            LabelValue(sess.deviceName ?: "Phone", "since ${Dates.dateTime(sess.signedInAt)}")
                        }
                        OutlinedButton(onClick = { confirm = "signout" }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Sign out on all phones")
                        }
                    }
                }
            }
        }
    }

    when (confirm) {
        "deactivate" -> ConfirmDialog("Deactivate account?",
            "${vm.staff?.fullName} will be signed out on every phone and cannot sign in until you activate the account again. " +
                "Their past work stays in the records.", "Deactivate",
            { confirm = null; vm.setActive(false) }, { confirm = null })
        "activate" -> ConfirmDialog("Activate account?", "${vm.staff?.fullName} will be able to sign in again.",
            "Activate", { confirm = null; vm.setActive(true) }, { confirm = null })
        "reset" -> ConfirmDialog("Reset password?",
            "A new temporary password will be created and ${vm.staff?.fullName} will be signed out on every phone.",
            "Reset", { confirm = null; vm.resetPassword() }, { confirm = null })
        "signout" -> ConfirmDialog("Sign out on all phones?", "They can sign in again with their password.",
            "Sign out", { confirm = null; vm.revokeSessions() }, { confirm = null })
    }
    vm.temporaryPassword?.let { temp ->
        val context = LocalContext.current
        val s = vm.staff
        AlertDialog(
            onDismissRequest = {},
            title = { Text("New temporary password") },
            text = { Text("Give this to ${s?.fullName}. It is shown only once:\n\nUsername: ${s?.username}\nTemporary password: $temp") },
            confirmButton = { TextButton(onClick = { vm.temporaryPassword = null }) { Text("Done") } },
            dismissButton = {
                if (s != null) {
                    TextButton(onClick = { ShareCredentials.share(context, vm.businessName, s.fullName, s.username, temp) }) {
                        Text("Share")
                    }
                }
            },
        )
    }
    if (vm.reauth.visible) ReauthDialog(vm.reauth.loading, vm.reauth.error, vm::submitReauth, vm::dismissReauth)
}
