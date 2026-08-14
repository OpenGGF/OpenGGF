# Managed agent scratch storage

**Status:** approved design, awaiting implementation

**Date:** 2026-08-14

## Context

Agent work has been placing durable task state, toolchain downloads, probe
outputs, and logs in `/tmp`. On this machine `/tmp` is a 16 GiB tmpfs. The
recent incident reached 100% usage: an LLVM tree occupied about 7.5 GiB, a
Claude session scratchpad about 2.6 GiB, and older task artifacts another
5.2 GiB. The LLVM tree has since been moved and the older agent artifacts have
been moved into a dated quarantine on the disk-backed home filesystem.

Maven tests already use `target/test-tmp`, but that only bounds test-created
temporary files. It does not cover agent sessions, downloaded SDKs, BizHawk
builds, trace probes, or manually created logs.

## Goals

1. Keep agent-owned durable or large temporary data off `/tmp`.
2. Preserve normal operating-system and application use of `/tmp`.
3. Make the correct location easy to discover from either Codex or Claude.
4. Retain enough history to recover an accidental cleanup without making the
   scratch area unbounded.
5. Keep committed instructions machine-neutral; the actual scratch root is a
   per-user setting exposed as `OGGF_SCRATCH_ROOT`.
6. Make setup and cleanup observable and safe to repeat.

## Non-goals

- Do not change the system-wide `/tmp` mount, size, or permissions.
- Do not relocate repository worktrees, Maven targets, ROMs, or user data.
- Do not automatically delete arbitrary paths in `/tmp`.
- Do not make the repository depend on one developer's absolute home path.

## Recommended architecture

The machine-local root is `$OGGF_SCRATCH_ROOT`, defaulting in the helper to
`$HOME/scratch/agent-tmp`. Its layout is:

```text
$OGGF_SCRATCH_ROOT/
  claude/       Claude Code's internal temporary root
  codex/        Codex subprocess temp files and sandbox-writable support data
  openggf/
    tasks/      task-specific probes, downloads, reports, and captures
  quarantine/   material moved out of an unsafe or stale location
```

The root and its children are mode `0700`. Every task directory is unique and
named with a human label plus a timestamp/random suffix. The helper refuses to
operate on a path outside the resolved root when pruning or quarantining.

### Claude

Set the user-level Claude Code `env.CLAUDE_CODE_TMPDIR` value to the resolved
`$OGGF_SCRATCH_ROOT/claude` path. Claude Code creates its own UID-specific
child below that path. Existing sessions are not migrated in place; they are
left alone until their normal retention window expires, and new sessions use
the disk-backed root after Claude is restarted.

### Codex

Set the user-level Codex shell environment policy for `TMPDIR`, `TMP`, and
`TEMP` to `$OGGF_SCRATCH_ROOT/codex/tmp`, add the Codex scratch directory as a
writable root, and set `sandbox_workspace_write.exclude_slash_tmp = true`.
The `$TMPDIR` exclusion remains disabled so commands can use the configured
disk-backed path. This is scoped to the local Codex configuration, not checked
into the repository.

The setting prevents ordinary agent shell commands from silently recreating
large task trees in `/tmp`. Small host-created sandbox bookkeeping entries may
still briefly appear there; those are not task storage and are expected to be
ephemeral.

### Repository workflow

Add a small `tools/agent-scratch` helper with these operations:

- `new LABEL`: create and print a unique task directory under
  `$OGGF_SCRATCH_ROOT/openggf/tasks`;
- `path KIND`: print a well-known child path for scripts;
- `status`: show filesystem free space and per-child usage;
- `prune [--dry-run]`: remove only old entries beneath the managed root,
  honoring the retention policy below.

The root `AGENTS.md` and `CLAUDE.md` will state that `/tmp` is for short-lived
OS-level files only. Agent tasks must use the helper or
`$OGGF_SCRATCH_ROOT/openggf/tasks`. Existing skill examples that direct trace
or probe output to `/tmp` will be changed to use the same environment variable.

### Retention and cleanup

Install a user-level `systemd-tmpfiles` policy and daily cleanup timer for the
managed root. The policy is age-based and only applies below
`$OGGF_SCRATCH_ROOT`:

| Area | Retention | Rationale |
| --- | ---: | --- |
| `openggf/tasks` | 7 days | Reprobes and reports are usually disposable. |
| `quarantine` | 14 days | Allows recovery after an accidental move. |
| `codex` | 30 days | Holds reusable build and command support data. |
| `claude` | 30 days | Avoids deleting resumable sessions too aggressively. |

Cleanup never scans or deletes arbitrary `/tmp` entries. Before the first
cleanup, the helper reports candidates in a dry-run mode. An active task is
protected by recent modification time; the conservative retention windows are
deliberately much longer than a normal command.

## Data flow

1. A session starts with the local Claude/Codex configuration and receives the
   disk-backed paths.
2. An agent creates a task directory with `tools/agent-scratch new` and sends
   tool outputs, downloads, and captures there.
3. Scripts use the helper's `path` operation rather than hard-coded `/tmp`
   paths.
4. `status` provides a quick capacity check before large captures or builds.
5. The daily user cleanup removes only entries older than their area-specific
   retention period. Material intentionally preserved is moved outside the
   managed root before cleanup.

## Failure handling and safety

- If `$OGGF_SCRATCH_ROOT` is unset, the helper uses a disk-backed `$HOME`
  fallback and prints the chosen path.
- If the root cannot be created or has insufficient free space, `new` fails
  clearly rather than falling back to `/tmp`.
- Prune validates that every candidate is below the managed root and never
  follows symlinks outside it.
- Quarantine is a move, not an irreversible delete. The dated quarantine is
  itself subject to the 14-day policy.
- Configuration changes are idempotent and preserve unrelated Claude/Codex
  settings.

## Verification

Implementation verification will cover:

1. shell syntax and helper dry-run tests, including path traversal rejection;
2. configuration parsing for the Claude JSON and Codex TOML updates;
3. a synthetic task directory proving creation, status accounting, and age
   pruning without touching `/tmp`;
4. an audit of the mirrored `AGENTS.md`/`CLAUDE.md` and targeted skill
   examples;
5. the repository's required baseline and post-change test comparisons.

## Alternatives considered

### Documentation only

Portable and cheap, but it leaves Claude's internal scratchpad on `/tmp` and
depends on every agent remembering the rule. It does not meet the prevention
goal.

### Redirect without cleanup

Moving Claude and Codex output to disk solves the tmpfs outage, but the new
root becomes an unbounded archive. It postpones the same failure onto the home
filesystem and provides no recovery policy.

The managed root, tool configuration, helper, and age-based user cleanup are
the smallest combination that addresses both recurrence and retention.

## References

- Claude Code environment variables: <https://code.claude.com/docs/en/env-vars>
- Codex configuration reference: <https://developers.openai.com/codex/config-reference>
