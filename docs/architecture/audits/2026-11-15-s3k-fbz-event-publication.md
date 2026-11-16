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

1. Keep renderer sampling with a frame-number guard: rejected because an extra
   draw with another clock still changes gameplay, as the red witness proves.
2. Reconstruct clouds from current live fields on restore: rejected because
   boss-load normalization follows CloudDeform and may have changed its inputs.
3. Chosen: event-owned immutable scroll inputs and concrete cloud output in the
   existing validated payload; renderer caches are disposable projections.

## Checks and limits

Final source: 406 tests, 8 inherited trace failures, 0 errors, 0 skips.
315 focused/guard/lifecycle cases and 82 route/checkpoint cases pass; the
complete-run replay passes. All 91 paired baseline cases have identical
failure messages/errors/skips; no missing cases or changed results. Lead owns broad smoke;
this lane does not run categories or the full suite. Native pixel capture and
new donor/wide route certification are outside this bounded publication change.

Strict citation checking of every touched file found three inherited stale
references in the shared event manager (lines 667, 2294, 2297); `git show` of
base confirms all three are unchanged. They concern HPZ/CNZ, outside this lane.
The separate FBZ-owner/audit check passes (7 verified, 1 unverifiable shorthand).
The tool also warns about an unrelated local Sonic CD disassembly checkout.

Validation ran across midnight into 2026-11-16. Initial failures were retained:
- Red witness: 1 test, 1 expected failure, no errors/skips.
- Initial focused selection: 250 tests, 1 diagnostic-phase failure, no errors/skips;
  fixed by retaining the diagnostic sample when death skips the table read.
- Extended focused attempt: compilation failed (missing explicit-FG interface
  method), 0 tests executed; method added.
- First combined selection: 406 tests, 14 failures, no errors/skips. Six new
  failures (two continuous-shake clock cases and four checkpoint replay cases)
  exposed an unsigned $FF00 restore. Casting to signed word fixes restoration
  of continuous mode; all six assertions remain unchanged and now pass.
- Final combined selection: 406 tests, 8 inherited failures, no errors/skips.
- Baseline route/trace selection: 91 tests, 8 failures, no errors/skips.

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

All runs reported every discovered ROM property found and none missing. The
first red-log redirect failed because target did not exist; no Maven launched
on that attempt. Creating target resolved it. No category/full-suite run or
exhaustive-audit opt-in was used. No release-wide certification is claimed.

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
final combined 287/204. User wait: 0. Research/edit/command overhead was not
instrumented and remains unknown, separate from these measured intervals.

`git diff --check` passes. Strict citation command was
`python3 tools/disasm/disasm_citations.py check --strict <all staged files>`;
the shared-manager failures above remain inherited. Rechecking FBZ owners and
this audit alone passed. Broad smoke, independent review, and new native-pixel,
sparse-allocation, donor/wide route certification remain outside this lane.
