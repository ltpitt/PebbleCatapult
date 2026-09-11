# Tasker Configuration Form Design

## Goal

Make Tasker configuration screens readable, consistent, and easy to configure
correctly. The first application is the Show List screen.

## Show List layout

The screen uses a fixed `Show list` heading at the top, followed by one
full-width vertical form:

1. `Title`, defaulting to `Choose a location` for a new action.
2. `Items`, defaulting to a valid JSON array containing Home, Work, and Other.
3. `Timeout (milliseconds)`, defaulting to `60000`.
4. A full-width `Save` button.

The form uses 16dp outer/safe-area padding and 12dp vertical spacing between
controls. The timeout field uses a numeric keyboard. Existing Tasker actions
load their saved values unchanged; examples are only new-action defaults.

## Shared screen principles

Future Android Tasker configuration screens should follow the same hierarchy:
a fixed screen heading, a single readable vertical form, full-width controls,
consistent spacing, explicit labels, visible validation, and a clear primary
button. Defaults must be valid examples that users can customise, not merely
syntactically ambiguous placeholders.

These rules apply to Android Tasker configuration UI only. Pebble watch UI
continues to follow the repository's separate official Pebble UI-pattern rule.

## Android guidance and Catapult-specific decisions

The layout follows Jetpack Compose Material guidance by using labeled Material
controls, predictable spacing, readable hierarchy, scalable text, and visible
error semantics. Catapult adds a fixed screen heading because Tasker opens these
screens as configuration destinations and the editable prompt title is user
data, not screen context.

## Validation and compatibility

The saved bundle keys, `SHOW_LIST` action, JSON option schema, timeout semantics,
and Tasker result variables remain unchanged. The screen must preserve existing
values when editing an action and must continue saving the same bundle shape.
