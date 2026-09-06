# Quick-Action Screens Design

## Goal

Provide a compact screen where Up, Select, and Down trigger distinct Tasker
actions, with optional long-press variants.

## Design

Add a `SHOW_QUICK_ACTIONS` request (packet 13) containing a title and up to
three bounded button descriptors. Each descriptor has a stable action ID and
display label, plus an optional `long_select` action ID. The watch renders the
labels and sends a `SCREEN_ACTION_RESULT` (packet 14) carrying the action ID and
gesture kind; `back` cancels. Empty button slots are disabled and are not
selectable.

The watch UI must begin from the closest matching example in the [official
Pebble UI patterns repository](https://github.com/pebble-examples/ui-patterns).
Its native layout, typography, colors, spacing, and interaction behavior lead
the implementation.

The phone exposes the same
[result contract](../reference/protocol-and-results.md#result-contract) as list
actions, including the button's action ID and gesture kind. Protocol version
negotiation prevents old watches from receiving this request. Packet IDs and the
session model come from
[`reference/protocol-and-results.md`](../reference/protocol-and-results.md);
lineage is in the [parity matrix](../reference/autopebble-parity-matrix.md).

## Failure handling and testing

Validate button count, labels, IDs, duplicate IDs, and UTF-8 byte limits before
sending. Test each button, `long_select`, empty slots, cancellation, timeout,
stale sessions, and unsupported protocol versions.
