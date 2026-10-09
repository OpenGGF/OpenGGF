# Ordinary-suite memory-cause investigation

Origin: the request to find what causes test-run out-of-memory failures, following
the focused throughput/per-test sampling task. Integration base:
`5bc5f4fa60728c6c074f9bbf8cf89d6dc6b05f6f` on `develop`; investigation worktree:
`.worktrees/ai-ordinary-memory-cause`.

The actual ordinary diagnostic completed without an OOM, but reached the 3 GiB
test heap's limit during several classes. Its final post-plan requested-GC floor
was **1,646,986,560 bytes / 1.534 GiB**. Growth was concentrated around particular
owners rather than proportional to test count: large mock invocation graphs,
a static source corpus and an audio test's static evidence map. Long
capture routes produced heavy allocation churn without comparable lasting
floor growth. The separate Maven compiler also retained collectible memory.
These establish pressure contributors; the user had no recent OOM error to
identify whether the historical failure was heap exhaustion or a process kill.

## Evidence before the instrumented run

The first [focused sample](2026-10-07-maven-focused-throughput.md#first-per-test-memory-report)
had stable later-pass post-GC floors. Its 42 cases did not cover long routes or
native memory, so it could not exonerate the ordinary suite.

Read-only Linux/JDK probes of an existing main-workspace run found two separate
JVMs. The Maven parent had no explicit heap cap: HotSpot chose
`MaxHeapSize=8174698496` (about 7.6 GiB), while the test fork was explicitly capped
at `3221225472` (3 GiB). Maven's heap used about 1.29 GiB; it had about 1.9 GiB
swapped out. The test fork's heap used about 2.82 GiB, with about 1.17 GiB swapped
out. These are a point-in-time pressure observation, not post-GC live memory or a
proof of a leak. No forced GC or histogram was requested in that other run.

A read-only cgroup check found the profiler and running Maven fork in the same
T3 application scope. That scope and its user-slice ancestors had unrestricted
`memory.max`, `memory.high` and `memory.swap.max`, and zero `oom`/`oom_kill`
events ([kernel counter definition](https://docs.kernel.org/admin-guide/cgroup-v2.html)).
These counters cover their current lifetimes; they provide no evidence
of a kernel OOM kill there, but cannot identify an undocumented crash from a
previous scope, host or boot.

That process was executing
`TestLrzWideColdRouteCapture.coldTeamClearsActOneAndReleasesPlayableActTwoWithIsolatedHistory`,
with its main thread inside `glFinish`/`GameplayCaptureSession.render`. The route
renders and reads back every frame. Source inspection shows the framebuffer's
native readback buffer is freed in `finally`, and the capture session destroys
graphics resources and its native context on close. Those are expected cleanup
paths; their presence does not prove that every allocation is released.

The existing category run `20261007T130421Z-74206bd5` subsequently recorded a
completed ordinary invocation: 3,005 reports, 26,166 cases, 28 failures, no errors,
61 skips, exit 1, 4,313.69 seconds. Its plan recorded head
`945b74e999c3584ad45b92a711b7a6dace7dc294`, one worker and a 120-minute limit.
No recorded failed case contained `OutOfMemory` or `Java heap space`. This is
external contextual evidence, not a clean baseline or this task's certification.
The global `HeadlessStateTeardownExtension` already resets engine state after
top-level classes. Neither test count nor an isolated high pre-GC reading proves
accumulating retention.

A concrete single-test candidate is
`TestS3kKosTimingRewindIntegration.hczRecordedCompletionRewindsBeforeOnAndAfterAdmission`:
it calls `TraceData.load(hcz_completerun)` only to select one hardware completion
edge. Streaming decompression counted **225,332,151 auxiliary bytes / 1,066,724
records**, alongside the physics rows, although the dedicated timing stream is
about 22 KiB. `TestTraceReplayRowPolicy` also loads the full AIZ1→HCZ prefix for a
row-policy assertion: **137,186,685 auxiliary bytes / 640,530 records**. These are
input-volume observations and rework candidates; object retention/peak heap must
be measured before attributing an OOM to them.

## Measurement protocol and rejected shortcuts

`profile_ordinary_memory.py` invokes actual `mvn test` with the ordinary POM
selection, one reused 3 GiB Surefire fork and verified absolute ROM paths. A
temporary service-loaded `SuiteMemoryListener` measures test/class allocation,
duration and sampled peaks, class-boundary heap floors within the active plan,
a final floor at normal JVM shutdown after plan execution returns, metaspace/direct/mapped
buffers, RSS/swap and bounded high-watermark histograms/native summaries. The
Maven parent retains its existing heap configuration and performs cold test
compilation inside the observed process. Separate process-tree probes preserve
Maven/test maxima and periodic heap information. Both JVMs have diagnostic-only
Native Memory Tracking; rotating test GC logs are summarized before deletion.
A final histogram/native snapshot is always collected after plan return; one of
the twelve snapshot slots is reserved for it, so early high-watermarks cannot
consume the final comparison.

The whole diagnostic queues exclusively. Its execution deadline excludes queue
wait; minute heartbeats expose long routes. Source revision and tool hashes,
Surefire outcomes/skips and listener completeness accompany the compact report.
Temporary helper classes, raw XML/events/GC logs and test artifacts are deleted
after extraction. Consumed reports are removed after their durable findings are
recorded here.

## Actual Surefire control

The queued control selected `TestCollisionLogic,TestBuildIdentity` at engine base
`5bc5f4fa6`; the Java observer source was the committed `acfba93f5` implementation.
The Python driver had been launched before that commit from its earlier working
snapshot. After a 2,676-second wait, actual Surefire ran one plan, two classes and
11 successful cases, with no skips/aborts/failures. All three requested GCs were
observed; the final live-heap floor was **7,713,648 bytes** (about 7.4 MiB).

Cold compilation of the complete production/test trees made the Maven process
(`1350069`, independently identified by its `jcmd` heap/flags snapshot) peak at
**2,644,475,904 bytes RSS** (about 2.46 GiB), with no sampled swap. The test JVM
(`1350938`) lasted about half a second and its final boundary RSS was
171,343,872 bytes. The one-second external sampler missed that short fork; its
tree maximum is not a combined parent/fork capacity bound. Maven's initial
`MaxHeapSize` was again 8,174,698,496 bytes. Compiler memory is a concrete
non-test contributor even when only eleven cheap cases are selected.

This control also exposed a reporting race: the Maven shell can exec Java with
the same PID/start time. The initial `other` role must become `maven` while its
peak is preserved. The fixed observer updates the role on the Java transition;
a regression checks the transition without losing RSS/swap maxima. Do not read
the pilot's initial role label as evidence of an unidentified 2.46 GiB process.

Rejected shortcuts:

- A standalone JUnit whole-suite scan: it would not reproduce Surefire's actual
  ordinary tags/exclusions and Maven parent/compilation memory.
- Sampling used heap without GC: this confuses transient allocation with live
  retention. Natural-GC confirmation is still needed because forced GC changes
  allocation pressure and timing.
- Collecting in `testPlanExecutionFinished`: Surefire can execute a separate plan
  per class, and that callback can still retain the completed class context. The
  listener instead collects at the next class start or normal JVM shutdown.
  An expanded control disproved the initial assumption that next-class GC always
  releases the preceding context: a 20 MiB `PER_CLASS` fixture remained live
  through the next class in JUnit 5.10.3, then disappeared after plan execution
  returned. The final floor retained the deliberately pinned arrays. Boundary
  events now record `allPlansFinished`; within-plan growth is a retention
  candidate that can include legitimate Jupiter fixture ownership. Compare the
  final post-plan floor before describing application leaks. Surefire's two-class
  control used a single plan, so this distinction matters to the real invocation.
  The same control gives the normal `PER_METHOD` class a 20 MiB fixture: that
  fixture is released before the next class. Thus the observed `PER_CLASS`
  lifetime does not establish a mechanism accumulating every ordinary test
  instance; most ordinary tests use the default per-method lifecycle.
- Treating all non-heap RSS as a native leak: committed heap, metaspace, JIT,
  thread stacks, shared pages and allocator high-watermarks all contribute.
  HotSpot NMT does not account for all third-party/driver allocations.
- Repeating the old trace-chain OOM explanation for the ordinary suite: trace
  classes are excluded here, and current chain execution plans compact segment
  descriptors rather than eagerly retaining all auxiliary rows. The historical
  POM comment is not evidence of the current ordinary cause.

## Collectible compiler memory and natural-GC control

The full diagnostic was admitted after **5,234 seconds** in the queue, using
observer revision `ef7b7dc55` and engine base `5bc5f4fa6`. After cold production
and test compilation, an ownership-checked experiment requested `GC.run` only
in this task's Maven parent (`1631022`, a descendant of driver `1371741`). The
test fork and other agents' JVMs were not collected by this experiment.

| Parent measurement | Before requested GC | Immediately after | Later settled reading |
|---|---:|---:|---:|
| Used Java heap | 1,331,794 KiB | 81,419 KiB | 195,268 KiB |
| Logical committed Java heap | 2,174,976 KiB | 466,944 KiB | 368,640 KiB |
| Process RSS | 420,564,992 bytes | 591,347,712 bytes | 415,842,304 bytes |
| Process swap | 2,309,410,816 bytes | 2,135,330,816 bytes | 425,291,776 bytes |

About **1.19 GiB of used compiler heap was collectible**. After release settled,
RSS plus swap fell from about **2.54 GiB to 0.78 GiB**. This establishes a
non-test contributor to memory pressure; it does not identify an undocumented
historical OOM. The later used-heap reading includes new allocations and is not
another post-GC floor. Logical heap sizing, NMT committed memory and physical
RSS/swap did not converge immediately, so the immediate RSS increase is not
evidence of a native leak. The diagnostic preserves the original compilation
peak, but parent measurements after this explicit collection are not an
untouched natural-GC baseline.

A separate queued **natural-GC, 1 GiB-per-JVM** control selected
`TestS3kKosTimingRewindIntegration,TestTraceReplayRowPolicy`. All six cases passed
with no failures, errors or skips. It recompiled 3,570 test sources; production
classes were already warm. After a 5,528.6-second queue wait, Maven took 24.654
seconds, with 25.3 seconds of queue hold and a 2.019 GiB process-tree peak RSS.
Thus the two eager trace-loading candidates do not individually exhaust that
configuration. This does not validate cold production compilation under 1 GiB,
or cumulative behavior of the entire ordinary suite.

## Throughput finding deferred to queue follow-up

An acknowledgment of already-inspected diagnostics waited behind another
worktree's ordinary run for roughly 91 minutes. `acknowledge_run` currently
acquires the global exclusive Maven barrier to delete that worktree's completed
run directory. A temporary regression using isolated fake worktrees and real OS
locks reproduced the cross-worktree wait: cleanup did not complete within two
seconds while another worktree held a resource-aware shared execution barrier.
The separate legacy-exclusive-holder control preserved its intended exclusion.

A possible follow-up is to retain own-worktree exclusion and legacy global-lock
compatibility while allowing independent cleanup to overlap resource-aware
Maven jobs. The user explicitly requested preservation of this run and queue
policy during parity coordination. No lock/admission implementation was changed;
the temporary exploratory regressions were removed. This finding is not a
delivered queue fix.

## Source-backed retention candidates

`TestStaleRewindCodecHelperCleanup` added **104.4 MiB** to the requested-GC
class floor. It stores the entire production/test Java source corpus in a static
list and has no release callback. At this revision that is 7,265 files / 74.03 MiB
of UTF-8 source. Estimating compact String backing arrays from the actual text
(Latin-1 where possible, otherwise UTF-16, including aligned array headers) gives
**100.83 MiB**, before String/path/list/record overhead. This is a concrete
avoidable test-owned cache, rather than a ROM-loading or gameplay leak. The
estimate explains the measured scale; it is not a heap-dump retained-size result.

Within-plan snapshots also showed 618,096 Mockito `InterceptedInvocation` objects
after `TestSozMiniboss`, and 1,014,582 after `TestLrzMinibossHitPath`. Their class
floor increases were **358.9 MiB** and **181.8 MiB** respectively. Invocation
records, reflection methods, call locations and weak-reference wrappers all
contribute; the invocation object's shallow bytes alone undercount that graph.
The classes drive many frames against mocks. Mockito 5.14.2's
[own inline-mock contract](https://github.com/mockito/mockito/blob/v5.14.2/mockito-core/src/main/java/org/mockito/plugins/InlineMockMaker.java)
documents weak-map retention risks and an explicit cleanup API. The bounded
post-plan intervention below confirms a substantial release in this
configuration; it does not establish that every retained mock is held by the
inline framework or that blanket teardown is safe for all fixture lifetimes.

The final post-plan histogram still contained **630,458** intercepted
invocations, **663,711** reflection methods and roughly **1.26 million** weak
reference wrappers. Some earlier mock graphs had disappeared, so the largest
within-plan histogram is not the final retained population. The final histogram
also contained 2,618,484 `SpriteMappingPiece` and 1,439,545 `ChunkDesc` objects.
Histograms show shallow class totals, not dominator retained sizes or which
static field owns every instance.

`SharedLevel` is another source-backed candidate: its `Level` field is final,
and `dispose()` resets global state without dropping that reference. Source
inspection found 90 static `SharedLevel` fields, only 20 with any explicit null
assignment. Closed fixtures held in such static fields can retain level/art
graphs. These counts do not prove every field was populated or account for a
specific measured retained size. Do not replace ownership evidence with a count
of suspicious fields.

`TestCompleteRunAudioComparator` added **333,870,728 bytes / 318.4 MiB** at its
class boundary. Its `semanticRetentionRemainsConstantAcrossHalfAMillionCompletedRequests`
test drives 500,000 requests, while the test helper's static
`IdentityHashMap<DriverService, ServiceEvidence> SERVICE_EVIDENCE` retains each
constructed service and its evidence without clearing. The final histogram
contained 500,128 `ServiceEvidence`, 524,708 `DriverService` and about a million
audio owner/state-field records. The production comparator's constant-state
assertion can pass while the test-data factory retains its input evidence.
That distinction makes this a test-owned cache candidate, not evidence that
the production semantic validator retained every completed request.

## Completed ordinary diagnostic

Command, from the investigation tree at diagnostic `ef7b7dc55`:

```bash
python3 tools/testing/profile_ordinary_memory.py --cold-compile --max-minutes 180 --output target/ordinary-memory-full.json
```

The observer and verified ROM arguments instrumented the actual ordinary
Surefire selection: no `-Dtest`, one reused 3 GiB fork, alphabetical order and
requested between-class GC. Engine source remained at `5bc5f4fa6`; main later
advanced through Survivors to `4cfb74564`. These measurements do not validate
those later engine changes or the separately staged parity candidates.

The invocation finished at **17:41 UTC**, Maven exit **1** for assertions:
**3,005 Surefire reports / 26,166 cases / 28 failures / zero errors / 61 skips**.
The observer completed one plan, with zero active windows, 2,959 top-level
classes and 26,148 executed cases (26,077 successful, 28 failed, 43 aborted).
Surefire also reported 18 disabled cases. Candidate source classes, nested
reports, disabled methods/containers and top-level class windows are different
counts; the 3,007-class source inventory is not a claim that every candidate ran.

The fork took **4,706.027 seconds / 78.4 minutes**, excluding queue and compilation.
Its recorded cumulative GC time was **235.368 seconds**. Rotating GC logs
retained 8,602 collection summaries, not the entire lifetime's collection count.
They include evacuation failures near **3,051 MiB of a 3,072 MiB heap**, followed
by full compaction. Such peaks also occurred in the Mod API signature scan,
Slay the Robotnik scene and audio comparator classes; long rendered captures
were not the only transient pressure source. The natural full-GC examples
reduced 3,050 MiB to 1,909 MiB and 3,049 MiB to 1,478 MiB. A successful eventual
collection does not make the preceding near-capacity peak safe under a smaller
heap or more concurrent processes.

Peak sampled process-tree RSS was **5,305,888,768 bytes / 4.94 GiB**; peak tree
swap was **3,687,030,784 bytes / 3.43 GiB**. These maxima happened independently
and must not be added into a purported simultaneous peak. Maven's own sampled
RSS maximum was 2,977,628,160 bytes, and the Surefire fork's was 4,005,044,224
bytes. The tree includes subprocesses launched by tests. The final fork boundary
had 3,745,026,048 bytes RSS plus 1,133,838,336 bytes swap at the same instant.
Final NMT committed memory was 3,918,613 KiB, including the 3 GiB committed
Java heap; direct-buffer accounting was 42,034,714 bytes and mapped buffers zero.
These metrics do not cover every third-party graphics/native allocation.

Skip inspection found 18 disabled diagnostic/opt-in cases and 43 aborted cases:
32 route cases lacked the required platform/flyer setup, four graphics cases
lacked their native prerequisites, and the others were explicit soak/probe,
capture/reference or route-precondition skips. No `@RequiresRom` skip indicated
missing ROMs. The final thread count was 46 Java / 100 OS threads; the roughly
20 Java threads during long capture execution did not show steady buildup,
but the later audio/tooling tail did increase threads. No historical native
thread failure can be ruled out from this one run.

The 28 red assertions remain **unattributed**: this instrumented measurement is
not an uninstrumented baseline/current comparison or a green engine delivery.
No unrelated runtime fix was attempted.

| Failed class | Cases | Failure identity / observed assertion |
|---|---:|---|
| `TestFbzSandopolisTimelineHeadless` | 1 | `productionExitResetsTimelineAndFreshDestinationRestoresAndReplaysTwice`: SOZ restore cycle 0, `instaShieldRegistered` false → true |
| `TestS3kMhzAct2AuthoredRoute` | 2 | `incomingRoutesCompleteActTwoWithLiveRewindBoundaries` [2], [3]: late pulley owns Tails / Sonic |
| `TestDezIncomingFinalRouteCapture` | 7 | Four ordinary ending routes expected 96 / got 0; incoming final fight [1], [2] died at 26706 / 26750; emerald route died at 53897 |
| `TestLrzActTwoColdRouteCapture` | 1 | `coldTeamCompletesActTwoAndReachesBossActWithRepeatableWorldState`: death at 36526 |
| `TestLrzBossColdRouteCapture` | 1 | `coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState`: death at 36526 |
| `TestLrzKnucklesColdRouteCapture` | 1 | `coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace`: expected 1069 / got 899 |
| `TestLrzTailsColdRouteCapture` | 4 | Four cold Tails act/fight/handoff routes: death at 19460 |
| `TestLrzWideBossColdRouteCapture` | 1 | `coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld`: expected 2796 / got 524 |
| `TestMhzPairColdRouteCapture` | 1 | `pairedColdCompletionIsolatesTheLiveTimelineAtTheActualFbzLoad`: missing actual history-reset boundary |
| `TestMhzWideColdRouteCapture` | 1 | `wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay`: zone-runtime state differs at restore 19500 |
| `TestSszColdRouteCapture` | 2 | Both cold complete/replica routes: death at 7311 |
| `TestSszSoloColdRouteCapture` | 2 | Both solo Sonic replica/Mecha routes: death at 7671 |
| `TestSszTailsColdRouteCapture` | 3 | Mecha route [1], [2] expected 48 / got 0; replica route [2] has dynamic object/slot differences on replay at 4018 |
| `TestS1GameplayAudioTimelineCli` | 1 | `shellUsesAbsoluteBootstrapToolsAndRejectsInjectedEnvironmentBeforePathLookup`: expected exit 0 / got 4 |

<details>
<summary>Exact failed case identities from the completed Surefire reports</summary>

```text
com.openggf.tests.TestFbzSandopolisTimelineHeadless.productionExitResetsTimelineAndFreshDestinationRestoresAndReplaysTwice
com.openggf.tests.TestS3kMhzAct2AuthoredRoute.incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]
com.openggf.tests.TestS3kMhzAct2AuthoredRoute.incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3]
com.openggf.tools.TestDezIncomingFinalRouteCapture.coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding
com.openggf.tools.TestDezIncomingFinalRouteCapture.coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding
com.openggf.tools.TestDezIncomingFinalRouteCapture.incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]
com.openggf.tools.TestDezIncomingFinalRouteCapture.incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]
com.openggf.tools.TestDezIncomingFinalRouteCapture.coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding
com.openggf.tools.TestDezIncomingFinalRouteCapture.coldEmeraldTeamClearsBothActsFinalFightAndDoomsday
com.openggf.tools.TestDezIncomingFinalRouteCapture.coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding
com.openggf.tools.TestLrzActTwoColdRouteCapture.coldTeamCompletesActTwoAndReachesBossActWithRepeatableWorldState
com.openggf.tools.TestLrzBossColdRouteCapture.coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState
com.openggf.tools.TestLrzKnucklesColdRouteCapture.coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace
com.openggf.tools.TestLrzTailsColdRouteCapture.coldTailsClearsActOneAndRestoresTraversalFightAndHandoff
com.openggf.tools.TestLrzTailsColdRouteCapture.coldTailsRestoresActTwoTraversalToTheMiddleCorridor
com.openggf.tools.TestLrzTailsColdRouteCapture.coldTailsCompletesBossActAndReachesPlayableHiddenPalace
com.openggf.tools.TestLrzTailsColdRouteCapture.coldTailsCompletesActTwoAndRestoresTheBoulderHandoff
com.openggf.tools.TestLrzWideBossColdRouteCapture.coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld
com.openggf.tools.TestMhzPairColdRouteCapture.pairedColdCompletionIsolatesTheLiveTimelineAtTheActualFbzLoad
com.openggf.tools.TestMhzWideColdRouteCapture.wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay
com.openggf.tools.TestSszColdRouteCapture.coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents
com.openggf.tools.TestSszColdRouteCapture.coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport
com.openggf.tools.TestSszSoloColdRouteCapture.coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches
com.openggf.tools.TestSszSoloColdRouteCapture.coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory
com.openggf.tools.TestSszTailsColdRouteCapture.coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[1]
com.openggf.tools.TestSszTailsColdRouteCapture.coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[2]
com.openggf.tools.TestSszTailsColdRouteCapture.coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int)[2]
com.openggf.tools.audio.timeline.TestS1GameplayAudioTimelineCli.shellUsesAbsoluteBootstrapToolsAndRejectsInjectedEnvironmentBeforePathLookup
```

</details>

## Duration and category findings

Class wall time includes setup/teardown and GC inside a class, but excludes
between-class collection/snapshots. Each class is charged once to the first
directory owner in the category policy, falling back to the runner's name rules.
Declared Java packages are resolved to their actual source paths; they are not
assumed to equal directory layout. Selection categories overlap, whereas this
accounting does not. Timings include forced-GC/NMT overhead and host contention;
they are useful cost measurements, not an uninstrumented performance benchmark.

| Primary category | Class wall time | Share |
|---|---:|---:|
| Tooling | 41.4 min | 55.1% |
| Gameplay | 21.5 min | 28.6% |
| Mods | 8.5 min | 11.3% |
| Audio | 1.7 min | 2.3% |
| Rewind | 1.3 min | 1.7% |
| Common, network, physics, rendering, content combined | 44.7 s | 1.0% |

| Longest class | Cases | Wall time | Executing-thread allocation |
|---|---:|---:|---:|
| `TestDezIncomingFinalRouteCapture` | 7, all failed | 406.3 s | 512.2 GiB |
| `TestInfiniteSonic` | 237 | 322.9 s | 493.7 GiB |
| `TestSozColdRouteCapture` | 9 | 299.4 s | 275.3 GiB |
| `TestDezColdRouteCapture` | 11 | 245.1 s | 189.3 GiB |
| `TestDezSoloActTwoColdRouteCapture` | 4 | 210.0 s | 206.8 GiB |
| `TestLrzColdRouteCapture` | 14 | 177.4 s | 173.6 GiB |
| `TestLrzKnucklesColdRouteCapture` | 5, one failed | 163.3 s | 177.6 GiB |
| `TestFbzCheckpointRoutes` | 529 | 133.0 s | 357.7 GiB |

The rendered capture classes step controller movies, call
`GameplayCaptureSession.render()` and perform framebuffer readback every frame,
then replay additional full-world rewind windows. The native rendering path
uses `glFinish`, introducing GPU synchronization. They repeat this work for
acts, characters and viewport widths; successful and failed routes both consume
execution time. `TestInfiniteSonic` repeats full-engine traversal, hazards,
death/reload and rewind across zones/acts/aspect ratios, including 6,000-frame
loops. Checkpoint classes multiply physical starpost/death/reload checks across
checkpoint, team, viewport and donor matrices. `TestFbzAct1RouteHeadless` even
contains one 30.1-second method with 105 configurations plus a reset control;
method count alone misses hidden loops. `TestRewindTorture` repeatedly advances
two frames then rewinds one, after building a reference route, so net progress
understates work. Audio chip script tests clock and compare bit-exact output
across hundreds of cases, with much less allocation.

Executing-thread class allocations total about **5.68 TiB cumulatively**; this
counts bytes allocated and reclaimed repeatedly, not live heap. Worker-thread
and native allocations are excluded. The longest individual measured case was
DEZ's wide ordinary solo Sonic ending route at **104.5 seconds** (failed), followed
by wide LRZ boss **89.2 seconds** (failed), wide LRZ Act 2 **86.6 seconds** and wide
LRZ Act 1 **75.1 seconds**. During most rendered captures the post-GC floor stayed
around 1.2 GiB; the heavy churn is distinct from the later static audio cache.

Before integration, upstream `a38bdddda` separately optimized
`TestInfiniteSonic`: bounded terrain observations, stopped background music
only in terrain-focused routes and stopped hazard trials after both required
witnesses were established. Its own matched measurements report 232.6 → 124.2
seconds, with 206 passed / 32 expected skips. That change and its
[validation record](../validation/levels/infinite-sonic-ghz1.md) are preserved.
The 322.9-second instrumented row above predates it; do not compare those two
different protocols as a measured speedup or treat the old row as current cost.

## Post-plan ownership intervention

An existing queued request ran the following seven classes in a separate JUnit
5.10.3 launcher JVM, with the same Mockito 5.14.2 agent, a 3 GiB heap and verified
ROM paths. No field or mock was released until all selected test execution had
returned. The temporary helper used the already compiled test/production output
from this worktree, rather than another worktree's build tree.

```text
com.openggf.game.rewind.TestStaleRewindCodecHelperCleanup
com.openggf.game.sonic2.TestSonic2RuntimePlcRendererRefresh
com.openggf.game.sonic2.objects.TestBlueBallsBackwardApproachRespawn
com.openggf.game.sonic3k.objects.TestMhzBossObjects
com.openggf.game.sonic3k.objects.TestSozMiniboss
com.openggf.game.sonic3k.objects.bosses.TestLrzMinibossHitPath
com.openggf.tools.audio.completerun.TestCompleteRunAudioComparator
```

After **2,628 seconds** in the unchanged queue, compilation and execution took
**91.213 seconds** against diagnostic commit `ef7b7dc55` / engine `5bc5f4fa6`.
All **269 cases passed**, with zero failed, skipped or aborted cases; exit 0.
Every requested phase GC was observed. The hard limit was three execution
minutes, excluding queue wait. The audio class was added to the already waiting
control before compilation began, following the final full-suite histogram;
there was no duplicate request or admission override.

| Sequential intervention | Post-GC heap bytes | Incremental release |
|---|---:|---:|
| After the complete selected plan returned | 1,321,219,936 | — |
| `Mockito.framework().clearInlineMocks()` | 711,730,016 | 609,489,920 bytes / 581.3 MiB |
| Set `TestStaleRewindCodecHelperCleanup.sourceCorpus` to null | 601,595,904 | 110,134,112 bytes / 105.0 MiB |
| Clear `TestCompleteRunAudioComparator.SERVICE_EVIDENCE` | 277,069,792 | 324,526,112 bytes / 309.5 MiB |
| Null populated non-final static `SharedLevel` fields in these classes | 276,238,840 | 830,952 bytes / 0.8 MiB |

The controlled post-plan floor fell from **1.230 GiB to 263.4 MiB**. Inline-mock
cleanup reduced intercepted invocation counts from 994,810 to 394,819, while
the source and audio interventions released their independent static roots.
The audio map contained exactly **500,128 entries** before clear; its services
and service-evidence instances disappeared from the subsequent leading
histogram. This establishes avoidable test-owned retention, rather than merely
a correlation between test duration and heap usage.

These are sequential deltas in a selected control, not additive retained-size
attributions for the full suite. Cleanup order can affect shared graphs.
Substantial Mockito and mapping objects remained: the procedure did not identify
every remaining root or make all mocks unreachable. Nulling the one selected
closed `SharedLevel` saved less than 1 MiB; the broad count of 90 static fields
did **not** establish this fixture as a major contributor. No engine, test
fixture or Mockito teardown policy was changed by the experiment.

The ownership method is reproducible with a small disposable launcher: run exact
selectors through `SummaryGeneratingListener`, let `Launcher.execute()` return,
request GC and record `MemoryMXBean` heap usage, then release one explicit owner
at a time and repeat. Reflection is confined to this diagnostic JVM. A bounded
`gcClassHistogram -all` after each observed collection supplies class-count
corroboration; it is not a heap dominator analysis. The revision-specific helper
and raw output are temporary; the reusable suite sampler, fixture-lifetime
controls and this protocol are preserved.

## Follow-up choices supported by the measurements

1. Bound or release the source-corpus cache and the audio evidence map at their
   actual test lifetimes. The half-million-request stress test should construct
   complete records without a static side map retaining every prior input, so
   its factory does not undermine the constant-retention assertion.
2. Address recorded mock calls in long frame-driving tests with owned-mock
   teardown, lightweight fakes or appropriately non-recording stubs. Mockito's
   [stub-only contract](https://github.com/mockito/mockito/blob/v5.14.2/mockito-core/src/main/java/org/mockito/MockSettings.java)
   disallows verification: `TestSozMiniboss` verifies its player in some cases,
   so a blanket `stubOnly()` conversion would change the tests' contract.
   Likewise, global per-test inline cleanup can invalidate mocks owned by
   longer-lived fixtures. Follow up on the remaining roots before adopting a
   suite-wide policy.
3. Reduce or separate Maven compiler pressure only after matched cold-production
   compilation checks. The successful 1 GiB trace control recompiles tests, not
   cold production; it does not justify lowering all build heaps. Parent heap
   cleanup/limits and test retained roots are independent contributors.
4. Rework capture allocation and rendering separately from retention fixes.
   Framebuffer readback and repeated route matrices dominate time, while most
   captures return to an existing floor. Preserve real route/rewind/render
   obligations; moving a slow check into a different profile changes coverage,
   and skipping graphics would not validate the same behavior.

No historical OOM mechanism is proven without the failed process's message or
kill evidence. This completed investigation does prove that a few avoidable
owners can consume roughly a gigabyte in a selected passing run, while large
temporary allocations and Maven compilation use additional memory. More JVMs
therefore need measured capacity headroom even when every individual test passes.

## Tool verification

At the pinned base, the Python safety suite passed 109 tests, with no skips.
With the role-transition control it passed 114, with no skips. The real JUnit
service-loading control checks deliberately retained memory, release of a large
per-class fixture after the plan, release of a per-method fixture before the next
class, and completion accounting. All five observer controls passed after the
per-method expansion. A cold-compilation control preserves
reports and rejects linked output paths before deleting anything. A GC-log control distinguishes a
2,800 MiB pre-collection reading from its 30 MiB post-collection reading. Actual
Java 21, Lua 5.4 and PowerShell tool preflight passed. Engine source, the POM,
selection policy, hooks and regular test instrumentation are unchanged.

The change-based runner's shared-tool fallback selected all ordinary categories
and guards. Proportionate validation follows the actual diagnostic-only contract:
these helper Java files live outside production/test source roots and are added
only to the explicitly requested profiler invocation; the ordinary classpath,
POM, selection rules and runtime are unchanged. Python safety, real JUnit
fixture controls, actual Surefire controls and the completed instrumented
census exercise that contract. No extra uninstrumented engine full suite or
guard run is claimed or required for these tools and their documentation. The
28 census assertions remain unattributed, with their identities preserved above.

Integration used updated `develop` base
`a38bdddda23e49eb93e3d09c6aeafa98ed1e3289`, preserving Survivors, the shared player
rule and the separately delivered Infinite Sonic optimization. The changelog,
agent tool index and measurement catalogue merged automatically. The integrated
working tree passed these proportionate checks before the merge commit:

```bash
python3 -m unittest discover -s tools/testing -p 'test_run_categor*.py'
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base a38bdddda23e49eb93e3d09c6aeafa98ed1e3289 --preflight
```

The safety suite passed **114 tests in 20.411 seconds**, with no skips. Actual
Java 21, Lua 5.4 and PowerShell preflight passed without running engine tests.
Local links, the `AGENTS.md`/`CLAUDE.md` mirror and whitespace checks passed.
The three measured diagnostic source hashes matched the integrated files.
Reviewing the updated-base selection again showed the full fallback, including
unrelated preserved untracked files; the diagnostic-only scope justification
above still applies. No extra Maven queue request was submitted.

## Retention and discarded-readback follow-up

The next candidate is scoped to the measured test owners, not engine gameplay.
It releases the source corpus in `@AfterAll`, makes audio service evidence
instance-owned and clears it after each case, and constructs complete
request/decision stress rows without the helper evidence map. The existing
half-million-request test additionally checks that its input factory retains
no extra service evidence. Missing evidence no longer eagerly builds a default
state for every successful map lookup.

`OwnedMocks` records only mocks/spies created by that fixture invocation and
clears their inline interceptors after assertions and teardown. It is used by
the measured MHZ, SOZ, LRZ and S2 PLC tests, not installed globally. SOZ keeps
its verified player mock; only unverified level/debug stubs are non-recording.
LRZ's unverified player is non-recording and its fixed position is installed
once before the frame loop instead of adding identical stubbings each tick.
The stub-count and call-history checks preserve the actual boss-state assertions.
A focused ownership control checks that cleanup leaves a second fixture's mock
valid and preserves ordinary mock, spy, custom-answer and stub-only behavior.

The capture helper separates drawing from image readback. `render()` still
performs the same drawing and screenshot capture. `renderFrame()` keeps render
thread tasks, sprite ordering, controlled scene/overlays, titles, fades, VHS
and `glFinish()`, omitting only framebuffer readback and RGBA construction.
Only discarded images in `TestSozColdRouteCapture` are replaced in this batch;
all nine routes and their full-world rewind, boss, puzzle, results and
load-history assertions remain. Existing image consumers retain readback.
New native controls compare ordinary/title pixels and state at 320/800 widths,
exercise actual S3K special-stage results, and assert that the draw-only call
does not invoke screenshot readback.
Source comparison against `2fc65c847` also matched the common/results drawing
blocks exactly after removing their screenshot returns and factoring the common
block into `drawFrame`; neither pass ordering nor `glFinish` changed.

The independent acknowledgment fix was delivered in `37a57ebdb`, with its
process-lock evidence in the [focused-throughput research](2026-10-07-maven-focused-throughput.md#independent-diagnostic-cleanup-follow-up).
The combined change-based plan selected all 3,009 ordinary classes plus guards
through the shared-path fallback. Proportionate validation is appropriate for
the unchanged gameplay and original capture contract: the changed fixture
lifetimes and optional readback are exercised directly, including native
rendering and complete SOZ routes. POM, selection policy, runtime algorithms,
timing/physics, CI and release gates are unchanged. The Python cleanup component
uses the runner exception. These focused checks are not a full-suite pass.

Both memory runs select the same nine classes with the ordinary Surefire
shape (one reused 3 GiB fork, alphabetical order and verified absolute ROMs).
They retain between-class requested GC for a matched retained-floor/control
comparison. Execution is bounded to 12 minutes, excluding queue wait;
7–9 execution minutes were budgeted. The baseline worktree remains at
`2fc65c8479570f16ebd9830115485ee369c2b1e6`; the candidate's updated base
`37a57ebdb` differs only by the independently verified Python cleanup changes.
Cold build/Maven-parent costs must not be described as test-class time or
assumed to improve with these Java fixture changes.

The baseline completed at that pinned commit with **281 tests in nine reports,
zero failures, errors or skips**, and a completed observer plan with no active
test windows. The command below ran from `.worktrees/ai-test-throughput-fixes`;
the profiler supplied the verified absolute ROM paths. Its queue wait was
4,314 seconds (71.9 minutes), separate from the **225.201-second** test-plan
measurement. The final post-plan requested collection was observed and retained
**1,270,886,640 bytes (1.184 GiB)** of used heap. This is a passing selected-run
retention baseline, not proof of a historical OOM or an application leak.

```bash
python3 tools/testing/profile_ordinary_memory.py \
  --test TestStaleRewindCodecHelperCleanup,TestSonic2RuntimePlcRendererRefresh,TestMhzBossObjects,TestSozMiniboss,TestLrzMinibossHitPath,TestCompleteRunAudioComparator,TestSozColdRouteCapture,TestGameplayCaptureSmoke,TestGameplayCaptureSkippedTitles \
  --max-minutes 12 --output target/throughput-memory-baseline.json
```

The SOZ capture class took **210.731 seconds**, allocating **210.38 GiB** on
the measured test thread. LRZ took **5.986 seconds**, allocating **20.86 GiB**.
Audio comparison took **1.490 seconds**, allocating **3.57 GiB**. These are
allocation totals over execution, not simultaneously live memory. The other
six classes each took less than two seconds. The sampled process-tree peaks
were 5.51 GiB RSS and 2.35 GiB swap, measured separately; individual JVM maxima
must not be added as simultaneous consumption. The Maven parent alone peaked
at 2.43 GiB RSS and 2.01 GiB swap, which is independent of the proposed test
fixture retention fixes.

The first candidate attempt stopped at test compilation (Maven exit 1 after
67 seconds), with zero tests and no observer plan. Two S2 PLC fixture helpers
still had `static` declarations after moving their mock creation to the
invocation owner; all six compiler errors referred to those instance-field
uses. Removing `static` from the two helpers repairs their lifetime access.
The already queued functional check also stopped on those declarations before
the correction, after 19.200 seconds, with zero tests. Neither attempt supplies
a test-memory or performance result; the corrected candidate and functional
checks use new queued invocations.

The corrected candidate at `37a57ebdb` plus the pending Java diff completed the
same **281 test identities and outcomes** as the baseline: nine reports,
zero failures, errors or skips, one completed observer plan and no active
windows. It ran the same command from `.worktrees/ai-test-throughput-candidate`,
substituting `target/throughput-memory-candidate.json` for the output. That
retry waited 362 seconds. Its final post-plan collection was observed and
retained **23,702,928 bytes (22.605 MiB)**, a **98.1% decrease** from the selected
baseline. The test plan took **177.574 seconds**, 21.1% less in this single
matched, instrumented pair. This is not a measured full-suite speedup.

| Changed class | Baseline seconds | Candidate seconds | Baseline thread allocation GiB | Candidate thread allocation GiB |
|---|---:|---:|---:|---:|
| `TestLrzMinibossHitPath` | 5.986 | 1.777 | 20.86 | 2.59 |
| `TestSozColdRouteCapture` | 210.731 | 168.097 | 210.38 | 114.10 |
| `TestCompleteRunAudioComparator` | 1.490 | 1.138 | 3.57 | 2.26 |

LRZ's fixed stubs/non-recording player cut measured allocation 87.6%; SOZ's
discarded readback removal cut it 45.8%, retaining all nine routes and assertions.
Audio's factory/lifetime changes cut it 36.7%. The other six candidate classes
each remained under two seconds. Source-corpus parsing itself still allocated
about 249 MiB; releasing that cache changes retention, not its parsing work.
The paired test-fork sampled peak RSS fell from 3.23 to 1.30 GiB. The candidate
still had a Maven parent peaking at 2.08 GiB RSS and 1.72 GiB swap; build
preparation differed after the compilation repair, so its change is not
attributed to the Java fixture fixes. The queue reservations remain unchanged.

The first executed functional control covered 21 cases, with no skips/errors.
The existing SOZ background and capture-argument controls passed all 14 cases.
Five assertions in the two new control classes exposed faulty test assumptions:
Mockito's scoped cleanup need not make a later method call throw, and a visible
title/results screen need not contain more than 16 distinct colours. The
corrected controls check that final-class inline mocks/spies cease to be mocks,
execute their real method after cleanup, and leave another owner's mock valid.
Native controls require a non-uniform framebuffer, exact pixel/state equality,
an active title overlay in title cases, and zero screenshot calls during the
draw-only path. Production drawing and the measured nine-class inputs were
unchanged by these control corrections.

The candidate then merged Sitar's updated integration `be3c31418` as
`a9152b1c3`, without conflicts in the eleven changed/control Java inputs; their
recorded source fingerprints remained identical. The subsequent destination
`a87271f43` changes only Sitar's validation documentation. Sitar's completed
updated-base run selected 3,020 ordinary classes and fresh guards: its published
record reports 26,373 ordinary cases with 28 inherited assertion failures,
zero errors and 62 matched skips, followed by 672 passing guard cases with no
skips. The ordinary lane took 6,523.21 seconds; this is another task's baseline
evidence, not a passing full-suite result for this candidate. Before its
diagnostics were consumed, direct report inspection confirmed 177 cases from
eight of the nine selected memory classes and 53 cases across four mandatory
S3K control reports passed without skips; the audio comparator's report was not
individually read.

Rechecking the combined dry-run plan against the original task pin at
`a9152b1c3` selected 3,022 ordinary classes plus guards, including the two new
control classes and intervening upstream work. The bounded validation rationale
above still applies to this task's fixture lifetimes and optional capture
readback. The final focused scope is the nine measured classes, the two new
controls, existing SOZ background/capture-argument controls, mandatory S3K
loading/bootstrap/decoding controls (including both packages' classes named
`TestSonic3kLevelLoading`), and the teardown-extension control: 19 classes,
expected 364 cases. It exercises natural collection in a reused
fork, rather than requesting collection between classes. No queued request has
been cancelled, reprioritized or admitted outside the shared scheduler.

The corrected native/ownership command completed against `a9152b1c3` plus
research-only documentation edits on 2026-10-08, with all eleven Java source
fingerprints unchanged. Both reports passed: **seven cases, zero failures,
errors or skips**. Exact pixels/state matched for ordinary and active title-card
frames at 320/800 widths, and actual special-stage results pixels matched with
a non-uniform framebuffer. Draw-only calls performed no screenshot readback;
scoped cleanup released final-class inline mocks/spies while preserving another
fixture's mock. The command recompiled 3,700 production and 3,587 test source
files under the normal JVM settings, taking **75 seconds** including tests.
Its last waiting heartbeat was 10,758 seconds (about 179 minutes), separate
from execution. Final integrated validation is recorded below.
The command below uses `OPENGGF_CHECKOUT` for the verified absolute main
checkout path, keeping the recorded command portable.

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dtest=TestOwnedMocks,TestGameplayCaptureFrameRendering \
  "-Ds3k.rom.path=${OPENGGF_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

## Integrated natural-collection control

The task merged cleanly into updated `develop` destination `02796b4c4` as
`d5f9767a6`. The intervening destination change added only the Sitar launcher;
Java inputs were unchanged from the Sitar integration already exercised by the
native controls. All eleven changed/control Java fingerprints matched the
candidate after integration, and the tested integration commit stayed unchanged
through execution. Both the actual-destination and original-task-pin dry-run
plans selected 3,022 ordinary classes plus guards; the documented proportionate
scope was used without editing either selection.

The integrated focused control passed **364 cases in 19 fresh reports, with
zero failures, errors or skips**, on 2026-10-08. It includes both packages'
`TestSonic3kLevelLoading`, all nine measured classes, the new ownership/native
controls, SOZ background/capture arguments, and the teardown extension. CRC32
and SHA-1 checks confirmed all three supplied ROM identities. The nine measured
classes still accounted for the same 281 successful cases. This is focused
validation; no passing full ordinary suite is claimed, and the inherited
28-failure broad baseline remains outside this bounded change.

The existing `--lean` lane capped **both Maven and Surefire at 1 GiB**, verified
in their actual Java command lines. It recompiled **all 3,700 production and
3,587 test sources** and used one reused test fork with natural collection,
without the profiler's between-class collection requests. Maven completed in
**263 seconds (4 minutes 23 seconds)**. Queue telemetry recorded **311.3 seconds
waiting**, **264.7 seconds holding the slot**, and **2.153 GiB sampled peak
process-tree RSS**, fitting the lane's existing 4 GiB reservation. The full SOZ
class passed its nine routes in 170.145 seconds; LRZ passed in 1.881 seconds and
the audio comparator's 104 cases passed in 1.049 seconds. These natural-GC
times are a separate functional/capacity control, not another matched benchmark.

This supplies the previously missing all-sources compilation evidence under
1 GiB for this revision, plus selected-test capacity evidence. It does not
certify the whole ordinary suite under 1 GiB, change global JVM limits, or
identify the historical OOM without its error/kill evidence. The cleanup
component's already completed 119-case Python safety verification and actual
tool preflight were not repeated on unchanged Python code. Documentation-only
recording after this run does not require another engine invocation.

```bash
TESTS=TestStaleRewindCodecHelperCleanup,TestSonic2RuntimePlcRendererRefresh,TestMhzBossObjects,TestSozMiniboss,TestLrzMinibossHitPath,TestCompleteRunAudioComparator,TestSozColdRouteCapture,TestGameplayCaptureSmoke,TestGameplayCaptureSkippedTitles,TestOwnedMocks,TestGameplayCaptureFrameRendering,TestSozBackgroundCapture,TestGameplayCaptureToolArgs,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestHeadlessStateTeardownExtension
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off "-Dtest=${TESTS}" \
  "-Dsonic1.rom.path=${OPENGGF_CHECKOUT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${OPENGGF_CHECKOUT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${OPENGGF_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

## SOZ test-only follow-up — 2026-10-08

Task pin: `5d1ff9b8206594ee1c979ae5174328dd9134ffd4`. The matched baseline
and candidate used separate fresh worktrees, identical production sources/POM,
the same S3K ROM path, one reused lean fork and the same four-class selector.
Both builds compiled 3,704 production and 3,593 test files. The observed test-JVM
arguments included `-Xmx1g`; the lean wrapper also caps Maven at 1 GiB. JFR
`settings=profile`, `maxsize=32m`, `dumponexit=true` was attached to each owned
Surefire fork shortly after launch. No queue settings, order or admissions changed.

| Class | Cases | Baseline seconds | Candidate seconds |
|---|---:|---:|---:|
| `TestSozColdRouteCapture` (unchanged control) | 9 | 133.095 | 136.102 |
| `TestSozLowerRockPuzzleCapture` | 10 | 37.125 | 22.099 |
| `TestSozBackgroundCapture` | 8 | 1.304 | 1.203 |
| `TestGameplayCaptureFrameRendering` (unchanged control) | 5 | 2.784 | 2.912 |

All **32 fully qualified case identities and outcomes matched**: zero failures,
errors or skips. Every lower-rock case was faster; the class saved 15.026 seconds,
**40.5% in this single pair**. Its three discarded `render()` calls now use
`renderFrame()`, and every registered snapshot key is diffed once rather than
again while building the assertion message. The background tests retain their
last exposed-sky image and all shimmer/restored pixel comparisons; preceding
frames draw without readback. Inputs, frame counts, viewport/character matrix,
rewind windows and assertions remain intact. No production Java changed.

The four-class test time summed to 174.308 → 162.316 seconds. End-to-end Maven
remained about four minutes per arm because both compiled all sources; this is
not a whole-suite or build-throughput benchmark. Sampled process-tree peak RSS
was 2.303 → 1.802 GiB, including compilation and native allocations. Neither
value is retained heap or proof of a leak.

The unchanged nine full routes contain **262,091 recorded input frames**, plus
45-frame forward/replay windows and playable-destination checks. Their baseline
main-thread samples included 743 Java observations: 438 in `step`, 228 in
drawing, 63 in rewind capture/restore/comparison, and 14 in boot/other work.
Of 2,709 native observations, 2,499 were under drawing. Native leaf observations
included 1,563 `glFinish`, 756 texture-1D uploads and 254 texture-2D uploads.
Java's notable leaf was `LevelTilemapManager.writeChunkAt` (161 observations).
These are sampled stacks, **not CPU percentages**: Java/native periods are
10/20 ms and native events include waiting. The separate Surefire command thread's
5,369 native read observations explain the misleading aggregate I/O ranking.
The candidate reproduced the same rendering bottleneck.

Lower-rock baseline readback contributed an estimated 15.41 GB of allocation
sample weight, 220 Java observations and 104 native pixel-read observations.
The candidate had no lower-rock screenshot observations; real background and
rendering-control readback remained visible. We keep full drawing and rewind
comparisons: skipping frames, rendering less often or hiding routes in another
profile would change coverage. Further full-route speed work should target the
renderer/upload path with matched state/pixel controls, separately from this
test-only change and the frozen gameplay/parity work.

The first analysis expanded all sampled stacks to JSON, producing 9.4 GB and
9.6 GB analysis-process RSS. It was stopped and its output deleted; this was
an owned analysis process, not either Maven run. That analysis overlapped
candidate preparation and early unchanged cold routes, so their small timing
difference is not attributed to the patch. The lower-rock class ran later.
The committed `JfrTestSummary` streams counters using a 128 MiB heap. It passed
both real-recording controls, distinguished sampled/background threads, kept
unmatched prefixes under `other`, and rejected missing arguments/files. It
preserves positive screenshot evidence without materializing the expanded data.

```bash
TESTS=TestSozColdRouteCapture,TestSozLowerRockPuzzleCapture,TestSozBackgroundCapture,TestGameplayCaptureFrameRendering
python3 tools/testing/maven_queue.py --lean -Dmse=off "-Dtest=${TESTS}" \
  "-Ds3k.rom.path=${OPENGGF_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
# Against the owned fork only; temporary JFR output stays in that worktree's target/.
jcmd <owned-surefire-pid> JFR.start name=soz-throughput settings=profile \
  maxsize=32m dumponexit=true filename=<absolute-worktree-target>/soz-throughput.jfr
java -Xmx128m --source 21 tools/testing/java/com/openggf/tools/JfrTestSummary.java \
  target/soz-throughput.jfr
```

The initial test-only dry-run selected 342 tooling/common classes plus guards.
Adding the standalone JFR reader produced the 3,027-class unclassified fallback.
Under proportionate validation, the matched 32 cases exercise every changed
route and pixel assertion plus the production render/readback contract; the
reader is separately exercised on actual recordings and invalid inputs. There
is no POM, selection-policy, hook, workflow, runtime or timing/physics change.
This bounded validation replaces the disproportionate category fallback; it is
not a full ordinary-suite or guard pass. Actual preflight passed with Java 21,
Lua 5.4 (`LUA_BIN=/usr/bin/lua5.4`) and PowerShell; the initial unqualified Lua
launch failed preflight before executing any tests.

Before integration, develop advanced to `c039c009131be4548c3ab40eb809a0f85c2808f1`.
Its delta from the task pin is parity evidence/coverage prose only. The candidate
fast-forwarded to that actual base without source changes or conflicts.

The task commit `cc9565959f284d809490c2dc476b0ab4a8ae2424` integrated as
`88ec2bad7859d8a6938a603351dbb8b30d4946da`. The final ordinary focused command
below ran without JFR and passed **23 cases, zero failures/errors/skips**, in
**52.170 seconds including compilation**. All fully qualified identities/outcomes
matched the baseline subset, and the three test-source hashes matched the measured
candidate. Class times were lower-rock 24.664 s, background 1.254 s and render
contract 2.642 s. Different warmup and instrumentation make these validation
times separate from the matched benchmark above. The unchanged nine full routes
were not repeated after their two passing runs. No full-suite/guard pass is claimed.

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dtest=TestSozLowerRockPuzzleCapture,TestSozBackgroundCapture,TestGameplayCaptureFrameRendering \
  "-Ds3k.rom.path=${OPENGGF_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```


## SOZ authored-input shortening follow-up (2026-10-08)

Initial task base `d740b7a0fadd97b2e7c104d56481a0235bdffb4c`, worktree
`feature/ai-soz-shorter-inputs`. Both the original and candidate queued commands
below pass all **nine fully qualified case identities**, zero failures/errors/skips.
Both use the lean1GiB JVM configuration without JFR. The temporary opt-in input
probe was unselected during the baseline and was removed before candidate validation.
No runtime, renderer, physics, queue, build or selection-policy code changed.

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dtest=TestSozColdRouteCapture \
  "-Ds3k.rom.path=${OPENGGF_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

| Cold route | Baseline seconds | Candidate seconds |
|---|---:|---:|
| Solo Sonic Act1 (changed inputs) |15.547|14.341|
| Solo Sonic Act2 |15.458|15.399|
| Solo Tails Act1 |7.283|7.480|
| Solo Tails Act2 |14.886|14.443|
| Solo Knuckles Act1 |10.220|10.001|
| Sonic + Tails Act1 |16.379|16.020|
| Sonic + Tails Act2 |21.502|19.540|
| Solo Knuckles Act2,320 |18.043|17.972|
| Solo Knuckles Act2,800 |20.805|19.989|

Surefire class time was140.5→135.5s; Maven execution including compilation was
3m34s→2m38s, excluding queue wait. The first arm compiled production and tests;
the second compiled tests only. **Build times are not a throughput comparison.**
The changed case saved1.206s (7.8%) in this single pair. Unchanged controls also
varied, so the aggregate5s difference is not attributed wholly to this change.

The solo Sonic Act1 movie shrank31,671→28,614 inputs (9.7%), retaining original
ranges `[0,22350)`, `[23896,24207)` and `[25718,31671)`. The cut removes an
obstructed approach pause and repeated stationary hopping; its source is committed
as `soz1-cold-sonic.script` and was compiled/reparsed through `InputLogAuthorTool`.
A whole-registry cold-prefix branch at22350 reached real sand defeat at27006
(original source input30063). Fresh uninterrupted acceptance then reached playable
Act2 at28436 instead of31490:3,054 earlier executed inputs. The different global
level clock can alter native handoff dispatch alignment; the test observes actual
control release instead of requiring an exact fixed destination frame. It still
steps and draws180 playable destination frames, including three neutral inputs
beyond the shorter movie.

The route still replays at100 and each1000-input source interval through28000,
plus the destination. Three semantic45-input full-world windows cover boss entry,
actual sand defeat and results start. The new boss-entry/defeat observations replace
idle-period coverage with explicit lifecycle observations; there are30 periodic/
destination and three semantic windows. Registry keys and every field still compare
on capture/restore and forward replay. The other eight movies, their ready frames,
Knuckles puzzle/bonus checks and Act2 boss hits/capsule/history isolation are unchanged.
All nine recorded movies now sum259,034 rather than262,091 inputs: **1.2% less**.
This bounded improvement does not remove the long rendered traversal matrix.

Rejected experiments, all controller-only on the same base:

- Removing another1,016 arena-approach inputs reached the boss but never won,
  then died atcandidate input27483 (original source31556). Preserve admission/wait inputs.
- The less aggressive branch retained another432 stalled inputs and won at27438;
  it offered no advantage over the selected branch.
- Reusing the paired Act1 movie for solo Sonic died at5727; using Tails Act1
  died at1432; using Tails Act2 died at2882. No copied shorter route was accepted.

These probes restored only an engine-owned prefix checkpoint and external input
edge history, stopped before any cross-world restore, and supplied no positions,
clock, boss health or trace-state writes. Their two JUnit methods completed but
individual candidate deaths above are rejected authoring outcomes, not green
acceptance routes. Historical external capture CSVs helped locate stalls;
they are not a current native oracle or a matched replay result.

The change-based dry-run selects3,056 ordinary classes plus guards because BK2/
script test resources are unclassified. Under proportionate validation, the direct
nine-route before/after pair covers the sole changed controller asset and acceptance
code, including real rendered traversal, full-world rewind, boss/results, incoming
playability and load/history isolation. It replaces that disproportionate fallback;
no full-suite/guard pass is claimed. Actual tool preflight passes with Java21,
Lua5.4 (`LUA_BIN=/usr/bin/lua5.4`) and PowerShell; the initial default-Lua attempt
failed before running tests.

Develop advanced during merge preparation to
`eaafa6ee053f5624c00a78652e841d8f78598f5d` (Sitar Hero music feedback).
The delta changes Mod API/music-host/example paths and their tests/documentation;
it does not change SOZ, physics, GameLoop, capture rendering or these controller
assets. Task commit `7679a7793b506ae3dd6dc7eaa397f4f66f00941b` merged that base
as `c02db40cee71a0d0378f93f59feccc1d54deebf2`, with no conflicts. The measured
candidate's SOZ test and asset bytes remain identical after reconciliation.
The actual-base dry-run still selects the full ordinary suite because of the
unclassified test resources; the same proportionate rationale applies.

A completed updated-base run passes **73 cases, zero failures/errors/skips**, in
3m35s including compilation. Its nine original cold routes keep the same ready
frames; the other64 cases cover both level-loading classes, AIZ intro handling,
bootstrap resolution, decoding and the input author. This establishes the actual
integration baseline rather than treating the earlier task pin as current.

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dtest=TestSozColdRouteCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestInputLogAuthorTool \
  "-Ds3k.rom.path=${OPENGGF_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=${OPENGGF_CHECKOUT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${OPENGGF_CHECKOUT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test -B
```

### Integrated qualification and coordinated publication (2026-10-08)

The queued post-merge focused command above completed at 14:54:23Z on
`6124a524eef9b42efb800d5bcb95376147507c9e`, exit 0: **73 cases, zero
failures/errors/skips**. All fully qualified case identities and outcomes match
the updated-base 73-case run at `eaafa6ee0`. The nine cold routes pass, together
with 64 loading/bootstrap/decoding/AIZ/input-author cases. Maven execution took
2m 51s excluding queue wait; the cold-route class took 144.638s. This is integrated
qualification, not a new matched performance experiment; it does not establish
an aggregate throughput improvement.

The separately owned [Sitar main qualification evidence](../plans/2026-10-08-sitar-hero-fumble-feedback.md)
records completed ordinary run `20261008T153213Z-5cd93bac` at 6124:
3,056 reports / 26,535 cases / 27 exact inherited failures / zero errors / 62 literal
inherited skips. All nine SOZ cases pass without skips. The ordinary lane finished
at 16:57:28Z with Maven exit 1. The owning coordinator attributed every failure and
skip to its baseline; these are its verified results, not another run performed
by this task. Research-only HEAD drift to `17ae561ad` made the combined category
invocation incomplete and suppressed its guard lane. A separate fresh normal
main guard invocation at 17ae then passed **674 cases, zero failures/errors/skips**,
exit 0, finishing at 17:15:24Z. The complete ordinary lane and fresh guard lane qualify
unchanged executable inputs separately; the ordinary suite is not all green.

The coordinator published and independently verified develop at
`378c1d715a2565249b696babab4f7aaed943cc6e`, then released the main hold. Its delta
from tested 17ae changes only two evidence/measurement Markdown files. This task
re-read that durable evidence, fast-forward pulled main without a HEAD change,
and checked that its integrated SOZ test, script and BK2 still match measured
candidate `7679a7793b506ae3dd6dc7eaa397f4f66f00941b` byte for byte.

The earlier 6124 fixture merge occurred beneath another owner's active ordinary
run and changed a movie read directly from `src/test/resources`. That interrupted
invocation was incomplete, not pass evidence. The owning coordinator records this
in the existing [measurement-hazard catalogue](../../agent-workflow/briefing-trace-rounds.md):
compilation does not freeze committed fixture inputs. Main's tracked inputs and
commits were preserved throughout the replacement qualification/publication.
This follow-up changes evidence prose only; no unchanged engine tests or reviews
were repeated.

After publication, the task tree was re-inspected: clean tracked status, all
commits already merged, and no identified process using its directory. Ignored
contents were accounted for as owned build/probe output, generated configuration
and symlinks to original ROM/config/disassembly inputs; the generated configuration
example matched the bundled template. Consumed owned root diagnostics were removed
under the worktree metadata lease, with XML hashes checked before deletion and
changed reports preserved. Normal worktree removal succeeded; the fully merged
`feature/ai-soz-shorter-inputs` branch was deleted and stale metadata pruned.
Original ROMs, unrelated worktrees, main's three dirty disassemblies and four known
untracked paths, foreign jobs and the external rendered visualization were preserved.
No temporary agents were created for this task.

## SOZ drawing-gap follow-up (2026-10-08)

Task base `d2a501ebc9919e6c43412a2eedf02a372eac5309`, isolated branch
`feature/ai-soz-skip-frame-drawing`. The user explicitly authorized trying skipped
frame drawing. No production Java, movie input, physics, build or selection policy
changed. The SOZ-only test policy services queued render-thread work after every
simulation tick, omits traversal drawing, draws every snapshot checkpoint, draws
both complete 45-frame replay branches, and draws the final playable destination.
Prepared loads can depend on the render-thread queue even when no scene is drawn.

The first queued command remained in every-frame mode while the drawing policy was
introduced before admission, under the worktree metadata lease. Its default was
initially true. The matched skipped command changes only
`-Dopenggf.soz.drawEveryFrame=false`; all three Java test-source hashes were identical
through both runs. Both completed **14 cases, zero failures/errors/skips**, with
identical fully qualified case identities/outcomes. The five unchanged capture
rendering controls took 2.661 / 2.664 seconds. The nine SOZ cases took
**138.309 → 45.423 seconds: 67.2% less, 3.04× faster** in this one matched pair.

| Route | Every-frame drawing (s) | Checkpoint/replay drawing (s) |
|---|---:|---:|
| Solo Sonic Act 1 | 15.058 | 4.592 |
| Solo Sonic Act 2 | 16.113 | 5.787 |
| Solo Tails Act 1 | 8.409 | 2.141 |
| Solo Tails Act 2 | 14.916 | 5.125 |
| Solo Knuckles Act 1 | 9.943 | 2.903 |
| Sonic + Tails Act 1 | 15.337 | 3.582 |
| Sonic + Tails Act 2 | 20.121 | 6.341 |
| Solo Knuckles Act 2, 320 | 17.252 | 6.680 |
| Solo Knuckles Act 2, 800 | 21.142 | 8.256 |

All nine printed destination-ready frames, traversal/destination replay counts and
semantic event sets match exactly. Drawing falls from **295,065 to 36,227 calls**,
while 259,245 traversal drawing calls are omitted. Both modes still execute
295,065 simulation steps: the skipped mode's drawings include 407 additional
checkpoint/final draws without simulation ticks, plus 35,820 replay-step draws.
Every world key/field restore and forward-replay comparison, real boss/results,
bonus/puzzle/capsule checks, roster/width/death checks and 180 playable destination
frames remain. This does not compare every intermediate world state or pixel
between modes, and the default no longer establishes every-frame presentation.

Four independent short controls pass in 4.326 seconds with zero skips: native
Sonic Act 1 and Knuckles Act 2 at 320/800 widths. Each compares fully drawn and
skipped 420-frame branches from an engine-owned prefix checkpoint after 600 real
movie inputs. Every snapshot key/field and all final pixels match. The old
framebuffer is first cleared to one magenta colour and checked as uniform, so
stale pixels from the fully drawn branch cannot fake success. These are bounded
engine-path controls, not new full-route/wider/donor or native-ROM visual certification.

```bash
# Fully drawn control: the prototype still defaulted to every-frame drawing.
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dtest=TestSozColdRouteCapture,TestGameplayCaptureFrameRendering \
  "-Ds3k.rom.path=${OPENGGF_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
# Same sources and selection; only the SOZ drawing mode changes.
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dtest=TestSozColdRouteCapture,TestGameplayCaptureFrameRendering \
  -Dopenggf.soz.drawEveryFrame=false \
  "-Ds3k.rom.path=${OPENGGF_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
# Strict checkpoint reconstruction controls, independently executed.
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dtest=TestSozColdRouteFrameDrawing \
  "-Ds3k.rom.path=${OPENGGF_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The actual commands used the original absolute main ROM path. Java 21, lean 1 GiB
heaps and no JFR were matched. Class time excludes queue wait and compilation;
Maven totals of 3m45s / 1m11s include different cold compilation costs and are not
an end-to-end throughput benchmark. The baseline waited roughly 44 minutes for
admission. No foreign job was cancelled, and no queue priority or reservation changed.

After those controls passed, the normal default became checkpoint/replay drawing;
`-Dopenggf.soz.drawEveryFrame=true` retains every-frame traversal drawing. Capture
tools, screenshot tests and other routes keep their existing drawing behavior.
The [headless guide](../../guide/contributing/headless-testing.md) and both SOZ
act matrices record the presentation coverage limit and fully drawn invocation.

The change-based plan selects **3,059 ordinary classes plus guards** because the
new non-test-named helper is unclassified. Its only consumers are the two SOZ
JUnit classes; its methods delegate to existing drawing/render-task APIs and own
only per-invocation counters and a boolean. Under proportionate validation, the
14-case matched route/render pair, four strict state/pixel controls and the required
60 S3K loading/bootstrap/decoding/AIZ cases directly cover the bounded change.
No runner selection was edited or narrowed, and no new full-suite/guard pass is
claimed by this task. Actual tool preflight passed Java 21, Lua 5.4 and PowerShell.

The actual destination advanced to `ad3d6a9965952a38b6a0ac2e9eb0285f721ee895`.
Its delta from the task pin adds standalone experimental Windows packaging,
workflow/probe files and prose, with no engine/POM/test-input changes. Task code
commit `b5cbc5b9c` merged that base as
`4efa6f59316fa42e9de8e31a0af6fd027f0a5ee7`, without conflicts. The final default-mode
command passes **78 cases, zero failures/errors/skips**, exit 0, in 1m15s including
compilation, finishing at18:58:52Z. All original14 case identities/outcomes remain
passing; default route milestones and draw/skip counts exactly match the measured
explicit-false mode. The SOZ class took46.407s; that validation time is separate
from the matched138.309/45.423s benchmark.

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dtest=TestSozColdRouteCapture,TestSozColdRouteFrameDrawing,TestGameplayCaptureFrameRendering,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils \
  "-Ds3k.rom.path=${OPENGGF_CHECKOUT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=${OPENGGF_CHECKOUT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${OPENGGF_CHECKOUT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test -B
```


### Published-base drawing qualification and renewed main coordination (2026-10-09)

The parity owner released main/publication holds at published
`019dd454b0d63b10a1d0585450bb28f34e360c04`. Its complete ordinary/guard evidence
is attributed to tested `b317e94ebdce60c6f81553113543295c75b1d826`, with an
independently verified audit-only publication successor, in the
[updated parity negative-case table](../audits/2026-10-07-stock-parity-gap-verification.md#updated-actual-main-full-assertion-and-skip-summary).
Those are the parity owner's full results, not a new full run by this task.

Private merge `5b44c0ccb6c6c12052829ed7d2a5ce9df9f05499` reconciles that published
base without conflicts. All three drawing-test source hashes remain unchanged;
production/build inputs exactly inherit the base. The same focused command above
completed at 2026-10-09T03:41:33Z: **78 cases, zero failures/errors/skips**, Maven
exit0, 2m06s including compilation. Every fully qualified case identity/outcome
and all nine printed route result lines match the earlier terminal78-case check.
The SOZ class took45.740s; this is qualification, not another matched benchmark.
The selected eight report identities retain5 capture controls,9 routes,4 strict
state/pixel controls and60 required S3K loading/bootstrap/decoding/AIZ cases.
Actual tool preflight again passed Java21, Lua5.4 and PowerShell. The current
change-based fallback selects3069 ordinary classes plus guards; the previously
explained bounded proportionate validation remains the chosen scope.

Before main integration, published develop advanced to
`efedf9198eef5e717c8827f1aa34dc7728cb92fd`. Its complete delta is one Windows
research Markdown path, with no executable/test/build/API change. Private
prose-only merge `c05940fb1da72cf2915a41840e403d9c5cc4aa0c` preserves that update;
the completed qualification remains attributed to5b44. Both destination checks
stopped before writing any main input or commit. Main then acquired the unrelated
voice integration `0103b9bdc880c142301073fba9024750ccf4f1c2`, whose owner had already
submitted its normal full destination command against efedf. Its
[delivery plan](../designs/2026-10-07-eggmans-sky.md) freezes main inputs and HEAD
through ordinary and fresh guards. This task preserves that invocation and
all foreign jobs; SOZ has not reached main. Updated private composition,
actual-main focused qualification, publication and owned cleanup remain pending
that owner's completed delivery. No post-merge result is claimed.


## Other route drawing policy follow-up (2026-10-09)

The authorized extension uses a second isolated worktree,
`.worktrees/ai-route-skip-frame-drawing`, based on published
`cf0ad0de351800e2e2ae5cf3da5af7ed4bfe9d02`. The immutable comparison source
`0d550d3b5be98651d8b9f4b4c8fc8aff7b24a3b9` includes the already-qualified private
SOZ change and this published base. The remaining 23 route classes initially kept
their original per-tick drawing/readback. No production Java, build, selection,
ROM, movie, input sequence or existing assertion was changed by the extension.

The first candidate control invocation used a working patch at
`7baa588d2467903dfeacd613b36e0174d2a7a492`, fingerprint
`4c56b6934b437eb6895ff799da93c18211d0afd114530dc99d57bad974832594`; the fingerprint
was unchanged through completion at 2026-10-09T09:07:52Z. The queued
`maven_queue.py --lean -Dmse=off` command selected `TestRouteFrameDrawing` plus
`TestSozColdRouteFrameDrawing,TestGameplayCaptureFrameRendering,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
and all three verified original absolute ROM paths. Both JVM heaps were 1 GiB.
It completed **8 reports / 90 cases / 88 passing / 2 failures / 0 errors / 0 skips**,
Maven exit1; summed class time28.692s. These are completed focused controls, not
a green candidate or a full-suite pass.

The two failures were MHZ: `mhz2-team-incoming-320` with native Sonic+Tails and
`mhz2-sonic-incoming-800` with native solo Sonic. Both restored their frame600
starting snapshots exactly. After 420 skipped draws, the first failing key was
`palette-colors`: 20 `normalRgb` bytes differed at indices
55–62,66–67,69–71,73,75–76,78–81 (for example index55: fully drawn109 versus
skipped73). The pixel comparison was not reached in those two cases. The other
19 new prefixes matched every registered world key/field and exact reconstructed
framebuffer pixels after the same poisoned-framebuffer protocol; the previous
4 SOZ controls,5 capture controls and60 required S3K cases also passed.

The initial hypothesis was the renderer's pending-palette commit, so both MHZ
controls were rerun with full drawing. This completed 26-case invocation at
2026-10-09T09:40:26Z:24 passing,2 failures,0 errors/skips,exit1,52.101s Maven.
Its fingerprint `5d306d27b6a2a4158e5f9ece7b007608f9cf12d40fac3121cbc30a9fb7ac2cb8`
was unchanged. Both complete 892-character palette failures were identical to
the initial skipped-drawing failures. **Rejected attribution:** that mismatch
cannot be assigned to drawing gaps; the fully drawn replay control also fails.
No runtime palette or rewind owner was changed. The next diagnostic attempted two independent fully drawn cold sessions before
comparing a third session with the same drawing gap. The other
19 prefixes keep their successful restore/replay comparison. This control hazard
is recorded in the existing
[workflow catalogue](../../agent-workflow/briefing-trace-rounds.md#measurement-hazards--all-produce-plausible-output).

The independent cold experiment completed at2026-10-09T09:58:27Z on the same
HEAD with unchanged working fingerprint
`d66a66dbb8dabdfc86c6d0c8991d0c03db69eef385cac697b667c5539aad51b8`:21 cases,
19 passing,2 failures,zero errors/skips,exit1,25.477s summed test time. Both failures
were between the first two **fully drawn** cold snapshots, before the skipped run:
spawn references and dynamic `playerOwner` references belong to separate sessions;
the first case also has an identity-keyed `childSpawns` entry. The existing
`RewindSnapshotDiff` is intentionally a same-session replay comparator.

Rejected: normalizing those references to force the cold comparison through that
API. The two MHZ drawing gaps remain unqualified; their route classes keep every
scene draw and remove only discarded readback. The final new control class retains
19 validated restore/gap prefixes for FBZ,LRZ,SSZ,DEZ and supported SSZ donors.
The failed diagnostic MHZ protocols are removed from the shippable control class;
no existing test or assertion is removed or weakened. All existing MHZ route
assertions, including inherited replay/history negatives, remain in the matched
route qualification. Together with the unchanged4 SOZ,5 rendering and60 S3K
controls, the final control scope is88 cases.


The final 19-case control source completed at2026-10-09T10:08:19Z, Maven exit0,
zero failures/errors/skips,45.063s Maven including recompilation. The working
fingerprint remained `2720539bdb9b0a2f02459f1c15bec2a4b62f804285071b48c131b50fba64ef42`.
The command was `python3 tools/testing/maven_queue.py --lean -Dmse=off
-Dtest=TestRouteFrameDrawing <three original absolute ROM properties> test -B`.
Every retained prefix compares all registered fields and all RGBA pixels after
600 fully drawn inputs, a snapshot restore and a420-input drawing gap.

The immutable matched baseline at0d550 completed at2026-10-09T09:30:40Z:
29 ordinary reports/189 cases/24 inherited failures/zero errors/skips,
1,500.313s summed test time,26:18 Maven,exit1. The queued request waited6,245s;
queue wait is excluded from test timings. The separate normal `-Pfbz-routes`
invocation passed2 reports/2 cases without skips,112.559s summed test time,exit0.
The baseline already contains the private SOZ optimization; its nine SOZ cases
are excluded from the additional23-class extension speedup. All24 complete
primary assertions match the published
[actual-main failure table](../audits/2026-10-07-stock-parity-gap-verification.md#updated-actual-main-full-assertion-and-skip-summary),
removing only its exception prefix and normalizing the two specifically verified
SSZ `RewindObjectStateBlob` hashes. The entire2,907-character normalized SSZ
payload was compared, including all fields. No other assertion was normalized.
These focused results preserve inherited negatives; they are not a whole-suite
or parity pass.
