#!/usr/bin/env bash
# Build a release Android APK using the same signing config as CI (see
# RELEASING.md's "Known issue on this fork" section for why this currently
# uses the debug key).
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

log "Building release APK (mobile/gradlew :app:assembleRelease)"
cd mobile
./gradlew :app:assembleRelease
log "APK: mobile/app/build/outputs/apk/release/catapult-mobile.apk"
