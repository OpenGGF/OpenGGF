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

The accepted owner correction is committed and tested at full SHA
`53b4d0363a39324a14e9fc00c9f59055e1b3f909`. Exactly four final invocations completed
sequentially on that clean frozen source; no ordinary smoke or 306/135-case
selection was repeated. Java 25.0.5.0.2, Lua 5.4, original verified absolute
S1/S2/S3K ROM paths and CLI EGL were used. DISPLAY, WAYLAND_DISPLAY,
JDK_JAVA_OPTIONS, JAVA_TOOL_OPTIONS and _JAVA_OPTIONS were unset;
ALSOFT_DRIVERS=null. The portable exact command block is in the same
[frontier entry](../../status/trace-frontier-log.md#protocol-owner-reconciliation-and-qualification-october-25).

- Strict `check_route_obligations.py --run --expected-candidate
  53b4d0363a39324a14e9fc00c9f59055e1b3f909 --rom s3k=<original absolute path>`:
  **26 exact physical cases, zero failures/errors/skips**, tool exit 0. The tool
  owned the clean queued rebuild, full candidate/ROM/source checks and fresh XML
  accounting. Queue 4 seconds, Maven 2:33, command wall 164.593 seconds; tool
  finished 15:00:32 UTC (Maven finished 15:00:28 UTC).
- Canonical `-Ptrace-replay -Dsurefire.forkCount=1
  -Dsurefire.runOrder=alphabetical` with the exact four-selector/six-case command
  in that entry: **6 cases, 1 known chain failure, zero errors/skips**, Maven
  exit 1. All three prefixes and standalone AIZ/HCZ pass, at 20,463/3,519
  compared frames. Physical fixture rows are 7,175 for segment 8 and 3,574 for
  segment 9, including 62/55 lag rows; both complete with zero physics,
  animation, bootstrap errors or warnings. Segment 11 remains incomplete:
  81,981 errors (69,334 physics, 12,647 animation), first row 1768 `y`
  `$0870/$0871`, last mismatching row 5725; actual stop is input 68801,
  HCZ1/load generation 9. The full failure stack also retains the incomplete
  run's suppressed unmatched-hardware-completion diagnostics; it is not a
  completed run or a clean hardware ledger. Queue 1 second, Maven 2:01,
  command wall 123.368 seconds; finished 15:03:26 UTC.
- Alternate `-Ptrace-segments
  -Dtest=TestS3kAizZoneSliceTraceReplay#replayMatchesTrace test -B`: **1 case,
  1 known failure, zero errors/skips**, Maven exit 1. All 26,041 compared frames
  retain exactly one non-cascading physics span: row 26179 `rings` 72/0.
  Animation/bootstrap/warnings are zero. Queue 2 seconds, Maven 40.211 seconds,
  command wall 42.971 seconds; finished 15:05:10 UTC.
- Fresh complete `-Pguards test -B`: **800 physical cases across 95 report
  suites, zero failures/errors/skips**, Maven exit 0. Both formerly failing
  title ownership/size methods physically executed and passed. Queue 49 seconds,
  Maven 5:29, command wall 378.912 seconds; finished 15:11:47 UTC.

The pilot consumed its own temporary XML; all other physical XML, full trace
frontiers and skips were inspected before deleting consumed reports/logs. Fork
properties confirm Java 25 and EGL. Standalone trace reports still advertise
missing auxiliary schemas; no full auxiliary, native rendering, audio, other
viewport/donor/team or whole-campaign qualification follows. No trace payload,
guard, exclusion or API pin changed. HCZ's returned-segment control/solid frontier,
the alternate trailing rings publication and S2 Sideways linked raw parent-slot
storage/allocation-pressure defect remain open. Ordinary smoke retains its three
matched inherited failures and 97 skip limits above.

Only this plan/frontier result prose changes after the tested commit; runtime,
tests and fixtures must remain byte-identical to `53b4d0363`. Do not rerun
unchanged executable checks for that prose. The parent reviews the final local
commits and owns main integration, push and eventual registry/worktree cleanup;
those delivery steps remain pending. No task-owned test job remains live.

## Parent integration

After reviewing the final commits and refreshing `origin/develop`, the parent
fast-forwarded the unchanged main-workspace `develop` branch from `c502c600` to
`b8d12399ea75b29dc5b75fdd964cb5ff5cb082fe`. No branch switch or conflict resolution
was needed. The only committed differences from tested `53b4d0363` are this plan
and the frontier evidence prose; executable inputs remain identical. The four
pre-existing dirty reference submodules and five untracked user files retain
their original status. Required execution evidence is reused rather than rerun
for integration alone. Push and task-checkout retirement are recorded in the
parent's delivery result and temporary-agent lifecycle record.

## Alternate AIZ trailing-ring investigation (October 25, source d3fa3d519)

Investigation only: the engine publishes the destination ring bank too early in
`FreshLevelTransitionBoundaryController.publishInitial()`. No production repair
is included. The isolated checkout was clean at the requested full SHA
`d3fa3d519f6acb7285d538b5f972807036f1019e`, branch
`bugfix/ai-aiz-trailing-rings-investigation-20261025`, worktree `t3-70b923f5`.
Hooks were installed; main and other checkouts were not changed. Historical
qualification at `53b4d0363`/`94c75d8a8` was not reused as current execution.
Historical introducer `71b357e7ae0586b28b9077a86c4f263a76841f46` deliberately
paired destination rings with player publication; `ec1d6b8dc` subsequently
identified the remaining alternate ring-reset staging frontier. Both were read
before any diagnostic adaptation.

The original `${ROM_ROOT}/{s1,s2,s3k}.gen` files were
verified against the repository CRC32/SHA-1 identities. In particular S3&K was
CRC32 `63522553`, SHA-1 `cfbf98c36c776677290a872547ac47c53d2761d6`.
The immutable input SHA-256 identities were:

- `traces/s3k/aiz_completerun/metadata.json`:
  `93f47c154d0bc83cf38a2a3c4b987a13a7796dd92cfae1508b58b61c0d1d8de4`.
- `traces/s3k/aiz_completerun/physics.csv.gz`:
  `1b60c8120b60aaf815e42123a58307e1b088201da6d6767c9703e40d16a51ce8`.
- `traces/s3k/aiz_completerun/aux_state.jsonl.gz`:
  `e580a0d5cf616cbc8ffbe0f46d565cf9a9ff2425a4ee5156da1f6af5ac6829bb`.
- `traces/s3k/_movies/s3k-complete-sonic-tails.bk2`:
  `82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf`.

These paths are relative to `src/test/resources/`. Disassembly pin was
`8b2cf3c4178f2382f844da67798de4befc50c5e0`; TraceChaser pin was
`322851cbfa2e1c1803332142e11ab74b4eb34eea`.

### Reproduction, clocks and native control

The focused command, from the owned checkout, was the following, with
`ROM_ROOT` set to the original absolute main-checkout ROM directory (no copies
or links). `BIZHAWK_HOME` below denotes that main checkout's existing
`docs/BizHawk-2.11-linux-x64` installation. Machine-specific home paths are
expressed as variables to obey repository prose policy:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JDK_JAVA_OPTIONS -u JAVA_TOOL_OPTIONS -u _JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
  LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic1.rom.path=${ROM_ROOT}/s1.gen \
  -Dsonic2.rom.path=${ROM_ROOT}/s2.gen \
  -Ds3k.rom.path=${ROM_ROOT}/s3k.gen \
  -Ptrace-segments '-Dtest=TestS3kAizZoneSliceTraceReplay#replayMatchesTrace' test -B
```

The unchanged baseline finished 19:21:37 UTC: Maven exit 1, **one physical case,
one failure, zero errors/skips**, 26,041 compared frames, exactly one
non-cascading physics span at row 26179 `rings` 72/0. Animation, bootstrap and
warnings were zero. Physical XML identifies precisely `replayMatchesTrace`;
fork properties confirm Java `25.0.5.0.2` and EGL. Maven time was 1:32, queue
wait 0, execution lease 93.5 seconds. The same command with
`OGGF_RINGS_PROBE=1` and passive prints in the replay loop, boundary controller
and `LevelManager.initPlayerAndCheckpoint()` finished 19:26:44 UTC with exactly
the same counts/frontier: Maven 1:34, queue 28.8 seconds, lease 95.3 seconds,
physical case duration 12.548 seconds (baseline 12.308). No comparator,
fixture, tolerance or gameplay value was changed. Prints were removed afterward.

`TraceFrame.parseCsvRow`/`parseV5Row` parse frame/rings/counters as hexadecimal. Row 26179 is
CSV frame `$6643`, **the last compared state-change row**, not the physical tail.
There are 26,228 physical rows, ending at index 26227/frame `$6673`.
`TraceExecutionModel.deriveSonic3kPhase()` classifies row 26179 as
`FULL_LEVEL_FRAME` because position changes with unchanged LF/lag; later rows
are `VBLANK_ONLY`, including the final reset. `stateEquals()` excludes rings,
and `shouldCompareGameplayStateForReplay()` omits VBlank-only rows. S3K's
comparison helper retains the current row's strict rings. The one-row report
therefore does not establish that the disagreement lasts only one hardware frame.

The producer observes completed hardware frame `941 + 1 + row`; its input is
BK2 index `941 + row` (TraceChaser `S3KCompleteRunSegmenter`/capture-loop
contract). Thus row 26179 is completed native frame 27121/input index 27120;
row 26227 is completed frame 27169/input 27168. The engine's previous-input
selection for the recorded pre-level prefix is separate from these labels.
The passive print's input index was calculated from that contract, not a direct
read of the private driver cursor. At row 26179 the observed recording-driver
execution count changes 26109→26110, while SpriteManager LF and ObjectManager
execution count remain zero. These are not native V-int or movie clocks.

The independent temporary C# observer used the pinned TraceChaser
`GpgxHost.Open`, original BK2 `SyncSettings`/sequential button rows, `Advance()`
and read-only execute callbacks. It built through
`BIZHAWK_HOME="$BIZHAWK_HOME" tools/tracechaser/bizhawk-headless/build.sh`.
The installed runtime's managed 2.11 identity/preflight was verified. Its Mono
launch used that existing runtime's `dll` for `MONO_PATH`/`LD_LIBRARY_PATH`, an
owned `target/native-bizhawk-data` for `BIZHAWK_DATA_HOME`, no display or live
audio, and `maven_queue.maven_slot(..., kind='native-rings-observer', estimate=60)`
with the unchanged default 7 GiB/8-core reservation. No native or engine RAM was
written. This was observation, not trace production/publication or pixel evidence.

The first native launch omitted `BIZHAWK_HOME` in its child environment and
failed closed in `BizHawkInstallation.Validate` before opening the ROM: exit 1,
no advanced frames (queue 64.2 seconds). After repairing that prerequisite, one
launch completed at 19:30:12 UTC with exit 0 and `COMPLETE frames=27172`, queue
55.8 seconds, execution lease 80.6 seconds. Its temporary wrapper called
`sys.exit(0)` inside the lease context, so queue telemetry labels the context
`interrupted`; the native child's exit/complete marker and observations establish
its completed scope, not that telemetry label.

All **150 overlapping frame-end rows 26078–26227**, six field groups per row
(rings, LF, V-int low word, lag count, player XY, camera XY), matched the committed
fixture exactly: 900 group comparisons, zero differences. Native callbacks prove:

- During advancement to 27121/row 26179, `loc_6310` begins with rings 72 and
  zeroed player positions. `Get_LevelSizeStart` publishes `$0280,$0020` and
  `$01E0,$0000` camera, then `loc_7870` waits with three terrain modules and
  rings still 72. Frame-end PC `$001D1C` is in `Wait_VSync`.
- Terrain modules are zero by row 26208, but rings remain 72. Subsequent samples
  execute synchronous CPU setup/decompression, including lag VBlanks and later
  PLC submission. Queue-empty is demonstrably not the counter reset edge.
- During advancement to 27169/physical row 26227, PC `$63BC` immediately before
  `move.w d0,(Ring_count).w` observes 72; PC `$63C0` immediately afterward
  observes 0. Both have V-int `$69F9`, LF 0, `Last_star_post_hit=0` and module
  count 0. The original ROM bytes at `$63B4` are
  `7000 4A38 FE2A 6648 31C0 FE20 21C0 FE22`; the write's address/operand and
  zero-starpost branch were checked against the original ROM.

Execute callbacks expose the *previous* `CompletedFrame` while the next frame
is advancing. The probe also recorded its independently assigned `advancingTo`;
the reset belongs to row 26227, not the callback's previous completed-row label
26226. The before/after instruction observations are the disconfirming control
against a recorder-only or HUD-only explanation. `Ring_count` is the actual
`$FFFE20` global bank, not an engine display latch. Native fresh title setup has
not yet spawned ordinary player code at the early position publication; these
observations do not demonstrate damage or a loss during playable gameplay.
`HUD_DrawInitial` separately writes literal zero digits, so displayed digits
cannot stand in for the retained bank.

### Production owner map and finite corrective boundary

The native order is explicit in `loc_6310 (sonic3k.asm:7788-7801)`:
`Get_LevelSizeStart`, `DeformBgLayer`, blocking `LoadLevelLoadBlock`, synchronous
`LoadLevelLoadBlock2`, then `j_LevelSetup`. Only afterward does
`loc_63B4 (sonic3k.asm:7826-7835)` clear the no-starpost counters, before
`loc_6468 (sonic3k.asm:7880-7887)` calls `SpawnLevelMainSprites` and the initial
object pass. `loc_7870 (sonic3k.asm:9772-9780)` is the terrain module wait;
`LoadLevelLoadBlock2 (sonic3k.asm:38714-38782)` decodes block/chunk streams and
loads layout/PLC/palette; `j_LevelSetup (sonic3k.asm:38706-38708)` calls
`LevelSetup`, whose implementation starts at line 102231.

Current engine owners represent the data and some boundaries, but **no equivalent
post-module synchronous CPU-setup/reset completion edge was found**:

- Data for `LoadLevelLoadBlock2` is already decoded by
  `Sonic3k.buildLevel()`/`Sonic3kLevel` construction: resource-plan chunk/block
  operations, `loadChunksWithCollision`, `loadBlocksWithPlan`, `loadMap`, palette
  and boundary loads. Prepared builds may do this on the resource worker and
  `installPreparedLevel()` publishes it. This host decoding is not a native
  CPU-service completion callback after the terrain wait.
- The late `LoadLevelLoadBlock` **art** producer is
  `Sonic3kTitleCardManager.publishFreshLevelRuntimeArtHandoffIfNeeded()` →
  `Sonic3k.queueFreshLevelRuntimeArt()` → `S3kRuntimeArtCoordinator`'s module
  queue. Its PRE_MAIN_LOOP/other hardware service callbacks retire prepared
  art jobs. `freshLevelArtWaitsForModuleQueue()` reads physical module occupancy;
  it does not execute or account for `LoadLevelLoadBlock2`/`j_LevelSetup`.
- Setup effects are distributed through `Sonic3kLevelInitProfile.levelLoadSteps`,
  `LevelManager.initLevelEventsForLevel()`/`Sonic3kLevelEventManager`, camera and
  zone setup. `prepareInitialLevelScroll()` models the initial deformation
  through `InitialProcessSpritesExecutor`; it is not a synchronous CPU setup
  completion signal. S3K inherits `LevelInitProfile.completeInitialPresentationPlcs()`'s
  no-op, so that existing hook cannot authenticate this missing edge either.
- `LevelManager.initPlayerAndCheckpoint()` replaces `LevelGamestate` during
  load. `resetPlayerForLevelStart()` selects fresh/checkpoint rings; the boundary
  saves that destination count and restores source 72 at row 26107. This
  replacement is not the lasting loss. `publishInitial()` later calls
  `publishDestinationRings()` at row 26179, prematurely writing saved zero.
  `complete()` skips that write when `initialPublished` is true.
- `spawnPlayerAtStartPosition`, `spawnSidekicks` and zone-player setup prepare
  the destination players during the load. The boundary captures/holds them;
  `publishInitial()` exposes the initial position; `complete()` restores their
  full state and dispatches the retained `InitialProcessSpritesLifecycle` via
  `consumePendingInitialProcessSpritesPass()`/`InitialProcessSpritesExecutor`.
  Live `GameLoopTitleCardLifecycle` and `RecordingFrameDriver` both call the
  shared publication owner. Their publication/completion predicates currently
  follow terrain-start/module occupancy. Neither is the missing post-CPU edge.

A repair must separate start-position publication from the destination-counter
publication at the **native post-terrain CPU-setup boundary**, before the initial
spawn/dispatch. The finite next scope is to represent/authenticate that existing
native setup progression in its S3K production load owner, then route an
exactly-once counter-publication operation through the shared transition boundary.
The destination value remains the profile/policy-selected bank, including
nonzero/checkpoint/return semantics; do not hardcode zero. Its independent
publication phase must be captured/restored alongside the existing initial-player
phase. Preserve S1/S2, omitted presentation and no-title continuation consumers.

**Rejected shortcut:** merely remove the initial write and put it in `complete()`.
No `complete()` call occurred through physical row 26227 in the passive replay;
that change could make the existing comparator green while silently omitting the
native tail reset. Queue-empty alone would also reset early (row 26208 here).
A fixture row, lag count or fitted elapsed delay supplies no production authority.
There is no reviewed implementation for the missing CPU edge in this investigation.

Required negative controls for subsequent work: a non-72 source bank must survive
start-position publication and held terrain/setup; the actual production bank must
then receive its selected destination value at the reset edge **including physical
VBLANK_ONLY tail observations**, with no repeated publication after a later write.
Exercise a selected nonzero destination/checkpoint bank, repeat publication and
completion, restore independently before/after player and counter publication,
and replay the initial object dispatch exactly once. Preserve S1/S2 and no-title
continuation behavior. `TestFreshLevelBoundaryRewindHeadless` currently asserts
zero at early publication and no reset at completion; those expectations came
from the mistaken pairing and need source-backed revision, not fixture tolerance.
The current implementation is the early-reset negative; a candidate that simply
never resets must fail the tail control. This does not attribute HCZ's separate returned-hand or drowning frontiers to
the reset defect. HCZ's lead owns any combined campaign qualification; no canonical, broad smoke, CI, push or PR work was run here.

All passive Java edits were restored byte-for-byte to the starting commit.
Temporary native probe/build and consumed XML/trace diagnostics are removed after
inspection. Only this evidence prose is retained; parent owns review, integration
and worktree retirement. The causal defect is established; the source-backed
post-CPU progression representation remains the explicit implementation gap.

## Native-frontier investigations and Sideways council disposition

The follow-up began at `d3fa3d519f6acb7285d538b5f972807036f1019e` and
requested two Sol investigations plus an Astra/Fable/Sol/Opus architecture
council. HCZ's Sol lead used two read-only Sol helpers; the alternate AIZ
investigator worked alone. The four architecture candidates were independent,
followed by a fresh Opus cross-review and parent synthesis. Architecture work
ran no builds, tests or native probes. This delivery contains documentation only.

The two investigative commits are `4576d1df1904623d1cfee50a9bed516cbef75a99`
(HCZ) and `dc839afe1e4fd5c692af755894824535000db597` (alternate rings).
Their measured inputs remain the pinned runtime, not this later documentation
composition. Source and test diagnostics were consumed; the recorded commands,
identities, failures and causal limits are preserved in their existing artifacts.

| Work | Finding and next boundary | Priority and complexity |
| --- | --- | --- |
| Returned HCZ continuity | The hand launcher omits its post-motion solid check; the eventual ownership stop is a real drowning/death restart. Repair and qualify the narrow launcher phase before attributing downstream divergences. | Highest campaign urgency. The first repair is bounded; whole-HCZ closure remains an uncertain larger investigation. |
| Alternate AIZ rings | Position publication clears the actual bank too early. Native reset follows additional CPU setup for which no equivalent production edge was found. | Required for strict transition fidelity. Shared timing/lifecycle work makes it more substantial than moving one assignment. No playable damage loss was demonstrated. |
| S2 Sideways | Both failed-allocation aliases and surviving admitted links need physical coordinate semantics across clear/reuse. | Medium overall, high conditional impact; natural occurrence is unmeasured. High architecture/verification complexity. Establish the complete representation contract before implementation. |

The [HCZ evidence](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#returned-hcz-landing-and-death-reload-attribution--2026-10-25)
separates the first landing cause from later drowning. Historical foreign repair
`5096be6cc1b7b8bbc9bcc9bf0646803ab7e9f5b5` was inspected, not integrated.
No result proves the launcher alone explains the death or certifies the outgoing
HCZ campaign boundary. The alternate reset remains independently attributed.

The [Sideways synthesis](../designs/2026-10-25-s2-sideways-storage.md)
selects physical coordinates and one manager-mounted fixed-cell authority with
complete occupant contracts. It retains explicit world lineage/capabilities,
named clear/carry operations, independent fixed dispatch/DPLC, and a single
composed restore authority. It rejects the initial candidates' unknown-occupant
throws, opaque creator state, animation caps, nullable ignored restores and
blanket clear-on-manager-reset rules. A wholesale canonical-field migration is
not the default; it remains a reconsideration if exact single-owner live fields
cannot express the demonstrated native domain.

No candidate was implementation-ready. The finite prerequisite is an inventory
of actual native writers/aliases and supported admissions, ROM consumer bounds,
clear/install/carry callers, snapshot writers, creator/API migration and admitted
single-member retirement. The thirteen-byte tail read is not the entire scope:
the child's `$36` cross-write and physical endpoint deletion also matter.
Two-player behavior and unusual mapping/DMA presentation remain unqualified;
they cannot be silently excluded to declare the supported repair complete.

The recommended implementation order is the narrow HCZ launcher correction,
the independently scoped load/reset representation, and Sideways after its
representation gate. This order does not authorize importing broader historical
compositions or weakening trace comparisons. This investigation/design round
lands no gameplay fix and changes no fixture, comparison, API or release gate.


## HCZ continuity implementation handoff — 2026-10-25

The isolated implementation on exact base`61be1910` corrects the two observed
HCZ object owners: post-motion launcher solid/retained standing, then Chopper
P1 lock/masked live logical publication. The [implementation evidence](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#hcz-object-phase-repairs-and-remaining-continuity-gate--2026-10-25)
and [HCZ1 matrix](../validation/levels/s3k-hcz1-sonic.md#launcher-and-chopper-continuity-repairs--2026-10-25)
record87 focused passes, five clean replay companions and the still-red chain.
No shared loading/timing/physics or AIZ reset owner was changed.

The old drowning stop68801 is passed; final ownership loss is70718 in HCZ2 after
an ordinary P1 death. Earliest remaining causal motion difference is controlled
Tails drowning3476: object control`81` bypasses the shared engine's pre-death
movement/timer, while native primary routine`0C` bypasses ordinary control and
moves before adding gravity. A routine-byte-only reporting correction or HCZ
column release would conceal the gameplay defect. The finite next scope is shared
primary-routine dispatch, CPU gating, fixed countdown/death ownership and rewind,
with S1/S2 negatives. First later P1 velocity difference5065 is a missed miniboss
hit; causation is not established before the earlier player frontier is repaired.
Sideways and the independent alternate-AIZ representation work remain unchanged.
Parent retains review, combined smoke, integration/push and checkout cleanup.


## Authorized primary-drowning continuation — 2026-10-25

The parent extended the HCZ scope to primary drowning dispatch and its necessary
fixed timer/CPU/history/projection owners. Those corrections now pass 178 focused
cases with no skips and qualify native P2 entry, sinking, death and despawn in the
continuous source. Canonical six remains 6/1F/0E/0S; segment 11 first comparison
is rings 3832 (104/94), with the first P1 motion still the miniboss contact at
5065. The source is HCZ1's second stint; the premature replacement destination
at 70718 is HCZ2. Rings and setup timing remain excluded. The native boss
`StartFight` code-pointer handoff versus next-pass loop initialization is the
bounded next owner, with a separate old-red control/commit. See the appended
existing audit; no general player architecture or hardware-timing campaign is
implied. Parent retains combined smoke and delivery ownership.
