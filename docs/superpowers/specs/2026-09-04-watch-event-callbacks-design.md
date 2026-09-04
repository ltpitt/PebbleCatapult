# Watch Event Callbacks Design

## Goal

Expose optional Pebble interaction events—tap, wrist twist, and battery state—
to Tasker without coupling them to interactive screen requests.

## Design

Add a separate event subscription/request path. Tasker configures which event
types are enabled and supplies an event identifier; the watch sends typed,
bounded event messages with timestamps where available. Events are
best-effort, do not suspend a Tasker action, and are never confused with
terminal interactive results.

Battery events are rate-limited and sent only on meaningful state changes.
Tap and twist events are emitted only when the watch can observe them reliably.
The phone maps events to Tasker broadcasts or variables using the existing
plugin integration.

## Failure handling and testing

Validate subscriptions, rate limits, event IDs, and protocol versions. Test
duplicate delivery, reconnects, disabled events, battery thresholds, and
screen/session coexistence. Implement this last after the request/response
features have real-device validation.
