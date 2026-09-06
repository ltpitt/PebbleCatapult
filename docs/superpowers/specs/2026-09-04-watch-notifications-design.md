# Watch Notifications Design

## Goal

Add a Catapult Tasker action that sends an immediate, one-way notification to
the Pebble, replacing the obsolete `com.getpebble.action.SEND_NOTIFICATION`
integration for Catapult users.

## Design

Add a `SEND_NOTIFICATION` Tasker action with title, body, and an optional
display duration. The phone validates UTF-8 byte
limits, then inserts a `TimelinePin` through PebbleKit Android 2 using the
`GENERIC_NOTIFICATION` layout. The Pebble/Core companion owns the native
notification rendering, vibration, persistence, and dismissal behavior.

This action does not render a Catapult watchapp window. The existing packet-11
dialog implementation remains available for future Catapult-specific messages.

The watch returns to the normal Catapult screen automatically after the
requested duration. Duration is bounded and has a safe default; a duration of
zero means no automatic dismissal. Notifications are fire-and-forget: Tasker
receives a dispatch success/failure status and does not wait for user input.

## Compatibility and failure handling

This uses the official Pebble timeline/notification path rather than a custom
watchapp packet. Duration is interpreted as the timeline pin duration; zero
creates a pin without an expiry. Vibration is controlled by the companion and
is no longer configurable by Catapult. A missing companion app, unsupported
timeline operation, oversized text, or failed insertion produces an explicit
Tasker failure.

## Testing

Add tests for input validation, UTF-8 limits, timeline layout construction,
duration mapping, missing companion handling, unsupported operations, and
insertion failures. The manual acceptance test sends notifications with
5-second and no-expiry settings from Tasker and verifies that they appear in
the official Pebble notification/timeline UI.
