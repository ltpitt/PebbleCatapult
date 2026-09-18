# README Refresh Design

## Goal

Refresh `README.MD` into a modern, functional, and welcoming landing page for
someone who has a Pebble watch and Tasker and wants to try Catapult. Preserve
the useful existing content and the demo media, but make the install path,
current capabilities, project context, and next steps easier to scan.

## Audience and positioning

The primary audience is a new Pebble + Tasker user. The README will:

- describe Catapult as a community fork of the original PebbleCatapult project
  by [@matejdro](https://github.com/matejdro/PebbleCatapult), with explicit
  appreciation for that foundation;
- explain that this fork's current goal is practical feature parity with
  AutoPebble / Pebble Tasker;
- state that prioritization is guided by user feedback, the maintainer's own
  needs, and available time, without promising complete parity;
- link to the [AutoPebble parity matrix](../reference/autopebble-parity-matrix.md)
  so built, planned, and intentionally dropped capabilities are clear.

## Page structure

The README will use this order:

1. **Hero:** title, one-sentence value proposition, a compact GitHub download
   call to action, and the existing `docs/demo.gif`.
2. **What it is:** a short fork and AutoPebble context paragraph.
3. **Quick Start:** download the Android APK and Pebble PBW from the rolling
   `debug-latest` GitHub prerelease, install both components, and sync Tasker
   actions. Keep the warning that the legacy Pebble app is unsupported and that
   users need microPebble or the new Pebble/Core app.
4. **What works today:** compact capability groups covering launch/organization,
   input/notifications, and interactive Tasker sessions. Explicitly mention
   Pebble Time Round support without maintaining a fragile exhaustive model
   table.
5. **Interactive Tasker results:** preserve the result-variable table and one
   practical example.
6. **Roadmap and project links:** link to the parity matrix, release page,
   contributor guide, and relevant project documentation.

## Installation and release presentation

For now, the README will focus on this fork's GitHub releases. Remove the
Obtainium and Rebble badges and do not present those services as installation
paths.

The primary call to action will be **Download the latest preview**, linking to
the `debug-latest` release:
`https://github.com/ltpitt/PebbleCatapult/releases/tag/debug-latest`.
Describe it as a rolling prerelease containing the Android APK and Pebble PBW,
so users understand that it is convenient for trying the current fork but is
not a stable release channel.

The Quick Start must distinguish the Android app download from the watchapp
download and must not imply that installing the APK alone installs the
watchapp. All release links must resolve to this repository rather than stale
upstream or malformed relative URLs.

## Content and visual treatment

- Retain `docs/demo.gif` as the only project visual; do not add a new
  screenshot-capture task.
- Add descriptive alternative text to the demo.
- Prefer short sections and compact bullets over a dense feature table.
- Base current capability claims on the parity matrix and implementation:
  task actions, nested folders, cached/offline actions, dynamic visibility,
  voice input, timeline pins, watch notifications, interactive list selection,
  and confirmation dialogs.
- Keep the interactive result names and meanings synchronized with the current
  Tasker result contract.
- Label preview/release status honestly and avoid implying that planned parity
  items are already available.
- Keep deeper protocol, contributor, and implementation details in their
  existing documents rather than duplicating them in the README.

## Non-goals

- No Android or watchapp behavior changes.
- No new images or screenshots.
- No Obtainium or Rebble installation integration in this refresh.
- No attempt to reproduce the entire AutoPebble parity matrix in the README.
- No rewrite of `CONTRIBUTING.MD`, release documentation, or internal specs.

## Acceptance criteria

- A new Pebble + Tasker user can understand Catapult's purpose and the
  Android-to-watch workflow from the README alone.
- The upstream maintainer and original PebbleCatapult project receive clear,
  visible credit.
- The README states the practical AutoPebble / Pebble Tasker parity goal and
  links to the detailed parity boundary.
- The only primary installation path is this fork's GitHub `debug-latest`
  release, with separate APK and PBW instructions and an explicit rolling
  preview label.
- The legacy Pebble app warning remains accurate.
- Pebble Time Round support is mentioned without overstating other platform
  coverage.
- Every current capability claimed is supported by the parity matrix or
  current user-facing implementation.
- The interactive variables and example match the current result contract.
- The existing demo GIF and contributor link remain usable, and all release,
  documentation, and image links resolve correctly.
