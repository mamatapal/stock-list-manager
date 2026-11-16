package app.stocklist.app.feature.staff

import app.stocklist.app.core.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.EmptyState
import app.stocklist.app.core.designsystem.ErrorState
import app.stocklist.app.core.designsystem.LoadingState
import app.stocklist.app.core.util.Dates
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.StaffDto
import app.stocklist.app.data.repository.StaffRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StaffListViewModel @Inject constructor(private val repo: StaffRepository) : ViewModel() {
    var staff by mutableStateOf<List<StaffDto>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun load() {
        error = null
        viewModelScope.launch {
            when (val r = repo.list()) {
                is ApiResult.Ok -> staff = r.value
                is ApiResult.Failure -> error = r.error.message
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffListScreen(onBack: () -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit, vm: StaffListViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.load() } // reload whenever the screen is shown again
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(tr("Staff")) }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back")) }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(tr("Add staff")) })
        },
    ) { padding ->
        val list = vm.staff
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                list == null && vm.error != null -> ErrorState(vm.error!!, vm::load)
                list == null -> LoadingState()
                list.isEmpty() -> EmptyState(tr("No staff accounts yet. Add one so a staff member can sign in on their own phone."),
                    tr("Add staff"), onAdd)
                else -> LazyColumn {
                    items(list, key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.fullName) },
                            supportingContent = {
                                Text(tr("@%1\$s · last sign-in %2\$s", s.username, Dates.dateTime(s.lastLoginAt)))
                            },
                            trailingContent = {
                                Text(if (s.isActive) tr("Active") else tr("Inactive"),
                                    color = if (s.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            },
                            modifier = Modifier.clickable { onOpen(s.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
