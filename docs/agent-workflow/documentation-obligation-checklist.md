# Documentation Obligation Checklist

Run this **mentally before finalizing** any object, level, trace, audio, or other
`src/main/` change on a non-`master` branch. It exists to keep technically correct
changes from failing branch policy or losing institutional knowledge.

This checklist is enforced, not advisory: the commit-trailer gate lives in
`.githooks/validate-policy.ps1` (Windows) / `.githooks/validate-policy.sh`
(macOS/Linux), dispatched by `.githooks/run-policy`. CI mirrors the same rules on
PRs into `develop`. Maven auto-installs the hooks during the `validate` phase
(`install-git-hooks` in `pom.xml`); if you commit without building first, run
`git config core.hooksPath .githooks` once.

**Do not bypass with `--no-verify`.** The trailer block is the required attestation
for the repo's "where relevant" documentation/discrepancy checks.

---

## How to read this checklist

Each item is one of:

- **REQUIRED** — the condition applies, so you must update the file *and* set the
  matching commit trailer to `updated` (and stage that file).
- **JUSTIFIED SKIP** — the condition does not apply, so the trailer may say `n/a`.
  For `Changelog` on an engine change, a *bare* `n/a` is rejected; you must write
  `n/a: <reason>` (see item 3).

The base trailer gate only checks that **staged files and trailer values agree**:
if the mapped file(s) are staged, the trailer must say `updated`; if not staged,
it must say `n/a`. So "lying" in either direction fails the hook.

---

## Pre-finalize checklist

### 1. Trace frontier — `docs/TRACE_FRONTIER_LOG.md`

- **REQUIRED** if a trace frontier **moved**, a previously passing trace
  **regressed**, a trace fix was committed, or a full `*TraceReplay` sweep was used
  to **choose the next target**.
- Record: the command run, commit/worktree context, pass/fail status, error count,
  and the first-error frame/field.
- **JUSTIFIED SKIP** if no trace frontier changed and no sweep drove target
  selection. There is **no dedicated trailer** for this file — it is policy, not a
  trailer gate. Stage the log edit alongside your change; do not defer it.
- Reminder: trace data is **comparison-only** — never hydrate/sync engine state from
  a trace. (Guarded by `TestTraceReplayInvariantGuard` / `TestTraceHydrateSwitchDefault`.)

### 2. Reusable pitfalls — `rom-pitfalls.md` (mirrored)

- **REQUIRED** if an object/badnik/trace fix revealed a reusable ROM-parity pitfall.
  Add the entry to the relevant per-game file **and its mirror**:
  - `.agents/skills/s2-implement-object/rom-pitfalls.md` **and**
    `.claude/skills/s2-implement-object/rom-pitfalls.md`
  - `.agents/skills/s3k-implement-object/rom-pitfalls.md` **and**
    `.claude/skills/s3k-implement-object/rom-pitfalls.md`
  - (S1 has no `rom-pitfalls.md` yet; if you add one, create it in both trees.)
- Editing these files **stages skill changes**, so the `Skills` trailer becomes
  **REQUIRED** = `updated` (see item 7). Mark cross-game applicability when the
  pitfall is not game-specific.
- **JUSTIFIED SKIP** if no reusable pitfall surfaced.

### 3. Changelog — `CHANGELOG.md`  →  trailer `Changelog`

- **REQUIRED** if engine behavior under `src/main/` changed in a changelog-worthy
  way: set `Changelog: updated` and stage `CHANGELOG.md`.
- **JUSTIFIED SKIP** — special rule: on a `feat`/`fix`/`perf` commit that touches
  `src/main/`, a **bare** `Changelog: n/a` is **rejected**
  (`validate_changelog_justification`). You must either set `Changelog: updated`
  **or** justify: `Changelog: n/a: <reason>` (e.g. `n/a: test-only helper`,
  `n/a: docs-only`). Commits with other subject prefixes, or that don't touch
  `src/main/`, may use a plain `n/a`.

### 4. Known discrepancies — `docs/KNOWN_DISCREPANCIES.md`  →  trailer `Known-Discrepancies`

- **REQUIRED** if you added, resolved, or changed an intentional/known divergence
  from ROM behavior (cross-game / non-S3K-specific): update the file, set
  `Known-Discrepancies: updated`, and stage it.
- **JUSTIFIED SKIP** = `n/a` if no known-discrepancy state changed.

### 5. S3K known discrepancies — `docs/S3K_KNOWN_DISCREPANCIES.md`  →  trailer `S3K-Known-Discrepancies`

- **REQUIRED** if you added/resolved/changed an **S3K-specific** parity gap:
  update the file, set `S3K-Known-Discrepancies: updated`, and stage it.
- **JUSTIFIED SKIP** = `n/a` otherwise.

### 6. Configuration — `CONFIGURATION.md`  →  trailer `Configuration-Docs`

- **REQUIRED** if configuration behavior changed: new/changed `config.json` flag,
  key binding, or feature toggle. Update the file, set
  `Configuration-Docs: updated`, and stage it.
- **JUSTIFIED SKIP** = `n/a` if no configuration surface changed.

### 7. Skills — `.agents/skills/` + `.claude/skills/`  →  trailer `Skills`

- **REQUIRED** if you changed agent guidance/skills. Both trees must have staged
  changes **together** (the gate fails if only one side is staged). Set
  `Skills: updated`. Editing any `rom-pitfalls.md` (item 2) triggers this.
- **JUSTIFIED SKIP** = `n/a` if no skill files changed.
- **Mirror rule:** any skill edit must be applied identically in both
  `.agents/skills/<name>/` and `.claude/skills/<name>/`.

### 8. Agent docs — `AGENTS.md` + `CLAUDE.md`  →  trailer `Agent-Docs`

- **REQUIRED** if you changed top-level agent guidance. **Both** `AGENTS.md` **and**
  `CLAUDE.md` must be staged together; set `Agent-Docs: updated`. (S3K-specific
  guidance belongs in `AGENTS_S3K.md`, not `CLAUDE.md` — but `AGENTS_S3K.md` has no
  separate trailer; it rides under agent-docs judgment.)
- **JUSTIFIED SKIP** = `n/a` if neither root agent doc changed.

### 9. Guide — `docs/guide/`  →  trailer `Guide`

- **REQUIRED** if you changed contributor/player guide content under `docs/guide/`
  (this is a **prefix** match on the directory): set `Guide: updated` and stage the
  guide file(s).
- **JUSTIFIED SKIP** = `n/a` if nothing under `docs/guide/` changed.

---

## Trailer → file/dir map (authoritative)

Source of truth: `.githooks/validate-policy.sh` / `.githooks/validate-policy.ps1`.
Every trailer value must start with `updated` or `n/a`. The block is auto-appended
to non-merge commits by `prepare-commit-msg` — fill it in, do not delete it.

| Trailer key | Maps to | Match type | Notes |
|-------------|---------|-----------|-------|
| `Changelog` | `CHANGELOG.md` | exact file | Bare `n/a` rejected on `feat`/`fix`/`perf` touching `src/main/`; use `n/a: <reason>`. |
| `Guide` | `docs/guide/` | directory prefix | Contributor/player guide tree. |
| `Known-Discrepancies` | `docs/KNOWN_DISCREPANCIES.md` | exact file | Cross-game intentional divergences. |
| `S3K-Known-Discrepancies` | `docs/S3K_KNOWN_DISCREPANCIES.md` | exact file | S3K-specific parity gaps. |
| `Agent-Docs` | `AGENTS.md` **and** `CLAUDE.md` | both, exact | Must stage both together when `updated`. |
| `Configuration-Docs` | `CONFIGURATION.md` | exact file | Config flags / key bindings / toggles. |
| `Skills` | `.agents/skills/` **and** `.claude/skills/` | both, prefix | Must stage both mirrors together when `updated`. |

`docs/TRACE_FRONTIER_LOG.md` has **no trailer** — it is a separate branch policy
obligation (item 1). Update it in the same commit as the trace work it documents.

### Example trailer block

```
Changelog: updated
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: updated
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: updated
```

(Here: an S3K engine fix that also added a pitfall entry to both skill mirrors and
recorded an S3K parity gap; no config, guide, or root-agent-doc changes.)

---

## Quick decision summary

- Touched `src/main/` engine behavior? → `Changelog: updated` + `CHANGELOG.md`,
  **or** `Changelog: n/a: <reason>` on `feat`/`fix`/`perf`.
- Trace frontier moved / regressed / drove target selection? → update
  `docs/TRACE_FRONTIER_LOG.md` (no trailer).
- Found a reusable pitfall? → update both `rom-pitfalls.md` mirrors → `Skills: updated`.
- Changed a known divergence? → `Known-Discrepancies` and/or
  `S3K-Known-Discrepancies` = `updated`.
- Changed a config flag/binding? → `Configuration-Docs: updated`.
- Changed skill files? → both trees staged + `Skills: updated`.
- Changed `AGENTS.md`/`CLAUDE.md`? → both staged + `Agent-Docs: updated`.
- Changed `docs/guide/`? → `Guide: updated`.
- Everything else → `n/a` (bare is fine except the `Changelog` engine-change rule).
