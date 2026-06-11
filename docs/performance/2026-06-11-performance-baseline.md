# Performance Optimization Baseline — 2026-06-11

Baseline measurements for the performance-optimization plan
(`docs/superpowers/plans/2026-06-11-performance-optimization.md`, spec
`docs/superpowers/specs/2026-06-11-performance-optimization-design.md`).
All later phases compare against the numbers in this document.

**Context:**
- Worktree: `sonic-engine-performance-optimization`, branch
  `bugfix/ai-performance-optimization`, based on `develop` at
  `35b3cabad` ("Merge remote-tracking branch 'origin/develop' into develop").
- Clean working tree at measurement time (only the untracked plan/spec docs).
- Machine: Windows 11, Java 21, default Maven surefire config (forkCount=4).
- All measurements are headless (no OpenGL context). GL-dependent paths are
  recorded as static analysis below.

## Step 1 — Clean source baseline

- `git status --short`: clean except the untracked plan/spec docs.
- S3K green list (`TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
  `TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`):
  **PASS — `MSE:OK modules=1 passed=51 failed=0 errors=0 skipped=0 time=50s`**.
- Full `*TraceReplay` sweep: `mvn "-Dtest=*TraceReplay" test` →
  **Tests run: 88, Failures: 52, Errors: 1, Skipped: 0**. No lwjgl/glfw
  `UnsatisfiedLinkError` or `TestBundledConfigResource` noise occurred in this
  sweep. The full failing list is the pre-work frontier (next section).

### Pre-work trace-failure baseline (the comparison list)

Only a trace that **passes** at this baseline and fails after a phase is a
regression. Format: `class :: error count, first-error frame/field`.

**Passing at baseline (must stay green):**

- TestS1Credits00Ghz1TraceReplay, TestS1Credits01Mz2TraceReplay,
  TestS1Credits02Syz3TraceReplay, TestS1Credits03Lz3TraceReplay,
  TestS1Credits04Slz3TraceReplay, TestS1Credits05Sbz1TraceReplay,
  TestS1Credits06Sbz2TraceReplay, TestS1Credits07Ghz1bTraceReplay
- TestS1Ghz1TraceReplay
- TestS2Ehz1TraceReplay, TestS2SczLevelSelectTraceReplay,
  TestS2WfzLevelSelectTraceReplay
- TestS3kAizTraceReplay (all 16 methods)

**Failing at baseline (known pre-work frontier):**

| Class | Errors | First error |
|---|---|---|
| TestS1FzCompleteRunTraceReplay | 155 | frame 713 -- y_speed |
| TestS1Ghz1CompleteRunTraceReplay | 292 | frame 1394 -- x_speed |
| TestS1Ghz2CompleteRunTraceReplay | 237 | frame 2370 -- y |
| TestS1Ghz3CompleteRunTraceReplay | 1108 | frame 370 -- y_speed |
| TestS1Lz1CompleteRunTraceReplay | 2992 | frame 302 -- y_speed |
| TestS1Lz2CompleteRunTraceReplay | 2102 | frame 1089 -- y |
| TestS1Lz3CompleteRunTraceReplay | 3229 | frame 466 -- y |
| TestS1Mz1CompleteRunTraceReplay | 450 | frame 1260 -- rolling |
| TestS1Mz1TraceReplay | 222 | frame 3224 -- y_speed |
| TestS1Mz2CompleteRunTraceReplay | 1074 | frame 2409 -- y_speed |
| TestS1Mz3CompleteRunTraceReplay | 1091 | frame 1702 -- y |
| TestS1Sbz1CompleteRunTraceReplay | 805 | frame 2268 -- air |
| TestS1Sbz2CompleteRunTraceReplay | 993 | frame 576 -- y |
| TestS1Sbz3CompleteRunTraceReplay | 4686 | frame 1421 -- camera_y |
| TestS1Slz1CompleteRunTraceReplay | 661 | frame 723 -- x_speed |
| TestS1Slz2CompleteRunTraceReplay | 270 | frame 651 -- g_speed |
| TestS1Slz3CompleteRunTraceReplay | 1500 | frame 718 -- y_speed |
| TestS1Syz1CompleteRunTraceReplay | 420 | frame 250 -- y_speed |
| TestS1Syz2CompleteRunTraceReplay | 336 | frame 1088 -- x_speed |
| TestS1Syz3CompleteRunTraceReplay | 710 | frame 1392 -- g_speed |
| TestS2Arz2LevelSelectTraceReplay | 1955 | frame 899 -- y_speed |
| TestS2ArzLevelSelectTraceReplay | 34 | frame 1285 -- tails_cpu_interact |
| TestS2Cnz2LevelSelectTraceReplay | 879 | frame 4418 -- tails_y |
| TestS2CnzLevelSelectTraceReplay | 316 | frame 837 -- tails_cpu_interact |
| TestS2Cpz2LevelSelectTraceReplay | 1062 | frame 2888 -- tails_x |
| TestS2CpzLevelSelectTraceReplay | 651 | frame 1157 -- tails_x_speed |
| TestS2DezEndingLevelSelectTraceReplay | 137 | frame 1557 -- x_speed |
| TestS2Htz2LevelSelectTraceReplay | 1298 | frame 831 -- tails_cpu_jumping |
| TestS2HtzLevelSelectTraceReplay | 353 | frame 419 -- tails_cpu_interact |
| TestS2Mcz2LevelSelectTraceReplay | 631 | frame 4485 -- tails_x |
| TestS2MczLevelSelectTraceReplay | 299 | frame 4513 -- y_speed |
| TestS2Mtz2LevelSelectTraceReplay | 2618 | frame 709 -- tails_x_speed |
| TestS2Mtz3LevelSelectTraceReplay | 3259 | frame 461 -- tails_cpu_interact |
| TestS2MtzLevelSelectTraceReplay | 1366 | frame 375 -- tails_cpu_interact |
| TestS2Ooz2LevelSelectTraceReplay | 1161 | frame 222 -- tails_cpu_interact |
| TestS2OozLevelSelectTraceReplay | 1134 | frame 1660 -- tails_g_speed |
| TestS3kAizCompleteRunTraceReplay | 5084 | frame 1095 -- x_speed |
| TestS3kCnzCompleteRunTraceReplay | 4826 | frame 0 -- y_speed |
| TestS3kHczCompleteRunTraceReplay | 4925 | frame 125 -- tails_air |
| TestS3kIczCompleteRunTraceReplay | 4230 | frame 0 -- tails_cpu_respawn_counter |
| TestS3kMgzCompleteRunTraceReplay | 7021 | frame 738 -- rings |
| TestS3kMhzCompleteRunTraceReplay | 3523 | frame 0 -- tails_cpu_routine |
| TestS3kMgzTraceReplay | n/a | Input alignment error at trace frame 33271 (BK2 input=0x0001, trace input=0x0009) |

`TestS3kCnzTraceReplay` is mixed (17 methods: 7 pass, 9 fail, 1 error):

- FAIL `replayMatchesTrace` — Input alignment error at trace frame 39672
  (BK2 input=0x0010, trace input=0x0000)
- FAIL `traceReplayCnzMinibossTailsPushBypassUsesHurtLatchedLeaderInput`
- FAIL `traceReplayHorizontalSpringLandingHandoffMatchesFrame3649`
- FAIL `traceReplayCnzMinibossPostBossKeepsArenaXClampAfterBossFlagClear`
- FAIL `traceReplayCnz2PreBalloonSlotPressureMatchesRomSequence`
- FAIL `traceReplayCnzMinibossLookDownBiasIsOwnedByFocusedPlayer`
- FAIL `traceReplayS3kRightWallPathRunsCeilingSeparationOnFrame5236`
- FAIL `traceReplayCnz2PathSwapConsumesSstSlotBeforeBalloonCluster`
- FAIL `traceReplayCnz2FixedAirCountdownInitialCadencePreservesBubblerSlot`
- ERROR `traceReplayCnzMinibossParentSecondMovePassUsesRomPhase` — NPE,
  `CnzMinibossInstance.getCurrentRoutine()` on null parent
  (TestS3kCnzTraceReplay.java:363)

## Step 2 — Frame-time and keyframe-spike baseline

Deterministic driver: `RewindBenchmark` over the S2 EHZ1 full-run trace
(`mvn test "-Dtest=RewindBenchmark" "-Dopenggf.rewind.benchmark.run=true"`,
keyframeInterval=60, 2000-frame samples). Raw JSON archived from
`target/rewind-benchmark-results.json` at measurement time. The AIZ1 trace
replay (`TestS3kAizTraceReplay`, 16 methods incl. the full ~8.8k-frame
`replayMatchesTrace`) completed in **54.44 s** and serves as the wall-clock
proxy for the S3K driver; per-frame percentiles below come from the EHZ1
benchmark since it samples per-frame timings directly.

| Phase | samples | mean | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|
| phase1.forward.off (engine step, no rewind framework) | 2000 | 44.6 µs | 0.4 µs | 169.4 µs | 390.2 µs | 16.93 ms |
| phase1.forward.on (PlaybackController.tick incl. every-60th-frame capture) | 2000 | 182.0 µs | 32.9 µs | 226.9 µs | 4.32 ms | 71.73 ms |
| phase2.capture (registry.capture, the keyframe spike body) | 1000 | 53.5 µs | 43.4 µs | 97.1 µs | 283.3 µs | 1.25 ms |
| phase3.restore | 500 | 106.0 µs | 75.8 µs | 207.0 µs | 374.3 µs | 6.57 ms |
| phase4.cold-seek | 48 | 6.41 ms | 5.77 ms | 14.61 ms | 34.61 ms | 34.61 ms |
| phase5.hot-seek.within-segment (stepBackward) | 59 | 1.58 ms | 0.24 ms | 0.68 ms | 72.14 ms | 72.14 ms |
| phase5.hot-seek.across-segment | 2 | 10.75 ms | — | — | — | 21.29 ms |

**Keyframe-spike summary:** framework-on p50 is 32.9 µs but p99 is 4.32 ms —
the every-60th-frame `registry.capture()` plus first-call warmup concentrates
into a periodic spike (capture alone: mean 53.5 µs, max 1.25 ms steady-state;
the 71.7 ms forward-on max includes first-frame warmup).

Other recorded benchmark gates at baseline:

- phase6.memory: 21 stored keyframes over 1200 frames,
  **8158 bytes/keyframe**, 171,320 retained bytes (0.16 MB). Largest
  subsystems: sprites 57,728 B; object-manager 52,136 B; level 9,384 B;
  oscillation 8,064 B.
- longtail.determinism: **clean rewind through 1200 frames** (max tested).
- phase7.audio (logical, NullAudioBackend): capture mean 803 ns, restore mean
  445 ns, replay mean 12.9 µs; replay allocatedBytes=466,448 over 500
  iterations (~933 B/replay), gcCountDelta=0.

## Step 3 — Held-rewind allocation baseline

Measured with a temporary (uncommitted) probe modeled on `RewindBenchmark`:
EHZ1 fixture + `RewindController`, forward 1200 frames, then 600 consecutive
`stepBackward()` calls, allocation via
`com.sun.management.ThreadMXBean.getThreadAllocatedBytes`.

| Mode | frames | wall | allocated | per-frame | rate (wall) | rate @60fps |
|---|---|---|---|---|---|---|
| Forward stepping | 1200 | 0.302 s | 9.31 MB | 7.9 KB | 30.8 MB/s | 0.47 MB/s |
| Held rewind (stepBackward) | 600 | 0.293 s | 30.36 MB | **51.8 KB** | 103.8 MB/s | **3.04 MB/s** |

Held rewind allocates **~6.5x more per frame** than forward stepping. The
per-backward-frame cost is dominated by `registry.restore()` +
re-instantiation: `ObjectManagerSnapshot` restore "recreates entries with
registered codecs" (ObjectManagerSnapshot.java:30) — every active object is
destroyed and re-instantiated each backward frame.

**Audio backend objects: not measurable headlessly.** The headless fixture
uses `NullAudioBackend` (AudioManager.java:73), so the SMPS driver rebuild
never runs in this probe. Static analysis of the production path (will
compare via this chain after Phase 3):

- Every `stepBackward()`/`seekTo()` calls `restoreAudioLogicalState(frame)` →
  `AudioKeyframeStore.replayToLogicalState` → `AudioManager.restoreLogicalSnapshot`
  (AudioKeyframeStore.java:34,59).
- `AbstractSmpsAudioBackend.restoreLogicalSnapshot` (line 815) constructs a
  fresh `SmpsDriver` via `newConfiguredSmpsDriver()` for the music snapshot
  (and a second one for a standalone SFX snapshot).
- `SmpsDriver.restoreSnapshot` (SmpsDriver.java:255) constructs a `new
  SmpsSequencer(...)` per active sequencer entry; each `SmpsSequencer` ctor
  builds a `new VirtualSynthesizer()` (SmpsSequencer.java:310/315) → `new
  PsgChipGPGX` + `new Ym2612Chip` (VirtualSynthesizer.java:25-26), and
  `Ym2612Chip` field-initializes a `new BlipResampler` (Ym2612Chip.java:435).

So with music playing, every held-rewind backward frame rebuilds the full
audio driver stack (`SmpsDriver` + sequencers + `Ym2612Chip` + `PsgChipGPGX`
+ `BlipResampler`) on top of the 51.8 KB/frame measured above. This is the
expected baseline symptom named in the plan.

## Step 4 — GPU upload baseline (static analysis; not measurable headlessly)

GL paths do not run headless; no temporary counters were committed. Numbers
below are derived from the source at baseline; post-phase comparison will use
the same static accounting (page bytes uploaded per `endBatch()` vs actual
dirty tile bytes).

**`PatternAtlas.endBatch()` (PatternAtlas.java:421-461):** atlas pages are
1024x1024 R8 (`ATLAS_WIDTH`/`ATLAS_HEIGHT` = 1024, GraphicsManager.java:57-58),
i.e. **1,048,576 bytes (1 MB) per page**. `endBatch()` uploads each dirty
page with a single full-page `glTexSubImage2D(0, 0, atlasWidth, atlasHeight)`
— there is no dirty-rectangle tracking, only a per-page boolean
(`dirtyPages[i]`). A DPLC update touching a handful of 8x8 tiles (64 bytes
each; e.g. a 32-tile player DPLC frame = 2,048 bytes of actual change)
re-uploads the full 1 MB page: **~512:1 upload amplification** for a typical
2 KB DPLC change, up to 2 MB/frame if both atlas pages (MAX_ATLASES=2) are
dirtied.

**Phase 2A upload measurement (Task 4, simulated):** `PatternAtlas.endBatch()`
now tracks exact dirty slots (up to 64 per page, individual 8x8 tile uploads
under one bind) with a dirty-bounding-rectangle fallback past that
(`GL_UNPACK_ROW_LENGTH`-strided, reset in finally). Measured through the
package-private upload-accounting sink in `TestPatternAtlasDirtyUploads`
(headless simulation — real GL scenes can't run headless, numbers are the
agreed simulated substitute for the two baseline workloads):
typical DPLC change (32 scattered tiles/endBatch) = **2,048 bytes vs
1,048,576-byte full-page baseline → 512.0x reduction**; CNZ slot-machine
burst (48 tiles every frame) = **3,072 bytes/endBatch → 341.3x reduction**.
Both exceed the ≥100x spec acceptance target for DPLC animation.

**`TilemapTexture.upload` (TilemapTexture.java:40-59):** uploads the full
`widthTiles x heightTiles` RGBA8 BG tilemap window via one `glTexSubImage2D`
(4 bytes/texel) every time the window content is rebuilt — the rebuild is
dirty-gated but not incremental, so a 16 px BG scroll rebuilds and re-uploads
the whole window in Java rather than only the newly exposed tile column/row.

## Re-measurement recipe for later phases

```bash
# S3K green list
mvn "-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils" test
# Trace sweep (compare against the baseline list above)
mvn "-Dtest=*TraceReplay" test
# Frame-time / keyframe / memory / longtail / audio-logical phases
mvn test "-Dtest=RewindBenchmark" "-Dopenggf.rewind.benchmark.run=true"
# Held-rewind allocation: re-create the temporary ThreadMXBean probe
# (forward 1200 frames, then 600 stepBackward; report per-frame KB and MB/s)
```

**Rebuilding the held-rewind allocation probe (Step 3):** copy
`RewindBenchmark`'s EHZ1 fixture/bootstrap (same trace, keyframeInterval=60,
`RewindController` wiring) into a temporary JUnit method. Cast
`ManagementFactory.getThreadMXBean()` to `com.sun.management.ThreadMXBean`
and read `getThreadAllocatedBytes(Thread.currentThread().threadId())`
immediately before and after (a) a 1200-frame forward-stepping loop and (b) a
600-iteration consecutive `stepBackward()` loop, alongside `System.nanoTime()`
for wall time. Report allocated-delta / iterations as per-frame KB plus MB/s
at measured wall and at a 60 fps frame budget, as in the Step 3 table. Do not
commit the probe.

## Phase 1 re-measurement (after Tasks 1-3)

Re-run of the EHZ1 `RewindBenchmark` (same recipe as Step 2) at the Phase 1
cut: Task 1 (exact audio/session/collision quick wins), Task 2 (rewind capture
reflection memoization), Task 3 (lazy render buckets, primitive ring active
indices, deterministic-audio-runtime fast paths). Trace sweep at this cut:
**88 run, 52 failures + 1 error — failure-set identical to the pre-work
baseline list above** (all 13 baseline-green classes stayed green).

| Phase | p50 | p99 | max | baseline p50 / p99 / max |
|---|---|---|---|---|
| phase1.forward.off | 0.5 µs | 315.5 µs | 15.70 ms | 0.4 µs / 390.2 µs / 16.93 ms |
| phase1.forward.on | 30.9 µs | 3.76 ms | 39.58 ms | 32.9 µs / 4.32 ms / 71.73 ms |
| phase2.capture | 44.3 µs | 277.8 µs | 1.24 ms | 43.4 µs / 283.3 µs / 1.25 ms |
| phase3.restore | 77.3 µs | 417.1 µs | 7.63 ms | 75.8 µs / 374.3 µs / 6.57 ms |
| phase4.cold-seek | 4.27 ms | 17.12 ms | 17.12 ms | 5.77 ms / 34.61 ms / 34.61 ms |
| phase5.hot-seek.within-segment | 0.16 ms | 51.04 ms | 51.04 ms | 0.24 ms / 72.14 ms / 72.14 ms |

**Keyframe-spike comparison:** framework-on p99 improved 4.32 ms → 3.76 ms and
the warmup-dominated max dropped 71.73 ms → 39.58 ms; steady-state capture is
flat (p50 43.4 → 44.3 µs, max 1.25 → 1.24 ms), as expected — Phase 1 did not
target the capture body itself beyond Task 2's reflection memoization, which
shows up in the forward-on tail rather than the isolated capture phase.

Other gates at this cut: phase6.memory unchanged (21 keyframes, 8158
bytes/keyframe, 171,320 retained bytes); longtail.determinism still clean
through 1200 frames; phase7.audio replay mean 11.5 µs (baseline 12.9 µs) with
allocatedBytes 466,376 over 500 iterations (~933 B/replay, unchanged) and
gcCountDelta=0.

Caveat: this benchmark is headless, so Task 3's render-path wins (lazy
sprite/object bucket rebuilds, allocation-free ring active-index iteration in
`draw()`) do not appear in these numbers — they remove per-rendered-frame work
that only runs with the GL pass. Restore/seek numbers carry run-to-run JIT/GC
variance (phase3.restore max regressed within noise); the structural rewind
fixes land in Phase 3. No acceptance-target claims at this cut.

## Phase 3 re-measurement (after Tasks 7-9)

Cut: Task 7 (in-place object restore), Task 8 (held-rewind audio restore
deferral), Task 9 (bounded audio command timeline, typed compact field
access, restore-path blob copy removal, precomputed snapshot sort keys).
Trace sweep at this cut: **88 run, 52 failures + 1 error — failure set
identical to the pre-work baseline list** (all 13 baseline-green classes
stayed green). S3K green list: PASS.

### Held-rewind allocation (Step 3 probe, same recipe)

| Mode | frames | wall | allocated | per-frame | rate (wall) | rate @60fps | baseline |
|---|---|---|---|---|---|---|---|
| Forward stepping | 1200 | 0.264 s | 9.17 MB | 7.46 KB | 34.7 MB/s | 0.458 MB/s | 7.9 KB / 0.47 MB/s |
| Held rewind (stepBackward) | 600 | 0.282 s | 27.69 MB | **45.06 KB** | 98.2 MB/s | **2.769 MB/s** | 51.8 KB / 3.04 MB/s |

**Acceptance target (≥10x reduction vs 51.8 KB/frame): MISSED in this
headless probe.** Measured reduction is **1.15x** (51.8 → 45.1 KB/frame;
3.04 → 2.77 MB/s @60fps). Honest gap analysis, from added probe
sub-measurements at the same cut:

- Pure `registry.restore(snapshot)`: **6.45 KB/restore** (Task 7's in-place
  restore is doing its job — restore is no longer the dominant allocator).
- Pure `registry.capture()`: **17.42 KB/capture**.
- `stepBackward` decomposition: each 60-frame backward segment crossing
  rebuilds the segment cache by replaying ~59 forward steps and capturing
  every frame — amortized ~24.5 KB/frame of `registry.capture()` plus
  ~7.3 KB/frame of forward engine stepping, on top of the per-frame 6.45 KB
  restore. Segment-rebuild capture allocation now dominates held rewind and
  was not a Phase 3 target (no plan task pools the per-frame capture
  snapshots/buffers inside `SegmentCache`).
- The headless probe structurally cannot see the two biggest production
  Phase 3 wins, as the Step 3 baseline itself noted: the audio driver
  rebuild chain (`SmpsDriver`/`SmpsSequencer`/`Ym2612Chip`/`BlipResampler`
  per backward frame) never runs under `NullAudioBackend`, and Task 8's
  restore deferral only engages while reverse audio presentation is active
  (live held rewind), which the probe does not activate. Task 9's
  command-timeline bounding removes a session-lifetime leak and per-restore
  full-list copies whose allocation profile in this 1200-frame fixture is
  negligible by construction.

Reaching 10x on this probe's definition would require allocation-free (or
pooled) `registry.capture()` for segment-cache rebuilds; Phase 4B (Task 11,
object/render allocation cleanup) is the remaining in-plan lever, but no
current task covers capture pooling — flagging for Task 13 acceptance.

### EHZ1 RewindBenchmark percentiles (same recipe as Step 2)

| Phase | p50 | p99 | max | Phase 1 cut p50 / p99 / max | baseline p50 / p99 / max |
|---|---|---|---|---|---|
| phase1.forward.off | 0.9 µs | 351.6 µs | 13.48 ms | 0.5 µs / 315.5 µs / 15.70 ms | 0.4 µs / 390.2 µs / 16.93 ms |
| phase1.forward.on | 28.5 µs | 3.07 ms | 30.84 ms | 30.9 µs / 3.76 ms / 39.58 ms | 32.9 µs / 4.32 ms / 71.73 ms |
| phase2.capture | 42.5 µs | 229.7 µs | 1.00 ms | 44.3 µs / 277.8 µs / 1.24 ms | 43.4 µs / 283.3 µs / 1.25 ms |
| phase3.restore | 77.1 µs | 452.4 µs | 6.75 ms | 77.3 µs / 417.1 µs / 7.63 ms | 75.8 µs / 374.3 µs / 6.57 ms |
| phase4.cold-seek | 3.70 ms | 12.85 ms | 12.85 ms | 4.27 ms / 17.12 ms / 17.12 ms | 5.77 ms / 34.61 ms / 34.61 ms |
| phase5.hot-seek.within-segment | 0.18 ms | 43.17 ms | 43.17 ms | 0.16 ms / 51.04 ms / 51.04 ms | 0.24 ms / 72.14 ms / 72.14 ms |

**Keyframe-spike comparison:** forward-on p99 improved again
(baseline 4.32 ms → Phase 1 3.76 ms → **3.07 ms**) and the max is down to
30.84 ms (from 71.73 ms baseline). Steady-state capture improved at the tail
(p99 283.3 → 229.7 µs, max 1.25 → 1.00 ms) from Task 9's typed compact
field access; p50 is flat as expected.

**Cold-seek:** p50 5.77 → 3.70 ms, max 34.61 → 12.85 ms vs baseline (a 2.7x
max improvement, mostly Task 7's in-place restore on the seek replay path).

Other gates at this cut: phase6.memory 21 keyframes at 8225 bytes/keyframe
(baseline 8158 — +0.8%, within the snapshot-content noise of intervening
develop-based work); longtail.determinism clean through 1200 frames;
phase7.audio capture/restore-logical means 654/384 ns (baseline 803/445 ns),
replay-logical mean 13.2 µs (baseline 12.9 µs, flat within noise).
