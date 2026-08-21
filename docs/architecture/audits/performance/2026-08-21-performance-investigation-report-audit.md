# Performance investigation report accuracy audit

## Decision

The nine reports in the 2026-08-21 performance investigation are useful
source reconnaissance, but they are not a reliable implementation priority
list. They identify several real code shapes, yet almost every impact ranking
is inferred rather than measured and several proposed transformations would
change observable ordering, lifecycle, failure-isolation, or rendered state.

Treat the reports as a **static candidate survey**. Do not carry their
`HIGH`/`MEDIUM` labels into planning. Promote a candidate only after a
reproducible measurement at a named commit and an accuracy oracle appropriate
to that subsystem.

## Review basis

| Item | Value |
|---|---|
| Review date | 2026-08-21 |
| Reviewed checkout | `develop` at `683b1a3984958b4b6ae53baf383a81bee6727078` |
| Report directory | `$AGENT_SCRATCH_ROOT/tasks/perf-investigation-20260821T122623Z-2247429-42c53748/reports/` |
| Reports | `00-summary.md` through `08-build-test-bench.md` |
| Method | Source inspection, call-path inspection, configuration inspection, and fixture-size verification |
| Runtime profiling | Not performed by this audit |
| Test execution | Not performed; this is a review of claims, not an implementation validation |

Confidence labels used below:

- **Confirmed:** the stated code shape and its frequency/lifetime premise are
  directly supported by the current source or existing recorded measurement.
- **Plausible:** the code shape is real, but its runtime materiality or the
  proposed equivalence is not established.
- **Incorrect:** a factual statement conflicts with the source.
- **Unsafe:** the proposed transformation can change observable behaviour.

## Report-level assessment

| Report | Assessment | Principal correction |
|---|---|---|
| `00-summary.md` | Overconfident | The ranking combines unmeasured hypotheses with unsafe proposals and cannot be used as a roadmap. |
| `01-rendering.md` | Mixed | The AIZ curtain direct-command path is real; broad “no visibility gate anywhere” and the reported implementation count are false, while plan caching omits mutable descriptor state. |
| `02-physics-collision.md` | Mostly sound static analysis | Duplicate ground scans are real, but impact labels are unmeasured and reuse must preserve scan-result values and side effects rather than merely share mutable result objects. |
| `03-object-manager.md` | Mixed | Repeated passes, allocation sites, linear lookup, and dynamic rewind-map retention are real; replacing the processed-identity set or maintaining fallback state naively is unsafe. |
| `04-audio.md` | Mixed with two unsafe recommendations | Logging, modulo, and copy costs are real; deferring sequencer cleanup and mixing the first voice directly into the accumulator violate existing contracts. |
| `05-decompression-loading.md` | Real hotspot shape, inaccurate redesign details | One-byte channel reads are real; `ByteArrayOutputStream.write` is not synchronized, standard Kosinski output size is not known before decode, and KosM module history cannot be reused without a full reset. |
| `06-allocation-gc-sweep.md` | Incomplete | “Fully catalogued” is false; `DefaultSolidExecutionRegistry.finishFrame()` alone creates per-object maps and per-player state records each frame. |
| `07-game-loop-timing-camera.md` | Hypothesis only | The claimed 6–12% core burn was not measured, and trace replay does not exercise the GLFW/vsync pacing loop. |
| `08-build-test-bench.md` | Best-supported report, with two factual errors | Both physics and auxiliary rows are eagerly retained; the default JVM argument disables CDS with `-Xshare:off`, it does not use a CDS archive. |

## Confirmed or well-supported candidates

### Trace-run retention

`TraceRunReplayWalker.plan()` creates a list of `SegmentPlan` records retaining
each segment's `TraceData`. `TraceData.load()` eagerly parses `physics.csv` into
a `List<TraceFrame>` and eagerly parses `aux_state.jsonl` into a frame-indexed
map. The class comment calling auxiliary events “lazy-loaded” conflicts with
the implementation.

The checked-in `pom.xml` records earlier heap measurements and identifies
auxiliary events as the dominant retained data. The Knuckles super-emerald run
contains 67 segments; the recorded uncompressed sizes are approximately
1,476.6 MiB of auxiliary JSONL and 56.1 MiB of physics CSV. Those figures
support prioritising a bounded-memory design. They do not support the report's
claim that physics rows already stream.

The durable design must account for all consumers of segment metadata,
hardware-timing schedules, boundary pairing, dynamic-art ledgers, and indexed
auxiliary queries. “Stream aux events” is a goal, not yet a sufficient design.

### AIZ fire-curtain draw shape

`AizFireCurtainRenderer.render()` emits one
`GraphicsManager.renderPatternWithId()` call per `TileDraw` after
`LevelRenderer` has flushed the sprite batch and before the next normal batch
is opened. This confirms the direct-command shape on the primary AIZ route.

The report's tile and GL-call totals are derived upper bounds, not captured
measurements. A batching change must preserve execute-time shader selection,
palette state, priority, clipping, and command order. The report correctly
noticed the water-shader capture hazard; that hazard makes a naive
`beginPatternBatch()`/`flushPatternBatch()` wrapper unsuitable.

### Duplicate grounded sensor probes

`PlayableSpriteMovement.captureTiltAnglesForGroundDispatch()` scans both
ground sensors before `CollisionSystem.resolveGroundAttachment()` performs its
own terrain probe. This is a real duplicate-work candidate on the ordinary
terrain-attached path.

Any reuse design must latch `SensorResult` values, the scan mode, centre
position, relevant solidity/background-collision inputs, and any
debug-observable sensor state. The sensor result holders are mutable and must
not be shared by reference across the two consumers.

### Dynamic rewind identity retention

`removeActiveObject()` removes the instance from `rewindObjectIds`, while
`removeDynamicObjectInstance()` removes it from live collections without
pruning the identity map. This can retain every removed dynamic object until a
manager reset. It is a credible lifetime leak and a relatively narrow
correctness-preserving candidate, but removal must be tested across capture,
remove, restore, and graph-reference cases before it is declared harmless.

### Small, concrete hot-path candidates

The following source observations are accurate, though their impact remains
unmeasured:

- FM key-on logging eagerly builds a string when `FINE` logging is disabled.
- `SampleBackedVoice` recomputes an invariant 64-bit loop modulo per sample.
- The OpenAL presentation handoff performs an avoidable intermediate copy.
- `FrameCollisionPlan` factories create immutable records that could be
  constants, although escape analysis may already remove the allocations.
- Kosinski, Nemesis, and Enigma channel readers use one-byte `ByteBuffer`
  reads; this is a credible load-time optimisation target.
- `objectIdInSlot(int)` scans active objects rather than using direct slot
  authority.
- `DefaultSolidExecutionRegistry` and solid checkpoint construction allocate
  per object/player per frame.

None should be ranked by source inspection alone.

## Material inaccuracies and unsafe proposals

### Audio lifecycle cleanup cannot be deferred

`SmpsDriver.removeCompletedSequencers()` does more than remove a silent voice.
It releases FM/PSG locks, removes SFX registry entries and claims, and emits a
completion-cleanup lifecycle service event. S&K's SFX-first path deliberately
runs cleanup between SFX and music service so the same VInt's music pass can
reuse the released channel. Hoisting cleanup out of the per-sample/boundary
path can therefore change both service order and audio output.

### Mixer scratch is a failure-isolation boundary

`AudioPresentationMixer` mixes each voice into `voiceScratch` inside a
`try`/`catch`, then commits the completed scratch buffer to `accumulation`.
Allowing the first voice to write directly into `accumulation` means a voice
that writes partially and then throws leaves partial audio behind. The
proposal is unsafe unless the voice API is strengthened to guarantee atomic
completion or the destination can be rolled back without adding equivalent
work elsewhere.

### Object processed identity cannot be inferred from `execOrder`

Objects may release their SST slot while remaining alive. That clears their
`execOrder` entry and moves subsequent execution through a slotless fallback.
The reused processed-identity set is what prevents the same instance from
executing twice in that frame. Replacing it with an `execOrder` membership
check loses that information. Incremental fallback maintenance is likewise an
ordering-sensitive redesign, not a mechanical list-pass removal.

### Fire-curtain plan cache key is incomplete

Sampled composition depends on live background descriptors in addition to
stage, cover height, source coordinates, and wave offsets. Gameplay layout
mutation can change those descriptors without changing the proposed key. List
reuse may be viable; cross-frame result reuse requires an authoritative layout
generation or immutable snapshot owned by the sampling boundary.

### Rendering visibility statement is false

The source contains existing visibility gates, including object renderers that
call `Camera.isOnScreen()`. A direct multiline search of the reviewed tree also
finds 667 `@Override` implementations of `appendRenderCommands`, not the
reported 653. More importantly, a broad object-level cull is not proven safe:
render bounds, vertical wrapping, screen-space emissions, debug paths, and
implementations with presentation side effects need separate treatment.

### Decoder redesign details overclaim equivalence

`ByteArrayOutputStream.write(int)` is not synchronized. A standard Kosinski
stream has no decompressed-size header, so an exactly-sized destination is not
available without a pre-scan or growth strategy. KosM does have a total-size
header, but each standard module begins a fresh history/descriptor state; a
reused decoder must reset that state completely at every module boundary.
Fast match copies must retain forward-overlap semantics.

### Build configuration description is wrong

The shared property is `test.cds.argLine=-Xshare:off`; this disables class data
sharing. The trace profile also overrides `surefire.argLine`, so supplying a
CLI `-Dsurefire.argLine=...` is silently ineffective for profiling. Extra JVM
diagnostics must be threaded through a property the profile actually expands,
such as `test.cds.argLine`, and their presence must be verified in the fork.

### Game-loop estimate has no evidence

The hybrid sleep/spin code and `glfwSwapInterval(1)` both exist. The conclusion
that the spin burns 6–12% of a core “on top of vsync” does not follow without
present-interval and CPU measurements on the interactive loop. Trace-replay
tests do not execute this GLFW/vsync pacing path and cannot validate it.

## Corrected priority

1. **Measure AIZ fire-curtain command count and frame cost.** It is directly
   on the primary release slice and has a clear pixel-equivalence oracle.
2. **Measure and design bounded trace-segment retention.** Existing heap
   evidence establishes a real infrastructure problem, but the ownership
   redesign is larger than the report suggests.
3. **Prove and fix dynamic rewind-map pruning.** This is primarily a lifetime
   correctness issue rather than a frame-time optimisation.
4. **Profile grounded collision and solid-registry allocations.** Promote
   duplicate sensor reuse or allocation work only if JFR shows material cost.
5. **Benchmark decoder paths using real ROM spans.** Preserve byte output,
   consumed length, module reset semantics, and hardware readiness budgets.
6. **Take isolated audio/render micro-wins only when observed.** Logging guards,
   invariant modulo caching, and copy elision are plausible but lower priority.

Defer batch-merging across the whole unified render pass, general visibility
culling, sequencer cleanup deferral, direct-to-accumulator voice mixing,
object-loop fallback redesign, DAC fixed-point conversion, buffer upload
strategy changes, and game-loop pacing changes until each has its own measured
problem statement and behavioural design.

## Evidence required for future performance reports

Every promoted finding should record:

| Field | Requirement |
|---|---|
| Observation | Exact source location and current behaviour |
| Frequency | Calls, allocations, bytes, or draws per representative frame/run |
| Measured cost | CPU, allocation weight, retained heap, GL calls, GPU time, or wall time |
| Measurement identity | Commit, JDK, host constraints, workload, warmups, and raw artifact path |
| Semantic invariant | The state/order/bytes/pixels that must remain identical |
| Verification | Focused tests plus the appropriate trace, audio, render, or decode oracle |
| Confidence | Confirmed, plausible, disproved, or unsafe |

JFR execution samples must not be presented as wall-time shares. Trace-suite
comparisons must prove report completeness, compare failure messages and class
sets in both directions, and record `framesCompared` for greens. A candidate
that lacks these fields remains reconnaissance, regardless of how obvious its
source shape appears.
