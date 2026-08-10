# S1 pre-main-loop level-load span — proposed hardware-timing contract extension

## Status

**Proposal awaiting human decision. Nothing here is implemented.** This document
is the deliverable of the investigation into the last residual on
`TestS1GhzMazeRoundTripChain.ghzMazeRoundTrip`
(`run_tail.edge[0]/[1].movie_logical_frame`, expected 9071, actual 9035,
delta 36). It answers two questions in order:

1. Can the span be derived from ROM data, needing no recorded input? **No.**
   Section 2 gives the evidence.
2. If not, what extension to the cross-game hardware-timing trace contract
   would be needed, what invariant keeps it safe, and what would
   `TestHardwareTimingAuthorityGuard` have to enforce? Sections 3-5.

Deliberately **not** proposed: a `36`, a per-zone load-span table, or any
"level load takes N frames" constant. Section 2 shows why each of those is
wrong even as an approximation.

## 1. What the span is

`GM_Level` has four frame-counted loops, all already modelled structurally:
`PaletteFadeOut` 22 rows, `Level_TtlCardLoop` as a PLC drain (150 + 1 prepare
frame, verified by summing `sum(ceil(tiles_i/9))` over `ArtLoadCues` at ROM
`0x01DD86`), `Level_Delay` 4, `PalFadeIn_Alt` 22 — total 199.

Between the title-card drain and `Level_Delay` sits a straight-line stretch
(`docs/s1disasm/sonic.asm:2856-2955`): `Hud_Base`, `PalLoad_Fade`,
`LevelSizeLoad`, `DeformLayers`, `LevelDataLoad`, `LoadTilesFromStart`,
`ColIndexLoad`, `ObjPosLoad`, `ExecuteObjects`, `BuildSprites`. It contains no
`WaitForVBlank`, so it consumes no counted iteration; the engine spends zero
rows in it. On hardware it costs real 68K time while the VDP keeps scanning and
the movie keeps advancing. That elapsed time is the whole of the residual.

The dominant costs are two data-dependent bitstream decoders called from
`LevelDataLoad` (`docs/s1disasm/_inc/LevelLayoutLoad.asm:17-24`): `EniDec` over
the zone's 16x16 block mappings and `KosDec` over its 256x256 chunk mappings.
`LoadTilesFromStart` (`docs/s1disasm/_inc/Level Drawing (REV01).asm:872-926`) is
structurally fixed — `DrawChunks` is 16 row strips x 32 blocks per plane, twice —
and is a minority of the cost.

## 2. The span is not computable at frame granularity

### 2.1 The arithmetic closes, confirming the diagnosis

For GHZ, `199 + 36 = 235`. The committed complete run's own manifest records the
observed level-to-level movie gap for GHZ as **235 and 236**. The residual is
exactly the un-modelled span, and nothing else is missing.

### 2.2 It is per-zone, so no constant exists

Level-to-level gaps from
`src/test/resources/traces/s1/runs/s1-sonic-complete-withemeralds/run_manifest.json`
(21 transitions):

| zone | gaps observed | map16 `.eni` bytes | map256 `.kos` bytes |
|---|---|---|---|
| GHZ | 236, 235 | 2464 | 8464 |
| MZ  | 228 x5 | 2058 | 6048 |
| SYZ | 230 x3 | 2414 | 9136 |
| LZ  | 216, 216, 217, 216, 217 | 810 | 10224 |
| SLZ | 220, 219, 219 | 2020 | 9264 |
| SBZ | 219, 220, 220 | 3738 | 10848 |

A 20-frame spread. Part of that spread is the per-zone `ArtLoadCues` title-card
drain, which is already modelled; the remainder is the span. Either way, a
single constant is refuted by inspection.

### 2.3 It is not a function of the compressed data either

If the span were derivable from what is being loaded — the hope that motivated
this investigation — the gap would be monotone in the decoder input. It is not.
SBZ has the **largest** input of any zone on both decoders (3738 + 10848) and
one of the **smallest** gaps (219-220); GHZ has 2464 + 8464 and the largest gap
(235-236). No additive model over compressed sizes fits: any coefficients that
reproduce GHZ over-predict SBZ by more than ten frames.

That is expected once you read the decoders. Enigma and Kosinski cost is a
function of the *code mix* in the bitstream (inline literal vs. copy, copy
length, code word length), not of its byte count, and the two zones' output
sizes are not proportional to their input sizes either. Deriving the cost would
mean attaching per-opcode 68K cycle counts to a faithful transliteration of
`EniDec`/`KosDec`/`NemDec` — a second, independently maintained model of the ROM
whose only possible validation is the traces it exists to predict.

### 2.4 Even a perfect cycle model would not land the frame

Two facts make frame-exactness unreachable:

- **Sub-frame jitter is observable in the fixture.** Within a zone the data is
  identical across acts, yet the gap moves by 1: GHZ 236/235, LZ 216/217, SLZ
  220/219, SBZ 219/220. Identical routines over identical data costing a
  different whole number of frames is the signature of a `ceil()` whose argument
  sits near a frame boundary — the span's start phase within the scanline
  differs, so the total crosses or does not cross. No frame-granularity model can
  produce that bit.
- **The budget is dominated by terms the engine does not model.** At NTSC
  ~128,000 cycles/frame the GHZ span is ~4.6M cycles. Of that, `DrawChunks`
  performs 4096 VDP data-port word writes whose cost depends on FIFO occupancy
  and bus arbitration against display and refresh, and ~36 VBlank interrupts
  fire inside the span and consume a phase-dependent slice of it. Being 1% out
  over 4.6M cycles is a third of a frame — enough to flip the stamp.

**Conclusion.** This is precisely the case CLAUDE.md:111-113 anticipates:
"Where genuine hardware timing cannot be derived from frame-granularity state at
all, the answer is a regenerable per-movie timing sidecar under rule 4, never a
tuned number."

## 3. Why the existing contract does not already cover it

[`2026-07-27-cross-game-hardware-timing-trace-contract.md`](2026-07-27-cross-game-hardware-timing-trace-contract.md)
authorises recorded timing to release the **readiness** of work the engine has
already submitted and prepared. The diverging field here is a **submission
stamp**: `movie_logical_frame` is the movie row at which engine-created work was
submitted. Nothing about readiness is wrong — ordinals, transfer ids and both
ledger fingerprints already match. What is wrong is the row cursor's position at
submission time, because the engine did not spend the span's rows.

Releasing readiness cannot fix a submission stamp, and stretching "release" to
mean "restamp" would silently widen the contract's authority. Hence a written
extension rather than an implementation.

## 4. The proposed extension (narrowest form)

**Name.** `pre_main_loop_span` — a row-advance-only timing event.

**Semantics.** When the engine independently reaches the owning boundary, the
event advances the **movie row cursor** by a recorded row delta, executing
nothing. It changes only which movie row subsequent engine-created work is
stamped with, and how recorded rows align for comparison. It is a clock
alignment, not a behaviour.

**What it must never touch.** `Level_frame_counter`, `V_int_run_count`,
`v_framecount`, oscillation, RNG, or any gameplay counter. The ROM is not
running its level main loop during the span either, so no gameplay clock should
advance across it. Only the movie/row cursor moves.

**Admission preconditions** (all required; any failure is a no-op, leaving the
trace red exactly as today):

1. The engine has, on its own, reached the boundary the event names — the
   transition from title-card PLC drain complete to `Level_Delay`. The boundary
   is named as a *kind*, never as a zone, act, frame index, route, or game name.
2. Ordinal matches the next unconsumed event of that kind.
3. The stable submission fingerprint of the level-load work the engine itself
   prepared at that boundary matches the event's fingerprint. This is the
   existing fingerprint mechanism, reused: it proves the engine and the recorder
   are talking about the same load, without the trace supplying what the load is.
4. The rows the delta covers carry no gameplay row in the recorded stream. The
   recorder must prove this at capture time; replay re-checks it.
5. The delta is strictly positive, bounded, and applied at most once per ordinal.
   The cursor is monotone; it can never rewind.

**Payload.** `{kind, ordinal, fingerprint, boundary, row_delta}` and nothing
else. No positions, no counters, no gameplay values, no zone/act, no frame index.

**Regenerability.** The field is derivable by the native harness from any BK2 of
any route, for all three games, by the same observation that produced the table
in section 2.2 — so a new recording carries its own span and nothing is fitted
to this fixture.

## 5. What `TestHardwareTimingAuthorityGuard` would have to enforce

Existing obligations carry over unchanged (parser/authority isolation, no
physics/aux/gameplay paths, no reflective mutation, fail-closed on mismatch).
The extension adds:

1. **Single consumer, single call site.** The row-cursor advance API is reachable
   only from the replay timing port; a source guard forbids any reference to it
   from gameplay, physics, object, PLC/DPLC, or level packages.
2. **Nothing executes during the advance.** A scope flag must make the object
   scan, level tick, VInt closure, and PLC/DPLC service *throw* if entered
   between cursor start and end. The guard asserts the flag exists, is set, and
   that the throwing paths are the real production entry points, not stubs.
3. **Cursor-only mutation.** Assert by reflection-free source guard that the
   advance writes exactly one field (the movie row cursor) and that no gameplay
   clock — `Level_frame_counter`, `V_int_run_count`, level frame count,
   oscillation phase, RNG — is writable from the timing package.
4. **Schema whitelist.** The parser rejects any field on the new kind outside
   `{kind, ordinal, fingerprint, boundary, row_delta}`; the guard asserts the
   whitelist and asserts the parser has no branch on zone, act, route, frame
   index, or game name.
5. **Monotonicity and idempotence.** Replaying the same ordinal twice, or an
   ordinal out of order, must be a no-op; a negative or unbounded delta must fail
   closed.
6. **No work creation.** Assert the port cannot submit, prepare, release, or
   reorder any hardware job — the advance has no reference to the production
   coordinator or `HardwareTimingService`.
7. **Confinement.** Assert the new kind is absent from every non-timing parser
   and that a stream lacking it degrades to today's behaviour (red, not green).

## 6. The decision being asked for

This is a genuine widening. Today recorded timing decides *when already-existing
work becomes ready*; this would let it decide *how many movie rows elapse in a
stretch of ROM code the engine does not model*. The mitigation is that the only
mutable state is the movie row cursor and nothing executes across the advance —
but a reviewer should weigh that against the alternative, which is entirely
respectable:

**Do nothing.** Leave `TestS1GhzMazeRoundTripChain.ghzMazeRoundTrip` red with the
diagnosis recorded here. The residual is fully understood, bounded, and confined
to a submission stamp; no gameplay state is wrong. Section 2 is the permanent
answer to anyone who reopens the "just derive it" line.

Rejected outright: any landed constant, per-zone table, or cycle-cost estimator
tuned until the test passes. Section 2.4 shows an estimator accurate to 1% still
fails, and "close to the ROM's but not equal" is the documented signature of a
value absorbing an error elsewhere.
