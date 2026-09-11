# Watch Task Selection and Status Bar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep the Catapult action list open after a task send succeeds and align status indicators left while keeping the clock right.

**Architecture:** Preserve the existing watch-to-phone protocol, callbacks, vibrations, and status-bar geometry. Make only the success callback and indicator draw coordinates surgical changes.

**Tech Stack:** Pebble C SDK, existing watch build scripts, Kotlin/Gradle focused regression test.

---

### Task 1: Keep the action list open after task selection

**Files:**
- Modify: `watch/src/ui/window_action_list.c:230-241`

- [ ] Remove only `window_stack_pop_all(true);` from the successful branch of `on_task_starting_result`. Keep `vibes_short_pulse()` and the failure branch unchanged.
- [ ] Confirm the diff contains no changes to packet sending or callback registration.

### Task 2: Move status indicators to the left

**Files:**
- Modify: `watch/src/ui/layers/status_bar.c:66-80`

- [ ] Replace the clock-relative `icon_x` calculation with a left-edge origin while preserving the existing bitmap offsets:
  - error and busy icons at `x = 3`
  - disconnected icon at `x = 0`
- [ ] Leave the clock frame, right-side width reservation, indicator priority, callbacks, and vertical coordinates unchanged.

### Task 3: Validate and commit

**Files:**
- Modify: `watch/src/ui/window_action_list.c`
- Modify: `watch/src/ui/layers/status_bar.c`

- [ ] Run `git diff --check`.
- [ ] Run `./gradlew :bluetooth:data:testDebugUnitTest --tests com.matejdro.catapult.bluetooth.WatchappConnectionImplTest --no-configuration-cache`.
- [ ] Run the existing watch build command `./scripts/build-watchapp.sh` when the Pebble SDK is available; otherwise report the environment limitation.
- [ ] Review the final diff for unrelated changes.
- [ ] Commit with `fix(watch): keep task list open and align status icons`.
- [ ] Push the committed branch to its configured upstream.
