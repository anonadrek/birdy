#!/usr/bin/env bash
# The publish loop (plan Task 16, spec 2026-09-25 §14): runs scripts/publish-next.mjs until nothing is ready,
# MAX_PUBLISH pages are out, or three DIFFERENT records in a row have failed (a failed record is excluded for
# the rest of the session after its first failure, so three in a row means something systematic: read
# reports/publish-loop-<date>.md before starting again). Run from a main checkout:
#   bash scripts/publish-loop.sh 5            five pages, then stop (a small first round)
#   bash scripts/publish-loop.sh              the whole queue
#   bash scripts/publish-loop.sh 1 --dry-run  extra arguments go to publish-next.mjs
# SLEEP_SECONDS (default 300) is the pause between two pushes, so Vercel builds one page at a time.
set -u
cd "$(dirname "$0")/.." || exit 1
MAX_PUBLISH=${1:-9999}
shift || true
SLEEP_SECONDS=${SLEEP_SECONDS:-300}
mkdir -p reports
: > reports/publish-loop-excluded.txt  # tom sessionsfil vid varje ny körning
published=0
consecutive_failures=0
while [ "$published" -lt "$MAX_PUBLISH" ] && [ "$consecutive_failures" -lt 3 ]; do
  node scripts/publish-next.mjs "$@"
  code=$?
  if [ "$code" -eq 0 ]; then
    consecutive_failures=0
    published=$((published + 1))
    [ "$published" -lt "$MAX_PUBLISH" ] && sleep "$SLEEP_SECONDS"
  elif [ "$code" -eq 3 ]; then
    echo "Inget mer att publicera just nu."
    break
  else
    consecutive_failures=$((consecutive_failures + 1))
  fi
done
echo "$published publicerade, $consecutive_failures fel i rad vid stopp."
echo "Stickprovet (plan Task 16 Step 4): cd ../tools/content-pipeline && uv run birdy-fetcher web spot-check"
