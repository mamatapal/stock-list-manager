-- Phase 2: units, suppliers, items, out-of-stock lists, and the sync idempotency ledger.
-- Every synced business table has: id (UUID made on the phone), business_id, version (server-incremented),
-- change_seq (per-business order for pull), server timestamps, created_by/updated_by, deleted_at (soft delete).

-- Units. The 14 built-in units have business_id NULL and fixed ids shared with the Android app.
CREATE TABLE unit (
    id          BINARY(16)  NOT NULL,
    business_id BINARY(16)  NULL,
    code        VARCHAR(20) NOT NULL,
    name        VARCHAR(40) NOT NULL,
    version     BIGINT      NOT NULL,
    change_seq  BIGINT      NOT NULL,
    created_by  BINARY(16)  NULL,
    updated_by  BINARY(16)  NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    deleted_at  DATETIME(6) NULL,
    -- unique per business while not deleted; built-in units use a fixed scope value
    code_key    VARCHAR(20) AS (IF(deleted_at IS NULL, UPPER(code), NULL)) STORED,
    scope_key   BINARY(16) AS (IFNULL(business_id, 0x00000000000000000000000000000000)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_unit_code (scope_key, code_key),
    KEY idx_unit_business_seq (business_id, change_seq),
    CONSTRAINT fk_unit_business FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO unit (id, business_id, code, name, version, change_seq, created_at, updated_at) VALUES
(UNHEX('00000000000000000000000000000001'), NULL, 'PCS',    'Pieces',  1, 0, NOW(6), NOW(6)),
(UNHEX('00000000000000000000000000000002'), NULL, 'KG',     'Kilogram', 1, 0, NOW(6), NOW(6)),
(UNHEX('00000000000000000000000000000003'), NULL, 'GRAM',   'Gram',    1, 0, NOW(6), NOW(6)),
(UNHEX('00000000000000000000000000000004'), NULL, 'LITRE',  'Litre',   1, 0, NOW(6), NOW(6)),
(UNHEX('00000000000000000000000000000005'), NULL, 'ML',     'Millilitre', 1, 0, NOW(6), NOW(6)),
(UNHEX('00000000000000000000000000000006'), NULL, 'PACKET', 'Packet',  1, 0, NOW(6), NOW(6)),
(UNHEX('00000000000000000000000000000007'), NULL, 'BOX',    'Box',     1, 0, NOW(6), NOW(6)),
(UNHEX('00000000000000000000000000000008'), NULL, 'DOZEN',  'Dozen',   1, 0, NOW(6), NOW(6)),
(UNHEX('00000000000000000000000000000009'), NULL, 'BUNDLE', 'Bundle',  1, 0, NOW(6), NOW(6)),
(UNHEX('0000000000000000000000000000000A'), NULL, 'METER',  'Meter',   1, 0, NOW(6), NOW(6)),
(UNHEX('0000000000000000000000000000000B'), NULL, 'CARTON', 'Carton',  1, 0, NOW(6), NOW(6)),
(UNHEX('0000000000000000000000000000000C'), NULL, 'BOTTLE', 'Bottle',  1, 0, NOW(6), NOW(6)),
(UNHEX('0000000000000000000000000000000D'), NULL, 'BAG',    'Bag',     1, 0, NOW(6), NOW(6)),
(UNHEX('0000000000000000000000000000000E'), NULL, 'OTHER',  'Other',   1, 0, NOW(6), NOW(6));

CREATE TABLE supplier (
    id             BINARY(16)   NOT NULL,
    business_id    BINARY(16)   NOT NULL,
    name           VARCHAR(150) NOT NULL,
    contact_person VARCHAR(120) NULL,
    mobile         VARCHAR(15)  NULL,
    alt_phone      VARCHAR(15)  NULL,
    address        VARCHAR(300) NULL,
    city           VARCHAR(80)  NULL,
    gstin          VARCHAR(15)  NULL,
    notes          VARCHAR(1000) NULL,
    active         BOOLEAN      NOT NULL,
    version        BIGINT       NOT NULL,
    change_seq     BIGINT       NOT NULL,
    created_by     BINARY(16)   NULL,
    updated_by     BINARY(16)   NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    deleted_at     DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_business_id (business_id, id),
    KEY idx_supplier_business_name (business_id, name),
    KEY idx_supplier_business_seq (business_id, change_seq),
    CONSTRAINT fk_supplier_business FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE item (
    id                    BINARY(16)    NOT NULL,
    business_id           BINARY(16)    NOT NULL,
    name                  VARCHAR(150)  NOT NULL,
    category              VARCHAR(80)   NULL,
    default_unit_id       BINARY(16)    NOT NULL,
    latest_purchase_price DECIMAL(15,4) NULL,
    latest_selling_price  DECIMAL(15,4) NULL,
    last_purchase_date    DATE          NULL,
    preferred_supplier_id BINARY(16)    NULL,
    notes                 VARCHAR(1000) NULL,
    active                BOOLEAN       NOT NULL,
    version               BIGINT        NOT NULL,
    change_seq            BIGINT        NOT NULL,
    created_by            BINARY(16)    NULL,
    updated_by            BINARY(16)    NULL,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    deleted_at            DATETIME(6)   NULL,
    -- one live item per name per business (case-insensitive); deleted items free the name
    name_key              VARCHAR(150) AS (IF(deleted_at IS NULL, LOWER(name), NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_business_id (business_id, id),
    UNIQUE KEY uk_item_name (business_id, name_key),
    KEY idx_item_business_category (business_id, category),
    KEY idx_item_business_seq (business_id, change_seq),
    CONSTRAINT fk_item_business FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE RESTRICT,
    CONSTRAINT fk_item_unit FOREIGN KEY (default_unit_id) REFERENCES unit (id) ON DELETE RESTRICT,
    CONSTRAINT fk_item_supplier FOREIGN KEY (business_id, preferred_supplier_id) REFERENCES supplier (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_item_prices CHECK ((latest_purchase_price IS NULL OR latest_purchase_price >= 0)
        AND (latest_selling_price IS NULL OR latest_selling_price >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE stock_list (
    id               BINARY(16)    NOT NULL,
    business_id      BINARY(16)    NOT NULL,
    title            VARCHAR(150)  NOT NULL,
    list_date        DATE          NOT NULL,
    supplier_id      BINARY(16)    NULL,
    notes            VARCHAR(2000) NULL,
    status           VARCHAR(10)   NOT NULL,
    receiving_status VARCHAR(10)   NOT NULL,
    total_amount     DECIMAL(15,2) NULL,
    version          BIGINT        NOT NULL,
    change_seq       BIGINT        NOT NULL,
    created_by       BINARY(16)    NULL,
    updated_by       BINARY(16)    NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    deleted_at       DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_list_business_id (business_id, id),
    KEY idx_stock_list_business_date (business_id, list_date),
    KEY idx_stock_list_business_supplier (business_id, supplier_id),
    KEY idx_stock_list_business_seq (business_id, change_seq),
    CONSTRAINT fk_stock_list_business FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_list_supplier FOREIGN KEY (business_id, supplier_id) REFERENCES supplier (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_stock_list_status CHECK (status IN ('DRAFT', 'SAVED')),
    CONSTRAINT chk_stock_list_receiving CHECK (receiving_status IN ('PENDING', 'PARTIAL', 'FULL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Lines of a list. Saved and synced together with their list (one version for the whole list).
-- quantity is the originally requested quantity: receiving (Phase 4) never changes it.
CREATE TABLE stock_list_item (
    id            BINARY(16)    NOT NULL,
    business_id   BINARY(16)    NOT NULL,
    stock_list_id BINARY(16)    NOT NULL,
    item_id       BINARY(16)    NULL,
    item_name     VARCHAR(150)  NOT NULL,
    description   VARCHAR(500)  NULL,
    quantity      DECIMAL(15,3) NOT NULL,
    unit_id       BINARY(16)    NOT NULL,
    price_basis   VARCHAR(10)   NOT NULL,
    entered_price DECIMAL(15,4) NULL,
    line_total    DECIMAL(15,2) NULL,
    sort_order    INT           NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_list_item_business_id (business_id, id),
    KEY idx_stock_list_item_list (stock_list_id, sort_order),
    CONSTRAINT fk_sli_list FOREIGN KEY (business_id, stock_list_id) REFERENCES stock_list (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_sli_item FOREIGN KEY (business_id, item_id) REFERENCES item (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_sli_unit FOREIGN KEY (unit_id) REFERENCES unit (id) ON DELETE RESTRICT,
    CONSTRAINT chk_sli_quantity CHECK (quantity > 0),
    CONSTRAINT chk_sli_basis CHECK (price_basis IN ('PER_UNIT', 'TOTAL')),
    CONSTRAINT chk_sli_price CHECK (entered_price IS NULL OR entered_price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Idempotency ledger: a retried push with the same op_id returns the stored result instead of applying twice.
CREATE TABLE sync_operation (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    business_id    BINARY(16)   NOT NULL,
    op_id          BINARY(16)   NOT NULL,
    user_id        BINARY(16)   NOT NULL,
    device_id      VARCHAR(64)  NULL,
    entity_type    VARCHAR(20)  NOT NULL,
    entity_id      BINARY(16)   NOT NULL,
    action         VARCHAR(10)  NOT NULL,
    result_status  VARCHAR(10)  NOT NULL,
    result_code    VARCHAR(40)  NULL,
    result_message VARCHAR(300) NULL,
    result_version BIGINT       NULL,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sync_operation_op (business_id, op_id),
    CONSTRAINT fk_sync_operation_business FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
