#!/usr/bin/env bash
# Sends one alert line to the owner's phone or chat and to the log.
#   ALERT_WEBHOOK_URL: an ntfy.sh topic (https://ntfy.sh/<secret-topic>, free phone app), a Slack or a Discord webhook
#   deploy/production/alert.sh "Backup failed: disk full"
source "$(dirname "$0")/lib.sh"
msg="[${STORE_DOMAIN:-store}] $*"
log "ALERT $msg" >> "${ALERT_LOG:-$BACKUP_DIR/alerts.log}"
[ -n "${ALERT_WEBHOOK_URL:-}" ] || { echo "$msg (no ALERT_WEBHOOK_URL: logged only)"; exit 0; }
if [[ "$ALERT_WEBHOOK_URL" == *ntfy* ]]; then
  curl -fsS -m 15 -H "Title: RbcTcsWorld Shop" -d "$msg" "$ALERT_WEBHOOK_URL" >/dev/null
else
  # Slack reads "text", Discord reads "content"; each ignores the other
  body=$(jq -cn --arg m "$msg" '{text: $m, content: $m}')
  curl -fsS -m 15 -H 'Content-Type: application/json' -d "$body" "$ALERT_WEBHOOK_URL" >/dev/null
fi
echo "alert sent: $msg"
