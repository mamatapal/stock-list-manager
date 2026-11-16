package app.stocklist.app.feature.staff

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import app.stocklist.app.core.util.ShareCredentials
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import app.stocklist.app.core.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.ErrorText
import app.stocklist.app.core.designsystem.FormField
import app.stocklist.app.core.designsystem.LoadingButton
import app.stocklist.app.core.designsystem.ReauthDialog
import app.stocklist.app.core.designsystem.SectionCard
import app.stocklist.app.core.designsystem.StepUpViewModel
import app.stocklist.app.core.session.SessionState
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.core.util.Validators
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.CreateStaffResponse
import app.stocklist.app.data.repository.AuthRepository
import app.stocklist.app.data.repository.StaffRepository
import app.stocklist.app.domain.permission.Permission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StaffCreateViewModel @Inject constructor(
    private val repo: StaffRepository,
    session: SessionStore,
    auth: AuthRepository,
) : StepUpViewModel(auth) {
    val businessName: String = (session.state.value as? SessionState.SignedIn)?.user?.businessName.orEmpty()
    val grantable: Set<Permission> = (session.state.value as? SessionState.SignedIn)?.user?.let { me ->
        if (me.isOwner) Permission.entries.toSet() else Permission.fromCodes(me.permissions)
    } ?: emptySet()

    var fullName by mutableStateOf("")
    var username by mutableStateOf("")
    var mobile by mutableStateOf("")
    var selected by mutableStateOf(Permission.entries.filter { it.staffDefault && it in grantable }.toSet())
        private set
    val errors = mutableStateMapOf<String, String>()
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var created by mutableStateOf<CreateStaffResponse?>(null)
        private set

    fun toggle(p: Permission, on: Boolean) {
        selected = if (on) selected + p else selected - p
    }

    fun submit() {
        fun check(f: String, m: String?) { if (m != null) errors[f] = m else errors.remove(f) }
        check("fullName", Validators.required(fullName, tr("Full name")))
        check("username", Validators.username(username))
        check("mobile", Validators.mobile(mobile, required = false))
        if (errors.isNotEmpty()) return
        saving = true
        error = null
        viewModelScope.launch {
            val r = repo.create(fullName, username, mobile, selected)
            saving = false
            if (needsStepUp(r) { submit() }) return@launch
            when (r) {
                is ApiResult.Ok -> created = r.value
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
fun StaffCreateScreen(onDone: () -> Unit, vm: StaffCreateViewModel = hiltViewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Add staff")) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Bilingual.oneLine(tr("Back"))) }
                },
                // Always visible, whatever the screen size and even with the keyboard open.
                actions = {
                    if (vm.created == null) {
                        TextButton(onClick = vm::submit, enabled = !vm.saving) {
                            Text(if (vm.saving) tr("Creating...") else tr("Create"), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val done = vm.created
            if (done != null) {
                SectionCard(tr("Account created")) {
                    Text(tr("Give these sign-in details to %1\$s. The temporary password is shown only once; they must choose their own password at first sign-in.", done.staff.fullName))
                    Text(tr("Username: %1\$s", done.staff.username), style = MaterialTheme.typography.titleMedium)
                    Text(tr("Temporary password: %1\$s", done.temporaryPassword), style = MaterialTheme.typography.titleMedium)
                }
                val context = LocalContext.current
                Button(
                    onClick = {
                        ShareCredentials.share(context, vm.businessName, done.staff.fullName, done.staff.username,
                            done.temporaryPassword)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(tr("  Share sign-in details"))
                }
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tr("Done")) }
                return@Column
            }
            val e = vm.errors
            ErrorText(vm.error)
            FormField(vm.fullName, { vm.fullName = it }, tr("Full name"), e["fullName"], capitalization = KeyboardCapitalization.Words)
            FormField(vm.username, { vm.username = it.trim() }, tr("Username"), e["username"],
                capitalization = KeyboardCapitalization.None)
            FormField(vm.mobile, { vm.mobile = it.filter(Char::isDigit).take(10) }, tr("Mobile (optional)"), e["mobile"],
                keyboardType = KeyboardType.Phone)
            Text(tr("Permissions"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(tr("Pre-selected: normal purchasing and receiving work."), style = MaterialTheme.typography.bodySmall)
            PermissionEditor(vm.selected, vm.grantable, enabled = !vm.saving, onToggle = vm::toggle)
        }
    }
    if (vm.reauth.visible) ReauthDialog(vm.reauth.loading, vm.reauth.error, vm::submitReauth, vm::dismissReauth)
}
