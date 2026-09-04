# Interactive Tasker Sessions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reliable, non-voice interactive Tasker workflow in which the phone sends a dynamic list or confirmation to the Pebble and receives the user's result.

**Architecture:** Add a small, versioned request/response protocol beside bucket synchronization. A phone-side session manager owns one request at a time and bridges the watch result to a waiting Tasker action; the watch exposes reusable list and confirmation windows without knowing about Tasker.

**Tech Stack:** Kotlin coroutines, Android Tasker plugin APIs already used in `mobile/tasker`, Rebble PebbleKit2/AppMessage, Pebble C SDK, JUnit/Kotlin test fixtures, Gradle.

---

## File map

- Create `mobile/tasker/api/src/main/kotlin/com/matejdro/catapult/tasker/InteractiveTaskerRequest.kt` for request types and immutable request data.
- Create `mobile/tasker/api/src/main/kotlin/com/matejdro/catapult/tasker/InteractiveTaskerResult.kt` for success, cancellation, timeout, and failure results.
- Create `mobile/tasker/api/src/main/kotlin/com/matejdro/catapult/tasker/InteractiveSessionManager.kt` for the phone-side session contract.
- Create `mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/InteractiveSessionManagerImpl.kt` for single-session state, timeout, and response validation.
- Modify `mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/TaskerActionRunner.kt` to start interactive Tasker actions and await results.
- Modify `mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/TaskerActionService.kt` only if service lifecycle handling is needed to keep the suspended action alive until a result.
- Modify `mobile/tasker/api/src/main/kotlin/com/matejdro/catapult/tasker/TaskerAction.kt` and `BundleKeys.kt` for the new action and serialized inputs.
- Modify `mobile/bluetooth/data/src/main/kotlin/com/matejdro/catapult/bluetooth/WatchappConnectionImpl.kt` to route incoming interactive responses and send requests.
- Create `mobile/bluetooth/api/src/main/kotlin/com/matejdro/catapult/bluetooth/InteractiveWatchMessage.kt` for typed phone/watch messages.
- Create `mobile/bluetooth/data/src/test/kotlin/com/matejdro/catapult/bluetooth/InteractiveWatchMessageTest.kt` for message validation and chunking cases.
- Modify `watch/src/connection/packets.c` and `watch/src/connection/packets.h` for protocol packets and callbacks.
- Create `watch/src/ui/window_interactive_list.c` and `.h` for the list screen.
- Create `watch/src/ui/window_interactive_confirm.c` and `.h` for the confirmation screen.
- Modify `watch/src/main.c` and `watch/CMakeLists.txt` to initialize and compile the interactive UI.
- Modify `protocol.md` to document packet IDs, fields, bounds, and result semantics.
- Add focused tests beside existing Tasker, Bluetooth, and watch protocol test conventions.

### Task 1: Define the interactive domain types

**Files:**
- Create: `mobile/tasker/api/src/main/kotlin/com/matejdro/catapult/tasker/InteractiveTaskerRequest.kt`
- Create: `mobile/tasker/api/src/main/kotlin/com/matejdro/catapult/tasker/InteractiveTaskerResult.kt`
- Create: `mobile/tasker/api/src/main/kotlin/com/matejdro/catapult/tasker/InteractiveSessionManager.kt`
- Test: `mobile/tasker/api/src/test/kotlin/com/matejdro/catapult/tasker/InteractiveTaskerRequestTest.kt`

- [x] **Step 1: Write failing tests for request bounds and result states**

```kotlin
@Test
fun `list request preserves item ids and values`() {
   val request = InteractiveTaskerRequest.List(
      title = "Locations",
      items = listOf(InteractiveTaskerRequest.Item("home", "Home")),
   )
   assertEquals("home", request.items.single().id)
   assertEquals("Home", request.items.single().value)
}

@Test
fun `result distinguishes cancellation from selection`() {
   assertIs<InteractiveTaskerResult.Cancelled>(InteractiveTaskerResult.Cancelled("back"))
   assertIs<InteractiveTaskerResult.Selection>(
      InteractiveTaskerResult.Selection("home", "Home"),
   )
}
```

- [x] **Step 2: Run the focused test and verify it fails because the types do not exist**

Run: `cd mobile && ./gradlew :tasker:api:test --tests '*InteractiveTaskerRequestTest'`

Expected: compilation failure for the missing request/result types.

- [x] **Step 3: Implement the immutable request/result contract**

```kotlin
sealed interface InteractiveTaskerRequest {
   data class List(val title: String, val items: kotlin.collections.List<Item>) :
      InteractiveTaskerRequest
   data class Confirmation(val title: String, val message: String) :
      InteractiveTaskerRequest

   data class Item(val id: String, val value: String)
}

sealed interface InteractiveTaskerResult {
   data class Selection(val id: String, val value: String) : InteractiveTaskerResult
   data class Confirmation(val accepted: Boolean) : InteractiveTaskerResult
   data class Cancelled(val reason: String) : InteractiveTaskerResult
   data class TimedOut(val reason: String) : InteractiveTaskerResult
   data class Failed(val reason: String) : InteractiveTaskerResult
}

interface InteractiveSessionManager {
   suspend fun awaitResult(request: InteractiveTaskerRequest): InteractiveTaskerResult
   fun cancelActive(reason: String)
   suspend fun acceptResult(sessionId: UInt, result: InteractiveTaskerResult)
}
```

- [x] **Step 4: Run the focused test and verify it passes**

Run: `cd mobile && ./gradlew :tasker:api:test --tests '*InteractiveTaskerRequestTest'`

Expected: PASS.

- [x] **Step 5: Commit the domain contract**

```bash
git add mobile/tasker/api
git commit -m "feat(tasker): define interactive session contract"
```

### Task 2: Implement the single-session manager

**Files:**
- Create: `mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/InteractiveSessionManagerImpl.kt`
- Test: `mobile/tasker/data/src/test/kotlin/com/matejdro/catapult/tasker/InteractiveSessionManagerImplTest.kt`

- [x] **Step 1: Write failing tests for selection, stale responses, cancellation, and timeout**

```kotlin
@Test
fun `matching response completes active session`() = runTest {
   val manager = manager(timeout = 1.minutes)
   val deferred = async {
      manager.awaitResult(
         InteractiveTaskerRequest.List("Locations", listOf(Item("home", "Home"))),
      )
   }
   advanceUntilIdle()
   manager.acceptResult(7u, InteractiveTaskerResult.Selection("home", "Home"))
   assertEquals(InteractiveTaskerResult.Selection("home", "Home"), deferred.await())
}

@Test
fun `timeout returns non-success result`() = runTest {
   val manager = manager(timeout = 1.seconds)
   val result = async { manager.awaitResult(InteractiveTaskerRequest.Confirmation("x", "y")) }
   advanceTimeBy(1_001)
   assertIs<InteractiveTaskerResult.TimedOut>(result.await())
}
```

- [x] **Step 2: Run the tests and verify they fail**

Run: `cd mobile && ./gradlew :tasker:data:test --tests '*InteractiveSessionManagerImplTest'`

Expected: compilation failure because the implementation and test factory are missing.

- [x] **Step 3: Implement guarded single-session state**

Use a `Mutex` plus a private active-session record containing the generated `UInt` ID, request, and `CompletableDeferred<InteractiveTaskerResult>`. Reject a second active request with `Failed("Another interactive session is active")`; ignore responses whose ID does not match; complete the deferred exactly once; and use `withTimeoutOrNull` to return `TimedOut`.

- [x] **Step 4: Run the focused tests**

Run: `cd mobile && ./gradlew :tasker:data:test --tests '*InteractiveSessionManagerImplTest'`

Expected: PASS, including stale and duplicate response cases.

- [x] **Step 5: Commit the manager**

```bash
git add mobile/tasker/data
git commit -m "feat(tasker): manage interactive session lifecycle"
```

### Task 3: Add typed phone/watch protocol messages

**Files:**
- Create: `mobile/bluetooth/api/src/main/kotlin/com/matejdro/catapult/bluetooth/InteractiveWatchMessage.kt`
- Modify: `protocol.md`
- Test: `mobile/bluetooth/data/src/test/kotlin/com/matejdro/catapult/bluetooth/InteractiveWatchMessageTest.kt`

- [x] **Step 1: Add failing tests for packet validation and chunk reassembly**

Cover list titles/items, confirmation messages, cancellation, selection, wrong session IDs, incomplete chunks, and duplicate chunks. Assert that oversized UTF-8 strings and item counts are rejected rather than truncated silently.

- [x] **Step 2: Run the focused test and verify it fails**

Run: `cd mobile && ./gradlew :bluetooth:data:test --tests '*InteractiveWatchMessageTest'`

Expected: compilation failure for missing message types.

- [x] **Step 3: Implement typed messages and deterministic chunking**

Define packet IDs distinct from packets 0–4, a fixed protocol version bump, `UInt` session IDs, item sequence numbers, total chunk count, and explicit terminal markers. Keep each encoded AppMessage payload below the watch-reported incoming buffer size. Validate all required fields before constructing a typed message.

- [x] **Step 4: Document the wire format**

Update `protocol.md` with exact packet IDs, dictionary keys, integer widths, UTF-8 limits, maximum list size, chunk ordering, and the rule that unknown/stale responses are rejected or ignored without completing Tasker.

- [x] **Step 5: Run tests and commit**

Run: `cd mobile && ./gradlew :bluetooth:data:test --tests '*InteractiveWatchMessageTest'`

Expected: PASS.

```bash
git add mobile/bluetooth/api mobile/bluetooth/data/src/test protocol.md
git commit -m "feat(protocol): add interactive watch messages"
```

### Task 4: Connect sessions to the existing phone/watch bridge

**Files:**
- Modify: `mobile/bluetooth/data/src/main/kotlin/com/matejdro/catapult/bluetooth/WatchappConnectionImpl.kt`
- Modify: `mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/TaskerServiceInjector.kt`
- Test: `mobile/bluetooth/data/src/test/kotlin/com/matejdro/catapult/bluetooth/WatchappConnectionImplTest.kt`

- [x] **Step 1: Write failing bridge tests**

Test that a list request emits ordered chunks, a matching selection reaches the session manager, stale responses are ignored, and an unavailable connection returns an explicit failure.

- [x] **Step 2: Run the focused bridge test**

Run: `cd mobile && ./gradlew :bluetooth:data:test --tests '*WatchappConnectionImplTest'`

Expected: FAIL until interactive packet routing is implemented.

- [x] **Step 3: Implement request sending and response routing**

Inject `InteractiveSessionManager` and a typed packet sender. Preserve existing welcome and bucket-sync handling. Add explicit cases for interactive response packet IDs and return `Nack` for malformed packets; do not catch or hide validation exceptions.

- [x] **Step 4: Run bridge and existing Bluetooth tests**

Run: `cd mobile && ./gradlew :bluetooth:data:test`

Expected: PASS.

- [x] **Step 5: Commit the bridge**

```bash
git add mobile/bluetooth mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/TaskerServiceInjector.kt
git commit -m "feat(bluetooth): bridge interactive sessions"
```

### Task 5: Add the Pebble list and confirmation UI

**Files:**
- Create: `watch/src/ui/window_interactive_list.c`
- Create: `watch/src/ui/window_interactive_list.h`
- Create: `watch/src/ui/window_interactive_confirm.c`
- Create: `watch/src/ui/window_interactive_confirm.h`
- Modify: `watch/src/connection/packets.c`
- Modify: `watch/src/connection/packets.h`
- Modify: `watch/src/main.c`
- Modify: `watch/CMakeLists.txt`

- [x] **Step 1: Add the packet/UI callback contract**

Define callbacks that receive a complete list or confirmation, and callbacks that emit selection, accepted/rejected, cancel, or display-error results with the active session ID.

- [x] **Step 2: Implement list navigation**

Use the existing action-list row sizing and highlight conventions. Render the request title and bounded item values, map Select to the selected item ID, and map Back to cancellation. Do not add voice controls or arbitrary text input.

- [x] **Step 3: Implement confirmation navigation**

Render the title/message with explicit Yes/No or OK/Cancel behavior. Back must produce cancellation rather than implicit acceptance.

- [x] **Step 4: Route packets without disturbing bucket sync**

Extend `receive_watch_packet` with interactive packet cases. Keep packet IDs 0–3 and bucket callbacks unchanged, and only open an interactive window after a complete, validated request has been reassembled.

- [x] **Step 5: Build and inspect all target platforms**

Run: `cd watch && pebble clean && pebble build`

Expected: successful builds for aplite, basalt, diorite, and emery, with no interactive UI symbols unresolved.

- [x] **Step 6: Commit the watch UI**

```bash
git add watch/src watch/CMakeLists.txt
git commit -m "feat(watch): add interactive selection and confirmation UI"
```

### Task 6: Expose interactive actions through Tasker

**Files:**
- Modify: `mobile/tasker/api/src/main/kotlin/com/matejdro/catapult/tasker/TaskerAction.kt`
- Modify: `mobile/tasker/api/src/main/kotlin/com/matejdro/catapult/tasker/BundleKeys.kt`
- Modify: `mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/TaskerActionRunner.kt`
- Modify: `mobile/tasker/data/src/main/kotlin/com/matejdro/catapult/tasker/TaskerActionService.kt`
- Create or modify configuration screens under `mobile/tasker/ui/src/main/kotlin/com/matejdro/catapult/tasker/ui/`
- Test: `mobile/tasker/data/src/test/kotlin/com/matejdro/catapult/tasker/TaskerActionRunnerTest.kt`

- [x] **Step 1: Write failing Tasker action tests**

Test list input parsing, result variable names, successful selection, confirmation rejection, cancellation, timeout, and connection failure. Assert that only a successful result calls `signalFinish` with success.

- [x] **Step 2: Add explicit action and bundle keys**

Add separate `SHOW_LIST` and `SHOW_CONFIRMATION` actions. Serialize list entries using a documented delimiter-safe format or JSON already supported by the module; reject malformed entries and blank IDs before starting a session.

- [x] **Step 3: Map session results to Tasker**

Return `%catapult_status` as `success`, `cancelled`, `timeout`, or `failed`; populate `%catapult_result_id` and `%catapult_result_value` only for a selection; and signal Tasker failure for every non-success result with a useful `%errmsg`.

- [x] **Step 4: Add configuration UI**

Provide fields for title, list payload or confirmation message, and timeout. Preserve the existing configuration-activity save/restore pattern and make the new action discoverable in Tasker.

- [x] **Step 5: Run Tasker tests**

Run: `cd mobile && ./gradlew :tasker:data:test :tasker:ui:test`

Expected: PASS.

- [x] **Step 6: Commit the Tasker surface**

```bash
git add mobile/tasker
git commit -m "feat(tasker): expose interactive watch actions"
```

### Task 7: Validate end-to-end behavior and compatibility

**Files:**
- Modify: `README.MD` with a short Tasker workflow example.
- Modify: `protocol.md` if integration testing reveals an omitted field or limit.
- Test: existing mobile and watch test suites.

- [x] **Step 1: Run focused automated validation**

Run:

```bash
cd mobile
./gradlew :tasker:api:test :tasker:data:test :tasker:ui:test :bluetooth:data:test
cd ../watch
pebble clean && pebble build
```

Expected: all selected Gradle tests pass and all four Pebble platforms build.

- [x] **Step 2: Run the full existing mobile test suite**

Run: `cd mobile && ./gradlew test`

Expected: BUILD SUCCESSFUL with no regressions in bucket synchronization or existing Tasker actions.

- [x] **Step 3: Perform the manual CSV location workflow** (manual, requires physical watch — not run in this pass)

Create a Tasker action that supplies several CSV-backed locations, start it while Catapult is connected, select one location on the Pebble, and verify `%catapult_result_id`, `%catapult_result_value`, and `%catapult_status=success`. Confirm that a follow-up Tasker action opens Google Maps with the selected coordinates.

- [x] **Step 4: Exercise failure paths** (manual, requires physical watch — not run in this pass)

Repeat with Back/cancel, a disconnected watch, an expired timeout, a stale response, and an oversized list. Verify each produces a non-success status and never launches the map action.

- [x] **Step 5: Document the workflow and commit**

Add the Tasker variable contract and CSV/Google Maps example to `README.MD`, then commit:

```bash
git add README.MD protocol.md
git commit -m "docs: document interactive Tasker workflows"
```

## Final verification checklist

- [x] Interactive protocol is versioned and documented.
- [x] Existing bucket synchronization and packet IDs 0–4 are unchanged.
- [x] Only one session can be active, with stale/duplicate responses harmless.
- [x] List selection and confirmation work without voice input.
- [x] Cancellation, timeout, malformed input, and connection loss are explicit failures.
- [x] Tasker receives documented result variables.
- [x] Aplite, basalt, diorite, and emery PBWs build successfully.
- [x] Full mobile tests pass.
