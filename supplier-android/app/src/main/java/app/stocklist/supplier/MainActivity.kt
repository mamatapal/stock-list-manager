package app.stocklist.supplier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.stocklist.supplier.core.designsystem.SupplierTheme
import app.stocklist.supplier.core.session.SessionState
import app.stocklist.supplier.core.session.SessionStore
import app.stocklist.supplier.navigation.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var session: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash stays up until the saved session has been read, so the wrong screen never flashes.
        installSplashScreen().setKeepOnScreenCondition { session.state.value is SessionState.Loading }
        super.onCreate(savedInstanceState)
        if (session.state.value is SessionState.Loading) lifecycleScope.launch { session.load() }
        enableEdgeToEdge()
        setContent {
            SupplierTheme { AppRoot() }
        }
    }
}
