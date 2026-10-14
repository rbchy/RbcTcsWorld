#!/usr/bin/env bash
# Start or update the demo: newest code from GitHub, rebuild what changed, wait until healthy, then check it
# from the outside like a visitor (deploy/smoke.sh). Run on the server:  ~/RbcTcsWorld_ECommerceSuite/deploy/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f deploy/.env ] || { echo "deploy/.env is missing: run deploy/setup-vm.sh or copy deploy/.env.example"; exit 1; }
# shellcheck disable=SC1091
. deploy/.env

echo "== Code"
git pull --ff-only
git log --oneline -1

echo "== Build and start (first time on a small server: 10-15 minutes)"
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build --wait --wait-timeout 600

echo "== Clean up old images"
docker image prune -f >/dev/null

echo "== Check from the outside"
deploy/smoke.sh "https://$DEMO_DOMAIN"
