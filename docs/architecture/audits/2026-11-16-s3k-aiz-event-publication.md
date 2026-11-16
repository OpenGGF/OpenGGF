# AIZ screen event publication — 2026-11-16

Common base: `e676eb5086b7069c5e8ffdd0dbd33990d5c2bf5f` (develop).
Initial reviewed head: `d4c08045a44d9000861d5baf8b14e9f300a914c0`, PR #341.
The lead's independent review found no blockers and confirmed the level clock,
seamless AIZ1→AIZ2 word preservation and atomic restore. This audit incorporates
its four corrections and two record-only findings. Original attempt logs remain
under `target/`; the original command/result catalogue is retained in
[the reviewed audit](https://github.com/OpenGGF/OpenGGF/blob/d4c08045a44d9000861d5baf8b14e9f300a914c0/docs/architecture/audits/2026-11-16-s3k-aiz-event-publication.md).

## ROM owners, frame order and initial witness

- `AizShipBombInstance` → `S3kAizEventWriteSupport` → AIZ events writes only
  `$10`: `AIZShipBomb_Drop` (sonic3k.asm:105458-105474).
- `DynamicWaterHeight_AIZ2` (sonic3k.asm:8680-8727) writes `-1` after screen
  events; `Obj_6E6E` (sonic3k.asm:8731-8739) later clears the same flag.
  The allocated timer has a separate ROM lifetime from the three shake words.
- `AIZ2_ScreenEvent` (sonic3k.asm:104923-104927) consumes the preceding
  offset; `loc_50260` (sonic3k.asm:105208-105216) and `loc_502C2`
  (sonic3k.asm:105247-105249) prepare the next offset after BG deformation.
  `S3kScreenShake` owns flag/offset/last-offset without API or table changes.
- `ShakeScreen_Setup` (sonic3k.asm:104234-104255) holds the flag and writes
  zero for stored Player_1 routine >= 6. Timed reads continue into the second
  table above $14. The real-load red witness wrote $1E: baseline returned zero
  instead of three (1 test, 1 failure, no errors/skips). It now passes, including
  stored routine eight while `getDead()` remains false.
- AIZ events own the canopy origin; `SwScrlAiz` projects it without anchoring
  on a reader call. `AIZ1BGE_Intro` (sonic3k.asm:104621-104636) hands off to
  normal deformation; `AIZ1_Deform` (s3.asm:70365-70378) reads then increments
  the canopy accumulator. The established frame-derived formula is retained.
- Battleship smooth X, forest-loop camera generation, redraw counters and ship
  Y remain event-owned. No AIZ BG collision-difference publisher was found.
  Existing right-wall deep-probe and non-colliding falling-bomb rules remain.

## Review dispositions

1. **Fixed — shared water policy.** The review correctly identified concrete
   AIZ type checks and runtime registry lookups in shared WaterSystem. Those
   code references are removed. `WaterShakeTimerLifecycle` is an internal
   semantic extension of the existing `DynamicWaterHandler`; it leaves the
   exported Mod API unchanged. WaterSystem invokes start/expiry edges at the
   same positions; Aiz2DynamicWaterHandler alone translates them to flag writes.
   Start remains after ScreenEvents (`LevelLoop`, sonic3k.asm:7916-7931).
   A real-load witness counts exactly 179 continuous setups, then the clear flag;
   a separate witness proves an intervening bomb is not masked by the timer.
   Other handlers implement neither callback and retain their existing behavior.
2. **Fixed — real-intro coverage.** Both earlier publication tests skipped the
   intro. A new witness now traverses the real Sonic+Tails intro, independently
   observes its first normal-deformation dispatch and pins the origin to that
   dispatch's `Level_frame_counter`. It asserts no anchoring during intro, no
   later re-anchoring, and preservation by the composed rewind payload.
3. **Fixed — unreachable rollback.** The two assertions described rollback but
   actually exercised prevalidation. Tests now name and assert rejection before
   state restoration. `restoreAizSidecar` no longer catches/rolls back: whole-
   manager prevalidation decodes the same bytes with the same scalar codecs on
   an isolated owner. Production apply has no target-dependent service lookup,
   and facade discard consists only of null assignments. A programming failure
   therefore propagates rather than masquerading as malformed input. The two
   retired catch fingerprints are removed from the broad-catch guard inventory.
4. **Fixed — independent last offset.** Two real shared setup calls yield flag
   12, current offset +4 and last offset -4. The new test restores over different
   target words and independently asserts all three, plus exact payload bytes.
5. **Recorded — sprite translation.** `Translate_Camera2ObjX`
   (sonic3k.asm:105504-105508) subtracts last offset and adds current offset.
   No AIZ object reads the shared last-offset word. Bombs submit world Y through
   PatternSpriteRenderer; LevelRenderer.prepareSpritePresentation passes
   `camera.getYWithShake()` to SpritePresentation, which subtracts that camera
   from pieces. Thus the bomb **does receive global presentation shake** even
   though its stored Y omits the native translation terms. GraphicsManager.flush
   likewise uses the shaken camera. The battleship emits no sprite commands:
   it is a BG plane effect, with SwScrlAiz composing shake and ship Y separately.
   This records the path, not proof of full native translation parity; unchanged.
6. **Recorded — fire branches.** `AIZ2BGE_FireRedraw`
   (sonic3k.asm:105082-105097) and `AIZ2BGE_WaitFire`
   (sonic3k.asm:105100-105171) can return without ShakeScreen_Setup; the engine
   calls setup every AIZ2 frame. With flag/offset zero this is invisible. No
   behavior change is made to these inherited branches.

## Rejected designs and deliberately retained work

- Patching private tables was rejected: it duplicates one ROM routine and omits
  last-offset ownership. Rendering the newly prepared offset was rejected by
  the ROM screen-before-background order. Neither is needed for the review fixes.
- Adding callbacks to exported DynamicWaterHandler was rejected in favor of an
  internal extension: no creator-facing API/version/signature change is needed.
- Moving the timer lifetime into shake setup was rejected: the timer is an
  allocated object and must not freeze with the player's shake gate. Allocation
  failure/same-pass slot dispatch remain inherited gaps: ROM allocation failure
  leaves no clearing object, while WaterSystem still expires its countdown.
- Atomic framed capture combines scalar publication and all three shake words;
  malformed headers, lengths, scalar encodings/enums and wide words are rejected
  before manager mutation. Same-build only; no legacy-payload migration.
- No terrain, forest redraw, object movement, collision, VBlank-only progression,
  shared shake implementation, hardware timing or FixBugs=0 branch is changed.

## Verification and limits

Review commands, with `G = -Dmse=off -Dopenggf.headless.gl=surfaceless-egl`:

- W: `python3 tools/testing/maven_queue.py` + G + `'-Dtest=TestGamePolicyArchitectureGuard,TestAizScreenPublication,TestAizComposedRewindState,TestS3kAizIntroEventsHeadless,TestSonic3kLevelEventRewindSnapshot' test`.
- F: wrapper + G + `'-Dtest=*Aiz*,*AIZ*,!**/trace/s3k/**,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot,WaterSystemTest,WaterPhysicsTest,Test*Water*,!**/soniccd/**,!**/trace/replay/**,TestSonic1LZWaterPreObjectClock,TestSonic1LZWaterEventsClock,TestScreenShakeTimerSlotObjectInstance,TestS3kAdditionalScrollProjection,TestS3kScreenEventCameraCopies,TestS3kZoneRuntimeStateAdapters,TestGamePolicyArchitectureGuard,TestBroadCatchSwallowingGuard' test`.
- T: `python3 tools/testing/maven_queue.py --normal` + G + `-Ptrace-replay-r7 '-Dtest=**/trace/s3k/**/Test*Aiz*' test`.
- V: `env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py` + G + `compile exec:java -Dexec.mainClass=com.openggf.tools.TraceCaptureTool '-Dexec.args=--trace aiz_completerun --clip frames:22400:23000 --scale 1 --out-dir target/aiz-review-forest-capture'`.

F/T/V run on candidate and detached common baseline, each with its own target/.
Tests use `-Dopenggf.surefire.reports=target/aiz-review-<kind>-tests`; T uses
`-Dopenggf.trace.reports=target/aiz-review-trace-reports`. Logs are
`target/aiz-review-{witness,focused,traces,capture}.log`. ROM discovery is checked.
W: 66 tests, zero failures/errors/skips, queue 132s, Maven execution 1m32s.
Final review results (tests / failures / errors / skips; per-job queue and Maven time):

| Run | Result | Queue; execution |
| --- | --- | --- |
| Candidate F | 1232 / 0 / 2 / 3 | 345s; 1m51s |
| Baseline F | 1225 / 0 / 2 / 3 | 415s; 1m50s |
| Candidate T | 38 / 17 / 0 / 0 | 440s; 2m13s |
| Baseline T | 38 / 17 / 0 / 0 | 195s; 2m10s |
| Candidate / baseline V | 601 frames each, exactly equal decoded hashes | 573s / 358s; 28s / 29s |

F has identical shared identities/outcomes/messages, two passing renamed rejection
checks and seven additional passing witnesses. Both guards pass; all 269 water
tests in 36 classes pass on both trees. Candidate F covers 170 classes.
T has identical failure identities/messages and all 22 full JSON reports exactly
equal, including complete-run and AIZ→HCZ. No category VERDICT (explicit selection).
V was freshly rerun on both trees. Inspected rows 22400, 22600, 22800 and 23000:
forest foreground intact in the forest portion, no blank foreground columns.
All jobs finished; no new/worsened failure, error or skip. Logs/captures retained.

Inherited issues in the initial matched runs (not hidden by green totals):
- Focused: both parameterizations of TestAizCollectorPublicRewind's
  publicIntroBeforeInitRoundTripsWithoutACollector throw services-unavailable.
- Skips: optional AIZ1 compatibility/recovery properties, and the legacy raw-
  GLFW fire-curtain diagnostic. The surfaceless trace capture is independent.
- Trace replayMatchesTrace failures, class prefixes and suffixes with full error
  counts in suffix order (zero warnings):
  `TestS3kKnucklesSuperAiz{2,3,4,5}SegmentTraceReplay`: 649,1222,375,199;
  `TestS3kMegaAiz{,2,3,4,5}SegmentTraceReplay`: 35,375,516,213,172;
  `TestS3kSonicTailsAiz{2,3,4,5}SegmentTraceReplay`: 1069,1451,1421,725;
  `TestS3kTailsFullChainAiz{2,3,4,5}SegmentTraceReplay`: 350,375,185,702.

Initial delivery preserved all 22 full trace JSON reports and all 601 decoded
forest-loop frames exactly against baseline. Captures are lossless FFV1 in MOV
on this build. This is baseline preservation, not new ROM pixel certification.
Strict AIZ-owner citation checking passes. The current full touched-file scan
retains three unchanged stale citations in manager branches outside this lane.
Smoke/integration remain with the lead; no broad suite, merge or PR watch here.
Queue/execution times are per-job, not additive wall time. User wait zero;
editing/exploration time is uninstrumented. Worktrees/logs/captures are retained.
