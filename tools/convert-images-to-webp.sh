#!/usr/bin/env bash
# Convert all hero/specimen JPGs to WebP at q75. Used once during Plan 6b3 T22 to
# shrink the AAB before launch. Updates YAML image_refs.path .jpg→.webp.

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
IMAGES_DIR="$ROOT/shared/content/images"
SPECIES_DIR="$ROOT/shared/content/species"
QUALITY="${QUALITY:-75}"

if ! command -v cwebp >/dev/null 2>&1; then
    echo "ERROR: cwebp not installed. Run: scoop install libwebp" >&2
    exit 1
fi

# Convert in parallel using xargs (8 workers).
echo "Finding JPGs under $IMAGES_DIR..."
mapfile -t JPGS < <(find "$IMAGES_DIR" -type f -name "*.jpg")
echo "Found ${#JPGS[@]} JPGs to convert at q$QUALITY (8 parallel workers)..."

printf '%s\n' "${JPGS[@]}" | xargs -P 8 -I {} bash -c '
    src="$1"
    dst="${src%.jpg}.webp"
    if cwebp -q "$2" -m 6 -quiet "$src" -o "$dst"; then
        rm "$src"
    else
        echo "FAIL: $src" >&2
    fi
' _ {} "$QUALITY"

echo "WebP conversion complete. Updating YAML image_refs..."

# Update YAML files: replace path entries like "Q12345/hero.jpg" → "Q12345/hero.webp"
mapfile -t YAMLS < <(find "$SPECIES_DIR" -type f -name "*.yaml")
echo "Found ${#YAMLS[@]} species YAMLs."

for yaml in "${YAMLS[@]}"; do
    sed -i 's|\(path: *.*Q[0-9]\+/[a-z0-9_-]\+\)\.jpg\b|\1.webp|g' "$yaml"
done

echo "Done. Now run: ./gradlew :shared:content:buildSpeciesDb"
