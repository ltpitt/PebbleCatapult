# Watch Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Catapult Tasker action that sends an immediate, one-way Pebble notification through the official Pebble/Core companion.

**Architecture:** The Tasker action validates title/body/duration and uses the official PebbleKit Android 2 `PebbleSender.insertTimelinePin` API with a `GENERIC_NOTIFICATION` layout. The Pebble/Core companion owns rendering, vibration, persistence, and dismissal; the existing custom packet/window remains available for future Catapult-specific messages.

**UI rule:** Official notification UI comes from the Pebble/Core companion. Any future Catapult-owned notification window must start from the closest matching example in the [official Pebble UI patterns repository](https://github.com/pebble-examples/ui-patterns).

> The original packet-11 implementation steps below are superseded for
> `SEND_NOTIFICATION`. Keep that code only as a reusable Catapult-owned dialog;
> new notification work must use `PebbleSender.insertTimelinePin`.

**Tech Stack:** Kotlin, Android Tasker plugin APIs, PebbleKit 2, Kotlin test fixtures, Pebble C SDK, CMake, existing AppMessage protocol.

---

## File map

- Modify `mobile/tasker/api/src/main/kotlin/com/matejdro/catapult/tasker/TaskerAction.kt` and `BundleKeys.kt` for the action and inputs.
- Modify `mobile/tasker/ui/src/main/kotlin/com/matejdro/catapult/tasker/ui/screens/...` and create `NotificationActivity.kt` for Tasker configuration.
- Modify `mobile/tasker/ui/src/main/AndroidManifest.xml` to register the configuration activity.
- Modify `mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/TaskerActionRunner.kt` to validate inputs and dispatch.
- Create `mobile/bluetooth/api/src/main/kotlin/com/matejdro/catapult/bluetooth/WatchNotificationMessage.kt` for packet encoding.
- Modify `mobile/bluetooth/api/src/main/kotlin/com/matejdro/catapult/bluetooth/WatchAppConnection.kt` and `mobile/bluetooth/data/src/main/kotlin/com/matejdro/catapult/bluetooth/WatchappConnectionImpl.kt` for one-way dispatch and explicit send failures.
- Modify `protocol.md` for packet fields, bounds, duration, and protocol version.
- Create `watch/src/ui/window_notification.c` and `window_notification.h` for rendering and timer lifecycle.
- Modify `watch/src/connection/packets.c`, `packets.h`, and `watch/src/main.c` for packet dispatch and cleanup.
- Add focused tests beside existing Tasker and Bluetooth tests.

### Task 1: Define notification inputs and results

**Files:**
- Modify: `mobile/tasker/api/src/main/kotlin/com/matejdro/catapult/tasker/TaskerAction.kt`
- Modify: `mobile/tasker/api/src/main/kotlin/com/matejdro/catapult/tasker/BundleKeys.kt`
- Test: `mobile/tasker/data/src/test/kotlin/com/matejdro/catapult/tasker/TaskerActionRunnerTest.kt`

- [ ] **Step 1: Write the failing runner tests**

Add tests for the new action shape:

```kotlin
@Test
fun `send notification dispatches title body style and duration`() = scope.runTest {
   runner.run(Bundle().apply {
      putString(BundleKeys.ACTION, TaskerAction.SEND_NOTIFICATION.name)
      putString(BundleKeys.TITLE, "Door")
      putString(BundleKeys.MESSAGE, "Front door opened")
      putString(BundleKeys.NOTIFICATION_VIBRATION, "short")
      putLong(BundleKeys.NOTIFICATION_DURATION_MS, 5_000)
   })

   notificationSender.sent.single() shouldBe
      NotificationRequest("Door", "Front door opened", VibrationStyle.SHORT, 5_000)
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
cd mobile && ./gradlew :tasker:data:test --tests '*TaskerActionRunnerTest*send notification*'
```

Expected: compilation failure because `SEND_NOTIFICATION` and the notification request types do not exist.

- [ ] **Step 3: Add the minimal API values**

Add `SEND_NOTIFICATION` to `TaskerAction` and these keys to `BundleKeys`:

```kotlin
const val NOTIFICATION_VIBRATION = "NOTIFICATION_VIBRATION"
const val NOTIFICATION_DURATION_MS = "NOTIFICATION_DURATION_MS"
```

Use a typed internal request with `VibrationStyle.NONE`, `SHORT`, and `DOUBLE`; use a five-second default and a zero value for no automatic dismissal.

- [ ] **Step 4: Run the test and verify the input contract compiles**

Run the same focused test. Expected: it now reaches the runner and fails because dispatch is not implemented.

- [ ] **Step 5: Commit the domain input contract**

```bash
git add mobile/tasker/api mobile/tasker/data/src/test
git commit -m "feat(tasker): define notification action inputs"
```

### Task 2: Implement phone-side notification packet and dispatch

**Files:**
- Modify: `mobile/bluetooth/api/src/main/kotlin/com/matejdro/catapult/bluetooth/WatchAppConnection.kt`
- Create or modify: `mobile/bluetooth/api/src/main/kotlin/com/matejdro/catapult/bluetooth/WatchNotificationMessage.kt`
- Modify: `mobile/bluetooth/data/src/main/kotlin/com/matejdro/catapult/bluetooth/WatchappConnectionImpl.kt`
- Modify: `mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/TaskerActionRunner.kt`
- Test: `mobile/bluetooth/data/src/test/kotlin/com/matejdro/catapult/bluetooth/WatchNotificationMessageTest.kt`
- Test: `mobile/tasker/data/src/test/kotlin/com/matejdro/catapult/tasker/TaskerActionRunnerTest.kt`

- [ ] **Step 1: Write packet and failure tests**

Cover the packet fields and boundaries:

```kotlin
@Test
fun `notification packet carries text vibration and duration`() {
   val packet = WatchNotificationMessage.Show(
      "Door", "Front door opened", VibrationStyle.SHORT, 5_000,
   ).toPacket(maxPayloadBytes = 256)

   packet[0u] shouldBe UInt32(PACKET_SHOW_NOTIFICATION)
   packet[2u] shouldBe Text("Door")
   packet[7u] shouldBe Text("Front door opened")
   packet[6u] shouldBe UInt8(1u)
   packet[8u] shouldBe UInt32(5_000u)
}

@Test
fun `notification rejects oversized UTF8 text`() {
   shouldThrow<IllegalArgumentException> {
      WatchNotificationMessage.Show("é".repeat(33), "body", VibrationStyle.NONE, 0)
   }
}
```

Also test negative durations, durations above the chosen maximum, invalid vibration values, payload overflow, disconnected watches, and an explicit send failure.

- [ ] **Step 2: Run Bluetooth and Tasker tests to verify red**

```bash
cd mobile && ./gradlew :bluetooth:data:test --tests '*WatchNotificationMessageTest' :tasker:data:test --tests '*TaskerActionRunnerTest*send notification*'
```

Expected: failures for missing packet type and dispatch.

- [ ] **Step 3: Implement the bounded message**

Use a new packet ID immediately after the existing interactive range (11), key `2` for title, key `7` for body, key `6` for vibration enum, and key `8` for duration milliseconds. Include the common protocol/version fields only if the existing connection contract requires them; notifications are not sessions and must not create an interactive session.

Enforce title <= 64 UTF-8 bytes, body <= 128 UTF-8 bytes, duration in `[0, 300_000]`, and the encoded packet strictly below the negotiated AppMessage buffer.

- [ ] **Step 4: Add the connection API and runner branch**

Add:

```kotlin
suspend fun sendNotification(notification: WatchNotificationMessage.Show)
```

to the watch connection abstraction. Queue the packet through the same sender used by interactive requests and propagate a failed send as an error. Add `TaskerAction.SEND_NOTIFICATION -> runNotification(bundle)` to `TaskerActionRunner`; invalid input throws `TaskerInvalidInputException`, while a successful dispatch returns no interactive result.

- [ ] **Step 5: Run tests to verify green**

```bash
cd mobile && ./gradlew :bluetooth:data:test --tests '*WatchNotificationMessageTest' :tasker:data:test --tests '*TaskerActionRunnerTest*send notification*'
```

Expected: PASS.

- [ ] **Step 6: Commit phone dispatch**

```bash
git add mobile/bluetooth mobile/tasker/data
git commit -m "feat(bluetooth): dispatch watch notifications"
```

### Task 3: Add the Tasker configuration UI

**Files:**
- Create: `mobile/tasker/ui/src/main/kotlin/com/matejdro/catapult/tasker/ui/NotificationActivity.kt`
- Modify: `mobile/tasker/ui/src/main/kotlin/com/matejdro/catapult/tasker/ui/screens/...`
- Modify: `mobile/tasker/ui/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add the configuration activity registration**

Register an exported activity with the existing `EDIT_SETTING` intent action and label it `Send notification`. Reuse `TaskerConfigurationActivity` and the existing save/blurb conventions; no separate UI test module exists for these configuration activities.

- [ ] **Step 2: Build the configuration screen**

Provide fields for title, body, vibration (`None`, `Short`, `Double`), and duration in milliseconds. Persist the four bundle keys and advertise the action as `%catapult_status` replacement output. Display the safe defaults: `Short` vibration and `5000` milliseconds.

- [ ] **Step 3: Validate configuration input**

Reject blank titles, malformed duration, negative duration, and values above 300000 milliseconds with a visible validation message. Preserve Tasker variables such as `%title` and `%body` in saved fields for runtime expansion.

- [ ] **Step 4: Build the UI module**

```bash
cd mobile && ./gradlew :tasker:ui:compileDebugKotlin :tasker:data:test --tests '*TaskerActionRunnerTest*send notification*'
```

Expected: BUILD SUCCESSFUL and the existing notification runner test remains green.

- [ ] **Step 5: Commit the Tasker UI**

```bash
git add mobile/tasker/ui
git commit -m "feat(tasker): configure watch notifications"
```

### Task 4: Implement the Pebble notification window

**Files:**
- Create: `watch/src/ui/window_notification.h`
- Create: `watch/src/ui/window_notification.c`
- Modify: `watch/src/connection/packets.c`
- Modify: `watch/src/connection/packets.h`
- Modify: `watch/src/main.c`

- [ ] **Step 1: Write a C-level behavior checklist before coding**

Exercise these cases through callbacks: show title/body, vibrate once, Back dismisses, Select dismisses, five-second timer dismisses, zero duration leaves the window open, a new notification replaces the old timer/window, and cleanup cancels the timer.

- [ ] **Step 2: Add the notification window API**

Expose:

```c
void window_notification_show(const char* title, const char* body,
    uint8_t vibration, uint32_t duration_ms);
void window_notification_dismiss(void);
void window_notification_dismiss_all(void);
```

Use a Window with title and scrollable body layers. Register Back and Select callbacks, enqueue the selected Pebble vibration pattern, and schedule an `AppTimer` only when `duration_ms > 0`.

- [ ] **Step 3: Route packet 11**

Validate packet ID, title/body tuple types and bounded lengths, vibration range, and duration range in `packets.c`. Dismiss any existing interactive/notification window before showing the new notification. Reject malformed input by logging and leaving the current screen safe.

- [ ] **Step 4: Build all watch targets**

```bash
cd watch && pebble clean && pebble build
```

Expected: BUILD SUCCESSFUL for aplite, basalt, diorite, and emery.

- [ ] **Step 5: Commit the watch window**

```bash
git add watch/src
git commit -m "feat(watch): show auto-dismissing notifications"
```

### Task 5: Document protocol and verify integration

**Files:**
- Modify: `protocol.md`
- Modify: `watch/src/main.c` and `mobile/bluetooth/data/src/main/kotlin/com/matejdro/catapult/bluetooth/Constants.kt` for the protocol version.
- Test: `mobile/bluetooth/data/src/test`, `mobile/tasker/data/src/test`

- [ ] **Step 1: Document packet 11**

Specify title/body keys, vibration values, duration bounds, dismissal semantics, and the fact that notifications are one-way and do not use interactive session IDs. Document protocol negotiation behavior for older watches.

- [ ] **Step 2: Add protocol/version tests**

Assert that a watch at the previous protocol version does not receive packet 11, and that the current version accepts valid notifications while rejecting malformed fields and oversized UTF-8 values.

- [ ] **Step 3: Run focused validation**

```bash
cd mobile && ./gradlew :bluetooth:data:test :tasker:data:test
cd ../watch && pebble build
```

Expected: all targeted tests pass and all watch targets build.

- [ ] **Step 4: Perform manual acceptance testing**

Install the generated APK and PBW, then run Tasker actions for:

1. A five-second notification: verify title/body, vibration, automatic return.
2. A zero-duration notification: verify it remains until Back or Select.
3. A replacement notification: verify only the newest body is visible and the old timer cannot dismiss it.
4. Oversized text and disconnected watch: verify Tasker reports failure.

- [ ] **Step 5: Commit documentation and integration coverage**

```bash
git add protocol.md mobile watch
git commit -m "docs: specify watch notification protocol"
```
