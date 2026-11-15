package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import tools.jackson.databind.JsonNode;

/** Builders for purchases and deliveries on top of the catalogue helpers. */
abstract class PurchaseTestSupport extends CatalogueTestSupport {

    String supplier(String token, String name) {
        Resp r = post("/api/v1/suppliers", supplierJson(name), token);
        assertEquals(201, r.status(), r.raw());
        return r.str("id");
    }

    String item(String token, String name, String unit, String buy, String sell) {
        Resp r = post("/api/v1/items", itemJson(name, unit, buy, sell, null), token);
        assertEquals(201, r.status(), r.raw());
        return r.str("id");
    }

    static String pline(String id, String itemId, String qty, String unit, String basis, String buy, String sell) {
        return pline(id, itemId, qty, unit, basis, buy, sell, null);
    }

    static String pline(String id, String itemId, String qty, String unit, String basis, String buy, String sell,
                        String listLineId) {
        return """
                {"id":"%s","itemId":"%s","quantity":"%s","unitId":"%s","priceBasis":"%s","purchasePrice":%s,
                 "sellingPrice":%s,"stockListItemId":%s,"updateCatalog":true}
                """.formatted(id, itemId, qty, unit, basis, q(buy), q(sell), q(listLineId));
    }

    static String purchaseJson(String supplierId, String invoice, String date, boolean complete, String receive,
                               String... lines) {
        return purchaseJson(supplierId, invoice, date, null, complete, receive, lines);
    }

    static String purchaseJson(String supplierId, String invoice, String date, String listId, boolean complete,
                               String receive, String... lines) {
        return """
                {"supplierId":"%s","invoiceNo":%s,"purchaseDate":"%s","stockListId":%s,"notes":"Bill",
                 "complete":%s,"lines":[%s],"receive":%s}
                """.formatted(supplierId, q(invoice), date, q(listId), complete, String.join(",", lines),
                receive == null ? "null" : receive);
    }

    static String receipt(String id, String purchaseLineId, String qty, String batch, String expiry) {
        return """
                {"id":"%s","purchaseLineId":"%s","kind":"RECEIPT","quantity":"%s","batchNo":%s,"expiryDate":%s}
                """.formatted(id, purchaseLineId, qty, q(batch), q(expiry));
    }

    static String correction(String id, String purchaseLineId, String qty, String correctsId, String reason) {
        return """
                {"id":"%s","purchaseLineId":"%s","kind":"CORRECTION","quantity":"%s","correctsId":"%s","reason":%s}
                """.formatted(id, purchaseLineId, qty, correctsId, q(reason));
    }

    static String delivery(String id, String purchaseId, String date, String... lines) {
        return """
                {"id":"%s","purchaseId":%s,"receivedDate":"%s","notes":null,"lines":[%s]}
                """.formatted(id, q(purchaseId), date, String.join(",", lines));
    }

    static JsonNode lineOf(Resp purchase, String lineId) {
        for (JsonNode l : purchase.body().get("lines")) {
            if (lineId.equals(text(l.get("id")))) return l;
        }
        throw new AssertionError("no line " + lineId + " in " + purchase.raw());
    }

    static String field(JsonNode node, String name) {
        return text(node.get(name));
    }
}
