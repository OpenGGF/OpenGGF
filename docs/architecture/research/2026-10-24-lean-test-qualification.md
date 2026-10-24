# Bounded lean-lane qualification — 2026-10-24

Base: `0b2c3c2a9127b080137b231ba7e7cfd9e3b1e164` (`develop`).
Task: qualify a bounded set of additional tests for the existing 4 GiB / 4-core
queue lane, following the [test value and cost audit](../audits/testing/2026-10-23-test-value-cost.md).
The first pass is recorded below; the subsequent dashboard and qualification
pass is recorded at the end of this document.

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

## Second pass: dashboard visibility and further qualification

Base: `d3deeb692bb61745148361266796effa1ce9a3c0`, the delivered first pass.
The user requested an updated dashboard and another bounded pass, then asked
for a tooling notice to other active agents.

The live dashboard had loaded revision 2 while the main workspace contained
revision 3. The user stopped its owning thread during this work. The updated
view reports its own loaded revision, configured standard budget, lean budget,
registry size and 50-class command limit. Waiting rows now show reported RAM/CPU
reservations, preserving custom fractional CPU values and unknown legacy data.
Jobs advertising a newer revision produce a dashboard-restart notice. None of
these display fields changes queue admission or authorizes a job restart.

The tooling notice was delivered to the three other active OpenGGF lead threads,
asking them to use the current main-workspace wrapper by absolute path for future
commands and forward the notice to active workers. Existing queued/running jobs
retain their requests, selections and loaded code.

The second shortlist contains 20 previously unregistered classes, with 183
JUnit declaration sites. The candidate reviewer stopped its final artifact check
when lead-owned documentation changed. The lead reconciled those changes,
verified that Java/POM/fixtures and eligibility rules still matched the base,
and checked all selector shapes, declaration counts, JSON/Markdown consistency
and evidence paths/line bounds. This did not require source or guard exceptions.

| Candidate | Declarations | Bounded fixture |
|---|---:|---|
| `com.openggf.net.protocol.TestGhostPackets` | 14 | 500 seeded samples of 1–30 seven-byte frames (at most 210 frame bytes); at most 19 aggregate entries; largest explicit hostile array is 4,097 bytes. Helpers create arrays in memory only. |
| `com.openggf.net.host.jdk.TestWebSocketFrames` | 4 | Four encoding payload lengths: 125, 126, 65,535 and 65,536 bytes. Reader uses an 11-byte in-memory masked frame with caps 16 and 4; close reason is 200 copies of é. No server or socket. |
| `com.openggf.game.rewind.TestRewindRegistry` | 16 | Per case, at most two active scalar adapters and one callback; tiny maps, integer snapshots and event lists. Anonymous helpers implement interfaces and carry no inherited JUnit fixtures. Default registry has no profiler. |
| `com.openggf.data.compression.TestKosinskiWindowBoundaries` | 2 | Two streams; fixed 10,000-byte builder; expected outputs 258 and 8,195 bytes. Resumable steps process 31 descriptors. Decoder starts with 4 KiB output, grows to 16 KiB for the larger case, and has a 1 MiB production output ceiling. |
| `com.openggf.data.compression.TestResumableKosinskiDecoder` | 6 | Canonical input is 8 bytes producing ABC; largest input is a 23-byte refill vector producing 16 bytes. Completion loops assert fewer than 4,096 steps. Decoder/snapshot copies start with 4 KiB output buffers. |
| `com.openggf.mods.state.TestModSaveSlot` | 18 | Mostly two-entry in-memory maps and v1/v2 integer text. Failure matrix is two destinations × two failure modes. Real temporary-file cases include a 1,048,577-byte oversized backup/text input and a two-byte malformed UTF-8 input; no workers. |
| `com.openggf.net.client.TestFrameAssembler` | 6 | Maximum explicit text is 65,537 ASCII characters; binary fragment is 2,049 bytes against a 4,096-byte cap. Custom-cap cases use 4/8 bytes. Exactly one nine-byte direct ByteBuffer branch, with no native library/device or socket. |
| `com.openggf.net.hub.TestGhostStreamValidator` | 13 | Injected integer clock; one or two validators per case; packets carry at most three seven-byte frames. Longest bounded loop permits 200 batches, stops on first rate violation; ordinary stream is 20 batches. Production retains one previous frame and a streaming SHA-256 digest. |
| `com.openggf.net.hub.TestRelevanceClassifier` | 8 | Seeded 120 rounds × at most 64 slots. Each query sorts at most 63 peers; retained history is at most 64 sets of eight peers. Directed cases use at most 13 players. Production scratch arrays each contain eight ints. |
| `com.openggf.game.rewind.TestRewindController` | 21 | FakeInputSource stores only a count (maximum 300), synthesizing tiny input records on demand. Largest history scenario records 180 scalar frames, rewinds 130 positions and runs short branch/boundary loops; intervals at most 60. Fade case advances 40 ticks and limits backward iterations to 100. |
| `com.openggf.game.rewind.schema.TestRewindStateBuffer` | 6 | Largest write is 28 primitive bytes in the default 64-byte backing array. Other arrays are two or four bytes. No managers, reflection schemas, files or property expansion. |
| `com.openggf.game.rewind.TestCompositeSnapshotOwnership` | 3 | One- or two-entry maps and a single-element Object array; standalone layout metadata only. Source and exposed view mutations are the discriminating controls. |
| `com.openggf.game.TestGameRngGoldenSequence` | 6 | One GameRng per vector; scalar long/int operations, one generation step per arithmetic case, no arrays or game boot. |
| `com.openggf.sprites.playable.TestObjectControlState` | 10 | One TestablePlayableSprite per case, two in the preserve-CPU case. Shared helper overrides drawing/sensor creation; superclass allocates six fixed 64-entry history arrays and a controller. No frames, level load or art loading. |
| `com.openggf.physics.TestObjectTerrainUtils` | 9 | At most one LevelManager mock, two SolidTiles and two chunk descriptors per case; each tile holds two 16-byte arrays. A handful of stubs and one reflected production probe; no level boot or simulation loop. |
| `com.openggf.mods.code.TestModObjectKeyRegistry` | 8 | At most two factories/owner patches, tiny registration maps, one interface proxy and record stub objects. Preview registration stores one BakedSheetRef path only. In-memory asset root has no assets; empty ROM-art/zone maps prevent materialization. |
| `com.openggf.net.protocol.TestPhase3Protocol` | 6 | 19 small control-message samples; roster encodes 1,283 bytes. Relay rejection input is 4,094 bytes. Tunneled string contains 65,336 quote characters, with escaped JSON bounded by the 132,096-byte master cap. No sockets or workers. |
| `com.openggf.io.TestModAssetRoot` | 25 | Temporary trees/JARs have at most three entries; payloads are generally 0–5 bytes, with one 8,192-byte replacement vector. Lowered caps exercise failure boundaries without allocating production maxima. One case submits exactly three three-byte reads to three workers; helpers stream through 8 KiB buffers. |
| `com.openggf.io.TestModAssetRootConcurrentBudget` | 1 | Exactly three fixed-pool tasks, per-asset cap three bytes, total validation cap six bytes; the failing InputStream emits two-byte chunks. Production read buffer is 8 KiB. Latch/future waits are five seconds plus one 250 ms coordination wait; executor/root are try-with-resources. |
| `com.openggf.physics.TestFbzCalcRoomInFrontBackgroundCollision` | 1 | Manually invokes only the other fixture's setUp/tearDown: two mocks, one 16-byte full-height tile array, one minimal playable sprite and one explicit background state. One reflected scan, no FBZ route, ROM load, rendering or inherited test execution. |

Deferred: `TestProtocolFuzzing` (bulk parser/exception churn), `TestModStorage`
(filesystem churn and creator-loader lifetime), and `TestSegmentCache`
(allocation measurement within the class). `TestGolfTransport` remains deferred.
These are scope/value decisions, not observed lean OOMs.

The recurring procedure is now `tools/testing/profile_small_tests.py`, using
existing queue/activity/cleanup helpers. Lead review added the Maven-added
`racing`/`examples` roots to its source fingerprint, cleanup of descendants after
an already-exited Maven parent, and rejection of unrequested test classes. Its
21 focused Python controls passed after the independent review fixes below. Unlike the first-pass diagnostics, it uses no
GC flags or JVM instrumentation. This pass compares exact outcomes and memory
headroom; the cold-lean/warm-normal timings are not a matched speed comparison.

The normal and lean runs each completed 183 cases across all 20 classes, with
zero failures, errors or skips. Their full `(class, name, status, count)`
multisets were identical. Both ran at `d3deeb692bb6` with unchanged input hash
`352cd0f4b435bbf0cc12323602c8fa2ec4ee3279cf84f8720a35d4bf02a96211`.
The lead kept the included source/tooling inputs frozen across both runs.

| Diagnostic | Compilation | Queue wait | Build drain | Execution | Sampled peak RSS + swap | Peak swap | Samples |
|---|---|---:|---:|---:|---:|---:|---:|
| lean | cold | 449.75s | 0.002s | 78.32s | 1.753 GiB | 0.000 MiB | 708 |
| normal | warm | 665.48s | 0.218s | 27.01s | 2.995 GiB | 0.000 MiB | 243 |

Own-worktree acquisition took under 2 ms in each run. The sampled lean JVM
arguments were `-Xmx1g` for both Maven and Surefire; normal Maven had no explicit
`-Xmx`, and normal Surefire had `-Xmx3g`. These cold/warm results establish outcome
equivalence and observed headroom, not a matched speed or memory reduction.

Independent profiler review found two evidence gaps after these runs: the
queue's best-effort process enumerator could suppress observation errors, and
fingerprinting only after admission could misidentify tooling edited during a
wait. The delivered diagnostic instead walks owned Linux task children with
explicit read-error handling and rejects input drift from a pre-wait hash before
cold cleanup/Maven execution. New controls cover failed inventory/owned-stat
reads, worker-thread children, exited/reused PIDs and edits during admission.
The earlier measurements have the former sampler's error-detection limitation;
the final cold automatic-lane check below uses the hardened sampler, includes all
20 candidates, and is the stronger admission-headroom evidence.

While qualification was running, develop advanced to `85d32cda4fe3`, including
architecture fixes and the dashboard redesign (`df1476c99`). The task tree
fast-forwarded after the paired runs. The incoming layout already rendered
per-job reservations and the new optional policy fields, so it replaced this
pass's initial HTML changes. Only fractional standard-memory display needed a
local correction. Backend fields and advisory revision warnings remain additive.
The final automatic run is against the reconciled tree, with no task-authored
Java, POM, fixture, scheduling-function or selection-policy edits.

All 20 candidates are accepted, bringing the registry from 70 to **90 classes**
(35 additions across both passes). The final ordinary-wrapper command selected
all 20 new classes plus the lexicographically first 30 pre-existing registry
classes. It passed **379 cases in 50 classes, zero failures/errors/skips**. The
new candidates' complete 183-case multiset still exactly matches both diagnostic
lanes after upstream reconciliation.

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B \
  "-Dtest=$automatic_classes" \
  "-Dopenggf.surefire.reports=$owned_temporary_directory/reports" test
```

The task-owned harness cleared only generated class/compiler outputs while
holding its worktree lease, then released that lease for the ordinary wrapper's
own ownership/admission. It reused the delivered strict sampler, retained a
ten-minute post-admission watchdog, and removed temporary XML/logs after outcome
extraction. There was no explicit `--lean`, custom JVM flag or performance
isolation in this final shared-lane check. The wrapper automatically chose
resource-aware admission at **4 GiB / 4 cores**; both observed JVMs used `-Xmx1g`.
It overlapped another admitted job.

At reconciled base `85d32cda4fe3` plus this task's executable edits, input hash
`43978e98b49bd14d5fa819d43bedec89d026bd90c255a2bac9ac3b0f7c1262f5` matched before and after.
Queue wait was 234.07s; post-admission wall time was
85.21s (including final observation/summary cleanup),
and total command observation was 319.28s. Across 3,170 samples,
peak simultaneous RSS plus swap was **2.092 GiB**,
peak RSS 2.080 GiB and peak swap
14.293 MiB. The swap belonged to the Python wrapper; neither
observed JVM showed swap. Separate peaks are not additive. There were no sampler
errors or missing JVM roles. This is sampled headroom for the selected cold run,
not a hard memory bound or a general reservation reduction for heavy jobs.


Dashboard validation: 30 focused Python tests passed before reconciliation. The
reconciled page also passed headless browser fixtures
at 1280 px and 390 px checked standard/lean/custom/unknown reservations, dynamic
registry counts, filtering, empty-row column span and absence of page overflow.
The collaborative browser snapshot operation twice timed out after 15 seconds;
headless rendering and direct API inspection supplied the UI checks instead.
The reconciled fixtures additionally checked fractional standard memory and
missing optional API fields; their console assertions passed at both widths.

Validation commands (from `ai-lean-dashboard-pass2`):

```bash
# The exact diagnostic selector is the sorted set of all 20 table FQCNs above.
python3 tools/testing/profile_small_tests.py --lane lean --test "$candidate_classes" --cold-compile --max-minutes 10 --output target/lean-pass2-cold.json
python3 tools/testing/profile_small_tests.py --lane normal --test "$candidate_classes" --max-minutes 10 --output target/lean-pass2-normal.json
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/testing -p test_run_category_small_profile.py -v
PYTHONDONTWRITEBYTECODE=1 LUA_BIN=/usr/bin/lua5.4 python3 -m unittest discover -v -s tools/testing -p 'test_run_categor*.py'
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base d3deeb692bb61745148361266796effa1ce9a3c0 --preflight
python3 tools/testing/run_categories.py --base d3deeb692bb61745148361266796effa1ce9a3c0
```

The complete Python safety selection passed **274 tests, zero skips**, including
30 dashboard controls and 21 profiler controls. Preflight verified Java 25,
Lua 5.4 and PowerShell in the actual launch environment. The reconciled
change-based plan selected 3,661 smoke candidate classes plus guards. The lead
used proportionate focused validation for this task's registry/diagnostic/display
changes: the real automatic command, paired outcomes, Python safety suite and UI
checks exercise their consumers and failure modes directly. No broad smoke or
release-only full-suite result is claimed; incoming architecture changes retain
their separate delivery evidence.

Static checks confirm all 90 registered classes satisfy the existing source
checks, 51-class requests remain ineligible, eligibility code is unchanged, and
`maven_schedule.py` differs only in the advisory revision literal (3 to 4).
Normal reservations stay 7 GiB / 8 cores, lean stays 4 GiB / 4 cores, the
50-class limit and source/invocation guards stay unchanged. New local links,
instruction mirrors and `git diff --check` passed.

The previously restarted dashboard instance at port 8766 had loaded revision 3
without the new registry metadata. Deployment restarts only that dashboard from
integrated main and checks revision 4, 90 registered classes, the 4 GiB / 4-core
lean reservation, the unchanged 50-class limit and the served HTML. Existing
queued/running Maven requests keep their original processes and loaded tooling.
Consumed diagnostic reports, raw outputs and task-owned transient harnesses are
removed; candidate and review conclusions remain in this durable research record.
