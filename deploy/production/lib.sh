#!/usr/bin/env bash
# Shared by the production scripts: project root, settings, the compose command and the S3 copy.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/deploy/production/.env}"
[ -f "$ENV_FILE" ] || { echo "$ENV_FILE is missing: run deploy/production/setup-prod.sh"; exit 1; }
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
compose() { docker compose -f "$ROOT/deploy/production/docker-compose.yml" --env-file "$ENV_FILE" "$@"; }
log() { echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) $*"; }

# aws s3 <args> against the off-site bucket (any S3-compatible storage: AWS S3, Oracle Object Storage, Backblaze B2,
# MinIO). Runs the official AWS CLI image, so nothing has to be installed on the server.
s3() {
  docker run --rm --network host -v "$BACKUP_DIR:/backups" \
    -e AWS_ACCESS_KEY_ID="$BACKUP_S3_ACCESS_KEY" -e AWS_SECRET_ACCESS_KEY="$BACKUP_S3_SECRET_KEY" \
    -e AWS_DEFAULT_REGION="${BACKUP_S3_REGION:-us-east-1}" \
    amazon/aws-cli:2.31.3 ${BACKUP_S3_ENDPOINT:+--endpoint-url "$BACKUP_S3_ENDPOINT"} s3 "$@"
}
offsite() { [ -n "${BACKUP_S3_BUCKET:-}" ]; }
s3_url() { echo "s3://$BACKUP_S3_BUCKET/${BACKUP_S3_PREFIX:-rbctcsworld}/$1"; }

# tables that only ever grow (nothing deletes their rows): the restore drill checks a restored backup has at least
# as many rows as the manifest recorded just before the dump
KEY_TABLES="users orders order_items payment_transactions audit_log flyway_schema_history"
