# Architecture debt paydown

## Delivery scope

Deliver the user-authorized accounting patch first, then fresh delegated work on
live-rewind ownership, private audio-source typing and shared selected-slot
preview conversion. Integration starts from `develop` at
`23a18e2a232d38cca4a541be586041e9b7b01163`. Main was fast-forwarded from
`8aa692b04`; its dirty reference submodules and unrelated untracked files remain
outside this task. The coordinator is isolated on
`bugfix/ai-architecture-paydown-20261022`.

The preceding architect review compared an installed-owner design with an
operation-scoped replay context. Both configured candidates and the cross-review
used GPT-6.1-Sol. The installed-owner design won because it concentrates lifetime
policy in the existing manager. Adopt the other candidate's executed-tick owner
provenance and deferred, identity-checked boundary notifications. Reject a
mutable scoped `ReplayEntry`, controller subtype checks at every mutator and a
general context/debt framework: existing course restoration legitimately
invalidates history outside such a scope.

ROM title-preview capability redesign, broad `LevelManager` decomposition,
public Mod API expansion and the unverified CNZ scoring concern remain outside
this campaign. October's merged remediation (`4a26a651e`) is not reopened.

## Ownership and implementation boundaries

- **Accounting worker:** `TestArchitecturalReviewGuard`, narrowly related test
  helpers and `docs/architecture/archunit-exceptions.md`. Count actual logical
  records through pinned ArchUnit semantics. Preserve description keys, disabled
  normal store updates, predicates and violation stores. Distinguish allowances
  from measured live violations. At the starting tree, low-level/framework/
  registry stores contain 184/11/2 records; cross-game has three grouped records
  with 34 dependency details, not 37 records. The old target of 20 needs explicit
  member-dependency units. The 159 allowed package pairs are permissions.
- **Fresh rewind lead and bounded helpers:** `LiveRewindManager`, live/special
  steppers, loop/lifecycle/recording callers and their focused tests. Audit actual
  advancing, seeking, recording, playback-wrapper, auditor and callback paths
  before claiming admission closure. Preserve `CourseControl.restore()`'s
  owner-directed future invalidation. Helpers own disjoint test files or a
  read-only entry audit; the lead owns focused execution after edits settle.
- **Audio worker:** private `AudioManager.BaseAudioSource` representation,
  sentinel-specific tests and its exact low-level frozen identities. Restore
  `Rom` typing, remove the dummy null field/casts, preserve immutable volatile
  publication, generation/overflow, rollback and reentrancy. Leave
  `GameAudioProfile.loadSegaPcm(Object)` and actual game-owned ROM reads alone.
  This is an honest identity migration, not automatically debt reduction.
- **Preview worker:** canonical conversion/result/common validation under the
  existing shared data-select package, S1/S2/S3K consumers, focused tests and
  corresponding cross-game freeze records. Preserve dimensions, transparency,
  quantization ties, palette line, pattern/tile offsets, mapping and ROM donation.
  Delete superseded adapter paths; keep title animation/decode separate.
- **Coordinator:** this plan, combined release/guide/exception prose,
  composition, independent review, combined validation, main-branch integration,
  push and cleanup. Workers report exact baseline deltas rather than competing
  to edit shared prose after the accounting phase.

## Rewind contract

1. History and its stepper are bound to one installed gameplay owner. Derive
   managers and a fresh `LevelFrameContext.from(owner)` per replay entry; sample
   once and pass coherent locals through PLC, bonus, controlled and pacing paths.
2. Admission precedes replay advancement or restoration. `PAUSED` is a real
   frame result and can advance the cursor; it cannot reject a stale operation.
   Preserve `SETUP_ONLY`, native timing and comparison-only trace authority.
3. Parking retains resumable history. Replacement, destruction, stepper-kind
   change and replacement of the registered special provider are distinct
   transitions. Bind special history to the instance used at adapter registration.
4. Pending release retains the outgoing controller, input source/handler, policy
   and fade owner even after context destruction clears manager references.
   Audio remains engine-owned/shared. Incoming installation cannot overwrite
   unresolved release; repeated A→B→C changes cannot install obsolete B.
5. Completed-frame recording carries the owner that executed the tick, rather
   than rediscovering the ambient owner after transition. Boundary callbacks carry
   installation identity, including same-owner reinstallations. Defer reentrant
   notifications and revalidate after replay only where native ordering permits.
6. Retain one active-session product behavior. Do not replace ambient service
   lookup with a second global resolver or impose a protocol on every generic
   controller mutator. If actual replay bypasses cannot share a small admission
   boundary, revisit that boundary before expanding the implementation.

## Execution and validation

- [x] Accounting: meaningful grouped-record/historical-label regressions,
  proportional queued guard checks, local tested commit and lead review.
- [x] Compose accounting before starting new implementation workers, so their
  isolated bases include it. No task branch is pushed.
- [ ] Rewind: discriminating owner/release/park/provider/callback/recording tests
  and native pause/setup/PLC behavior, then bounded implementation and focused
  verification. Record reproduced failures separately from structural risks.
- [ ] Audio: source publication and generation/rollback regressions plus affected
  low-level architecture guards; no public signature or broad rule weakening.
- [ ] Preview: exact conversion output and migrated consumer regressions plus
  affected architecture guards; no duplicate compatibility conversion path.
- [ ] Compose all lanes, reconcile intentional frozen-identity changes and update
  actual accounting/prose once. Review the composed source before costly checks.
- [ ] Inspect the category plan against the pinned integration SHA and preflight
  the actual launch environment. State candidate count, expected execution cost
  and stopping rule before one required combined smoke/guard selection. Focused
  checks do not certify the complete campaign. No routine full-suite run.
- [ ] Attribute disputed failures with matched baseline/current failing identities
  and assertions. Fix introduced regressions narrowly. Inspect skips and retain
  honest coverage limits; do not repair unrelated systems to force green output.
- [ ] Inspect and acknowledge consumed category diagnostics. Integrate into the
  current main-workspace branch without switching it, verify the composed state
  as policy requires, push only that branch and account for every task change
  before removing owned worktrees/merged local branches.

Use the shared Maven queue and one session/observer per job. Do not duplicate
queued/running checks on unchanged source/selection/environment. Queue delay is
pending work, not a failed run. Java 25, Lua 5.4 and PowerShell are checked through
the admitted preflight. Existing absolute main-workspace ROM paths are used;
never create or copy ROM links. Offscreen graphics retain the required EGL flag.
Build trees stay local to each worktree. Compare disputed failures narrowly;
do not repeat unchanged baseline/development/integrated smoke ceremonially.

## Checkpoint

Accounting commit `f24c05a1f1395f3fa854a478aac1fec4e6329727` is composed at
`0eb27d87503decca64b0b68af86d83ffd4545e77`; the lead reviewed both changed
files. The patch changes only `TestArchitecturalReviewGuard.java` and the existing
exception guide. Production code, predicates, frozen records and POM are
unchanged. The accounting worktree is clean and retained on
`bugfix/ai-architecture-accounting-20261022` at
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-93ebe5be`.

The worker's admitted Java probe reported Java 25.0.5. Its two new accounting
cases failed against original accounting, with two intended failures and no
errors/skips. Final focused verification at `f24c05a1f` executed 114 cases with
zero failures, errors or skips:

```bash
python3 tools/testing/maven_queue.py -v
python3 tools/testing/maven_queue.py -Dmse=off -Pguards '-Dtest=TestArchitecturalReviewGuard#frozenRecordCountsIgnoreHistoricalDescriptionLabels+frozenRecordCountsKeepEscapedMultilineRecordsGrouped' test -B
python3 tools/testing/maven_queue.py -Dmse=off -Pguards '-Dtest=TestArchitecturalReviewGuard,TestArchitecturalSourceGuard,TestArchUnitRules,TestArchUnitTestRules' test -B
```

This is bounded guard evidence, not smoke or full-suite validation. XML cases
were inspected by the worker and consumed reports removed; no receipt/archive
was added. The coordinator did not duplicate unchanged focused coverage.

Main remains `develop` at the pinned base; no integration push has occurred.
Coordinator hooks are installed. The registry under
`~/.local/state/temporary-agents/` owns task IDs, workspaces and expiry. Next:
launch fresh rewind, audio and conversion lanes from the composed accounting
base. No live Maven job belongs to the coordinator.
