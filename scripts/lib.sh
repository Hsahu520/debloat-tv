# Shared by the scripts: reads tv.conf and connects to the TV over ADB. Source it, don't run it.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -f "$ROOT/tv.conf" ]; then
  # shellcheck disable=SC1091
  set -a; . "$ROOT/tv.conf"; set +a
fi
: "${TV_HOST:?No TV address. Copy tv.conf.example to tv.conf and set TV_HOST, or run with TV_HOST=<tv ip>}"
command -v adb >/dev/null || { echo "adb not found: install Android platform-tools (see README)." >&2; exit 1; }
TV="$TV_HOST"
case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
adb connect "$TV" >/dev/null 2>&1 || true
if ! adb -s "$TV" get-state >/dev/null 2>&1; then
  echo "Can't reach the TV at $TV. Is it on, on the same Wi-Fi, with USB debugging on?" >&2
  echo "If the TV shows 'Allow USB debugging?', tick 'Always allow' and press OK, then run this again." >&2
  exit 1
fi
tv() { adb -s "$TV" shell "$@"; }
