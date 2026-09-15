# Agent Instruction Boundaries

## Goal

Keep contributor guidance discoverable at the narrowest applicable scope while
making documentation updates part of important functionality changes.

## Scope and ownership

- `AGENTS.md` at the repository root owns repository-wide coordination,
  cross-component planning, watchapp behavior, protocol constraints that affect
  both sides, and watch-side debugging or memory guidance.
- `mobile/AGENTS.md` owns Android-specific implementation guidance, including
  PebbleKit usage, watchapp launch lifecycle from the phone, Tasker contracts,
  phone logging, and Android UI conventions.
- Guidance should not be duplicated across the two files. The root file may
  point contributors to `mobile/AGENTS.md` when a change enters the Android
  app.

## Documentation requirement

When changing important functionality, contributors must update the root
`README.MD` and any other affected user-facing instructions or reference
documentation. If no documentation update is needed, the change should make
that assessment explicit in its implementation notes or review description.

## Implementation and verification

Move Android-specific sections from the root file into `mobile/AGENTS.md`,
retain watch and cross-component rules at the root, and add the documentation
requirement at repository scope. Verify that each instruction appears in only
one applicable file and that links and headings remain valid.
