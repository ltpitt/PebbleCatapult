# README Refresh Design

## Goal

Refresh `README.MD` as an accurate, user-facing introduction to Catapult. The
README should explain what the project does, how users install both components,
which capabilities are currently available, and how interactive Tasker results
are consumed.

## Scope

The change is limited to `README.MD`. It will:

- describe Catapult as an AutoPebble-compatible successor for PebbleOS and
  Tasker;
- clarify Android app and watchapp installation paths and companion-app
  compatibility;
- replace the stale feature list with capabilities implemented today,
  including cached actions, folders, dynamic visibility, voice input, timeline
  pins, interactive list/confirmation sessions, and watch notifications;
- document the interactive result variables with a concise Tasker example;
- link to the AutoPebble parity matrix for the complete built/planned/dropped
  feature boundary;
- retain the contributor link and point developers to existing project
  documentation without duplicating the contributor guide.

## Documentation boundaries

The README will not duplicate the protocol registry, parity table, detailed
developer setup, or implementation specifications. Those remain in the existing
reference and contributor documentation and are linked from the README where
useful.

## Acceptance criteria

- Every feature claimed as available is supported by the current parity matrix
  or current user-facing implementation.
- Installation instructions distinguish the Android app from the watchapp and
  retain the warning about the unsupported legacy Pebble app.
- The interactive variables and their meanings match the current result
  contract.
- A new user can understand the basic Tasker-to-watch workflow without reading
  internal documentation.
- Existing badges, demo media, contributor link, and valid release links remain
  usable.
