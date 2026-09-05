# Interactive Tasker Sessions Design

## Goal

Extend Catapult's existing Tasker integration so a Tasker action can start an
interactive phone/watch session, receive a watch selection or confirmation,
and continue with the result. A representative workflow is selecting a
location from a phone-side CSV list and then opening that location in Google
Maps.

Voice input is explicitly deferred until it can be added without compromising
the Pebble UI or interaction quality.

The shared session model, packet IDs, gesture vocabulary, and Tasker result
contract used throughout this spec are defined in
[`reference/protocol-and-results.md`](../reference/protocol-and-results.md).
How this feature fits Catapult's AutoPebble lineage is tracked in the
[parity matrix](../reference/autopebble-parity-matrix.md).

## Architecture

The feature extends the existing versioned AppMessage protocol rather than
creating a separate companion app or encoding transient interactions in
buckets.

- **Tasker plugin action** starts a session with a request type, title, and
  payload, waits asynchronously, and exposes the result variables defined in the
  [result contract](../reference/protocol-and-results.md#result-contract),
  including `%catapult_result_id`, `%catapult_result_value`,
  `%catapult_result_action`, and `%catapult_status`.
- **Interactive session manager** owns one active session, assigns a session
  ID, validates responses, tracks timeout/cancellation, and resumes the
  waiting Tasker action.
- **Watchapp connection** adds request/response packets and transparent
  chunking/reassembly. Existing bucket synchronization remains unchanged.
- **Watch UI** provides reusable screen variants (list and confirmation today).
  It reports selection, cancellation, or errors without knowing about Tasker.
- **Tasker result adapter** maps session outcomes to Tasker variables and
  completion status. Cancelled, expired, and failed sessions must not be
  reported as successful.

A session owns a bounded screen stack; list and confirmation are the first two
screen variants. A screen either completes the session with a terminal result
or pushes/pops within it, as described in the
[session model](../reference/protocol-and-results.md#session-model) and the
[navigation stacks design](2026-09-04-navigation-stacks-design.md). Only one
interactive session is active at a time. Session IDs make stale and duplicate
responses harmless.

## Protocol

Phone-to-watch messages add:

- `SHOW_LIST`: session ID, title, item count, and chunked item records.
- `SHOW_CONFIRMATION`: session ID, title, and confirmation text.
- `CANCEL`: session ID and reason.

Watch-to-phone messages add:

- `LIST_SELECTION`: session ID, selected item ID, and selected value.
- `CONFIRMATION_RESULT`: session ID and accepted/rejected state.
- `CANCEL_OR_ERROR`: session ID and reason.

The protocol reserves an input-mode field for a future prompt type, but
version one does not expose voice or arbitrary text entry. Payload sizes and
item counts are bounded by Pebble AppMessage and screen constraints.

## User experience

Version one supports:

- titled dynamic lists with selectable rows;
- simple Yes/No or OK/Cancel confirmations;
- watch-side cancellation;
- phone-side timeout and controlled failure reporting.

The phone remains responsible for interpreting results and launching external
apps. Catapult does not directly implement CSV storage or Google Maps
behavior; those are Tasker workflow concerns.

## Error handling

The phone rejects malformed, oversized, unknown, stale, or duplicate messages.
The watch reports inability to render or complete a request explicitly.
Timeout, cancellation, protocol mismatch, connection loss, and Tasker failure
each produce distinct non-success statuses where the Tasker API permits it.
No failure path may silently complete the Tasker action as successful.

## Testing

Automated tests should cover:

- packet encoding and decoding;
- chunking and reassembly;
- session IDs and stale/duplicate responses;
- successful list selection and confirmation;
- cancellation and timeout;
- Tasker result variables and failure statuses;
- unchanged bucket synchronization.

Manual integration testing should use a CSV-backed location list, select a
location on a Pebble, verify the coordinates reach Tasker, and verify that
Tasker can launch a map intent with the selected coordinates.

## Out of scope

- voice input;
- arbitrary text entry on the watch;
- replacing bucket synchronization;
- direct CSV or Google Maps integrations inside Catapult;
- multiple simultaneous interactive sessions.
