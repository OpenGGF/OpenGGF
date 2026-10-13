# Runtime performance series — 2026-10-12

## Status

Baseline qualification is in progress. The inherited original complete AIZ → HCZ
route now passes its trace and terminal controls at local accuracy checkpoint
`1baa8e6dc` plus updated-base merge `eca81166b`; broad matched validation is
complete: all 26 ordinary failures and all 64 skips are inherited/matched, and
fresh guards pass. The alternate retains 59 inherited mismatches. No numeric performance
baseline is frozen, no performance experiment has been accepted, and no percentage
saving is claimed. Private-display runtime qualification is in progress while the desktop remains
locked. The audible task process was stopped, and subsequent device streams were
verified on the private audio sink. Integration and push have not happened.
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

## Updated-base accuracy checkpoint

Accuracy checkpoint: `1baa8e6dc9c5de5223a64633e7043411845a997f`.
The main branch advanced to `3ac1363ed2b2549d9e7c9d913b3b617b47e1bc53`;
fetch/fast-forward pull reported already up to date and preserved unrelated files.
Local merge `eca81166b7bbb06eeb557f92f4a804029130a5b4` adopts that source.
The sole conflict was the measurement-hazard table: both the private-audio
lesson and upstream socket/palette/full-assertion lessons were retained.
No runtime code conflicted. Main now requires Java 25. This change precedes
numeric baseline freezing; no Java/upstream gain is attributed to this task.

Java 25 clean compile passed (46.574 seconds is build cost, not runtime evidence).
Java 25/Lua 5.4/tool preflight passed. Both owned worktrees initialize pinned
TraceChaser `e0a2443e086ca657a49227c5467eeecd06e40ece` for guard prerequisites.
The old control tree was clean and wholly ancestral before moving its local
control branch from `a0e2b79cc` to the updated base; untouched capture evidence
retains the old source identity. The original numeric baseline still does not exist.

Updated candidate focused checks: 167 tests, zero failures/errors/skips,
including both level-loading classes, the required S3K skip/bootstrap/decoder
checks, capture-audio boundaries, boss/bridge recreation and the 99 earlier
local/schema checks. Selectors:
`TestAiz2BossEndSequenceObjects,TestAizEndBossInstance,TestSonic3kZoneFeatureProvider,TestS3kAizEndBossGraphRewind,TestS3kAizBridgeRiderRewind,TestAiz2ObjectRewindCodecs,TestCompactFieldCapturerPolicy,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestTraceCaptureUnifiedAudio`.
Command used Java 25, the normal queued `-Dmse=off '-Dtest=<selectors>' test -B`
shape and all three absolute canonical ROM properties from the variables above.

Updated route controls: 7 tests, 6 passing, one inherited alternate-AIZ failure,
zero errors/skips. S1 GHZ1/LZ1, S2 EHZ1/ARZ, original complete AIZ → HCZ, and
HCZ1 pilot pass. Alternate AIZ still has 59 errors at row 25,589 animation
$13/$05. Selectors were
`TestS1Ghz1TraceReplay,TestS1Lz1CompleteRunTraceReplay,TestS2Ehz1TraceReplay,TestS2ArzLevelSelectTraceReplay,TestS3kAizTraceReplay#replayMatchesTrace,TestS3kAizZoneSliceTraceReplay,TestS3kHcz1RoutePilot`,
using the same normal `-Ptrace-replay -Dsurefire.forkCount=1` command with Java 25.
These remain separate replay controls, not full Engine.loop qualifications.

The combined plan against actual destination `3ac1363ed` selects all 3,170
ordinary classes plus all guards. The matched main-source run recorded in the
[upstream delivery evidence](../2026-10-12-mod-audit-remediation.md#integrated-develop-comparison-and-final-follow-up)
has 26 inherited assertion failures, 64 skips and one historical Golf socket
error subsequently repaired by a test-only correction and a 17-case focused run.
Its raw diagnostics were consumed by their owner. Our matched base/current runs
will retain full failure and skip identity comparison before acknowledgment.
Expect roughly 65 minutes per combined invocation, based on the completed
3,619-second ordinary plus 265-second guard run. Stop at 120 execution minutes
or 10 minutes without output; a timeout is incomplete validation. New/worsened
or unattributed failures block integration. Timing measurements remain isolated
from these builds/tests and have not begun.

### Locked-desktop live-runtime qualification

After adopting Java 25, the normal Engine driver stalled at `glfwShowWindow`
before GL capabilities/audio initialization. An owned child-launched debugger
located the native wait in `XCheckTypedWindowEvent` through Xlib/XCB. Both automatic
and explicit X11 selection reproduced it; a minimal 640×448 GLFW window also
stalled on X11. Native Wayland opened that minimal window. A separate full Engine
Wayland probe initialized normal GL, SMPS/OpenAL and the configured live rewind,
then waited at `glfwSwapBuffers` without completing its route. These probes do not
qualify a runtime baseline, and no hardware/JDK gain or performance rejection is
counted from them.

Read-only `qdbus6 org.freedesktop.ScreenSaver /ScreenSaver GetActive` returned
`true`; KWin reports 6.7.5/Wayland with the same RX 9070 XT/Mesa driver. This is
consistent with the blocked visibility/presentation boundaries, rather than
establishing a Java regression. An unlocked, visible desktop is needed to retest that desktop path; the private
hardware display qualification below provides a separate route.
No desktop unlock, global display/audio setting, or unrelated process was changed.

The Wayland full-runtime probe's actual process-owned ALSA/OpenAL stream was
verified on the explicitly named private sink, Float32 stereo at 48 kHz. It was
stopped after locating the swap wait. The idle owned null-sink module was then
unloaded after verifying its ID/name/arguments and zero connected streams.
The owned debugger inferiors and minimal-window probes were also reaped. The
private output must be recreated and verified before any further audible engine
launch. Task sound does not reach the desktop speakers; unrelated streams remain
untouched. Both matched broad correctness invocations remain active/queued;
there are no timing measurements running concurrently.

### Updated Java 25 handoff media

A correctness-only `TraceCaptureTool` launch at `eca81166b` used Java 25,
`--enable-native-access=ALL-UNNAMED -Xmx3g`, the task tree's own production classes
and the same `--trace src/test/resources/traces/s3k/aiz1_to_hcz_fullrun --scale 2
--clip frames:20180:20797 --no-ghosts` arguments, with output under
`${OPENGGF_PERF_TASK_DIR}/handoff-captures-java25`. It completed with exit 0 and
no process-owned speaker stream. All 618 decoded frame hashes and all 494,400
stereo PCM sample frames match the earlier candidate captures exactly. The
newline-joined video MD5 list retains SHA-256
`51ed6bbf4b9b778c980f47bd7934fd881915d1f0c9eff35c38888e854dd3ee90`;
PCM SHA-256 remains
`7d32aea56b52460d544aeafa4dd6906146e03d4c17ab449beeae5565604b5cfa`.
This corroborates deterministic rendering/audio across the final accuracy edge,
updated base and Java migration for that handoff window; it does not certify
native RGB/waveform equality or provide a complete-runtime resource score.

### Completed candidate broad checks (matched base still active)

At runtime source `eca81166b`, combined run `20261012T215139Z-2c945d94`
completed its full selection of 3,170 ordinary classes and all guards. Ordinary:
3,168 class reports, 27,645 checks, 26 assertion failures, no errors, 64 skips,
3,575.50 seconds. Fresh guards: 88 reports, 677 checks, no failures/errors/skips,
276.30 seconds. Invocation exit 1. Diagnostics remain needed for comparison with
matched base run `20261012T225115Z-b1b082de` at `3ac1363ed`; acknowledge both
only after inspecting the comparison. The tested working-tree fingerprint was
`dc8dbbcfa3e519c82c887e767bb06c7e8775284fe98c9068099cd29915645d37`.

All 26 failed identities match the upstream MHZ/LRZ/SSZ/DEZ table; 25 complete
raw assertions also match its SHA-256 digests. The SSZ width-400 composite
snapshot assertion differs (`13ac120a05a92dfcff17999e9920fd96d6cdd6bf89a4346ee53b611167406150`);
its class/schema/state-combining hashes require semantic attribution with the
matched base, not token normalization. Skip identity/reason comparison is pending.
The candidate adds 14 net checks. One old incorrect button oracle was replaced:
`cutsceneButtonRetainsUpForLaterPlayerSlotWhenKnucklesReachesIt` becomes
`cutsceneButtonRetainsConsumedUpHistoryAndUnlocksNextPlayerDispatch`, preserving
the already-consumed logical input history while checking native next-dispatch
unlock. Do not call the broad run green or integrate before attribution completes.

### Private hardware display qualification

A task-owned `dbus-run-session -- kwin_wayland --virtual --width 1280 --height
720 --scale 1 --output-count 1 --socket openggf-runtime-perf --no-lockscreen
--no-global-shortcuts --no-kactivities` has a private 0700 runtime directory,
configuration/data directories, and session bus. It opens no desktop window.
The first socket path exceeded Unix's 108-byte limit and was rejected; the
owned retry uses a short generated path. The first minimal client selected the
integrated GPU and was rejected. Child-only `DRI_PRIME=pci-0000_03_00_0` selects
the RX 9070 XT, as documented by
[Mesa's GPU selector](https://docs.mesa3d.org/envvars.html#envvar-DRI_PRIME).
A normal OpenGL 4.1-core GLFW Wayland window then completed 60 buffer swaps with
focus and reported RX 9070 XT/radeonsi/gfx1201/ACO, OpenGL 4.6, Mesa
26.2.4-arch3.1. This is real hardware rendering, not a software renderer.

The full Engine S1 GHZ1 probe completed 3,905 controller rows with 3,900 level
iterations, movie indices 840–4,744, actual 640×448 window, normal audio/render
submission and configured live rewind. A fresh expanded run exited 0:
4,057 presentation updates, rewind current 3,905 / earliest 300 (filled history,
including the preceding keyframe). Position, all three speeds, subpixels, angle,
air/rolling flags, camera, rings and animation/mapping match native exactly.
All controller masks match and the five held level rows preserve the recorded
execution shape. Absolute VBlank count has a constant 524 offset; its per-row
advances match, and no gameplay clock was set from the recording. The native
`ground_mode` column is angle-derived; seven cached internal enum boundary
values differ, while the existing `TraceBinder` angle projection matches.
Retain the internal values for baseline/candidate comparison rather than hiding them.

Reading `GL_FRONT` after swap returned zero even for a minimal nonblack clear
with no GL error; it is not a valid image oracle on this path. The earlier final
blank-frame rejection was a probe/readback failure. The normal Engine F12
screenshot path instead captures before swap and produced a visually inspected,
nonblank 640×448 gameplay image at row 999, without an extra simulation tick.
The later generic probe hands the diagnostic playback panel to its external
owner through the existing API, leaving the game HUD available.

S2 EHZ1's first full live run completed 5,852 rows (movie 899–6,750), actual
640×448, rewind current 5,852 / earliest 2,250, with private audio verified.
Position/speeds/subpixels/camera/rings/animation and flags match; controller masks
and VBlank deltas match. Absolute level counter starts 27 rather than 1 and keeps
that 26-count offset. Starting playback as soon as the main player exists instead
of waiting for `Level_started_flag` did not change that offset. This hypothesis
is rejected; do not reset/hydrate the counter to make it match. Startup preparation
and canonical clock/sidekick control coverage still need qualification.

These are correctness/driver runs concurrent with tests, not acceptance timing.
The prototype retains comparison data and emits per-row diagnostics; it is not
an acceptable memory/timing measurement driver as-is. The private compositor and
null sink remain owned and registered outside the repo and must be cleaned up.
No numeric baseline, performance gain or performance-rejection count exists.

### Matched broad attribution completed

Updated base `3ac1363ed`, run `20261012T225115Z-b1b082de`: ordinary 3,168
reports / 27,631 checks, 26 assertion failures, no errors, 64 skips, 3,640.93s;
fresh guards 88 reports / 677 checks, no failures/errors/skips, 263.10s. Exit 1.
Against candidate `eca81166b`, there are no added/removed failed identities,
25 complete assertion matches, no added/removed skips, and no changed complete
skip reasons. No failure/skip details were omitted. The remaining SSZ assertion
has base SHA-256 `23eaae1f14b8fdd3889bbaed36d28e6078f7259b9d47af973d615665fa9cad12`
and candidate SHA-256 `13ac120a05a92dfcff17999e9920fd96d6cdd6bf89a4346ee53b611167406150`.

Normal queued matched selector
`TestSszTailsColdRouteCapture#coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters`
with Java 25, `-Dmse=off`, canonical absolute S3K ROM property and `test -B`
completed two checks per source: one pass, the same frame-4018 assertion failure,
no errors/skips. Comparison-only temporary test SHA-256 was
`e52f7ce0b97c308cfe721c80bf940be7acc9384f6bf1eea76ab334fef6544f0b`.
All six rows match by side, every ObjectRefId field, schema ID, full native type,
complete scalar bytes and opaque values. JetFlame schema 15 and GunArm schema 16
retain the full scalar table in the upstream validation artifact, and every
opaque array is empty. The second parameter is width 800 in the current test;
older prose calls it width 400. The failing slot/child identities are unchanged.
No hash tokens were stripped to claim equality. Both original tests were restored
byte-for-byte to `db4583214f4430c81cf25b7260a716358be575ff2d7772a6067be01367c2af1e`.
These checks attribute all 26 failures as inherited; this is a completed red
ordinary suite with passing guards, not a green full suite. No SSZ fix or
production formatter change was made. Integration/push remain pending.

### Complete-driver S3K qualification frontier

The standalone full Engine probe initially used row 335, confusing compared-row
count with pre-level length. The canonical resolver observes `Game_mode=12` at
row 289. With controller start `511 + 289 = 800`, the 1,000-row prefix matches all
checked gameplay state; its sole raw-field difference is first-row camera Y
918/912. Canonical `phaseForReplay` classifies row 289 as `VBLANK_ONLY`, and
`shouldCompareGameplayStateForReplay` is false there (row 290 is the first full
compared level frame). No expected camera/player/clock value was written into
the engine.

The first 20,509-row full live attempt omitted the native controller latch offset
and diverged at row 1,428, speed 0/12 when manual control began. The existing
`TraceReplayRowPolicy.appliedInputOffset` selects the previous BK2 input for this
native sample phase. Adding that input-only rule matched the playable route until
row 5,463, when LIVE readiness completed the AIZ1 → AIZ2 work ahead of the
reference: X 12,237/205, Y 893/765, camera X 12,048/16 and Y 736/608. This full
attempt remains rejected as a driver qualification; its EOF is not proof it
followed the mandatory handoff. There is still no performance experiment or score.

The next external-only prototype activates the approved recorded-admission port
after idle LIVE queues, installs the original v5 readiness schedule, and attaches
the existing service-boundary observer. It registers the port with live rewind,
selects each raw input row, and checks unmatched completions. Its only authority
is matching kind/ordinal/fingerprint/boundary for already-prepared ROM work; it
neither supplies gameplay values nor creates jobs. A 1,800-row prefix exited 0
and retained only the non-gameplay initial camera difference. Full handoff
qualification and terminal readiness checks remain pending.

One full retry failed closed before any controller rows because Pulse briefly
reported the new process-owned stream's sink as unassigned (`4294967295`). The
watcher now permits that unassigned state to settle under its deadline while
still immediately rejecting any assigned physical output. The owned private sink
was checked before relaunch. No desktop sound or acceptance measurement came
from that failed startup. All subsequent source/hardware flags must be pinned
before the numeric baseline; the latest prototype uses `-Xmx3g`, whereas earlier
S1/S2 qualification probes used `-Xmx1g`.

### C8 — live construction/reset uses the selected world

The normal Engine exposed a setup difference hidden by matching bootstrap and
world modules in the existing headless fixtures. `AbstractPlayableSprite` used
`bootstrapGameModule()` at construction and reset even after a world had selected
S3K. The bootstrap remained S2: Tails' first CPU dispatch wrote $4000 instead of
S3K `sub_13ECA`'s $7F00 (sonic3k.asm:26806). Status/animation refresh later repaired
the cached rules but could not undo that first dispatch. Rebinding the legacy
configuration before Engine construction did not fix this and was rejected.

Two new `TestPlayableSessionRules` regressions preserve an S3K world with a
different S2 bootstrap. Both failed before the change (2 assertion failures,
0 errors/skips). Construction and reset now use the existing
`currentOrBootstrapGameModule()` accessor, preserving the pre-session fallback.
Focused tests `TestPlayableSessionRules,TestPhysicsProfileRegression,
TestHybridGameRules,TestPhysicsProfile` passed 60 checks, 0 failures/errors/skips:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dtest=TestPlayableSessionRules,TestPhysicsProfileRegression,TestHybridGameRules,TestPhysicsProfile test -B
```

The private normal-Engine 1,800-row prefix (raw rows 289–2088) then matched both
characters across X/Y, X/Y/ground speed, angle, air, rolling, both subpixels,
routine, status, animation and mapping (14 fields each; 0 differences on compared
rows). Initial raw row 289 is a non-gameplay observation under the canonical
policy. The complete rendered route and C8 broader checks remain in progress;
the previously completed broad result predates C8 and is not its validation.

The preceding full readiness-controlled prototype completed all 20,509 rows
289–20797 only after the ordinary title-card/loading iterations were observed by
the playback cursor. Before that change it stopped seven rows early; that run
was rejected despite reaching the presentation iteration cap. Strict EOF now
requires every input row. The two raw P1 differences occur only at non-gameplay
rows 289 and 20791; every canonically compared P1 state matched. Full P2 and
terminal-job verification is still required. These are driver/accuracy trials,
not rejected performance experiments (the performance rejection streak is zero).

The completed C8 private rendered run executed 20,509 rows, 289–20797, and
20,512 normal presentation updates at 640×448 on the RX 9070 XT. The existing
`TraceBinder` / canonical phase and comparison projection applied to the captured
CSV passed **20,463 compared observations, 0 errors/warnings**. Raw checks retain
26 sidekick status differences at rows 16217–16242 (facing bit only); they satisfy
the existing airborne, zero-horizontal-speed facing-only comparator predicate.
That predicate was not changed. Stand-slot raw sentinels remain documented as
engine -1 versus native 0 when no object is ridden. No gameplay value was written
from comparison data.

The live terminal state has exactly the original control's four KOS_MODULE_QUEUE
parents, ordinals 38–41, matching all four fingerprints and ROM sources $0D6F28,
$15C3A2, $0D6D84, $39BEDA. Unmatched recorded completions are zero. The destination
HCZ title owner has submitted its load; the fixture ends before that load finishes.
Its EOF correctly resets live rewind history; filled-history RSS must be measured
in the preceding frozen gameplay window, not at that empty-history EOF.

Real process fdinfo also exposes kernel `drm-resident-vram` and workload engine
counters. Deduplicate FDs by `(drm-pdev, drm-client-id)` as required by the
[DRM usage-statistics specification](https://docs.kernel.org/gpu/drm-usage-stats.html).
The probe had one active discrete client and one integrated setup client; both
reported zero shared VRAM. These are measured resident backing-store counters,
not requested texture sizes. This correctness-run observation is not a frozen
VRAM benchmark or performance gain; sampling cadence and window aggregation remain
to be qualified and pinned.

C8 follow-up/domain validation passed 80 checks with no failures/errors/skips,
including the new bootstrap-fallback regression, original AIZ complete trace,
LZ2 complete trace, and all four mandatory S3K loading/bootstrap classes. Command:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dtest=TestPlayableSessionRules,TestS1Lz2CompleteRunTraceReplay,TestS3kAizTraceReplay,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils \
  "-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

ARZ's complete private rendered route passed 5,073 input rows / 5,064 canonical
compared observations, 0 errors/warnings (Sonic and Tails included). LZ1's rendered
1,500-row prefix failed at row 534, rings 0/1; all checked movement fields matched,
and rings caught up at 535. Native first VBlank is $6C69 while normal fresh load
starts at 2. A startup-clock phase hypothesis is unproven; no clock was copied
from trace data. That route is not qualified by trimming it. LZ2's independent
complete trace passes. Its first rendered full attempt was invalidated at roughly
7,560 rows by concurrent recompilation of its own target/classes, which removed a
lazily loaded cleanup class. Keep ALL builds separate from live runs, including
correctness qualification; the required complete LZ2 retry remains pending.

LZ2's rebuilt, isolated private rendered retry passed all 10,173 input rows /
9,901 canonical compared observations (0 errors/warnings), with 10,307 normal
presentation updates and live rewind current/earliest 10173/6570 at EOF.
The input-only maintained normal-Engine tool reproduced GHZ1 fullrun at 3,905
input rows / 3,900 compared observations, 0 errors/warnings. Its proposed 240-row
window verified 240 executed simulation frames using actual level-counter deltas,
and exactly 3,600 retained rewind frames at both boundaries. Its optional CSV
mode is correctness-only and its numeric output is not an acceptance baseline.
The original short normal S1 route leaves only four seconds after history fills;
a longer GHZ1 complete-run fixture is being checked before freezing the window
so memory stability and smaller improvements can be assessed over a longer span.

`RuntimeInputPlan` and `RuntimeEngineBenchmarkTool` promote the recurrent
qualification method into engine-facing tools. Plan compilation runs in a
separate process and retains only controller inputs (including the preceding
latched input), structural row policies and approved hardware completion edges.
No expected physics/auxiliary values are retained by the runtime. Two focused
input-plan regressions first caught missing optional `source_bk2`; the compiler
now uses the existing adjacent-movie fallback when absent. Round-trip controller
identity, immutable ownership, comparison-field exclusion and unknown-field
rejection passed alongside all three session-rule checks (5 tests, 0 failures/
errors/skips). The maintained tool verifies actual simulation counter deltas and
filled history, and rejects incomplete workloads; requested full-row counts are
labelled separately from measured executed-frame counts.

The longer GHZ1 complete-run fixture passed its independent control (1 test,
0 failures/errors/skips) and the input-only normal Engine: 5,598 controller rows /
5,595 canonical observations, 0 errors/warnings. Its proposed 4000–5500 window
verified 1,499 executed simulation frames (one held row) and exactly 3,600 retained
rewind frames at both boundaries. External sampling recorded 489 observations
while that CSV-enabled qualification window ran; those values are not the numeric
acceptance baseline. The six-scenario proposal now uses this longer normal S1
route and LZ2 as its demanding route.

The external sampler's independent controls pass (2 Python tests): duplicated
FDs are deduplicated within the device/client pair, requested storage is ignored
in favor of residency, and unknown units fail instead of producing plausible
byte counts. Measurement code and all windows remain to be frozen after the
remaining input-only driver qualifications.

The maintained input-only S2 normal EHZ1 run passed all 5,852 canonical compared
observations, 0 errors/warnings, with Sonic and Tails included. Its 4000–5500
window verified 1,500 executed frames and full 3,600-frame rewind retention at both
boundaries. The normal startup retains the previously observed absolute level
counter offset +26; comparator policy does not certify that absolute origin.
Measured counter deltas and controller/trajectory identities are explicit; no
expected counter was written into the engine. This is a coverage limit, not a
claim of matching every raw CSV clock field.

The first maintained S3K full-route qualification failed closed after its
measurement window: fade entry held the cursor at the end boundary, causing a
second interval write beyond its fixed array. No numeric result was accepted.
The tool now closes the boundary exactly once and distinguishes presentation
interval count from controller rows and verified simulation frames. A bounded
interval capacity includes legitimate held presentations; only recorded samples
enter percentiles. Complete S3K rerun is required. EOF reporting now also captures
actual pending-job kinds/ordinals/fingerprints/ROM sources for comparison with
the existing terminal control. This remains driver qualification, not a
performance experiment; the rejection streak is zero.

After the boundary/reporting repair, focused
`TestRuntimeInputPlan,TestPlayableSessionRules,TestHardwareTimingAuthorityGuard,
TestTraceHydrateSwitchDefault` completed 31 tests, 0 failures/errors/skips under
Java 25 with normal queued `-Dmse=off -Dtest=<selectors> test -B`. The full S3K
input-only rerun remains active; this focused result is not broad validation.
