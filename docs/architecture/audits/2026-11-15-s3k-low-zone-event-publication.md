# S3K low-severity screen-event survey — 2026-11-15

Base: `e676eb5086b7069c5e8ffdd0dbd33990d5c2bf5f` (`develop`). Scope: CNZ, LRZ, SOZ, SSZ and DEZ; shared `S3kScreenShake` is unchanged. Source inspection is distinguished below from executed verification. This is not route certification or a claim that all five zones satisfy every HCZ rule.

## Common frame and shake contract

`LevelLoop` (sonic3k.asm:7918-7934) executes `SpecialEvents`, objects, `DeformBgLayer` (camera), then `ScreenEvents`. `ScreenEvents` (sonic3k.asm:102279-102302) copies camera words, dispatches foreground before background, and publishes vertical scroll. Engine equivalents are
`LevelFrameStep`'s pre-physics/object/camera/post-camera phases and `Sonic3kLevelEventManager.onUpdate`, followed by the native parallax dispatch. Objects therefore see the previous screen-event generation. `ShakeScreen_Setup` (sonic3k.asm:104234-104256) saves the old offset, suppresses
output without decrement at primary routine >=6, decrements positive flags, and indexes continuous shake with `Level_frame_counter & $3F`. `ScreenShakeArray` (sonic3k.asm:104277-104279) immediately precedes `ScreenShakeArray2` (sonic3k.asm:104280-104285): positive counts above $14
read into the continuous table. No FixBugs branch is changed. Follow-up retained for the lead: HPZ has water and, like LRZ and the DEZ final
arena, passes `getDead()` through to `S3kScreenShake.setup`. A drowning player with native routine >=6 but `getDead() == false` may therefore still get shake;
this was source-inspected, not exercised here. The lead addressed that gate
and the shared owner's Javadoc table citation in the follow-up below.

## CNZ — (c), demonstrated death-output defect; retain scalar ownership

`Sonic3kCNZEvents` keeps timer, prepared offset and applied offset as scalars; its update saves the applied value before ticking. `CnzZoneRuntimeState`
exposes that value; `SwScrlCnz` does not tick it. The local table has only 20
bytes. The observed cutscene writes are $14, not an above-$14 request:
`loc_65C78` (sonic3k.asm:134039-134055) and
`loc_65CAC` (sonic3k.asm:134056-134065), implemented by
`Cnz2CutsceneButtonInstance`. Do not infer a reachable overlength defect from
its public setter alone.
`CNZ2_ScreenEvent` (sonic3k.asm:107932-107956) applies the preceding offset;
`loc_52280` (sonic3k.asm:108070-108079) prepares the next one after deformation.
The production-frame `TestCnzScreenShakeDeath` failed with displayed shake 5
instead of zero after death. The local tick now suppresses output at the native
primary-routine >=6 gate without decrementing the flag. No other timing changes.
A persistent shared owner would also require replacing the scalar sidecar
capture contract; that is not a clear local net deletion.
Survey correction: the scroll handler also publishes `Events_bg+$10` and BG X
through `publishDeformOutputs`, corresponding to `CNZ1_Deform`
(sonic3k.asm:107711-107744). The boss writer is specifically
`CnzMinibossScrollControlInstance`, through `setBossScrollState`.
`pendingZoneActWord` exists in the event sidecar. The manager's four act-2
size accumulators and release counters are captured by `captureExtra` and
restored by `restoreExtra`; relocation has no demonstrated deletion benefit.
The three shake scalars are captured by `ZoneEventSchemaSidecar`.

## LRZ — (a), shared shake already owned; no change

`LrzZoneRuntimeState` owns `S3kScreenShake` plus the applied offset, captured
with background routines, fractional animation words, saved camera/redraw
state, rock windows and boss-act state. The manager advances it near the head
of its **post-camera** update (not pre-physics), preserving the preceding
sample for camera and scroll consumers.
`LRZ1_ScreenEvent` (sonic3k.asm:115245-115254) applies the old sample;
`loc_56C68` (sonic3k.asm:115365-115376) is a background setup tail.
Objects write the flag: big doors, crusher/child, miniboss and cutscene
boulder. Native examples are `loc_3461E` (sonic3k.asm:71010-71018),
`loc_42A68` (sonic3k.asm:88137-88162),
`loc_76164` (sonic3k.asm:157133-157145), and
`loc_786EA` (sonic3k.asm:160246-160257).
Survey correction: `SwScrlLrz` publishes deformation/Death Egg words during
native dispatch; its restore rebuild explicitly suppresses publication. It is
not wholly read-only. No new observable defect or shake deletion is established.

## SOZ — (b), replace the local routine with the shared owner

`SozEventState` now holds the three shake words in `S3kScreenShake` instead of
two independent ints. `Sonic3kSOZEvents.updateShake` delegates at exactly the
existing call sites. The ROM byte-table read and local algorithm are deleted;
runtime source has a net deletion. Capture/restore includes the shared owner's
last-offset word; this changes the same-build, unreleased payload length.
No migration or cross-build rewind compatibility is claimed.
Writers remain `SozAct1Events`, `SozMinibossInstance` and `SozEndBossInstance`:
`loc_55A9A` (sonic3k.asm:113652-113663) starts continuous rumble,
`sub_55D94` (sonic3k.asm:113917-113927) changes it to eight timed frames,
`loc_76C72` (sonic3k.asm:158080-158088) writes $14, and
`loc_77790` (sonic3k.asm:158955-158968) writes $14 on the end-boss footfall.
`SOZ1_ScreenEvent` (sonic3k.asm:113616-113619) reads the previous offset;
`loc_55BA2` (sonic3k.asm:113736-113740) prepares the next one.
Act 2's corresponding tail is `loc_565F4` (sonic3k.asm:114684-114694).
The previous routine already loaded all 84 contiguous ROM bytes; there was
no demonstrated above-$14 bug in SOZ. The replacement preserves that detail.
`SwScrlSoz` projects camera copies and captured sand/background state without
advancing shake. Sand/cork flags, collision and redraw machines remain with
`SozZoneRuntimeState`/`SozEventState` and their existing capture.

## SSZ — (a), shared shake already owned; no change

`SszLaunchState` owns the shared shake, applied value, launch fractions and
column arrays, captured by `SszZoneRuntimeState`. Act 2 uses the same owner.
`loc_572CE` (sonic3k.asm:115977-116009) starts continuous launch shake;
`SSZ1_ScreenEvent` (sonic3k.asm:115950-115954) reads the old offset and
`loc_578F0` (sonic3k.asm:116515-116523) runs setup after background drawing.
Survey corrections: current manager code calls screen init while preparing
initial objects, guarded by captured `screenInitApplied`; update retains a
fallback. The comment claiming an ordinary pre-physics fallback is stale:
no such call exists in the current manager. `SwScrlSsz` also advances background
drift/redraw state, guarded by captured `backgroundScrollFrame`; it is not
pure projection. This is retained ownership, not proof of a failed restore.

## DEZ — (a), shared shake already owned; no change

No ordinary act-1/2 screen-event shake consumer was found:
`DEZ1_ScreenEvent` (sonic3k.asm:118674-118686) and
`DEZ2_ScreenEvent` (sonic3k.asm:118776-118788).
Final arena `$1700` is a separate runtime. `DezFinalScreenEvents` publishes old
shake, runs background events, then advances the shared owner. Native
`sub_5A508` (sonic3k.asm:120409-120418) reads the old word and
`loc_5A74E` (sonic3k.asm:120626-120632) prepares the next one.
Flag writers include the boss footfall `loc_7FF6A`
(sonic3k.asm:171210-171218), hand `loc_80C60`
(sonic3k.asm:172428-172438), and arena signal `loc_8642E`
(sonic3k.asm:181489-181494). Objects write flags, not phase/offset.
`DezFinalBossZoneRuntimeState` captures shake with planes, redraw/window,
art and boss words; scroll consumers use that publication.

## Verification and limits

Commands use `python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=<selection>" test`.
Focused selection: `TestCnzScreenShakeDeath,SwScrlCnzTest,TestSwScrlCnz2P,TestCnzZoneRuntimeState,TestS3kCnzParallaxRegisteredRewind,TestSozScreenEvents,TestSozRuntimeState,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot`.
Result: **157 tests, 0 failures/errors/skips**. Both same-named loading classes run.
CNZ red witness: 1 test/1 failure (expected shake 0, actual 5); green also checks flag retention.
Initial SOZ fixture selected routine 0, which returns before setup: four new-case failures.
The same eight-class selection and copied witness on detached base reproduced all four
assertions (130 tests/4 failures/0 errors/skips); routine 4 fixes the fixture, not production.
Trace selection (all 13 CNZ/SOZ classes): `TestS3kCnzZoneSliceTraceReplay,TestS3kCnzTraceReplay,TestS3kKnucklesSuperCnzSegmentTraceReplay,TestS3kKnucklesSuperCnz2SegmentTraceReplay,TestS3kTailsFullChainCnzSegmentTraceReplay,TestS3kSonicTailsCnzSegmentTraceReplay,TestS3kSozCompleteRunTraceReplay,TestS3kKnucklesSuperSozSegmentTraceReplay,TestS3kKnucklesSuperSoz2SegmentTraceReplay,TestS3kTailsFullChainSozSegmentTraceReplay,TestS3kTailsFullChainSoz2SegmentTraceReplay,TestS3kSonicTailsSozSegmentTraceReplay,TestS3kSonicTailsSoz2SegmentTraceReplay`.
Candidate and base each: **39 tests/15 failures/0 errors/skips**. All 13 full JSON
payloads and all 39 test identities/outcomes/failure messages compare equal.
The 12 inherited replay failures are listed below (class suffix `TraceReplay` omitted).
Three additional inherited CNZ assertions fail: `traceReplayCnz2PreBalloonSlotPressureMatchesRomSequence`,
`traceReplayCnz2PathSwapConsumesSstSlotBeforeBalloonCluster`, and
`traceReplayCnz2FixedAirCountdownInitialCadencePreservesBubblerSlot` (frames17824/20584/17824).

| Replay class | Errors | First mismatch (frame / field) |
| --- | ---: | --- |
| TestS3kCnz | 3112 | 17322 / camera_x |
| TestS3kCnzZoneSlice | 4255 | 14616 / camera_x |
| TestS3kKnucklesSuperCnzSegment | 779 | 0 / x_sub |
| TestS3kKnucklesSuperCnz2Segment | 692 | 1600 / g_speed |
| TestS3kSonicTailsCnzSegment | 9250 | 2695 / tails_y_speed |
| TestS3kTailsFullChainCnzSegment | 1264 | 12940 / status_byte |
| TestS3kSozCompleteRun | 0 | all frames match |
| TestS3kKnucklesSuperSozSegment | 36 | 3216 / x |
| TestS3kKnucklesSuperSoz2Segment | 3388 | 99 / rings |
| TestS3kSonicTailsSozSegment | 3589 | 5104 / y_speed |
| TestS3kSonicTailsSoz2Segment | 4537 | 9349 / y_speed |
| TestS3kTailsFullChainSozSegment | 14 | 4496 / y |
| TestS3kTailsFullChainSoz2Segment | 1231 | 976 / rings |

Every wrapper found `s3k.rom.path`; `missing=none`. No categories/full suite run;
there is no category VERDICT for these focused wrapper invocations. Strict citation,
local link-target (573), whitespace and commit-policy checks pass. The unrelated
Sonic CD checkout/pin warning is retained; no reference checkout was changed.
Job waits (initial/red/green/traces/base-focus/base-traces): 379/389/774/834/565/980s; these overlap.
Maven execution in that order: 01:47 min / 38.544 s / 01:43 min / 03:09 min / 01:53 min / 03:09 min. User wait: 0; other time unmeasured.

## Follow-up: player-routine shake gate (2026-11-16)

Base: `61630491a0` (`develop`). Six `ShakeScreen_Setup` call sites passed
`getDead()` as the `Player_1+routine >= 6` gate: HPZ and LRZ in
`Sonic3kLevelEventManager`, SSZ1 launch and SSZ2 in `Sonic3kSSZEvents`, SOZ's
`updateShake` and `DezFinalScreenEvents`. A drowning sink installs routine 6
before the engine dead flag, so those zones kept counting down and publishing
shake. All six now read `NativePlayableRoutine.isAtOrBeyondDeathForNativeRead`,
as AIZ, LBZ, MGZ and MHZ already did. `S3kScreenShake` now cites
`ScreenShakeArray2` (sonic3k.asm:104280-104285).

Red witness `TestS3kShakeSetupPlayerRoutineGate` loads each zone, proves its
real call site consumes a timed $14 countdown, then repeats with Player 1 at
routine 6 and the dead flag clear. Before the fix all six cases kept counting
(expected $14, was $13); after it all pass. The same 33-class selection (zone
event tests, the eight architecture guards, the four required S3K tests and
the HPZ/HPZ2/LRZ/SSZ/SSZ1-cold/SOZ/DEZ trace replays) ran on this branch and on
`develop`: the five pre-existing trace failures are identical in identity,
totals and first divergence; nothing else fails. No route or drowning capture
was run.
