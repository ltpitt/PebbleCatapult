# Tasker Watch Notification Delivery

## Problem

Tasker currently reaches Catapult and `PebbleSender.insertTimelinePin()` reports
success, but the user sees no immediate watch notification. A timeline pin
provides history but does not replace Catapult's existing immediate
watch-message transport.

## Behavior

The Tasker `SEND_NOTIFICATION` action will:

1. Validate title, body, vibration, and duration once.
2. Send the notification through the existing Catapult watch-message path so
   the watch can show the popup and vibrate immediately.
3. Insert a generic notification timeline pin through PebbleKit Android 2 so
   the notification is also retained in the watch timeline.
4. Report failure to Tasker if either transport fails; success is reported only
   after both transports complete.

The existing `CREATE_PIN` and `DELETE_PIN` timeline actions are unchanged.

## Design

`TaskerActionRunner.runNotification` remains the orchestration boundary. It
will call `InteractiveSessionManager.sendNotification` with the configured
vibration and duration, then call `PebbleSender.insertTimelinePin` using the
same title/body and current timestamp. The existing `startWatchapp` callback
continues to be supplied to the direct-message path.

Direct-message errors retain their current exception and Tasker error mapping.
Timeline result values retain their explicit mapping to user-facing failures.
If the direct message fails, timeline insertion is not attempted; if timeline
insertion fails after the direct message succeeds, Tasker receives a failed
result rather than a false success.

## Tests

Update the Tasker runner tests to verify:

- both immediate dispatch and timeline insertion occur for a valid notification;
- the direct dispatch receives title, body, vibration, duration, and the
  watch-app start callback;
- direct dispatch failures are propagated;
- timeline transport failures are mapped and propagated;
- existing duration and input validation behavior remains unchanged.
