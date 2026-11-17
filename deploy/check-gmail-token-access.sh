#!/bin/bash
# Post-deployment check of the owner's restricted SSH access for the Gmail token update (docs/EMAIL_SETUP.md).
# The deploy workflow runs it on the server, as the deploy user, after every deployment. It transfers no credentials,
# reads no .env values and changes nothing.
#
# It checks that:
#   1. set-gmail-token.sh is a regular file owned by this user with mode 755 (not writable by group or others), in a
#      folder that only this user can write;
#   2. the forced command works exactly as sshd starts it (through the login shell, needs the executable bit): it
#      answers READY to "check" and refuses any other command;
#   3. if the owner's restricted key is installed in authorized_keys: its line runs exactly this script with the
#      no-pty, no-port-forwarding, no-agent-forwarding and no-X11-forwarding options (or "restrict"), the same key has
#      no second, unrestricted line, and authorized_keys is not writable by group or others.
# If the owner's key is not installed yet, part 3 is reported as skipped. The owner can also test the real key from
# Windows: gmail-authorize.ps1 -CheckOnly.
#
# Usage: check-gmail-token-access.sh [AUTHORIZED_KEYS_FILE]   (default: ~/.ssh/authorized_keys)
# Exit code 0: all good (or key not installed yet); 1: a problem was found.
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$DIR/set-gmail-token.sh"
KEYS="${1:-$HOME/.ssh/authorized_keys}"
ME="$(id -un)"
problems=0

problem() {
  echo "PROBLEM: $1"
  problems=$((problems + 1))
}

writable_by_others() { [ -n "$(find "$1" -maxdepth 0 -perm /022 2>/dev/null)" ]; }

# 1. File, owner and mode.
if [ ! -f "$SCRIPT" ] || [ -L "$SCRIPT" ]; then
  problem "$SCRIPT is missing"
else
  [ "$(stat -c %U "$SCRIPT")" = "$ME" ] || problem "$SCRIPT does not belong to $ME"
  mode="$(stat -c %a "$SCRIPT")"
  [ "$mode" = "755" ] || problem "$SCRIPT must have mode 755 (it has $mode)"
fi
[ "$(stat -c %U "$DIR")" = "$ME" ] || problem "$DIR does not belong to $ME"
if writable_by_others "$DIR"; then problem "$DIR must not be writable by group or others"; fi

# 2. The forced command, started the way sshd starts it.
if [ -f "$SCRIPT" ]; then
  out="$(cd / && env SSH_ORIGINAL_COMMAND=check bash -c "$SCRIPT" </dev/null 2>&1)"
  rc=$?
  if [ "$rc" -ne 0 ] || [ "$out" != "READY" ]; then
    problem "the forced command did not answer READY to check (exit code $rc)"
  fi
  (cd / && env SSH_ORIGINAL_COMMAND="sh -i" bash -c "$SCRIPT" </dev/null >/dev/null 2>&1)
  rc=$?
  [ "$rc" -eq 2 ] || problem "the forced command did not refuse another command (exit code $rc)"
fi

# 3. The owner's restricted key, if installed.
if [ ! -f "$KEYS" ] || ! grep -qF "command=\"$SCRIPT\"" "$KEYS"; then
  echo "SKIPPED: no restricted key for $SCRIPT in $KEYS yet (install it as described in docs/EMAIL_SETUP.md)."
else
  if writable_by_others "$KEYS"; then problem "$KEYS must not be writable by group or others"; fi
  while IFS= read -r line; do
    if [[ ! "$line" =~ ^(.*[^[:space:]])[[:space:]]+((ssh|ecdsa|sk)-[A-Za-z0-9@.-]+)[[:space:]]+([A-Za-z0-9+/=]+) ]]; then
      problem "a restricted key line in $KEYS cannot be read"
      continue
    fi
    options="${BASH_REMATCH[1]}"
    key="${BASH_REMATCH[4]}"
    if [[ ",$options," != *",restrict,"* ]]; then
      for opt in no-pty no-port-forwarding no-agent-forwarding no-X11-forwarding; do
        [[ ",$options," == *",$opt,"* ]] || problem "the restricted key line is missing the option $opt"
      done
    fi
    count="$(grep -cF "$key" "$KEYS")"
    [ "$count" -eq 1 ] || problem "the owner's key appears $count times in $KEYS; it must only appear once, restricted"
  done < <(grep -F "command=\"$SCRIPT\"" "$KEYS")
  [ "$problems" -gt 0 ] || echo "OK: the owner's restricted key runs only $SCRIPT."
fi

if [ "$problems" -gt 0 ]; then
  echo "Gmail token access check: $problems problem(s)."
  exit 1
fi
echo "OK: the Gmail token update script is in place (owner $ME, mode 755) and answers READY."
exit 0
