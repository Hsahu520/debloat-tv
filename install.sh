#!/usr/bin/env bash
# Step 4: build the launcher, install it on the TV, make it Home and set its screensaver.
# Run again after any change: it updates the launcher in place and keeps its settings.
set -euo pipefail
cd "$(dirname "$0")"
. scripts/lib.sh
PKG=com.gathin.tvhome

# Java 17+ and the Android SDK (see README). local.properties tells Gradle where the SDK is.
export JAVA_HOME="${JAVA_HOME:-$( /usr/libexec/java_home 2>/dev/null || dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
if [ ! -f launcher/local.properties ]; then
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  for d in "$SDK" ~/Library/Android/sdk ~/Android/Sdk /opt/homebrew/share/android-commandlinetools /usr/local/share/android-commandlinetools; do
    [ -n "$d" ] && [ -d "$d/platforms" ] && { SDK="$d"; break; }
  done
  [ -d "$SDK/platforms" ] || { echo "Android SDK not found: set ANDROID_HOME (see README)." >&2; exit 1; }
  echo "sdk.dir=$SDK" > launcher/local.properties
fi
(cd launcher && ./gradlew assembleRelease -q --console=plain)

adb -s "$TV" push launcher/app/build/outputs/apk/release/app-release.apk /data/local/tmp/tvhome.apk >/dev/null
tv pm install -r /data/local/tmp/tvhome.apk
tv rm /data/local/tmp/tvhome.apk

# Permissions an app can't ask for on a TV: Wi-Fi name, the remote's microphone (Jarvis), and repairing its own
# Home-button helper after an update.
tv pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true
tv pm grant $PKG android.permission.RECORD_AUDIO || true
tv pm grant $PKG android.permission.WRITE_SECURE_SETTINGS || true
# The Home-button helper sends the Home button (and the stock home at boot) to this launcher. An update turns it off.
tv settings put secure enabled_accessibility_services $PKG/.HomeKeyService
tv settings put secure accessibility_enabled 1
tv cmd package set-home-activity $PKG/.MainActivity >/dev/null || true
# The launcher's screensaver: greeting, clock, date and weather.
tv settings put secure screensaver_components $PKG/.ClockDream
tv settings put secure screensaver_enabled 1
# Minutes without the remote before the screensaver starts (SCREENSAVER_MINUTES in tv.conf; unset keeps the TV's own).
[ -n "${SCREENSAVER_MINUTES:-}" ] && tv settings put system screen_off_timeout $((SCREENSAVER_MINUTES * 60000))
tv am start -n $PKG/.MainActivity >/dev/null
echo "Installed. Press Home on the remote."
