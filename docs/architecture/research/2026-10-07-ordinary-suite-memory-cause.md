# Ordinary-suite memory-cause investigation

Origin: the request to find what causes test-run out-of-memory failures, following
the focused throughput/per-test sampling task. Integration base:
`5bc5f4fa60728c6c074f9bbf8cf89d6dc6b05f6f` on `develop`; investigation worktree:
`.worktrees/ai-ordinary-memory-cause`.

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
