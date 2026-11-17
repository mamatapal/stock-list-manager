#!/bin/bash
# Tests for deploy/set-gmail-token.sh and the static rules for the email scripts. Run from anywhere:
#   bash deploy/tests/set-gmail-token-test.sh
# Uses throw-away folders and test values only.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="$ROOT/deploy/set-gmail-token.sh"
PASS=0
FAIL=0

ok() { PASS=$((PASS + 1)); echo "ok   - $1"; }
bad() { FAIL=$((FAIL + 1)); echo "FAIL - $1"; }
expect() { if [ "$1" = "$2" ]; then ok "$3"; else bad "$3 (expected '$2', got '$1')"; fi; }

CID="123456789012-abcdefghijklmnopqrstuvwxyz012345.apps.googleusercontent.com"
SECRET="GOCSPX-TestSecretValue_0123456789"
TOKEN="1//0gTestRefreshTokenValue-abcdefghijklmnopqrstuvwxyz_0123456789"

new_server() {
  local d
  d="$(mktemp -d)"
  cp "$SCRIPT" "$d/set-gmail-token.sh"
  chmod 700 "$d/set-gmail-token.sh"
  printf 'API_DOMAIN=1-2-3-4.sslip.io\nDB_PASSWORD=abc$def\n# comment line\nMAIL_FROM=Stock List Manager <suppliermanagement.mail@gmail.com>\nGMAIL_OAUTH_REFRESH_TOKEN=old-token-value\nMAIL_PROVIDER=smtp\n' >"$d/.env"
  chmod 600 "$d/.env"
  echo "$d"
}

run() { # dir, op-or-empty, stdin, [SSH_ORIGINAL_COMMAND]
  local d="$1" op="$2" input="$3"
  if [ $# -ge 4 ]; then
    printf '%s' "$input" | SSH_ORIGINAL_COMMAND="$4" bash "$d/set-gmail-token.sh" >"$d/out" 2>"$d/err"
  else
    printf '%s' "$input" | bash "$d/set-gmail-token.sh" $op >"$d/out" 2>"$d/err"
  fi
  echo $?
}

others() { grep -v -E '^GMAIL_OAUTH_(CLIENT_ID|CLIENT_SECRET|REFRESH_TOKEN)=' "$1/.env"; }

# --- check ---------------------------------------------------------------------------------------------------------
d="$(new_server)"
before="$(sha256sum "$d/.env")"
expect "$(run "$d" check "")" 0 "check exits 0"
expect "$(cat "$d/out")" "READY" "check prints exactly READY"
expect "$(sha256sum "$d/.env")" "$before" "check changes nothing"
expect "$(run "$d" "" "" check)" 0 "check works as the forced command (SSH_ORIGINAL_COMMAND)"
expect "$(cat "$d/out")" "READY" "forced-command check prints READY"
expect "$(run "$d" check "$CID
$SECRET
$TOKEN
")" 0 "check ignores standard input"
expect "$(sha256sum "$d/.env")" "$before" "check never reads values into .env"

# --- update --------------------------------------------------------------------------------------------------------
d="$(new_server)"
kept="$(others "$d")"
expect "$(run "$d" "" "$CID
$SECRET
$TOKEN
" update)" 0 "update exits 0"
expect "$(cat "$d/out")" "UPDATED GMAIL_OAUTH_CLIENT_ID GMAIL_OAUTH_CLIENT_SECRET GMAIL_OAUTH_REFRESH_TOKEN" "update prints only the key names"
expect "$(others "$d")" "$kept" "every other line of .env is kept byte for byte"
expect "$(grep -c '^GMAIL_OAUTH_REFRESH_TOKEN=' "$d/.env")" 1 "the old refresh token line is replaced, not duplicated"
expect "$(grep '^GMAIL_OAUTH_CLIENT_ID=' "$d/.env")" "GMAIL_OAUTH_CLIENT_ID=$CID" "client id written"
expect "$(grep '^GMAIL_OAUTH_CLIENT_SECRET=' "$d/.env")" "GMAIL_OAUTH_CLIENT_SECRET=$SECRET" "client secret written"
expect "$(grep '^GMAIL_OAUTH_REFRESH_TOKEN=' "$d/.env")" "GMAIL_OAUTH_REFRESH_TOKEN=$TOKEN" "refresh token written"
expect "$(stat -c %a "$d/.env")" 600 ".env keeps mode 600"
if grep -q -e "$SECRET" -e "$TOKEN" -e "$CID" "$d/out" "$d/err"; then bad "values never printed"; else ok "values never printed"; fi
expect "$(find "$d" -name '.env.gmail.*' | wc -l)" 0 "no temporary file left behind"
expect "$(run "$d" "" "$CID
$SECRET
${TOKEN}x
" update)" 0 "a second update works"
expect "$(grep -c '^GMAIL_OAUTH_' "$d/.env")" 3 "still exactly three GMAIL_OAUTH lines"
expect "$(run "$d" update "$CID"$'\r'"
$SECRET"$'\r'"
$TOKEN"$'\r'"
")" 0 "Windows line endings are accepted"
expect "$(grep '^GMAIL_OAUTH_REFRESH_TOKEN=' "$d/.env")" "GMAIL_OAUTH_REFRESH_TOKEN=$TOKEN" "carriage returns are removed"

# --- refused operations --------------------------------------------------------------------------------------------
for cmd in "" "scp -t /opt/stocklist" "internal-sftp" "sftp" "update; cat .env" "check extra" "bash" "UPDATE"; do
  d="$(new_server)"
  before="$(sha256sum "$d/.env")"
  expect "$(run "$d" "" "$CID
$SECRET
$TOKEN
" "$cmd")" 2 "forced command '$cmd' is refused"
  expect "$(sha256sum "$d/.env")" "$before" "refused '$cmd' changes nothing"
done
d="$(new_server)"
expect "$(run "$d" "" "")" 2 "no operation at all is refused"

# --- bad input -----------------------------------------------------------------------------------------------------
bad_inputs=(
  "$CID
$SECRET
"
  "$CID
$SECRET
$TOKEN
extra-line
"
  "$CID
$SECRET
$TOKEN"
  "$CID
$SECRET
token with spaces in it 0123456789
"
  "$CID
$SECRET
1//abc\$def0123456789012345678
"
  "not-a-client-id
$SECRET
$TOKEN
"
  "$CID
short
$TOKEN
"
)
i=0
for input in "${bad_inputs[@]}"; do
  i=$((i + 1))
  d="$(new_server)"
  before="$(sha256sum "$d/.env")"
  expect "$(run "$d" update "$input")" 4 "bad input $i is refused"
  expect "$(sha256sum "$d/.env")" "$before" "bad input $i leaves .env unchanged"
  expect "$(find "$d" -name '.env.gmail.*' | wc -l)" 0 "bad input $i leaves no temporary file"
  if grep -q -e "$SECRET" -e "$TOKEN" "$d/out" "$d/err"; then bad "bad input $i: values never printed"; else ok "bad input $i: values never printed"; fi
done

# --- server not ready ----------------------------------------------------------------------------------------------
d="$(new_server)"
chmod 644 "$d/.env"
expect "$(run "$d" check "")" 3 "check refuses an .env that is not mode 600"
d="$(new_server)"
rm "$d/.env"
expect "$(run "$d" check "")" 3 "check refuses a missing .env"
d="$(new_server)"
mv "$d/.env" "$d/real.env"
ln -s "$d/real.env" "$d/.env"
expect "$(run "$d" check "")" 3 "check refuses a symbolic link as .env"

# --- shared .env lock and concurrent changes -------------------------------------------------------------------
# The deploy workflow edits .env while holding .env.lock; the update must wait for it and keep the deploy's change.
d="$(new_server)"
( flock 9; sed -i 's/^MAIL_PROVIDER=smtp$/MAIL_PROVIDER=smtp # edited by deploy/' "$d/.env"; sleep 2 ) 9>>"$d/.env.lock" &
holder=$!
sleep 0.5
start=$(date +%s)
expect "$(run "$d" update "$CID
$SECRET
$TOKEN
")" 0 "update waits for the deploy's lock, then succeeds"
waited=$(( $(date +%s) - start ))
wait "$holder"
if [ "$waited" -ge 1 ]; then ok "update waited for the lock (${waited}s)"; else bad "update did not wait for the lock"; fi
expect "$(grep -c '^MAIL_PROVIDER=smtp # edited by deploy$' "$d/.env")" 1 "the deploy's change made under the lock is kept"
expect "$(grep '^GMAIL_OAUTH_REFRESH_TOKEN=' "$d/.env")" "GMAIL_OAUTH_REFRESH_TOKEN=$TOKEN" "and the token update is there too"

# A program that ignores the lock changes .env while the update runs: nothing is written, its change is kept.
d="$(new_server)"
shim="$(mktemp -d)"
printf '#!/bin/bash\necho "MANUAL_EDIT=1" >> "$(dirname "$1")/.env"\n' >"$shim/sync"
chmod +x "$shim/sync"
expect "$(PATH="$shim:$PATH" run "$d" update "$CID
$SECRET
$TOKEN
")" 3 "an update notices a change made during it and stops"
expect "$(grep -c '^MANUAL_EDIT=1$' "$d/.env")" 1 "the other program's change is kept"
expect "$(grep '^GMAIL_OAUTH_REFRESH_TOKEN=' "$d/.env")" "GMAIL_OAUTH_REFRESH_TOKEN=old-token-value" "nothing from the stopped update was written"
expect "$(find "$d" -name '.env.gmail.*' | wc -l)" 0 "the stopped update leaves no temporary file"
if grep -q "changed by another program" "$d/err"; then ok "the reason is reported"; else bad "the reason is reported"; fi

# --- deployment: install the new script, then the post-deployment access check ------------------------------------
OWNER_KEY="AAAAC3NzaC1lZDI1NTE5AAAAIOwnerTestKey0123456789abcdefghijklmnopqrs"
GHA_KEY="AAAAC3NzaC1lZDI1NTE5AAAAIGitHubActionsKey0123456789abcdefghijklmn"
deployed_server() { # like a server after "Copy and start": old script in place, new one copied as .new (mode 644)
  local d
  d="$(new_server)"
  chmod 750 "$d"
  echo '#!/bin/bash
echo old version' >"$d/set-gmail-token.sh"
  chmod 755 "$d/set-gmail-token.sh"
  cp "$SCRIPT" "$d/set-gmail-token.sh.new"
  chmod 644 "$d/set-gmail-token.sh.new"
  for f in install-gmail-token-script.sh check-gmail-token-access.sh; do cp "$ROOT/deploy/$f" "$d/$f"; chmod 755 "$d/$f"; done
  printf 'ssh-ed25519 %s github-actions-deploy\ncommand="%s/set-gmail-token.sh",no-pty,no-port-forwarding,no-agent-forwarding,no-X11-forwarding ssh-ed25519 %s owner-gmail-token\n' \
    "$GHA_KEY" "$d" "$OWNER_KEY" >"$d/authorized_keys"
  chmod 600 "$d/authorized_keys"
  echo "$d"
}

d="$(deployed_server)"
bash "$d/install-gmail-token-script.sh" >"$d/out" 2>&1
expect "$?" 0 "the installer puts the new script in place"
expect "$(stat -c %a "$d/set-gmail-token.sh")" 755 "the installed script is executable, mode 755"
expect "$(stat -c %U "$d/set-gmail-token.sh")" "$(id -un)" "the installed script keeps its owner"
expect "$(sha256sum <"$d/set-gmail-token.sh")" "$(sha256sum <"$SCRIPT")" "the installed script is the new version"
expect "$(find "$d" -name 'set-gmail-token.sh.new' -o -name '.set-gmail-token.sh.tmp' | wc -l)" 0 "no leftover copy"
expect "$(cd / && env SSH_ORIGINAL_COMMAND=check bash -c "$d/set-gmail-token.sh" </dev/null)" READY "the forced command answers READY after the deployment"
bash "$d/install-gmail-token-script.sh" >/dev/null 2>&1
expect "$?" 0 "running the installer again with nothing new is fine"

before="$(sha256sum <"$d/.env")"
bash "$d/check-gmail-token-access.sh" "$d/authorized_keys" >"$d/out" 2>&1
expect "$?" 0 "the post-deployment check passes"
if grep -q "OK: the owner's restricted key runs only" "$d/out"; then ok "the check confirms the restricted key"; else bad "the check confirms the restricted key"; fi
if grep -q -e "$OWNER_KEY" -e "$GHA_KEY" "$d/out"; then bad "the check never prints keys"; else ok "the check never prints keys"; fi
expect "$(sha256sum <"$d/.env")" "$before" "the check changes nothing in .env"

check_fails() { # dir, description
  bash "$1/check-gmail-token-access.sh" "$1/authorized_keys" >"$1/out" 2>&1
  expect "$?" 1 "$2"
}
d="$(deployed_server)"; bash "$d/install-gmail-token-script.sh" >/dev/null; chmod 644 "$d/set-gmail-token.sh"
check_fails "$d" "the check fails when the script is not executable"
d="$(deployed_server)"; bash "$d/install-gmail-token-script.sh" >/dev/null; chmod 775 "$d/set-gmail-token.sh"
check_fails "$d" "the check fails when the script is writable by the group"
d="$(deployed_server)"; bash "$d/install-gmail-token-script.sh" >/dev/null; chmod 770 "$d"
check_fails "$d" "the check fails when the folder is writable by the group"
d="$(deployed_server)"; bash "$d/install-gmail-token-script.sh" >/dev/null; sed -i 's/no-pty,//' "$d/authorized_keys"
check_fails "$d" "the check fails when the restricted key line lacks no-pty"
d="$(deployed_server)"; bash "$d/install-gmail-token-script.sh" >/dev/null; echo "ssh-ed25519 $OWNER_KEY owner-again" >>"$d/authorized_keys"
check_fails "$d" "the check fails when the owner's key also has an unrestricted line"
d="$(deployed_server)"; bash "$d/install-gmail-token-script.sh" >/dev/null; chmod 664 "$d/authorized_keys"
check_fails "$d" "the check fails when authorized_keys is writable by the group"
d="$(deployed_server)"; bash "$d/install-gmail-token-script.sh" >/dev/null; chmod 644 "$d/.env"
check_fails "$d" "the check fails when the forced command cannot answer READY"
d="$(deployed_server)"; bash "$d/install-gmail-token-script.sh" >/dev/null
sed -i 's/,no-pty,no-port-forwarding,no-agent-forwarding,no-X11-forwarding/,restrict/' "$d/authorized_keys"
bash "$d/check-gmail-token-access.sh" "$d/authorized_keys" >"$d/out" 2>&1
expect "$?" 0 "the restrict option is accepted instead of the four no-* options"
d="$(deployed_server)"; bash "$d/install-gmail-token-script.sh" >/dev/null; sed -i '/owner-gmail-token/d' "$d/authorized_keys"
bash "$d/check-gmail-token-access.sh" "$d/authorized_keys" >"$d/out" 2>&1
expect "$?" 0 "without the owner's key yet, the check passes"
if grep -q "^SKIPPED:" "$d/out"; then ok "and reports the key part as skipped"; else bad "and reports the key part as skipped"; fi

# --- static rules for the email scripts ----------------------------------------------------------------------------
for f in deploy/set-gmail-token.sh deploy/mail-test.sh deploy/check-gmail-token-access.sh deploy/install-gmail-token-script.sh tools/windows/gmail-authorize.ps1 tools/windows/GmailAuthorize.psm1; do
  if [ ! -f "$ROOT/$f" ]; then bad "$f exists"; continue; fi
  if grep -qiw "sudo" "$ROOT/$f"; then bad "$f does not use sudo"; else ok "$f does not use sudo"; fi
done
for f in tools/windows/gmail-authorize.ps1 tools/windows/GmailAuthorize.psm1; do
  if grep -qiE 'clipboard|clip\.exe|\|\s*clip\b' "$ROOT/$f"; then bad "$f has no clipboard path"; else ok "$f has no clipboard path"; fi
done

echo
echo "$PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
