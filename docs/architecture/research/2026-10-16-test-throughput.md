# Category test throughput and admission continuity

The first delivery removes repeated Maven preparation from category batches and
preserves bounded waiting priority across those batches. It does not change test
selection, class order, batch membership, JVM concurrency or resource reservations.

The implementation was developed in `.worktrees/ai-test-throughput` on
`feature/ai-test-throughput`, based on
`c1e641cad1644ee0b63667ec0e52f94b801b774f`. Codex and Claude Opus 5.5 compared the
designs against the repository's actual Maven lifecycle and queue implementation.

## Observed problem

At 13:40 UTC on 2026-10-16, the launcher-startup task had one unchanged all-smoke
run, `20261016T115121Z-fd2f81b4`, with 28 of 68 ordinary batches complete. Its
selection contained 3,362 candidate classes. The task owner reported 1,033.63
seconds of test execution. Matching queue telemetry recorded 5,480.3 seconds
waiting and 1,035.7 seconds holding admission. These are sequential intervals
from one run, not sums across parallel jobs. Other elapsed work was not fully
partitioned. The evidence did not support the suggested 20-hour prediction.

Each old batch invoked the Maven `test` lifecycle. That repeated Git identity
capture, resource scanning and compilation before Surefire, including a
roughly 712 MB test-resource tree and 4,018 test sources. Separately, every new
batch reset its queue arrival, forfeiting the preceding batch's accumulated wait.

## Decisions

### Prepare compiled inputs once

Run `mvn -Dmse=off test-compile -B` once while holding the existing worktree build
lease. It uses build-only admission policy. Then run independent queued
`surefire:test` invocations for the same ordinary batches and the separate guards.
This retains all class neighbours and profile configuration while removing the
repeated preparation lifecycle. Maven startup and Surefire startup still occur
for every batch.

Preparation inherits the worktree lease into its child process. Its elapsed time
counts against the invocation budget; shared-admission waits do not. A failed,
interrupted or timed-out preparation launches no tests. Source fingerprints are
checked across preparation and between batches. Per-invocation fork directories
are created explicitly because the `validate` phase no longer runs per batch.

The POM contract check rejects execution-level Surefire configuration, non-Surefire
work in the skipped phases, implicit or property-dependent execution phases,
POM-declared parent/lifecycle extensions, and lane profiles that change preparation inputs.
The current standalone POM satisfies it. A future incompatible POM must update
the runner deliberately rather than silently omit lifecycle work. Preparation
status lives in `preparation.json`, separate from JUnit totals, under the existing
bounded diagnostic cleanup policy.

Tests must not mutate prepared classes/resources. Re-running `testResources`
previously repaired deleted resources between batches; prepare-once does not.
Source inspection found no tests relying on that repair. Worktree locks exclude
participating Maven commands, not external IDE writers.

### Carry bounded waiting credit

One process-local `ServiceCredit` belongs to each category invocation. At the end
of an admitted batch it applies:

```text
credit = max(0, min(300 seconds, previous credit + actual wait) - actual hold)
next virtual arrival = actual submission time - credit
```

Cap before charging service. A 3,600-second wait and 40-second hold leave 260
seconds, so the continuation needs another 40 seconds of waiting before aging.
Keeping the original arrival, or capping after subtraction, would let an old
suite repeatedly win immediate aged priority. Preparation and gaps earn nothing;
cancelled waiting earns nothing. Interrupted admitted work still spends credit.

The virtual arrival uses the existing waiting-record field. Telemetry continues
to use actual timestamps. There is no persistent balance, timing history, pass
cache or resumption receipt. Normal and lean budgets, maximum concurrency,
exclusive profiles, cancellation and OS lease authority remain unchanged.

Real-process tests exposed a second priority loss. A foreign scheduler saw a
continuation's legitimately retained worktree lease as `WORKTREE_BUSY`, allowing
another tree to backfill ahead of it. The fix probes shared capacity separately
for foreign retained continuations, releasing all probe descriptors immediately.
Only the continuation's owner can retain execution leases. The waiting record
never grants permission to write another worktree's `target/`.

### Deferred options

- Adaptive or larger batches change which classes share a JVM. Keep the existing
  50-class partition until the cheaper preparation path has been observed on
  realistic broad runs. No timing-history store is introduced for this delivery.
- Raising all runs to two forks trades per-run latency for more heap and exclusive
  admission. The existing opt-in remains; shared JUnit state is not made concurrent.
- Smaller global reservations require memory evidence from expensive classes.
  The existing lean lane already covers bounded focused tests. Removing repeated
  preparation does not establish a safe new heap budget.
- A dedicated interactive reservation or additional hosts remains an option if
  measured resource pressure still dominates. This change offers admission
  opportunities and bounded credit, not a wall-time guarantee or preemption.

## Verification

The unchanged baseline Python safety suite passed 167 tests in 24.970 seconds.
The implementation passed 190 tests in 27.013 seconds with:

```bash
python3 -m unittest discover -s tools/testing -p 'test_run_categor*.py'
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base c1e641cad1644ee0b63667ec0e52f94b801b774f --preflight
```

Preflight passed Java 25, Lua 5.4 and PowerShell. It is not engine coverage.
The Python checks cover preparation failure/timeout/interruption, source changes,
budget consumption, cleanup, profile contract rejection, identical batching,
retained lock authority, cancellation, and real-process scheduling in serial and
resource-aware modes. No POM, Java, category selection policy, workflow or hook
changed, so the runner-only validation exception applies. No broad engine suite
was launched to test the wrapper.

A deterministic single-slot simulation schedules six 40-second category batches
and sixty 30-second focused jobs. Both policies complete the identical work in
2,040 simulated seconds. Resetting age finishes category work at 2,040 seconds;
bounded credit finishes it at 840 seconds, with 20 focused jobs completed before
that point and no consecutive category batches. This proves the scheduling
mechanism in that workload, not a measured host throughput gain.

Opus 5.5's implementation review found no blocking defects. The conservative
contract check intentionally scans every POM profile, so future opt-in lifecycle
changes may require a runner update even when that profile is not selected.
Preparation participates in common artifact compaction so its success logs are
deleted and failure tails remain bounded. The existing cleanup-error behavior
is unchanged; preparation's causal reason is written before temporary cleanup.

## Warm Maven comparison

On this Linux host with Java 25.0.4.1, Maven 3.9.16 and 32 available CPUs, five
alternating pairs ran against the same prepared engine/test classes. A fresh
initial `-Psmoke test-compile` build succeeded in 74 seconds; that one-time cost
is separate from the following warm invocation timings. Each measured process
held normal queue admission and its worktree lease. Other worktrees could run
concurrently; system load was about 4 at setup and 5.7 near completion. This is a
shared-host comparison, not an isolated CPU benchmark.

Each side ran exactly the same 11 case identities from `TestCollisionLogic` and
`TestBuildIdentity`, with no failures, errors or skips in any invocation:

```text
mvn -Dmse=off -B -Psmoke test -Dtest=TestCollisionLogic,TestBuildIdentity
mvn -Dmse=off -B -Psmoke surefire:test -Dtest=TestCollisionLogic,TestBuildIdentity
```

Both commands also passed separate worktree-owned
`-Dopenggf.surefire.reports=<output>/reports` and
`-Dopenggf.test.tmpdir=<output>/tmp` paths. Every repeat used a new Maven process;
the measured interval included process launch, test execution and exit, but
excluded queue waiting. XML case identities and outcomes were compared after
every invocation, before report deletion.

| Pair | Lifecycle seconds | Prepared Surefire seconds |
|---|---:|---:|
| 1 | 23.549 | 1.381 |
| 2 | 24.559 | 1.537 |
| 3 | 24.515 | 1.623 |
| 4 | 24.269 | 1.355 |
| 5 | 24.113 | 1.406 |
| Median | 24.269 | 1.406 |
| Range | 23.549–24.559 | 1.355–1.623 |

Median summed JUnit time was 0.247 seconds through the lifecycle and 0.212 seconds
through direct Surefire. The difference is dominated by preparation outside the
tests. A separate diagnostic invocation with timestamped Maven output showed
`testCompile` beginning at 2.020 seconds, reporting a source-triggered module
recompile of all 4,018 test sources, then reaching Surefire at 22.421 seconds.
Production compilation reported up to date. Engine and Java test sources had
not changed during the experiment. This identifies repeated test compilation
as the dominant cost on this checkout; the compiler's underlying staleness
trigger was not investigated. The diagnostic held exclusive test admission and
is not included in the five-pair timing table.

Across the experiment, queue waiting was 449.55 seconds for lifecycle
invocations and 0.04 seconds for direct invocations. That unequal waiting reflects
arrival and contention during this experiment, and is excluded from the performance
comparison. It must not be attributed to the preparation change.

The queue's two-second sampler observed lifecycle peaks of 2.14–2.38 GiB, but
direct invocations finished before a useful child-process sample. Those tiny
reported direct peaks are sampling misses, not evidence of negligible memory.
No memory-reduction percentage or new safe reservation is claimed.

This small check demonstrates reduced per-batch overhead. It does not establish
a 17-fold improvement for a ROM-heavy suite: test work, one-time compilation,
each remaining Maven/fork startup, and shared admission still contribute.

Two additional direct-Surefire checks passed under exclusive admission. The
`smoke,test-concurrent` profile executed all 13 cases from `TestCollisionLogic`,
`TestBuildIdentity` and `TestInputBindingFactoryTempDirectory`, including the
check that lists the actual fork's `java.io.tmpdir`. The `guards` profile executed
five `TestBuildToolingGuard` methods covering smoke/release selection, one-fork
and two-fork configuration, runtime paths and supported documentation. Both had
zero failures, errors and skips. Their commands were:

```bash
mvn -Dmse=off -B -Psmoke,test-concurrent surefire:test \
  -Dtest=TestCollisionLogic,TestBuildIdentity,TestInputBindingFactoryTempDirectory
mvn -Dmse=off -B -Pguards surefire:test \
  '-Dtest=TestBuildToolingGuard#guardsProfileMustUseOneLargerForkForWholeGraphAnalysis+ordinarySuiteMustUseOneReusedThreeGibForkWithIsolatedRuntimePaths+concurrentProfileMustIsolateProcessesAndRetainSerialJupiter+routineTestsMustDefaultToSmokeAndFullSuiteMustBeReleaseOnly+supportedDocumentationMustUseQueuedMavenAndExplicitHookBootstrap'
```

These commands also used worktree-owned report/tmp directories, explicit
`fork-1` and `fork-2` directories, and
`-Dorg.slf4j.simpleLogger.showDateTime=true`. They were executed through
`maven_slot(..., exclusive=True)` with inherited lease descriptors. The same
timestamp option was used in the separate lifecycle diagnostic. These are
focused profile checks, not complete ordinary or guard-suite validation.

Before integration, `develop` advanced to `d3fb0625e` with the independent S3K
parity delivery. Its delta from the measured base did not change `tools/testing`,
`pom.xml`, `.mvn`, `.githooks`, `.github`, `AGENTS.md` or `CLAUDE.md`. Its separate
changelog changes describe S3K endings and are preserved. Measurements above
remain attributed to `c1e641cad` engine/test inputs; they do not claim to time the
new S3K content. Integration checks verify the resulting runner and POM contract.

## Measurement limits and follow-up

The broad launcher run is evidence of the original bottleneck, not a controlled
before/after comparison. It must finish under its existing owner and environment.
No cancellation, restart, coverage reduction or resource-policy bypass was used
to obtain capacity for this task. Existing wrappers remain lock-compatible, but
they cannot apply the corrected foreign-continuation probe; linked worktrees
can invoke the updated main-workspace wrapper from their own cwd and should
update their category-runner tooling before starting a new category invocation.

Inspect future broad-run queue wait, preparation, per-batch execution and sampled
memory separately. The service-credit simulation must not be presented as a
whole-suite elapsed-time forecast. Report actual completed coverage and skips
before consuming diagnostics.

## Live queue dashboard follow-up

The user selected a local browser dashboard after throughput delivery
`c4f78fa1f2ad9bd6fbe99013aafe8864f4b3639d`. The implementation in
`feature/ai-test-queue-view` uses a standard-library loopback server and a static
page polling every two seconds. It reads existing waiting leases, running usage
samples and bounded completion telemetry. Additive job metadata supplies actual
submission/admission times and worktree labels for new clients; old clients stay
compatible, with unavailable timings shown as unknown. Scheduling age includes
carried credit and is never presented as elapsed waiting or a start-time forecast.

Lock probing was rejected for this observer: it must not acquire admission or
worktree locks, prune files or change scheduling. Linux `/proc/locks` provides
liveness. On this host its overlay device ID differed from `stat` for the same
held file. Matching only device/inode therefore hid real jobs; matching only inode
would risk false ownership. The observer resolves an alias through the owner's
actual file descriptor and matching `fdinfo` lock record. Unverifiable owners
remain separate from active counts. Review also caught Windows' exclusive lock
on byte zero: payload reads now start at byte one, like existing queue readers.
Other platforms display unverified records when OS lease observation is unavailable.

Validation from the task worktree:

```bash
python3 -m unittest discover -s tools/testing -p 'test_run_categor*.py'
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base c4f78fa1f2ad9bd6fbe99013aafe8864f4b3639d --preflight
python3 -m unittest discover -s tools/testing -p 'test_run_category_dashboard.py'
```

The combined Python safety run passed 210 checks. After review's two portability
fixes, all 21 dashboard checks passed, including a simulated unreadable locked
byte and unknown-platform handling; this is not native Windows/macOS execution.
Actual Java 25, Lua 5.4 and PowerShell preflight passed. JavaScript syntax and
agent-document mirrors passed. Browser checks against the real queue verified
automatic updates, pause/resume, manual refresh while paused, worktree filtering
and layouts at 1280px and 390px without page overflow. No Maven tests were needed
for this observational tooling change; there is no engine-suite claim.

Ten local snapshot collections with policy already loaded took a median 1.81ms
(range 1.77–10.60ms), observing one running job and four waiters at the final
sample. This small diagnostic excludes HTTP/browser work and policy reloads,
which occur at most every 30 seconds. It is not a general overhead guarantee.
No extra process-memory sampler or history ledger was added. The dashboard
cannot show build-only work, reconstruct missing legacy timings, guarantee
admission order or infer test success from a completed execution record.
