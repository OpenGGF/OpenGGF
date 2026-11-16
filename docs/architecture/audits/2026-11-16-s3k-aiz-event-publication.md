# AIZ screen event publication — 2026-11-16

Base: `e676eb5086b7069c5e8ffdd0dbd33990d5c2bf5f` (develop).
Scope: AIZ only; HCZ and other zone implementations remain unchanged.

## Owners and ROM order

- `AizShipBombInstance` writes `$10` through `S3kAizEventWriteSupport` and
  `AizObjectEventBridge` to AIZ events. It never writes an offset.
  `AIZShipBomb_Drop` (sonic3k.asm:105458-105474) owns impact handling.
- `DynamicWaterHeight_AIZ2` (sonic3k.asm:8680-8727) writes `-1` and allocates
  an invisible object; `Obj_6E6E` (sonic3k.asm:8731-8739) counts down and clears
  the same flag. The ROM timer is distinct from the three global shake words.
  Existing WaterSystem countdown and slot reservation are retained.
- `AIZ2_ScreenEvent` (sonic3k.asm:104923-104927) adds the **previous** offset
  to the copied foreground Y. `loc_50260` (sonic3k.asm:105208-105216) and
  `loc_502C2` (sonic3k.asm:105247-105249) prepare the next offset only after
  background deformation/drawing. Keeping a displayed offset is therefore
  necessary; deleting it as a generic “pending copy” would change ROM order.
- `ShakeScreen_Setup` (sonic3k.asm:104234-104255) owns flag countdown, offset
  and last offset. It suppresses offset generation and holds the flag when
  Player_1's stored routine is at least six. AIZ now delegates to the existing
  `S3kScreenShake` without changing its API or tables.
- AIZ1 canopy phase was anchored by the first renderer call. The event owner
  now retains that origin, including whether normal deformation has begun.
  Intro-to-normal dispatch is `AIZ1BGE_Intro`
  (sonic3k.asm:104621-104636); subsequent normal deformation comes through
  `AIZ1BGE_NormalRefresh` / `AIZ1BGE_Normal` (sonic3k.asm:104651-104675).
  Scene projections retain only an explicitly supplied presentation origin.
- Battleship smooth X, forest-loop camera generation, redraw counters and
  ship Y remain in AIZ events. No AIZ background collision-difference publisher
  was found in the runtime adapter. The existing AIZ1 right-wall deep probe
  rule and the bomb's non-colliding falling phase are unchanged.

## Witness and change

The real AIZ ROM fixture's new witness writes `$1E` and invokes the owning
shake setup. On the base it fails: decremented `$1D` produces zero, expected
three from the contiguous second table. It also checks stored routine eight
with `getDead() == false`: the flag must hold and next offset become zero.
Red command:
`python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestSonic3kAIZEvents#shakeSetupHonoursStoredRestartRoutineAndContiguousTimedTable' test`
Result: 1 test, 1 failure, 0 errors, 0 skips; S3K ROM found.

AIZ now has a framed same-build payload combining its scalar schema and all
three shake words. Manager framing validates it before restoring base scalars.
Malformed headers, lengths and wide shake words are rejected without mutation;
noncanonical scalar encodings are rejected by re-encoding a probe. Legacy
unframed payloads have no migration. Older malformed-sidecar tests now expect
whole-snapshot rejection, as required by the new composed contract.

WaterSystem no longer selects continuous offsets on every setup. Its AIZ
handler writes the flag at timer start and expiry; a bomb can overwrite that
flag in between, as in the ROM. Other dynamic-water handlers take neither new
branch. The timer itself is still captured by WaterSystem's existing rewind
owner, and its invisible slot object retains its own lifetime.

## Alternatives and deliberately retained work

1. Keep duplicated tables and only patch their bounds/death gate: rejected
   because it leaves two implementations of one ROM routine and makes the
   missing last-offset word easy to overlook again.
2. Delete the applied/displayed offset and render the new shared offset:
   rejected by AIZ2's screen-before-background ordering above.
3. Move the invisible water timer's lifetime into shake setup: rejected because
   the ROM allocates an object, and setup is gated by the player's routine.
   Timer allocation failure and same-pass object-slot dispatch remain existing
   behavior. On allocation failure the ROM has no clearing object, whereas
   WaterSystem still expires its countdown; that inherited gap is not repaired.

No object movement, terrain mutation, forest redraw timing, collision policy,
VBlank-only event advancement or hardware-timing authority is changed. The
existing VBlank-only no-op is correct: these screen events are main-loop work.
The shipped FixBugs=0 behavior is retained; no fixed-build branch is introduced.

## Verification

Final focused selection: 992 tests in 136 classes, zero assertion failures,
two inherited errors and three inherited skips. All 912 baseline identities
(124 classes) have identical outcomes/messages; all 80 additional checks pass.
Raw outputs remain under `target/aiz-*.log`; baseline outputs belong to the
retained detached `.worktrees/aiz-publication-baseline` checkout. No broad
category or full-suite run was requested.
Queue wait, execution, user wait and unknown wall time are reported separately
at handoff; the lane does not infer execution time from queue elapsed time.

Command catalogue (all test commands use the queue wrapper; `G` below means
`-Dmse=off -Dopenggf.headless.gl=surfaceless-egl`; `-Dtest=` values are quoted):

- W: `python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestSonic3kAIZEvents#shakeSetupHonoursStoredRestartRoutineAndContiguousTimedTable' test`.
- R: wrapper + G + `-Dtest=TestSonic3kAIZEvents,SwScrlAizTest,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot` + `test`.
- B: wrapper + G + `-Dtest=*Aiz*,*AIZ*,!**/trace/**,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot` + `test`.
- C: B with `WaterSystemTest,TestWaterSystemRewindSnapshot,TestRetailDynamicWaterMotion,TestSonic3kDynamicWaterHandlers,TestScreenShakeTimerSlotObjectInstance` appended to its selector.
- F: C with exclusion `!**/trace/s3k/**` instead of `!**/trace/**`, and `TestS3kAdditionalScrollProjection,TestS3kScreenEventCameraCopies,TestS3kZoneRuntimeStateAdapters` appended.
- T: `python3 tools/testing/maven_queue.py --normal` + G + `-Ptrace-replay-r7 '-Dtest=**/trace/s3k/**/Test*Aiz*' test`.
- V: `env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py` + G + `compile exec:java -Dexec.mainClass=com.openggf.tools.TraceCaptureTool '-Dexec.args=--trace aiz_completerun --clip frames:22400:23000 --scale 1 --out-dir target/aiz-forest-capture'`.
- Shared-only selector: `TestS3kAdditionalScrollProjection,TestS3kScreenEventCameraCopies,TestS3kZoneRuntimeStateAdapters,TestS3kAizInputProvenance,TestS3kAizPrefixClosureContract`; cancelled before admission, superseded by F.

Each selected run uses its own `-Dopenggf.surefire.reports=target/<run>-tests`;
T also uses `-Dopenggf.trace.reports=target/<run>-reports`. These output-only
arguments and redirected log names do not select gameplay. No category VERDICT
is available: category/full-suite execution was explicitly excluded.

| Attempt / log under target | Command | Tests / failures / errors / skips | Queue seconds; Maven execution |
| --- | --- | --- | --- |
| `aiz-red.log` | W | 1 / 1 / 0 / 0 (expected red) | 16; 1m35s |
| `aiz-first.log` | R | 127 / 2 / 0 / 0 (old partial-restore expectations) | 65; 1m35s |
| `aiz-focused.log` | B | compile failure, no tests (overbroad test-helper edit) | 305; 1m37s |
| `aiz-traces.log` | T | same compile failure, no tests | 387; 35s |
| baseline `aiz-focused.log` | B | 912 / 0 / 2 / 3 | 514; 1m36s |
| baseline `aiz-traces.log` | T | 38 / 17 / 0 / 0 | 276; see log |
| `aiz-focused2.log` | C | 960 / 2 / 3 / 3; three new issues subsequently repaired | 784; see log |
| `aiz-traces2.log` | T | 38 / 17 / 0 / 0; all 22 full JSON reports exactly equal baseline | 884; see log |
| `aiz-final-traces.log` | T | 38 / 17 / 0 / 0; all 22 full JSON reports exactly equal baseline | 690; 2m12s |
| `aiz-final.log` | F | 992 / 0 / 2 / 3; matched baseline outcomes | 532; 2m37s |
| candidate / baseline `aiz-capture.log` | V | 601 decoded frames each, all frame hashes equal | 411 / 440; 29s / 28s |

V emits lossless FFV1 video in MOV on this build (ffprobe confirmed 320×224 BGRA).
Extracted and inspected rows 22400, 22600, 22800 and 23000; the forest foreground
is intact in the forest portion, with no blank foreground columns. Every decoded
frame in the full window matches baseline. This is baseline preservation, not
new ROM pixel parity. Captures and extracted PNGs remain under each tree's
`target/aiz-forest-capture/`.

User wait: zero. Queue and Maven execution above are per-job elapsed values,
not additive wall time; other exploration/editing time was not instrumented.

Inherited focused errors: both parameterizations `[1]` and `[2]` of
`TestAizCollectorPublicRewind.publicIntroBeforeInitRoundTripsWithoutACollector`
throw “services not available — object must be created through ObjectManager”.
Inherited skips: `TestS3kAiz1CompatibilityRoutes.axisRouteCompletes` and
`TestS3kAiz1SpringRecovery.liveSpringJumpCrossesAndReplaysWhileWalkingIsRejected`
need optional route properties; `TestAizFireCurtainGpuDiag.captureFireCurtainFrames`
aborts GPU initialization (legacy raw GLFW). No new skip-policy rule was added.
The required surfaceless capture succeeds independently of that diagnostic.

Inherited trace failures (each method is `replayMatchesTrace`; numbers are the
full report error counts, with zero warnings):

| Class prefix / AIZ segment suffix | Error counts in suffix order |
| --- | --- |
| `TestS3kKnucklesSuperAiz{2,3,4,5}SegmentTraceReplay` | 649, 1222, 375, 199 |
| `TestS3kMegaAiz{,2,3,4,5}SegmentTraceReplay` | 35, 375, 516, 213, 172 |
| `TestS3kSonicTailsAiz{2,3,4,5}SegmentTraceReplay` | 1069, 1451, 1421, 725 |
| `TestS3kTailsFullChainAiz{2,3,4,5}SegmentTraceReplay` | 350, 375, 185, 702 |

Strict AIZ-events/WaterSystem/audit checking passes after six stale-comment repairs.
The full touched-file scan retains five inherited stale citations: three outside
AIZ manager branches, one old AIZ1 matrix entry and one test comment; none is new.
Smoke, independent review and merging belong to the lead; this lane leaves a draft.
