# Catapult for Pebble

A watchapp for PebbleOS watches for interacting with the [Tasker](https://tasker.joaoapps.com/).

Catapult is an AutoPebble-compatible successor. Its relationship to the original
AutoPebble app — what is implemented, planned, or intentionally dropped — is
tracked in the [AutoPebble parity matrix](docs/superpowers/reference/autopebble-parity-matrix.md).

![](docs/demo.gif)

# Install

**Note: the old Pebble app is not supported. You have to use either the microPebble or the new Pebble/Core app.**

## Android app

[<img src="docs/badge_obtainium.png" alt="Get it on Obtainium">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/matejdro/PebbleCatapult)
[<img src="docs/badge_github.png" alt="Get it on GitHub">](../../releases/latest)

## Watchapp

[<img src="docs/badge_rebble.svg" alt="Get it on Rebble Store">](https://apps.rebble.io/en_US/application/69501540da1501000944b086)
[<img src="docs/badge_github.png" alt="Get it on GitHub">](../../releases/latest)

# Features

#### Launch Tasker tasks from the watch
Launch your favourite tasks directly from your watch

#### Subfolders
Organise your tasks however you want

#### Faaast
List of tasks is cached on the watch, so you don't need to wait for the app to load, you can browse and trigger your tasks immediately

#### Dynamically show/hide actions
You can use Tasker to dynamically show/hide actions from the watch. For example, hide all of your home automation actions when you are not home.

#### Voice input
You can input text into tasks via watch's microphone

#### Timeline pin creation
"Launch" timeline pins from Tasker onto the watch

#### Interactive Tasker sessions
Show a list or a confirmation dialog on the watch and let Tasker act on the
answer. Only one interactive session can be active at a time; starting a new
one cancels any previous one.

The action's result is returned to Tasker as these variables:

| Variable | Meaning |
| --- | --- |
| `%catapult_status` | `success`, `failed`, `cancelled`, or `timeout` |
| `%catapult_result_id` | Selected item's ID (list selection only) |
| `%catapult_result_value` | Selected item's value (list selection only) |

Example: an "Ask for a location" task supplies a CSV of saved locations as a
list, the user picks one on the watch, and a follow-up "If `%catapult_status`
~ `success`" task opens Google Maps with
`geo:0,0?q=%catapult_result_value(%catapult_result_id)`.

# Contributing

See [CONTRIBUTING](CONTRIBUTING.MD)
