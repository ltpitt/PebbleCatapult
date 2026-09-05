# Action IDs and Fallbacks Design

## Goal

Make interactive actions stable for Tasker automations while keeping display
labels editable.

## Design

Use opaque, non-empty action IDs for automation and separate human-readable
labels for the watch. IDs are returned unchanged; labels are never used as
identifiers. This spec owns the shared
[result contract](../reference/protocol-and-results.md#result-contract) —
`success`, `cancelled`, `timeout`, and `failed`, with an optional failure
reason, plus `%catapult_result_action` for the gesture kind.

When a configured secondary (`long_select`) action is absent, the watch falls
back to the primary action only where the request explicitly permits fallback.
Otherwise it returns a distinct `failed` result. The contract is shared by
lists, detail screens, and quick actions.

## Failure handling and testing

Reject blank, duplicate, or oversized IDs and labels. Test label changes,
duplicate IDs, missing actions, explicit fallback, result-variable mapping,
and compatibility with existing list and confirmation configurations.
