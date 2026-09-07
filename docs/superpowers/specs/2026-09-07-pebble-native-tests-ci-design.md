# Pebble Native Tests in CI

## Goal

Make the existing host-side C tests for the Pebble watchapp a required,
repeatable CI check without involving the Pebble emulator or changing the
shipped watchapp.

## Scope

This first slice covers:

- One aggregate command for all native watch tests.
- A local `make watch-test` entry point.
- Running the aggregate command in pull-request and develop workflows.
- Retaining strict compiler and test failure behavior.

It does not add emulator automation, UI screenshot testing, or new behavioral
tests. Those can build on this foundation later.

## Test layout and execution

Tests remain in `watch/tests/` and continue to compile selected production C
files into temporary desktop executables. The aggregate script will invoke the
existing lifecycle and packet test scripts in a deterministic order and fail
if any compilation or assertion fails.

Each test script owns its temporary executable and removes it with a shell trap,
including when the test fails. Test binaries are not written to the Pebble
build output and are not consumed by `pebble build`.

The local Make target delegates to the aggregate script, matching the existing
Make-to-script convention. The same aggregate script is used locally and in CI
so the two execution paths cannot drift.

## CI integration

The pull-request workflow will run the native watch tests independently from
the Pebble SDK build. The develop workflow will run the same check before
building and publishing release artifacts. The test step uses the runner's
standard C compiler and requires no display server, emulator, Android device,
or Pebble SDK installation.

The existing `pebble build` step remains unchanged and continues to validate
that the complete watchapp can be compiled for all configured target
platforms. Native test success does not replace that build check.

## Failure handling

The aggregate script will use strict shell error handling. A compiler warning
treated as an error, failed assertion, missing test executable, or failed test
script will fail the CI job and prevent later release steps. No failures will
be swallowed or converted into successful output.

## Validation

Validation will run the aggregate native test command locally, the existing
Pebble build, and the repository's relevant workflow/configuration checks.
The implementation must leave the `.pbw` contents and Pebble build behavior
unchanged apart from the separately reported test command.
