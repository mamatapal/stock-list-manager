-- Phase S1: supplier organisations and their users (Supplier Management app), email links, and the administrators of
-- the Supplier Administration portal. New tables only: nothing in V1-V5 is changed. Supplier data is not tied to any
-- business; shops and suppliers are connected in a later phase.

CREATE TABLE supplier_org (
    id                BINARY(16)    NOT NULL,
    legal_name        VARCHAR(150)  NOT NULL,
    trade_name        VARCHAR(150)  NULL,
    contact_person    VARCHAR(120)  NOT NULL,
    mobile            VARCHAR(15)   NOT NULL,
    email             VARCHAR(254)  NOT NULL,
    address           VARCHAR(300)  NOT NULL,
    city              VARCHAR(80)   NOT NULL,
    state             VARCHAR(80)   NOT NULL,
    pin_code          VARCHAR(6)    NOT NULL,
    gstin             VARCHAR(15)   NULL,
    -- PENDING_EMAIL_VERIFICATION -> PENDING_APPROVAL -> APPROVED / REJECTED; APPROVED <-> SUSPENDED
    status            VARCHAR(30)   NOT NULL,
    email_verified_at DATETIME(6)   NULL,
    submitted_at      DATETIME(6)   NULL,
    reviewed_by       BINARY(16)    NULL,
    reviewed_at       DATETIME(6)   NULL,
    -- the reason shown to the supplier after a rejection or suspension
    status_reason     VARCHAR(500)  NULL,
    version           BIGINT        NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_supplier_org_status (status, submitted_at),
    KEY idx_supplier_org_gstin (gstin),
    KEY idx_supplier_org_mobile (mobile),
    CONSTRAINT chk_supplier_org_status CHECK (status IN
        ('PENDING_EMAIL_VERIFICATION', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SUSPENDED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- People who sign in to the Supplier Management app. The email (lower case) is the sign-in name.
CREATE TABLE supplier_user (
    id                 BINARY(16)   NOT NULL,
    supplier_org_id    BINARY(16)   NOT NULL,
    email              VARCHAR(254) NOT NULL,
    full_name          VARCHAR(120) NOT NULL,
    mobile             VARCHAR(15)  NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    role               VARCHAR(20)  NOT NULL,
    status             VARCHAR(10)  NOT NULL,
    email_verified_at  DATETIME(6)  NULL,
    failed_login_count INT          NOT NULL DEFAULT 0,
    locked_until       DATETIME(6)  NULL,
    last_login_at      DATETIME(6)  NULL,
    token_version      INT          NOT NULL DEFAULT 0,
    version            BIGINT       NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    owner_org_id       BINARY(16) AS (CASE WHEN role = 'SUPPLIER_OWNER' THEN supplier_org_id END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_user_email (email),
    UNIQUE KEY uk_supplier_user_one_owner (owner_org_id),
    KEY idx_supplier_user_org (supplier_org_id),
    CONSTRAINT fk_supplier_user_org FOREIGN KEY (supplier_org_id) REFERENCES supplier_org (id) ON DELETE RESTRICT,
    CONSTRAINT chk_supplier_user_role CHECK (role IN ('SUPPLIER_OWNER', 'SUPPLIER_STAFF')),
    CONSTRAINT chk_supplier_user_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE supplier_refresh_token (
    id            BINARY(16)   NOT NULL,
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
    UNIQUE KEY uk_supplier_refresh_hash (token_hash),
    KEY idx_supplier_refresh_user (user_id, revoked_at),
    KEY idx_supplier_refresh_family (family_id),
    CONSTRAINT fk_supplier_refresh_user FOREIGN KEY (user_id) REFERENCES supplier_user (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Single-use email links (verify the email address, reset the password). Only the SHA-256 of the token is stored.
CREATE TABLE supplier_email_token (
    id         BINARY(16)  NOT NULL,
    user_id    BINARY(16)  NOT NULL,
    purpose    VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    version    BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_email_token_hash (token_hash),
    KEY idx_supplier_email_token_user (user_id, purpose, created_at),
    CONSTRAINT fk_supplier_email_token_user FOREIGN KEY (user_id) REFERENCES supplier_user (id) ON DELETE RESTRICT,
    CONSTRAINT chk_supplier_email_token_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'PASSWORD_RESET'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Administrators of the Supplier Administration portal. Created only on the server (deploy/create-admin.sh).
-- totp_secret is encrypted with ADMIN_TOTP_KEY (AES-GCM); totp_last_step stops a code being used twice.
CREATE TABLE platform_admin (
    id                 BINARY(16)   NOT NULL,
    username           VARCHAR(40)  NOT NULL,
    full_name          VARCHAR(120) NOT NULL,
    email              VARCHAR(254) NULL,
    password_hash      VARCHAR(255) NOT NULL,
    totp_secret        VARCHAR(255) NOT NULL,
    totp_last_step     BIGINT       NOT NULL DEFAULT 0,
    status             VARCHAR(10)  NOT NULL,
    failed_login_count INT          NOT NULL DEFAULT 0,
    locked_until       DATETIME(6)  NULL,
    last_login_at      DATETIME(6)  NULL,
    version            BIGINT       NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_admin_username (username),
    CONSTRAINT chk_platform_admin_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Portal sign-in sessions. The browser holds the random session id in an HttpOnly cookie; only its SHA-256 is here.
CREATE TABLE platform_admin_session (
    id           BINARY(16)  NOT NULL,
    admin_id     BINARY(16)  NOT NULL,
    session_hash VARCHAR(64) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    expires_at   DATETIME(6) NOT NULL,
    revoked_at   DATETIME(6) NULL,
    ip           VARCHAR(45) NULL,
    version      BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_admin_session_hash (session_hash),
    KEY idx_platform_admin_session_admin (admin_id, revoked_at),
    CONSTRAINT fk_platform_admin_session_admin FOREIGN KEY (admin_id) REFERENCES platform_admin (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Append-only history of a supplier organisation: registration, email verification, submissions, reviews.
CREATE TABLE supplier_org_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    supplier_org_id BINARY(16)   NOT NULL,
    action          VARCHAR(30)  NOT NULL,
    actor_type      VARCHAR(10)  NOT NULL,
    actor_id        BINARY(16)   NULL,
    from_status     VARCHAR(30)  NULL,
    to_status       VARCHAR(30)  NULL,
    reason          VARCHAR(500) NULL,
    ip              VARCHAR(45)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_supplier_org_event_org (supplier_org_id, created_at),
    CONSTRAINT fk_supplier_org_event_org FOREIGN KEY (supplier_org_id) REFERENCES supplier_org (id) ON DELETE RESTRICT,
    CONSTRAINT chk_supplier_org_event_actor CHECK (actor_type IN ('SUPPLIER', 'ADMIN', 'SYSTEM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
