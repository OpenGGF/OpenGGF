# LBZ event publication — 2026-11-16

## Ownership, ROM evidence and implementation

Base: `e676eb5086b7069c5e8ffdd0dbd33990d5c2bf5f` (develop).
This lane owns LBZ only; shared shake semantics and other zone files are unchanged.

- `LevelSetup` (sonic3k.asm:102231-102240) clears the native flag and both offsets.
  The game-wide Java boolean survives level reset and is not this ROM state.
- `LBZ2BGE_DeathEgg` (sonic3k.asm:111531-111546) tests the entire flag word
  to end `Event_LBZ2_DeathEgg` rumble. `loc_62942` / `loc_62A0A`
  (sonic3k.asm:129688-129696,129756-129763) instead test its high byte (`tst.b`
  at the big-endian word address). This width distinction was absent from the survey.
- Ship `loc_8D450` (sonic3k.asm:193052-193058) sets the flag's high byte;
  `loc_8D47C` (sonic3k.asm:193069-193080) clears the word on player release.
  Objects now route these writes through the LBZ runtime adapter to its event owner.
- `LBZ1_ScreenEvent` / `LBZ2_ScreenEvent`
  (sonic3k.asm:110931-110934,111353-111356) read the preceding offset.
  `ShakeScreen_Setup` (sonic3k.asm:104234-104255) advances the three native words
  after BG deformation. `Sonic3kLBZEvents` owns `S3kScreenShake` and the applied
  foreground copy; the native routine death/restart gate is used.
- `LBZ1BGE_Normal` (sonic3k.asm:111252-111282) runs setup, while
  `LBZ1BGE_DoTransition` (sonic3k.asm:111285-111332) does not. The producer
  preserves this distinction. Continuous sampling no longer belongs to rendering.
- Foreground copies are captured before launch changes the live camera.
  `LBZ2_DeathEggMoveScreen` (sonic3k.asm:112111-112187) owns `_unkEEA2` FG speed,
  `_unkEE9C` BG speed, `Events_bg+$02/$06` accumulators, `+$14` combined delta and
  target water. Its direct ship add is a non-consuming per-dispatch publication;
  skipped motion publishes zero. Native routine >= 6 gates movement and shake.
  BG deformation subtracts shake from the already-shaken FG copy, not the live Y.
- Cloud scrolling reads and adds $E00 at each deformation dispatch
  (sonic3k.asm:111803-111804,112045-112046), including held level counters. It is
  now an actual captured longword rather than an inferred first-frame anchor.
- `LBZ2_Deform` (sonic3k.asm:111632-111711) owns the normal table, waterline,
  background-camera and art-phase publication. `LBZ2_DeathEggDeform`
  (sonic3k.asm:111898-111950) retains HScroll words, `Events_fg_3` and `_unkEEA0`.
  These were additional state beyond the survey's shake defect.
- `LbzBackgroundEvents`, owned by LBZ events, retains those words, the cloud
  phase and frozen-fall continuation. `SwScrlLbz` only projects its completed output.
  Fresh-Level assembly configures the producer with existing ROM-loaded inputs;
  rebuilding a renderer cannot initialize or advance it.
- Launch accumulators, rider delta, detach scroll, water state and Death Egg
  terrain/window continuation retain their existing event/runtime writers. Their
  numeric state and the event-owned background continuation are composed with the
  event scalars and shake words in one same-build payload. The managed runtime
  registry adapter carries identity and no second byte payload. Act/character
  identity is framed explicitly because scalar codecs treat final fields as
  structural. Fresh-owner restore and registry rebinding preserve the numeric owner.
- No new background collision difference is introduced. Big Arm's camera-copy
  reader continues through the runtime adapter to the preceding completed FG copy.
  The shipped arithmetic/table behaviour is retained; no fixed-bug alternative is enabled.

## Witness and design choices

`TestS3kLbzScreenPublication.rendererRebuildDoesNotConsumeTimedShake` loads real
LBZ2, requests a native timed shake, steps production frames and rebuilds the
renderer in the same frame. On the base: **expected -5, observed 0**, one failure,
no errors/skips. It exercises the lost value rather than just the field layout.
Additional witnesses cover another zone's boolean not prolonging rumble, contiguous
shake-table indexing above $14, and atomic malformed/truncated payload rejection.

Rejected designs:

1. Keep the renderer's pending/consumed offset and enlarge its rewind record.
   This preserves the witnessed double-render consumption and splits publication ownership.
2. Put only shake in the event owner and retain renderer-owned cloud/table latches.
   Death Egg additive waves and frozen fall still require renderer lifetime for gameplay rewind.
3. Make each object compute offsets. The ROM objects write only the flag; this
   would duplicate frame-clock and death-gate decisions and violate the ROM order.

The existing numeric deformation tests now exercise the event-owned component;
provider assertions continue to require the public `SwScrlLbz` projection handler.
Old runtime-format migration tests were replaced by same-build rejection checks.

## Verification and limits

All Maven invocations use `python3 tools/testing/maven_queue.py -Dmse=off`.
Logs are disposable worktree-local `target/lbz-*.log`; no raw output is committed.
ROM discovery reported all configured properties found and `missing=none`.

- `-Dtest=TestS3kLbzScreenPublication test`: red witness, 1 failure / 0 errors / 0 skips.
- Same selection (`lbz-green.log`): compile error from a wrong interface method;
  repaired before proceeding; no tests executed.
- Same selection (`lbz-green2.log`): test compile errors from stale adapter API calls;
  repaired; no tests executed.
- Focused selection (`lbz-focused.log`): test compile errors from an inadvertently
  changed MGZ block in a shared test; that block was restored. No other zone edits remain.
- Focused selection (`lbz-focused2.log`): compile error from one stale removed field;
  repaired; no tests executed.
- Focused selection (`lbz-focused3.log`): 231 tests, 2 failures, 0 errors/skips.
  Both failures were changed unit assertions (provider type and prepared table index),
  corrected from the provider contract and ROM table. Real-load witnesses, schemas,
  native Big Arm bridge and consumer tests passed.
- `lbz-focused4.log`: 241 tests, 0 failures/errors/skips.
- `Q L test` (`lbz-local.log`): 551 tests, 10 failures, 0 errors/skips. Three
  fixture classes still wrote the retired boolean or expected same-frame shake.
  The same three classes passed on the base: 62 tests, 0 failures/errors/skips.
- `Q L,G test` (`lbz-local2.log`): 694 tests, 2 failures, 0 errors/skips. Two
  numeric tests still dispatched the producer when intending a renderer rebuild.
  Their unchanged baseline classes passed: 32 tests. Rebuild tests now read output.
- `Q L,G test` (`lbz-local3.log`): 694 tests, 0 failures/errors/skips.
- `Q L,G test` (`lbz-final-local.log`): 697 tests, 0 failures/errors/skips.
- `Q T test` (`lbz-traces.log`, `lbz-final-traces.log`): each ran all five classes,
  5 assertion failures / 0 errors / 0 skips, with full report JSON equal to base.
- Base `Q -Dtest=*Lbz*TraceReplay,SwScrlLbzTest,TestS3kAdditionalScrollProjection test`:
  37 tests / 5 trace failures / 0 errors / 0 skips. Both numeric classes passed.
- Qualified `Q L,G test` (`lbz-qualified-local.log`): 700 tests, 0 failures/errors/skips.
- Qualified `Q T test` (`lbz-qualified-traces.log`): 5 inherited failures, 0 errors/skips;
  every key in all five report JSON files remains identical to the common base.
- Strict citation check on every touched file: only three existing manager stale
  citations remain (`ShakeScreen_Setup`, `Change_Act2Sizes`, `Process_Sprites`).
  The exact base check reproduces them; new LBZ citations have no stale entries.
  `git diff --check` passes. An unrelated scddisasm checkout warning is inherited.

Command aliases above: `Q = python3 tools/testing/maven_queue.py -Dmse=off
-Dopenggf.headless.gl=surfaceless-egl`; `L = -Dtest=*Lbz*,*LBZ*,!**/trace/**`;
`G` adds the eight required S3K guard/adapter/projection classes below;
`T = -Dtest=*Lbz*TraceReplay`. All selectors are quoted shell arguments.
Base local selection was `TestLbzKnucklesInventoryRewind,TestSonic3kLbzLaunchSignals,
TestS3kLbz1KnucklesSequenceHeadless`. Initial red/compile attempts omitted GL.

Focused selection: `SwScrlLbzTest,TestSonic3kLbzRewindRoundTrip,TestLbzLaunchRuntimeState,
TestS3kLbzScreenPublication,TestS3kLbzEndBossChildLinksRewind,TestS3kAiz1SkipHeadless,
TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,
TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot,
TestS3kZoneRuntimeStateAdapters,TestSonic3kLbzBigArmTransitionBridge,
TestS3kAdditionalScrollProjection,TestLbz2RideCameoInstances,TestLbzFinalBoss2Instance`.
The 241-test run adds `TestS3kLbzFinalBoss2GraphRewind` and uses
`-Dopenggf.headless.gl=surfaceless-egl`.

This is bounded contract coverage, not zone certification. Existing act matrices
retain their full-route, viewport, donor and roster gaps. No categories/full suite
were run; the lead owns the change-based smoke selection.

Matched trace reports (common base and all three candidate runs):

| Class suffix | Frames | Errors | First error |
| --- | ---: | ---: | --- |
| KnucklesLbz2BigArm | 6309 | 263 | 76, rings |
| LbzZoneSlice | 46075 | 2091 | 30582, tails_y |
| KnucklesSuperLbzSegment | 24832 | 3035 | 0, y |
| SonicTailsLbzSegment | 32581 | 4940 | 1171, x |
| TailsFullChainLbzSegment | 42161 | 3281 | 0, y |

Equality compares every JSON key and full error ranges/values, not totals alone.
These fixtures remain red; no replay gate or comparison input was changed.
Time: queue lower bounds and Maven execution wall time are recorded separately
in local logs. Final local queue wait was at least 843s, Maven execution 1m51s;
trace queue wait at least 903s, Maven execution 1m19s. User wait is zero; uninstrumented time is unknown.
