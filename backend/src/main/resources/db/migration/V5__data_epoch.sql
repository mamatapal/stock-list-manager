-- Identifies the current "copy" of the data. deploy/restore.sh gives it a new value after a database restore; phones
-- that see a new value throw away their downloaded copy and download everything again, so they never keep records
-- the restored database does not have, and never miss changes because the change counter went back.
CREATE TABLE system_state (
    id         TINYINT     NOT NULL,
    data_epoch VARCHAR(36) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_system_state_single CHECK (id = 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO system_state (id, data_epoch) VALUES (1, UUID());
