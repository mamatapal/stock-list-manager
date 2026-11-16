package app.stocklist.app.feature.activity

import app.stocklist.app.core.i18n.tr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.app.core.designsystem.EmptyState
import app.stocklist.app.core.designsystem.ErrorState
import app.stocklist.app.core.designsystem.LoadingState
import app.stocklist.app.core.util.Dates
import app.stocklist.app.data.remote.ActivityEntryDto
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.repository.ActivityRepository
import app.stocklist.app.domain.permission.PermissionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActivityViewModel @Inject constructor(private val repo: ActivityRepository) : ViewModel() {
    var items by mutableStateOf<List<ActivityEntryDto>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    private var page = 0

    init {
        reload()
    }

    fun reload() {
        page = 0
        items = null
        error = null
        fetch()
    }

    fun more() {
        page++
        fetch()
    }

    private fun fetch() {
        loadingMore = true
        viewModelScope.launch {
            when (val r = repo.page(page)) {
                is ApiResult.Ok -> {
                    items = items.orEmpty() + r.value.items
                    hasMore = (items?.size ?: 0) < r.value.total
                }
                is ApiResult.Failure -> error = r.error.message
            }
            loadingMore = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(onBack: () -> Unit, vm: ActivityViewModel = hiltViewModel()) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(tr("Activity log")) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back")) }
        })
    }) { padding ->
        val list = vm.items
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                list == null && vm.error != null -> ErrorState(vm.error!!, vm::reload)
                list == null -> LoadingState()
                list.isEmpty() -> EmptyState(tr("No activity yet."))
                else -> LazyColumn {
                    items(list, key = { it.id }) { e ->
                        ListItem(
                            headlineContent = { Text(PermissionSummary.actionLabel(e.action)) },
                            supportingContent = { Text("${e.username ?: "Unknown user"} · ${Dates.dateTime(e.createdAt)}") },
                        )
                        HorizontalDivider()
                    }
                    if (vm.hasMore) {
                        item {
                            OutlinedButton(onClick = vm::more, enabled = !vm.loadingMore,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(if (vm.loadingMore) tr("Loading...") else tr("Load more"))
                            }
                        }
                    }
                }
            }
        }
    }
}
