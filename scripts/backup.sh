#!/usr/bin/env bash
# Back up DocShelf: pg_dump of the postgres service + tarball of the encrypted blobs volume into ./backups/<timestamp>/
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROJECT="${COMPOSE_PROJECT_NAME:-docshelf}"
STAMP="$(date +%Y%m%d-%H%M%S)"
DEST="${1:-$ROOT/backups/$STAMP}"
mkdir -p "$DEST"

# Load DB names from .env if present (defaults match docker-compose.yml).
if [[ -f .env ]]; then set -a; . ./.env; set +a; fi
DB="${POSTGRES_DB:-docshelf}"
DB_USER="${POSTGRES_USER:-docshelf}"

echo "==> Backing up to $DEST"

# 1. Database. Custom-format pg_dump (compressed, restorable with pg_restore).
if [[ -z "$(docker compose ps -q postgres 2>/dev/null)" ]]; then
  echo "postgres service is not running; starting it" >&2
  docker compose up -d postgres
  until docker compose exec -T postgres pg_isready -U "$DB_USER" -d "$DB" >/dev/null 2>&1; do sleep 1; done
fi
echo "==> pg_dump $DB"
docker compose exec -T postgres pg_dump -U "$DB_USER" -d "$DB" --format=custom --no-owner --no-privileges \
  > "$DEST/docshelf.dump"

# 2. Blobs. Read the named volume through a throwaway container so the backend need not be running.
VOLUME="${PROJECT}_blobs"
if docker volume inspect "$VOLUME" >/dev/null 2>&1; then
  echo "==> tar volume $VOLUME"
  docker run --rm \
    -v "$VOLUME:/data/blobs:ro" \
    -v "$DEST:/backup" \
    alpine:3 sh -c 'cd /data/blobs && tar czf /backup/blobs.tar.gz .'
else
  echo "volume $VOLUME not found; skipping blobs (nothing uploaded yet?)" >&2
fi

# 3. Manifest (no secrets).
cat > "$DEST/MANIFEST.txt" <<EOF
DocShelf backup
created:   $(date -u +%Y-%m-%dT%H:%M:%SZ)
database:  $DB (pg_dump custom format, docshelf.dump)
blobs:     docker volume $VOLUME (blobs.tar.gz, AES-256-GCM encrypted files)
restore:   scripts/restore.sh $(basename "$DEST")

The master key (DOCSHELF_MASTER_KEY in .env) is NOT included in this backup on purpose.
EOF

du -sh "$DEST"/* | sed 's/^/   /'
echo
echo "==> Done."
echo "!! This backup is useless without DOCSHELF_MASTER_KEY from your .env."
echo "!! Store that key separately (password manager), not next to the backup."
