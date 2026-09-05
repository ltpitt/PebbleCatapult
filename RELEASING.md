# Releasing Catapult

Catapult releases produce both the Android APK and Pebble watchapp PBW from
the same release workflow.

## Quick debug build (for trying changes on a phone)

If you just want an installable APK to sideload and test, without waiting for
the full test/lint/screenshot-test/watchapp/versioning pipeline, use the
`quick-build` workflow in `.github/workflows/quick-build.yaml`:

1. Open **Actions → quick-build → Run workflow**, pick your branch, and start
   it.
2. It only runs `:app:assembleDebug` — no tests, no lint, no watchapp build,
   no versioning, no GitHub Release.
3. Download the APK from the run's **Artifacts** section
   (`catapult-mobile-debug-apk`) and sideload it.

This build is signed with the debug key and is never published as a GitHub
Release. Use the full `develop-build` workflow below for anything you intend
to actually release.

## Automated release

The canonical process is the `develop-build` workflow in
`.github/workflows/develop.yaml`.

1. Merge changes into `main` using conventional commits. Use `fix:` for a
   patch release, `feat:` for a minor release, and a breaking-change marker
   for a major release.
2. Open **Actions → develop-build → Run workflow**, select `main`, and start
   the workflow. The workflow also runs daily at 07:00 UTC.
3. The workflow checks for new `fix` or `feat` commits since the version
   recorded in `version.txt`. If none exist, it skips the release.
4. It calculates the next Pebble-compatible version, updates:
   `version.txt`, `mobile/version.txt`, `watch/version.txt`, and
   `watch/package.json`, then commits and tags that version.
5. It builds and tests the Android app and Pebble watchapp, then creates a
   GitHub Release containing:
   - `mobile/app/build/outputs/apk/release/catapult-mobile.apk`
   - `watch/build/catapult-watchapp.pbw`

Do not manually edit the version files for a normal release. The workflow
keeps the Android and watch versions aligned when both components changed; if
only one component changed, the release notes link to the previous version of
the unchanged component.

## Required repository configuration

The workflow needs:

- GitHub Actions enabled for the repository.
- Workflow permissions that allow `GITHUB_TOKEN` to push commits and tags and
  create releases.
- Repository secrets `RELEASE_KEY_PASSWORD` and
  `RELEASE_KEYSTORE_PASSWORD` for the signed Android release build (see the
  note below if this fork does not have the original password).
- Git LFS and recursive submodules available to the checkout.

### Known issue on this fork: lost release keystore password

`mobile/keys/release.jks` is byte-identical to the upstream repository's file,
but the password that protects it was never part of this fork's secrets and
could not be recovered. As a temporary measure, `mobile/app/build.gradle.kts`
signs the `release` build type with the **debug** signing config instead of
`release`. This keeps the automated pipeline working, but:

- The resulting APK is signed with the debug key, not suitable for Play Store
  distribution or any channel that expects a stable, private signing key.
- If you obtain the real `RELEASE_KEY_PASSWORD` /
  `RELEASE_KEYSTORE_PASSWORD`, set them as repository secrets and revert the
  `signingConfig = signingConfigs.getByName("debug")` line in
  `mobile/app/build.gradle.kts` back to
  `signingConfig = signingConfigs.getByName("release")`.
- If the password is permanently unrecoverable, generate a new release
  keystore, store its password as a repository secret, and replace
  `mobile/keys/release.jks`. Note this changes the app's signing identity,
  which matters if the app is ever published somewhere that checks signatures
  (e.g. Play Store update compatibility).

The release job also installs the Pebble SDK and uses Java 21 for the Android
build. These tools are installed by the workflow; they are not prerequisites
on the computer that starts the workflow.

## Before relying on a release

Confirm that the workflow is visible and manually dispatchable in the Actions
tab. If GitHub does not list `develop-build`, inspect the workflow YAML and
repository Actions settings before attempting a release. A file that exists in
`.github/workflows/` but is not registered by GitHub cannot be dispatched.

Check the generated changelog URLs in `develop.yaml` when transferring the
repository to a different owner. They must point to the actual GitHub
repository, not the original upstream owner.

## Manual fallback

Use a manual release only when the workflow cannot be repaired in time. Build
the signed Android APK and Pebble PBW with the same version, run the same
tests, tag the version, and upload both artifacts to a GitHub Release. Record
the reason for bypassing automation and update this guide if the workflow
contract changes.
