package app.stocklist.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.stocklist.app.core.designsystem.LoadingState
import app.stocklist.app.core.session.SessionState
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.data.remote.MeDto
import app.stocklist.app.data.repository.AuthRepository
import app.stocklist.app.domain.permission.Permission
import app.stocklist.app.feature.account.AccountScreen
import app.stocklist.app.feature.activity.ActivityScreen
import app.stocklist.app.feature.auth.LoginRole
import app.stocklist.app.feature.auth.WelcomeScreen
import app.stocklist.app.feature.dashboard.OwnerDashboardScreen
import app.stocklist.app.feature.dashboard.StaffDashboardScreen
import app.stocklist.app.feature.auth.ChangePasswordScreen
import app.stocklist.app.feature.auth.ForgotPasswordScreen
import app.stocklist.app.feature.auth.LoginScreen
import app.stocklist.app.feature.auth.RegisterScreen
import app.stocklist.app.feature.business.BusinessProfileScreen
import app.stocklist.app.feature.catalogue.Access
import app.stocklist.app.feature.catalogue.BatchDetailScreen
import app.stocklist.app.feature.catalogue.BatchesScreen
import app.stocklist.app.feature.catalogue.ExpiryScreen
import app.stocklist.app.feature.catalogue.ItemHistoryScreen
import app.stocklist.app.feature.catalogue.ItemDetailScreen
import app.stocklist.app.feature.catalogue.PurchaseDetailScreen
import app.stocklist.app.feature.catalogue.PurchaseEditScreen
import app.stocklist.app.feature.catalogue.PurchasesScreen
import app.stocklist.app.feature.catalogue.ReceiveGoodsScreen
import app.stocklist.app.feature.catalogue.ItemEditScreen
import app.stocklist.app.feature.catalogue.ItemListScreen
import app.stocklist.app.feature.catalogue.StockListDetailScreen
import app.stocklist.app.feature.catalogue.StockListEditScreen
import app.stocklist.app.feature.catalogue.StockListsScreen
import app.stocklist.app.feature.catalogue.SupplierDetailScreen
import app.stocklist.app.feature.catalogue.SupplierEditScreen
import app.stocklist.app.feature.catalogue.SupplierListScreen
import app.stocklist.app.feature.catalogue.SyncProblemsScreen
import app.stocklist.app.feature.catalogue.WorkSection
import app.stocklist.app.feature.staff.StaffCreateScreen
import app.stocklist.app.feature.staff.StaffDetailScreen
import app.stocklist.app.feature.staff.StaffListScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    val session: SessionStore,
    private val auth: AuthRepository,
) : ViewModel() {
    init {
        viewModelScope.launch { session.load() }
    }

    /** Picks up permission changes made by the owner since the last sign-in (ignored when offline). */
    fun refreshProfile() {
        viewModelScope.launch { auth.refreshProfile() }
    }
}

/** Chooses between the sign-in flow, the forced password change, and the main app. */
@Composable
fun AppRoot(vm: RootViewModel = hiltViewModel()) {
    val state by vm.session.state.collectAsStateWithLifecycle()
    when (val s = state) {
        SessionState.Loading -> LoadingState()
        is SessionState.SignedOut -> AuthNav(s.reason)
        is SessionState.SignedIn -> {
            LaunchedEffect(s.user.userId) { vm.refreshProfile() }
            if (s.user.mustChangePassword) ChangePasswordScreen(forced = true, onDone = {})
            else MainNav(s.user, onRefreshProfile = vm::refreshProfile)
        }
    }
}

@Composable
private fun AuthNav(reason: String?) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "welcome") {
        composable("welcome") {
            WelcomeScreen(reason,
                onOwnerLogin = { nav.navigate("login/owner") },
                onStaffLogin = { nav.navigate("login/staff") },
                onRegister = { nav.navigate("register") })
        }
        composable("login/owner") {
            LoginScreen(LoginRole.OWNER, reason, onBack = { nav.popBackStack() }, onForgot = { nav.navigate("forgot") })
        }
        composable("login/staff") {
            LoginScreen(LoginRole.STAFF, reason, onBack = { nav.popBackStack() }, onForgot = {})
        }
        composable("register") { RegisterScreen(onBack = { nav.popBackStack() }) }
        composable("forgot") { ForgotPasswordScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun MainNav(user: MeDto, onRefreshProfile: () -> Unit) {
    val nav = rememberNavController()
    val perms = Permission.fromCodes(user.permissions)
    val canManageStaff = user.isOwner || Permission.STAFF_MANAGE in perms
    val canEditBusiness = user.isOwner || Permission.SETTINGS_MANAGE in perms
    val access = Access(user)
    val idArg = listOf(navArgument("id") { type = NavType.StringType })
    // PDF_GENERATE: without it no PDF button is shown (the PDF is drawn on the phone from data this user may see).
    val pdf: ((app.stocklist.app.feature.pdf.PdfJob) -> Unit)? = if (access.canMakePdf) { job -> nav.navigate(job.route) } else null
    NavHost(nav, startDestination = "home") {
        composable("home") {
            val work: @androidx.compose.runtime.Composable () -> Unit = {
                WorkSection(access,
                    onSuppliers = { nav.navigate("suppliers") },
                    onItems = { nav.navigate("items") },
                    onLists = { nav.navigate("lists") },
                    onProblems = { nav.navigate("sync-problems") },
                    onPurchases = { nav.navigate("purchases") },
                    onExpiry = { f -> nav.navigate("expiry?filter=$f") },
                    onBatches = { nav.navigate("batches") },
                    onReports = { nav.navigate("reports") })
            }
            if (user.isOwner) {
                OwnerDashboardScreen(user,
                    onBusiness = { nav.navigate("business") },
                    onStaff = { nav.navigate("staff") },
                    onActivity = { nav.navigate("activity") },
                    onAccount = { nav.navigate("account") },
                    work = work)
            } else {
                StaffDashboardScreen(user,
                    onBusiness = { nav.navigate("business") },
                    onStaff = { nav.navigate("staff") },
                    onAccount = { nav.navigate("account") },
                    onRefresh = onRefreshProfile,
                    work = work)
            }
        }
        composable("sync-problems") { SyncProblemsScreen(onBack = { nav.popBackStack() }) }
        if (access.canMakePdf) {
            composable(app.stocklist.app.feature.pdf.PdfJob.ROUTE, arguments = listOf("job", "id", "from", "to", "filter").map { a ->
                navArgument(a) { type = NavType.StringType; nullable = true }
            }) {
                app.stocklist.app.feature.pdf.PdfPreviewScreen(canShare = access.canSharePdf, onBack = { nav.popBackStack() })
            }
        }
        if (access.canViewReports) {
            composable("reports") {
                app.stocklist.app.feature.pdf.ReportsScreen(access.canViewProfitReport, onBack = { nav.popBackStack() }, onPdf = pdf)
            }
        }
        if (access.canViewSuppliers) {
            composable("suppliers") {
                SupplierListScreen(access, onBack = { nav.popBackStack() }, onAdd = { nav.navigate("supplier-edit") },
                    onOpen = { nav.navigate("supplier/$it") })
            }
            composable("supplier/{id}", arguments = idArg) {
                SupplierDetailScreen(access, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("supplier-edit?id=$it") },
                    onOpenPurchase = { nav.navigate("purchase/$it") }, onAllPurchases = { nav.navigate("purchases?supplierId=$it") },
                    onPdf = pdf)
            }
            composable("supplier-edit?id={id}", arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true })) {
                SupplierEditScreen(onDone = { nav.popBackStack() })
            }
        }
        if (access.canViewItems) {
            composable("items") {
                ItemListScreen(access, onBack = { nav.popBackStack() }, onAdd = { nav.navigate("item-edit") },
                    onOpen = { nav.navigate("item/$it") })
            }
            composable("item/{id}", arguments = idArg) {
                ItemDetailScreen(access, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("item-edit?id=$it") },
                    onHistory = { nav.navigate("item-history/$it") }, onBatches = { nav.navigate("batches?itemId=$it") })
            }
            composable("item-edit?id={id}&name={name}&return={return}", arguments = listOf(
                navArgument("id") { type = NavType.StringType; nullable = true },
                navArgument("name") { type = NavType.StringType; nullable = true },
                navArgument("return") { type = NavType.BoolType; defaultValue = false },
            )) { entry ->
                val handBack = entry.arguments?.getBoolean("return") == true
                ItemEditScreen(access, onDone = { nav.popBackStack() }, onCreated = { id ->
                    if (handBack) nav.previousBackStackEntry?.savedStateHandle?.set("newItemId", id)
                })
            }
        }
        if (access.canViewPurchases) {
            composable("purchases?supplierId={supplierId}", arguments = listOf(navArgument("supplierId") { type = NavType.StringType; nullable = true })) {
                PurchasesScreen(access, onBack = { nav.popBackStack() }, onNew = { nav.navigate("purchase-edit") },
                    onOpen = { nav.navigate("purchase/$it") }, onPdf = pdf)
            }
            composable("purchase/{id}", arguments = idArg) {
                PurchaseDetailScreen(access, onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("purchase-edit?id=$it") },
                    onReceive = { nav.navigate("purchase-receive/$it") }, onPdf = pdf)
            }
            composable("purchase-receive/{id}", arguments = idArg) {
                ReceiveGoodsScreen(onBack = { nav.popBackStack() })
            }
            composable("purchase-edit?id={id}&listId={listId}", arguments = listOf(
                navArgument("id") { type = NavType.StringType; nullable = true },
                navArgument("listId") { type = NavType.StringType; nullable = true },
            )) { entry ->
                val newItem by entry.savedStateHandle.getStateFlow<String?>("newItemId", null).collectAsStateWithLifecycle()
                PurchaseEditScreen(onBack = { nav.popBackStack() }, newItemId = newItem,
                    onNewItemHandled = { entry.savedStateHandle["newItemId"] = null },
                    onCreateItem = { name -> nav.navigate("item-edit?name=${android.net.Uri.encode(name)}&return=true") }, onSaved = { saved ->
                    nav.popBackStack()
                    val shown = nav.currentBackStackEntry?.takeIf { it.destination.route == "purchase/{id}" }?.arguments?.getString("id")
                    if (shown != saved) nav.navigate("purchase/$saved")
                })
            }
        }
        if (access.canViewPurchases) {
            composable("item-history/{id}", arguments = idArg) {
                ItemHistoryScreen(onBack = { nav.popBackStack() }, onOpenPurchase = { nav.navigate("purchase/$it") }, onPdf = pdf)
            }
        }
        if (access.canViewExpiry) {
            composable("expiry?filter={filter}", arguments = listOf(navArgument("filter") { type = NavType.StringType; nullable = true })) {
                ExpiryScreen(onBack = { nav.popBackStack() }, onOpenBatch = { nav.navigate("batch/$it") },
                    onBatches = { nav.navigate("batches") }, onPdf = pdf)
            }
            composable("batches?itemId={itemId}", arguments = listOf(navArgument("itemId") { type = NavType.StringType; nullable = true })) {
                BatchesScreen(onBack = { nav.popBackStack() }, onOpenBatch = { nav.navigate("batch/$it") })
            }
            composable("batch/{id}", arguments = idArg) {
                BatchDetailScreen(access, onBack = { nav.popBackStack() }, onOpenPurchase = { nav.navigate("purchase/$it") })
            }
        }
        if (access.canViewLists) {
            composable("lists") {
                StockListsScreen(access, onBack = { nav.popBackStack() }, onNew = { nav.navigate("list-edit") },
                    onOpen = { nav.navigate("list/$it") })
            }
            composable("list/{id}", arguments = idArg) {
                StockListDetailScreen(access, onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("list-edit?id=$it") },
                    onOpenCopy = { copy -> nav.navigate("list-edit?id=$copy") },
                    onRecordPurchase = { listId -> nav.navigate("purchase-edit?listId=$listId") }, onPdf = pdf)
            }
            composable("list-edit?id={id}", arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true })) { entry ->
                val newItem by entry.savedStateHandle.getStateFlow<String?>("newItemId", null).collectAsStateWithLifecycle()
                StockListEditScreen(onBack = { nav.popBackStack() }, canCreateItem = access.has(Permission.ITEM_ADD), newItemId = newItem,
                    onNewItemHandled = { entry.savedStateHandle["newItemId"] = null },
                    onCreateItem = { name -> nav.navigate("item-edit?name=${android.net.Uri.encode(name)}&return=true") }, onSaved = { saved ->
                    nav.popBackStack()
                    // After creating, show the saved list; after editing, the list screen underneath is already it.
                    val shown = nav.currentBackStackEntry?.takeIf { it.destination.route == "list/{id}" }?.arguments?.getString("id")
                    if (shown != saved) nav.navigate("list/$saved")
                })
            }
        }
        if (user.isOwner) {
            composable("activity") { ActivityScreen(onBack = { nav.popBackStack() }) }
        }
        composable("business") { BusinessProfileScreen(canEditBusiness, onBack = { nav.popBackStack() }) }
        composable("account") {
            AccountScreen(user, onBack = { nav.popBackStack() }, onChangePassword = { nav.navigate("password") })
        }
        composable("password") { ChangePasswordScreen(forced = false, onDone = { nav.popBackStack() }) }
        if (canManageStaff) {
            composable("staff") {
                StaffListScreen(onBack = { nav.popBackStack() }, onAdd = { nav.navigate("staff/new") },
                    onOpen = { id -> nav.navigate("staff/$id") })
            }
            composable("staff/new") { StaffCreateScreen(onDone = { nav.popBackStack() }) }
            composable("staff/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                StaffDetailScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
