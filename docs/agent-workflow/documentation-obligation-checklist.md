# Documentation Obligation Checklist

Run this **mentally before finalizing** any object, level, trace, audio, or other
`src/main/` change on a non-`master` branch. It exists to keep technically correct
changes from failing branch policy or losing institutional knowledge.

This checklist is enforced, not advisory: the commit-trailer gate lives in
`.githooks/validate-policy.ps1` (Windows) / `.githooks/validate-policy.sh`
(macOS/Linux), dispatched by `.githooks/run-policy`. CI mirrors the same rules on
non-draft pull requests, including the shared `develop`/`next` destinations. Install hooks explicitly once per worktree with
`tools/testing/install-hooks.sh` (PowerShell: `.ps1`). Maven does not change
Git configuration during `validate`.

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

## Release and artifact destinations

Release procedure changes must keep
[release publishing](../project/release-publishing.md),
[release rollover](../project/release-rollover.md), `AGENTS.md`/`CLAUDE.md`,
and the mirrored `release-publishing` skill consistent. Pushes to `master`
automatically publish after successful validation and builds. Manual dispatch
is validation-only. Confirm the actual release before reporting it shipped;
do not instruct maintainers to create the version tag before the workflow.

### Which version am I writing for?

Read the relevant branch's `pom.xml`; it selects the current release line.
Currently **master is 0.6.20260911, and both develop and next are
0.7.prerelease**. The development refs intentionally share a commit, with
`targetBranch=develop` and `candidateBranchAliases=next` in the policy descriptor.
New prose from either branch belongs in `CHANGELOG.0.7.prerelease.md`; earlier
0.7 themes remain in `CHANGELOG.0.7.md`. Root `CHANGELOG.md` is the index and
shared-line pointer. The 0.8 roadmap remains future scope. When next becomes an
independent line, clear aliases, advance its POM and related metadata, and restore
its root Unreleased section through [release rollover](../project/release-rollover.md).

### Where each kind of prose goes

- **`CHANGELOG.<develop version>.md`** — every change, at the detail a user or
  maintainer would want. This is the per-version record and the default
  destination. Historical changelog files change only for factual corrections.
- **`CHANGELOG.md`** — the release index and current shared-line pointer; an
  independent `next` line uses its Unreleased section after a deliberate split.
- **`README.md` release section** — a **very high-level summary** of the
  version: a handful of themes a reader skims to learn what this version is
  about. It is not a change log, and there is no expectation that a given
  commit earns a README line.
- **`docs/changelog/v<version>-release-summary.md`** — public release
  messaging, validation results, blockers.
- **`docs/changelog/v<version>-prerelease-detailed.md`** — investigations,
  workstreams, superseded approaches, gate decisions.

### Fixes to unreleased work

A fix to a feature introduced in the same unreleased version is **not** a
separate item anywhere user-facing: that feature never shipped broken, so a
reader of the release notes learns nothing from the fix. Fold it into the
existing entry so the entry describes the corrected end state. In the README
this usually means no change at all: the theme already covers the feature.
Edit a theme only when the fix changes what the theme says; never append a
"fixed X" line or paragraph beside the theme that introduced X. No hook
requires a README change on a develop merge. A fix earns its own entry only when it
repairs behavior that shipped in a **previous** release; say which release
regressed.

### Engineering artifacts

Engineering designs, plans, research, audits, and validation belong under the
matching `docs/architecture/` subdirectory from `docs/README.md`; stage their
supporting artifacts with the task. Do not create loose planning documents.

## Pre-finalize checklist

### 1. Trace frontier — `docs/status/trace-frontier-log.md`

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
  - `.agents/skills/s1-implement-object/rom-pitfalls.md` and its `.claude` mirror.
- Editing these files **stages skill changes**, so the `Skills` trailer becomes
  **REQUIRED** = `updated` (see item 7). Mark cross-game applicability when the
  pitfall is not game-specific.
- **JUSTIFIED SKIP** if no reusable pitfall surfaced.

### 3. Changelog — `CHANGELOG.md`  →  trailer `Changelog`

- **REQUIRED** for release-worthy engine changes: use the per-version changelog
  selected under “Which version am I writing for?” above (currently
  `CHANGELOG.0.7.prerelease.md`). Keep the existing thematic structure: add a
  `- **Lead:** ...` bullet under the matching `###`/`####` area section, or refine
  an existing bullet when the change belongs to unreleased work. Do not add a
  dated entry at the top. Keep commit hashes, frame numbers and test tallies in
  engineering evidence. Historical changelog files change only for factual
  corrections. The root `CHANGELOG.md` is the release index; update it only when
  the index changes. Its exact path owns the hook trailer: when only the selected
  versioned file changes, use
  `Changelog: n/a: release note recorded in <actual per-version changelog path>`.
- **JUSTIFIED SKIP** — special rule: on a `feat`/`fix`/`perf` commit that touches
  `src/main/`, a **bare** `Changelog: n/a` is **rejected**
  (`validate_changelog_justification`). You must either set `Changelog: updated`
  **or** justify: `Changelog: n/a: <reason>` (e.g. `n/a: test-only helper`,
  `n/a: docs-only`). Commits with other subject prefixes, or that don't touch
  `src/main/`, may use a plain `n/a`.

### 4. Known discrepancies — `docs/status/known-discrepancies.md`  →  trailer `Known-Discrepancies`

- **REQUIRED** if you added, resolved, or changed an intentional/known divergence
  from ROM behavior (cross-game / non-S3K-specific): update the file, set
  `Known-Discrepancies: updated`, and stage it.
- **JUSTIFIED SKIP** = `n/a` if no known-discrepancy state changed.

### 5. S3K known discrepancies — `docs/S3K_KNOWN_DISCREPANCIES.md`  →  trailer `S3K-Known-Discrepancies`

- **REQUIRED** if you added/resolved/changed an **S3K-specific** parity gap:
  update the file, set `S3K-Known-Discrepancies: updated`, and stage it.
- **JUSTIFIED SKIP** = `n/a` otherwise.

### 6. Configuration — `CONFIGURATION.md`  →  trailer `Configuration-Docs`

- **Changing a default** needs exactly three edits: `putDefault` in
  `SonicConfigurationService`, the value in `src/main/resources/config.yaml`, and the
  row here. Player files hold only changed settings, so no migration or version bump
  exists; `TestSparseUserConfig` fails when the template and the default disagree.

- **REQUIRED** if configuration behavior changed: new/changed `config.yaml` flag,
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
| `Known-Discrepancies` | `docs/status/known-discrepancies.md` | exact file | Cross-game intentional divergences. |
| `S3K-Known-Discrepancies` | `docs/S3K_KNOWN_DISCREPANCIES.md` | exact file | S3K-specific parity gaps. |
| `Agent-Docs` | `AGENTS.md` **and** `CLAUDE.md` | both, exact | Must stage both together when `updated`. |
| `Configuration-Docs` | `CONFIGURATION.md` | exact file | Config flags / key bindings / toggles. |
| `Skills` | `.agents/skills/` **and** `.claude/skills/` | both, prefix | Must stage both mirrors together when `updated`. |

`docs/status/trace-frontier-log.md` has **no trailer** — it is a separate branch policy
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

## Validating trailers before you push

The local hooks and CI compute "is this file staged?" from **different bases**, so a
commit that satisfies one can still be rejected by the other. Run CI's own validator
before every push; it is silent on success and takes under a second:

```bash
bash .githooks/validate-policy.sh ci-push "$(git rev-parse @{u})" "$(git rev-parse HEAD)" develop
```

Two consequences are worth knowing in advance, because both have cost real time:

**Amendments need care.** Hooks validate the staged diff, which may differ from
an amended commit's full diff. Prefer a follow-up commit when correcting delivered
work. Inspect the hook's actual comparison base before rewriting local history;
do not reset a working tree just to satisfy a documentation trailer.

**Merge commits skip documentation-trailer validation.** Resource/content checks
still apply. Local staged-content checks compare a pending merge with `MERGE_HEAD`;
CI's `commit_candidates` examines paths changed relative to **both** parents, so
content inherited unchanged from either side is not attributed as new merge content.
The incoming non-merge commits still require accurate trailers. Do not invent merge
trailer obligations from the second-parent helper; run the validator on the actual
range, including conflict resolutions.

**AI co-authors are the final block.** Every AI-assisted commit records each
contributing model's name and version. Put documentation/policy trailers before
the co-author block, separated by one blank line, and leave no text after it.
For Codex use `noreply@openai.com`. Pass the exact message through
`git interpret-trailers --parse <message-file>` before committing and confirm all
intended co-author lines are present.

## Quick decision summary

- Touched `src/main/` engine behavior? → `Changelog: updated` + `CHANGELOG.md`,
  **or** `Changelog: n/a: <reason>` on `feat`/`fix`/`perf`.
- Trace frontier moved / regressed / drove target selection? → update
  `docs/status/trace-frontier-log.md` (no trailer).
- Found a reusable pitfall? → update both `rom-pitfalls.md` mirrors → `Skills: updated`.
- Changed a known divergence? → `Known-Discrepancies` and/or
  `S3K-Known-Discrepancies` = `updated`.
- Changed a config flag/binding? → `Configuration-Docs: updated`.
- Changed skill files? → both trees staged + `Skills: updated`.
- Changed `AGENTS.md`/`CLAUDE.md`? → both staged + `Agent-Docs: updated`.
- Changed `docs/guide/`? → `Guide: updated`.
- Everything else → `n/a` (bare is fine except the `Changelog` engine-change rule).
