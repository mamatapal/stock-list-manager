package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Names, notes and titles typed in Hindi are stored, searched and synced exactly as typed. */
class HindiTextIT extends CatalogueTestSupport {

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static boolean hasName(Resp list, String field, String value) {
        for (JsonNode n : list.body()) {
            if (value.equals(text(n.get(field)))) return true;
        }
        return false;
    }

    @Test
    void hindiNamesRoundTrip() {
        Shop s = shop();
        String supplierName = "श्री गणेश ट्रेडर्स " + unique("");
        Resp sup = post("/api/v1/suppliers", """
                {"name":"%s","contactPerson":"रमेश कुमार","mobile":"9876543210","city":"वाराणसी","notes":"सोमवार को माल देते हैं"}
                """.formatted(supplierName), s.staff());
        assertEquals(201, sup.status(), sup.raw());
        String supplierId = sup.str("id");
        Resp got = get("/api/v1/suppliers/" + supplierId, s.owner());
        assertEquals(supplierName, got.str("name"));
        assertEquals("रमेश कुमार", got.str("contactPerson"));
        assertEquals("वाराणसी", got.str("city"));
        assertEquals("सोमवार को माल देते हैं", got.str("notes"));
        assertEquals(supplierName, jdbc.queryForObject("select name from supplier where id = uuid_to_bin(?)", String.class, supplierId));
        assertTrue(hasName(get("/api/v1/suppliers?q=" + enc("गणेश"), s.owner()), "name", supplierName), "search by Hindi name");
        assertTrue(hasName(get("/api/v1/suppliers?q=" + enc("वाराणसी"), s.owner()), "name", supplierName), "search by Hindi city");

        String itemName = "सरसों का तेल " + unique("");
        Resp item = post("/api/v1/items", """
                {"name":"%s","category":"तेल","defaultUnitId":"%s","latestPurchasePrice":"150.00","latestSellingPrice":"165.00"}
                """.formatted(itemName, LITRE), s.staff());
        assertEquals(201, item.status(), item.raw());
        assertEquals(itemName, get("/api/v1/items/" + item.str("id"), s.owner()).str("name"));
        assertEquals("तेल", get("/api/v1/items/" + item.str("id"), s.owner()).str("category"));
        assertTrue(hasName(get("/api/v1/items?q=" + enc("सरसों"), s.owner()), "name", itemName), "search items in Hindi");
        Resp duplicate = post("/api/v1/items", """
                {"name":"%s","defaultUnitId":"%s"}""".formatted(itemName, PCS), s.staff());
        assertEquals(409, duplicate.status(), "the same Hindi name is a duplicate: " + duplicate.raw());

        Resp list = post("/api/v1/stock-lists", """
                {"title":"सोमवार की लिस्ट","listDate":"2026-11-16","supplierId":"%s","notes":"जल्दी चाहिए","status":"SAVED",
                 "lines":[{"id":"%s","itemId":"%s","itemName":"%s","quantity":"2","unitId":"%s","priceBasis":"PER_UNIT",
                           "enteredPrice":"150","description":"बड़ी बोतल"}]}
                """.formatted(supplierId, newId(), item.str("id"), itemName, LITRE), s.staff());
        assertEquals(201, list.status(), list.raw());
        Resp savedList = get("/api/v1/stock-lists/" + list.str("id"), s.owner());
        assertEquals("सोमवार की लिस्ट", savedList.str("title"));
        assertEquals("जल्दी चाहिए", savedList.str("notes"));
        assertEquals("बड़ी बोतल", text(savedList.body().get("lines").get(0).get("description")));

        // Another phone receives the same text through sync.
        Resp pulled = get("/api/v1/sync/pull?since=0", s.owner());
        assertTrue(hasName(new Resp(200, pulled.body().get("suppliers"), ""), "name", supplierName));
        assertTrue(hasName(new Resp(200, pulled.body().get("items"), ""), "name", itemName));
    }
}
