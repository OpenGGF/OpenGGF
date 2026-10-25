# Architecture, goals and progress audit remediation

## Scope and starting evidence

The October 25 Astra review inspected `159655c8460dac15912dc4326491386a75368666`.
The user authorized changes with Sol implementation workers. Delivery starts at
`f7620e579c1a8cf7b0371f9c5a438f6a4eff5ea7` on `develop`; the intervening private-lobby
design is unrelated and remains intact. This is routine development, not public
release preparation or a claim of complete campaign qualification.

The reviewed findings are bounded:

- Native PCM tests can classify non-graphics `IllegalStateException` failures as
  missing graphics prerequisites.
- An isolated placement helper retires its audio backend rather than its entire
  owning manager. No resource leak was demonstrated by the review.
- S2 Sideways platform allocation failure retains an unregistered Java child;
  native failure instead writes through fixed Tails-tail storage. The existing
  known-gap assertions characterize strict capture failure, not a repair.
- Active roadmaps lag native ending and racing-extraction implementation, omit
  Stageplay's release disposition, and combine simulation correctness with
  optional presentation qualification. Rule-placement prose also describes a
  superseded collision-rule shape.
- Continuous AIZ-to-HCZ evidence must be established on current source. The
  October 17 recorded chain frontier is historical and does not certify this base.
- Hosted publication deliberately has no private-ROM dependency. Existing
  release guards require manual ROM validation; candidate-specific qualification
  must be strengthened with that policy made explicit.

## Work ownership

The parent owns composition, delivery-wide validation, integration, push and
cleanup. Workers use separate T3-managed checkouts, commit locally and return
focused evidence. They do not push or modify the main workspace.

| Slice | Owned outcome | Initial state |
| --- | --- | --- |
| Capture assurance and disposal | Narrow prerequisite admission, non-graphics failure control, complete isolated audio disposal | `dfc7d9784`: 32 focused tests passed, zero failures/errors/skips |
| Roadmap reconciliation | Source-backed implementation/qualification status, Stageplay scope, finite configuration tiers, timing dispositions and corrected architecture prose | `e4fb59d17`: 99 links/anchors and factual/structural checks passed |
| Native fixed-slot qualification | Preserve the native animation prerequisite; qualify storage/removal boundaries without inventing parent bytes | `6fe1b50fa` composed as `1bd1dadea`: 39 focused cases and one exact normalized-signature check pass; storage gap remains open |
| Continuous campaign boundary | Current named AIZ-to-HCZ frontier and smallest ROM-owned correction, retaining strict comparisons | `aa5fb2d34`: load correction passes 149 focused and 43 authority checks; strict prefix reaches HCZ's first row |
| Release preparation | Explicit exact-candidate qualification and required case accounting, consistent with publication policy | `a2cf1b322`: Python safety and actual tool/strict-ROM prerequisite preflight passed; real route run remains parent validation |
| Internal-terminal hook admission | Match established normalized terminal boundaries without exempting public changes | `0eff72951`: both-shell negative controls, 453 Python cases (one historical skip) and 49 Java passes |

The user confirmed that ROMs must not be placed on GitHub and accepted stronger
local preparation. Preserve hosted publication and manual ROM opt-in. The local
tool must bind its named subset to the expected candidate and reject missing or
skipped evidence; it must not describe the existing 26-case pilot as complete
release qualification or replace the other public-release gates.

The strict-mode worker reports 42 route-tool safety checks and 98 release-policy
safety checks passing without skips. Skip-classifier checks pass 19 cases and
skip `CheckedInPolicyTests.test_preserved_f56d4fae1_reports` because its historical
archive is absent. Actual Java 25/Lua 5.4/PowerShell preflight and committed-SHA
strict prerequisites with the original local S3&K ROM pass. No native route,
smoke or full suite was run for that worker; parent will exercise the strict
26-case pilot against the composed committed candidate as affected domain/tool
integration evidence, separately from broad smoke.

Session/global ownership and restore-order concerns are source-level maintenance
risks. They do not authorize an indiscriminate service-locator migration or new
restore framework. A change needs a concrete violated invariant and focused
before/after evidence. The previous composition-root cleanup tranche stays closed
unless an affected behavior supplies that evidence.

## Verification and integration

1. Workers run focused checks through the shared Maven queue, with original ROM
   identities and explicit absolute paths. They inspect exact failures and skips;
   graphics work uses the configured offscreen backend.
2. The parent reviews composed changes and inspects the change-based category plan
   against the pinned pre-task base above. Broad validation, if required by the
   actual combined changes, uses smoke, preflight, bounded batches and a stated
   class count/cost/stopping rule. No routine full suite is authorized.
3. Required affected trace/domain checks remain separate. Recorded-timing replay,
   live routing and native audiovisual evidence retain their different claims.
   Do not weaken comparisons or promote bounded route checks to campaign parity.
4. New or worsened failures block integration. Disputed failures use bounded,
   matched baseline/current checks rather than repeated broad runs.
5. Integrate into the existing main-workspace `develop` branch without switching
   it; preserve unrelated changes and reconcile upstream intent. Push only that
   branch after required verification. Remove task checkouts/branches only after
   all changes and generated files are accounted for.

## Checkpoint

All accepted slices are composed locally; main integration, combined validation
and push remain pending. Assurance command:
`maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl` with original three
absolute ROM properties, followed by
`-Dtest=TestNativePcmCapturePrerequisites,TestNativePcmCaptureLive,TestPlayablePlacementAudioOwnership,TestNativePlayerTerrainPlacement,TestStageplayGroundSurvey,TestAudioManagerStreamedPortOwnership,TestRawGlfwTestSetupGuard test -B`.
At `dfc7d9784`, all 32 cases passed without failures/errors/skips on Java
25.0.5.0.2/EGL, including all five PCM cases. The worker's pre-fix controls observed
the false skip and both missed private-port releases. Reports were consumed.
This is bounded Linux verification, not cross-platform graphics qualification.

The campaign worker's clean `f7620e579` canonical session 51690 finished at
11:12:16 UTC with one assertion failure and no errors/skips: segment 8 retains
four physics errors at row 7126, and the walk stops at input 68801/segment 11,
HCZ1/load generation 9. Alternate session 12519 retains its sole row 26179
`rings` difference, with no errors/skips. The parent reviewed the exact historical
`760b2005d` runtime/test change and native corroboration and authorized a narrow
adaptation to the present instance lifecycle owner. Independent pre-fix regression:
four cases, three failures; corrected four cases pass inside focused session 3120
(149 total cases). At `aa5fb2d34`, canonical/prefix session 52854 completes with
six cases, one inherited failure, zero errors/skips. All three strict prefixes and
the known-green AIZ/HCZ prerequisites pass. Segment 8's four row-7126 errors are
removed; completed segment 9 also has no mismatches. Segment 11 still stops at
input 68801, with 81,981 incomplete-comparison errors and first row 1768 `y`
`$0870/$0871`. Alternate session 4338 retains its sole trailing rings difference;
authority session 60839 passes all 43 cases. These are bounded frontier advances,
not complete campaign qualification. Exact commands and fingerprints are in the
October 25 [frontier entry](../../status/trace-frontier-log.md#2026-10-25--fresh-load-plc-order-qualifies-the-continuous-aiz-to-hcz-load-boundary).

The fixed-tail worker's 12-case native animation prerequisite passes without skips,
but the conservative hook treats a nested engine-internal snapshot component as
a public API change. The established normalized terminal remains unchanged.
Policy correction `0eff72951` uses only nested terminals established in both
compared Git snapshots. It retains identity, visibility, nesting and outer/sibling
declarations, rejects malformed registry inputs and prevents a new classification
from exempting its own edit. No registry or signature pin changed. Bash and
PowerShell old-red/new-green controls cover staged and committed comparisons.
The worker's Python safety run executes 453 cases with no failures and the same
one absent historical-archive skip; 49 queued Java hook/build checks pass without
failures/errors/skips. Both shells pass the committed-range policy check.
PowerShell was exercised on Linux, not Windows. The normalized candidate
signature check remains a separate requirement on the final animation source.

The independently executed normalized candidate check passed one physical case
before storage edits. The later fixed-tail-only candidate exposes the already
documented dynamic-parent storage boundary: supported raw deletion and Stageplay
retirement do not execute Obj7A's linked-target cleanup. Obj05 then reads 13 bytes
across eight fields at a cleared or reused parent slot, without an occupant guard.
A live-object resolver, class-default bytes or automatic cascade cannot reproduce
that contract. The parent rejected shipping the candidate's unknown-parent throw
and stopped expansion into generic SST storage. The worker retains the finite
disconfirming evidence in the existing rewind audit and restores the candidate
edits; the independent animation prerequisite can land after the hook correction.
The original Sideways allocation-pressure defect remains open. The final
prerequisite-only nine-class run passes 39 cases without failures/errors/skips;
the normalized candidate check passes one physical case on that compiled source.
Pure pose sampling follows the same native script-change boundary. The committed
controller/pose/test bytes match that tested source. Original Sideways production,
qualification and known-bug text are unchanged; no rejected storage code remains.

An independent Sol static review of `dfc7d9784`, `a2cf1b322` and `aa5fb2d34`
found no actionable issues. It ran no builds/tests and excluded the later
animation and hook changes; this review is not substitute execution evidence.
## Completed frozen-candidate validation

The composed frozen candidate is `4e5938d4104a032b027b3615d5dea1fba9b6d7da`.
Actual Java 25/Lua 5.4/PowerShell preflight passed. Its strict pilot completed all
26 required physical cases with zero failures, errors or skips (Maven 2:27,
queue 0 seconds). Canonical/prefix replay completed six cases, one inherited
failure, no errors/skips (Maven 2:04, queue 101 seconds); the alternate completed
one case with its one inherited failure, no errors/skips (38.247 seconds, queue
0). The exact frontiers in the linked October 25 entry remain unchanged:
segments 8 and 9 are clean; segment 11 is incomplete with 81,981 errors,
first row 1768 `y` `$0870/$0871`, stop input 68801/load generation 9. The
alternate retains only trailing row 26179 `rings` 72/0.

The change-based smoke inventory was 3,788 classes in 76 batches. Run
`20261025T115236Z-8d2376cf` hit its 90-minute execution limit after 69 complete
batches and partial batch 70: 37,773 cases, two failures, zero errors, 92 skips.
Preparation was 26.62 seconds and execution 5,373.58 seconds (90:00.20 together);
reported queue wait was 626 seconds and wall time 6,035.88 seconds. This timeout
remains incomplete as an individual invocation.

Continuation `20261025T134910Z-5b6db30b` used the existing `run_plan` API through a
temporary adapter to execute every one of the 338 pending candidates in original
batches 70–76, with unchanged order/exclusions. It completed 2,279 cases, one
failure, zero errors and five skips, then 800 guards with two new source-guard
failures and no errors/skips. Execution was 28:51.79, reported queue 17:25 and wall
46:18.01. Excluding superseded partial batch 70, all 76 ordinary batches account
for **39,916 cases, three failures, zero errors and 97 skips**. No failure/skip
records were omitted. Both run directories were acknowledged; the temporary
adapter and logs were deleted. Worker intervals overlap and must not be summed
as delivery elapsed time.

All three ordinary failures were matched independently on clean
`f7620e579c1a8cf7b0371f9c5a438f6a4eff5ea7` and frozen `4e5938d`:

- `TestModEngineWiringSeams#audioResetAndBackendReplacementReleaseStreamedSessionState`
  fails at line 92/helper 219 because its source-text assertion expects inline
  `beginSourceMutation()` in `resetState()`. Existing extraction `04a5a6c729`
  delegates to `sourcePublication.reset()`, which still begins that mutation.
- `TestHczMinibossNativeRendering#allOrbitPhasesMatchRomSpriteCompositionInNativePixels`
  fails at line 53, `GLFW must initialize for pixel verification`. Its raw GLFW
  setup does not consume the configured EGL context; no pixels were compared.
  The two-method matched arms each physically ran two cases, two failures, no
  errors/skips. Full exception types, assertions and stacks were byte-identical;
  baseline/current queue waits were 8/120 seconds and Maven times 81/89 seconds.
- `TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput`
  fails at line 182. Each matched exact-method `-Psmoke clean test` arm ran one
  case with one failure, no errors/skips; full XML type/message/stack matched.
  The earlier exit-4 and empty-stdout assertions passed. Unchanged `ProcessBuilder`
  inherits `JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`; a launcher
  notice before application stderr is the source-backed mechanism inference,
  not a retained raw-child-stderr observation. Baseline/current queue waits were
  37.2/409.1 seconds; execution leases were 77.0/81.9 seconds. No extra arm ran.

The first 92 skips cover opt-in benchmarks, pictures, captures, native GPU/soak
checks; legacy GLFW shader/data-select/foreground prerequisites; unavailable mod
JAR properties; unsupported Infinite Sonic platform/flyer acts; and the CPZ spin
tube capture prerequisite. The final five exact skips are:

- `TestS3kSlotsGlassNative#glassOccludesPlayerAfterRealBonusFrame`
  (`openggf.test.gl.native`).
- `TestSecondStrikeHczAirRecovery#zeroRingNativeBubbleRefillsReturnsAndRecreatesForWholeWorldReplay`
  (`secondstrike.hcz.air.mod`).
- `TestWestMgzBossNativeArena#packagedCreatorKnucklesHasAnOrdinaryDamageFreeSideResponse`
  (`secondstrike.mgz.packaged.arena`).
- `TestS1OpenGgfAudioCapture#capturesTheCompleteReferenceControlledInterval`
  (local deterministic BizHawk reference).
- `TestS1Ghz1OpenGgfAudioTimelineCapture#captureRequestedOutput`
  (no requested timeline capture).

These are not missing-ROM skips. Ordinary smoke remains red with matched
inherited failures and the stated coverage limits; it is not fully green.

## Upstream reconciliation and protocol-owner correction

Concurrent main advance `c502c600bb4df3a411438b1cd3814466aed9a83d` adds multiplayer
communication, preferences, the scene bridge and the unpublished candidate API
pin. Merge `fcc4d0d282b8f01c560e5a237b6d486231eb296a` preserves it and frozen
`4e5938d`. All 71 upstream files merged cleanly; the changelog was the only shared
changed path. Every disjoint input retained its original bytes. No unrelated
gameplay, audio algorithm, physics or renderer owner changed.

The combined change-based plan against `4e5938d` selects all 3,797 smoke classes
because configuration, racing and API paths are shared/unclassified. The parent's
proportionate replacement is all 20 Java test classes changed in
`824a16f21..c502c600`, plus TestModApiSignatureSurface, TestModApiHookPolicy,
TestSceneChatPreferences, TestModSceneHost, TestMultiplayerChatConfiguration,
TestEngineSettingsScreen, TestS3kFreshTitlePlcLoadBoundary and
TestPlayablePlacementAudioOwnership, deduplicated to exactly 25 classes. This
covers the new consumers, pin/hook coupling and audit load/disposal boundaries;
the upstream's own broader subsystem checks are recorded in its
[committed plan](2026-10-25-multiplayer-communication.md).

At `fcc4d0d28`, that queued selection passed **306 physical cases**, no
failures/errors/skips (queue 602 seconds, Maven 2:14). Exact XML suite identities
match all 25 classes. The physical method
`com.openggf.mods.TestModApiSignatureSurface#candidateZeroSevenSurfaceIsPinnedToTheCurrentSurface`
ran once and passed. Both Bash and PowerShell `ci-push 4e5938d..fcc4d0d28 develop`
passed, each auditing 14,733 entries; diff, AGENTS/CLAUDE and skill mirrors passed.
The separate fresh guards completed 800 cases with the same two new audit
source-guard failures as the frozen run, zero errors/skips. Diagnostics were
consumed. These failures are not inherited from the pre-audit base:

- `TestArchitecturalSourceGuard#releaseCriticalLargeClassesDoNotGrowWithoutExtraction`:
  `GameLoopTitleCardLifecycle.java` grew to 262 effective lines against 253.
- `TestArchitecturalSourceGuard#titleLifecycleOwnsPresentationBindingAndSharedReleaseWithNarrowLiveHost`:
  `enterPending(GameLoop, ...)` introduces a forbidden root backreference.

The accepted correction moves the first-title hardware scan/tail into the
existing stateless `GameLoopPlcLifecycle.enterPendingTitleCard` bridge. Both
GameLoop entries use synchronous entry/provider callbacks; the retained
presentation owner returns to 253 effective lines. No guard, exclusion, API pin,
fixture, comparison or timing authority changes. An initial draft captured the
level before callbacks; Sol review r2 rejected it because entry or hardware
preparation can rebind the current level. Two new parameterized controls were
old-red (two failures, no errors/skips; queue 74 seconds, Maven 85 seconds).
The accepted supplier resolves the live level again for the tail.

Parent job 36790 finished at 14:51:57 UTC on `fcc4d0d28` plus exactly four accepted
runtime/test files. Its exact focused selection was TestGameLoopTitleCardLifecycle
(2), TestGameLoopTitleCardOwnership (40, including both rebind controls),
TestGameLoopTitleCardPause (2), TestS3kFreshTitlePlcLoadBoundary (4) and
TestArchitecturalSourceGuard (87): **135 cases, zero failures/errors/skips**,
queue 386 seconds, Maven 91 seconds. Physical XML and Java 25/EGL properties were
inspected, then diagnostics deleted. This unchanged selection is not repeated.

## Final qualification and delivery checkpoint

The independent reconciliation checkout is `t3-f55c17c1`, branch
`feature/ai-audit-upstream-reconciliation-20261025`. The original parent checkout
at `.worktrees/ai-audit-remediation-20261025` remains frozen at `4e5938d`. Main
`develop` remains `c502c600`, fetched/fast-forward checked at 14:40 UTC; its dirty
reference submodules and unrelated user files are preserved. The parent owns
registry updates, main integration, push and eventual checkout cleanup.

After committing the accepted owner correction and this evidence, freeze source
for exactly four final checks: the strict 26-case pilot with its full committed
expected SHA, the six-case canonical/prefix selection above, the one-case
alternate, then a fresh complete `-Pguards` JVM. Use Java 25, Lua 5.4, verified
absolute S1/S2/S3K ROM paths and explicit CLI EGL; unset DISPLAY, WAYLAND_DISPLAY,
JDK_JAVA_OPTIONS, JAVA_TOOL_OPTIONS and _JAVA_OPTIONS, with ALSOFT_DRIVERS=null.
Keep each queued request alive and target ownership sequential. Inspect physical
cases, skips and complete frontier reports before consuming diagnostics. Do not
repeat ordinary smoke or the 306/135-case focused selections. The protocol-owner
change justifies these affected route/trace/guard checks; it does not authorize
other runtime repairs. Update this checkpoint after those checks, recording the
tested commit and remaining HCZ/Sideways gaps. A later prose-only commit does not
require repeating unchanged executable checks. Integration and push stay pending.
