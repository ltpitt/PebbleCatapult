# In-App Log Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a today-log reader to the Android Tools area so users can inspect, copy, or share logs without losing the existing ZIP export.

**Architecture:** Add a focused date-aware log reader in the tools UI module, keep `FileLoggingController` responsible for flushing and locating the log directory, and expose reader state from the existing Tools ViewModel. Add a dedicated navigation key/screen for a Material 3 reader with copy and text-share actions.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Kotlin coroutines/flows, Kotlinova navigation, Metro DI, JUnit 5.

---

## File map

- Create `mobile/common-navigation/src/main/kotlin/com/matejdro/catapult/navigation/keys/LogReaderScreenKey.kt` for the serializable navigation destination.
- Create `mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/LogReader.kt` for date-aware file matching and deterministic concatenation.
- Create `mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/LogReaderViewModel.kt` for loading today's content and exposing `Outcome` state.
- Create `mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/LogReaderScreen.kt` for the reader destination and Compose UI.
- Create `mobile/tools/ui/src/test/kotlin/com/matejdro/catapult/tools/ui/LogReaderTest.kt` for file-selection and concatenation behavior.
- Create `mobile/tools/ui/src/test/kotlin/com/matejdro/catapult/tools/ui/LogReaderViewModelTest.kt` for loading, empty, and failure states.
- Modify `mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/ToolsScreen.kt` to navigate to the reader action.
- Modify `mobile/tools/ui/src/main/res/values/strings.xml` with reader, empty-state, snackbar, and share labels.

### Task 1: Add the date-aware reader unit

**Files:**
- Create: `mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/LogReader.kt`
- Test: `mobile/tools/ui/src/test/kotlin/com/matejdro/catapult/tools/ui/LogReaderTest.kt`

- [ ] **Step 1: Write failing tests for matching, ordering, and empty results**

Create a temporary-directory test fixture with files named like the configured TinyLog pattern and unrelated files. Test the reader contract:

```kotlin
class LogReaderTest {
   @Test
   fun `reads only files for requested date in filename order`() {
      val folder = Files.createTempDirectory("log-reader-test").toFile()
      try {
         folder.resolve("log_2026-09-06_12-00-00.txt").writeText("second")
         folder.resolve("log_2026-09-06_08-00-00.txt").writeText("first")
         folder.resolve("log_2026-09-05_23-59-00.txt").writeText("yesterday")
         folder.resolve("device.txt").writeText("device")
         folder.resolve("logs.zip").writeText("zip")

         LogReader().read(folder, LocalDate.of(2026, 9, 6)) shouldBe "first\nsecond"
      } finally {
         folder.deleteRecursively()
      }
   }

   @Test
   fun `returns null when no valid file exists for requested date`() {
      val folder = Files.createTempDirectory("log-reader-test").toFile()
      try {
         folder.resolve("log_2026-09-05_08-00-00.txt").writeText("yesterday")

         LogReader().read(folder, LocalDate.of(2026, 9, 6)) shouldBe null
      } finally {
         folder.deleteRecursively()
      }
   }

   @Test
   fun `ignores malformed log names`() {
      val folder = Files.createTempDirectory("log-reader-test").toFile()
      try {
         folder.resolve("log_2026-09-06.txt").writeText("missing timestamp")
         folder.resolve("log_2026-09-06_08-00-00.log").writeText("wrong extension")

         LogReader().read(folder, LocalDate.of(2026, 9, 6)) shouldBe null
      } finally {
         folder.deleteRecursively()
      }
   }
}
```

Import `java.nio.file.Files` and use the repository’s existing JUnit 5 assertion convention.

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./mobile/gradlew -p mobile :tools:ui:testDebugUnitTest --tests com.matejdro.catapult.tools.ui.LogReaderTest
```

Expected: compilation/test failure because `LogReader` does not exist yet.

- [ ] **Step 3: Implement the minimal reader**

Implement a small class with this contract:

```kotlin
class LogReader {
   fun read(logFolder: File, date: LocalDate): String?
}
```

Use a strict regular expression for `log_yyyy-MM-dd_HH-mm-ss.txt`, parse the date from each filename, filter to the requested date, sort by filename, read each file as UTF-8, and join non-empty file contents with a single newline. Return `null` when no matching files exist. Do not catch `IOException`; let the caller surface it.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run the same Gradle test command. Expected: all `LogReaderTest` tests pass.

- [ ] **Step 5: Commit the reader unit**

```bash
git add mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/LogReader.kt mobile/tools/ui/src/test/kotlin/com/matejdro/catapult/tools/ui/LogReaderTest.kt
git commit -m "feat: add date-aware log reader" -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 2: Add reader navigation and ViewModel state

**Files:**
- Create: `mobile/common-navigation/src/main/kotlin/com/matejdro/catapult/navigation/keys/LogReaderScreenKey.kt`
- Create: `mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/LogReaderViewModel.kt`
- Create: `mobile/tools/ui/src/test/kotlin/com/matejdro/catapult/tools/ui/LogReaderViewModelTest.kt`

- [ ] **Step 1: Add the serializable destination key**

Create:

```kotlin
@Serializable
data object LogReaderScreenKey : BaseScreenKey()
```

Use the same package and imports as `ToolsScreenKey`.

- [ ] **Step 2: Add the dedicated reader ViewModel**

Create an `@Inject`/`@ContributesScopedService` `LogReaderViewModel` implementing `SingleScreenViewModel<LogReaderScreenKey>`. Inject `CoroutineResourceManager`, `ActionLogger`, and `FileLoggingController`; accept `today: () -> LocalDate = LocalDate::now` as a constructor parameter for deterministic tests; instantiate the stateless `LogReader` directly as a private property. Expose a `StateFlow<Outcome<String?>>` initialized with `Outcome.Success(null)`.

Add:

```kotlin
fun readTodayLogs() = resources.launchResourceControlTask(_logContent) {
   actionLogger.logAction { "LogReaderViewModel.readTodayLogs()" }

   val content = withDefault {
      fileLoggingController.flush()
     LogReader().read(fileLoggingController.getLogFolder(), today())
   }

   emit(Outcome.Success(content))
}

fun resetLogContent() {
   _logContent.value = Outcome.Success(null)
}
```

The ViewModel must not catch read or flush exceptions. Add tests with a fake `FileLoggingController` and a `TestScope`, following existing `testCoroutineResourceManager()` conventions:

```kotlin
@Test
fun `loads today's logs`() = scope.runTest {
  fakeController.logFolder.resolve("log_2026-09-06_08-00-00.txt").writeText("hello")
  viewModel.readTodayLogs()
  runCurrent()
  viewModel.logContent.value shouldBeSuccessWithData "hello"
}

@Test
fun `exposes empty result when no logs exist`() = scope.runTest {
  viewModel.readTodayLogs()
  runCurrent()
  viewModel.logContent.value shouldBeSuccessWithData null
}
```

Construct the production ViewModel with the default `LocalDate::now` and construct the test ViewModel with `{ LocalDate.of(2026, 9, 6) }`. Add a fake-controller test that makes `flush()` throw and assert the state is `Outcome.Error`; do not convert that error into an empty success.

- [ ] **Step 3: Run compilation and focused existing tests**

Run:

```bash
./mobile/gradlew -p mobile :tools:ui:testDebugUnitTest
```

Expected: the tools UI module compiles and its tests pass.

- [ ] **Step 4: Commit navigation and state wiring**

```bash
git add mobile/common-navigation/src/main/kotlin/com/matejdro/catapult/navigation/keys/LogReaderScreenKey.kt mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/LogReaderViewModel.kt mobile/tools/ui/src/test/kotlin/com/matejdro/catapult/tools/ui/LogReaderViewModelTest.kt
git commit -m "feat: expose log reader state" -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 3: Add the reader screen with copy and share

**Files:**
- Create: `mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/LogReaderScreen.kt`
- Modify: `mobile/tools/ui/src/main/res/values/strings.xml`

- [ ] **Step 1: Add all user-visible strings**

Add resources for `read_today_logs`, `today_logs`, `no_logs_today`, `copy_logs`, `share_logs`, `logs_copied`, and `share_logs_title`. Keep wording concise and sentence case, for example:

```xml
<string name="read_today_logs">Read today's logs</string>
<string name="today_logs">Today's logs</string>
<string name="no_logs_today">No logs have been written today.</string>
<string name="copy_logs">Copy logs</string>
<string name="share_logs">Share logs</string>
<string name="logs_copied">Logs copied</string>
<string name="share_logs_title">Share today's logs</string>
```

- [ ] **Step 2: Implement the injected navigation screen**

Create an `@InjectNavigationScreen`/`@ContributesScreenBinding` screen for `LogReaderScreenKey` that injects `Navigator` and `LogReaderViewModel`. Collect `viewModel.logContent`, trigger `readTodayLogs()` once when the destination is entered, and render loading, error, empty, and content states using existing `Outcome` and error components.

Use a `Scaffold` with `TopAppBar`, a back action from the injected `Navigator`, and two top-bar icon buttons. Render content with `SelectionContainer` around a `LazyColumn` or scrollable `Text` using a monospace `FontFamily`. The complete string must remain available for copy/share.

Copy should call `LocalClipboardManager.current.setText(AnnotatedString(content))` and show `logs_copied` in a `SnackbarHost`. Share should create:

```kotlin
Intent(Intent.ACTION_SEND).apply {
   type = "text/plain"
   putExtra(Intent.EXTRA_TEXT, content)
}
```

Launch it with `Intent.createChooser(intent, context.getString(R.string.share_logs_title))` from the Compose `LocalContext` activity context. Do not add URI permissions because this shares text.

- [ ] **Step 3: Run the module tests/build**

Run:

```bash
./mobile/gradlew -p mobile :tools:ui:testDebugUnitTest
```

Expected: the screen compiles and all tools UI tests pass.

- [ ] **Step 4: Commit the reader screen**

```bash
git add mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/LogReaderScreen.kt mobile/tools/ui/src/main/res/values/strings.xml
git commit -m "feat: add in-app log reader screen" -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 4: Add the Tools entry point

**Files:**
- Modify: `mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/ToolsScreen.kt`

- [ ] **Step 1: Add navigation and action state wiring**

Add `LogReaderScreenKey` navigation to the injected `Navigator`. Pass a callback from `ToolsScreenContent` that navigates to the reader destination. Keep ZIP share behavior and `resetLog()` unchanged.

- [ ] **Step 2: Add the second action to the grid**

Add a `ToolButton` using `R.drawable.logs`, `R.string.read_today_logs`, and the navigation callback. Keep the existing adaptive grid and spacing so the two log actions align with the permissions action on compact and expanded widths.

- [ ] **Step 3: Update the preview**

Pass the new callback to `ToolsScreenContent` in `ToolsScreenPreview`, leaving it a no-op.

- [ ] **Step 4: Run compilation and lint for the changed module**

Run:

```bash
./mobile/gradlew -p mobile :tools:ui:lintDebug :tools:ui:testDebugUnitTest
```

Expected: lint and unit tests pass.

- [ ] **Step 5: Commit the Tools entry point**

```bash
git add mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/ToolsScreen.kt
git commit -m "feat: expose log reader from tools" -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 5: Verify the integrated Android behavior

**Files:**
- Modify only if integration compilation identifies a concrete issue in the files above.

- [ ] **Step 1: Run the complete targeted mobile checks**

Run:

```bash
./mobile/gradlew -p mobile :tools:ui:testDebugUnitTest :tools:ui:lintDebug :app:assembleDebug
```

Expected: tests, lint, and debug APK assembly complete successfully.

- [ ] **Step 2: Inspect the final diff**

Run:

```bash
git diff HEAD~4..HEAD --check
git status --short
```

Expected: no whitespace errors and no untracked generated files. Confirm the ZIP action remains present, the reader action navigates to `LogReaderScreenKey`, and no permissions or dependency changes were introduced unnecessarily.

- [ ] **Step 3: Commit any necessary integration correction**

If the integrated build reveals a concrete issue in `LogReader.kt`, `LogReaderViewModel.kt`, `LogReaderScreen.kt`, `ToolsScreen.kt`, the navigation key, or the strings file, make the smallest correction, rerun the failing command, and commit it:

```bash
git add mobile/common-navigation/src/main/kotlin/com/matejdro/catapult/navigation/keys/LogReaderScreenKey.kt mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/LogReader.kt mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/LogReaderViewModel.kt mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/LogReaderScreen.kt mobile/tools/ui/src/main/kotlin/com/matejdro/catapult/tools/ui/ToolsScreen.kt mobile/tools/ui/src/main/res/values/strings.xml
git commit -m "fix: wire in-app log reader integration" -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```
