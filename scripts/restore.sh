#!/usr/bin/env bash
# Undo scripts/debloat.sh: turn every app in the list back on.
set -euo pipefail
LIST="${1:?usage: scripts/restore.sh lists/<your-tv>.txt}"
. "$(dirname "$0")/lib.sh"
for P in $(sed -e 's/#.*//' -e 's/[[:space:]]//g' "$LIST" | grep -v '^$'); do
  tv pm path "$P" >/dev/null 2>&1 && echo "$(tv pm enable "$P" | tr -d '\r')" || echo "skip $P (not installed)"
done
