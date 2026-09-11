# Android UI Guidance

## Tasker configuration screens

Tasker configuration screens use a consistent, readable form layout:

- Start with a fixed screen heading that identifies the configuration screen.
- Keep controls in one vertical form with 16dp outer/safe-area padding.
- Use 12dp vertical spacing between controls.
- Make fields and the primary action full width.
- Give every field an explicit, human-readable label.
- Use valid, concrete example defaults for new actions; preserve saved values
  when editing existing actions.
- Use an input-appropriate keyboard and show validation errors visibly near the
  relevant controls.
- Keep saved bundle keys and result-variable contracts stable when changing
  presentation.

This guidance follows Jetpack Compose Material and accessibility principles:
clear hierarchy, predictable spacing, labeled controls, scalable content, and
visible semantics. Apply it to new screens and when substantially revising an
existing screen.
