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
- [x] Rewind: discriminating owner/release/park/provider/callback/recording tests
  and native pause/setup/PLC behavior, then bounded implementation and focused
  verification. Record reproduced failures separately from structural risks.
- [x] Audio: source publication and generation/rollback regressions plus affected
  low-level architecture guards; no public signature or broad rule weakening.
- [x] Preview: exact conversion output and migrated consumer regressions plus
  affected architecture guards; no duplicate compatibility conversion path.
- [x] Compose all lanes, reconcile intentional frozen-identity changes and update
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

## Following bounded cuts

After this campaign, use each ratchet as a migration queue with a named owner
boundary and behavior evidence, rather than treating a passing cap as progress.
Review and prune only the identities actually solved by each cut.

- The single remaining framework-root record belongs to
  `ScriptFramesApplyStrategy`. A separate graphics-owner injection cut can close
  that rule's frozen debt without reopening live rewind.
- The remaining cross-game allowance separates ten host-ROM donation members
  from four shared menu-animator members. The initial <=20-member target has
  been reached. Choose <=10 members next by extracting the animator in its own
  bounded change while preserving explicit donation.
- Low-level audio/graphics and the nine shared-to-concrete title-preview records
  need their own caller-first boundaries. The private audio tuple correction
  deliberately does not claim to solve these. Keep the existing low-level target
  and review ROM-title preview capabilities separately before implementation.

Package-pair permissions and source-size caps remain enforcement budgets. Measure
actual dependencies before choosing the next removal, and do not infer an
acyclic graph from the empty cycle store after explicit ignores.

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

Fresh isolated requests now running from coordinator commit `15163c5d2`:
`architecture-paydown-rewind-20261022-r1`,
`architecture-paydown-audio-20261022-r1` and
`architecture-paydown-preview-20261022-r1`. Rewind may use fresh bounded entry
audit and regression-authoring helpers; each lane lead owns focused checks.
Earlier review agents are not reused for implementation.

Preview commit `f201a784d22e7834ac4abb16eea72fb121b0ba6a` is complete on
`bugfix/ai-shared-selected-preview-20261022`, retained clean at
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-8076f2d7`. The lead inspected its
production, test and frozen-record diffs. A fresh independent read-only reviewer
found no actionable defects, confirmed unchanged conversion/validation bodies,
complete consumer migration and exact freeze pruning, and ran `git diff --check`.
It did not repeat Maven or independently exercise interactive presentation.
Moving the converter/result/validator into `game.dataselect` removes
20 cross-game member dependencies, leaving 14 in two grouped records. The
conversion and validation bodies are unchanged. The worker's clean post-move
check covered 60 converter/validator/profile cases and 13 presentation cases;
one new rendering expectation incorrectly assumed forward piece submission.
Only that assertion was corrected to the unchanged reverse traversal, then all
13 affected presentation cases passed. The other 60 retained their passing
evidence. The 114 architecture guard cases also passed. All completed checks
reported zero errors/skips; the initial rendering expectation was the sole
failure. Exact commands remain in the delegated task handoff.

Audio has five staged lane files at
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-096b49e7` on
`bugfix/ai-audio-source-typing-20261022`, based on `15163c5d2`. Its 152
guard/API-pin cases and 88 focused audio cases passed with no failures, errors
or skips. Its deliberate unchanged-store check rejected the exact private
identity migration. The low-level record change is 184 to 187: deleting the
dummy `AudioManager.rom` sentinel and typing the actual immutable tuple adds
real constructor/field/accessor/helper dependencies. This is honest typing and
accounting, not debt removal. Publication, generation and audio behavior remain
unchanged.

The normal audio commit exposed a policy-hook false positive: its textual API
projection includes an annotation on the deleted private sentinel and the
private record's component declaration. The exact candidate API snapshot
passes and cannot express those private changes. A bounded coordinator helper
owned only both hook mirrors and `TestModApiHookPolicy`. Correction
`fdddd710e0f929dc8bf3a227dd1613e27d3b58c5` keeps annotations with their
declarations and excludes private type subtrees. The original projection failed
three intended regressions in a 29-case run. Both mirrors then passed 152
`TestModApiHookPolicy,TestBuildToolingGuard` cases with zero failures/errors/skips,
including visible-signature, annotation and promotion controls; shell/PowerShell
syntax checks passed. The actual staged audio projections now match the base
exactly. The lead reviewed the complete mirrored correction. Audio is composing
this commit through normal hooks; no public pin or declaration disguise is used.

Rewind commit `10544b1ca0fee4395d6bf666cb45309b8dbcff04` is complete at
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-34d24551`, with fresh independent review
pending. Its 36 owned files are committed; two inherited dirty disassembly
submodules remain untouched. Ten framework-root records were removed, with no
replacement allowances; `ScriptFramesApplyStrategy` remains. Focused selections
reported 167/118/14/162 cases with zero failures/errors after implementation
corrections. These overlap and must not be added as unique coverage. The 118-case
selection skipped one opt-in checkpoint performance measurement; the follow-up
explicitly ran HCZ's slow reload and the InfiniteSonic scripted-rewind scenario.
No baseline runtime failure was reproduced. An initial public level-boundary
setter was rejected by policy and replaced with the internal access bridge;
reporter installation was extracted after the unchanged GameLoop size cap
rejected the first candidate. Neither API pins nor source budgets were raised.
No combined smoke has run.

Fresh rewind review of `10544b1ca` found one integration defect: eager
`bindOwner` installation captures frame zero when title-card exit changes to
bonus mode, before deferred bonus assembly finishes. S3K's slot coordinator then
rejects restoring that pre-assembly snapshot into its assembled runtime. The
original lead is reproducing the real title-card path and correcting the
installation boundary. Review found no other concrete lifetime/admission gaps;
it ran no Maven. Do not compose this candidate until the correction is verified.

Audio completed normally at `aa62f72fd8d3c38fda0191ff29346e852089306e`,
with prerequisite `2056713d6d7ad118c9e45d8a6a74e8cffcefdb58` cherry-picked
from `fdddd710e`. Its five-file diff stayed byte-identical through that
composition; normal hooks passed and the checkout is clean. The 240 unchanged
focused cases were not repeated. A fresh audio/policy reviewer found a new hook
counterexample: an enum constant list without a trailing semicolon is discarded
when closing the type, missing a visible API edit and rejecting its valid pin as
pin-only. The same reviewer reproduced annotation array defaults being skipped
as bodies even though the exact signature snapshot includes those values.
No audio defect was found. The original hook helper owns a bounded mirrored
correction and red regressions for both declaration cases. These are introduced
behaviors to fix before delivery, not reasons to weaken pin coupling. Preview is
composed at `2ea26c3c6`.

Both follow-ups are now composed and lead-reviewed. Rewind correction
`a5562b6cbc5ef4da78119524a500d91c72cbae82` reproduced the real slot exception
and fresh HCZ entry restoring world frame two at rewind frame one. It roots
history after completed destination assembly, verifies the assembled owner still
owns the loop, and preserves native setup cadence. Its changed-source selection
passed 139 cases with zero failures/errors/skips, including both regressions,
title-card/admission behavior, capabilities, API signatures and architecture.
Policy correction `526098af0f9b6b3671fa43b65637cb4e3a640778` reproduced four
enum failures in 35 cases and two annotation-array failures in five targeted
cases. Final hook/build-tooling coverage passed 163 cases with zero
failures/errors/skips across both mirrors. Its measured queue wait was 701
seconds and Maven execution 98 seconds. No duplicate request was submitted.

Composition at `b242c9db8` preserves both tested policy-fix blobs and all lane
source. Audio's duplicated earlier hook prerequisite conflicted with the later
policy correction; exact blob comparison confirmed its prerequisite matched
`fdddd710e`, and the three conflicts kept the tested `526098af0` files. The
exception-guide conflict reconciles low-level 187, framework-root 1 and cross-game
14 members in two groups. No rule keys, predicates or source budgets changed.
Combined prose also picks the next cross-game target of <=10 members.

The dry-run selection against pinned `23a18e2a2` includes 3,583 candidate classes
across all ten smoke categories and separate guards because loop/shared/policy
changes cross subsystem boundaries. This is routine smoke, not the release-only
full suite. Allow roughly one to two hours of execution plus unknown queue delay,
with a 120-minute execution budget excluding admission waits. Keep the existing
request alive; after results, attribute disputed failures with matched narrow
baseline/current identities and fix only introduced regressions. No unchanged
baseline/development/integrated broad repetition is planned.

The coordinator's actual launch-environment preflight passed with no tests:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl python3 tools/testing/run_categories.py --base 23a18e2a232d38cca4a541be586041e9b7b01163 --rom-directory "$OPENGGF_MAIN_WORKTREE" --preflight
```

Here `OPENGGF_MAIN_WORKTREE` names the existing main checkout recorded in the
external task registry. Existing main-root `s1.gen`, `s2.gen` and `s3k.gen` hashes match the required
original ROM identities. No ROM links/copies were made. Required graphics flags
will be retained; launcher notices and reused-JVM property changes remain the
documented attribution hazards rather than reasons to strip those flags.

Main remains `develop` at the pinned base; no integration push has occurred.
Coordinator hooks are installed. The registry under
`~/.local/state/temporary-agents/` owns task IDs, workspaces and expiry. Next:
inspect completed lane diffs, compose and review, then inspect the actual
combined category selection before running it. No live Maven job belongs to
the coordinator; no combined suite has run.
