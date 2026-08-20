# S1 `VBlank_Lag` V-int admission

## Status

Proposal. Not implemented. This note answers one question and stops: can the
ROM's `VBlank_Lag` stall be expressed inside the existing
[cross-game hardware-timing trace contract](2026-07-27-cross-game-hardware-timing-trace-contract.md),
or does it require a new, inherently row-keyed recorded input?

**Answer: (b).** It requires a new recorded input. It cannot be expressed as
job-readiness edges, and the reason is arithmetic rather than aesthetic. The
rest of this note gives the ROM basis, the disproof of (a), the shape the new
input would take, the guard changes it implies, and the discriminator a future
reader needs to tell a legitimate lag release from an illegitimate one.

The contract change proposed here is a user decision. Nothing in this note has
been implemented and no guard has been changed.

## ROM basis

`VBlank:` tests the pending routine selector first
(`docs/s1disasm/sonic.asm:656-657`):

```
		tst.b	(v_vblank_routine).w			; was a VBlank routine set?
		beq.s	VBlank_Lag				; if not, this is a lag frame, branch
```

The selector is stored by the main loop immediately before it waits, and is
reset to `id_VBlank_Lag` by `VBlank` itself as soon as a real handler is
dispatched (`sonic.asm:675`). So `v_vblank_routine == 0` at V-int entry means
exactly one thing: *the 68K main loop had not yet reached its next wait when
this V-int fired.*

`VBlank_Lag` (`sonic.asm:712-746`) performs no `ProcessPLC` call of any kind.
Outside Labyrinth Zone it branches straight to `VBlank_Music`; inside LZ it
performs a CRAM/H-blank palette transfer and then also branches to
`VBlank_Music`. Both paths fall through to `VBlank_Exit`
(`sonic.asm:684-687`), which still does:

```
		addq.l	#1,(v_vblank_count).w			; increment VBlank counter
```

A lag V-int therefore **advances `v_vblank_count` while decompressing zero
tiles**. Every mode handler that does decompress reaches it through
`ProcessPLC_9Tiles` (`sonic.asm:781`, `:946`, `:970`) or `ProcessPLC_3Tiles`
(`sonic.asm:867`); `VBlank_Lag` reaches neither.

The corpus evidence for this being the operative mechanism was established
before this note and is recorded in `docs/status/trace-frontier-log.md`: across
all 34 segments of the S1 complete-emeralds chain, 37 stalls, all 37 on lag
frames; 3,331 serviced rows, none on a lag frame; zero counterexamples in
either direction.

## Why (a) is impossible: the decode arithmetic

`ProcessPLC_9Tiles` (`sonic.asm:1431-1440`) arms a per-frame budget and then
`ProcessPLC` (`:1455-1490`) spends it:

- `v_plc_framepatternsleft` is set to 9 (or 3) at the top of each *serviced*
  V-int;
- the inner `.loop` decrements `v_plc_patternsleft` — the queue head's
  remaining tile count — once per decoded tile;
- when `v_plc_patternsleft` hits zero the code branches to
  `ProcessPLC_ShiftCue` (`:1494`) immediately, **abandoning the rest of the
  frame budget**; the next entry begins fresh on the *next* serviced V-int.

`v_plc_patternsleft` is written only inside `ProcessPLC`. Nothing else
decrements it. This is the field the trace compares as
`queue.nemesis_plc_queue.remaining_work`.

The hardware-timing contract's authority (contract 3, "External work
completion") is a *readiness release on an already-prepared, already-submitted
job*. Its documented S1 form is narrower still: the existing
`NEMESIS_PLC_QUEUE` kind "releases only the `RunPLC` arming edge", per the
class comment on `TestS1S2PlcComparisonOnlyGuard.timingKindRegistryIsClosedToUndeclaredWork`.
Readiness release controls *whether a completed entry retires*. It does not and
cannot control *whether the head entry's tile counter decrements this frame*.

The failure at the ss_5 -> syz2 bridge, syz2 row 70, makes the distinction
concrete:

| field | expected (ROM) | actual (engine) |
|---|---|---|
| `remaining_work` | 1 | 24 |
| `queued_fingerprints` | 6 entries | 5 entries |

The engine holds *fewer* queued entries and a *larger* head remainder: it has
already retired an entry the ROM has not, and is 9-tile-servicing a later entry
while the ROM is still one tile from finishing an earlier one. That is a
queue-phase offset accumulated over roughly three unserviced V-ints
(23 tiles ≈ 2.5 frames at 9 tiles/frame), not a readiness disagreement about a
single job.

The decisive point is the shape of the required trajectory, not the size of
this particular error. Reproducing `VBlank_Lag` requires rows on which
`remaining_work` **does not change mid-entry**. No sequence of readiness edges
can produce such a row: while an entry is the head and the mode handler runs,
`ProcessPLC` decrements it, and a readiness edge has no vote in that. Holding
readiness parks a *finished* entry at remaining zero; it cannot pause an
*unfinished* one. Option (a) is ruled out on the ROM's own arithmetic.

## Why the existing tear-derived suppression does not reach it

`TraceRunFrameDriver.presentationRowIsCarriedLagClosure`
(`src/main/java/com/openggf/trace/replay/runs/TraceRunFrameDriver.java:228`)
is correct and must not be touched. It models *"no V-int elapsed for this
row"* — a counter that did not advance, or a row following a span that did not
advance — and suppresses the row's service accordingly. Four of the chain's six
presentation bridges pass because of it.

The ROM condition in this note is the opposite structural case: **a V-int did
run, and it took the lag branch**. The census over 186,720 row transitions
found 46 non-advancing rows (all inside the six bridges) and exactly four
delta-two rows; outside those bridges the recorded counter advances by exactly
one on every transition. The 33 rows that matter here are ordinary
single-frame lag V-ints with delta one on both sides. They leave no counter
signature whatsoever. Two are the bridge frontier; 31 sit in ordinary level
segments, 15 of them in lz4's first 123 rows — which is what one would expect
from `VBlank_Lag`'s LZ-only CRAM transfer path being reached at all.

Any model derived from the recorded counter delta is structurally incapable of
reaching those 33 rows. This is not a tuning shortfall; the information is
absent from the recording.

## The shape of (b)

The right datum is the ROM's own branch input, not a list of frame indices.

> Record `v_vblank_routine` as sampled at V-int entry, before the handler
> dispatches, once per physical row.

A minimal boolean derivation (`vint_dispatched_mode_handler = routine != 0`)
is sufficient for replay, but recording the byte is preferable: it is the
actual RAM value, it is self-describing, it also identifies *which* mode
handler ran, and it cannot be reconstructed from the boolean later.

Placement: a per-physical-row field in the existing v5 row stream, or a
dedicated v5 sidecar stream keyed by `raw_frame`. Either is inherently
row-keyed. That is the whole point and should not be disguised.

Replay consumes it at exactly one place: selecting `PlcLifecyclePhase.LAG`
instead of the row's mode phase. The engine already has that mechanism —
`Sonic1PlcService` services nothing under `LAG`
(`src/main/java/com/openggf/game/sonic1/resources/Sonic1PlcService.java:165,183`),
`DynamicArtDmaServiceModel` already excludes it
(`src/main/java/com/openggf/game/rules/DynamicArtDmaServiceModel.java:31`), and
`TraceRunPresentationClosure` already runs a row under it
(`src/main/java/com/openggf/trace/replay/runs/TraceRunPresentationClosure.java:58`).
The phase is correctly cited and correctly implemented; it simply never fires
here because nothing tells replay the V-int took the lag branch.

## What the port may and may not do

May:

- select `PlcLifecyclePhase.LAG` for the row's represented V-int, and nothing
  else;
- do so only for a row that owns a real V-int (the existing
  suppressed-closure rule keeps priority for rows that own none);
- be restored by rewind along with the row cursor.

May not:

- carry, seed, or adjust `v_plc_patternsleft`, `v_plc_buffer_dest`, queue
  contents, fingerprints, or any decode progress value;
- create, retire, reorder, or prepare a queue entry;
- select a mode handler *other* than lag — a recorded nonzero routine byte
  admits the engine's own natively-derived phase or fails; it never overrides
  it. Replay uses the datum only to answer "did the ROM run no handler here",
  never "which handler should I run";
- reach physics, aux, or any gameplay owner;
- key on zone, route, game name, fixture name, or frame *number*. `raw_frame`
  is a row identity for a per-row recording, not a scheduling predicate; the
  distinction is that no value is compared against a frame index to decide
  behaviour.

## Which contract invariant this breaks

Precisely one, and it should be stated without softening: the contract's
non-goal *"Adding zone-, route-, trace-, or frame-specific scheduling
branches"* was written to forbid per-row scheduling inputs, and this is a
per-row scheduling input.

Three things narrow the breach, and a reviewer should weigh them rather than
accept them:

1. **It is contract 2, not contract 3.** "Execution phase" already says replay
   models a physical frame's non-level VInt/main-loop regime, and that "phase
   evidence must be structural... may come from mode and lifecycle state
   already recorded for comparison, but must not be inferred from a fixture
   name, route, position, animation, or a convenient row shape."
   `v_vblank_routine` is the mode/lifecycle selector itself — the strongest
   possible form of structural phase evidence. What is new is the *granularity*
   (per physical row) rather than the *category*.
2. **It records a scheduling outcome, not a hardware cause.** The contract's
   governing principle is "record the smallest scheduling outcome observable to
   the game, not the hardware cause that produced it." `v_vblank_routine == 0`
   is that outcome. The cause — how long the 68K spent in decompression, DMA,
   or Z80 arbitration — stays unrecorded and unmodelled, which is also why the
   non-goal against recording host execution duration is untouched.
3. **It is not gameplay hydration.** Nothing is written into engine state. The
   port selects between two code paths the engine already owns, both of which
   are ports of ROM routines, and the row's *result* (tiles decoded, queue
   contents, everything compared) is produced natively by the engine's own
   `ProcessPLC` model. Hard rule 4's test — "the change only affects *when*
   real, engine-created work becomes ready" — is satisfied in substance: the
   work is engine-created, the delay is real, and no value crosses.

What is *not* narrowed, and is the honest cost: contract 3's admission checks
(kind, ordinal, stable fingerprint, service boundary) are a strong,
self-proving identity handshake that fails closed when the engine and the
recording disagree about which work exists. A per-row phase input has no such
handshake. Its only integrity check is that the row exists and owns a V-int.
That is a genuinely weaker guarantee, and it is the substance of the decision
being asked of the user.

## Guard changes this would require

- `TestS1S2PlcComparisonOnlyGuard.timingKindRegistryIsClosedToUndeclaredWork`
  asserts `HardwareWorkKind` is exactly
  `{KOS_MODULE_QUEUE, KOS_DECOMPRESSION_QUEUE, NEMESIS_PLC_QUEUE}`. **No change
  is needed**, and none should be made: this input is not a `HardwareWorkKind`
  and must never become one. Keeping the registry closed is what prevents this
  proposal from being smuggled in as a fourth completion kind.
- `TestS1S2PlcComparisonOnlyGuard.traceProductionSourcesDoNotDependOnNativePlcServices`
  and `replayAndBootstrapSourcesDoNotReferenceNativePlcServices` forbid
  `com.openggf.trace` and `com.openggf.trace.replay` from referencing
  `Sonic1PlcService`/`Sonic2PlcService`. **No change is needed.** Replay already
  selects a `PlcLifecyclePhase` without naming a PLC service; the new input
  rides that same seam. If an implementation finds itself needing to weaken
  either assertion, that is proof the implementation is wrong, not that the
  guard is.
- `TestHardwareTimingAuthorityGuard` confines the *timing port* to its
  parser/authority isolation. **No change is needed**, because this input must
  not be routed through `HardwareTimingService` at all. It needs its own
  guard, not a hole in that one.
- **New guard required.** A per-row phase input needs a guard asserting: the
  recorded routine byte reaches exactly one consumer; that consumer's only
  output is a `PlcLifecyclePhase` selection; a nonzero recorded value never
  overrides the natively-derived phase; and no physics, aux, or gameplay owner
  can read it. Without that guard this proposal should not land, because
  nothing else would stop the field from growing consumers.

## Telling a legitimate lag release from an illegitimate one

For a future reader, in order of decisiveness:

1. **Does it change what the row does, or only which of two ROM-ported paths
   runs?** Legitimate: replay picks `LAG`, and every compared value is still
   produced by the engine's `ProcessPLC` port. Illegitimate: any recorded
   number reaches `v_plc_patternsleft`, `v_plc_buffer_dest`, a fingerprint
   list, or a queue slot.
2. **Is the recorded value the ROM's branch input, or a measurement of the
   engine's error?** Legitimate: `v_vblank_routine`, cited to
   `sonic.asm:656-657`. Illegitimate: a frame list, a stall count, a tile
   offset, or any value obtained by diffing a fixture — hard rule 3's fitted-
   model test, which a green fixture cannot distinguish.
3. **Does it only ever say "no handler ran"?** Legitimate: the input can
   suppress a handler, never select one. Illegitimate: the recorded byte
   choosing which mode handler replay dispatches — that is route hydration
   wearing the phase's clothes.
4. **Would it hold for a BK2 nobody has recorded?** The recording carries the
   ROM's own RAM value at the ROM's own branch point, so a new movie carries
   its own answer. Any construct that would need the 33 rows re-enumerated for
   a new movie has failed this test.

## Open items, not addressed here

- No committed S1 or S2 fixture carries a `hardware_timing` stream; a probe
  logged 100 `NEMESIS_PLC_QUEUE` submissions with zero recorded edges across
  the whole chain, so the recorded port is currently inactive for S1
  regardless of this proposal. An unmerged S1 fixture branch
  (`bugfix/ai-s1-fixture-rebase-r1`) carries a `nemesis_plc_queue` stream and
  was deliberately not merged because it is not green.
- Adopting (b) requires a fixture re-record: no existing recording contains
  `v_vblank_routine`. The scope of that re-record — and whether it is worth it
  against 16 of 34 segments currently never replayed — is part of the decision.
- S2 has the same `VBlank`/lag structure. This note deliberately scopes to S1
  and does not claim the S2 case without its own corpus evidence.
