package app.stocklist.supplier.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.stocklist.supplier.core.session.SessionState
import app.stocklist.supplier.core.session.SessionStore
import app.stocklist.supplier.feature.account.BusinessDetailsScreen
import app.stocklist.supplier.feature.account.HomeScreen
import app.stocklist.supplier.feature.account.SettingsScreen
import app.stocklist.supplier.feature.auth.CheckEmailScreen
import app.stocklist.supplier.feature.auth.ForgotPasswordScreen
import app.stocklist.supplier.feature.auth.LoginScreen
import app.stocklist.supplier.feature.auth.RegisterScreen
import app.stocklist.supplier.feature.network.CatalogueScreen
import app.stocklist.supplier.feature.network.ConnectionScreen
import app.stocklist.supplier.feature.network.ConnectionsScreen
import app.stocklist.supplier.feature.network.ListingScreen
import app.stocklist.supplier.feature.network.OrderScreen
import app.stocklist.supplier.feature.network.OrdersScreen
import app.stocklist.supplier.feature.network.ProductFormScreen
import app.stocklist.supplier.feature.welcome.WelcomeScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(session: SessionStore) : ViewModel() {
    val state = session.state
}

/** Signed out: welcome, registration and sign-in. Signed in: the account screens. */
@Composable
fun AppRoot(vm: RootViewModel = hiltViewModel()) {
    when (val s = vm.state.collectAsState().value) {
        SessionState.Loading -> {}
        is SessionState.SignedOut -> SignedOutNav(s.reason)
        is SessionState.SignedIn -> SignedInNav()
    }
}

@Composable
private fun SignedOutNav(reason: String?) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "welcome") {
        composable("welcome") {
            WelcomeScreen(reason, onRegister = { nav.navigate("register") }, onSignIn = { nav.navigate("login") })
        }
        composable("register") {
            RegisterScreen(onBack = { nav.popBackStack() }, onRegistered = { email ->
                nav.navigate("check-email/" + Uri.encode(email)) { popUpTo("welcome") }
            })
        }
        composable("check-email/{email}", arguments = listOf(navArgument("email") { type = NavType.StringType })) {
            val email = it.arguments?.getString("email").orEmpty()
            CheckEmailScreen(email, onSignIn = { nav.navigate("login") { popUpTo("welcome") } })
        }
        composable("login") {
            LoginScreen(reason, onBack = { nav.popBackStack() }, onForgot = { nav.navigate("forgot") })
        }
        composable("forgot") {
            ForgotPasswordScreen(onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun SignedInNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onDetails = { nav.navigate("details?edit=false") },
                onCorrect = { nav.navigate("details?edit=true") },
                onSettings = { nav.navigate("settings") },
                onNetwork = { route -> nav.navigate(route) },
            )
        }
        composable("details?edit={edit}", arguments = listOf(navArgument("edit") {
            type = NavType.BoolType
            defaultValue = false
        })) {
            BusinessDetailsScreen(it.arguments?.getBoolean("edit") ?: false, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        // Supplier Network (approved businesses; the server refuses everyone else).
        composable(NetworkRoutes.LISTING) { ListingScreen(onBack = { nav.popBackStack() }) }
        composable(NetworkRoutes.CONNECTIONS) {
            ConnectionsScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("connection/$it") })
        }
        composable("connection/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            ConnectionScreen(onBack = { nav.popBackStack() })
        }
        composable(NetworkRoutes.CATALOGUE) {
            CatalogueScreen(
                onBack = { nav.popBackStack() },
                onAdd = { nav.navigate("product") },
                onOpen = { nav.navigate("product?id=$it") },
            )
        }
        composable("product?id={id}", arguments = listOf(navArgument("id") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        })) {
            ProductFormScreen(onDone = { nav.popBackStack() })
        }
        composable(NetworkRoutes.ORDERS) {
            OrdersScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("order/$it") })
        }
        composable("order/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            OrderScreen(onBack = { nav.popBackStack() })
        }
    }
}

/** Start screens of the Supplier Network section. */
object NetworkRoutes {
    const val LISTING = "listing"
    const val CONNECTIONS = "connections"
    const val CATALOGUE = "catalogue"
    const val ORDERS = "orders"
}
