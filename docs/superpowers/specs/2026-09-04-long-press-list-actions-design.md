# Long-Press List Actions Design

## Goal

Allow a Tasker list row to expose a secondary long-press action while preserving
the existing tap-to-select behavior.

## Design

Extend each list item with an optional `long_select` action identifier and
return the selected item ID, value, and gesture kind. The phone sends the action
metadata in the existing bounded, chunked list protocol, extending
`LIST_SELECTION` with a gesture-kind field rather than adding a packet (see the
[packet registry](../reference/protocol-and-results.md#packet-id-allocation)).
The watch displays the same list and maps a normal press to `select` and a long
press to `long_select`; `back` remains cancellation.

Tasker receives the variables defined in the
[result contract](../reference/protocol-and-results.md#result-contract):
`%catapult_status`, `%catapult_result_id`, `%catapult_result_value`, and
`%catapult_result_action`. Missing `long_select` metadata falls back to normal
selection, so existing configurations remain valid.

## Failure handling and testing

Reject invalid or oversized action IDs before transmission. Unknown session
IDs, duplicate responses, timeout, cancellation, and unsupported protocol
versions must not complete the Tasker action successfully. Add request/response
serialization tests, watch callback tests, and a manual Tasker acceptance flow.
