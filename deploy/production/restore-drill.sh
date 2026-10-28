#!/usr/bin/env bash
# Proves the newest backup can really be restored (cron Sundays 04:30, see setup-prod.sh), without touching the store:
# the backup is decrypted and restored into a throw-away PostgreSQL container, then checked against its manifest
# (SHA-256, schema version, key tables at least as many rows as when it was taken). A backup nobody has restored is a hope.
#   deploy/production/restore-drill.sh                 newest local backup
#   deploy/production/restore-drill.sh --from-offsite  newest backup in the bucket (as after losing the server)
source "$(dirname "$0")/lib.sh"
trap 'rc=$?; docker rm -f rbctcsworld-drill >/dev/null 2>&1 || true; rm -f "$plain"; \
  [ $rc -eq 0 ] || "$ROOT/deploy/production/alert.sh" "Restore drill FAILED (exit $rc): the newest backup could not be restored"' EXIT
plain=""
: "${BACKUP_PASSPHRASE:?set BACKUP_PASSPHRASE}"
mkdir -p "$BACKUP_DIR"

if [ "${1:-}" = "--from-offsite" ]; then
  offsite || { echo "BACKUP_S3_BUCKET is not set"; exit 1; }
  s3 cp "$(s3_url latest.json)" /backups/drill-latest.json --only-show-errors
  manifest="$BACKUP_DIR/drill-latest.json"
  file=$(jq -r .file "$manifest")
  s3 cp "$(s3_url "$file")" "/backups/$file" --only-show-errors
else
  manifest="$BACKUP_DIR/latest.json"
  [ -f "$manifest" ] || { echo "no backup yet ($manifest)"; exit 1; }
  file=$(jq -r .file "$manifest")
fi
log "restore drill of $file"

test "$(sha256sum "$BACKUP_DIR/$file" | cut -d' ' -f1)" = "$(jq -r .sha256 "$manifest")" \
  || { echo "SHA-256 does not match the manifest: the file is damaged"; exit 1; }
plain="$BACKUP_DIR/.drill.dump"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:BACKUP_PASSPHRASE -in "$BACKUP_DIR/$file" -out "$plain"

docker rm -f rbctcsworld-drill >/dev/null 2>&1 || true
docker run -d --name rbctcsworld-drill -e POSTGRES_PASSWORD=drill -e POSTGRES_DB=drill postgres:16 >/dev/null
for _ in $(seq 1 60); do docker exec rbctcsworld-drill pg_isready -U postgres -d drill >/dev/null 2>&1 && break; sleep 1; done
sleep 2
docker exec -i rbctcsworld-drill pg_restore -U postgres -d drill --no-owner --no-privileges --exit-on-error < "$plain"
q() { docker exec rbctcsworld-drill psql -U postgres -d drill -Atc "$1"; }

fail=0
version=$(q "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")
if [ "$version" = "$(jq -r .schemaVersion "$manifest")" ]; then echo "  ok    schema version $version"
else echo "  FAIL  schema version $version, manifest says $(jq -r .schemaVersion "$manifest")"; fail=1; fi
for t in $KEY_TABLES; do
  expected=$(jq -r ".rows[\"$t\"]" "$manifest")
  got=$(q "SELECT count(*) FROM $t")
  if [ "$got" -ge "$expected" ]; then echo "  ok    $t: $got rows (backup said $expected)"
  else echo "  FAIL  $t: $got rows, backup said $expected"; fail=1; fi
done
[ "$fail" -eq 0 ] || exit 1
touch "$BACKUP_DIR/last-drill-success"
log "restore drill passed"
