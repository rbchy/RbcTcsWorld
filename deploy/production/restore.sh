#!/usr/bin/env bash
# Puts a backup back into the LIVE store. Everything written since that backup is lost, so it asks first.
#   deploy/production/restore.sh rbctcsworld-20261101T021500Z.dump.enc        a file in BACKUP_DIR
#   deploy/production/restore.sh --latest-offsite                             newest backup in the bucket
#   add --yes to skip the question (scripts, CI)
# Steps: checksum against the manifest, decrypt, stop the backend (no new orders), replace the schema, restore,
# start the backend (Flyway brings an older backup up to the current schema), smoke test.
source "$(dirname "$0")/lib.sh"
: "${BACKUP_PASSPHRASE:?set BACKUP_PASSPHRASE}"
plain="$BACKUP_DIR/.restore.dump"
trap 'rm -f "$plain"' EXIT
yes=false
args=()
for a in "$@"; do [ "$a" = "--yes" ] && yes=true || args+=("$a"); done
target="${args[0]:?usage: restore.sh <file.dump.enc> | --latest-offsite [--yes]}"

if [ "$target" = "--latest-offsite" ]; then
  s3 cp "$(s3_url latest.json)" /backups/restore-latest.json --only-show-errors
  target=$(jq -r .file "$BACKUP_DIR/restore-latest.json")
  s3 cp "$(s3_url "$target")" "/backups/$target" --only-show-errors
  s3 cp "$(s3_url "${target%.dump.enc}.json")" "/backups/${target%.dump.enc}.json" --only-show-errors
fi
file="$BACKUP_DIR/$(basename "$target")"
manifest="${file%.dump.enc}.json"
[ -f "$file" ] && [ -f "$manifest" ] || { echo "missing $file or its manifest"; exit 1; }
test "$(sha256sum "$file" | cut -d' ' -f1)" = "$(jq -r .sha256 "$manifest")" || { echo "checksum mismatch: file damaged"; exit 1; }

if [ "$yes" != true ]; then
  echo "This replaces ALL data of the live store with the backup from $(jq -r .created "$manifest")."
  read -r -p "Type RESTORE to continue: " answer
  [ "$answer" = "RESTORE" ] || { echo "cancelled"; exit 1; }
fi

openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:BACKUP_PASSPHRASE -in "$file" -out "$plain"
log "restore $(basename "$file") into the live store"
compose stop backend
compose exec -T postgres psql -v ON_ERROR_STOP=1 -U ecommerce -d ecommerce \
  -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public AUTHORIZATION ecommerce;"
compose exec -T postgres pg_restore -U ecommerce -d ecommerce --no-owner --no-privileges --exit-on-error < "$plain"
compose exec -T redis redis-cli FLUSHALL >/dev/null          # sign-in lockouts belong to the old state
compose up -d --wait backend
compose restart frontend
compose up -d --wait frontend caddy
log "restore done"
"$ROOT/deploy/smoke.sh" --production "https://$STORE_DOMAIN"
