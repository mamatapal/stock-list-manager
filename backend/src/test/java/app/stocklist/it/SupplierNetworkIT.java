package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Supplier network (V7): directory, connections, catalogue, product links and purchase-order statuses, across shops
 * and suppliers. Goods receipt is covered by GoodsReceiptIT.
 */
class SupplierNetworkIT extends NetworkTestSupport {

    private static List<String> ids(Resp list, String pointer) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : list.body()) out.add(text(n.at(pointer)));
        return out;
    }

    // ================================================================== directory

    @Test
    void onlyApprovedSuppliersThatOptedInAppear() {
        Shop shop = shop();
        String tag = unique("dir");
        Sup listed = approvedSupplier(true, "Listed " + tag);
        Sup hidden = approvedSupplier(false, "Hidden " + tag);
        String pendingEmail = supplierEmail();
        Resp pending = verifiedSupplierNamed(pendingEmail, "Pending " + tag);

        Resp found = get(NET + "/directory?q=" + tag, shop.owner());
        assertEquals(200, found.status(), found.raw());
        assertEquals(List.of(listed.orgId()), ids(found, "/supplier/id"));

        // Hidden by default; a business waiting for approval cannot even turn the listing on.
        assertEquals(false, get(PORTAL + "/directory-listing", hidden.token()).body().get("listed").asBoolean());
        Resp notApproved = put(PORTAL + "/directory-listing", "{\"listed\":true}", pending.str("accessToken"), null);
        assertEquals(403, notApproved.status(), notApproved.raw());
        assertEquals("SUPPLIER_NOT_APPROVED", notApproved.str("code"));
        assertEquals(404, get(NET + "/directory/" + hidden.orgId(), shop.owner()).status());
        assertEquals(404, get(NET + "/directory/" + orgId(pending), shop.owner()).status());

        // Only public business details: no contact person, phone, email, GSTIN or account data.
        Resp one = get(NET + "/directory/" + listed.orgId(), shop.owner());
        assertEquals(200, one.status(), one.raw());
        String raw = one.raw();
        for (String hiddenField : List.of("email", "mobile", "gstin", "contactPerson", "address", "pinCode",
                "APPROVED", "@example.com", "9876543210")) {
            assertFalse(raw.contains(hiddenField), "directory shows " + hiddenField + ": " + raw);
        }
        assertEquals("Indore", text(one.at("/supplier/city")));
        assertNull(text(one.at("/connectionStatus")));
    }

    @Test
    void optingOutOrSuspensionHidesTheSupplierButKeepsConnectionsAndOrders() {
        Shop shop = shop();
        String tag = unique("out");
        Sup s = approvedSupplier(true, "Leaving " + tag);
        String conn = connect(shop.owner(), s);
        String item = item(shop.owner(), "Sugar", KG, "40", "45");
        String product = product(s, "Sugar 1 kg", "kg", "41.00");
        assertEquals(200, link(shop.owner(), conn, product, item, KG, "1").status());
        String orderId = order(shop.owner(), conn, orderLine(product, "5")).str("id");

        setListed(s, false);
        assertTrue(ids(get(NET + "/directory?q=" + tag, shop.owner()), "/supplier/id").isEmpty());
        assertEquals(404, get(NET + "/directory/" + s.orgId(), shop.owner()).status());
        assertEquals(200, get(NET + "/connections/" + conn, shop.owner()).status());
        assertEquals("ACTIVE", get(NET + "/connections/" + conn, shop.owner()).str("status"));
        assertEquals(200, get(NET + "/orders/" + orderId, shop.owner()).status());
        assertEquals(200, get(NET + "/connections/" + conn + "/catalogue", shop.owner()).status());

        setListed(s, true);
        suspend(s);
        assertTrue(ids(get(NET + "/directory?q=" + tag, shop.owner()), "/supplier/id").isEmpty());
        assertEquals(200, get(NET + "/orders/" + orderId, shop.owner()).status(), "history stays readable");
        Resp blocked = post(NET + "/orders", orderJson(null, conn, orderLine(product, "1")), shop.owner());
        assertEquals("SUPPLIER_NOT_AVAILABLE", blocked.str("code"), blocked.raw());
        Resp supplierBlocked = get(PORTAL + "/orders", s.token());
        assertEquals("SUPPLIER_SUSPENDED", supplierBlocked.str("code"));
        reinstate(s);
        assertTrue(ids(get(NET + "/directory?q=" + tag, shop.owner()), "/supplier/id").contains(s.orgId()));
    }

    // ================================================================== connections

    @Test
    void theShopRequestsAndTheSupplierAcceptsWithHistoryOnBothSides() {
        Shop shop = shop();
        Sup s = approvedSupplier(true, "Accepting " + unique(""));
        String id = request(shop.owner(), s);
        assertEquals("REQUESTED", get(NET + "/connections/" + id, shop.owner()).str("status"));

        Resp incoming = get(PORTAL + "/connections?status=REQUESTED", s.token());
        assertEquals(200, incoming.status(), incoming.raw());
        assertEquals(List.of(id), ids(incoming, "/id"));
        JsonNode shopSeen = incoming.body().get(0).get("shop");
        assertEquals("Pune", text(shopSeen.get("city")));
        assertTrue(text(shopSeen.get("name")).endsWith("Stores"));
        assertFalse(incoming.raw().contains("9876543210"), "suppliers never see the shop's phone");
        assertFalse(incoming.raw().contains("@example.com"), "or its email");

        Resp accepted = post(PORTAL + "/connections/" + id + "/accept", null, s.token());
        assertEquals(200, accepted.status(), accepted.raw());
        assertEquals("ACTIVE", accepted.str("status"));
        Resp mine = get(NET + "/connections/" + id, shop.owner());
        assertEquals("ACTIVE", mine.str("status"));
        JsonNode history = mine.body().get("history");
        assertEquals(2, history.size());
        assertEquals("REQUESTED", text(history.get(0).get("toStatus")));
        assertEquals("SHOP", text(history.get(0).get("actorType")));
        assertEquals("ACTIVE", text(history.get(1).get("toStatus")));
        assertEquals("SUPPLIER", text(history.get(1).get("actorType")));
        assertNull(text(accepted.body().at("/history/0/actorId")), "the supplier does not see shop user ids");
    }

    @Test
    void suppliersCannotStartAConnection() {
        Sup s = approvedSupplier(true, "No initiator " + unique(""));
        // There is no endpoint for it: the supplier API has no way to create a connection.
        JsonNode paths = get("/v3/api-docs", null).body().get("paths");
        assertTrue(paths.get(PORTAL + "/connections").get("post") == null, "no POST " + PORTAL + "/connections");
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM supplier_connection WHERE supplier_org_id = "
                + "UUID_TO_BIN(?)", Long.class, s.orgId());
        post(PORTAL + "/connections", "{\"businessId\":\"" + newId() + "\"}", s.token());
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM supplier_connection WHERE supplier_org_id = "
                + "UUID_TO_BIN(?)", Long.class, s.orgId()));
        // And the database refuses a connection started by a supplier.
        Shop shop = shop();
        String conn = request(shop.owner(), s);
        assertThrows(Exception.class, () -> jdbc.update(
                "UPDATE supplier_connection SET initiated_by = 'SUPPLIER' WHERE id = UUID_TO_BIN(?)", conn));
    }

    @Test
    void duplicateRequestsAndInvalidTransitionsAreRefused() {
        Shop shop = shop();
        Sup s = approvedSupplier(true, "Strict " + unique(""));
        String id = request(shop.owner(), s);
        Resp again = post(NET + "/connections", "{\"supplierOrgId\":\"" + s.orgId() + "\"}", shop.owner());
        assertEquals(409, again.status(), again.raw());
        assertEquals("CONNECTION_EXISTS", again.str("code"));
        assertEquals("CONNECTION_STATUS", post(NET + "/connections/" + id + "/end", null, shop.owner()).str("code"));

        Resp declined = post(PORTAL + "/connections/" + id + "/decline", "{\"reason\":\"We do not deliver there\"}",
                s.token());
        assertEquals("DECLINED", declined.str("status"));
        assertEquals("CONNECTION_STATUS", post(PORTAL + "/connections/" + id + "/accept", null, s.token()).str("code"),
                "a declined request does not become active");

        // A new request reopens the same row, as a request again (never straight to active).
        Resp renewed = post(NET + "/connections", "{\"supplierOrgId\":\"" + s.orgId() + "\"}", shop.owner());
        assertEquals(201, renewed.status(), renewed.raw());
        assertEquals(id, renewed.str("id"));
        assertEquals("REQUESTED", renewed.str("status"));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM supplier_connection WHERE supplier_org_id = "
                + "UUID_TO_BIN(?)", Long.class, s.orgId()));

        // The shop withdraws it; the supplier can no longer accept it.
        assertEquals("CANCELLED", post(NET + "/connections/" + id + "/cancel", null, shop.owner()).str("status"));
        assertEquals("CONNECTION_STATUS", post(PORTAL + "/connections/" + id + "/accept", null, s.token()).str("code"));

        // One row per pair, in the database too.
        assertThrows(Exception.class, () -> jdbc.update("""
                INSERT INTO supplier_connection (id, business_id, supplier_org_id, status, initiated_by, requested_by,
                    requested_at, version, created_at, updated_at)
                SELECT UUID_TO_BIN(UUID()), business_id, supplier_org_id, 'REQUESTED', 'SHOP', requested_by, NOW(6), 0,
                    NOW(6), NOW(6) FROM supplier_connection WHERE id = UUID_TO_BIN(?)""", id));
    }

    @Test
    void eitherSideEndsAnActiveConnectionAndOpenOrdersAreCancelled() {
        Shop shop = shop();
        Sup s = approvedSupplier(true, "Ending " + unique(""));
        String conn = connect(shop.owner(), s);
        String item = item(shop.owner(), "Tea", KG, "300", "350");
        String product = product(s, "Tea leaf", "kg", "290");
        assertEquals(200, link(shop.owner(), conn, product, item, KG, "1").status());
        String sent = order(shop.owner(), conn, orderLine(product, "1")).str("id");
        String dispatched = order(shop.owner(), conn, orderLine(product, "2")).str("id");
        assertEquals(200, supplierAct(s, dispatched, "accept", null).status());
        assertEquals(200, supplierAct(s, dispatched, "dispatch", null).status());

        Resp ended = post(PORTAL + "/connections/" + conn + "/end", "{\"reason\":\"Closing the route\"}", s.token());
        assertEquals("ENDED", ended.str("status"), ended.raw());
        assertEquals("SUPPLIER", ended.str("endedBy"));
        assertEquals("CANCELLED", get(NET + "/orders/" + sent, shop.owner()).str("status"));
        assertEquals("SYSTEM", text(get(NET + "/orders/" + sent, shop.owner()).at("/history/1/actorType")));
        assertEquals("DISPATCHED", get(NET + "/orders/" + dispatched, shop.owner()).str("status"),
                "goods on their way stay receivable");
        assertEquals("CONNECTION_STATUS", post(NET + "/connections/" + conn + "/end", null, shop.owner()).str("code"));
        assertEquals("CONNECTION_NOT_ACTIVE",
                post(NET + "/orders", orderJson(null, conn, orderLine(product, "1")), shop.owner()).str("code"));

        // The shop side ends too.
        Shop other = shop();
        String conn2 = connect(other.owner(), s);
        Resp byShop = post(NET + "/connections/" + conn2 + "/end", null, other.owner());
        assertEquals("ENDED", byShop.str("status"), byShop.raw());
        assertEquals("SHOP", byShop.str("endedBy"));
    }

    @Test
    void connectionsCannotBeReachedAcrossShopsOrSuppliers() {
        Shop a = shop();
        Shop b = shop();
        Sup s1 = approvedSupplier(true, "First " + unique(""));
        Sup s2 = approvedSupplier(true, "Second " + unique(""));
        String conn = request(a.owner(), s1);

        assertEquals(404, get(NET + "/connections/" + conn, b.owner()).status());
        assertEquals(404, post(NET + "/connections/" + conn + "/cancel", null, b.owner()).status());
        assertEquals(404, get(PORTAL + "/connections/" + conn, s2.token()).status());
        assertEquals(404, post(PORTAL + "/connections/" + conn + "/accept", null, s2.token()).status());
        assertEquals("REQUESTED", get(NET + "/connections/" + conn, a.owner()).str("status"), "nothing changed");
        assertTrue(ids(get(NET + "/connections", b.owner()), "/id").isEmpty());
        assertTrue(ids(get(PORTAL + "/connections", s2.token()), "/id").isEmpty());

        // Tokens of one side are not accepted by the other side's API.
        assertEquals(401, get(NET + "/connections", s1.token()).status());
        assertEquals(401, get(PORTAL + "/connections", a.owner()).status());
        // Unknown or unlisted suppliers cannot be asked.
        assertEquals(404, post(NET + "/connections", "{\"supplierOrgId\":\"" + newId() + "\"}", a.owner()).status());
        Sup hidden = approvedSupplier(false, "Unlisted " + unique(""));
        assertEquals(404, post(NET + "/connections", "{\"supplierOrgId\":\"" + hidden.orgId() + "\"}", b.owner()).status());
    }

    @Test
    void supplierContactsAreLinkedOnlyOnRequestAndOnlyOnce() {
        Shop shop = shop();
        Sup s1 = approvedSupplier(true, "Contact one " + unique(""));
        Sup s2 = approvedSupplier(true, "Contact two " + unique(""));
        String c1 = connect(shop.owner(), s1);
        String c2 = connect(shop.owner(), s2);
        assertNull(get(NET + "/connections/" + c1, shop.owner()).str("supplierContactId"), "not linked automatically");

        String contact = supplier(shop.owner(), "Agarwal (my contact)");
        linkContact(shop.owner(), c1, contact);
        Resp clash = put(NET + "/connections/" + c2 + "/contact", "{\"supplierId\":\"" + contact + "\"}", shop.owner(), null);
        assertEquals("CONTACT_IN_USE", clash.str("code"), clash.raw());
        assertEquals("CONTACT_CHOICE", put(NET + "/connections/" + c2 + "/contact", "{}", shop.owner(), null).str("code"));

        Resp created = put(NET + "/connections/" + c2 + "/contact", "{\"createContact\":true}", shop.owner(), null);
        assertEquals(200, created.status(), created.raw());
        String newContact = created.str("supplierContactId");
        assertEquals("Agarwal Traders", get("/api/v1/suppliers/" + newContact, shop.owner()).str("name"));

        Shop other = shop();
        String foreign = supplier(other.owner(), "Not yours");
        assertEquals("UNKNOWN_SUPPLIER",
                put(NET + "/connections/" + c1 + "/contact", "{\"supplierId\":\"" + foreign + "\"}", shop.owner(), null)
                        .str("code"));
    }

    // ================================================================== catalogue and product links

    @Test
    void suppliersManageOnlyTheirOwnCatalogue() {
        Sup s1 = approvedSupplier(true, "Catalogue one " + unique(""));
        Sup s2 = approvedSupplier(true, "Catalogue two " + unique(""));
        String p = product(s1, "Moong dal", "kg", "120.50");
        assertEquals(404, put(PORTAL + "/products/" + p, productJson("Stolen", "kg", "1"), s2.token(), null).status());
        assertEquals(404, post(PORTAL + "/products/" + p + "/deactivate", null, s2.token()).status());
        assertTrue(ids(get(PORTAL + "/products", s2.token()), "/id").isEmpty());

        assertEquals("PRODUCT_NAME_REQUIRED", post(PORTAL + "/products", productJson(" ", "kg", "1"), s1.token()).str("code"));
        assertEquals("PRODUCT_UNIT_REQUIRED", post(PORTAL + "/products", productJson("Rice", "", "1"), s1.token()).str("code"));
        assertEquals("INVALID_NUMBER", post(PORTAL + "/products", productJson("Rice", "kg", "-1"), s1.token()).str("code"));

        Resp changed = put(PORTAL + "/products/" + p, productJson("Moong dal (split)", "kg", "125"), s1.token(), null);
        assertEquals(200, changed.status(), changed.raw());
        assertEquals("125.00", changed.str("price"));
        assertEquals(false, post(PORTAL + "/products/" + p + "/deactivate", null, s1.token()).body().get("active").asBoolean());
        assertEquals(true, post(PORTAL + "/products/" + p + "/activate", null, s1.token()).body().get("active").asBoolean());

        // A business that is not approved has no catalogue at all.
        Resp pending = verifiedSupplierNamed(supplierEmail(), "Waiting " + unique(""));
        assertEquals("SUPPLIER_NOT_APPROVED",
                post(PORTAL + "/products", productJson("Rice", "kg", "1"), pending.str("accessToken")).str("code"));
    }

    @Test
    void shopsSeeOnlyTheCatalogueOfActiveConnectionsAndNothingIsMatchedAutomatically() {
        Shop shop = shop();
        Sup s = approvedSupplier(true, "Matching " + unique(""));
        // The shop already has an item with exactly the product's name.
        String item = item(shop.owner(), "Basmati Rice", KG, "90", "110");
        int itemsBefore = get("/api/v1/items", shop.owner()).body().size();
        String product = product(s, "Basmati Rice", "kg", "88");
        String inactive = product(s, "Old stock", "kg", "1");
        post(PORTAL + "/products/" + inactive + "/deactivate", null, s.token());

        String conn = request(shop.owner(), s);
        assertEquals("CONNECTION_NOT_ACTIVE", get(NET + "/connections/" + conn + "/catalogue", shop.owner()).str("code"));
        post(PORTAL + "/connections/" + conn + "/accept", null, s.token());

        Resp cat = get(NET + "/connections/" + conn + "/catalogue", shop.owner());
        assertEquals(200, cat.status(), cat.raw());
        assertEquals(List.of(product), idsOf(cat.body().get("products")), "active products only");
        assertTrue(cat.body().at("/products/0/link").isNull(), "same name, but not linked: " + cat.raw());
        assertEquals(itemsBefore, get("/api/v1/items", shop.owner()).body().size(), "publishing creates no shop item");

        Resp unlinked = post(NET + "/orders", orderJson(null, conn, orderLine(product, "1")), shop.owner());
        assertEquals("PRODUCT_NOT_LINKED", unlinked.str("code"), unlinked.raw());

        Resp linked = link(shop.owner(), conn, product, item, KG, "1");
        assertEquals(200, linked.status(), linked.raw());
        assertEquals(item, text(linked.at("/link/itemId")));
        assertEquals("Basmati Rice", text(linked.at("/link/itemName")));

        // Another shop never sees it.
        Shop other = shop();
        assertEquals(404, get(NET + "/connections/" + conn + "/catalogue", other.owner()).status());
    }

    private static List<String> idsOf(JsonNode products) {
        List<String> out = new ArrayList<>();
        for (JsonNode p : products) out.add(text(p.at("/product/id")));
        return out;
    }

    @Test
    void onlyTheShopkeeperLinksProductsAndNeverAcrossTenants() {
        Shop shop = shop();
        Shop other = shop();
        Sup s1 = approvedSupplier(true, "Linker " + unique(""));
        Sup s2 = approvedSupplier(true, "Other linker " + unique(""));
        String conn = connect(shop.owner(), s1);
        String otherConn = connect(other.owner(), s2);
        String mine = item(shop.owner(), "Ghee", KG, "500", "550");
        String theirs = item(other.owner(), "Ghee", KG, "500", "550");
        String p1 = product(s1, "Ghee 1 kg", "tin", "480");
        String p2 = product(s2, "Ghee 1 kg", "tin", "470");

        assertEquals(403, link(shop.staff(), conn, p1, mine, KG, "1").status(), "default staff may not link");
        assertEquals(401, put(NET + "/connections/" + conn + "/products/" + p1 + "/link",
                "{\"itemId\":\"" + mine + "\"}", s1.token(), null).status(), "suppliers cannot link");
        assertEquals("UNKNOWN_ITEM", link(shop.owner(), conn, p1, theirs, KG, "1").str("code"));
        assertEquals(404, link(shop.owner(), conn, p2, mine, KG, "1").status(), "another supplier's product");
        assertEquals(404, link(shop.owner(), otherConn, p2, mine, KG, "1").status(), "another shop's connection");
        assertEquals("LINK_CHOICE", put(NET + "/connections/" + conn + "/products/" + p1 + "/link", "{}", shop.owner(),
                null).str("code"));

        // A new item only because the shopkeeper asks for it.
        int before = get("/api/v1/items", shop.owner()).body().size();
        Resp created = put(NET + "/connections/" + conn + "/products/" + p1 + "/link", """
                {"newItem":{"name":"Desi ghee","category":"Dairy","defaultUnitId":"%s"},"unitsPerPack":"1"}"""
                .formatted(KG), shop.owner(), null);
        assertEquals(200, created.status(), created.raw());
        assertEquals(before + 1, get("/api/v1/items", shop.owner()).body().size());
        assertEquals("Desi ghee", text(created.at("/link/itemName")));

        // Changing and removing the link.
        assertEquals(mine, text(link(shop.owner(), conn, p1, mine, PCS, "12").at("/link/itemId")));
        assertEquals(204, delete(NET + "/connections/" + conn + "/products/" + p1 + "/link", shop.owner(), null).status());
        assertEquals(404, delete(NET + "/connections/" + conn + "/products/" + p1 + "/link", shop.owner(), null).status());
    }

    // ================================================================== purchase orders

    @Test
    void ordersNeedAnActiveConnectionAndFollowTheAllowedTransitions() {
        Shop shop = shop();
        Sup s = approvedSupplier(true, "Orders " + unique(""));
        String conn = request(shop.owner(), s);
        String item = item(shop.owner(), "Salt", KG, "20", "25");
        String product = product(s, "Iodised salt", "kg", "18");
        assertEquals("CONNECTION_NOT_ACTIVE",
                post(NET + "/orders", orderJson(null, conn, orderLine(product, "1")), shop.owner()).str("code"));
        post(PORTAL + "/connections/" + conn + "/accept", null, s.token());
        assertEquals(200, link(shop.owner(), conn, product, item, KG, "1").status());

        assertEquals("EMPTY_ORDER", post(NET + "/orders", orderJson(null, conn), shop.owner()).str("code"));
        assertEquals("DUPLICATE_PRODUCT", post(NET + "/orders",
                orderJson(null, conn, orderLine(product, "1"), orderLine(product, "2")), shop.owner()).str("code"));
        assertEquals("INVALID_NUMBER", post(NET + "/orders", orderJson(null, conn, orderLine(product, "0")),
                shop.owner()).str("code"));

        Resp placed = order(shop.owner(), conn, orderLine(product, "10"));
        String id = placed.str("id");
        assertEquals("SENT", placed.str("status"));
        assertEquals("180.00", placed.str("totalAmount"));
        assertEquals("SENT", get(PORTAL + "/orders/" + id, s.token()).str("status"));

        // Suppliers: only from SENT; rejecting needs a reason.
        assertEquals("ORDER_STATUS", supplierAct(s, id, "dispatch", null).str("code"));
        assertEquals("REASON_REQUIRED", supplierAct(s, id, "reject", "{\"reason\":\"  \"}").str("code"));
        assertEquals("ACCEPTED", supplierAct(s, id, "accept", null).str("status"));
        assertEquals("ORDER_STATUS", supplierAct(s, id, "accept", null).str("code"));
        assertEquals("ORDER_STATUS", supplierAct(s, id, "reject", "{\"reason\":\"Too late\"}").str("code"));
        assertEquals("DISPATCHED", supplierAct(s, id, "dispatch", "{\"reason\":\"Van 7\"}").str("status"));
        assertEquals("ORDER_STATUS", post(NET + "/orders/" + id + "/cancel", null, shop.owner()).str("code"),
                "a dispatched order cannot be cancelled");

        // Rejected and cancelled orders are final.
        String rejected = order(shop.owner(), conn, orderLine(product, "1")).str("id");
        Resp r = supplierAct(s, rejected, "reject", "{\"reason\":\"Out of stock\"}");
        assertEquals("REJECTED", r.str("status"), r.raw());
        assertEquals("Out of stock", get(NET + "/orders/" + rejected, shop.owner()).str("statusReason"));
        assertEquals("ORDER_STATUS", supplierAct(s, rejected, "accept", null).str("code"));
        assertEquals("ORDER_STATUS", post(NET + "/orders/" + rejected + "/cancel", null, shop.owner()).str("code"));

        String cancelSent = order(shop.owner(), conn, orderLine(product, "1")).str("id");
        assertEquals("CANCELLED", post(NET + "/orders/" + cancelSent + "/cancel", "{\"reason\":\"Ordered twice\"}",
                shop.owner()).str("status"));
        assertEquals("ORDER_STATUS", supplierAct(s, cancelSent, "accept", null).str("code"));
        String cancelAccepted = order(shop.owner(), conn, orderLine(product, "1")).str("id");
        supplierAct(s, cancelAccepted, "accept", null);
        assertEquals("CANCELLED", post(NET + "/orders/" + cancelAccepted + "/cancel", null, shop.owner()).str("status"));
        assertEquals("ORDER_STATUS", supplierAct(s, cancelAccepted, "dispatch", null).str("code"));

        JsonNode history = get(NET + "/orders/" + id, shop.owner()).body().get("history");
        assertEquals(3, history.size());
        assertEquals("DISPATCHED", text(history.get(2).get("toStatus")));
        assertEquals("Van 7", text(history.get(2).get("reason")));
        List<String> rejectedOnes = new ArrayList<>();
        for (JsonNode o : get(NET + "/orders?status=REJECTED", shop.owner()).body()) rejectedOnes.add(text(o.get("id")));
        assertEquals(List.of(rejected), rejectedOnes);
        assertEquals("INVALID_REQUEST", get(NET + "/orders?status=LOST", shop.owner()).str("code"));
    }

    @Test
    void ordersAreVisibleOnlyToTheirShopAndTheirSupplier() {
        Shop shop = shop();
        Shop other = shop();
        Sup s = approvedSupplier(true, "Private " + unique(""));
        Sup s2 = approvedSupplier(true, "Stranger " + unique(""));
        String conn = connect(shop.owner(), s);
        String item = item(shop.owner(), "Oil", LITRE, "150", "170");
        String product = product(s, "Mustard oil", "litre", "140");
        link(shop.owner(), conn, product, item, LITRE, "1");
        String id = order(shop.owner(), conn, orderLine(product, "3")).str("id");

        assertEquals(404, get(NET + "/orders/" + id, other.owner()).status());
        assertEquals(404, post(NET + "/orders/" + id + "/cancel", null, other.owner()).status());
        assertEquals(404, post(NET + "/orders/" + id + "/receive", receiptJson(null, null), other.owner()).status());
        assertEquals(404, get(PORTAL + "/orders/" + id, s2.token()).status());
        assertEquals(404, supplierAct(s2, id, "accept", null).status());
        assertTrue(get(NET + "/orders", other.owner()).body().isEmpty());
        assertTrue(get(PORTAL + "/orders", s2.token()).body().isEmpty());
        assertEquals("SENT", get(NET + "/orders/" + id, shop.owner()).str("status"), "nothing changed");

        // Another shop cannot order through this shop's connection.
        assertEquals(404, post(NET + "/orders", orderJson(null, conn, orderLine(product, "1")), other.owner()).status());
        // Suppliers never see the shop's own item ids.
        Resp seen = get(PORTAL + "/orders/" + id, s.token());
        assertTrue(seen.body().at("/lines/0/itemId").isNull(), seen.raw());
        assertFalse(seen.raw().contains(item));
    }

    @Test
    void orderLinesKeepWhatWasOrderedWhenTheCatalogueOrLinkChanges() {
        Shop shop = shop();
        Sup s = approvedSupplier(true, "Snapshot " + unique(""));
        String conn = connect(shop.owner(), s);
        String item = item(shop.owner(), "Biscuits", PCS, "10", "12");
        String other = item(shop.owner(), "Cookies", PCS, "10", "12");
        String product = product(s, "Glucose biscuits", "box", "240");
        link(shop.owner(), conn, product, item, PCS, "24");
        Resp placed = order(shop.owner(), conn, orderLine(product, "2"));
        String id = placed.str("id");

        put(PORTAL + "/products/" + product, productJson("Renamed biscuits", "carton", "999"), s.token(), null);
        post(PORTAL + "/products/" + product + "/deactivate", null, s.token());
        link(shop.owner(), conn, product, other, PCS, "12");

        Resp now = get(NET + "/orders/" + id, shop.owner());
        JsonNode line = now.body().get("lines").get(0);
        assertEquals("Glucose biscuits", text(line.get("productNameEn")));
        assertEquals("box", text(line.get("unit")));
        assertEquals("240.00", text(line.get("unitPrice")));
        assertEquals("480.00", text(line.get("lineAmount")));
        assertEquals(item, text(line.get("itemId")));
        assertEquals("24", text(line.get("unitsPerPack")));
        assertEquals("Glucose biscuits", text(get(PORTAL + "/orders/" + id, s.token()).at("/lines/0/productNameEn")));
        // The deactivated product can no longer be ordered.
        assertEquals("PRODUCT_NOT_AVAILABLE",
                post(NET + "/orders", orderJson(null, conn, orderLine(product, "1")), shop.owner()).str("code"));
    }

    @Test
    void sendingTheSameOrderTwicePlacesItOnce() {
        Shop shop = shop();
        Sup s = approvedSupplier(true, "Once " + unique(""));
        String conn = connect(shop.owner(), s);
        String item = item(shop.owner(), "Rice", KG, "40", "50");
        String product = product(s, "Rice", "kg", "38");
        link(shop.owner(), conn, product, item, KG, "1");
        String id = newId();
        Resp first = post(NET + "/orders", orderJson(id, conn, orderLine(product, "5")), shop.owner());
        Resp second = post(NET + "/orders", orderJson(id, conn, orderLine(product, "5")), shop.owner());
        assertEquals(201, first.status(), first.raw());
        assertEquals(id, second.str("id"), second.raw());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE id = UUID_TO_BIN(?)",
                Long.class, id));
        // Another shop cannot use this shop's connection (or take over the id: see NetworkIntegrityIT).
        Shop other = shop();
        assertEquals(404, post(NET + "/orders", orderJson(id, conn, orderLine(product, "5")), other.owner()).status());
    }

    @Test
    void orderEventsCannotBeChangedThroughTheApplication() {
        // Insert-only repositories and immutable entities; the database rows are kept as written.
        Shop shop = shop();
        Sup s = approvedSupplier(true, "History " + unique(""));
        String conn = connect(shop.owner(), s);
        String item = item(shop.owner(), "Jaggery", KG, "60", "70");
        String product = product(s, "Jaggery", "kg", "55");
        link(shop.owner(), conn, product, item, KG, "1");
        String id = order(shop.owner(), conn, orderLine(product, "1")).str("id");
        supplierAct(s, id, "accept", null);
        long events = jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_event WHERE order_id = UUID_TO_BIN(?)",
                Long.class, id);
        assertEquals(2, events);
        String first = jdbc.queryForObject("SELECT to_status FROM purchase_order_event WHERE order_id = UUID_TO_BIN(?) "
                + "ORDER BY id LIMIT 1", String.class, id);
        assertEquals("SENT", first);
        assertTrue(java.util.Arrays.stream(app.stocklist.network.PurchaseOrderEventRepository.class.getMethods())
                .noneMatch(m -> m.getName().startsWith("delete")), "no delete method on the order history");
        assertTrue(java.util.Arrays.stream(app.stocklist.network.SupplierConnectionEventRepository.class.getMethods())
                .noneMatch(m -> m.getName().startsWith("delete")), "no delete method on the connection history");
    }

    // ================================================================== permissions

    @Test
    void ownersHoldTheNewPermissionsAndStaffNeedThemGranted() {
        Shop shop = shop();
        Resp me = get("/api/v1/auth/me", shop.owner());
        String perms = me.body().get("permissions").toString();
        assertTrue(perms.contains("SUPPLIER_NETWORK_MANAGE") && perms.contains("PURCHASE_ORDER_MANAGE"), perms);
        String staffPerms = get("/api/v1/auth/me", shop.staff()).body().get("permissions").toString();
        assertFalse(staffPerms.contains("SUPPLIER_NETWORK_MANAGE"), staffPerms);
        assertFalse(staffPerms.contains("PURCHASE_ORDER_MANAGE"), staffPerms);
        assertEquals(403, get(NET + "/directory", shop.staff()).status());
        assertEquals(403, get(NET + "/orders", shop.staff()).status());
        assertEquals("PERMISSION_DENIED", get(NET + "/connections", shop.viewer()).str("code"));

        String u = unique("net");
        Resp c = createStaff(shop.owner(), stepUp(shop.owner(), PW), u, "[\"SUPPLIER_NETWORK_MANAGE\"]");
        assertEquals(201, c.status(), c.raw());
        String token = staffSession(u, c.str("temporaryPassword")).str("accessToken");
        assertEquals(200, get(NET + "/directory", token).status());
        assertEquals(200, get(NET + "/connections", token).status(), "connections can be read with either");
        assertEquals(403, get(NET + "/orders", token).status(), "ordering needs PURCHASE_ORDER_MANAGE");

        // The catalogue rows still list both codes, staff_default off.
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM permission WHERE code IN "
                + "('SUPPLIER_NETWORK_MANAGE','PURCHASE_ORDER_MANAGE') AND staff_default = TRUE", Long.class));
    }

    private String staffWith(String owner, String permissionsJson) {
        String u = unique("perm");
        Resp c = createStaff(owner, stepUp(owner, PW), u, permissionsJson);
        assertEquals(201, c.status(), c.raw());
        return staffSession(u, c.str("temporaryPassword")).str("accessToken");
    }

    @Test
    void eachNewPermissionGrantsExactlyItsDocumentedAccess() {
        Shop shop = shop();
        String owner = shop.owner();
        Sup s = approvedSupplier(true);
        Sup other = approvedSupplier(true);
        String conn = connect(owner, s);
        String item = item(owner, "Sugar", KG, "40", "45");
        String product = product(s, "Sugar", "kg", "38");
        String linkPath = NET + "/connections/" + conn + "/products/" + product + "/link";
        assertEquals(200, link(owner, conn, product, item, KG, "1").status());
        String placed = order(owner, conn, orderLine(product, "5")).str("id");

        // Only PURCHASE_ORDER_MANAGE: reads connections and catalogues, orders; no discovery, no network changes.
        String orders = staffWith(owner, "[\"PURCHASE_ORDER_MANAGE\"]");
        assertEquals(403, get(NET + "/directory", orders).status(), "discovery needs SUPPLIER_NETWORK_MANAGE");
        assertEquals(403, get(NET + "/directory/" + other.orgId(), orders).status());
        assertEquals(200, get(NET + "/connections", orders).status());
        assertEquals(200, get(NET + "/connections/" + conn, orders).status());
        assertEquals(200, get(NET + "/connections/" + conn + "/catalogue", orders).status());
        assertEquals(403, post(NET + "/connections", "{\"supplierOrgId\":\"" + other.orgId() + "\"}", orders).status());
        assertEquals(403, put(NET + "/connections/" + conn + "/contact", "{\"createContact\":true}", orders, null)
                .status());
        assertEquals(403, link(orders, conn, product, item, KG, "2").status(), "product links need the network permission");
        assertEquals(403, delete(linkPath, orders, null).status());
        assertEquals(403, post(NET + "/connections/" + conn + "/end", null, orders).status());
        assertEquals(200, get(NET + "/orders", orders).status());
        assertEquals(200, get(NET + "/orders/" + placed, orders).status());
        Resp own = post(NET + "/orders", orderJson(null, conn, orderLine(product, "1")), orders);
        assertEquals(201, own.status(), own.raw());
        assertEquals(200, post(NET + "/orders/" + own.str("id") + "/cancel", null, orders).status());
        // Nothing changed through the refused calls.
        assertEquals("ACTIVE", get(NET + "/connections/" + conn, owner).str("status"));
        assertEquals("1", text(get(NET + "/connections/" + conn + "/catalogue", owner)
                .at("/products/0/link/unitsPerPack")));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM supplier_connection WHERE supplier_org_id = "
                + "UUID_TO_BIN(?)", Long.class, other.orgId()));

        // Only SUPPLIER_NETWORK_MANAGE: discovery, connections, contacts and links; no orders.
        String network = staffWith(owner, "[\"SUPPLIER_NETWORK_MANAGE\"]");
        assertEquals(200, get(NET + "/directory", network).status());
        assertEquals(200, get(NET + "/directory/" + other.orgId(), network).status());
        assertEquals(200, get(NET + "/connections/" + conn, network).status());
        assertEquals(200, get(NET + "/connections/" + conn + "/catalogue", network).status());
        assertEquals(200, link(network, conn, product, item, KG, "1").status());
        assertEquals(403, get(NET + "/orders", network).status(), "orders need PURCHASE_ORDER_MANAGE");
        assertEquals(403, get(NET + "/orders/" + placed, network).status());
        assertEquals(403, post(NET + "/orders", orderJson(null, conn, orderLine(product, "1")), network).status());
        assertEquals(403, post(NET + "/orders/" + placed + "/cancel", null, network).status());
        assertEquals(403, post(NET + "/orders/" + placed + "/receive", receiptJson(null, null), network).status());
        assertEquals("SENT", get(NET + "/orders/" + placed, owner).str("status"));
        assertEquals(201, post(NET + "/connections", "{\"supplierOrgId\":\"" + other.orgId() + "\"}", network)
                .status());
    }

    // ================================================================== dispatched goods that never arrive

    private record Dispatch(Shop shop, Sup sup, String conn, String item, String product) {}

    private Dispatch dispatchSetup() {
        Shop shop = shop();
        Sup s = approvedSupplier(true);
        String conn = connect(shop.owner(), s);
        linkContact(shop.owner(), conn, supplier(shop.owner(), "Lost lorry contact"));
        String item = item(shop.owner(), "Ghee", KG, "500", "560");
        String product = product(s, "Ghee", "kg", "480");
        assertEquals(200, link(shop.owner(), conn, product, item, KG, "1").status());
        return new Dispatch(shop, s, conn, item, product);
    }

    private String receiptPurchaseOf(String orderId) {
        return jdbc.queryForObject("SELECT BIN_TO_UUID(receipt_purchase_id) FROM purchase_order WHERE id = UUID_TO_BIN(?)",
                String.class, orderId);
    }

    private Resp failDelivery(String orderId, String reason, String token) {
        return post(NET + "/orders/" + orderId + "/delivery-failed",
                reason == null ? null : "{\"reason\":\"" + reason + "\"}", token);
    }

    @Test
    void theShopClosesADispatchedOrderWhoseGoodsNeverArrived() {
        Dispatch d = dispatchSetup();
        String owner = d.shop().owner();
        Resp placed = order(owner, d.conn(), orderLine(d.product(), "4"));
        String id = placed.str("id");
        String rp = receiptPurchaseOf(id);

        // Only a dispatched order.
        assertEquals("ORDER_STATUS", failDelivery(id, "Never came", owner).str("code"), "SENT");
        assertEquals(200, supplierAct(d.sup(), id, "accept", null).status());
        assertEquals("ORDER_STATUS", failDelivery(id, "Never came", owner).str("code"), "ACCEPTED");
        assertEquals(200, supplierAct(d.sup(), id, "dispatch", null).status());

        // A reason is required; only the shop, with PURCHASE_ORDER_MANAGE, and only its own order.
        Resp noReason = failDelivery(id, null, owner);
        assertEquals(400, noReason.status(), noReason.raw());
        assertEquals("REASON_REQUIRED", noReason.str("code"));
        assertEquals("REASON_REQUIRED", failDelivery(id, "   ", owner).str("code"));
        assertEquals(403, failDelivery(id, "Never came", d.shop().staff()).status());
        assertEquals(403, failDelivery(id, "Never came", staffWith(owner, "[\"SUPPLIER_NETWORK_MANAGE\"]")).status());
        assertEquals(404, failDelivery(id, "Never came", shop().owner()).status());
        assertEquals(401, failDelivery(id, "Never came", d.sup().token()).status(), "a supplier token is refused");
        Resp api = get("/v3/api-docs", null);
        assertTrue(api.raw().contains("/api/v1/supplier-network/orders/{orderId}/delivery-failed"));
        assertFalse(api.raw().contains("/api/v1/supplier-portal/orders/{orderId}/delivery-failed"),
                "suppliers have no way to close an order this way");
        assertEquals("DISPATCHED", get(NET + "/orders/" + id, owner).str("status"));

        Resp closed = failDelivery(id, "Lorry never arrived", owner);
        assertEquals(200, closed.status(), closed.raw());
        assertEquals("DELIVERY_FAILED", closed.str("status"));
        assertEquals("Lorry never arrived", closed.str("statusReason"));
        assertTrue(closed.str("deliveryFailedAt") != null, closed.raw());
        assertNull(closed.str("receiptStatus"), "not a receipt: no FULL or SHORT");
        assertNull(closed.str("purchaseId"));
        assertNull(text(closed.at("/lines/0/receivedQuantity")));
        assertNull(text(closed.at("/lines/0/receiptStatus")));
        JsonNode history = closed.body().get("history");
        JsonNode last = history.get(history.size() - 1);
        assertEquals("DISPATCHED", text(last.get("fromStatus")));
        assertEquals("DELIVERY_FAILED", text(last.get("toStatus")));
        assertEquals("SHOP", text(last.get("actorType")));
        assertTrue(text(last.get("actorId")) != null, last.toString());
        assertEquals("Lorry never arrived", text(last.get("reason")));
        assertNull(text(last.get("receiptStatus")));

        // No purchase, delivery or batch; stock unchanged.
        assertArrayEquals(new long[]{0, 0, 0}, receiptRows(rp));
        assertEquals(0, batchCount(d.item()));

        // The supplier sees the outcome and the reason.
        Resp seen = get(PORTAL + "/orders/" + id, d.sup().token());
        assertEquals("DELIVERY_FAILED", seen.str("status"));
        assertEquals("Lorry never arrived", seen.str("statusReason"));
        assertEquals(1, get(PORTAL + "/orders?status=DELIVERY_FAILED", d.sup().token()).body().size());
        assertEquals(1, get(NET + "/orders?status=DELIVERY_FAILED", owner).body().size());

        // Final: nothing else can happen to it.
        Resp receive = post(NET + "/orders/" + id + "/receive", receiptJson(null, null,
                receiptLine(lineFor(placed, d.product()), "4", null, null, null)), owner);
        assertEquals(409, receive.status(), receive.raw());
        assertEquals("ORDER_STATUS", receive.str("code"));
        assertEquals("ORDER_STATUS", failDelivery(id, "Again", owner).str("code"));
        assertEquals("ORDER_STATUS", post(NET + "/orders/" + id + "/cancel", null, owner).str("code"));
        for (String action : List.of("accept", "dispatch")) {
            assertEquals("ORDER_STATUS", supplierAct(d.sup(), id, action, null).str("code"), action);
        }
        assertEquals("ORDER_STATUS", supplierAct(d.sup(), id, "reject", "{\"reason\":\"x\"}").str("code"));
        assertArrayEquals(new long[]{0, 0, 0}, receiptRows(rp));
        assertEquals("DELIVERY_FAILED", get(NET + "/orders/" + id, owner).str("status"));
    }

    @Test
    void aDispatchedOrderCanStillBeClosedAfterTheConnectionEnded() {
        Dispatch d = dispatchSetup();
        String owner = d.shop().owner();
        String lost = order(owner, d.conn(), orderLine(d.product(), "2")).str("id");
        String received = order(owner, d.conn(), orderLine(d.product(), "1")).str("id");
        for (String id : List.of(lost, received)) {
            assertEquals(200, supplierAct(d.sup(), id, "accept", null).status());
            assertEquals(200, supplierAct(d.sup(), id, "dispatch", null).status());
        }
        Resp receivedOrder = get(NET + "/orders/" + received, owner);
        assertEquals(200, post(NET + "/orders/" + received + "/receive", receiptJson(null, null,
                receiptLine(lineFor(receivedOrder, d.product()), "1", null, null, null)), owner).status());

        assertEquals(200, post(PORTAL + "/connections/" + d.conn() + "/end", "{\"reason\":\"Closing\"}",
                d.sup().token()).status());
        assertEquals("DISPATCHED", get(NET + "/orders/" + lost, owner).str("status"), "ending keeps dispatched orders");
        Resp closed = failDelivery(lost, "Goods never arrived", owner);
        assertEquals(200, closed.status(), closed.raw());
        assertEquals("DELIVERY_FAILED", closed.str("status"));
        assertArrayEquals(new long[]{0, 0, 0}, receiptRows(receiptPurchaseOf(lost)));

        // A received order cannot be turned into a failed delivery.
        Resp late = failDelivery(received, "Too late", owner);
        assertEquals(409, late.status(), late.raw());
        assertEquals("ORDER_ALREADY_RECEIVED", late.str("code"));
        assertEquals(1, batchCount(d.item()), "only the received order's goods");
    }

    // ================================================================== quantity precision when ordering

    @Test
    void anOrderThatCouldNotBeReceivedInFullIsRefusedBeforeItIsSaved() {
        Shop shop = shop();
        String owner = shop.owner();
        Sup s = approvedSupplier(true);
        String conn = connect(owner, s);
        linkContact(owner, conn, supplier(owner, "Spice packs"));
        String item = item(owner, "Pepper", KG, "800", "900");
        String other = item(owner, "Clove", KG, "1200", "1400");
        String pack = product(s, "Pepper 125 g", "pack", "95");
        String loose = product(s, "Clove", "kg", "1100");
        assertEquals(200, link(owner, conn, pack, item, KG, "0.125").status());
        assertEquals(200, link(owner, conn, loose, other, KG, "1").status());
        String orders = "SELECT COUNT(*) FROM purchase_order WHERE connection_id = UUID_TO_BIN(?)";

        // 1.5 packs x 0.125 kg = 0.1875 kg: four decimals, refused.
        Resp refused = post(NET + "/orders", orderJson(null, conn, orderLine(pack, "1.5")), owner);
        assertEquals(400, refused.status(), refused.raw());
        assertEquals("QUANTITY_PRECISION", refused.str("code"));
        assertEquals("QUANTITY_PRECISION", post(NET + "/orders", orderJson(null, conn, orderLine(pack, "0.004")), owner)
                .str("code"), "0.0005 kg");
        // One bad line refuses the whole order: nothing is saved.
        assertEquals("QUANTITY_PRECISION", post(NET + "/orders", orderJson(null, conn, orderLine(loose, "2"),
                orderLine(pack, "1.5")), owner).str("code"));
        assertEquals(0L, jdbc.queryForObject(orders, Long.class, conn));

        // Valid combinations: 2 x 0.125 = 0.25, 1.6 x 0.125 = 0.2, 0.008 x 0.125 = 0.001, 1.234 x 1.
        for (String qty : List.of("2", "1.6", "0.008")) {
            Resp ok = post(NET + "/orders", orderJson(null, conn, orderLine(pack, qty)), owner);
            assertEquals(201, ok.status(), qty + ": " + ok.raw());
        }
        assertEquals(201, post(NET + "/orders", orderJson(null, conn, orderLine(loose, "1.234")), owner).status());

        // A link of 0.333: 3 x 0.333 = 0.999 is fine, 1.5 x 0.333 = 0.4995 is not.
        assertEquals(200, link(owner, conn, pack, item, KG, "0.333").status());
        assertEquals(201, post(NET + "/orders", orderJson(null, conn, orderLine(pack, "3")), owner).status());
        assertEquals("QUANTITY_PRECISION", post(NET + "/orders", orderJson(null, conn, orderLine(pack, "1.5")), owner)
                .str("code"));
        assertEquals(5L, jdbc.queryForObject(orders, Long.class, conn));

        // Every order that could be placed can be received in full: 1.6 packs of 0.125 kg = 0.2 kg in stock.
        assertEquals(200, link(owner, conn, pack, item, KG, "0.125").status());
        Resp placed = order(owner, conn, orderLine(pack, "1.6"));
        assertEquals(200, supplierAct(s, placed.str("id"), "accept", null).status());
        Resp received = post(NET + "/orders/" + placed.str("id") + "/receive", receiptJson(null, null,
                receiptLine(lineFor(placed, pack), "1.6", null, null, null)), owner);
        assertEquals(200, received.status(), received.raw());
        assertEquals("FULL", received.str("receiptStatus"));
        Resp batches = get("/api/v1/batches?itemId=" + item, owner);
        assertEquals("0.2", text(batches.body().get(0).get("quantity")), batches.raw());
    }
}
