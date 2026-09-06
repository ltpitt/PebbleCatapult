# Protocol and Result Contract Registry

This is the authoritative reference for Catapult's AppMessage packet IDs, the
Tasker result-variable contract, and the gesture vocabulary shared by every
interactive feature. Specs must link here instead of redefining these values so
that packet numbers never collide and the result contract stays consistent.

The wire-level details of each shipped packet live in the repository-root
[`protocol.md`](../../../protocol.md); this document owns the *allocation table*
and the cross-cutting contracts.

## Pebble UI rule

Every watch UI change must start from the closest matching example in the
[official Pebble UI patterns repository](https://github.com/pebble-examples/ui-patterns).
Use its layout, native layer types, typography, colors, spacing, and interaction
patterns as the baseline; adapt only the data and lifecycle behavior required by
Catapult. This rule applies to every implementation plan and screen variant.

Android-to-Pebble communication must start from the official
[PebbleKit Android 2](https://github.com/pebble-dev/PebbleKitAndroid2) APIs and
documentation. Prefer typed `PebbleSender` operations, including
`insertTimelinePin` for official Pebble timeline/notification experiences, over
custom protocols or legacy companion broadcasts when supported.

## Gesture vocabulary

Interactive screens use exactly three gestures. No third "multi-click" tier
exists (see the parity matrix).

| Gesture | Meaning |
| --- | --- |
| `select` | Primary press on the focused element. |
| `long_select` | Secondary/long press on the focused element. |
| `back` | Back button; pops a screen or, on the root screen, cancels the session. |

## Result contract

Every interactive session (list, confirmation, text, quick action, and any
future screen variant) reports its outcome through the same Tasker variables.

| Variable | Meaning |
| --- | --- |
| `%catapult_status` | One of `success`, `cancelled`, `timeout`, `failed`. |
| `%catapult_result_id` | Selected item's stable action ID (screens that select an item). |
| `%catapult_result_value` | Selected item's display value (screens that select an item). |
| `%catapult_result_action` | Gesture kind that produced the result (`select` or `long_select`). |

Rules:

- IDs are opaque and stable; display labels/values are never used as identifiers.
- A secondary (`long_select`) action falls back to the primary action **only
  when the request explicitly permits fallback**; otherwise the session reports
  `failed`.
- `cancelled`, `timeout`, and `failed` must never be reported as `success`. No
  failure path may silently complete a Tasker action successfully.

## Session model

One interactive session is active at a time. A session owns a bounded **screen
stack**; each screen is a typed variant (`list`, `confirmation`, `text`,
`quick`). The watch renders screens and handles gestures; the phone owns the
authoritative stack. A screen either **completes** the session with a terminal
result or **pushes/pops** within it. `back` on the root screen is cancellation.
Session IDs make stale and duplicate messages harmless.

## Packet ID allocation

Packets 0–11 are shipped and their IDs are frozen. Dictionary key `0` always
carries the packet ID.

| ID | Name | Direction | Status |
| --- | --- | --- | --- |
| 0 | Watch Welcome | Watch → Phone | Shipped |
| 1 | Phone Welcome | Phone → Watch | Shipped |
| 2 | Re-start bucketsync | Phone → Watch | Shipped |
| 3 | Follow-up bucket data | Phone → Watch | Shipped |
| 4 | Trigger action | Watch → Phone | Shipped |
| 5 | SHOW_LIST | Phone → Watch | Shipped |
| 6 | SHOW_CONFIRMATION | Phone → Watch | Shipped |
| 7 | CANCEL | Phone → Watch | Shipped |
| 8 | LIST_SELECTION | Watch → Phone | Shipped |
| 9 | CONFIRMATION_RESULT | Watch → Phone | Shipped |
| 10 | CANCEL_OR_ERROR | Watch → Phone | Shipped |
| 11 | Show notification | Phone → Watch | Shipped |
| 12 | SHOW_TEXT | Phone → Watch | Reserved (detail-text-screens) |
| 13 | SHOW_QUICK_ACTIONS | Phone → Watch | Reserved (quick-action-screens) |
| 14 | SCREEN_ACTION_RESULT | Watch → Phone | Reserved (text/quick result: action ID + gesture) |
| 15 | SCREEN_POP | Watch → Phone | Reserved (non-terminal back in a multi-screen stack) |
| 16 | WATCH_EVENT | Watch → Phone | Reserved (tap/twist/battery events) |

Rules for adding packets:

- Reserve the next free ID here **before** writing wire details in `protocol.md`.
- Protocol negotiation gates every packet above the peer's reported version;
  older watches receive only the version response.
- Reuse an existing packet with an added, optional, bounded field when the
  semantics are the same (for example, long-press list actions extend
  `LIST_SELECTION` with a gesture-kind field rather than adding a packet).

Reserved IDs are proposals until their spec is implemented; confirm the final
number here when the wire format lands.
