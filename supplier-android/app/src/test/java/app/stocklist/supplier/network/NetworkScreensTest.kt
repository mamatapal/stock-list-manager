package app.stocklist.supplier.network

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.stocklist.supplier.core.designsystem.SupplierTheme
import app.stocklist.supplier.core.i18n.AppLanguage
import app.stocklist.supplier.core.i18n.Lang
import app.stocklist.supplier.core.util.ProductRules
import app.stocklist.supplier.data.remote.ApiError
import app.stocklist.supplier.data.remote.ConnectionDto
import app.stocklist.supplier.data.remote.EventDto
import app.stocklist.supplier.data.remote.ListingDto
import app.stocklist.supplier.data.remote.OrderDto
import app.stocklist.supplier.data.remote.OrderLineDto
import app.stocklist.supplier.data.remote.OrgDto
import app.stocklist.supplier.data.remote.ShopSummaryDto
import app.stocklist.supplier.feature.account.NetworkSection
import app.stocklist.supplier.feature.network.ConnectionContent
import app.stocklist.supplier.feature.network.ConnectionsContent
import app.stocklist.supplier.feature.network.ListingContent
import app.stocklist.supplier.feature.network.Load
import app.stocklist.supplier.feature.network.OrderContent
import app.stocklist.supplier.feature.network.OrdersContent
import app.stocklist.supplier.feature.network.ProductFields
import app.stocklist.supplier.feature.network.ProductFormContent
import app.stocklist.supplier.navigation.NetworkRoutes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Supplier Network screens on the JVM (Robolectric): what is shown for each state, that every action asks for
 * confirmation and runs once, that required reasons are enforced, loading and error states, navigation and the two
 * languages. Screens are tested through their stateless *Content composables, with the server answers as data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h914dp")
class NetworkScreensTest {
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun english() = Lang.setForTest(AppLanguage.ENGLISH)

    @After
    fun reset() = Lang.setForTest(AppLanguage.ENGLISH)

    private val shop = ShopSummaryDto("Agarwal Stores", "Pune", "Maharashtra")
    private val at = "2026-11-18T05:30:00Z"

    private fun connection(status: String, id: String = "c1") = ConnectionDto(
        id, status, shop, requestedAt = at,
        history = listOf(EventDto("SHOP", toStatus = "REQUESTED", at = at)),
    )

    private val line = OrderLineDto(
        id = "l1", lineNo = 1, productId = "p1", productNameEn = "Toor dal", productNameHi = "तूर दाल", unit = "kg",
        quantity = "10", unitPrice = "120.00", lineAmount = "1200.00",
    )

    private fun order(status: String) = OrderDto(
        "0f3a9c1d-1111-2222-3333-444455556666", status, shop, note = "Before Monday", totalAmount = "1200.00",
        lineCount = 1, createdAt = at, lines = listOf(line), history = listOf(EventDto("SHOP", toStatus = "SENT", at = at)),
    )

    /** Scrolls a node of a scrolling screen into view and checks it is shown (screens are longer than the phone). */
    private fun SemanticsNodeInteraction.shown(): SemanticsNodeInteraction = performScrollTo().assertIsDisplayed()

    private fun confirmInDialog(label: String) =
        rule.onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()

    // ------------------------------------------------------------------ navigation

    @Test
    fun theHomeSectionOpensEachSupplierNetworkScreen() {
        val opened = mutableListOf<String>()
        rule.setContent { SupplierTheme { NetworkSection { opened += it } } }
        rule.onNodeWithText("Supplier Network").assertIsDisplayed()
        rule.onNodeWithTag("open-orders").performClick()
        rule.onNodeWithTag("open-connections").performClick()
        rule.onNodeWithTag("open-catalogue").performClick()
        rule.onNodeWithTag("open-listing").performClick()
        assertEquals(listOf(NetworkRoutes.ORDERS, NetworkRoutes.CONNECTIONS, NetworkRoutes.CATALOGUE, NetworkRoutes.LISTING), opened)
    }

    @Test
    fun aConnectionInTheListOpensItsDetailsAndBackReturns() {
        rule.setContent {
            SupplierTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "list") {
                    composable("list") {
                        ConnectionsContent(Load.Ready(listOf(connection("REQUESTED"))), false, null, {}, {}) { nav.navigate("detail/$it") }
                    }
                    composable("detail/{id}") {
                        ConnectionContent(Load.Ready(connection("REQUESTED")), false, null, null, { nav.popBackStack() }, {}, {}, {}, {})
                    }
                }
            }
        }
        rule.onNodeWithTag("connection-c1").performClick()
        rule.onNodeWithText("Shop connection").assertIsDisplayed()
        rule.onNodeWithTag("accept").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag("connection-c1").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ connections

    @Test
    fun connectionTabsSeparateRequestsConnectedAndPast() {
        val all = listOf(connection("REQUESTED", "a"), connection("ACTIVE", "b"), connection("ENDED", "c"), connection("DECLINED", "d"))
        rule.setContent { SupplierTheme { ConnectionsContent(Load.Ready(all), false, null, {}, {}, {}) } }
        rule.onNodeWithText("Requests (1)").assertIsDisplayed()
        rule.onNodeWithText("Connected (1)").assertIsDisplayed()
        rule.onNodeWithText("Past (2)").assertIsDisplayed()
        rule.onNodeWithTag("connection-a").assertIsDisplayed()
        rule.onNodeWithTag("tab-PAST").performClick()
        rule.onNodeWithTag("connection-c").assertIsDisplayed()
        rule.onNodeWithTag("connection-d").assertIsDisplayed()
        rule.onNodeWithTag("connection-a").assertDoesNotExist()
    }

    @Test
    fun acceptingARequestAsksFirstAndRunsOnce() {
        var accepted = 0
        rule.setContent {
            SupplierTheme { ConnectionContent(Load.Ready(connection("REQUESTED")), false, null, null, {}, {}, { accepted++ }, {}, {}) }
        }
        rule.onNodeWithTag("accept").performClick()
        assertEquals("nothing is sent before the confirmation", 0, accepted)
        rule.onNodeWithText("Accept this shop?").assertIsDisplayed()
        confirmInDialog("Accept")
        assertEquals(1, accepted)
        rule.onNodeWithText("Accept this shop?").assertDoesNotExist()
    }

    @Test
    fun decliningTakesAnOptionalReason() {
        val declined = mutableListOf<String?>()
        rule.setContent {
            SupplierTheme { ConnectionContent(Load.Ready(connection("REQUESTED")), false, null, null, {}, {}, {}, { declined += it }, {}) }
        }
        rule.onNodeWithTag("decline").performClick()
        rule.onNodeWithTag("confirm").assertIsEnabled()
        rule.onNodeWithTag("reason").performTextInput("We do not deliver to Pune")
        rule.onNodeWithTag("confirm").performClick()
        assertEquals(listOf<String?>("We do not deliver to Pune"), declined)
    }

    @Test
    fun activeConnectionsCanBeEndedAndClosedOnesOfferNothing() {
        val ended = mutableListOf<String?>()
        var c by mutableStateOf(connection("ACTIVE"))
        rule.setContent { SupplierTheme { ConnectionContent(Load.Ready(c), false, null, null, {}, {}, {}, {}, { ended += it }) } }
        rule.onNodeWithTag("accept").assertDoesNotExist()
        rule.onNodeWithTag("end").performClick()
        rule.onNodeWithText("End this connection?").assertIsDisplayed()
        rule.onNodeWithTag("confirm").performClick()
        assertEquals(listOf<String?>(""), ended)

        c = connection("DECLINED")
        rule.onNodeWithTag("end").assertDoesNotExist()
        rule.onNodeWithText("This connection is closed. Only the shop can send a new request.").assertIsDisplayed()
    }

    @Test
    fun actionsAreOffWhileTheServerIsAnswering() {
        rule.setContent { SupplierTheme { ConnectionContent(Load.Ready(connection("REQUESTED")), true, null, null, {}, {}, {}, {}, {}) } }
        rule.onNodeWithTag("busy").assertIsDisplayed()
        rule.onNodeWithTag("accept").assertIsNotEnabled()
        rule.onNodeWithTag("decline").assertIsNotEnabled()
    }

    // ------------------------------------------------------------------ orders

    @Test
    fun aNewOrderShowsItsLinesAndCanBeAccepted() {
        var accepted = 0
        rule.setContent { SupplierTheme { OrderContent(Load.Ready(order("SENT")), false, null, null, {}, {}, { accepted++ }, {}, {}) } }
        rule.onNodeWithText("Order #0F3A9C1D").assertIsDisplayed()
        rule.onNodeWithTag("status").assertTextEquals("New order")
        rule.onNodeWithText("Toor dal").shown()
        rule.onNodeWithText("10 kg × ₹120.00 = ₹1,200.00").shown()
        rule.onNodeWithTag("total").shown().assertTextEquals("₹1,200.00")
        rule.onNodeWithTag("dispatch").assertDoesNotExist()
        rule.onNodeWithTag("accept").performScrollTo().performClick()
        confirmInDialog("Accept order")
        assertEquals(1, accepted)
    }

    @Test
    fun rejectingNeedsAReason() {
        val rejected = mutableListOf<String>()
        rule.setContent { SupplierTheme { OrderContent(Load.Ready(order("SENT")), false, null, null, {}, {}, {}, { rejected += it }, {}) } }
        rule.onNodeWithTag("reject").performClick()
        rule.onNodeWithText("Reject this order?").assertIsDisplayed()
        rule.onNodeWithTag("confirm").assertIsNotEnabled()
        rule.onNodeWithTag("reason").performTextInput("Out of stock until Friday")
        rule.onNodeWithTag("confirm").assertIsEnabled().performClick()
        assertEquals(listOf("Out of stock until Friday"), rejected)
    }

    @Test
    fun anAcceptedOrderIsDispatchedWithAnOptionalNote() {
        val dispatched = mutableListOf<String?>()
        rule.setContent { SupplierTheme { OrderContent(Load.Ready(order("ACCEPTED")), false, null, null, {}, {}, {}, {}, { dispatched += it }) } }
        rule.onNodeWithTag("accept").assertDoesNotExist()
        rule.onNodeWithTag("dispatch").performClick()
        rule.onNodeWithText("Mark as dispatched?").assertIsDisplayed()
        rule.onNodeWithTag("confirm").performClick()
        assertEquals(listOf<String?>(null), dispatched)
    }

    @Test
    fun aReceivedOrderShowsTheShortageAndOffersNoActions() {
        val received = order("RECEIVED").copy(
            receiptStatus = "SHORT", receivedAt = at,
            lines = listOf(line.copy(receivedQuantity = "8", shortageQuantity = "2", receiptStatus = "SHORT")),
        )
        rule.setContent { SupplierTheme { OrderContent(Load.Ready(received), false, null, null, {}, {}, {}, {}, {}) } }
        rule.onNodeWithTag("status").assertTextEquals("Received by the shop")
        rule.onNodeWithText("Received: 8 kg").shown()
        rule.onNodeWithText("Short: 2 kg").shown()
        rule.onNodeWithTag("accept").assertDoesNotExist()
        rule.onNodeWithTag("dispatch").assertDoesNotExist()
    }

    @Test
    fun theInboxGroupsOrdersAndSearches() {
        val all = listOf(order("SENT"), order("DISPATCHED").copy(id = "1b2c3d4e-0000-0000-0000-000000000002",
            shop = ShopSummaryDto("Gupta Kirana", "Indore", null)))
        val opened = mutableListOf<String>()
        rule.setContent { SupplierTheme { OrdersContent(Load.Ready(all), false, null, {}, {}) { opened += it } } }
        rule.onNodeWithText("To do (1)").assertIsDisplayed()
        rule.onNodeWithTag("order-1b2c3d4e-0000-0000-0000-000000000002").assertDoesNotExist()
        rule.onNodeWithTag("filter-ALL").performClick()
        rule.onNodeWithTag("search").performTextInput("gupta")
        rule.onNodeWithTag("order-0f3a9c1d-1111-2222-3333-444455556666").assertDoesNotExist()
        rule.onNodeWithTag("order-1b2c3d4e-0000-0000-0000-000000000002").performClick()
        assertEquals(listOf("1b2c3d4e-0000-0000-0000-000000000002"), opened)
    }

    // ------------------------------------------------------------------ loading and errors

    @Test
    fun loadingThenAnErrorWithTryAgain() {
        var load by mutableStateOf<Load<List<OrderDto>>>(Load.Loading)
        var retried = 0
        rule.setContent { SupplierTheme { OrdersContent(load, false, null, {}, { retried++ }, {}) } }
        rule.onNodeWithTag("search").assertDoesNotExist()
        load = Load.Failed(ApiError(0, ApiError.NETWORK, "No connection to the server. Check the internet and try again."))
        rule.onNodeWithText("No connection to the server. Check the internet and try again.").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
        load = Load.Ready(emptyList())
        rule.onNodeWithText("No orders yet. Connected shops send orders from their Stock List Manager app.").assertIsDisplayed()
    }

    @Test
    fun aFailedActionKeepsTheOrderOnScreenWithTheMessage() {
        rule.setContent {
            SupplierTheme {
                OrderContent(Load.Ready(order("SENT")), false, "This order has already changed. Refresh to see its current status",
                    null, {}, {}, {}, {}, {})
            }
        }
        rule.onNodeWithText("This order has already changed. Refresh to see its current status").shown()
        rule.onNodeWithText("Toor dal").shown()
    }

    // ------------------------------------------------------------------ catalogue form

    @Test
    fun theProductFormShowsMistakesBeforeAnythingIsSent() {
        var saved = 0
        rule.setContent {
            SupplierTheme {
                var fields by remember { mutableStateOf(ProductFields("", "", "", "", "")) }
                var errors by remember { mutableStateOf<ProductRules.Errors?>(null) }
                ProductFormContent(
                    isNew = true, load = Load.Ready(null), fields = fields, onChange = { fields = it }, errors = errors,
                    busy = false, error = null, onBack = {}, onRetry = {},
                    onSave = {
                        val e = ProductRules.check(fields.nameEn, fields.nameHi, fields.unit, fields.pack, fields.price)
                        errors = e
                        if (e.none) saved++
                    },
                    onSetActive = {},
                )
            }
        }
        rule.onNodeWithText("Add product").assertIsDisplayed()
        rule.onNodeWithTag("toggle-active").assertDoesNotExist()
        rule.onNodeWithTag("save").performScrollTo().performClick()
        rule.onNodeWithText("Enter the price").shown()
        assertEquals(0, saved)
        rule.onNodeWithTag("nameEn").performTextInput("Toor dal")
        rule.onNodeWithTag("unit").performTextInput("kg")
        rule.onNodeWithTag("price").performTextInput("12.345")
        rule.onNodeWithTag("save").performScrollTo().performClick()
        rule.onNodeWithText("Enter the price as a number, for example 240 or 240.50").shown()
        assertEquals(0, saved)
        rule.onNodeWithTag("price").performTextReplacement("120.50")
        rule.onNodeWithTag("save").performScrollTo().performClick()
        assertEquals(1, saved)
    }

    // ------------------------------------------------------------------ directory listing

    @Test
    fun theListingSwitchAsksFirstAndShowsWhatShopsSee() {
        val changes = mutableListOf<Boolean>()
        val org = OrgDto(
            id = "o1", legalName = "Sharma Traders Pvt Ltd", tradeName = "Sharma Traders", contactPerson = "Ravi",
            mobile = "9876543210", email = "ravi@example.com", address = "MG Road", city = "Indore", state = "MP",
            pinCode = "452001", status = "APPROVED",
        )
        rule.setContent {
            SupplierTheme { ListingContent(org, Load.Ready(ListingDto(false, "APPROVED")), false, null, {}, {}) { changes += it } }
        }
        rule.onNodeWithText("Not listed: shops cannot find you").assertIsDisplayed()
        rule.onNodeWithText("Sharma Traders").assertIsDisplayed()
        rule.onNodeWithText("9876543210").assertDoesNotExist()
        rule.onNodeWithTag("listed").performClick()
        assertEquals("nothing changes before the confirmation", emptyList<Boolean>(), changes)
        confirmInDialog("Show")
        assertEquals(listOf(true), changes)
    }

    // ------------------------------------------------------------------ languages

    @Test
    fun bothLanguagesAreShownAndTheChosenOneComesFirst() {
        Lang.setForTest(AppLanguage.ENGLISH, showBoth = true)
        rule.setContent { SupplierTheme { OrdersContent(Load.Ready(emptyList()), false, null, {}, {}, {}) } }
        rule.onNodeWithText("Purchase orders / खरीद ऑर्डर").assertIsDisplayed()
        Lang.setForTest(AppLanguage.HINDI, showBoth = true)
        rule.onNodeWithText("खरीद ऑर्डर / Purchase orders").assertIsDisplayed()
        rule.onNodeWithText("करना है (0) / To do (0)").assertIsDisplayed()
    }

    @Test
    fun theChosenLanguageIsKeptOnThePhone() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Lang.set(context, AppLanguage.HINDI)
        Lang.setForTest(AppLanguage.ENGLISH)
        Lang.init(context)
        assertEquals(AppLanguage.HINDI, Lang.language)
        Lang.set(context, AppLanguage.ENGLISH)
        Lang.init(context)
        assertEquals(AppLanguage.ENGLISH, Lang.language)
    }
}
