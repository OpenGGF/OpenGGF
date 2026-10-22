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
- [x] Inspect the category plan against the pinned integration SHA and preflight
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

The first combined run at `d8424987434505b4b17e393af6f7dfdbcc99fefa`
was deliberately superseded after 52 of 72 ordinary batches: 21,158 tests,
two failures, zero errors and 84 skips. It is incomplete evidence, not a smoke
pass; guards had not started. Preparation took 84.67 seconds and the completed
ordinary batches used 1,951.16 seconds of execution. Admission waiting is
additional; total queue time is not established here.

The scene-act failure reproduced at the original assertion: mode entry replaces
a context-installed observer when it installs its history reporter. Test-only
correction `09514d823a411c0d30b4ec64cce7c576764a37a7` observes through the
loop, preserves the original exit assertion, and adds a production-reporter
scenario proving real owned history, retirement on scene return and rejection of
retained playback handles. Four affected scene/course restore cases passed with
zero failures/errors/skips. No production admission behavior was weakened.

`TestHczMinibossNativeRendering#allOrbitPhasesMatchRomSpriteCompositionInNativePixels`
failed at line 53 because direct GLFW initialization requires a display. The
unchanged test reproduced the identical assertion on both pinned `23a18e2a2`
and candidate `d84249874`, each one test/one failure/zero errors/skips under the
same display-unset EGL environment and verified original ROMs. Neither reached
pixel verification. This is an inherited coverage limitation, not a pixel pass.
All 84 recorded skip reasons were inspected with no omitted cases: opt-in
diagnostics, unavailable graphics prerequisites, inapplicable route cases and
absent optional Second Strike JAR fixtures. No original-ROM skip was present.
Consumed category diagnostics were acknowledged and deleted.

The reason to stop this task-owned run was materially changed destination source,
not admission delay. `develop` first advanced to `1c7e412f4` with ghost-label and
promo changes, then to `21340080222077c92ebe868eb97979e4e232ed1e` with 114
additional object/render/index, rewind-comparison and policy files. In particular,
upstream added a shared primary-record declaration probe and edits to both hooks,
overlapping this campaign's private-ownership correction. Finishing the old
source's remaining batches would not validate this reconciled candidate.

An isolated integration lead composed `09514d823` at `49a40158` and is now
reconciling immutable upstream `213400802`. Preserve the upstream record probe
and this campaign's fallback ownership/enum/default handling. Reproduce the
public-record/nested-annotation array-default interaction before correcting it;
preserve record private-array and executable-body exclusion. Focused hosted-run,
ghost, owned-rewind, native-object-index and snapshot checks plus structural,
signature, hook and Python release-policy checks precede the new combined run.
The coordinator will inspect the actual updated selection and validate the
changed candidate. This is a source change requiring new coverage, not repeated
unchanged baseline/development/integrated ceremony.

The reconciled integration head is `085cf2b6f8f00e02ea024deab7f29963ffa53578`
(tree `5bc055fd3de8625f74b7ea9b9a071d39462497ce`), composed at coordinator
`e53b1051`. Only the two hook files conflicted with upstream. Both keep its
primary-record probe and the byte-identical `526098af0` fallback. A reproduced
record/nested-annotation array-default omission caused four incorrect admissions
across the two mirrors; a bounded probe correction retains declaration values
while excluding default-method bodies. API pins, capabilities, caps and frozen
records are unchanged. The lead reviewed the complete correction and verified
that all executable and test source matches the focused-tested child exactly.

Focused verification on that settled source passed with zero failures, errors
or skips: 132 integration cases across eleven hosted-run/ghost/rewind/native-index/
snapshot classes; one separately enabled HCZ reload case; 296 cases across eight
structural/signature/policy classes; and 42 Python policy cases. The Python command
was `python3 -m unittest tools.testing.test_release_snapshot_policy`; Maven used
the shared queue, verified absolute ROM paths and the same display-unset EGL
environment. Consumed reports were removed. Exact selectors remain in the owned
integration checkpoint. These selections overlap earlier focused evidence and
are not a complete smoke result.

The new change-based plan from pinned `23a18e2a2` selects 3,594 candidate classes,
all ten smoke categories and separate guards, still in 72 ordinary batches.
Retain the 120-minute execution budget, excluding queue waits, and allow roughly
one to two hours of execution plus unknown admission delay. Clean obsolete build
output before preparing this changed source. No second unchanged broad run is
planned after integration; repeat affected checks only for new changes, failures
or unresolved risk. Final destination ancestry and source identity will be checked
before integration and push.

The coordinator's actual launch-environment preflight passed with no tests:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl python3 tools/testing/run_categories.py --base 23a18e2a232d38cca4a541be586041e9b7b01163 --rom-directory "$OPENGGF_MAIN_WORKTREE" --preflight
```

Here `OPENGGF_MAIN_WORKTREE` names the existing main checkout recorded in the
external task registry. Existing main-root `s1.gen`, `s2.gen` and `s3k.gen` hashes match the required
original ROM identities. No ROM links/copies were made. Required graphics flags
will be retained; launcher notices and reused-JVM property changes remain the
documented attribution hazards rather than reasons to strip those flags.

Main remains on `develop`, now at observed upstream `213400802`; no campaign
integration push has occurred. Coordinator hooks are installed. The registry
under `~/.local/state/temporary-agents/` owns exact task IDs, workspaces, tested
source, job handles and expiry. Next: inspect the reconciled integration handoff,
compose it and inspect the new combined category selection. The superseded
coordinator job is closed and its diagnostics acknowledged; the integration
lead's focused work is complete and composed. The updated combined run is next;
no complete smoke/guard pass has been claimed.

The replacement combined run `20261022T181843Z-a6e0e23c` completed all 72
ordinary batches on clean source `575b3a62d`: 37,719 tests, 151 failures,
1,018 errors and 93 skips. Separate guards passed 722 cases with no failures,
errors or skips. Preparation took 81.49 seconds, ordinary execution 5,800.93
seconds and guards 280.67 seconds; queue waiting is additional and its total is
not established. This is a completed red smoke run, not a delivery pass.

The earlier HCZ GLFW assertion remains inherited. Newly exposed rewind failures
include FBZ boundary observation, Slot replay's one-frame dynamic-art diagnostic
offset, and lifecycle/capture consumers dereferencing a retired context's null
controller during world replacement. These require root-cause investigation and
bounded correction before integration. All 93 skip identities/reasons were
inspected with none omitted: opt-in diagnostics/captures, unavailable graphics
prerequisites, unsupported route products, optional creator fixtures and one CPZ
spin-tube capture prerequisite. None reported a missing original ROM. These are
coverage limits, not passes for the skipped behavior.

Failure details are capped at 1,000 cases; 169 additional failures/errors were
omitted. Recover their affected identities narrowly rather than infer success
from aggregate totals. A fresh isolated rewind implementation child starts at
`575b3a62d`; a separate matched baseline selection checks five representative
methods at pinned `23a18e2a2`. The coordinator owns bounded recovery of late DEZ
capture failures and final review. Retain original semantic assertions and
diagnostic comparison, migrate stale consumers only when ownership evidence
warrants it, and fix production recording at its owning boundary if reproduced.
No duplicate broad selection is queued. Main remains unchanged at `213400802`;
integration, push, diagnostic acknowledgment and accounted cleanup remain open.

The fresh remediation child reproduced two narrow failures on the original
runtime: the FBZ observer assertion and a new Slot diagnostic floor comparison
(`latestFrame` 87 versus 86; logical frame 88 versus 87). Its bounded production
correction defers live recording and consumed-frame floor capture until successful
PLC publication, retaining immutable input/pacing and revalidating installation,
controller, input source, level and providers. Pre-tick assembly stays immediate.
Body/publication failures discard queued captures. Independent read-only review
found no current-consumer blocker in the three-file correction. A coordinator
reset cannot cancel a queue already detached for draining; current teardown marks
its owner unavailable first, so callback admission rejects history mutation.
Do not generalize that into a blanket cancellation guarantee.

Lifecycle tests now distinguish retired windows from ready destination history,
retain route/Saved2/floor/replay assertions, and prove stale handles cannot mutate
the live world. FBZ observes the loop-installed reporter and waits for completed
fresh assembly. The D01 ending is native LEVEL; its reveal retains a completion
callback after title/fresh flags clear. Preserve composition/position/actor checks
at their original point, then wait bounded neutral frames for the first admitted
production controller before requiring its distinct frame-zero history. Later
rescue, recording, full replay, credits and terminal Start assertions remain.
Neither migration warrants a boss or general snapshot-reference repair.

Corrected-source focused evidence now includes complete Portal/LRZ/Sanctuary
Origin/Pedestal matrices (568/4/580/1,426 cases), all seven DEZ Incoming cases,
60 runtime cases (Deferred Setup 3, PLC 30, Owner Lifetime 14, Input Source 13),
one FBZ replay case and 15 architecture/API guards, all with zero failures,
errors or skips. These counts combine unchanged passing methods with exact
failed-method reruns; they overlap earlier checks and are not a new combined
smoke pass. One attempt failed test compilation with no tests executed; two
new harness errors were repaired without changing the reviewed production
correction. Passing methods were not repeated solely for those repairs.

Matched original-baseline verification at `23a18e2a2` passed eight invocations
across five existing FBZ/Slot/LRZ/Sanctuary/DEZ methods with no skips; their test
files were byte-identical to `575b3a62d`. Separate original-source DEZ recovery
passed all eleven Cold Route cases and exposed four Incoming failures, now
corrected. Nine short SSZ/SOZ gap checks also passed with no skips, but do not
identify the original omitted cases.

Three DEZ Solo cases pass; the Tails case fails initial controller construction
because an escape object's captured parent is absent. Its exact original-baseline
method passed one case with no failures, errors or skips; the test file matches
`575b3a62d` and pre-enable stimuli are unchanged. Source comparison confirms an
admission bypass: owner binding eagerly installs/captures before the loop checks
transition gates, whereas the baseline installs after admission or recording.
An exact current-source probe confirms the first installation at level frame
62,359 is ready and unblocked. The failing same-owner reinstallation at 62,455
is ready but rewind-blocked, non-rewindable, fading with pending completion and
inactive for transition; its eager constructor immediately captures the absent
parent. The exact Tails method also passed one case with no failures, errors or
skips on reconciled destination `213400802`, after a clean build. Neither baseline
reproduces the exception. This attributes the early capture to the campaign;
upstream snapshot changes are not a reason for a boss-reference repair.

Removing eager installation wholesale was considered and rejected: explicit
initial mode-entry and completed-assembly callers rely on binding before an
identity-scoped reporter, which cannot bootstrap an absent installation. Simple
deletion would silently drop their boundaries. The bounded correction under
verification instead rejects opportunistic object-world binding while the actual
level body is inactive for transition. Preserve valid bootstrap, special-provider
entry, stale-reporter rejection, frame-zero contracts and allowed cosmetic-fade
completed-tick recording; do not add blanket fade/pending-exit guards. The probe
is temporary and must be removed before commit. The original
omitted three failures/three errors remain unattributed. Final scope, local
commit, composition, adequate final coverage, integration, push and cleanup remain
open; no green campaign delivery is claimed.

The ROM-free inactive-binding control reproduced one intended failure before
the five-line gate. With the gate, that control, an inactive-outgoing-level special
provider control and the exact Tails route all passed: three tests, no failures,
errors or skips. Temporary probes are removed. Independent incremental review
found no blocker in production diff `c3b458d8`; reconciliation/deferred-boundary
draining still precedes the gate, and only `LEVEL_FRAME` opportunistic binding
is gated. The child is verifying 53 affected owner/entry/fresh-assembly/fade/
portal/D01 cases and four architecture guards. Unchanged PLC/input code and the
full matrix are not repeated solely for this gate.

All seven DEZ Incoming cases are now green through three unchanged passing
branches plus four exact reruns after the native-fade wait correction; the four
reruns exercised original composition and later rescue/replay/terminal assertions.
These remain bounded focused results. After the child's remaining checks, local
commit and exact-source composition, one complete changed-source smoke/guard
selection is planned to resolve the omitted-case gap. The dry-run still selects
3,594 candidate classes, all ten smoke categories, 72 ordinary batches and separate
guards. Expect roughly two hours of execution plus unknown queue delay; retain
the 120-minute execution and ten-minute no-output limits. A bounded stop is
incomplete validation, not a pass. No new broad request has been launched yet.
