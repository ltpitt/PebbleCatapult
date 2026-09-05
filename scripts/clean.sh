#!/usr/bin/env bash
# Clean all local build outputs (Gradle + Pebble).
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

log "Cleaning mobile/ (gradlew clean)"
(cd mobile && ./gradlew clean)

if [[ -d watch/build ]]; then
  log "Cleaning watch/build"
  rm -rf watch/build
fi
