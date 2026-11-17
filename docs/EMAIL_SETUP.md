# Email setup: Gmail API (OAuth 2.0)

Stock List Manager sends six kinds of email: the shop owner's password-reset code, and the supplier emails
(verification link, password-reset link, "already registered" notice, password-changed notice, and approval
decisions). How they leave the server is chosen by `MAIL_PROVIDER`:

| `MAIL_PROVIDER` | How email is sent |
| --- | --- |
| `smtp` (default, also when unset) | Through the SMTP server in `SMTP_HOST` and friends. Nothing changes from earlier versions. |
| `gmail-api` | Through the Gmail API of one Gmail account, authorized once with OAuth 2.0. For use without an own domain. |

The email text (Hindi and English), links, rate limits, audit history and API answers are the same with both.

> **Status of `gmail-api`: a limited trial for the owner only.** The Google Cloud app stays *unverified*. Whether
> Google's personal-use exemption covers this commercial service is **not established**. You will see an
> "unverified app" warning when you authorize, and Google could restrict the app later. This is not approved
> long-term email for a public commercial service. The long-term answer is an own domain with a verified email
> provider (see [the last section](#later-own-domain-and-a-verified-email-provider)). SMTP stays available.

## How it works

1. **Once, on your Windows computer:** `tools/windows/gmail-authorize.ps1` signs in to Google (Desktop app flow on
   `127.0.0.1` with PKCE, `state` and `nonce`), checks everything Google returns, and sends the result to the
   server **only over SSH** (standard input). Nothing is saved on the computer, put on the clipboard or shown.
2. **On the server:** `set-gmail-token.sh` (the only command your restricted SSH key can run) writes three values to
   `/opt/stocklist/.env`.
3. **In the backend:** an access token is fetched from Google only when an email is sent. There is no timer and no
   call at startup.

No step uses elevated rights: `/opt/stocklist` and `.env` belong to the `deploy` user, which runs the script.

## What you need

- The Gmail account `suppliermanagement.mail@gmail.com` (two-step verification on).
- Windows 10 or 11 with the built-in **OpenSSH Client** (Settings > System > Optional features) and PowerShell.
- Access to the AWS console for the EC2 server (for the host key and one line in `authorized_keys`).
- The backend version that contains this feature, deployed (a separate, approved deployment).

## 1. Google Cloud (once)

Signed in **only** as `suppliermanagement.mail@gmail.com`, at <https://console.cloud.google.com>:

1. Create a project, for example `stock-list-manager-mail`.
2. **APIs & Services > Library:** enable **Gmail API**.
3. **Google Auth Platform > Branding / Audience** (OAuth consent screen): user type **External**, app name
   `Stock List Manager`, support and developer email `suppliermanagement.mail@gmail.com`.
4. **Data access (scopes):** add `https://www.googleapis.com/auth/gmail.send` (plus `openid` and `email`).
5. **Audience > Publishing status:** **In production**. Do not submit for verification. (In "Testing" the refresh
   token expires after 7 days.)
6. **Clients > Create client:** type **Desktop app**, name `gmail-authorize`. Keep the page open: the helper asks for
   the client ID and client secret. You do not need to download the JSON file.

The client secret of a Desktop client is not confidential (Google treats such apps as public clients); PKCE protects
the sign-in. The refresh token the helper obtains is the real secret.

## 2. SSH access for the token update (once)

### 2.1 Your own SSH key, loaded in the agent

In PowerShell **as administrator, once**, start the OpenSSH agent automatically:

```powershell
Get-Service ssh-agent | Set-Service -StartupType Automatic
Start-Service ssh-agent
```

Then, as yourself:

```powershell
ssh-keygen -t ed25519 -f $HOME\.ssh\stocklist_owner -C owner-gmail-token   # choose a passphrase
ssh-add $HOME\.ssh\stocklist_owner
Get-Content $HOME\.ssh\stocklist_owner.pub        # the public key, for step 2.3
```

The helper always connects with `BatchMode=yes`, so it never asks for a passphrase: the key must be loaded with
`ssh-add` before you run it.

### 2.2 The server's host key, checked independently

Never accept a host key the first time you see it. Get it from AWS:

1. **Preferred:** EC2 console > the instance > Actions > Monitor and troubleshoot > **Get system log**. Find the
   lines between `BEGIN SSH HOST KEY FINGERPRINTS` and `END SSH HOST KEY FINGERPRINTS` and copy the **ED25519**
   value `SHA256:...`.
2. **If the log no longer shows it:** open a browser session from the AWS console (EC2 Instance Connect or Session
   Manager) and run:

   ```bash
   ssh-keygen -l -f /etc/ssh/ssh_host_ed25519_key.pub
   cat /etc/ssh/ssh_host_ed25519_key.pub
   ```

Write down the fingerprint. Then create the helper's own known-hosts file with one line: the server address you will
use, a space, and the key type and key from `ssh_host_ed25519_key.pub` (without the trailing comment):

```powershell
Set-Content $HOME\.ssh\stocklist_known_hosts '3.104.239.4 ssh-ed25519 AAAAC3Nza...'
ssh-keygen -l -f $HOME\.ssh\stocklist_known_hosts    # must show the same SHA256 value you wrote down
```

A fingerprint stored earlier counts only if it was itself checked against one of these two sources. If the server is
replaced or its host keys change, the helper stops by design: repeat this step, never skip it.

### 2.3 A restricted key for the deploy user

In the AWS console session, add **one line** to `/home/deploy/.ssh/authorized_keys` (keep the existing GitHub
Actions line as it is). Replace the key with yours from step 2.1:

```text
command="/opt/stocklist/set-gmail-token.sh",no-pty,no-port-forwarding,no-agent-forwarding,no-X11-forwarding ssh-ed25519 AAAA...your-key... owner-gmail-token
```

```bash
sudo -u deploy nano /home/deploy/.ssh/authorized_keys     # add the line, save
sudo -u deploy chmod 600 /home/deploy/.ssh/authorized_keys
```

With this line your key can run **only** the token script, and only its two operations `check` and `update`. It
cannot open a shell, use Docker or copy files.

## 3. Authorize (once, and again only if needed)

On Windows, in PowerShell, from the project folder:

```powershell
.\tools\windows\gmail-authorize.ps1 -SshTarget deploy@3.104.239.4 `
    -SshKey $HOME\.ssh\stocklist_owner -ExpectedHostKey SHA256:the-value-from-step-2.2
```

1. **SSH check:** the key must be in the agent, the host key must match, and the server must answer `READY`.
   Otherwise the helper stops before anything is asked from Google.
2. It asks for the client ID and the client secret (the secret is not shown while typing), then opens the browser.
   Sign in as `suppliermanagement.mail@gmail.com`, accept the "unverified app" warning (Advanced > Go to ...), and
   allow **Send email on your behalf**.
3. It checks the answer: the Gmail send permission was granted and nothing unrequested; the ID token's signature
   (Google's published keys), issuer, audience, expiry, nonce, the exact account and that its email is verified.
4. It sends the three values to the server over SSH. The server answers
   `UPDATED GMAIL_OAUTH_CLIENT_ID GMAIL_OAUTH_CLIENT_SECRET GMAIL_OAUTH_REFRESH_TOKEN`.

If anything fails after Google issued the token, the helper revokes it. There is no other way to send the token: if
SSH does not work, fix SSH access first. Do not run it while the "Deploy backend" workflow is running (that workflow
also edits `.env`), and do not run it inside `Start-Transcript`.

## 4. Switch the server to the Gmail API (once)

In the AWS console session, as the deploy user:

```bash
cd /opt/stocklist
nano .env    # set the lines below, save
```

```text
MAIL_PROVIDER=gmail-api
GMAIL_SENDER=suppliermanagement.mail@gmail.com
MAIL_FROM=Stock List Manager <suppliermanagement.mail@gmail.com>
SUPPLIER_MAIL_FROM=Supplier Management <suppliermanagement.mail@gmail.com>
```

`MAIL_FROM` and `SUPPLIER_MAIL_FROM` must use `GMAIL_SENDER`, or an alias listed in `GMAIL_SEND_AS_ALIASES` that is
already set up in Gmail (Settings > Accounts > "Send mail as"). Otherwise the backend refuses to start and says which
setting is wrong. The backend cannot read Gmail's alias list (the send permission does not allow it), so list only
aliases that really exist; Gmail would otherwise replace the From address.

Recreate the backend so it reads `.env` (a plain `docker restart` keeps the old values):

```bash
docker compose -f docker-compose.prod.yml up -d backend
```

## 5. Test

```bash
./mail-test.sh your.address@gmail.com
./mail-test.sh your.address@outlook.com
```

Check in both inboxes: the email arrived (also look in spam), the Hindi text is readable, the From name is right.
Then test one real supplier registration with email verification, and one shop owner password reset.

## When it stops working

If Google withdraws the authorization (you removed the app's access, the Gmail password changed, it was unused for
six months, or Google restricted the app), the backend log shows once an hour:

```text
Gmail API authorization lost (invalid_grant). Emails are not being sent. Re-run tools/windows/gmail-authorize.ps1, then recreate the backend container (docs/EMAIL_SETUP.md).
```

Fix: run step 3 again, then recreate the backend (step 4, last command) and test (step 5). Network errors and
temporary Google errors do not need this: they fail only that email, and the next email tries again.

To go back to SMTP: set `MAIL_PROVIDER=smtp` (with the `SMTP_*` settings) and recreate the backend.

## Security notes

- The refresh token exists only in `/opt/stocklist/.env` (mode 600, owner `deploy`) and in the backend's memory. It is
  never logged, never in GitHub, never in an APK.
- The backend logs only the error type when sending fails, never tokens, links or email text.
- To withdraw access completely: myaccount.google.com > Security > Third-party connections > remove the app, and
  delete the three `GMAIL_OAUTH_*` lines from `.env`.
- To remove the owner's SSH access: delete the `owner-gmail-token` line from `/home/deploy/.ssh/authorized_keys`.

## Later: own domain and a verified email provider

For long-term public use, buy a domain, publish a homepage and privacy policy on it, and use a verified transactional
email provider (for example Amazon SES):

1. Add the provider's **DKIM**, **SPF** and **DMARC** DNS records for the domain; for SES, also request production
   access (leave the sandbox).
2. Set `MAIL_PROVIDER=smtp`, the provider's `SMTP_*` settings, and `MAIL_FROM` / `SUPPLIER_MAIL_FROM` on the new
   domain. A `@gmail.com` From address cannot be used through another provider: it fails DMARC.
3. Recreate the backend and test.
4. Then revoke the Gmail app's access and delete the OAuth client in Google Cloud.
