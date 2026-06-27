#!/usr/bin/env bash
#
# Bring up the local docker-compose stack reliably.
#
# The compose file pins each named volume to ./volume_data/<name> via a local-driver bind
# (driver_opts: type=none, o=bind, device=...). Two things trip up `docker compose up`:
#   1. The bind target dirs must already exist — the local driver won't create them.
#   2. Docker Desktop (macOS/Windows) caches host-path lookups; a volume object created before
#      its dir is visible fails to "populate" with: mount ... : no such file or directory.
#
# This script ensures the dirs exist, warms Docker Desktop's path cache, then brings the stack
# up. If the populate race still bites, it recreates the (data-less) bind volume objects and
# retries once — safe because the actual data lives in ./volume_data, not in the volume object.
#
# Usage: scripts/dev-up.sh [extra docker compose up args]
#   e.g. scripts/dev-up.sh                 # whole stack
#        scripts/dev-up.sh platform-portal # one service
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE_FILE="docker-compose.yml"
PROJECT="${COMPOSE_PROJECT_NAME:-$(basename "$PWD" | tr '[:upper:]' '[:lower:]')}"

# 1. Ensure every volume_data bind target exists (parsed from the compose file).
#    Portable to macOS' bash 3.2 (no mapfile).
echo "→ Ensuring volume_data directories exist…"
count=0
while IFS= read -r d; do
  [ -n "$d" ] || continue
  mkdir -p "$d"
  count=$((count + 1))
done < <(grep -oE 'volume_data/[a-z_]+' "$COMPOSE_FILE" | sort -u)
printf '  created/verified %d directories\n' "$count"

# 2. Warm Docker Desktop's host-path cache so the named-volume bind resolves.
echo "→ Warming Docker host-path cache…"
docker run --rm -v "$PWD/volume_data:/v" alpine true >/dev/null 2>&1 || true

# 3. Bring the stack up; self-heal the bind-volume populate race on first failure.
up() { docker compose up -d "$@"; }

echo "→ docker compose up -d ${*:-}"
if ! up "$@"; then
  echo "⚠ up failed — recreating bind volume objects and retrying once…"
  docker compose down --remove-orphans || true
  # These volumes are just bind pointers; their data lives in ./volume_data, so removing the
  # volume objects is non-destructive.
  docker volume ls -q | grep "^${PROJECT}_" | xargs -r docker volume rm >/dev/null 2>&1 || true
  up "$@"
fi

echo "✓ Stack is up. Status:"
docker compose ps --format '  {{.Name}}\t{{.Status}}'
