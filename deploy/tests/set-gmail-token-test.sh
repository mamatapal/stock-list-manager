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

# --- static rules for the email scripts ----------------------------------------------------------------------------
for f in deploy/set-gmail-token.sh deploy/mail-test.sh tools/windows/gmail-authorize.ps1 tools/windows/GmailAuthorize.psm1; do
  if [ ! -f "$ROOT/$f" ]; then bad "$f exists"; continue; fi
  if grep -qiw "sudo" "$ROOT/$f"; then bad "$f does not use sudo"; else ok "$f does not use sudo"; fi
done
for f in tools/windows/gmail-authorize.ps1 tools/windows/GmailAuthorize.psm1; do
  if grep -qiE 'clipboard|clip\.exe|\|\s*clip\b' "$ROOT/$f"; then bad "$f has no clipboard path"; else ok "$f has no clipboard path"; fi
done

echo
echo "$PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
