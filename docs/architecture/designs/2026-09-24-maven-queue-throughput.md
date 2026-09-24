# Maven queue throughput — 2026-09-24

## Problem

Agents lose cycles waiting in the shared Maven queue. The goal is less time blocked per
request without lowering coverage, determinism or the documented concurrency ceiling
(measurement hazard 43: two to three suite runs on a 32-core / 30 GiB host). CI and
release gates are unchanged.

Base: `develop` at `719d7678d`, worktree `.worktrees/ai-maven-queue-throughput`.

## Findings

- **The admission rule was effectively serial for most of a full run.** Admission required
  `MemAvailable >= runs × 7 GiB + 2 GiB` while also counting every active run as a full
  7 GiB reservation. MemAvailable already excludes what those runs use. On this host,
  with other applications open, MemAvailable idles at about 20.9 GiB. After one full
  suite reaches its measured 5.8 GiB peak
  ([2026-09-15 profiling](../research/2026-09-15-maven-resource-admission.md)), about
  15 GiB remains, below the 16 GiB a second run needs. A second worktree could only start
  while the first JVM was still young. The CPU rule double-counted load in the same way.
- **Most explicit profiles forced exclusive runs.** Every profile other than `smoke`/`guards`,
  including `trace-replay`, took the whole machine, although each keeps the default
  shape: one reused fork with the shared `-Xmx3g`.
- **Nothing recorded waits.** The queue kept no history of wait, hold or memory, so the
  split between broad, focused and exclusive waiting was unknown.
- **Broad-run cost profile (context for later phases).** Summed per-class Surefire time
  from leftover reports was 11.7 min (`OpenGGF-next/target`, 2,810 classes) and 19.0 min
  (main checkout, 2,625 classes). These are mixed-run snapshots, not a controlled
  measurement. The top 10 classes were 50–61% of test time. More than 2,500 classes
  took under 1 s each and 1–2.6 min in total. Three guards
  (`TestActiveSegmentPayloadAuthorityGuard`, `TestObjectUpdateClockTerminologyGuard`,
  `TestBuildToolingGuard`) were 136 s of the 186 s guard time. The 2026-09-15 run measured
  a 15-minute full invocation at a mean of 1.9 cores.

## Delivered (phase 1: queue only)

1. **Usage credit.** A slot holder creates an OS-leased `.git/maven-running/*.lease` under the
   admission lock. A sampler thread publishes the holder's process-tree RSS and 60-second
   CPU every 2 s. Admission adds the credit to available memory and CPUs:
   `min(realised, own reservation)` per live, fresh (≤15 s) record. A young, stale,
   malformed or unlocked record earns nothing, so a dead parent, an older wrapper or a
   torn write keeps the full reservation. The record is removed before the slot is
   released. Records never authorize execution.
2. **`maxRuns` default 3**, the documented ceiling. Memory normally binds first.
3. **Shared single-fork profiles.** `ci`, `trace-replay`, `trace-segments`,
   `trace-replay-r7`, `trace-diagnostics`, `fbz-routes`, `audio-reference` and
   `audio-local-wave` join `smoke`/`guards` under the default reservation. A Python test
   pins their fork count and heap against `pom.xml`; breaking it on purpose with
   `test-concurrent` fails it. `benchmarks` stays exclusive because its assertions are
   wall-clock sensitive, even though its shape passes. `test-concurrent`, `audio-stress`,
   `tracechaser-integration`, packaging and overrides also stay exclusive.
4. **Telemetry.** Each admitted or cancelled request appends one JSON line to
   `.git/maven-queue-log.jsonl`: kind, worktree, wait, hold, peak RSS and mean cores. It has
   no arguments or results, so it is not a validation receipt. The log trims to its newest
   half above 512 KiB. `maven_queue.py --stats` summarises it by kind with each kind's
   share of the total wait.

### Rejected

- **Smaller reservations for focused runs** (rejected again from 2026-09-15). A single test
  still starts a 3 GiB-heap fork. The first real focused run here peaked at 2.96 GiB.
  Credit gives the same benefit without guessing a peak.
- **Raising `maxRuns` alone.** The double-counted memory rule binds before the run cap.
- **PID files or process-group ownership for sampling.** The holder samples its own
  descendants and publishes through a lease it holds. A PID is never an authority.
- **Time-stamped records in the slot lock files.** A killed holder's fresh-looking record could
  credit a later legacy holder of that slot. Separate leases tie liveness to the writer.

## Next phases (decide from telemetry)

After a few days of `--stats`, pick by each kind's share of wait:

- **Phase 2: shorten broad runs at the same memory.** Measure time outside tests
  (Maven lifecycle, compilation, the separate guard invocation). Speed up the three guards
  that dominate guard time with one shared source/class-graph index per JVM. Look at
  the HCZ route and S2 audio-oracle classes that lead ordinary time. Re-enable CDS: it
  was disabled with `-Xshare:off` only to silence agent warnings in `80c4c5d36`.
- **Phase 3: several JVMs sized by heap need.** Only if broad-run hold time still dominates.
  It raises per-run footprint, and changing which classes share a JVM risks exposing
  order-dependent state.

## Validation

Python-runner exception (queue/runner Python, tests and prose; no POM, Java, selection
policy, workflow or hook change):

- `python3 -m unittest discover -s tools/testing -p 'test_run_categor*.py'`: 92 tests
  passed, three consecutive runs (81 before the change). These include competing-process
  tests. Without credit, a second run is held at 15 GiB free; with a published 3 GiB of
  realised usage, the same second run is admitted. A fourth worktree is held by the cap
  of three. A finished request and a cancelled waiter each leave one telemetry line.
- Real run: `maven_queue.py -Dmse=off "-Dtest=TestCollisionLogic" test` passed 1/1. It was
  logged as `focused`, holding 48.5 s with a 2.96 GiB sampled peak and 2.05 mean cores.
