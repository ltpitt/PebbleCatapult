# Pebble Time Round Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Pebble Time Round (`chalk`) target to Catapult and launch the built watchapp in the local Round emulator for user testing.

**Architecture:** Keep the change at the watchapp packaging boundary: declare `chalk` alongside the existing Pebble targets in `watch/package.json`. Reuse the repository's build helper to produce the watchapp package, then use the Pebble CLI's `--emulator chalk` install path to launch it without changing UI or protocol code.

**Tech Stack:** Pebble SDK 3 watchapp, Pebble Tool v5.0.16, JSON manifest, shell build/install scripts.

---

## File Structure

- Modify: `watch/package.json:15-20` - declare the `chalk` target platform.
- Use: `scripts/build-watchapp.sh` - run the existing watchapp build and normalize the generated artifact name.
- Generate (ignored): `watch/build/catapult-watchapp.pbw` - installable multi-platform watchapp package used by the emulator.
- No test file - this is a manifest-only target addition; validation uses manifest parsing, the existing build, and the emulator launch.
- No documentation file - the approved design found no existing platform support list to update.

### Task 1: Declaring the Pebble Time Round target

**Files:**
- Modify: `watch/package.json:15-20`

- [ ] **Step 1: Add `chalk` to the target platform list**

Insert `"chalk"` after `"basalt"` so the target list reads:

```json
"targetPlatforms": [
  "aplite",
  "basalt",
  "chalk",
  "diorite",
  "emery"
]
```

- [ ] **Step 2: Verify the manifest parses and declares `chalk`**

Run from the repository root:

```bash
node -e "const p=require('./watch/package.json'); if (!p.pebble.targetPlatforms.includes('chalk')) process.exit(1); console.log('chalk target declared')"
```

Expected output:

```text
chalk target declared
```

- [ ] **Step 3: Commit the manifest change**

```bash
git add watch/package.json
git commit -m "feat(watch): add Pebble Time Round target" \
  -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 2: Building the watchapp with the new target

**Files:**
- Use: `scripts/build-watchapp.sh`
- Generate (ignored): `watch/build/catapult-watchapp.pbw`

- [ ] **Step 1: Build the watchapp using the repository helper**

Run from the repository root:

```bash
./scripts/build-watchapp.sh
```

Expected result:

```text
Watchapp: watch/build/catapult-watchapp.pbw
```

The command must exit with status 0 and leave `watch/build/catapult-watchapp.pbw`
available for installation. A compiler or SDK error is a blocking failure and
must be reported without changing UI code.

- [ ] **Step 2: Confirm the generated package exists**

```bash
test -s watch/build/catapult-watchapp.pbw
```

Expected result: the command exits with status 0 and prints no output.

### Task 3: Launching Catapult in the Pebble Time Round emulator

**Files:**
- Use: `watch/build/catapult-watchapp.pbw`

- [ ] **Step 1: Install and start the app on the `chalk` emulator**

Run from the repository root:

```bash
pebble install --emulator chalk watch/build/catapult-watchapp.pbw
```

`pebble install` launches the selected emulator and starts Catapult after the
package is installed. The command must complete without an install or emulator
connection error and leave the app visible on the Round emulator.

- [ ] **Step 2: Hand off manual UI testing**

Once the emulator is showing Catapult, manually exercise the main action list,
navigation into a subfolder, and the interactive list/confirmation screens. Do
not make layout changes in this pass; record any visual clipping, overflow, or
interaction issue as a follow-up round-device fix.

