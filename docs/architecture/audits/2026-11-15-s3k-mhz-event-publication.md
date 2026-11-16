# MHZ event publication — 2026-11-15 (review corrections 2026-11-16)

Base: `e676eb5086b7069c5e8ffdd0dbd33990d5c2bf5f`; reviewed head: `3607748c9d`.

## ROM ownership and change

- Full flag-write search: MHZ's only specific writer is `loc_76164` (sonic3k.asm:157133-157144),
  writing `$14` at the end-boss escape threshold. The miniboss, ordinary objects,
  events and seamless transition do not write it. `LevelSetup` (sonic3k.asm:102239-102240) clears it.
- The survey's disconnected writer was real: the boss wrote a game-wide boolean
  with no MHZ consumer; it now requests the zone's ROM flag. The shared boolean remains.
- `MHZ2_ScreenEvent` (sonic3k.asm:112488-112492) adds the preceding offset to the camera copy.
  `ShakeScreen_Setup` (sonic3k.asm:104234-104255) prepares the next offset after deformation.
- `MHZ1_BackgroundInit` (sonic3k.asm:112310-112317) initializes the loop words;
  `Adjust_BGDuringLoop` (sonic3k.asm:104157-104179) advances them in background events.
  `MHZ1_BackgroundEvent` (sonic3k.asm:112320-112365) retains logical history across Load_Level.
- `MHZ_Deform` (sonic3k.asm:112368-112398) publishes BG/animation bands without shake cancellation;
  `sub_554B8` (sonic3k.asm:113164-113202) cancels shake before the ratio and restores only BG X.
  The old renderer applied that cancellation to ordinary MHZ too.
- `loc_555D2` (sonic3k.asm:113293-113307) publishes pillar helper X for the next object pass.
  This renderer-owned gameplay write was an additional survey finding.

Events now own loop history, deformation/animation words, collision helper X and
`S3kScreenShake`; runtime reads that owner and scroll only projects. A framed,
validated same-build payload restores scalar publication plus all three shake words
atomically. No old-format migration, trace gameplay hydration or shared shake API change.
The ramp's DIVU remainder and shipped loop arithmetic are retained; no fixed-bug branch selected.
The original real-load rebuild witness failed on four runtime bytes at base and passes here.

## Designs rejected

1. Renderer-owned accumulator plus a better snapshot: rebuilds still replace gameplay state.
2. Runtime publication plus a separate event-phase sidecar: splits one dispatch/rewind boundary.
3. Chosen: event-owned composition, runtime read adapter, projection only, following HCZ.

## Independent review disposition

1. **FIXED:** double application confirmed in real ordinary frames. Natural red witness
   `ordinaryShakeMovesTheRenderCameraAndSpriteDrawExactlyOnce` observed camera and render bounds
   15398 instead of 15403, and actual playable draw tile x=150 instead of 145.
   Removed the handler's separate shake channel; native copied-camera application remains.
2. **FIXED coverage:** restored grounded Y=$400 routine 8->$10 same-dispatch setup beside
   airborne Y=$450 negative case. The previous audit was wrong to call the original assertion
   incorrect: its grounded player enters loc_5528A, not the early airborne wait return.
3. **FIXED coverage:** real `LoopCarry`/`transferAfterTargetInit` test retains nontrivial $200
   loop history, checks hScroll and rebuild immutability; removed reflection-only act rebase.
   The pre-review DMA test already drove a real handoff; this adds stronger loop/output
   assertions. Deform checks actual handler hScroll; boss threshold checks
   its injected real zone flag owner. Each detects a deliberate omission (four red mutations).
4. **FIXED:** exact label-start assertions first failed, then passed after correcting both
   production comments and this audit to loc_76164=157133 and loc_555D2=113293.

**RECORDED, unchanged:** the carry adds one frame of movement when the camera advanced since
its previous sample. Base `SwScrlMhz.captureForActTransition` carries `loopActualCameraX+offset`;
the new LoopCarry retained that behavior. ROM writes the current rebased copy into Events_fg_0
in `MHZ1_BackgroundEvent` (sonic3k.asm:112320-112354), specifically lines 112352-112354,
making its following loop delta zero.
No timing correction is claimed here; the handoff witness isolates retention with no extra movement.

## Exact Maven commands and results

Output redirections to `target/mhz-*.log` omitted. Baseline commands ran in detached,
clean cowtrees at the base SHA, with independent target trees. All runs found verified ROMs
(`missing=none`); all completed test selections had zero errors/skips. No OOM occurred.

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestS3kMhzScreenPublication test
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kMhzScreenPublication,TestMhzComposedRewindState,TestSonic3kMHZEvents,SwScrlMhzTest,TestSonic3kMhzRuntimeStateRegistration,TestMhzRewindFidelitySweep,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot,TestS3kMhzAuthoredRoute,TestS3kMhzAct2AuthoredRoute,TestMhzCheckpointRoutes,TestMhzBossObjects,TestS3kMhzPatternAnimation' test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kMhzScreenPublication,TestMhzComposedRewindState,TestSonic3kMHZEvents,SwScrlMhzTest,TestSonic3kMhzRuntimeStateRegistration,TestMhzRewindFidelitySweep,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot,TestS3kMhzAuthoredRoute,TestS3kMhzAct2AuthoredRoute,TestMhzCheckpointRoutes,TestMhzBossObjects,TestS3kMhzPatternAnimation,*Mhz*TraceReplay' test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kMhzAuthoredRoute,TestS3kMhzAct2AuthoredRoute,*Mhz*TraceReplay' test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dtest=TestSonic3kMHZEvents test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dtest=TestS3kMhzScreenPublication#ordinaryShakeMovesTheRenderCameraAndSpriteDrawExactlyOnce test
python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestSonic3kMHZEvents#act2BackgroundEventRoutine8FallthroughRunsShakeSetupInTheSameDispatch+realActHandoffCarriesLoopHistoryAndRebuildsTheScrollOutput,TestSonic3kMhzRuntimeStateRegistration#bossAreaDeformCancelsShakeBeforeRatioAndRestoresItAfterward,TestMhzBossObjects#mhzEndBossDefeatDashThresholdStartsFadeShakeAndTimerStop' test
```

| Run | Tests / failures | Queue seconds | Maven seconds | Qualification |
| --- | --- | ---: | ---: | --- |
| Original base rebuild red | 1 / 1 | 336 | 95 | Expected runtime publication mutation |
| Initial compile-only | none | 0 | 56.671 | Failed import/getter type; corrected |
| Original focused | 388 / 9 | 520 | 143 | Five corrected issues + four baseline routes |
| Original candidate focused + traces | 410 / 26 | 846 | 177 | 25 baseline failures + corrected test setup |
| Original baseline routes + traces | 25 / 25 | 983 | 159 | Same identities/assertions as candidate |
| Original corrected event rerun | 64 / 0 | 492 | 37.571 | All event tests passed |
| Review natural red | 1 / 1 | 597 | 37.611 | Actual camera/bounds/sprite double shake |
| Review four omission controls | 4 / 4 | 643 | 104 compile + 3.557 test | Expected failures; all mutations restored |
| Review fresh baseline routes + traces | 25 / 25 | 805 | 149 | Fresh comparison control |
| Review first full focused + traces | 413 / 26 | 707 | 185 | 25 baseline failures + misplaced new boss assertion |
| Review corrected full focused + traces | 413 / 25 | 131 | 122 | All 25 failures match fresh baseline; 388 pass |

The omission controls disabled only same-dispatch setup, handoff carry, BG scroll-word output,
and the boss zone request. These qualify new coverage of already-correct behavior; they are
not natural failures of the reviewed head. The production defect has its separate natural red.
The first full run found my new pre-threshold assertion was too late: spawn+$C0 plus the
first four-pixel move already reaches $44D0. It now checks zero before the first update,
then $14 immediately after it. No production behavior changed for this test correction.

## Trace and inherited failures

Original comparison: 21 reports identical in counts, frame/field coverage, verification groups,
complete grouped errors/warnings and replay gates: 9,397 grouped errors on each side (not raw
row/field mismatch counts). Review final comparison: all 23 baseline test-class assertions and all 21 trace reports
match; zero new/worse mismatches. Per-class base = original and corrected candidates:

| Replay class | Grouped errors | First frame / field |
| --- | ---: | --- |
| TestS3kMhzZoneSliceTraceReplay | 3191 | 6958 / rings |
| TestS3kKnucklesSuperMhz2SegmentTraceReplay | 901 | 1120 / y |
| TestS3kKnucklesSuperMhz3SegmentTraceReplay | 568 | 1483 / y_speed |
| TestS3kKnucklesSuperMhz4SegmentTraceReplay | 142 | 1795 / y_speed |
| TestS3kKnucklesSuperMhz5SegmentTraceReplay | 17 | 256 / rings |
| TestS3kKnucklesSuperMhz6SegmentTraceReplay | 377 | 168 / y_speed |
| TestS3kKnucklesSuperMhzSegmentTraceReplay | 25 | 0 / y_speed |
| TestS3kSonicTailsMhz2SegmentTraceReplay | 614 | 1220 / tails_y_speed |
| TestS3kSonicTailsMhz3SegmentTraceReplay | 144 | 914 / camera_y |
| TestS3kSonicTailsMhz4SegmentTraceReplay | 11 | 1021 / routine |
| TestS3kSonicTailsMhz5SegmentTraceReplay | 30 | 759 / x |
| TestS3kSonicTailsMhz6SegmentTraceReplay | 529 | 433 / camera_x |
| TestS3kSonicTailsMhz7SegmentTraceReplay | 11 | 2107 / routine |
| TestS3kSonicTailsMhz8SegmentTraceReplay | 13 | 1672 / player_mapping_frame |
| TestS3kSonicTailsMhz9SegmentTraceReplay | 1756 | 865 / tails_y |
| TestS3kSonicTailsMhzSegmentTraceReplay | 11 | 1276 / routine |
| TestS3kTailsFullChainMhz2SegmentTraceReplay | 233 | 3371 / y |
| TestS3kTailsFullChainMhz3SegmentTraceReplay | 192 | 1156 / camera_x |
| TestS3kTailsFullChainMhz4SegmentTraceReplay | 318 | 2319 / x_sub |
| TestS3kTailsFullChainMhz5SegmentTraceReplay | 304 | 218 / y_speed |
| TestS3kTailsFullChainMhzSegmentTraceReplay | 10 | 1389 / routine |

All four inherited route issues: MHZ1 reaches only `pulley` (five other required markers absent);
MHZ2 parameter 1 reaches no required markers; parameters 2/3 die at input 36000/26152.
Original assertions/identities match baseline; MHZ1 expected-set display order alone differs.

Whole-PR strict citations previously found three unchanged manager HPZ/CNZ stale references
(120901, 180510-180515, 180666-180723), outside MHZ scope. MHZ-only strict check passed;
review touched-file strict check passes (56 ok, 9 near, 7 unverifiable; no stale). Unrelated scddisasm checkout/pin warning remains.
Queue and Maven times are separate and overlap between trees. User wait zero; research/editing
and unmeasured host overhead unknown. Logs are disposable target outputs. Lead owns smoke and
independent review; no category/full-suite run or visual pixel certification is claimed. Existing
route/team/donor/viewport gaps remain; no trace frontier was chosen/moved, so frontier log unchanged.
