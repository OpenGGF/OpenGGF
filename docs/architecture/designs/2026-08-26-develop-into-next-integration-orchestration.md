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
- committed local `develop`: `9b46505eb10675a4ab19772dd2707566a0432973`
- `origin/develop`: `9b46505eb10675a4ab19772dd2707566a0432973`
- merge base: `59e59c8feb5fb5a247ff0ab43da63aeccc742cb0`

Local `develop` is clean and matches the fetched remote-tracking tip. The
integration consumes this exact committed tip, never workspace dirt. The
earlier audit observed `origin/develop` four commits behind; the remote advanced
without changing the selected source hash before the design review completed.

From the merge base, `next` and `develop` have 611 and 1,866 unique commits
respectively. They changed 2,324 and 1,931 files, with 228 paths touched by both
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
is frozen `develop` `9b46505eb`. This keeps the 0.7 development line legible
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

The integration branch provides a pinned, reviewed compatibility adapter under
`tools/testing/` for this historical baseline only. The adapter runs as the
coordinator's child command in a newly created immutable detached worktree and:

1. requires the detached worktree to match `84d9a3761`, have no tracked or
   untracked source changes, and have no existing `target` path;
2. derives the coordinator session roots from its injected `OPENGGF_*`
   identity, then creates only an ignored `target` symlink to the
   session-owned build root;
3. creates build-root links for `surefire-reports`, `test-tmp`, trace reports,
   diagnostics, artifacts, and distribution to their coordinator-owned roots;
4. invokes Maven with an override of frozen `next`'s user-defined
   `surefire.argLine` that preserves its CDS, Mockito agent, and heap options
   while adding
   `org.lwjgl.system.SharedLibraryExtractPath=<session tmp>/lwjgl-${surefire.forkNumber}`;
5. declares the adapter itself in `OPENGGF_RUNTIME_INPUTS`, so the coordinator's
   runtime-input digest detects any change during a run; and
6. removes only the ignored `target` symlink on exit, leaving the immutable
   source identity unchanged and the session evidence intact.

The adapter is test infrastructure, not a production or gameplay change. Its
self-test runs one frozen-next guard through the pinned coordinator, requires
start/end markers and a valid manifest, proves the expected Surefire XML is in
the manifest inventory, and verifies the report's JVM properties point
`java.io.tmpdir` at the session root and LWJGL extraction at the resolved
per-fork path. The full parent baseline cannot start until that self-test and
the coordinator's own self-test pass.

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
from frozen `develop` `9b46505eb` by absolute path while its process working
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
and `PASS|FAILURE|ERROR|SKIPPED`. Duplicate identities are invalid. A test
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
classification. Record any legitimate pre-existing red, sandbox-only
limitation, or explicitly deferred risk without weakening tests. Install the
tracked `develop` hook path explicitly in every worktree that lacks the current
installer, and record the exact final range-policy invocation and its base/head
hashes.

## Acceptance criteria

The integration is ready for human review only when:

1. The integration merge commit's first parent is the reviewed pre-merge
   integration-branch commit rooted at frozen `next` `84d9a3761`, and its exact
   second parent is frozen committed `develop` `9b46505eb`.
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
