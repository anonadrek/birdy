#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEBSITE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

copied=0
skipped=0
missing=0

while IFS=$'\t' read -r src_rel dst_rel src_root; do
  src="$src_root/$src_rel"
  dst="$WEBSITE_DIR/$dst_rel"

  if [ ! -f "$src" ]; then
    echo "  MISS  $src"
    missing=$((missing + 1))
    continue
  fi

  mkdir -p "$(dirname "$dst")"

  if [ -f "$dst" ] && [ "$src" -ot "$dst" ]; then
    skipped=$((skipped + 1))
    continue
  fi

  cp "$src" "$dst"
  echo "  COPY  $src_rel -> $dst_rel"
  copied=$((copied + 1))
done < <(node "$SCRIPT_DIR/resolve-manifest.mjs")

echo ""
echo "Sync complete: $copied copied, $skipped skipped, $missing missing"
[ "$missing" -gt 0 ] && exit 1
exit 0
