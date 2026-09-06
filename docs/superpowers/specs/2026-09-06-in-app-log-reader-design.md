# In-App Log Reader Design

## Goal

Make troubleshooting after installing a new APK easier by allowing users to read,
copy, and share the logs written today, while preserving the existing ZIP export.
The first UI version is today-only, but the reader boundary will support a future
selected date.

## Scope

### Included

- Add a **Read today's logs** action to the existing Tools screen.
- Flush pending log entries before reading.
- Read all matching rolling log files for the selected date from the existing
  private log directory.
- Display the combined log in a read-only, vertically scrollable monospace view.
- Copy the complete text to the clipboard with a snackbar confirmation.
- Share the complete text through Android's `ACTION_SEND` chooser as `text/plain`.
- Show loading, empty, and error states using existing application patterns.
- Keep the existing ZIP export behavior unchanged.

### Deferred

- A date picker or an all-days log browser. The reading API accepts a date so
  this can be added without changing the storage format or screen architecture.

## Existing logging format

The app uses TinyLog rolling files in the private cache log directory. Files are
named `log_{date:yyyy-MM-dd_HH-mm-ss}.txt`, rotate daily and at 1 MB, and retain
ten backups. The reader will match the date portion of this filename and read
matching `.txt` files in filename order, which is chronological for this format.
Malformed or unrelated files are ignored.

## Architecture and data flow

The existing Tools ViewModel remains the orchestration point:

1. The Tools screen invokes a ViewModel action.
2. The ViewModel runs the operation on the existing background dispatcher.
3. The logging reader flushes writers, selects files for the requested date, and
   returns their combined text.
4. The ViewModel exposes loading, success, empty, and failure state.
5. The reader screen renders the state and invokes copy/share actions.

The file-selection and concatenation logic should be a small independently
testable unit. Its public input includes a `LocalDate` (or equivalent date
value) even though the initial caller always supplies the current date.

The existing ZIP generation, device-info file, and `FileProvider` URI flow are
not changed.

## User experience

The Tools grid gains a second clear action next to **Save logs**. Selecting it
opens a dedicated reader destination using the app's existing Material 3 theme
and navigation conventions.

The reader contains:

- A top-bar title, **Today's logs**.
- A read-only, selectable, vertically scrollable monospace text area.
- **Copy** and **Share** top-bar actions.
- A progress indicator while logs are flushed and loaded.
- An empty state explaining that no logs have been written today.
- The existing error presentation for read failures.

Copy places the complete text in the system clipboard and displays a snackbar
confirmation. Share sends the complete text as `text/plain` through the standard
Android chooser. No storage or notification permissions are required because
the logs remain in app-private storage.

## Error handling and edge cases

- Flush failures and file read failures are surfaced through the existing error
  state; they are not swallowed.
- No matching files produce the explicit empty state, not a successful blank
  document.
- Only valid, date-matching `.txt` files are included.
- Files are concatenated in deterministic filename order.
- The full text is available while the reader destination is open. No arbitrary
  truncation is introduced in the first version.

## Testing

Unit tests will cover:

- Selecting only files matching the requested date.
- Ignoring malformed, unrelated, ZIP, and device-info files.
- Ordering multiple rolling files chronologically.
- Returning an empty result when no matching files exist.
- Propagating file and flush failures.

UI/ViewModel tests will cover loading, success content, empty state, copy/share
inputs, and surfaced failures. Existing Android build and targeted test tasks
will be used for validation.

## Future extension

A future date picker can list dates represented by the same valid log filename
pattern and pass the selected date into the existing reader. The reader screen
title and empty-state copy should then be parameterized by date; no storage
migration is required.
