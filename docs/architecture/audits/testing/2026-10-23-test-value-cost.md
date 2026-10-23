# Test value, resource cost and optimisation audit — 2026-10-23

Audit base: `e633e4c8259e610e81854fc8acd35172ac592068` (`develop`, after fetching and fast-forwarding).
Implementation worktree: `.worktrees/ai-test-value-cost-audit`.

The useful optimisation target is work that does not contribute to an assertion:
unconsumed trace streams, repeated compilation, unused reference snapshots,
repeated parser setup, unnecessary mock history, and image output that nobody
checks. Expensive independent ROM/audio oracles and real route/rewind boundaries
are worth preserving. Deleting inexpensive assertions is unlikely to move total
delivery time materially.

This audit inventories the configured Maven test roots, other first-party Java
test trees, and named Python/shell/PowerShell/Lua tests, then examines representative
mechanisms and outliers deeply. Runtime tools outside those roots and optional
submodules are not an exhaustive part of this source census. It does not
claim a method-by-method semantic review of 26,000 declarations, a mutation score,
or a new whole-suite timing. The detailed reviewed findings distinguish source
evidence, historical measurements and this task's new focused measurements.

## Scope and counting

At the pinned base, the source census found:

| Unit | Count | Meaning |
|---|---:|---|
| Java files across the inventoried roots | 4,463 | Includes helpers, abstract bases, examples and manual/runtime entrypoints |
| Java files in root Maven test roots | 4,350 | Includes 99 files in racing main-source directories added as test roots |
| Other example/project Java test-tree files | 113 | Separate project entrypoints, not automatically additional Maven tests |
| JUnit annotation sites | 26,064 | Includes parameterized/factory declarations; **not executed case count** |
| Python test modules | 67 | 763 syntactic `test*` definitions; dynamic subtests and skips differ |
| Shell/PowerShell/Lua test entrypoints | 10 | Explicit tooling/domain entrypoints |
| Ordinary category-runner candidate files | 3,655 | Authoritative `run_categories.py --category all` plan; no tests launched |
| Guard-pattern source paths | 90 | 89 executable-looking sources plus a compatibility facade |
| Individually reviewed source files | 181 | 188 file/method findings; not a random sample of suite quality |

The source scanner strips comments and string literals, recognizes qualified
JUnit annotations, inherited test methods and ArchUnit fields, and separates
manual `main` entrypoints. It is a source inventory, not JUnit discovery. Eight
ordinary candidate paths are support/abstract sources. Twenty-two candidates
have class-level tags excluded from smoke; method tags, conditions, inherited
conditions, ROM/native availability and dynamic factories further change actual
execution. No subtraction of source counts is presented as a passing test count.

The companion census includes 4,540 source paths and family/cost screens within
the stated roots. The 11 additional racing main entrypoints include operational
server/worker launchers as well as validation tools; they are not 11 JUnit tests. Nine are tool/diagnostic/generator or health-probe
entrypoints; `MasterServerMain` and `VerifierMain` are operational applications
and excluded from test judgments and the family totals below.
Unreviewed value labels are explicitly provisional family-level assessments;
they are not evidence for deleting a test. The [reviewed-findings JSON](2026-10-23-test-value-cost-findings.json)
contains the durable individual judgments. Optional disassembly and TraceChaser
submodule internals are outside the first-party census; their OpenGGF boundary
checks are included. Main-workspace dirty submodules and unrelated files were
preserved.

Reproduce the authoritative selection with:

```bash
python3 tools/testing/run_categories.py --category all
```

The census uses `git ls-files`, the POM source roots and the runner's existing
`inventory`/`owners` functions. It does not infer tests from file size, filename
alone, or the presence of `assert` in a wrapper.

## Value and cost are separate axes

**High value** protects a meaningful failure: shipped-ROM behavior, independent
native output, input/authority rejection, real resource ownership, persistence,
recreation, load transitions, or a discriminating regression. It has an oracle
that would fail for a plausible wrong implementation. Synthetic fixtures can be
excellent at this; a real ROM does not automatically make a weak assertion good.

**Low value** means low *incremental automated regression signal*: a tautology,
an unused golden vector, existence already required by compilation, an obsolete
wording scan, or a diagnostic that prints discrepancies without rejecting them.
An investigative probe can have high research value and low regression value.
It should be explicitly selected, not deleted merely for that classification.

**Cost** has several dimensions: test execution CPU/wall time, cumulative
allocation, post-plan retained heap, native/GPU resources, disk/encoding work,
compiler/process startup and queue reservations. A one-second test allocating
several GiB is expensive in churn while modest in elapsed time. A few cheap
checks can still incur an expensive Maven compilation. `mixed` means different
methods/dimensions/lanes have different properties, not a numerical midpoint.

| Quadrant | Examples | Action |
|---|---|---|
| High value, low cost | `TestNativePositionOps`, `TestControlCodecAdversarial`, `TestEditorRegionOperations`, `TestPatternAtlasLookup`, trace V5 rejection and owner-lifecycle seams | Keep in fast coverage; extend with precise boundaries and negative controls |
| High value, high cost | Continuous ROM run chains, native FM pins, whole-world rewind, actual SDK packaging, compiler-backed authority guards | Remove setup/observation waste; preserve oracle, scope and lifecycle |
| Low value, low cost | Class-existence assertion inside a typed reader suite, YAML dependency availability, exact incidental prose pins | Fold into stronger contracts when touching the area; little speedup expected |
| Low value, high cost | Whole-source obsolete-helper prose scans; fully materialized traces used only for diagnostic self-consistency; opt-in print-only long probes as regression gates | Stream/narrow construction or move diagnostic purpose to its explicit lane; retain the real contract |

Important false positives: `TestTerrainCollisionManagerReset` uses null checks
to prove a populated pool is cleared. `TestS1Mz1SlotLayoutRegression` and
`TestGolfTransport` delegate to strong asserting helpers. `TraceCatalogHangTest`
uses a timeout as its oracle, although an ambient checkout is a weakly controlled
fixture. A naive assertion-count classifier would misjudge all four.

## Family assessment

Families below are a mutually exclusive presentation grouping. Runner categories
overlap and are not changed by this audit. Counts include 4,265 detected test/manual
entrypoint sources (excluding the two production racing service mains), not execution results; the companion inventory identifies support files.

| Family | Sources | Value assessment | Main resource/quality concern |
|---|---:|---|---|
| Gameplay and routes | 1,738 | Mostly high; long routes and short event checks protect different obligations | Repeated boots/prefixes, hidden parameter products, failure masking, mocked frame loops |
| Rewind, recordings and saves | 464 | High; restoration is distinct from ROM correctness | Unused full reference snapshots, object graph lifetime, expanded archive builders |
| Trace contracts and replay | 374 | High independent parity and authority boundaries; some diagnostics mixed/low | Full auxiliary materialization for small questions; long continuous transitions |
| Audio and synthesis | 296 | High native pins and adversarial parser tests; oracle independence varies | Chip cycles, huge synthetic raw-state documents, static evidence factories, unused PNGs |
| Rendering and presentation | 254 | High when actual pixels/order/wrap are checked | GL setup/readback/synchronization; command assertions do not prove GPU output |
| Mods and SDK | 204 | High fault, packaging, persistence and classloader boundaries | Recompiling/repackaging identical examples, generated art, weak broad smoke totals |
| Physics and collision | 100 | High cheap exact behavior; isolated weak smoke exceptions | Avoid replacing ROM word semantics with fitted expectations |
| ROM decoding/content | 91 | High independent decoder vectors and valid/invalid boundaries | Immutable decode reuse can help; round-trip-only expected values are narrower |
| Structural guard naming family | 89 | High authority guards; wording/migration checks mixed/low | Per-file javac tasks, repeated graph imports, child startup and incomplete naming discovery |
| Network/racing | 128 | High protocol rejection, reconnect, backpressure, identity and teardown | Preserve real sockets/barriers; previous clock-injection wins already landed |
| Editor/Stageplay | 61 | High small state/command tests plus necessary rendering integration | Keep native prerequisites and scene/global cleanup explicit |
| Other engine/Java tooling | 293 | Mixed; meaningful policy/launcher tests alongside diagnostic/prose checks | Compiler/CLI setup and source analysis dominate some families |
| Separate example projects | 96 | Model/scene contracts plus manually invoked probes | Root integration wrappers may execute these; do not count them twice as distinct coverage |
| Non-Java tooling | 77 | High queue, process, packaging and evidence validation | Real process isolation, interpreter matrices and domain prerequisites; not all run in CI |

The additional racing roots illustrate the difference between a workload and a
test: `GhostLoadTestTool` prints outcome counts, while its separate JUnit caller
asserts the expected finishes/sanctions. `TrackProfileExportTool` permits an empty
or partial result when ROMs are unavailable, so successful generation is not a
completeness check. Video-generation tools have state gates but do not independently
establish pixel correctness. Deployment, real-socket and multi-process scale tools
have meaningful rejection oracles; retain their explicit modes, ownership and
measurement boundaries. The reviewed findings cover all nine manual entrypoints.

## Current execution policy and the cost outside assertions

Routine Maven and categories use smoke. `full-suite` is public-release-only;
guards, traces, audio references/stress and FBZ routes have separate selection.
The full-suite profile still excludes `fbz-route`: "full" is not every domain
lane. Four `TestNo*`/`Test*Guard*`/`TestArchUnit*`/boundary naming patterns choose
guards, so a behavioral `TestNoDeviceAudioSink` lands there while the ordinary
`TestModApiSignatureSurface` imports production bytecode. The reviewed
`TestFbzCompatibilityMatrix` prose also overstates ordinary route coverage: its
representative route is tagged slow and its full matrix is `fbz-route`.

The POM comments about roughly two-minute smoke and old eager trace retention
are historical context, not a present runtime forecast. Main default smoke is
much larger now. There is no new entire-suite elapsed-time claim in this audit.

The category runner already compiles/prepares once, then queues ordinary batches
of at most 50 candidates and fresh guards. This task does not re-propose that
delivered change or add persistent pass caches. A previous matched 11-case
experiment measured median lifecycle invocation **24.269 s** versus prepared
Surefire **1.406 s**, with only about 0.2 s of JUnit work; see the
[October throughput record](../../research/2026-10-16-test-throughput.md).
That is evidence about startup/preparation, not a 17-fold whole-suite speedup.

At an early read-only telemetry snapshot, the retained log had 1,446 jobs since
2026-10-22 17:19 UTC: category-batch median/p95 holds **12/528 s**, focused
**48/106 s**, and guard-category **273/491 s**. Median/p95 category-batch waits
were **16/558 s**. These heterogeneous, rolling, cross-worktree observations have
no test selectors/outcomes and cannot rank individual classes or predict this
delivery. Queue wait and execution are separate; overlapping waits are not added
into elapsed time. Heap caps/reservations are not measured memory consumption.

First-party trace fixtures occupy about **702.2 MiB stored** at this base. Eager
decompression can create much larger temporary representations. Existing
three-GiB reused test forks and the small-test lane are deliberate capacity
policies; changing fork count, global heap limits or JUnit parallelism is not a
low-risk substitute for removing unnecessary work.

## Ranked improvements

### 1. Narrow trace evidence loading — implemented in this delivery

`TestS3kKosTimingRewindIntegration` only consumes the first KOS module completion
edge. Load the same metadata and strict timing stream through existing APIs;
keep the real ROM submission, preparation, recorded boundary, output bytes,
before/on/after rewind, one-claim rejection and next ordinal.

`TestLiveTraceComparatorObserver` loads LBZ solely for metadata, then constructs
its own two rows and CPU events. Load that same metadata file directly. All
observer/phase/ownership assertions and fixture rows stay unchanged. These two
tests no longer incidentally validate the unused physics/auxiliary fixture streams.
Whole-fixture validation belongs to the explicit V5 loader/replay contracts; this
change preserves the stated timing and observer contracts, not incidental parsing.

`TestTraceReplayRowPolicy` is deliberately left unchanged. Its real prefix has
structural events needed by production phase resolution; a synthetic replacement
needs an equivalence witness, not just a copied expected phase. The current
measurement of this unchanged control makes it a clear future candidate.
`TraceData.loadMetadataOnly` would not solve either materialization problem: it
still reads auxiliary events.

### 2. Compile golf probes once, preserve independent execution — implemented

The 27 transport methods all compile the same external networking/model/probe
source set. Move only that compilation to class setup. Every probe keeps a new
null-parent `URLClassLoader`, fresh runtime objects, real sockets and its existing
assertions. The separate protocol test still independently compiles and executes
its external sources. No global bytecode cache, shared writable fixture or
cross-test live classloader is introduced.

This is the bounded first example of a larger opportunity: `ExampleModHarness`
rebuilds packages/resources for many scene tests. A later owned compiled/package
fixture could serve fresh kits, loaders, storage and sessions. Do not share live
scene state or omit the actual validator/isolated SDK boundary.

### 3. Retain only consumed seek-reference snapshots

`TestRewindTraceSeekDeterminism` captures 1,000+ full world snapshots for a case
and reads only a single endpoint. Keep every forward input, controller keyframe,
seek and comparator; retain the full snapshot only at the consumed endpoint.
Before landing, compare the old/new endpoint snapshots and show that omitted
reference captures have no engine side effects. Keep all four independent cases.
This is a stronger candidate than thinning `TestRewindTorture`, which already
captures only scheduled reference frames and deliberately exercises audio.

### 4. Batch parse-only authority analysis with bounded AST lifetime

`TestActiveSegmentPayloadAuthorityGuard` creates per-file javac parsing tasks for
8,424 eligible main/test source files at this base. Parse bounded batches with
explicitly closed file managers and fresh visitor state; retain only violations
and release ASTs. Preserve exact source scope, locations, reflection/MethodHandle
and alias/relay negatives. Avoid one enormous retained AST or an unproved lexical
prefilter. The clock terminology guard is different: it requires type attribution
and override resolution, already isolated in its own child JVM.

### 5. Reuse immutable S2 raw-state input and bound remaining evidence factories

`TestS2RequestAwareOracleRawStream` repeatedly constructs 750 rows carrying 8 KiB
state encoded as hex: **12,288,000 state characters** before JSON overhead and
copies. Reuse a bounded immutable canonical input and construct one mutation at
a time. Retain full rows, rehashed self-consistent corruptions, error precedence
and terminal validation; production buffering is already fixed.

The comparator's old static evidence-map retention is fixed. The neighboring
`TestCompleteRunAudioCaptureStore` and `TestCompleteRunAudioTrace` still have
static identity maps without a release callback. Move evidence to per-case
ownership or eliminate the map; preserve generated records/digests/hostile input
sizes and prove repeated-run cleanup. Their present retained sizes are unmeasured.

### 6. Reuse compatible compiler/bytecode work without weakening scope

Several audio guards import identical production graphs, while active-payload
guards intentionally include tests. Share only genuinely identical immutable
compiled-input/import-option scopes and release graph owners. A longer-lived
shared cache may increase retention, so compare exact class sets and post-plan
memory before adoption. For SDK tests, preserve distinct output/classpaths and
code/data-only variants; compiling everything on one large classpath would hide
missing dependencies.

### 7. Remove unconsumed PNG output, retain real presentation

`TestNativePcmCaptureLive` asserts audio/ownership/cadence over 70, 600 and 260
windows, but `--no-video` still produces PNGs. Add an explicit no-image-output
mode that draws every corresponding presentation using the existing draw-only
seam, drains identical PCM, and keeps state/write ledgers. Preserve all drawing,
GL completion and title/load ordering; keep separate small pixel/video checks.
Compare exact PCM/state/ledger results and prove no readback/PNG calls. Existing
route drawing-gap optimisations are already implemented; MHZ has explicit
replay evidence requiring full drawing and is not a blanket candidate.

### 8. Reduce mock history and fixture setup where assertions do not consume it

`TestFbzPlaneTransition` and `TestLiveRewindPacingFrames` drive long loops through
unverified mocks. Use owned non-recording stubs/fakes where call history is not
part of the assertion. Keep verified interactions recording and retain another
fixture's mocks through cleanup. `TestLrzMinibossInstance` runs a gate before
construction-only checks; separate raw construction from gated behavior while
retaining actual gate/phase-transition tests. Do not write terminal state into
a test whose claim is reaching that state through production behavior.

Closed static `SharedLevel` owners merit lifecycle cleanup, but the historical
selected intervention released only 0.8 MiB from those handles. Their count is
not evidence of a large present leak. Keep class-level immutable load amortization
and the per-test roster/collision/spawner rebinding contract.

### 9. Improve route-matrix structure before shrinking it

One FBZ method contains 105 width/donor/team scenarios plus an isolation row.
Report scenarios independently so an early failure does not hide all later rows;
preserve the deliberate ordered contamination/reset proof separately. This can
increase reporting/setup cost while improving useful coverage. Map real route,
short transition, boss, rewind and load obligations before removing duplicates.
The two known redundant full FBZ traversals were already consolidated.

Share immutable inputs/decoding where identity matches. Reusing a mutable saved
world across fresh sessions risks live Level/Block/Chunk references and inherited
state. A short independent event check is not redundant merely because a long
route eventually traverses the same area. Authored route shortening needs event,
clock, rewind and timeline equivalence, not simply the same final coordinates.

### 10. Separate small contracts from exhaustive breadth and repair weak oracles

Some `slow-suite` classes mix tiny rejection/semantic contracts with huge stress
streams. Moving the small cases into smoke can improve routine confidence; moving
work between profiles is not itself a speedup. Retain anti-materialization child
heap limits, high-entropy/compressed-size floors and full native script pins in
their explicit exhaustive lanes.

Concrete repairs worth doing for value:

- `TestCompleteRunAudioCaptureStore` declares golden canonical/gzip/manifest
  literals that are never asserted; its "pinned digest" checks only a 64-character
  length. Replace with independently verified current-schema literal bytes/digest.
- `AudioFrameClockTest` calls 48,000/60 and 44,100/60 fractional cases, but both are
  integral. Use nondivisible inputs and independently expected carry/peek behavior.
- `TestCollisionLogic`'s fixed array length and unsigned-byte range are not
  collision correctness. Replace with meaningful independent decoding/query cases.
- `TestWFZBoss` has named timer/collision tests that write/read a field or check
  initial zero without entering the phase named. Preserve useful initial constants
  and add actual before/at/after behavior before removing redundancy.
- `TestObjectManagerExecLoopParity` only idles 180 frames without throwing; it
  observes no spawn/unload order or parity. Give it a real witness or map its
  crash contract to an existing stronger test.
- Palette migration guards accept incidental text and some silently ignore missing
  target files. Replace with ownership rules and positive/bypass controls; prose
  presence is not write authority.

These are repair candidates, not an instruction to delete all tests in their
classes. The large old-name cleanup scan is a better cost target than tiny
dependency/prose checks: stream sources and retain violations instead of retaining
the entire approximately 88.8 MB source corpus. Its old permanent cache retention
was already fixed with class teardown.

## Measurement and coverage safeguards

Optimisation acceptance requires the same meaningful observations, not just the
same pass count. Keep exact identities, parameters, failures and skip reasons;
prove helper assertions/negative controls still fire. Preserve ROM/native oracle
provenance, independent sessions/loaders, every required temporal boundary and
ownership cleanup. Self-replay is rewind evidence, not independent ROM parity.

Measure changed slices under the queue; split compilation, execution and waiting.
Executing-thread allocation excludes worker/native allocation and is not peak
heap. Sampled RSS includes heap capacity, JIT/metaspace and native resources;
short peaks can be missed. Requested-GC floors inside JUnit can retain legitimate
PER_CLASS fixtures; compare post-plan floors before asserting a leak. Historical
single-pair timings on Java 21 are not current Java 25 speed predictions.

No global heap/fork/admission changes, tolerance relaxation, ROM-row deletion,
route fixture shortening or full-suite run is part of this delivery. A graphics
prerequisite abort is not passing pixel coverage. A failed route cannot establish
assertions beyond its failure frontier.

## This delivery: measurements and validation

Baseline was clean `e633e4c8259e610e81854fc8acd35172ac592068`.
Candidate was that base plus the four Java test edits; SHA-256 of
`git diff --binary -- src/test` was
`0fc8d1fdd73e69d8730f145d839b69a69c856606c39f00015e60ef39957c6154`
and remained unchanged through profiling and functional validation. Audit prose
changed during the measurement phase without changing executable inputs.

The existing memory profiler ran a fresh diagnostic JVM for each selector group,
three repeated passes within each process, before then after (not interleaved).
Environment: Linux, Java `25.0.5.0.2`, 3 GiB diagnostic heap, 100 ms sampling,
`--gc none` (no test/class-boundary forced GC; the tool still requests post-plan
GC for its run floors), exclusive test admission and build-activity isolation.
Class-container measurements include class setup such as compilation. They
exclude Maven preparation and queue waits.

| Class | Median seconds, before → after | Median allocated MiB, before → after | Allocation reduction |
|---|---:|---:|---:|
| `TestS3kKosTimingRewindIntegration` | 1.322 → 0.025 | 2,957.6 → 22.3 | 99.25% |
| `TestTraceReplayRowPolicy` | 0.793 → 0.867 | 1,781.5 → 1,781.4 | 0.01% |
| `TestLiveTraceComparatorObserver` | 1.752 → 0.038 | 3,727.5 → 7.2 | 99.81% |
| `TestGolfProtocol` | 0.104 → 0.171 | 82.4 → 84.8 | -2.97% |
| `TestGolfTransport` | 23.763 → 22.137 | 8,266.1 → 6,223.3 | 24.71% |

These are **executing-thread cumulative allocations**, not peak RAM or retained
heap. The two narrowed trace loads removed 99.25% and 99.81% of median allocation;
the transport change removed 24.71%. Median class time fell 98.09%, 97.84% and
6.84%, respectively. The unchanged row-policy control took 9.29% longer with
essentially identical allocation. The protocol helper's unchanged compilation
work also varied (0.104 → 0.171 s; allocation rose 2.97%). Three non-interleaved
samples establish the large waste removal but not statistical confidence or a
whole-suite speedup. First-pass startup/JIT differences are visible below.

| Class | Seconds: before passes 1 / 2 / 3 | Seconds: after passes 1 / 2 / 3 | Allocated MiB: before passes 1 / 2 / 3 | Allocated MiB: after passes 1 / 2 / 3 |
|---|---|---|---|---|
| `TestS3kKosTimingRewindIntegration` | 1.792 / 1.322 / 1.288 | 0.300 / 0.025 / 0.023 | 2990.1 / 2957.6 / 2956.2 | 66.4 / 22.3 / 22.3 |
| `TestTraceReplayRowPolicy` | 0.870 / 0.793 / 0.757 | 1.038 / 0.867 / 0.799 | 1773.5 / 1782.2 / 1781.5 | 1777.3 / 1782.3 / 1781.4 |
| `TestLiveTraceComparatorObserver` | 2.480 / 1.752 / 1.640 | 0.659 / 0.038 / 0.032 | 3853.4 / 3727.5 / 3724.2 | 130.2 / 7.2 / 7.0 |
| `TestGolfProtocol` | 0.809 / 0.104 / 0.090 | 0.853 / 0.171 / 0.132 | 153.7 / 82.4 / 82.2 | 153.6 / 84.8 / 84.0 |
| `TestGolfTransport` | 24.430 / 23.763 / 23.751 | 22.201 / 22.137 / 22.106 | 8266.1 / 8276.5 / 8261.3 | 6228.7 / 6223.3 / 6210.6 |

Post-plan final heap floors did not materially fall for the two trace groups:
approximately 9.19 → 9.24 MiB for Kos plus its control and 11.51 → 11.71 MiB for
the observer. Golf's group floor was 20.85 → 14.94 MiB. These sampled/requested-GC
observations do not establish peak-RSS reductions or a leak fix. The selected
transport class still allocates roughly 6.1 GiB per pass: compiling once removes
setup waste but does not make its real networking workload inexpensive.

All **48 distinct test identities** matched before/after, each reporting
`SUCCESSFUL` on all three passes: 144 executions per side, zero skips, aborts,
failures or container failures. This diagnostic launcher is separate from the
133-test Surefire validation below.

Exact profiler commands were the following with `PHASE=before`, then `PHASE=after`
(the actual output names are substituted by the shell):

```bash
python3 tools/testing/profile_test_memory.py --gc none --repeat 3 --max-minutes 10 \
  --output "target/test-audit-${PHASE}.json" \
  com.openggf.game.sonic3k.resources.TestS3kKosTimingRewindIntegration \
  com.openggf.trace.replay.TestTraceReplayRowPolicy
python3 tools/testing/profile_test_memory.py --gc none --repeat 3 --max-minutes 10 \
  --output "target/test-audit-observer-${PHASE}.json" \
  com.openggf.trace.live.TestLiveTraceComparatorObserver
python3 tools/testing/profile_test_memory.py --gc none --repeat 3 --max-minutes 10 \
  --output "target/test-audit-golf-${PHASE}.json" \
  com.openggf.mods.code.TestGolfProtocol com.openggf.mods.code.TestGolfTransport
```

Queue admission waits for those groups were **377 / 324 / 467 seconds before**
and **508 / 535 / 560 seconds after**. Waiting intervals overlap and are not added
as delivery time. No separate end-to-end profiler/process timing is claimed.
The compact reports were inspected for exact identities, status arrays and skips;
raw/compact diagnostic output is temporary, not a committed pass cache.


## Integration and remaining work

The implementation is confined to four test files; production code, selection
policy, heap/fork settings, fixture data and every test method identity are
unchanged. Independent source review found no Java blockers and verified the
strict loader call chain, all 27 transport probe targets, fresh loader isolation,
exception propagation and temporary-directory ownership. It also caught the
initial census omission of the 99 racing main-source files; the final census
includes those roots and distinguishes manual/runtime entrypoints from JUnit.

Focused Surefire validation on the base plus the four-file diff passed **133
tests, zero failures/errors/skips** on Java 25. Maven reported 52.908 seconds;
queue admission reported zero seconds. This is bounded validation, not a
whole-suite or all-fixture replay pass. The four ROM properties were populated
by `run_categories.rom_args`, using identity-verified absolute paths from the
existing main workspace. Equivalent command with that directory expressed
portably (the repository excludes machine-local home paths from prose):

```bash
AUDIT_ROM_DIR="$(git worktree list --porcelain | sed -n '1s/^worktree //p')"
python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestS3kKosTimingRewindIntegration,TestLiveTraceComparatorObserver,TestTraceReplayRowPolicy,TestGolfProtocol,TestGolfTransport,TestHardwareTimingStreamLoader,TestTraceV5LoadingContract,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Dsonic1.rom.path=$AUDIT_ROM_DIR/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$AUDIT_ROM_DIR/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$AUDIT_ROM_DIR/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dkis2.rom.path=$AUDIT_ROM_DIR/kis2.gen" test -B
```

The change-based plan selected all 3,655 ordinary candidates plus guards because
the observer test has no narrower owner mapping. That selection is disproportionate
for these bounded test-only changes: the sole modified helper has two consumers,
and focused validation includes both, the changed timing/observer contracts,
strict timing/V5 loader controls and the four mandatory S3K checks. No shared
runtime algorithm, build contract or selection policy changed. Under the
repository's proportionate-validation rule, the focused checks replace a broad
smoke run; no full-suite run was requested or launched. Actual preflight passed
Java 25, Lua 5.4 and PowerShell with:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base e633e4c8259e610e81854fc8acd35172ac592068 --preflight
```

The remaining ranked redesigns require the revalidation contracts above; they
are not implemented or measured by this delivery. The highest-priority next
experiments are consumed-only rewind references and bounded javac batches,
followed by S2 raw-state fixture construction and audio image-output suppression.
No tests were deleted or moved to a slower lane to obtain these improvements.
