# AutoPebble Parity Matrix

This document is the single source of truth for how Catapult relates to the
original [AutoPebble Pebble app](https://github.com/joaomgcd/AutoPebble-Pebble-app).
Catapult is an AutoPebble-compatible *successor*, not a byte-for-byte port: it
keeps the useful capabilities and deliberately drops features that fight the
native Pebble experience or that belong in the Android app instead.

Each AutoPebble capability is classified as:

- **Built** — present in Catapult today.
- **Planned** — has an approved design spec, not yet implemented.
- **Dropped** — intentionally not supported, with rationale.

Shared contracts referenced below (result variables, packet IDs, gesture
vocabulary) live in [`protocol-and-results.md`](protocol-and-results.md).

## Matrix

| AutoPebble capability | Class | Catapult design | Rationale |
| --- | --- | --- | --- |
| Task/action list | Built | — | Core Catapult action list. |
| Nested folders (subfolders) | Built | — | Core Catapult directory model. |
| Cached/offline actions | Built | — | Bucket synchronization caches actions on the watch. |
| Dynamic show/hide actions | Built | — | Enabled flag synced per action. |
| Voice argument for an action | Built | — | Watch voice dictation passed as a task argument. |
| Timeline pin creation/deletion | Built | — | Tasker `CREATE_PIN` / `DELETE_PIN` actions. |
| Interactive list selection | Built | [interactive-tasker-sessions](../specs/2026-09-03-interactive-tasker-sessions-design.md) | `SHOW_LIST` screen variant. |
| Confirmation dialog | Built | [interactive-tasker-sessions](../specs/2026-09-03-interactive-tasker-sessions-design.md) | `SHOW_CONFIRMATION` screen variant. |
| One-way watch notification | Built | [watch-notifications](../specs/2026-09-04-watch-notifications-design.md) | Packet 11; replaces the obsolete `SEND_NOTIFICATION` broadcast. |
| Quick-action screen (Up/Select/Down) | Planned | [quick-action-screens](../specs/2026-09-04-quick-action-screens-design.md) | `quick` screen variant. |
| Text/detail screen (scrollable) | Planned | [detail-text-screens](../specs/2026-09-04-detail-text-screens-design.md) | `text` screen variant. |
| Live clock text variable | Planned | [detail-text-screens](../specs/2026-09-04-detail-text-screens-design.md) | Optional time token inside a text screen. |
| Screen stack / addressable screen IDs | Planned | [navigation-stacks](../specs/2026-09-04-navigation-stacks-design.md) | Umbrella session model; screens push/pop within one session. |
| Custom back / long-back actions | Planned | [navigation-stacks](../specs/2026-09-04-navigation-stacks-design.md) | Back gestures may carry an action before popping. |
| Long-click list action | Planned | [long-press-list-actions](../specs/2026-09-04-long-press-list-actions-design.md) | `long_select` gesture on a row. |
| Stable action IDs + fallback contract | Planned | [action-ids-fallbacks](../specs/2026-09-04-action-ids-fallbacks-design.md) | Opaque IDs, editable labels, shared result contract. |
| Tap / wrist-twist / battery events | Planned | [watch-event-callbacks](../specs/2026-09-04-watch-event-callbacks-design.md) | Best-effort event path, separate from screens. |
| Motion X/Y/Z accelerometer streaming | Dropped (deferred) | [watch-event-callbacks](../specs/2026-09-04-watch-event-callbacks-design.md) (deferred subsection) | High battery cost and niche; revisit only with real-device demand. |
| Multi-click (third gesture tier) | Dropped | — | Pebble UX standardizes on `select` + `long_select`; a third tier is undiscoverable. |
| Per-cell custom sizes / first-cell size | Dropped | — | Catapult lists use uniform native rows for consistent rendering across platforms. |
| Keep-scroll-position flag | Dropped | — | Native menu retains position by default; a per-request flag adds protocol surface for no real gain. |
| Title/text font overrides | Dropped | — | Catapult owns consistent native typography. |
| Fullscreen toggle | Dropped | — | Disabled even in AutoPebble; modern PebbleOS status bar is standard. |
| Do-not-disturb per screen | Dropped | — | Interactive sessions are explicitly requested; DND belongs to the OS. |
| Light on/off/short control | Dropped | — | Backlight is an OS interaction concern, not a Tasker output. |
| Custom CSV vibration patterns | Dropped | — | The notification action exposes a bounded vibration enum instead. |
| Vibrate-on-click persisted setting | Dropped | — | On-watch persisted settings are replaced by Android-app configuration. |
| On-watch settings screen | Dropped | — | Configuration lives in the Catapult Android app. |

## Notes on dropped features

"Dropped" is a product decision, not a limitation to be worked around. If a
dropped capability gains real-device demand, promote it to **Planned** by
writing a spec and reclassifying its row here — do not reintroduce it ad hoc in
another spec.
