# Test session tooling

Supported Maven/build commands run through the coordinator so each invocation
has a lease, isolated temporary/output roots, and a retained manifest:

```bash
tools/testing/install-hooks.sh
tools/testing/test-session.sh -- mvn test
tools/testing/test-session.sh -- mvn package
```

PowerShell uses `install-hooks.ps1` and `test-session.ps1` with the same
arguments. Hook installation is explicit; Maven does not mutate Git
configuration during `validate`.

The coordinator is quiet by default. It prints compact
`OPENGGF_TEST_RUN_START` and `OPENGGF_TEST_RUN_END` markers containing the
session-owned manifest and `maven.log` paths while retaining the full child
output in that log. Search or read bounded portions of the reported log for
diagnosis. Pass `--verbose` before `--` only when interactive troubleshooting
requires live child output; `--quiet` is an accepted explicit form of the
default. The terminal manifest is authoritative for `surefire_reports`,
`trace_reports`, `diagnostics_root`, `artifact_root`, and `distribution_root`;
never read a fixed `target/` report directory to certify a run.

## Session storage lifecycle

Storage selection is fail-closed and visible. An explicit `OPENGGF_TEST_ROOT`
uses `EXPLICIT_OVERRIDE`. Otherwise a configured managed root must pass
`agent-scratch verify` and `agent-scratch reserve-test-session --json`; the
versioned reservation supplies the canonical root/allocation, tier
`MANAGED_CODEX_TEST_SESSIONS`, filesystem identity, byte snapshot,
inode-count status/value, canonical `lease_root`, retention deadline, and
helper version. Re-run `tools/agent-scratch install` after helper-source
updates, then run `agent-scratch verify`. Static verification of the installed
helper, configuration, lanes, writable-root policy, and unit-file content is
mandatory. When the sandbox cannot reach the user service bus, its runtime
timer states are visibly `UNAVAILABLE_IN_SANDBOX`; that does not invalidate
otherwise verified static state. Unknown service-manager errors and any
missing, stale, malformed, unsafe, timed-out, or failed configured helper still
fail startup and never fall through to project storage.

Only when managed scratch is not configured may the coordinator use the
visibly warned `PROJECT_LOCAL_FALLBACK`. `SYSTEM_TMP_EXPLICIT` requires
`--allow-system-tmp`. Before Maven starts, every tier must have usable bytes at
least `max(20 GiB, 5% of filesystem capacity)`. An unsigned decimal
`OPENGGF_TEST_MIN_FREE_BYTES` can raise, but never lower, that floor; a blank,
whitespace, malformed, or lower value is invalid. Every tier also performs a
live contained create/write/flush/read/unlink inode-availability probe at
launch and completion. A managed reservation reports `inode_count_status` as
`MEASURED` with numeric `usable_inodes`, where zero refuses immediately, or as
`UNAVAILABLE_DYNAMIC` with JSON-null `usable_inodes` for a dynamically
allocated inode pool, for which the live probe is authoritative rather than
treating dynamic allocation as measured exhaustion. Unmanaged tiers likewise
do not fabricate a numeric inode count.

Explicit `--lock-root` or `OPENGGF_TEST_LOCK_ROOT` remains highest priority.
Without an override, a managed reservation uses its verified `lease_root`, the
installed `$AGENT_SCRATCH_ROOT/codex/test-session-locks` lane, so an ordinary
sandboxed wrapper run never needs writable Git metadata. Only unmanaged tiers
default to the per-worktree Git lock root. The coordinator still owns namespace
creation, owner/liveness evidence, recovery, and removal; the helper only
creates and verifies the shared managed lane.

Terminal finalisation removes only `tmp` and `build/test-classes/traces` after
binding them to the manifest's verified session identity. A provider must
support descriptor-relative `SecureDirectoryStream` deletion or a non-null
stable `fileKey()` plus same-store atomic tombstoning; there is no ordinary
pathname-walk fallback. OpenJDK 21's native Windows provider exposes neither,
so native Windows runs remain certifying with
`RETAINED_PLATFORM_UNSUPPORTED` and no automatic compaction until a future
Actworks/Slipmat native file-ID bridge exists. Capacity checks and managed
retention still apply there.

Compaction preserves `manifest.json`, `command.txt`, `maven.log`, reports,
diagnostics, ordinary resources, compiled classes other than copied traces,
JAR/native/package outputs, `artifacts/`, `distribution/`, and every manifest
inventory entry. Use `--retain-ephemeral` (PowerShell:
`-RetainEphemeral`) for a diagnostic run; it records
`RETAINED_BY_REQUEST` but does not disable retention expiry. A compaction
failure changes an otherwise green run to `STORAGE_FINALIZATION_FAILED`; an
existing child or identity failure remains primary and the storage error is
additional evidence.

The start marker fields are exactly `run_id`, `isolation`, `lwjgl`,
`manifest`, `lease`, `log`, `state`, `storage_tier`, `launch_usable_bytes`, and
`capacity_floor_bytes`. They identify the session/lease/evidence paths,
isolation models, initial state and launch-capacity decision. The end marker
fields are `run_id`, `isolation`, `lwjgl`, `exit_code`, `state`, `valid`,
`manifest`, `log`, `compaction_status`, `reclaimed_bytes`, and
`completion_usable_bytes`, plus `process_tree_stopped` when shutdown handling
has that result. `state` and `valid` remain the run verdict and identity
validity; compaction fields report only storage finalisation.

Managed terminal sessions expire after seven days unless protected by a
bounded keep marker. A live `RUNNING` lease is never compacted or pruned. An
expired stale `RUNNING` session is atomically moved to quarantine, not deleted,
and receives the normal fourteen-day quarantine period.

The external acceptance harness creates disposable repositories and fake Maven
processes to exercise lease contention, linked worktrees, temporary-directory
isolation, report ownership, source/runtime mutation, interruption/reclaim, and
raw lifecycle rejection:

```bash
tools/testing/run-session-process-harness.sh
```

Harness roots are retained on failure. Set `OPENGGF_HARNESS_ROOT` to place the
disposable repository under a managed scratch directory.
