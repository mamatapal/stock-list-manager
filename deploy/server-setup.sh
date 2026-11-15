#!/bin/bash
# One-time setup of a fresh Ubuntu 22.04 / 24.04 server for Stock List Manager.
# Paste this whole file into your provider's "user data" / "post-install script" / "startup script" box when you
# create the server (or run it as root once). It:
#   - installs Docker, a firewall (SSH, HTTP, HTTPS only), fail2ban and automatic security updates,
#   - creates the user "deploy" that only GitHub Actions can log in to (key below; password logins are switched off),
#   - creates /opt/stocklist/.env with random database passwords and token key that never leave this server,
#   - schedules a daily database backup at 02:30.
# Then run the "Deploy backend" workflow on GitHub; it installs and starts the backend.
set -euxo pipefail

# Public half of the GitHub Actions deploy key (the private half is the DEPLOY_SSH_KEY secret on GitHub).
DEPLOY_KEY="ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJ54AvSMNM+D7FTJXF3e4JfTLVpnXFeP4kWpUya6RNll github-actions-deploy@stock-list-manager"
# Optional: your own domain, e.g. API_DOMAIN=api.yourshop.in (its DNS A record must point at this server).
# Left empty, the server uses <ip-with-dashes>.sslip.io, a free name that always points at this IP.
API_DOMAIN="${API_DOMAIN:-}"

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get -y -o Dpkg::Options::=--force-confold upgrade
apt-get install -y ca-certificates curl openssl ufw fail2ban unattended-upgrades
command -v docker >/dev/null || curl -fsSL https://get.docker.com | sh
systemctl enable --now docker

id deploy >/dev/null 2>&1 || useradd -m -s /bin/bash deploy
usermod -aG docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
echo "$DEPLOY_KEY" > /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys

install -d -m 750 -o deploy -g deploy /opt/stocklist
install -d -m 700 -o deploy -g deploy /var/backups/stocklist

if [ ! -f /opt/stocklist/.env ]; then
  IP="$(curl -fsS --max-time 10 https://api.ipify.org || hostname -I | awk '{print $1}')"
  DOMAIN="${API_DOMAIN:-$(echo "$IP" | tr . -).sslip.io}"
  cat > /opt/stocklist/.env <<ENV
API_DOMAIN=$DOMAIN
DB_PASSWORD=$(openssl rand -hex 24)
DB_ROOT_PASSWORD=$(openssl rand -hex 24)
JWT_SIGNING_KEY=$(openssl rand -base64 48 | tr -d '\n')
JWT_KEY_ID=k1
APP_BUSINESS_ZONE=Asia/Kolkata
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
MAIL_FROM=
BACKUP_DIR=/var/backups/stocklist
BACKUP_KEEP_DAYS=30
APP_VERSION=1.0.0
ENV
  chown deploy:deploy /opt/stocklist/.env
  chmod 600 /opt/stocklist/.env
fi

# Firewall: only SSH (for GitHub deploys), HTTP (certificate check) and HTTPS (the app).
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

# Key-only SSH logins: passwords cannot be guessed if they are not accepted. The provider's web console still works.
mkdir -p /etc/ssh/sshd_config.d
printf 'PasswordAuthentication no\nKbdInteractiveAuthentication no\nPermitRootLogin prohibit-password\n' > /etc/ssh/sshd_config.d/60-stocklist.conf
systemctl reload ssh || systemctl reload sshd || true
systemctl enable --now fail2ban

# Daily backup at 02:30 server time (backup.sh arrives with the first deploy).
echo '30 2 * * * cd /opt/stocklist && [ -x ./backup.sh ] && ./backup.sh >> /opt/stocklist/backup.log 2>&1' | crontab -u deploy -

echo "Stock List Manager server ready. App address: https://$(grep ^API_DOMAIN= /opt/stocklist/.env | cut -d= -f2)/" | tee /root/stocklist-setup.txt
