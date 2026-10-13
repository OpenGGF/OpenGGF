# Maven queue contention after the smoke default

Original task/base: `develop` at `b3464b31b6a6d855797f353e2c192d514ffaaf2b`.
Implementation: `.worktrees/ai-maven-queue-contention`,
`bugfix/ai-maven-queue-contention`. The integration branch later advanced to
`36df162ea45d4fe8aec41a48121504116d2082f8` with Mod compatibility changes;
its queue/runner, POM, workflow and agent guidance remained identical to the base.

## Observed causes

The maintainer reported agents waiting an hour for testing. The bounded queue log
confirmed completed focused jobs waiting 55–64 minutes to execute for 20–67 seconds.
A snapshot of today's completed telemetry had median wait 15.3 minutes, p95
67.1 minutes and maximum 68.6 minutes; nineteen waits exceeded an hour. Median
execution was 40 seconds. These are job intervals, not summed delivery time.

The live snapshot had 26 waiters and one running `ai-github-issues` smoke job.
Fourteen waiters used lean reservations. Linux showed that running Python holder
owning **an exclusive `maven-queue.lock`**, with
`JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`. The old
`needs_exclusive` classified every nonempty JVM environment variable as an
unmeasured resource override, even that graphics-only property. A regression
reproduced the false exclusivity for all three supported JVM option variables.
Existing participating worktrees already had the smoke update and identical queue
tooling; stale profiles were not the primary cause in this snapshot.

Broad smoke itself can also be long. A surviving `runtime-perf-series` progress
summary recorded 3,173 candidates, 3,154 ordinary report classes and 26,690 cases:
ordinary 3,534.65 seconds, guards 287.54 seconds, total hold about 64 minutes.
Two other ordinary smoke runs timed out after 40 minutes before guards began.
The current job showed individual smoke classes taking 38–92 seconds. Consumed
diagnostics no longer identify all historical expensive classes or launch flags;
these timing observations do not establish parity or retrospectively prove that
all long holders were exclusive.

Available host RAM was approximately 6.9 GiB in one sample, with 32 CPUs. The
normal reservation remains 7 GiB/8 cores, lean 4 GiB/4 cores, headroom 2 GiB,
maximum three admitted runs. More nominal slots or lower reservations would not
be justified by an average 40-second job or one small sampled process tree.

## Change and alternatives

- Allow exactly `-Dopenggf.headless.gl=surfaceless-egl` in JVM environment options
  without forcing exclusivity. Preserve it in lean launches. Unknown options,
  malformed quoting, heap/fork/thread changes or mixed unsafe options remain
  conservative. Do not remove required graphics flags to win admission.
- Partition the **complete** ordinary category inventory into deterministic
  batches of at most 50 candidate source classes, with the existing smoke tags
  and exclusions. Each batch queues normally, then releases shared admission;
  guards queue separately. Raw unfiltered Maven runs remain monolithic.
- Retain the worktree lease throughout to protect `target/` and active
  diagnostics. Validate the retained descriptor against the requested lock;
  POSIX idempotent flock probes prevent bypassing a foreign holder. Maven inherits
  the batch's leases. Windows retains serialized admission and trusts the internal
  caller's matching held descriptor; Linux competing-process checks are the
  observed ownership evidence here.
- A blocked aged same-tree waiter must not prevent the retained holder's next
  batch from resuming. The continuation marker changes scheduling, never execution
  authority; unrelated aged requests retain capacity priority. A real-process
  regression reproduced this otherwise possible deadlock.
- Accumulate execution time across batches and guards, excluding each queue wait.
  Check the combined source fingerprint after admission and execution. Aggregate
  bounded failures/skips under ordinary/guard results; record exact batch commands.
  Empty individual tag-filtered/helper batches are allowed, empty overall lanes fail.
- Stop on a nonzero Maven exit without JUnit failure/error reports. Mark remaining
  coverage incomplete and retain failed batch identity and the causal log, rather
  than retrying a broken build or overwriting its only evidence. The review found
  this risk; a red/green regression verifies the correction.

Keeping one slot across ordinary and guards was rejected by the observed hour-long
hold. Releasing the worktree lock too was rejected because another same-tree
command could replace classes or prune active diagnostics between batches. Ignoring
aging, lowering resource reservations, preempting unrelated jobs and deleting locks
were not used. The complete smoke selection, full-suite release policy and existing
memory/CPU ceilings are preserved.

Fifty candidates bound batch size, **not time**. A single expensive class can still
hold admission, guards remain one job, memory pressure can prevent overlap, and
extra Maven/JVM startups may increase total broad-run cost. Existing already-running
or queued invocations have loaded their previous implementation; this change does
not hot-patch, cancel or restart them. Updated tooling applies to subsequent runs.

## Verification

- Unchanged base: `python3 -m unittest discover -s tools/testing -p
  'test_run_categor*.py'`: 144 passed, no skips, 24.854 seconds.
- Candidate, same command: 161 passed, no skips, 25.621 seconds. This includes real
  competing-process leases with synthetic resources/fake Maven, complete batch
  coverage, aged continuation, foreign holder protection, cancellation/inherited
  descriptors, huge queue waits excluded from budget, source changes, infrastructure
  failure preservation and bounded aggregate evidence. Assertions failed before
  their respective fixes, including the false graphics exclusivity and deadlock.
- `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base
  b3464b31b6a6d855797f353e2c192d514ffaaf2b --preflight`: Java 25, Lua 5.4 and
  PowerShell passed; no engine tests launched.

This is the documented Python-runner validation scope: no POM, category ownership,
selection policy, Java, workflow or hook changes. Broad engine runs would repeat
unchanged gameplay while not adding queue safety coverage. Neither full-suite nor
complete smoke gameplay certification or a production wait-time speedup is claimed.
