# Watch Task Selection and Status Bar Design

## Goal

After a task is successfully selected in Catapult, keep the current action-list
window open. Move the status indicator icons to the left side of the status bar
while keeping the clock right-aligned.

## Design

The watch task-selection callback will preserve its existing success and failure
vibrations, but will stop calling `window_stack_pop_all(true)` after a successful
send. The current action-list window and menu selection therefore remain intact.
The phone/watch packet protocol and send-completion callback remain unchanged.

The status bar will retain its existing clock frame, indicator priority, update
callbacks, and right-side reserved-space calculation. Only the indicator draw
coordinates will change from the clock-adjacent position to the left edge of the
status-bar layer. Existing per-bitmap offsets will be preserved so the icons'
native sizing and vertical alignment do not change.

This follows the existing Pebble UI pattern of keeping persistent status content
in a dedicated top bar layer, adapted to Catapult's custom connection indicators.
Reference: https://github.com/pebble-examples/ui-patterns

## Error handling

Failed sends will continue to vibrate twice and leave the action list visible.
Successful sends will continue to vibrate once and now also leave the action
list visible. No communication or Tasker behavior changes are included.

## Validation

- Build the watchapp through the repository's existing watch build command.
- Run the existing focused mobile watch-connection test.
- Inspect the diff to confirm only watch task-window behavior and indicator
  coordinates changed.
