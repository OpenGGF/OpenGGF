# S1 `VBlank_Lag` V-int admission

## Status

Proposal. Not implemented, and no guard has been edited. This note answers one
question: can the ROM's `VBlank_Lag` stall be expressed inside the existing
[cross-game hardware-timing trace contract](2026-07-27-cross-game-hardware-timing-trace-contract.md)
as withheld job readiness, or does it need a per-row recorded fact?

**Answer: (b), a per-row recorded fact.** Option (a) is dead on arithmetic
rather than philosophy, and the note gives the disproof rather than asserting
it. The form proposed is the narrowest one that works: a `vint_lag` row event
with no ordinal, no fingerprint and no payload, consumed only by the V-int
service scheduler.

Reviewed independently; that review corrected two things in the original
framing, both incorporated below and marked where they land.

## ROM basis

`VBlank:` tests the pending routine selector first
(`docs/s1disasm/sonic.asm:656-657`):

```
		tst.b	(v_vblank_routine).w			; was a VBlank routine set?
		beq.s	VBlank_Lag				; if not, this is a lag frame, branch
```

The selector is stored by the main loop immediately before it waits, and
`VBlank` resets it to `id_VBlank_Lag` as soon as a real handler is dispatched
(`sonic.asm:675`). So `v_vblank_routine == 0` at V-int entry means exactly one
thing: *the 68K main loop had not yet reached its next wait when this V-int
fired.*

`VBlank_Lag` (`sonic.asm:712-746`) performs no `ProcessPLC` call of any kind.
Outside Labyrinth Zone it branches straight to `VBlank_Music`; inside LZ it
performs a CRAM/H-blank palette transfer and then also branches to
`VBlank_Music`. Both fall through to `VBlank_Exit` (`sonic.asm:684-687`):

```
		addq.l	#1,(v_vblank_count).w			; increment VBlank counter
```

A lag V-int therefore **advances `v_vblank_count` while decompressing zero
tiles**. Every handler that decompresses reaches it via `ProcessPLC_9Tiles`
(`sonic.asm:781`, `:946`, `:970`) or `ProcessPLC_3Tiles` (`:867`);
`VBlank_Lag` reaches neither.

Corpus evidence, established before this note and recorded in
`docs/status/trace-frontier-log.md`: across all 34 segments of the S1
complete-emeralds chain, 37 stalls, all 37 on lag frames; 3,331 serviced rows,
none on a lag frame. Zero counterexamples either way.

## Why (a) is impossible

Three independent reasons. Any one is sufficient.

**1. The comparator asserts `remaining_work` per row.** A row that services 9
tiles when the ROM serviced 0 is already wrong *on that row*, whatever its
readiness state. Readiness-withholding is invisible to the field being
compared.

**2. The decode counter is not readiness-owned.** `ProcessPLC_9Tiles`
(`sonic.asm:1431-1440`) arms `v_plc_framepatternsleft` to 9 at the top of each
*serviced* V-int; `ProcessPLC`'s inner loop (`:1455-1490`) decrements
`v_plc_patternsleft` — the queue head's remaining tile count, i.e. the compared
`remaining_work` — once per decoded tile, and nothing else writes it. On
completion it branches to `ProcessPLC_ShiftCue` (`:1494`) immediately,
abandoning the frame budget; the next entry starts fresh on the next serviced
V-int. Readiness release governs whether a **finished** entry retires. It
cannot pause an **unfinished** one. Reproducing `VBlank_Lag` requires rows
where `remaining_work` does not change mid-entry, and no readiness edge can
produce such a row.

**3. The queue chains off internal completion, not released readiness.** So
holding readiness leaves the whole downstream schedule permanently one frame
early per stall — the error is deferred, not removed.

And a mid-job stall has **no job identity**. Slicing each 9-tile quantum into a
pseudo-job to manufacture one would invent a work granularity the ROM does not
have: a row-keyed stream in a costume. **An (a)-shaped fix that goes green has
fitted something.**

The syz2 row 70 frontier illustrates all three:

| field | expected (ROM) | actual (engine) |
|---|---|---|
| `remaining_work` | 1 | 24 |
| `queued_fingerprints` | 6 entries | 5 entries |

The engine holds *fewer* entries and a *larger* head remainder — it has already
retired an entry the ROM has not, and the two `remaining_work` values describe
*different entries*. A queue-phase offset of ~23 tiles ≈ 2.5 frames at 9
tiles/frame. Not a readiness disagreement about one job.

Note also that the existing `NEMESIS_PLC_QUEUE` kind is arming-granularity by
design — per the class comment on
`TestS1S2PlcComparisonOnlyGuard.timingKindRegistryIsClosedToUndeclaredWork`,
S1's entry "releases only the `RunPLC` arming edge". It was never shaped to
reach per-frame decode.

## Where the contract line actually falls

**Correction to the original framing of this note.** It framed the question as
readiness-versus-stall and treated any per-row input as breaching the contract.
That was wrong about the contract.

The contract's governing principle is *"record the smallest scheduling outcome
observable to the game, not the hardware cause that produced it."* Contract 1,
main-loop admission, **already is** a per-row recorded fact consumed as
scheduling admission. The completion schema itself carries `raw_frame`. So the
non-goal against keying on frame index prohibits **engine code branching on
frame numbers** — the fitted `if (frame == 4917)` failure that hard rule 3
exists to stop — not row-anchored recorded events. Read the other way, the
existing lag rows would already be illegal.

This proposal is therefore an extension of contract 1/2 in the same category as
what is already there, not a breach requiring a new authority. What is new is
the *granularity of the regime fact*, not the kind of thing being recorded.

## The proposed form

A per-row V-int regime fact. **Not** a new `HardwareWorkKind`:

```json
{"event": "vint_lag", "raw_frame": 4917}
```

No ordinal, no fingerprint, no payload, because it is not a job and must never
acquire a job's shape.

Sampling: `v_vblank_routine == 0` at V-int entry, before dispatch — the
discriminator the ROM itself uses. Recording the byte rather than a boolean is
preferable: it is the actual RAM value, self-describing, and identifies which
handler ran.

Consumption: **only** the V-int service scheduler. On a flagged row the V-int
takes the modelled `VBlank_Lag` branch — no PLC service — and counters still
advance. The engine already owns every downstream piece:
`Sonic1PlcService` services nothing under `PlcLifecyclePhase.LAG`
(`src/main/java/com/openggf/game/sonic1/resources/Sonic1PlcService.java:165,183`),
`DynamicArtDmaServiceModel:31` excludes it, and
`TraceRunPresentationClosure:58` already runs a row under it. The phase is
correctly implemented and correctly cited; nothing currently tells replay the
V-int took the lag branch.

## The rows are not signature-less at capture

**Second correction to the original framing.** This note previously said the 33
ordinary lag rows "carry no recorded signature at all". That is true of what
was recorded and false of what is *observable*. The census looked at counter
deltas in the existing recording, not at what the recorder could sample.

S1 reads joypads only from real V-int handlers. Verified in the listing:
`VBlank_Lag` (`sonic.asm:712-746`) contains no `ReadJoypads` call, while the
handlers do — `VBlank_Levels` at `:818`, `VBlank_SpecialStage` at `:885`,
`VBlank_TitleCards`/`VBlank_Ending` at `:916`, `VBlank_Continue` at `:981`, and
the shared `VBlank_StandardTransfers` at `:1009`. So an emulator lag frame — no
input poll — is a capture-time predicate for `VBlank_Lag` dispatch.

Two refinements found while verifying this, neither fatal:

- There is a **sixth** `ReadJoypads` call site, at `sonic.asm:617` in
  `ErrorWaitForC`. It is the crash error handler and is unreachable in a valid
  trace, so the predicate holds — but "every read sits at those five" is not
  literally exhaustive and should not be repeated as such.
- The reviewer hedged that a pause path might skip input and false-positive.
  In S1 it does not: `VBlank_Paused` (`:805`) either branches to
  `VBlank_SpecialStage` (`:885`) or falls through into `VBlank_Levels`
  (`:818`), and both read joypads.

The conclusion is unchanged and still correct: **record `v_vblank_routine`
directly rather than the input-poll proxy.** The proxy's value is that it
proves the rows are distinguishable at capture time; the ROM's own branch input
is what should actually be stored.

## The flag gates the V-int only — not gameplay admission

This is the trap, and it deserves its own guard test.

The 33 rows **ran their main loop**: the counter advances by 1 on both sides.
They are not no-gameplay lag rows. A design that routes a `vint_lag` row into
the existing "main loop did not run" path desyncs all 33 immediately, and would
do so in a way that looks like a deeper physics bug.

So the constraint is: the flag selects the V-int branch and nothing else.
Gameplay admission stays with the existing counter model and the existing
`presentationRowIsCarriedLagClosure` suppression
(`src/main/java/com/openggf/trace/replay/runs/TraceRunFrameDriver.java:228`),
which is correct, correctly cited, and must not be touched — it models "no
V-int elapsed for this row", the opposite structural case to "a V-int ran and
took the lag branch".

**Not independently verified by this note:** the exact row-to-gameplay-frame
relationship in the S1 recorder. The constraint above is taken from review and
is the safe direction regardless, but an implementer must confirm against the
recorder how a physical lag frame is represented before wiring anything.

## What the port may and may not do

May: select the modelled `VBlank_Lag` branch for the row's V-int; advance the
V-int counters that `VBlank_Exit` advances; be restored by rewind with the row
cursor.

May not: carry or adjust `v_plc_patternsleft`, `v_plc_buffer_dest`, queue
contents, fingerprints or any decode progress; create, retire, reorder or
prepare a queue entry; **select a handler** — it can only ever say "no handler
ran", never which one; suppress gameplay, main-loop admission, input latching
or object dispatch; reach physics, aux or any gameplay owner; become a
`HardwareWorkKind`.

## Guard changes

No existing assertion needs to change, and two specifically must not:

- `timingKindRegistryIsClosedToUndeclaredWork` — **keep closed.** This input is
  not a `HardwareWorkKind`. The closed registry is what stops this being
  smuggled in as a fourth completion kind.
- `traceProductionSourcesDoNotDependOnNativePlcServices` and
  `replayAndBootstrapSourcesDoNotReferenceNativePlcServices` — unchanged.
  Replay already selects a `PlcLifecyclePhase` without naming a PLC service.
  If an implementation needs either weakened, the implementation is wrong.
- `TestHardwareTimingAuthorityGuard` — unchanged, because this must not route
  through `HardwareTimingService` at all.

**Two new guards required before this lands:**

1. Confinement: the `vint_lag` fact reaches exactly one consumer, the V-int
   service scheduler; its only output is the lag-branch selection; it cannot
   reach physics, objects, input latching or main-loop admission.
2. Gameplay-admission isolation: a `vint_lag` row still runs its gameplay
   frame. This is the correction above, encoded so it cannot regress silently.

## Sequencing — supersedes the note's original order

Two things must happen **before** any stall stream is published:

1. Fix the presentation-bridge row-application gap.
2. Diagnose the three fixtures that diverged under the arm stream. An
   unexplained divergence under one timing kind is the "constant absorbing an
   error elsewhere" smell, and installing a second stream on top of it will
   misattribute the reds.

## Telling a legitimate lag release from an illegitimate one

1. **Does it change what the row does, or only which of two ROM-ported paths
   the V-int takes?** Legitimate: the branch is selected and every compared
   value is still produced by the engine's `ProcessPLC` port. Illegitimate: any
   recorded number reaching `v_plc_patternsleft`, a fingerprint list or a queue
   slot.
2. **Is the recorded value the ROM's branch input, or a measurement of the
   engine's error?** Legitimate: `v_vblank_routine`, cited to `:656-657`.
   Illegitimate: a frame list, stall count or tile offset obtained by diffing a
   fixture — hard rule 3's fitted model, which a green fixture cannot detect.
3. **Does it only ever say "no handler ran"?** The input may suppress a
   handler, never select one.
4. **Does it leave gameplay admission alone?** If a `vint_lag` row stops
   running its main loop, the design is wrong.
5. **Would it hold for a BK2 nobody has recorded?** The recording carries the
   ROM's own RAM value at the ROM's own branch point, so a new movie carries
   its own answer.

## Open items

- Adopting this needs a **fixture re-record**: no existing recording carries
  `v_vblank_routine`. Scope that against 16 of 34 segments currently never
  replayed.
- No committed S1/S2 fixture carries a `hardware_timing` stream; a probe logged
  100 `NEMESIS_PLC_QUEUE` submissions with zero recorded edges across the
  chain, so the recorded port is inactive for S1 regardless. An unmerged branch
  (`bugfix/ai-s1-fixture-rebase-r1`) carries a `nemesis_plc_queue` stream and
  was not merged because it is not green.
- S2 has the same `VBlank`/lag structure. Deliberately not claimed here without
  its own corpus evidence.
