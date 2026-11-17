-- Supplier network (Backend PR #1 of the scope reset): shops find approved suppliers, connect, browse their
-- catalogue, place purchase orders and receive the goods. Additive only: one new column on supplier_org (default
-- FALSE), two new permission rows, and new tables. Nothing in V1-V6 is changed or removed.
--
-- Stock is not kept here: goods receipt creates an ordinary completed purchase with one delivery through the existing
-- purchase workflow (purchase -> purchase_delivery -> batch), and the order only points at that purchase.

-- Suppliers must opt in before shops can find them; only APPROVED organisations are ever listed.
ALTER TABLE supplier_org
    ADD COLUMN listed_in_directory BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_supplier_org_directory ON supplier_org (listed_in_directory, status);

-- Two shop permissions, off for staff (staff_default FALSE): owners hold every permission implicitly, and no
-- existing staff member is granted them. sort_order continues after the 25 permissions of V1.
INSERT INTO permission (code, description, staff_default, sort_order) VALUES
('SUPPLIER_NETWORK_MANAGE', 'Find suppliers, manage supplier connections and link their products', FALSE, 26),
('PURCHASE_ORDER_MANAGE',   'Place and cancel purchase orders and receive the goods',            FALSE, 27);

-- One row per shop-supplier pair, for the whole life of the relationship. Only the shop starts a request;
-- REQUESTED -> ACTIVE / DECLINED (supplier) or CANCELLED (shop); ACTIVE -> ENDED (either side). A closed row can only
-- go back to REQUESTED through a new, explicit request by the shop, never straight to ACTIVE.
CREATE TABLE supplier_connection (
    id              BINARY(16)   NOT NULL,
    business_id     BINARY(16)   NOT NULL,
    supplier_org_id BINARY(16)   NOT NULL,
    -- The shop's own supplier contact (purchases need one), linked explicitly by the shopkeeper.
    supplier_id     BINARY(16)   NULL,
    status          VARCHAR(12)  NOT NULL,
    initiated_by    VARCHAR(10)  NOT NULL,
    requested_by    BINARY(16)   NOT NULL,
    requested_at    DATETIME(6)  NOT NULL,
    responded_at    DATETIME(6)  NULL,
    ended_at        DATETIME(6)  NULL,
    ended_by        VARCHAR(10)  NULL,
    status_reason   VARCHAR(500) NULL,
    version         BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_connection_pair (business_id, supplier_org_id),
    UNIQUE KEY uk_connection_business_id (business_id, id),
    -- Referenced by events and orders, so their shop and supplier always match the connection they belong to.
    UNIQUE KEY uk_connection_tenant (business_id, supplier_org_id, id),
    UNIQUE KEY uk_connection_contact (business_id, supplier_id),
    KEY idx_connection_org_status (supplier_org_id, status),
    CONSTRAINT fk_connection_business FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE RESTRICT,
    CONSTRAINT fk_connection_org FOREIGN KEY (supplier_org_id) REFERENCES supplier_org (id) ON DELETE RESTRICT,
    CONSTRAINT fk_connection_contact FOREIGN KEY (business_id, supplier_id) REFERENCES supplier (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_connection_status CHECK (status IN ('REQUESTED', 'ACTIVE', 'DECLINED', 'CANCELLED', 'ENDED')),
    CONSTRAINT chk_connection_initiator CHECK (initiated_by IN ('SHOP')),
    CONSTRAINT chk_connection_ended_by CHECK (ended_by IS NULL OR ended_by IN ('SHOP', 'SUPPLIER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Append-only history of each connection (the application never updates or deletes these rows).
CREATE TABLE supplier_connection_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    connection_id   BINARY(16)   NOT NULL,
    business_id     BINARY(16)   NOT NULL,
    supplier_org_id BINARY(16)   NOT NULL,
    actor_type      VARCHAR(10)  NOT NULL,
    actor_id        BINARY(16)   NULL,
    from_status     VARCHAR(12)  NULL,
    to_status       VARCHAR(12)  NOT NULL,
    reason          VARCHAR(500) NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_connection_event_connection (connection_id, id),
    CONSTRAINT fk_connection_event_connection FOREIGN KEY (business_id, supplier_org_id, connection_id) REFERENCES supplier_connection (business_id, supplier_org_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_connection_event_actor CHECK (actor_type IN ('SHOP', 'SUPPLIER', 'SYSTEM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- A supplier's catalogue. Kept apart from the shops' item tables; products are deactivated, never deleted.
CREATE TABLE supplier_product (
    id              BINARY(16)    NOT NULL,
    supplier_org_id BINARY(16)    NOT NULL,
    name_en         VARCHAR(150)  NOT NULL,
    name_hi         VARCHAR(150)  NULL,
    unit            VARCHAR(30)   NOT NULL,
    pack            VARCHAR(100)  NULL,
    price           DECIMAL(15,2) NOT NULL,
    active          BOOLEAN       NOT NULL,
    version         BIGINT        NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_product_org_id (supplier_org_id, id),
    KEY idx_supplier_product_org_active (supplier_org_id, active, name_en),
    CONSTRAINT fk_supplier_product_org FOREIGN KEY (supplier_org_id) REFERENCES supplier_org (id) ON DELETE RESTRICT,
    CONSTRAINT chk_supplier_product_price CHECK (price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- The shopkeeper's explicit choice: this supplier product is this shop item, bought in this shop unit, with this many
-- shop units in one supplier unit. Never created automatically. One link per product per shop, and only for a
-- supplier the shop has a connection row with.
CREATE TABLE supplier_product_link (
    id                  BINARY(16)    NOT NULL,
    business_id         BINARY(16)    NOT NULL,
    supplier_org_id     BINARY(16)    NOT NULL,
    supplier_product_id BINARY(16)    NOT NULL,
    item_id             BINARY(16)    NOT NULL,
    unit_id             BINARY(16)    NOT NULL,
    units_per_pack      DECIMAL(15,3) NOT NULL,
    created_by          BINARY(16)    NOT NULL,
    updated_by          BINARY(16)    NOT NULL,
    version             BIGINT        NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_link_business_product (business_id, supplier_product_id),
    UNIQUE KEY uk_product_link_business_id (business_id, id),
    KEY idx_product_link_business_org (business_id, supplier_org_id),
    CONSTRAINT fk_product_link_connection FOREIGN KEY (business_id, supplier_org_id) REFERENCES supplier_connection (business_id, supplier_org_id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_link_product FOREIGN KEY (supplier_org_id, supplier_product_id) REFERENCES supplier_product (supplier_org_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_link_item FOREIGN KEY (business_id, item_id) REFERENCES item (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_link_unit FOREIGN KEY (unit_id) REFERENCES unit (id) ON DELETE RESTRICT,
    CONSTRAINT chk_product_link_units CHECK (units_per_pack > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- A purchase order from one shop to one connected supplier. SENT -> ACCEPTED / REJECTED (supplier);
-- ACCEPTED -> DISPATCHED (supplier); ACCEPTED or DISPATCHED -> RECEIVED (shop); SENT or ACCEPTED -> CANCELLED (shop).
-- receipt_purchase_id is fixed when the order is placed and becomes the id of the purchase created on receipt, so a
-- repeated or concurrent receipt can never create a second purchase (primary key of purchase), and purchase_id is
-- unique, so a purchase can belong to only one order.
CREATE TABLE purchase_order (
    id                  BINARY(16)    NOT NULL,
    business_id         BINARY(16)    NOT NULL,
    supplier_org_id     BINARY(16)    NOT NULL,
    connection_id       BINARY(16)    NOT NULL,
    status              VARCHAR(12)   NOT NULL,
    note                VARCHAR(1000) NULL,
    total_amount        DECIMAL(15,2) NOT NULL,
    line_count          INT           NOT NULL,
    created_by          BINARY(16)    NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    accepted_at         DATETIME(6)   NULL,
    rejected_at         DATETIME(6)   NULL,
    dispatched_at       DATETIME(6)   NULL,
    received_at         DATETIME(6)   NULL,
    cancelled_at        DATETIME(6)   NULL,
    status_reason       VARCHAR(500)  NULL,
    receipt_purchase_id BINARY(16)    NOT NULL,
    purchase_id         BINARY(16)    NULL,
    received_by         BINARY(16)    NULL,
    version             BIGINT        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_order_business_id (business_id, id),
    -- Referenced by lines and events, so their shop and supplier always match the order.
    UNIQUE KEY uk_purchase_order_tenant (business_id, supplier_org_id, id),
    UNIQUE KEY uk_purchase_order_receipt_purchase (receipt_purchase_id),
    UNIQUE KEY uk_purchase_order_purchase (purchase_id),
    KEY idx_purchase_order_business_status (business_id, status, created_at),
    KEY idx_purchase_order_org_status (supplier_org_id, status, created_at),
    KEY idx_purchase_order_connection_status (connection_id, status),
    CONSTRAINT fk_purchase_order_business FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_order_org FOREIGN KEY (supplier_org_id) REFERENCES supplier_org (id) ON DELETE RESTRICT,
    -- Shop, supplier and connection together must be one existing connection.
    CONSTRAINT fk_purchase_order_connection FOREIGN KEY (business_id, supplier_org_id, connection_id) REFERENCES supplier_connection (business_id, supplier_org_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_order_purchase FOREIGN KEY (business_id, purchase_id) REFERENCES purchase (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_purchase_order_status CHECK (status IN ('SENT', 'ACCEPTED', 'REJECTED', 'DISPATCHED', 'RECEIVED', 'CANCELLED')),
    CONSTRAINT chk_purchase_order_total CHECK (total_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- What was ordered, frozen when the order is placed: the supplier's product name, unit, pack and price at that time,
-- and the shop item, unit and conversion the shopkeeper had linked. Later catalogue or link changes never alter it.
CREATE TABLE purchase_order_line (
    id                  BINARY(16)    NOT NULL,
    business_id         BINARY(16)    NOT NULL,
    supplier_org_id     BINARY(16)    NOT NULL,
    order_id            BINARY(16)    NOT NULL,
    line_no             INT           NOT NULL,
    supplier_product_id BINARY(16)    NULL,
    product_name_en     VARCHAR(150)  NOT NULL,
    product_name_hi     VARCHAR(150)  NULL,
    unit                VARCHAR(30)   NOT NULL,
    pack                VARCHAR(100)  NULL,
    quantity            DECIMAL(15,3) NOT NULL,
    unit_price          DECIMAL(15,2) NOT NULL,
    line_amount         DECIMAL(15,2) NOT NULL,
    item_id             BINARY(16)    NOT NULL,
    item_unit_id        BINARY(16)    NOT NULL,
    units_per_pack      DECIMAL(15,3) NOT NULL,
    received_quantity   DECIMAL(15,3) NULL,
    received_unit_price DECIMAL(15,2) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_order_line_no (order_id, line_no),
    KEY idx_purchase_order_line_business (business_id, order_id),
    CONSTRAINT fk_po_line_order FOREIGN KEY (business_id, supplier_org_id, order_id) REFERENCES purchase_order (business_id, supplier_org_id, id) ON DELETE RESTRICT,
    -- The product must belong to the order's supplier.
    CONSTRAINT fk_po_line_product FOREIGN KEY (supplier_org_id, supplier_product_id) REFERENCES supplier_product (supplier_org_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_po_line_item FOREIGN KEY (business_id, item_id) REFERENCES item (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_po_line_unit FOREIGN KEY (item_unit_id) REFERENCES unit (id) ON DELETE RESTRICT,
    CONSTRAINT chk_po_line_quantity CHECK (quantity > 0),
    CONSTRAINT chk_po_line_price CHECK (unit_price >= 0 AND line_amount >= 0),
    CONSTRAINT chk_po_line_units CHECK (units_per_pack > 0),
    CONSTRAINT chk_po_line_received CHECK (received_quantity IS NULL OR (received_quantity >= 0 AND received_quantity <= quantity))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Append-only status history of each order (the application never updates or deletes these rows).
CREATE TABLE purchase_order_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    order_id        BINARY(16)   NOT NULL,
    business_id     BINARY(16)   NOT NULL,
    supplier_org_id BINARY(16)   NOT NULL,
    actor_type      VARCHAR(10)  NOT NULL,
    actor_id        BINARY(16)   NULL,
    from_status     VARCHAR(12)  NULL,
    to_status       VARCHAR(12)  NOT NULL,
    reason          VARCHAR(500) NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_po_event_order (order_id, id),
    CONSTRAINT fk_po_event_order FOREIGN KEY (business_id, supplier_org_id, order_id) REFERENCES purchase_order (business_id, supplier_org_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_po_event_actor CHECK (actor_type IN ('SHOP', 'SUPPLIER', 'SYSTEM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
