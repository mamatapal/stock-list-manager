#!/usr/bin/env bash
# Consistent online backup of the Stock List Manager database (the app keeps running).
# Usage: ./backup.sh            -> $BACKUP_DIR/stocklist-YYYYmmdd-HHMMSS.sql.gz
# Cron (daily 02:30):  30 2 * * * cd /opt/stocklist && ./backup.sh >> /var/log/stocklist-backup.log 2>&1
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a
BACKUP_DIR="${BACKUP_DIR:-/var/backups/stocklist}"
KEEP="${BACKUP_KEEP_DAYS:-30}"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
STAMP="$(date -u +%Y%m%d-%H%M%S)"
OUT="$BACKUP_DIR/stocklist-$STAMP.sql.gz"
TMP="$OUT.partial"

# Same options as BackupRestoreIT, which checks that such a dump restores exactly.
docker compose -f docker-compose.prod.yml exec -T -e MYSQL_PWD="$DB_ROOT_PASSWORD" mysql \
  mysqldump -uroot --single-transaction --routines --triggers --events --no-tablespaces \
  --set-gtid-purged=OFF --default-character-set=utf8mb4 --hex-blob stocklist | gzip -9 > "$TMP"

# A good dump ends with "Dump completed"; refuse to keep a truncated one.
if ! gunzip -c "$TMP" | tail -n 1 | grep -q "Dump completed"; then
  echo "$(date -u) backup FAILED: dump incomplete" >&2; rm -f "$TMP"; exit 1
fi
mv "$TMP" "$OUT"
chmod 600 "$OUT"
sha256sum "$OUT" > "$OUT.sha256"
find "$BACKUP_DIR" -name 'stocklist-*.sql.gz*' -mtime "+$KEEP" -delete
echo "$(date -u) backup OK: $OUT ($(du -h "$OUT" | cut -f1))"
# Copy $OUT off this server too (another machine, cloud storage): a backup on the same disk is not a backup.
