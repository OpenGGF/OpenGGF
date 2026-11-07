# Documentation And Branch Policy

This page explains the documentation and commit-message policy that applies to normal development
branches. It is written for contributors; the hook scripts in `.githooks/` are the enforcement
source of truth.

## Branch Names

Normal contributions use focused branches from `develop`. For delegated work in an
existing checkout, use its current branch as the base unless another is specified.
Create task branches in separate worktrees; never switch the main workspace branch:

- `feature/ai-...` for new features.
- `bugfix/ai-...` for fixes.

Keep one logical task per branch when practical. If a branch spans unrelated systems, split it
before opening a PR.

## Git Hooks

Tracked hooks live in `.githooks/`. Maven never mutates Git configuration during `validate`
or any other phase; install the hooks explicitly once per worktree:

```bash
tools/testing/install-hooks.sh      # PowerShell: tools/testing/install-hooks.ps1
```

The script sets `core.hooksPath` to `.githooks` for that worktree. Check the result with
`git config core.hooksPath`.

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

Each value must start with `updated` or `n/a`. Complete blocks for common
changes are under [Example Trailer Blocks](#example-trailer-blocks).

## Trailer Map

| Trailer | File or directory | Use `updated` when |
| --- | --- | --- |
| `Changelog` | `CHANGELOG.md` | The root release index or development-line pointer changed. Per-version notes alone use `n/a` with their destination as the reason. |
| `Guide` | `docs/guide/` | Player or contributor guide content changed. |
| `Known-Discrepancies` | `docs/status/known-discrepancies.md` | A cross-game or non-S3K intentional divergence was added, changed, or resolved. |
| `S3K-Known-Discrepancies` | `docs/S3K_KNOWN_DISCREPANCIES.md` | An S3K-specific intentional divergence was added, changed, or resolved. |
| `Agent-Docs` | `AGENTS.md` | Top-level agent guidance changed. `AGENTS.md` is the only project instruction file; a staged `CLAUDE.md` is rejected. |
| `Configuration-Docs` | `CONFIGURATION.md` | A config flag, key binding, startup behavior, or user-facing config default changed. |
| `Skills` | `.agents/skills/` | Agent skill guidance changed. Edit `.agents/skills/`, regenerate the `.claude/skills/` copy with `python3 tools/git/sync_skills.py` and stage both; the staged copy must match exactly. |

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

## Release Note Destination

`pom.xml` selects the engine release line, and new notes go in the
`CHANGELOG.<version>.md` it names: `develop` (0.8.prerelease) writes
`CHANGELOG.0.8.prerelease.md` and `next` (0.9.prerelease) writes
`CHANGELOG.0.9.prerelease.md`. `CHANGELOG.0.7.md` is the frozen 0.7 release file.
The root `CHANGELOG.md` holds the release index. For an engine change whose note is in the versioned file:

```text
Changelog: n/a: release note recorded in CHANGELOG.0.8.prerelease.md
```

Fold fixes to an unreleased feature into its existing entry. Keep commit hashes,
frame numbers and detailed validation in the appropriate engineering record.
The [documentation checklist](../../agent-workflow/documentation-obligation-checklist.md)
and [release rollover](../../project/release-rollover.md) describe the destinations.

## AI-Assisted Commits

End the message with a `Co-authored-by` trailer for each contributing model,
including its name and version. Put documentation trailers before that final
block, separated by one blank line; keep multiple co-author lines contiguous.
For example:

```text
Co-authored-by: OpenAI Codex GPT-6 <noreply@openai.com>
```

Use the checked preparation entry point for ordinary AI-assisted commits from an
existing non-master branch. First stage and review the intended files, then write
the complete UTF-8 message with LF line endings to a file outside the staged scope. Run from the repository
root, listing every staged path literally, including deletions and both sides of
renames:

```bash
python3 tools/testing/checked_commit.py --message-file /tmp/commit-message.txt \
  --co-author 'OpenAI Codex GPT-6.1 <noreply@openai.com>' \
  -- tools/testing/checked_commit.py docs/guide/contributing/documentation-policy.md
```

Replace the example paths with the complete reviewed staged scope. Repeat
`--co-author` for each contributor in final-block order. The final block must
contain only those contiguous `Co-authored-by` lines, preceded by a blank line.
The seven policy lines belong before that separator. Git's
`interpret-trailers --parse` can return only the final block, so it is not a
replacement for policy validation.

<details>
<summary>What the checked commit helper verifies, and its limits</summary>

The helper snapshots the exact message bytes, compares literal paths with the
index, checks the final parsed coauthors, and invokes `.githooks/run-policy
commit-msg`. That existing dispatcher validates the seven policy lines and
staged content. Any failed prerequisite aborts before `git commit` is invoked.
It then checks that the message, branch, HEAD, and staged tree did not change and
runs ordinary `git commit --cleanup=verbatim --file` with the snapshot. Normal
hooks still run and may reject the commit. The helper does not stage files,
accept arbitrary Git flags, or support initial, merge, or amend commits.

Install the repository hooks first. Do not run concurrent Git writers in the
same checkout. Drift checks do not provide atomic isolation against other
processes, and hooks retain their normal authority to modify the index or
message during Git's commit lifecycle. Existing policy restrictions on unusual
filenames still apply. A nonzero result means no successful commit was reported;
inspect Git status before retrying.

Run the boundary's disposable-repository controls with:

```bash
python3 -m unittest discover -s tools/testing -p 'test_run_category_checked_commit.py' -v
```

</details>

## Trace Frontier Log

`docs/status/trace-frontier-log.md` does not have its own trailer, but it is still required when trace
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

- `docs/status/known-discrepancies.md` for cross-game or non-S3K-specific divergences.
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

Merge commits skip the trailer validation above. The `README.md` release section is a
handful of version themes, not a change log: a merge into `develop` edits it only when it adds
or changes a theme, and most merges leave it alone. A fix to work that has not yet shipped folds
into the existing changelog entry and earns no README text. No hook or CI check requires a
README change on a merge.

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
