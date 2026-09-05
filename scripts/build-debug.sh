#!/usr/bin/env bash
# Build a debug Android APK. Fast: no lint, no tests, no watchapp.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

log "Building debug APK (mobile/gradlew :app:assembleDebug)"
cd mobile
./gradlew :app:assembleDebug
log "APK: mobile/app/build/outputs/apk/debug/catapult-mobile.apk"
