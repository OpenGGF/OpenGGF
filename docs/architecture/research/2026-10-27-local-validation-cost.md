# Local validation cost reduction — 2026-10-27

Base: `develop` at `9a796ec60f8437fab1f2fffb755174a73c7f7659`, fetched and
fast-forward checked before work. Owned tree: `.worktrees/ai-local-test-cost`,
`feature/ai-local-test-cost`. The maintainer requested high-cost, low-value tests
become cheaper, discriminating contracts or move to an exceptionally manual lane.

## Evidence and scope

The current all-category plan contains 4,236 candidate classes, not executed
cases. A read-only snapshot of existing run `20261027T110056Z-a6be8f4a` in the
`t3-b59ba251` tree (head `0bf1f2feed11f4139a81fbd15fbfb2c55d8b7db4`, base
`9a796ec60`) had completed 78/85 ordinary batches, reporting 6,200.38 execution
seconds, 41,776 tests, 33 failures and 76 skips. This was an incomplete foreign
run, not this task's certification. Its 70th batch held admission for 1,258.7
seconds; its command timestamp matches that telemetry start within one second.
That batch includes the entry/reload and bonus products changed here. No original
per-class timing remains for the completed batch, so it cannot attribute those
seconds to individual tests. Foreign runs and diagnostics were left untouched.

The rolling queue snapshot held 1,301 jobs since 26 October 09:46 UTC. Its 677
category batches had median/p95/max holds of 12/382/1,266 seconds. Focused-job
p95 waiting was 734 seconds. These heterogeneous observations have no test
outcomes/identities and are not a controlled benchmark or an elapsed-delivery sum.

Source inspection found eight acts inheriting 140 normal-entry plus 140 actual
restart rows each. Bonus tests add 420 requested-return products, 420 real-portal
products and 140 Slot live-history products, alongside 5/5/3 existing pilots.
The repeated products have real assertions, but exhaustive higher-order
configuration combinations have low incremental value on every unrelated local
change. The October [value/cost audit](../audits/testing/2026-10-23-test-value-cost.md)
also identified the full-source deleted-codec wording corpus and whole-tree
clock-parameter terminology attribution as costly source-policy work.

## Changes and tradeoffs

- Routine canonical entry and death/reload tests run a deterministic greedy
  covering set of supported pairs across donor, main, production viewport and
  roster shape (solo, one follower, mixed, duplicate, more than two followers).
  Every selected row comes from the original 140 products. The ROM-free selector
  test checks all six feasible axis-pair sets, all roster boundaries, determinism,
  uniqueness and a 40-row upper bound. Original boot, death, ownership, life-loss,
  world replacement and twice-forward complete-world assertions are shared intact.
- Full canonical products and all 980 bonus Cartesian products carry
  `exhaustive-audit`; the original bonus pilots remain routine, including the
  three actual-portal Slot live-history controls. This reduces routine breadth;
  it does not establish three/four-axis or whole-act certification.
- The deleted-helper wording scan and the whole-tree clock-parameter naming
  check use the same manual tag. Inspection confirmed the clock class has only
  the whole-tree naming test, not separate synthetic JUnit controls; no such
  control coverage is claimed. Gameplay clock behavior and authority guards
  are unchanged.
- `TestBuildToolingGuard#releaseGateToolsShouldRejectCorruptAndChangedEvidence`
  took 256.922 reported seconds in the affected run. It launches *every* Python
  release-tool suite and the skip-classifier suite inside a single JUnit case.
  Its wrapper is now manual audit coverage; actual Python assertions remain
  directly runnable for tool changes and explicitly selected by the existing
  release ROM-validation workflow. Small release-wiring guards stay routine.
- The explicit `exhaustive-audit` Maven profile selects only that tag and
  restores guard filename discovery. Default, smoke, guards and release
  full-suite exclude it. It has no activation or CI/release invocation, and is
  not an ordinary integration gate. Full-suite still selects the independent
  slow oracles; exceptional configuration/source-policy audits are separate.
- The two Python safety-suite JVM controls use the existing 4 GiB/4-core
  small-job lane for their fixed 128 MiB synthetic children. Compilation still
  owns its build/activity leases and releases them before test admission;
  global limits and admission rules are unchanged. The initial Python check
  was terminated with exit 143 after waiting over 14 minutes for the former
  normal reservation; that incomplete invocation is not a pass.
- The category runner prints three slowest classes per batch and retains top-20
  class/suite and case observations per lane before deleting XML. Timing evidence
  stays inside existing temporary results and is removed by acknowledgment;
  no pass cache, extra archive or timing-history store is introduced.

Raising forks/heaps/admission limits was rejected: it leaves unnecessary work
in place and changes resource/isolation policy. Moving entire lifecycle classes
was rejected because it removes useful behavior boundaries; only full products
move and representative checks keep the same asserting helper. Deleting old
products was rejected because a targeted investigation can still request them.
Re-running a full suite simply to change its selection was rejected: the bounded
profile/discovery and affected-consumer checks below cover the change directly.
No runtime algorithm, ROM input, tolerance or trace frontier changes.

## Validation

Compilation with queued build-only `-Dmse=off test-compile -B` passed on Java 25
in Maven-reported 1:23. Tool preflight with `LUA_BIN=/usr/bin/lua5.4` passed Java
25, Lua 5.4 and PowerShell. These are prerequisite/build observations, not gameplay
coverage. The POM/test/runner diff plus untracked source inputs has fingerprint
`d0dd5011a8cb29e50999ccf8c841e9c15b5dfe314cafd1490acb6b08a0ea3da4`
(SHA-256 of binary diff, then sorted untracked paths/content, excluding prose
outside those trees). Actual resolved effective POMs for default, smoke, guards
and full-suite exclude `exhaustive-audit`; its own resolved profile selects only
that group with no inherited filename exclusions. Temporary effective POM files
were inspected and deleted. Further results are recorded here before integration.


The affected invocation ran 600 cases in Maven-reported 6:08 after 618 seconds
waiting for admission. All 476 engine/ROM cases and the independent selector
passed, with zero skips. Each of eight act matrices ran 25 normal-entry and 25
actual-reload rows (CNZ1 adds three unchanged local controls); the bonus classes
ran their existing 8/5 pilots. The prior 140-row products plus bonus products
therefore remove **2,820 repeated scenarios** from routine coverage. This is a
case-selection reduction, not a measured entire-suite wall-time speedup.

The first guard class ran 123 tests with three failures. Adding the new profile's
explicit shared run-order setting repaired `ordinarySurefireShouldUseSharedAlphabeticalRunOrder`;
replacing the TraceChaser test's exact entire exclusion-string assertion with a
semantic profile/group assertion repaired `traceChaserStaysExactOptionalAndOutsideOrdinaryBuilds`.
The unrelated `s3kStaticSessionStateDebtShouldNotGrow` failure was matched on the
unchanged main base using the exact method: both report the same additions,
`HCZWaterSkimHandler.slotRetired` and `Aiz2BossEndSequenceState.cutsceneKnucklesSlot`.
The matched baseline ran 1 test / 1 failure / 0 errors / 0 skips in 0.545 reported
JUnit seconds (Maven 47.218 seconds including preparation), admission wait 0.
No runtime debt list or gameplay source was changed to mask it.

Python runner/scheduler safety verification passed **285 tests** with
`python3 -m unittest discover -s tools/testing -p 'test_run_categor*.py'`.
Its elapsed 579.317 seconds includes the second JVM control's 544-second queue
wait; the first control acquired immediately. The synthetic fixtures intentionally
exercise internal skip handling; no Python test was skipped. The original
four new timing-summary checks failed before implementation with missing timing
fields, then passed. The safety suite also checks merged timing evidence survives
raw XML cleanup, with ordinary and guard rankings separate.


A proposed whole-guard retry was cancelled while still queued (813 seconds,
zero test execution): the first invocation already covered unchanged guards,
and repeating them was disproportionate. Final revalidation selects only
`routineTestsMustDefaultToSmokeAndFullSuiteMustBeReleaseOnly`,
`ordinarySurefireShouldUseSharedAlphabeticalRunOrder`, and
`traceChaserStaysExactOptionalAndOutsideOrdinaryBuilds` with an exact method
selector and the existing explicit lean lane. The routine-profile guard also
checks the release-tool wrapper's actual `@Tag`, preventing its accidental return
to routine coverage. The final POM/test/runner input fingerprint before staging is
`9b3696ffb88f43a77a2aab3b69fb76418d9796fea5919d3c1a89f049db302ca5` by the same procedure above.


Final focused guard verification passed **3 tests / 0 failures / 0 errors /
0 skips**, 0.359 reported JUnit seconds, Maven 49.753 seconds, after 383 seconds
queue waiting. Exact command (with `LUA_BIN=/usr/bin/lua5.4`):

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestBuildToolingGuard#routineTestsMustDefaultToSmokeAndFullSuiteMustBeReleaseOnly+ordinarySurefireShouldUseSharedAlphabeticalRunOrder+traceChaserStaysExactOptionalAndOutsideOrdinaryBuilds' \
  test -B
```

The affected engine command used `maven_queue.py --normal -Dmse=off
-Dopenggf.headless.gl=surfaceless-egl surefire:test -B`, exact `-Dtest` selectors
for the eight `TestS3k*EntryMatrix` consumers, the two bonus product classes,
`TestCanonicalEntryMatrixSelection`, `TestBuildToolingGuard`, the manual clock
class (correctly filtered out), and the four mandatory S3K selectors. All three
original absolute REV01/locked-on ROM paths were SHA-1 verified with the existing
`rom_args` helper; donor ROMs remained original files. The simple-name
`TestSonic3kLevelLoading` selector also selects its existing seven-case facade.

The change-based plan selects all 4,237 candidates plus guards because POM/test
infrastructure changed. That broad engine run is disproportionate here: no
runtime or fixture changed, all eight affected matrix consumers and the retained
bonus pilots executed, supported-pair selection has an independent ROM-free
contract, effective POMs establish the selection boundaries, the affected profile
guards pass, and runner safety/preflight pass. The failed full guard's two
regressions were repaired narrowly; its remaining static-state debt is matched
to the baseline and remains reported, not greenwashed. No release full suite,
whole trace sweep or entire-smoke timing claim is made. Real Surefire XML was
also parsed through the new bounded timing summarizer successfully. Diagnostics
are consumed locally and removed; no pass cache or archive is retained.

## Complete matched local timing pair

The maintainer subsequently requested actual before/after runtime figures.
Both complete **smoke plus guards** selections finished on 27 October 2026.
Test execution fell from **2h 14m 19.03s to 1h 46m 6.08s**:
**1,692.95 seconds / 28m 12.95s / 21.01% less execution**.
The remaining broad local selection still takes about 106 execution minutes.

| Snapshot | Pinned commit | Measurement tree | Run |
| --- | --- | --- | --- |
| Before | `9a796ec60f8437fab1f2fffb755174a73c7f7659` | `.worktrees/ai-test-runtime-before` | `20261027T144805Z-6acdd286` |
| After | `c27aa5c1929be583e87de6169b7783b544cf37e3` | `.worktrees/ai-test-runtime-after` | `20261027T183329Z-8f49eb96` |

Each tree had a fresh, separate `target/`. The same existing
`profile_maven.py`, queue/control harness and category registry were byte-identical
between snapshots; the category runner's new timing reporting and the intended
POM/test selection changes differed. The supervisor ran before, then after,
without overlapping the two measurements. Both completed all **85 ordinary
batches plus one separate guard batch**, one worker, with the existing admission
and heap policy. No release `-Pfull-suite` or manual audit was selected.

Both actual preflights passed Java 25, Lua 5.4 and PowerShell. The environment used
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk` (observed release `25.0.5.0.2`),
`LUA_BIN=/usr/bin/lua5.4`, unset `DISPLAY`/`WAYLAND_DISPLAY`, and surfaceless
EGL. Original absolute S1, S2, S3&K and KiS2 ROM paths were supplied through
`--rom-directory` pointing at the main checkout's original-ROM directory; required original ROM
identities were verified with the existing helper. Each task-owned checkout
configuration link was replaced with a private copy of the same starting config,
SHA-256 `6abd111c03f798dbe2a618cb4985aeb599127f44668edb2a8edbde118d11f7da`.
This protected the shared config and kept starting inputs equal.

Equivalent invocation from each pinned measurement tree, with
`OPENGGF_ROM_DIRECTORY` set to that absolute original-ROM directory:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/profile_maven.py \
    --output "$PWD/target/suite-runtime-profile.json" \
    -- --category all --headless-gl surfaceless-egl \
    --rom-directory "$OPENGGF_ROM_DIRECTORY" \
    --max-minutes 180 --run
```

The stopping rule was completion of every planned ordinary/guard batch, allowing
a completed failing JUnit run to be timed; incomplete/timeout/crash/source-drift
results would stop the pair. The 180-minute execution limit excludes queue
waiting; the existing ten-minute no-output timeout remained enabled.
The lightweight profiler sampled descendant CPU/RSS once per second, without
forced GC. This was **one sequential shared-host pair** with shared dependency,
ROM, OS and GPU caches, rather than an isolated or repeated hardware benchmark.

### Complete command and execution accounting

Durations below are wall-clock seconds. Ordinary/guard execution excludes shared
admission waiting and includes Maven/JVM launch and shutdown for its batches.
Command wall starts at the profiler's runner launch; initial worktree creation
and the separately completed preflights are outside it.

| Measurement | Before | After |
| --- | ---: | ---: |
| Cold preparation | 93.88 | 97.49 |
| Ordinary smoke execution | 7,510.14 | 6,140.00 |
| Separate guards execution | 548.89 | 226.08 |
| **Smoke + guards execution** | **8,059.03** | **6,366.08** |
| Recorded queue waiting, 86 admissions each | 5,358.50 | 3,216.00 |
| Residual launch/report/accounting overhead | 12.77 | 12.41 |
| **End-to-end command wall** | **13,524.18** | **9,691.98** |
| Executed JUnit cases, both lanes | 44,947 | 42,118 |
| Failures / errors / skips | 46 / 0 / 81 | 45 / 0 / 81 |

End-to-end wall was **3h 45m 24.18s → 2h 41m 31.98s**.
Its 1h 3m 52.20s reduction includes **35m 42.50s less recorded queue waiting**;
the test-execution reduction is 28m 12.95s. Queue observations were captured from
all 86 completed admission records for each unique measurement tree before
rolling telemetry could discard them. Preparation activity waits were negligible
in both `preparation.json` records. The small residual includes other launch,
reporting and rounded telemetry costs; it is not assigned wholesale to waiting.
During the pair, waits included memory pressure and foreign exclusive
measurements; those jobs were left intact.

Sampled descendant CPU was 10,655.26 → 9,296.86 seconds; process-tree peak RSS
was 4,658,860,032 → 4,611,825,664 bytes. CPU is a sampled lower bound; RSS sums
shared pages and one-second sampling can miss short-lived children/peaks.
These figures do not establish a smaller safe heap or reservation.

### Attribution and remaining cost

| Observation | Before reported seconds | After reported seconds |
| --- | ---: | ---: |
| `TestS3kBonusPortalMechanismProducts` | 873.554 | 16.502 |
| Product-heavy ordinary batch 70, process wall | 1,312.25 | 80.33 |
| `TestBuildToolingGuard` | 275.7 | 26.983 |
| Unchanged nine-case `TestS3kKnucklesEndingLifecycleCapture` | 118.495 | 120.871 |

The portal class fell **98.11%**; batch 70 fell **93.88%**. The batch boundaries
shift by one candidate after the new selector class, so whole-selection totals
and the matched portal owner are the primary comparison. The unchanged ending
control passed all nine cases with zero skips in both snapshots. Through the
leading 69 batches execution was 3,010.46 → 2,987.44 seconds; the main divergence
appears where the expensive products were removed.

The portal and ending class observations came from Surefire XML/the runner's
XML timing summary. The old build-guard number uses Maven's displayed precision.
The old return-products class separately reported 284.2 seconds. Its after
per-batch XML was removed automatically and its timing fell outside the retained
global top-20 ranking before inspection, so no matched return-class speedup is
claimed. No extra test run was submitted solely to recover that component timing.

The slowest remaining after classes were:

| Owner | Reported JUnit seconds |
| --- | ---: |
| `TestSitarHeroCharts` | 522.705 |
| `TestLrzSszCheckpointRoutes` | 325.218 |
| `TestS3kGumballPachinkoRewardMechanics` | 314.004 |
| `TestS3kLrzHpzSszColdCampaign` | 256.129 |
| `TestS3kSanctuaryPedestalLifecycle` | 235.376 |
| `TestS3kSanctuaryOriginReturnLifecycle` | 181.817 |

The Sitar class's three full-catalogue ROM cases alone report **522.438 seconds**:
S3K 306.212, S2 149.027, S1 67.199. Source inspection confirmed they prepare
every song in each catalogue and check all supported roles/difficulties;
separate small tests cover chart curation, clocks, difficulty and ownership.
This is a concrete next cost/value review candidate. None of these remaining
owners was modified by the timing task. Class/case observations are attribution
hints and are not summed into the process-wall total.

### Exact failure and skip comparison

Both snapshots completed with failures. All **45 after failures** match before
exactly by class, test identity, failure kind, exception type and assertion
message, with **zero new or modified failures**. The ordinary lane has the same
42 failures; guards retain three. All 81 skipped identities and reasons match
exactly. Both lanes in both snapshots report zero omitted failures/skips.

The shared guard diagnoses are:

- `TestRewindCoverageGuard#noNewCoverageGapsBeyondBaseline`: missing coverage
  key `MhzEndBossInstance#objectRef#hitProxy`.
- `TestBuildToolingGuard#s3kStaticSessionStateDebtShouldNotGrow`: the same
  `HCZWaterSkimHandler.slotRetired` and
  `Aiz2BossEndSequenceState.cutsceneKnucklesSlot` additions.
- `TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree`:
  `TestTestReelExtension#bodyPasses` has no assertion/verify/fail oracle.

The one before-only failure is deliberately deferred:
`TestObjectUpdateClockTerminologyGuard#objectUpdateClockUsesVIntRunCountTerminologyAcrossBoundaryAndFrameworkHooks(Path)`.
It reports `frame` at `TestTasS3kWaterSkimLifetime.java:210` and
`TestTasS3kOffscreenSolid.java:41`, and `f` at
`TestTasS3kOrtExhaustion.java:60`. Moving this wording audit to
`exhaustive-audit` explains **46 → 45 failures**; it does not repair those
violations.

The 81 skips comprise 32 placement-dependent absences, ten optional mod-asset
prerequisites and 39 opt-in capture/measurement, environment, reference or route
assumptions. Those include the spin-tube capture/release assumption, an explicitly
isolated OpenAL driver, uninitialized CNZ visual setup and absent local reference
captures. No skip reason reports a missing original ROM. These completed smoke
runs remain bounded, failing validation; no full-suite, exhaustive-audit,
whole-route or whole-trace certification is claimed.

Shared failing identities, preserved without raw stacks/log archives:

```text
com.openggf.game.rewind.coverage.TestRewindCoverageGuard#noNewCoverageGapsBeyondBaseline
com.openggf.game.sonic3k.objects.TestCnzMinibossRegistered#numericSlotIsImplementedInBothPointerTables
com.openggf.tests.TestBuildToolingGuard#s3kStaticSessionStateDebtShouldNotGrow
com.openggf.tests.rules.TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree
com.openggf.tools.TestDezColdRouteCapture#coldCompleteRouteDefeatsBothMinibossPhasesAndLoadsActTwo
com.openggf.tools.TestDezColdRouteCapture#coldIncomingActTwoClimbsEnergyBridgesAndTogglesGravityWithRewind
com.openggf.tools.TestDezColdRouteCapture#coldIncomingActTwoCompletesUpperGravityRouteCarrierAndCountdownLaunchWithRewind
com.openggf.tools.TestDezColdRouteCapture#coldIncomingActTwoCrossesTiltingBridgeWithoutAShieldWithRewind
com.openggf.tools.TestDezColdRouteCapture#coldIncomingActTwoDefeatsGravityBossAndLoadsFinalStageWithoutTransformation
com.openggf.tools.TestDezColdRouteCapture#coldIncomingActTwoDescendsInvertedSpringShaftWithRewind
com.openggf.tools.TestDezColdRouteCapture#coldIncomingActTwoTraversesBothTransportersWithRewind
com.openggf.tools.TestDezColdRouteCapture#coldIncomingActTwoTraversesGravityTubesConveyorAndStaircaseWithRewind
com.openggf.tools.TestDezColdRouteCapture#coldIncomingActTwoTraversesUpperTransportChainsAndEastHubWithRewind
com.openggf.tools.TestDezColdRouteCapture#coldRouteClearsAllTurbinePanelsAndOpensDoorWhileObjectControlled
com.openggf.tools.TestDezColdRouteCapture#coldUpperRouteTraversesLiftsConveyorsAndTimedBridgesWithRewind
com.openggf.tools.TestDezIncomingFinalRouteCapture#coldEmeraldTeamClearsBothActsFinalFightAndDoomsday
com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding
com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding
com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding
com.openggf.tools.TestDezIncomingFinalRouteCapture#coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding
com.openggf.tools.TestDezSoloActTwoColdRouteCapture#coldSonicAloneClearsActTwoAndLoadsFinalStage
com.openggf.tools.TestDezSoloActTwoColdRouteCapture#coldTailsAloneClearsActTwoAndLoadsFinalStage
com.openggf.tools.TestDezSoloActTwoColdRouteCapture#coldTailsLowerGravityPadPreservesPendingContactAcrossRewind
com.openggf.tools.TestDezSoloActTwoColdRouteCapture#coldWideSonicAloneClearsActTwoAndLoadsFinalStage
com.openggf.tools.TestDezSoloColdRouteCapture#coldSonicAloneClearsMinibossAndReachesPlayableActTwo
com.openggf.tools.TestDezSoloColdRouteCapture#coldTailsAloneClearsMinibossAndReachesPlayableActTwo
com.openggf.tools.TestDezSoloColdRouteCapture#coldWideSonicClearsBothBossPhasesAndReachesPlayableActTwo
com.openggf.tools.TestLrzKnucklesColdRouteCapture#coldKnucklesClearsMinibossAndRestoresFightAndActTwoHandoff
com.openggf.tools.TestLrzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace
com.openggf.tools.TestLrzKnucklesColdRouteCapture#coldKnucklesReachesMinibossAndRestoresTraversalAndCloudEscape
com.openggf.tools.TestLrzKnucklesColdRouteCapture#coldKnucklesReplaysIncomingRouteAndFirstActTwoDoor
com.openggf.tools.TestLrzKnucklesColdRouteCapture#coldKnucklesRestoresActTwoTraversalToTheMiddleCorridor
com.openggf.tools.TestLrzKnucklesColdRouteCapture#coldKnucklesRestoresActTwoUpperClimbsAndDoorRelease
com.openggf.tools.TestMhzKnucklesColdRouteCapture#coldKnucklesCompletesActOneAndRestoresNativeInteractions
com.openggf.tools.TestMhzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndIsolatesTheFbzTimeline
com.openggf.tools.TestS3kHpzJoinedColdRouteCapture#coldKnucklesReplaysUpperPadAndTheJoinedSszCraneControlRelease
com.openggf.tools.TestS3kLrzHpzSszColdCampaign#knucklesColdCampaignClearsSkySanctuaryTwoThroughTheEndingAndTitle
com.openggf.tools.TestS3kLrzHpzSszColdCampaign#pairColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory
com.openggf.tools.TestS3kLrzHpzSszColdCampaign#sonicColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory
com.openggf.tools.TestS3kLrzHpzSszColdCampaign#tailsColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory
com.openggf.tools.TestSozColdRouteCapture#knucklesColdActTwoCompletesWithPuzzleReplayAndPlayableLavaReef
com.openggf.tools.TestSozColdRouteCapture#knucklesWideColdActTwoCompletesWithPuzzleReplayAndPlayableLavaReef
com.openggf.tools.TestSozColdRouteCapture#soloColdActCompletesWithTraversalReplayAndPlayableDestination(int, String)[5]
com.openggf.tools.TestSszWidePairedColdRouteCapture#coldCompleteWideRouteDefeatsAllBossesAndIsolatesTheDezTimeline
com.openggf.tools.TestSszWidePairedColdRouteCapture#coldFirstReplicaAndTransportReplayWithRetiredColumnDebris
```

The existing temporary summaries, command records and profiler outputs were
inspected for these aggregates. The diagnostic acknowledgment commands are
`run_categories.py --acknowledge 20261027T144805Z-6acdd286` and
`run_categories.py --acknowledge 20261027T183329Z-8f49eb96` in their respective trees.
The committed research record retains the comparison; raw diagnostics and the
owned measurement worktrees are disposable workflow output.


## Thirty-minute local command target

The maintainer requested a 30-minute complete local validation target after the
matched pair measured 6,366.08 seconds of smoke/guard execution plus preparation.
New base: `cebb7cd74787dc72636275253bb60575914fdeb3`, fetched and fast-forward
checked. Task tree: `.worktrees/ai-local-thirty-minute`, branch
`feature/ai-local-thirty-minute`. Original ROMs and the same Java 25 / Lua 5.4 /
surfaceless EGL setup remain in use, with a private starting-config copy.

The target covers the complete local command, including preparation and guards.
Queue time remains separately observed; global admission, heaps, forks and
resource reservations are unchanged. No timeout, missing-ROM skip or hand-narrowed
category plan can establish this target. One complete all-category measurement
follows focused verification; further changes require affected revalidation.

### Selection and preserved contracts

- Full Sitar ROM song catalogues and the 22-song S2 PCM preparation are manual
  audits. Routine tests prepare seven representative song prefixes across all
  three games, and four S2 arrangements covering pitched/noise PSG, absent PSG,
  harmony and the credits boundary. They keep the original part/difficulty
  assertions. The independent full S2 sequencer/control-flow oracle remains
  routine. A failed twenty-second credits witness was rejected because it ended
  before a required synth part; the S2 witness retains the existing forty-second
  boundary at 8 kHz, without weakening its oracle.
- Festival/ruins, Infinite Sonic and Putt Putt full character/width/activity/act
  products remain manual. Routine witnesses cover every distinct activity,
  character/width boundaries, six dry zones plus the SBZ3 alias, both platform
  and flyer families, four full golf routes and one mixed-roster two-act route.
  All unique short regressions remain. The extracted asserting bodies are shared
  with the original products; fresh harness/session ownership is retained.
- AIZ1/HCZ1 entry checks cover five preset/donor diagonals and two legacy widths.
  Real intro/input release, movement, complete two-cycle replay and repeated
  owner-replacing reset assertions remain. HCZ shares one fixture for the entry
  and subsequent reset sequence. Original full products are manual.
- Gumball/Pachinko finite configuration products and sanctuary stage/outcome
  products are manual. All five real mechanisms retain native and representative
  owner/lifetime checks; all seven physical pedestal selectors, native result
  outcomes, conversion/non-conversion, donated bodies and roster pilots remain.
- Checkpoint configuration/death products in FBZ, MHZ, DEZ, LRZ/SSZ and SOZ are
  manual. Native witnesses still contact every authored placement, and first-post
  real two-death/incoming-world checks retain both acts, all character owners,
  all three donors and CPU roster ownership. SOZ retains every other placed post
  natively plus its solo and mixed/duplicate-team pilots. The ROM-free selection
  guard compares placement projections against the complete products so a later
  narrowing cannot silently drop a native post.
- Reviewed long cold-route/campaign qualifications in the capture tests are
  method-tagged manual audits. Their full existing assertions and distinct
  rewind spots remain. The LRZ family repeats roughly 145,711 input passes for
  a 31,440-input terminal route; the terminal route intentionally clears earlier
  spot sets, so it is not a substitute for the earlier assertions. Local
  lifecycle/object checks, the 1,020-input DEZ Knuckles gravity replay, the
  2,556-input Tails signed-camera flight, CNZ2 input round-trip, and independent
  menu/save boundaries remain routine. Cold reachability, accumulated RNG/pool
  state and the deferred replay windows are explicitly outside a smoke pass.
- The two ending classes keep three real lifecycle witnesses each across
  character/team, bad/Chaos/super progress, NTSC/PAL and boundary widths. The
  complete original ending products are separate manual methods sharing the
  same execution/replay helpers. Cheap native branch/timer/palette and follower
  retirement tests remain routine. A campaign's second 600-frame comparison
  that only printed differences was removed; its actual asserted menu/save
  inlet remains.

No production algorithm, ROM fixture, tolerance or admission rule changed.
Whole-class deferrals were avoided in mixed owners. The new selection guard
pins expensive product tags alongside their retained behavior witnesses; it
checks actual provider coverage without booting products merely to count them.

### Focused verification and budget measurement

Build-only `maven_queue.py -Dmse=off test-compile -B` passed on Java 25 in
Maven-reported 1:32. Tool preflight passed Java 25, Lua 5.4 and PowerShell.
The combined prepared focused invocation selected 31 affected selectors plus
its method-qualified profile contract within that set, with original absolute
ROM paths and surfaceless EGL. It executed **679 cases / 1 failure / 0 errors /
0 skips** in Maven-reported 5:03. The sole failure was the rejected too-short S2
credits witness described above. All other 678 cases passed, including the new
selection guard and the four required S3K stability selectors. The independent
Incoming DEZ final-fight witnesses passed locally; this does not repair or renew
its deferred cold campaigns.

Observed focused class timings included Festivals 17.846 seconds, Infinite Sonic
34.756, Putt Putt 20.249, HCZ1 entry/reset 13.962, AIZ1 entry 8.418, Sitar charts
7.893, Gumball/Pachinko mechanisms 10.885, and the three Knuckles endings 41.201.
None of the retained platform/flyer representatives skipped their assumptions.
Focused repair verification and the complete command measurement follow before
delivery; the 30-minute target is not yet claimed achieved.

The repaired S2 representative method passed **1 case / 0 failures / 0 errors /
0 skips**, reported 10.20 JUnit seconds, Maven 39.817 seconds including affected
test recompilation, with zero queue wait. The unchanged parts of the earlier
focused selection were not repeated. The new platform/flyer representatives all
executed (no assumption skips), and selection/placement guards passed.

The first complete-command candidate was interrupted after **1,889.833 seconds
(31m30s)** because it had already missed the target with only **72/85 ordinary
batches complete** and no guards. Preparation took 87.83 seconds; the partial
ordinary lane recorded 1,780.76 execution seconds, 31,276 cases, one failure,
zero errors and 41 skips. Queue waits were zero. Run
`20261027T215055Z-00a1c320`, source fingerprint
`64f5aa231beb44c077b023adf6e683a91bc778afd3133ab8f0d0e1a652a2bd21`,
is **incomplete**, not a validation pass. The sole observed failure is the
unchanged baseline `TestCnzMinibossRegistered#numericSlotIsImplementedInBothPointerTables`,
`AssertionFailedError: expected: <true> but was: <false>`. Remaining coverage
was not inferred. Its consumed diagnostics were acknowledged.

The next revision retains full random-seed rewind coverage plus bounded
adjacent-keyframe replay, keeps all terrain probes and historical digest pins
while replacing a mocked delegate with the existing direct level delegate,
and moves the remaining large SOZ/LRZ/squeeze configuration products into the
manual audit with short real-behavior witnesses. The payload-authority guard
parses sources in bounded batches with a fresh scanner per compilation unit;
new regressions compare exact diagnostics and final partial-batch coverage.

Five scene test classes now compile/package their immutable example source once
per class. Each witness still performs a production repository scan/validation
and opens a fresh registration plan, class loader, host, input and storage root.
The original `build` convenience path retains its behavior. A ROM-free isolation
regression opens two runtimes, closes one and exercises the other while checking
that their types and mutable owners differ and package bytes remain unchanged.
There is no global compilation cache or reusable validation result.

The second focused invocation passed **237 cases / 0 failures / 0 errors /
1 skip** in Maven-reported 2m26s including recompilation. The skip remains the
explicit long-running rewind soak. The terrain oracle kept all original hashes
and ran in 4.291 seconds (first candidate 33.091); rewind torture ran in 6.304
(first candidate 51.018); squeeze 3.433 (27.035); HPZ compatibility 8.270
(33.581). All 37 payload-authority guard cases passed in 17.77 seconds,
including the real repository scan and the two new batching regressions.
The ROM-free compiled-package isolation regression passed, as did all five
scene consumers. These focused times are not a completed budget measurement.

HPZ compatibility/lifecycle now distribute five presets across every supported
solo pair and distinct normalized donor/team shape. Every transport, follower
authority, Knuckles refusal, two-death and joined-arrival assertion remains;
full width products are manual. SOZ connected mechanisms keep all four real
mechanisms under native, S1 and S2 rules, distributing donated widths rather
than multiplying every mechanism by every preset. Hook-policy checks retain
every Bash and available PowerShell decision while building each final fixture
once and awaiting both read-only hook processes before its next mutation.

The final focused three-class invocation passed **82 cases / 0 failures /
0 errors / 0 skips**, Maven 1m09s including affected compilation. Both hook
implementations were available. Actual-launch preflight passed with Java 25,
`LUA_BIN` selecting Lua 5.4 and PowerShell; a preceding probe without that
required Lua selection was rejected before tests. Config SHA-256 remains
`6abd111c03f798dbe2a618cb4985aeb599127f44668edb2a8edbde118d11f7da`.
The change-based plan against `cebb7cd74787dc72636275253bb60575914fdeb3`
selects all ten smoke categories, 4,238 candidates in 85 batches, plus guards.
The next measurement starts with a clean target, unchanged one-worker/budget
settings and original ROMs. Expected cost is 30–40 minutes, still uncertain;
the stopping rule is all 85 ordinary batches and guards completed, with a
60-minute execution safety limit. A completed failing run may establish cost
but requires exact baseline failure attribution; a partial run cannot.

### Completed one-worker result and remaining cost

Run `20261027T223548Z-d9fae56a`, on the same base with uncommitted source
fingerprint `899bb4023d6e9ec3ab54aa956ee4a4709cb51a785dff45641add133b29b80729`,
completed every planned batch:

| Component | Seconds | Coverage |
| --- | ---: | --- |
| Cold preparation | 88.06 | Java 25, clean target |
| Ordinary smoke, one worker | 2,204.97 | 85/85 batches; 35,100 cases; 1 failure; 0 errors; 49 skips |
| Guards, fresh JVM | 155.57 | 808 cases; 3 failures; 0 errors; 0 skips |
| Recorded test admission wait | 0.00 | 86 admissions |
| **Complete command wall** | **2,475.634 (41m15s)** | profiler complete, exit 1 |

The remaining 27.03 seconds of command wall were not separately attributed;
build admission waiting was below one millisecond. The 30-minute target is
**missed** at one worker. All four failures match the earlier matched baseline
exactly by identity, kind, type and assertion message: the CNZ registration
assertion and the existing rewind-coverage, S3K static-session-debt and
assertion-free-diagnostic guards. None was fixed by this task. The 49 skip
reason/count pairs equal the earlier 81 after removing the 32 platform/flyer
assumption skips whose full products are now manual; all selected new
representatives executed. Original ROM identity failures are not hidden skips.
Consumed diagnostics were acknowledged; no pass receipt or raw report archive
was retained.

The late cost review found two small optional products (SSZ bridge checkpoint
width/profile combinations and SOZ rock-puzzle widths reusing five BK2s). They
remain routine because removing their repeated rows cannot close the remaining
gap. Unique ending pixels, authored MHZ routes, route-author recovery, dense
rewind windows and bounded real-socket recovery remain routine too.

The largest remaining class was `TestRaceSession` at 50.166 seconds, including
a literal 31-second relay-grant sleep. Its existing `ControlledMasterServer`
seam advances the actual master clock on the real relay-room loop with a
completion barrier. Only the RELAY row now advances 31,000 milliseconds through
that seam; DIRECT/LAN still use the normal server. The original expired-grant,
fresh code resolution, password, chat, real socket admission and cleanup
assertions remain. Room identity is read only; production grant-expiry code and
its 30,000-millisecond threshold remain authoritative. No production API was
added.

After focused clock verification, the next complete cold-target measurement
uses the already-supported `--workers 2` / `test-concurrent` mode, with every
ordinary candidate and the separate one-worker guards. Each ordinary JVM keeps
its existing 3 GiB maximum and serial Jupiter execution; existing exclusive
queue admission remains mandatory for this mode. No scheduler limits or heap
settings are raised. The host reports 32 logical CPUs and 31,919,976 KiB RAM.
Expected complete cost is 25–35 minutes, still unproven; completion of all
batches and guards, with the existing 60-minute execution safety limit, remains
the stopping rule. This is a shared-host observation, not an isolated hardware
benchmark, and the clock change means it is not a pure fork-count experiment.

Focused `TestRaceSession` verification passed **38 cases / 0 failures /
0 errors / 0 skips**, Maven 50.090 seconds including recompilation. The whole
class took 20.04 JUnit seconds versus 50.166 in the completed one-worker run.
DIRECT, RELAY and LAN private-code rows all executed. This removes the wall
sleep while retaining the real grant-expiry/admission contract.

### Thirty-minute target achieved

Run `20261027T232058Z-984e96b3` completed on base
`cebb7cd74787dc72636275253bb60575914fdeb3` with the uncommitted source
fingerprint `ce445abe0d9fc0d39704c8655b300c86411058155c714d35c94855ef5ca3263a`.
Every ordinary batch and the separate guard invocation finished.

| Component | Seconds | Coverage |
| --- | ---: | --- |
| Cold preparation | 93.23 | Java 25, clean target |
| Ordinary smoke, two workers | 1,439.16 | 85/85 batches; 35,100 cases; 1 failure; 0 errors; 49 skips |
| Guards, one fresh JVM | 156.22 | 808 cases; 3 failures; 0 errors; 0 skips |
| Recorded test admission wait | 0.00 | 86 admissions |
| **Complete command wall** | **1,715.311 (28m35.3s)** | profiler complete, exit 1 |

This is **84.689 seconds below the 30-minute target**. The remaining 26.70
seconds of wall time were not separately attributed; build admission waiting
was below one millisecond. The two-worker run has exactly the same 35,908
reported cases, four failure identities/kinds/types/assertion messages and
49 skipped identities/reasons as the completed one-worker run. Both runs have
zero omitted failure/skip records and zero errors. None of the four inherited
failures was repaired or waived by this task:

| Failure identity | Unchanged baseline finding |
| --- | --- |
| `TestCnzMinibossRegistered#numericSlotIsImplementedInBothPointerTables` | `expected: <true> but was: <false>` |
| `TestRewindCoverageGuard#noNewCoverageGapsBeyondBaseline` | `MhzEndBossInstance#objectRef#hitProxy` coverage gap |
| `TestBuildToolingGuard#s3kStaticSessionStateDebtShouldNotGrow` | `HCZWaterSkimHandler.slotRetired` and `Aiz2BossEndSequenceState.cutsceneKnucklesSlot` debt |
| `TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree` | `TestTestReelExtension#bodyPasses` lacks an oracle |

The earlier cold-command comparison, with recorded queue waiting removed, was
2h16m05.7s before the initial selection work and 1h47m56.0s after it. This task's
completed one-worker command took 41m15.6s; the final two-worker command took
28m35.3s. The final observation is 79.0% below the original active command and
73.5% below the previously delivered active command. These are shared-host
observations with warm dependency/OS/GPU caches, not portable timing guarantees.
The final step combines the clock repair and two workers, so its 30.7% reduction
from the one-worker result is not attributed solely to concurrency.

Sampled descendant RSS sums peaked at 4.10 GiB for one worker and 4.72 GiB for
two. Sampled CPU totals were 5,207.05 and 6,261.04 seconds respectively: shorter
wall time uses more total CPU in the two-worker mode. Sampling can miss short
children/peaks and RSS sums shared pages; these figures do not replace the
existing heap/admission budgets. The ordinary two-worker mode retains exclusive
queued test admission and releases it between the same bounded batches.
Focused jobs and resource-constrained machines keep the one-worker default.

Equivalent complete measurement from the task tree, with
`OPENGGF_ROM_DIRECTORY` set to the main checkout's original-ROM directory:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/profile_maven.py \
    --output "$PWD/target/thirty-minute-profile.json" \
    -- --category all --workers 2 --headless-gl surfaceless-egl \
    --rom-directory "$OPENGGF_ROM_DIRECTORY" --max-minutes 60 --run
```

The target is established for this complete local smoke-plus-guards command,
not release-only full-suite, exhaustive audits or an entire trace sweep.
Original ROMs and the unchanged starting config were supplied. Deferred
configuration interactions, cold reachability, accumulated campaign state and
replay windows remain explicit audit/qualification limits. Consumed diagnostics
were inspected and acknowledged; profiler output remains temporary under
`target/` and is discarded with the task tree. No raw reports, pass cache or
validation receipt were archived.

Implementation and the measured local recipe are committed as
`0bd11d03e4e19aab199a5bcd8c8fe90250769b25`
(`perf(test): bound routine validation with real behavior witnesses`). No
executable input changed after the completed two-worker run; subsequent edits
record its results and qualified-host guidance. The original full products and
deferred cold qualifications remain callable through the explicit audit lane.

## Bounded parser concurrency experiment

On 2026-10-28 the maintainer requested actual experiments with Java concurrency
inside individual tests. This experiment changes only the native-holder and
active-payload source audits and their test-only executor/controls. Base:
`8945c0a789b27be57b2361c70f561f31872a76ca`, fetched and fast-forward checked.
Owned branch/worktree: `feature/ai-internal-test-concurrency`,
`.worktrees/ai-internal-test-concurrency`. Java 25, Lua 5.4 and PowerShell
preflight passed. These are ROM-free audits; no engine simulation, route,
viewport, fixture selection, queue limit, JVM count or heap setting changes.

`SourceScanExecutor` permits one or two task workers, keeps results in input
order, rethrows worker exceptions/assertions, and cancels outstanding work
before executor close waits for termination. Native-holder parsing keeps each
file's compiler and lexical scanner private to its worker; compiled-class
ownership checks remain on the test thread. Payload parsing keeps the existing
32-file batches, with at most one live batch per worker and fresh per-unit
scanner state. Workers read source text inside their task rather than retain a
whole source corpus. The existing synthetic holder and payload controls also
compare concurrent results against their exact expected owners/diagnostics.

Five additional executor cases force overlap/out-of-order completion, verify
the queued tail is visited exactly once, preserve exceptions and assertion
failures, cancel/finish a blocked sibling, and reject unsupported worker counts.
No original cases or assertions were removed. Normal Jupiter execution stays
serial; guards still use one reused 3 GiB fork and existing admission budgets.

### Measurements

All invocations used `profile_small_tests.py --lane normal --max-minutes 10`
with exact FQCN selectors, no forced GC, and the profiler's existing exclusive
test/build-activity leases. The unchanged baseline selected
`com.openggf.level.objects.TestNativeHolderReleaseGuard` and
`com.openggf.tests.trace.runs.TestActiveSegmentPayloadAuthorityGuard`.
Prototype invocations added `com.openggf.tests.TestSourceScanExecutor`.
Example command, with a new output filename per invocation:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/profile_small_tests.py --lane normal \
    --test com.openggf.level.objects.TestNativeHolderReleaseGuard,com.openggf.tests.trace.runs.TestActiveSegmentPayloadAuthorityGuard,com.openggf.tests.TestSourceScanExecutor \
    --max-minutes 10 --output target/internal-parallel-two.json
```

| Observation | Native-holder class | Payload-authority class | Original class sum | Reported cases |
| --- | ---: | ---: | ---: | --- |
| Unchanged, first worktree build | 19.187s | 18.737s | 37.924s | 39 / 0 failures / 0 errors / 0 skips |
| Unchanged, compiled inputs present | 19.222s | 18.695s | 37.917s | 39 / 0 failures / 0 errors / 0 skips |
| Two workers, first prototype | 10.076s | 14.979s | 25.055s | 44 / 0 failures / 0 errors / 0 skips |
| Two workers, unchanged confirmation | 9.777s | 14.925s | 24.702s | 44 / 0 failures / 0 errors / 0 skips |

The original 39 case identities, statuses and multiplicities match exactly;
the only additional cases are the five executor controls. Both prototype
case inventories match exactly. Their executor class took 0.011s / 0.012s.
The confirmation reduces the two original class times by 49.1% and 20.2%,
34.9% combined (13.215 seconds). These are focused observations on one host,
not a new whole-suite timing or an independently qualified low-heap lane.

The first baseline's Maven command succeeded and source stayed unchanged, but
its memory observer recorded one missing `VmSwap` field near JVM exit. Its
memory result is invalid and is excluded from comparison. The unchanged rerun
and both prototypes have complete valid sampler evidence, no timeout, no
source drift and no omitted failure/skip records. The observer was not relaxed.

| Valid observation | Test-process peak RSS | Test-process peak swap | Simultaneous whole-process-tree peak RSS + swap | Maven execution, including build | Queue wait |
| --- | ---: | ---: | ---: | ---: | ---: |
| Unchanged rerun | 3,903,950,848 B | 7,872,512 B | 6,672,855,040 B | 70.220s | 351.956s |
| First prototype | 4,041,142,272 B | 0 B | 6,707,875,840 B | 56.487s | 193.254s |
| Confirmation | 4,015,157,248 B | 0 B | 6,840,197,120 B | 57.121s | 97.423s |

Test-process peak RSS rose about 106–131 MiB. The whole-tree measure includes
Maven, compilation and its observed swapping; do not sum independent process
peaks or interpret RSS as retained Java heap. Sampling at 100 ms can miss peaks
and double-count shared pages. Cold/warm Maven lifecycle times are not matched
build-time comparisons; the table separates reported test-class duration.
The first worktree build spent 122.879s executing and 2,449.155s queued; its
long host-memory admission delay was not test execution. User-freed RAM allowed
the queue to progress. Build-drain waits were measured separately and below
0.02s in the valid observations.

The normal baseline source/fixture/build fingerprint is
`6bad1e2db2e61a0198fa8d3e733a149d2df96e61393cd039e80da41025236948`;
both measured prototypes retain
`4e9262e4e9fcc0bcd65dbf7d002da0cd1bb21c9e75ad0f340798c5c496d4d644`.
No Java implementation changed between the two prototype measurements.
Subsequent prose records the result without changing those executable inputs.

### Validation scope

The change-based plan against the pinned base selects all ten smoke categories,
4,260 candidates plus guards, because the helper/control and payload test live
in shared test directories. Validation instead uses the two actual source-audit
consumers (which scan the complete main/test source roots) and the five bounded
executor controls. Impact is confined to these test implementations; production,
build/selection policy and runtime consumers do not change. The real scans,
exact diagnostics, final partial batch, lexical isolation and forced concurrency
failure/teardown controls address the changed behavior. This is proportionate
focused validation, not a full smoke or release-suite pass. Temporary profiler
reports are consumed and removed before delivery; no raw archive or pass cache
is retained.

## Resource-aware batching follow-through (2026-10-28)

The next step expands beyond the two-parser experiment without reducing selected
coverage. The task base is `dc73d64858eef4087e70b572d780eb3f7cf50201`.
Its category inventory contains 4,260 ordinary candidates. The existing 90-class
small-test registry accounts for only 3.883 historical JUnit seconds and was
scattered through alphabetical batches: no entire ordinary batch qualified.
A source/fixture audit identified 26 additional candidates covering 139.248
historical JUnit seconds. These historical costs come from the completed
`15ffd434` capture, not a matched current-source performance comparison.

The candidates cover hook-policy subprocesses, Golf/Sitar transport probes,
seven creator compiler/rules suites, nine network clients/hosts and seven SDK
classes. Their exact names live in `maven_small.py`'s batch-only pool. The audit
checked unique FQCNs across source roots, packages, source ownership, fixture and
loader cleanup, bounded payloads, native/direct memory and child processes. No
ROM/game-module/tag/inheritance matcher was relaxed. Creator outer counts hide
nested suites: their unchanged launcher still scans the compiled creator tests
with `--fail-if-no-tests`; the seven source trees have no disabled/conditional
or assumption-based tests. Outer case counts do not quantify that inner breadth.

### Preparation and measurement limits

Three early diagnostics completed their selected cases successfully but had
invalid memory observers: normal three-class/99-case, lean 26-class/268-case,
and normal 33-class/336-case runs, all with zero case failures/errors/skips.
Their Java source inputs stayed unchanged. A missing `/proc` `VmSwap` field
invalidates those memory observations; successful cases do not repair them.
The 33-class run supplies the bounded pre-change guard/case comparison.

An attempted lean rerun then failed before any test launched. A matched
build-only reproduction exposed `java.lang.OutOfMemoryError: Java heap space`
in javac 25.0.5 while compiling 5,004 test source files with `MAVEN_OPTS=-Xmx1g`:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk MAVEN_OPTS=-Xmx1g \
  python3 tools/testing/maven_queue.py -Dmse=off test-compile -B
```

Normal-memory preparation subsequently completed successfully (34.209 seconds
in the first prepared diagnostic). Consequently the new registrations apply to
prepared category batches only. They do not extend the existing automatic
focused lifecycle registry: a successful small test heap does not qualify the
whole-project compiler at that heap.

The profiler's explicit `--prepare` mode mirrors the category runner: hold the
worktree lock, validate the preparation contract, compile once under normal
Maven memory/shared build activity, release that activity, request test admission,
drain builds, then measure only `surefire:test`. Source checks protect the gap;
no pass cache or receipt is created. Preparation execution counts against the
watchdog; build/queue waits are separate. Preparation memory is unsampled.
The first prepared attempt exposed missing lifecycle-created temp directories
(two creator errors), which were repaired in profiler setup with controls.
Neither failure was converted into a pass or a skip.

The observer accepts missing memory fields only with positive same-identity
exit evidence, zero virtual size/RSS and a fresh status with at most one remaining
thread and no swap field. Such observations are counted separately; unreadable
or unexplained live-process data still fails qualification. A requested two-fork
shape must actually show both test JVMs in one sample. Linux source references:
[`proc_pid_status`](https://github.com/torvalds/linux/blob/master/fs/proc/array.c),
[`do_exit`/`exit_mm`](https://github.com/torvalds/linux/blob/master/kernel/exit.c),
and [`PF_EXITING`](https://code.googlesource.com/linux/torvalds/linux/+/0c59ae1290741854b6cf597ef05bfa9bc811389f/include/linux/sched.h).
Older invalid reports are not retroactively accepted.

### Execution and scheduling changes

The runner stable-partitions qualified/normal candidates into batches of at most
50, preserving the exact selected multiset and relative order within each pool.
Qualification uses one source inventory, then rechecks each low-heap batch before
admission. Rejected source/environment shapes keep normal heaps and requested
workers. One-worker low-heap batches reserve 4 GiB/4 cores; two-worker batches
retain normal exclusive admission, with 1 GiB Maven/test heaps and serial Jupiter
inside each fork. Guards remain one normal fork. The new batch-only pool does not
establish the short runtime needed for aged-head backfill; that exception retains
its previously reviewed registry. Queue limits and reservation defaults do not
change. Per-batch results expose the actual heap, worker and admission shapes.

Source guards now retain line starts rather than boxing one entry for every
UTF-16 character. Equivalence controls retain every offset, comment convention,
line diagnostic and mutable-list behavior. The strict GameServices guard skips
files that cannot contain its required literal. The trace alignment guard scans
at most two independently owned aux streams, retaining every row, raw/gzip
precedence, counts, offsets and discovery-order diagnostics. Its shared movie/input
cache method remains serial. Controls force real overlap and reverse completion,
compare against the original serial loop, and verify reader closure on failures.

### Failure attribution

A bounded pre-change check at the task base reconfirmed four inherited failures
with zero errors/skips: CNZ's `numericSlotIsImplementedInBothPointerTables`,
rewind coverage's `noNewCoverageGapsBeyondBaseline` (`MhzEndBossInstance#objectRef#hitProxy`),
build tooling's `s3kStaticSessionStateDebtShouldNotGrow` (`slotRetired` and
`cutsceneKnucklesSlot`), and assertion-free diagnostics' scan (`TestTestReelExtension#bodyPasses`).
Each exact method ran once under normal queued admission, with separate fresh
ordinary/guard JVMs and explicit surfaceless EGL/absolute original ROM paths.
These identities and assertion messages, rather than failure totals, are the
comparison baseline for combined validation.

### Prepared low-heap qualification

The corrected 26-class invocations used the same source fingerprint
`b504b09cffc504e39a8fa88b7e1aef9cadb2810c3cd2904673314a2b4ef90478`,
Java 25, Lua 5.4, no display variables, no forced GC and the profiler's exclusive
measurement leases. Both completed with **268 cases / 0 failures / 0 errors /
0 skips**. Complete identities/statuses/multiplicities match each other and the
26-class subset of the normal 336-case baseline exactly. Both memory observers
are valid with no errors, source drift or missing JVM roles. Each counted five
verified exit observations. Per-process arguments confirm `-Xmx1g` in Maven and
each test JVM; two test JVMs were observed in one sample for the two-worker run.

| Prepared test JVMs | Test invocation | Normal-memory preparation | Test queue wait | Sampled simultaneous process-tree peak RSS + swap | Reservation |
| --- | ---: | ---: | ---: | ---: | --- |
| 1 | 132.912s | 35.904s | 470.930s | 2,227,101,696 bytes (2.074 GiB) | 4 GiB / 4 cores |
| 2 | 80.492s | 34.586s | 336.122s | 2,911,584,256 bytes (2.712 GiB) | normal exclusive 7 GiB / 8 cores |

Commands used `profile_small_tests.py --lane lean --prepare --workers 1` and
`--workers 2`, with the exact 26 batch-only FQCNs, `--max-minutes 10`, and fresh
output filenames. Preparation holds only build activity and is unsampled; the
RSS/swap values describe the measured Maven/test/child processes. Independent
RSS and swap peaks must not be added. Sampling can miss short peaks and RSS can
count shared pages more than once. Reservations remain conservative estimates.

The matched test invocations improve by **39.4% (52.420 seconds)**. Active
preparation plus invocation improves from 168.816s to 115.079s; queue delay is
separate. This is one measured pair on the qualified host, not a universal speedup
or a whole-suite before/after claim. Existing broad validation already supported
two normal-heap JVMs; its final combined timing must be measured separately.

### Focused guard and tooling verification

The final-policy focused command selected the scanner, runtime-singleton,
bootstrap-provider, object-physics, object-services, trace-alignment and executor
classes with `profile_small_tests.py --lane normal --prepare --max-minutes 10`.
It completed **77 cases / 0 failures / 0 errors / 0 skips**. All 68 original case
identities/statuses/multiplicities from the 33-class baseline are retained exactly;
nine new controls cover the compact mapper, strict-accessor scanner and concurrent
aux streams. Source fingerprint:
`eaddbfae66826284570adbb9ff3ef2001b022963780233c90f52b25c5976b307`.
The memory observer is valid. Test invocation took 16.389s, normal preparation
33.438s and test admission waited 344.546s.

| Reported JUnit class seconds | Before | After (including new controls) |
| --- | ---: | ---: |
| Runtime singleton source guard | 5.587 | 1.378 |
| Bootstrap provider source guard | 4.279 | 4.145 |
| Trace fixture movie alignment | 10.028 | 5.798 |
| Sum of these three classes | 19.894 | 11.321 |

The targeted sum improves 43.1% (8.573s). Bootstrap changes little: compact
indexing is not a claim that its remaining scan/regex cost disappeared. Other
post-change class times are object physics 1.939s, object services 1.260s, scanner
controls 0.020s and executor controls 0.009s. Those are class times, not Maven or
whole-command timings; the earlier top-20 summary did not retain every small
baseline class, so no invented whole-envelope comparison is made.

The complete Python safety command (`python3 -m unittest discover -s tools/testing
-p 'test_run_categor*.py'`) passed 302 controls, including both real 128 MiB Java
observer probes, with no skips. Its 1,241.692s elapsed includes worktree/test queue
waiting and is not test execution cost. A final conservative backfill-policy
adjustment and advisory revision bump to 5 then passed all 301 Python-only
controls (34.309s); the two unchanged Java observer probes were not repeated.
One-worker batch-only tests receive the lower memory reservation but cannot claim
the aged-head short-job exception. `_JAVA_OPTIONS` rejection, caller-root `.mvn`
checks, failure-ignore XML status, preparation timeout classification, and exact
selection/source-change/normal-worker fallback all have controls. Actual Java 25,
Lua 5.4 and PowerShell preflight passed for the broad launch environment.


### Complete cold smoke follow-through

At `899df2b3b0205d6b34f2731c47204c00116207cd`, the complete change-based command
used `--base dc73d64858eef4087e70b572d780eb3f7cf50201 --workers 2
--headless-gl surfaceless-egl --rom-directory <original-rom-directory>
--max-minutes 40 --run`, Java 25 and Lua 5.4 with display variables unset.
Compiled outputs were cleared under the build activity lease before launch.
All 4,260 ordinary candidates completed in 86 batches, then fresh single-worker
guards. Ordinary: 35,244 cases, one inherited failure, zero errors, 52 skips.
Guards: 817 cases, three inherited failures, zero errors/skips. All four exact
failure identities, kinds, types and messages match the bounded baseline above;
no omitted failure/skip records. Diagnostics were inspected and acknowledged.

| Current cold run timing | Seconds |
| --- | ---: |
| Normal-memory preparation | 100.74 |
| Ordinary batch execution | 1,564.79 |
| Guard execution | 149.34 |
| Sum of these execution phases | 1,814.87 (30m15s) |
| Recorded queue waits, 87 admissions | 3,312.90 (55m13s) |
| Total wall | 5,140.87 (85m41s) |
| Wall excluding recorded queue wait | 1,827.97 (30m28s) |
| Other/unmeasured non-queue overhead | 13.10 |

Queue values are rounded telemetry; total wall uses process start and final status
mtime. This run is slightly over the 30-minute execution target and substantially
over it in elapsed wall time. It is not a matched whole-suite before/after: the
older timing source predates intervening engine and test additions. The first
three qualified batches (50/50/16 candidates) used 1 GiB heaps but still retained
exclusive 7 GiB / 8-core admission for this measured run; their sampled RSS peaks
were 0.672, 1.882 and 1.688 GiB. They do not establish hard memory bounds.

The 52 skips were inspected. Three require REV00 properties not previously in
runner discovery; existing original root images match engine SHA-1 and CRC32
identities. Discovery now supplies their separate `sonic1.rev00.rom.path` and
`sonic2.rev00.rom.path` properties without copying or changing ROMs. Three methods require explicit properties; other consumers fall back to the same
local original filenames. A focused check covers all seven consumer classes below.
The other 49 skips cover opt-in captures/measurements, optional creator packages,
native/reference prerequisites, CNZ visual setup and the existing spin-tube
capture assumption. They are coverage limits, not passing assertions.

### Prepared pair shared admission

After the full run, a runner-only admission change gives fully qualified prepared
two-fork batches a 5 GiB / 8-core reservation on supported resource-aware hosts.
The measured 2.712 GiB RSS-plus-swap cohort peak leaves conservative margin, not
a universal bound. Unknown environments/source shapes fall back to normal heaps
and exclusive two-worker admission. Heavy pairs and normal guards keep their
existing budgets; no queue limit, headroom, age/credit rule or short-job exception
changes. Both forks retain serial Jupiter and 1 GiB maximum heaps. The pair never
qualifies for aged-head backfill. Advisory tooling revision is 6.

The affected Python matrix verifies pair/one-worker/guard/forced fallback,
resource arithmetic and exact selection/graphics/ROM preservation. Engine inputs
and test commands remain those already exercised; a bounded real admission probe
checks the new scheduling path rather than repeating unchanged broad coverage.

Final Python-only policy safety verification passed 304 controls in 33.701s,
with zero skips. The two already-completed real Java observer controls were not
repeated. Actual preflight passed with Java 25, `LUA_BIN=/usr/bin/lua5.4`, and
PowerShell. A probe without that Lua environment failed before executing tests;
the corrected launch environment passed. Mirrors and `git diff --check` passed.

### Shared-admission observation and upstream reconciliation

At `712c6e63de0b27e43fd37ac5e778449cb9e85056`, a focused real invocation
selected the exact 26-class cohort after normal preparation. It acquired
resource-aware admission at 5 GiB / 8 cores, without the short-job exception.
All 268 cases passed with zero errors/skips, exactly matching the earlier case
inventory. Execution took 84.811s. No other job was running at acquisition; one
other worktree was running by completion. This observes genuine shared admission,
not a manufactured competing load or a claim of uncontended timing.
Source fingerprint: `055d94b0b57808c3c3096a16e8522457068d53ea804de669c98a57bb02442fd6`.

`develop` advanced to `e2ffdc430c2d330fa6e0069fd68e86c303625e43` during the cold
run, adding native-mod support and world/scene lifetime repairs. Merge
`7f97d5eca86fa4b9b2ec65149326c6490bcb4511` preserves both independent measurement
hazards in the briefing conflict. Relative to the updated base, this rollout
changes no production source, POM or workflow. The POM changes belong to upstream's
native packaging; the ordinary preparation contract remains valid.

The change-based dry plan still selects 4,260 ordinary candidates and guards;
116 qualify for prepared low-heap batches. Repeating the whole engine suite would
repeat unchanged coverage. Instead, reconciliation verifies the affected low-heap
cohort (including changed native-bundling and lifecycle consumers), every guard,
and all seven REV00-property consumer classes. Java guard algorithms and selection
remain those already exercised in the complete cold run. These focused checks do
not claim a new complete smoke pass at the reconciled SHA.

At the reconciled source, the prepared two-JVM cohort again completed 268 cases,
zero failures/errors/skips and no source drift. Its sole identity difference is
upstream's renamed native-bundling wiring test, with the revised assertion passing.
The first memory observer was invalid: a process with exit flags and zero virtual
size/RSS still reported two threads with missing `VmSwap`. The strict observer
rejected it; no memory qualification or peak is inferred from that report.

The queued normal `-Dtest` check selected `TestRetailRomRevisionIntegration`,
`TestRetailRevisionRendering`, `TestRetailDynamicWaterMotion`,
`TestSonic2RetailRevisions`, `TestKis2RetailRevisionIsolation`,
`TestHostEmeraldPresentation`, and `TestSonic2CompetitionLifecycle`, each by full
package-qualified name, with surfaceless EGL and runner-discovered absolute paths
to verified originals. All 48 cases passed, zero skips/errors/failures, including
the three explicitly skipped prerequisites in the broad run. This supplements
that run; its original 52 skips remain reported, with 49 other coverage limits.

A separate fresh normal JVM ran `maven_queue.py --normal -Dmse=off -Pguards
-Dopenggf.headless.gl=surfaceless-egl test -B` with the same absolute ROM properties.
All 809 cases completed with zero errors/skips; the three exact failure identities,
kinds, types and assertion messages match the baseline. Upstream moved two native
contract classes out of the guard naming pattern and added a build-tooling case;
the lower count is not an omitted lane or a passing reinterpretation of failures.

All 304 Python-only safety controls passed again on the reconciled tree in
33.751s, zero skips. Actual Java 25 / Lua 5.4 / PowerShell preflight passed again.
Temporary reports/logs and run diagnostics are consumed and removed before delivery.

A single confirmation at the same reconciled source fingerprint `f6a7ea37e4ec9f812465aeff439d213dee22b09a16a11e82f1db62a858de001b`
completed successfully with valid memory observation, no observer errors or source
drift, both test JVMs observed concurrently, and the identical 268-case inventory
with no failures/errors/skips. Invocation: 82.417s; normal preparation:
34.447s; queue wait: 59.913s. Sampled simultaneous
process-tree peak RSS plus swap: 3,002,634,240 bytes
(2.796 GiB). This confirmation resolves the rejected
observer run without weakening its live-thread rule or treating its missing sample
as passing evidence. Sampling limitations and conservative reservations still apply.


### Final integration with native TAS support

The final fetch also brought `6b2393b0dfb01f5056e5e6d0eb792c7aaa5acbc7`, the
completed native TAS mechanism rollout. Its own complete smoke and final
178-consumer/seven-audit verification are recorded in
[the TAS support record](../validation/2026-10-28-tas-engine-support.md).
Integration merge `dad5be2260f15139fb5ec1a29b18eb74306d6d4e` was conflict-free.
The updated source selects 4,281 ordinary candidates, of which the same 116
qualify for low-heap batching; selection remains complete. No testing helper or
POM changed in that last upstream addition.

A fresh queued normal-JVM check at `dad5be2260f15139fb5ec1a29b18eb74306d6d4e`
selected the union of the 26 batch-only cohort classes, the seven focused guard
and helper classes listed above, and the seven ROM-property consumers. All
**393 cases in 40 classes passed, zero failures/errors/skips**, command exit 0.
It used `maven_queue.py --normal -Dmse=off -Dtest=<exact FQCN union>
-Dopenggf.headless.gl=surfaceless-egl test -B`, with the verified absolute ROM
properties and controlled temporary reports. Those reports were consumed and
removed. This checks the compact mapper and exact bootstrap audit against the
new production sources, concurrent trace readers, affected cohort behavior and
ROM consumers after integration. Earlier broad timings and memory observations
retain their earlier source identities; they are not relabelled as observations
of the newer TAS source. Mirrors, syntax and whitespace checks passed, and the
integrated executable inputs match this tested tree. Only this evidence prose
is appended afterward; unchanged completed coverage is reused.

## Further smoke experiments: remove observation waste and test discrimination (2026-10-29)

The maintainer asked how much further routine smoke can shrink while still
catching obvious mistakes. The next bounded experiment starts at `develop`
`d425f50b2` in an isolated worktree, with a private configuration copy, Java 25,
Lua 5.4, original verified ROMs and explicitly display-free surfaceless EGL.
The initial preflight omitted `LUA_BIN` and failed before tests; the corrected
launch environment passed Java/Lua/PowerShell preflight. No release full suite,
admission-limit change or extra JVM worker experiment was submitted.

Three approaches remain useful: reduce setup/drawing/clock-wait waste while
retaining assertions; replace repeated/long products with short discriminating
boundary witnesses before moving their exhaustive form to manual audit; and
qualify more independent JVMs against actual memory and timing. This experiment
chooses the first. More JVMs do not remove unnecessary work, and an arbitrary
smaller selection cannot establish equal defect detection. A later few-minute
smoke target is an experimental objective, not a measured result or a profile
introduced by this change.

### Ending pixel experiment

`TestS3kEndingScenePriorityPixels` drew over 46,000 prefix frames across its
14 cases before checking a handful of declared native producer/publication
boundaries. The candidate reuses `RouteFrameDrawing`: every simulation tick and
pending render-thread task still runs, followed by a real checkpoint draw.
The original per-pixel ROM reconstructions, native state, overlap/footprint
requirements, widths, frame counts and final publication steps are unchanged.
The existing draw-every-frame property remains available. No production code,
POM, tag, fixture or category selection changes in the final candidate.

One additional control runs the declared 320-width reunion prefix with continuous
drawing and with skipped prefix drawing in fresh owned sessions. It compares
complete pixels, serialized native ending state and scroll. Its initial arbitrary
`>32` distinct-color assertion failed on the valid continuous image, which has
25 colors. That fixture mistake was diagnosed and replaced by a nonblank check;
all existing independent ROM-derived pixel assertions remain unchanged. The
first corrected 15-case invocation passed in 9.971 class seconds.

A temporary production mutation then made `getRenderHFlip()` return logical
facing. The original two reunion width rows both passed: the two values agree at
that checkpoint, so their pin alone does not prove independence. An earlier
mutation attempt used a nonexistent method and failed compilation with zero
executed cases; that was not detection evidence. The additional control now
forces logical facing right without stepping or changing native render flags,
checks that the native flip remains set, and restores logical facing in `finally`.
The same compiling mutation then ran the one control and failed exactly at
`native render flip must not follow logical facing`, zero errors/skips. This
provides discrimination against that specific plausible wrong implementation,
not a general mutation-score or whole-suite effectiveness claim. Temporary
production edits were restored byte-for-byte and the runtime/source fingerprint
was checked before the final clean positive run.

### Final positive verification and limits

After restoration and recompilation, all **15 cases passed, zero failures/errors/
skips**, with all original 14 identities retained exactly plus the contrast/control
case. Source fingerprint: `66f06861d225a95016ee7bba31471b1b38406e1fcd6d928209744dcc7f692b62`. The original
14-case baseline fingerprint is `65bdd97c56ebd5254621c8b030ced08b415f712802eb75f567e5ff8f926c1963`.

| Matched owner | Cases | Reported JUnit class seconds |
| --- | ---: | ---: |
| Original continuous prefix drawing | 14 | 41.751 |
| Checkpoint prefixes plus full-image/state/scroll and facing contrast control | 15 | 9.366 |

This owner improves by 32.385s / 77.6% including the added control. It is
one shared-host before observation and a final after observation, with the earlier
corrected positive also recorded above. There is no whole-suite speedup estimate:
parallel batch critical paths, JVM startup and other owners do not scale by this
percentage. Command wall was 148.654s before and 182.754s finally;
these include different compilation and admission waits and must not be treated
as a matched command-wall comparison. No current cold smoke timing is claimed.

Equivalent focused command uses `maven_queue.py --normal -Dmse=off
-Dtest=com.openggf.tools.TestS3kEndingScenePriorityPixels
-Dopenggf.headless.gl=surfaceless-egl test -B`, Java 25 / Lua 5.4 with display
variables unset and the existing runner's verified absolute original-ROM arguments.
Per-invocation reports and temporary log chunks were consumed and deleted; the
bounded task diagnostics are removed before delivery. Mutations never enter the
committed production tree. All source/fixture inputs stayed fixed within each
individual invocation; failed diagnostic/compilation runs are not passes.

The change-based dry plan selected common/tooling (647 ordinary candidates) and
guards. That broad run is disproportionate for a local test-prefix refactor:
no shared helper, production algorithm, POM, selector, fixture or scheduler changed;
all original cases, complete declared checkpoint comparison and the adversarial
control ran, followed by a clean positive after restoring the mutation. Focused
coverage is not a new broad smoke/full-suite pass and does not resolve the earlier
four inherited broad failures. The latest upstream addition during this experiment
changes README/features prose only; executable inputs remain comparable.

### Bounded next experiments

A read-only source audit identified these candidates, not measured improvements:

- `TestInfiniteSonic`'s terrain-only window-continuity and high-road checks can
  trial the existing `launchTerrain` owner instead of normal looping music.
  Retain all parameters, physical crossings and scroll assertions. Keep normal
  audio for audio/challenge/rewind cases and compare exact outcomes and cost.
- `TestSonicSurvivors`' 20,000-frame endless soak can trial a short production
  witness spanning the actual deadline, pickup expiry and renewed formation
  admission, with the soak retained manually. Disabling expiry and reinstating
  the old enemy cap must fail the retained controls before moving coverage.
- Its 38 route/leader arena rows can trial stopping after proven wall contact
  and landing instead of fixed 450-frame waits each way. Retain every row, both
  walls, floor/survival checks and the Death Egg exception; removed walls/floor
  must still fail. Do not assume arrival before measuring it.
- The 19-row `TestRouteFrameDrawing` matrix covers real characters, viewports,
  donors and zones; source inspection alone does not establish redundancy.
  Attribute boot/drawn-prefix/restore/reconstruction cost before changing rows.
- Additional JVMs require measured memory, actual overlap and identical outcomes.
  No three/four-fork safety or whole-suite speedup is established here.

The stopping criterion for a smaller smoke should be retained detection of
plausible failures, with cheap deterministic/adversarial checks and short real
boundaries, rather than an arbitrary case percentage. Expensive meaningful
integration checks need affected-change coverage; only low-incremental-value
products belong in the exceptionally manual lane. No new fast-smoke profile or
five/ten-minute guarantee is introduced by this experiment.
