# Detail and Text Screens Design

## Goal

Show a scrollable detail screen on the Pebble before the user commits an
action.

## Design

Add a bounded `SHOW_TEXT` request containing a title, body, and optional action
labels. The phone chunks and validates UTF-8 text using the same session
protocol rules as lists. The watch renders a native scrollable text window;
Select invokes the primary action, Back cancels, and an optional secondary
action is available through long press.

Tasker receives the selected action and session result through the existing
status variables plus `%catapult_result_action`. The screen is independent of
Tasker and can be reused by future callers.

## Failure handling and testing

Reject text exceeding protocol limits, incomplete chunks, malformed terminal
markers, and unsupported requests. Test encoding, reassembly, scrolling
boundaries, selection, cancellation, timeout, and stale responses on both
phone and watch.
