# Runtime performance series — 2026-10-12

## Status and source

Accuracy prerequisites and complete-runtime qualification are in progress.
**The original numeric baseline is frozen from five fresh processes per scenario.
No performance experiment has begun or been accepted; rejection count is zero.** The intended
metric is total process CPU time per simulated frame, equally weighted between
normal and demanding scenarios in each game. No saving is attributed to accuracy
repairs, upstream changes, Java migration, or replacing an invalid helper harness.
Integration, push and task-worktree cleanup have not happened.

The user authorized repairing inherited AIZ → HCZ failures after both complete
fixtures failed their untouched-source controls. Local checkpoints:

| Purpose | Commit |
|---|---|
| Initial fast-forwarded develop source (from `15f3cf371`) | `a0e2b79cc3a47cedd11a0f9daf6a32ea4ced19cb` |
| Native handoff repair C1–C7 | `1baa8e6dc9c5de5223a64633e7043411845a997f` |
| Updated-base merge; sole conflict retained both hazard lessons | `eca81166b7bbb06eeb557f92f4a804029130a5b4` |
| Updated matched develop base | `3ac1363ed2b2549d9e7c9d913b3b617b47e1bc53` |
| C8 selected-world binding, input-only runtime tools and detailed qualification record | `48bc7c34bd6068199763f62620a5193586c67293` |

Worktree `.worktrees/ai-runtime-perf-series`, local branch
`feature/ai-runtime-perf-series`; clean control `.worktrees/ai-runtime-perf-control`
remains at `3ac1363ed`. Main's unrelated dirty submodules/files were preserved.
Detailed earlier derivations/commands are preserved in this same dated artifact
at `1baa8e6dc` and `48bc7c34b` (`git show COMMIT:PATH`); this current record condenses
the outcome without archiving raw diagnostics.

## Environment and measurement boundaries

- Current source requires Arch OpenJDK **25.0.4.1**, Maven 3.9.16, Lua 5.4.
  `JAVA_HOME=/usr/lib/jvm/java-25-openjdk`, `LUA_BIN=/usr/bin/lua5.4`.
  Early controls used Java 21.0.12.1+1 before the upstream requirement changed;
  both precede numeric freezing. TraceChaser is pinned at
  `e0a2443e086ca657a49227c5467eeecd06e40ece`.
- Linux 7.2.8-2-cachyos; Ryzen 9 9950X, 16 cores/32 logical CPUs, 30 GiB RAM.
  Discrete RX 9070 XT, 16 GiB, PCI `0000:03:00.0`; radeonsi/gfx1201/ACO,
  DRM 3.64, Mesa 26.2.4-arch3.1. Integrated PCI `0000:19:00.0` also has a small
  process-owned setup client; report both, deduplicating FDs by device/client ID.
- Desktop `ScreenSaver.GetActive` reported true; the user confirmed locked/asleep.
  Main XWayland stalled in show-window, native Wayland in swap. An owned private
  KWin virtual Wayland display, 1280×720 scale 1, with
  `DRI_PRIME=pci-0000_03_00_0`, runs the real discrete renderer and normal swaps.
  A default-device private attempt selected the integrated GPU and was rejected.
- The audible owned S1 process was stopped. Actual OpenAL ALSA output is verified
  by owned PID on a private Pulse sink, 48 kHz stereo Float32; buffer sizes
  960/2880 frames. Named private ALSA config plus explicit host Pulse socket
  preserves device/mixer work without changing the user's fallback device.
  Unassigned streams get only a bounded settle interval; assigned physical output
  fails closed. Private display/sink ownership metadata is external and cleanup
  remains required. No other user's display/audio/process was changed.
- Qualification uses `--enable-native-access=ALL-UNNAMED -Xmx3g`, native 320×224,
  physical 640×448, 60 Hz, audio enabled, normal effects, live rewind enabled for
  all games, 60-second history; production audio keyframes remain 60 ticks and
  gameplay checkpoints 10 ticks. All effective settings are emitted in reports.
  Earlier `-Xmx1g` probes are historical qualification, never acceptance results.

Existing ROMs are read in place, never copied/renamed/linked manually:

| Game / ROM root file | CRC32 | SHA-1 |
|---|---|---|
| S1 `Sonic The Hedgehog (W) (REV01) [!].gen` | `AFE05EEE` | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| S2 `Sonic The Hedgehog 2 (W) (REV01) [!].gen` | `7B905383` | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| S3K `Sonic and Knuckles & Sonic 3 (W) [!].gen` | `63522553` | `CFBF98C36C776677290A872547AC47C53D2761D6` |

`OPENGGF_ROM_ROOT` is the absolute existing ROM checkout;
`OPENGGF_PERF_TASK_DIR` is the external task evidence directory. Actual local
resolutions, launch commands, backend files and ownership are recorded there.

`RuntimeInputPlan` compiles controller inputs, their predecessor, structural row
policy and dedicated hardware readiness in a separate JVM. The measured process
loads no physics/auxiliary comparison rows. `RuntimeEngineBenchmarkTool` runs the
shipping Engine window/render/audio/rewind/pacing loop. Exact all-thread process
CPU includes workers and GC; controller and actual-state SHA-256 digests plus
actual level-counter deltas verify work. Requested full-row counts are labelled
separately from measured executed frames. Update-to-update presentation intervals
include pacing and are not CPU measurements. Filled history is asserted at the
measurement boundary; reported EOF can intentionally have reset history.

The external sampler measures RSS and kernel VmHWM, and reads kernel resident
GPU backing-store counters rather than requested storage. See the
[DRM counter contract](https://docs.kernel.org/gpu/drm-usage-stats.html).
It deduplicates FDs, rejects unknown units/missing residency/late attachment,
and protects against PID reuse. Resident GPU peaks are **sampled**: spikes shorter
than the 50-ms cadence can be missed. Private compositor CPU is outside the
application process. CSV-enabled qualification and profiling are never timing
acceptance. All builds/tests must be separate from live runs in that build tree.

## Untouched controls and inherited gaps

| Control | Compared observations | Trace errors | First error / result |
|---|---:|---:|---|
| `TestS1Ghz1TraceReplay` | 3,905 | 0 | Passed |
| `TestS1Lz1CompleteRunTraceReplay` | 13,070 | 0 | Passed |
| `TestS2Ehz1TraceReplay` | 5,852 | 0 | Passed |
| `TestS2CpzLevelSelectTraceReplay` | 5,744 | 2 | Row 927, `rings`: 1 / 0 |
| `TestS2ArzLevelSelectTraceReplay` | 5,073 | 0 | Passed |
| `TestS2MtzLevelSelectTraceReplay` | 10,134 | 0 | Passed |
| `TestS3kAizTraceReplay#replayMatchesTrace` | 20,463 | 57 | Row 20,302, `player_animation_id`: $00 / $05 |
| `TestS3kAizZoneSliceTraceReplay` | 26,041 | 99 | Row 25,589, `player_animation_id`: $13 / $05 |
| `TestS3kHczZoneSliceTraceReplay#replayMatchesTrace` | 29,302 | 4,699 | Row 9,482, `air`: 1 / 0 |
| `TestS3kHczZoneSliceTraceReplay#hczPoindexterOccupiesRomSlotBeforeTailsBounce` | — | — | Passed |
| `TestS3kAiz1RoutePilot` | — | — | Passed; 5,174 ticks to AIZ2 reload, no recovery continuation |
| `TestS3kHcz1RoutePilot` | — | — | Passed |


All controls above executed on `a0e2b79cc` with absolute canonical ROM paths;
missing-ROM skips did not qualify a route. The original handoff is now zero-error;
the alternate retains 59 inherited mismatches, first row 25589 animation $13/$05.
No failing fixture was trimmed to make it a benchmark. CPZ2 and full HCZ remain
unqualified; independent pilot passes do not certify their full traces.

## Accepted accuracy prerequisites

| ID | ROM-backed repair / evidence |
|---|---|
| C1 | Remove capsule's second Tails restore. Floating capsule `loc_866CC` owns end-pose/swing, boss post-defeat owns Restore_PlayerControl/2; `loc_7D078` is another zone's post-boss owner. |
| C2 | Knuckles uses plain lowest-free AllocateObject from `AIZEndBoss_StopPlayer`, not after-current allocation. Native lower slot defers initialization to the next object scan. |
| C3 | Retain the boss SST as its native post-defeat continuation and keep ROM-loaded button/bridge; capture 12 scalar continuation values, not an unregistered controller reference. Compensating bridge delays/synthetic replacement graphs were rejected. |
| C4 | Results retain native camera ownership and semantic forest-front gates; signed-bit bridge contact rejection does not discard an existing rider. |
| C5 | Waiting boss remains alive without invented range deletion; results-parent dispatch owns capsule release, with host policy retained before disposal. |
| C6 | Results allocate lowest-free and dispatch normally; button clears forced input without rewriting already-consumed UP history. Bridge checks solids only at its native checkpoint, keeps its rider latch through the $0E countdown and delayed eject; rider set is captured for recreation/forward rewind. |
| C7 | Button routine entry changes routine and returns; Y contact is checked on the next dispatch. One old extra-UP oracle was replaced by `cutsceneButtonRetainsConsumedUpHistoryAndUnlocksNextPlayerDispatch`, explicitly preserving test identity change. |
| C8 | Construction/reset bind the selected world, retaining pre-session bootstrap fallback. A normal S3K world with S2 bootstrap otherwise wrote Tails' $4000 instead of `sub_13ECA`'s $7F00 (sonic3k.asm:26806) for 767 early observations. Both regression paths failed before the fix. Rebinding legacy config alone was disproved. |

The original full trace passes 20,463 compared observations and its exact terminal
control: KOS_MODULE_QUEUE ordinals 38–41, ROM sources $0D6F28, $15C3A2, $0D6D84,
$39BEDA and their unchanged submission fingerprints. This fixture ends after HCZ
load submission, before title ownership claims all completions; it does not
measure a completed HCZ load duration. No expected state/clock/position was written
from comparison rows. Reference checkout `1a454a0e335137a1a016d1090a9f4528d36944cf`
was read without modifying the user's disassembly.

## Correctness evidence and rejected driver hypotheses

- Updated C1–C7 focused run: 167 tests, 0 failures/errors/skips; includes required
  S3K skip/loading/bootstrap/decoder checks, boss/bridge recreation and forward
  replay, rewind schemas and unified capture audio. Exact selectors remain at
  checkpoint `48bc7c34b` in this artifact.
- C8: initial 2/2 regression assertions failed; physics/hybrid/rule focus then
  passed 60. Additional domain run passed 80, including original complete AIZ,
  LZ2, all four mandatory S3K checks, and pre-session fallback (0 skips).
- Input-plan tests caught absent optional source_bk2, then passed with the
  established adjacent-movie fallback. Final focused input/session/authority/
  hydrate-default run: 31 tests, 0 failures/errors/skips. Sampler controls:
  2 Python tests pass (deduplication/residency/units).
- `TraceBenchmarkTool --mode full` has offscreen submission but lacks normal
  presentation/device/filled-history work; it was rejected as a whole-runtime
  baseline. The rich external prototype also retains comparison data/per-row
  logging, so its resource figures are not acceptance results.
- Wrong S3K start row 335 was rejected; canonical LEVEL begins at row 289,
  controller offset `511+289=800`. Omitting previous-input latch diverged at
  row 1428, speed 0/12. Omitting approved readiness admission wrapped AIZ1 early
  at 5463, X 12237/205, Y 893/765. Only matching prepared-job kind/ordinal/
  fingerprint/service-boundary release corrected it; no gameplay values/job
  creation came from the timing stream.
- A prototype stopped seven rows early at HCZ title load; strict EOF and observing
  ordinary loading iterations corrected the cursor. A maintained-tool fade then
  repeated its end-boundary interval and failed closed. The boundary now closes
  once, includes only recorded presentation samples, and verifies actual simulated
  counts. These are driver failures, not rejected performance experiments.
- Concurrent recompilation invalidated one live LZ2 run by removing a lazily
  loaded cleanup class. Isolated rebuilt full retry passed; no such run is reused.
- Live LZ1 prefix failed rings 0/1 at row 534, caught up at 535; movement matched.
  Native first VBlank $6C69 versus fresh engine 2 suggests an unproven phase issue.
  No clock was fitted/copied; choose the independently passing complete LZ2.
- S2 normal startup has inherited absolute level-counter offset +26. Existing
  comparator does not certify that origin. Input/actual-state identity and
  measured counter deltas are explicit; this is not a claim all raw clocks match.
- S3K raw rows 289 and 20791 are non-gameplay under the unchanged canonical
  policy. Raw Tails facing bit differs for 26 rows 16217–16242, satisfying the
  existing airborne/zero-horizontal-speed facing-only predicate; it was not
  loosened. Non-riding stand-slot sentinels are native 0 / engine -1. Raw values
  remain in qualification data and will be preserved in candidate digests.

Rendered handoff captures (rows 20180–20797): two fresh processes have identical
618 FFV1 640×448 frames and 494,400 48-kHz stereo PCM sample frames. Updated Java
25 capture also matches all decoded pixels and PCM. Video MD5-list SHA-256
`51ed6bbf4b9b778c980f47bd7934fd881915d1f0c9eff35c38888e854dd3ee90`;
PCM SHA-256 `7d32aea56b52460d544aeafa4dd6906146e03d4c17ab449beeae5565604b5cfa`.
Untouched a0 differs in 228 frames starting 20304, and audio from 20403; native
slot evidence explains corrected next-scan Knuckles initialization at 20404.
Inherited waterfall/color gaps remain; no native RGB parity is claimed.

## Frozen workload matrix

Frozen windows are relative input-plan cursor, half-open; raw rows include the stated
start. Normal/demanding weights are 0.5 each; intended score is arithmetic mean
of scenario total process CPU per verified simulated frame. The longer normal
S1 route replaces an unfrozen four-second window with a 25-second window after
history fills. Normal S3K ends before act reload; demanding S3K includes the
complete qualified AIZ→HCZ fixture. Water/transparency occur in LZ2/ARZ, dynamic
art/palettes and object/boss load span the matrix.

| Scenario | Fixture | Raw route rows | Frozen window | Input-plan SHA-256 |
|---|---|---|---|---|
| s1-normal | `s1/ghz1_completerun` | 0–5597 | 4000–5500 | `a261efca1f72ba300ac64c56ce76e16b0389fd633d8fe9bb34442a291979fe64` |
| s1-demanding | `s1/lz2_completerun` | 0–10172 | 4000–5500 | `cb36b9230510a70bb2024c8eaf74da415271c4acb92fc4322a2b3e70eaca4f61` |
| s2-normal | `s2/ehz1_fullrun` | 0–5851 | 4000–5500 | `f778f928e02197c3abb35de10a571bb71fec1198e04d900319d0fc489473ca73` |
| s2-demanding | `s2/arz` | 0–5072 | 3700–5000 | `bd25e57e4f8394e3053703b3fe042bae86058146dc23e73a0ba5dd18a466eca5` |
| s3k-normal | `s3k/aiz1_to_hcz_fullrun` | 289–5388 | 3700–5000 | `822e023ecd764e60522de8bb9c41241883a9699b91643a4e66fd61797d347811` |
| s3k-demanding | `s3k/aiz1_to_hcz_fullrun` | 289–20797 | 18711–20481 | `f3b7eb166507ed0200395a3193d41b8cc815baec0a6b357fd170d049e359b190` |

| Input-only normal Engine qualification | Input rows | Canonical compared | Errors/warnings | Measured executed frames |
|---|---:|---:|---:|---:|
| S1 longer normal GHZ1 | 5598 | 5595 | 0/0 | 1499 |
| S1 demanding LZ2 | 10173 | 9901 | 0/0 | 1496 |
| S2 normal EHZ1 | 5852 | 5852 | 0/0 | 1500 |
| S2 demanding ARZ | 5073 | 5064 | 0/0 | 1294 |
| S3K normal AIZ1 prefix | 5100 | 5099 | 0/0 | 1300 |
| S3K demanding complete handoff | 20509 | 20463 | 0/0 | 1770 |

Maintained full S3K: 20,512 presentations, 1,770 measured intervals, full rewind
at both boundaries (3601 retained frames), exact four terminal jobs and zero
unmatched completions. Longer normal S1 has 3600 retained frames at both bounds;
its CSV-enabled sampler saw 489 observations (not numeric baseline evidence).
Normal S2 also retains 3600. Complete rich LZ2 retry: 10,307 presentations,
rewind current/earliest 10173/6570. Private screenshots were visually checked;
all acceptance pixel/PCM checkpoints still need freezing for the optimization loop.

## Broad matched validation — completed before C8/tools

| Source/run | Ordinary reports/checks/failures/errors/skips | Time | Fresh guards |
|---|---|---:|---|
| `3ac1363ed` / `20261012T225115Z-b1b082de` | 3168 / 27631 / 26 / 0 / 64 | 3640.93s | 88 reports, 677 checks, 0 failures/errors/skips; 263.10s |
| `eca81166b` / `20261012T215139Z-2c945d94` | 3168 / 27645 / 26 / 0 / 64 | 3575.50s | 88 reports, 677 checks, 0 failures/errors/skips; 276.30s |

Both exited 1: **completed red ordinary suites, passing guards**, not a green
full suite. Selected 3170 ordinary classes plus guards. Candidate fingerprint
`dc8dbbcfa3e519c82c887e767bb06c7e8775284fe98c9068099cd29915645d37` was restored
exactly after an accidental documentation edit during that run; runtime code did
not change. Both diagnostic directories were consumed and ACK/deleted.

All 26 failed identities match the
[explicit inherited MHZ/LRZ/SSZ/DEZ table](../2026-10-12-mod-audit-remediation.md)
(its failure-identity table); 25 complete assertions and all 64 skip identities/
complete reasons match. Skips: 32 Infinite assumptions, 6 GL availability,
1 CPZ interaction, 1 missing BizHawk audio reference, 24 opt-in diagnostics.
The one SSZ second-parameter assertion (current width **800**, older prose 400)
has raw hash SHA base `23eaae1f14b8fdd3889bbaed36d28e6078f7259b9d47af973d615665fa9cad12`
versus candidate `13ac120a05a92dfcff17999e9920fd96d6cdd6bf89a4346ee53b611167406150`.
Do not strip hashes: bounded matched exact-method checks each had two tests,
one pass/one same frame-4018 failure, 0 errors/skips. All six semantic blob rows
match by complete identity, type, schema, scalar bytes and empty opaque arrays;
semantic digest `024257c7449c18148844d90ebfe35118fe3a30a2b7f637d54ac4f9a21bc1419b`.
The full scalar table is in the linked artifact. Probe SHA
`e52f7ce0b97c308cfe721c80bf940be7acc9384f6bf1eea76ab334fef6544f0b`; both originals
restored to `db4583214f4430c81cf25b7260a716358be575ff2d7772a6067be01367c2af1e`.
No production SSZ/formatter fix was made. C8/tools require their own combined
validation at delivery; this earlier broad result does not certify them.

## Exact command shapes and next work

All Java commands use a separately built, own-worktree classpath; never share
or copy target trees. Concrete classpaths/plan files/backend configs are external.

```bash
# Focused C8/input/authority checks (31 passed)
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py \
  -Dmse=off -Dtest=TestRuntimeInputPlan,TestPlayableSessionRules,TestHardwareTimingAuthorityGuard,TestTraceHydrateSwitchDefault test -B
# Domain focus (80 passed); original complete handoff and LZ2 included
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py \
  -Dmse=off -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dtest=TestPlayableSessionRules,TestS1Lz2CompleteRunTraceReplay,TestS3kAizTraceReplay,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils \
  "-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
# Normal longer S1 independent control: 1 test, no failures/errors/skips
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py \
  -Dmse=off -Ptrace-replay -Dsurefire.forkCount=1 -Dtest=TestS1Ghz1CompleteRunTraceReplay \
  "-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" test -B
python3 -m unittest discover -s tools/performance -p 'test_*.py' -v
# Offline compile, then private normal-Engine qualification (example S3K)
java -Xmx3g -cp "$OPENGGF_RUNTIME_CLASSPATH" com.openggf.tools.RuntimeInputPlan \
  src/test/resources/traces/s3k/aiz1_to_hcz_fullrun 0 0 289 999999 "$PLAN_JSON"
java --enable-native-access=ALL-UNNAMED -Xmx3g -cp "$OPENGGF_RUNTIME_CLASSPATH" \
  com.openggf.tools.RuntimeEngineBenchmarkTool "$PLAN_JSON" "$OPENGGF_ROM_ROOT" \
  "$REPORT_JSON" 18711 20481 "$QUALIFICATION_CSV"
python3 tools/performance/sample_runtime.py --pid "$OWNED_JVM_PID" \
  --log "$PROCESS_LOG" --report "$REPORT_JSON"
# Completed pre-C8 broad candidate; control adds --category all
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py --base 3ac1363ed2b2549d9e7c9d913b3b617b47e1bc53 \
  --rom-directory "$OPENGGF_ROM_ROOT" --max-minutes 120 --run
```

Next: collect repeated fresh-process original CPU/RSS/VRAM/tail/startup/load results
with builds/tests/profile runs absent. Profile that accepted runtime, rank measured
bottlenecks, test one isolated hypothesis, and require at least five alternating
paired baseline/candidate acceptance runs against original and previous accepted.
No repeatable >5% per-scenario regression is acceptable. Stop only at verified
50% in each game's score or five consecutive rejected performance experiments.

Protocol freeze: external `frozen-runtime-protocol.json`, SHA-256
`ae9cca7f1f746916c9ef03252f59d9ac28a23ba3af26fc2bf0cc59b76f11211b` before attaching
the source commit identity. CPU affinity is all 32 logical processors; governor
`powersave`, driver `amd-pstate-epp`, unchanged for all runs. Five original fresh
process runs per scenario precede optimization. Acceptance requires at least five
alternating paired runs, medians and variability, original and prior accepted
comparisons, exact qualified input/trajectory/count identities, and no repeatable
>5% scenario regressions. Framebuffers/PCM use separate normal live-capture clips
over the frozen windows, plus S3K 5250–5600 and 20180–20797 transition spans.
The detailed protocol contains all six full controller/trajectory gold digests.

The final startup/load instrumentation observes through the next normal update
after the first gameplay presentation, enclosing its audio/render/swap. Separate
`--load GAME ZONE ACT ROM_ROOT REPORT_JSON` uses LIVE readiness and fixed neutral
P1/P2 input with existing load controls, without a trace or route-score window.
S1 LZ2, S2 ARZ and S3K HCZ reach LEVEL on the real discrete renderer (135, 79,
129 presentation updates). These diagnose load cost, not complete-route fidelity.
Updated loading/input/session/authority focus passed 73 tests, 0 failures/errors/
skips; neutral-load-only final change compiled and all three LIVE probes passed.
CSV-disabled startup-boundary smoke retained the qualified controller/trajectory
digests and verified 240 actual frames. No smoke/profiling resource figures are
used as the original numeric baseline.

## Original numeric baseline — frozen

Source `184c9d54ed4fc2c1fed3cb6313653a95f9156e3d`; final external protocol SHA-256
`43f6fd83c89bcc9c373a5136b47fb68f1a5282f56dc278ffe48947ea705f0bf5` (full source
identity attached to the prior frozen protocol). Thirty runtime runs and fifteen
separate LIVE load runs completed. No builds/tests/profiles overlapped these
measurements; periodic process checks found no concurrent Maven or profiler.
Every runtime has exact qualified controller/trajectory hashes, controller rows,
actual measured simulation counts and full-route presentations, CSV disabled,
full rewind at the bounds, valid resident counters and zero shared VRAM. All
five full handoffs match the exact four terminal-job identities. Raw numeric
reports and compact aggregation are in the external task directory; no consumed
Maven diagnostics were retained.

CPU is total process time / verified simulation frames. Other columns show the
median of five per-run medians or peaks; tails are paced presentation intervals.
Resident GPU peaks are sampled at 50 ms, with the stated spike-coverage limit.

| Scenario | CPU ms/frame median | MAD | Min–max | RSS / peak MiB | Resident VRAM / sampled peak MiB | Frame p50 / p95 / p99 ms |
|---|---:|---:|---:|---:|---:|---:|
| s1-normal | 1.527685 | 0.046698 | 1.474316–1.607738 | 413.3 / 421.7 | 77.3 / 86.9 | 16.694 / 16.934 / 17.306 |
| s1-demanding | 1.417112 | 0.120321 | 1.256684–1.537433 | 429.1 / 557.6 | 66.0 / 70.0 | 16.814 / 17.053 / 17.204 |
| s2-normal | 1.400000 | 0.033333 | 1.286667–1.486667 | 439.3 / 440.4 | 70.0 / 70.0 | 16.817 / 17.095 / 17.292 |
| s2-demanding | 1.669243 | 0.085008 | 1.584235–1.808346 | 550.7 / 558.5 | 68.0 / 70.0 | 16.809 / 17.072 / 17.266 |
| s3k-normal | 2.569231 | 0.076923 | 2.415385–2.692308 | 546.8 / 558.5 | 79.1 / 79.1 | 16.791 / 17.285 / 17.476 |
| s3k-demanding | 2.581921 | 0.045198 | 2.531073–2.689266 | 491.5 / 625.9 | 83.5 / 88.1 | 16.783 / 17.247 / 17.436 |

Equally weighted frozen CPU scores (mean of the two scenario medians):

| Game | ms/simulated frame | 50% target |
|---|---:|---:|
| s1 | 1.472399 | 0.736199 |
| s2 | 1.534621 | 0.767311 |
| s3k | 2.575576 | 1.287788 |

Startup through first completed gameplay presentation, and separate LIVE fresh-load medians:

| Scenario / load | Process CPU ms | Wall ms |
|---|---:|---:|
| s1-normal startup | 2270.000 | 2966.397 |
| s1-demanding startup | 2450.000 | 2890.720 |
| s2-normal startup | 2320.000 | 2066.468 |
| s2-demanding startup | 2700.000 | 2075.699 |
| s3k-normal startup | 2310.000 | 763.758 |
| s3k-demanding startup | 2220.000 | 799.067 |
| s1 separate fresh load | 1060.000 | 2413.133 |
| s2 separate fresh load | 1260.000 | 1505.522 |
| s3k separate fresh load | 1210.000 | 2364.835 |

The observed CPU spread is significant in demanding S1 and several other
scenarios. Keep every result; do not substitute a favorable original run or
credit warm-cache/drift changes. Paired alternating baseline/candidate evidence
must exceed this noise and satisfy every per-scenario regression guard.

Whole-process profiling has begun in separate fresh runs of this accepted
implementation. Verified async-profiler 4.5 official release archive SHA-256
`89546fbb9ee0fc5496c7edd4099b0709489bc78b0d8057ccbb4b801f6b032b62`. CPU stacks
are collected only over the frozen window, alongside OS per-thread user/system
CPU deltas; profiling resource numbers are not acceptance numbers. No measured
bottleneck or optimization hypothesis has been selected yet.

### First profiling audit

Separate six-scenario CPU profiles all retained the frozen controller/trajectory
digests. OS per-thread user/system deltas independently cover workers/GC.
S1 normal's C2 thread consumed 98 ticks out of 236 total observed thread ticks;
S1 demanding 78/211, S2 normal 56/212, S2 demanding 78/231, S3K normal 79/316,
S3K demanding 25/493. Main-thread and driver work remain substantial; S3K
demanding's `java:gdrv0` used 217 ticks (190 user/27 system), versus main 206.
At 100 Hz these are measured contributions, not allocation or FPS proxies.

An unprofiled fresh S1 normal control retained its exact trajectory and showed
C2 91 ticks (90 user/1 system), main 105, confirming compiler work is not merely
profiler-attach overhead. Stack profiles have a coverage limit: the busy native
`gdrv0` thread is absent from async-profiler's sampled roots, although OS CPU
deltas include it. Do not normalize visible Java/native samples into a claim
about all-process savings. JVM debug symbols were unavailable, so compilation
task labels are being collected separately before ranking a concrete hypothesis.
No production performance edit or accepted/rejected experiment exists yet.
### Experiment P1 — shared execution-order preparation (rejected)

Isolated `feature/ai-perf-exec-order`, parent `7ffa810a48351283ed654417a7769ac27088cb0e`.
Measured unprofiled S1 compiler CPU is 91 ticks; a separate HotSpot compilation
log has repeated `ObjectManager.updateCounterBasedExecThenLoad` tasks, about
0.242 seconds elapsed across three tasks near the window. This motivates a small
method-boundary experiment, not a predicted whole-engine percentage. Native
driver CPU and sampling coverage limits remain separate bottlenecks.

Extract the two identical position-snapshot and execution-index blocks into
shared private helpers. Each ROM path retains its exact local clearing order;
all callbacks, active/dynamic iteration order, fixed-slot exclusions, slot
allocation, dispatch, unload, retirement and placement remain unchanged.
HotSpot may inline the helpers or move compilation elsewhere; reject if paired
whole-process savings do not exceed noise. No JVM flags or game behavior changes.

Candidate `1227f4cb7210219cf420f6c0205fbc4b703868aa`; original numeric
source `184c9d54ed4fc2c1fed3cb6313653a95f9156e3d`. Focused slot/callback/lifetime/
handoff-rewind checks passed 35 tests; frozen route/terminal checks passed 20,
all without failures/errors/skips. Presented FFV1 frame hashes and decoded
48 kHz stereo PCM matched byte-for-byte on all six routes. The demanding S3K
capture covered 15,550 presentations from cursor 4,961 through EOF, including
AIZ act reload and AIZ → HCZ. Long capture finalization was awaited before
engine cleanup; early shutdown had discarded an incomplete control recording.
That discarded capture is not correctness evidence.

Preliminary alternating fresh-process screen (CPU ms/simulated frame):

| Scenario | Complete pairs | Baseline median | Candidate median | Change |
|---|---:|---:|---:|---:|
| s1-normal | 2 | 1.527685 | 1.577718 | +3.28% |
| s1-demanding | 2 | 1.383690 | 1.463904 | +5.80% |
| s2-normal | 1 | 1.413333 | 1.386667 | −1.89% |
| s2-demanding | 1 | 1.684699 | 1.731066 | +2.75% |
| s3k-normal | 1 | 2.392308 | 2.530769 | +5.79% |
| s3k-demanding | 1 | 2.553672 | 2.649718 | +3.76% |

S1 normal paired changes +5.26%/+1.30%; demanding +4.72%/+6.93%.
All four S1 candidate runs were slower. This conservative rejection screen
established no benefit; it is **not** a completed five-pair acceptance matrix
or a claim of a statistically established >5% regression. Demanding S1 peak
RSS also varied −5.56%/+10.54%, requiring investigation if a later candidate
otherwise merits acceptance. No measurements were discarded in favor of a
faster run. First-round separate LIVE loads completed; one later S2 baseline
was unpaired, and its in-progress candidate was cancelled. Those are excluded
from paired conclusions.

Commands used the fixed protocol and source-specific classpaths:
`python3 ${OPENGGF_PERF_TASK_DIR}/visual_batch.py`, then
`python3 ${OPENGGF_PERF_TASK_DIR}/p1_paired_batch.py`; each measured child ran
`RuntimeEngineBenchmarkTool <frozen-plan> ${OPENGGF_ROM_ROOT} <report> <measure-from> <measure-to>`
with `--enable-native-access=ALL-UNNAMED -Xmx3g`, no qualification CSV,
capture or profiling. The external sampler command was
`python3 tools/performance/sample_runtime.py --pid <owned-java-pid> --log <runtime-log> --report <report>`.
Completed per-run reports and decoded capture controls are in the external task
directory; no raw logs are added to Git. The original baseline is unchanged.

Reject the source extraction and retain only this evidence. Accepted gains:
zero; consecutive rejected performance experiments: **one**. Compiler-heavy
fresh-process CPU remains real, but this method-boundary change did not help.
Next investigate common measured FM/PSG synthesis work, or real rendering-driver
work (particularly demanding S3K), with an exact PCM/framebuffer control before
any acceptance benchmark. Do not treat sampled Java coverage as whole-process
coverage or a helper speedup as an engine saving.
