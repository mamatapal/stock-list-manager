package app.stocklist.app.feature.auth

import app.stocklist.app.core.i18n.LanguageSwitch
import app.stocklist.app.core.i18n.tr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import app.stocklist.app.core.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** First screen when signed out: owners and staff each have their own sign-in. */
@Composable
fun WelcomeScreen(
    signedOutReason: String?,
    onOwnerLogin: () -> Unit,
    onStaffLogin: () -> Unit,
    onRegister: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LanguageSwitch() }
            Spacer(Modifier.height(16.dp))
            Text(tr("Stock List Manager"), style = MaterialTheme.typography.headlineMedium)
            Text(tr("Purchase lists, suppliers and stock for your shop."), style = MaterialTheme.typography.bodyLarge)
            if (signedOutReason != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(signedOutReason, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOwnerLogin, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(tr("Owner sign-in")) }
            OutlinedButton(onClick = onStaffLogin, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(tr("Staff sign-in")) }
            Spacer(Modifier.height(24.dp))
            Text(tr("Setting up a new shop?"), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRegister) { Text(tr("Register your business")) }
        }
    }
}
