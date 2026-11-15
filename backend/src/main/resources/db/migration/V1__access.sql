-- Phase 1: businesses, users, permissions, sessions, devices, audit log, change counter.
-- Never edit this file after it has been deployed; add V2, V3, ... instead.

CREATE TABLE business (
    id          BINARY(16)   NOT NULL,
    name        VARCHAR(150) NOT NULL,
    address     VARCHAR(300) NOT NULL,
    city        VARCHAR(80)  NOT NULL,
    state       VARCHAR(80)  NOT NULL,
    pin_code    VARCHAR(6)   NOT NULL,
    phone       VARCHAR(15)  NOT NULL,
    email       VARCHAR(254) NOT NULL,
    gstin       VARCHAR(15)  NULL,
    logo_key    VARCHAR(200) NULL,
    status      VARCHAR(16)  NOT NULL,
    version     BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_business_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE app_user (
    id                   BINARY(16)   NOT NULL,
    business_id          BINARY(16)   NOT NULL,
    username             VARCHAR(40)  NOT NULL,
    full_name            VARCHAR(120) NOT NULL,
    mobile               VARCHAR(15)  NULL,
    email                VARCHAR(254) NULL,
    password_hash        VARCHAR(255) NOT NULL,
    role                 VARCHAR(10)  NOT NULL,
    status               VARCHAR(10)  NOT NULL,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_count   INT          NOT NULL DEFAULT 0,
    locked_until         DATETIME(6)  NULL,
    last_login_at        DATETIME(6)  NULL,
    token_version        INT          NOT NULL DEFAULT 0,
    created_by           BINARY(16)   NULL,
    version              BIGINT       NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    -- exactly one owner per business
    owner_business_id    BINARY(16) AS (CASE WHEN role = 'OWNER' THEN business_id END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username),
    UNIQUE KEY uk_app_user_business_id (business_id, id),
    UNIQUE KEY uk_app_user_one_owner (owner_business_id),
    KEY idx_app_user_business_role (business_id, role),
    CONSTRAINT fk_app_user_business FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE RESTRICT,
    CONSTRAINT chk_app_user_role CHECK (role IN ('OWNER', 'STAFF')),
    CONSTRAINT chk_app_user_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE permission (
    code          VARCHAR(64)  NOT NULL,
    description   VARCHAR(200) NOT NULL,
    staff_default BOOLEAN      NOT NULL,
    sort_order    INT          NOT NULL,
    PRIMARY KEY (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO permission (code, description, staff_default, sort_order) VALUES
('SUPPLIER_VIEW',                'View suppliers',                                TRUE,  1),
('SUPPLIER_ADD',                 'Add suppliers',                                 TRUE,  2),
('SUPPLIER_EDIT',                'Edit suppliers',                                TRUE,  3),
('SUPPLIER_DELETE',              'Delete suppliers',                              FALSE, 4),
('ITEM_VIEW',                    'View items',                                    TRUE,  5),
('ITEM_ADD',                     'Add items',                                     TRUE,  6),
('ITEM_EDIT',                    'Edit items',                                    TRUE,  7),
('ITEM_DELETE',                  'Delete items',                                  FALSE, 8),
('LIST_CREATE',                  'Create out-of-stock lists',                     TRUE,  9),
('LIST_EDIT',                    'Edit out-of-stock lists',                       TRUE,  10),
('LIST_DELETE',                  'Delete out-of-stock lists',                     FALSE, 11),
('PURCHASE_RECORD',              'Record purchases',                              TRUE,  12),
('PURCHASE_EDIT',                'Edit purchases',                                FALSE, 13),
('PURCHASE_DELETE',              'Delete or cancel purchases',                    FALSE, 14),
('PURCHASE_PRICE_ENTER',         'Enter purchase prices',                         TRUE,  15),
('SELLING_PRICE_ENTER',          'Enter selling prices',                          TRUE,  16),
('CATALOG_SELLING_PRICE_CHANGE', 'Change the latest selling price in the item catalogue', FALSE, 17),
('PURCHASE_HISTORY_VIEW',        'View purchase history',                         TRUE,  18),
('PDF_GENERATE',                 'Generate PDFs',                                 TRUE,  19),
('PDF_SHARE',                    'Print and share PDFs',                          TRUE,  20),
('EXPIRY_VIEW',                  'View expiry tracking',                          TRUE,  21),
('EXPECTED_PROFIT_VIEW',         'View expected profit',                          FALSE, 22),
('PROFIT_REPORT_VIEW',           'View profit reports',                           FALSE, 23),
('SETTINGS_MANAGE',              'Manage business settings',                      FALSE, 24),
('STAFF_MANAGE',                 'Manage staff accounts',                         FALSE, 25);

CREATE TABLE user_permission (
    business_id     BINARY(16)  NOT NULL,
    user_id         BINARY(16)  NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    granted_by      BINARY(16)  NULL,
    granted_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, permission_code),
    KEY idx_user_permission_business (business_id),
    CONSTRAINT fk_user_permission_user FOREIGN KEY (business_id, user_id) REFERENCES app_user (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_permission_code FOREIGN KEY (permission_code) REFERENCES permission (code) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE refresh_token (
    id            BINARY(16)   NOT NULL,
    business_id   BINARY(16)   NOT NULL,
    user_id       BINARY(16)   NOT NULL,
    family_id     BINARY(16)   NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL,
    device_id     VARCHAR(64)  NOT NULL,
    device_name   VARCHAR(100) NULL,
    issued_at     DATETIME(6)  NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    revoked_at    DATETIME(6)  NULL,
    revoke_reason VARCHAR(20)  NULL,
    replaced_by   BINARY(16)   NULL,
    version       BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_user (user_id, revoked_at),
    KEY idx_refresh_token_family (family_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (business_id, user_id) REFERENCES app_user (business_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_token (
    id          BINARY(16)  NOT NULL,
    business_id BINARY(16)  NOT NULL,
    user_id     BINARY(16)  NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    used_at     DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    version     BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_hash (token_hash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (business_id, user_id) REFERENCES app_user (business_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE device (
    id           BINARY(16)   NOT NULL,
    business_id  BINARY(16)   NOT NULL,
    user_id      BINARY(16)   NOT NULL,
    device_id    VARCHAR(64)  NOT NULL,
    name         VARCHAR(100) NULL,
    app_version  VARCHAR(20)  NULL,
    fcm_token    VARCHAR(255) NULL,
    last_seen_at DATETIME(6)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    version      BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_user_device (user_id, device_id),
    KEY idx_device_business (business_id),
    CONSTRAINT fk_device_user FOREIGN KEY (business_id, user_id) REFERENCES app_user (business_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Append-only. In production the application's database account is granted INSERT and SELECT only
-- on this table (see deploy/mysql-grants.sql). business_id is NULL only for failed logins with an unknown username.
CREATE TABLE activity_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    business_id BINARY(16)   NULL,
    user_id     BINARY(16)   NULL,
    action      VARCHAR(50)  NOT NULL,
    record_type VARCHAR(50)  NULL,
    record_id   VARCHAR(64)  NULL,
    details     JSON         NULL,
    device_id   VARCHAR(64)  NULL,
    ip          VARCHAR(45)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_activity_business_time (business_id, created_at),
    KEY idx_activity_user_time (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Per-business change counter used by sync (Phase 2). Created with every business.
CREATE TABLE business_seq (
    business_id     BINARY(16) NOT NULL,
    next_change_seq BIGINT     NOT NULL,
    PRIMARY KEY (business_id),
    CONSTRAINT fk_business_seq_business FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
