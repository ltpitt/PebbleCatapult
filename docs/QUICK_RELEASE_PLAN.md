# Plan: quick debug builds published as a GitHub Release

Status: **implemented** in `.github/workflows/quick-build.yaml`. This
document is kept as the design record and as a reference if the workflow
ever needs to be reconstructed or reviewed. The "Implementation steps" and
"Full resulting file" sections below describe what was actually applied.

## Problem

`quick-build.yaml` (added to fix the release pipeline in this fork) builds
`:app:assembleDebug` and the Pebble watchapp fast, with no
lint/tests/versioning. Its outputs are available as workflow **artifacts** and
as assets on the rolling `debug-latest` prerelease, which:

- Expires (default 90 days) and needs a GitHub login + the Actions UI to
  download.
- Isn't a stable link you can send to yourself or install via a release
  manager / QR code the way a Release asset is.

We want a quick build that is exactly as fast, but lands somewhere as
easy to grab as a real release, without being confused for one.

## Design

### Keep it clearly separate from proper releases

- Proper releases (`develop-build`) stay the only workflow that touches
  `version.txt` / `mobile/version.txt` / `watch/version.txt` /
  `watch/package.json`, runs lint/detekt/tests/screenshot tests, builds the
  watchapp, and tags semantic versions like `0.90`.
- Quick builds get their own tag namespace and are always marked
  **prerelease** in GitHub, so they never show up as "Latest release" and are
  visually distinct in the releases list.

### Tagging strategy

Use a single rolling tag, e.g. `debug-latest`, updated in place on every run
(`allowUpdates: true`, `removeArtifacts: true` in `ncipollo/release-action`,
same action already used by `develop-build`). Rationale:

- One predictable URL
  (`https://github.com/<owner>/<repo>/releases/tag/debug-latest`) to
  bookmark/share — no need to hunt for "the latest debug build".
- No tag/version-list clutter building up over time from every quick build.
- Explicitly **not** a substitute for reproducible/traceable releases — the
  release body will record the branch name and commit SHA it was built from,
  so it's still traceable to a specific commit even though the tag itself
  moves.

If we later want to keep a history of quick builds instead of a single rolling
one, switch to `debug-<run_number>-<short_sha>` and skip `allowUpdates`. Not
needed for the current use case (test on my phone right after pushing).

### Workflow changes (`.github/workflows/quick-build.yaml`)

The existing trigger, Android debug build, and APK artifact upload remain.
The workflow also installs the latest Pebble SDK, builds the watchapp, and
publishes both outputs. Use `ncipollo/release-action` pinned to
the **exact same commit SHA already used in this repo**
(`.github/workflows/develop.yaml` line ~262:
`339a81892b84b4eeb0f6e744e4574d79d0d9b8dd # v1.21.0`) — do not use a
different version or the `@v1` floating tag.

#### Implementation steps

1. Open `.github/workflows/quick-build.yaml`.
2. Add a `permissions:` block directly under `build-debug-apk:` (as a sibling
   of `runs-on:`), so the job looks like:

   ```yaml
   jobs:
     build-debug-apk:
       runs-on: "ubuntu-latest"
       permissions:
         contents: write
       steps:
   ```

3. Add the Pebble SDK setup and watchapp build before the artifact uploads.
4. At the very end of the `steps:` list, publish both outputs:

   ```yaml
         - name: Publish quick build as a prerelease
           uses: ncipollo/release-action@339a81892b84b4eeb0f6e744e4574d79d0d9b8dd # v1.21.0
           env:
             GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
           with:
             tag: debug-latest
             commit: ${{ github.sha }}
             name: "Quick debug build (${{ github.ref_name }} @ ${{ github.sha }})"
             body: |
               Debug-signed convenience build, **not** a tested release.
               Branch: `${{ github.ref_name }}`
               Commit: `${{ github.sha }}`
               Run: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}
             artifacts: "mobile/app/build/outputs/apk/debug/catapult-mobile.apk,watch/build/catapult-watchapp.pbw"
             prerelease: true
             allowUpdates: true
             removeArtifacts: true
             generateReleaseNotes: false
   ```

5. Save the file. Do not change any other workflow behavior.

The manual dispatch requires a `quick_release_id` string input. The run name
includes that id so callers can correlate a dispatch with its run:

```yaml
run-name: "Quick debug build (${{ github.ref_name }}, ${{ inputs.quick_release_id }})"
on:
  workflow_dispatch:
    inputs:
      quick_release_id:
        required: true
        type: string
```

The workflow uses global `concurrency.group: quick-build` with
`cancel-in-progress: false`. GitHub keeps one run in progress and one pending
run; a newer pending run can replace the older pending run. This is
latest-pending-wins, not an unbounded queue.

#### Full resulting file (for reference / sanity check)

For reference, the relevant resulting workflow includes the latest Pebble SDK
setup and publishes both the Android APK and Pebble PBW:

```yaml
name: quick-build
run-name: "Quick debug build (${{ github.ref_name }}, ${{ inputs.quick_release_id }})"
on:
  workflow_dispatch:
    inputs:
      quick_release_id:
        description: "Unique id used to correlate this dispatch with its run"
        required: true
        type: string
# GitHub keeps one running run plus one pending run; a newer pending run can
# replace the older pending run. This is latest-pending-wins, not an unbounded queue.
concurrency:
  group: quick-build
  cancel-in-progress: false
jobs:
  build-debug-apk:
    runs-on: "ubuntu-latest"
    permissions:
      contents: write
    steps:
      - uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2
        with:
          lfs: true
          submodules: recursive
          fetch-depth: 0
      - name: Globally enable build cache and parallel execution
        run: |
          mkdir -p ~/.gradle

          cat >> ~/.gradle/gradle.properties<< EOF
          org.gradle.caching=true
          org.gradle.parallel=true
          EOF
      - uses: actions/setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654 # v5.2.0
        with:
          java-version: '21'
          distribution: temurin
          cache: gradle
      - uses: android-actions/setup-android@7c5672355aaa8fde5f97a91aa9a99616d1ace6bc
      - uses: astral-sh/setup-uv@08807647e7069bb48b6ef5acd8ec9567f424441b # v8.1.0
      - uses: actions/setup-python@a309ff8b426b58ec0e2a45f0f869d46889d02405 # v6.2.0
        with:
          python-version: '3.13'
      - name: Install latest Pebble SDK
        run: uv tool install pebble-tool --python 3.13 && (pebble sdk install latest || true)
      - name: Enable Gradle remote build cache
        uses: burrunan/gradle-cache-action@663fbad34e03c8f12b27f4999ac46e3d90f87eca
        with:
          debug: false
          concurrent: true
          read-only: true
          build-root-directory: mobile

      - name: Assemble debug APK
        run: "./gradlew :app:assembleDebug"
        working-directory: mobile

      - name: Build watchapp
        run: pebble build && mv build/watch.pbw build/catapult-watchapp.pbw
        working-directory: watch

      - name: Upload debug APK
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: catapult-mobile-debug-apk
          path: mobile/app/build/outputs/apk/debug/catapult-mobile.apk
          retention-days: 14

      - name: Publish quick build as a prerelease
        uses: ncipollo/release-action@339a81892b84b4eeb0f6e744e4574d79d0d9b8dd # v1.21.0
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          tag: debug-latest
          commit: ${{ github.sha }}
          name: "Quick debug build (${{ github.ref_name }} @ ${{ github.sha }})"
          body: |
            Debug-signed convenience build, **not** a tested release.
            Branch: `${{ github.ref_name }}`
            Commit: `${{ github.sha }}`
            Run: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}
          artifacts: "mobile/app/build/outputs/apk/debug/catapult-mobile.apk,watch/build/catapult-watchapp.pbw"
          prerelease: true
          allowUpdates: true
          removeArtifacts: true
          generateReleaseNotes: false
```

### Documentation changes (`RELEASING.md`)

In the "Quick debug build" section, document that the APK and PBW are both
published in the rolling prerelease:

> This build is signed with the debug key. It is published as the rolling
> `debug-latest` **prerelease** on GitHub (overwritten on every run) — see
> `https://github.com/<owner>/<repo>/releases/tag/debug-latest`. It is not a
> substitute for the full `develop-build` release below.

The previous planned-only note is superseded now that this workflow publishes
both quick-build artifacts.

### Verification (run these after implementing, in order)

1. Trigger the workflow:
   `gh workflow run quick-build --repo <owner>/<repo> --ref <branch> -f quick_release_id=<unique-id>`
   Use a unique ID for each dispatch and match it to the run name
   `Quick debug build (<branch>, <unique-id>)`.
2. Wait for that run to finish:
   `gh run watch <run-id> --repo <owner>/<repo> --exit-status`
3. Confirm the prerelease exists and has exactly two assets:
   `gh release view debug-latest --repo <owner>/<repo>`
   — check the output shows `prerelease: true`, one `.apk` asset, and one
   `.pbw` asset.
4. Run the workflow a second time (any branch) and repeat step 3 — confirm
   the asset was replaced, not duplicated, and the release body's commit SHA
   changed to match the new run.
5. Confirm the "Latest release" shown at
   `https://github.com/<owner>/<repo>/releases` is still the last proper
   `develop-build` version (e.g. `0.90`), **not** `debug-latest` — this is
   what "prerelease" is for. If `debug-latest` shows up as "Latest", the
   `prerelease: true` field was dropped or misspelled; fix it before
   considering this done.

## Explicitly out of scope for this plan

- Auto-triggering on every push to `main` (that's the separate,
  already-deferred "proper release on push to main" idea — quick builds stay
  manually dispatched so they don't fire on unrelated commits, e.g. docs-only
  changes).
- Any lint/test gating — quick builds intentionally skip these; that's the
  entire point of the fast path.
- Changing what `develop-build` considers a "proper" release.

## Compatibility with upstream

`quick-build.yaml` does not exist upstream (`matejdro/PebbleCatapult`), so
this plan only touches a fork-only file. It does not modify
`develop.yaml`, `version.txt`, or any application source, so it carries zero
merge risk with upstream.
