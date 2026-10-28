#!/usr/bin/env bash
# One-time setup of the server for the LIVE store (Ubuntu 24.04; an Oracle Cloud VM like the demo, or any VPS).
# Run on the server as the normal user:  ~/RbcTcsWorld/deploy/production/setup-prod.sh
# Safe to run again. Does not start the store: fill in deploy/production/.env, then run deploy-prod.sh.
set -euo pipefail
REPO_URL="https://github.com/rbchy/RbcTcsWorld.git"
DIR="$HOME/RbcTcsWorld"
[ "$(id -u)" -ne 0 ] || { echo "Run as the normal user (e.g. ubuntu), not root."; exit 1; }

echo "== 1/5 Docker, git, jq, openssl"
command -v docker >/dev/null 2>&1 || curl -fsSL https://get.docker.com | sudo sh
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y git jq openssl iptables-persistent unattended-upgrades >/dev/null
sudo usermod -aG docker "$USER"

echo "== 2/5 Firewall: only 22, 80 and 443; automatic security updates"
for port in 80 443; do
  sudo iptables -C INPUT -p tcp -m state --state NEW --dport "$port" -j ACCEPT 2>/dev/null || {
    reject=$(sudo iptables -L INPUT --line-numbers -n | awk '$2 == "REJECT" {print $1; exit}')
    if [ -n "$reject" ]; then sudo iptables -I INPUT "$reject" -p tcp -m state --state NEW --dport "$port" -j ACCEPT
    else sudo iptables -A INPUT -p tcp -m state --state NEW --dport "$port" -j ACCEPT; fi; }
done
sudo netfilter-persistent save >/dev/null
sudo dpkg-reconfigure -f noninteractive unattended-upgrades >/dev/null

echo "== 3/5 Project"
if [ -d "$DIR/.git" ]; then git -C "$DIR" pull --ff-only; else git clone "$REPO_URL" "$DIR"; fi

echo "== 4/5 deploy/production/.env"
ENV_FILE="$DIR/deploy/production/.env"
if [ -f "$ENV_FILE" ]; then
  echo "kept the existing $ENV_FILE"
else
  umask 077
  sed -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 32)|" \
      -e "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -hex 32)|" \
      -e "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=Owner-$(openssl rand -hex 8)!|" \
      -e "s|^BACKUP_PASSPHRASE=[^ ]*|BACKUP_PASSPHRASE=$(openssl rand -hex 32)|" \
      -e "s|^BACKUP_DIR=.*|BACKUP_DIR=$HOME/backups|" \
      "$DIR/deploy/production/.env.example" > "$ENV_FILE"
  echo "written $ENV_FILE - now set STORE_DOMAIN and ADMIN_EMAIL, and copy BACKUP_PASSPHRASE and ADMIN_PASSWORD"
  echo "into your password manager (without the passphrase no backup can ever be read)"
fi

echo "== 5/5 Scheduled jobs: backup 02:15 daily, health check every 5 minutes, restore drill Sundays 04:30"
chmod +x "$DIR"/deploy/production/*.sh "$DIR"/deploy/*.sh
mkdir -p "$HOME/backups"
( crontab -l 2>/dev/null | grep -v 'deploy/production/' ; cat <<CRON
15 2 * * * $DIR/deploy/production/backup.sh >> $HOME/backups/backup.log 2>&1
*/5 * * * * $DIR/deploy/production/healthcheck.sh >> $HOME/backups/health.log 2>&1
30 4 * * 0 $DIR/deploy/production/restore-drill.sh >> $HOME/backups/drill.log 2>&1
CRON
) | crontab -
echo
echo "Done. Log out and in once (docker group), fill in $ENV_FILE, then:  $DIR/deploy/production/deploy-prod.sh"
