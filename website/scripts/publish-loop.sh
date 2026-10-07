#!/usr/bin/env bash
# The publish loop (plan Task 16, spec 2026-09-25 §14): runs scripts/publish-next.mjs until nothing is ready,
# MAX_PUBLISH pages are out, publish-next asks for a stop (exit 4), reports/STOP exists, or three DIFFERENT
# records in a row have failed (a failed record is excluded for the rest of the session after its first
# failure, so three in a row means something systematic: read reports/publish-loop-<date>.md before starting
# again). Run from the loop's own worktree (plan Task 16, "Uppsättning"), never the main clone:
#   bash scripts/publish-loop.sh 5            five pages, then stop (a small first round)
#   bash scripts/publish-loop.sh              the whole queue
#   bash scripts/publish-loop.sh 3 --dry-run  arguments after the number go to publish-next.mjs
#   touch reports/STOP                        stops the loop before its next record (delete it to start again)
# SLEEP_SECONDS (default 300) is the pause after a page is live, so Vercel builds one page at a time.
#
# Everything runs inside main(), and `main "$@"; exit` is the last line: bash then has the whole script read
# before it starts, so a fast-forward to origin/main that rewrites this file mid-run (publish-next does one
# per record) can't change what the running loop does.
set -u
main() {
  cd "$(dirname "$0")/.." || exit 1
  MAX_PUBLISH=9999
  if [ $# -gt 0 ] && [ "${1#-}" = "$1" ]; then
    case "$1" in
      ''|*[!0-9]*|0*) echo "MAX_PUBLISH ska vara ett positivt heltal, inte: $1" >&2; exit 2 ;;
    esac
    MAX_PUBLISH=$1
    shift
  fi
  SLEEP_SECONDS=${SLEEP_SECONDS:-300}
  STOP=reports/STOP
  mkdir -p reports
  if [ -e "$STOP" ]; then
    echo "$STOP finns: ta bort den för att starta loopen." >&2
    exit 2
  fi
  : > reports/publish-loop-excluded.txt  # tom sessionsfil vid varje ny körning
  published=0
  consecutive_failures=0
  stopped=0
  while [ "$published" -lt "$MAX_PUBLISH" ] && [ "$consecutive_failures" -lt 3 ]; do
    if [ -e "$STOP" ]; then
      echo "Stoppad med $STOP."
      break
    fi
    node scripts/publish-next.mjs "$@"
    code=$?
    if [ "$code" -eq 0 ]; then
      consecutive_failures=0
      published=$((published + 1))
      if [ "$published" -lt "$MAX_PUBLISH" ]; then
        waited=0
        while [ "$waited" -lt "$SLEEP_SECONDS" ] && [ ! -e "$STOP" ]; do
          sleep 5
          waited=$((waited + 5))
        done
      fi
    elif [ "$code" -eq 3 ]; then
      echo "Inget mer att publicera just nu."
      break
    elif [ "$code" -eq 1 ]; then
      consecutive_failures=$((consecutive_failures + 1))
    else
      # 4 is publish-next asking for a stop; any other code is a crash, which must not count as one record's
      # failure (the record may well be committed or pushed already).
      echo "publish-next avslutades med kod $code: stopp, se reports/publish-loop-*.md." >&2
      stopped=1
      break
    fi
  done
  echo "$published publicerade, $consecutive_failures fel i rad vid stopp."
  echo "Stickprovet (plan Task 16 Step 4): cd ../tools/content-pipeline && uv run birdy-fetcher web spot-check"
  if [ "$stopped" -eq 1 ] || [ "$consecutive_failures" -ge 3 ]; then
    return 1
  fi
}
main "$@"; exit $?
