# FBZ event publication — 2026-11-15

Base: `e676eb5086b7069c5e8ffdd0dbd33990d5c2bf5f` (`develop`).

## Owners and ROM evidence

- Events own the HScroll+$1FC longword. Outdoor `loc_52A70`
  (sonic3k.asm:108942-108960) reads it before adding $E00;
  `FBZ2_CloudDeform` (sonic3k.asm:109747-109807) reads it shifted right
  three before adding $8000. Indoor deformation freezes it.
- Outdoor motion objects write Events_bg+$08 (bob). Events consume that after
  objects; scroll projection reads the completed sample.
- `FBZ2_ScreenEvent` (sonic3k.asm:109375-109379) adds the existing shake offset
  to the camera copy. `FBZ2BGE_BossEvent` (sonic3k.asm:109690-109707)
  deforms clouds and computes collision differences before shake setup and
  before load-position normalization. Old-offset-first is correct, contrary
  to treating that survey observation as a defect.
- `loc_8642E` (sonic3k.asm:181489-181501) writes only the shake flag;
  `loc_702D6` (sonic3k.asm:148589-148596) conditionally clears only the flag.
  Neither writes an offset or phase. FBZ background init also starts the flag;
  the end-boss event controller writes it through the existing manager route.
- `S3kScreenShake` owns the three shake words. The phase getter is retained as
  a diagnostic sample of the level clock, never a second shake algorithm.
- Events publish ten cloud screen-coordinate pairs. Cloud objects read these;
  the scroll handler's cloud slots are derived presentation output only.
- Events retain collision intent/differences; the runtime adapter publishes them
  to collision consumers. The manager reconciles the same runtime owner after rewind.

## Witness and implementation

`TestFbzEventPublication` loads real outdoor FBZ1, admits an ordinary frame,
then rebuilds a renderer with a different draw clock. Before the change it
fails because the runtime payload changes at byte 8260 (1 failure, no skips).
It also checks boss cloud publication restore/rebuild and atomic rejection of
invalid cloud words/publication modes with real FBZ2 loading.

Gameplay now advances deformation; `SwScrlFbz` projects the completed camera,
drift, bob, offsets and shake. It cannot advance an accumulator. The same-build
runtime payload includes the complete scroll publication and cloud coordinates;
restore validates a detached probe before mutating the live owner. Ordinary
updates no longer retry ScreenInit/BackgroundInit; runtime installation owns
initialization. Seamless reload explicitly completes deformation before rebuilding.

Subboss objects use flag-only writes. Their bridge no longer offers offset/phase
writes. Manager `setScreenShakeActive`/`isScreenShakeActive` signatures and
semantics remain: set/clear continuous mode and test a nonzero flag. No other
zone or GameStateManager flag was changed. The explicit manager/event state
setter remains for tooling fixtures, separate from the object bridge.

No FixBugs conditional in these routines was changed; Java word/long wrapping
and ROM read-before-increment order are preserved.

## Designs considered

1. Renderer sampling with a frame guard: rejected; another draw clock changes gameplay (red witness).
2. Reconstruct clouds from live fields: rejected; post-CloudDeform normalization changes its inputs.
3. Chosen: event-owned scroll inputs and cloud output in one payload; renderer caches are disposable.

## Checks and limits

First delivery source: 406 tests, 8 inherited trace failures, 0 errors, 0 skips.
315 focused/guard/lifecycle cases and 82 route/checkpoint cases pass; the
complete-run replay passes. All 91 paired baseline cases have identical
failure messages/errors/skips; no missing cases or changed results. Lead owns broad smoke;
this lane does not run categories or the full suite. Native pixel capture and
new donor/wide route certification are outside this bounded publication change.

Original strict checks: three inherited manager citations (667/2294/2297),
confirmed unchanged at base; FBZ owners/audit pass (7 verified, 1 shorthand).
An unrelated Sonic CD checkout warning also remains.

Original runs (2026-11-15/16), no test errors/skips: red 1/1 failure;
focused 250/1 failure (diagnostic phase retained on death); extended attempt
compiled no tests (missing explicit-FG method, corrected); combined 406/14
failures (six unsigned-$FF00 restore failures corrected without weakening tests);
final 406/8 inherited failures; baseline route/trace 91/8 inherited failures.

Exact commands (all test invocations use the queue; `F` and `L` expand below):

```bash
F=TestFbzEventPublication,TestFbzEventsAct1,TestFbzEventsAct2,TestFbzZoneRuntimeState,TestFbzEventRewindRoundTrip,TestFbzFramePhaseOrdering,TestFbzScrollHandler,TestFbzBossCloudDeform,TestFbzBossCloudIdentity,TestFbzOutdoorBgMotion,TestFbzEventWriteSupport,TestFbz2SubbossRewind,TestFbzAct2Subboss,TestFbzBossGraphRewind,TestS3kAdditionalScrollProjection,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot
L=TestFbzAct1RomRuntimeLifecycle,TestFbzAct2RomRuntimeLifecycle,TestFbzEntryReloadResetMatrix,TestFbzActTransitionHeadless
R='*Fbz*TraceReplay,TestFbzAct1RouteHeadless,TestFbzAct2RouteHeadless,TestFbzNativeCharacterRoutes,TestFbzCheckpointRoutes'
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestFbzEventPublication test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$F" test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$F,$L" test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$R" test # detached baseline
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$F,$L,$R" test # twice: unsigned restore, then corrected
```

All ROMs found, none missing. Initial log redirect failed before Maven; fixed.
No categories/full-suite/exhaustive-audit opt-in or release-wide certification.

Inherited trace results, identical on baseline and final candidate:

| Class (all `TestS3k` prefix) | Errors | First divergence |
| --- | ---: | --- |
| KnucklesSuperFbz2SegmentTraceReplay | 34 | frame 152 -- rings mismatch (expected=189, actual=190) |
| KnucklesSuperFbz3SegmentTraceReplay | 455 | frame 6593 -- y_speed mismatch (expected=-0378, actual=-0478) |
| KnucklesSuperFbz4SegmentTraceReplay | 140 | frame 4348 -- player_animation_id mismatch (expected=0x0000, actual=0x001F) |
| KnucklesSuperFbzSegmentTraceReplay | 99 | frame 1610 -- rings mismatch (expected=31, actual=32) |
| SonicTailsFbzSegmentTraceReplay | 3913 | frame 7619 -- player_animation_id mismatch (expected=0x0000, actual=0x001F) |
| TailsFullChainFbz2SegmentTraceReplay | 798 | frame 1026 -- player_animation_id mismatch (expected=0x0005, actual=0x0006) |
| TailsFullChainFbz3SegmentTraceReplay | 289 | frame 4342 -- camera_y mismatch (expected=0x025E, actual=0x025B) |
| TailsFullChainFbzSegmentTraceReplay | 991 | frame 216 -- y mismatch (expected=0x0586, actual=0x0587) |

Timing, seconds (queue / Maven execution): red 270/86; initial focused 350/92;
baseline 433/175; compile failure 1161/51; first combined 590/199;
final combined 287/204. User wait: 0. Research/edit/command overhead: unknown, separately uninstrumented.

`git diff --check` passes; strict checks retain only inherited issues above.
Broad smoke/review and native-pixel, sparse-allocation, donor/wide qualification
remain with the lead or later work, outside this lane.

## Independent review revision — 2026-11-16

Reviewed head: `454fa3fbe5707f6fea80395c633b1b727a2a18da`.

1. FIXED: production `ModZoneEventProvider` witness throws ClassCastException.
   The redundant cast/publication is removed: synchronous reload installs and
   initializes the target events after camera offsets, already publishing there.
2. FIXED indoor projection: red BG Y 496 vs native 497 with retained shake 3.
   Projection retains the published camera-copy delta before deformation.
   Outdoor-specific suspicion rejected: native Y is $16+bob, independent of
   camera Y; the added outdoor case passes unchanged. Translated views also tested.
3. FIXED latent timed restore: red flag -256 vs expected countdown 13.
   Version 17 captures all three shared-owner words with captureTo/restoreFrom;
   malformed flag/offset/last-offset words are rejected atomically, no migration.
4. Strengthened: cloud actually changes across the two ordinary frames before
   restore; real events retain offset 3 after flag-clear, consume it in foreground
   Y, then publish next offset zero. Both assertions pass before implementation.

Record-only inherited reload mode/accumulator and sparse cloud-table gaps are
linked from [the bug catalogue](../../status/s3k-known-bugs.md#fbz-inherited-reload-and-sparse-cloud-publication-gaps--2026-11-16).
Red: 23 tests, 3 failures, no errors/skips. Exact revision commands follow;
`F`, `L`, `R` are defined above, all ROMs found, none missing.

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dtest=TestFbzWrappedEventTransition,TestFbzEventPublication,TestS3kAdditionalScrollProjection test
F="${F/TestFbzEventPublication,/TestFbzEventPublication,TestFbzWrappedEventTransition,}"
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$F,$L,$R" test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$R" test # fresh detached e676eb5086 baseline
```

Review validation: 411 tests, 8 inherited trace failures, 0 errors/skips;
320 focused/guard/lifecycle/wrapper and 82 route/checkpoint cases pass, as does
complete-run replay. All 91 paired results match the fresh 91/8 baseline.
Queue/execution seconds: red 570/38; baseline 615/176; candidate 655/207.
User wait 0; research/edit overhead unknown. Strict revision source citations pass. The bug
catalogue has 35 stale and one missing-file citation, identically reproduced on
baseline; no new stale/missing citations. These unrelated references are untouched.
