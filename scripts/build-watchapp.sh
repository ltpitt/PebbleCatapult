#!/usr/bin/env bash
# Build the Pebble watchapp locally. Requires the pebble-tool CLI and SDK to
# already be installed (see .github/workflows/develop.yaml's "Install pebble
# SDK" step for the exact one-time setup this expects).
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
require_cmd pebble "Install it with: uv tool install pebble-tool --python 3.13 && pebble sdk install latest"

log "Building watchapp (watch/pebble build)"
cd watch
pebble build
mv build/watch.pbw build/catapult-watchapp.pbw
log "Watchapp: watch/build/catapult-watchapp.pbw"
