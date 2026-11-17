#!/bin/bash
# Called by the deploy workflow on the server (as the deploy user) after the files are copied. Puts the new
# set-gmail-token.sh (copied as set-gmail-token.sh.new) in place with one atomic rename, owned by this user, mode 755.
# The owner's restricted SSH key runs /opt/stocklist/set-gmail-token.sh as its forced command, so it must never be
# half-written, missing or non-executable: an update that is running keeps reading the old file, the next one gets
# the new file. Nothing else is changed.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f set-gmail-token.sh.new ]; then
  echo "set-gmail-token.sh: nothing new to install"
  exit 0
fi
sed -i 's/\r$//' set-gmail-token.sh.new
install -m 755 set-gmail-token.sh.new .set-gmail-token.sh.tmp
mv -f .set-gmail-token.sh.tmp set-gmail-token.sh
rm -f set-gmail-token.sh.new
echo "set-gmail-token.sh installed (mode $(stat -c %a set-gmail-token.sh), owner $(stat -c %U set-gmail-token.sh))"
