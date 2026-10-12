# Runtime performance series — 2026-10-12

## Status

Baseline qualification is in progress. No numeric performance baseline is frozen,
no performance experiment has been accepted, and no percentage saving is claimed.
The intended target is total process CPU time per simulated frame in S1, S2 and
S3K, with resident/peak memory, attributable GPU memory and frame-time tails as
regression checks. The user authorized repairing inherited AIZ → HCZ handoff
failures after both checked complete routes failed their existing controls.

Initial updated source: `a0e2b79cc3a47cedd11a0f9daf6a32ea4ced19cb` (`develop`).
Main was fast-forwarded from `15f3cf371a49e9755568588b00b1f515225aaa4d` before
qualification; no performance result is attributed to that earlier source.
Worktree: `.worktrees/ai-runtime-perf-series`, local branch
`feature/ai-runtime-perf-series`. Existing dirty reference submodules and
untracked main-workspace files were preserved.

## Environment discovered

- Java for controls: Arch OpenJDK `21.0.12.1+1`, Maven `3.9.16`;
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`, `LUA_BIN=/usr/bin/lua5.4`.
  The host default is Java 25; the first preflight rejected that default. The
  explicit Java 21/Lua 5.4 preflight passed, without executing tests.
- Linux `7.2.8-2-cachyos`, AMD Ryzen 9 9950X (16 cores / 32 logical CPUs),
  30 GiB RAM. Desktop/background processes and another worktree's queued test
  were active during qualification. These are correctness runs, not timing runs.
- Real GLX context: AMD Radeon RX 9070 XT, radeonsi/gfx1201/ACO,
  Mesa `26.2.4-arch3.1`, DRM `3.64`; driver reports 16,384 MiB video memory.
  Global free-memory extensions are not application-attributable resident VRAM.
  No reliable application VRAM measurement has yet been qualified.
- Original ROM files were read in place, with matching CRC32 / SHA-1:

| Game | Root ROM file | CRC32 | SHA-1 |
|---|---|---|---|
| S1 | `Sonic The Hedgehog (W) (REV01) [!].gen` | `AFE05EEE` | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| S2 | `Sonic The Hedgehog 2 (W) (REV01) [!].gen` | `7B905383` | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| S3K | `Sonic and Knuckles & Sonic 3 (W) [!].gen` | `63522553` | `CFBF98C36C776677290A872547AC47C53D2761D6` |

The JVM acceptance flags, route matrix, frame windows, warmup, viewport,
configuration and equally weighted scores remain to be frozen before performance
optimization. None of the following counts measures executed runtime frames,
CPU, resident memory, VRAM, startup cost or level-load cost.

## Untouched-source correctness controls

All runs completed on the initial source with explicit absolute ROM paths.
The first invocation completed 7 tests: 3 failures, 0 errors, 0 skips. The
alternative invocation completed 4 tests: 0 failures/errors/skips. The second
complete AIZ fixture invocation completed 1 test: 1 failure, 0 errors/skips.
Report counts below are compared trace observations, not total runtime work.

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

The AIZ1 and HCZ1 pilots independently exercise their act routes; they do not
qualify the AIZ2 → HCZ1 handoff. No failing trace was trimmed, relaxed or used
as an accepted performance scenario.

Commands, from the worktree (replace selectors with each exact list below):

`OPENGGF_ROM_ROOT` resolves to the absolute main-workspace root;
`OPENGGF_PERF_TASK_DIR` is the external task evidence directory. Exact local
resolutions are retained with the external capture/launcher metadata, not in
portable repository documentation.

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py --base a0e2b79cc --preflight

JAVA_HOME=/usr/lib/jvm/java-21-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  "-Dtest=$CONTROL_SELECTORS" \
  "-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

Selectors for the first run:
`TestS1Ghz1TraceReplay,TestS1Lz1CompleteRunTraceReplay,TestS2Ehz1TraceReplay,TestS2CpzLevelSelectTraceReplay,TestS3kAizTraceReplay#replayMatchesTrace,TestS3kHczZoneSliceTraceReplay`.
Selectors for the second:
`TestS2ArzLevelSelectTraceReplay,TestS2MtzLevelSelectTraceReplay,TestS3kAiz1RoutePilot,TestS3kHcz1RoutePilot`.
The third used `-Dtest=TestS3kAizZoneSliceTraceReplay`, only the S3K ROM property,
and omitted `-Dsurefire.runOrder=alphabetical`.

## Handoff investigation

A temporary animation-write stack probe reproduced both original failures
unchanged (57 / 99 errors). It identified a second WAIT write from
`Aiz2EndEggCapsuleInstance.restoreNativePlayerControls`, after the boss's own
restore and after WALK had begun. The scheduling results hook cited
`loc_7D078`, a different post-boss routine. AIZ's floating capsule `loc_866CC` only
runs `Check_TailsEndPose`, swings and moves; the AIZ boss's
`AIZEndBoss_StartPostDefeatCutscene` owns `Restore_PlayerControl/2`.
Reference: `docs/skdisasm/sonic3k.asm`, reference checkout
`1a454a0e335137a1a016d1090a9f4528d36944cf`; the user-supplied reference checkout
was read without modifying it.

Removing that scheduled second restore passes all 51 then-selected focused
object tests and changes the two complete replay results to 37 errors / first
row 20,713 `air`, and 97 errors / first row 25,589 `player_animation_id`.
The new short regression fails on the original producer with WALK $00 becoming
WAIT $05. These are uncommitted prerequisite candidates, not performance gains.

The bridge countdown matches `loc_2B452`: test-before-decrement, $0E initial
count, release on the next zero-count entry. An object-order probe found the
button starting collapse on row 20,698 versus native row 20,699. The cutscene
Knuckles object initially ran one movement step early. Its creation used the
engine's after-current allocator even though `AIZEndBoss_StopPlayer` calls
plain `AllocateObject`. Switching to the lowest-free allocator aligns the
Knuckles position at row 20,696 ($4B1A,$016F), but moves it before the synthetic
button and leaves the same early collapse. That does not establish a passing
handoff; the remaining dependency is the folded layout/controller object graph.

## Benchmark harness limits and next work

The existing `TraceBenchmarkTool --mode full` renders a 320×224 offscreen level
scene, omits normal window presentation/display shader work, and does not prove
filled live rewind history. Its timings and section allocations cannot serve
as this task's full-runtime CPU or resident-memory baseline.

Next: finish the handoff ownership/order repair, verify complete controls and
affected restore/forward-replay/rendering behavior, then qualify a complete
runtime measurement drive. Freeze the six-scenario matrix and acceptance
protocol before any optimization: repeated fresh processes, isolated timing,
five paired alternating baseline/candidate runs, original and previous accepted
comparisons, per-scenario tails/peaks and equal game weights. Rejected resource
hypotheses and accepted performance changes will be recorded here once that
loop starts. The five-experiment stopping rule has not started.

### Production-display probe

A temporary external probe invokes the existing Engine initialization, display
and cleanup paths with real GLX and audio enabled. Two hidden-window attempts
produced blank pixels: the ordinary focus callback had paused the GameLoop.
Declaring a tool-owned host-focus lease (without changing production pause
behavior) then rendered active S1 LEVEL gameplay, 639,477 nonblack pixels in
an actual 960×672 framebuffer. The requested 640×448 logical window is scaled
by the desktop and snapped to the supported integer scale. The viewport must
be pinned from the actual framebuffer, not inferred from requested dimensions.
This was a boot/pipeline qualification probe, not a performance acceptance run,
not a route control and not evidence of filled rewind history.

The driver exposes `drm-resident-vram`, `drm-shared-vram` and
`drm-resident-gtt` per client. A sampler must deduplicate file descriptors by
(device, client ID), distinguish shared storage, and sample during verified
rendered workload. `drm-total-vram` is requested storage, while the deprecated
amdgpu `drm-memory-vram` aliases resident storage; see the
[kernel DRM usage interface](https://docs.kernel.org/gpu/drm-usage-stats.html).
No boot-only GPU counter reading is recorded as a benchmark score.

### Native-owner repair under validation

The allocator correction alone did not fix the bridge handoff: it placed
Knuckles in the expected lower free slot, but the synthetic button/bridge
replacement still reordered the scan. Native observations show button slot 4,
Knuckles slot 6 and bridge slot 8 at the late handoff. The synthetic graph put
the button after Knuckles. The bridge countdown itself matches the ROM's
zero-test-before-decrement routine; adding a compensating delay was rejected.

The current candidate retains the ROM-loaded button and bridge and changes the
existing boss's dispatch to its post-defeat continuation, matching
`AIZEndBoss_StartCapsuleSequence`. It arms `_unkFAA3` in
`AIZEndBoss_StartDefeat`, without resetting that flag when creating the capsule.
Continuation state is captured as a value under the boss's rewind lifetime,
rather than as a reference to an unregistered controller. Two regression tests
failed on the previous implementation (2 failures, 0 errors/skips): the bridge
flag was not armed, and capsule creation destroyed the original button.
A compact capture/restore test also checks recreation and forward continuation.
The owner repair and its tests compile. Focused validation completed on the
initial Java 21 base: 67 tests, 0 failures/errors/skips (52 sequence-object and
15 boss tests), including recreated continuation restore/forward replay. Both
complete handoff controls remain queued; this is not a handoff pass.

Commands for the candidate:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk python3 tools/testing/maven_queue.py -Dmse=off compile -B
JAVA_HOME=/usr/lib/jvm/java-21-openjdk python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestAizEndBossInstance,TestAiz2BossEndSequenceObjects' \
  "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
JAVA_HOME=/usr/lib/jvm/java-21-openjdk python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay \
  -Dsurefire.forkCount=1 '-Dtest=TestS3kAizTraceReplay#replayMatchesTrace,TestS3kAizZoneSliceTraceReplay' \
  "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

### Engine-loop qualification

A temporary tool subclass drove the existing `Engine.loop` with neutral logical
inputs and the declared host-focus lease. It completed 300 presentation-loop
updates, with 149 live-rewind gameplay frames retained (earliest 0), and a
nonblank 960 × 672 real-driver framebuffer. Presentation iterations are not
executed simulation frames. This proves the loop and rewind path can run in the
measurement environment; it proves neither filled history nor route parity and
provides no CPU, memory, VRAM or frame-time benchmark result.

Upstream main moved during qualification, including a Java 25 migration.
The Java 21 results above remain attributed to the initial source. Before
freezing a baseline, reconcile the updated destination, rerun matched controls
on its required Java version, and pin that source/environment. Do not attribute
upstream Java/runtime changes to this performance series.

### Input and audio qualification limits

The first 300 executed S1 GHZ1 frames through the actual Engine loop matched
native position, X/Y/ground velocity, air, animation, mapping and gameplay
counter exactly. Counting title-card presentation iterations initially produced
false comparisons; the probe now distinguishes those from executed gameplay.
The longer probe diverged at observation 2,111: native holds its gameplay
counter at 2,111 while this simple driver advances to 2,112. The native recording
has five such service gaps, despite a zero lag-counter column. Advancing the
movie only on gameplay-counter changes does not implement the complete timing
contract. The prefix is useful driver evidence, not complete-route qualification.
The longer run was stopped when the user reported unintended audible playback.

A task-owned private audio output is now qualified without changing the desktop
fallback device or disabling game audio. OpenAL's ALSA backend opens an explicitly
named `openggf_private` PCM, routed by a child-only `ALSA_CONFIG_PATH` to the
owned Pulse/PipeWire sink `openggf_runtime_perf_20261012`. The stream's process
ID and destination were verified during the run. OpenAL reported active stereo
Float32 mixing at 48,000 Hz with a 960/2,880-frame buffer. The private-routing
probe completed normally. An earlier forced-Pulse attempt fell back to no device
and was rejected; an attempted override of the generic ALSA default did not
provide verified private routing and was also rejected. Use the explicit named
PCM and fail closed on unverified routing or a speaker-device fallback. Pin this
output environment before the numeric baseline; none of these attempts supplies
an acceptance measurement. The owned sink module ID is recorded outside the
repository and must be unloaded at task cleanup.

The first native-owner candidate did **not** pass the complete controls (2 tests,
2 failures, 0 errors/skips). The original route failed its semantic terminal
hardware check: four declared pending KOS-module jobs were expected, but none
were pending. That assertion precedes the divergence summary, so no original
route mismatch total is attributed to this run. The alternate route reported
266 errors, first row 25,040 `tails_air` (expected 0, actual 1). This variant is
not accepted and supplies no performance comparison.

Consumer review found that retaining native owners also requires removing their
reliance on the synthetic-override marker: generic results restored the full
camera bounds, and the forest-front display bucket was released. Functional
regressions reproduce both consequences (2 failures, 0 errors/skips). Native
aux at alternate row 25,040 shows Tails grounded with status `$08`,
`object_control=$81`, and interact slot 6 (the original bridge). The owning
`SolidObjectFull2_1P` checks standing/air/bounds before `MvSonicOnPtfm` returns
on signed object control without clearing the standing owner. A third focused
regression is testing that retained-rider path. Repair these concrete consumers
before another complete comparison; do not compensate with a route-dependent
restore delay or replace the original graph again.

The retained-rider regression also failed before its fix (1 failure, 0
errors/skips); its initial helper-type compile mistake was corrected before
collecting that red test result. After the three consumer repairs, 87 focused
tests passed with no failures/errors/skips, including native-owner camera and
display behavior, signed-control rider retention, boss/bridge graph restore and
recreation paths. The complete controls returned to 37 errors (original, first
20,713 `air` 0/1) and 97 errors (alternate, first 25,589 animation $13/$05),
with no new early consumer failure or terminal-hardware failure. Neither
complete route is yet passing. A temporary observer is comparing live SST slots
and owner phases against native aux at the remaining boundaries.

### Waiting boss and results-parent ownership

Existing native `slot_dump` events resolved the apparent missing allocations;
no new native capture was necessary. At original row 18,328, slots 9–13 and
15–26 contain `Obj_AIZ2BGTreeMove` (`$5068A`), and the waiting boss retains
slot 14. The owning label was resolved through the local disassembly listing
and checked against `Obj_AIZ2BGTreeMove`'s camera-delete routine. The boss first
appears at row 18,060 with camera X `$4680`; camera backtracks to `$44C0` at row
18,076 before returning to the arena. `Obj_AIZEndBossWait` only returns while
camera X is below `$4880`, without a range-delete tail. The engine's earlier
`isPersistent=false` wait allowed the loader allocation to be discarded and
recreated after background trees retired. A functional backtracking test failed
by losing the original active boss instance (1 failure, 0 errors/skips).

A second regression failed because the last results child published the capsule
release immediately (1 failure, 0 errors/skips). Native `Obj_LevelResultsWait2`
reads the child's decremented count on its own subsequent dispatch and then
clears `_unkFAA8`. The candidate now retains the waiting boss and publishes that
release from the results parent's exit callback, retaining hosted-run exit policy.
Focused checks passed: 89 tests, 0 failures/errors/skips. Both complete fixtures
still fail: the original's terminal check again expects four pending KOS-module
jobs but observes none; the alternate has 45 errors, first row 25,951 animation
$05/$07. This candidate is not accepted. A temporary observer is now checking
which boss instances remain live and the first late state differences; the
terminal assertion prevents assigning an original mismatch count yet.

Commands for this iteration use the same explicit Java 21 and S3K ROM environment
as above. Red selectors were:
`TestAizEndBossInstance#waitingLayoutBossRetainsItsOriginalSlotUntilTheArenaIsReached`
and `TestAiz2BossEndSequenceObjects#lastResultsChildDoesNotPublishTheParentsCapsuleRelease`.
The former needed a corrected four-argument ObjectManager call and a real
backtracking step before collecting the behavioral red result; an in-range
waiting check alone passed and did not reproduce the fault. Green selectors:
`TestAiz2BossEndSequenceObjects,TestAizEndBossInstance,TestSonic3kZoneFeatureProvider,TestS3kAizEndBossGraphRewind,TestS3kAizBridgeRiderRewind,TestAiz2ObjectRewindCodecs`
with `--lean -Dmse=off ... test -B`. Complete controls used the unchanged
`-Ptrace-replay -Dsurefire.forkCount=1` two-fixture command above.

The private output was unloaded while correctness work waits for the shared
Maven queue: it had zero connected streams, the desktop default was still the
physical output, and the owned module ID/name/arguments were verified before
removal. Recreate and record the private module before the next runtime probe;
the launcher must continue to reject an absent or unverified audio route.

### Native dispatch boundaries

The lifetime observer confirmed a single retained boss, in original slot 14
and alternate slot 11. The original results release occurred after that boss's
dispatch, leaving its WAIT restore and walking one entry late. The alternate
route first diverged after the button: rows 25,951–25,952 retained LOOK_UP instead
of WAIT, then row 25,965 retained ROLL instead of HURT_FALL. The observer was
removed after collecting those comparisons; no comparison values enter gameplay.

Three functional regressions failed on the current candidate (3 tests,
3 failures, 0 errors/skips). `sub_86984` uses lowest-free `AllocateObject` for
results, while the engine used after-current allocation plus a blanket deferred
first update. `loc_65C56` unlocks P1 after its consumed input history is recorded;
the engine retained its forced UP word into the next player dispatch. The bridge
countdown `loc_2B452` returns without `SolidObjectFull2`, retaining its own rider
bits until the delayed eject even if the player jumps. The engine's automatic
solid pass unseated that rider and its countdown rewrote the player's OnObj bit.

The candidate now uses lowest-free results allocation with ordinary slot-driven
dispatch, clears the button's input override without rewriting consumed history,
and places bridge solid resolution at its normal routine checkpoint only.
The existing `cutsceneButtonRetainsUpForLaterPlayerSlotWhenKnucklesReachesIt`
test encoded the incorrect extra-UP assumption; it was replaced by
`cutsceneButtonRetainsConsumedUpHistoryAndUnlocksNextPlayerDispatch`. This identity
change is a corrected native expectation, not an omitted failing control.

Red command, from this worktree at unchanged source base `a0e2b79cc`:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestAiz2BossEndSequenceObjects#aizResultsUseAllocateObjectBelowTheCapsuleOwner+cutsceneButtonRetainsConsumedUpHistoryAndUnlocksNextPlayerDispatch+countdownOnlyBridgeKeepsItsNativeRiderLatchAfterAJump' \
  "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

An initial package-private SolidContact constant caused compilation failure;
it was replaced by the public constructor before collecting the behavioral red
result. The six-class focused invocation ran 91 tests: 90 passed and the
countdown check failed because it explicitly called the forbidden solid
checkpoint rather than exercising native object dispatch. The corrected check
uses `ObjectManager.update`; its focused rerun passed (1 test, no
failures/errors/skips). This verifies all 91 focused identities across the two
invocations, not a single all-green suite run. Its first red checkpoint assertion
is not evidence about the production countdown dispatch. The complete controls
ran with 2 failures and no errors/skips. The alternate reported 81 errors,
first row 25,589 animation $13/$05. The original failed the terminal hardware
assertion. Its freshly regenerated finally-block report (checked against this
invocation's file modification time) contains 27 errors across 20,467 compared
observations, first row 20,714 animation $1B/$05 and air 1/0. This report adds
comparison evidence even though the assertion prevented the console summary;
the terminal boundary still failed and no route pass is claimed.

The actual-dispatch bridge test was strengthened to establish its standing latch
through a real normal contact, then jump during the countdown. Two setup defects
made the first attempts invalid evidence about that path: TestObjectServices
defaults to an inert solid registry, and camera $4880 placed the normal bridge
outside its custom range window. The test now injects DefaultSolidExecutionRegistry,
uses in-range camera $4A00, and checks that the normal owner is still live before
starting collapse. This qualified check passed with the candidate (1 test, no
failures/errors/skips). A matched red run without the new rider publication
failed the final HURT_FALL assertion (1 test, 1 failure, no errors/skips), with
the qualified setup unchanged. Manual checkpoints return contact results without `SolidObjectListener`
compatibility callbacks; the bridge now consumes the normal checkpoint's standing
results itself, including clearing retired riders, and leaves them untouched
during the countdown. The invalid intermediate failures are not regression proof.
The qualified red used the same single-method command, at
`TestAiz2BossEndSequenceObjects#countdownOnlyBridgeKeepsItsNativeRiderLatchAfterAJump`,
with the candidate's result-consumption block temporarily omitted. That block
was restored byte-for-byte before the focused candidate rerun.
That rerun passed 64 tests, no failures/errors/skips, using selectors
`TestAiz2BossEndSequenceObjects,TestS3kAizEndBossGraphRewind,TestS3kAizBridgeRiderRewind`
with the explicit Java 21, absolute S3K ROM and lean command shape above.
The two existing bridge-rider rewind tests cover the collapsing log bridge;
they are not drawbridge countdown replay evidence. The end-boss graph tests
cover boss children; actual drawbridge countdown restore and transition replay
remain delivery obligations.
The complete candidate invocation then passed the original handoff control:
20,463 compared observations, zero divergences, and its terminal hardware-work
assertion passed. The alternate still fails with 59 errors, first row 25,589
animation $13/$05 (2 tests overall, 1 failure, no errors/skips). These are
accuracy results, not performance measurements. Existing native slot dumps show
the alternate waiting boss in slot 5, whereas the engine observer found slot 11.
`Obj_AIZ2MakeTree` / `loc_50640` allocates trees after its current slot; the
engine currently uses lowest-free allocation. An allocation-only probe will
check whether trees consume the native free boss slot. The existing omitted
last two tree-script entries and foreground-mask gap are separate inherited
visual coverage limitations; do not expand that work without evidence.
The alternate allocation probe completed (1 inherited trace failure, 59 errors,
no errors/skips) with `OGGF_SLOT_PROBE=1 OGGF_SLOT_PROBE_FULL=1
OGGF_SLOT_PROBE_OUT=target/aiz-alternate-slot-probe` and the alternate-only
normal trace command. Native row 22,560 has slot 5 free; the engine still has
Sonic3kPathSwapObjectInstance there. At row 22,592 that engine object is gone,
but its pre-load occupancy already sent the waiting boss to slot 11 instead of
native slot 5. Thus the proposed explanation that a tree occupies slot 5 is
rejected; no tree allocation code was changed. The probe truncates native code
pointers as object IDs, so its mismatch totals/types are not controls here;
only slot positions and the engine occupant descriptions were used, with raw
native pointers resolved separately. Additional graph differences remain.

Use the complete passing original route for the mandatory AIZ → HCZ scenario.
The alternate remains an explicitly inherited failing control, improved from
99 to 59 errors; it does not qualify as a benchmark route. No new failing
identity or widened tolerance was introduced. The accuracy prerequisite still
needs drawbridge countdown restore, presentation checks and matched updated-base
validation before the runtime baseline can freeze.
The drawbridge countdown recreate/forward-eject test failed before its policy
change: the recreated owner lost the retained rider (1 failure, no errors/skips).
`standingPlayers` is now CAPTURED rather than TRANSIENT, because normal contacts
cannot rebuild it during the countdown. The focused command used selectors
`TestS3kAizBridgeRiderRewind,TestCompactFieldCapturerPolicy,TestAiz2BossEndSequenceObjects`
and passed 66 tests, no failures/errors/skips. The new third bridge test exercises
actual owner recreation and the same delayed forward eject; it is still a local
owner-state check, not a whole-route rewind/pixel certificate.

### Rendered handoff and audio comparison

The maintained TraceCaptureTool rendered raw rows 20,180–20,797 at logical
320×224, scale 2, without ghosts: 618 FFV1 frames at 640×448 plus 494,400
48-kHz stereo PCM sample frames (24-bit container output). A second fresh
process produced identical decoded video frame hashes and identical PCM bytes.
The video hash-list SHA-256 is
`51ed6bbf4b9b778c980f47bd7934fd881915d1f0c9eff35c38888e854dd3ee90`;
PCM SHA-256 is
`7d32aea56b52460d544aeafa4dd6906146e03d4c17ab449beeae5565604b5cfa`.
Audio is nonzero (mean −29.4 dB, maximum −12.3 dB). This is the intentional
headless PCM capture backend, not a complete-runtime device/CPU benchmark.

The untouched `a0e2b79cc` source was built separately in
`.worktrees/ai-runtime-perf-control` / `feature/ai-runtime-perf-control`.
It produces the same dimensions/frame/sample counts. Against it, 228 decoded
video frames differ, starting at raw row 20,304; inspected checkpoints show
the corrected player animation and later bridge/Knuckles timing. The first
123 frames are identical, including the inherited striped waterfall rendering.
This does not certify native pixel parity or repair unrelated rendering gaps.
PCM differs from row 20,403 onward. Native slot dumps show Knuckles still at
the unexecuted `Obj_CutsceneKnuckles` pointer on that row and initialized on
20,404; the corrected lower-slot allocation defers the engine's music request
to that initializer. Native `loc_61FC2` calls `sub_65DD6`, which allocates
`Obj_Song_Fade_Transition`; this check does not certify that helper's waveform
or fade timing. It verifies deterministic output and retains the existing
audio pipeline/settings rather than asserting PCM equality with the inaccurate
old dispatch.

Captures live under the explicit external task directory
`${OPENGGF_PERF_TASK_DIR}/`, in
`handoff-captures`, `handoff-captures-repeat`, and `handoff-captures-untouched`.
Both owned queued repeat/control exec requests were cancelled before admission
(exit 130); no running test was interrupted. The already-built maintained Java
CLI then completed both captures normally while another full suite continued.
Classpath roots use each tree's own `target/classes`, the same POM dependencies,
and exclude `target/test-classes`; no build tree was copied/shared. Neither
capture process opened a speaker-device stream.

Exact capture arguments were:
`--trace src/test/resources/traces/s3k/aiz1_to_hcz_fullrun --out-dir <task-directory>
--scale 2 --clip frames:20180:20797 --no-ghosts`.
First launch used `JAVA_HOME=/usr/lib/jvm/java-21-openjdk python3
tools/testing/maven_queue.py -Dmse=off exec:java
'-Dexec.mainClass=com.openggf.tools.TraceCaptureTool' '-Dexec.args=<arguments>'`.
The subsequent launches used `/usr/lib/jvm/java-21-openjdk/bin/java
--enable-native-access=ALL-UNNAMED -Xmx3g -cp <each-tree-classpath>
com.openggf.tools.TraceCaptureTool <arguments>`, with each tree as cwd.
FFmpeg decoded video with `-map 0:v:0 -pix_fmt rgba -f framemd5 -` and audio
with `-map 0:a:0 -f s24le -acodec pcm_s24le -`; hashes compare decoded content,
not timestamped container bytes. These correctness captures supply no timing,
resident-memory, or VRAM score.

### First unlocked transition dispatch

Final owner review found a bounded Y-check edge: native
`AIZEndBoss_StartLevelTransitionCheck` installs its next routine and returns;
only the following dispatch tests Y (`sonic3k.asm:138327-138340`). A player
already below the threshold made the folded controller request HCZ immediately.
`hydrocityTransitionWaitsForTheDispatchAfterButtonUnlock` reproduced that defect
(1 failure, no errors/skips). The candidate now consumes the routine-change
entry while still advancing the independently folded camera child. Three
existing controller/Y/camera checks needed their setup to advance to the actual
Y-check dispatch; their identities and coordinate assertions are retained.
An unused boss runtime-state helper was removed after its callers disappeared.

The seven-class lean rerun completed 99 tests, with 98 passing and one existing
controller setup still expecting same-entry transition. Its corrected
single-method rerun passed (1 test, no failures/errors/skips). No production code
changed after that seven-class run. Full handoff controls for this final phase
change completed: original passes all 20,463 compared observations and terminal
hardware checks; alternate retains 59 errors, first row 25,589 animation $13/$05
(2 tests, 1 inherited failure, no errors/skips). This is still accuracy work, with zero performance
experiments or measured gains.
