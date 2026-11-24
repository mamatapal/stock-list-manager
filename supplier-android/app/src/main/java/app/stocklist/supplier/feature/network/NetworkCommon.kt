package app.stocklist.supplier.feature.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.stocklist.supplier.core.designsystem.EmptyState
import app.stocklist.supplier.core.designsystem.ErrorState
import app.stocklist.supplier.core.designsystem.FormField
import app.stocklist.supplier.core.designsystem.LoadingState
import app.stocklist.supplier.core.i18n.Bilingual
import app.stocklist.supplier.core.i18n.Text
import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.core.util.ProductRules
import app.stocklist.supplier.data.remote.ApiError
import app.stocklist.supplier.data.remote.EventDto

/** What a screen shows of server data: loading, an error to retry, or the data (kept while refreshing). */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val error: ApiError) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

/** Screen frame of the Supplier Network screens: title, back, refresh and a thin bar while the server answers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScaffold(
    title: String,
    onBack: () -> Unit,
    busy: Boolean,
    onRefresh: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Bilingual.oneLine(tr("Back")))
                }
            },
            actions = {
                actions()
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.testTag("refresh")) {
                        Icon(Icons.Filled.Refresh, contentDescription = Bilingual.oneLine(tr("Refresh")))
                    }
                }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("busy"))
            content()
        }
    }
}

/** Loading, error (with Try again) or the content. A failed refresh keeps the data on screen with the error above. */
@Composable
fun <T> LoadContent(load: Load<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (load) {
        Load.Loading -> LoadingState()
        is Load.Failed -> ErrorState(load.error.message, onRetry)
        is Load.Ready -> content(load.value)
    }
}

@Composable
fun NothingHere(message: String) = EmptyState(message)

/** A coloured label for a status. */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(color, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun connectionColor(status: String): Color = when (status) {
    "REQUESTED" -> MaterialTheme.colorScheme.tertiaryContainer
    "ACTIVE" -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
fun orderColor(status: String): Color = when (status) {
    "SENT" -> MaterialTheme.colorScheme.tertiaryContainer
    "ACCEPTED", "DISPATCHED" -> MaterialTheme.colorScheme.primaryContainer
    "RECEIVED" -> MaterialTheme.colorScheme.secondaryContainer
    "REJECTED", "CANCELLED", "DELIVERY_FAILED" -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

/**
 * The confirmation step of an action that takes a reason or note (decline, end, reject, dispatch), shown on the
 * screen itself rather than in a pop-up so the keyboard has room. Nothing is sent until the confirm button is
 * pressed; with required = true it stays off until something is typed. Checked like on the server (at most 500).
 */
@Composable
fun ReasonPanel(
    title: String,
    message: String,
    fieldLabel: String,
    confirmLabel: String,
    required: Boolean,
    busy: Boolean,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().testTag("reason-panel"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            FormField(
                value = text,
                onValueChange = { text = it; error = null },
                label = fieldLabel,
                error = error,
                singleLine = false,
                modifier = Modifier.testTag("reason"),
            )
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(tr("Cancel"))
                }
                androidx.compose.material3.Button(
                    onClick = {
                        error = ProductRules.reason(text, required)
                        if (error == null) onConfirm(text.trim())
                    },
                    enabled = !busy && (!required || text.isNotBlank()),
                    modifier = Modifier.weight(1f).testTag("confirm"),
                ) { Text(confirmLabel) }
            }
        }
    }
}

/** The history of a connection or order, oldest first: when, who, the new status and any reason. */
@Composable
fun HistoryList(events: List<EventDto>, isOrder: Boolean) {
    if (events.isEmpty()) {
        Text(tr("No history yet"), style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        events.forEach { e ->
            Column {
                Text(
                    NetworkText.eventStatus(e.toStatus, isOrder) +
                        (NetworkText.receipt(e.receiptStatus)?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    NetworkText.dateTime(e.at) + " · " + NetworkText.actor(e.actorType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                e.reason?.takeIf { it.isNotBlank() }?.let {
                    Text(tr("Reason: %1\$s", it), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
