package app.stocklist.supplier.network

import app.stocklist.supplier.core.i18n.AppLanguage
import app.stocklist.supplier.core.i18n.Lang
import app.stocklist.supplier.core.util.ProductRules
import app.stocklist.supplier.data.remote.ConnectionDto
import app.stocklist.supplier.data.remote.OrderDto
import app.stocklist.supplier.data.remote.ProductDto
import app.stocklist.supplier.data.remote.ShopSummaryDto
import app.stocklist.supplier.feature.network.CatalogueLists
import app.stocklist.supplier.feature.network.ConnectionLists
import app.stocklist.supplier.feature.network.ConnectionTab
import app.stocklist.supplier.feature.network.NetworkText
import app.stocklist.supplier.feature.network.OrderFilter
import app.stocklist.supplier.feature.network.OrderLists
import app.stocklist.supplier.feature.network.ProductFilter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class NetworkLogicTest {
    @After
    fun english() = Lang.setForTest(AppLanguage.ENGLISH)

    private fun conn(id: String, status: String, at: String) =
        ConnectionDto(id, status, ShopSummaryDto("Shop $id", "Pune", "Maharashtra"), requestedAt = at)

    private fun order(id: String, status: String, shop: String, at: String) =
        OrderDto(id, status, ShopSummaryDto(shop, "Indore", "MP"), totalAmount = "10.00", lineCount = 1, createdAt = at)

    private fun product(id: String, name: String, active: Boolean, hi: String? = null) =
        ProductDto(id, name, hi, "kg", null, "1.00", active)

    @Test
    fun connectionsAreGroupedByWhatTheSupplierCanDo() {
        val all = listOf(
            conn("a", "REQUESTED", "2026-11-17T10:00:00Z"),
            conn("b", "REQUESTED", "2026-11-18T10:00:00Z"),
            conn("c", "ACTIVE", "2026-11-01T10:00:00Z"),
            conn("d", "DECLINED", "2026-11-02T10:00:00Z"),
            conn("e", "CANCELLED", "2026-11-03T10:00:00Z"),
            conn("f", "ENDED", "2026-11-04T10:00:00Z"),
        )
        assertEquals(listOf("b", "a"), ConnectionLists.of(all, ConnectionTab.REQUESTS).map { it.id })
        assertEquals(listOf("c"), ConnectionLists.of(all, ConnectionTab.CONNECTED).map { it.id })
        assertEquals(listOf("f", "e", "d"), ConnectionLists.of(all, ConnectionTab.PAST).map { it.id })
    }

    @Test
    fun ordersAreGroupedAndSearched() {
        val all = listOf(
            order("0f3a9c1d-0000-0000-0000-000000000001", "SENT", "Agarwal Stores", "2026-11-18T10:00:00Z"),
            order("1b2c3d4e-0000-0000-0000-000000000002", "ACCEPTED", "Gupta Kirana", "2026-11-17T10:00:00Z"),
            order("2c3d4e5f-0000-0000-0000-000000000003", "DISPATCHED", "Agarwal Stores", "2026-11-16T10:00:00Z"),
            order("3d4e5f60-0000-0000-0000-000000000004", "RECEIVED", "Sharma Mart", "2026-11-15T10:00:00Z"),
            order("4e5f6071-0000-0000-0000-000000000005", "DELIVERY_FAILED", "Sharma Mart", "2026-11-14T10:00:00Z"),
        )
        assertEquals(2, OrderLists.of(all, "", OrderFilter.TO_DO).size)
        assertEquals(1, OrderLists.of(all, "", OrderFilter.DISPATCHED).size)
        assertEquals(2, OrderLists.of(all, "", OrderFilter.CLOSED).size)
        assertEquals(5, OrderLists.of(all, "", OrderFilter.ALL).size)
        assertEquals(listOf("SENT", "DISPATCHED"), OrderLists.of(all, "agarwal", OrderFilter.ALL).map { it.status })
        assertEquals("by order number, with or without #", "RECEIVED", OrderLists.of(all, "#3D4E5F60", OrderFilter.ALL).single().status)
        assertEquals("ACCEPTED", OrderLists.of(all, "gupta", OrderFilter.TO_DO).single().status)
        assertEquals("search by city", 5, OrderLists.of(all, "indore", OrderFilter.ALL).size)
        for (s in listOf("REJECTED", "CANCELLED", "RECEIVED", "DELIVERY_FAILED")) assertEquals(OrderFilter.CLOSED, OrderLists.filterOf(s))
    }

    @Test
    fun catalogueSearchesBothNamesAndFiltersAvailability() {
        val all = listOf(product("1", "Toor dal", true, "तूर दाल"), product("2", "Moong dal", false), product("3", "Sugar", true))
        assertEquals(listOf("Moong dal", "Toor dal"), CatalogueLists.of(all, "DAL", ProductFilter.ALL).map { it.nameEn })
        assertEquals(listOf("Toor dal"), CatalogueLists.of(all, "तूर", ProductFilter.ALL).map { it.nameEn })
        assertEquals(listOf("Sugar", "Toor dal"), CatalogueLists.of(all, "", ProductFilter.AVAILABLE).map { it.nameEn })
        assertEquals(listOf("Moong dal"), CatalogueLists.of(all, "", ProductFilter.HIDDEN).map { it.nameEn })
    }

    @Test
    fun moneyUsesTheRupeeSignAndIndianGrouping() {
        assertEquals("₹0.00", NetworkText.money("0.00"))
        assertEquals("₹240.50", NetworkText.money("240.50"))
        assertEquals("₹1,200.00", NetworkText.money("1200.00"))
        assertEquals("₹1,23,456.50", NetworkText.money("123456.50"))
        assertEquals("₹12,34,56,789", NetworkText.money("123456789"))
        assertEquals("-", NetworkText.money(null))
        assertEquals("abc", NetworkText.money("abc"))
    }

    @Test
    fun orderNumbersDatesAndPlaces() {
        assertEquals("#0F3A9C1D", NetworkText.orderNumber("0f3a9c1d-1111-2222-3333-444455556666"))
        assertEquals("18-11-2026 11:00", NetworkText.dateTime("2026-11-18T05:30:00Z", ZoneId.of("Asia/Kolkata")))
        assertEquals("-", NetworkText.dateTime(null))
        assertEquals("-", NetworkText.dateTime("not a date"))
        assertEquals("Agarwal Stores, Pune", NetworkText.place("Agarwal Stores", "Pune", null))
    }

    @Test
    fun everyStatusHasTextInBothLanguages() {
        val orderStatuses = listOf("SENT", "ACCEPTED", "REJECTED", "DISPATCHED", "RECEIVED", "CANCELLED", "DELIVERY_FAILED")
        val connectionStatuses = listOf("REQUESTED", "ACTIVE", "DECLINED", "CANCELLED", "ENDED")
        val english = orderStatuses.map(NetworkText::orderStatus) + connectionStatuses.map(NetworkText::connectionStatus)
        assertTrue(english.none { it == "Unknown status" })
        assertEquals("Delivery failed", NetworkText.orderStatus("DELIVERY_FAILED"))
        Lang.setForTest(AppLanguage.HINDI)
        val hindi = orderStatuses.map(NetworkText::orderStatus) + connectionStatuses.map(NetworkText::connectionStatus)
        hindi.zip(english).forEach { (hi, en) -> assertTrue("$en has no Hindi", hi != en) }
        assertEquals("कम मिला", NetworkText.lineReceipt("SHORT"))
        assertEquals("नहीं मिला", NetworkText.lineReceipt("NOT_RECEIVED"))
        assertEquals("पूरा मिला", NetworkText.receipt("FULL"))
        assertNull(NetworkText.receipt(null))
        // Only new and accepted orders need the supplier; the rest wait for the shop or are closed.
        assertNotNull(NetworkText.orderNextStep("SENT"))
        assertNotNull(NetworkText.orderNextStep("ACCEPTED"))
        assertNull(NetworkText.orderNextStep("RECEIVED"))
    }

    @Test
    fun productsAreCheckedLikeOnTheServer() {
        assertTrue(ProductRules.check("Toor dal", "", "kg", "", "120").none)
        assertTrue(ProductRules.check("Toor dal", "तूर दाल", "kg", "30 kg bag", "0").none)
        val empty = ProductRules.check(" ", "", "", "", "")
        assertEquals("Required", empty.nameEn)
        assertEquals("Required", empty.unit)
        assertEquals("Enter the price", empty.price)
        assertNull(empty.nameHi)
        assertNull(empty.pack)
        assertEquals("At most 150 characters", ProductRules.check("x".repeat(151), "", "kg", "", "1").nameEn)
        assertEquals("At most 30 characters", ProductRules.check("Dal", "", "k".repeat(31), "", "1").unit)
        for (bad in listOf("12.345", "-5", "abc", "1,2.3.4", "12345678901234")) {
            assertEquals(bad, "Enter the price as a number, for example 240 or 240.50", ProductRules.price(bad))
        }
        for (good in listOf("240", "240.5", "240.50", "₹ 1,200", "१२०")) assertNull(good, ProductRules.price(good))
        val r = ProductRules.request(" Toor dal ", " ", " kg ", "", "₹ 1,200.50")
        assertEquals("Toor dal", r.nameEn)
        assertNull(r.nameHi)
        assertEquals("kg", r.unit)
        assertNull(r.pack)
        assertEquals("1200.50", r.price)
        assertEquals("120", ProductRules.request("Dal", "", "kg", "", "१२०").price)
    }

    @Test
    fun reasonsAreRequiredOnlyWhereTheServerRequiresThem() {
        assertEquals("Give a reason", ProductRules.reason("  ", required = true))
        assertNull(ProductRules.reason("", required = false))
        assertNull(ProductRules.reason("Out of stock", required = true))
        assertEquals("At most 500 characters", ProductRules.reason("x".repeat(501), required = false))
    }
}
