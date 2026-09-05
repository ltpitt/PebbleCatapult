# Interactive Navigation Stacks Design

## Goal

Provide the umbrella session model for all interactive screens: support
multi-screen flows within a single session without requiring Tasker to start a
new session for every screen. List, confirmation, text, and quick-action
screens are all variants that live on this stack.

Shared session model, packet IDs, gesture vocabulary, and result contract are
defined in [`reference/protocol-and-results.md`](../reference/protocol-and-results.md).
Lineage is tracked in the [parity matrix](../reference/autopebble-parity-matrix.md).

## Design

Keep one session ID while allowing the phone to push a new bounded screen
request and the watch to pop on `back`. The phone-side session manager owns the
screen stack and validates that responses belong to the active session. A
terminal result completes the Tasker action; `back` on the root screen returns
cancellation, while `back` on a deeper screen pops one level
(`SCREEN_POP`, non-terminal).

Screen requests use typed variants for `list`, `text`, `confirmation`, and
`quick` actions. The watch owns rendering and navigation gestures, while the
phone owns the authoritative stack and can send an explicit reset/cancel
request.

### Custom back and long-back actions

Any screen may attach an optional action to its `back` gesture. When present,
the watch reports that action (using the result contract) before popping or
cancelling; when absent, `back` behaves as plain navigation. A separate
long-back action may be attached the same way, mapped to `long_select` on the
back button. Back actions never suppress cancellation semantics on the root
screen: the session still ends.

## Failure handling and testing

Cap stack depth and serialized screen size. Reject invalid transitions,
unknown screen types, stale responses, and stack overflow without silently
success-completing Tasker. Test push/pop order, root cancellation, reset,
back/long-back actions, timeouts, reconnects, and malformed transitions.
