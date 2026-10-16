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

## Qualified small jobs and one aged-head backfill

This follow-up started from dashboard merge `e8b9a0db2` and incorporated
`1fb1f4ce4` (the process-cleanup safety fix) before final validation. The earlier
interruptions produced no completed qualification result. They are not counted
as test failures or completed runs. The safety fix merged without conflicts and
preserves the new category subprocess environment handling.

The queue's bounded history supported keeping different execution budgets:
617 of 629 recorded ordinary batches peaked below 4 GiB, but the maximum was
4.855 GiB and guards reached 6.736 GiB. All 36 recorded focused lean invocations
were below 2.692 GiB. These sampled process-tree measurements can miss peaks and
count shared pages twice. They do not establish a universal 4 GiB ceiling.

The implementation therefore qualifies an explicit registry of 55 small classes,
with at most 50 exact classes/method selectors per command. Source resolution
rejects ambiguity, inherited fixtures, expensive annotations and unknown command
shapes. Both Maven and Surefire use 1 GiB heaps and reserve 4 GiB / 4 cores. The
normal lane, guards and unknown tests retain existing reservations. A category
batch qualifies only when every candidate is registered and one worker is used;
its includes, order, smoke profile and assertions are unchanged. `--normal`
opts focused commands out. Explicit `--lean` still supports caller-reviewed
checks but does not by itself grant backfill eligibility.

An aged resource-aware request blocked on memory or CPU permits one qualified
small request from another tree to use spare capacity. The gate-protected ledger
consumes this allowance only on actual admission and retains it until the aged
request leaves the waiting set. A completed small job does not replenish it.
Afterward the queue drains for the head. Serial requests, full slots and unknown
capacity retain draining behavior. This limits overtaking by count, not execution
time; there is no new timeout or preemption. Existing worktree/slot locks remain
the execution authority, including with older wrappers.

Opus 5.5 contributed design and review. A proposed negative-keyword classifier
was rejected: absence of ROM or native keywords does not establish bounded
allocations through helpers. The explicit registry requires fixture review and
actual lean execution before expansion. Review also caught two unsafe admission
assumptions, now covered by regressions: explicit lean must not automatically
receive backfill rights, and automatic qualification requires one unambiguous
`-Dmse=off` so selectors and heap flags survive the Maven extension.

Waiting records now publish admission reasons and resource shortfalls. Dashboard
rows distinguish fresh scheduler reports from conservative resource observations
for legacy clients. Unknown/stale data stays unknown. The observer acquires no
queue locks, changes no policy and does not forecast start times.

Validation used the development tree at `1fb1f4ce4` plus this patch:

- `python3 -m unittest discover -v -s tools/testing -p 'test_run_categor*.py'`
  exercised 240 checks in 31.960s. One stale CLI test double rejected the new
  `reservation` keyword; its signature and normal-budget assertions were updated.
  `PYTHONPATH=tools/testing python3 -m unittest -v test_run_category_control
  test_run_category_queue.QueueTests.test_normal_cli_opt_out_and_auto_lean_share_the_expected_budget_contract`
  then passed all nine affected checks, including explicit unqualified lean.
  Process tests cover racing backfill owners, one consumed allowance after exit,
  failed ledger writes, aged-head progress and old-client lock compatibility.
- `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base
  1fb1f4ce4 --preflight` passed actual Java 25, Lua 5.4 and PowerShell checks.
- The first 50 sorted `maven_small.QUALIFIED_CLASSES` were passed unchanged as
  `-Dtest=<comma-separated classes>` to `python3 tools/testing/maven_queue.py
  -Dmse=off -B -Dtest=... -Dopenggf.surefire.reports=<worktree>/target/short-job-validation/focused-reports test`.
  Automatic lean passed 50 reports / 307 tests with zero failures, errors or skips.
  Queue telemetry recorded 0.0s wait, 27.0s execution and 1.734 GiB sampled peak
  process-tree RSS, while two unrelated category runs were active. Maven reported
  26.397s including compilation. This is one qualification run, not a speedup benchmark.
- The remaining five sorted classes (four utilities and `TestBuildIdentity`)
  exercised `run_plan` with an exact focused qualification plan, one ordinary
  batch, no guards, the real preparation contract, retained worktree lock and
  `maven_slot` callback with 4 GiB / 4 cores. Preparation took 23.04s; the unchanged
  includes batch passed 5 reports / 28 tests in 1.76s, without failures, errors or
  skips. Its command retained `-Psmoke`, ROM properties and includes-file selection
  while adding the 1 GiB Surefire shape. This is tool integration qualification,
  not a narrowed substitute for broad smoke coverage.
- The live browser endpoint refreshed successfully. Display-only fixtures covered
  memory shortfall and draining rows, blocker labels and literal HTML-like worktree
  names. At 1280px and 390px there was no page overflow; the narrow table scrolls.
  Python tests cover stale/unknown reasons and observation provenance.

The change-based plan selects broad smoke because shared tooling is involved.
Focused Python safety coverage plus actual execution of every newly qualified
class addresses this bounded admission/heap change; no POM, Java, tag, selection
manifest, workflow or hook changed. No broad engine or release-full-suite claim
is made. Consumed category diagnostics and focused reports are deleted before
delivery. Existing running commands retain the code they loaded; new invocations
must use the updated tooling, including by absolute path from older worktrees.

## Active execution versus retained worktree ownership

The follow-up starts at `3fa0730c6` after the qualified-small-job delivery. A live
snapshot showed two normal batches with 7 GiB reservations, three focused waiters,
and two exclusive requests. The focused selectors were expensive ROM/campaign
checks, not qualified registry entries. Their 31–39 second scheduling estimates
were class-count hints, not measured completion forecasts. Available host RAM did
not establish capacity after reserving growth for the running jobs and headroom.

Opus 5.5 traced a larger problem to the benchmark's copy of `run_isolated.py` at
`da6330ae6`: it acquired exclusive admission, tried every linked worktree lock,
then released admission and requeued if any tree was busy. Category runners retain
their tree between batches, so an idle tree repeatedly caused a complete queue
drain with no measurement. The bounded queue log contained 91 completed benchmark
records with `holdSeconds: 0.0`, consistent with that loop. Rounded telemetry alone
does not prove that each attempt ran no command. Summed waits are not elapsed
delivery time. The develop version from `7d12e4c2c` instead blocked while acquiring
all tree locks before admission, retaining a partial lock set and stalling other
work. Neither ordering models the actual timing conflict.

The replacement distinguishes active build execution from output ownership.
Wrapped builds and category preparation hold a shared repository activity lease
only while executing. A benchmark keeps one queue request, takes exclusive test
admission, then drains active builds through an exclusive activity lease. An intent
mutex blocks new builds during draining and measurement. Idle foreign category
trees no longer matter; the benchmark protects its own tree before joining the
test queue. Opus review caught that submitting before this own-tree acquisition
could age a busy-tree measurement into a queue-wide drain. A real-process test
reproduced that regression before the correction, then passed with no waiting
record published until the own tree was available. Descriptors
are inherited by the launched process. Activity holders must never acquire test
admission. No foreign tree-lock sweep or retry/requeue loop remains.

A separate false-exclusive case was the explicit POM default
`-Dsurefire.forkCount=1`. This and `-Dsurefire.reuseForks=true` now retain normal
shared admission; mixed unsafe options, other counts/reuse settings, heap overrides
and unknown profiles remain conservative. Both normal and lean budgets are
unchanged. Reducing normal reservations without first capping/qualifying the Maven
parent was deferred. Estimate-derived benchmark kill deadlines were also rejected:
a priority hint is not an execution-time contract. No running job was restarted.

The benchmark returns its child's exit code after contexts close, so a successful
command is no longer logged as interrupted by an internal `SystemExit(0)`.
Completed queue telemetry still does not mean command success; the audit and
process status retain nonzero exits. Queue hold time includes active-build draining,
whereas audit timestamps cover the launched measurement. Category preparation
records build-isolation waiting separately and excludes it from its timeout budget.

Validation on `feature/ai-ready-admission` at `3fa0730c6` plus this patch:

- `python3 -m unittest discover -v -s tools/testing -p 'test_run_categor*.py'`
  passed 247 checks in 33.140s. After broadening the activity hook to all logged
  Maven commands and hardening owned fixture cleanup,
  `PYTHONPATH=tools/testing python3 -m unittest -v test_run_category_activity
  test_run_category_preparation test_run_category_resources` passed all 35 affected
  checks in 1.634s. Actual private-process checks cover overlapping builds, draining,
  preventing new-build overtaking, cancellation, descriptor inheritance after
  wrapper death, and preparation waiting longer than its execution timeout.
- `PYTHONPATH=tools/testing python3 -m unittest discover -v -s tools/performance
  -p 'test_run_isolated.py'` passed six checks in 1.830s after adding nonzero-exit
  coverage and the review regression for own-tree readiness. A real linked-worktree fixture holds a foreign category tree idle while
  two independent measurement invocations finish with exits 0 and 7, each with
  one completed, nonzero-hold queue record. No host Maven or engine work is launched.
- `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base
  3fa0730c628cd27c6da80a692fdc0ac048d36b98 --preflight` passed Java 25, Lua 5.4
  and PowerShell prerequisites. The change-based plan selects 3,447 smoke candidates
  because shared tooling changed. Focused Python/process safety checks exercise the
  changed lock and command-classification contract directly; no POM, Java, test
  selection, workflow or hooks changed, so broad engine validation is disproportionate.
  This is not a smoke or full-suite pass.

Migration matters: running Python processes keep their loaded code. Use updated
wrappers throughout linked worktrees or invoke the main wrapper by absolute path
from the intended cwd. Older wrappers/direct Maven/IDE builds bypass the activity
lease; existing 0.5-second process sampling rejects observed interference but can
miss short overlaps. Different clones and arbitrary host load are not excluded.
