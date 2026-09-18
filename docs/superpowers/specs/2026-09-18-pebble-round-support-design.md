# Pebble Time Round Support Design

## Context

The watchapp currently declares `aplite`, `basalt`, `diorite`, and `emery` as
target platforms in `watch/package.json`. Pebble Time Round uses the `chalk`
platform, so the current manifest does not include it.

## Goal

Add `chalk` to the watchapp target platforms and launch the app in the Pebble
Time Round emulator so the user can test the existing UI on the round display.

## Non-goals

- Do not proactively redesign or refactor the watch UI.
- Do not change the Android companion or watch protocol.
- Do not claim full round-device compatibility based only on a successful build.
  Emulator observations can drive a later UI-fix pass.

## Design

1. Update `watch/package.json` to add `"chalk"` to
   `pebble.targetPlatforms`.
2. Use the existing Pebble CLI to build the watchapp.
3. Install and start the resulting app in the `chalk` emulator.
4. Report build, emulator, or SDK failures with the exact command output so
   they can be addressed explicitly.

The watch UI already derives several primary container sizes from the active
window bounds. This pass intentionally leaves known fixed-size details alone;
the emulator is the validation surface for deciding whether they need changes.

## Validation

- Confirm the manifest contains `chalk`.
- Run the existing watchapp build command.
- Install/start Catapult with the Pebble Round emulator.
- Leave interactive UI testing to the user after the emulator starts.

No new automated tests are needed for this manifest-only change.

## Documentation

The current README does not list supported watch platforms. No user-facing
documentation change is required for this focused emulator test.
