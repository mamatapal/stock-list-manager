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

## Build on GitHub (no local tools needed)

Every push to `main` (and every pull request) runs two GitHub Actions workflows:

- **Android APK** (`.github/workflows/android-apk.yml`): JetBrains JDK 21, Android SDK 35, Gradle 8.13 wrapper;
  runs `./gradlew testDebugUnitTest assembleDebug` in `android/` and uploads the installable debug APK as the
  artifact **Stock-List-Manager-debug-APK** (kept 30 days). Open the run under the repository's **Actions** tab and
  download it from **Artifacts**. To point the APK at your server, set the repository variable `API_BASE_URL`
  (Settings → Secrets and variables → Actions → Variables) or enter it when starting the workflow by hand
  (**Run workflow**). Without it the APK uses `http://10.0.2.2:8080/`, which only works in an emulator on the PC
  running the backend.
- **Backend tests** (`.github/workflows/backend-ci.yml`): JDK 21, `./mvnw verify` in `backend/` against a
  throw-away MySQL 8.4 (Testcontainers). It never deploys anything.
