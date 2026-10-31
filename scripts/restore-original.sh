#!/usr/bin/env bash
# Put the TV back the way it was in backup/original (the first scripts/backup.sh run), or in another backup folder:
#   scripts/restore-original.sh                 # uses backup/original
#   scripts/restore-original.sh backup/<date>   # uses that snapshot
# It re-enables every app that was enabled then, disables the ones that were disabled then, restores the Home,
# accessibility and screensaver settings, and removes this launcher if it wasn't on the TV before.
# Apps you uninstalled since can't come back by script: it lists them so you can reinstall them from the Play Store.
set -euo pipefail
. "$(dirname "$0")/lib.sh"
SNAP="${1:-$ROOT/backup/original}"
[ -f "$SNAP/packages-installed.txt" ] || { echo "No backup at $SNAP. Run scripts/backup.sh first (before any change)." >&2; exit 1; }
PKG=com.gathin.tvhome

NOW_INSTALLED=$(tv pm list packages | sed 's/^package://' | tr -d '\r' | sort)
NOW_DISABLED=$(tv pm list packages -d | sed 's/^package://' | tr -d '\r' | sort)
WAS_DISABLED=$(cat "$SNAP/packages-disabled.txt")

echo "Re-enabling apps that were on before:"
for P in $NOW_DISABLED; do
  grep -qx "$P" "$SNAP/packages-disabled.txt" || echo "  $(tv pm enable "$P" | tr -d '\r')"
done
echo "Disabling apps that were off before:"
for P in $WAS_DISABLED; do
  echo "$NOW_INSTALLED" | grep -qx "$P" || continue
  echo "$NOW_DISABLED" | grep -qx "$P" || echo "  $(tv pm disable-user --user 0 "$P" | tr -d '\r')"
done

# Settings this project changes: put each back to its old value, or remove it if it wasn't set.
restore_setting() {   # restore_setting <namespace> <key>
  local line
  line=$(grep -m1 "^$2=" "$SNAP/settings-$1.txt" || true)
  if [ -n "$line" ]; then tv settings put "$1" "$2" "${line#*=}"; else tv settings delete "$1" "$2" >/dev/null; fi
}
restore_setting secure enabled_accessibility_services
restore_setting secure accessibility_enabled
restore_setting secure screensaver_components
restore_setting secure screensaver_enabled
restore_setting global private_dns_mode
restore_setting global private_dns_specifier

if grep -qx "$PKG" "$SNAP/packages-installed.txt"; then
  echo "The launcher was already installed in that backup: keeping it."
elif echo "$NOW_INSTALLED" | grep -qx "$PKG"; then
  echo "Removing the launcher: $(tv pm uninstall "$PKG" | tr -d '\r')"
fi
HOME_BEFORE=$(cat "$SNAP/home-activity.txt" 2>/dev/null || true)
case "$HOME_BEFORE" in
  */*) tv cmd package set-home-activity "$HOME_BEFORE" >/dev/null 2>&1 && echo "Home is $HOME_BEFORE again." || true ;;
esac

GONE=$(comm -23 "$SNAP/packages-installed.txt" <(echo "$NOW_INSTALLED"))
if [ -n "$GONE" ]; then
  echo
  echo "These apps were installed in the backup but are gone now; reinstall them from the Play Store if you want them:"
  printf '  %s\n' $GONE
fi
echo
echo "Done. Restart the TV once (hold the remote's power button > Restart) to finish."
