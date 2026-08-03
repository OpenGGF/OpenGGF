# Cross-game hardware-timing trace contract

## Status

Approved after independent review. The symptom-first direction, the narrow
authoritative hardware-completion exception, and the schema-2 S3K direct
Kosinski extension are user-approved. This document inventories
timing-sensitive Mega Drive activities used by Sonic 1, Sonic 2, and Sonic 3
& Knuckles and defines the minimum authority a dedicated hardware-timing
input stream may have over them. The direct-queue implementation details are
owned by
[`2026-07-28-s3k-kos-decompression-queue.md`](2026-07-28-s3k-kos-decompression-queue.md);
this document remains the cross-game authority boundary.

The governing principle is:

> Record the smallest scheduling outcome observable to the game, not the
> hardware cause that produced it.

Most expensive hardware work therefore does not need its own trace event. If
its only gameplay-visible consequence is that the 68K main loop missed a
frame, the existing lag-row contract is sufficient. A new completion event is
reserved for work that remains pending while the main loop continues and the
ROM explicitly polls a hardware-owned readiness gate.

### 2026-08-02 suppressed-row boundary clarification

Schema-2 capture can observe a loop-tail completion on a physical row whose
`Level_frame_counter` remains held. In that case the CPU has already traversed
`Process_Kos_Queue` and reached `Wait_VSync`, but the stored row owns only the
resulting VBlank closure and no gameplay dispatch. A `pre_main_loop` edge
explicitly recorded on that row is structural evidence for the completion's
deferred visibility. Replay may expose that exact edge to the timing observer
after the row's VInt closure, without executing another production service,
main loop, object scan, or producer.

When that admission succeeds, the suppressed-row closure completes only the
production coordinator's post-service half for `pre_main_loop`. This is the
ordinary queue-owned observation of newly ready work: for S3K it retires the
ready direct FIFO head so the KosM parent can claim it at its next
`post_objects` state step. Replay does not repeat the coordinator pre-step or
`HardwareTimingService.service`, and therefore cannot create work, advance
preparation, or invent a consumer. The timing observer returns only whether it
consumed an exact edge; it never receives or calls the coordinator itself.

The ordinary admission operation proves its boundary from the production
service's `lastServicedBoundary`. That proof is intentionally unavailable after
the suppressed row has serviced VInt. The recorded-completion authority may
therefore expose one distinct suppressed-row admission operation. It accepts
only `pre_main_loop`, and only the replay port may invoke it after proving that
the next unconsumed edge belongs to the latched current raw row. It bypasses
only the stale `lastServicedBoundary` equality; it reuses every pending-head,
kind, ordinal, fingerprint, preparation, release, and deduplication check.
Source guards confine the operation to the replay port and confine the port's
suppressed-row entry to the stateless timing observer.

This is not elapsed-row reconciliation: advancing to the next raw row never
authorizes a stale edge. An ordinary lag row without a current-row
`pre_main_loop` edge remains VInt-only. The exception still releases only an
already-submitted, already-prepared FIFO head after kind, ordinal, fingerprint,
and boundary all match; missing, unprepared, reordered, mismatched, or
gap-crossing work fails closed.

This clarification does not authorize a module-parent completion merely because
the recorder first observes its RAM retirement on a held-counter row. The
schema-2 native recorder currently classifies every newly observed module-head
retirement from a duplicate `Level_frame_counter` sample as `vint_service`.
That classifier and the published `6.40-s3k-completerun` timing streams predate
the production loop-tail phase correction in `ddaf8e152`. If such an edge names
a parent that production has not prepared, replay must fail closed. The next
owner is an audited native-recorder observation-row/service-row attribution
review. If it finds the capture attribution stale, correction requires a
separately approved fixture publication; if it validates the current stamp, a
broader partial-CPU-prefix replay contract requires its own design and review.
Neither outcome authorizes timing authority to run `Process_Kos_Module_Queue`,
backdate an edge ad hoc, or prepare the parent.

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

The same rule applies to hardware-work identity when a standalone segment
begins after earlier work in its captured structural run. Before the first
production submission of a kind, the timing schedule may establish that
kind's first recorded ordinal as the production ledger's initial ordinal.
This base is restored by rewind. It is not reapplied at a run-chain handoff,
cannot renumber an existing submission, and does not change preparation,
readiness, payload, or gameplay state. Edges for one kind must be contiguous
within one timing stream. Exported edges across structural segments may have
ordinal gaps where native phase work intentionally produced no completion
edge; the production submissions and claims must advance the ledger through
that gap. Handoff never seeds it. If those native submissions are absent, the
later edge fails its ordinary engine-identity admission check.
An empty initial run schedule does not infer a base from a later segment:
when ordinal continuity depends on earlier production submissions, those
submissions must occur. A reviewed nonzero initial base must be established
explicitly at initial run installation, before production submits that kind.

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
| Direct Kosinski decompression queue | Not used as the S3K-style owner | Not used as the S3K-style owner | `Kos_decomp_queue_count` independently gates AIZ intro and ICZ act-transition progression | External completion in S3K schema 2 | `KOS_DECOMPRESSION_QUEUE` is authoritative only under the reviewed schema-2 registry |
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
- metadata discovery key: `"hardware_timing_schema": 1` or
  `"hardware_timing_schema": 2`;
- fixture trace schema: `trace_schema: 7`;
- current native S3K standard recorder version for schema 2: `6.41-s3k`;
- native S3K complete-run recorder version for schema 2:
  `6.42-s3k-completerun` (schema 2 was introduced in both native recorder
  families at version `6.38`); and
- the frozen Lua recorders remain at `6.37-s3k` and
  `6.37-s3k-completerun`, emitting schema 1 only.

For legacy `trace_schema <= 6` fixtures, absence of both the metadata key and
file means no authoritative timing input; replay uses only the production
scheduler and existing lag/phase contracts. For `trace_schema: 7`, a file
without the metadata key, the key without the file, a value other than integer
`1` or `2`, or an unknown future version is a hard fixture-load failure. An
empty stream is valid in either hardware-timing schema.

Events use UTF-8, one compact JSON object per LF-terminated line, and canonical
ordering by `raw_frame`, then ROM loop-tail boundary order `vint_service`,
`post_objects`, `pre_main_loop`, then `kind`, then `ordinal`. Duplicate event
identities and out-of-order lines are rejected rather than normalized.

The kind registry is selected by the metadata version:

```text
schema 1: KOS_MODULE_QUEUE
schema 2: KOS_MODULE_QUEUE, KOS_DECOMPRESSION_QUEUE
```

Under schema 1, `KOS_MODULE_QUEUE` uses recorded final readiness while
`KOS_DECOMPRESSION_QUEUE` remains a live production queue. Under schema 2,
both kinds use recorded final readiness. No timing stream may configure both
kinds as live, and an event kind not admitted by the selected registry fails
loading or admission. `PLC_QUEUE`, VDP transfer fences, and plane-draw fences
remain non-authoritative inventory candidates; each requires separate ROM
evidence and design review.

Committed schema-1 fixtures remain loadable. A fixture that crosses the AIZ
intro or ICZ act-transition direct-count consumer cannot certify that
boundary, because its direct work is live rather than edge-authorized. The
checked compatibility inventory lives in
`TestCommittedHardwareTimingFixtures`; replacement with schema-2 native
output is a separate publication action requiring explicit approval.

### Boundary application

The fixture loader compiles each raw edge into the existing replay execution
timeline; runtime code never infers a boundary from row contents.

- `vint_service`: apply inside the row's selected VInt service, before any
  post-VInt main-loop consumer.
- `post_objects`: apply after the row's object scan. A consumer in that scan
  cannot observe it until its next admitted dispatch.
- `pre_main_loop`: normally apply after `post_objects` as the current frame's
  final loop-tail boundary, ahead of `Wait_VSync` and the next admitted
  iteration. A held-counter row may instead expose a `pre_main_loop` completion
  edge explicitly compiled for that same raw row after its VInt closure. This
  represents deferred observation of the prior loop tail; it does not admit a
  main loop or traverse production queue service. On exact admission, replay
  runs only the production coordinator's `pre_main_loop` post-service hook so
  its normal queue metadata observes readiness. Its dedicated admission
  bypasses only the ordinary service's now-`vint_service` last-boundary check
  and remains confined to the compiled current row.
- Lag rows execute eligible VInt service but no main-loop or object consumer.
  They expose no other boundary unless the compiled current row contains the
  held-counter `pre_main_loop` completion described above.
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

The same production queue/decoder state machines serve both ordinary play and
replay. They retain the ROM descriptor, bookmark, source/destination, module,
FIFO, and service-point state required by each physical queue. S3K standard
Kosinski work uses one shared four-entry direct FIFO; KosM parents enqueue
their child streams into that FIFO and advance only through their coordinator.
Ordinary play releases work from ROM-derived work units at the
disassembly-defined service points, never from host wall-clock duration.

The recorded scheduler may replace only the final readiness admission. It does
not replace submission, preparation, queue order, decoder progress, service
calls, or the consumer. Live AIZ/HCZ acceptance tests must demonstrate that the
production scheduler remains within the recorded ROM completion boundaries;
trace green alone is insufficient evidence of live accuracy.

### Trace replay

For a kind configured as recorded by the selected schema, the dedicated edge
is authoritative over final readiness:

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

For the S3K Kos queues:

- observe the ROM queue/busy owners already read by consumer routines;
- emit an edge only on the eligible pending-to-complete transition;
- assign independent per-kind ordinals from observed FIFO lifecycles;
- retain direct slot identity across bit-15 busy progress and reconcile
  retirement plus append without requiring a sampled zero;
- emit module retirement at `post_objects` before any same-frame direct
  retirement at the later loop-tail `pre_main_loop` boundary;
- keep stage gating ahead of any optional diagnostic hook;
- retain invisible, sound-disabled, maximum-speed operation; and
- terminate within a bounded window.

The native harness is the fixture-publication authority. Before publication, its
implementation must be established against the audited ROM/disassembly
semantics, behavioral and unit tests, cross-implementation vectors where
available, and independent code review. An existing candidate is valid when it
was produced by the unchanged implementation that receives that review.
Lua/native byte equivalence is optional corroboration, not a publication
prerequisite. Version-1 differential coverage includes `6.37-s3k` standard and
`6.37-s3k-completerun` captures and byte-exact empty streams for routes with no
eligible completion. The maintained native schema-2 recorders are
`6.41-s3k` and `6.42-s3k-completerun`; they emit both module and direct
retirements. Recorder 6.37 treats an unchanged
`Level_frame_counter` as `vint_service` unless the ROM is inside its
held-counter title-card load loop. That loop is armed only by the fixed
`Obj_TitleCard` parent in physical SST slot 8 with `objoff_48` set, and its
post-object admission remains active for the iteration selected by the ROM's
raw `objoff_48` or `Nem_decomp_queue` exit predicates. An advancing counter
also admits `post_objects`. A Nemesis job alone cannot arm the exception, so
ordinary lag rows remain VInt-only. The stream is not declared through
`aux_schema_extras`.

Native 6.41/6.42 no longer uses the generic row heuristic for a module parent
whose prior modules-left byte is exactly `0x81`: a canonical one-head FIFO
removal/shift, including exact active identity, trailing entries, cardinality,
and reset/mode fencing, proves the ROM POST owner even when the observation row
holds `Level_frame_counter`. Duplicate/no-retirement rows remain VInt-only;
stale, malformed, multi-head, append-during-shift, and reset-crossing shapes
fail closed. Frozen Lua 6.37 behavior is unchanged and may therefore differ at
this one native state-proven attribution.

After capture, publication records and pins the native candidate's digests,
lengths, event counts, ordering, ranges, and semantic inventory as immutable
evidence, then copies that output exactly after explicit user approval.
Committed tests use those frozen literal expectations; they must not calculate
expected values by invoking the native recorder that produced the candidate.

## Acceptance criteria

The first implementation is accepted only when:

1. S1 and S2 PLC audits prove their per-handler service and polling behavior;
   existing replay results remain unchanged unless a separately reviewed
   timing kind is justified.
2. AIZ, HCZ, and ICZ submit matching generic Kos queue work without zone or
   trace predicates; module children contend in the shared direct FIFO.
3. Recorded Kos completion edges release only matching prepared kind,
   ordinal, fingerprint, and boundary.
4. AIZ intro and ICZ act-transition progression emerge from the production
   direct-count predicate; AIZ and HCZ ring-reset timing still emerges from
   the ordinary results/title routines.
5. Missing, duplicate, reordered, mismatched, wrong-boundary, and unprepared
   edges fail structurally; a shifted valid edge causes strict downstream
   comparison failure.
   Suppressed-row coverage additionally proves that an exact prepared
   current-row `pre_main_loop` edge succeeds without production service or
   gameplay, while absent, unprepared, wrong-kind, wrong-ordinal,
   wrong-fingerprint, wrong-boundary, stale, and unrepresented-gap cases fail
   closed. Rewind consumes the edge exactly once again after restore.
6. Every gameplay row remains compared.
7. Live play uses a deterministic non-wall-clock scheduler.
8. Rewind across pending and completed work is deterministic.
9. No trace payload writes gameplay state.
10. All previously-green non-LBZ S3K replays and the repository's required
    S3K bootstrap/loading guards remain green. LBZ is not used as a trace-work
    validation target.
11. Schema-1 consumer-crossing fixtures remain loadable but are not described
    as direct-boundary certification; schema-2 replacement remains behind the
    fixture-publication approval gate.

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
