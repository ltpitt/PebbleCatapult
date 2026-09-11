# Tasker Choice Prompt Design

## Goal

Allow a Tasker workflow to pause at a decision, present a bounded set of
choices on the Pebble, and resume with the user's selected option. The first
slice is intentionally limited to one-of-N choice prompts. Confirmation and
precompiled replies are represented as ordinary choices.

## Interaction contract

Tasker supplies a title and a non-empty list of options. Every option has a
stable opaque `id` and a display `value`. The action blocks until one of these
outcomes occurs:

- `success`: the watch selected an option; expose its ID and value.
- `cancelled`: the user pressed back or the session was explicitly cancelled.
- `timeout`: no terminal response arrived before the configured deadline.
- `failed`: the prompt could not be delivered or a response was invalid.

The existing Tasker variables remain the result surface:
`%catapult_status`, `%catapult_result_id`, and `%catapult_result_value`.
Non-success outcomes must never populate a success-shaped result.

## Architecture and data flow

The Tasker action validates and converts its configuration into the existing
`InteractiveTaskerRequest.List` model. `InteractiveSessionManager` owns one
active session, assigns a session ID, selects a connected watch sender, and
waits with a bounded timeout.

The Bluetooth layer serializes the request using the existing chunked
`InteractiveWatchMessage` protocol. The watch reassembles and renders the
choice prompt, then returns the selected stable ID/value. The phone validates
the session ID, watch identity, and that the returned pair belongs to the
original option list before completing the deferred result. Stale, duplicate,
or unsolicited responses are ignored.

Back/cancellation sends the existing cancel message where possible. Disconnect,
send failure, timeout, and malformed input resolve explicitly through the
corresponding result status.

## Scope boundaries

Multi-choice selection and phone-mediated voice/free-form input are later
extensions of the same session model. They are not part of the first
implementation or manual acceptance test. “Quick action” is not a separate
protocol type; a precompiled reply is simply a choice with a suitable label.

## Manual acceptance test

Configure a Tasker action with three options, trigger it, select the second
option on the watch, and verify that the workflow resumes with that option's
stable ID and value. Repeat with back and with an expired timeout; verify that
neither path reports success.

## Implementation constraints

Before implementation, inspect the closest official Pebble UI pattern for a
selectable list and preserve its native layer types, layout, typography,
spacing, and interaction structure. Inspect the official PebbleKit Android 2
APIs before changing phone-to-watch communication. The existing AppMessage
protocol is retained because it carries the interactive request/response
contract; no notification or timeline mechanism is appropriate for a blocking
Tasker decision.
