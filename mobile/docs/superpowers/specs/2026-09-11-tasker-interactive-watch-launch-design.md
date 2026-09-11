# Tasker Interactive Watch Launch Design

## Problem

Tasker interactive actions currently require an existing Catapult watchapp
connection. If the watchapp is closed, `InteractiveSessionManager` has no
registered sender and completes the Tasker action immediately without sending a
`SHOW_LIST` packet.

## Design

Before waiting for an interactive result, the Tasker action marks the next
watchapp launch as intentional and starts the Catapult watchapp through the
typed Pebble sender API. The interactive session manager waits for a sender to
register, bounded by the action timeout. Once registered, it sends the existing
interactive request unchanged.

If the watchapp cannot register before the timeout, the action returns an
explicit failure and does not claim success. Existing behavior remains
unchanged when a sender is already registered.

## Testing

Add session-manager tests for a sender registering after `awaitResult` starts
and for registration timeout. Add runner coverage that verifies the watchapp
launch is requested before the interactive session begins.
