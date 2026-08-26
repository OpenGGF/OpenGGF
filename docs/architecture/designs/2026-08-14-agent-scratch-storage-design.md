# Managed agent scratch storage

**Status:** implemented

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
   per-user setting exposed as `AGENT_SCRATCH_ROOT`.
6. Make setup and cleanup observable and safe to repeat.

## Non-goals

- Do not change the system-wide `/tmp` mount, size, or permissions.
- Do not relocate repository worktrees, Maven targets, ROMs, or user data.
- Do not automatically delete arbitrary paths in `/tmp`.
- Do not make the repository depend on one developer's absolute home path.

## Recommended architecture

The machine-local root is `$AGENT_SCRATCH_ROOT`, defaulting in the helper to
`$HOME/scratch/agent-tmp`. Its layout is project-agnostic:

```text
$AGENT_SCRATCH_ROOT/
  claude/       Claude Code's internal temporary root
  codex/        Codex subprocess temp files and sandbox-writable support data
  tasks/        task-specific probes, downloads, reports, and captures
  quarantine/   material moved out of an unsafe or stale location
```

The root and its children are mode `0700`. Every task directory is unique and
named with a human label plus a timestamp/random suffix. The helper refuses to
operate on a path outside the resolved root when pruning. Moving an explicitly
audited source into `quarantine` remains a separate, operator-approved move;
the helper never scans arbitrary `/tmp` paths.

The repository's `tools/agent-scratch` is tracked bootstrap/source, not a
runtime dependency. Run `tools/agent-scratch install` from any source checkout
to install the stable user-wide `$HOME/.local/bin/agent-scratch`; routine work
in every project invokes `agent-scratch`. Re-run the source install command
after helper updates. The installer-generated systemd service invokes the
installed copy, never a checkout or worktree. `$HOME/.local/bin` must be on the
user's `PATH`. `OGGF_SCRATCH_ROOT` remains only a compatibility alias when it
matches `AGENT_SCRATCH_ROOT`; new configuration uses the generic name.

### Claude

Set the user-level Claude Code `env.AGENT_SCRATCH_ROOT` value to the resolved
absolute root and `env.CLAUDE_CODE_TMPDIR` to its `claude` child. Claude Code
creates its own UID-specific child below that path. The installer uses the
deliberately short default root and requires the actual `$TMPDIR` observed by
a sandboxed Claude Bash command to be inside that root; if Claude selects the
system-default fallback, setup fails and asks for a shorter root. Existing
sessions are not migrated in place; they are left for explicit
audit/quarantine, with no promise that they will expire automatically, and
new sessions use the disk-backed root after Claude is restarted.

### Codex

Resolve the root once during setup and write the resulting absolute path to
the user-level Codex shell environment policy for `AGENT_SCRATCH_ROOT`,
`TMPDIR`, `TMP`, and `TEMP`, using `$AGENT_SCRATCH_ROOT/codex/tmp`. Add the
resolved Codex scratch directory as a writable root and set
`sandbox_workspace_write.exclude_slash_tmp = true`.
The `$TMPDIR` exclusion remains disabled so commands can use the configured
disk-backed path. No unresolved `$AGENT_SCRATCH_ROOT` reference is written into
TOML: Codex does not interpolate shell variables in these literal values.

These sandbox settings constrain Codex commands only when the session uses
`sandbox_mode = "workspace-write"`. They cannot prevent `/tmp` writes in
`danger-full-access`/unrestricted sessions, so the repository rule and helper
remain the cross-mode control. Verification covers both modes explicitly.

In workspace-write sessions this prevents ordinary agent shell commands from
silently recreating large task trees in `/tmp`. Small host-created sandbox
bookkeeping entries may still briefly appear there; those are not task storage
and are expected to be ephemeral.

### Repository workflow

The installed `agent-scratch` helper has these operations (the repository
`tools/agent-scratch` source is used only to bootstrap or update it):

- `new LABEL`: create and print a unique task directory under
  `$AGENT_SCRATCH_ROOT/tasks`;
- `path KIND`: print a well-known child path for scripts;
- `status`: show filesystem free space and per-child usage;
- `keep PATH --until YYYY-MM-DD`: mark a task directory as retained until a
  bounded expiry (at most 30 days from the command date);
- `prune [--dry-run]`: remove only old entries beneath the managed root,
  honoring the retention policy below and skipping unexpired keep markers.

Indefinite preservation is intentionally not supported inside the managed
root: material needed longer than the keep maximum must be moved to a normal
disk-backed archive outside this root. `status` reports the keep-marker count
and nearest marker expiry.

The root `AGENTS.md` and `CLAUDE.md` state that `/tmp` is for short-lived
OS-level files only. Agent tasks use the installed helper or
`$AGENT_SCRATCH_ROOT/tasks`; trace and probe examples use the same environment
variable.

### Retention and cleanup

Install a dedicated user-level `agent-scratch-prune.service` and daily timer
that invokes the installed helper's `prune` operation. Setup resolves
`AGENT_SCRATCH_ROOT` once and writes that absolute value to a user
`EnvironmentFile`; the service therefore does not depend on shell expansion.
Setup materializes `$HOME/.local/bin/agent-scratch` in the unit, validates that
it remains executable, runs `systemd-analyze verify`, and retires any legacy
OpenGGF-named generated unit. Re-running the source `tools/agent-scratch
install` after helper updates replaces the stable installed copy; neither
service nor timer references a checkout or worktree. The service acquires the
helper lock, skips the `claude` subtree while a Claude process is running,
skips the `codex` subtree while a Codex process is running, and skips any task
directory containing an unexpired keep marker. `status` surfaces a failed
cleanup unit and its last error. The service is enabled and its next trigger is
verified during installation. The timer uses `Persistent=true`, so a missed run
is performed when the user manager starts again; cleanup otherwise requires the
user manager to be active. Enabling `loginctl enable-linger` is an explicit,
optional choice for cleanup while logged out, not a setup default.

The helper applies these age limits only below the managed root:

| Area | Retention | Rationale |
| --- | ---: | --- |
| `tasks` | 7 days | Reprobes and reports are usually disposable. |
| `quarantine` | 14 days | Allows recovery after an accidental move. |
| `codex` | 30 days | Holds reusable build and command support data. |
| `claude` | 30 days | Avoids deleting resumable sessions too aggressively. |

Cleanup never scans or deletes arbitrary `/tmp` entries. Before the first
cleanup, the helper reports candidates in a dry-run mode. A task is protected
by an unexpired keep marker, and tool-internal trees are protected while their
own process is running; mtime is used only to decide whether a candidate is
old enough after those protections. Long-running work must use a bounded
`keep` expiry rather than relying on a heartbeat that a quiet process may not
produce. Prune rejects malformed markers and markers expiring more than 30
days in the future instead of trusting hand-edited metadata.

## Data flow

1. A session starts with the local Claude/Codex configuration and receives the
   disk-backed paths.
2. An agent creates a task directory with `agent-scratch new` and sends
   tool outputs, downloads, and captures there.
3. Scripts use the helper's `path` operation rather than hard-coded `/tmp`
   paths.
4. `status` provides a quick capacity check before large captures or builds.
5. The daily user cleanup removes only entries older than their area-specific
   retention period. Material intentionally preserved is keep-marked or moved
   outside the managed root before cleanup.

## Failure handling and safety

- If `$AGENT_SCRATCH_ROOT` is unset, the helper uses a disk-backed `$HOME`
  fallback and prints the chosen path. Setup materializes the resolved path in
  the user service environment file.
- Setup rejects relative roots, roots containing control/newline characters,
  roots that resolve through unsafe symlinks, and roots under `/tmp`. It
  verifies that the resolved filesystem is not `tmpfs`/`ramfs`, and that the
  root is owned by the current user with mode `0700`, before writing any local
  configuration.
- If the root cannot be created or written, `new` fails clearly rather than
  falling back to `/tmp`. `status` reports free bytes and inodes; callers may
  require a minimum free-byte reserve before large captures.
- Prune validates that every candidate is below the managed root and never
  follows symlinks outside it. `new` and `prune` acquire the same lock while
  selecting or removing candidates. Traversal is descriptor-relative and
  no-follow; symlinked task roots and path-traversal labels are rejected even
  if another same-user process attempts a symlink swap during pruning.
- Quarantine is a move, not an irreversible delete. The dated quarantine is
  itself subject to the 14-day policy.
- Configuration changes are idempotent and preserve unrelated Claude/Codex
  settings.

## Verification

Implementation verification covers:

1. helper syntax and dry-run tests, including relative/control-character
   roots, path traversal, static symlinks, an actual symlink-swap/TOCTOU race,
   and concurrent `new`/`prune` locking;
2. configuration parsing for the Claude JSON and Codex TOML updates, with
   absolute `AGENT_SCRATCH_ROOT` and temp paths verified in Claude/Codex child
   processes;
3. a synthetic task directory proving creation, status accounting, bounded
   keep-marker expiry, and age pruning without touching `/tmp`;
4. a user-timer installation check proving the service is enabled and has a
   next trigger;
5. a sandboxed Claude command check that fails setup if the actual `$TMPDIR`
   escapes the configured root, plus Codex workspace-write and
   unrestricted-mode checks documenting the latter's deliberate limitation;
6. an audit of all live `/tmp` output instructions across the mirrored
   `.agents`/`.claude` skills, relevant runbooks, and existing ignored scratch
   locations, while keeping `AGENTS.md` and `CLAUDE.md` byte-identical;
7. the repository's required baseline and post-change test comparisons.

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
