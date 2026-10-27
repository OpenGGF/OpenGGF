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
