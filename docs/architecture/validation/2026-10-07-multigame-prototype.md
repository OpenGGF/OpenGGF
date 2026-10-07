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
No public ModApi surface/version/pin changes. Cross-team overlap is confined to
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
invincibility-music restoration remain intact. The actual destination plan
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
or artifact is imported as this branch's observation. Observed final host/device behavior and measured budgets follow below; packaging
and broad/domain validation remain pending.


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
successes. All-focus observations follow below; packaging and broad/domain
validation remain pending.


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
or a trace pass. Final candidate methods must be compared literally.

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
