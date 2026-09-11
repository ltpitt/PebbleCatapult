# Tasker Configuration Form Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve the Show List Tasker configuration screen with clear hierarchy, consistent spacing, full-width controls, and valid example defaults.

**Architecture:** Keep the existing `InteractiveListScreen` navigation entry and saved Bundle contract. Extract the composable form content into a focused function so state/default selection stays in the screen while layout and labels are easy to test and reuse for future Tasker forms.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android Bundle, Kotlin serialization, JUnit/Kotest.

---

## File map

- Modify `mobile/tasker/ui/src/main/kotlin/com/matejdro/catapult/tasker/ui/screens/interactive/InteractiveScreens.kt`: initialize new-action defaults, preserve existing values, and render the fixed heading/full-width spaced form.
- Modify `mobile/tasker/ui/src/test/kotlin/com/matejdro/catapult/tasker/ui/screens/interactive/InteractiveScreensTest.kt`: verify the default JSON and timeout contract if the UI module test setup supports screen-content tests; otherwise test the extracted pure default helpers.
- Existing `mobile/AGENTS.md`: repository-wide Android Tasker form rules already document the layout invariant and must remain aligned with the implementation.

### Task 1: Add deterministic default helpers and tests

**Files:**
- Modify: `mobile/tasker/ui/src/main/kotlin/com/matejdro/catapult/tasker/ui/screens/interactive/InteractiveScreens.kt`
- Create or modify: `mobile/tasker/ui/src/test/kotlin/com/matejdro/catapult/tasker/ui/screens/interactive/InteractiveScreensTest.kt`

- [ ] **Step 1: Add failing tests for new-action defaults**

Test the pure helper behavior:

```kotlin
@Test
fun `new list configuration uses location examples`() {
   listTitle(Bundle()) shouldBe "Choose a location"
   listItems(Bundle()) shouldBe
      """[{"id":"home","value":"Home"},{"id":"work","value":"Work"},{"id":"other","value":"Other"}]"""
   listTimeout(Bundle()) shouldBe 60_000L
}

@Test
fun `existing list configuration preserves saved values`() {
   val existing = Bundle().apply {
      putString(BundleKeys.TITLE, "Choose a door")
      putString(BundleKeys.ITEMS, """[{"id":"front","value":"Front door"}]""")
      putLong(BundleKeys.TIMEOUT_MS, 15_000L)
   }
   listTitle(existing) shouldBe "Choose a door"
   listItems(existing) shouldBe """[{"id":"front","value":"Front door"}]"""
   listTimeout(existing) shouldBe 15_000L
}
```

Keep the helpers `internal` so tests verify the real default/preservation behavior without depending on Android screenshot infrastructure.

- [ ] **Step 2: Run the focused UI test and verify it fails**

Run:

```bash
./gradlew -x :buildSrc:commit-hooks :tasker:ui:test
```

Expected: the new helper tests fail because the helpers/defaults do not exist yet.

- [ ] **Step 3: Implement the default helpers**

Use constants for:

```kotlin
private const val DEFAULT_LIST_TITLE = "Choose a location"
private const val DEFAULT_LIST_ITEMS =
   """[{"id":"home","value":"Home"},{"id":"work","value":"Work"},{"id":"other","value":"Other"}]"""
private const val DEFAULT_TIMEOUT_MS = 60_000L

internal fun listTitle(existingData: Bundle): String =
   existingData.getString(BundleKeys.TITLE) ?: DEFAULT_LIST_TITLE

internal fun listItems(existingData: Bundle): String =
   existingData.getString(BundleKeys.ITEMS) ?: DEFAULT_LIST_ITEMS

internal fun listTimeout(existingData: Bundle): Long =
   existingData.getLong(BundleKeys.TIMEOUT_MS, DEFAULT_TIMEOUT_MS)
```

Use `?:` rather than `orEmpty()` so an explicitly saved empty string remains visible for validation instead of being silently replaced by an example.

- [ ] **Step 4: Re-run the focused UI test**

Run the same Gradle command. Expected: all helper tests pass.

- [ ] **Step 5: Commit the default behavior**

```bash
git add mobile/tasker/ui/src/main/kotlin/com/matejdro/catapult/tasker/ui/screens/interactive/InteractiveScreens.kt mobile/tasker/ui/src/test/kotlin/com/matejdro/catapult/tasker/ui/screens/interactive/InteractiveScreensTest.kt
git commit -m "feat(tasker): add Show List example defaults"
```

### Task 2: Implement the readable Show List layout

**Files:**
- Modify: `mobile/tasker/ui/src/main/kotlin/com/matejdro/catapult/tasker/ui/screens/interactive/InteractiveScreens.kt`

- [ ] **Step 1: Add the fixed heading and layout imports**

Use `Arrangement.spacedBy`, `fillMaxWidth`, `safeDrawingPadding`, `MaterialTheme`, and `KeyboardOptions` with `KeyboardType.Number`.

- [ ] **Step 2: Replace the unbounded Column with the shared form structure**

Render:

```kotlin
Column(
   Modifier
      .padding(16.dp)
      .safeDrawingPadding(),
   verticalArrangement = Arrangement.spacedBy(12.dp),
) {
   Text("Show list", style = MaterialTheme.typography.headlineSmall)
   OutlinedTextField(
      value = title,
      onValueChange = { title = it },
      label = { Text("Title") },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
   )
   OutlinedTextField(
      value = items,
      onValueChange = { items = it },
      label = { Text("Items (JSON array of id/value objects)") },
      modifier = Modifier.fillMaxWidth(),
      minLines = 4,
   )
   OutlinedTextField(
      value = timeout,
      onValueChange = { timeout = it },
      label = { Text("Timeout (milliseconds)") },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
   )
   Button(
      onClick = { /* retain existing saveConfiguration body */ },
      modifier = Modifier.fillMaxWidth(),
   ) {
      Text("Save")
   }
}
```

Preserve the existing bundle keys, action name, replacement variables, title
blurb, and timeout fallback when saving.

- [ ] **Step 3: Run UI tests and compile the UI module**

```bash
./gradlew -x :buildSrc:commit-hooks :tasker:ui:test :tasker:ui:compileDebugKotlin
```

Expected: tests pass and Kotlin compilation succeeds.

- [ ] **Step 4: Commit the layout change**

```bash
git add mobile/tasker/ui/src/main/kotlin/com/matejdro/catapult/tasker/ui/screens/interactive/InteractiveScreens.kt
git commit -m "fix(tasker): improve Show List configuration layout"
```

### Task 3: Build and install the test APK

**Files:**
- Artifact: `mobile/app/build/outputs/apk/debug/catapult-mobile.apk`

- [ ] **Step 1: Build the debug APK**

```bash
cd mobile
./gradlew -x :buildSrc:commit-hooks --no-daemon :app:assembleDebug
```

Expected: `catapult-mobile.apk` is generated. If the latest PebbleCommons revision is incompatible with the current branch, stop and report the exact compiler mismatch rather than changing the submodule pointer.

- [ ] **Step 2: Verify the connected device**

```bash
$ANDROID_HOME/platform-tools/adb devices
```

Expected: at least one device appears with status `device`.

- [ ] **Step 3: Install the APK**

```bash
$ANDROID_HOME/platform-tools/adb install -r mobile/app/build/outputs/apk/debug/catapult-mobile.apk
```

Expected: `Success`.

- [ ] **Step 4: Manually verify the form**

Open Tasker’s Catapult Show List configuration and verify:

- The fixed `Show list` heading is visible above the fields.
- New actions contain the location examples and `60000`.
- Fields have consistent spacing and use the available width.
- Timeout opens a numeric keyboard.
- Reopening a saved action preserves its customized values.
