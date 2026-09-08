# Tasker Watch Notification Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Tasker notifications appear immediately on the watch while also preserving them as official Pebble timeline notification pins.

**Architecture:** Keep `TaskerActionRunner.runNotification` as the orchestration boundary. It will send through `InteractiveSessionManager` first, using the existing Catapult watch-message protocol and watch-app startup callback, then insert the same notification as a PebbleKit Android 2 timeline pin. Explicit failures from either transport remain visible to Tasker.

**Tech Stack:** Kotlin, Android, Kotlin coroutines, PebbleKit Android 2 `PebbleSender`, Kotest/JUnit 5, Gradle.

---

### Task 1: Extending the runner test seam for immediate delivery

**Files:**
- Modify: `mobile/tasker/data/src/test/kotlin/com/matejdro/catapult/tasker/TaskerActionRunnerTest.kt:51-65`

- [ ] **Step 1: Add recording state and failure injection to the fake session manager**

Add these members to `RecordingInteractiveSessionManager`:

```kotlin
data class NotificationCall(
   val title: String,
   val body: String,
   val vibration: Int,
   val durationMs: Long,
   val startWatchapp: Boolean,
)

val notifications = mutableListOf<NotificationCall>()
var notificationFailure: Throwable? = null

override suspend fun sendNotification(
   title: String,
   body: String,
   vibration: Int,
   durationMs: Long,
   startWatchapp: suspend () -> Unit,
) {
   notificationFailure?.let { throw it }
   notifications += NotificationCall(title, body, vibration, durationMs, startWatchapp = true)
}
```

Keep the existing interactive request behavior unchanged.

- [ ] **Step 2: Run the current focused test suite**

Run:

```bash
./gradlew :tasker:data:testDebugUnitTest --tests 'com.matejdro.catapult.tasker.TaskerActionRunnerTest' --no-daemon --console=plain
```

Expected: the existing suite passes before changing the runner behavior.

- [ ] **Step 3: Commit the test seam**

```bash
git add mobile/tasker/data/src/test/kotlin/com/matejdro/catapult/tasker/TaskerActionRunnerTest.kt
git commit -m "test: record immediate Tasker notification dispatch"
```

### Task 2: Lock down dual notification delivery with failing tests

**Files:**
- Modify: `mobile/tasker/data/src/test/kotlin/com/matejdro/catapult/tasker/TaskerActionRunnerTest.kt:67-140`

- [ ] **Step 1: Extend the success test**

After `runner.run(bundle)`, assert both transports:

```kotlin
interactiveManager.notifications.single() shouldBe
   RecordingInteractiveSessionManager.NotificationCall(
      title = "Door",
      body = "Front door opened",
      vibration = VibrationStyle.SHORT.ordinal,
      durationMs = 5_000,
      startWatchapp = true,
   )
```

Keep the existing timeline layout and duration assertions.

- [ ] **Step 2: Add direct-dispatch failure coverage**

Add a test that sets:

```kotlin
interactiveManager.notificationFailure = IllegalStateException("Watch connection is unavailable")
```

and asserts the exception is propagated by `runner.run(...)` and
`pebbleSender.insertedPins` remains empty.

- [ ] **Step 3: Update timeline failure coverage**

Keep the existing `TimelineResult.Unknown("Disconnected")` assertion, and add
an assertion that the immediate notification was recorded before the timeline
failure is raised.

- [ ] **Step 4: Run the focused tests and verify the new assertions fail**

Run:

```bash
./gradlew :tasker:data:testDebugUnitTest --tests 'com.matejdro.catapult.tasker.TaskerActionRunnerTest' --no-daemon --console=plain
```

Expected: the new immediate-dispatch assertions fail because
`TaskerActionRunner.runNotification` currently inserts only a timeline pin.

- [ ] **Step 5: Commit the failing regression tests**

```bash
git add mobile/tasker/data/src/test/kotlin/com/matejdro/catapult/tasker/TaskerActionRunnerTest.kt
git commit -m "test: require immediate and timeline Tasker notifications"
```

### Task 3: Orchestrating both notification transports

**Files:**
- Modify: `mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/TaskerActionRunner.kt:86-134`

- [ ] **Step 1: Dispatch the immediate notification before timeline insertion**

Immediately after `validateNotification(request)`, call:

```kotlin
interactiveSessionManager.sendNotification(
   title = request.title,
   body = request.body,
   vibration = request.vibration.ordinal,
   durationMs = request.durationMs,
   startWatchapp = { sender.startAppOnTheWatch(WATCHAPP_UUID) },
)
```

Do not catch or convert the existing direct-message exception; this preserves
the current Tasker failure path and prevents timeline insertion after a failed
immediate notification.

- [ ] **Step 2: Retain the existing timeline insertion and result mapping**

Leave the `TimelinePin` construction and all explicit `TimelineResult` branches
in place after the direct send. The timeline pin must reuse the request title,
body, duration, and current timestamp.

- [ ] **Step 3: Run the focused tests**

Run:

```bash
./gradlew :tasker:data:testDebugUnitTest --tests 'com.matejdro.catapult.tasker.TaskerActionRunnerTest' --no-daemon --console=plain
```

Expected: all `TaskerActionRunnerTest` tests pass, including both delivery
assertions and failure ordering.

- [ ] **Step 4: Commit the implementation**

```bash
git add mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/TaskerActionRunner.kt
git commit -m "fix: deliver Tasker notifications immediately and to timeline"
```

### Task 4: Verify the Android-facing behavior

**Files:**
- No source changes expected.

- [ ] **Step 1: Run the complete Tasker data test suite**

Run:

```bash
./gradlew :tasker:data:testDebugUnitTest --no-daemon --console=plain
```

Expected: all Tasker data unit tests pass.

- [ ] **Step 2: Reinstall/run the debug app and trigger the existing Tasker action**

Use the connected Fairphone debug target and run the configured Tasker action
once with a short notification duration.

- [ ] **Step 3: Confirm both delivery markers in logcat**

Run:

```bash
"$HOME/Library/Android/sdk/platform-tools/adb" logcat -d -v threadtime -t 500 |
   grep -E 'Sending notification packet to watch|Notification packet sent successfully|Pebble notification inserted successfully'
```

Expected: the direct-message success markers and the timeline insertion marker
appear for the same trigger, and the watch shows the popup/vibration.

- [ ] **Step 4: Commit only if verification requires a follow-up fix**

If a follow-up source change is required by the device test, add the targeted
test first, rerun the focused suite, and commit it separately with a message
describing the observed device failure.
