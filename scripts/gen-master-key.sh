#!/usr/bin/env bash
# Generate a DocShelf master key (32 random bytes, base64). With --write, put it into .env (refuses to overwrite a non-example key).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/.env"
EXAMPLE_KEY="$(grep -E '^DOCSHELF_MASTER_KEY=' "$ROOT/.env.example" | cut -d= -f2- || true)"

KEY="$(openssl rand -base64 32 | tr -d '\n')"

if [[ "${1:-}" != "--write" ]]; then
  # Print only. Paste into .env as DOCSHELF_MASTER_KEY=<value>.
  echo "$KEY"
  exit 0
fi

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ROOT/.env.example" "$ENV_FILE"
  echo "Created .env from .env.example"
fi

CURRENT="$(grep -E '^DOCSHELF_MASTER_KEY=' "$ENV_FILE" | cut -d= -f2- || true)"
if [[ -n "$CURRENT" && "$CURRENT" != "$EXAMPLE_KEY" && "$CURRENT" != "ZGV2LW9ubHktaW5zZWN1cmUta2V5LTAwMDAwMDAwMDA=" ]]; then
  echo "Refusing to replace the existing DOCSHELF_MASTER_KEY in .env." >&2
  echo "Rotating the key makes every stored blob and encrypted field unreadable." >&2
  echo "If you really want a new key, remove the line from .env by hand and re-run." >&2
  exit 1
fi

TMP="$(mktemp)"
if grep -qE '^DOCSHELF_MASTER_KEY=' "$ENV_FILE"; then
  awk -v k="$KEY" 'BEGIN{done=0} /^DOCSHELF_MASTER_KEY=/ && !done {print "DOCSHELF_MASTER_KEY=" k; done=1; next} {print}' "$ENV_FILE" > "$TMP"
else
  cat "$ENV_FILE" > "$TMP"
  printf '\nDOCSHELF_MASTER_KEY=%s\n' "$KEY" >> "$TMP"
fi
mv "$TMP" "$ENV_FILE"
chmod 600 "$ENV_FILE"

echo "Wrote a new DOCSHELF_MASTER_KEY into .env (value not shown)."
echo "Back up .env somewhere safe (password manager). Without it, backups cannot be decrypted."
