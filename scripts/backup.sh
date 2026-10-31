#!/usr/bin/env bash
# Step 2: snapshot the TV before changing anything: installed and disabled apps, the Home app, and all settings.
# The FIRST run is also kept as backup/original; scripts/restore-original.sh puts the TV back to exactly that.
# Later runs add a dated copy next to it and never touch backup/original.
set -euo pipefail
. "$(dirname "$0")/lib.sh"
OUT="$ROOT/backup/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
tv pm list packages | sed 's/^package://' | tr -d '\r' | sort > "$OUT/packages-installed.txt"
tv pm list packages -d | sed 's/^package://' | tr -d '\r' | sort > "$OUT/packages-disabled.txt"
tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
  | tail -1 | tr -d '\r' > "$OUT/home-activity.txt"
tv getprop | tr -d '\r' > "$OUT/getprop.txt"
for n in global secure system; do tv settings list "$n" | tr -d '\r' > "$OUT/settings-$n.txt"; done
echo "Backup saved in $OUT"
if [ ! -d "$ROOT/backup/original" ]; then
  cp -R "$OUT" "$ROOT/backup/original"
  echo "This is your first backup, so it is also saved as backup/original."
  echo "scripts/restore-original.sh puts the TV back to it."
fi
