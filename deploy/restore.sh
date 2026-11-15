#!/usr/bin/env bash
# Restores a backup made by backup.sh. Stops the backend, replaces the database, starts the backend again.
# Usage: ./restore.sh /var/backups/stocklist/stocklist-20261115-023000.sql.gz
# Test a backup without touching production:  ./restore.sh <file> --check   (restores into stocklist_check)
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a
FILE="${1:?usage: restore.sh <backup.sql.gz> [--check]}"
MODE="${2:-}"
[ -f "$FILE" ] || { echo "No such file: $FILE" >&2; exit 1; }
if [ -f "$FILE.sha256" ]; then sha256sum -c "$FILE.sha256"; fi
gunzip -c "$FILE" | tail -n 1 | grep -q "Dump completed" || { echo "Backup file is incomplete" >&2; exit 1; }

mysql_root() { docker compose -f docker-compose.prod.yml exec -T -e MYSQL_PWD="$DB_ROOT_PASSWORD" mysql mysql -uroot "$@"; }

if [ "$MODE" = "--check" ]; then
  mysql_root -e "DROP DATABASE IF EXISTS stocklist_check; CREATE DATABASE stocklist_check CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
  gunzip -c "$FILE" | mysql_root --default-character-set=utf8mb4 stocklist_check
  mysql_root -e "SELECT 'businesses', COUNT(*) FROM stocklist_check.business UNION ALL SELECT 'purchases', COUNT(*) FROM stocklist_check.purchase UNION ALL SELECT 'migrations', MAX(version) FROM stocklist_check.flyway_schema_history WHERE success"
  mysql_root -e "DROP DATABASE stocklist_check"
  echo "Backup restores correctly (checked in a temporary database)."
  exit 0
fi

read -r -p "This REPLACES the live database with $FILE. Type RESTORE to continue: " ok
[ "$ok" = "RESTORE" ] || { echo "Cancelled."; exit 1; }
./backup.sh   # safety copy of the current state first
docker compose -f docker-compose.prod.yml stop backend
mysql_root -e "DROP DATABASE stocklist; CREATE DATABASE stocklist CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL ON stocklist.* TO 'stocklist'@'%'"
gunzip -c "$FILE" | mysql_root --default-character-set=utf8mb4 stocklist
# Tell every phone to drop its downloaded copy and fetch the restored data again.
mysql_root -e "UPDATE stocklist.system_state SET data_epoch = UUID() WHERE id = 1"
docker compose -f docker-compose.prod.yml start backend
echo "Restored $FILE. Phones resynchronise on their next sync; see docs/BACKUP_RESTORE.md."
