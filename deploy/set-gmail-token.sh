#!/bin/bash
# Gmail API token update for the server. This is the ONLY command the owner's restricted SSH key can run: it is the
# forced command of that key in /home/deploy/.ssh/authorized_keys (see docs/EMAIL_SETUP.md). The Windows helper
# tools/windows/gmail-authorize.ps1 calls it; nobody needs to run it by hand.
#
#   check   confirms that "update" can run here (user, .env owner and mode, folder, flock). Reads no values and
#           changes nothing. Prints READY.
#   update  reads exactly three lines on standard input (OAuth client id, client secret, refresh token), writes them
#           to .env atomically (mode 600, every other line kept as it is) and prints only the names of the keys.
#
# The operation comes from SSH_ORIGINAL_COMMAND (forced command) or, for local tests, from the first argument.
# Anything else is refused. No elevated rights are used: .env and this folder belong to the user that runs the
# script (deploy). Values are never printed. This script does not restart the backend.
#
# .env is shared with the deploy workflow and with manual edits. Two protections keep every change:
#   1. Everything that edits .env takes the same lock, /opt/stocklist/.env.lock (flock): this script, the deploy
#      workflow, and manual edits done as "flock /opt/stocklist/.env.lock nano /opt/stocklist/.env".
#   2. This script also notes the exact content of .env before reading it and checks it again just before replacing
#      it. If anything changed it in between (for example an editor that ignores the lock), nothing is written and
#      it stops with exit code 3, so another program's change is never silently lost.
#
# Exit codes: 0 done, 2 operation refused, 3 server not ready, 4 bad input.
set -euo pipefail
umask 077

DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$DIR/.env"
LOCK_FILE="$DIR/.env.lock"
KEYS_RE='^(GMAIL_OAUTH_CLIENT_ID|GMAIL_OAUTH_CLIENT_SECRET|GMAIL_OAUTH_REFRESH_TOKEN)='

fail() {
  echo "ERROR: $2" >&2
  exit "$1"
}

op="${SSH_ORIGINAL_COMMAND-${1:-}}"
case "$op" in
  check | update) ;;
  *) fail 2 "only 'check' or 'update' are allowed" ;;
esac

ready() {
  local me
  me="$(id -un)"
  [ -d "$DIR" ] && [ -w "$DIR" ] || fail 3 "the folder $DIR is not writable by $me"
  [ "$(stat -c %U "$DIR")" = "$me" ] || fail 3 "the folder $DIR does not belong to $me"
  [ -f "$ENV_FILE" ] && [ ! -L "$ENV_FILE" ] || fail 3 "$ENV_FILE is missing"
  [ "$(stat -c %U "$ENV_FILE")" = "$me" ] || fail 3 "$ENV_FILE does not belong to $me"
  [ "$(stat -c %a "$ENV_FILE")" = "600" ] || fail 3 "$ENV_FILE must have mode 600"
  command -v flock >/dev/null 2>&1 || fail 3 "flock is not installed"
}

ready

if [ "$op" = "check" ]; then
  echo "READY"
  exit 0
fi

exec 9>>"$LOCK_FILE"
flock -w 60 9 || fail 3 "$ENV_FILE is locked by another update or a deploy; try again in a minute"

client_id=""
client_secret=""
refresh_token=""
IFS= read -r -t 10 client_id || fail 4 "expected three lines on standard input"
IFS= read -r -t 10 client_secret || fail 4 "expected three lines on standard input"
IFS= read -r -t 10 refresh_token || fail 4 "expected three lines on standard input"
if IFS= read -r -t 2 extra; then fail 4 "expected exactly three lines on standard input"; fi
if [ -n "${extra:-}" ]; then fail 4 "expected exactly three lines on standard input"; fi
client_id="${client_id%$'\r'}"
client_secret="${client_secret%$'\r'}"
refresh_token="${refresh_token%$'\r'}"

# Only characters that are safe in .env and in docker compose (no spaces, quotes, $ or =).
[[ "$client_id" =~ ^[0-9]+-[A-Za-z0-9_]+\.apps\.googleusercontent\.com$ ]] || fail 4 "the client id has an unexpected format"
[[ "$client_secret" =~ ^[A-Za-z0-9_-]{10,200}$ ]] || fail 4 "the client secret has an unexpected format"
[[ "$refresh_token" =~ ^[A-Za-z0-9._/~+-]{20,1024}$ ]] || fail 4 "the refresh token has an unexpected format"

fingerprint() { stat -c '%i %s %Y' "$ENV_FILE" && sha256sum <"$ENV_FILE"; }
before="$(fingerprint)" || fail 3 "could not read $ENV_FILE"

tmp="$(mktemp "$DIR/.env.gmail.XXXXXX")"
trap 'rm -f "$tmp"' EXIT
status=0
grep -v -E "$KEYS_RE" "$ENV_FILE" >"$tmp" || status=$?
[ "$status" -le 1 ] || fail 3 "could not read $ENV_FILE"
printf 'GMAIL_OAUTH_CLIENT_ID=%s\nGMAIL_OAUTH_CLIENT_SECRET=%s\nGMAIL_OAUTH_REFRESH_TOKEN=%s\n' \
  "$client_id" "$client_secret" "$refresh_token" >>"$tmp"
chmod 600 "$tmp"
sync "$tmp" 2>/dev/null || true
if [ "$(fingerprint)" != "$before" ]; then
  fail 3 "$ENV_FILE was changed by another program during the update; nothing was written. Run the update again."
fi
mv -f "$tmp" "$ENV_FILE"
trap - EXIT
unset client_id client_secret refresh_token

echo "UPDATED GMAIL_OAUTH_CLIENT_ID GMAIL_OAUTH_CLIENT_SECRET GMAIL_OAUTH_REFRESH_TOKEN"
