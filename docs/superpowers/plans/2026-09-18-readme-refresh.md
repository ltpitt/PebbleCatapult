# README Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite the root README into a modern, concise landing page that helps a new Pebble + Tasker user install Catapult from this fork's GitHub preview release and understand its current AutoPebble/Pebble Tasker parity goal.

**Architecture:** Keep the implementation documentation-only and limited to `README.MD`. Use the existing demo GIF, link directly to the rolling `debug-latest` GitHub release for both the Android APK and Pebble PBW, and link deeper capability/developer details to the existing parity, release, and contribution documents.

**Tech Stack:** GitHub-flavored Markdown, existing `docs/demo.gif` media, GitHub Releases, and shell-based link/content validation.

---

## File map

- **Modify:** `README.MD` — the complete public landing page and installation guide.
- **Reference:** `docs/superpowers/specs/2026-09-15-readme-refresh-design.md` — approved content, audience, and scope.
- **Reference:** `docs/superpowers/reference/autopebble-parity-matrix.md` — source of truth for built, planned, and dropped capabilities.
- **Reference:** `RELEASING.md` — confirms the `debug-latest` preview semantics and APK/PBW release assets.
- **Reference:** `CONTRIBUTING.MD` — destination for contributor guidance.
- **Reference:** `docs/demo.gif` — existing hero visual that must remain in use.
- **Tests:** None; this is a documentation-only change. Validation uses Markdown inspection, repository checks, and live GitHub release/link checks.

### Task 1: Rewriting the README landing page

**Files:**
- Modify: `README.MD`

- [ ] **Step 1: Replace the stale README with the approved landing-page content**

Replace the entire contents of `README.MD` with the following. Keep the repository-relative asset and documentation links exactly as shown, and keep the release URL pointed at this fork:

```markdown
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
   - `catapult-mobile.apk` — the Android app.
   - `catapult-watchapp.pbw` — the Pebble watchapp.
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
```

- [ ] **Step 2: Check the documentation diff for formatting errors**

Run from the repository root:

```bash
git diff --check
git diff -- README.MD
```

Expected result: `git diff --check` produces no output, and the diff contains
only the intended README rewrite with no changes to source code, release files,
or image assets.

- [ ] **Step 3: Confirm the local assets and required documentation targets exist**

Run:

```bash
test -f docs/demo.gif
test -f docs/superpowers/reference/autopebble-parity-matrix.md
test -f RELEASING.md
test -f CONTRIBUTING.MD
```

Expected result: all commands exit successfully.

### Task 2: Validating README claims and links

**Files:**
- Test: `README.MD` and the linked release/documentation targets

- [ ] **Step 1: Verify removed installation providers and stale release links are absent**

Run:

```bash
! rg -n 'Obtainium|Rebble|matejdro/PebbleCatapult/releases|../../releases' README.MD
```

Expected result: the command exits successfully without matching lines. The
upstream repository may remain in the attribution link, but release/download
links must point to `ltpitt/PebbleCatapult`.

- [ ] **Step 2: Verify the README contains the approved user-facing contracts**

Run:

```bash
rg -n \
  'community fork|AutoPebble|Pebble Tasker|debug-latest|catapult-mobile\.apk|catapult-watchapp\.pbw|Pebble Time Round|%catapult_status|%catapult_result_id|%catapult_result_value|CONTRIBUTING\.MD' \
  README.MD
```

Expected result: each required concept has at least one matching line, including
the upstream credit, parity goal, rolling preview label, both release assets,
Round support, all three Tasker result variables, and the contribution link.

- [ ] **Step 3: Verify the rolling preview release exposes both documented assets**

Run:

```bash
gh release view debug-latest \
  --repo ltpitt/PebbleCatapult \
  --json isPrerelease,assets \
  --jq '{isPrerelease, assets: [.assets[].name]}'
```

Expected result: `isPrerelease` is `true`, and the asset list contains both
`catapult-mobile.apk` and `catapult-watchapp.pbw`.

- [ ] **Step 4: Verify the external release and attribution URLs respond**

Run:

```bash
curl -fsSI https://github.com/ltpitt/PebbleCatapult/releases/tag/debug-latest >/dev/null
curl -fsSI https://github.com/matejdro/PebbleCatapult >/dev/null
curl -fsSI https://github.com/matejdro/microPebble >/dev/null
curl -fsSI https://tasker.joaoapps.com/ >/dev/null
```

Expected result: all four commands exit successfully.

### Task 3: Committing the README refresh

**Files:**
- Modify: `README.MD`

- [ ] **Step 1: Review the final diff against the approved design**

Run:

```bash
git diff --stat
git diff -- README.MD
git status --short
```

Expected result: only `README.MD` is modified, and the final content covers the
approved hero, upstream credit, parity goal, GitHub-only preview install flow,
current capability groups, interactive results, and project links.

- [ ] **Step 2: Commit the documentation change**

Run:

```bash
git add README.MD
git commit -m "docs: refresh README for new users" \
  -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

Expected result: a commit is created with only the README refresh staged.
