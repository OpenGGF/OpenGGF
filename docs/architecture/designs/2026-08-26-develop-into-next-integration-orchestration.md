# Develop Into Next Integration Orchestration Design

Date: 2026-08-26

Target branch: `next`

Integration branch: `feature/ai-next-develop-integration-20260826`

## Purpose

Integrate the current committed `develop` tip into `next` while preserving the
0.7 work already staged on `next`, adopting `develop`'s current shared-runtime
owners and ROM-accuracy fixes, and proving that the result introduces no
regression relative to either parent.

This is a semantic reconciliation. It is not a mechanical conflict-resolution
exercise and must not use blanket `ours`, `theirs`, or fitted trace behavior.

## Frozen starting evidence

The design was approved against these fetched local tips, last verified at the
start of the 2026-08-26 integration session:

- `next`: `84d9a3761f618035dd1caa40a3d5fc72a1019693`
- `origin/next`: `84d9a3761f618035dd1caa40a3d5fc72a1019693`
- committed local `develop`: `e3f390e9fe381b89cc54a2399b5a49843ba044e4`
- `origin/develop`: `e3f390e9fe381b89cc54a2399b5a49843ba044e4`
- merge base: `59e59c8feb5fb5a247ff0ab43da63aeccc742cb0`

Local `develop` is clean and matches the fetched remote-tracking tip. The
integration consumes this exact committed tip, never workspace dirt. During
plan review, both refs advanced together from `9b46505eb` by merge commit
`f1b82774d` (`bugfix/ai-s2-cadence-tests`). The source was therefore re-frozen,
the merge simulation repeated, and both design and plan returned through their
independent review loops before implementation.

At the Task 3 baseline gate, `develop` and `origin/develop` advanced again by
the reviewed S2 special-stage pacing changes and their merge commits, from
`f1b82774d` to `97bc177ee`; `next` remained unchanged. The new tip is a strict
fast-forward descendant. Its delta does not touch the pinned wrapper,
coordinator, hooks, Maven configuration, or Task 1 adapter inputs, and an
isolated merge-tree comparison reports the same semantic conflict paths against
both develop tips. Parent evidence at `f1b82774d` is nevertheless historical
and cannot certify the new parent, so the adapter pin and all develop baselines
must be repeated against `97bc177ee` after this amendment's independent review.

Before the baseline restart, local and remote `develop` then diverged: local
CNZ slot-machine work and remote S3K runtime work were reconciled by merge
commit `e3f390e9f` and that commit was pushed to `origin/develop`. The resulting
tip is seven commits and 28 changed paths beyond `97bc177ee`. Its wrapper,
coordinator, self-test, hook, Maven, and adapter prerequisite bytes are
unchanged; a fresh recursive merge simulation still reports exactly 77
conflict paths. Evidence captured at `97bc177ee` is nevertheless historical.
The adapter pin, both parent baselines, union inventory, and the four affected
conflict-ledger rows must be repeated against `e3f390e9f`. This pushed commit is
the immutable merge snapshot; later `develop` descendants are follow-up
fast-forward deltas on the source line and do not change this merge's exact
second parent or imply that `next` itself can fast-forward to them.

After that snapshot was frozen, the 2026-08-26 source refs advanced on separate
lines from common ancestor `e3f390e9f`: local `develop` reached
`75f64e53c379e4ac65fdde1d3c37fc2f52701711` through the 0.6 scope-freeze
documentation merge (two local-only commits), while `origin/develop` reached
`ed9f55941343093e226b5ad55d80ece7a9417267` through the S2 CPZ2 water-palette
fix (one remote-only commit). These descendants are outside the frozen merge
snapshot and its parent baselines. Their reconciliation is a separately tested
and pushed source-line follow-up before final delivery; it does not alter the
exact `e3f390e9f` second parent.

From the merge base, `next` and `develop` have 611 and 1,879 unique commits
respectively. They changed 2,324 and 1,950 files, with 232 paths touched by both
sides. An isolated recursive merge simulation at the final pre-baseline
integration head reports 77 conflicts: 49 production files, 17 test or guard
files, and 11 build, policy, tooling, or documentation files. The additional
path relative to the original 76-path simulation is the intentional pre-merge
Task 1 documentation at `tools/testing/README.md`; the develop-tip drift itself
does not change the conflict-path set.

## Requirements

### Goals

1. Make the complete committed `develop` history an ancestor of the resulting
   integration commit while retaining the frozen `next` line as first-parent
   ancestry through the committed design and planning artifacts.
2. Preserve `develop` as the authority for current shared ROM-accuracy fixes,
   trace-v5 behavior, runtime ownership, test-session isolation, and build/hook
   policy.
3. Preserve and re-express `next`'s 0.7 behavior on those current owners,
   including its Mod API candidate, editor and creator tooling, Time Attack and
   multiplayer foundations, super-emerald progression, powered forms, late S3K
   route work, and release roadmap.
4. Reconcile every conflict from source evidence, current APIs, focused tests,
   or an explicit documented decision.
5. Establish exact parent baselines and accept no integration-introduced
   regression against the union of the parent results.
6. Produce reviewable requirements, decisions, task ownership, test evidence,
   integration results, and end-to-end review artifacts.
7. Stop before updating or pushing `next` for explicit human review.

### Non-goals

- Completing the open 0.7 route, outro, trace-chain, Mod API publication, or
  release-candidate milestones.
- Broadly refactoring code that neither resolves a merge conflict nor restores
  a parent-owned contract.
- Making a pre-existing parent red set green unless a merge decision exposes a
  small, directly attributable harness defect.
- Rewriting either parent history or replaying the 611 `next`-only commits.
- Pushing the integration branch or `next` before human confirmation.

### Constraints

- Work occurs in an isolated worktree; the main `develop` workspace never
  switches branch.
- Runtime assets remain ROM-only, and disassembly trees remain research input,
  never executable runtime or test input.
- Shared runtime code receives no game-name or zone carve-outs.
- Trace fixtures remain comparison-only except for the permitted hardware
  scheduling contract.
- Conflict decisions follow the smallest accurate runtime owner and the
  shipped-ROM `FixBugs = 0` path.
- Every certifying Maven invocation uses the test-session coordinator at the
  pinned `develop` source commit, JDK 21, quiet markers, and a session-owned
  temporary root. The merged tree uses its own
  `tools/testing/test-session.sh`; the frozen `next` baseline, whose tree
  predates that wrapper and its Maven output properties, invokes the pinned
  `develop` wrapper by absolute path from inside an immutable detached `next`
  baseline worktree and runs Maven through the reviewed frozen-parent adapter
  described below. Evidence records the wrapper and adapter commits, file
  hashes, commands, and separate session manifests.
- Ordinary and structural-guard sessions are separate; raw Maven output is not
  certification evidence. Frozen `develop` and the merged tree use their
  in-tree `-Pguards` profile. Frozen `next` predates that profile, so its
  equivalent fresh-JVM session uses an explicit selector generated from its own
  source tree with the merged profile's conventions (`Test*Guard*`, `TestNo*`,
  `TestArchUnit*`, and `TestAudioPresentationBoundary`). The selector inventory
  and reports must agree exactly, with zero missing or duplicate class.
- Repository hooks and documentation/trailer policy remain enabled; no
  `--no-verify` operation is permitted.

## Architectural decision

### History and workspace shape

The integration is a direct merge of committed `develop` into a branch created
from `next`. The integration branch already contains the approved design and
planning commits above frozen `next`; therefore the merge commit's exact first
parent is the final pre-merge integration-branch commit, whose uninterrupted
first-parent ancestry reaches frozen `next` `84d9a3761`. Its exact second parent
is frozen `develop` `e3f390e9f`. This keeps the 0.7 development line legible
while recording all of `develop` as ancestry. A single coordinating agent owns
the merge index because Git cannot safely compose independent partial
resolutions of one conflicted index.

The integration worktree is
`.worktrees/next-develop-integration-20260826`. Subagents may inspect this
worktree, but the coordinator alone stages or resolves its merge index. Before
implementation, the coordinator publishes a file-level conflict ledger with
one owner and integration order per path. Any parallel edit or verification
runs in a separate worktree and a separate test-session namespace; completed
patches are reviewed and applied centrally in ledger order.

### Conflict authority

Conflict resolution uses a two-part rule:

1. Adopt the current `develop` abstraction, lifecycle boundary, and shared
   runtime owner unless evidence shows the `next` owner is intentionally newer.
2. Restore the observable `next` feature contract on that owner rather than
   dropping it because its old implementation no longer applies cleanly.

The highest-risk reconciliation families are audio presentation and rewind;
game-loop and level transitions; object solidity, touch, respawn, and ring
state; playable movement and animation; and S3K zone, event, data-select,
special-stage, and object behavior. Build policy and release documentation are
resolved as contracts, not append-only prose.

### Orchestration model

The coordinator retains ownership of branch operations, the merge index,
cross-family interfaces, integration commits, full-suite comparison, and final
report. Three parallel investigation lanes provide independent evidence:

1. Audio and shared runtime ownership.
2. S3K, level, object, ring, and playable-movement ownership.
3. Build policy, tests, guards, documentation, and baseline comparison.

Each lane begins read-only and returns relevant files and symbols, parent
intent, conflict risks, recommended resolutions, and focused verification.
Implementation is then assigned only where file ownership is disjoint. Every
implementation result receives independent review before integration.

The third lane also owns Milestone 0 bookkeeping: regenerate the candidate
`src/test/resources/mods/mod-api-signatures-0.7.txt` only after production
conflicts settle; update the current 0.7 roadmap and README claims from measured
results; create
`docs/architecture/validation/2026-08-26-develop-into-next-integration.md`; and
update `docs/status/trace-frontier-log.md` whenever a required sweep moves or
selects a frontier. The validation report contains an owner and 0.7 release
disposition for every remaining failure or error.

### Frozen-parent test-session adapter

Frozen `next` `84d9a3761` predates both the coordinator and the POM properties
that route build, report, temporary, diagnostic, artifact, and distribution
output into a coordinator session. An external coordinator without an adapter
would therefore produce a misleading manifest with empty report inventory while
Maven wrote into the worktree's ignored `target` directory.

The integration branch provides a pinned, reviewed launcher, external exclude
file, and compatibility adapter under `tools/testing/` for this historical
baseline only. The original adapter used a `target` symlink, but that changed
canonical path semantics and is superseded below by a private bind mount onto
an empty real directory. Ignoring the root mountpoint must still be established
before the coordinator takes its first source digest. The launcher sets an inherited, process-local
`GIT_CONFIG_COUNT` entry for `core.excludesFile` that points at the pinned file,
whose sole effective pattern is the root `/target` entry. It does not mutate
repository or user Git configuration. The launcher and exclude file are named
in `OPENGGF_RUNTIME_INPUTS`, and the adapter validates the inherited config and
file hash before creating anything.

The adapter runs as the coordinator's child command in a newly created
immutable detached worktree and:

1. requires the detached worktree to match `84d9a3761`, have no tracked or
   untracked source changes, and have no existing `target` path;
2. preflights Linux unprivileged user/mount/PID namespaces and bind mounts, derives
   the coordinator roots from its injected `OPENGGF_*` identity, creates an
   ignored empty real directory at `target`, and creates empty real mountpoint
   directories beneath the session build root; before root mapping it also
   authenticates the outer numeric UID's passwd home against canonical `HOME`
   without changing `HOME`, and rejects any existing `user.home` override in
   Maven arguments, `MAVEN_OPTS`, or `JAVA_TOOL_OPTIONS`;
3. runs a namespace supervisor with
   `unshare --user --map-root-user --mount --pid --fork --kill-child=KILL` and
   no replacement procfs mount. Its child is PID 1 of the private PID namespace,
   so Maven, Surefire, and every descendant remain kernel-contained there and
   cannot daemonize into the ancestor PID namespace. The child makes `/`
   recursively private with `mount --make-rprivate /` before any binding,
   bind-mounts the session build root onto the real worktree `target`, then
   nested-bind-mounts coordinator-owned tmp, Surefire, trace, diagnostic,
   artifact, and distribution roots onto their historical `target/*` paths;
4. authenticates every mount with `mountpoint` plus matching no-follow
   device/inode identity between source and mounted target, records the exact
   outer supervisor PID/start time, the outer PID/start time and PID-namespace
   inode of private PID 1, their common mount-namespace inode, and bounded
   mount-table evidence, then pauses at a ready/go barrier; the
   still-parent-namespace adapter verifies those same identities while
   simultaneously proving `target` is a non-mount, empty ordinary directory in
   its view before releasing Maven;
   Maven therefore observes real worktree-local canonical paths while every
   byte is stored in the exact coordinator root;

   Because procfs is intentionally not remounted, `/proc/1` inside the private
   PID namespace still names host PID 1. The private PID 1 publishes its
   `NSpid` chain by shell-native reading of an already-open
   `/proc/self/status`; it must not use `/proc/$$` or a subprocess whose
   `/proc/self` would name that subprocess. The outer adapter authenticates the
   published host PID against the supervisor's single direct
   `/proc/<supervisor>/task/<supervisor>/children` edge, process start time, an
   `NSpid` chain ending in 1, PID-namespace inode, and the common
   mount-namespace inode before releasing Maven.
5. root mapping gives the namespace child mount capability but would otherwise
   make Java select `/root`; the adapter preserves user semantics by appending
   `-Duser.home=<authenticated outer passwd home>` to adapter-owned
   `MAVEN_OPTS` for both Maven invocations and to frozen `next`'s resolved
   `surefire.argLine`, while preserving existing options. It also appends fork-specific
   `org.lwjgl.system.SharedLibraryExtractPath=<session tmp>/lwjgl-${surefire.forkNumber}`
   through the frozen POM's own `${surefire.argLine}` expansion; the historical
   POM continues to own `java.io.tmpdir=<worktree>/target/test-tmp`, whose real
   mount identity is the coordinator tmp root; and
6. installs cleanup before creating the mountpoint and writes a recovery marker
   with its exact ordinary-directory type/device/inode, expected parent-empty
   state, exact supervisor and private-PID-1 identities, PID-namespace inode,
   and their common mount-namespace inode. Normal cleanup reaps the exact
   supervisor and then verifies both recorded process identities are gone;
   forced cleanup performs the same bounded verification. After termination,
   an absent PID or the same PID with a different start time means the recorded
   identity is gone, while the same PID with the same start time means it still
   survives. Private PID-namespace lifecycle is
   the proof that no adapter-created workload survives: when PID 1 exits the
   kernel kills the namespace's remaining processes, and when the supervisor
   dies `--kill-child=KILL` kills PID 1. Only after that proof may cleanup remove
   the still-exact, parent-non-mount, empty real `target` directory with
   `rmdir`. It never
   recursively deletes, follows, replaces, reads/unlinks a link, or empties
   `target`.

The adapter does not scan every host `/proc/*/ns/mnt` entry. Unreadable
privileged host processes are unrelated to the adapter-created PID namespace
and cannot be classified by an unprivileged harness. Concurrent hostile
privileged host interference is outside this certification threat model; no
unprivileged adapter can prove its absence. The authenticated private PID
namespace is the bounded ownership boundary for cleanup.

The orchestrator invokes the pinned launcher with expected harness commit
`e3f390e9f`. The wrapper and `TestSessionCoordinator.java` must resolve beneath
an immutable detached worktree at that exact commit. Before launch, the
launcher asserts the harness worktree's detached clean identity and byte-checks
`tools/testing/test-session.sh` and
`tools/testing/TestSessionCoordinator.java` against their corresponding blobs
from `git show e3f390e9fe381b89cc54a2399b5a49843ba044e4`; a wrapper or coordinator
outside that worktree, at another commit, or with different bytes is rejected. It then
starts the coordinator with `OPENGGF_RUNTIME_INPUTS` already containing the
launcher, exclude file, adapter, wrapper, and coordinator-source paths. The
coordinator reads and hashes those inputs before the child starts, so any
mid-run change invalidates the session. The exact harness commit, Git-config
environment, and input hashes are recorded in the adapter evidence, and the
adapter asserts an empty
`git status --porcelain --untracked-files=all` both before mountpoint creation
and after the ready/go isolation proof while the run is active. Adapter commands may invoke Maven lifecycle
phases such as `test` or `package`, but must never include `clean`: the
historical clean plugin could traverse or replace the active mount topology and
destroy the authenticated routing contract.

An outer recovery/finally step runs after every coordinator outcome. This is
separate from the child trap because forced process-tree termination can prevent
the trap from running. Recovery accepts only a marker whose run ID, canonical
worktree, target path, original empty-directory device/inode, expected
parent-empty state, supervisor and private-PID-1 PID/start identities,
PID-namespace inode, common mount-namespace inode, and session roots agree with
the just-started session. It bounded-waits for both exact process identities to
be absent or for the same PID to have a different start time before using
no-follow inspection and `rmdir` only on
that exact still-empty, parent-non-mount ordinary directory. A symlink,
non-empty directory, changed identity, surviving supervisor or PID 1,
propagation leak, or mismatched marker is reported for human inspection and is
never deleted.

The adapter is test infrastructure, not a production or gameplay change. Its
self-test suite covers successful Maven completion, ordinary Maven failure, and
forced child-process termination. Every case checks exact detached HEAD, clean
tracked/non-ignored source inventory, post-run `target` absence, and unchanged
source digest. The forced-termination case proves the outer recovery path, and
negative cases prove that a changed/non-empty ordinary directory, live mount,
or symlink is left untouched.

The successful self-test selects enough frozen-next guards to force at least two
Surefire JVMs with a two-fork configuration. It requires start/end markers and
a valid manifest, proves every expected Surefire XML is in the manifest
inventory, and extracts the report JVM properties and process evidence before
namespace teardown and mountpoint removal. At least two distinct fork identities must expose two
distinct resolved `lwjgl-<fork>` directories beneath the session tmp root; a
literal unresolved `${surefire.forkNumber}`, duplicate path, or path outside
the session fails the adapter.

Frozen `next`'s POM normally reports `java.io.tmpdir` lexically as
`<detached worktree>/target/test-tmp`. That spelling is not acceptable through
the compatibility symlink: path-containment tests compare lexical roots with
canonical children, so the symlink creates adapter-only failures even though it
resolves to the right bytes. Empirical frozen-POM runs also proved that
`-DargLine`, `-Dproject.build.directory`, and post-start Surefire user-property
promotion cannot preserve every historical JUnit/startup consumer. A private
bind mount is the supported mechanism: both lexical and canonical test-visible
paths remain `<worktree>/target/test-tmp`, while `mountpoint` and device/inode
evidence prove their backing directory is the exact coordinator tmp root.
Before cleanup, the adapter records those paths, mount identities, outer UID,
authenticated home, fork `user.home`, run ID, and session root in diagnostics.
LWJGL remains injected through
`${surefire.argLine}` as a distinct per-fork child beneath the direct session
tmp root. A mutation test changes a scratch copy named in
pre-launch `OPENGGF_RUNTIME_INPUTS` during a controlled run and requires the
coordinator to end `INVALID_IDENTITY_CHANGED`.

Frozen `next` also contains one historical test side effect that is incompatible
with the newer coordinator identity contract:
`TestRewindRoundTripProbe.probeReportIsWrittenToDisk` intentionally overwrites
the tracked `docs/status/rewind-round-trip-gaps.md`. The test passes, and develop
later classified the path as generated output by deleting it and adding the path
to `.gitignore`; nevertheless, a run that simply leaves the rewrite behind is
identity-invalid and cannot serve as a parent baseline.

The adapter therefore owns one fail-closed normalization, pinned simultaneously
to frozen HEAD `84d9a3761f618035dd1caa40a3d5fc72a1019693`, exact path
`docs/status/rewind-round-trip-gaps.md`, and committed Git blob
`d83614ec3a32abd1d6636d2be247ade01331bf3c`. Before Maven it authenticates the
regular, non-symlink preimage and archives its bytes outside the worktree. After
the child exits it may archive a changed regular report under session
diagnostics only when the file retains the expected report title and summary
shape **and** the current authenticated session's Surefire XML records exact
class `com.openggf.game.rewind.coverage.TestRewindRoundTripProbe`, method
`probeReportIsWrittenToDisk`, and its outcome. It records that outcome plus
original/generated SHA-256 values and byte lengths, then atomically restores
the exact committed bytes before the coordinator takes its final digest. The
generated hash is evidence, not an allowlisted constant: the report embeds the
current date and does not contractually guarantee iteration or locale ordering.

No other path is normalized. An unexpected initial blob, missing or replaced
report, archive/restore failure, or any second tracked/untracked mutation must
remain identity-invalid. A failing Maven child keeps its original status when
normalization succeeds; normalization failure controls only an otherwise
successful result and is always diagnosed.

An adapter safety or cleanup failure must also be visible to the frozen
coordinator's final source digest. This is necessary because `target` is ignored
on frozen `next`: replacing that directory with a symlink can make namespace
teardown and outer cleanup fail while the coordinator would otherwise publish
`FAILED` / `valid=true`. On such a failure, and only on such a failure, the
adapter arms a pinned identity tripwire at the same authenticated historical
report path. If the probe has already made the report dirty, it leaves those
bytes untouched. If the report still exactly matches the authenticated
preimage, it writes a deterministic, session-identified invalidation marker and
records the reason, run ID, child Maven status, adapter namespace-teardown
status, resulting hash, and byte length in diagnostics. It must not restore or
normalize the tripwire before coordinator finalization. The unsafe-target
symlink fixture binds the observed provenance separately: child Maven status N
is the launcher/coordinator process status, namespace teardown status 75 is the
pre-finalization safety failure that arms the tripwire, and authenticated outer
cleanup status 73 preserves the symlink after finalization.
Failure to arm the tripwire is itself a hard launcher failure and can never be
accepted as evidence. After finalization, the launcher restores the exact
authenticated preimage; it does not repair, remove, or disguise the unsafe
`target` object.

The tripwire does not apply to an ordinary Maven failure when adapter safety and
cleanup succeed: that result remains `FAILED` / `valid=true` and is admissible
terminal-red parent evidence. Parent-baseline consumers must authenticate the
launcher's terminal outcome and cleanup diagnostics as well as the coordinator
manifest; a manifest emitted by an adapter run is never consumed alone.

The launcher's outer recovery is mandatory. Before Maven, the adapter writes
the authenticated recovery marker with run ID, frozen HEAD, canonical
worktree/report paths, exact preimage archive path/hash/length, ordinary target
mountpoint path and pre-mount no-follow type/device/inode, expected
parent-empty/non-mount state, exact supervisor and private-PID-1 PID/start
identities, PID-namespace inode, and their common mount-namespace inode. If
forced termination bypasses the child trap, outer
recovery uses only those authenticated ordinary-directory and namespace
identities; `readlink`/`unlink` recovery is forbidden.

Normal and outer cleanup use one order: prove the exact supervisor and private
PID 1 are gone, relying on kernel PID-namespace lifecycle to terminate their
descendants; restore the exact authenticated report preimage when required;
recheck that `target` is the same empty, parent-non-mount ordinary directory
with the recorded device/inode; then remove only that directory with `rmdir`.
It may restore for hygiene without Surefire proof, but such a run remains
non-certifying. Empirical INT/TERM tests against the exact authenticated frozen
coordinator show that its shutdown hook forcibly terminates the adapter before
the child trap can finish, then takes the dirty final digest. The binding
launcher-signal outcome is therefore `INVALID_IDENTITY_CHANGED`, followed by
outer restoration after finalization. It is never `PASSED`, `ABORTED`, or valid;
the restored worktree does not retroactively change the invalid manifest.

The exact frozen coordinator has a further shutdown race: its normal and
shutdown finalizers can contend for the fixed `manifest.json.tmp`, leaving a
correct on-disk `INVALID_IDENTITY_CHANGED` / `valid=false` manifest but no
`OPENGGF_TEST_RUN_END` line. The signal matrix may recognize that shape only to
prove non-certification and outer hygiene. It must record the missing marker and
may never promote or compare the run. This is not an exception for parent or
candidate evidence: every normal certifying run still requires both start and
end markers, and an absent end marker is invalid regardless of the manifest.

The exact outcome matrix is binding. Child status 0 plus authorized successful
normalization yields status 0 / `PASSED` / `valid=true`; child status N plus
authorized successful normalization preserves N / `FAILED` / `valid=true`.
Normalization failure after child 0 yields nonzero /
`INVALID_IDENTITY_CHANGED` / `valid=false`; after child N it preserves N while
the manifest is `INVALID_IDENTITY_CHANGED` / `valid=false`. Launcher signal
propagation preserves 130 or 143 and yields an on-disk
`INVALID_IDENTITY_CHANGED` / `valid=false` manifest, with either its matching
terminal line or the explicitly diagnosed frozen-coordinator race above,
followed by authenticated outer worktree restoration. The
archived generated report is parent hygiene evidence, not a repository
deliverable and not a parent test failure.

Adapter safety/cleanup failure after child 0 yields nonzero /
`INVALID_IDENTITY_CHANGED` / `valid=false`; after child N it preserves N while
the manifest is `INVALID_IDENTITY_CHANGED` / `valid=false`. In both cases the
launcher restores only the authenticated report preimage after finalization and
preserves any unsafe target for inspection. A tripwire-arm failure is a hard,
non-certifying launcher failure even if the frozen coordinator happened to emit
a superficially valid manifest.

The full parent baseline cannot start until the adapter self-tests and the
coordinator's own self-test pass.

### Failure handling and rollback

The original `next` and `develop` branches remain untouched during development.
The integration branch can be abandoned without rewriting either parent. A
failed or ambiguous conflict is left unresolved until its owner and evidence
are established; it is never hidden by accepting an entire side.

Parent baseline failures are recorded rather than erased. An integration result
is rejected if a parent-passing test becomes red, a parent failure changes or
worsens due to the merge, a parent test identity is absent from the merged
inventory, a required `next` capability disappears, or a certifying session
lacks its start/end markers.

Before implementation and again before human review, the coordinator fetches
and verifies `next` and `origin/next` against the frozen target hash, and
verifies that both `develop` refs still contain frozen snapshot `e3f390e9f`.
Target drift or loss of snapshot ancestry pauses promotion and requires a
reviewed amendment. Any later source-line descendants, whether currently
linear or awaiting their own reconciliation merge, are recorded as follow-up
deltas; they do not refreeze or invalidate this integration snapshot, and the
parent baseline remains detached at `e3f390e9f`. Before final delivery, those
descendants must be reconciled into one pushed source tip without rewriting
history, but that follow-up reconciliation is verified separately rather than
silently changing this merge's second parent. A later target update is
attempted only after the same checks.
Because the original branches remain unchanged, rollback
before promotion is branch abandonment; any partial remote update is recovered
with a new reviewed commit or an explicit human-authorized remote correction,
never a forced rewrite inferred by the agent.

## Verification design

### Parent baselines

Record exact, wrapper-produced ordinary and structural-guard sessions for both
frozen parents in immutable detached worktrees. `develop` uses its in-tree
wrapper and `-Pguards` profile. Frozen `next` runs the wrapper and coordinator
from frozen `develop` `e3f390e9f` by absolute path while its process working
directory remains the detached `next` worktree and the compatibility adapter
routes every output into that coordinator session. Because frozen `next` has
no `guards` Maven profile, generate its explicit fresh-JVM guard selector from
the frozen tree using the four merged-profile naming conventions above, record
the sorted source inventory, run exactly those classes, and reject any missing,
duplicate, or extra report. This is equivalent parent guard evidence without
mutating or borrowing a POM profile from another tree.

First self-test and hash the external coordinator, then record its commit and
SHA-256 in the validation report. Capture run IDs, manifests, logs,
pass/failure/error/skip outcomes, complete test inventories, and environmental
limitations.

Persist the three verified canonical ROM paths and their CRC32/SHA-1 identities
in a task-owned managed-scratch evidence file with owner-only permissions. The
file contains paths and hashes, never ROM bytes. Every independent command,
agent, and worktree reloads that file and immediately requires non-empty values,
regular-file existence, and matching hashes before starting a ROM-backed run;
no shell-process assignment is assumed to survive into a later invocation.

ROM-backed runs discover the actual files and verify their bytes before use:

- S1 REV01: CRC32 `AFE05EEE`, SHA-1
  `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`, property
  `sonic1.rom.path`;
- S2 REV01: CRC32 `7B905383`, SHA-1
  `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`, property
  `sonic2.rom.path`; and
- locked-on S3K: CRC32 `63522553`, SHA-1
  `CFBF98C36C776677290A872547AC47C53D2761D6`, property
  `s3k.rom.path`.

### Conflict-family verification

After each resolved family, compile and run its focused tests before moving to
dependent families. The focused matrix must cover at least:

- audio backend, presentation, rewind, SMPS, and cross-game policy;
- game-loop, title-card, level-transition, checkpoint, and seamless-load paths;
- object manager, solid/touch processing, respawn, rings, movement, and
  animation;
- S1/S2 conflict owners;
- S3K zone registry/events, AIZ/HCZ/LBZ objects, data select, Big Ring and
  special-stage entry;
- Mod API surface and samples, editor, Time Attack/multiplayer, rewind, and
  trace-run ownership; and
- hook, build-tooling, architecture, rewind-coverage, compression, and source
  guards.

### Final certification

Run the ordinary suite and separate fresh-JVM structural guards through the
test-session coordinator. Build a clean class inventory for each tree and
normalize every Surefire outcome as class, method or parameterized identity,
and `PASS|FAILURE|ERROR|SKIPPED`. Red outcomes also retain a deterministic
signature containing element kind, exception type, normalized message,
normalized UTF-8 byte length, and a streaming SHA-256 of the complete normalized
failure body. The normalizer converts line endings to LF and replaces the
canonical worktree, session-root, run-ID, and ISO-8601 timestamp tokens before
hashing. Duplicate identities are invalid. A test
identity that exists or executes on either parent but is absent from the merged
inventory is classified `ABSENT` and is a regression unless a documented,
reviewed removal is itself an intended merge deliverable. A selected executable
test class that produces no report is also `ABSENT`; non-test helpers require an
explicit evidence-backed allowlist.

Attempt the ordinary suite as one certifying session first. Both frozen parents
currently complete Maven but are inventory-invalid because Surefire separately
discovers a compiled nested class and Jupiter also discovers it through its
top-level owner, producing a duplicate testcase identity. The POMs' explicit
ordinary excludes mean Surefire does not add its default inner-class exclude.
Do not normalize that duplicate away and do not use `-Dtest`, which overrides
the POM's ordinary includes and excludes.

The first fallback is one authenticated explicit-source session per tree. Build
a sorted selector by mapping each exact fully-qualified top-level ordinary
source root to its generated-test-classes-relative slash path plus `.java`
(`com.openggf.Foo` becomes `com/openggf/Foo.java`) after applying that tree's
normal Surefire source includes and POM excludes. The selector contains no `$`
names or wildcards, is stored at a canonical absolute managed-scratch path, and
is added to
`OPENGGF_RUNTIME_INPUTS` before launch so the coordinator hashes it before and
after execution. Pass it through
exactly one `-Dsurefire.includesFile=<authenticated-selector>` property; reject
`-Dtest`, `surefire.includes`, another `surefire.includesFile`, or any other
caller selector override. Surefire 3.2.5 then selects
each top-level class once while Jupiter recursively discovers its `@Nested`
tests; existing POM excludes, groups, fork count, fork reuse, system properties,
and cross-class JVM lifetime remain unchanged.

Before accepting an explicit-source session, capture and parse that exact
tree's effective ordinary Surefire configuration. Require no configured
ordinary `<includes>` (because `includesFile` appends to them), record the
effective excludes and groups, and prove those values are unchanged in the
selector invocation. A focused real frozen-POM proof supplies one exact
top-level root containing `@Nested` plus a class excluded by the effective POM;
the nested testcase must execute exactly once and the excluded class must emit
no report. Repeat this gate for the merged candidate because its effective POM
can differ from either parent.

The outcome exporter treats a selected top-level class as owning its exact XML
classname and classnames beginning with the exact `root + '$'` boundary. It
preserves each nested classname in the testcase identity, rejects `$` in the
selector roots, rejects lookalike prefixes and duplicate nested identities, and
counts a root covered when either it or a nested descendant emits a testcase.
The selector root list and slash-path patterns must be an ordinal bijection, and
every root must be covered or explicitly allowlisted as a non-test helper.

If the authenticated explicit-source session exhausts memory or terminates
before producing a complete inventory, retain both invalid attempts and run an
OOM-safe deterministic partition of the complete test class inventory through
separate wrapper sessions. Each executable class must
appear in exactly one successful partition, with zero missing or duplicate
classes, and the aggregation manifest records every child run ID and outcome.
Use one deterministic union partition map across all three trees, filtering
each partition to classes present in the tree being run so parent-only or
merged-only classes do not create false selector failures. Preserve the union
slot identity in the aggregation report. This partitioned aggregate, plus the
separate fresh-JVM guard session, is the second certifying fallback; neither a
partial full run nor a duplicate-identity monolith is ever reported as the
suite result.

Rerun order-sensitive or environment-sensitive changes in isolation before
classification. A parent red whose candidate has the same outcome kind but a
different signature is not silently accepted: it requires paired isolated
reruns of the exact frozen parent and candidate with the same selector and
environment, plus a ledger owner and documented disposition, and remains a
regression if the change is merge-attributable. Record any legitimate pre-existing red, sandbox-only
limitation, or explicitly deferred risk without weakening tests. Install the
tracked `develop` hook path explicitly in every worktree that lacks the current
installer, and record the exact final range-policy invocation and its base/head
hashes.

## Acceptance criteria

The integration is ready for human review only when:

1. The integration merge commit's first parent is the reviewed pre-merge
   integration-branch commit rooted at frozen `next` `84d9a3761`, and its exact
   second parent is frozen committed `develop` `e3f390e9f`.
2. No conflict markers, unresolved index entries, unintended generated output,
   or executable disassembly dependency remains.
3. Every conflict decision is traceable to parent behavior, current ownership,
   ROM/disassembly evidence, or focused tests.
4. Both parent baselines and the merged result have certifying evidence, clean
   test inventories, and no unexplained missing or duplicate identity.
5. No test passing on either parent regresses or becomes `ABSENT` in the merged
   result, and no baseline failure worsens in a merge-attributable way.
6. The current `next` feature gates named above remain present and verified.
7. The Mod API 0.7 candidate snapshot is regenerated from the resolved merged
   production surface, and its policy and signature guards pass.
8. README and roadmap claims use current measured evidence; the integration
   validation report assigns an owner and 0.7 release disposition to every
   remaining failure/error; and trace-frontier documentation is updated when
   required by the executed sweep. The documentation accurately distinguishes
   0.6 release records from the 0.7 candidate roadmap.
9. Independent end-to-end review reports no unresolved blocker.
10. The branch remains local and `next` remains unchanged pending explicit human
   approval.

## Assumptions and risks

- Local and fetched remote-tracking refs agree on frozen `next` and pushed
  snapshot `e3f390e9f`. Target drift or loss of source-snapshot ancestry before
  merge or promotion triggers the amendment and review process. A later
  `develop` descendant is recorded as a follow-up source-line delta instead of
  being silently absorbed into this merge; divergent post-snapshot descendant
  lines are reconciled before final delivery without changing the frozen
  baseline or exact merge parent.
- Both parents may have pre-existing red tests. Exact outcome comparison, not a
  simplistic all-green requirement, governs acceptance.
- Trace payload volume makes file and line counts poor estimates of manual
  effort; the 77 current conflict paths (the original 76 semantic set plus the
  intentional Task 1 tooling/documentation conflict) and post-merge behavioral comparison are
  the useful sizing measures.
- Prior `develop`-into-`next` work exposed direct-test harness regressions and a
  Surefire heap ceiling only after integration. The plan therefore includes
  focused post-merge reruns and bounded-log diagnosis rather than assuming a
  successful textual merge is behaviorally complete.
- Network, display, or native-tool tests may require capabilities unavailable
  in the sandbox. Such results must be reproduced under the appropriate
  approved environment before they are classified as baseline or merge
  failures.

## Human review boundary

The coordinator will present the merged history, decisions, changed files,
test-session evidence, unresolved risks, and end-to-end review. Updating or
pushing `next` is a separate action and occurs only after explicit human
confirmation. Immediately before that action, the coordinator fetches again,
verifies the frozen target is unchanged, runs the exact range policy on the
reviewed head, and records the intended local branch update and push command.
