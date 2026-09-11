# Tasker Interactive Watch Launch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Tasker interactive prompts launch Catapult on the watch and wait for its connection before sending the existing request.

**Architecture:** `TaskerActionRunner` requests an intentional watchapp launch. `InteractiveSessionManagerImpl` waits for a sender registration instead of failing immediately when none is currently connected. The existing `WatchappConnectionImpl` packet protocol and watch UI remain unchanged.

**Tech Stack:** Kotlin, coroutines, PebbleKit Android 2, JUnit/Kotlin test infrastructure.

---

### Task 1: Add sender-registration waiting tests

**Files:**
- Modify: `mobile/tasker/data/src/test/kotlin/com/matejdro/catapult/tasker/InteractiveSessionManagerImplTest.kt`

- [ ] **Step 1: Add a test where registration happens after the request starts**

Use a short test timeout, start `awaitResult`, register an `InteractiveRequestSender` from the test coroutine, and assert that the sender receives the request rather than returning `Watch connection is unavailable`.

- [ ] **Step 2: Add a test for registration timeout**

Call `awaitResult` without registering a sender and assert an explicit failed result after the bounded wait.

- [ ] **Step 3: Run the focused test**

Run:

```bash
./gradlew :tasker:data:test --tests '*InteractiveSessionManagerImplTest'
```

Expected: the new tests fail because the manager currently returns immediately when no sender exists.

### Task 2: Implement bounded sender waiting

**Files:**
- Modify: `mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/InteractiveSessionManagerImpl.kt`

- [ ] **Step 1: Add a registration signal**

Maintain a coroutine-safe notification for sender registration and signal it from `registerSender`. Do not hold the manager mutex while suspending.

- [ ] **Step 2: Replace the immediate unavailable result**

When no sender is registered, wait for registration up to the interactive timeout. If no sender arrives, return `InteractiveTaskerResult.Failed("Watch connection is unavailable")`.

- [ ] **Step 3: Preserve existing session and cancellation semantics**

Only create `ActiveSession` after selecting a sender. Keep duplicate-session rejection, response validation, timeout cancellation, and unregister behavior unchanged.

- [ ] **Step 4: Run the focused test**

Run:

```bash
./gradlew :tasker:data:test --tests '*InteractiveSessionManagerImplTest'
```

Expected: PASS.

### Task 3: Launch the watchapp before interactive actions

**Files:**
- Modify: `mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/TaskerActionRunner.kt`
- Test: `mobile/tasker/data/src/test/kotlin/com/matejdro/catapult/tasker/TaskerActionRunnerTest.kt`

- [ ] **Step 1: Add runner assertions**

Provide a fake `PebbleSender` and `WatchappOpenController`, execute a list or confirmation action, and assert the intentional-open marker and `startAppOnTheWatch(WATCHAPP_UUID)` are invoked before waiting for the result.

- [ ] **Step 2: Implement the launch helper**

For both `runInteractiveList` and `runInteractiveConfirmation`, call:

```kotlin
openController.setNextWatchappOpenForAutoSync()
sender.startAppOnTheWatch(WATCHAPP_UUID)
```

Then call `interactiveSessionManager.awaitResult(...)` with the existing request and timeout.

- [ ] **Step 3: Run Tasker data tests**

Run:

```bash
./gradlew :tasker:data:test
```

Expected: PASS.

### Task 4: Verify the end-to-end packet path

**Files:**
- No source changes expected.

- [ ] **Step 1: Build the compatible APK**

Use the repository’s existing Gradle command with `-x :buildSrc:commit-hooks` if hook resolution fails.

- [ ] **Step 2: Install the APK and launch one Show List Tasker action**

Capture Android logs around the trigger.

- [ ] **Step 3: Confirm the required sequence**

The logs must show watchapp launch/connection registration followed by an outgoing interactive request with packet ID `5`; selecting an item must produce packet ID `8` and the Tasker result variables.
