# Maven resource profiling and admission — 2026-09-15

## Question and method

How much host memory and CPU does the full ordinary/guard selection need, and can
linked worktrees run concurrently without abandoning the existing queue?

The baseline is `6cd8ec1881fcab438705ec4fd736804dd6630a50` on `develop`, in the
main workspace. Java 21.0.11, Maven 3.9.16, Linux x86-64, 32 logical CPUs and
30.44 GiB physical RAM. Existing build output was present; this is an exploratory
warm-workspace measurement, not a cold-build benchmark. Other host applications
remained running. The queue admitted the baseline immediately and exclusively.

Command: `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --category all --run`.
The selection was 2,588 ordinary classes plus the separate guard lane, one worker,
with the default 3 GiB test-JVM heap and the runner's existing absolute ROM discovery.
Preflight passed after selecting installed Lua 5.4 rather than the default Lua.
Stopping rule: 40 execution minutes or 10 minutes without output, excluding queue wait.

A temporary Python/psutil sampler followed descendants of the category-runner PID
once per second, summed RSS, and retained cumulative user+system CPU by
`(pid, creation_time)` even after a sampled child exited. The reusable
[`profile_maven.py`](../../../tools/testing/profile_maven.py) preserves this method
and produces bounded aggregate JSON. Reproduction:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/profile_maven.py --output target/maven-profile.json -- --category all --run
```

RSS sums shared pages and can overcount physical memory. One-second sampling misses
short-lived children and transient peaks, making sampled CPU a lower bound.
CPU cores means CPU-seconds divided by wall-seconds; it is not a percent of the host.
The profiler labels queue-inclusive wall time and mean CPU. Do not use a queued
mean as the cost of active tests. Read completion status, exit code, skips and failure
identities separately from resource measurements. Separate trace/native/diagnostic
profiles and two-worker execution are outside this measurement.

## Resource measurements

The completed run took **898.24 seconds (14.97 minutes)**, with no queue wait.
One-second sampling produced 893 samples. The ordinary lane took 721.99 seconds;
the guard lane took 174.57 seconds. Runner/probe startup and transitions account
for the small remaining difference.

| Measurement | Whole invocation |
|---|---:|
| Peak summed descendant RSS | 5.823 GiB |
| 95th-percentile summed descendant RSS | 5.074 GiB |
| Sampled user + system CPU | 1,736.52 CPU-seconds |
| Mean CPU usage | 1.933 cores |
| 95th-percentile one-second CPU usage | 4.641 cores |
| Peak sampled CPU usage | 8.662 cores |
| Minimum host available memory | 11.380 GiB |

The highest RSS sample occurred at elapsed 715.17 seconds with four descendants,
near the end of the ordinary lane. The guard-phase peak was approximately 4.680 GiB
(the phase split is rounded to the observed transition, not a separate oracle).
The 7 GiB reservation rounds above the 5.823 GiB observed peak; the shared 2 GiB
headroom and double-counted active reservations add further admission margin.
CPU reservation is 8 cores: much higher than mean or p95, with the separate host-load
check adding margin. This is one exploratory run, not a worst-case proof.

## Observed test coverage

The ordinary lane completed in 721.99 seconds: 2,588 reports, 20,487 tests,
1 failure, 0 errors, 18 skips. The baseline failure is
`com.openggf.game.rewind.TestRemainingRewindTailInventory.remainingRoundTripTailMatchesInventory`:

```text
total object class count changed
expected total=1010 passed=790 graphCovered=220 noCodec=0; current total=1011 passed=791 graphCovered=220 noCodec=0 ==> expected: <1010> but was: <1011>
```

The skips include two unavailable graphics contexts, the existing CPZ spin-tube
capture/release assumption, and 15 opt-in/soak/local-reference checks. None reports
a missing game ROM. These are coverage limits, not passes. This task does not change
the Java tests, inventory or engine behavior.

The guard lane completed 84 reports and 668 tests with 3 failures, 0 errors and
0 skips. Exact baseline identities/messages:

- `com.openggf.game.rewind.TestRewindArchitectureGuard.objectRewindAnnotationsDoNotGrowWithoutExplicitBaselineTriage`:
  `Object packages should prefer central rewind policies/codecs. New @RewindTransient/@RewindDeferred annotations need explicit triage.`
  followed by `Unexpected growth:` and
  `src/main/java/com/openggf/game/sonic3k/objects/SozQuicksandObjectInstance.java#@RewindTransient = 2`.
- `com.openggf.tests.TestBuildToolingGuard.supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`:
  `supported documentation must describe direct Maven and explicit hook setup:` followed
  by five `AGENTS.md/CLAUDE.md do not contain required direct-Maven guidance:` entries:
  `Concurrent Maven runs need separate worktrees.`,
  `mvn -Dmse=off "-Dtest=TestCollisionLogic" test`,
  `mvn -Dmse=off -Pguards test -B`, `mvn -Dmse=off package`, and
  `python3 tools/testing/run_categories.py --base <printed-pinned-base> --run`.
- `com.openggf.tests.rules.TestNoAssertionFreeDiagnostics.noAssertionFreeTestMethodsUnderTestsTree`:
  `Assertion-free @Test methods found (add a real oracle, demote off @Test, or allowlist with a reason in TestNoAssertionFreeDiagnostics):`
  followed by `FbzRouteEvidenceProbe#printEvidence has no assertion/verify/fail oracle (src/test/java/com/openggf/tests/FbzRouteEvidenceProbe.java)` and
  `LevelSolidityMapProbe#writeSolidityMap has no assertion/verify/fail oracle (src/test/java/com/openggf/tests/trace/LevelSolidityMapProbe.java) ==> expected: <true> but was: <false>`.

## Admission design

Keep the OS-owned original `maven-queue.lock`: serial and previous single-lock
clients hold it exclusively; resource-aware clients hold it shared. This preserves
mutual exclusion during adoption. A brief admission lock makes resource checking
and numbered-slot acquisition atomic. Each worktree has an additional exclusive
lease to protect its own `target/`. All execution descriptors pass into Maven on
POSIX, preserving ownership if its Python parent is killed. Normal cancellation
stops the process tree before releasing leases. No PID files, daemon, stale-lock
cleanup or strict FIFO promises are introduced.

Default limits are two invocations, 7 GiB and 8 CPU cores per invocation, plus 2 GiB
memory headroom. Admission checks available memory, CPU affinity, visible cgroup v2
ancestor limits and one-minute system load. Existing runs count as full reservations
in addition to OS-observed usage. This intentionally overestimates demand and covers
young JVMs whose current RSS has not reached their peak. Reservations are admission
estimates, not hard resource enforcement. Unrelated host load can change afterwards.

The settings live in shared Git configuration, documented in the
[testing guide](../../../tools/testing/README.md#queued-maven-execution).
`OPENGGF_MAVEN_QUEUE=serial` keeps an invocation exclusive. Unsupported/inaccessible
resource counters and CPU allocations too small for two reservations retain serial
progress. Two-worker category runs, nonstandard explicit Maven profiles, thread/fork/
heap overrides and JVM environment overrides remain exclusive because their cost
was not measured. Custom implicit Maven configuration requires the serial override.
Changing policy between runs avoids mixed reservation estimates.

### Rejected alternatives

- Raising a fixed semaphore limit alone: it cannot account for other host load or
  available memory. The existing measurement-hazard catalogue records corrupted
  validation under concurrent resource pressure.
- Admitting from instantaneous idle CPU/RSS alone: simultaneous startup observes
  cheap young processes and can admit more peaks than fit. Full reservations and
  atomic admission cover this race.
- Replacing the old lock with independent concurrent slots: older queue clients
  would no longer exclude new clients. Shared/exclusive ownership preserves that
  established execution boundary.
- Automatically treating every Maven profile as the default suite: two forks and
  larger heaps invalidate the single-worker estimate. Those shapes remain serial.

## Implementation validation

The change-based plan selects the full engine suite because the Python paths fall
through its shared-path rule. The repository's explicit Python-runner exception
applies: no POM, selection policy, Java, workflow or hook changes. Implementation
validation uses the Python safety suite and real Java/Lua/PowerShell preflight;
the engine run above is the requested exploratory profile, not a green claim for
this change. Real competing-process tests exercise concurrency, per-worktree
exclusion, capacity waiting, old/serial compatibility, cancellation, inherited
leases after parent death, and fallback progress. Unit tests cover reservation
arithmetic, cgroup ancestors, invalid configuration and profiler aggregation.


Observed implementation checks in `.worktrees/ai-maven-resources`:

- `python3 -m unittest discover -s tools/testing -p 'test_run_categor*.py'`:
  68 tests passed. After tuning memory reservation from 6 to 7 GiB, the six resource
  policy tests passed again; no Java or selection changes accompanied the tuning.
- `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 6cd8ec1881fcab438705ec4fd736804dd6630a50 --preflight`:
  Java 21, Lua 5.4 and PowerShell checks passed.
- The candidate wrapper launched `-Dmse=off -Dtest=TestCollisionLogic test` in both
  main and development worktrees after the baseline exclusive run finished. Each
  ran one test with no failures/errors/skips. Maven durations were 19.688 and
  46.559 seconds, ending at 11:35:55 and 11:36:22 BST: both began around 11:35:35.
- With the final 7 GiB default, main ran the same collision test while development
  ran `-Dmse=off -Pguards '-Dtest=TestBuildToolingGuard#supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap' -Dopenggf.surefire.reports=target/maven-resource-guard-reports test`.
  Both acquired resource-aware slots and ended at 11:39:02 BST, with Maven durations
  18.979 and 19.943 seconds. The collision test passed; the guard ran one test with
  the baseline failure. An XML/JSON comparison verified exact class, method and
  complete failure-message equality, not truncated prefixes or totals.
- `git diff --check` and `cmp AGENTS.md CLAUDE.md` passed. The existing guard's
  direct-Maven guidance expectations are stale on the baseline; they were not
  rewritten as part of this queue change.

These checks establish the admission mechanism and preserve observed baseline
failures. They do not certify two concurrent full suites, cold builds, alternate
profiles or graphics availability. Temporary samples, XML and logs are consumed
and removed; the category run is acknowledged through the runner.
