#!/bin/bash
# Builds the server and site and installs them on the Linux host as the "medic" systemd service.
# Usage: deploy/deploy-server.sh [ssh-host]   (default: kali, via ~/.ssh/claude_servers_config)
set -euo pipefail
HOST="${1:-kali}"
SSH=(ssh -F "$HOME/.ssh/claude_servers_config" -o BatchMode=yes "$HOST")
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
node tools/export-content.js
./gradlew :server:installDist -q --console=plain
COPYFILE_DISABLE=1 tar --no-xattrs -czf /tmp/medic-deploy.tgz -C server/build/install server -C ../../.. docs
scp -F "$HOME/.ssh/claude_servers_config" -q /tmp/medic-deploy.tgz "$HOST:/tmp/medic-deploy.tgz"
"${SSH[@]}" 'sudo bash -s' <<'REMOTE'
# The server runs as Pterodactyl server 36 "Medic Course" (egg 19); files live in its volume.
set -euo pipefail
U=06355667-100c-4298-8782-d22eebaf1058
V=/var/lib/pterodactyl/volumes/$U
T=$(grep -E "^token:" /etc/pterodactyl/config.yml | awk '{print $2}' | tr -d '"')
H="Authorization: Bearer $T"
power() { curl -sk -o /dev/null -X POST -H "$H" -H "Content-Type: application/json" -d "{\"action\":\"$1\"}" https://127.0.0.1:8080/api/servers/$U/power; }
mkdir -p /tmp/medic-new && tar xzf /tmp/medic-deploy.tgz -C /tmp/medic-new
power stop; for i in $(seq 1 60); do [ "$(docker inspect -f {{.State.Running}} $U 2>/dev/null)" = "true" ] || break; sleep 1; done
rm -rf $V/app $V/docs && mv /tmp/medic-new/server $V/app && mv /tmp/medic-new/docs $V/docs
rm -rf /tmp/medic-new /tmp/medic-deploy.tgz
chown -R pterodactyl:pterodactyl $V
power start
for i in $(seq 1 40); do curl -fs http://127.0.0.1:8787/health && echo && exit 0; sleep 2; done
docker logs --tail 30 $U; exit 1
REMOTE
