# Watch Notifications Design

## Goal

Add a Catapult Tasker action that sends an immediate, one-way notification to
the Pebble, replacing the obsolete `com.getpebble.action.SEND_NOTIFICATION`
integration for Catapult users.

## Design

Add a `SEND_NOTIFICATION` Tasker action with title, body, optional vibration
style, and an optional display duration. The phone validates UTF-8 byte
limits, encodes a versioned phone-to-watch packet, and sends it through the
existing PebbleKit 2 connection. The watch renders a native notification
window, vibrates according to the request, and dismisses it with Back or
Select.

The watch returns to the normal Catapult screen automatically after the
requested duration. Duration is bounded and has a safe default; a duration of
zero means no automatic dismissal. Notifications are fire-and-forget: Tasker
receives a dispatch success/failure status and does not wait for user input.

## Compatibility and failure handling

This is separate from interactive sessions and Timeline pins. Old watches
negotiate the existing protocol version and do not receive notification
packets. A disconnected watch, unsupported packet version, oversized text, or
failed send produces an explicit Tasker failure. When multiple watches are
connected, the notification is sent to the connected watch with the lowest
watch ID; this deterministic fallback preserves the existing no-selector
semantics without adding another Tasker field.

## Testing

Add tests for input validation, UTF-8 limits, packet sizing, vibration and
duration encoding, unsupported versions, and send failures. Add watch tests for
rendering, manual dismissal, automatic dismissal, replacement by a newer
notification, and timer cleanup. The manual acceptance test sends notifications
with 5-second and no-timeout settings from Tasker.
