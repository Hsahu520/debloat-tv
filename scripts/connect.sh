#!/usr/bin/env bash
# Step 1: check the computer can talk to the TV, and show what it is.
set -euo pipefail
. "$(dirname "$0")/lib.sh"
echo "Connected to $TV"
echo "Model:    $(tv getprop ro.product.model | tr -d '\r')"
echo "Android:  $(tv getprop ro.build.version.release | tr -d '\r') (SDK $(tv getprop ro.build.version.sdk | tr -d '\r'))"
echo "Home now: $(tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r')"
