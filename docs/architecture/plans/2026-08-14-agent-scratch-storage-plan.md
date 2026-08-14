# Managed Agent Scratch Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep Codex, Claude Code, and OpenGGF diagnostic work off the 16 GiB
`/tmp` tmpfs, while providing bounded, recoverable cleanup on the user's
disk-backed home filesystem.

**Architecture:** Add one dependency-free Python helper at
`tools/agent-scratch` that resolves and validates `$OGGF_SCRATCH_ROOT`, creates
task directories, reports capacity, writes bounded keep markers, prunes only
managed children using descriptor-relative no-follow traversal, and installs
the per-user Claude/Codex configuration plus a daily systemd user timer. The
repository documents the helper as the sole location for durable agent output;
the actual root remains machine-local and is never committed.

**Tech Stack:** Python 3.11+ standard library (`argparse`, `fcntl`, `json`,
`tomllib`, `os` descriptor APIs, and `/proc/self/mountinfo` parsing on Linux),
POSIX user systemd, Claude Code `settings.json`, Codex TOML configuration,
shell/Lua/Python documentation examples, and the existing repository
hook/test workflow.

## Global Constraints

- Never write agent-owned durable output to `/tmp`; `/tmp` remains available
  only for short-lived OS-level files.
- The root must be absolute, user-owned, mode `0700`, outside `/tmp`, and on a
  non-`tmpfs`/non-`ramfs` filesystem. Missing roots are created only after all
  lexical and filesystem checks pass; failure never falls back to `/tmp`.
- All managed traversal and deletion is descriptor-relative with
  `O_NOFOLLOW`; `new` and `prune` share an advisory lock. Symlink swaps,
  traversal labels, malformed keep markers, and unsafe roots fail closed.
- Claude and Codex configuration edits are idempotent, preserve unrelated
  settings, write absolute materialized paths, and never contain a literal
  `$OGGF_SCRATCH_ROOT` TOML interpolation.
- User-level service files are generated at install time and are not committed
  to the repository. Installation must use the canonical checkout path and be
  rerun if that checkout moves.
- Keep the mirrored `AGENTS.md`/`CLAUDE.md` files byte-identical and update
  mirrored `.agents/skills`/`.claude/skills` content together.
- Do not stage ROMs, generated links, machine-local configuration, quarantine
  contents, or test output.

---

## Task 1 — Implement the managed helper and its tests

**Files:**

- `tools/agent-scratch`
- `tools/test_agent_scratch.py`

- [ ] Add an executable Python helper with a `main(argv)` dispatcher and these
  public operations: `new LABEL`, `path KIND`, `status`,
  `keep PATH --until YYYY-MM-DD`, `prune [--dry-run]`, `install`, and
  `verify`. Keep the script runnable from any working directory by resolving
  its repository root independently of the current directory.
- [ ] Implement root/layout validation and creation: resolve the environment
  value or `$HOME/scratch/agent-tmp`, reject relative/control-character/
  `/tmp`/unsafe-symlink roots, check owner/mode and filesystem type by parsing
  Linux `/proc/self/mountinfo` (longest decoded mount-point match; reject
  `tmpfs` and `ramfs`), create `claude`, `codex/tmp`, `openggf/tasks`, and
  `quarantine` as `0700`, and expose the selected absolute root in command
  output. Include a parser fixture for accepted ext4/btrfs and rejected
  tmpfs/ramfs mounts.
- [ ] Implement safe directory primitives using `os.open(..., O_DIRECTORY |
  O_NOFOLLOW)`, `dir_fd` operations, `os.scandir(fd)`, and `lstat`-style
  metadata. Do not use path-based recursive deletion or follow directory
  symlinks. Put the shared `fcntl.flock` lock under the validated root and
  hold it while selecting/creating/removing entries.
- [ ] Implement `new` label validation (single bounded component, no slash,
  traversal, or control characters), unique UTC timestamp/PID/random naming,
  and creation under `openggf/tasks` with mode `0700`.
- [ ] Implement `path` for the named `claude`, `codex`, `codex-tmp`,
  `tasks`, and `quarantine` children, rejecting unknown kinds and creating
  only the documented child when needed.
- [ ] Implement `keep` with an exact marker format, ISO-date validation, no
  expired/far-future (over 30 days) dates, descriptor-relative marker writes,
  and a clear error for paths outside the managed root or non-directories.
- [ ] Implement `status` with free bytes, free inodes, per-area byte counts,
  active-process protection state, keep-marker count, nearest expiry, and any
  last failed cleanup-unit error available from `systemctl --user`.
- [ ] Implement `prune` age policies (tasks 7 days, quarantine 14 days,
  Codex 30 days, Claude 30 days), dry-run reporting, unexpired keep-marker
  protection, live Claude/Codex process protection, malformed/far-future marker
  rejection, and safe descriptor-relative recursive removal. The command must
  never scan or delete arbitrary `/tmp` entries.
- [ ] Implement `install` as an idempotent host setup: resolve/validate the
  root, update `~/.claude/settings.json` env values (`OGGF_SCRATCH_ROOT` and
  `CLAUDE_CODE_TMPDIR`) while preserving its existing mode, update
  `~/.codex/config.toml` `shell_environment_policy.set`
  values (`OGGF_SCRATCH_ROOT`, `TMPDIR`, `TMP`, `TEMP`) plus
  `sandbox_workspace_write` (`exclude_slash_tmp = true`,
  `exclude_tmpdir_env_var = false`, and the absolute Codex writable root),
  preserving unrelated keys and rejecting conflicting existing managed values.
  Use a narrow comment-preserving TOML table editor: recognize existing
  `[shell_environment_policy.set]` and `[sandbox_workspace_write]` tables,
  parse existing assignments with `tomllib`, update only managed keys, append
  the Codex root to (rather than replace) an existing `writable_roots` array,
  preserve comments/unrelated nested keys, deduplicate on repeat runs, and
  atomically replace the `0600` Codex config. Detect dotted-key and inline-table
  representations of either managed table before writing and fail clearly
  without modifying the file unless they are explicitly supported. Test
  comments, existing roots, unrelated keys, dotted/inline unsupported forms,
  and conflicting managed values.
- [ ] Have `install` materialize an absolute `EnvironmentFile` at
  `~/.config/oggf-agent-scratch/environment` (`0600`), generate the user files
  `~/.config/systemd/user/openggf-agent-scratch-prune.service` and
  `~/.config/systemd/user/openggf-agent-scratch-prune.timer`, and quote the
  canonical checkout helper path with a dedicated systemd `ExecStart` C-style
  quoting routine (escaping whitespace, quotes, backslashes, and control
  characters; do not rely on `systemd-escape --shell`). Run
  `systemd-analyze --user verify` on both generated files, daemon-reload,
  enable/start the timer, and verify enabled state plus a next trigger. If the
  user manager is unavailable, leave files intact and report the exact
  activation command rather than claiming cleanup is active. Reject unexpected
  non-regular config/unit/environment targets before replacing them, and write
  all generated files atomically.
- [ ] Add `verify` checks for config parsing, absolute child temp variables,
  root confinement, systemd unit syntax, and a fresh sandboxed Claude command.
  If Claude is installed and the command runs, return nonzero when observed
  `$TMPDIR` escapes the configured root; if it is unavailable or cannot run
  because authentication/session state is missing, report `unverified` and do
  not claim the Claude portion passed. Explicitly report that Codex
  `danger-full-access` cannot be constrained by workspace sandbox settings.
- [ ] Add a standard-library test script that creates a unique
  `TemporaryDirectory(dir=$HOME/.cache/oggf-agent-scratch-tests)` (creating
  that `0700` parent first, asserting the mount is accepted, and removing the
  child in `finally`; never use `tempfile`'s `/tmp` default) and covers syntax,
  root rejection, labels/traversal,
  static symlink rejection, an actual symlink-swap race, concurrent
  `new`/`prune` locking, status accounting, bounded keep markers, dry-run and
  real pruning, config idempotence/parsing, systemd `ExecStart` quoting with a
  space-containing checkout path, and the no-touch guarantee for an unrelated
  sentinel outside the managed root.

**Verification:**

```bash
python3 -m py_compile tools/agent-scratch tools/test_agent_scratch.py
python3 tools/test_agent_scratch.py
tools/agent-scratch path tasks
tools/agent-scratch status
```

## Task 2 — Document the storage contract and remove live `/tmp` output recipes

**Files:**

- `AGENTS.md`
- `CLAUDE.md`
- `.agents/skills/s1-trace-replay/SKILL.md`
- `.claude/skills/s1-trace-replay/SKILL.md`
- `.agents/skills/trace-replay-bug-fixing/SKILL.md`
- `.claude/skills/trace-replay-bug-fixing/SKILL.md`
- `docs/agent-workflow/README.md`
- `docs/agent-workflow/runbooks/runbook-multi-agent-trace-orchestration.md`

- [ ] Add the machine-neutral scratch policy to both agent instruction files:
  use `tools/agent-scratch new/path`, keep output under
  `$OGGF_SCRATCH_ROOT/openggf/tasks`, reserve `/tmp` for short-lived OS files,
  run `status` before large captures, and use bounded `keep` or a normal
  archive for material that outlives retention. Keep the files byte-identical.
- [ ] Replace every live trace regeneration/probe output example that points
  at `/tmp` with the helper-managed task path, including shell snippets and
  Windows guidance. Keep explanatory warnings about why `/tmp` is unsafe, but
  do not leave a copyable command that creates durable output there.
- [ ] Replace the multi-agent benchmark runbook's `mktemp -d /tmp/...` retention
  directory with a helper-created task directory and document the required
  `OGGF_SCRATCH_ROOT` preflight.
- [ ] Add an Agent Workflow README section documenting host setup
  (`tools/agent-scratch install`), verification, daily cleanup semantics,
  active-process/keep protections, and the fact that existing sessions are
  audited rather than migrated in place.
- [ ] Run a repository-wide audit of `.agents/skills`, `.claude/skills`,
  runbooks, ignored scratch locations, and executable tooling; classify every
  remaining `/tmp` reference as policy text, OS-level short-lived use, or an
  intentional test/guard rather than an output destination.

**Verification:**

```bash
cmp -s AGENTS.md CLAUDE.md
rg -n '/tmp' .agents/skills .claude/skills docs/agent-workflow tools
git diff --check
```

## Task 3 — Exercise host configuration and cleanup on the real machine

- [ ] After the helper is present on the integration branch, run
  `tools/agent-scratch install` from the canonical main checkout so the user
  service does not point at a temporary worktree. Preserve and inspect the
  existing Claude/Codex settings before and after the idempotent update.
- [ ] Restart Claude Code and start a fresh Codex process, then run the helper's
  verification path. Confirm child processes receive absolute
  `OGGF_SCRATCH_ROOT`, `TMPDIR`, `TMP`, and `TEMP`; confirm Claude's actual
  sandbox `$TMPDIR` is within the configured root. An installed Claude that
  runs but reports an outside path is a failed setup; an unavailable or
  unauthenticated Claude is explicitly `unverified`, not a success claim. Also
  record any unavailable user-manager limitation rather than weakening the
  guard.
- [ ] Run `tools/agent-scratch prune --dry-run`, inspect all candidates, then
  rely on the enabled timer for cleanup. Do not delete arbitrary `/tmp`
  contents; preserve active `/tmp/claude-1000` or current task state until a
  fresh session has been verified.

## Task 4 — Required repository integration verification

- [ ] Before integration, fetch and fast-forward `develop` without discarding
  user changes, run the full Maven baseline on JDK 21, and record exact output
  and failures under the managed task root.
- [ ] Run the focused helper/documentation checks plus the full Maven suite in
  the development worktree; distinguish pre-existing baseline failures from
  regressions.
- [ ] Merge the feature branch into the main-workspace `develop` branch without
  switching the main workspace, rerun the full Maven suite and the focused
  checks, compare with the baseline, and stage the required README merge
  summary.
- [ ] Push only `develop`, verify the pushed commit, remove the clean completed
  worktree, delete the fully merged local feature branch, and prune stale
  worktree metadata. Preserve and report any unknown user-authored worktree
  changes instead of deleting them.
