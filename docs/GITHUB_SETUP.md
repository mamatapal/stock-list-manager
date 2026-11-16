# Running everything from GitHub (no software on your computer)

Everything is built and deployed by GitHub Actions. You only use a web browser.

| Workflow (Actions tab) | When it runs | What you get |
| --- | --- | --- |
| **Android APK** | every push to `main`, every pull request, or **Run workflow** | `Stock-List-Manager-debug-APK` (testing) and `Stock-List-Manager-staging-APK` (minified, like the release) |
| **Android release APK** | **Run workflow**, or pushing a tag like `v1.0.1` | `Stock-List-Manager-release-APK`: signed with your key; install this on real phones |
| **Backend tests** | every change under `backend/` | test results; nothing is deployed |
| **Deploy backend** | only **Run workflow** | tests, then installs/updates the server (a database backup is made first) |

## One-time setup

### 1. Repository secrets and variables
Repository → **Settings → Secrets and variables → Actions**.

**Secrets** (tab "Secrets" → New repository secret):

| Name | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the release signing key, as text (from the key file you were given) |
| `ANDROID_KEYSTORE_PASSWORD` | its password |
| `DEPLOY_SSH_KEY` | the private deploy key (the whole block from `-----BEGIN OPENSSH PRIVATE KEY-----` to `-----END OPENSSH PRIVATE KEY-----`) |

**Variables** (tab "Variables" → New repository variable):

| Name | Value |
| --- | --- |
| `DEPLOY_HOST` | your server's IP address, e.g. `203.0.113.10` |
| `API_BASE_URL` | optional: `https://api.yourshop.in/` if you use your own domain. Without it, apps use `https://<ip-with-dashes>.sslip.io/` |

Keep a copy of the signing key file and its password outside GitHub (secrets cannot be read back). If the key is lost,
installed apps can never be updated; people would have to uninstall and reinstall.

### 2. The server
On AWS (free plan): follow [AWS_SETUP.md](AWS_SETUP.md) instead of the generic steps below.

1. At a hosting provider, create a server: **Ubuntu 24.04**, 2 GB RAM, location India (Mumbai/Bangalore/Delhi).
2. In the provider's "user data" / "post-install script" / "startup script" box, paste the whole of
   [`deploy/server-setup.sh`](../deploy/server-setup.sh). (No such box? Open the provider's web console as root and
   run: `curl -fsSL https://raw.githubusercontent.com/mamatapal/stock-list-manager/main/deploy/server-setup.sh | bash`)
3. Wait ~5 minutes, then put the server's IP address in the variable `DEPLOY_HOST`.
4. Actions → **Deploy backend** → **Run workflow**. When it is green, its summary shows the app server address.
5. Actions → **Android release APK** → **Run workflow**. Download the APK from the run's **Artifacts** and install it.
6. In the app, **Register your business** to create the owner account.

### Own domain (optional)
Point an A record (e.g. `api.yourshop.in`) at the server, change `API_DOMAIN` in `/opt/stocklist/.env` on the server
(provider web console: `nano /opt/stocklist/.env`), set the variable `API_BASE_URL=https://api.yourshop.in/`,
then run **Deploy backend** and **Android release APK** again and install the new APK.

### Password-reset emails (recommended)
Without email settings an owner who forgets the password cannot reset it. Fill in `SMTP_HOST`, `SMTP_PORT`,
`SMTP_USERNAME`, `SMTP_PASSWORD` and `MAIL_FROM` in `/opt/stocklist/.env` on the server (any SMTP service, e.g.
Brevo, Zoho, Gmail with an app password) and run **Deploy backend** again.

## Updating the app on phones
Run **Android release APK** again. Each run has a higher version number, so the new APK installs over the old one
and keeps the phone's data.
