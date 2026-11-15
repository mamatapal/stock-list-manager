# Stock List Manager

Inventory purchasing and supplier management for small shops: one Spring Boot + MySQL backend shared by
many businesses, and an offline-first Android app for owners and staff.

Design and plan: see the "Technical Implementation Plan" doc in the claude.ai project.

## Status

| Phase | Scope | State |
| --- | --- | --- |
| 1 | Registration, login, sessions, step-up, staff accounts, permissions, business profile, audit log | Backend: 37/37 tests passing (MySQL 8.4). Android: builds, 8/8 unit tests passing; manual emulator check pending |
| 2 | Suppliers, items, units, sync engine | Not started |
| 3 | Out-of-stock lists, PDF engine | Not started |
| 4 | Purchases, receiving, partial deliveries, batches | Not started |
| 5 | History, expiry, reports, dashboards | Not started |
| 6 | Hardening, backup/restore, deployment, release build | Not started |

## Folders

- `backend/` - Java 21, Spring Boot 4.1, MySQL 8, Flyway (`backend/src/main/resources/db/migration`)
- `android/` - Kotlin, Jetpack Compose, Room (SQLCipher), Hilt, Retrofit
- `docker-compose.dev.yml` - local MySQL for development

## Run locally (Windows, PowerShell)

1. Start MySQL (needs Docker Desktop):
   ```
   docker compose -f docker-compose.dev.yml up -d
   ```
2. Backend tests (database tests are skipped if Docker is not running):
   ```
   cd backend
   .\mvnw.cmd test
   ```
3. Run the backend:
   ```
   cd backend
   $env:DB_URL="jdbc:mysql://localhost:3307/stocklist?serverTimezone=UTC"
   $env:DB_USER="stocklist"; $env:DB_PASSWORD="stocklist-dev"
   $env:JWT_SIGNING_KEY="<output of: openssl rand -base64 48>"
   $env:SWAGGER_ENABLED="true"; $env:MAIL_LOG_TOKENS="true"
   .\mvnw.cmd spring-boot:run
   ```
   API documentation: http://localhost:8080/swagger-ui.html
4. Android: open the `android` folder in Android Studio and run on an emulator. The debug build talks to
   `http://10.0.2.2:8080/` (your PC as seen from the emulator). For a real phone on the same Wi-Fi, add
   `api.baseUrl=http://<your PC's IP>:8080/` to `android/local.properties`.

Never commit `.env`, `local.properties`, `keystore.properties` or keystores.
