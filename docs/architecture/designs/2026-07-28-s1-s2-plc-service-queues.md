# Sonic 1 and Sonic 2 PLC service queues

Date: 2026-07-28

## Summary

Sonic 1 and Sonic 2 both retain Nemesis-compressed Pattern Load Cue (PLC) work
across frames. Their ordinary loops can continue while selected VBlank handlers
resume the queue, and gameplay objects poll the whole queue for readiness.
OpenGGF currently materializes most PLC art synchronously, so those readiness
intervals disappear.

This design first tests whether S1/S2 PLC completion is reproducible from ROM
submissions plus structural interrupt, phase, and lag state. A fixed-budget
logical queue is the preferred implementation, but it is not assumed correct
until it predicts captured ROM readiness edges. Art may remain eagerly
decompressed and registered for rendering. Recorded PLC state is diagnostic
evidence during this proof and does not drive the engine.

The work deliberately excludes:

- the completed S3K Kosinski module queue;
- the in-flight S3K direct Kosinski decompression queue;
- S3K's Nemesis queue, which needs its own consumer inventory and design;
- player, ending, or special-stage animation DPLCs that only select rendered
  tiles and have no queue-busy gameplay consumer;
- incremental host-side Nemesis decoding and VDP transfer emulation; and
- a new `PLC_QUEUE` hardware-timing authority kind.

## Source-of-truth findings

The existing hardware-timing contract gives S1/S2 PLCs a provisional
`NATIVE_SERVICE_QUEUE_PENDING_REVIEW` disposition. It does not prove that a
frame-level logical model is sufficient. The disassembly establishes:

- S1 `RunPLC` prepares the head entry and `ProcessPLC` resumes it. Selected
  VBlank handlers process either three or nine patterns
  (`docs/s1disasm/sonic.asm:775-784,860-870,1376-1515`).
- S2 `RunPLC_RAM` prepares the head entry and `ProcessDPLC` or
  `ProcessDPLC2` resumes six or three patterns
  (`docs/s2disasm/s2.asm:2148-2289`).
- Both games admit ordinary object or mode loops while the queue is pending.
  Queue consumers are catalogued in
  `docs/architecture/audits/2026-07-27-s1-hardware-timing-inventory.md` and
  `docs/architecture/audits/2026-07-27-s2-hardware-timing-inventory.md`.

The name `ProcessDPLC` in S2 is historical. This design concerns the general
PLC buffer, not playable-character dynamic pattern selection.

Several facts remain unproven:

- replay must identify every interrupt handler that actually services PLCs;
- lag VBlanks do not necessarily service the queue;
- water-split paths can defer the three-pattern work from VBlank into HBlank;
- a completed entry requires a later main-loop `RunPLC` preparation before the
  next entry can be serviced;
- completion visibility must be pinned relative to each polling consumer; and
- retail S1 and S2 publish the pattern count before Huffman preparation is
  complete, creating a documented interrupt race
  (`docs/s1disasm/sonic.asm:1392-1413`;
  `docs/s2disasm/s2.asm:2164-2191`).

The fixed tile budget proves progress per completed service invocation. It
does not by itself prove which invocations occurred or that an atomic
preparation model matches retail execution.

## Decision

### Evidence gate

Before committing to the logical implementation, diagnostic ROM captures must
observe:

- every PLC submission, clear, and replacement;
- `RunPLC`/`RunPLC_RAM` preparation begin and end;
- queue head, destination, patterns remaining, and queue-empty state;
- selected VBlank handler and whether service ran at 9/6/3 patterns;
- HBlank-deferred service;
- emulator lag and gameplay-loop admission; and
- the first consumer observation of the empty edge.

Clear and replacement captures include the buffer, `PatternsLeft`, and decoder
progress before and after the call. Retail `RunPLC` stores its advanced source
pointer back into the first buffer longword, while `ClearPLC` zeroes that
longword without clearing the other decoder scalars
(`docs/s1disasm/sonic.asm:1360-1368,1402-1408`;
`docs/s2disasm/s2.asm:2131-2139,2181-2187`). The evidence gate must prove every
covered production clear/replace occurs while no decoder is active. If not,
the native model is rejected until an amended design models the exact aliased
RAM and retail fault behavior; it must not invent a coherent preserved job.

One identical-input replay pair is sufficient to smoke-test recorder
repeatability. The native-model gate instead uses materially distinct
execution instances: individual-level movies, different level completions,
special-stage detours, and complete-run windows that reach equivalent
consumers with different preceding queue and interrupt histories. Instances
within one multi-level BK2 count separately when their submission/service
histories differ. A standalone predictor consumes only ROM PLC definitions
plus recorded structural phase/lag and consumer poll identity/order—not
recorded PLC progress or poll result—and must predict every preparation, pop,
and consumer-visible empty edge across that corpus.

The retail preparation race receives an explicit disposition:

1. prove it is not entered on the covered production routes and preserve it as
   a documented non-goal;
2. model the cycle-sensitive window; or
3. if it creates healthy, repeatable timing variation that structural replay
   cannot predict, stop and amend this design before introducing any recorded
   completion authority.

Diagnostic PLC fields never become replay inputs. Failure of the evidence gate
does not authorize a queue-empty hydration event.

If a required production lifecycle has no movie/save-state route, the report
marks it unavailable rather than manufacturing a duplicate route. Approval
requires all available materially distinct instances to match plus at least
one varied-history comparison for each common consumer family. A unique
consumer such as Final Zone may be single-instance covered when the corpus
contains only one authentic execution. A missing common consumer family with
no varied-history evidence leaves the result `EVIDENCE_INCOMPLETE`.
A compact derived evidence vector may be committed with the research report so
the predictor remains independently rerunnable; it contains structural rows
and observed diagnostic edges, not ROM bytes, gameplay state, or a runtime
fixture.

### Preferred approach after the gate: logical queue state with eager payloads

Each game receives a session-owned PLC timing queue. A request parses the PLC
definition from the user-supplied ROM and records one logical entry per ROM PLC
entry in FIFO order. The existing rendering path may immediately decompress
and register the associated art. Queue service consumes ROM-derived pattern
counts at the exact service point used by that game's active VBlank handler.

The logical queue is the authoritative owner of:

- entry order;
- head preparation state;
- remaining patterns in the active entry;
- destination metadata needed to identify and restore entries;
- queue-busy state;
- clear, replace, and append semantics; and
- rewind capture and restoration.

It is not the authoritative owner of rendered pixels. A queue becoming ready
allows the ROM-modeled consumer to advance; it does not trigger gameplay
mutations itself.

### Deferred fallback: trace-recorded completion edges

Adding a `PLC_QUEUE` timing stream would not remove the need for production
submission, identity, ordering, consumer, and rewind state. It would also make
trace replay more accurate than ordinary play unless production has an
independent scheduler. The approved hardware-timing registry therefore remains
unchanged during the evidence phase.

If the native predictor fails with identical submissions and structural
phase/lag, implementation stops. A separate design review must demonstrate an
independently identifiable, already-submitted, prepared queue job and the
smallest consumer-visible completion boundary before `PLC_QUEUE` can be
considered. Queue-state payloads, per-entry art bytes, and consumer mutations
remain forbidden.

### Rejected: queue-specific trace anchors

Starting a fixture after the queue drains is acceptable only when the fixture
explicitly excludes that lifecycle. Padding input, selecting a route-specific
anchor, suppressing rows, or hydrating queue state from trace data would mask
the missing production behavior and violate the comparison-only replay
contract.

### Rejected: one timing counter per consumer

The current Final Zone counter correctly preserves one known S1 symptom, but
duplicating that pattern for ARZ, results, Game Over, and special-stage
consumers would lose FIFO contention and clear/append semantics. The durable
owner is the game-wide PLC buffer.

## Architecture

### Shared queue kernel

This section is conditional on the evidence gate passing.

`com.openggf.level.resources.NemesisPlcServiceQueue` provides the mechanics
shared by S1 and S2. It has no game, zone, trace, or object knowledge.

Conceptual API:

```java
public final class NemesisPlcServiceQueue {
    public void replaceQueued(PlcDefinition definition,
                              List<Integer> patternCounts);
    public void append(PlcDefinition definition,
                       List<Integer> patternCounts);
    public void clearQueued();
    public void prepareHead();
    public void servicePatterns(int patternBudget);
    public boolean isBusy();
    public Snapshot capture();
    public void restore(Snapshot snapshot);
}
```

`replaceQueued` and `clearQueued` require the Task 1-proven idle-decoder
precondition. Non-idle calls fail rather than silently discard work or invent
a separately preserved job. `append` models `AddPLC`/`LoadPLC`. The queue
rejects a definition/count cardinality mismatch and negative pattern counts.

`prepareHead` models `RunPLC`/`RunPLC_RAM`: when no decoder is active and a
head entry exists, it initializes that entry but consumes no patterns.
`servicePatterns` models `ProcessPLC`/`ProcessDPLC`: it consumes up to the
specified number of patterns from the prepared head. On completion it removes
that entry, leaving the next entry unprepared until a later `prepareHead`.
This preserves the ROM's setup interval between entries.

The kernel stores a logical active decoder entry and queued descriptors only
after Task 1 proves production clear/replace never enters the retail aliasing
hazard. Both retain ROM source and VRAM destination plus remaining/total
pattern counts. It does not store decompressed bytes.

### ROM-derived pattern counts

Pattern counts are computed when a PLC is submitted using the same
user-supplied ROM entry that feeds rendering. `PlcParser.decompressEntryRaw`
provides the exact decompressed byte length; the logical count is
`bytes.length / Pattern.PATTERN_SIZE_IN_ROM`. A non-multiple is a hard
ROM/data error.

The result may be cached per ROM identity and source address, but the cache is
derived data, not queue state. No count is hard-coded in a zone or consumer.
The existing S1 Final Zone constants are removed once equivalence tests prove
the general queue produces the same timing.

### Game-owned façades

The shared kernel deliberately does not decide service cadence or call order.

`Sonic1PlcService` owns:

- parsing S1 PLC IDs from `Sonic1Constants.ART_LOAD_CUES_ADDR`;
- mapping `NewPLC`, `AddPLC`, and `ClearPLC` call sites to kernel operations;
- calling `prepareHead` at S1 `RunPLC` sites;
- choosing three- or nine-pattern service from the admitted S1 VBlank
  lifecycle; and
- exposing only `isBusy()` to S1 consumers.

`Sonic2PlcService` owns the corresponding S2 table and:

- `LoadPLC` append, `LoadPLC2`/replacement, and clear semantics verified
  against the disassembly;
- `RunPLC_RAM` head preparation;
- six-pattern `ProcessDPLC` and three-pattern `ProcessDPLC2` service; and
- `isBusy()` for S2 consumers.

The exact association between VBlank routine and budget remains in the
game-owned façade or lifecycle provider. Shared runtime code does not branch
on game identity.

### Session ownership and frame ordering

Each service belongs to the active game session and is reachable through that
game's module/service graph. Objects receive it through existing injected
services; they never call `getInstance()`.

Queue processing must preserve ROM visibility:

1. the selected VBlank service consumes the current prepared head;
2. the admitted main loop begins;
3. `RunPLC`/`RunPLC_RAM` prepares a queued head when the containing ROM loop
   does so;
4. events and objects observe `isBusy()` at their disassembly-defined point;
5. an object/event PLC operation mutates logical state synchronously at that
   exact producer call site; and
6. later object slots in the same scan observe the mutation, while earlier
   slots are not retroactively blocked.

There is no unconditional shared-frame service call. Title-card, fade,
special-stage, results, credits, and ordinary level handlers use different
budgets and call sites. The game lifecycle owner selects the service operation
from semantic phase state.

Lag-only and HBlank-deferred rows receive explicit semantic operations. A
generic `VINT_SERVICE` callback is insufficient unless the evidence gate proves
that it faithfully represents the selected handler and deferred work.

For the ordinary level loop, the existing `LevelFrameStep` may expose a
game-provided VBlank PLC service hook, but it consumes an interface such as
`PlcVBlankService.serviceLevelVBlank()` rather than testing `GameId`.

### Rendering integration

Logical submission and art materialization are separate effects of one
game-owned request:

```text
ROM PLC request
  ├─ parse entries and derive pattern counts
  ├─ append/replace logical queue
  └─ eagerly materialize/register render art
```

Rendering remains immediately safe in OpenGGF. Gameplay consumers must use
logical `isBusy()`, never renderer availability, sheet registration, atlas
state, or host decompression completion.

Repeated requests still affect logical FIFO state even when the renderer
already has the sheet. Existing "already registered" render deduplication must
not suppress a ROM queue submission.

### Rewind

The snapshot contains:

- active decoder state plus queued descriptors in order;
- source address and destination tile for each entry;
- total and remaining pattern counts;
- whether the head is prepared; and
- any façade lifecycle state needed to reproduce the next service call.

Restore reconstructs logical state without parsing the trace, resubmitting
work, changing queue order, or registering art a second time. Each game façade
is the sole rewind registration owner for its kernel and lifecycle state; the
kernel is not independently registered a second time. Guard tests cover new
final scalars, collections, and static/session ownership.

## Consumer migration

### Sonic 1

The first delivery migrates:

- level title-card readiness;
- Final Zone boss startup;
- level-results card;
- Game Over card;
- special-stage results/emerald object; and
- the surrounding special-stage results loop where represented by the engine.

Credits and level-select callers are connected when their engine lifecycle
owner exists. Unsupported/dormant objects are documented rather than given a
fake owner.

The Final Zone migration replaces `Sonic1FzPlcTimingQueue`. The boss continues
to increment RNG on every busy frame; it simply polls the shared S1 service.

### Sonic 2

The first delivery migrates:

- level title-card readiness;
- ARZ boss initialization;
- level-results card;
- Game/Time Over object;
- special-stage results object and results loop; and
- two-player results where the lifecycle exists.

All runtime PLC producers in implemented level events and bosses must submit
to the service even if their art was eagerly loaded earlier. The initial
inventory includes WFZ, OOZ, MTZ, ARZ, DEZ, signpost/results, animals,
capsule, explosions, and boss PLC requests. Missing producer behavior is
implemented from the disassembly rather than inferred from a poll symptom.

ARZ is a consumer, not the owner: it polls the game-wide queue and contains no
ARZ-specific timing constant.

## Error handling

- Invalid PLC IDs fail at the game façade boundary with the game and PLC ID.
- A parsed definition/count mismatch is an invariant failure.
- A decompressed length that is not a whole number of patterns fails before
  queue mutation.
- Queue overflow follows verified ROM capacity behavior. Until that behavior
  is pinned for both games, implementation must fail closed rather than drop,
  reorder, or overwrite an entry silently.
- Restoring an invalid snapshot fails with the offending entry and field.
- Rendering failure and logical queue mutation are transactional:
  validate/decompress/materialize all ROM-derived payloads without publishing
  either effect, then commit the logical mutation and a non-throwing prepared
  renderer registration. If registration can still fail, roll back before
  exposing the request. A partial logical submission is forbidden.

## Testing strategy

### Queue kernel

Unit tests prove:

- append, queued-descriptor replacement, and queued-descriptor clear;
- rejection of clear/replace while a decoder is active under the
  evidence-gated idle precondition;
- preparation consumes no patterns;
- three-, six-, and nine-pattern budgets;
- entry completion and the setup interval before the next entry;
- whole-buffer busy semantics;
- repeated identical submissions remain distinct FIFO work;
- invalid pattern counts do not mutate the queue; and
- snapshot round trips at unprepared, partial, entry-boundary, and empty
  states.

### Diagnostic prediction

Before production queue code, recorder tests and capture analysis prove:

- one identical-input smoke pair proves recorder stability;
- materially distinct executions exercise different submission histories,
  service exposure, and consumer latencies;
- structural phase/lag plus ROM-derived pattern counts predicts every
  execution independently;
- HBlank deferral preserves the observed same-frame or next-frame visibility;
- lag rows either service or skip work exactly as the selected ROM handler
  does; and
- no covered route enters the partial-preparation interrupt window, unless a
  reviewed retail-race model is included.

The predictor must fail when one service call, handler selection, preparation
bubble, or lag classification is deliberately shifted.

### ROM-backed parity

With discovered S1/S2 ROM paths:

- parse representative standard, zone, boss, results, and Game Over PLCs;
- derive all pattern counts from ROM bytes;
- compare calculated drain frames with an independent test oracle based on
  the disassembly service algorithm; and
- prove S1 Final Zone timing matches or corrects the existing narrow model
  before removing it.

### Lifecycle and consumers

Focused integration tests prove exact observation order:

- completion in VBlank is visible to the following object scan;
- a head prepared in the main loop is not serviced retroactively;
- S1 Final Zone RNG advances once per busy boss frame;
- S2 ARZ initialization remains blocked by unrelated earlier FIFO entries;
- an earlier-slot producer makes the queue busy for a later-slot consumer in
  the same scan, while the inverse order does not change the earlier result;
- clear/replace changes queued descriptors immediately while idle and rejects
  a non-idle invocation;
- renderer deduplication does not suppress logical resubmission;
- clear/replace operations change consumers on the correct frame; and
- rewind across completion reproduces the same readiness edge.

### Trace policy and regression

Source guards continue to forbid PLC authority from physics or aux trace data.
No `PLC_QUEUE` hardware timing fixture is added. Existing short traces may
remain scoped before unimplemented lifecycle paths, but complete-run tests
must not suppress queue-active rows.

Focused S1 and S2 trace replays covering Final Zone and ARZ are run when the
required ROM fixtures exist. The full Maven suite then verifies no S3K Kos
module or in-flight Kos decompression behavior was changed.

## Delivery boundaries

This design should be implemented after the S3K direct Kos decompression
worktree lands or rebased onto its integrated result. The implementation may
reuse generic session/rewind conventions introduced there, but must not
generalize or edit its S3K queue semantics as part of this scope.

The evidence phase is complete only when the available varied-history ROM
corpus and the structural predictor establish whether the native model is
sufficient. If it
passes, the implementation is complete when S1/S2 queue-busy gameplay behavior
derives from ROM-backed logical queues in ordinary play and trace replay, all
implemented producers/consumers use the game-owned services, the narrow S1
Final Zone counter is retired, and no trace-derived completion driver exists.
If it fails, the design and plan must be amended and re-reviewed before
production implementation continues.
