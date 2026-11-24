package app.stocklist.supplier.network

import app.stocklist.supplier.core.i18n.AppLanguage
import app.stocklist.supplier.core.i18n.Lang
import app.stocklist.supplier.core.util.ProductRules
import app.stocklist.supplier.data.remote.ApiError
import app.stocklist.supplier.data.remote.ApiResult
import app.stocklist.supplier.data.remote.LoginRequest
import app.stocklist.supplier.data.remote.ProductRequest
import app.stocklist.supplier.data.repository.NetworkRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * End to end against a real backend with a throwaway MySQL 8.4 database, started by the "End-to-end tests" job of
 * supplier-android-apk.yml (never production). The supplier side uses the app's own network layer (NetworkApi +
 * NetworkRepository, real JSON); the shop side is driven over HTTP the way the Stock List Manager app calls it.
 *
 * Needs E2E_BASE_URL, E2E_SUPPLIER_EMAIL and E2E_SUPPLIER_PASSWORD (an approved supplier) and E2E_PENDING_EMAIL /
 * E2E_PENDING_PASSWORD (a verified supplier waiting for approval). Without them (ordinary unit-test runs) it is skipped.
 */
class SupplierNetworkEndToEndTest {
    private val base: String? = System.getenv("E2E_BASE_URL")?.let { if (it.endsWith("/")) it else "$it/" }
    private val http = OkHttpClient()
    private val jsonType = "application/json".toMediaType()

    @Before
    fun needsTheTestBackend() {
        assumeTrue("runs only in the end-to-end CI job (E2E_BASE_URL)", base != null)
        Lang.setForTest(AppLanguage.ENGLISH)
    }

    private fun env(name: String): String = checkNotNull(System.getenv(name)) { "$name is not set" }

    private fun <T> ok(r: ApiResult<T>): T {
        if (r is ApiResult.Failure) throw AssertionError("expected success, got ${r.error}")
        return (r as ApiResult.Ok).value
    }

    private fun failure(r: ApiResult<*>): ApiError {
        assertTrue("expected a refusal, got $r", r is ApiResult.Failure)
        return (r as ApiResult.Failure).error
    }

    /** Signs a supplier in through the app's AuthApi and returns the app's network repository for that supplier. */
    private fun supplier(email: String, password: String): Pair<NetworkRepository, TestApis> = runBlocking {
        val session = TestApis(base!!).auth.login(LoginRequest(email, password, "e2e-" + UUID.randomUUID(), "CI"))
        val apis = TestApis(base) { session.accessToken }
        NetworkRepository(apis.network) to apis
    }

    // ------------------------------------------------------------------ the shop side, over HTTP

    private data class Http(val status: Int, val body: JsonElement?) {
        val obj: JsonObject get() = body!!.jsonObject
        fun str(key: String): String? = obj[key]?.let { if (it is JsonObject || it is JsonArray) null else it.jsonPrimitive.contentOrNullSafe() }
    }

    private fun call(method: String, path: String, body: String?, token: String?): Http {
        val req = Request.Builder().url(base + path.removePrefix("/"))
            .method(method, body?.toRequestBody(jsonType) ?: if (method == "GET") null else "".toRequestBody(jsonType))
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            return Http(res.code, if (text.isBlank()) null else Json.parseToJsonElement(text))
        }
    }

    private fun shopOwner(): Pair<String, String> {
        val user = "e2e" + UUID.randomUUID().toString().take(8)
        val r = call("POST", "/api/v1/auth/register-business", """
            {"businessName":"E2E $user Stores","ownerName":"Owner $user","username":"$user","mobile":"9876543210",
             "email":"$user@example.com","password":"Shop-Password-2026","address":"12 MG Road","city":"Pune",
             "state":"Maharashtra","pinCode":"411001","gstin":null,"deviceId":"${UUID.randomUUID()}","deviceName":"CI"}
        """.trimIndent(), null)
        assertEquals(r.toString(), 201, r.status)
        return r.str("accessToken")!! to "E2E $user Stores"
    }

    @Test
    fun theWholeSupplierNetworkFlowWorksAgainstTheRealBackend() = runBlocking {
        val (repo, apis) = supplier(env("E2E_SUPPLIER_EMAIL"), env("E2E_SUPPLIER_PASSWORD"))
        val me = apis.supplier.me()
        assertEquals("APPROVED", me.org.status)
        val orgId = me.org.id

        // Directory listing: hidden by default, shown once the supplier opts in.
        assertFalse(ok(repo.listing()).listed)
        assertTrue(ok(repo.setListing(true)).listed)
        assertTrue(ok(repo.listing()).listed)

        // A shop finds the supplier in its directory and sends a request (only shops can).
        val (shop, shopName) = shopOwner()
        val found = call("GET", "/api/v1/supplier-network/directory?q=" +
            java.net.URLEncoder.encode(me.org.legalName.take(10), Charsets.UTF_8), null, shop)
        assertEquals(200, found.status)
        assertTrue("listed supplier is in the shop's directory",
            found.body!!.jsonArray.any { it.jsonObject["supplier"]!!.jsonObject["id"]!!.jsonPrimitive.content == orgId })
        val requested = call("POST", "/api/v1/supplier-network/connections", """{"supplierOrgId":"$orgId"}""", shop)
        assertEquals(requested.toString(), 201, requested.status)
        val connectionId = requested.str("id")!!

        // The supplier sees the request with the shop's name, declines, the shop asks again, the supplier accepts.
        val incoming = ok(repo.connections()).single { it.id == connectionId }
        assertEquals("REQUESTED", incoming.status)
        assertEquals(shopName, incoming.shop.name)
        assertEquals("Pune", incoming.shop.city)
        val declined = ok(repo.declineConnection(connectionId, "Not delivering this month"))
        assertEquals("DECLINED", declined.status)
        assertEquals("Not delivering this month", declined.statusReason)
        assertEquals(201, call("POST", "/api/v1/supplier-network/connections", """{"supplierOrgId":"$orgId"}""", shop).status)
        assertEquals("ACTIVE", ok(repo.acceptConnection(connectionId)).status)
        assertEquals("a second accept is refused", "CONNECTION_STATUS", failure(repo.acceptConnection(connectionId)).code)
        assertTrue(ok(repo.connection(connectionId)).history.size >= 4)

        // Catalogue: add, refuse an invalid product, change the price, switch availability off and on.
        assertTrue(ProductRules.check("Toor dal", "तूर दाल", "kg", "30 kg bag", "120").none)
        val product = ok(repo.saveProduct(null, ProductRules.request("Toor dal", "तूर दाल", "kg", "30 kg bag", "120")))
        assertEquals("120.00", product.price)
        assertEquals("PRODUCT_NAME_REQUIRED", failure(repo.saveProduct(null, ProductRequest("", null, "kg", null, "1"))).code)
        assertEquals("125.50", ok(repo.saveProduct(product.id, ProductRules.request("Toor dal", "तूर दाल", "kg", "30 kg bag", "125.50"))).price)
        assertFalse(ok(repo.setProductActive(product.id, false)).active)
        val shopCatalogue = { call("GET", "/api/v1/supplier-network/connections/$connectionId/catalogue", null, shop) }
        assertFalse("an unavailable product is not shown to shops", shopCatalogue().toString().contains(product.id))
        assertTrue(ok(repo.setProductActive(product.id, true)).active)
        assertTrue(shopCatalogue().toString().contains("125.50"))
        assertTrue(ok(repo.products()).any { it.id == product.id })

        // The shop links the product to its own item (explicitly) and orders three times.
        val item = call("POST", "/api/v1/items", """
            {"name":"Toor dal","category":"Grocery","defaultUnitId":"$KG","latestPurchasePrice":null,
             "latestSellingPrice":null,"preferredSupplierId":null}
        """.trimIndent(), shop)
        assertEquals(item.toString(), 201, item.status)
        assertEquals(200, call("PUT", "/api/v1/supplier-network/connections/$connectionId/products/${product.id}/link",
            """{"itemId":"${item.str("id")}","unitId":"$KG","unitsPerPack":"1"}""", shop).status)
        assertEquals(200, call("PUT", "/api/v1/supplier-network/connections/$connectionId/contact", """{"createContact":true}""", shop).status)
        val orderIds = (1..3).map {
            val o = call("POST", "/api/v1/supplier-network/orders",
                """{"connectionId":"$connectionId","note":"Order $it","lines":[{"productId":"${product.id}","quantity":"10"}]}""", shop)
            assertEquals(o.toString(), 201, o.status)
            o.str("id")!!
        }
        val (rejectId, fulfilId, openId) = orderIds

        // The supplier's inbox: the orders with shop, products, quantities and the saved price.
        val inbox = ok(repo.orders())
        assertTrue(inbox.filter { it.id in orderIds }.all { it.status == "SENT" && it.shop.name == shopName })
        val detail = ok(repo.order(fulfilId))
        val line = detail.lines.single()
        assertEquals("Toor dal", line.productNameEn)
        assertEquals("10", line.quantity)
        assertEquals("125.50", line.unitPrice)
        assertEquals("1255.00", line.lineAmount)

        // Reject needs a reason; accept; a second accept and dispatching an unaccepted order are refused.
        assertEquals("REASON_REQUIRED", failure(repo.rejectOrder(rejectId, "  ")).code)
        val rejected = ok(repo.rejectOrder(rejectId, "Out of stock"))
        assertEquals("REJECTED", rejected.status)
        assertEquals("Out of stock", rejected.statusReason)
        val accepted = ok(repo.acceptOrder(fulfilId))
        assertEquals("ACCEPTED", accepted.status)
        assertNotNull(accepted.acceptedAt)
        assertEquals("ORDER_STATUS", failure(repo.acceptOrder(fulfilId)).code)
        assertEquals("ORDER_STATUS", failure(repo.dispatchOrder(openId, null)).code)

        // Dispatch with a note; the shop then receives 8 of 10 (only the shop's receipt changes its stock).
        val dispatched = ok(repo.dispatchOrder(fulfilId, "Truck MH12 AB 1234"))
        assertEquals("DISPATCHED", dispatched.status)
        assertNotNull(dispatched.dispatchedAt)
        assertEquals("Truck MH12 AB 1234", dispatched.history.last().reason)
        val shopOrder = call("GET", "/api/v1/supplier-network/orders/$fulfilId", null, shop)
        val lineId = shopOrder.obj["lines"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content
        val receipt = call("POST", "/api/v1/supplier-network/orders/$fulfilId/receive",
            """{"lines":[{"lineId":"$lineId","quantity":"8"}]}""", shop)
        assertEquals(receipt.toString(), 200, receipt.status)
        val received = ok(repo.order(fulfilId))
        assertEquals("RECEIVED", received.status)
        assertEquals("SHORT", received.receiptStatus)
        assertEquals("8", received.lines.single().receivedQuantity)
        assertEquals("2", received.lines.single().shortageQuantity)
        assertEquals("SHORT", received.lines.single().receiptStatus)

        // The supplier ends the connection: the order not yet accepted is cancelled; closed orders stay as they were.
        val ended = ok(repo.endConnection(connectionId, "Closing the account"))
        assertEquals("ENDED", ended.status)
        assertEquals("SUPPLIER", ended.endedBy)
        assertEquals("CANCELLED", ok(repo.order(openId)).status)
        assertEquals("RECEIVED", ok(repo.order(fulfilId)).status)
        assertEquals("REJECTED", ok(repo.order(rejectId)).status)
        assertEquals("ENDED", ok(repo.connections()).single { it.id == connectionId }.status)
    }

    @Test
    fun theServerDecidesWhoMayUseTheSupplierNetwork() = runBlocking {
        // A verified business that is not approved yet is refused by the server, whatever the app shows.
        val (pending, _) = supplier(env("E2E_PENDING_EMAIL"), env("E2E_PENDING_PASSWORD"))
        val e = failure(pending.orders())
        assertEquals(403, e.status)
        assertEquals("SUPPLIER_NOT_APPROVED", e.code)
        assertEquals("Your business is not approved yet", e.message)
        assertEquals("SUPPLIER_NOT_APPROVED", failure(pending.setListing(true)).code)

        // Without a valid session nothing is answered.
        val anonymous = NetworkRepository(TestApis(base!!).network)
        assertEquals(401, failure(anonymous.connections()).status)
        val forged = NetworkRepository(TestApis(base) { "not-a-token" }.network)
        assertEquals(401, failure(forged.products()).status)
    }

    private companion object {
        const val KG = "00000000-0000-0000-0000-000000000002"
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
