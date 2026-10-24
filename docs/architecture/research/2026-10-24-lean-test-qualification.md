# Bounded lean-lane qualification — 2026-10-24

Base: `0b2c3c2a9127b080137b231ba7e7cfd9e3b1e164` (`develop`).
Task: qualify a bounded set of additional tests for the existing 4 GiB / 4-core
queue lane, following the [test value and cost audit](../audits/testing/2026-10-23-test-value-cost.md).

## Decision and scope

Accept all 15 reviewed classes below into `maven_small.py`: the explicit registry
grows from 55 to 70 classes. Qualified jobs reserve 4 GiB rather than 7 GiB
(43% less reserved RAM). This is an expansion of measured eligibility, not a
reduction in the global reservation. The advisory tooling revision advances
from 2 to 3 to distinguish the expanded admission policy.

The normal 7 GiB / 8-core reservation, 2 GiB host headroom, three-job ceiling,
50-class command limit, source-shape rejection and test selection are unchanged.
Both Maven and the test fork use 1 GiB heaps for lean commands. Category batches
qualify only if every member qualifies; no batch is split, reordered or narrowed
by this change. Runtime classes, assertions and fixtures are unchanged.

The earlier allocation reductions did not establish a proportional peak-RAM
reduction. The prior queue investigation recorded ordinary batches reaching
4.855 GiB and guards 6.736 GiB. That evidence still argues against a universal
4 GiB reservation; see the [original qualification record](2026-10-16-test-throughput.md#qualified-small-jobs-and-one-aged-head-backfill).

## Candidate review

| Class | Declaration sites | Review basis |
|---|---:|---|
| `com.openggf.sprites.TestNativePositionOps` | 6 | Six independent centre-word/subpixel sentinel checks protect a shared gameplay invariant using one real small playable sprite per case. |
| `com.openggf.graphics.TestPatternAtlasLookup` | 9 | Headless lookup/alias/collision/churn cases give independent boundary and ownership coverage without a GL context. |
| `com.openggf.net.protocol.TestControlCodecAdversarial` | 4 | Literal structured mutations reject integer truncation, trailing roots, fractional fields and null membership slots; useful negatives beyond round trips. |
| `com.openggf.net.identity.TestPlayerIdentity` | 12 | Real persistence, signatures, tamper rejection, permissions and concurrent first-use ownership are high-value bounded checks. |
| `com.openggf.physics.TestSwingMotion` | 4 | Four independent positive/negative ROM arithmetic vectors check direction transitions and overshoot cancellation with constant-size inputs. |
| `com.openggf.audio.runtime.TestAudioFrameClock` | 3 | Covers exact totals for nondivisible rates plus restore/carry continuation using a tiny real clock. |
| `com.openggf.audio.synth.TestFastFmWaveformAlignment` | 4 | Independent synthetic signals prove timing alignment accepts one coherent shift and rejects wrong pitch/carrier/silence. |
| `com.openggf.level.TestLevelManagerRewindBoundary` | 2 | Two short real-manager cases protect attached-owner boundary dispatch when an unrelated context is ambient. |
| `com.openggf.game.TestPhysicsProfile` | 35 | 35 distinct declarations cover literal physics constants, water/shoes precedence, game rules and provider dispatch without booting a game. |
| `com.openggf.net.protocol.TestControlCodec` | 12 | Twelve independent declarations cover accepted traffic and malformed/oversized/duplicate/missing membership and admission fields. |
| `com.openggf.net.protocol.TestControlCodecPolymorphism` | 2 | Complete finite sealed-subtype traversal plus unknown/missing discriminator rejection provides breadth that manual codec samples miss. |
| `com.openggf.net.identity.TestDisplayNames` | 4 | Independent Unicode normalization, bidi/control filtering and byte/code-point limit vectors protect user-visible identity sanitization. |
| `com.openggf.net.ghost.TestGhostFrameCodec` | 4 | Literal bit-layout and unsigned-coordinate expectations complement round-trip checks in a seven-byte wire format. |
| `com.openggf.audio.runtime.TestAudioRingBuffers` | 3 | Seeded operation sequences compare optimized ring behavior to a separate modulo reference, with directed wrap and empty cases. |
| `com.openggf.mods.code.TestGolfProtocol` | 1 | One declaration invokes many independent engine-free protocol rejection, receipt and reconnect boundary assertions; include as the higher-cost measured candidate. |

The 15 classes contain 105 declaration sites; parameterized rows determine the
actual execution count. Review followed helper allocations and lifecycle owners,
not just annotations. PatternAtlas uses headless lookup paths; the audio cases
use clocks, bounded primitive buffers and synthetic signals rather than devices
or native oracles. NativePositionOps and the rewind boundary cases construct
small manager graphs without loading a level. PlayerIdentity includes 24 virtual
thread callers and bounded key-file/crypto cases, so POSIX skips and executor
completion must be checked. GolfProtocol separately compiles 21 external sources
and executes its protocol probe through a fresh null-parent classloader.

Excluded from this qualification:

- `TestEditorRegionOperations`: its nested fixture `extends AbstractLevel`, which
  the unchanged whole-source inheritance rejection catches.
- `TestUserRecordingVerifier`: its declared package and source path do not match
  the existing qualification contract.
- `TestGolfTransport`: structurally eligible but deferred; real sockets, workers,
  creator-loader teardown and near-2-MiB traffic are outside this first small
  batch. The prior allocation improvement alone does not qualify its RAM budget.

Independent source reviews covered candidate allocations and lifecycle, then
lease ordering, heap setup and measurement limits. Qualification uses completed
case identities and resource observations below, rather than the source reviews
alone. No ROM-required, trace, native-generator or guard lane was
reclassified. Existing unknown-class, ambiguity, package, inheritance, tag and custom-argument rejection
remain in place.

## Measurements

All five invocations exited zero: no failures, errors, skips, OOMs or timeouts.
The 14-class runs each executed the same 108 case identities; both protocol runs
executed the same one case. Each lean run observed both Maven and Surefire with
`-Xmx1g`. The 105 declaration sites therefore produced 109 distinct executions.

| Selection / lane | Executions | Queue + tree wait (s) | Build drain (s) | Execution (s) | JUnit class time (s) | Peak tree RSS (GiB) | Sampled swap |
|---|---:|---:|---:|---:|---:|---:|---|
| light-cold | 108 | 437.188 | 0.000 | 82.607 | 0.562 | 1.935 | Not sampled |
| light-normal | 108 | 343.173 | 0.000 | 25.976 | 0.521 | 2.475 | 0 B |
| light-warm | 108 | 382.923 | 0.000 | 28.602 | 0.536 | 1.783 | 0 B |
| protocol-normal | 1 | 676.324 | 21.612 | 27.526 | 0.827 | 2.688 | 0 B |
| protocol-lean | 1 | 385.028 | 0.000 | 28.499 | 0.854 | 2.033 | 0 B |

The matched warm light pair reduced sampled peak RSS by 28.0% (2.475 to
1.783 GiB), with execution 10.1% longer (25.976 to 28.602 seconds). The protocol
pair reduced sampled peak RSS by 24.3% (2.688 to 2.033 GiB), with execution
3.5% longer (27.526 to 28.499 seconds). This supports smaller admission budgets
with substantial observed headroom, while exposing a Maven GC/time tradeoff;
it is not a speedup claim. The light tests themselves took about half a second.

Retained Maven GC records rose from 96 collections / 650 ms pauses to
364 / 1,181 ms in the warm light pair; the protocol pair rose from
103 / 651 ms to 364 / 1,148 ms. Lean Maven reached rounded after-GC figures
near its heap ceiling, including during cold compilation. Test-fork retained
after-GC highs were only 13 MiB for the light cases and 30 MiB for the protocol
probe. Do not attribute the whole process-tree reduction solely to test-fork
memory. The completed cold build supplies startup evidence, but future project
growth can still invalidate the 1 GiB build budget.

The bounded diagnostic uses the existing `lean_command` to generate heap flags,
then adds GC logging only. It takes its own worktree lease before exclusive test
admission, then drains builds through the exclusive activity lease. Maven and
its children inherit the leases. Every invocation releases admission afterward.
A temporary observer samples summed descendant RSS with a 100 ms wait between
scans; scan time makes the actual interval longer. It excludes its own Python
process. Shared pages can be counted twice and short peaks can be missed.
Per-JVM peaks occur at potentially different times and must not be summed as a simultaneous peak.
The first already-queued cold invocation recorded RSS only. Subsequent diagnostic
invocations and the final automatic-lane check also sample per-process `VmSwap`
and simultaneous RSS plus swap; none of these is a total-host-pressure measure.
GC log totals describe observed collection pauses, not all GC CPU work. Rotating
logs can omit early events; rounded after-GC heap figures are not a maximum live
set. Missing collection records do not imply zero heap use.
Queue/build-drain waits are separate from execution. A cold compilation measures
startup/build headroom; before the first run, `target/classes`, `test-classes`
and `maven-status` were all absent; dependency and filesystem caches were retained.
Warm comparisons use the same already-compiled source.
Each invocation starts fresh Maven and Surefire JVMs: warm refers to compiled
outputs/caches, not JIT warmup. Normal Maven keeps its inherited/default heap,
with a 3 GiB test heap; lean bounds both to 1 GiB. The fixed-order pairs are
qualification observations, not speedup evidence or a universal memory/timing
bound. The leases coordinate cooperating wrappers, not unrelated host activity.
The 4 GiB / 4-core values are admission estimates, not enforced resource limits;
this experiment does not establish a four-core CPU maximum or qualify every
possible 50-class combination.
Inspection of the earlier profiler also corrected the initial audit’s isolation
claim: that tool excluded queued tests but did not drain/exclude build-only work.
The allocation observations remain useful; its time comparisons have an additional
interference limitation. This experiment explicitly holds both exclusive leases.

## Validation and delivery

- `python3 -m unittest discover -v -s tools/testing -p 'test_run_categor*.py'`:
  252 tests passed in 44.295 seconds, no skips. This covers exact selectors,
  custom-argument rejection, heap propagation, normal opt-out, unchanged category
  includes, backfill and advisory revision behavior.
- `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base
  0b2c3c2a9127b080137b231ba7e7cfd9e3b1e164 --preflight`: passed actual Java 25,
  Lua 5.4 and PowerShell checks. This was completed before diagnostics and does
  not represent engine-suite validation.
- Static comparison found the qualification/admission AST unchanged after removing
  only the module docstring and registry literal. All 70 entries individually
  pass existing source checks; a 70-class command still fails the 50-class cap.
- Actual automatic admission:
  `python3 tools/testing/maven_queue.py -Dmse=off -B -Dtest=<50 exact FQCNs>
  -Dopenggf.surefire.reports=<temporary reports directory> test` selected the
  resource-aware 4 GiB / 4-core lane without explicit `--lean` or JVM overrides.
  Cold compilation and all 326 executions across 50 reports passed, with zero
  failures/errors/skips. The 109 new case identities/statuses matched the paired
  diagnostics exactly; both JVMs were observed with `-Xmx1g`.
  Sampled peak tree RSS was 1.637 GiB; peak swap was
  25.742 MiB, and the highest simultaneous RSS plus swap
  was 1.649 GiB (do not sum the separate maxima).
  This leaves substantial observed margin within 4 GiB, but is not a no-swap
  result. The wrapper/Maven had sampled swap; the test fork had none. The observer
  collected 2,607 samples. Command wall time was 292.796 seconds,
  comprising 189.707 seconds through admission and
  103.089 seconds afterward. The latter includes compilation,
  tests and wrapper cleanup, not just JUnit execution.

The change-based plan (`run_categories.py --base
0b2c3c2a9127b080137b231ba7e7cfd9e3b1e164`) selected all 3,655 smoke candidates
plus guards because it conservatively treats testing-tool changes as shared.
The lead chose proportionate focused validation: executable edits are registry
entries and an advisory revision literal, with no scheduling functions, Java,
POM, selection or fixture edits. The safety suite and real automatic invocation
exercise the changed budget path directly. No broad engine smoke or release-only
full suite was run, and none is claimed.

The diagnostic selection was the first 14 rows of the candidate table together,
then `com.openggf.mods.code.TestGolfProtocol` separately. Each command used
`-Dmse=off -B -Dtest=<exact comma-separated FQCNs> test` with a temporary
`openggf.surefire.reports` directory. The one-off observer held the existing
queue/worktree/activity leases described above and launched Maven directly
inside those leases. Lean arguments/environment came from `lean_command`;
normal retained the POM's 3 GiB Surefire heap. Only GC logging was added.
There was a ten-minute execution watchdog per diagnostic; admission waiting
was excluded. Stop qualification on missing cases, unexpected skips, failures,
OOM or timeout rather than accepting a normal-lane retry as a lean pass.

Java, POM and external probe sources remained byte-identical to the pinned base
through the matched measurements. Only report prose changed during that sequence.
The final automatic check uses the ordinary wrapper without explicit `--lean`,
GC flags or other JVM overrides, after clearing only compiled outputs and compiler
status. It combines all 15 new classes with the lexicographically first 35
registry classes at the pinned base, at the unchanged 50-class command limit. This check exercises actual automatic
admission and cold compilation; it does not claim isolated timing or universal
batch coverage. Raw logs, XML and temporary measurement directories are consumed
and deleted; the engineering summary here is the durable record.

The final source/evidence review found no substantive blocker. Before integration,
new document links resolved and `git diff --check` passed. All executable inputs
for the final automatic check were the pinned base plus the registry additions
and advisory revision change recorded here; later edits completed prose only.
