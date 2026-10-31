#!/usr/bin/env bash
# Step 3 (optional): disable the apps named in a list, one per line (# comments allowed).
# Disables only (pm disable-user), never uninstalls: scripts/restore.sh <same list> turns them all back on.
set -euo pipefail
LIST="${1:?usage: scripts/debloat.sh lists/<your-tv>.txt}"
. "$(dirname "$0")/lib.sh"
PKGS=$(sed -e 's/#.*//' -e 's/[[:space:]]//g' "$LIST" | grep -v '^$' || true)
# Packages Android or the TV needs to boot and to reach Home. A list that names one is refused as a whole.
CORE='^(android|com\.android\.systemui|com\.android\.shell|com\.android\.phone|com\.android\.settings|com\.android\.tv\.settings|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\..*|com\.android\.vending|com\.google\.android\.gms|com\.google\.android\.gsf|com\.google\.android\.apps\.tv\.launcherx|com\.google\.android\.tvlauncher|com\.google\.android\.tungsten\.setupwraith|com\.google\.android\.inputmethod\.latin)$'
BAD=$(printf '%s\n' "$PKGS" | grep -E "$CORE" || true)
if [ -n "$BAD" ]; then
  echo "Refusing $LIST: these must stay enabled or the TV may not boot or may show a black screen:" >&2
  printf '  %s\n' $BAD >&2
  exit 1
fi
for P in $PKGS; do
  if tv pm path "$P" >/dev/null 2>&1; then
    echo "$(tv pm disable-user --user 0 "$P" | tr -d '\r')"
  else
    echo "skip $P (not installed)"
  fi
done
echo "Done. Undo with: scripts/restore.sh $LIST"
