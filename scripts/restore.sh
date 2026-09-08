#!/usr/bin/env bash
# Restore DocShelf from a backup directory made by scripts/backup.sh (replaces the database and the blobs volume).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ $# -lt 1 ]]; then
  echo "usage: scripts/restore.sh <backup-dir>   (e.g. backups/20260908-221500)" >&2
  exit 2
fi
SRC="$1"
[[ -d "$SRC" ]] || SRC="$ROOT/$1"
[[ -f "$SRC/docshelf.dump" ]] || { echo "no docshelf.dump in $SRC" >&2; exit 2; }
SRC="$(cd "$SRC" && pwd)"

PROJECT="${COMPOSE_PROJECT_NAME:-docshelf}"
if [[ -f .env ]]; then set -a; . ./.env; set +a; fi
DB="${POSTGRES_DB:-docshelf}"
DB_USER="${POSTGRES_USER:-docshelf}"
VOLUME="${PROJECT}_blobs"

echo "This will REPLACE database '$DB' and the blobs volume '$VOLUME' with the contents of:"
echo "   $SRC"
echo "Make sure .env contains the SAME DOCSHELF_MASTER_KEY that was in use when the backup was taken."
read -r -p "Type 'restore' to continue: " ANSWER
[[ "$ANSWER" == "restore" ]] || { echo "aborted"; exit 1; }

echo "==> Stopping backend"
docker compose stop backend >/dev/null 2>&1 || true

echo "==> Ensuring postgres is up"
docker compose up -d postgres >/dev/null
until docker compose exec -T postgres pg_isready -U "$DB_USER" -d postgres >/dev/null 2>&1; do sleep 1; done

echo "==> Recreating database $DB"
docker compose exec -T postgres psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 -q \
  -c "DROP DATABASE IF EXISTS \"$DB\" WITH (FORCE);" \
  -c "CREATE DATABASE \"$DB\" OWNER \"$DB_USER\";"

echo "==> pg_restore"
docker compose exec -T postgres pg_restore -U "$DB_USER" -d "$DB" --no-owner --no-privileges --exit-on-error \
  < "$SRC/docshelf.dump"

if [[ -f "$SRC/blobs.tar.gz" ]]; then
  echo "==> Restoring blobs into volume $VOLUME"
  docker volume create "$VOLUME" >/dev/null
  docker run --rm \
    -v "$VOLUME:/data/blobs" \
    -v "$SRC:/backup:ro" \
    alpine:3 sh -c 'cd /data/blobs && find . -mindepth 1 -delete && tar xzf /backup/blobs.tar.gz'
else
  echo "no blobs.tar.gz in backup; blobs volume left untouched" >&2
fi

echo "==> Starting backend"
docker compose up -d backend >/dev/null
echo "==> Done. Check: docker compose logs -f backend"
