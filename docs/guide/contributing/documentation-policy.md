# Documentation And Branch Policy

This page explains the documentation and commit-message policy that applies to normal development
branches. It is written for contributors; the hook scripts in `.githooks/` are the enforcement
source of truth.

## Branch Names

Use focused branches from `develop`:

- `feature/ai-...` for new features.
- `bugfix/ai-...` for fixes.

Keep one logical task per branch when practical. If a branch spans unrelated systems, split it
before opening a PR.

## Git Hooks

Tracked hooks live in `.githooks/`. Maven installs them during the `validate` phase by pointing
`core.hooksPath` at `.githooks`:

```bash
mvn validate
```

If you commit before building, install the hooks manually:

```bash
git config core.hooksPath .githooks
```

Do not bypass the policy with `--no-verify`. The trailer block is the project's explicit
attestation that related documentation and discrepancy files were considered.

## Commit Trailers

Every non-merge commit on a non-`master` branch must include these trailers. The
`prepare-commit-msg` hook appends the block automatically; fill in each value instead of deleting
it.

```text
Changelog: updated|n/a
Guide: updated|n/a
Known-Discrepancies: updated|n/a
S3K-Known-Discrepancies: updated|n/a
Agent-Docs: updated|n/a
Configuration-Docs: updated|n/a
Skills: updated|n/a
```

Each value must start with `updated` or `n/a`.

## Trailer Map

| Trailer | File or directory | Use `updated` when |
| --- | --- | --- |
| `Changelog` | `CHANGELOG.md` | Engine behavior changed in a way users or contributors should know about. |
| `Guide` | `docs/guide/` | Player or contributor guide content changed. |
| `Known-Discrepancies` | `docs/KNOWN_DISCREPANCIES.md` | A cross-game or non-S3K intentional divergence was added, changed, or resolved. |
| `S3K-Known-Discrepancies` | `docs/S3K_KNOWN_DISCREPANCIES.md` | An S3K-specific intentional divergence was added, changed, or resolved. |
| `Agent-Docs` | `AGENTS.md` and `CLAUDE.md` | Top-level agent guidance changed. Stage both files together. |
| `Configuration-Docs` | `CONFIGURATION.md` | A config flag, key binding, startup behavior, or user-facing config default changed. |
| `Skills` | `.agents/skills/` and `.claude/skills/` | Agent skill guidance changed. Stage both mirrored trees together. |

If a mapped file is staged, the matching trailer must say `updated`. If the mapped file is not
staged, the trailer should say `n/a`.

## Changelog Rule For Engine Changes

A `feat`, `fix`, or `perf` commit that touches `src/main/` must either:

- set `Changelog: updated` and stage `CHANGELOG.md`, or
- justify the skip after `n/a`.

Examples:

```text
Changelog: updated
```

```text
Changelog: n/a: test-only helper
```

A bare `Changelog: n/a` is rejected for `feat`, `fix`, and `perf` commits that touch
`src/main/`.

## Trace Frontier Log

`docs/TRACE_FRONTIER_LOG.md` does not have its own trailer, but it is still required when trace
work changes the frontier.

Update it when:

- a trace frontier moves forward,
- a previously passing trace regresses,
- a trace fix is committed, or
- a full `*TraceReplay` sweep is used to choose the next target.

Record the command, branch or commit context, pass/fail status, error count, and first-error
frame/field. Stage the log update with the trace work it documents.

## Known Discrepancies

Update the appropriate discrepancy file when behavior intentionally diverges from ROM behavior or
when an existing known divergence is resolved:

- `docs/KNOWN_DISCREPANCIES.md` for cross-game or non-S3K-specific divergences.
- `docs/S3K_KNOWN_DISCREPANCIES.md` for S3K-specific divergences.

Do not leave intentional differences only in PR comments or commit messages.

## Configuration Docs

Update `CONFIGURATION.md` when changing any user-facing configuration key, default, key binding,
feature toggle, or startup behavior. This includes development and debug flags when contributors
are expected to use them.

## Guide Docs

Update `docs/guide/` when a change affects how users play, configure, test, debug, or extend the
engine. Prefer a short guide update over forcing future contributors to reverse-engineer a workflow
from source code.

## Merge Policy

When maintainers merge a non-`master` branch into `develop`, the merge must include a
`README.md` release/change-log summary for the branch. Merge commits skip the trailer validation
above, but this README rule still applies.

## Example Trailer Blocks

Engine fix with changelog and no other docs:

```text
Changelog: updated
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a
```

Docs-only guide change:

```text
Changelog: n/a
Guide: updated
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a
```

Engine fix that documents an intentional S3K discrepancy:

```text
Changelog: updated
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: updated
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a
```
