# Implicit state machines: inventory and first remediation

## Scope and ownership

This is a static inventory of state-machine families, followed by two bounded
interventions. It is not an exhaustive count of runtime machines or a claim of
whole-project correctness. Discovery started on `22dcba2a1`; the integration
branch starts on fetched develop `a3ab4c74e`. The intervening upstream commit
changes media configuration, not either lifecycle owner.

The parallel code-complexity effort owns contact/population/linked-child rewind
and draft PR 266. Its source and validation trees remain untouched. This work
uses a separate task branch and will be delivered as a separate draft against
develop, without merging or pushing develop. No rubric score is inferred from
these changes.

## Inventory

A machine is implicit here when transition authority or future behavior depends
on combinations of fields, retained resources, history or call order beyond a
single named phase. An explicit local enum can still participate in such a
protocol. ROM routine machines are intentional translations, not automatically
refactoring debt.

| Family | Main owners | Consequence for contributors |
| --- | --- | --- |
| Level admission and transitions | `LevelIterationAdmissionController`, `FreshLevelTransitionBoundaryController`, `LevelTransitionCoordinator` | Loaded, title-complete, published and playable are different boundaries. |
| Physical service and art readiness | `PlcFrameLifecycleCoordinator`, `HardwareTimingJob`, `KosinskiModuleQueue` | Prepared data, eligible service, publication and consumer claim are separate facts. |
| Trace runs | `TraceSessionLauncher`, run playback coordinator/walker | Comparison closure does not necessarily complete physical service or teardown. |
| Rewind | `LiveRewindManager`, release retry coordinator, `RewindRegistry` | Installation identity, pending release and restoration order matter beyond captured values. |
| Suspension | `GameLoopSuspension`, suspended gameplay | Parking preserves ownership; it does not authorize advancing or destroying the parked world. |
| Host pacing | `LiveFrameRuntime`, fixed scheduler | A host iteration, simulation tick and displayed picture need not correspond one-to-one. |
| Stage round trips | Special/bonus-stage and ending sessions | Provider retention, results, fades, return-state restoration and destination admission have separate lifetimes. |
| Player control | Playable sprite, movement, sidekick, abilities | Queued controls, one-tick markers, histories and effect timers affect subsequent transitions. |
| Object execution | Object manager, placement, execution and retirement | Existence, slot occupancy, spawn eligibility and same-frame execution differ. Reserved to the parallel effort where overlapping. |
| ROM actors | Objects, badniks, bosses, ending actors | Routine/subroutine, animation, timer and family signals jointly determine the next update. Preserve shipped semantics. |
| Zone systems | Events, zone runtime holders, water, scroll and palette owners | Camera, terrain, event routine and published presentation must progress coherently. |
| Checkpoints | Checkpoint/respawn coordinators | Selected restart state is a different contract from a complete rewind snapshot. |
| Sound | SMPS sequencer/driver, request pipelines | Track, voice, fade, tempo and request-admission state have distinct clocks and owners. |
| Audio presentation | Command batches, producer and voice registry | Applied, prepared, committed, rolled back and reverse-release work have different retry rules. |
| GPU presentation | Renderer, upload queues, batches and framebuffer scopes | Pending output and GL restoration require balanced ownership on failure paths too. |
| Input and recording | Input handler, text/recorded-input scopes, recording sessions | Suppressed gameplay may still require edge/cursor bookkeeping. |
| Live capture | `LiveCaptureController`, frame grabbers and recorder | Current audio source, detach obligation, GL readback and asynchronous finalization differ. |
| UI and Stageplay | Native menus, title/results providers, Stageplay/editor workspaces | Selection/playback status does not capture pending navigation or temporary control ownership. |
| Creator execution | Mod runtime/context, scene host and mutators | Registration, failure, disablement, visits and capability closure constrain retries. |
| Networking | Handshake, round engine, client session and outbound writer | Deadlines, generations and pending work must remain attached to the correct attempt. |
| Boot/resource scopes | Headless boot, ROM/asset/audio/GL scopes | Reused JVMs expose initialization/teardown obligations hidden by fresh-process tests. |
| Tooling and extensions | Maven queue/category control, release workflows, racing/examples | OS leases, requests, verdicts and extension run states need separate analysis; script/dependency coverage is incomplete. |

## Fresh-level admission

Owner: `src/main/java/com/openggf/LevelIterationAdmissionController.java`.
Two progression booleans had three reachable combinations: awaiting title,
awaiting the ordinary boundary, and awaiting assembly. A private phase now names
these directly. Whether a boundary exists remains LevelManager's authority.
The independent same-iteration hold still prevents reentering LEVEL on the load
iteration. Pause holds progression. Observation still precedes publication,
including when publication throws; retirement still follows successful assembly
and its observer. No world activation, object admission, identity or rewind
registration order was changed. No behavioral defect was established.

The worker's baseline-with-characterizations and candidate each ran the same 36
test identities, without failures, errors or skips:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestLevelIterationAdmissionController,TestLevelIterationHardwareTimingAdmissionOrder,TestGameLoopFreshLevelHandoff,TestFreshLevelBoundaryAfterSetup' test -B
```

Baseline queue/execution: 34m54s/1m46s. Candidate: 55m13s/1m32s.
An earlier candidate request exited 143 while queued, executing no tests.
The baseline is original runtime plus the new characterization tests; this is
not a broad-suite comparison. Worker source commit: `ffece6a6a`.

## Capture audio lease ownership

Owner: `src/main/java/com/openggf/capture/LiveCaptureController.java`.
The previous `audio` reference represented both a frame source and the real
producer lease. Refused detach deliberately retained that reference, but silence
fallback or restart could overwrite it. The repair keeps `audioLease` until
caller-thread detach succeeds, independently of the source used to produce the
current frame. Restart retries detach before acquisition. Continued refusal uses
silence without acquiring another lease. Background clearing does not detach.

Four deterministic witnesses failed on the original source: losing the producer
lease during drain fallback, failing to retire the lease on restart, acquiring
again while detach remains refused, and losing metadata-fallback cleanup. All
26 pre-existing controller cases passed in that RED run. Post-fix focused checks:

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=LiveCaptureControllerTest,LiveCapturePresentationCoordinatorTest,TestLiveCaptureCadenceBoundary,CaptureBufferOwnershipTest' test -B
```

Working tree subsequently committed as `43254c5c5`: 56 passed, no
failures/errors/skips. RED queue/execution:
34m20s/1m45s; GREEN: 53m49s/1m32s. An earlier GREEN request exited 143 before
admission after at least 61m14s waiting; its cause is unknown. Java 25 and Lua 5.4
were used; ROM discovery reported missing=none. These tests need no GL context.

## Review, remaining questions and delivery

Combined reviewed source: `6cb012490`, following capture cherry-pick `5c377304f`.
Opus 5.5 reviewed it at maximum effort with fast mode off, finding no code defect
in either change. It independently parsed the base and candidate catch sites
and inspected worker logs and compiled capture code, without running Maven.
The broad-catch guard fails at that source until three existing pins are
reconciled. The review approved a one-for-one replacement: ten capture sites
remain ten, seven unchanged, with no extra method or engine call sheltered.

The inner start boundary still reports acquisition/metadata failure and selects
silence; the outer boundary still reports FAILED if start cannot complete.
The close helper still returns detach failure so its caller can reach FAILED
or silence while retaining the real lease. Removing those boundaries would
strand STARTING/ACTIVE or lose recovery. The three old/new fingerprints are:

| Site | Old prefix | Reviewed replacement |
| --- | --- | --- |
| `start` inner | `51a9752d` | `a2499506accdee8cac2dfd1ca8122c78eaa2951a81f24b0e0688150f56444a77` |
| `start` outer | `b43f47a7` | `a1d5cf73e351fb5d8c8ec7e6232d7011957018d75df8674aa69543bf7a7d84bb` |
| `closeAudioOnCaller` | `e1062e6c` | `7f4d2856a8a29a754b892b48c3489ba6a99153f272d04128b239389ffcc047af` |

Only these fingerprints were replaced; count and debt reasons remain unchanged.
The exact guard still needs a real queued pass after reconciliation. Combined
source-matched validation also remains pending. Shared category jobs owned by the
parallel effort are not reusable for this branch's source and inputs. The dry
plan selects 4,483 smoke classes plus guards, two workers, with the runner's
40-minute execution cap per invocation excluding shared queue wait.

The review noted that the observer-failure test checks boundary completion, not
phase retirement ordering; it cannot prove that ordering independently. A
post-review extension exercises a subsequent notified load after observer
failure without inspecting private phase fields. This extension is pending
verification. The existing real-AudioManager capture/backend-swap test is added
to the focused selection; the worker witnesses otherwise use the raw producer.

Capture still admits start after terminal close in the inspected source, and an
old finalizer surviving timeout may interact with that restart. These are
unwitnessed follow-up hypotheses, not defects claimed fixed by the lease repair.
Other inventory families remain research targets, not remediated systems.
The reviewer also noted that other tools can arm a boundary without notifying
admission, potentially retaining stale progression; the old flags behaved the
same way. A refused lease across FAILED abort/restart lacks its own witness.
These remain research gaps, not reported new regressions.

Prefer local phases for mutually exclusive progression and distinct fields for
independent ownership obligations. Reject a generic state-machine framework,
mass conversion of ROM routines, or package splitting as evidence of reduced
complexity: none establishes a better lifecycle contract by itself.

There is no whole-suite or delivery claim yet. User wait is zero; unmeasured
coordination/runtime time remains unknown. Worker worktrees and uncommitted
submodule state must be accounted for before cleanup.

## Delivery validation: focused admission

The exact combined 12-class request at `6cb012490` plus the four supplied
review/doc changes executed 107 tests: 106 passed, one failed, no errors or
skips. The guard really ran (four cases, one failure), and the real
`com.openggf.audio.TestLiveCaptureSurvivesBackendSwap` ran seven passing cases.
All 30 controller cases, the 20 admission cases including the observer-recovery
extension, and the S3K fresh-release/ending witnesses passed.

The sole failure was
`com.openggf.tools.TestBroadCatchSwallowingGuard#mainSourcesHaveOnlyInventoriedLegacyHandlers`.
It reported two old `TraceCapturePresentationSetup.open` signatures without the
trailing `boolean`, and their two observed replacements with it. This is inherited
from `22dcba2a1`: its overloads preserve native-sprite-limit defaults and move the
same cleanup handlers into explicitly configurable overloads. The fingerprints
`4523cca6e5c9b9159a820caf45d81a82cdf9d297d02cf9cdfdbee142a48b87c6` and
`6890b2ce885ff34c942c65f6a389569d0ac79c8242e62cf887243e494781ddd6`
pin the identical attempted cleanup and handler bodies. Only the two method-signature
keys were corrected one-for-one; hashes, counts, reasons and protected cleanup
work are unchanged. No allowance was added or guard weakened. This small
mechanical prerequisite repair is additional to the three reviewed capture pins.

The first request waited 733 seconds for admission; Maven reported 1m43s
execution (launcher wall about 838s). Java 25, Lua 5.4 and surfaceless EGL were
selected; DISPLAY/WAYLAND_DISPLAY were unset; ROM discovery reported missing=none.
The exact combined request is being repeated after the two signature corrections.
The frozen baseline category run was submitted independently while focused
admission waited: 4,483 identical ordinary candidates, 91 batches, guards, two
workers. Its preparation passed in 120.59s. Bounded diagnostics are retained only
for the requested full negative-case/skip comparison, then acknowledged. No
category source tree will be edited or built while its run is active.

The repeated exact selection passed: **107 tests, zero failures/errors/skips**,
including four actual broad-catch guard cases and seven real-AudioManager
backend-swap cases (confirmed in Maven class lines and Surefire XML). Admission
wait was 660s; Maven execution 40.538s; launcher wall 701.03s. Both focused
requests used this command, without `-Pguards`:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestBroadCatchSwallowingGuard,TestLiveCaptureSurvivesBackendSwap,LiveCaptureControllerTest,LiveCapturePresentationCoordinatorTest,TestLiveCaptureCadenceBoundary,CaptureBufferOwnershipTest,TestLevelIterationAdmissionController,TestLevelIterationHardwareTimingAdmissionOrder,TestGameLoopFreshLevelHandoff,TestFreshLevelBoundaryAfterSetup,TestS3kFreshLevelReleaseVint,TestS3kEndingFreshLevelTerrainWait' test -B
```

Tested source was `6cb012490b05aaa8e7a9c856c34395b9eaecd503` plus the observer
extension and reconciled pins recorded here. Runtime was unchanged after the
combined review. The four supplied pending files are preserved together in the
evidence commit; the pin file additionally includes the two inherited signature
corrections above. Broad comparison and draft delivery remain pending. Sol owns
validation and routine delivery; no further agents, reviewers or observers were
started. User wait is zero; unmeasured coordination time remains unknown.
