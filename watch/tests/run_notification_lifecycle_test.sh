#!/bin/sh
set -eu

cd "$(dirname "$0")/.."
binary="tests/.notification_lifecycle_test"
trap 'rm -f "$binary"' EXIT

cc -std=c11 -Wall -Wextra -Werror \
    -I src \
    src/ui/notification_lifecycle.c tests/notification_lifecycle_test.c \
    -o "$binary"
"$binary"
echo "notification lifecycle tests passed"
