# Interactive Navigation Stacks Design

## Goal

Support multi-screen interactive flows without requiring Tasker to start a new
session for every screen.

## Design

Keep one session ID while allowing the watch to push a new bounded screen
request and pop on Back. The phone-side session manager owns the screen stack
and validates that responses belong to the active session. A terminal result
completes the Tasker action; Back on the root screen returns cancellation.

Screen requests use typed variants for list, text, confirmation, and quick
actions. The watch owns rendering and navigation gestures, while the phone
owns the authoritative stack and can send an explicit reset/cancel request.

## Failure handling and testing

Cap stack depth and serialized screen size. Reject invalid transitions,
unknown screen types, stale responses, and stack overflow without silently
success-completing Tasker. Test push/pop order, root cancellation, reset,
timeouts, reconnects, and malformed transitions.
