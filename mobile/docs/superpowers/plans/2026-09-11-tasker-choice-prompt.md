# Tasker Choice Prompt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver and validate a blocking Tasker choice prompt that lets a user select one Pebble option and returns the result to Tasker.

**Architecture:** Reuse the existing `InteractiveTaskerRequest.List`, `InteractiveSessionManager`, chunked `InteractiveWatchMessage`, and watch interactive-list window. The phone owns the session and validates the returned option; Tasker receives the existing status, ID, and value variables.

**Tech Stack:** Kotlin, coroutines, Android Tasker plugin service, PebbleKit Android 2/AppMessage, Pebble C watchapp, JUnit/Kotest.

---

## Existing implementation map

- `mobile/tasker/api/.../InteractiveTaskerRequest.kt`: immutable title/options request and JSON decoding.
- `mobile/tasker/data/.../TaskerActionRunner.kt`: blocking `SHOW_LIST` Tasker action and timeout handling.
- `mobile/tasker/data/.../InteractiveSessionManagerImpl.kt`: single active session, sender routing, timeout, cancellation, and response validation.
- `mobile/bluetooth/api/.../InteractiveWatchMessage.kt`: bounded/chunked request and response wire model.
- `mobile/bluetooth/data/.../WatchappConnectionImpl.kt`: phone/watch serialization and response forwarding.
- `watch/src/connection/packets.c`: watch packet assembly and response emission.
- `watch/src/ui/window_interactive_list.c`: watch selection UI.
- `mobile/tasker/data/src/main/kotlin/.../TaskerActionService.kt`: `%catapult_status`, `%catapult_result_id`, and `%catapult_result_value` mapping.

The requested first slice is already implemented across these surfaces. The execution work is therefore verification-first: run the focused automated coverage, build the watch artifact, and perform the three manual acceptance paths before any push.

### Task 1: Verify the phone-side choice contract

**Files:**
- Test: `mobile/tasker/api/src/test/kotlin/com/matejdro/catapult/tasker/InteractiveTaskerRequestTest.kt`
- Test: `mobile/tasker/data/src/test/kotlin/com/matejdro/catapult/tasker/InteractiveSessionManagerImplTest.kt`
- Test: `mobile/tasker/data/src/test/kotlin/com/matejdro/catapult/tasker/TaskerActionRunnerTest.kt`
- Test: `mobile/tasker/data/src/test/kotlin/com/matejdro/catapult/tasker/TaskerResultMappingTest.kt`

- [ ] **Step 1: Run request and session tests**

Run:

```bash
./gradlew :tasker:api:test :tasker:data:test --tests '*InteractiveTaskerRequestTest' --tests '*InteractiveSessionManagerImplTest' --tests '*TaskerActionRunnerTest' --tests '*TaskerResultMappingTest'
```

Expected: all selected tests pass, including matching selection, invalid selection rejection, timeout, cancellation, stale-session rejection, and Tasker variable mapping.

- [ ] **Step 2: Add a regression test only if the focused run exposes a missing contract**

Keep the existing `SHOW_LIST` and `InteractiveTaskerRequest.List` API. If a missing case is found, add one test beside the nearest existing test, using a three-option request and asserting the selected stable ID/value; do not introduce a second choice protocol.

- [ ] **Step 3: Re-run the focused phone tests**

Run the same Gradle command and require a passing result before proceeding.

### Task 2: Verify the phone/watch wire path

**Files:**
- Test: `mobile/bluetooth/data/src/test/kotlin/com/matejdro/catapult/bluetooth/InteractiveWatchMessageTest.kt`
- Modify only if needed: `mobile/bluetooth/api/src/main/kotlin/com/matejdro/catapult/bluetooth/InteractiveWatchMessage.kt`

- [ ] **Step 1: Run protocol tests**

```bash
./gradlew :bluetooth:data:test --tests '*InteractiveWatchMessageTest'
```

Expected: list order, chunk assembly, terminal metadata, UTF-8 bounds, stable ID/value selection, and malformed packet rejection all pass.

- [ ] **Step 2: Preserve the current wire contract**

Do not allocate a new packet for confirmation or quick actions. A choice prompt uses packet IDs 5 and 8, with session ID and stable option ID/value fields.

### Task 3: Build and manually test the watch interaction

**Files:**
- Build artifact: `watch/build/catapult-watchapp.pbw`
- Manual test configuration: Tasker `SHOW_LIST` action with three JSON options.

- [ ] **Step 1: Build the watchapp**

Run from `watch/`:

```bash
./waf build
```

Expected: `build/catapult-watchapp.pbw` is generated successfully.

- [ ] **Step 2: Configure the manual success path**

Use a Tasker action with title `Choose location` and options:

```json
[
  {"id":"home","value":"Home"},
  {"id":"work","value":"Work"},
  {"id":"other","value":"Other"}
]
```

Set a timeout long enough for manual interaction, trigger the workflow, select `Work` on the watch, and assert:

```text
%catapult_status = success
%catapult_result_id = work
%catapult_result_value = Work
```

- [ ] **Step 3: Test cancellation**

Trigger the same action, press Back on the watch, and assert `%catapult_status = cancelled`; it must not be `success`.

- [ ] **Step 4: Test timeout**

Set a short timeout, trigger the action, do not interact, and assert `%catapult_status = timeout`; it must not be `success`.

- [ ] **Step 5: Record manual results before pushing**

Do not push until the success, cancellation, and timeout paths have been observed on the connected watch and Tasker.
