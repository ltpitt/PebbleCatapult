# Pebble Native Tests in CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing host-side Pebble C tests a required local and CI check without changing the shipped watchapp.

**Architecture:** Add one aggregate shell script under `watch/tests/` that invokes the existing test scripts. Expose that script through the repository Makefile and call the same script from pull-request and develop workflows, keeping native tests separate from the Pebble SDK build.

**Tech Stack:** POSIX shell, C11 host compiler, GNU Make, GitHub Actions, Pebble SDK.

---

## File map

- Create `watch/tests/run_all.sh`: deterministic entry point for every host-side Pebble test.
- Modify `Makefile`: add a `watch-test` target delegating to the aggregate script.
- Modify `.github/workflows/verifyPr.yaml`: run native watch tests in pull requests.
- Modify `.github/workflows/develop.yaml`: run native watch tests in the release pipeline.

### Task 1: Add the aggregate native test command

**Files:**
- Create: `watch/tests/run_all.sh`
- Test: `watch/tests/run_notification_lifecycle_test.sh`
- Test: `watch/tests/run_notification_packet_test.sh`

- [ ] **Step 1: Create the executable aggregate script**

Create `watch/tests/run_all.sh` with:

```sh
#!/bin/sh
set -eu

cd "$(dirname "$0")"

./run_notification_lifecycle_test.sh
./run_notification_packet_test.sh

echo "all native Pebble tests passed"
```

The script must use paths relative to its own directory so it works from the
repository root, from `watch`, and from a CI workflow's default directory.

- [ ] **Step 2: Mark the script executable**

Run:

```bash
chmod +x watch/tests/run_all.sh
```

- [ ] **Step 3: Run the aggregate command locally**

Run:

```bash
./watch/tests/run_all.sh
```

Expected output includes:

```text
notification lifecycle tests passed
notification packet decoder tests passed
all native Pebble tests passed
```

- [ ] **Step 4: Confirm no temporary binaries remain**

Run:

```bash
test ! -e watch/tests/.notification_lifecycle_test
test ! -e watch/tests/.notification_packet_test
```

Expected: both commands exit successfully without output.

- [ ] **Step 5: Commit the aggregate command**

```bash
git add watch/tests/run_all.sh
git commit -m "test(watch): add native test runner"
```

### Task 2: Add the local Make entry point

**Files:**
- Modify: `Makefile:5-6` for the `.PHONY` declaration and `Makefile` target section

- [ ] **Step 1: Add `watch-test` to `.PHONY`**

Extend the existing declaration so it includes `watch-test`:

```make
.PHONY: help build build-release watchapp watch-test test check install clean \
        quick-release release releases
```

- [ ] **Step 2: Add the target next to `watchapp`**

Add:

```make
watch-test: ## Run native Pebble host tests
	@watch/tests/run_all.sh
```

This target must not call `pebble build`; it only runs the host-side tests.

- [ ] **Step 3: Run the Make target**

Run:

```bash
make watch-test
```

Expected: the same three success lines from the aggregate script.

- [ ] **Step 4: Verify the help output**

Run:

```bash
make help | grep "watch-test"
```

Expected:

```text
  watch-test      Run native Pebble host tests
```

- [ ] **Step 5: Commit the local entry point**

```bash
git add Makefile
git commit -m "test(watch): expose native tests through make"
```

### Task 3: Run native watch tests in pull-request CI

**Files:**
- Modify: `.github/workflows/verifyPr.yaml` after the checkout/setup steps and before the Android build

- [ ] **Step 1: Add a dedicated native test step**

Add this step after checkout and before SDK-dependent build steps:

```yaml
      - name: Run native Pebble tests
        run: ./watch/tests/run_all.sh
```

The step must not depend on Android setup, the Pebble SDK, an emulator, or a
display server.

- [ ] **Step 2: Check workflow syntax and placement**

Run:

```bash
git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 3: Commit the pull-request workflow change**

```bash
git add .github/workflows/verifyPr.yaml
git commit -m "ci: run native Pebble tests on pull requests"
```

### Task 4: Run native watch tests in the develop pipeline

**Files:**
- Modify: `.github/workflows/develop.yaml` after checkout and before SDK-dependent build steps

- [ ] **Step 1: Add the same native test step**

Add:

```yaml
      - name: Run native Pebble tests
        run: ./watch/tests/run_all.sh
```

Use the same command as the pull-request workflow so local, PR, and develop
validation cannot diverge.

- [ ] **Step 2: Validate the complete local behavior**

Run:

```bash
make watch-test
make watchapp
git diff --check
```

Expected: native tests pass, the existing Pebble build succeeds, and the diff
check produces no output. The new test runner must not add files under
`watch/build/` or change the generated `.pbw`.

- [ ] **Step 3: Commit the develop workflow change**

```bash
git add .github/workflows/develop.yaml
git commit -m "ci: run native Pebble tests before releases"
```

### Task 5: Final verification

- [ ] **Step 1: Run the shared test command**

```bash
./watch/tests/run_all.sh
```

Expected: all native Pebble tests pass.

- [ ] **Step 2: Inspect the final change set**

```bash
git --no-pager diff HEAD~4..HEAD --check
git status --short
```

Expected: no whitespace errors. Any pre-existing unrelated worktree changes,
including the `PebbleCommons` submodule state, must remain untouched.
