#!/usr/bin/env bash
# Optional: push a theme folder (config.json, wallpaper.jpg, wallpapers/) to the launcher without rebuilding.
# Example: scripts/theme.sh themes/aurora-dynamic
set -euo pipefail
THEME="${1:?usage: scripts/theme.sh themes/<name>}"
. "$(dirname "$0")/lib.sh"
[ -f "$THEME/config.json" ] || { echo "no config.json in $THEME" >&2; exit 1; }
URI=content://com.gathin.tvhome.theme
put() {   # put <local file> <name in the theme>
  local stage="/data/local/tmp/theme-$(echo "$2" | tr '/' '-')"
  adb -s "$TV" push "$1" "$stage" >/dev/null
  tv "content write --uri $URI/$2 < $stage; rm -f $stage"
}
put "$THEME/config.json" config.json
[ -f "$THEME/wallpaper.jpg" ] && put "$THEME/wallpaper.jpg" wallpaper.jpg
if [ -d "$THEME/wallpapers" ]; then
  tv "content delete --uri $URI/wallpapers" >/dev/null 2>&1 || true
  for f in "$THEME"/wallpapers/*; do [ -f "$f" ] && put "$f" "wallpapers/$(basename "$f")"; done
fi
tv am force-stop com.gathin.tvhome
tv am start -n com.gathin.tvhome/.MainActivity >/dev/null
echo "Theme $(basename "$THEME") applied."
