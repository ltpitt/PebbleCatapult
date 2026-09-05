# Detail and Text Screens Design

## Goal

Show a scrollable detail screen on the Pebble before the user commits an
action.

## Design

Add a bounded `SHOW_TEXT` request (packet 12) containing a title, body, and
optional action labels. The phone chunks and validates UTF-8 text using the same
session protocol rules as lists. The watch renders a native scrollable text
window; `select` invokes the primary action, `back` cancels, and an optional
secondary action is available through `long_select`. A text body may include an
optional live-clock token that the watch renders as the current time and
refreshes each minute.

The watch returns the selected action and session result through the shared
[result contract](../reference/protocol-and-results.md#result-contract),
including `%catapult_result_action`. Packet IDs and the session model come from
[`reference/protocol-and-results.md`](../reference/protocol-and-results.md);
lineage is in the [parity matrix](../reference/autopebble-parity-matrix.md). The
screen is independent of Tasker and can be reused by future callers.

## Failure handling and testing

Reject text exceeding protocol limits, incomplete chunks, malformed terminal
markers, and unsupported requests. Test encoding, reassembly, scrolling
boundaries, selection, `long_select`, cancellation, live-clock refresh, timeout,
and stale responses on both phone and watch.
