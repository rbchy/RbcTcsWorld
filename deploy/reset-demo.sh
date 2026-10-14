#!/usr/bin/env bash
# Nightly reset of the public demo (cron, see setup-vm.sh): visitors' accounts, orders and edits disappear,
# the database is rebuilt by Flyway from the migrations (seeded products, coupons, pictures) and the admin
# is created again from deploy/.env. Certificates and the code stay.
set -euo pipefail
cd "$(dirname "$0")/.."
compose() { docker compose -f deploy/docker-compose.yml --env-file deploy/.env "$@"; }

echo "$(date -Is) reset start"
compose stop backend
compose exec -T postgres psql -v ON_ERROR_STOP=1 -U ecommerce -d ecommerce \
  -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public AUTHORIZATION ecommerce;"
compose exec -T redis redis-cli FLUSHALL >/dev/null
curl -fsS -X DELETE http://127.0.0.1:8025/api/v1/messages >/dev/null || true   # Mailpit inbox
compose up -d --wait backend
compose restart frontend            # nginx looks the backend up again ...
compose up -d --wait frontend caddy  # ... and visitors get pages only once it is healthy again
echo "$(date -Is) reset done"
