# Supplier Administration

Suppliers register themselves in the **Supplier Management** app. Nobody can use supplier business features until
an administrator approves the business in the **Supplier Administration** portal.

## 1. How a supplier gets approved

1. The supplier registers in the app with the business details, mobile number, email and a password.
2. An email with a verification link arrives (valid 24 hours, works once). The supplier opens it and presses
   **सत्यापित करें / Verify**. No SMS and no codes are involved.
3. The business now waits for approval. An administrator signs in at `https://<server>/admin/`, opens
   **Waiting for approval**, checks the details (the page warns about other suppliers with the same GSTIN or mobile
   number) and presses **Approve** or **Reject** (with a reason the supplier will see).
4. The supplier gets an email. A rejected supplier can correct the details in the app; saving sends them for
   review again.
5. An approved supplier can be **suspended** (with a reason) and later **reinstated**. Suspension takes effect
   immediately. Every step is kept in the supplier's history.

Until email is configured (Amazon SES, see the plan), links are not emailed. For testing before then, the logs show
only that a link was requested; nothing secret is written to the logs in production.

## 2. Account recovery (who can take over an account)

Only someone who can read the registered email can get into a supplier account:

- Nobody can sign in until the email address has been verified with the emailed link.
- A forgotten password can only be replaced through a single-use link sent to the registered email (valid 1 hour;
  a newer link cancels older ones). There are no recovery questions, SMS or codes, and administrators cannot set a
  supplier's password.
- After a reset or a password change, every other phone is signed out and the registered email receives a notice.
- Every step is kept in the supplier's history with the time and IP address: reset requested, password reset,
  password changed, a second registration attempt with the same email, and "email verified through a password-reset
  link". In the last case the portal also shows a warning, because the business details were typed before the email
  was proven: check that they belong to the email owner before approving.

## 3. Administrators

Administrators sign in with **username + password + a 6-digit code from an authenticator app** (Google
Authenticator, Microsoft Authenticator or similar). Five failed attempts lock the account for 15 minutes. A portal
session ends after 30 minutes without use and always after 8 hours.

Administrators can only be created on the server. Connect to the server with SSH as the `deploy` user, then:

```bash
cd /opt/stocklist
./create-admin.sh create alice "Alice Sharma" alice@example.com   # type the password twice (12+ characters)
```

The command prints an authenticator key once. On Alice's phone: open the authenticator app, choose
**Add account > Enter a setup key**, type the account name and the key, and choose **Time based**. Then sign in at
`https://<server>/admin/`.

| Task | Command |
| --- | --- |
| Lost phone or forgotten password | `./create-admin.sh reset alice` (new password and new key; signs out everywhere) |
| Remove access | `./create-admin.sh deactivate alice` |

Keep `ADMIN_TOTP_KEY` from `/opt/stocklist/.env` in your password manager together with the other server secrets.
