#!/bin/bash
# Manage Supplier Administration portal administrators. Run on the server, in /opt/stocklist, as the deploy user:
#
#   ./create-admin.sh create USERNAME "Full Name" [EMAIL]   new administrator
#   ./create-admin.sh reset USERNAME                        new password and new authenticator key (lost phone)
#   ./create-admin.sh deactivate USERNAME                   block the administrator and sign them out
#
# The password is typed at the prompt (twice, not shown). For create and reset, the authenticator key is printed once:
# add it to an authenticator app (Google Authenticator, Microsoft Authenticator) on the administrator's phone.
# There is no way to create administrators over the internet.
set -euo pipefail
cd "$(dirname "$0")"

usage() { sed -n '2,10p' "$0"; exit 1; }
[ $# -ge 2 ] || usage
command="$1"
username="$2"
args=(--admin-cli="$command" --admin-username="$username")
case "$command" in
  create)
    [ $# -ge 3 ] || usage
    args+=(--admin-name="$3")
    [ $# -ge 4 ] && args+=(--admin-email="$4")
    ;;
  reset|deactivate) ;;
  *) usage ;;
esac

# A one-off copy of the backend container on the internal network (no port is opened). It starts, does the job and
# stops; the running server is not touched.
docker compose -f docker-compose.prod.yml run --rm --no-deps -it backend \
  --server.port=0 --logging.level.root=WARN --spring.main.banner-mode=off "${args[@]}"
