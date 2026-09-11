# Agent Instructions

## Pebble UI

When adding or changing Pebble UI, start from the closest matching example in
the official [Pebble UI patterns repository](https://github.com/pebble-examples/ui-patterns).
Preserve its native layer types, element positions, sizing, colors, fonts,
spacing, and animation structure. Adapt only the application data and lifecycle
behavior required by Catapult.

### Click handling gotcha (app faults)

`window_single_click_subscribe()` (and friends) may only be called from inside
the window's **currently active** click config provider. Calling it after
`menu_layer_set_click_config_onto_window()` — which swaps the active provider to
the MenuLayer's own — is illegal and app-faults the watch (crash back to
launcher, seen as `prv_check_is_in_click_config_provider` when symbolicated).

Correct MenuLayer pattern: let the MenuLayer own the click config, handle
selection with the `.select_click` callback, and let the default BACK button pop
the window. Run any cancel/cleanup logic in the window `unload` handler, guarded
by a "resolved" flag so a normal selection doesn't also fire cancel.

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
`window_stack_pop_all(true)` and quits (see `PebbleCommons/watch/connection/bucket_sync.c`).

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

## Planning

Apply these rules to every implementation plan and screen variant. Before
implementing a feature, inspect the relevant official Pebble examples and
PebbleKit Android 2 APIs, then document any necessary deviation.

## Debugging & logs

Catapult spans two processes: the Android companion app (phone) and the Pebble
watchapp. Watch-side failures usually surface as an error packet the watchapp
sends *back* to the phone, so **always capture phone logs first** — they show
both sides.

### Phone (Android app) logs — primary tool

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

### Watch-side logs — developer connection

Live `APP_LOG` output from the watch (physical or emulator):

```bash
# Physical watch, through the phone's Pebble app developer connection:
pebble logs --phone <PHONE_IP>          # e.g. 192.168.178.142 (phone wlan0 IP)
# Get the IP: $ADB -t <transport_id> shell ip addr show wlan0

# Emulator:
pebble logs --emulator aplite
```

### Deterministic reproduction on the emulator

`watch/tools/catapult_interactive.py` replays the exact interactive wire protocol
straight to the watchapp (bypassing Tasker and the Android app). Build/install to
an emulator, then drive it over the pypkjs websocket:

```bash
cd watch && pebble build && pebble install --emulator aplite
# find the emulator's pypkjs --port in `ps aux | grep pypkjs`, then connect
# libpebble2 to ws://127.0.0.1:<port> and call replay(p, session=<fresh int>).
```

Use a **fresh session id** every run: the watch suppresses re-showing an already
completed session. Emulator button injection (`press_button` / `select_row` in
the same tool) exercises the full render → selection round-trip.

**Always reproduce on `aplite`**, not just `basalt`: aplite has the smallest app
RAM (~24 KB heap) and catches memory bugs the larger platforms hide.

## Watch memory (all models, esp. aplite)

The watchapp must run on aplite, which has only ~24 KB of app RAM. Keep heap use
minimal and deterministic:

- Do **not** deep-copy large payloads into per-window structs when the data
  already lives in a longer-lived buffer (e.g. the interactive assembly buffers
  in `watch/src/connection/packets.c`). Have the window reference that
  caller-owned storage instead. A duplicate multi-KB `calloc` that succeeds on
  basalt will fail on aplite after bucket sync has consumed the heap, and the
  window then reports a generic "unable to display" error.
- Prefer pointers to existing static/global buffers over large fixed arrays
  embedded in dynamically allocated structs.
- After any watch UI change, verify on the aplite emulator (and ideally the
  aplite device), because allocation failures are platform-dependent.
