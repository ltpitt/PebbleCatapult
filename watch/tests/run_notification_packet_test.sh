#!/bin/sh
set -eu

cd "$(dirname "$0")/.."
binary="tests/.notification_packet_test"
trap 'rm -f "$binary"' EXIT

cc -std=c11 -Wall -Wextra -Werror \
    -I tests/fixture -I src \
    tests/fixture/pebble.c src/connection/notification_packet.c \
    tests/notification_packet_test.c -o "$binary"
"$binary"
echo "notification packet decoder tests passed"
