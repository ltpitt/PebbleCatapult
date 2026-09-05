#!/usr/bin/env bash
# Run the Android unit test suite (mirrors what CI runs, minus the
# screenshot tests which need Paparazzi's native font/rendering setup).
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

log "Running unit tests (mobile/gradlew runDebugTests, excluding screenshot tests)"
cd mobile
./gradlew runDebugTests -x :app-screenshot-tests:testDebugUnitTest --continue
