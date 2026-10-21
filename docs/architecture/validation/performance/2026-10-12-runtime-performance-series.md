# Runtime performance series — 2026-10-12

## Status and source

The required accuracy prerequisite and complete-runtime baseline are qualified.
**The original numeric baseline is frozen from five fresh processes per scenario.
Five valid consecutive performance experiments were rejected. Optimization stopped at the requested checkpoint with 0% accepted gain in each game. Accuracy/tooling delivery is validated, integrated, pushed and cleaned up.** The intended
metric selected for this frozen series is total process CPU time per simulated frame, equally weighted between
normal and demanding scenarios in each game. No saving is attributed to accuracy
repairs, upstream changes, Java migration, or replacing an invalid helper harness.
The original request allows CPU, resident memory, or measured GPU VRAM, using one
metric consistently across all games. The 50% stretch target is **cumulative**
across accepted changes; individual changes need only show a verified gain. This
CPU series has none. The checkpoint is delivered on `develop`; see the final delivery record below.

The user authorized repairing inherited AIZ → HCZ failures after both complete
fixtures failed their untouched-source controls. Local checkpoints:

| Purpose | Commit |
|---|---|
| Initial fast-forwarded develop source (from `15f3cf371`) | `a0e2b79cc3a47cedd11a0f9daf6a32ea4ced19cb` |
| Native handoff repair C1–C7 | `1baa8e6dc9c5de5223a64633e7043411845a997f` |
| Updated-base merge; sole conflict retained both hazard lessons | `eca81166b7bbb06eeb557f92f4a804029130a5b4` |
| Updated matched develop base | `3ac1363ed2b2549d9e7c9d913b3b617b47e1bc53` |
| C8 selected-world binding, input-only runtime tools and detailed qualification record | `48bc7c34bd6068199763f62620a5193586c67293` |

Development used `.worktrees/ai-runtime-perf-series` and a clean control at
`4a9bec1eaf86e7b70959113e484315c5013c1b7c`; updated-base merge was
`4a0b2cd6bdf3050ffd5044c4c40f4e29aa3d2723`. Both trees and all six experiment/
original-baseline trees are now removed after verification and push. Upstream
Mod compatibility and queue changes receive no task performance credit.
Unrelated dirty submodules/files were preserved.
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
| C8 | Construction/reset bind the selected world, retaining pre-session bootstrap fallback. A normal S3K world with S2 bootstrap otherwise wrote Tails' $4000 instead of `sub_13ECA`'s $7F00 (sonic3k.asm:26846) for 767 early observations. Both regression paths failed before the fix. Rebinding legacy config alone was disproved. |

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

## Reproducible command shapes (baseline and qualification)

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

Historical baseline plan: collect repeated fresh-process original CPU/RSS/VRAM/tail/startup/load results
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
### Experiment P2 — batch PSG ticks between generator events (rejected)

Isolated `feature/ai-perf-psg-quiet`, parent `010e8aef3`. The six-scenario
profiles show common PSG output/tick work: visible exclusive samples in
`emitLevelChanges` are 51/1,931 S1 normal, 77/1,663 S2 normal, and 46/1,825
S3K demanding, with additional counter/noise work. These are profiler-visible
samples, not complete process shares; native driver sampling misses remain.
The saving ceiling is modest and must be demonstrated in complete runtime
measurements. Compiler work and rendering remain separate bottlenecks.

Hypothesis: checking all four outputs on every internal tick spends CPU when
none can change. Render in spans ending at the next generator expiry or render
boundary; emit each level change at the same original tick and run the existing
expiry logic. Quiet-span arithmetic must preserve every counter even when muted,
all polarity/LFSR evolution, discrete period-zero behavior, integrated held-high
periods, prior-period countdowns after a write, and pending changes at a render
endpoint. Writes remain at their existing render boundaries. No persistent
state, public API, sample rate, noise model, gain, resampling or rewind changes.

Controls: public-API single-tick versus batched rendering at the chip tick rate,
both variants, period 0/1/2/1023, randomized intermediate periods, all noise
modes, chip-type changes, mute/panning and snapshot restore; existing hardware,
slicing, physical-write/admission, snapshot and presentation-rewind checks.
Require frozen full-route controller/trajectory/frame counts and identical
decoded presented pixels/PCM, then at least five alternating fresh-process
pairs for acceptance. Reject early if a correctness control fails or a
preliminary screen establishes no worthwhile benefit; an early rejection is
not a completed acceptance matrix. Original baseline remains fixed.

The new single-tick/batch control passed on the untouched source (1 test,
no failure/error/skip) before the edit. Candidate focused audio selection
passed 92 checks; physical-write/admission selection passed 41, both without
failures/errors/skips (the overlapping fade check was rerun with explicit
absolute ROM paths). Commands: queued Maven `--lean -Dmse=off -Dtest=` with
`TestPsgChipHardwareBehaviour,TestPsgChipSnapshot,TestPsgSfxAdmissionNoiseSilence,TestS3kFadeOutPsgSilence,TestPsgVolumeChangeSemantics,TestS3kOverriddenPsgEnvelopeTiming,TestAudioManagerRewindSuppression,TestAudioPresentationProducerRewind`,
then `TestS3kFadeOutPsgSilence,TestChipWriteObserver,TestSmpsPhysicalDevice,TestSmpsPhysicalPort,TestSfxAdmissionMutationJournal,TestS3kProductionAdmissionObservation`
and all three absolute canonical ROM properties, `test -B`. These are focused
checks, not broad delivery validation. Full runtime pixel/PCM controls and
performance acceptance remain pending; P2 is not accepted.

Candidate `97bcc5a72e82be92ca565d7b3bc26c2ad5efcb4d`. All six normal-runtime
presented captures matched the immutable baseline's decoded frame and PCM
hashes, controller/trajectory digests and presentation counts. The demanding
S3K control includes 15,550 presentations across both AIZ transitions and HCZ
handoff. These capture runs were separate from acceptance measurements.

Rejected preliminary CPU screen (ms/simulated frame):

| Scenario / pair | Baseline | Candidate | Change |
|---|---:|---:|---:|
| s1-normal / 1 | 1.387592 | 1.460974 | +5.29% |
| s1-normal / 2 | 1.407605 | 1.514343 | +7.58% |
| s1-demanding / 1 | 1.363636 | 1.510695 | +10.78% |

S1 normal repeated an increase above 5%, blocking acceptance. The candidate
was slower than both fresh matched normal baselines; comparing only with the
higher original median would have concealed that result. No acceptance or
50% claim is made: the five-pair/all-game matrix was **not completed**. An
in-progress second demanding baseline and an earlier unpaired S2 baseline
were cancelled and are excluded. Original CPU scores remain unchanged.

Commands: `python3 ${OPENGGF_PERF_TASK_DIR}/visual_p2_batch.py` reused
unchanged baseline media controls and captured all six candidate routes;
`python3 ${OPENGGF_PERF_TASK_DIR}/p2_paired_batch.py`, then
`python3 ${OPENGGF_PERF_TASK_DIR}/p2_s1_screen.py` prioritised the second S1
pairs after the initial negative screen. Per-child JVM flags, input plans,
windows, rewind/audio/render settings and external sampling commands were
identical to P1. Pair order changed for rejection screening; scenario weights
and acceptance obligations did not. Only verified owned process groups were
stopped, and unrelated desktop/audio processes were preserved.

Revert the source batching. Keep the public-API randomized equivalence test
as a useful independent chip contract check; it passes on untouched source.
Accepted gains: zero; consecutive rejected performance experiments: **two**.
The event-batching hypothesis passed functional controls but did not improve
whole-process runtime. Do not credit its reduced helper iteration count.
Next measured common rendering bottleneck is HScrollBuffer upload: inclusive
visible samples 96/1,931 S1 normal, 151/1,663 S2 normal, 167/1,825 S3K demanding.
S3K additionally has substantial atlas upload/driver work. As before, visible
sample shares omit busy native driver threads and are not process percentages.
### Original build isolation and experiment P3 — unchanged HScroll upload (rejected)

Original numeric source stays `184c9d54ed4fc2c1fed3cb6313653a95f9156e3d`.
A dedicated `feature/ai-perf-original` worktree was built with the same JDK
and dependencies before rebuilding any accepted-tree classes. All **7,354
class files and 145 other classpath resources** matched the existing original
build byte-for-byte. Sorted class-path/hash manifest SHA-256
`125abe28c5b434e441d55fef8199dee56591acf1ac0c1bb3ee84e3ff59411d91`.
Only the external baseline classpath location changed to this immutable build;
no build trees were shared/copied and no original measurements or protocol
fields changed. Build command: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off compile -B`.

P3 is isolated at `feature/ai-perf-hscroll-cache`, parent `48cbbc76e`.
HScroll upload is common measured work (visible inclusive samples S1 normal
96/1,931; S2 normal 151/1,663; S3K demanding 167/1,825). Its synchronous
texture updates also contribute native driver work not fully visible in these
profiles. The hypothesis is that unchanged normalized scanline values need
no data upload. Cache the exact values last uploaded by this texture owner;
retain binding-to-zero behavior even when skipping, and invalidate on cleanup.
Do not quantize, approximate, change format/resolution or reuse another
resource's cache. Array/view overloads share the same normalized contents,
including signed packed-word selection and zero-filled missing rows.

Controls cover real 1D texture readback, repeated uploads through both
overloads with a pre-existing binding, null input, zeros, signed extrema,
foreground/background selection and identical-data resource/context recreation.
Run these on unchanged source before implementation, then candidate, in the
owned private hardware rendering environment. Existing rewind/lifetime checks
and all frozen normal-runtime pixel/PCM, input/trajectory and frame-count
controls remain required. Five paired runs across all scenarios are required
for acceptance; preliminary rejection does not certify full coverage.

Unchanged source and candidate native selections each passed 2 tests, no
failures/errors/skips. Command in the private hardware Wayland environment:
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestHScrollBufferCompatibility,TestScrollBufferUploadNative -Dopenggf.scrollNative=true test -B`.
The current change adds only owner-private exact-value cache storage, keeps
texture binding zero on a hit, and invalidates the cache in cleanup.
A separate diagnostic-only classpath counts actual upload decisions over
the frozen runtime window; those instrumented CPU numbers are not acceptance.
Require workload digests/frame counts to match before using its frequency
evidence. Production acceptance classes contain no diagnostic counters.

P3 candidate `33576af86f33ec3de2989787d74290a5529e2802`. Separate diagnostic
profiles retained the frozen input/trajectory digests and measured frame
counts. S1 normal had 1 unchanged upload out of 1,500 calls; demanding S1
had 712/1,500. The latter justified a runtime screen, but helper call counts
were never credited as CPU savings. All six normal-runtime frame/PCM captures
matched exactly, including the extended S3K transitions, explicit full-route
frame counts and final prepared hardware jobs.

Preliminary CPU screen (ms/simulated frame):

| Scenario / pair | Baseline | Candidate | Change |
|---|---:|---:|---:|
| s1-normal / 1 | 1.574383 | 1.507672 | −4.24% |
| s1-normal / 2 | 1.547698 | 1.641094 | +6.03% |
| s1-demanding / 1 | 1.356952 | 1.437166 | +5.91% |
| s1-demanding / 2 | 1.383690 | 1.323529 | −4.35% |

Both equally weighted S1 round scores were slightly slower (+0.46%, +1.13%),
with opposite per-scenario directions. No saving beyond noise was established.
Conservatively reject; this is not a statistically established material
regression, a complete five-pair matrix, or evidence about S2/S3K CPU gains.
Original scores stay fixed. Commands: diagnostic-only
`python3 ${OPENGGF_PERF_TASK_DIR}/hscroll_profile_batch.py` (separate named
classpath with upload counters), `python3 ${OPENGGF_PERF_TASK_DIR}/visual_p3_batch.py`
(production classes), then `python3 ${OPENGGF_PERF_TASK_DIR}/p3_s1_screen.py`
(two baseline/candidate pairs per S1 scenario, no captures/counters/CSV).

Restore the production HScroll implementation; retain the native repeated
upload and recreation checks, which passed on original source. Accepted
gains: zero; consecutive rejected performance experiments: **three**.
Next inspect the other common measured rendering costs rather than inferring
engine savings from skipped GL calls.
### Experiment P4 — respect tilemap shader location caching (rejected)

Isolated `feature/ai-perf-uniform-cache`, parent `3cd17e86c`. The accepted
source profiles contain repeated `TilemapShaderProgram.cacheUniformLocations`
work in ordinary rendering: S1 normal 24/1,931 visible samples and S2 normal
20/1,663. These are modest visible contributions, not total-process fractions.
The owning source override calls its superclass but proceeds to query all
40 tilemap locations on every draw despite the superclass's `uniformsCached`
flag. The renderer calls it during each draw.

Hypothesis: return when the existing cache flag is set, retaining all per-draw
uniform value writes. Inherited `setProgramId` already invalidates the flag,
including same-ID reassignment after relink; new shader instances begin
uncached. No shader, uniform value, graphics resolution, effect, GL version,
texture lifetime, gameplay, audio or rewind changes. A package-private query
seam permits native query-count checks without changing public/Mod API contracts.

The [OpenGL 4.1 specification §2.11.7](https://registry.khronos.org/OpenGL/specs/gl/glspec41.core.pdf)
(pp. 73–74) assigns locations on successful link and retains them until
relink. The native control exercises the shipping shader, repeated calls,
explicit same-ID invalidation, a test-only partial replacement shader with
inactive optional uniforms, ownership cleanup and real uniform-value readback.
An initial test wrongly assumed the shipping shader had inactive uniforms;
all forty are active. The corrected replacement fixture covers that case.
Before the cache guard, the corrected control failed at
`locationsAreReusedUntilTheProgramIsReassigned`: repeated calls queried
40 then 80 locations (1 failure, no errors/skips). This reproduces the
performance bug. Candidate native shader/cache and scroll/lifetime controls
passed 2 tests, no failures/errors/skips. The query count stayed at 40 on
repeated calls and advanced only at explicit invalidation. Per-scenario
visible location-cache samples across the full matrix were 24/1,931,
11/1,729, 20/1,663, 13/1,793, 10/2,157 and 15/1,825 in frozen scenario order.
Whole-process performance acceptance remains pending.

Native command is queued Maven `-Dmse=off -Dtest=TestTilemapUniformCacheNative
-Dopenggf.shaderNative=true test -B`; the candidate selection additionally
includes `TestScrollBufferUploadNative -Dopenggf.scrollNative=true`. Before
starting the test JVM/Engine, the launcher now verifies the exact owned
display process/group, private 0700 runtime directory, owned socket and live
connection. DISPLAY and ambient DBus settings are removed; isolation failure
never falls back to the user's desktop. This enforces the updated AGENTS
instructions and does not alter frozen workload/configuration or JVM flags.

Candidate `2a1e413a9ec449ee7141cf41f89ed2f8a559949d`. All six production
frame/PCM controls matched baseline, with full input/trajectory/presentation/
executed-frame checks and S3K terminal prepared jobs unchanged.

Rejected preliminary CPU screen (ms/simulated frame):

| Scenario / pair | Baseline | Candidate | Change |
|---|---:|---:|---:|
| s1-normal / 1 | 1.474316 | 1.567712 | +6.33% |
| s1-normal / 2 | 1.380921 | 1.607738 | +16.43% |
| s1-demanding / 1 | 1.316845 | 1.470588 | +11.68% |

Normal S1 repeatedly exceeded the 5% CPU regression threshold, blocking
acceptance. Helper query reduction is not engine improvement. The cause of
the whole-process increase is not attributed by these timing runs; compiler
variability and waiting remain separate profiling questions. The second
demanding baseline completed but its candidate was cancelled; this unpaired
run is excluded. No S2/S3K acceptance timing or completed five-pair matrix
is claimed. Original baseline scores remain fixed.

Commands: `python3 ${OPENGGF_PERF_TASK_DIR}/visual_p4_batch.py`, then
`python3 ${OPENGGF_PERF_TASK_DIR}/p4_s1_screen.py`, identical frozen child
arguments/flags and external sampling. Retain native uniform value/lifetime
checks that pass on unchanged source, without the rejected query-count
expectation or production query seam. The repeated-query behavior remains
in production; its isolated control and rejection evidence are preserved here.
Accepted gains: zero; consecutive rejected performance experiments: **four**.
Profile actual frame waiting/pacing CPU before selecting the fifth hypothesis;
do not tune constants from wall-time or use a faster helper as acceptance.

The retained `TestTilemapUniformLifetimeNative#uniformWritesSurviveRepeatedCachingAndProgramReplacement`
passed on unchanged production source (1 test, no failure/error/skip), using
the private display and `-Dopenggf.shaderNative=true` in the queued Maven
command. It verifies actual uniform writes after repeated caching, same-ID
reassignment, replacement by a partial shader, and ownership cleanup. The
rejected query-count expectation and query seam are not retained.

### Pacing profiling audit and experiment P5 — PSG level comparison (rejected)

Separate fresh S1 normal original and rejected-P4 diagnostics used an
instrumented Engine copy to count deadline-spin episodes and current-thread
CPU time, never elapsed waiting time. Both matched frozen controller/trajectory
digests, executed frames and presentations; both recorded **zero episodes and
zero spin CPU** over the window. Thread CPU support/enabling was checked before
launch. Commands `python3 ${OPENGGF_PERF_TASK_DIR}/pacing_profile_batch.py` and
`python3 ${OPENGGF_PERF_TASK_DIR}/pacing_p4_profile_batch.py`. Their named
classpaths are profiling-only; production/acceptance builds remain untouched.
Do not infer a pacing cause for the rejected CPU screens or tune the spin margin
from this evidence. This diagnostic is not a fifth optimization experiment.

P5 is isolated at `feature/ai-perf-psg-levels`, parent `974b43d3e`. Common
measured output-comparison work remains: `PsgChip.emitLevelChanges` exclusive
visible samples S1 normal 51/1,931, S2 normal 77/1,663, S3K demanding 46/1,825.
Whole-engine saving is plausibly modest; these are not process percentages.
Hypothesis: repeated amplitude array loads and the four-channel loop spend
CPU at every chip tick. Cache the eight amplitudes in render-call locals and
expand the four output comparisons in their original channel order. Keep
every generator tick, counter/LFSR event, delta-buffer call, render boundary
and public state contract unchanged. No batching, new persistent state,
allocation, sample-rate, mute/panning, resampling, waveform or rewind changes.
Writes/configuration occur between renders per the existing PSG time contract
(§2.4), so amplitudes are constant within one call; polarity/LFSR are still
read afresh at each original tick.

Run existing hardware/slicing/mixed-register equivalence, snapshot, physical
write, admission and audio-rewind controls with explicit canonical ROM paths.
Require all frozen normal-runtime pixel/PCM/input/trajectory/frame/terminal
controls, then five alternating fresh-process pairs for acceptance. A bounded
negative screen may reject without claiming full timing coverage. If this is
the fifth consecutive rejection, end optimization and deliver the checkpoint
with original scores, achieved gains, rejection evidence and next bottleneck.

### Active goal and experiment ledger (2026-10-13)

Tracked goal: whole-process CPU reduction of at least 50% in each frozen game
score, or an evidence-backed checkpoint after five consecutive rejections,
followed by required delivery validation/integration/cleanup. Original baseline
is fixed at `184c9d54ed4fc2c1fed3cb6313653a95f9156e3d`; accepted gains remain zero.

| Idea | Focused / media controls | Paired CPU result | Decision |
|---|---|---|---|
| P1 execution-loop extraction | Passing / six scenarios identical | All four S1 comparisons slower | Rejected |
| P2 quiet PSG batching | Passing / six scenarios identical | S1 normal +5.29%, +7.58%; demanding +10.78% | Rejected |
| P3 identical hscroll upload reuse | Passing / six scenarios identical | S1 weighted pairs +0.46%, +1.13%; direction unstable | Rejected |
| P4 uniform locations once per program | Passing / six scenarios identical | S1 normal +6.33%, +16.43%; demanding +11.68% | Rejected |
| P5 PSG amplitude locals / ordered comparisons | 123 tests, zero failures/errors/skips; six media controls identical | Five S1 pairs: score −2.98%, uncertainty crosses zero | Rejected |

Before/after CPU is total process time per verified executed frame, including
compiler/driver/audio workers and GC. Negative screens are bounded rejections,
not five-pair acceptance matrices; their individual tables above preserve
coverage and variability. Astra completed a user-authorized read-only
review of measured bottlenecks and interpretation; suggestions remain hypotheses
until tested. No concurrent builds/tests/profiling during acceptance timings.

### Astra review (read-only, 2026-10-13)

User-authorized `OpenAI GPT-6-Astra`, native delegated task
`/root/astra_perf_review`, made no edits and ran no builds/tests/benchmarks.
It corroborated P5's amplitude lifetime and tick/delta ordering under the existing
between-render write contract. Full production PCM/state comparison remains
required and all six P5 media controls subsequently passed.

P3 is a rejection for no demonstrated benefit, not proven regression. C2 thread
CPU is part of the frozen fresh-process cost: S1 normal 98/236 OS ticks, S2 normal
56/212, S3K demanding 25/493. S3K demanding driver work is 217/493 ticks versus
206 on main, so visible profiler counts cannot estimate total-process percentages.
The two zero-spin diagnostics do not support changing the deadline margin.

Best checkpoint hypotheses, **unimplemented and unaccepted**:

1. Measure driver command generation and small texture updates: demanding S3K
   has 272 visible Gallium samples under `PatternAtlas$GlUploadSink.upload` and
   162 under hscroll upload. The atlas small-change path (`PatternAtlas.java`,
   lines 770–791 at accepted source) issues per-tile uploads. Consider contiguous
   upload coalescing only after measuring counts/bytes and driver-thread CPU,
   preserving art versions, flush boundaries and sprite ordering. P3 tested
   identical hscroll reuse, not atlas coalescing.
2. Profile coherent fast-YM synthesis: `FastYm2612Dsp.renderChannel` exclusive
   visible samples are 102/86/70 in S1 normal/S2 normal/S3K demanding, with
   additional operator/envelope costs. Measure the full chip/DSP/resampler path;
   preserve register admission, DAC, envelope and snapshots. Its achievable
   whole-process saving is unknown.

Neither idea overrides the five-consecutive-rejection stopping rule.

### Delivery base reconciliation (pending validation)

Main and `origin/develop` advanced to `13add5b0b918a1f7ff7e343c8ccc858ad4527d85`.
`git fetch origin` and `git pull --ff-only origin develop` completed on main
without switching its branch or changing unrelated files/submodules. The sole
upstream commit changes routine validation selection/documentation to smoke;
it changes no runtime production Java. The original numerical source, protocol
and scores remain immutable. Updated-base build equivalence and combined
validation remain pending, and no upstream performance gain is attributed.

The current repository instruction expressly supersedes older routine
full-suite delivery requirements: release-only exhaustive tests are not selected
for this task. Use actual updated-base change-based smoke selection with fresh
guards and required focused ROM/trace/native checks; compare outcomes, failures
and skips by identity. The earlier completed ordinary-suite results remain
historical evidence, not a pass for subsequent C8/tool additions.

P5 source `a9ab1e2b023a90b1f17d5b8de49dcd32ac80c8d9` passed all six
normal presented framebuffer/PCM controls, including 15,550 presentations
across the complete demanding S3K route. Controller/trajectory digests, frame
counts, terminal hardware jobs and sample counts are identical. Command:
`python3 ${OPENGGF_PERF_TASK_DIR}/visual_p5_batch.py`.

Initial S1 paired screen (ms/frame): normal 1.541027→1.494330 (−3.03%)
and 1.541027→1.647765 (+6.93%); demanding 1.336898→1.303476 (−2.50%)
and 1.377005→1.270053 (−7.77%). This mixed result is **not acceptance** or
a demonstrated repeatable normal-route regression. Extend S1 to five fresh
pairs with `python3 ${OPENGGF_PERF_TASK_DIR}/p5_s1_five_screen.py` before
deciding whether to complete the other frozen scenarios. No settings, windows,
weights or source change. No gain accepted and rejection count remains four.

### P5 timing-isolation audit — rejection withdrawn

The preliminary fifth-rejection claim in `c5d40485e` is withdrawn. Queue
telemetry identified another worktree's Maven test from 10:08:51–10:10:02 UTC,
overlapping the third S1 normal pair. Its +15.35% result and the matching baseline
are excluded from all performance evidence. P5 remains unaccepted and pending;
consecutive valid rejected hypotheses remain **four**. No production gain retained.

Original numerical runs (02:29–03:42 UTC report completions) and P1–P4 timing
intervals have no overlapping completed Maven jobs in the shared queue history.
P5 pairs 1/2 remain valid. A separate short foreign test at 10:05:29–10:05:57 UTC
was after pair 2 had finished and before pair 3's measurement, but that whole
third process pair is conservatively excluded anyway. The raw excluded pair
remains task-external as invalid evidence, not an acceptance result.

The first combined delivery category run `20261013T101218Z-7e7ec548` at
`437eb321a` was cancelled to repair measurement isolation. It is incomplete;
no suite pass is claimed. The runner stopped owned Maven children and its
diagnostics were inspected/acknowledged. Resume validation after performance
work is final.

Retry the same pinned P5 source `a9ab1e2b0` with
`python3 ${OPENGGF_PERF_TASK_DIR}/exclusive_p5_screen.py`, which holds the
existing shared Maven admission lease exclusively for the full batch. This
blocks scheduled tests without deleting locks or stopping foreign work. The
original source/protocol/flags/routes/windows/settings/weights remain unchanged.
Timing remains separate from builds/tests/profiling; detect bypassing build
processes and fail the run rather than count contaminated observations.

The exclusive-test-only retry detected two subsequent bypassing Maven builds
and stopped its own batch. Neither incomplete attempt counts as a performance
result. The second identified source was `.worktrees/ai-second-strike-routes`;
foreign work was never stopped or edited. Build-only commands intentionally
bypass shared test admission, so test exclusivity alone was insufficient.

The next retry also holds the existing per-worktree build leases for 122
registered linked worktrees (plus the admitted task-tree lease) before launching
any measured JVM. Existing builds finish first; new wrapped builds/tests wait.
All leases are OS-owned, inherited by the batch, and released through scoped
cleanup; no lock file is deleted. A 0.5-second bypass-process guard remains.
Source, flags, routes, frame windows, settings, controller plans, scenario weights
and process/residency counters are unchanged. The external queue/guard overhead
is common to both sources and is not charged as application CPU. Original
baseline scores remain fixed; this isolation repair contributes no claimed gain.

Updated-base build comparison (original `184c9d54e`, updated task `437eb321a`):
7,498 existing files are byte-identical, none missing. The sole changed existing
resource is `version.properties`, where only `app.commit` changes; version,
base version and dirty=false agree. Added classes are the three lossless
`RuntimeEngineVisualControl` diagnostic classes, unused by acceptance. No
upstream runtime saving is attributed. The actual P5 build's generated metadata
is commit `974b43d3e`, dirty=true from its pre-commit focused build; its PSG
bytecode is pinned to the final experimental source `a9ab1e2b0` (only Javadoc
changed after compilation), and all six production media controls matched.
Metadata differences are explicit; no compilation is performed during timing.

After repeated bypasses, the user authorized a 30-minute quiet interval. All
four other active T3 threads and their owned helpers confirmed no build/test,
direct javac, replay/capture, benchmark or profiler work would launch until
2026-10-13 11:05 UTC or an earlier release. The original workload is retried
with admission/build leases and the bypass guard still enabled. Record release
and final audit before claiming this interval complete.

### Final P5 decision — fifth valid rejection (2026-10-13)

The contaminated and interrupted attempts above remain excluded. The complete
retry finished at 11:04:59 UTC, exit 0, with no bypass interference recorded.
All admission/build leases released, and release messages were delivered to
the four other threads. A final-minute extension was coordinated as a precaution;
completion preceded even the original 11:05 deadline.

P5 source `a9ab1e2b023a90b1f17d5b8de49dcd32ac80c8d9` is **rejected for
no verified score saving beyond noise**, not a demonstrated repeatable CPU
regression. All 123 focused checks and six complete lossless pixel/PCM controls
passed. Previous accepted implementation and original baseline have the same
production performance code; the production PSG change is reverted.

| Pair | Normal baseline | Normal candidate | Change | Demanding baseline | Demanding candidate | Change |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 1.541027 | 1.494330 | -3.03% | 1.336898 | 1.303476 | -2.50% |
| 2 | 1.541027 | 1.647765 | +6.93% | 1.377005 | 1.270053 | -7.77% |
| 3 | 1.567712 | 1.587725 | +1.28% | 1.370321 | 1.323529 | -3.41% |
| 4 | 1.440961 | 1.501001 | +4.17% | 1.397059 | 1.336898 | -4.31% |
| 5 | 1.547698 | 1.440961 | -6.90% | 1.229947 | 1.390374 | +13.04% |

CPU median / MAD / range in ms per verified simulated frame:

- s1-normal, baseline: 1.541027 / 0.006671 / 1.440961–1.567712.
- s1-normal, candidate: 1.501001 / 0.060040 / 1.440961–1.647765.
- s1-demanding, baseline: 1.370321 / 0.026738 / 1.229947–1.397059.
- s1-demanding, candidate: 1.323529 / 0.020053 / 1.270053–1.390374.

The equally weighted frozen S1 score is baseline **1.455674**, candidate
**1.412265 ms/frame**, nominal −2.98%. Original frozen S1 score remains
**1.472399**; comparison to it is nominal −4.08%, never credited as accepted
saving. Across actual paired rounds, score change median is −0.0073%, MAD
0.9041 percentage points, range −2.784% to +1.933%. Exhaustive paired bootstrap
(5⁵ = 3,125 resamples; same round indices for both scenarios and sources) of
the frozen score gives candidate-minus-baseline 95% interval **−0.056778 to
+0.026845 ms/frame**. It spans zero; the nominal median saving is insufficient
for acceptance. This is S1-only timing coverage; no S2/S3K timing or complete
six-scenario acceptance certificate is claimed. Their media controls did pass.

S1 normal median RSS/peak RSS/VRAM/peak VRAM/p95/p99 changes are
−2.73/−2.32/−0.01/−0.01/−0.14/−0.29%; demanding
−5.78/−1.53/−0.01/−0.01/−0.17/−0.29%. Individual RSS differences varied
widely (including positive demanding pairs); these are reported as variability,
not an accepted memory gain or a clean memory certificate. No allocation
proxy or requested texture storage substitutes for resident measurements.
Startup/extra live-load comparisons were not completed for this rejected candidate.

Commands: `python3 ${OPENGGF_PERF_TASK_DIR}/visual_p5_batch.py`, initial
`p5_s1_screen.py`, then corrected `exclusive_p5_screen.py` retry around
`p5_s1_five_screen.py`. Input/controller/trajectory/frame/terminal checks remain
mandatory for every completed report. The reusable isolation runner is promoted
as `tools/performance/run_isolated.py`; its four focused guard/ownership checks passed, with the two residency checks: six tests, no failures or skips.

**Five consecutive valid rejections; accepted gains 0% in S1, S2, S3K.
The 50% target is not reached. No sixth optimization is started.**
Original game scores remain 1.472399, 1.534621, 2.575576 ms/frame. The best
next measured hypotheses remain atlas upload coalescing/driver-command work
and coherent fast-YM synthesis from Astra's review, with attainable savings
unmeasured. Complete updated-base correctness/tooling validation, integration,
push and cleanup before claiming delivery.

Isolation utility verification: `python3 -m unittest discover -s tools/performance
-p 'test_*.py' -v` completed six tests with no failures/errors/skips. The new
checks reject real compiler/test launchers without mistaking a benchmark classpath
for Maven, refuse to signal a changed process group, and bound cleanup of the
owned session. The reusable CLI holds existing OS leases and never kills a
discovered foreign PID.

Main advanced during document-only coordination to `5e2f51f07917d5eff8ac120007dac55327f93f32`
(two Chaos Mode design/document commits over `13add5b0b`). Fetch/fast-forward
confirmed; no upstream runtime source or build setting changed. The original
numeric baseline remains fixed, and no upstream performance saving is attributed.
This is the actual destination for the resumed combined selection.

### Delivery validation deadline/audio correction

Run `20261013T115209Z-8a0b83ae` at `bd635447b` was admitted after a long
queue wait, then cancelled early. No completed test summary or pass is claimed;
its diagnostics were inspected and acknowledged. A comparable smoke run
confirmed the 40-minute default stopped ordinary validation incomplete at
2,733 reports / 23,381 tests before guards. Retain the same 3,173-class smoke
selection and fresh guards, with `--max-minutes 120`; this is a deadline
correction, not selection narrowing or release-only full-suite validation.
Expected cost is now about an hour ordinary plus separate guards; stop at
120 minutes or 10 minutes without output, marking any timeout incomplete.

The early ordinary log showed real speaker activity. The benchmark's private
display/runtime directory and original audio sink had already been cleaned,
so a fresh owned null sink is created solely for delivery tests, using the same
verified private ALSA/OpenAL route. Host default audio is unchanged. Apply this
environment to both candidate and any matched baseline checks; remove the
new sink after the final required audio/test work. No acceptance timing changes.

### Completed combined delivery run and focused fixture repair

Run `20261013T120250Z-ff9534d2` tested `a6fef6a27b3b06d5a02f90dc9e87e6dfb0d5be90`
against pinned destination `5e2f51f07917d5eff8ac120007dac55327f93f32`:
`python3 tools/testing/run_categories.py --base 5e2f51f07917d5eff8ac120007dac55327f93f32 --max-minutes 120 --run`.
Java 25/Lua 5.4 and the private real audio route above were retained. Ordinary
smoke: **3,154 reports / 26,690 tests / 43 failures / 0 errors / 64 skips**,
3,534.65 seconds. Fresh guards: **88 reports / 678 tests / 5 failures / 0 errors /
0 skips**, 287.54 seconds. Both completed red; no green/full-suite claim.

Twenty-five complete inherited assertions match the linked 26-identity baseline
SHA table exactly. The SSZ second-width assertion has raw SHA
`452f095108f505bc9d598a2e4eecacbcf6efe135716491000b399a142b11ab05`;
its composite state hashes require a fresh bounded semantic comparison.
Seventeen additional failures expose fixtures changing only the bootstrap
registry while an active world still owns another module. Nine classes fail:
collision model (4), edge balance (2), speed capping (2), look delay (1), S3K
super eligibility (1), FBZ cages (1), playable movement (4), collision order (1),
and character provider resolution (1). Assertions remain intact. The fixtures
now select the world with `configureGameModuleFixture`, or explicitly clear the
session when testing pre-session provider lookup. No gameplay source changes.

Matched baseline commands on `5e2f51f07`, each through `maven_queue.py --lean
-Dmse=off ... test`, passed **21** and **237** tests with zero failures/errors/skips:

- `-Dtest=TestCollisionModel,TestEdgeBalance,TestGroundSpeedCapping,TestLookScrollDelay`
- `-Dtest=TestSonic3kSuperTransformationEligibility,TestFbzWireCages,TestPlayableSpriteMovement,TestSpriteManagerCollisionOrder,TestCharacterTypeResolution`

Candidate repair (uncommitted over `a6fef6a27`) uses their combined selector plus
`TestPlayableSessionRules,TestRuntimeInputPlan`: **263 passed**, no failures,
errors or skips, 1m11 execution. The original 17 failures are therefore resolved
by fixture ownership, with selected-world and pre-session regression coverage
retained. The five guard failures identify unaudited CLI singleton/bootstrap
access and stale exact module-access approvals. CLI tools now use the existing
`EngineServices` root, configure its shared service before `Engine.init`, and
retain the same loop/counters/settings. Module-resolution configuration is held
by reference, not snapshotted by root construction. Original measurement inputs,
source, binary and numeric baseline remain fixed; these tooling repairs earn
no performance credit. Guard rerun and SSZ attribution remain pending.

Upstream `b3464b31b` adds coordination guidance over a Chaos research document
commit: no executable runtime changes. Reconcile those documents at integration;
do not repeat unchanged broad smoke coverage as a ceremony.


SSZ attribution completed with the exact two-case selector
`-Dtest=TestSszTailsColdRouteCapture#coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters`
through the normal Maven queue, canonical absolute S3K ROM and private audio.
Each source had **2 tests / 1 pass / 1 identical frame-4018 failure / 0 errors /
0 skips**. The comparison-only probe SHA was
`0adbc4f1df9d2ab34ad980dc9b467f79a2cb8a74e6cad249c7025baee93202c0`.
All six rows match exactly by side, complete ObjectRefId, schema, native type,
scalar bytes and opaque arrays (`[]`). Structured row digest:
`7de7616f613d913918e462add180c5d07efcfdac6a6124a7a84a158dd80dc324`.
The linked earlier scalar table remains exact. Every blob hash delta also equals
`31² × classHashDelta mod 2³²`. Only after this full semantic match, substitute
the captured schema/type/scalar/opaque representation for each known blob in the
complete raw assertion: all other assertion fields match exactly. This is not
arbitrary hash stripping. Both diagnostic files were restored byte-for-byte to
`db4583214f4430c81cf25b7260a716358be575ff2d7772a6067be01367c2af1e`.
The remaining 26 failures are attributed inherited outcomes, not a green suite.

A bounded baseline check selected the **31 existing classes corresponding to
candidate skip identities**, excluding the new `TestTilemapUniformLifetimeNative`.
Exact selector is the sorted unique simple class names from the completed run's
`skipped_cases`; all three canonical absolute ROM properties and the same private
audio environment were passed through `maven_queue.py -Dmse=off ... test`.
**447 tests, 0 failures/errors, 63 skips**, 2m52 execution. Compare with the
category runner's own `summarize`/skip-reason representation: all **63 existing
identities and complete reasons match**, with no omissions. The candidate's
64th skip is the newly added opt-in native uniform diagnostic, whose separate
native execution already passed; its guard property remains unchanged. Smoke
scope differs from the older full ordinary scope, so equal aggregate counts were
never used as equivalence evidence. The first comparison attempt incorrectly
included stack traces in XML reasons; rerunning the comparison using the owning
runner's representation established exact equality without changing assertions.

Focused fresh-JVM guard repair check:
`maven_queue.py -Dmse=off -Pguards -Dtest=TestProductionSingletonClosureGuard,TestBootstrapModuleProviderCachingGuard test`.
Only the two failed guard classes are repeated; the other completed guard coverage
is unchanged. Result pending. Integration/push/cleanup remain pending.

Exact matched-skip arguments (same Java/private-audio environment as above;
`OPENGGF_ROM_ROOT` denotes the pinned canonical absolute ROM directory):

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestArenaMaskRenderer,TestBackgroundScrollWrapPixels,TestCPZObjectBugs,TestDisplayShaderPackDiagnostics,TestForegroundWindowRendering,TestInfiniteSonic,TestLevelRendererBackgroundSamplingPerformance,TestLiveRewindCheckpointCost,TestObjectRewindTypeSafetyDispatchPerformance,TestRewindTorture,TestS1Ghz1OpenGgfAudioTimelineCapture,TestS1OpenGgfAudioCapture,TestS3kAiz1CompatibilityRoutes,TestS3kAiz1SpringRecovery,TestS3kRewindAllocationProbe,TestS3kSlotsGlassNative,TestSceneRenderer,TestScrollBufferUploadNative,TestShaderPixelCentreSampling,TestSlayCompositePixels,TestSlotWindowGpuPassNative,TestSmpsRepeatedPlaybackBenchmark,TestSonicSurvivors,TestSozAct1ArenaCapture,TestSozAct1VictoryCapture,TestSozColdAct1Capture,TestSozColdAct2Capture,TestSozConnectedMechanismCapture,TestSozEndBossVictoryCapture,TestSozMinibossCapture,TestSozPostBossRedrawCapture "-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

Source audit before merging upstream `4a9bec1ea`: `git diff --name-only 184c9d54ed4fc2c1fed3cb6313653a95f9156e3d -- src/main/java` lists only the two runtime CLI tools. Gameplay, audio, rendering and rewind production sources therefore retain the qualified numeric baseline implementation; rejected optimizations are reverted. The frozen protocol SHA remains `43f6fd83c89bcc9c373a5136b47fb68f1a5282f56dc278ffe48947ea705f0bf5`, and the final decision file records five rejections and 0% accepted gain.

Upstream advanced to `4a9bec1eaf86e7b70959113e484315c5013c1b7c`: runtime Mod compatibility reporting plus a queue-contention fix. The waiting guard request (owned PID 1723741, session 48341) was cancelled with SIGINT before execution, exit 130, because updated-source delivery now requires reconciliation; no pass is claimed. This is a source/queue-policy change, not an observation timeout. The checkpoint contains 263 focused passes, matched SSZ attribution and all 63 existing skip reasons. Guard verification remains pending on the reconciled source. Original protocol, numeric baseline and rejection count remain fixed; upstream performance effects are unmeasured and receive no task credit.


Updated-base merge `4a0b2cd6bdf3050ffd5044c4c40f4e29aa3d2723` retains both
measurement-hazard accounts in the sole conflict; release prose combined cleanly.
Runtime compatibility reporting is upstream work, not a task performance gain.
Preflight passed Java 25/Lua 5.4/PowerShell on the reconciled tree. The complete
change-based plan against `4a9bec1eaf86e7b70959113e484315c5013c1b7c` selects
3,176 smoke candidates plus guards; prior completed smoke coverage is not erased.

The new retained-worktree batching exposes a real isolation-CLI lock inversion:
our exclusive admission waited for a category holder's worktree while that holder
needed admission to finish. A real private Git-worktree/OS-flock regression failed
before repair, with shared admission blocked at the retained-peer boundary. Acquire
all existing build leases in sorted order **before** exclusive admission, lending
the held current-tree descriptor to the queue. Canonicalize the requested worktree
root and report only actually leased trees. The test then passed; seven performance
Python checks passed, zero failures/skips, and CLI help passed. The probe launches
no compiler/game and signals only its owned child during bounded cleanup. This is
validation-tool correctness for the upstream queue contract, not experiment six
or an accepted runtime optimization. Original benchmark isolation/data remain
fixed; that run predated retained category batching.


### Reconciled-source delivery scope

The completed combined smoke/guard run covers this task's shared runtime changes.
The inspected updated plan selects 3,176 smoke candidates plus guards. The
subsequent reconciliation adds upstream compatibility diagnostics/catalog/fault
reporting and the Python isolation lease-order fix; it changes no gameplay,
physics, synthesis, rendering or rewind algorithm. Repeat the affected upstream
contracts and repaired fixture identities on matched sources, the failed guards
in a fresh JVM, and the mandatory domain routes. This is focused follow-up to
completed broad coverage, not a new full-suite pass. Public-release preparation
remains the only reason to select `-Pfull-suite`.

Matched source pins: control `4a9bec1eaf86e7b70959113e484315c5013c1b7c`,
candidate Java `4a0b2cd6bdf3050ffd5044c4c40f4e29aa3d2723`. Both use the
updated local queue with the pinned Java/private-audio environment above.
The exact shared selector is:

```text
TestCollisionModel,TestEdgeBalance,TestGroundSpeedCapping,TestLookScrollDelay,TestSonic3kSuperTransformationEligibility,TestFbzWireCages,TestPlayableSpriteMovement,TestSpriteManagerCollisionOrder,TestCharacterTypeResolution,TestModCompatibilityRuntime,TestEffectiveCatalogBuilder,TestModCompatibilityDiagnostics,TestModCompatibilityReporter,TestModRegistrationRuntime,TestModManagerScreen,TestModContextAndFaultBoundary,TestModGameplayPolicyFaultBoundary,TestModRuntime
```

Command: `python3 tools/testing/maven_queue.py --lean -Dmse=off
-Dtest=<shared-selector> test`; candidate additionally selects
`TestPlayableSessionRules,TestRuntimeInputPlan` (20 versus 18 classes).
Two earlier waiting requests were cancelled before execution because the selector
incorrectly named nonexistent `TestModFaultBoundary`; no result is claimed for
those requests. The corrected requests remain alive; no duplicate was submitted.

Fresh guard command is the two-class command recorded above. Domain command:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical -Dtest=TestPlayableSessionRules,TestS1Lz2CompleteRunTraceReplay,TestS3kAizTraceReplay,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils "-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

All four requests are queued; results, integration, push and cleanup are pending.
There is no active owned delegate; Astra's read-only task is terminal. The queue
coordination update is applied locally, with no idle worker restarted for a relay.


### Completed reconciled delivery checks

Executed candidate source: `7d12e4c2cb5f2d26fb65a65042dbc3df82499560`
(Java unchanged from `4a0b2cd6b`); control remains `4a9bec1eaf86e7b70959113e484315c5013c1b7c`.
All commands above completed with exit 0:

| Check | Tests | Failures / errors / skips | Execution |
|---|---:|---|---|
| Updated-base shared focused checks (18 classes) | 399 | 0 / 0 / 0 | 1m19 |
| Combined candidate focused checks (20 classes) | 404 | 0 / 0 / 0 | 1m14 |
| Two repaired guard classes, fresh JVM | 49 | 0 / 0 / 0 | 33.880s |
| Required domain routes/loading, trace profile | 80 | 0 / 0 / 0 | 1m16 |

The 399 shared full test identities and outcomes match exactly; the candidate
adds five passing tests. Domain coverage includes the complete original
AIZ → HCZ and S1 LZ2 fixtures, selected-world rules, and all mandatory S3K
loading checks. ROM properties resolved without skips. The seven Python
performance-tool checks and CLI help also passed as recorded above.
`maven_queue.py -Dmse=off -DskipTests package` passed in 36.566s on Java 25.
This packages the validated source without rerunning tests.

All 17 introduced fixture failures and five guard failures from the completed
combined run are repaired. The 26 attributed inherited ordinary failures remain
explicitly inherited; the 63 existing skip identities/reasons matched their
control, with one new opt-in native diagnostic already separately exercised.
These are focused follow-up passes to a completed red smoke/guard run, not a
green full-suite claim or public-release certification.

After upstream reconciliation, production differences against the immutable
numeric baseline consist of nine upstream Mod compatibility files plus the two
CLI tools. No rejected runtime optimization remains. Upstream effects are
unmeasured and receive no performance credit. The original protocol SHA and
all three original CPU scores remain fixed. Integration/push/cleanup pending.


### Final delivery and cleanup — 2026-10-13

Integrated into the main workspace's existing `develop` branch, without switching
branches, at **`52e411be5f71ee412b28c1825730b1e43898e217`**. The updated destination
was still `4a9bec1eaf86e7b70959113e484315c5013c1b7c`; fast-forward pull found no
further upstream change. Integration had no conflicts, and its tracked tree
exactly matched validated task commit `2b22095b3`. Unrelated dirty disassemblies,
archives, scripts and notes remain untouched.

Post-integration command, using the same Java/private-audio environment:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestPlayableSessionRules,TestRuntimeInputPlan test
```

**5 tests, 0 failures/errors/skips, exit 0**, 1m14 execution. The full identities
were inspected. The completed candidate/control, guard, domain, media and rewind
evidence above remains applicable to the identical integrated sources. No
unchanged broad run was repeated, and no green full-suite claim is made.

`git push origin develop` succeeded; `git ls-remote origin refs/heads/develop`
confirmed the exact integration SHA. Only the integrated destination branch was
pushed. This documentation-only follow-up records that observed delivery.

After successful verification/push, all eight owned worktrees were clean, with
clean initialized submodules and commits reachable from `develop`; all were
removed with their local branches. Workflow build outputs, caches and hook-created
links were discarded. Eleven ignored example configs/notes/mod-save files were
preserved, with byte hashes, under the external task directory's
`cleanup-preserved/`; `delivery-cleanup.json` records exact trees and preservation.
No unknown/user-authored file was discarded. Worktree metadata cleanup completed.

The owned private display was already removed. The delivery private audio module
was verified by exact ID/name/arguments, had zero streams, and was unloaded;
desktop audio was unchanged. Astra is terminal, with its useful review committed
and no owned CLI process/session. All timing quiet holds were released earlier.
Durable protocol, captures and measurement reports remain outside the repository.
Consumed category diagnostics were acknowledged and removed; no raw Maven logs
were archived.

**Stop condition: five consecutive valid rejected experiments, 0% accepted CPU
gain in S1/S2/S3K; the cumulative 50% stretch target is not reached.** The best
next measured hypothesis remains reducing small atlas texture-upload commands;
YM synthesis/resampling is the second investigation. Their whole-engine savings
are unmeasured. No sixth optimization was started.
