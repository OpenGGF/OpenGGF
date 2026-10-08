# Focused Maven throughput on a memory-constrained agent host

Origin: the 2026-10-07 request to improve test throughput for agents sharing the
local Maven queue. Integration base: `6d817a9d74f135714f3da59ab9aab156cc09473e`
on `develop`; implementation tree: `.worktrees/ai-maven-focused-throughput`.

## Observed bottleneck

The queue already allowed three resource-aware runs on Linux, but the host could
not afford three normal reservations. One sample showed 32 usable CPUs, load
about 4, only 6.7 GiB available memory, one running suite and 11 live waiters.
Normal commands reserved 7 GiB and 8 cores each, plus shared 2 GiB memory headroom.

The bounded queue log contained 1,484 jobs. Its 1,148 focused jobs had a 25-second
median hold, a 497-second p95 wait and a 4,873-second maximum wait. Their p95 peak
summed RSS was 3.64 GiB, but the maximum was 6.56 GiB. The most recent 100 completed
focused jobs peaked at 5.311 GiB. These numbers justify investigating focused
memory cost; they do not justify lowering every command's reservation. Recent
full category runs also exceeded the nominal normal reservation.

An independent scheduling issue was explicit in the existing process test: an
aged request blocked on its own busy worktree stopped unrelated trees from using
available capacity. Pausing other trees cannot release that worktree's `target/`.

## Implemented approach

`maven_queue.py --lean ...` is an opt-in lane for small focused tests. Both Maven
and the reused test JVM have a 1 GiB maximum heap. The test command preserves the
Mockito agent, CDS setting and macOS first-thread flag. Exact selectors and the
test lifecycle are required; profiles, heap/fork/thread overrides and custom
launch configuration are rejected. The ordinary command retains its existing
shape. A lean OOM is a failed run, not an automatic successful retry.

The lean reservation is 4 GiB and 4 cores, leaving allowance beyond the two
bounded heaps. Existing normal reservations, shared headroom and the three-run
ceiling remain. Waiting records carry each request's budget; running leases
already have the necessary budget fields. Admission sums the actual live budgets
and credits measured usage without double-counting it. Unknown or stale holders
retain a normal reservation. Inconsistent lease/slot counts forfeit credit and
use the largest observed budgets. Legacy waiting records without a budget remain
valid, and the shared/exclusive compatibility lock still excludes serial clients.

An aged resource-aware request blocked only by its worktree permits other trees
to backfill. Its tree is skipped for the rest of that scan, preserving the oldest
request even if the holder exits between probes. Shared resource or compatibility
blockage still drains the queue for an aged head. Aged serial requests also drain
the queue. No running command is preempted.

## Rejected alternatives and limits

- Raising the three-run ceiling: the observed host was memory-limited with idle
  CPUs; more nominal slots cannot satisfy the existing reservations.
- Lowering normal reservations from focused-job averages: the measured tail was
  substantially larger than the average, and full runs were larger still.
- Reducing every test's heap: exact selectors can include memory-heavy tests.
  The smaller heap is explicit and does not replace required broad/domain runs.
- Ignoring aging to expedite this task: the new real-JVM checks joined the shared
  queue and waited behind older work. The change preserves shared-capacity fairness.

Reservations are advisory rather than hard RSS/CPU limits. Heap caps do not cap
native allocations. Two-second RSS sampling can miss short peaks and sums shared
pages. Mean CPU conceals startup bursts. The lean lane is appropriate only when
the focused test's memory needs are bounded and understood. Existing worktrees
can invoke the updated wrapper by absolute path from their own working directory;
their source and build outputs remain local to that tree.

## Verification

The change-based plan selected all 3,005 engine classes and guards through its
shared-path fallback. The queue changes meet the Python-runner exception. The
added Java diagnostic launcher is outside Maven source/test trees and ordinary
classpaths: it changes no engine, POM, selection policy, workflow or hook. For
that isolated tool, proportionate validation covers a real JUnit control with
dynamic/skipped tests and deliberately retained memory, plus an actual queued
Maven build and ROM/native test group. Running 3,005 unchanged engine classes
would not test the new helper beyond those direct consumers. Engine-suite
certification is not claimed.

- Baseline at the integration SHA:
  `python3 -m unittest discover -s tools/testing -p 'test_run_categor*.py'`:
  92 tests passed, no skips.
- Candidate, same command: 109 tests passed, no skips. Real competing-process
  checks include two 4 GiB jobs fitting a 10 GiB snapshot while a normal job
  waits, mixed/unknown budgets, worktree exclusion, aged capacity drainage,
  busy-tree backfilling, cancellation and inherited leases. CLI checks verify
  the bounded arguments/environment and the published lean reservation.
- `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base
  6d817a9d74f135714f3da59ab9aab156cc09473e --preflight`:
  Java 21, Lua 5.4 and PowerShell prerequisites passed.
- `git diff --check` and `cmp AGENTS.md CLAUDE.md` passed.

The resource-snapshot process tests demonstrate admission and ordering, not
production wall-clock speedup. Real queued lean checks on the base main workspace
and this development tree each passed `TestCollisionLogic,TestBuildIdentity`:
11 tests, no skips/failures/errors. The development selector also named the
nonexistent `TestSolidObjectCollisionResolver`; it supplied no coverage and is
not counted. The jobs overlapped in separate trees for about 23 seconds. Sampled
process-tree RSS peaked at 1.389 and 1.385 GiB, holds were 23.2 and 71.5 seconds,
and waits were 2,894.8 and 3,047.9 seconds. The cold compile differs from the main
tree, so these timings are not a matched speedup benchmark. A subsequent current
CLI invocation passed the same 11 tests; `/proc` observations verified both JVMs
actually used `-Xmx1g`, with `-Xshare:off` and the Mockito agent in Surefire.

## First per-test memory report

The user chose likely-expensive tests rather than the entire ordinary suite.
The separate `profile_test_memory.py` compiles a JUnit Platform listener only into
its temporary diagnostic directory; ordinary runs have no profiling overhead.
The listener covers dynamic/parameterized cases, failures, aborts and skips. The
tool holds the shared queue lease throughout Maven test compilation and the
single reused diagnostic JVM. Raw events/logs/helper/test artifacts are consumed
and removed; the compact report is diagnostic, not Surefire certification.

Command in the implementation worktree, engine source at the integration base
above, with the uncommitted diagnostic tooling:

```bash
python3 tools/testing/profile_test_memory.py --gc test --repeat 3 --max-minutes 20 \
  TestGameplayCaptureSmoke TestAiz2ShipLoopRewindRoundTrip \
  com.openggf.tests.TestSonic3kLevelLoading TestRomReadAllBytes
```

Java `21.0.12.1`, 3 GiB maximum heap, verified S3K ROM path, 100 ms sampling.
Four classes / 14 cases executed three times: 42 successful executions, zero
skips, aborts, test failures or container failures; command exit 0. Queue wait
was 317 seconds and is excluded from execution timeout. Native capture really
executed; it did not take its GL-unavailable skip.

Allocation below is the mean of passes 2 and 3 on the executing thread, in MiB.
Peak heap is the maximum JVM-global observation across all three passes, including
boundary samples. Fixtures, framework/profiler overhead, workers and caches can
affect global peaks; worker and native allocations are excluded from the thread
counter. These are not minimum required heap sizes.

| Class / method | Warm allocated MiB | Peak heap MiB |
|---|---:|---:|
| Capture / `omittedTitleStillCreatesNativeSozControllerAndGhosts` | 183.44 | 157.86 |
| Capture / `capturesMovingLeaderWithVisiblePlayerPixels` | 165.91 | 157.85 |
| Capture / `positionedMhzRecordingKeepsTheRequestedCameraWindow` | 78.81 | 121.05 |
| Rewind / `battleshipSurvivesRewindRestoreAndCameraLockIsNotOrphaned` | 64.91 | 61.24 |
| Level loading / `aiz1UsesKnucklesStartPositionWhenSessionSelectedTeamIsKnuckles` | 12.87 | 23.05 |
| Level loading / `preparedBuildUsesCapturedCharacterWithoutWorkerServiceLookups` | 10.83 | 35.48 |
| Level loading / `aiz1LoadsWithValidResourceReferences` | 10.79 | 30.86 |
| Level loading / `mgz2BackgroundCollisionRejectsZeroPointerRows` | 10.79 | 30.81 |
| Level loading / `mgz1Has32RowMapWithCorrectBlocks` | 10.76 | 30.81 |
| Level loading / `fbz1LoadsWithValidResourceReferences` | 10.61 | 30.81 |
| Level loading / `aiz1UsesRomStartPosition` | 8.49 | 28.81 |
| ROM read / `headerNameReadsDoNotMoveSharedChannelPosition` | 0.07 | 16.82 |
| ROM read / `checksumWalkDoesNotMoveSharedChannelPosition` | 0.13 | 16.82 |
| ROM read / `readAllBytes_doesNotMoveSharedChannelPosition` | 0.06 | 16.82 |

Post-GC heap after complete passes: **17,572,640 → 17,590,008 → 17,605,648 bytes**
(16.76 → 16.78 → 16.79 MiB). GC was observed on every boundary. Later growth was
17,368 then 15,640 bytes, consistent with small harness/cache bookkeeping rather
than a substantial sustained heap leak in this group. More repetitions and
ownership inspection are needed to rule out slower retention. The positioned
MHZ case retained about 22 MiB at its test callback yet the complete pass floor
returned lower: callback deltas alone would falsely suggest a leak.

Sampled per-test process RSS peaked at 542.54 MiB. Direct-buffer pool observations
were about 4.1 MiB and mapped buffers zero; LWJGL malloc/GPU memory is not measured
by those pools. RSS does not establish native retention. Long cold-route tests
were intentionally not run in the initial group.

The real JUnit control retains one 1 MiB static byte array per pass: the allocation
counter sees it and later post-GC pass floors grow by more than 800 KiB. A dynamic
test with a quoted name and a disabled test verify identity/JSON escaping and
skip reporting. Uncollected/partial event controls cannot become retained-heap
measurements or a completed census. Launcher lifecycle references:
[JUnit listener](https://docs.junit.org/5.10.0/api/org.junit.platform.launcher/org/junit/platform/launcher/TestExecutionListener.html),
[JUnit 5.10.3 guide](https://junit.org/junit5/docs/5.10.3/user-guide/index.html).

## Rework candidates

1. **Capture setup and repeated boots:** warm capture class allocation totals
   about 437 MiB, far above the short ROM-loading cases. Inspect repeated native
   setup/level preparation before reducing assertion coverage; profile workers
   separately for prepared builds.
2. **Discarded full-frame readback on long routes:** a read-only thread snapshot
   of the previously running ordinary suite found its main thread in
   `ScreenshotCapture.captureFramebufferRegion` from
   `TestLrzWideBossColdRouteCapture`. That test has a roughly 50,659-frame cold
   input and calls `session.render()` while discarding the returned image.
   An 800×224 image allocates a 716,800-byte Java pixel array plus a native RGBA
   readback buffer per call; the native buffer is freed in `finally`. These byte
   sizes are derived from source, not a measured route allocation total. A draw
   path that retains rendering/publication while avoiding unused readback is a
   promising follow-up, requiring native-render/route validation. It is not
   implemented in this tooling change.
3. **Rewind fixture loading:** the direct AIZ2 round-trip allocates about 65 MiB
   warm but shows no later retained growth. Separate level boot from snapshot
   allocation before deciding which part to optimize.

No engine behavior or test assertions were weakened to obtain these results.
The first report prioritizes churn and throughput investigation; it does not
certify the ordinary suite, prove all tests fit the lean lane, or rule out leaks
in unprofiled long routes/native memory.

## Integration reconciliation

The destination advanced to `945b74e999c3584ad45b92a711b7a6dace7dc294`
while the task was running. Its eleven commits contain separately verified stock
parity corrections; Maven tooling, POM and profiler dependencies were unchanged.
The release prose merged automatically. Both independently added measurement
hazards were retained at the shared heading: test-boundary retention and moving
live logs across filesystems. The first memory report remains attributed to the
earlier engine source, not the newer parity code. Updated-base and integrated
tooling checks do not re-certify the upstream engine changes.

- Updated baseline `945b74e999c3584ad45b92a711b7a6dace7dc294`, full Python
  safety suite: 92 tests passed, no skips (16.885 seconds).
- Reconciled candidate `6a696410c`, same command: 109 tests passed, no skips
  (18.558 seconds).
- Integrated `develop` at `63fea861e6e225c9073a88e3bc10513ec06d0950`, same
  command: 109 tests passed, no skips (18.790 seconds).
- Integrated launch environment: `LUA_BIN=/usr/bin/lua5.4 python3
  tools/testing/run_categories.py --base
  945b74e999c3584ad45b92a711b7a6dace7dc294 --preflight` passed Java 21,
  Lua 5.4 and PowerShell; no engine tests executed by preflight.
- Agent mirrors, changed Markdown links, diff whitespace and commit-policy hooks
  checked successfully. The combined plan was inspected against the actual
  updated base; focused tooling validation above was used for the isolated tool
  and queue as explained earlier. No full ordinary-suite pass is claimed.

## Independent diagnostic cleanup follow-up

The ordinary-memory task observed acknowledgment waiting about 91 minutes for
unrelated Maven execution. An aged global exclusive cleanup request can also
stop new admissions, although acknowledgment only deletes completed local
metadata. The regression uses two real processes and linked temporary Git
worktrees: acknowledgment exceeded a three-second deadline while another tree
held a resource-aware execution lease. This is lock contention, not JVM work.

The fix gives acknowledgment an exclusive current-worktree lease and a shared
legacy compatibility lease, in the same acquisition order as Maven. It does not
reserve a JVM slot or publish an admission request. Other resource-aware trees
can continue, while own-tree writers and legacy/serial exclusive holders still
exclude deletion. Windows keeps exclusive compatibility locking. Admission
budgets, ordering, aging, existing requests and running jobs are unchanged.
Target, category-directory and run-directory symlinks are rejected.

On the `2fc65c8479570f16ebd9830115485ee369c2b1e6` base, the Python safety suite
passed 119 tests in 22.534 seconds with no skips:

```bash
python3 -m unittest discover -s tools/testing -p 'test_run_categor*.py'
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 2fc65c8479570f16ebd9830115485ee369c2b1e6 --preflight
```

Actual Java 21, Lua 5.4 and PowerShell preflight passed. The competing-process
checks cover overlap with another worktree, exclusion in the same worktree,
legacy exclusive exclusion and OS release of the local lease after cancellation
of a blocked cleanup process. All process cancellation in these checks is
confined to temporary test repositories; no real queued job is cancelled.
The category-runner Python exception applies to this independently delivered
change: no POM, selection policy, Java, workflow or hook changes are included.

The integrated `develop` tree passed the same 119 safety tests in 21.333 seconds
and the actual tool preflight before the cleanup merge was finalized. Whitespace
and the agent-document mirror also passed. Java fixture/readback work is a
separate pending part of the same throughput task; this cleanup verification is
not an engine-suite pass.

A live-host control of the delivered `37a57ebdb` CLI removed its own synthetic
recognized diagnostic directory in **0.076 seconds**, while the previously
observed shared Surefire JVM remained alive. This exercised the real
`run_categories.py --acknowledge` path; it launched no Maven command and made
no engine-test or validation-pass claim. The synthetic metadata was deleted.
