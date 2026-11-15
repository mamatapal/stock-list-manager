# Backup and restore guide

## What holds the data

| Where | What | How it is protected |
| --- | --- | --- |
| Server MySQL database | Everything of every business: accounts (password hashes only), suppliers, items, lists, purchases, deliveries, batches, history, audit log | `backup.sh` daily, copied off the server |
| Server `.env` | Database passwords, token signing key, SMTP settings | Keep a copy in a password manager |
| Release signing key (`stocklist-release.jks`) | Needed to publish app updates | Keep two copies offline; see RELEASE.md |
| Phones | An encrypted copy of what each user may see, plus changes not yet sent | Re-downloaded from the server on sign-in; only **unsent** changes exist nowhere else |

Phones are not a backup and do not need one: signing in on a new or reset phone downloads everything again. The only
data that can be lost with a phone is what was recorded offline and never synced; the Sync screen shows how many
such changes there are, and sign-out warns before deleting them.

## Daily backup

```bash
cd /opt/stocklist
./backup.sh       # → /var/backups/stocklist/stocklist-YYYYmmdd-HHMMSS.sql.gz (+ .sha256)
```
- Consistent while the app is running (`mysqldump --single-transaction`); no downtime.
- Refuses to keep an incomplete dump; files are readable by root only; backups older than `BACKUP_KEEP_DAYS`
  (default 30) are deleted.
- Schedule it: `sudo crontab -e` → `30 2 * * * cd /opt/stocklist && ./backup.sh >> /var/log/stocklist-backup.log 2>&1`
- **Copy every backup to another place** (another server, cloud storage such as S3/Backblaze/Google Drive with
  `rclone`, or an encrypted USB disk). A backup on the same disk as the database does not survive a disk failure.
- The dump contains business data and password hashes: store it encrypted (for example `gpg -c`) when it leaves the
  server.

## Check that backups work (monthly)

```bash
./restore.sh /var/backups/stocklist/stocklist-20261115-023000.sql.gz --check
```
Restores into a temporary database, prints the number of businesses, purchases and the schema version, and
removes it again. Production is not touched. The same procedure is run by the automated test `BackupRestoreIT`
(dump while the app runs, restore into an empty database, compare every table, ids, money, dates and price history).

## Restore after data loss

```bash
cd /opt/stocklist
./restore.sh /var/backups/stocklist/stocklist-20261115-023000.sql.gz
# type RESTORE to confirm
```
The script:
1. checks the file (checksum and completeness),
2. makes a safety backup of the current database,
3. stops the backend, replaces the database with the backup, marks the data as restored, starts the backend.

What users see:
- Everything is as it was at the time of the backup. Changes made on the server after that are gone.
- On their next sync every phone notices the restore, throws away its downloaded copy and downloads the restored
  data again (tested in `SyncScenarioTest`). Changes a phone had **not yet sent** are kept and sent afterwards;
  if they refer to something that no longer exists, the Sync screen shows them for a decision.
- Accounts and passwords are those of the backup time. Anyone who changed their password after the backup uses the
  old one again.

## Moving to a new server

1. Install the new server (ENVIRONMENT.md) with the **same `.env`** (same `JWT_SIGNING_KEY` keeps everyone signed in).
2. Stop the backend on the old server, run `./backup.sh`, copy the file across.
3. `./restore.sh <file>` on the new server.
4. Point the `API_DOMAIN` DNS record at the new server. Phones keep the same address and carry on.

## Restoring one business or one record

Not supported as a self-service action: backups contain all businesses. Restore into a check database
(`--check` does this temporarily; or create `stocklist_check` by hand) and copy the needed rows with SQL, scoped by
`business_id`. Most single-record mistakes do not need a restore: deletions are soft, cancelled purchases are kept,
and every edit of a completed purchase is kept in its revision history.
