# Watchapp Agent Instructions

> These instructions apply to the Pebble watchapp. Repository-wide and Android
> guidance lives in the root [`AGENTS.md`](../AGENTS.md) and
> [`mobile/AGENTS.md`](../mobile/AGENTS.md).

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

## Debugging and reproduction

Watch-side failures usually surface as an error packet the watchapp sends back
to the phone. For Android-side logs and cross-process failures, follow
[`mobile/AGENTS.md`](../mobile/AGENTS.md).

### Developer connection logs

Live `APP_LOG` output from the watch (physical or emulator):

```bash
# Physical watch, through the phone's Pebble app developer connection:
pebble logs --phone <PHONE_IP>          # e.g. 192.168.178.142 (phone wlan0 IP)
# Get the IP: $ADB -t <transport_id> shell ip addr show wlan0

# Emulator:
pebble logs --emulator aplite
```

### Deterministic emulator reproduction

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

## Watch memory (all models, especially aplite)

The watchapp must run on aplite, which has only ~24 KB of app RAM. Keep heap use
minimal and deterministic:

- **Do not** deep-copy large payloads into per-window structs when the data
  already lives in a longer-lived buffer (e.g. the interactive assembly buffers
  in `watch/src/connection/packets.c`). Have the window reference that
  caller-owned storage instead. A duplicate multi-KB `calloc` that succeeds on
  basalt will fail on aplite after bucket sync has consumed the heap, and the
  window then reports a generic "unable to display" error.
- Prefer pointers to existing static/global buffers over large fixed arrays
  embedded in dynamically allocated structs.
- After any watch UI change, verify on the aplite emulator (and ideally the
  aplite device), because allocation failures are platform-dependent.
