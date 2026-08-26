# Test-session isolation and result validity

## Status

Approved in discussion as the direction for implementation and amended after
independent review. This document specifies the implementation boundary; no
runtime or build changes are made by the design commit.

The terminal allocation, capacity, retention, and compaction contract is
defined by the later
[session storage and worktree lifecycle safety addendum](2026-08-26-session-storage-and-worktree-lifecycle-safety.md).
That addendum supersedes this document's original root-selection and cleanup
assumptions without changing the session identity, lease, source-validity, or
evidence ownership contracts below.

## Motivation

OpenGGF's test workflow currently has several independent shared-state hazards:

- Maven and Surefire can create temporary files in the Maven JVM's system
  temporary directory before Surefire's child-JVM `argLine` is applied. The
  existing `target/test-tmp` setting therefore does not protect the Maven
  process itself.
- Maven compiler output, `target/test-classes`, Surefire XML reports, trace
  divergence reports, and many diagnostic captures use shared `target` paths.
  Two Maven invocations in one worktree can overwrite classes or make one run
  consume the other run's reports.
- Trace and CI consumers assume fixed paths such as
  `target/trace-reports`, so stale or partial reports can look like current
  evidence.
- A test result can be numerically complete but still invalid if the worktree
  branch, commit, or source files changed during the run.
- The sandbox used by agents may make `/tmp` or the managed scratch root
  unwritable. A run must select a verified writable root or fail before Maven
  starts producing partial evidence.

The current POM's temporary-directory configuration and the trace harnesses'
fixed report paths are useful containment measures, but they do not define an
owner for a complete test run. The owner is the missing abstraction.

## Goals

The test workflow will provide a named **test session** with these guarantees:

1. At most one lifecycle-running test/build session owns a given worktree.
2. Every session has a unique, discoverable output root.
3. Maven, Surefire forks, compiler output, reports, and generated diagnostics
   use the session's paths.
4. A session reports its start identity and end validity in both human-readable
   logs and a machine-readable manifest.
5. A result is invalid if its source identity changed or if the process did not
   reach a trustworthy terminal state.
6. A read-only system temporary directory produces a clear early error or is
   bypassed by the selected writable session root; it never causes a long,
   misleading partial suite.
7. Linked worktrees can run in parallel because the lease is scoped to the
   worktree, not the repository's common Git directory.

## Non-goals

- This does not make unsafe gameplay singleton state safe inside one JVM. The
  existing Surefire fork policy and singleton-reset tests remain responsible
  for that.
- This does not change test pass/fail semantics or make known-red trace tests
  green.
- This does not permanently archive every generated report. Retention and
  promotion of diagnostic evidence remain separate concerns.
- This does not allow trace data to hydrate gameplay state or alter any trace
  authority contract.

## Session layout

The default session root is outside Maven's normal `target` directory so that a
clean of the legacy output directory cannot delete an active session's
evidence. As amended by the terminal-storage addendum, the resolver uses:

1. An explicit `OPENGGF_TEST_ROOT` override. If it is present but not absolute,
   writable, or owned by the current user, startup fails; it never silently
   falls through to another root.
2. A verified versioned `agent-scratch reserve-test-session --json` allocation
   in `MANAGED_CODEX_TEST_SESSIONS` when managed scratch is configured. Any
   configured helper/install/verification/reservation failure fails closed;
   project fallback is forbidden.
3. The visibly labelled ignored project-local `.openggf/test-runs` directory,
   only when managed scratch is not configured.
4. A verified writable system temporary directory only when explicitly enabled
   by the caller with `--allow-system-tmp`.

The project-local fallback is ephemeral test output, not a replacement for
durable agent artifacts. A report that must outlive normal retention is copied
to the managed scratch/archive location by the caller.

The managed schema includes canonical allocation and `lease_root` paths. Its
`inode_count_status` is `MEASURED` with numeric `usable_inodes`, or
`UNAVAILABLE_DYNAMIC` with JSON-null `usable_inodes` for a dynamic inode pool.
Static helper/configuration/lane/unit-file verification remains mandatory in a
sandbox; an unreachable user service bus is recorded separately as runtime
state `UNAVAILABLE_IN_SANDBOX`, while unknown service-manager errors fail.

Every tier applies the addendum's pre-launch floor of
`max(20 GiB, 5% of filesystem capacity)`, with
`OPENGGF_TEST_MIN_FREE_BYTES` able only to raise it, and its all-tier live inode
probe. A measured zero refuses immediately; a dynamic unavailable count relies
on the live probe and is never treated as zero. Terminal compaction is limited to `tmp` and
`build/test-classes/traces`; `--retain-ephemeral` retains them for diagnosis.

Each session is created with an ID containing a UTC timestamp, the launcher PID,
and a cryptographically random suffix, for example:

```text
20260823T101530Z-p4812-a7f93c
```

The launcher uses atomic directory creation. A timestamp alone is not a unique
identity because two invocations can share a clock tick or the clock can move.

```text
<session-root>/<run-id>/
  manifest.json
  command.txt
  maven.log
  tmp/                         # Maven and fork-JVM temporary files
  build/                       # Maven project.build.directory
    surefire-reports/
    trace-reports/
    ...
  diagnostics/
```

The run manifest starts in `RUNNING` state and ends in exactly one of
`PASSED`, `FAILED`, `INVALID_IDENTITY_CHANGED`, `ABORTED`, `STARTUP_FAILED`, or
`STORAGE_FINALIZATION_FAILED`.
An orphaned `RUNNING` manifest is retained for diagnosis and is never silently
reused.

## Worktree lease and supported entrypoints

The launcher acquires an atomic lease under a root selected independently from
the output root. An explicit `--lock-root` or `OPENGGF_TEST_LOCK_ROOT` has
highest priority. Otherwise a managed reservation supplies the verified
`<AGENT_SCRATCH_ROOT>/codex/test-session-locks` root, allowing the exact default
wrapper to operate inside Codex's writable sandbox boundary. Unmanaged tiers
fall back to the per-worktree Git metadata directory returned by
`git rev-parse --git-dir`:

```text
<selected-lock-root>/
  openggf-test-session.lock[-<worktree-hash>]/
    lease.lock                         # regular file held with Java FileChannel/FileLock
    owner.json
```

Both the Git and managed locations survive `git clean -fdx`; external/managed
namespaces include a stable worktree hash so linked worktrees remain distinct.
There is no deletable in-worktree lease fallback. If neither a verified managed
lease root, explicit external root, nor writable per-worktree Git metadata is
available, startup fails closed before Maven.

`owner.json` records the run ID, process ID, host, worktree path, start time,
command, branch, and starting `HEAD`. If the directory already exists, the
launcher prints the owner information and exits before starting Maven.

The lease namespace is deliberately an atomically published directory rather
than a PID-only file. PID files are vulnerable to stale processes, PID reuse,
and partial writes. The creator first makes a uniquely named staging directory
beside the canonical namespace, writes `initializing.json` with the run ID, PID,
host, worktree, command, and start time, and atomically moves that complete
directory into `openggf-test-session.lock`. No empty canonical namespace is
ever exposed. `lease.lock` is the regular-file ownership primitive: after
publication the coordinator creates it with `CREATE_NEW`, acquires an
exclusive Java `FileChannel`/`FileLock`, and only then writes `owner.json`
through a temporary file followed by an atomic move. `initializing.json` stays
until `owner.json` is visible, so a crash in the post-lock/pre-owner window has
recoverable owner metadata. A contender that sees `initializing.json` without
`owner.json` waits briefly for initialization to finish, then reports the
startup state and exits without reclaiming or deleting anything. If the creator
dies before the lock or owner exists, `--reclaim` verifies the recorded process
is gone, creates `reclaiming.json` with `CREATE_NEW`, and renames the namespace
to a retained stale name; a live initializing process is never reclaimed. Once
`owner.json` exists, a contender reports the owner without touching it. A stale
lease may be inspected and explicitly reclaimed by a separate command after
checking the recorded process and manifest.

Normal coordinators never create `lease.lock` inside a published namespace that
still has only `initializing.json`; only the creator that published that
namespace may finish its initialization. For lockless recovery, the reclaimer
claims `reclaiming.json` with `CREATE_NEW` before renaming. This atomic claim is
the recovery mutex: competing reclaimers lose the create race, and normal
coordinators refuse both `initializing.json` and `reclaiming.json` states.
Process liveness checks use the recorded PID and process start identity where
the platform exposes it, so a stale marker is never reclaimed merely because a
PID number is absent.

The staging-directory publication and every canonical namespace rename require
`StandardCopyOption.ATOMIC_MOVE`; `AtomicMoveNotSupportedException` is a
fail-closed startup/reclaim error, with the staging or marked namespace
retained for diagnosis. There is no non-atomic fallback.

Reclaim uses a cross-platform two-stage protocol. When a lock exists, the
reclaimer creates `reclaiming.json` while holding the same `FileLock`, with
`CREATE_NEW`, recording
its own PID, host, start time, and target run ID, and flushes it. It then closes
the channel before renaming the namespace to a retained stale name. Every
normal coordinator checks for `reclaiming.json` before acquiring the lease and
checks it again immediately after acquiring the lock; if the marker appeared,
it releases the lock and retries or exits without starting Maven. The
reclaimer creates that marker while holding the lock, so Windows' inability to
rename an open locked file cannot create a close-then-rename race. If a
reclaimer dies after writing the marker, a later explicit reclaim verifies that
the recorded reclaimer process is gone, reacquires the target lock, confirms
the target owner is stale, and resumes the close-before-rename sequence. A
failed rename leaves the marker for that explicit retry; no automatic deletion
occurs. POSIX follows the same sequence for one deterministic protocol on both
platforms.

All contention/recovery loops use one exact policy: attempt 1 starts
immediately, followed by at most three retry attempts after 50 ms, 100 ms, and
200 ms sleeps. A retryable condition is a lock conflict, a newly observed
`reclaiming.json`, an incomplete initialization marker, or a transient failure
to acquire/revalidate the target namespace; non-retryable validation errors
fail immediately. A normal coordinator performs no Maven launch when any
attempt observes a reclaim or incomplete-initialization marker; after the
fourth failed attempt it exits 75 (`EX_TEMPFAIL`) with no Maven manifest.

Explicit reclaim attempt 1 atomically creates `reclaiming.json` with
`CREATE_NEW`. If it already exists, the reclaimer reads its recorded PID,
process-start identity, host, and target run ID: a live owner is retryable
contention, while a dead owner may be resumed by the explicit reclaim command.
The marker remains in place across retries, and only one reclaim command can
create it. On every attempt, reclaim revalidates the target owner and lease;
when `lease.lock` exists it acquires the lock, checks `reclaiming.json` again,
then closes the channel before each `ATOMIC_MOVE` rename. A lockless recovery
uses the already-created marker as its mutex and atomically renames only after
the recorded initializing process is revalidated dead. If all four attempts
fail, reclaim exits 75 and leaves every marker and namespace retained for a
later explicit invocation.

No selected lease namespace uses common repository metadata. Explicit and
managed external lock roots key their namespace by the canonical worktree path;
the unmanaged Git-local path is already the linked worktree's own metadata
directory. This permits independent linked worktrees to run concurrently.
Managed external storage is the default for a managed reservation, while an
explicit external root is selected only by `--lock-root` or
`OPENGGF_TEST_LOCK_ROOT`.

The launcher owns the lease for the entire Maven invocation, including `clean`,
`compile`, `test-compile`, `test`, `verify`, `package`, and report aggregation. It holds an
exclusive `FileChannel`/`FileLock` on the lease for the full child-process
lifetime; a second coordinator cannot acquire it. All documented test/build
entrypoints use the launcher.

The launcher also generates a random session capability, writes it to an
owner-readable file, and passes only that file's path to Maven. The POM's
`validate` and `pre-clean` guards read the same active manifest and capability,
so `mvn clean test` authorizes both phases without consuming a one-time value.
They verify the canonical worktree, lease path, run ID, command hash, and
`RUNNING` state before allowing a lifecycle phase. The capability file uses
owner-only permissions on POSIX and an owner-only ACL on Windows.

The lease and capability are an accidental-concurrency and stale-path guard,
not a hostile same-user security boundary. A process that deliberately copies
another process's readable capability can imitate its properties; it still
cannot acquire the coordinator's exclusive lease, and any result from an
unwrapped or manually redirected process is non-certifying. Preventing a
privileged same-user process from reading or imitating a local test session is
outside this feature's scope and would require OS-specific peer credentials.

Direct plugin goals are not supported test/build entrypoints. They are not
treated as certifying runs and must be invoked through the coordinator when
they create or inspect project outputs. Maven cannot intercept every arbitrary
direct goal from the POM, so the design does not claim that an unwrapped
`mvn exec:java` can be blocked by lifecycle validation. The supported policy
boundary is the coordinator plus lifecycle guards, and CI/release evidence is
accepted only from a coordinator manifest. IDE inspection has a separate
allowlisted mode for model/help goals and cannot unlock lifecycle phases.

The isolation guarantee is intentionally scoped to coordinator-launched
`clean`, `compile`, `test`, `test-compile`, `verify`, and `package` lifecycles
and to direct goals only when the coordinator launches them. An unwrapped
`surefire:test`, `exec:java`, or other output-producing direct goal is an
unsupported escape path: it may use legacy shared paths, is not protected by
the session lease, and can never produce certifying evidence. The contributor
guides call this out explicitly so the limitation is visible rather than
mistaken for a hidden guarantee.

The four normal local launchers are a deliberate non-certifying exception:
they pass `-Dopenggf.session.guard.skip=true` so the distributable remains in
`target/` and can be launched normally. That switch does not make arbitrary
Maven lifecycles supported or produce certifying evidence.

`clean` has one unambiguous policy for certifying workflows: an unwrapped
`mvn clean` is rejected by a `pre-clean` session guard before Maven's clean
plugin deletes `target`. A coordinator-launched `clean` is allowed only when
its capability names the current lease and command. A second coordinator
cannot acquire that lease. The active session therefore survives the
rejection, and its external build/report/package roots provide defense in
depth if an unrelated tool attempts to remove the legacy `target` directory.
An explicitly provisioned external lock root is checked at session
finalization; if it disappears, the session is marked invalid rather than
silently accepted. Only the normal local launchers may opt out of this guard,
and they do not run `clean`.

## Lifecycle

The session launcher performs these operations in order:

1. Resolve and probe the session root. The probe creates and removes a file and
   directory, so a path that merely exists but is not writable is rejected.
2. Allocate the unique run directory using an atomic create operation.
3. Acquire the worktree lease and write the initial manifest.
4. Capture source and runtime-input identity:
   - canonical worktree path;
   - branch and `HEAD`;
   - a deterministic content digest of every tracked and non-ignored untracked
     file, including path, size, and bytes; this catches changes to existing
     untracked files whose Git status line would otherwise remain unchanged;
   - staged and unstaged Git status and diff hashes for diagnostics;
   - hashes of declared ignored runtime inputs, including supplied ROMs,
     `config.yaml`/`config.json`, local mods, and other files named by the
     active launcher arguments;
   - Java and Maven versions;
   - requested Maven profiles and arguments;
   - ROM paths and hashes when supplied.
5. Generate the session capability and set the session environment before
   launching Maven. `MAVEN_OPTS` and `JAVA_TOOL_OPTIONS` preserve existing
   options and append the session `-Djava.io.tmpdir=<session>/tmp`; the session
   directory is also assigned to `TMPDIR`, `TMP`, and `TEMP` for Maven,
   Surefire, shell, ffmpeg, packaging, and other child processes. The POM passes
   the same session directory to Surefire forks through `argLine`, because the
   parent and child JVMs have different startup timing. Each Surefire fork also
   receives `-Dorg.lwjgl.system.SharedLibraryExtractPath=<session>/tmp/lwjgl-<fork>`.
   This prevents concurrent forks, worktrees, or CI jobs from replacing one
   another's extracted LWJGL natives while preserving a common session-owned
   parent temp root. The prior values are
   recorded in the manifest and are changed only in the coordinator's child
   environment.
6. Pass the session build directory, Surefire report directory, trace report
   directory, and diagnostic root as explicit Maven/system properties.
   The manifest path is passed as `openggf.session.manifest`; it is the
   authoritative input for both lifecycle guards.
   The coordinator also passes the guard identity properties
   `openggf.session.manifest`,
   `openggf.session.run-id`, `openggf.session.capability`,
   `openggf.session.command-hash`, `openggf.session.worktree`,
   `openggf.session.lease-path`, and
   `openggf.session.allowed-phases`. `openggf.session.command-hash` is the
   SHA-256 of the canonical child argv, joined with NUL separators; the
   allowed phase list is derived from the supported lifecycle command before
   Maven starts. The POM exposes these as system properties to the guard
   invocation, and a missing or inconsistent value is a rejection.
7. Capture Maven output in `maven.log` without streaming it to the console by
   default. Emit a single-line `OPENGGF_TEST_RUN_START` marker before Maven and
   a corresponding `OPENGGF_TEST_RUN_END` marker after it; both markers name
   the manifest and log paths. An explicit `--verbose` option additionally
   streams child output for interactive human troubleshooting, while `--quiet`
   remains an accepted explicit form of the default.
8. Determine the terminal state from the Maven exit code, the terminal
   `BUILD SUCCESS`/`BUILD FAILURE` marker, and source identity.
9. Capture the final source identity, report inventory, exit code, duration,
   and terminal state in the manifest.
10. Release the lease only after the final manifest write succeeds. If the
    process is interrupted, the manifest remains visibly incomplete and the
    next invocation reports it rather than treating it as a passing run.

The end marker contains at least `run_id`, exit code, terminal state,
`source_unchanged`, `runtime_inputs_unchanged`, `valid`, duration, and absolute
manifest/report paths.
The JSON manifest is authoritative; log markers are for quick scanning and
incident correlation.

## Maven and test-output integration

The POM gains overridable properties for:

- the Maven build directory;
- the Surefire report directory;
- the session temporary directory;
- the trace report directory;
- the general test diagnostic root; and
- the packaged artifact and distribution roots.

The default values preserve the current `target` layout for ordinary project
inspection, but the session launcher always supplies run-local values. All
Surefire profiles (`default`, `guards`, trace profiles, benchmarks, and CI)
must consume the shared properties rather than independently reconstructing
`target/test-tmp`.

Trace harnesses currently returning `Path.of("target", "trace-reports")` or
`Path.of("target/trace-reports")` are moved behind one test-output path
resolver. The resolver receives the session property and retains the existing
default only when no session is active. Visual, performance, rewind, audio,
and other generated test outputs follow the same resolver where they write
files rather than merely describing a fixture path. The migration is complete
only when the fixed-path writer inventory is empty or an entry is explicitly
classified as an input/default assertion.

Every generated report has both a logical key and a unique physical path. The
physical path includes the session, profile, writer identity, and test
invocation identity. The canonical owner key is:

```text
profile / fully-qualified-test-class / test-method / parameter-index /
  invocation-id / lane-id / logical-report-key
```

`parameter-index` is `0` for a non-parameterized test. `invocation-id` is the
first-class identity for a JUnit invocation: the first 16 lowercase hexadecimal
characters of the SHA-256 of the
JUnit 5 `ExtensionContext.getUniqueId()` (which includes template, repeated,
parameterized, and dynamic-test indexes). Non-JUnit writers must supply an
equivalent stable invocation ID; a writer without one cannot use a shared
report path. `lane-id` is the manifest/run-chain lane or an explicit
special-stage/segment identity; it is never a process ID or frame number. When
Surefire exposes a fork number, it is recorded as diagnostic metadata, not used
as the sole identity. The resolver sanitizes this owner key and writes a
physical file below the session report root. It opens the path with
`CREATE_NEW`; a collision is a hard failure, not a last-writer-wins replacement.

The manifest contains a `reports` array with `logical_key`, `owner_key`,
`physical_path`, `kind`, and `status` for every report. Writers do not mutate
the manifest concurrently. Each writer first creates its report with
`CREATE_NEW`, then atomically publishes an adjacent owner metadata file under
the same report root. After Maven exits, the coordinator scans only those
metadata files, verifies that each referenced report is complete, and builds
the manifest array. A missing, duplicate, or malformed metadata entry makes
the session invalid. A required logical key must resolve to exactly one owner;
multiple owners require an explicit aggregation entry. Trace profiles run with
one Surefire fork until all report writers carry the stable lane identity
above; after that migration, parallel forks remain allowed because the path
contract, not fork count, provides uniqueness. CI resolves required logical
reports through the manifest rather than assuming a basename.

CI and release workflow checks stop globbing a shared historical directory.
They read the session manifest and inspect the report directories named there.
This preserves the existing coverage and warning policies while making stale
reports impossible to include accidentally. `TestBuildToolingGuard`,
`TraceTriageTool`, and all release/CI scripts consume the manifest's paths.

The coordinator's manifest has a stable top-level handoff shape:

```json
{
  "run_id": "...",
  "state": "PASSED",
  "manifest": "<absolute path>",
  "worktree": "<canonical path>",
  "lease_path": "<absolute path>",
  "source_digest": "<sha256>",
  "runtime_inputs_digest": "<sha256>",
  "build_root": "<absolute path>",
  "tmp_root": "<absolute path>",
  "surefire_reports": "<absolute path>",
  "trace_reports": "<absolute path>",
  "diagnostics_root": "<absolute path>",
  "artifact_root": "<absolute path>",
  "distribution_root": "<absolute path>",
  "reports": [],
  "artifacts": []
}
```

The coordinator accepts an optional `--export-file` path. On successful or
failed completion it writes UTF-8, newline-terminated `key=value` records for
the manifest, run id, and all handoff roots: `manifest`, `run_id`, `build_root`,
`tmp_root`, `surefire_reports`, `trace_reports`, `diagnostics_root`,
`artifact_root`, and `distribution_root`. Paths containing carriage returns or
newlines are rejected at startup, so no additional escaping is needed. The
manifest remains the authoritative record; the exported root fields are a
workflow convenience for shell steps that package or inspect the same run.
GitHub Actions passes `$GITHUB_OUTPUT` or its PowerShell equivalent to this
option, so later steps consume `${{ steps.<session>.outputs.manifest }}`. Local
invocations receive the same path in the end marker and stdout. No workflow
step falls back to `target/surefire-reports` or `target/trace-reports`.

The supported CI invocation is explicit on both shells. POSIX jobs use
`tools/testing/test-session.sh --export-file "$GITHUB_OUTPUT" -- mvn ...`;
Windows jobs use `tools/testing/test-session.ps1 -ExportFile $env:GITHUB_OUTPUT
-- mvn ...`. The
coordinator owns the child environment and writes the absolute manifest before
returning Maven's result code. Report/assertion steps receive the manifest as a
workflow output and resolve `surefire_reports`, `trace_reports`,
`artifact_root`, and `distribution_root` from it.

Native packaging and distribution scripts stop passing literal
`${basedir}/target`/`dist` paths. They consume the session artifact and
distribution roots, and release jobs publish only files listed by the
manifest. The session acceptance suite includes `mvn package -Pnative
-DskipTests` on each supported platform class where the platform is available.

The migration is intentionally staged, but the final contract is not declared
complete until all generated-output writers and package consumers use session
paths. The first stage supplies the lease, validated capability, parent-JVM temp
override, and fail-fast manifest. It serializes legacy writers while they
are being migrated; it does not claim that a raw clean is harmless to those
legacy paths. The second stage moves compiler/package/report roots and every
generated test output into the session root, after which a legacy `target`
cleanup cannot affect an active supported session.

## Source validity

A dirty worktree at session start is allowed; agents often need to test local
changes. It is recorded as part of the starting identity. The result is valid
only when the ending identity matches it exactly. This catches both a concurrent
commit and an unstaged source edit.

Ignored generated output under the session root is excluded from the source
fingerprint. Declared ignored runtime inputs are included in a separate
`runtime_inputs_digest`; any change to tracked files, untracked source/config
files, staged state, branch, `HEAD`, ROM, config, or mod input invalidates the
run even if all tests pass. If a runtime input cannot be identified or hashed,
the run is non-certifying rather than silently valid.

This rule prevents a passing report from being attributed to a different source
revision. It also means a user may continue editing after a run begins, but
must rerun the suite before using that result as release evidence.

## Repository workflow contract

After implementation, the supported commands in `AGENTS.md`, `CLAUDE.md`,
`README.md`, and contributor/testing/trace guides use the coordinator examples,
not raw `mvn test` or `mvn package` commands. `AGENTS.md` and `CLAUDE.md` are
updated together and remain byte-identical where their mirrored guidance is
intended to match. Raw Maven lifecycle commands are documented as unsupported
and non-certifying; the lifecycle guards reject them when they reach the
project unless a normal local launcher has explicitly selected its
non-certifying bypass. That bypass exists only to preserve the interactive
`target/` package-and-launch workflow; it is not used by tests, traces, CI, or
release commands. A separate explicit hook-bootstrap command installs
`.githooks` once and does not run from Maven `validate`.

The agent-facing isolation contract is explicit in both mirrored instruction
files. Codex and Claude must enter through the POSIX or PowerShell session
wrapper for certifying builds, tests, trace replays, and captures. The wrapper's
session-owned temporary root is the sandbox boundary: Maven, Surefire, reports,
diagnostics, and every fork's LWJGL native extraction directory remain below
that run's root. Parallel agents use separate worktrees and wrapper sessions;
they never share a temporary or LWJGL extraction directory. The start/end
markers (`OPENGGF_TEST_RUN_START` and `OPENGGF_TEST_RUN_END`) and their manifest
are required evidence. The markers expose the session log path, and agents use
targeted searches or bounded reads of that quiet-by-default log instead of
streaming or replaying it wholesale into context. Raw Maven lifecycle
commands—including the non-certifying launcher path—are not evidence.
The ordinary test profile excludes structural guards; release validation runs
the separate `-Pguards` profile through a fresh wrapper session so whole-
production ArchUnit imports cannot retain their graph behind the long ordinary
suite.

The storage addendum extends but does not reinterpret those markers. Start adds
`storage_tier`, `launch_usable_bytes`, and `capacity_floor_bytes` alongside the
existing run/isolation/evidence-path fields. End adds `compaction_status`,
`reclaimed_bytes`, and `completion_usable_bytes` alongside the existing
exit/state/identity verdict. Storage finalisation can turn an otherwise green
run into `STORAGE_FINALIZATION_FAILED`, but it cannot replace an existing child
or identity failure as the primary result.

## Failure handling and cleanup

- No writable root: emit `STARTUP_FAILED`, explain each candidate path, and do
  not invoke Maven.
- No verified managed lease root, writable per-worktree Git metadata, or
  explicit external lock root: emit `STARTUP_FAILED` and do not use a
  project-root lock that destructive cleanup can remove.
- Existing lease: emit the owner manifest and exit without touching build
  outputs, reports, or the active session.
- Maven startup failure: retain the manifest and Maven log as
  `STARTUP_FAILED` or `FAILED`; do not infer test counts.
- Missing terminal marker: mark `ABORTED`, regardless of whether the process
  exit code is zero.
- Source or declared runtime-input identity changed: mark
  `INVALID_IDENTITY_CHANGED` and return nonzero.
- Stale session: managed terminal sessions use seven-day retention unless kept;
  a live `RUNNING` lease is preserved, while an expired stale `RUNNING` session
  is atomically moved to the fourteen-day quarantine lane rather than deleted.
- Terminal storage: preserve manifest, command/log, reports, diagnostics,
  artifacts, distributions, package output, ordinary resources, and manifest
  inventories. Remove only the addendum's two allowlisted reproducible trees.
- Unsupported destructive identity: retain without mutation as
  `RETAINED_PLATFORM_UNSUPPORTED`. In particular, native Windows on OpenJDK 21
  remains certifying and retained pending an Actworks/Slipmat native file-ID
  bridge; its capacity and retention policies still apply.

The launcher does not run a broad recursive pathname deletion. Terminal
compaction requires descriptor-relative secure streams or a stable-key,
same-store atomic tombstone strategy; uncertainty retains evidence. Maven no
longer installs Git hooks by mutating shared `.git/config` during `validate`;
hook installation is an explicit bootstrap command. A read-only Git
configuration therefore does not produce a build-side mutation attempt or hide
behind a non-fatal Maven error.

## Verification contract

The implementation is complete only when these checks exist:

1. Two sessions started concurrently in one worktree: one owns the lease, the
   other exits before Maven and reports the owner; the first session's classes,
   reports, and manifest remain intact. A stale or mismatched capability is
   rejected; a second coordinator cannot join or redirect the active session.
2. Two sessions in separate linked worktrees: both start and complete without
   sharing output paths.
3. Maven and Surefire run with the system temporary directory made unusable:
   all session-managed temporary files land under the writable session root,
   with no `/tmp/surefire-*` attempt.
4. Concurrent trace writers in one Maven invocation, including multiple
   Surefire forks where enabled, produce distinct physical reports and a
   complete logical-key manifest without last-writer-wins behavior.
5. A test writes a trace divergence report: it lands under the session report
   root, and a second run cannot consume it.
6. `HEAD`, a tracked file, an existing untracked source file, staged state, or
   a declared ignored runtime input changes during a run: the end marker and
   manifest are invalid, and the launcher returns nonzero.
7. The process is interrupted: the manifest remains `RUNNING`/`ABORTED`, the
   next run reports the orphan, and no stale report is counted as current.
8. Raw `mvn clean`, `compile`, `test-compile`, `test`, `verify`, and `package` without a valid
   capability fail before mutating outputs; IDE model inspection remains
   available only through the allowlisted mode. Direct plugin goals are tested
   as non-certifying unless invoked through the coordinator.
9. An active final-stage session remains intact when a separate process attempts
   raw `mvn clean`: the pre-clean guard rejects that command before deletion,
   and the session's build, report, and package roots are outside `target` as
   defense in depth. The worktree lease prevents supported concurrent lifecycle
   commands.
10. Existing focused guards and the full default/trace suites produce the same
   test semantics when their reports are read through the session manifest.

The process-level cases are not ordinary in-process JUnit tests. A disposable
external integration harness creates a temporary Git repository/worktree,
installs a controllable fake Maven executable, and exercises concurrent
processes, interruption, read-only temp roots, source mutation, stale leases,
raw lifecycle commands, and cleanup. Pure manifest, digest, path, and lock
helpers have ordinary unit tests. POSIX and Windows wrappers share the same
manifest contract and each has a native process-harness job.

## Expected implementation surface

The first implementation plan should cover:

- a cross-platform JDK-21 session coordinator under `tools/testing/`, with
  `tools/testing/test-session.sh` and `tools/testing/test-session.ps1` thin
  POSIX and PowerShell entrypoint wrappers;
- explicit `.gitignore` exceptions for `tools/testing/` and its contents,
  because the current `tools/*` rule ignores new tools;
- a shared session/manifest/capability model and process-tree shutdown logic;
- the cross-platform file-lock lease and capability validator used by Maven's
  `validate` and `pre-clean` guards;
- POM properties and the lifecycle validation guard;
- the test-output path resolver and trace harness migration;
- CI/release report-consumer migration;
- native packaging/distribution root migration;
- explicit Git-hook bootstrap and documentation of the no-mutation Maven path;
- ignored session-root configuration;
- focused unit tests plus the disposable external process harness and
  Linux/Windows validation;
- synchronized `AGENTS.md`/`CLAUDE.md` updates, testing/dev-setup/trace guide
  updates, and documentation updates for every supported Maven entrypoint;
- removal of Maven's automatic `.git/config` mutation and an explicit,
  standalone hook-bootstrap command.

No engine gameplay code, trace schema, ROM loading rule, or release gate is
changed by this design.
