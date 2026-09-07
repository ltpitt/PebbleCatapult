#!/bin/sh
set -eu

cd "$(dirname "$0")"
./run_notification_lifecycle_test.sh
./run_notification_packet_test.sh
echo "all native Pebble tests passed"
