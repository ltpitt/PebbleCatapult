# Catapult for Pebble

<div align="center">

Control your Tasker actions from your Pebble watch.

[**Download the latest preview**](https://github.com/ltpitt/PebbleCatapult/releases/tag/debug-latest)
·
[**See the feature parity matrix**](docs/superpowers/reference/autopebble-parity-matrix.md)

</div>

![Catapult running on a Pebble watch](docs/demo.gif)

## What is Catapult?

Catapult connects [Tasker](https://tasker.joaoapps.com/) with PebbleOS watches,
so actions you already use on Android can be launched, organized, and answered
from your wrist.

This repository is a community fork of
[PebbleCatapult](https://github.com/matejdro/PebbleCatapult), originally created
and maintained by [@matejdro](https://github.com/matejdro). The foundation and
original work deserve real credit and appreciation.

The current goal of this fork is practical feature parity with AutoPebble /
Pebble Tasker. Progress is guided by user feedback, the maintainer's own needs,
and available time. It is an ambitious direction rather than a promise that
every historical feature will return. The
[AutoPebble parity matrix](docs/superpowers/reference/autopebble-parity-matrix.md)
shows what is built, planned, or intentionally not supported.

## Quick start

The current download is a rolling preview. It is convenient for trying the
latest fork, but it is not a stable release channel.

1. Open the [latest preview release](https://github.com/ltpitt/PebbleCatapult/releases/tag/debug-latest).
2. Download both assets:
   - `catapult-mobile.apk` - the Android app.
   - `catapult-watchapp.pbw` - the Pebble watchapp.
3. Install the APK on your Android phone.
4. Install the PBW through a supported Pebble companion app, then open Catapult
   and sync your Tasker actions.

The legacy Pebble app is not supported. Use
[microPebble](https://github.com/matejdro/microPebble) or the new
Pebble/Core app instead.

## What works today

Catapult currently supports:

- **Launch and organize:** launch Tasker actions from the watch, use nested
  folders, and browse cached actions even when the phone is not immediately
  available.
- **Dynamic action lists:** show or hide actions from Tasker based on the
  current context.
- **Input and notifications:** pass voice input from the watch to a Tasker
  action, create or delete timeline pins, and send watch notifications.
- **Interactive sessions:** show a list or confirmation dialog on the watch and
  return the user's answer to Tasker. Show List titles and items can use Tasker
  variable replacement.
- **Pebble Time Round:** the watchapp includes the `chalk` target alongside the
  other supported PebbleOS targets.

## Interactive Tasker results

Interactive actions return these local variables to Tasker:

| Variable | Meaning |
| --- | --- |
| `%catapult_status` | `success`, `failed`, `cancelled`, or `timeout` |
| `%catapult_result_id` | Selected item's ID (list selection only) |
| `%catapult_result_value` | Selected item's value (list selection only) |

For example, an "Ask for a location" task can show a CSV of saved locations,
then use a follow-up Tasker condition:

```text
If %catapult_status ~ success
    Open Google Maps:
    geo:0,0?q=%catapult_result_value(%catapult_result_id)
```

## Project links

- [Latest preview release](https://github.com/ltpitt/PebbleCatapult/releases/tag/debug-latest)
- [AutoPebble parity matrix](docs/superpowers/reference/autopebble-parity-matrix.md)
- [Release and build notes](RELEASING.md)
- [Contributing](CONTRIBUTING.MD)

## Contributing

Bug reports, feedback, and contributions are welcome. See the
[contribution guide](CONTRIBUTING.MD) for the project workflow.
