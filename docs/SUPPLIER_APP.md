# Supplier Management app

A separate Android app (`supplier-android/`) for suppliers. It talks only to the supplier portal API
(`/api/v1/supplier-portal`) of the same backend; it never sees shop data. Every screen is in Hindi and English (both
shown, the chosen language first; switch on the first screen or in Settings). No SMS and no one-time codes are used.

## What it does (Phase S1)

- Register a supplier business: business name, trade name, contact person, mobile number, email, address, PIN code,
  GSTIN (optional) and a password.
- Verify the email with the link that is emailed (see `docs/SUPPLIER_ADMINISTRATION.md`).
- Sign in with email and password; see the review status: email not verified, waiting for approval, approved,
  not approved (with the reason; correct the details and send again), suspended (with the reason).
- Business details, language, change password, sign out, forgotten password (link by email).

Shop connections, the product catalogue and orders come in later phases.

## Builds on GitHub

- **Supplier Management APK** workflow: runs on every change under `supplier-android/` — unit tests (including the check
  that every text and every server error has Hindi), a debug APK and a minified staging APK. Download them from
  *Artifacts* on the run page. The debug APK uses the server in the repository variable `API_BASE_URL` (or the
  `DEPLOY_HOST` server).
- **Supplier Management release APK** workflow (manual): signed release APK, published on the Releases page with a
  `supplier-v…` tag. It is never marked *latest*, so the Stock List Manager download link is not affected.

## Release signing key (once)

The app has its **own** key, separate from Stock List Manager's. Create it on any computer with a JDK, keep the file
and password safe (losing them means the app can no longer be updated), then add two repository secrets:

```bash
keytool -genkeypair -v -keystore supplier-release.p12 -storetype PKCS12 -alias supplier \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Supplier Management"
base64 -w0 supplier-release.p12   # → secret SUPPLIER_ANDROID_KEYSTORE_BASE64
```

Secret `SUPPLIER_ANDROID_KEYSTORE_PASSWORD` = the password chosen above.
