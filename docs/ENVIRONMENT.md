# Environment and deployment guide

Stock List Manager has two parts:

| Part | What it is | Where it runs |
| --- | --- | --- |
| Backend | Java 21 Spring Boot service + MySQL 8.4 | One Linux server (or VM) with Docker |
| App | Android app (Android 8.0 / API 26 and newer) | Owner's and staff's phones |

All businesses share one backend; every row carries its business id and the server only ever uses the business of
the signed-in user (taken from the access token, never from the request).

## 1. Server requirements

- Linux with Docker Engine 24+ and the Docker Compose plugin.
- 2 vCPU, 2 GB RAM, 20 GB disk is enough for hundreds of shops; MySQL data grows slowly (text and numbers only).
- A domain name for the API (for example `api.yourshop.in`) whose DNS **A record points at the server**.
- Firewall: allow inbound **80** and **443** only (Caddy needs 80 for the certificate challenge). Do not expose
  3306 or 8080; in the provided compose file MySQL and the backend are on an internal network only.
- Outbound: 443 (Let's Encrypt), and your SMTP port (587) if password-reset emails are used.

## 2. Install

```bash
sudo mkdir -p /opt/stocklist && cd /opt/stocklist
# copy the contents of stock-list-backend-1.0.0.zip here (app.jar, Dockerfile, docker-compose.prod.yml, Caddyfile,
# .env.example, backup.sh, restore.sh, db/, docs/)
cp .env.example .env && chmod 600 .env
nano .env                                  # fill in every value, see section 3
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f backend   # wait for "Started StockListApplication"
curl https://api.yourshop.in/actuator/health                 # {"status":"UP"}
```

On first start the backend creates all tables itself (Flyway migrations V1–V7, see `db/migration`). There is **no
default account**: the first owner registers from the app ("Register your business").

## 3. Configuration (`.env`)

| Variable | Required | Meaning |
| --- | --- | --- |
| `API_DOMAIN` | yes | Public host name of the API. The app's server address is `https://<API_DOMAIN>/`. |
| `DB_PASSWORD` | yes | Password of the `stocklist` MySQL user. Long random value (`openssl rand -base64 24`). |
| `DB_ROOT_PASSWORD` | yes | MySQL root password, used only by backup/restore. |
| `JWT_SIGNING_KEY` | yes | Base64 of at least 32 random bytes (`openssl rand -base64 48`). The backend refuses to start with a shorter key. Changing it signs every user out. |
| `JWT_KEY_ID` | no | Label of the signing key (default `k1`). |
| `APP_BUSINESS_ZONE` | no | Time zone for "today" in expiry lists (default `Asia/Kolkata`). |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | recommended | Sends owners their password-reset code. **Without SMTP an owner who forgets the password cannot reset it** (staff passwords are reset by the owner in the app). |
| `SUPPLIER_JWT_SIGNING_KEY` | yes | Signing key of the Supplier Management app's tokens, separate from `JWT_SIGNING_KEY` (`openssl rand -base64 48`). Created automatically on the server by the deploy workflow if missing. Changing it signs every supplier out. |
| `ADMIN_TOTP_KEY` | yes | Encrypts the Supplier Administration administrators' authenticator keys (`openssl rand -base64 32`). Created automatically by the deploy workflow if missing. **Back it up with `.env`**: without it every administrator must be reset with `create-admin.sh reset`. |
| `BACKUP_DIR`, `BACKUP_KEEP_DAYS` | no | Where `backup.sh` writes and how long backups are kept (default `/var/backups/stocklist`, 30 days). |

Set by the compose file from `API_DOMAIN`: `PUBLIC_BASE_URL=https://<API_DOMAIN>` (the address used in emailed
supplier links). Until SMTP is configured, supplier emails are not sent; the server only logs that a link was
requested (never the link itself).

Fixed in production by the compose file: `SWAGGER_ENABLED=false` (no public API explorer) and
`MAIL_LOG_TOKENS=false` (reset codes are never written to logs).

Secrets live only in `.env` on the server (mode 600). They are not in the source code, the jar or the Docker image.

### Backend settings without environment variables

`application.yml` inside the jar holds the safe defaults: access tokens 15 minutes, refresh tokens 30 days (rotated
on every use; a reused refresh token revokes the whole session), step-up tokens 5 minutes, account lock after 5
wrong passwords for 15 minutes, 20 login attempts per minute per IP, reset codes valid 30 minutes, error responses
never contain messages from exceptions or stack traces.

## 4. Point the app at the server

The release APK has the server address built in (see [RELEASE.md](RELEASE.md)):
`android/keystore.properties` → `api.baseUrl=https://api.yourshop.in/`. Release builds only allow HTTPS.

## 5. Upgrades

1. `./backup.sh` (always back up before an upgrade).
2. Copy the new `app.jar` (and any changed deploy files) into `/opt/stocklist`.
3. `docker compose -f docker-compose.prod.yml up -d --build backend`
4. New database migrations run automatically on start-up. Migrations only ever add; they never edit an applied one
   (Flyway refuses to start if an applied migration file was changed).
5. Check `/actuator/health` and sign in from a phone.

**Migrations and recovery.** Flyway has no automatic undo: a migration that has run cannot be rolled back by the
backend. If a migration fails or the upgrade has to be reversed, stop the backend and restore the database from the
backup taken in step 1, following [BACKUP_RESTORE.md](BACKUP_RESTORE.md) ("Restore after data loss"), then start the
previous `app.jar`. So step 1 is not optional: check that the backup file was written (and copied off the server)
before step 3.

**Next backend deployment (Supplier Management and Supplier Network).** Phase S1 (V6) has not been
deployed yet, so the next backend deployment runs both of these migrations on start-up, in order:

| Migration | Contents |
| --- | --- |
| `V6__supplier_accounts_admin.sql` | Supplier Management accounts and Supplier Administration (Phase S1) |
| `V7__supplier_network.sql` | Supplier Network: directory listing, connections, supplier catalogue, product links, purchase orders, two new permissions (off for staff) |

Both only add tables, columns and permission rows; nothing existing is changed or removed. Take and check the backup
first (step 1). Supplier onboarding still needs working email before it is opened to suppliers.

Phones keep working offline during the restart and send their changes afterwards.

## 6. Operations

- **Health**: `GET /actuator/health` → `{"status":"UP"}` (no details are exposed).
- **Logs**: `docker compose -f docker-compose.prod.yml logs backend`. Passwords, tokens and reset codes are never
  logged.
- **Backups**: daily cron of `backup.sh`, copied off the server. See [BACKUP_RESTORE.md](BACKUP_RESTORE.md).
- **Certificates**: renewed automatically by Caddy.
- **Signing everyone out** (for example after a suspected key leak): set a new `JWT_SIGNING_KEY` and restart.

## 7. Development environment (Windows)

```powershell
docker compose -f docker-compose.dev.yml up -d          # MySQL on localhost:3307
cd backend; .\mvnw.cmd test                             # 120+ tests against real MySQL (needs Docker Desktop)
.\scripts\dev-backend.ps1                               # runs the backend on :8080 with Swagger UI
```
Android: open `android/` in Android Studio. Debug builds use `http://10.0.2.2:8080/` (the PC as seen from the
emulator); `devB`/`devC` builds install side by side to act as extra phones.
