# Plan: quick debug builds published as a GitHub Release

Status: **planned, not implemented yet**. This document is written so it can
be implemented mechanically, step by step, without needing to make design
judgment calls — those calls are already made below. If you are implementing
this: follow "Implementation steps" in order, use the exact YAML given, and
run the exact verification commands at the end. Do not improvise field names,
action versions, or file paths — copy them from this document.

## Problem

`quick-build.yaml` (added to fix the release pipeline in this fork) already
builds `:app:assembleDebug` fast, with no lint/tests/watchapp/versioning. Its
only output today is a workflow **artifact**, which:

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

Everything about the existing workflow stays the same (trigger, build step,
artifact upload). Only two things are added: a `permissions` block on the
job, and one new step at the end. Use `ncipollo/release-action` pinned to
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

3. At the very end of the `steps:` list (after the existing `Upload debug
   APK` step), add this new step exactly as written:

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
             artifacts: "mobile/app/build/outputs/apk/debug/catapult-mobile.apk"
             prerelease: true
             allowUpdates: true
             removeArtifacts: true
             generateReleaseNotes: false
   ```

4. Save the file. Do not change any other step in `quick-build.yaml`.

#### Full resulting file (for reference / sanity check)

If in doubt, the whole file should end up matching this (only the last two
blocks — `permissions` and the new step — are new; everything above them is
unchanged from today):

```yaml
name: quick-build
run-name: "Quick debug build (${{ github.ref_name }})"
on:
  workflow_dispatch:
concurrency:
  group: quick-build-${{ github.ref }}
  cancel-in-progress: true
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
          artifacts: "mobile/app/build/outputs/apk/debug/catapult-mobile.apk"
          prerelease: true
          allowUpdates: true
          removeArtifacts: true
          generateReleaseNotes: false
```

### Documentation changes (`RELEASING.md`)

In the "Quick debug build" section, replace the sentence that starts with
"This build is signed with the debug key and is never published as a GitHub
Release" with:

> This build is signed with the debug key. It is published as the rolling
> `debug-latest` **prerelease** on GitHub (overwritten on every run) — see
> `https://github.com/<owner>/<repo>/releases/tag/debug-latest`. It is not a
> substitute for the full `develop-build` release below.

Also delete the `> Planned: ...` blockquote that currently links to this
document (it becomes stale once this is implemented).

### Verification (run these after implementing, in order)

1. Trigger the workflow:
   `gh workflow run quick-build --repo <owner>/<repo> --ref <branch>`
2. Wait for it to finish:
   `gh run watch <run-id> --repo <owner>/<repo> --exit-status`
3. Confirm the prerelease exists and has exactly one asset:
   `gh release view debug-latest --repo <owner>/<repo>`
   — check the output shows `prerelease: true` and one `.apk` asset.
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
