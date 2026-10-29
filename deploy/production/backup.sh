#!/usr/bin/env bash
# Daily backup of the store's database (cron 02:15, see setup-prod.sh):
#   1. pg_dump in PostgreSQL's custom format (compressed, restorable table by table), checked with pg_restore --list
#   2. encrypted with AES-256 (BACKUP_PASSPHRASE): the file is safe in any cloud bucket
#   3. a manifest: SHA-256, schema version and row counts of the key tables (the restore drill checks them)
#   4. copied off the server (S3-compatible bucket) - a backup on the same disk dies with the disk
#   5. local copies older than BACKUP_KEEP_DAYS (14) deleted; the bucket keeps its own history (lifecycle rule)
# Any failure: an alert, and the last-success mark stays old, so the health check keeps reporting it.
source "$(dirname "$0")/lib.sh"
: "${BACKUP_PASSPHRASE:?set BACKUP_PASSPHRASE in deploy/production/.env (and keep a copy outside the server)}"

mkdir -p "$BACKUP_DIR"
umask 077
name="rbctcsworld-$(date -u +%Y%m%dT%H%M%SZ)"
tmp="$BACKUP_DIR/.$name.dump"
trap 'rc=$?; rm -f "$tmp"; [ $rc -eq 0 ] || "$ROOT/deploy/production/alert.sh" "Backup FAILED (exit $rc) - see the backup log"' EXIT

log "backup $name"
# counts first, dump second: rows are only added between the two, so the dump holds at least these
counts=$(for t in $KEY_TABLES; do
  printf '"%s":%s,' "$t" "$(compose exec -T postgres psql -U ecommerce -d ecommerce -Atc "SELECT count(*) FROM $t")"
done)
version=$(compose exec -T postgres psql -U ecommerce -d ecommerce -Atc \
  "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")
compose exec -T postgres pg_dump -U ecommerce -d ecommerce -Fc --no-owner --no-privileges > "$tmp"
compose exec -T postgres pg_restore --list < "$tmp" > /dev/null          # a dump that cannot be read is no backup
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass env:BACKUP_PASSPHRASE -in "$tmp" -out "$BACKUP_DIR/$name.dump.enc"
rm -f "$tmp"
sha=$(sha256sum "$BACKUP_DIR/$name.dump.enc" | cut -d' ' -f1)
cat > "$BACKUP_DIR/$name.json" <<JSON
{"file":"$name.dump.enc","sha256":"$sha","created":"$(date -u +%Y-%m-%dT%H:%M:%SZ)","schemaVersion":"$version","rows":{${counts%,}}}
JSON
cp "$BACKUP_DIR/$name.json" "$BACKUP_DIR/latest.json"
log "written $name.dump.enc ($(du -h "$BACKUP_DIR/$name.dump.enc" | cut -f1)), schema $version"

if offsite; then
  s3 cp "/backups/$name.dump.enc" "$(s3_url "$name.dump.enc")" --only-show-errors
  s3 cp "/backups/$name.json" "$(s3_url "$name.json")" --only-show-errors
  s3 cp "/backups/latest.json" "$(s3_url latest.json)" --only-show-errors
  log "copied off the server to $(s3_url "")"
else
  log "WARNING: BACKUP_S3_BUCKET is not set - the backup is only on this server"
fi

find "$BACKUP_DIR" -maxdepth 1 -name 'rbctcsworld-*' -mtime +"${BACKUP_KEEP_DAYS:-14}" -delete
touch "$BACKUP_DIR/last-success"
log "backup done"
