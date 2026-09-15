# Agent Instruction Boundaries Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate Android-specific contributor guidance into `mobile/AGENTS.md` and require user-facing documentation updates for important functionality.

**Architecture:** Keep root `AGENTS.md` authoritative for repository-wide, cross-component, and watch-side rules. Keep Android implementation and phone-debugging rules in `mobile/AGENTS.md`, with a root pointer rather than duplicated instructions.

**Tech Stack:** Markdown guidance files, Git.

---

### Task 1: Move Android guidance to the mobile scope

**Files:**
- Modify: `AGENTS.md`
- Modify: `mobile/AGENTS.md`

- [ ] **Step 1: Move Android-specific sections**

Move the root sections `Android-to-Pebble communication`, `Watchapp launch & auto-close lifecycle`, and `Returning variables to Tasker (and showing them on the plugin screen)` into `mobile/AGENTS.md`. Move the phone-side subsection `Phone (Android app) logs — primary tool` there as well.

- [ ] **Step 2: Add the root scope pointer and documentation rule**

Add to root `AGENTS.md`:

```markdown
## Documentation and scope

Keep Android-specific guidance in [`mobile/AGENTS.md`](mobile/AGENTS.md);
avoid duplicating it here. When changing important functionality, update
`README.MD` and any other affected user-facing instructions or reference
documentation. If no documentation update is needed, state that assessment in
the implementation notes or review description.
```

- [ ] **Step 3: Add the mobile scope heading**

Start `mobile/AGENTS.md` with a scope note:

```markdown
> These instructions apply to the Android app. Repository-wide and watch-side
> guidance lives in the root [`AGENTS.md`](../AGENTS.md).
```

- [ ] **Step 4: Verify ownership and links**

Run:

```bash
rg -n "Android-to-Pebble|Watchapp launch|Returning variables|Phone \\(Android app\\)|Documentation and scope" AGENTS.md mobile/AGENTS.md
git diff --check
```

Expected: each moved Android section appears only in `mobile/AGENTS.md`, the documentation rule appears in root `AGENTS.md`, and `git diff --check` exits successfully.

### Task 2: Commit and push the guidance update

**Files:**
- Add: `docs/superpowers/plans/2026-09-15-agent-instruction-boundaries.md`
- Add: `docs/superpowers/specs/2026-09-15-agent-instruction-boundaries-design.md` (already committed)
- Modify: `AGENTS.md`
- Modify: `mobile/AGENTS.md`

- [ ] **Step 1: Review the complete diff**

Run:

```bash
git diff -- AGENTS.md mobile/AGENTS.md docs/superpowers/plans/2026-09-15-agent-instruction-boundaries.md
```

Confirm the root file contains only repository-wide/watch-side rules plus the scope/documentation rule, and the mobile file contains Android-specific rules.

- [ ] **Step 2: Commit**

Run:

```bash
git add AGENTS.md mobile/AGENTS.md docs/superpowers/plans/2026-09-15-agent-instruction-boundaries.md
git commit -m "docs: separate mobile agent guidance" -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

- [ ] **Step 3: Push**

Run:

```bash
git push origin main
```

Expected: the new commit is pushed to `origin/main`.
