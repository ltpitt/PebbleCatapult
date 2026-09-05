#!/usr/bin/env bash
# Build the debug APK and install it on a connected device/emulator via adb.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
require_cmd adb "Install Android platform-tools and ensure a device/emulator is connected (adb devices)."

"$(dirname "${BASH_SOURCE[0]}")/build-debug.sh"

log "Installing on the first connected device/emulator (adb install -r)"
adb install -r mobile/app/build/outputs/apk/debug/catapult-mobile.apk
