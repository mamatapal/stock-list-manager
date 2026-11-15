package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Server reports: totals, item prices, expected profit, which purchases count, permissions and isolation. */
class ReportIT extends PurchaseTestSupport {
    private static final String PERIOD = "?from=2026-10-01&to=2026-11-30";

    record Data(Shop shop, String agarwal, String bansal, String rice, String oil) {}

    private void buy(String token, String supplier, String invoice, String date, boolean complete, String... lines) {
        Resp r = post("/api/v1/purchases", purchaseJson(supplier, invoice, date, complete, null, lines), token);
        assertEquals(201, r.status(), r.raw());
    }

    /**
     * A-1: rice 10 kg at 50, sells at 60 (cost 500, profit 100). A-2: rice 5 kg for 300 in total, sells at 70 (cost
     * 300, profit 50). B-1: rice 2 kg at 55 without selling price (cost 110) and oil 3 L at 100, sells at 120 (cost
     * 300, profit 60). A draft and a cancelled purchase are never counted.
     */
    private Data data() {
        Shop s = shop();
        String a = supplier(s.owner(), "Agarwal"), b = supplier(s.owner(), "Bansal");
        String rice = item(s.owner(), "Rice", KG, null, null), oil = item(s.owner(), "Oil", LITRE, null, null);
        buy(s.owner(), a, "A-1", "2026-10-01", true, pline(newId(), rice, "10", KG, "PER_UNIT", "50", "60"));
        buy(s.owner(), a, "A-2", "2026-11-01", true, pline(newId(), rice, "5", KG, "TOTAL", "300", "70"));
        buy(s.owner(), b, "B-1", "2026-11-05", true, pline(newId(), rice, "2", KG, "PER_UNIT", "55", null),
                pline(newId(), oil, "3", LITRE, "PER_UNIT", "100", "120"));
        buy(s.owner(), a, "A-draft", "2026-11-06", false, pline(newId(), rice, "99", KG, "PER_UNIT", "1", "2"));
        Resp x = post("/api/v1/purchases", purchaseJson(a, "A-x", "2026-11-07", true, null,
                pline(newId(), rice, "100", KG, "PER_UNIT", "1", "2")), s.owner());
        callWithHeader("POST", "/api/v1/purchases/" + x.str("id") + "/cancel", "{\"reason\":\"Duplicate\"}",
                s.owner(), "If-Match", Long.toString(version(x)));
        buy(s.owner(), b, "B-old", "2026-09-30", true, pline(newId(), oil, "1", LITRE, "PER_UNIT", "999", null));
        return new Data(s, a, b, rice, oil);
    }

    private String staffWith(String owner, String perms) {
        String u = unique("rp");
        Resp c = createStaff(owner, stepUp(owner, PW), u, perms);
        return staffSession(u, c.str("temporaryPassword")).str("accessToken");
    }

    private static List<String> col(JsonNode rows, String field) {
        List<String> out = new ArrayList<>();
        for (JsonNode r : rows) out.add(text(r.get(field)));
        return out;
    }

    @Test
    void purchaseReportCountsOnlyCompletedPurchasesInThePeriod() {
        Data d = data();
        Resp r = get("/api/v1/reports/purchases" + PERIOD, d.shop().owner());
        assertEquals(200, r.status(), r.raw());
        assertEquals("3", r.str("purchaseCount"));
        assertEquals("1", r.str("cancelledCount"));
        assertEquals("1210.00", r.str("totalCost"));
        assertEquals("210.00", r.str("expectedProfit"));
        assertTrue(r.body().get("profitIncluded").asBoolean());
        assertTrue(r.str("profitNote").contains("not realised profit"));
        assertEquals(List.of("A-1", "A-2", "B-1", "A-x"), col(r.body().get("rows"), "invoiceNo"),
                "oldest first; the draft and the purchase outside the period are left out; cancelled listed");
        assertEquals("CANCELLED", text(r.body().get("rows").get(3).get("status")));

        Resp oil = get("/api/v1/reports/purchases" + PERIOD + "&itemId=" + d.oil(), d.shop().owner());
        assertEquals(List.of("B-1"), col(oil.body().get("rows"), "invoiceNo"));
        Resp agarwal = get("/api/v1/reports/purchases" + PERIOD + "&supplierId=" + d.agarwal(), d.shop().owner());
        assertEquals("800.00", agarwal.str("totalCost"));
    }

    @Test
    void profitFiguresNeedTheirOwnPermission() {
        Data d = data();
        Resp r = get("/api/v1/reports/purchases" + PERIOD, d.shop().staff());
        assertEquals(200, r.status(), r.raw());
        assertEquals("1210.00", r.str("totalCost"));
        assertFalse(r.body().get("profitIncluded").asBoolean());
        assertNull(text(r.body().get("expectedProfit")));
        assertNull(text(r.body().get("expectedSales")));
        for (JsonNode row : r.body().get("rows")) assertNull(text(row.get("expectedProfit")));
        for (JsonNode row : get("/api/v1/reports/supplier-totals" + PERIOD, d.shop().staff()).body().get("rows")) {
            assertNull(text(row.get("expectedProfit")));
        }
        for (JsonNode row : get("/api/v1/reports/item-prices" + PERIOD, d.shop().staff()).body().get("rows")) {
            assertNull(text(row.get("expectedProfit")));
        }

        assertEquals(403, get("/api/v1/reports/expected-profit" + PERIOD, d.shop().staff()).status());
        String profitOnly = staffWith(d.shop().owner(), "[\"PURCHASE_HISTORY_VIEW\",\"EXPECTED_PROFIT_VIEW\"]");
        assertEquals(403, get("/api/v1/reports/expected-profit" + PERIOD, profitOnly).status(),
                "seeing profit on a purchase is not the same as the profit report");
        assertEquals("210.00", get("/api/v1/reports/purchases" + PERIOD, profitOnly).str("expectedProfit"));
        String reports = staffWith(d.shop().owner(),
                "[\"PURCHASE_HISTORY_VIEW\",\"EXPECTED_PROFIT_VIEW\",\"PROFIT_REPORT_VIEW\"]");
        assertEquals(200, get("/api/v1/reports/expected-profit" + PERIOD, reports).status());

        for (String path : List.of("purchases", "supplier-totals", "item-prices", "expected-profit")) {
            assertEquals(403, get("/api/v1/reports/" + path + PERIOD, d.shop().viewer()).status(), path);
        }
    }

    @Test
    void supplierTotals() {
        Data d = data();
        Resp r = get("/api/v1/reports/supplier-totals" + PERIOD, d.shop().owner());
        assertEquals(200, r.status(), r.raw());
        assertEquals("1210.00", r.str("totalCost"));
        JsonNode rows = r.body().get("rows");
        assertEquals(List.of("Agarwal", "Bansal"), col(rows, "supplierName"), "largest first");
        assertEquals(List.of("2", "1"), col(rows, "purchaseCount"));
        assertEquals(List.of("800.00", "410.00"), col(rows, "totalCost"));
        assertEquals(List.of("66.12", "33.88"), col(rows, "sharePercent"));
        assertEquals(List.of("2026-11-01", "2026-11-05"), col(rows, "lastPurchaseDate"));
        assertEquals(List.of("150.00", "60.00"), col(rows, "expectedProfit"));
    }

    @Test
    void itemPrices() {
        Data d = data();
        Resp r = get("/api/v1/reports/item-prices" + PERIOD, d.shop().owner());
        assertEquals(200, r.status(), r.raw());
        JsonNode rows = r.body().get("rows");
        assertEquals(List.of("Oil", "Rice"), col(rows, "itemName"));
        JsonNode rice = rows.get(1);
        assertEquals("kg", text(rice.get("unitCode")).toLowerCase());
        assertEquals("3", text(rice.get("purchaseCount")));
        assertEquals("17", text(rice.get("totalQuantity")));
        assertEquals("910.00", text(rice.get("totalCost")));
        assertEquals("50.00", text(rice.get("lowestUnitPrice")));
        assertEquals("60.00", text(rice.get("highestUnitPrice")), "300 for 5 kg is 60 per kg");
        assertEquals("53.5294", text(rice.get("averageUnitPrice")), "910 / 17, weighted by quantity");
        assertEquals("55.00", text(rice.get("latestUnitPrice")));
        assertEquals("2026-11-05", text(rice.get("latestDate")));
        assertEquals("Bansal", text(rice.get("latestSupplierName")));
        assertEquals("70.00", text(rice.get("latestSellingPrice")), "the latest line that has a selling price");
        assertEquals("150.00", text(rice.get("expectedProfit")));

        Resp bansal = get("/api/v1/reports/item-prices" + PERIOD + "&supplierId=" + d.bansal(), d.shop().owner());
        assertEquals("55.00", text(bansal.body().get("rows").get(1).get("averageUnitPrice")));
    }

    @Test
    void expectedProfitReportIsAnEstimateOverPricedLinesOnly() {
        Data d = data();
        Resp r = get("/api/v1/reports/expected-profit" + PERIOD, d.shop().owner());
        assertEquals(200, r.status(), r.raw());
        assertEquals("3", r.str("purchaseCount"));
        assertEquals("1210.00", r.str("totalCost"));
        assertEquals("1100.00", r.str("pricedCost"));
        assertEquals("110.00", r.str("unpricedCost"), "rice from Bansal has no selling price");
        assertEquals("1310.00", r.str("expectedSales"));
        assertEquals("210.00", r.str("expectedProfit"));
        assertEquals("16.03", r.str("marginPercent"));
        assertTrue(r.str("note").contains("estimate"));
        assertTrue(r.str("note").contains("not realised"));
        assertEquals(List.of("Agarwal", "Bansal"), col(r.body().get("bySupplier"), "name"));
        assertEquals(List.of("150.00", "60.00"), col(r.body().get("bySupplier"), "expectedProfit"));
        assertEquals(List.of("Rice", "Oil"), col(r.body().get("byItem"), "name"));
        assertEquals(List.of("15.79", "16.67"), col(r.body().get("byItem"), "marginPercent"));
    }

    @Test
    void emptyPeriodsBadPeriodsAndOtherBusinesses() {
        Data d = data();
        Resp empty = get("/api/v1/reports/purchases?from=2020-01-01&to=2020-01-31", d.shop().owner());
        assertEquals(200, empty.status(), empty.raw());
        assertEquals("0", empty.str("purchaseCount"));
        assertEquals("0.00", empty.str("totalCost"));
        assertEquals(0, empty.body().get("rows").size());
        Resp none = get("/api/v1/reports/expected-profit?from=2020-01-01&to=2020-01-31", d.shop().owner());
        assertEquals("0.00", none.str("expectedProfit"));
        assertNull(text(none.body().get("marginPercent")), "no sales, so no margin rather than a division by zero");
        assertEquals(0, get("/api/v1/reports/item-prices?from=2020-01-01&to=2020-01-31", d.shop().owner())
                .body().get("rows").size());

        assertEquals(400, get("/api/v1/reports/purchases?from=2026-11-30&to=2026-10-01", d.shop().owner()).status());
        assertEquals(400, get("/api/v1/reports/purchases?from=2026-11-30", d.shop().owner()).status());

        Shop other = shop();
        Resp theirs = get("/api/v1/reports/supplier-totals" + PERIOD, other.owner());
        assertEquals(0, theirs.body().get("rows").size());
        assertEquals("0.00", theirs.str("totalCost"));
    }
}
