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
- committed local `develop`: `f1b82774d4aeb9585e75bd74e90856e7b67256d7`
- `origin/develop`: `f1b82774d4aeb9585e75bd74e90856e7b67256d7`
- merge base: `59e59c8feb5fb5a247ff0ab43da63aeccc742cb0`

Local `develop` is clean and matches the fetched remote-tracking tip. The
integration consumes this exact committed tip, never workspace dirt. During
plan review, both refs advanced together from `9b46505eb` by merge commit
`f1b82774d` (`bugfix/ai-s2-cadence-tests`). The source was therefore re-frozen,
the merge simulation repeated, and both design and plan returned through their
independent review loops before implementation.

From the merge base, `next` and `develop` have 611 and 1,868 unique commits
respectively. They changed 2,324 and 1,933 files, with 228 paths touched by both
sides. An isolated recursive merge simulation reports 76 conflicts: 49
production files, 17 test or guard files, and 10 build, policy, or documentation
files.

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
is frozen `develop` `f1b82774d`. This keeps the 0.7 development line legible
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
baseline only. Frozen `next` ignores `/target/*` but not a symlink named
`target`, so ignoring the link must be established before the coordinator takes
its first source digest. The launcher sets an inherited, process-local
`GIT_CONFIG_COUNT` entry for `core.excludesFile` that points at the pinned file,
whose sole effective pattern is the root `/target` entry. It does not mutate
repository or user Git configuration. The launcher and exclude file are named
in `OPENGGF_RUNTIME_INPUTS`, and the adapter validates the inherited config and
file hash before creating anything.

The adapter runs as the coordinator's child command in a newly created
immutable detached worktree and:

1. requires the detached worktree to match `84d9a3761`, have no tracked or
   untracked source changes, and have no existing `target` path;
2. derives the coordinator session roots from its injected `OPENGGF_*`
   identity, then creates only an ignored `target` symlink to the
   session-owned build root;
3. creates build-root links for `surefire-reports`, `test-tmp`, trace reports,
   diagnostics, artifacts, and distribution to their coordinator-owned roots;
4. resolves frozen `next`'s platform-effective `surefire.argLine` under the
   active Maven profiles, preserving CDS, Mockito agent, heap, and macOS
   `-XstartOnFirstThread` options; appends fork-specific
   `org.lwjgl.system.SharedLibraryExtractPath=<session tmp>/lwjgl-${surefire.forkNumber}`
   through the frozen POM's own `${surefire.argLine}` expansion; and separately
   supplies Maven user property `-Djava.io.tmpdir=<canonical session tmp>`,
   whose Surefire user-property promotion replaces the historical plugin-local
   value in the running fork before tests execute;
5. installs its cleanup trap before creating any link and writes a session-side
   recovery marker containing the canonical worktree path, link path, run ID,
   and exact session build target; and
6. removes only the ignored `target` symlink after a no-follow `lstat` confirms
   that it is a symlink and its canonical target exactly matches the recorded
   session build root. It never recursively deletes, follows, replaces, or
   empties `target`.

The orchestrator invokes the pinned launcher with expected harness commit
`f1b82774d`. The wrapper and `TestSessionCoordinator.java` must resolve beneath
an immutable detached worktree at that exact commit. Before launch, the
launcher asserts the harness worktree's detached clean identity and byte-checks
`tools/testing/test-session.sh` and
`tools/testing/TestSessionCoordinator.java` against their corresponding blobs
from `git show f1b82774d4aeb9585e75bd74e90856e7b67256d7`; a wrapper or coordinator
outside that worktree, at another commit, or with different bytes is rejected. It then
starts the coordinator with `OPENGGF_RUNTIME_INPUTS` already containing the
launcher, exclude file, adapter, wrapper, and coordinator-source paths. The
coordinator reads and hashes those inputs before the child starts, so any
mid-run change invalidates the session. The exact harness commit, Git-config
environment, and input hashes are recorded in the adapter evidence, and the
adapter asserts an empty
`git status --porcelain --untracked-files=all` both before and after link
creation while the run is active. Adapter commands may invoke Maven lifecycle
phases such as `test` or `package`, but must never include `clean`: the
historical clean plugin could unlink the routed `target` symlink and recreate a
worktree-local directory.

An outer recovery/finally step runs after every coordinator outcome. This is
separate from the child trap because forced process-tree termination can prevent
the trap from running. Recovery accepts only a marker whose run ID, canonical
worktree, link path, and link target agree with the just-started session; it
uses no-follow inspection and unlinks only that exact symlink. A missing,
ordinary directory, changed target, or mismatched marker is reported for human
inspection and is never deleted automatically.

The adapter is test infrastructure, not a production or gameplay change. Its
self-test suite covers successful Maven completion, ordinary Maven failure, and
forced child-process termination. Every case checks exact detached HEAD, clean
tracked/non-ignored source inventory, post-run `target` absence, and unchanged
source digest. The forced-termination case proves the outer recovery path, and
a negative case proves that an ordinary directory or mismatched symlink is left
untouched.

The successful self-test selects enough frozen-next guards to force at least two
Surefire JVMs with a two-fork configuration. It requires start/end markers and
a valid manifest, proves every expected Surefire XML is in the manifest
inventory, and extracts the report JVM properties and process evidence before
the link is removed. At least two distinct fork identities must expose two
distinct resolved `lwjgl-<fork>` directories beneath the session tmp root; a
literal unresolved `${surefire.forkNumber}`, duplicate path, or path outside
the session fails the adapter.

Frozen `next`'s POM normally reports `java.io.tmpdir` lexically as
`<detached worktree>/target/test-tmp`. That spelling is not acceptable through
the compatibility symlink: path-containment tests compare lexical roots with
canonical children, so the symlink creates adapter-only failures even though it
resolves to the right bytes. Empirical frozen-POM runs proved that neither
`-DargLine` nor `-Dproject.build.directory` displaces that plugin configuration.
The supported mechanism is Maven user property
`-Djava.io.tmpdir=<canonical session tmp>`: Surefire promotes it into the
running fork after startup, and reports/tests observe the direct canonical
root. The startup argument still resolves through the authenticated
`target/test-tmp` link to the same session bytes; it is not accepted as the
reported test-visible identity. Before cleanup, the adapter records lexical
and canonical values, run ID, and session root in diagnostics and requires both
reported temp values to equal that root. LWJGL remains injected through
`${surefire.argLine}` as a distinct per-fork child beneath it. A mutation test changes a scratch copy named in
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

The launcher's outer recovery is mandatory. Before Maven, the adapter writes
the authenticated recovery marker with run ID, frozen HEAD, canonical
worktree/report paths, exact preimage archive path/hash/length, and target-link
identity. If forced termination bypasses the child trap, outer recovery uses
no-follow type checks and those identities to restore only that exact report.
It may restore for hygiene without Surefire proof, but such a run remains
non-certifying. Empirical INT/TERM tests against the exact authenticated frozen
coordinator show that its shutdown hook forcibly terminates the adapter before
the child trap can finish, then takes the dirty final digest. The binding
launcher-signal outcome is therefore `INVALID_IDENTITY_CHANGED`, followed by
outer restoration after finalization. It is never `PASSED`, `ABORTED`, or valid;
the restored worktree does not retroactively change the invalid manifest.

The exact outcome matrix is binding. Child status 0 plus authorized successful
normalization yields status 0 / `PASSED` / `valid=true`; child status N plus
authorized successful normalization preserves N / `FAILED` / `valid=true`.
Normalization failure after child 0 yields nonzero /
`INVALID_IDENTITY_CHANGED` / `valid=false`; after child N it preserves N while
the manifest is `INVALID_IDENTITY_CHANGED` / `valid=false`. Launcher signal
propagation preserves 130 or 143 and yields `INVALID_IDENTITY_CHANGED` /
`valid=false`, followed by authenticated outer worktree restoration. The
archived generated report is parent hygiene evidence, not a repository
deliverable and not a parent test failure.

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
and verifies all four refs (`next`, `origin/next`, `develop`, and
`origin/develop`) against the frozen hashes. Drift on either line pauses
promotion, updates both design and plan, and requires a reviewed reintegration
and repeated affected baselines. A later target update is attempted only after
the same drift check. Because the original branches remain unchanged, rollback
before promotion is branch abandonment; any partial remote update is recovered
with a new reviewed commit or an explicit human-authorized remote correction,
never a forced rewrite inferred by the agent.

## Verification design

### Parent baselines

Record exact, wrapper-produced ordinary and structural-guard sessions for both
frozen parents in immutable detached worktrees. `develop` uses its in-tree
wrapper and `-Pguards` profile. Frozen `next` runs the wrapper and coordinator
from frozen `develop` `f1b82774d` by absolute path while its process working
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

Attempt the ordinary suite as one certifying session first. If the Surefire JVM
exhausts memory or terminates before producing a complete inventory, retain that
failed session and run an OOM-safe deterministic partition of the complete test
class inventory through separate wrapper sessions. Each executable class must
appear in exactly one successful partition, with zero missing or duplicate
classes, and the aggregation manifest records every child run ID and outcome.
Use one deterministic union partition map across all three trees, filtering
each partition to classes present in the tree being run so parent-only or
merged-only classes do not create false selector failures. Preserve the union
slot identity in the aggregation report. This partitioned aggregate, plus the
separate fresh-JVM guard session, is the certifying fallback; a partial full run
is never reported as the suite result.

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
   second parent is frozen committed `develop` `f1b82774d`.
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

- Local and fetched remote-tracking refs currently agree on both frozen tips.
  Any source or target drift before merge or promotion triggers the amendment
  and review process rather than being silently absorbed.
- Both parents may have pre-existing red tests. Exact outcome comparison, not a
  simplistic all-green requirement, governs acceptance.
- Trace payload volume makes file and line counts poor estimates of manual
  effort; the 76 semantic conflicts and post-merge behavioral comparison are
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
