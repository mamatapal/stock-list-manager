package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;

/**
 * The backup and restore procedure from docs/BACKUP_RESTORE.md (deploy/backup.sh, deploy/restore.sh), run against the
 * real MySQL 8.4 while the application is running: the dump is consistent, restores into an empty database, and
 * gives back exactly the rows (binary ids, decimals, dates) that existed when it was taken.
 */
class BackupRestoreIT extends PurchaseTestSupport {
    private static final String DUMP_FLAGS = "--single-transaction --routines --triggers --events --no-tablespaces "
            + "--set-gtid-purged=OFF --default-character-set=utf8mb4 --hex-blob";

    private ExecResult sh(String command) throws Exception {
        ExecResult r = MYSQL.execInContainer("sh", "-c", command);
        assertEquals(0, r.getExitCode(), command + "\n" + r.getStderr());
        return r;
    }

    private String root() {
        return "MYSQL_PWD='" + MYSQL.getPassword() + "' ";
    }

    private Map<String, Long> counts(String schema) {
        Map<String, Long> out = new LinkedHashMap<>();
        List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = ? "
                + "AND table_type = 'BASE TABLE' ORDER BY table_name", String.class, schema);
        for (String t : tables) {
            out.put(t, jdbc.queryForObject("SELECT COUNT(*) FROM `" + schema + "`.`" + t + "`", Long.class));
        }
        return out;
    }

    private List<Map<String, Object>> rows(String schema, String sql) {
        return jdbc.queryForList(sql.replace("{db}", "`" + schema + "`"));
    }

    @Test
    void aBackupRestoresExactlyWhatWasThere() throws Exception {
        Shop s = shop();
        String sup = supplier(s.owner(), "Backup Traders");
        String item = item(s.owner(), "Mustard oil", LITRE, "150.25", "180");
        String line = newId();
        Resp p = post("/api/v1/purchases", purchaseJson(sup, "BK-1", "2026-11-10", true,
                delivery(newId(), null, "2026-11-11", receipt(newId(), line, "2.5", "LOT-9", "2027-03-31")),
                pline(line, item, "12.5", LITRE, "PER_UNIT", "150.25", "180")), s.owner());
        assertEquals(201, p.status(), p.raw());

        String db = MYSQL.getDatabaseName();
        Map<String, Long> atBackup = counts(db);
        String purchasesSql = "SELECT HEX(id) id, invoice_no, purchase_date, total_cost, total_expected_profit, version "
                + "FROM {db}.purchase ORDER BY id";
        String linesSql = "SELECT HEX(id) id, item_name_snapshot, quantity, purchase_unit_price, selling_unit_price, total_cost "
                + "FROM {db}.purchase_item ORDER BY id";
        List<Map<String, Object>> purchasesAtBackup = rows(db, purchasesSql);
        List<Map<String, Object>> linesAtBackup = rows(db, linesSql);

        // Backup (as deploy/backup.sh does it), while the application keeps running.
        sh(root() + "mysqldump -uroot " + DUMP_FLAGS + " " + db + " | gzip > /tmp/backup.sql.gz");
        assertTrue(Long.parseLong(sh("stat -c %s /tmp/backup.sql.gz").getStdout().trim()) > 1000, "backup is not empty");

        // Work carries on after the backup.
        supplier(s.owner(), "Added after the backup");
        assertEquals(atBackup.get("supplier") + 1, counts(db).get("supplier"));

        // Restore (as deploy/restore.sh does it) into an empty database.
        sh(root() + "mysql -uroot -e 'DROP DATABASE IF EXISTS restore_check; CREATE DATABASE restore_check "
                + "CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL ON restore_check.* TO `" + MYSQL.getUsername() + "`@`%`'");
        sh("gunzip -c /tmp/backup.sql.gz | " + root() + "mysql -uroot --default-character-set=utf8mb4 restore_check");

        Map<String, Long> restored = counts("restore_check");
        assertEquals(atBackup, restored, "every table, with the row counts at the time of the backup");
        assertTrue(restored.containsKey("flyway_schema_history"), "migration history is part of the backup");
        assertEquals(rows(db, "SELECT version, checksum FROM {db}.flyway_schema_history WHERE success ORDER BY installed_rank"),
                rows("restore_check", "SELECT version, checksum FROM {db}.flyway_schema_history WHERE success ORDER BY installed_rank"));
        assertEquals(purchasesAtBackup, rows("restore_check", purchasesSql), "ids, dates and money are identical");
        assertEquals(linesAtBackup, rows("restore_check", linesSql), "historical price snapshots are identical");
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM restore_check.batch WHERE batch_no = 'LOT-9' "
                + "AND expiry_date = '2027-03-31' AND quantity = 2.5", Long.class));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM restore_check.app_user WHERE password_hash LIKE '$%'",
                Long.class) >= 1, "password hashes (never plain passwords) are restored");
        sh(root() + "mysql -uroot -e 'DROP DATABASE restore_check'");
    }

    @Test
    void afterARestorePhonesAreToldToDownloadEverythingAgain() {
        Shop s = shop();
        String before = get("/api/v1/sync/pull?since=0", s.owner()).str("dataEpoch");
        assertTrue(before != null && before.length() == 36, before);
        assertEquals(before, get("/api/v1/sync/pull?since=0", s.staff()).str("dataEpoch"), "the same for every user");
        // What deploy/restore.sh runs after loading the backup.
        jdbc.update("UPDATE system_state SET data_epoch = UUID() WHERE id = 1");
        String after = get("/api/v1/sync/pull?since=5", s.owner()).str("dataEpoch");
        assertTrue(!before.equals(after), "a restored database announces a new epoch");
    }
}
