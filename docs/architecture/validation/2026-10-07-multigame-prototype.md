# Multigame prototype implementation evidence

Original base: `6d817a9d74f135714f3da59ab9aab156cc09473e`.
Branch: `feature/ai-multigame-prototype`, isolated review delivery against develop.
The [accepted blueprint](../designs/2026-10-07-multigame-blueprint.md) remains the
future MVP/product roadmap. This record concerns the process-host prototype.

## Implementation and decisions

- One managed JVM owns each real production game. No singleton-root swapping,
  historic GameRuntime or speculative engine-wide GameInstance refactor.
- `ExclusiveLiveGameDriver` is a lifetime input/step lease; one common held byte
  admits one LIVE native iteration and each native polling owner derives edges.
  Physical/debug/movie/trace/recording/pacing routes cannot compete. Genesis Start
  stays game input, not host pause. Transport ordinals never supply gameplay.
- Production hidden-GL bootstrap and complete native renderer produce top-down
  320×224 RGBA; production no-device SMPS presentation produces 48 kHz stereo PCM.
  A worker has no OpenAL device; one host sink applies post-synthesis focus.
- Title, loading, ready/countdown, linked pause/restart/fault/exit, keyboard and
  standard-pad control live in small host owners. ROM-backed menu cues have their
  own standalone presentation clock, separate from all three workers.
- Protocol v1 bounds commands, identities, frame/text/PCM sizes and one outstanding
  request. Host cancellation checks health under the same monitor as tuple commit;
  GPU publication checks generation again on the UI thread.
- Worker directories are owned temporary paths under this tree's target. Cwd/class
  path and environment whitelist exclude linked config and injected JVM switches.
  Normal/forced process close is bounded; remaining owner releases continue after
  any cleanup failure and preserve the failure for honest reporting.

Rejected approaches: active-root swapping and three SessionManager opens retain
shared mutable oscillators/media or destroy the previous mode (owning source in
blueprint). Logical input override alone leaves physical/debug input and
fast-forward pumps reachable; the exclusive lifetime seam closes that surface.
Using capture settings unchanged forces skipped AIZ intro/audio off, so workers
use the underlying production boot with an explicit native recipe instead.

## Independent review

The initial read-only ownership audit found failed OpenAL-sink reuse on retry,
cleanup short-circuiting and commit-after-close races. Source fixes dispose sound
owners before retry, aggregate all releases, and reserve/commit admissions under
the close monitor. Regressions exercise transported PCM copy/pause/flush, owner
release after injected failure, and a collected tuple rejected after closure.
The final integrated source review found zero concrete blockers in the actual
`2fc65c847..81e862140` diff. It did not execute tests or certify runtime behavior.
Its report is retained outside the repository as `final-review-r2.md`.
The later bounded `composed-audio-review.md` audit inspected the substantive
develop audio/API integration and `be3c31418..1bef7a8cc`: zero established source
blockers. It checked indexed S3K bank identity, saved-duration/rest snapshot
ownership, independent menu presentation and unchanged input/worker boundaries.
Execution and compiled signature verification remained the implementation lead's
responsibility; their results are recorded below.

## Support and honest gates

Native Sonic GHZ1/EHZ1 and Sonic+CPU Tails AIZ1, 320×224, NTSC 60, LIVE readiness with the normal PROFILED loading
data, native intros, no donor/custom packages. This is simultaneous playable
opening-level feasibility. No opening-act common completion, full campaign,
linked rewind/checkpoint/replay resume, leaderboards, native-image or all-platform
claim. Unsupported special/bonus/ending renderer branches stop recoverably.

The maintained common program is a neutral-entry/movement/jump witness, not a
winning route. Existing engine rewind remains in each fresh session, but this
host intentionally exposes no linked restore; future checkpoints must include
poll baselines, all independent clocks and a transactional host cursor.

No new zone/act or route certification is delivered, so existing level matrices
and AIZ→HCZ obligations remain inherited; a load/movement witness does not close
their coverage gaps. No trace frontier change or trace hydration is introduced.
This task adds no public ModApi surface or version change. The composed candidate
pin includes upstream additions and is checked against actual compiled source.
Cross-team overlap is confined to
GameLoop/InputHandler/external ownership and the final-PCM sink intake. Hardened's
nativePlayerInput/fresh-launch hooks and Mutators' overlay/settings contracts are
separate; this branch has no code dependency on either team.

## Verification accounting

Tool preflight with Java 21.0.12.1 and `LUA_BIN=lua5.4` passed. Default `lua` is 5.5,
so guard runs use the explicit 5.4 binary. ROM identities verified against the
AGENTS table through original absolute files. Original base remains an ancestor. Recovery merged completed worker
`ee9913faa` through `7df56f239`, then fetched and merged actual destination
`2fc65c847` through `81e862140`. The independently added protocol was identical
after whitespace normalization; both release entries were retained. Upstream
title-new-game forwarding, unsupported two-player title handling and host-module
invincibility-music restoration remain intact. That initial `2fc65c847` plan
selects 3,018 ordinary classes plus fresh-JVM guards. Preflight passed on this
merged source with Java 21, explicit Lua 5.4 and PowerShell; it executed no tests.

Initial host focus `21533` passed 28 tests with no failures/errors/skips before
worker/destination integration. The earlier `59115` source/admission race failed
test compilation and provides no test result. Old compile `35496` has fresh
compiler outputs but no recoverable console completion; timestamps are not a pass.
The new actual-destination focus `5251` completed on `0599bff789`: 75 tests
passed with zero failures/errors/skips in 15 fresh XML reports, inspected and
consumed. Java 21 compiled 3,715 main sources; Maven execution took 75 seconds after
a 4,619-second queue wait. The compiled `ModApiSignatureSurface --snapshot` export
contains 20,048 lines and exactly matches the normalized 0.7 candidate pin. The
release descriptor and published pins remain unchanged.

Worker focus `25181` passed 67 unchanged cases and failed the native pause guard;
a bounded production probe identified `GameStateManager.isGamePaused` as the
normal ROM Pause_Loop authority. The corrected native case `26792` passed 1 with
no failures/errors/skips. Its fresh process diagnostic preserved 36 exact forward
and replay RGBA/PCM/owner frames at `captures/worker-replay-final-1` outside the
repository. This is the worker's observed GHZ1 GPU and pre-device PCM evidence,
not final integrated triple-host or device certification.

The actual title/missing-ROM diagnostics captured 344 and 34 desktop frames, but
failed overall: PixelFont's fixed 224-pixel geometry used the host's opposing
projection, and standalone `setRom` rebuilt the producer and dropped its supplied
UI sink. Recovery corrects the host-only font projection and uses the explicit
ROM loader without that reconfiguration. Five real ROM menu cues and two exact
production-spawn hook-registration failures have regression tests. A shared
acquisition rollback reaps the child, closes streams/executor and removes only
its generated directory before constructor failure escapes.

Native capture on this Linux desktop previously diagnosed an OpenAL
libsystemd/keyutils dependency. Helpers offer a child-only existing-library
preload, private Pulse null sink and optional child-only driver-vsync settings.
A final window is required to match exact PID/title/viewability/positive geometry
immediately before recorder launch; actual frame counts and recorder/process
status accompany the evidence. `KeyRelease` must use Xlib's release event class;
changing the `type` argument on `KeyPress` still serialized type 2. No peer source
or artifact is imported as this branch's observation. The following captures and
package checks predate the substantive develop audio/API composition; later
composed-source evidence is recorded separately below.


## Observed integrated presentation

Evidence root outside the repository:
`$MULTIGAME_EVIDENCE/captures/`, where the task scratch directory is recorded in
the owning temporary-agent registry. Reproduction uses a new outside-repository
output directory; committed input remains `examples/three-openings/common.pad`.

`host-final-1` used the normal default-visible host window, exact PID/title,
IsViewable mapping and 1024×700 geometry before ffmpeg. It completed 1,800 committed
common ticks / 5,400 synchronized state rows, 1,055 actual encoded window frames,
host/video exit 0 and managed recorder SIGINT 255; all acquired processes stopped
and the private audio module was unloaded. `title.png` and `play-1536.png` were
inspected after synchronized rows; `observed-window-31s.png` was extracted from
the actual X11 video and inspected separately.

At tick 1,440, S1/S2/S3K centres were (80,944)/(96,656)/(5002,1051). After the same
Right+C signal, tick 1,536 centres were (240,915)/(251,625)/(5216,1024); the inspected
native GPU image shows all three characters jumping. Native V-int/level clocks
were 1385/1384,1476/1476 and1535/1535 at that same host ordinal. All tuples used
one common held value; initial native non-polling iterations retain distinct
poll boundaries. Final native centres/rings were (1141,885)/1,(1296,625)/5 and
(6493,1050)/7. This is movement/jump evidence, not completing an act.

The private OpenAL-device loopback `speaker.wav` is 48 kHz stereo / 35.5 seconds;
per-channel temporal AC RMS is 1255.656/1299.227, with peaks below clipping.
`focused.wav` has exactly 30 seconds of production focused packets; `menu.wav`
contains separately decoded ROM cues, with per-channel AC RMS 228.842/387.415.
The audio inspector centres each channel independently, so different constant
stereo DC levels cannot impersonate temporal sound. The artifact is device
loopback, not a claim of physical-speaker audition.

Visible-host admission-to-GPU-publication has 1,800 samples:
p50/p95/p99=2.16/4.10/6.43 ms. Sampled peak host RSS is 508,948 KiB at 0.2-second
intervals, not an unsampled allocation/GPU-memory bound. Boot/preparation from
LOADING to READY took about 1.08 seconds in this run. Additional worker/probe
RSS and tuple budgets are recorded by the completed isolation oracle below.

`host-lifecycle-final-1` passed actual-key loading cancellation; pause with a
SIGSTOP-stalled owned worker committed exactly one pending tuple (9→10) and no
more; focus 0→1; fresh generation restart; an owned worker kill with sibling
teardown; retry and title/exit; then a second missing-ROM session with no worker
launch and clean exit. Its two actual windows encoded 421 and 42 frames. Selected
presentation rows and the readable missing-ROM image were inspected. Actual
device PCM has 16.45 seconds with channel AC RMS 658.514/1033.744. Teardown closed
both sessions/recorders and unloaded only the private sink. The observed X11
release sent just after title exit was tightened in the helper: only a destroyed
owned window on release is accepted; press/other dispatch errors fail.

Later default-WM follow-ups `host-focus-final-1` and `host-focus-final-2` failed
before recording: exact owned PID/title/positive geometry existed but remained
IsUnmapped for 30 seconds. The second main thread was RUNNABLE inside
`GLFW.glfwCreateWindow` after 22.79 seconds. Both failed helpers reaped the host
and unloaded the private sink. A bounded isolated GLFW 3.4 startup probe reproduced
this with default-visible creation; hidden-first creation returned but explicit
show stalled. No speculative production window/backend patch was retained.
The [GLFW 3.4 X11 source](https://github.com/glfw/glfw/blob/3.4/src/x11_window.c)
shows the visibility wait used by create/show; attributing the observed stall to
that wait is an inference from these boundaries, not a native C-stack proof.

The explicitly requested `--unmanaged-window` diagnostic maps only the same
owned PID/title surface without WM decorations and records that choice. It
retains the ordinary host loop, exclusive common input, complete production
GPU/PCM and 60 Hz admission. This can qualify media under that diagnostic
presentation setup; it cannot certify the failing default-WM startup. The
normal first play capture and two lifecycle sessions remain separately observed
successes. The following all-focus observations also predate the later composition.


`host-focus-final-3` explicitly used the owned override-redirect diagnostic
window, with the same native loop/input/producer code. It completed 1,800 ticks,
1,051 encoded frames and clean teardown. Actual host focus changed at committed
ticks 908 and 1,451 (requested observation thresholds 900 and 1,440); all 5,400
native state rows are byte-identical to `host-final-1`. The 35.5-second device
loopback has per-channel temporal AC RMS 1045.641/1101.253. A 1,024-frame PCM
anchor from each focused stream at ticks 600/1,200/1,680 matches its independently
booted native solo stream, the host's post-focus PCM and the actual device
loopback bit-exactly. Device offsets are 722,208/1,202,208/1,586,208 stereo frames.
This proves each chosen ROM-native stream reached the device under the explicit
diagnostic presentation setup. The default-WM limitation remains open.

`isolation-final-1` completed the maintained common program independently for
three solos, a S1/S2 pair, the triplet, duplicate S1 worlds and reversed roster.
Every native RGBA/pre-focus PCM/state tuple matched its solo oracle across all
1,800 ticks. Sibling load/close/reopen/crash preserved survivor media; stale
generation was rejected before publication. A managed fresh checkpoint diagnostic
matched 36/36 RGBA/PCM/native-owner packets while 765 survivor/oracle tuples
matched, including 20 after diagnostic exit. All managed processes were stopped.
Selected native images and synchronized states are maintained per cell.

| Probe cell | Boot ms | Step p50/p95/p99 ms | Sampled workers RSS KiB | Sampled probe RSS KiB |
| --- | ---: | --- | ---: | ---: |
| S1 solo | 650 | 0.960 / 1.811 / 2.820 | 342,072 | 368,592 |
| S2 solo | 729 | 0.849 / 1.611 / 2.755 | 378,556 | 376,536 |
| S3K solo | 749 | 1.127 / 2.833 / 4.680 | 343,796 | 377,444 |
| S1/S2 pair | 589 | 0.966 / 1.969 / 3.689 | 695,156 | 393,556 |
| Triplet | 826 | 1.426 / 3.334 / 5.373 | 1,019,872 | 413,588 |
| Duplicate S1 | 580 | 1.072 / 2.154 / 3.334 | 668,864 | 397,828 |
| Reversed triplet | 793 | 1.345 / 3.232 / 5.128 | 1,037,604 | 399,376 |

Each percentile uses 1,800 complete tuple samples. RSS is sampled every 60 probe
ticks and includes native/runtime memory; it is not a measured maximum between
samples or a PSS/GPU bound. Each worker has `-Xmx512m`; raw RGBA tuple sizes are
286,720 / 573,440 / 860,160 bytes for one/two/three members. At 60 tuples/second,
three RGBA streams carry about 51.6 MB/s plus bounded PCM and headers. Each
endpoint permits one pending request. Visible-host publication above adds actual
UI scheduling/upload cost; neither metric is end-to-end physical-pad-to-speaker
latency. The assessed p99 values fit the 16.67 ms admission target, while an OS
stall still slows the whole tuple without dropped input or fabricated catch-up.

The shared clean destination trace baseline `65406` at exact `2fc65c847` completed
with three assertion failures, zero errors/skips in 108 seconds after a 4,477-second
queue wait. Its light source/identity/assertion summary is in the Hardened task
scratch; this branch reuses it, without running another baseline. AIZ/AIZslice/HCZ
first mismatches remain 20302 animation 0/5, 25589 animation 0x13/5 and 9482 air 1/0,
with totals 57/99/4,699. These are three red trace methods, not ordinary validation
or a trace pass. The composed candidate comparison is recorded below.

## Packaged recovery verification

Normal queued `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests verify`
completed on `b4dbbf338`: Java 21 compiled 3,715 main sources; execution took
87 seconds after 4,197 seconds waiting. The engine/fat jars and attached SDK and
Javadoc jars were built, and `verify-openggf-mod-sdk-artifacts` executed
successfully. Tests were explicitly skipped; this is packaging evidence.
The signature snapshot exported from the actual packaged fat jar contains
20,048 lines and exactly matches the normalized 0.7 candidate pin.

Installed Python Xlib requires a truthy handler return to acknowledge a collected
protocol error. The maintained helper now returns true before inspecting and
raising press/other errors; only a destroyed owned window on release is accepted.
A bounded native C-stack attachment attempt reproduced visible GLFW creation
stalling, but Linux ptrace policy rejected sibling debugger attachment. It
produced no C stack, reaped the exact probe, and removed its temporary source.
The visibility-wait attribution remains explicitly inferred.

## Composed published-source verification

Recovery intent-merged the substantive audio/API destination `be3c31418` into
`1bef7a8cc`. After both frozen requests ended, fetch confirmed published develop
`02796b4c497c3aa887f77a8d8e9697b7009d2b4c`, merged as `d2213a557`.
The latter successor adds only the Sitar design document and root launcher;
Java, resources, POM, API, hooks and category selection are unchanged from be3.
Original `6d817a9d7`, input `7c9b5cb6a` and worker `ee9913faa` remain ancestors.
No main checkout operations, peer feature import or rebasing were used.

Normal queued focus `74251` on clean frozen `1bef7a8cc` compiled 3,720 main and
3,595 test sources with Java 21: 29 fresh reports, **218 passed, zero
failures/errors/skips**. Execution took 121 seconds after 10,705 seconds waiting.
Its selectors covered challenge/worker ownership, saved-duration/rest and indexed
bank semantics, SMPS/audio rewind snapshots, scene music/network lifetime, Mod API
policy and SDK tooling. `verify dependency:build-classpath` also built the engine,
fat jar, SDK and Javadoc artifacts and passed the SDK artifact verifier.
The actual packaged signature export contains **20,105 lines**, exactly matching
the normalized unpublished 0.7 pin. The release descriptor and published pins
remain unchanged. Reports and actual Linux/Java/three absolute ROM properties were
inspected, then consumed; no raw Maven archives are retained.

Normal trace-profile request `21216` on the same source ran exactly nine
`replayMatchesTrace` methods: five S1 complete-run fixtures (GHZ1, MZ1, LZ1, SBZ1,
FZ) and S2 EHZ1 passed; S3K AIZ/AIZslice/HCZ failed with **three assertions, zero
errors/skips**. Execution took 65 seconds after 7,519 seconds waiting. Each full
assertion and test identity is literally equal to the shared clean `2fc65c847`
SAME3 baseline above. This is candidate execution after the be3 composition,
with unchanged concrete inherited divergences; it is not a clean be3 baseline
execution or a trace pass. No frontier moved and no fixture selected new work.

### Rebuilt-package audiovisual observation

`captures/host-focus-final-4` uses the newly verified fat jar and committed
`common.pad`, with the explicit owned override-redirect diagnostic setup.
Exact PID/title/IsViewable/1024×700/depth24 preceded recording. It completed
1,800 ticks / 5,400 common-input state rows and **1,057 actual window frames**.
Host/video exited 0; device recorder ended by managed SIGINT 255; all processes
were reaped and the private Pulse module unloaded. The title, tick 1,536 GPU image
and actual video frame at 31 seconds were inspected with selected CSV rows.
All three native players visibly jump on held mask 40, retaining the independent
native clocks and centre positions recorded above.

The actual device loopback has 35.55 seconds at 48 kHz stereo, per-channel temporal
AC RMS 1045.934/1101.812; focused packets have 30 seconds and AC 1120.534/1133.168.
ROM-native menu cues have 34.933 seconds and AC 228.608/463.898. Focus changed at
committed ticks 909 and 1441. Each 1,024-frame native-solo/post-focus/device anchor
at 600/1200/1680 is bit-exact, at device offsets 727840/1207840/1591840 stereo frames.
This proves actual device output under the recorded diagnostic setup, not
physical-speaker audition or default-WM certification.

Host admission-to-publication uses 1,800 samples: p50/p95/p99=2.59/4.73/7.53 ms.
Sampled peak host RSS 501,820 KiB at 0.2-second intervals; preparation took about
1.062 seconds from LOADING to READY. The sampling and latency limits above apply.

`captures/isolation-final-2` uses that same rebuilt package. All seven 1,800-tick
cells matched complete RGBA/pre-focus PCM/native-state solo oracles. Sibling
load/close/reopen/crash and stale-generation checks passed. The managed S1
checkpoint diagnostic matched 36/36 GPU/PCM/native-owner frames while 796 complete
survivor tuples agreed, including 20 after diagnostic exit. Duplicate/reversed
tick 1,536 rows and the replay-end image/rows 35–36 were inspected. All processes
stopped. Fresh resource measurements are:

| Probe cell | Boot ms | Step p50/p95/p99 ms | Sampled workers RSS KiB | Sampled probe RSS KiB |
| --- | ---: | --- | ---: | ---: |
| S1 solo | 625 | 1.019 / 1.929 / 3.182 | 332,696 | 371,192 |
| S2 solo | 658 | 0.981 / 2.056 / 3.724 | 361,112 | 381,844 |
| S3K solo | 719 | 1.170 / 2.970 / 4.760 | 345,932 | 382,412 |
| S1/S2 pair | 663 | 1.009 / 2.101 / 3.930 | 690,168 | 394,104 |
| Triplet | 791 | 1.651 / 3.671 / 6.338 | 1,045,812 | 411,748 |
| Duplicate S1 | 698 | 1.661 / 3.072 / 5.392 | 665,012 | 381,096 |
| Reversed triplet | 913 | 1.959 / 4.348 / 7.421 | 1,042,676 | 377,216 |

Each cell has 1,800 samples, RSS every 60 ticks, worker heap 512 MiB and one pending
request per member. The diagnostic does not widen supported gameplay scope.
The earlier normal-window lifecycle evidence remains attributed to its source;
the composition did not change those host/input/lifecycle owners.

### Final category gate and handoff

Final combined change-based validation used the actual published destination.
The `02796b4c497c3aa887f77a8d8e9697b7009d2b4c` plan selects
3,031 ordinary classes, all categories, plus separate fresh-JVM guards. Tool
preflight passed with Java 21, explicit Lua 5.4 and PowerShell; no tests executed.
The latest measured baseline cost is about 109 minutes ordinary plus 3.6 minutes
guards. The candidate invocation used a 150-minute execution cap and 10-minute
no-output stop; queue waiting is excluded and a timeout means incomplete.
The qualified published be3 ordinary baseline is red: run
`20261007T213945Z-2b9d2b4b`, 3,020 selected / 3,018 reports / 26,373 tests,
28 inherited assertion failures, zero errors, 62 skips, 6,523.21 seconds; separate
fresh guards 86 reports / 672 passed / zero skips, 214.76 seconds. Its owner compared
all complete failures/skips to the durable 4cfb baseline: 27 literal assertions
after stripping the exception prefix and the full SSZ assertion using only the
independently verified `RewindObjectStateBlob@hex` normalization. The launcher/doc
successors change no executable contract. This is inherited-failure qualification,
not green full-suite evidence or a substitute for this branch's candidate run.

After that gate, delivery pauses at a clean implementation handoff. The user
assigned root-owned Opus polish and a separate promo-video stage before the final
feature push/one PR. This lead does not create duplicate polish/video workers.

Run `20261008T010238Z-67cefaaa` on frozen `decfd1a50` completed: ordinary
**3,029 reports / 26,416 tests / 28 assertion failures / zero errors / 62 skips**,
5,205.16 seconds after 446 seconds queue waiting. All 28 complete assertions and
62 complete first causal skip reasons matched the qualified baseline, with zero
missing/unmatched/omitted cases or ROM skips. Twenty-seven assertions matched
literally after exception-prefix stripping; only the known full SSZ assertion
used the verified blob-identity normalization. Required S3K bootstrap, decoding,
AIZ-skip and both level-loading classes passed 60 cases without skips. Linux
Infinite passed 206 of 238 with 32 expected skips; its driver observation passed.
This is completed ordinary validation with inherited failures, not green evidence.

Separate fresh guards ran 86 reports / 672 tests in 218.78 seconds: two **new task
guard failures**, zero errors/skips. The singleton closure guard found the probe's
JDK digest factory imported by wildcard, which its existing owner-scoped exact
import recognition cannot resolve. The size ratchet found GameLoop at 3,400
effective lines against its unchanged 3,381 budget. These blocked clean handoff.
The inspected run diagnostics were acknowledged and deleted; light identity/full
comparison summaries remain in task scratch, without raw log/XML archives.

Repair uses an exact `MessageDigest` import and extracts exclusive-driver
readiness checks, no-owner rejection and iteration pause/callback handling into
the existing `ExclusiveLiveGameDriver`. Conditions, exceptions, callback order,
try/finally behavior and native iteration/input owners are preserved. No guard,
budget, public API, timing rule or algorithm is changed. Verification of this
bounded repair uses affected exclusive-input/native-worker tests, a new compiled
package/API/SDK check and fresh guards; the completed ordinary and domain checks
remain attributed to their source. Native isolation/media is rechecked from the
rebuilt package. The bounded independent `guard-repair-review.md` review found
no established blocker. It checked admission short-circuiting, pause, callback
failure precedence and final presence-manager initialization. An unsupported
internal null-driver call now rejects instead of stepping; the sole production
caller supplies its acquired driver. No supported API behavior depends on it.

### Completed guard repair and implementation handoff

Repair source `c441ab657e6d5239586e3501629648235346e431` remained clean and
frozen through both normal queued invocations:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestExclusiveLiveGameDriver,TestExclusiveHeldInput,TestWorkerGameSessionRom,TestChallengeWorker,TestWorkerReplayDiagnostic,TestChallengeMenuAudioRom,TestChallengeProcessAcquisition,TestSonic1PatternAnimatorRewindSnapshot,TestSonic1PaletteCyclerLz' \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" \
  verify dependency:build-classpath -Dmdep.outputFile=target/challenge-classpath.txt
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -B -Dmse=off -Pguards \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" test
```

The variables denote the original verified absolute ROM files. Focus `64204`
compiled 3,720 main / 3,595 test sources with Java 21 and passed **39 tests in nine
fresh reports, zero failures/errors/skips**; 94 seconds execution after 595
seconds waiting. The new callback test exercises production failure, after-step
exception precedence and host-paused callback exclusion. The native worker case
retains actual GPU/PCM replay and native pause assertions. `verify` built the
engine/fatjar/SDK/Javadoc and passed artifact verification; the actual fatjar
export again exactly matches all **20,105** normalized candidate signatures.

Fresh full guards `69811` passed **86 reports / 672 tests, zero failures/errors/
skips**, 228 seconds after 683 seconds waiting. Both exact previously failing
methods passed. Guard source and the size budget were unchanged. Fresh reports,
runtime and ROM properties were inspected and consumed; light class counts and
the two repaired method identities remain outside Git. The final ordinary run
above remains attributed to `decfd1a50`; this bounded owner extraction is verified
by affected production tests and fresh full guards, without repeating unchanged
ordinary or trace checks. No whole-suite green claim is made.

The rebuilt repair fatjar produced `captures/host-focus-final-5`: **1,800 common
ticks / 5,400 synchronized rows / 1,067 actual window frames**, exact owned
PID/title/viewable 1024×700/depth24, explicit override-redirect diagnostic setup.
Title, tick 1,536 native GPU image and the actual 31-second video image were
inspected. All three jump on held mask 40 with the same native centres/clocks
recorded above. Host/video exited 0, device recorder stopped by managed SIGINT
255; all acquired processes were reaped and the private Pulse module unloaded.

Actual device PCM is 36 seconds / 48 kHz stereo, temporal channel AC RMS
1036.566/1092.094. Native-solo → host post-focus → device anchors of 1,024 stereo
frames each match exactly at ticks 600/1200/1680, device offsets
736544/1216544/1601568. Focus changes were committed at 904 and 1442; preparation
LOADING→READY took 1.315 seconds. Host publication latency has 1,800 samples,
p50/p95/p99=4.52/8.36/15.13 ms; sampled host RSS peaked at 498,008 KiB. These are
this run's observations, not a universal frame deadline, allocation or GPU-memory
bound. Device loopback is not physical-speaker audition; default-WM startup
remains the separately documented limitation.

`captures/isolation-final-3` passed all seven 1,800-tick complete RGBA/pre-focus
PCM/native-state oracle cells, duplicate/reversed membership, sibling load/close/
reopen/crash and stale-generation rejection. The managed checkpoint matched
36/36 RGBA/PCM/native-owner frames while **726** survivor tuples matched during
the diagnostic and **20** more after exit. Selected duplicate/reversed tick
1,536 rows and checkpoint replay rows 35–36/image were inspected.

| Probe cell | Boot ms | Step p50/p95/p99 ms | Sampled workers RSS KiB | Sampled probe RSS KiB |
| --- | ---: | --- | ---: | ---: |
| S1 solo | 746 | 1.070 / 1.975 / 3.032 | 336,300 | 369,828 |
| S2 solo | 640 | 0.899 / 1.625 / 2.779 | 368,100 | 389,408 |
| S3K solo | 735 | 1.221 / 2.893 / 4.781 | 348,696 | 389,904 |
| S1/S2 pair | 587 | 1.031 / 2.014 / 3.358 | 701,304 | 395,208 |
| Triplet | 749 | 1.794 / 5.405 / 9.124 | 1,050,920 | 412,972 |
| Duplicate S1 | 624 | 1.095 / 2.251 / 3.744 | 653,592 | 391,032 |
| Reversed triplet | 841 | 1.345 / 3.412 / 5.540 | 1,011,380 | 391,724 |

Each cell retains 1,800 step samples, RSS sampling every 60 ticks, 512 MiB worker
heaps and one outstanding request per endpoint. All managed processes stopped;
the exact three owned worktree-CWD scan and registry showed no remaining native,
Maven or capture process. These checks qualify the implementation handoff, not
the later full-act/MVP/product gates. Feature push/PR remains held for the user's
root-owned Opus polish and separate promo stage. The final observed pane footer
needs spacing/glyph review during that polish; the native viewport and player
sprites themselves remain complete.

### Final published successor composition

The final fetch found published develop
`33d3976c53304dbbea1c695914ecdd7bfc64cf9d`, intent-merged without conflicts as
`3f0172ddf46b972c0c42ee5dd88c20f4aff5ba8a`. Its fifteen-path delta after
`02796b4c4` adds scoped test-fixture release, optional screenshot readback in
`GameplayCaptureSession` and their controls/documentation. The owning
[memory investigation](../research/2026-10-07-ordinary-suite-memory-cause.md)
records 364 passing integrated focused cases and natural-collection limits;
it explicitly does not certify a green ordinary suite or whole-suite 1 GiB
capacity. That evidence is not relabeled as this branch's execution.

Multigame workers do not use `GameplayCaptureSession`. Its actual gameplay,
input, audio and Mod API/POM/pin sources are unchanged from the repaired-source
guard and native checks. The final actual-base dry-run still selects all
categories, **3,033 ordinary classes plus guards**, without altering selection.
Java 21 / Lua 5.4 / PowerShell preflight passed on the composed source. The
completed broad run above remains attributed to its original source/base;
the bounded upstream capture/fixture changes are supplemented proportionately:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestOwnedMocks,TestGameplayCaptureFrameRendering' \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" \
  verify dependency:build-classpath -Dmdep.outputFile=target/challenge-classpath.txt
```

Normal composed request `44239` admitted immediately and completed in 91 seconds:
**seven tests / two fresh reports, zero failures/errors/skips**, Java 21 compile
3,720 main / 3,598 test sources. Native title/results/ordinary pixels and state
match with optional readback, while scoped mock cleanup preserves another owner.
Final engine/fatjar/SDK/Javadoc and artifact verification passed; the actual
final fatjar again exactly matches the **20,105-line** normalized candidate pin.
Fresh Linux/runtime/ROM properties were inspected and consumed, with a light
source/command/count summary retained outside Git. This is composed focused
validation, not a new full-suite run on `33d3976c5`.

Both fully integrated input/worker child worktrees and local branches were
removed after exact clean-state, ancestry, ignored-resource and process
accounting. Generated config examples matched bundled bytes; only those
workflow outputs and hook-created resource links were discarded, preserving
their original targets. Child results and harness-owned histories remain.
The unmerged lead branch/worktree is retained for root-owned Opus polish;
no feature push or PR occurred during this implementation handoff.
