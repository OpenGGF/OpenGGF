# Cross-game hardware-timing trace contract

## Status

Proposed design under independent review. The symptom-first direction and the
narrow authoritative hardware-completion exception are user-approved. This
document inventories timing-sensitive Mega Drive activities used by Sonic 1,
Sonic 2, and Sonic 3 & Knuckles and defines the minimum authority a dedicated
hardware-timing input stream may have over them.

The governing principle is:

> Record the smallest scheduling outcome observable to the game, not the
> hardware cause that produced it.

Most expensive hardware work therefore does not need its own trace event. If
its only gameplay-visible consequence is that the 68K main loop missed a
frame, the existing lag-row contract is sufficient. A new completion event is
reserved for work that remains pending while the main loop continues and the
ROM explicitly polls a hardware-owned readiness gate.

## Goals

- Reproduce hardware-dependent scheduling without copying gameplay state from
  the trace.
- Reuse the established lag-frame model wherever it completely describes the
  observable result.
- Keep replay independent of host decompression, rendering, I/O, and CPU
  speed.
- Preserve strict comparison of gameplay state, including ring count, after
  the relevant scheduling outcome is reproduced.
- Fail loudly when the engine and trace disagree about which hardware work
  exists.

## Non-goals

- Cycle-accurate 68K, Z80, VDP, or DMA emulation.
- Recording compressed byte progress, DMA byte counts, VDP FIFO occupancy, or
  host execution duration. A stable submission fingerprint is comparison
  evidence for independently submitted work; it is not a trace-supplied work
  descriptor.
- Allowing a trace to set rings, positions, routines, object slots, event
  flags, or any other gameplay state.
- Adding zone-, route-, trace-, or frame-specific scheduling branches.
- Making visual-only timing release-blocking in physics traces.

## The five replay contracts

Every timing-sensitive activity must reduce to one of these contracts.

### 1. Main-loop admission

The existing trace `lag` outcome says whether the gameplay main loop ran.
When it did not run, replay services the ROM-equivalent interrupt work,
retains/re-samples controls according to the game's lag policy, and does not
run gameplay.

The cause of the missed frame is deliberately absent. Decompression, map
construction, DMA setup, Z80 bus arbitration, or any other long 68K task all
produce the same replay outcome when their only observable consequence is a
lag frame.

### 2. Execution phase

Some physical frames use a non-level VInt/main-loop regime: fade, lag,
special-stage, controller/DMA, title or another structural phase. Replay
models the phase's observable scheduling semantics, not the low-level work
that selected it.

Phase evidence must be structural. It may come from mode and lifecycle state
already recorded for comparison, but must not be inferred from a fixture
name, route, position, animation, or a convenient row shape.

### 3. External work completion

This is the sole new authority proposed by this design. It applies when:

1. production code has submitted real ROM-backed work;
2. the ROM exposes a readiness value polled by ordinary main-loop code;
3. the main loop can continue while that value remains pending;
4. completion timing depends on hardware work not represented by `lag` or the
   execution phase; and
5. readiness can affect a gameplay-visible lifecycle.

The dedicated timing stream may release a matching pending job. Physics CSV
and auxiliary events remain comparison-only and have no access to this port.
The timing stream may not perform the consumer's response.

### 4. Hardware-relative initial base

Persistent values such as `V_int_run_count` may depend on power-on history
that a segment trace does not replay. The trace may seed such a value once at
a structural segment boundary. The engine then advances it natively.

This is initial-state reconstruction, not recurring synchronization.

### 5. Diagnostic-only presentation timing

Raster effects, palette publication, sprite dropout, audio presentation, and
other visual/audio-only results remain diagnostic unless the ROM exposes a
RAM readiness gate that blocks or branches gameplay. A physics trace does not
gain authority merely because the presentation differs.

## Cross-game inventory

The table records the symptom that matters to replay. “Lag” means no
cause-specific event should be added. “Phase” means existing or strengthened
structural scheduling. “Completion candidate” means the mechanism must pass
the eligibility gate above before receiving trace authority.

| Activity | Sonic 1 | Sonic 2 | Sonic 3 & Knuckles | Replay contract | Current disposition |
|---|---|---|---|---|---|
| Long synchronous decompression or level initialization | Physical VInts occur while the main loop is unavailable | Explicitly visible in special-stage and level-start lag rows | Present during black-screen level loads and other initialization | Lag | S1/S2 substantially covered; audit S3K lag capture parity |
| Normal PLC processing | `RunPLC` services a persistent queue while ordinary loops and some objects poll it | Normal/fade/special handlers service a persistent queue; ordinary gameplay can poll it | PLC/AniPLC coexist with later Kos queues | Native deterministic service queue; external completion candidate only if lag/phase is insufficient | Do not record individual PLC entries; audit service cadence and polled gates |
| Direct Kosinski decompression queue | Not used as the S3K-style owner | Not used as the S3K-style owner | `Kos_decomp_queue_count` independently gates AIZ intro and ICZ act-transition progression | Native deterministic service queue and reviewed completion candidate | Separate from the module queue |
| Kosinski/KosinskiM module queue | Not used as the S3K-style owner | Not used as the S3K-style owner | Resumable module queue remains pending while results/title/event code continues polling | External completion | `KOS_MODULE_QUEUE` is the first approved authoritative kind |
| Nemesis, Enigma, Saxman, raw map decompression | Normally synchronous from gameplay's point of view | Normally synchronous; special-stage work produces lag rows | Normally synchronous unless wrapped in an explicit deferred queue | Lag | No codec-specific trace authority |
| VDP DMA transfer and FIFO pressure | Can consume VInt budget or contribute to lag | Can consume VInt budget; controller/DMA VInt is structurally distinct | Queued art, palette, tile and plane transfers may have completion flags | Lag, phase, or completion candidate | Record a completion only when the ROM polls a gameplay-visible fence |
| Foreground/background plane drawing | Usually initialization/presentation | Special-stage name-table and draw pipeline has structural waits | Some background-event routines wait for a draw/refresh result | Phase or completion candidate | Inventory each polled RAM fence; presentation alone is diagnostic |
| Animated tile/DPLC uploads | VInt/frame-counter driven | VInt/frame-counter driven | AniPLC plus custom DMA updaters | Native deterministic counter; diagnostic presentation | No completion authority unless gameplay polls readiness |
| Palette fades | Fixed VInt loops | Fixed VInt loops with handler-specific PLC service | Fixed VInt loops around transitions | Phase | Model the loop; do not synchronize the final palette value |
| Palette cycling | Counter/table driven | Counter/table driven | Counter/table/event-flag driven | Native deterministic counter | Visual comparison only unless a gameplay routine reads the same flag |
| Controller sampling | Physical sample and logical word publication depend on VInt/main-loop admission | Lag and special-stage paths distinguish sampled and consumed controls | Same class of physical/logical publication | Lag and phase | BK2 supplies buttons; scheduling supplies when they become logical input |
| Persistent VInt counters and parity bytes | Object, animation, sound-gate and demo cadence | Object, animation and special-stage cadence | Object cadence, bonus-stage entropy and ongoing Slots reads | Initial base plus native advancement | Existing metadata/base work is the precedent |
| Software RNG seeded from hardware-relative time | Seed/history may depend on VInt history | Seed/history may depend on prior session work | Gumball and Slots explicitly consume VInt-derived state | Initial base plus native advancement | Never synchronize individual RNG calls |
| Ordinary object scheduling | Deterministic SST scan | Deterministic SST scan | Deterministic SST scan | Native gameplay | Hardware timing has no authority once main-loop admission is known |
| Special-stage draw/update pipelines | Rotation/object work follows special-stage VInts | Drawing index, duration wait, controller/DMA wait and fades are explicitly phased | Special-stage/bonus-stage modes have their own VInt-derived counters | Lag and phase | S2 is the strongest existing model; audit S1/S3K against it |
| H-Interrupt/raster effects | Water/scroll presentation | Water splits and per-line effects | Water splits, window-plane and per-line deformation | Diagnostic presentation | No physics synchronization unless a RAM gate changes gameplay progression |
| Sprite table upload and hardware sprite limits | Presentation/dropout | Presentation/dropout | Presentation/dropout | Diagnostic presentation | Object existence must not be inferred from rendered sprite presence |
| Region/refresh rate | PAL/NTSC changes physical frame cadence | PAL/NTSC changes physical frame cadence | PAL/NTSC changes physical frame cadence | Session configuration | Deterministic from ROM/region; no recurring trace event |
| Z80/SMPS driver ticks | Audio continues independently across some 68K work | Audio plus bus-request/driver-load work | Audio continues independently across some 68K work | Separate audio clock; lag if 68K is stalled | No physics-state authority from audio completion |
| Z80 bus requests or driver loading | May consume 68K time | Runtime driver/data handling can consume 68K time | May consume 68K time | Lag | Record the missed main-loop frame, not the bus transaction |
| VDP status/busy polling | May extend a synchronous operation | May extend a synchronous operation | May extend a synchronous operation or back a polled fence | Lag or completion candidate | Completion requires an explicit ROM-visible readiness owner |
| SRAM/save/peripheral waits | Outside active trace gameplay | Outside active trace gameplay | Outside active trace gameplay | Excluded unless evidence appears | Explicitly ruled out for current trace scope |

## S1/S2 lag-frame coverage audit

S1 and S2 should not gain new completion events merely because their ROMs use
decompression or DMA. Lag is sufficient only when all of the following hold:

- raw capture includes every physical emulator frame;
- the lag flag distinguishes a serviced interrupt from an executed gameplay
  loop;
- replay advances the game's required VInt-owned counters and queues on that
  row;
- input sampling/reuse follows the game's lag path; and
- no ordinary main-loop routine polls a still-pending hardware readiness value
  across multiple non-lag rows.

The S2 special-stage initialization timeline is the reference example for
synchronous initialization:
decompression and PLC work span physical VInts, but the trace needs only lag
rows plus structural fade, special-stage, and controller/DMA phases. It does
not need one event per decompressor or DMA operation.

Normal S1/S2 PLC queues are an explicit exception to the assumption that all
loading collapses into lag: ordinary loops can continue while a PLC remains
pending. They are initially classified as native deterministic service queues,
not as automatically authoritative trace inputs. Their audit must enumerate
which VInt handlers service them, which gameplay routines poll them, and
whether existing replay advances that service on lag and non-lag rows.

Any proposed S1/S2 authoritative completion kind must then demonstrate a
polled, gameplay-visible readiness gate whose timing is not already reproduced
by lag, execution phase, and deterministic queue service.

## Completion event schema

Authoritative edges live in a dedicated hardware-timing stream, not
`physics.csv` or `aux_state.jsonl`. The representation is intentionally small:

```json
{
  "event": "hardware_work_completed",
  "raw_frame": 10429,
  "boundary": "post_objects",
  "kind": "kos_module_queue",
  "ordinal": 3,
  "submission_fingerprint": "sha256:..."
}
```

- `kind` names a hardware-service class, not a zone, archive, object, or trace.
- `ordinal` is monotonic within that kind and structural replay session.
- `submission_fingerprint` is generated independently by the recorder and
  engine from a canonical tuple of kind, ROM source span, destination span,
  compression variant, and module count. The trace cannot use it to construct
  or modify a job.
- `raw_frame` is the physical capture row.
- `boundary` is one of `vint_service`, `pre_main_loop`, or `post_objects`.
  It identifies the service boundary at which the ROM first exposes the
  completion.
- There is no payload containing gameplay state or work progress.

The container contract is exact:

- filename: `hardware_timing.jsonl`;
- metadata discovery key: `"hardware_timing_schema": 1`;
- fixture trace schema: `trace_schema: 7`;
- S3K standard recorder version: `6.34-s3k`;
- S3K complete-run recorder version: `6.34-s3k-completerun`.

For legacy `trace_schema <= 6` fixtures, absence of both the metadata key and
file means no authoritative timing input; replay uses only the production
scheduler and existing lag/phase contracts. A file without the metadata key,
the key without the file, a value other than integer `1`, or an unknown future
version is a hard fixture-load failure. An empty version-1 stream is valid.

Events use UTF-8, one compact JSON object per LF-terminated line, and canonical
ordering by `raw_frame`, then boundary order `vint_service`,
`pre_main_loop`, `post_objects`, then `kind`, then `ordinal`. Duplicate event
identities and out-of-order lines are rejected rather than normalized.

The initial authoritative kind registry contains only:

```text
KOS_MODULE_QUEUE
```

`KOS_DECOMPRESSION_QUEUE`, `PLC_QUEUE`, VDP transfer fences, and plane-draw
fences remain non-authoritative inventory candidates. Each requires separate
ROM evidence and design review.

### Boundary application

The fixture loader compiles each raw edge into the existing replay execution
timeline; runtime code never infers a boundary from row contents.

- `vint_service`: apply inside the row's selected VInt service, before any
  post-VInt main-loop consumer.
- `pre_main_loop`: apply after the row's VInt service and immediately before
  an admitted main-loop dispatch.
- `post_objects`: apply after the row's object scan. A consumer in that scan
  cannot observe it until its next admitted dispatch.
- Lag rows execute eligible VInt service but no main-loop or object consumer.
- Setup-only and advance-only rows execute only the service boundaries named
  by their production lifecycle; they never gain an implied gameplay pass.
- The first raw row has no synthetic predecessor. An edge on it must match a
  job submitted by the represented structural prefix or fails.
- At segment end, any unconsumed edge or pending non-exportable job fails.
- Rewind restores the compiled-edge cursor, so the same edge is consumed once
  again on replaying the restored boundary.

## Engine model

### Submission

Production code submits a typed hardware job with ROM-backed input. Submission
allocates the next ordinal for that kind and computes the stable fingerprint
from the job it actually submitted. Host-side data preparation may finish
immediately, but observable readiness remains owned by the timing service.

### Ordinary play

One production queue/decoder state machine serves both ordinary play and
replay. It retains the ROM descriptor, bookmark, source/destination, module,
FIFO, and service-point state required by that queue. Ordinary play releases
work from ROM-derived work units at the disassembly-defined VInt service
points, never from host wall-clock duration.

The recorded scheduler may replace only the final readiness admission. It does
not replace submission, preparation, queue order, decoder progress, service
calls, or the consumer. Live AIZ/HCZ acceptance tests must demonstrate that the
production scheduler remains within the recorded ROM completion boundaries;
trace green alone is insufficient evidence of live accuracy.

### Trace replay

For an eligible kind, the dedicated recorded edge is authoritative over final
readiness:

- an engine job whose data is ready early remains observably pending;
- the matching kind, ordinal, fingerprint, and boundary are released at the
  recorded boundary;
- the job must already be prepared; an edge cannot force preparation or
  decoder progress;
- release makes the engine readiness owner change naturally;
- the ROM-modeled consumer performs every downstream mutation.

The timing adapter is a dedicated input port. It cannot read physics or aux
comparison data and never calls a title-card, results, level-load, ring,
object, or event mutation API.

### Rewind

Rewind captures the complete ordered FIFO and replay ledger:

- next ordinal per kind;
- every queued job's kind, ordinal, fingerprint, canonical submission fields,
  prepared/released state, and output ownership;
- active descriptor/bookmark/source/destination/module/FIFO progress;
- deterministic-scheduler work-unit progress;
- compiled completion edges and the consumption cursor; and
- the set of already-consumed edge identities.

A restored replay must reproduce output side effects and accept the same future
completion edge exactly once. Tests cover snapshots immediately before, on,
and after completion in ordinary and recorded modes.

## Failure semantics

Synchronization must expose structural disagreement rather than conceal it.

Replay fails when:

- a completion edge has no matching pending kind and ordinal;
- the independently computed submission fingerprint differs;
- the service boundary differs;
- the matching job is not prepared;
- the engine submits an unexpected additional job;
- the expected job was already released;
- a job remains pending past the structural segment boundary;
- two events release the same job; or
- the completion would be consumed at an ambiguous replay phase.

Reports show both sides:

```text
expected completion: KOS_MODULE_QUEUE#3
engine pending:       KOS_MODULE_QUEUE#2
```

An engine that submitted the wrong work must not be made green by releasing
whatever happens to be pending. Moving an otherwise valid edge changes
gameplay timing and should produce ordinary strict comparison failures; an edge
is called structurally invalid only when identity, preparation, ordering, or
boundary disagrees.

## Comparison policy

Gameplay comparison remains strict. In particular, this design does not
declare ring reset timing non-release-blocking.

The completion edge reproduces the missing external timing input. The
results/title routine must then clear rings through its ordinary ROM-modeled
branch. A ring mismatch after the edge remains a real divergence.

During development, a report may label fields inside a proven pending interval
as timing-correlated diagnostics. Such labeling must not suppress committed
release assertions and must disappear once the completion contract is active.

## Recorder policy

Recorders should poll stable RAM symptoms at the normal capture point. They
should not install broad execute/write hooks merely to infer hardware causes.

For the S3K Kos queue:

- observe the ROM queue/busy owner already read by consumer routines;
- emit an edge only on the eligible pending-to-complete transition;
- assign the ordinal from observed queue lifecycles;
- keep stage gating ahead of any optional diagnostic hook;
- retain invisible, sound-disabled, maximum-speed operation; and
- terminate within a bounded window.

Native and Lua recorders must emit byte-equivalent timing events before a
fixture is regenerated. The version-1 differential gates cover both
`6.34-s3k` standard and `6.34-s3k-completerun` native/Lua captures, including
byte-exact empty streams for routes with no eligible completion. The stream is
not declared through `aux_schema_extras`.

## Acceptance criteria

The first implementation is accepted only when:

1. S1 and S2 PLC audits prove their per-handler service and polling behavior;
   existing replay results remain unchanged unless a separately reviewed
   timing kind is justified.
2. AIZ and HCZ submit matching generic Kos queue work without zone or trace
   predicates.
3. Recorded Kos completion edges release only matching prepared kind,
   ordinal, fingerprint, and boundary.
4. AIZ and HCZ ring-reset timing emerges from their ordinary results/title
   routines.
5. Missing, duplicate, reordered, mismatched, wrong-boundary, and unprepared
   edges fail structurally; a shifted valid edge causes strict downstream
   comparison failure.
6. Every gameplay row remains compared.
7. Live play uses a deterministic non-wall-clock scheduler.
8. Rewind across pending and completed work is deterministic.
9. No trace payload writes gameplay state.
10. All previously-green non-LBZ S3K replays and the repository's required
    S3K bootstrap/loading guards remain green. LBZ is not used as a trace-work
    validation target.

## Follow-up audit outputs

Before implementation, produce:

1. an S1 timing inventory separating synchronous lag work from PLC queues that
   ordinary loops poll;
2. the equivalent S2 inventory, using the special-stage timeline as the
   reference;
3. an S3K inventory of every main-loop-polled hardware readiness value,
   separating the direct Kos queue, Kos module queue, VDP/plane fences, and
   presentation-only flags; and
4. a trace-schema audit that pins the raw-frame boundary at which completion
   becomes visible to replay.

These audits should remove candidates when lag or phase already covers them.
The goal is the smallest completion-kind registry that explains every
remaining hardware-timing-sensitive gameplay boundary.
