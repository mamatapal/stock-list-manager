-- Phase 3: purchases (supplier bills), deliveries (goods arriving), batches (expiry), revisions (history).
-- Rows that record what happened are never updated in place or deleted: purchase lines keep price snapshots,
-- delivery lines are append-only (mistakes are fixed with CORRECTION rows), revisions are insert-only.

CREATE TABLE purchase (
    id                   BINARY(16)    NOT NULL,
    business_id          BINARY(16)    NOT NULL,
    supplier_id          BINARY(16)    NOT NULL,
    invoice_no           VARCHAR(60)   NULL,
    purchase_date        DATE          NOT NULL,
    stock_list_id        BINARY(16)    NULL,
    notes                VARCHAR(2000) NULL,
    -- DRAFT, PENDING (completed, nothing received), PARTIAL, RECEIVED, CANCELLED
    status               VARCHAR(10)   NOT NULL,
    received_date        DATE          NULL,
    over_received        BOOLEAN       NOT NULL DEFAULT FALSE,
    item_count           INT           NOT NULL,
    revision_no          INT           NOT NULL,
    total_cost           DECIMAL(15,2) NULL,
    total_expected_sales DECIMAL(15,2) NULL,
    total_expected_profit DECIMAL(15,2) NULL,
    priced_cost          DECIMAL(15,2) NULL,
    completed_at         DATETIME(6)   NULL,
    completed_by         BINARY(16)    NULL,
    cancelled_at         DATETIME(6)   NULL,
    cancel_reason        VARCHAR(500)  NULL,
    version              BIGINT        NOT NULL,
    change_seq           BIGINT        NOT NULL,
    created_by           BINARY(16)    NULL,
    updated_by           BINARY(16)    NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    deleted_at           DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_business_id (business_id, id),
    KEY idx_purchase_business_date (business_id, purchase_date),
    KEY idx_purchase_business_supplier_invoice (business_id, supplier_id, invoice_no),
    KEY idx_purchase_business_seq (business_id, change_seq),
    CONSTRAINT fk_purchase_business FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_supplier FOREIGN KEY (business_id, supplier_id) REFERENCES supplier (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_list FOREIGN KEY (business_id, stock_list_id) REFERENCES stock_list (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_purchase_status CHECK (status IN ('DRAFT', 'PENDING', 'PARTIAL', 'RECEIVED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Billed lines. Item name, unit code and prices are copied here when the line is saved, so later catalogue
-- changes never alter purchase history.
CREATE TABLE purchase_item (
    id                  BINARY(16)    NOT NULL,
    business_id         BINARY(16)    NOT NULL,
    purchase_id         BINARY(16)    NOT NULL,
    item_id             BINARY(16)    NOT NULL,
    item_name_snapshot  VARCHAR(150)  NOT NULL,
    unit_id             BINARY(16)    NOT NULL,
    unit_code_snapshot  VARCHAR(20)   NOT NULL,
    quantity            DECIMAL(15,3) NOT NULL,
    price_basis         VARCHAR(10)   NOT NULL,
    entered_amount      DECIMAL(15,4) NULL,
    purchase_unit_price DECIMAL(15,4) NULL,
    selling_unit_price  DECIMAL(15,4) NULL,
    total_cost          DECIMAL(15,2) NULL,
    expected_sales      DECIMAL(15,2) NULL,
    expected_profit     DECIMAL(15,2) NULL,
    margin_percent      DECIMAL(9,2)  NULL,
    markup_percent      DECIMAL(9,2)  NULL,
    stock_list_item_id  BINARY(16)    NULL,
    update_catalog      BOOLEAN       NOT NULL,
    sort_order          INT           NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_item_business_id (business_id, id),
    KEY idx_purchase_item_purchase (purchase_id, sort_order),
    KEY idx_purchase_item_list_line (business_id, stock_list_item_id),
    KEY idx_purchase_item_item (business_id, item_id),
    CONSTRAINT fk_pi_purchase FOREIGN KEY (business_id, purchase_id) REFERENCES purchase (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_pi_item FOREIGN KEY (business_id, item_id) REFERENCES item (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_pi_unit FOREIGN KEY (unit_id) REFERENCES unit (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pi_list_line FOREIGN KEY (business_id, stock_list_item_id) REFERENCES stock_list_item (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_pi_quantity CHECK (quantity > 0),
    CONSTRAINT chk_pi_basis CHECK (price_basis IN ('PER_UNIT', 'TOTAL')),
    CONSTRAINT chk_pi_prices CHECK ((entered_amount IS NULL OR entered_amount >= 0)
        AND (selling_unit_price IS NULL OR selling_unit_price >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- One arrival of goods against a purchase. The id comes from the phone, so a retried submission is recognised.
CREATE TABLE purchase_delivery (
    id            BINARY(16)    NOT NULL,
    business_id   BINARY(16)    NOT NULL,
    purchase_id   BINARY(16)    NOT NULL,
    received_date DATE          NOT NULL,
    received_by   BINARY(16)    NOT NULL,
    notes         VARCHAR(1000) NULL,
    created_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_delivery_business_id (business_id, id),
    KEY idx_delivery_purchase (business_id, purchase_id),
    KEY idx_delivery_business_date (business_id, received_date),
    CONSTRAINT fk_pd_purchase FOREIGN KEY (business_id, purchase_id) REFERENCES purchase (business_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Received quantities. RECEIPT rows are positive; CORRECTION rows are signed, point at the receipt they fix and
-- need a reason. Received quantity = sum of both kinds.
CREATE TABLE purchase_delivery_item (
    id                BINARY(16)    NOT NULL,
    business_id       BINARY(16)    NOT NULL,
    delivery_id       BINARY(16)    NOT NULL,
    purchase_item_id  BINARY(16)    NOT NULL,
    kind              VARCHAR(10)   NOT NULL,
    quantity_received DECIMAL(15,3) NOT NULL,
    corrects_id       BINARY(16)    NULL,
    reason            VARCHAR(300)  NULL,
    batch_no          VARCHAR(60)   NULL,
    expiry_date       DATE          NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pdi_business_id (business_id, id),
    KEY idx_pdi_delivery (delivery_id),
    KEY idx_pdi_purchase_item (business_id, purchase_item_id),
    CONSTRAINT fk_pdi_delivery FOREIGN KEY (business_id, delivery_id) REFERENCES purchase_delivery (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_pdi_line FOREIGN KEY (business_id, purchase_item_id) REFERENCES purchase_item (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_pdi_corrects FOREIGN KEY (business_id, corrects_id) REFERENCES purchase_delivery_item (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_pdi_kind CHECK (kind IN ('RECEIPT', 'CORRECTION')),
    CONSTRAINT chk_pdi_quantity CHECK ((kind = 'RECEIPT' AND quantity_received > 0)
        OR (kind = 'CORRECTION' AND quantity_received <> 0 AND corrects_id IS NOT NULL AND reason IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Stock that arrived, for expiry tracking. quantity = receipt plus its corrections.
CREATE TABLE batch (
    id               BINARY(16)    NOT NULL,
    business_id      BINARY(16)    NOT NULL,
    item_id          BINARY(16)    NOT NULL,
    supplier_id      BINARY(16)    NOT NULL,
    purchase_id      BINARY(16)    NOT NULL,
    delivery_item_id BINARY(16)    NOT NULL,
    batch_no         VARCHAR(60)   NULL,
    quantity         DECIMAL(15,3) NOT NULL,
    unit_id          BINARY(16)    NOT NULL,
    purchase_date    DATE          NOT NULL,
    received_date    DATE          NOT NULL,
    expiry_date      DATE          NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_delivery_item (delivery_item_id),
    KEY idx_batch_business_expiry (business_id, expiry_date),
    KEY idx_batch_business_item (business_id, item_id),
    CONSTRAINT fk_batch_business FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_item FOREIGN KEY (business_id, item_id) REFERENCES item (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_supplier FOREIGN KEY (business_id, supplier_id) REFERENCES supplier (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_purchase FOREIGN KEY (business_id, purchase_id) REFERENCES purchase (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_delivery_item FOREIGN KEY (business_id, delivery_item_id) REFERENCES purchase_delivery_item (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_unit FOREIGN KEY (unit_id) REFERENCES unit (id) ON DELETE RESTRICT,
    CONSTRAINT chk_batch_quantity CHECK (quantity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- The full previous state of a completed purchase, written before every edit or cancellation. Insert-only.
CREATE TABLE purchase_revision (
    id          BINARY(16)   NOT NULL,
    business_id BINARY(16)   NOT NULL,
    purchase_id BINARY(16)   NOT NULL,
    revision_no INT          NOT NULL,
    snapshot    JSON         NOT NULL,
    changed_by  BINARY(16)   NOT NULL,
    changed_at  DATETIME(6)  NOT NULL,
    reason      VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_revision (business_id, purchase_id, revision_no),
    CONSTRAINT fk_revision_purchase FOREIGN KEY (business_id, purchase_id) REFERENCES purchase (business_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
