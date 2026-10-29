#!/usr/bin/env bash
# Every 5 minutes (cron, see setup-prod.sh): is the store up and is it safe? Alerts only when the answer changes
# (a new problem, or everything fine again), so a long outage is one message, not 288.
#   storefront and API answer over HTTPS; every container healthy; disk below 85 %;
#   last successful backup younger than 26 hours; TLS certificate valid for more than 14 days.
# CERT_MIN_DAYS (14) and DISK_ALERT_PERCENT (85) can be changed in deploy/production/.env.
# A server that is completely off cannot report itself: add an outside uptime check too (docs/deploy/PRODUCTION_BN.md).
source "$(dirname "$0")/lib.sh"
CURL="curl -sS -o /dev/null -m 15 -w %{http_code} ${CURL_OPTS:-}"
BASE="https://$STORE_DOMAIN"
problems=()

[ "$($CURL "$BASE/healthz")" = 200 ] || problems+=("storefront does not answer")
[ "$($CURL "$BASE/api/store/legal")" = 200 ] || problems+=("API does not answer")
while read -r service state health; do
  [ -z "$service" ] && continue
  if [ "$state" != running ] || { [ -n "$health" ] && [ "$health" != healthy ]; }; then
    problems+=("container $service is ${health:-$state}")
  fi
done < <(compose ps -a --format '{{.Service}} {{.State}} {{.Health}}')
used=$(df --output=pcent / | tail -1 | tr -dc '0-9')
[ "$used" -lt "${DISK_ALERT_PERCENT:-85}" ] || problems+=("disk ${used}% full")
mark="$BACKUP_DIR/last-success"
if [ ! -f "$mark" ] || [ $(( $(date +%s) - $(stat -c %Y "$mark") )) -gt $(( ${BACKUP_MAX_AGE_HOURS:-26} * 3600 )) ]; then
  problems+=("no successful backup in ${BACKUP_MAX_AGE_HOURS:-26} hours")
fi
if [ "${SKIP_CERT_CHECK:-false}" != true ]; then
  end=$(echo | openssl s_client -servername "$STORE_DOMAIN" -connect "$STORE_DOMAIN:443" 2>/dev/null \
        | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2)
  if [ -z "$end" ]; then problems+=("TLS certificate cannot be read")
  elif [ $(( ($(date -d "$end" +%s) - $(date +%s)) / 86400 )) -lt "${CERT_MIN_DAYS:-14}" ]; then
    problems+=("TLS certificate expires $end"); fi
fi

state_file="$BACKUP_DIR/health.state"
now=$( [ ${#problems[@]} -eq 0 ] && echo "OK" || printf '%s; ' "${problems[@]}" )
before=$(cat "$state_file" 2>/dev/null || echo "OK")
mkdir -p "$BACKUP_DIR"; echo "$now" > "$state_file"
if [ "$now" != "$before" ]; then
  if [ "$now" = OK ]; then "$ROOT/deploy/production/alert.sh" "Recovered: everything is fine again"
  else "$ROOT/deploy/production/alert.sh" "Problem: ${now%; }"; fi
fi
echo "$(date -u +%H:%M:%SZ) $now"
[ "$now" = OK ]
