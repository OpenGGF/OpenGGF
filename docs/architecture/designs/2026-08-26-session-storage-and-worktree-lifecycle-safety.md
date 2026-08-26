# Session storage and worktree lifecycle safety

## Status

Approved in discussion as an immediate OpenGGF containment delivery. This
document defines the implementation boundary; it does not implement the
changes. The generic toolset is intentionally deferred to the future
`OpenGGF/Actworks` project, where `cowtree` remains the copy-on-write worktree
provisioner and `Slipmat` will own generic scratch/session lifecycle.

## Incident and motivation

The root filesystem reached 97% utilisation even though no single active run
needed that capacity. The immediate recovery removed only validated generated
`tmp` directories and copied trace resources from terminal test sessions. It
recovered roughly 526 GiB and left manifests, Maven logs, reports,
diagnostics, distributions, promoted artifacts, active sessions, and Git state
intact.

The incident exposed two ownership gaps:

1. A test session has a strong execution and evidence contract, but no terminal
   storage contract. It allocates hundreds of MiB of reproducible data and
   waits for age-based pruning even after reaching a terminal state.
2. Agent rounds create worktrees as isolated subprojects, but the workflow has
   contradictory persistence rules and no single command that proves a tree is
   safe to retire.

These are lifecycle failures, not one-off cleanup failures. The containment
delivery must prevent recurrence without deleting unique work or weakening the
certifying test-session boundary.

## Goals

The delivery will:

1. Prevent a configured managed-scratch allocation failure from silently
   falling back to `.openggf/test-runs`.
2. Allocate agent test sessions in a Codex-sandbox-writable managed lane and
   verify the allocation contract before Maven starts.
3. Enforce a pre-launch capacity floor before a test session can consume more
   storage.
4. Compact narrowly defined reproducible data after every terminal session
   while retaining authoritative evidence and promoted outputs.
5. Make storage selection and finalisation visible in the manifest and the
   start/end markers.
6. Replace ambiguous worktree persistence guidance with a safe, auditable
   stand-down workflow and a repository-owned lifecycle command.
7. Preserve clean boundaries so the generic pieces can later move to
   Actworks/Slipmat without changing OpenGGF's test semantics.

## Non-goals

- This delivery does not create Actworks or Slipmat.
- It does not rename `agent-scratch` inside OpenGGF. The eventual extraction
  will carry the new product name; the containment change preserves the
  installed command for compatibility.
- It does not change gameplay, trace comparison, PLC readiness, or hardware
  timing authority. `Cuepoint` remains reserved for that engine domain.
- It does not automatically delete dirty worktrees, unknown filesystem
  directories, unmerged branches, or user-created artifacts.
- It does not make age-based retention the only protection against a full
  filesystem.
- It does not archive or compress every build output. Material that must
  outlive bounded scratch retention must still be explicitly promoted or kept.

## Constraints and source-of-truth boundaries

- All certifying Maven work continues to enter through
  `tools/testing/test-session.sh` or `test-session.ps1`.
- `TestSessionCoordinator` continues to own run identity, worktree leasing,
  child-process supervision, terminal state, and evidence manifests.
- `agent-scratch` owns managed-root validation, allocation, and retention.
- `TestSessionCoordinator` owns narrowly allowlisted finalisation inside the
  unique session directory it created, for every storage tier.
- Git owns registered worktree and branch state. Filesystem inspection may add
  orphan candidates, but it may not override Git's registered state.
- The official Codex sandbox contract uses
  `sandbox_workspace_write.writable_roots` for narrow additional writable
  roots. Environment variables alone do not prove sandbox writability.
- `.git`, `.agents`, and `.codex` remain protected even when a parent is a
  writable root. The design does not broaden or bypass those protections.
- OpenGGF's existing test-session evidence and identity invariants remain
  authoritative.

## Architecture

The containment delivery has three components with deliberately narrow
interfaces:

```text
agent-scratch
  reserve-test-session --json
          |
          v
TestSessionCoordinator
  resolve -> verify -> capacity gate -> run -> finalize -> compact

tools/worktree-lifecycle
  audit [roots...]
  retire <registered-worktree>
```

`TestSessionCoordinator` never deletes a complete session tree. It compacts
only fixed descendants of a manifest-bound terminal session that it created.
The worktree tool may query the coordinator's lease owner/liveness contract,
but it does not mutate or delete test-session lease namespaces; coordinator
reclaim semantics remain separate.

## Managed session allocation

### Storage lanes

`agent-scratch install` creates and manages:

```text
<AGENT_SCRATCH_ROOT>/
  codex/
    tmp/
    test-sessions/
  tasks/
  quarantine/
```

Codex already receives `<AGENT_SCRATCH_ROOT>/codex` as an additional writable
root. Placing test sessions below `codex/test-sessions` makes allocation,
curation, compaction, and diagnostics available inside the same enforced
sandbox boundary. General durable task artifacts remain under `tasks`; the
test-session lane does not replace `agent-scratch new`.

The installer must create the lane with the same ownership, mode, canonical
path, non-symlink, non-tmpfs, and filesystem checks used for existing managed
areas. Re-running install is idempotent and upgrades existing installations.

Managed test sessions have a seven-day default retention period. Terminal
sessions older than that are eligible for the existing manifest-aware prune
path unless protected by a bounded keep marker. A `RUNNING` session with a live
lease owner is never compacted or pruned. A stale `RUNNING` session older than
the retention period is atomically moved to the managed quarantine lane for
its existing fourteen-day quarantine period; it is not deleted directly.

### Structured allocation contract

The coordinator calls a structured helper operation rather than parsing the
last line of `agent-scratch new`:

```text
agent-scratch reserve-test-session --json
```

On success it returns exactly one JSON object containing at least:

- schema version;
- canonical managed root;
- canonical allocation path;
- storage tier `MANAGED_CODEX_TEST_SESSIONS`;
- filesystem device identity;
- usable bytes at allocation time;
- inode-count status `MEASURED` or `UNAVAILABLE_DYNAMIC`, with a numeric
  usable-inode value only when measured;
- canonical managed lease root `<root>/codex/test-session-locks`;
- retention deadline;
- helper version.

The helper atomically creates a unique allocation directory below the exact
managed lane. It performs a same-directory create, write, flush, atomic rename,
read, and unlink probe before reporting success. It rejects symlink traversal,
foreign ownership, unsafe modes, a changed filesystem identity, or a path
outside the lane.

Routine reservation must fit the installed Codex writable-root boundary. It
may read and validate the managed root and existing `codex` ancestors, but it
must not create or lock `.agent-scratch.lock`, `tasks`, `quarantine`, or any
other root-level path. Installation owns that broader layout. Reservation opens
the already-installed `codex/test-sessions` lane descriptor-relatively,
validates every ancestor, and serializes allocation by locking the lane
directory descriptor (or a lock contained inside the writable lane). A sandbox
that grants write access only to `<root>/codex` must be able to verify and
reserve without elevated access; a missing/unsafe lane remains fail-closed.

Installation also creates `<root>/codex/test-session-locks`. A managed
reservation returns that canonical lease root as part of the same verified
schema. The coordinator's explicit `--lock-root`/environment override retains
highest priority; otherwise a managed allocation uses this writable managed
lease root rather than the sandbox-protected Git common directory. Namespace
creation, owner publication, liveness, recovery, and deletion remain owned by
the coordinator's existing lease protocol. The helper creates/verifies the
lane but never prunes coordinator lock metadata.

The coordinator independently canonicalises the result, confirms containment
below the configured lane, confirms ownership and directory type, and creates
its run directory atomically. A syntactically valid helper response is not by
itself trusted.

### Root-selection policy

The resolver uses these rules:

1. An explicit `OPENGGF_TEST_ROOT` remains highest priority. An invalid
   override fails closed and never falls through.
2. If managed scratch is configured through `AGENT_SCRATCH_ROOT` (or the
   supported legacy variable), the helper must be installed, verified, and
   able to allocate the managed Codex session lane. Missing, timed-out,
   malformed, unsafe, or failed allocation is a startup error. Project-local
   fallback is forbidden in this state.
3. If managed scratch is not configured, an unmanaged human environment may
   use `.openggf/test-runs`, but the start marker and manifest must label it
   `PROJECT_LOCAL_FALLBACK` and print an actionable installation warning. The
   fallback is never silent.
4. System temporary storage remains opt-in through `--allow-system-tmp` and is
   labelled `SYSTEM_TMP_EXPLICIT`.

This preserves a usable contributor path while making managed agent failures
unmistakable.

## Capacity guardrail

After allocation verification and before publishing a `RUNNING` manifest or
launching Maven, the coordinator obtains usable capacity from the allocation's
actual filesystem. The default required headroom is:

```text
max(20 GiB, 5% of filesystem capacity)
```

`OPENGGF_TEST_MIN_FREE_BYTES` may raise this floor for a larger run. It may not
lower or disable the default. The value must be an unsigned decimal integer;
invalid values are startup errors.

The launch is refused when either usable bytes are below the floor or inode
availability cannot be proved. Java has no portable API for a live numeric
free-inode count on every supported filesystem, so this proof has two parts:

- when the managed helper reports `MEASURED`, a numeric allocation-time inode
  snapshot is required, zero refuses immediately, and the snapshot is retained
  as observability;
- filesystems such as Btrfs report both total and free inode counters as zero
  because their inode pool is dynamic. The helper reports those as
  `UNAVAILABLE_DYNAMIC` with JSON `null`, never as measured exhaustion;
- for every storage tier, immediately before launch the coordinator performs a
  contained create, write, file flush, read, and unlink probe inside the unique
  session directory. It also flushes the directory where the platform supports
  opening directories for durability. Failure of the portable file probe
  refuses launch; lack of directory-flush support records
  `DIRECTORY_FLUSH_UNSUPPORTED` observability and does not masquerade as inode
  exhaustion. Success records `AVAILABLE` without pretending it measured a
  numeric count.

Unmanaged tiers record their numeric inode count as unavailable with an exact
reason, never as a fabricated value. The same live availability probe runs at
completion and is recorded independently. It is a point-in-time guard, not a
reservation of a future inode.

Once an allocation directory exists, the coordinator records a capacity or
probe refusal in a `STARTUP_FAILED` manifest before returning. A failure to
measure capacity is itself fail-closed and uses the last verified allocation
snapshot plus a bounded diagnostic to publish terminal evidence; failure of a
completion probe never strands the manifest in `RUNNING`. The error reports
the allocation path, storage tier, usable capacity, required capacity, and the
non-destructive commands to inspect or prune managed storage. There is no
`--allow-low-disk` bypass for certifying runs.

An explicitly present but blank or whitespace-only
`OPENGGF_TEST_MIN_FREE_BYTES` is invalid. Every string placed in a start/end
marker, including configured lock paths, is encoded or rejected so control
characters cannot create counterfeit marker lines.

Installed-helper verification must also be usable inside the managed Codex
sandbox. Static verification of helper bytes, configuration, lane ownership,
and unit-file content remains mandatory. When `systemctl --user` cannot reach
the user bus, both known diagnostic families (including “failed to connect to
bus” and “failed to connect to user scope bus”) record runtime service state as
`UNAVAILABLE_IN_SANDBOX` and do not invalidate otherwise verified static
state. Unknown service-manager errors, stale helper/configuration, missing
lanes, and wrong writable roots still fail closed.

Byte capacity and live inode availability are measured again during
finalisation and recorded even when the child process fails. The guardrail is
not a reservation guarantee; it is an early refusal that prevents a
known-low-capacity run from worsening the incident.

## Terminal session compaction

### Authority and timing

The coordinator first determines the child result and validates run identity.
It then writes a terminal pre-compaction manifest and applies the same
compaction algorithm to managed, explicit, project-local, and explicitly
selected system-temporary tiers.

The coordinator accepts only the unique session directory it created, with a
supported manifest schema and a terminal state. It binds every candidate path
to the manifest's canonical session root, refuses symlinks and mount
crossings, and operates descriptor-relative where the platform supports it.
It refuses `RUNNING`, an unknown state, a replaced directory, or an
out-of-scope path. The storage tier changes allocation and retention policy,
not the terminal compaction allowlist.

Deletion has two platform strategies with the same fail-closed result schema:

- When `SecureDirectoryStream` is available, traversal and deletion stay
  relative to identity-checked open parent descriptors.
- On providers without secure directory streams but with a stable public file
  key, the
  coordinator first binds and preflights every candidate, creates an
  identity-bound private staging lane inside the session on the same file
  store, atomically moves one candidate at a time to an unpredictable
  tombstone name, and verifies that the moved file key is the bound candidate
  key. It then walks the tombstone without following symbolic links or reparse
  points and revalidates every ancestor identity immediately before each
  operation. A swap moves the replacement itself, never follows it; any
  identity, reparse, access, atomic-move, or inspection uncertainty stops the
  deletion and records truthful partial progress.

The stable identity token is the provider's non-null
`BasicFileAttributes.fileKey()` paired with the captured file-store identity.
OpenJDK 21's native Windows provider keeps a volume/file index internally but
returns `null` from the public `fileKey()` contract, so this containment
delivery cannot safely bind Windows tombstones without a separate native
file-ID bridge. An injected stable-key fixture proves only the generic
tombstone algorithm, not native Windows support.

If a provider supplies neither `SecureDirectoryStream` nor a non-null stable
file key, the coordinator performs no mutation and records
`RETAINED_PLATFORM_UNSUPPORTED` with the provider/file-store reason. That
visible retained result is certifying rather than a storage-finalisation
failure because no destructive operation was attempted; the capacity gate and
normal retention remain active. A null identity never falls back to pathname
trust.

There is no ordinary pathname-walk fallback. Both strategies inspect and bind
all allowlisted candidates before the first mutation, never touch an external
target through a link, and report fully removed and partially modified
relative paths separately. Native Windows/PowerShell sessions remain
certifying as visibly retained/unsupported under JDK 21; automatic Windows
compaction is a deferred Actworks/Slipmat native-file-identity capability, not
a claim of this containment delivery.

### Compactable data

Automatic terminal compaction removes only:

```text
<session>/tmp
<session>/build/test-classes/traces
```

Those paths were measured as the dominant reproducible storage cost. The
following remain intact:

- `manifest.json`, `command.txt`, and terminal `maven.log.gz` (or the original
  `maven.log` when compression cannot be published safely);
- Surefire and trace reports;
- diagnostics;
- compiled production and test classes other than copied trace resources;
- ordinary copied test resources;
- JARs, native libraries, and other package outputs;
- `artifacts/` and `distribution/`;
- every path named by the manifest's report and artifact inventories.

The coordinator never broadens its deletion set because a directory is large
or old. New compactable paths require a schema change and focused tests.

### Finalisation result

The coordinator records the validated session identity, compaction state,
exact removed relative paths, reclaimed bytes, before/after usable bytes, and
any error in the final manifest and end marker.

If compaction fails after an otherwise successful run, the certifying wrapper
exits nonzero with terminal state `STORAGE_FINALIZATION_FAILED`. If the child
or identity validation already failed, that original failure remains primary
and the storage-finalisation error is recorded as an additional failure. No
cleanup failure may turn a red run green.

The immediate emergency delivery is intentionally a containment slice:
fail-closed managed allocation, capacity gates, terminal compaction, and
terminal log compression. Full managed-envelope retirement and the worktree
lifecycle command remain follow-up work. In particular, direct-manifest
retention does not prove or repair retention for nested or broken envelopes,
so this slice makes no such claim.

`--retain-ephemeral` skips automatic compaction for a deliberate diagnostic
run. The manifest records who requested it through the command line, the paths
retained, and `compaction.status = RETAINED_BY_REQUEST`. Retention expiry still
applies unless the allocation is separately kept.

### Manifest additions

The manifest adds:

- allocation schema and helper version;
- storage tier and canonical allocation lane;
- allocation verification result;
- capacity floor and launch/completion usable bytes;
- `allocation_inode_count_status`, nullable `allocation_usable_inodes`, and an
  explicit allocation inode reason when the count is unavailable;
- `launch_inode_probe_status` / `completion_inode_probe_status` plus their
  nullable errors, and separate launch/completion directory-flush status;
- launch/completion numeric inode fields remain JSON `null` with the reason
  that the live probe status is authoritative; they never repeat the
  allocation snapshot under a phase-current name;
- compaction status, removed relative paths, and reclaimed bytes;
- `retainEphemeral` and storage-finalisation error details.

Helper-specific fields are `null` with an explicit not-applicable reason for
non-managed tiers; they are never silently omitted in a way that could be
mistaken for an old or malformed manifest.

The start marker prints run ID, manifest, log, storage tier, launch free bytes,
and capacity floor. The end marker prints the existing verdict plus compaction
status, reclaimed bytes, and completion free bytes.

## Worktree lifecycle

### Single rule

The branch is the durable code artifact, promoted managed-scratch output is the
durable generated artifact, and the worktree is disposable only after proof.
Workers persist value; the lead retires the workspace at stand-down.

The trace-green-fleet skill and its mirror will no longer call trace worktrees
“persistent” or leave clean trees merely “for review.” A clean unmerged branch
is reviewable after its worktree is removed. A dirty tree is not disposable.

### Repository-owned command

`tools/worktree-lifecycle` provides two initial operations:

```text
tools/worktree-lifecycle audit [--root <allowlisted-root>] [--json]
tools/worktree-lifecycle retire <registered-worktree> [--base <ref>]
  [--confirm-detached-head <sha>] [--apply]
```

All operations are dry-run unless `--apply` is supplied. The command resolves
the repository common directory and always obtains every registered worktree
from `git worktree list --porcelain`; `--root` adds an allowlisted filesystem
root for physical-orphan discovery and never limits registered-worktree
reporting. The command refuses `/`, a home directory, a project root,
unresolved variables, globs, symlinked roots, and paths outside explicit
allowlists.

`audit` classifies without modifying:

- clean registered worktree, branch merged into the base;
- clean registered worktree, branch not merged;
- clean detached worktree, recording its exact HEAD;
- dirty or unreadable registered worktree;
- Git-prunable metadata;
- unregistered physical directory with a broken foreign `.git` pointer;
- unknown directory.

Human output includes path, apparent bytes, branch or detached HEAD, dirty
state, merge relation, registration state, and proposed action. `--json`
emits a versioned record for future Actworks ingestion.

When `--base` is omitted, the command uses the branch checked out in the main
workspace identified by `git worktree list --porcelain`, matching the
repository's integration policy. It prints that branch and resolved commit in
both output formats. If the main workspace is detached or its branch cannot be
resolved, `--base` is required. Apply mode revalidates that the resolved base
has not changed since classification.

`retire --apply` handles one explicitly named registered worktree:

1. Re-read and revalidate Git registration and canonical path.
2. Refuse the main worktree, Git-locked worktrees, a live test-session lease
   proven through the coordinator's existing owner/liveness contract, and any
   dirty or unreadable state. The tool does not pretend it can discover every
   arbitrary process whose current directory happens to be inside a worktree.
3. Record branch/HEAD, base, merge relation, and size in the operation result.
4. Remove the worktree through `git worktree remove`, never raw recursive
   deletion.
5. Delete a local branch with `git branch -d` only when Git proves it is merged
   into the requested base. Retain clean unmerged branches.
6. Run `git worktree prune` only after successful removal.

Detached clean worktrees require an explicit confirmation flag naming the
recorded HEAD. Broken foreign-pointer orphans are audit-only in this delivery;
their high-yield cleanup remains a separately approved destructive operation.

The command reports but never deletes `openggf-test-session.lock*` metadata.
Those namespaces remain under `TestSessionCoordinator`'s explicit reclaim
protocol. Because registered worktree removal mutates protected Git metadata,
an agent running inside Codex's normal workspace-write sandbox must use the
ordinary approval path for `retire --apply`; the design does not broaden the
sandbox to make removal silent.

Lease audit covers two canonical sources: the linked-worktree Git directory
for legacy/explicit local locks, and the installed managed lease root returned
by `agent-scratch path test-session-locks` when managed scratch is configured.
The latter is a shared directory whose namespace metadata identifies and
validates the owning worktree. An unreadable configured managed lease root
blocks retirement; absent managed configuration is not an error. Audit reports
root provenance and never deletes either source.

### Workflow enforcement

The paired trace-green-fleet skills, agent guidance, and workflow docs will
require every worktree-owning stage to report branch, HEAD, path, and dirty
state. At stand-down the lead must run `audit` and either:

- retire a clean tree;
- retain it with a stated dirty/active blocker; or
- preserve and commit/copy the unique state, then retry retirement.

Final summaries list blockers, not an open-ended inventory of review trees.
Guard tests verify the two skill mirrors remain identical and contain the
stand-down contract.

“Stand-down” means the lead has explicitly ended that worker or experimental
lane. A development worktree participating in the repository's normal delivery
flow remains in place through integration, post-merge verification, push, and
the existing final cleanup checks required by `AGENTS.md`.

## Failure handling

- Managed allocation failure: fail before manifest publication or Maven.
- Malformed helper JSON: fail closed and retain helper stderr in the startup
  diagnostic.
- Capacity below floor: fail before Maven with measured values.
- Child process failure: preserve the child exit and compact only after the
  terminal manifest is safely written.
- Compaction refusal/failure: preserve all remaining data and report a storage
  finalisation failure.
- Worktree dirty, locked, active, unreadable, or changed during validation:
  refuse retirement without mutation.
- Branch deletion refusal: worktree removal may remain successful, but retain
  the branch and report Git's exact reason.
- Orphan ambiguity: audit-only; never infer safety from age or size.

## Migration and rollback

1. Re-run `tools/agent-scratch install` after the source update. Installation
   creates `codex/test-sessions`, updates Codex's managed configuration if
   needed, and verifies the exact environment and writable-root contract.
2. Existing managed `tasks/openggf-test-session-*` and project-local sessions
   are not moved. Their current age-based retention remains in force.
3. New managed sessions use only the Codex test-session lane.
4. Existing worktrees are visible through `audit`; no bulk removal occurs as
   part of installation or migration.
5. Rollback reverts the source change and reinstalls the older helper. Existing
   session directories remain ordinary retained data and can still be
   inspected manually.

The installed-helper verifier must compare the installed executable and
generated configuration against the source contract. A stale helper is an
actionable managed-allocation failure, not a reason to fall back locally.

## Testing and acceptance criteria

### `agent-scratch` tests

- install creates the managed Codex session lane idempotently;
- rendered Codex configuration contains the exact canonical writable root;
- verify detects a missing lane, stale installed helper, environment mismatch,
  and missing/wrong writable root;
- verify treats the two known sandbox user-bus-unavailable diagnostics as
  `UNAVAILABLE_IN_SANDBOX` only after all static evidence passes, while an
  unknown service-manager error still fails;
- structured allocation returns valid JSON and a successfully probed path;
- a deterministic Btrfs-shaped `f_files=0, f_favail=0` fixture emits
  `inode_count_status=UNAVAILABLE_DYNAMIC` and JSON-null `usable_inodes`, while
  a measured zero fixture remains `MEASURED` and numeric;
- a simulated sandbox makes the managed root and non-Codex siblings read-only
  while leaving the installed Codex lane writable; reservation succeeds,
  creates/touches no root `.agent-scratch.lock`, `tasks`, `quarantine`, or
  other sibling, and returns a valid allocation;
- concurrent sandboxed reservations serialize through the lane-contained lock
  and return distinct, probed directories;
- a missing, symlinked, or otherwise unsafe test-session lane fails closed
  without repairing or mutating the root layout;
- install creates and verify validates `codex/test-session-locks`; reservation
  returns its canonical path without writing it, and a missing/symlinked lease
  lane fails closed;
- allocation rejects symlinks, foreign ownership, unsafe modes, wrong roots,
  malformed paths, and failed atomic operations;
- terminal sessions obey seven-day retention and bounded keep markers;
- a live `RUNNING` session is preserved, while a stale expired `RUNNING`
  session moves to quarantine rather than being deleted directly.

### Coordinator self-tests and process harness

- configured managed allocation failure never creates `.openggf/test-runs`;
- a default managed run uses the returned writable lease root and reaches the
  child inside the ordinary Codex sandbox without `--lock-root`;
- unmanaged project-local fallback is visibly classified;
- explicit overrides preserve fail-closed behavior;
- low capacity prevents child launch, with an injectable threshold for a
  deterministic test;
- success, test failure, identity invalidation, abort, shutdown, and process
  tree failure all attempt the correct terminal finalisation;
- managed, explicit, project-local, and explicitly selected system-temporary
  tiers use the same compaction contract;
- compaction rejects active, non-terminal, symlinked, mount-crossing, and
  replaced session paths;
- compaction removes only the two allowlisted paths and reports exact bytes;
- evidence, reports, artifacts, JAR/native outputs, and ordinary resources
  survive compaction;
- `--retain-ephemeral` retains both compactable paths and is recorded;
- a compaction failure makes a green child non-certifying;
- POSIX and PowerShell wrappers expose equivalent flags and markers.

### Worktree lifecycle tests

Disposable fake repositories cover:

- clean merged branch: worktree removable and branch deletable;
- clean unmerged branch: worktree removable and branch retained;
- dirty merged branch: blocked;
- locked or main worktree: blocked;
- clean detached tree: audit records HEAD and apply requires explicit HEAD;
- registered path: never treated as a filesystem orphan;
- broken `C:/...` `.git` pointer below an allowlisted root: audit candidate
  only;
- unknown/symlink/out-of-root paths: blocked;
- active and recovered test-session lock namespaces: reported, never deleted;
- state changed between classification and apply: blocked on revalidation;
- dry-run performs no mutation and JSON output is schema-stable.

### Project verification

Implementation must run, through the quiet session wrapper:

```text
python3 tools/test_agent_scratch.py
tools/testing/run-session-process-harness.sh
tools/testing/test-session.sh -- mvn test
tools/testing/test-session.sh -- mvn -Dmse=off -Pguards test -B
```

Focused tests run first. The ordinary suite and fresh-JVM guard suite are both
required before integration. Reports must include each run ID, manifest path,
and log path.

## Documentation changes during implementation

- Amend the existing test-session isolation design to reference this terminal
  storage addendum rather than rewriting its execution contract.
- Update `tools/testing/README.md` and `docs/agent-workflow/README.md` with
  allocation tiers, capacity refusal, compaction, retention opt-out, worktree
  audit, and retirement.
- Update `AGENTS.md` and `CLAUDE.md` together.
- Update the paired trace-green-fleet skills together and set the appropriate
  commit trailers.
- Keep the future Actworks/Slipmat extraction named as deferred architecture,
  not as a dependency of this containment delivery.

## Risks and mitigations

- **Evidence deletion:** the compaction allowlist is fixed and
  manifest-contained; everything else is preserved.
- **Symlink or path-swap attack:** both helper and coordinator canonicalise and
  revalidate; destructive operations refuse symlinks and out-of-lane paths.
- **False capacity confidence:** the guard is explicitly a preflight, not a
  reservation, and completion capacity is recorded.
- **Cleanup changes a test verdict:** storage finalisation has its own state and
  can only make a green result non-certifying, never make a failure pass.
- **Loss of unmerged work:** clean unmerged worktrees may be removed only after
  branch/HEAD recording; the branch is retained. Dirty trees are blocked.
- **Unsafe legacy orphan cleanup:** legacy foreign-pointer directories are
  inventory-only in this delivery.
- **OpenGGF-specific coupling:** helper operations use versioned JSON and narrow
  ownership boundaries so Actworks/Slipmat can later adopt them without
  importing game-engine behavior.

## Deferred Actworks boundary

After the immediate OpenGGF priorities are resolved, a separate architecture
session will define `OpenGGF/Actworks` as the umbrella for worktree-backed agent
subprojects. `cowtree` will remain a constituent tool. `Slipmat` will absorb
generic managed allocation, retention, capacity, session compaction, and
worktree lifecycle interfaces. OpenGGF will then retain only thin adapters and
its certifying test semantics.
