package app.stocklist.supplier.feature.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stocklist.supplier.core.i18n.LanguageSwitch
import app.stocklist.supplier.core.i18n.Text
import app.stocklist.supplier.core.i18n.tr

@Composable
fun WelcomeScreen(signedOutReason: String?, onRegister: () -> Unit, onSignIn: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { LanguageSwitch() }
            Spacer(Modifier.height(48.dp))
            Text(tr("Supplier Management"), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(tr("For suppliers who sell to shops. Register your business; after our team approves it, you can work with your shops here."),
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center)
            if (signedOutReason != null) {
                Spacer(Modifier.height(16.dp))
                Text(signedOutReason, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(40.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRegister, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(tr("Register your business"))
                }
                OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(tr("Sign in"))
                }
            }
        }
    }
}
