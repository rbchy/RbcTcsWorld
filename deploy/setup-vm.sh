#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu server (Oracle Cloud "Always Free" VM, Ubuntu 24.04, x86 or ARM) for the demo.
# Run it ON THE SERVER, as the normal user (ubuntu), not as root:
#
#   curl -fsSL https://raw.githubusercontent.com/rbchy/RbcTcsWorld_ECommerceSuite/main/deploy/setup-vm.sh | bash
#
# What it does (safe to run again):
#   1. installs Docker and git
#   2. opens ports 80 and 443 in the server's own firewall (Oracle's Ubuntu images block them by default)
#   3. downloads the project to ~/RbcTcsWorld_ECommerceSuite
#   4. writes deploy/.env with random secrets and the sslip.io address of this server (only if it is missing)
#   5. installs the nightly reset (03:30 server time)
# Then start the shop with:  ~/RbcTcsWorld_ECommerceSuite/deploy/deploy.sh
set -euo pipefail

REPO_URL="https://github.com/rbchy/RbcTcsWorld_ECommerceSuite.git"
DIR="$HOME/RbcTcsWorld_ECommerceSuite"

if [ "$(id -u)" -eq 0 ]; then
  echo "Run this as the normal user (e.g. ubuntu), not as root. It uses sudo where needed."; exit 1
fi

echo "== 1/5 Docker and git"
if ! command -v docker >/dev/null 2>&1; then
  sudo apt-get update -y
  sudo apt-get install -y ca-certificates curl git
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y git iptables-persistent >/dev/null
sudo usermod -aG docker "$USER"

echo "== 2/5 Firewall: allow 80 and 443"
for port in 80 443; do
  if ! sudo iptables -C INPUT -p tcp -m state --state NEW --dport "$port" -j ACCEPT 2>/dev/null; then
    # Oracle's images end the INPUT chain with a REJECT rule: the new rule must come before it
    reject=$(sudo iptables -L INPUT --line-numbers -n | awk '$2 == "REJECT" {print $1; exit}')
    if [ -n "$reject" ]; then
      sudo iptables -I INPUT "$reject" -p tcp -m state --state NEW --dport "$port" -j ACCEPT
    else
      sudo iptables -A INPUT -p tcp -m state --state NEW --dport "$port" -j ACCEPT
    fi
  fi
done
sudo netfilter-persistent save >/dev/null

echo "== 3/5 Project"
if [ -d "$DIR/.git" ]; then
  git -C "$DIR" pull --ff-only
else
  git clone "$REPO_URL" "$DIR"
fi

echo "== 4/5 deploy/.env"
ENV_FILE="$DIR/deploy/.env"
if [ -f "$ENV_FILE" ]; then
  echo "kept the existing $ENV_FILE"
else
  ip=$(curl -fsS https://api.ipify.org || curl -fsS https://ifconfig.me)
  admin_password="Demo-$(openssl rand -hex 6)!"
  umask 077
  cat > "$ENV_FILE" <<ENV
DEMO_DOMAIN=${ip//./-}.sslip.io
DB_PASSWORD=$(openssl rand -hex 32)
JWT_SECRET=$(openssl rand -hex 32)
ADMIN_EMAIL=admin@rbctcsworld.com
ADMIN_PASSWORD=$admin_password
ENV
  echo "written $ENV_FILE (only you can read it)"
  echo "   demo address : https://${ip//./-}.sslip.io"
  echo "   store admin  : admin@rbctcsworld.com / $admin_password   <- write this down"
fi

echo "== 5/5 Nightly reset at 03:30"
chmod +x "$DIR"/deploy/*.sh
( crontab -l 2>/dev/null | grep -v 'deploy/reset-demo.sh' ; echo "30 3 * * * $DIR/deploy/reset-demo.sh >> $HOME/reset-demo.log 2>&1" ) | crontab -

echo
echo "Done. Log out and in again once (so the docker group applies), then run:"
echo "   $DIR/deploy/deploy.sh"
