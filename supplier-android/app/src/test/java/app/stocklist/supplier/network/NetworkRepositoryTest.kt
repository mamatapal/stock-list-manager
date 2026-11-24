package app.stocklist.supplier.network

import app.stocklist.supplier.core.i18n.AppLanguage
import app.stocklist.supplier.core.i18n.Lang
import app.stocklist.supplier.data.remote.ApiError
import app.stocklist.supplier.data.remote.ApiResult
import app.stocklist.supplier.data.remote.ProductRequest
import app.stocklist.supplier.data.repository.NetworkRepository
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Every Supplier Network call of the app against a mock server: the exact path, method and body the backend expects
 * (SupplierNetworkController), answers parsed as the backend sends them (NetworkDtos, including fields the app does
 * not use), and server errors turned into the app's own English/Hindi text.
 */
class NetworkRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: NetworkRepository

    @Before
    fun start() {
        Lang.setForTest(AppLanguage.ENGLISH)
        server = MockWebServer()
        server.start()
        repo = NetworkRepository(TestApis(server.url("/").toString(), token = { "test-token" }).network)
    }

    @After
    fun stop() {
        server.shutdown()
        Lang.setForTest(AppLanguage.ENGLISH)
    }

    private fun answer(body: String, code: Int = 200) =
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))

    private fun problem(code: String, status: Int) =
        answer("""{"type":"about:blank","title":"x","status":$status,"code":"$code","detail":"server text"}""", status)

    private fun <T> ok(r: ApiResult<T>): T {
        assertTrue("expected success, got $r", r is ApiResult.Ok)
        return (r as ApiResult.Ok).value
    }

    private fun failure(r: ApiResult<*>): ApiError {
        assertTrue("expected failure, got $r", r is ApiResult.Failure)
        return (r as ApiResult.Failure).error
    }

    // A connection exactly as the backend writes it (SupplierConnectionDto), with an extra unknown field.
    private val connectionJson = """
        {"id":"c1","status":"REQUESTED","shop":{"name":"Agarwal Stores","city":"Pune","state":"Maharashtra"},
         "requestedAt":"2026-11-18T05:30:00Z","respondedAt":null,"endedAt":null,"endedBy":null,"statusReason":null,
         "version":0,"history":[{"actorType":"SHOP","fromStatus":null,"toStatus":"REQUESTED","reason":null,
         "receiptStatus":null,"at":"2026-11-18T05:30:00Z"}],"somethingNew":true}
    """.trimIndent()

    private val lineJson = "{\"id\":\"l1\",\"lineNo\":1,\"productId\":\"p1\",\"productNameEn\":\"Toor dal\"," +
        "\"productNameHi\":\"तूर दाल\",\"unit\":\"kg\",\"pack\":\"30 kg bag\",\"quantity\":\"10\",\"unitPrice\":\"120.00\"," +
        "\"lineAmount\":\"1200.00\",\"itemId\":null,\"itemUnitId\":null,\"unitsPerPack\":\"1\"," +
        "\"receivedQuantity\":null,\"receivedUnitPrice\":null,\"shortageQuantity\":null,\"receiptStatus\":null}"

    private val orderJson = """
        {"id":"0f3a9c1d-1111-2222-3333-444455556666","status":"SENT",
         "shop":{"name":"Agarwal Stores","city":"Pune","state":"Maharashtra"},"note":"Before Monday",
         "totalAmount":"1200.00","lineCount":1,"createdAt":"2026-11-18T05:30:00Z","updatedAt":"2026-11-18T05:30:00Z",
         "acceptedAt":null,"rejectedAt":null,"dispatchedAt":null,"receivedAt":null,"cancelledAt":null,
         "deliveryFailedAt":null,"statusReason":null,"receiptStatus":null,"version":0,
         "lines":[$lineJson],
         "history":[]}
    """.trimIndent()

    @Test
    fun directoryListingIsReadAndChanged() = runTest {
        answer("""{"listed":false,"status":"APPROVED"}""")
        val listing = ok(repo.listing())
        assertFalse(listing.listed)
        assertEquals("GET", server.takeRequest().method)

        answer("""{"listed":true,"status":"APPROVED"}""")
        assertTrue(ok(repo.setListing(true)).listed)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/v1/supplier-portal/directory-listing", put.path)
        assertEquals("""{"listed":true}""", put.body.readUtf8())
        assertEquals("Bearer test-token", put.getHeader("Authorization"))
    }

    @Test
    fun connectionsAreListedAcceptedDeclinedAndEnded() = runTest {
        answer("[$connectionJson]")
        val list = ok(repo.connections())
        assertEquals("Agarwal Stores", list.single().shop.name)
        assertEquals("REQUESTED", list.single().history.single().toStatus)
        assertEquals("/api/v1/supplier-portal/connections", server.takeRequest().path)

        answer(connectionJson.replace("\"REQUESTED\",\"shop\"", "\"ACTIVE\",\"shop\""))
        assertEquals("ACTIVE", ok(repo.acceptConnection("c1")).status)
        val accept = server.takeRequest()
        assertEquals("POST", accept.method)
        assertEquals("/api/v1/supplier-portal/connections/c1/accept", accept.path)

        answer(connectionJson)
        ok(repo.declineConnection("c1", "  Not delivering to Pune  "))
        val decline = server.takeRequest()
        assertEquals("/api/v1/supplier-portal/connections/c1/decline", decline.path)
        assertEquals("""{"reason":"Not delivering to Pune"}""", decline.body.readUtf8())

        answer(connectionJson)
        ok(repo.endConnection("c1", "   "))
        val end = server.takeRequest()
        assertEquals("/api/v1/supplier-portal/connections/c1/end", end.path)
        assertEquals("a blank reason is not sent", "{}", end.body.readUtf8())
    }

    @Test
    fun catalogueProductsAreCreatedChangedAndSwitchedOnAndOff() = runTest {
        val product = """{"id":"p1","nameEn":"Toor dal","nameHi":"तूर दाल","unit":"kg","pack":null,"price":"120.00",
            "active":true,"version":0,"createdAt":"2026-11-18T05:30:00Z","updatedAt":"2026-11-18T05:30:00Z"}"""
        val request = ProductRequest("Toor dal", "तूर दाल", "kg", null, "120")

        answer(product, 201)
        assertEquals("p1", ok(repo.saveProduct(null, request)).id)
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/api/v1/supplier-portal/products", create.path)
        assertEquals("""{"nameEn":"Toor dal","nameHi":"तूर दाल","unit":"kg","price":"120"}""", create.body.readUtf8())

        answer(product)
        ok(repo.saveProduct("p1", request))
        val update = server.takeRequest()
        assertEquals("PUT", update.method)
        assertEquals("/api/v1/supplier-portal/products/p1", update.path)

        answer(product.replace("\"active\":true", "\"active\":false"))
        assertFalse(ok(repo.setProductActive("p1", false)).active)
        assertEquals("/api/v1/supplier-portal/products/p1/deactivate", server.takeRequest().path)
        answer(product)
        assertTrue(ok(repo.setProductActive("p1", true)).active)
        assertEquals("/api/v1/supplier-portal/products/p1/activate", server.takeRequest().path)

        answer("[$product]")
        assertEquals(1, ok(repo.products()).size)
    }

    @Test
    fun ordersAreListedOpenedAcceptedRejectedAndDispatched() = runTest {
        answer("[$orderJson]")
        val orders = ok(repo.orders())
        assertEquals("SENT", orders.single().status)
        assertEquals("/api/v1/supplier-portal/orders", server.takeRequest().path)

        answer(orderJson)
        val order = ok(repo.order("o1"))
        val line = order.lines.single()
        assertEquals("Toor dal", line.productNameEn)
        assertEquals("10", line.quantity)
        assertEquals("120.00", line.unitPrice)
        assertNull(line.receivedQuantity)
        assertEquals("/api/v1/supplier-portal/orders/o1", server.takeRequest().path)

        answer(orderJson.replace("\"SENT\"", "\"ACCEPTED\""))
        assertEquals("ACCEPTED", ok(repo.acceptOrder("o1")).status)
        assertEquals("/api/v1/supplier-portal/orders/o1/accept", server.takeRequest().path)

        answer(orderJson.replace("\"SENT\"", "\"REJECTED\""))
        ok(repo.rejectOrder("o1", "Out of stock"))
        val reject = server.takeRequest()
        assertEquals("/api/v1/supplier-portal/orders/o1/reject", reject.path)
        assertEquals("""{"reason":"Out of stock"}""", reject.body.readUtf8())

        answer(orderJson.replace("\"SENT\"", "\"DISPATCHED\"").replace("\"dispatchedAt\":null", "\"dispatchedAt\":\"2026-11-18T09:00:00Z\""))
        val dispatched = ok(repo.dispatchOrder("o1", "Truck MH12 AB 1234"))
        assertEquals("2026-11-18T09:00:00Z", dispatched.dispatchedAt)
        val dispatch = server.takeRequest()
        assertEquals("/api/v1/supplier-portal/orders/o1/dispatch", dispatch.path)
        assertEquals("""{"reason":"Truck MH12 AB 1234"}""", dispatch.body.readUtf8())
    }

    @Test
    fun aReceivedOrderShowsWhatTheShopReceived() = runTest {
        answer(orderJson.replace("\"SENT\"", "\"RECEIVED\"").replace("\"receiptStatus\":null,\"version\"", "\"receiptStatus\":\"SHORT\",\"version\"")
            .replace("\"receivedQuantity\":null,\"receivedUnitPrice\":null,\"shortageQuantity\":null,\"receiptStatus\":null",
                "\"receivedQuantity\":\"8\",\"receivedUnitPrice\":\"120.00\",\"shortageQuantity\":\"2\",\"receiptStatus\":\"SHORT\""))
        val o = ok(repo.order("o1"))
        assertEquals("SHORT", o.receiptStatus)
        assertEquals("8", o.lines.single().receivedQuantity)
        assertEquals("2", o.lines.single().shortageQuantity)
        assertEquals("SHORT", o.lines.single().receiptStatus)
    }

    @Test
    fun serverErrorsBecomeTheAppsOwnTextInBothLanguages() = runTest {
        problem("ORDER_STATUS", 409)
        val e = failure(repo.acceptOrder("o1"))
        assertEquals(409, e.status)
        assertEquals("ORDER_STATUS", e.code)
        assertEquals("This order has already changed. Refresh to see its current status", e.message)

        Lang.setForTest(AppLanguage.HINDI)
        problem("SUPPLIER_NOT_APPROVED", 403)
        assertEquals("SUPPLIER_NOT_APPROVED", failure(repo.orders()).code)
        problem("CONNECTION_STATUS", 409)
        assertEquals("यह कनेक्शन पहले ही बदल चुका है। इसकी अभी की स्थिति देखने के लिए रिफ़्रेश करें",
            failure(repo.acceptConnection("c1")).message)

        // A code the app does not know falls back to the HTTP status, never to the server's English sentence.
        Lang.setForTest(AppLanguage.ENGLISH)
        problem("SOMETHING_NEW", 500)
        assertEquals("The server had a problem. Please try again", failure(repo.products()).message)
    }

    @Test
    fun noInternetIsARetryableErrorAndNothingIsQueued() = runTest {
        server.shutdown()
        val e = failure(repo.dispatchOrder("o1", null))
        assertTrue(e.isNetwork)
        assertEquals("No connection to the server. Check the internet and try again.", e.message)
    }
}
