#!/usr/bin/env bash
# Certbot deploy hook: reload SafeWork's Nginx only after a certificate renews successfully.
set -euo pipefail

SAFEWORK_DIR="${SAFEWORK_DIR:-/opt/safework}"
COMPOSE_FILE="$SAFEWORK_DIR/docker-compose.yml"

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "SafeWork compose file not found: $COMPOSE_FILE" >&2
  exit 1
fi

cd "$SAFEWORK_DIR"
if ! docker compose ps --status running --quiet web | grep -q .; then
  echo "SafeWork web container is not running; cannot reload Nginx" >&2
  exit 1
fi

docker compose exec -T web nginx -s reload
echo "SafeWork Nginx reloaded after certificate renewal"
