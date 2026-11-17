#!/bin/bash
# Send one test email (Hindi + English) through the configured provider (MAIL_PROVIDER: smtp or gmail-api).
# Run on the server, in /opt/stocklist, as the deploy user:
#
#   ./mail-test.sh you@example.com
#
# A second test email is sent when SUPPLIER_MAIL_FROM is a different address from MAIL_FROM. Prints only whether
# the provider accepted each email, or the type of error; tokens and passwords are never printed. Check the inbox and
# the spam folder of the test address afterwards. See docs/EMAIL_SETUP.md.
set -euo pipefail
cd "$(dirname "$0")"

[ $# -eq 1 ] || { sed -n '2,10p' "$0"; exit 1; }

# A one-off copy of the backend container on the internal network (no port is opened). It starts, sends and stops;
# the running server is not touched. It reads the same .env as the running backend.
docker compose -f docker-compose.prod.yml run --rm --no-deps -T backend \
  --server.port=0 --logging.level.root=WARN --spring.main.banner-mode=off --mail-test="$1"
