# Quick-Action Screens Design

## Goal

Provide a compact screen where Up, Select, and Down trigger distinct Tasker
actions, with optional long-press variants.

## Design

Add a `SHOW_QUICK_ACTIONS` request containing a title and up to three bounded
button descriptors. Each descriptor has a stable action ID and display label,
plus an optional long-press action ID. The watch renders the labels and sends a
typed action result; Back cancels. Empty button slots are disabled and are not
selectable.

The phone exposes the same result variables as list actions, including the
button position and action kind. The protocol version negotiation prevents old
watches from receiving this request.

## Failure handling and testing

Validate button count, labels, IDs, duplicate IDs, and UTF-8 byte limits before
sending. Test each button, long press, empty slots, cancellation, timeout,
stale sessions, and unsupported protocol versions.
