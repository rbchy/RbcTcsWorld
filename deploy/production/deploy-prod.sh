#!/usr/bin/env bash
# Start or update the live store: a backup FIRST (an update can always be undone with restore.sh), the newest code,
# rebuild, wait until every container is healthy, then check it from the outside like a customer.
source "$(dirname "$0")/lib.sh"
cd "$ROOT"
if compose ps --status running --services 2>/dev/null | grep -qx postgres; then
  echo "== Backup before the update"
  "$ROOT/deploy/production/backup.sh"
fi
echo "== Code"
git pull --ff-only
git log --oneline -1
echo "== Build and start"
compose up -d --build --wait --wait-timeout 900
docker image prune -f >/dev/null
echo "== Check from the outside"
"$ROOT/deploy/smoke.sh" --production "https://$STORE_DOMAIN"
