# Agent Instructions

## Pebble UI

When adding or changing Pebble UI, start from the closest matching example in
the official [Pebble UI patterns repository](https://github.com/pebble-examples/ui-patterns).
Preserve its native layer types, element positions, sizing, colors, fonts,
spacing, and animation structure. Adapt only the application data and lifecycle
behavior required by Catapult.

## Android-to-Pebble communication

When adding or changing Android-to-Pebble communication, start with the official
[PebbleKit Android 2](https://github.com/pebble-dev/PebbleKitAndroid2) APIs and
documentation. Prefer typed `PebbleSender` operations, including
`insertTimelinePin` for official Pebble timeline and notification experiences,
over custom protocols or legacy companion broadcasts when the API supports the
required behavior.

## Planning

Apply these rules to every implementation plan and screen variant. Before
implementing a feature, inspect the relevant official Pebble examples and
PebbleKit Android 2 APIs, then document any necessary deviation.
