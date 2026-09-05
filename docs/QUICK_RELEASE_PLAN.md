# Plan: quick debug builds published as a GitHub Release

Status: **planned, not implemented yet**. This document describes the design
before we touch `.github/workflows/quick-build.yaml`. Implement it as a
separate, reviewable change once the approach below is agreed.

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

1. Trigger: keep `workflow_dispatch` (manual, any branch) as today.
2. Build: unchanged — `:app:assembleDebug` only.
3. New step after the build: create/update the `debug-latest` GitHub Release
   via `ncipollo/release-action`, with:
   - `tag: debug-latest`, `commit: ${{ github.sha }}`
   - `prerelease: true`
   - `allowUpdates: true`, `removeArtifacts: true` (replace the old APK)
   - `name: "Quick debug build (${{ github.ref_name }} @ ${{ github.sha }})"`
   - `body`: branch, commit SHA, run URL, and a one-line reminder that this is
     a debug-signed, untested convenience build, not a release candidate.
   - `artifacts: mobile/app/build/outputs/apk/debug/*.apk`
4. Add an explicit `permissions: contents: write` block to the job (least
   privilege) instead of relying on the repository-wide default token
   permission. This also documents, in the workflow file itself, exactly why
   this workflow needs write access.
5. Still upload the workflow artifact too (cheap, keeps today's behavior for
   anyone using the Actions UI directly).

### Documentation changes (`RELEASING.md`)

Update the existing "Quick debug build" section to say the APK is also
published as the `debug-latest` prerelease, with the direct link pattern,
once implemented.

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
