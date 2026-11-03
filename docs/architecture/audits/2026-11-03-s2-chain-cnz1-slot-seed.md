# S2 complete-run CNZ1 slot-machine seed investigation

Measured base: `origin/develop` at `e6a8b4a34e4c2962c2e10fc65b119eecb77150d4`.
Lane: `bugfix/ai-trace-s2-chain-cnz1`. This is a diagnosis, **not a runtime fix**.
The requested CNZ1 repair and post-fix validation remain open. No comparator
was suppressed or rebaselined. Experimental gameplay changes and probes were
removed before this documentation commit.

## Observed ring source and causal chain

The first missed ring is an ObjDC **slot-machine prize**, not a placed ring,
bumper award or lost ring. In `seg14_cnz1/aux_state.jsonl.gz`, slot21 is created
at trace row1850 at `(0740,01E0)`. It changes routine0→2 at row1876 at
`(06D7,01E0)`, when physics rings25→26. Slot30 is created at row1852 and awards
at row1878, rings26→27. Sonic remains captured at `(06C0,01E0)` until the cage
releases him at row1878. These are zero-based trace rows; row1876 has CSV frame
`0754`, gameplay counter`0755`, and V-blank counter`C0B0`.

ObjDC awards automatically on its 26th execution, without collision:
`ObjDC_Main (s2.asm:25470-25494)`. The cage creates rings with delay`$1A`:
`loc_2BD4E (s2.asm:59272-59311)`.

The engine has no prize children at row1876: its reels are still spinning.
Read-only comparator-row probes show its initial, stopped positions before
activation are `B600/D600/DA00`; native aux records `5300/6A00/4D00`.
Activation agrees at row1675: routine`0C`, speeds`33/32/33`, target word`0005`.
At row1849 native is inactive (`18`) with reward2; the engine remains in fine
adjustment (`10`). At row1876 its state is still `10`, positions
`0500/0100/C10C`, reward0, no prize children. Thus the first ring mismatch
exposes a much earlier machine-state disagreement.

An executed initialization stack records `CNZ_INIT vint=1b9b6`:

```text
CNZSlotMachineManager.routineInit
CNZSlotMachineManager.update
Sonic2ZoneFeatureProvider.updateAfterObjectExecution
LevelManager.updateZoneFeaturesAfterObjectExecution
ObjectManager.runAfterExecBeforePlacement
LevelFrameStep.execute
GameLoopTitleCardLifecycle.update
GameLoop.updateTitleCardMode
```

`SlotMachine_Routine1 (s2.asm:59438-59460)` seeds the indices from the low
V-int byte and rotates it right3 twice. `$B6` therefore produces precisely
`B6/D6/DA`; `$53` produces `53/6A/4D`. Runtime executes initialization during
locked title-card work. The native title-card wait loop runs objects/sprites
and PLC work, without the ordinary `DeformBgLayer` call:
`Level_TtlCard (s2.asm:4914-4925)`, and the later wait at s2.asm:5060-5066.
The separate pre-load deformation call and routine reset also matter
(s2.asm:4932,4983); simply choosing the first compared row is not proven correct.

### Executed transition-clock correction

A branch-site probe of `completeInterLevelVblankBudget` records the CNZ1
transition in this order:

```text
INTERLEVEL_CLOCK old=1B8C9 new=1B95B required=168 actual=22
CNZ_INIT vint=1b9b6
INTERLEVEL_CLOCK old=1B9F6 new=1B95B required=168 actual=323
```

Thus the harness first fills the movie-clock budget, lets title-card work
consume the clock and initialize the machine, then removes155 ticks after
that work. It cannot undo the stored reel indices. Subsequent compared V-int
counters can agree while a clock-dependent owner retains the earlier seed.
This is an executed ordering defect, not evidence that the reels should be
reseeded at comparator admission. The correct native initialization service
point/value within the load/title span remains unresolved. A safe follow-up
must pace the owning production work before it consumes the clock, preserve
the ROM's deformation/title ordering, and satisfy shared-harness validation.
Do not replace the initial indices with aux values, subtract a fitted clock
constant, or use the segment identity to select behavior.

### Isolated discriminating control

A temporary JUnit test drove the actual manager API, independently of the live
chain. For each seed, it called `update(seed+n)` for n=0..3 (initial roll),
then `activate()`, then `update(0xB95C+row)` for row1675 onward. Only the initial
seed changed; target selection and subsequent V-int inputs stayed identical.

| Initial V-int | Completion row | Reward |
| --- | ---: | ---: |
| B953 | 1849 | 2 |
| B9B6 | 1885 | 2 |

Both completion rows match their respective observed runs. Final control:
`TestCNZSlotMachineRng`,9 tests,0 failures/errors/skips. An initial control
assertion guessed1886 for engine completion (the cage observes completion on
its next update); it failed with observed1885 and was corrected. The test was
temporary and is not committed as a fixture-specific engine contract.

### Rejected experiment

The ROM cage's completed-reel branch enters payout in the same invocation:
`loc_2BDF8 (s2.asm:59315-59322)`. An experimental direct payout call preserved
normal odd-frame gating and child ordering, but **did not fix the seed**:
CNZ1 first error stayed1876 `rings`, total rose166318→319568, and the walk
changed from ownership loss to a missing level-advance boundary. This candidate
was rejected and reverted. Its larger total was not qualified under the lane's
error-list attribution gate; it must not be integrated.

## Inherited ARZ2 divergence

Baseline remains4442 errors, first row5512 `x`, ROM`2C71`, engine`2C72`.
Aux records Sonic first pushing capsule Obj3E slot17 at `(2C9C,04B1)` on this
row. Its native left edge is `2C9C-002B=2C71`. This identifies the relevant
contact, **not a proven engine-side cause**: no capsule collision branch probe
was run and no ARZ2 fix is claimed.

## Validation and limits

Environment: JDK25, Lua5.4, surfaceless EGL; DISPLAY, WAYLAND_DISPLAY,
JDK_JAVA_OPTIONS, MAVEN_ARGS and LD_LIBRARY_PATH unset; JAVA_TOOL_OPTIONS is
`-Dopenggf.headless.gl=surfaceless-egl`. ROMs are the existing absolute paths
under `${ROM_ROOT}/`: `s1.gen`, `s2.gen`, `s3k.gen`,
`kis2.gen`, `s1-r0.gen`, `s2-r0.gen`. No ROMs or links were changed.

Baseline command (parameterized only to omit the machine-local checkout path;
`ROM_ROOT` was the main checkout containing the existing ROMs; same environment
for all following commands):

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dsonic1.rom.path=${ROM_ROOT}/s1.gen \
  -Dsonic2.rom.path=${ROM_ROOT}/s2.gen \
  -Ds3k.rom.path=${ROM_ROOT}/s3k.gen \
  -Dkis2.rom.path=${ROM_ROOT}/kis2.gen \
  -Dsonic1.rev00.rom.path=${ROM_ROOT}/s1-r0.gen \
  -Dsonic2.rev00.rom.path=${ROM_ROOT}/s2-r0.gen test
```

Focused runs used the identical command plus
`-Dtest=TestS2CompleteEmeraldRunChain` (original probes, rejected candidate,
reverted probes), or `-Dtest=TestCNZSlotMachineRng` (seed-only controls).
All were queued, with one command session per run. Queue delays occurred;
separate delivery execution/wait totals were not measured and are unknown.

Full baseline:886 tests,10 failures,1 error,7 skips. The35 standalone S2
replay classes all passed. All120 testcases in the S2 trace package passed.
This is a profile result, not an ordinary/full-suite pass. No full post-fix
profile exists because no runtime fix is being delivered. Neither r7 nor
trace-segments was run: no shared runtime candidate is delivered. A future
shared scheduling/collision/loading fix must satisfy the brief's full
before/after profile requirements. No regression clearance is claimed for
the rejected experiment.

| Class | Baseline errors/frontier | Final unchanged-source evidence |
| --- | --- | --- |
| TestS2CompleteEmeraldRunChain, ARZ2 | 4442 /5512 x | Same in reverted probes |
| TestS2CompleteEmeraldRunChain, CNZ1 | 166318 /1876 rings; incomplete | Same in reverted probes |
| TestS2EhzHalfpipeRoundTripChain | 2 art-gap axes | No runtime change; no repeated profile |
| TestKis2CompleteEmeraldRunChain | 10 axes | No runtime change; no repeated profile |

### Every S2 chain axis at baseline and after probe reversion

The final reverted probe reproduces the full baseline chain assertion message
exactly after normalizing the local report path. CNZ1 is again166318 errors,
first1876 rings, incomplete. The same15 axes remain; CNZ1's incomplete comparator report is not a separate
segment-physics axis in this walk. No axis is fixed.

- [walk-failure] java.lang.AssertionError: segment 20 lost production ownership before source closure (mode=TITLE_CARD, level=LevelIdentity[loadGeneration=16, progressionZone=3, romZone=12, act=0], BK2 cursor=120236)
- [segment-physics] segment 19 of s2-sonic-tails-complete-emeralds diverged: 4442 physics comparator errors, first non-camera mismatch at frame 5512 field x rom=0x2C71 engine=0x2C72
- [dynamic-art-gap] shared dynamic-art gap comparison failed for ss -> seg2_ehz1
- [dynamic-art-gap] shared dynamic-art gap comparison failed for ss_2 -> seg3_ehz1
- [dynamic-art-gap] shared dynamic-art gap comparison failed for ss_3 -> seg4_ehz1
- [dynamic-art-gap] shared dynamic-art gap comparison failed for seg4_ehz1 -> seg5_ehz2
- [dynamic-art-gap] shared dynamic-art gap comparison failed for ss_4 -> seg6_ehz2
- [dynamic-art-gap] shared dynamic-art gap comparison failed for ss_5 -> seg7_ehz2
- [dynamic-art-gap] shared dynamic-art gap comparison failed for seg7_ehz2 -> seg8_cpz1
- [dynamic-art-gap] shared dynamic-art gap comparison failed for seg8_cpz1 -> seg9_cpz2
- [dynamic-art-gap] shared dynamic-art gap comparison failed for ss_6 -> seg10_cpz2
- [dynamic-art-gap] shared dynamic-art gap comparison failed for seg10_cpz2 -> seg11_arz1
- [dynamic-art-gap] shared dynamic-art gap comparison failed for ss_7 -> seg12_arz1
- [dynamic-art-gap] shared dynamic-art gap comparison failed for seg12_arz1 -> seg13_arz2
- [dynamic-art-gap] shared dynamic-art gap comparison failed for seg13_arz2 -> seg14_cnz1

### Standalone S2 replay classes

All were measured on the updated base. There is no post-fix column to claim;
the final delivery changes documentation only.

| Class | Baseline | Errors / first row |
| --- | --- | --- |
| TestS2Arz1CompleteEmeraldsSegmentTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Arz2LevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2ArzLevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Cnz2LevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2CnzLevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Cpz1Seg8CompleteEmeraldsSegmentTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Cpz2LevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Cpz2Seg10CompleteEmeraldsSegmentTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Cpz2Seg9CompleteEmeraldsSegmentTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2CpzLevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2DezEndingLevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Ehz1Seg1CompleteEmeraldsSegmentTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Ehz1Seg2CompleteEmeraldsSegmentTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Ehz1Seg2HalfpipeSegmentTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Ehz1TraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Ehz2Seg6CompleteEmeraldsSegmentTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Htz2LevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2HtzLevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Mcz2LevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2MczLevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Mtz2LevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Mtz3LevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2MtzLevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2Ooz2LevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2OozLevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2SczLevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2SpecialStage1TraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2SpecialStage2TraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2SpecialStage3TraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2SpecialStage4TraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2SpecialStage5TraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2SpecialStage6TraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2SpecialStage7TraceReplay | 1 passed, 0 skipped | 0 / none |
| TestS2SpecialStageTraceReplay | 2 passed, 0 skipped | 0 / none |
| TestS2WfzLevelSelectTraceReplay | 1 passed, 0 skipped | 0 / none |

### Nonpassing baseline cases

- `TestTraceBaselineRegression#knownRedTraceFrontiersDoNotRegress`: skipped.
- `TestTraceDataAuxSchemaPerformance#measuresPostLoadMissingSchemaQueriesOnLargestFixture`: skipped.
- `TestKis2Ehz1TraceReplay#replayMatchesTrace`: failure.
- `TestKis2CompleteEmeraldRunChain#ehz1ThroughAllEmeraldsToDeathEgg`: failure.
- `TestS1CompleteEmeraldRunChain#ghz1ToScrapBrainAcrossEverySpecialStage`: failure.
- `TestS2CompleteEmeraldRunChain#ehz1ToDeathEggAcrossEverySpecialStage`: failure.
- `TestS2EhzHalfpipeRoundTripChain#ehzHalfpipeRoundTrip`: failure.
- `TestS3kBonusRoundTripChain#gumballRoundTrip`: skipped.
- `TestS3kBonusRoundTripChain#pachinkoRoundTrip`: skipped.
- `TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds`: error.
- `TestTraceRunActivePayloadPerformance#allSegmentsStayWithinFixedDescriptorAndInstalledCaps`: skipped.
- `TestTraceRunActivePayloadPerformance#installedRootModelIncludesTheRealPreparedLaunchGraph`: skipped.
- `TestTraceRunDescriptorPlanningPerformance#compactDescriptorsRetainLessHeapWithoutOwningEagerPayloads`: skipped.
- `TestS3kCnzTraceReplay#replayMatchesTrace`: failure.
- `TestS3kCnzTraceReplay#traceReplayCnz2PreBalloonSlotPressureMatchesRomSequence`: failure.
- `TestS3kCnzTraceReplay#traceReplayCnz2PathSwapConsumesSstSlotBeforeBalloonCluster`: failure.
- `TestS3kCnzTraceReplay#traceReplayCnz2FixedAirCountdownInitialCadencePreservesBubblerSlot`: failure.
- `TestS3kKnucklesLbz2BigArmTraceReplay#replayMatchesTrace`: failure.

The7 skips are opt-in baseline/performance audits and absent S3K gumball and
pachinko run directories; no S2 replay skipped for a missing ROM.

## Suggested frontier-log entry (lead-owned log not edited)

S2 CNZ1 diagnosis on e6a8b4a34:166318 errors first1876 rings is ObjDC slot
payout (slot21; native awards1876, second slot30 at1878). Actual reel seed
is wrong before activation: engineB6/D6/DA vs native53/6A/4D. Executed init
stack is inside locked title-card processing; the harness subsequently rewinds
the clock1B9F6→1B95B after initialization at1B9B6, retaining the old seed.
Isolated actual-manager control
reproduces native completion1849 from seed53 and engine completion1885 from
seedB6, both reward2. Direct payout-fallthrough experiment does not clear the
frontier and was reverted. No runtime fix or closure of segment20 ownership
failure is claimed. Baseline full trace-replay886/10fail/1error/7skip;35 S2
standalone replay classes green, halfpipe2 axes and KiS2 chain10 axes inherited.
ARZ2 remains4442 first5512 x at capsule side contact; engine-side cause unproven.
