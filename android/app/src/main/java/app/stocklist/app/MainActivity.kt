package app.stocklist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.stocklist.app.core.designsystem.StockListTheme
import app.stocklist.app.core.session.SessionState
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.navigation.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var session: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash stays up until the saved session has been read, so the user never sees a flash of the wrong screen.
        installSplashScreen().setKeepOnScreenCondition { session.state.value is SessionState.Loading }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StockListTheme { AppRoot() }
        }
    }
}
