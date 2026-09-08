#!/usr/bin/env bash
# Build the Pebble watchapp and install it on a connected watch.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
require_cmd pebble "Install it with: uv tool install pebble-tool --python 3.13"

"$(dirname "${BASH_SOURCE[0]}")/build-watchapp.sh"

log "Installing watchapp on the connected watch"
if [[ -n "${PEBBLE_PHONE_IP:-}" ]]; then
  pebble install --phone "$PEBBLE_PHONE_IP" watch/build/catapult-watchapp.pbw
else
  pebble install --phone watch/build/catapult-watchapp.pbw
fi
