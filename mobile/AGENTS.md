# Android UI Guidance

> These instructions apply to the Android app. Repository-wide and watch-side
> guidance lives in the root [`AGENTS.md`](../AGENTS.md).

## Android-to-Pebble communication

When adding or changing Android-to-Pebble communication, start with the official
[PebbleKit Android 2](https://github.com/pebble-dev/PebbleKitAndroid2) APIs and
documentation. Prefer typed `PebbleSender` operations, including
`insertTimelinePin` for official Pebble timeline experiences, over custom
protocols or legacy companion broadcasts when the API supports the required
behavior.

To produce a **standard watch notification** (one that pops up, is dismissible,
and lands in the watch's notification history), post an ordinary Android
notification via `NotificationManagerCompat`; the Pebble companion app mirrors it
to the watch. `PebbleSender` exposes no notification API — `insertTimelinePin`
writes timeline pins (a separate BlobDB, reached with the watchface up/down
buttons), not the notification inbox.

### Watchapp launch & auto-close lifecycle

Launching the watchapp from the phone can arm an **auto-close after sync**: when
`WatchappOpenController.setNextWatchappOpenForAutoSync()` is set, the phone adds
key `3` to the welcome packet, and after bucket sync the watch calls
`window_stack_pop_all(true)` and quits (see
`PebbleCommons/watch/connection/bucket_sync.c`).

Only fire-and-forget **sync** actions should arm auto-close. Any UI that must
**stay open** — interactive Show List / confirmation, and hand-built
notification screens — must launch the watchapp WITHOUT arming auto-close.
Call `resetNextWatchappOpen()` before `startAppOnTheWatch(...)` to also clear a
stale flag left by a previous sync action; otherwise the watchapp opens and
immediately closes, surfacing on the phone as a "Watch connection closed" /
session-cancelled error even though the watch itself is fine.

Also register the interactive sender only **after** the watch welcome packet has
established a positive buffer size — registering on connection construction races
ahead of the welcome and fails interactive sends as "Watch connection is
unavailable".

### Returning variables to Tasker (and showing them on the plugin screen)

Interactive actions return their result as Tasker local variables in the bundle
passed to `TaskerPlugin.Setting.signalFinish` — e.g. `%catapult_status`,
`%catapult_result_id`, `%catapult_result_value` (see `TaskerResultKeys`). A task
can use these in the next action (e.g. Flash `%catapult_result_value`).

For Tasker to **show** these on the action's config screen and offer them for
autocomplete, the configuration activity must *declare* them: attach the
`net.dinglisch.android.tasker.RELEVANT_VARIABLES` string-array extra to the
result Intent (via `TaskerPlugin.addRelevantVariableList`, or the
`TaskerPluginConstants.RELEVANT_VARIABLES` key). Each entry is a `\n`-separated
`name\nlabel\ninfo` string; names must be lower-case local vars. Returning the
variables at runtime works without this, but there is no config-time guidance
until they are declared. Keep the declared names in sync with the runtime keys
by sourcing both from `TaskerResultKeys`.

### Incoming AppMessage integers are always 32-bit (PebbleKit width normalization)

**PebbleKit Android delivers every received number as `UInt32`/`Int32`, regardless
of the width the watchapp wrote** (see `BasePebbleListenerService` KDoc in
`io.rebble.pebblekit2.client`). So a watch that writes `dict_write_uint16(4, …)`
or `dict_write_uint8(5, …)` arrives on the phone as `PebbleDictionaryItem.UInt32`.

Never decode incoming numeric fields with a fixed-width `as? UInt16`/`UInt8`
cast — it returns null and the whole packet is rejected. This bit the interactive
`SHOW_LIST` round-trip: the watch's selection reply (`total`=uint16,
`terminal`=uint8, confirmation flag=uint8) was NACKed as "Missing chunk count",
so the session timed out. It worked on the emulator (pypkjs preserves widths) but
never on a real phone. Read incoming integers width-agnostically (accept
UInt8/UInt16/UInt32) — see `requireUnsigned` in `InteractiveWatchMessage.kt`. The
**outgoing** send path may still use exact widths; only the decode path must be
width-agnostic.

## Android debugging

### Phone logs — primary tool

The phone is typically paired over wireless `adb`. Use the SDK's `adb`
(`~/Library/Android/sdk/platform-tools/adb`), and target it explicitly because a
wireless device often appears twice:

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB devices -l                 # note the transport_id of the FP6/phone
$ADB -t <transport_id> logcat -c # clear, then reproduce on the phone
$ADB -t <transport_id> logcat -v time > /tmp/catapult_logcat.txt
```

Grep for `WatchappConnectionImpl`, `PebbleProtocol`, `interactive`, and the
watchapp UUID `54be2d78-a70c-4573-a73e-5b0f1323d4cd`. Outbound `AppMessagePush`
dictionaries and inbound ACK/NACK/error packets are all logged here. A watch
UI failure appears as an inbound error packet (e.g. interactive packet id `10`
with a human-readable reason string) after the chunks were ACKed.

## Planning

Before implementing Android functionality, inspect the relevant official
PebbleKit Android 2 APIs and documentation, then document any necessary
deviation.

## Tasker configuration screens

Tasker configuration screens use a consistent, readable form layout:

- Start with a fixed screen heading that identifies the configuration screen.
- Keep controls in one vertical form with 16dp outer/safe-area padding.
- Use 12dp vertical spacing between controls.
- Make fields and the primary action full width.
- Give every field an explicit, human-readable label.
- Use valid, concrete example defaults for new actions; preserve saved values
  when editing existing actions.
- Use an input-appropriate keyboard and show validation errors visibly near the
  relevant controls.
- Keep saved bundle keys and result-variable contracts stable when changing
  presentation.

This guidance follows Jetpack Compose Material and accessibility principles:
clear hierarchy, predictable spacing, labeled controls, scalable content, and
visible semantics. Apply it to new screens and when substantially revising an
existing screen.
