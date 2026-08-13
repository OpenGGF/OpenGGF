# Level-entry seam frame costing

**Status: REOPENED 2026-08-13 by direct measurement. The verdict below ("abandon; no
candidate throughput of any kind") is WRONG, and so is the handover audit that repeats it.**
The 63 residual rows are **`Vint_Lag` frames** — the ROM's own name for them — and the
emulator records them. They are neither incomputable nor a decompression rate: they are
main-loop-admission outcomes, exactly the contract-1 disposition the cross-game timing
contract already assigns to "long synchronous decompression or level initialization".
See [the measurement section](#2026-08-13-measured-the-63-rows-are-vint_lag-frames) at the
foot of this note. Everything between here and there is retained for its ROM citations and
its correctly-ruled-out shortcuts; its *conclusion* is superseded.

**Older status line (superseded):** diagnosed; blocked on a **capture-side** change, not an
engine one. The
original framing of this note (that it needed a suspendable level-load state machine) was
**refuted by the feasibility pass below** — the ordering is already correct and the only
missing quantity is the pre-card span's duration, for which no permitted source exists in
the current fixtures.

**Date:** 2026-08-13

## Summary

The engine's level-entry seam runs its phases in the wrong order relative to the ROM. The
gap between two recorded segments is the correct *length*, but the engine spends it
front-loaded: it runs the entire title card first and then idles. The ROM fades, clears
and decompresses first, and shows the card last.

~~Correcting this requires giving the level load a **frame cost**, which today it cannot
have.~~ **Superseded.** The seam is already split at one boundary and the ordering is
already load-then-card; see the feasibility pass. What is missing is only *how long* the
pre-card phase should take, and that duration has no rule-compliant source today. Closing
it needs the **recorder** to capture the S2 load span — a capture-side change, legitimate
under the project's rules but requiring a fixture regeneration.

## What was measured

For the level→level `level_advance` gap of `TestS2CompleteEmeraldRunChain`:

| | rows spent in the gap |
|---|---|
| recorded | 171 |
| engine | 170 |

The length is already right. `destinationReady`
(`TraceRunPlaybackCoordinator.java:396-399`) refuses admission until the shared BK2 cursor
reaches the destination's `bk2_frame_offset`, which pins it.

The *composition* differs:

| | order inside the gap |
|---|---|
| **engine** | `TITLE_CARD` for 78 rows, then 92 rows idling in `LEVEL` |
| **ROM** | fade → interrupts-off `ClearScreen`/`LoadTitleCard` → `Level_ClrRam` → level art decompression (~93 rows) → *then* `Level_TtlCard` |

ROM path: `Level:` at `s2.asm:4757-4758` (the `bset #GameModeFlag_TitleCard` that ends the
recorder's segment), `Pal_FadeToBlack` call site `:4765` and routine `:3370-3383`
(`move.w #$15,d4` + `dbf` = 22 counted `WaitForVint` iterations), the interrupts-off window
`:4767-4770`, `Level_ClrRam` `:4806`, and `Level_TtlCard` `:4914-4924`.

Consequence: the engine's title-card art edges are stamped **93 rows early**, which is
exactly the `delta=93` on every failing `run_gap.edge[N].movie_logical_frame`. Four of the
five failing axis slots on that test trace to this single defect.

## Why it cannot be fixed cheaply

> **This section's central claim is WRONG and is retained only so the error is not
> repeated.** It asserted that the title card necessarily begins at gap row 0 because
> `InitStep` carries no frame cost, and that ROM ordering therefore requires a suspendable
> cross-frame state machine across all three games. The feasibility pass below measured
> otherwise: the card is raised by a *request flag* consumed on a later frame, so the seam
> is **already** split at one boundary and the ordering is **already** load-then-card.
> Anyone building this should start from `consumeTitleCardRequest()`, not from `InitStep`.

~~The engine executes all 20 level-init steps synchronously in one loop
(`LevelManager.java:385-394`), so the title card necessarily begins at gap row 0.~~
~~Giving the seam ROM ordering therefore means converting the shared level load into a
suspendable cross-frame state machine.~~

The shortcuts below remain correctly ruled out, and are the real obstacle — both are
attempts to supply the missing *duration*:

- **A pre-card hold of 22 rows** (the `Pal_FadeToBlack` count). This is a fitted constant
  in ROM costume. The remaining ~70 rows are interrupts-off screen work plus Kosinski
  level-art decompression, which is *payload-dependent* — so any fixed boundary offset is
  wrong for a different act, and wrong for any BK2 nobody has recorded yet.
- **Retuning `FadeManager.FADE_DURATION`.** Investigated and rejected: fade duration and
  colour ramp are genuinely separate quantities and `FadeManager.java:62-66` already
  documents both correctly (22 ROM V-int iterations vs 21 colour steps). Nothing there
  needs changing.

Note also that S1's currently-green gap edges deliberately absorb 34–40 un-timed rows as
padding (`TraceRunPlaybackCoordinator.java:382-395`). A partial implementation would
disturb them.

## Things ruled out, so they are not retried

- **The gap is too short.** No — 170 against 171. Do not lengthen it.
- **The 78 is an invented duration constant.** No. Both halves are ROM-derived and cited in
  place: 52 rows of PLC drain at `move.w #6,(Plc_FramePatternsLeft).w` (`s2.asm:2202-2213`)
  over `PlrList_Ehz1` + `PlrList_Std2` with the ROM's per-entry `ceil(patterns/6)`
  quantisation, and 26 rows of `LEAVE_*_PASSES` (`TitleCardManager.java:78-84`) cited to the
  leave loop at `s2.asm:5060-5066`.
- **The 169–199 gap variance comes from the results bonus tally.** It cannot — the tally
  runs inside `Level_MainLoop` while `Game_Mode` is still `$0C`, and the capture finalises a
  segment at the first `$8C` frame and re-arms at the first `$0C` (`Level_StartGame`,
  `s2.asm:5084`). The variance is payload-dependent un-timed load cost.
- **Bumping the V-int counter by the gap's movie-row count.** This is the trap, documented
  at length in [docs/status/known-discrepancies.md](../../status/known-discrepancies.md).
  `TraceRunReplayWalker.interLevelVblankBudget` already computes the correct number and is
  gated off for S2; enabling it is one line and produces a byte-identical catastrophe
  (122,139 errors at segment 7 frame 524 on `sidekick_y`) because the counter then disagrees
  with the sidekick position-record buffer — one object on two clocks.

## A smaller shape worth one feasibility pass

Before committing to the full state machine, one variant deserves an honest look because it
threads the existing rules rather than working around them.

The seam's un-timed cost is **art decompression** — and art-decompression readiness is
precisely what the documented hardware-timing sidecar is permitted to delay
([the cross-game timing contract](2026-07-27-cross-game-hardware-timing-trace-contract.md),
hard rule 4). If the seam were split into **two or three coarse ROM-ordered phases** —
pre-card load work, then card — with the card phase gated on readiness of the engine's *own
submitted* load jobs, and that readiness timed by the sidecar, then the **ordering** comes
from ROM structure and the **duration** from the sanctioned timing port. No per-`InitStep`
frame costs and no 20-step state machine: only the seam itself becomes two suspendable
phases.

This may still founder on the same "init runs synchronously in one loop" blocker, in which
case it collapses back to the full job.

### 2026-08-13 feasibility pass: attempted, and it does not thread the rules

The pass was run and landed no code. Three findings, in increasing order of finality.

**The ordering is already correct; only the duration is missing.** The engine does not run
the card before the load. `LevelManager.loadLevel` completes every init step, and the card
is raised by a *request flag* consumed on a later frame update
(`GameLoop.java:1645-1653`, `presentPendingTitleCardDuringSuppressedRunRow`
`GameLoop.java:914-921`, both via `levelManager.consumeTitleCardRequest()`). The seam is
therefore *already* split at exactly one boundary, and the "two coarse ROM-ordered phases"
shape needs no new suspension point at all — holding the request consumption is a few
lines. Nothing here is blocked by `InitStep` lacking a frame-cost concept. The entire
93-row delta is that the load phase costs **zero** frames, so the variant reduces, with no
residue, to "how many frames does the pre-card phase cost" — the one question the fitted
constant is forbidden to answer.

**The sanctioned timing port has no data for S1 or S2, and cannot acquire any within the
existing fixtures.** `hardware_timing.jsonl` is emitted only by the S3K output set
(`tools/bizhawk-headless/src/Program.cs:25-38`: `TraceOutputFileNames` is physics/aux/
metadata, `S3kTraceOutputFileNames` adds the timing stream), and the recorder defines
exactly two kinds, `kos_module_queue` and `kos_decompression_queue`
(`HardwareTimingEventEngine.cs:18,21`). Of the 202 `hardware_timing*` files under
`src/test/resources/traces`, **zero** are under `s1/` or `s2/`. Using the port for the S2
emerald run would require extending the S2 recorder, minting a new event kind, and
republishing the fixture.

**Even with such a stream, the pre-card span fails contract 3's preconditions.** Between
`Level:` and `Level_TtlCard` the ROM performs no `WaitForVint` other than the 22 inside
`Pal_FadeToBlack`; `ClearScreen`/`LoadTitleCard` run under `move #$2700,sr`
(`s2.asm:4767-4770`) and everything through `Level_ClrRam`, the VDP setup, `Level_LoadPal`
and `Level_PlayBgm` (`:4806-4913`) runs inline. The main loop does **not** continue while
this work is pending, and no readiness gate for it is polled. The one readiness gate that
does exist — `tst.l (Plc_Buffer).w`, `s2.asm:4919` — is polled *inside* `Level_TtlCard`,
i.e. after the card object already exists, so it cannot gate card creation; it is the
52-row drain the engine already models. By the contract's own governing principle
("record the smallest scheduling outcome observable to the game… a new completion event is
reserved for work that remains pending while the main loop continues"), this span is
contract 1 (lag) / contract 2 (execution phase) work, not contract 3. Recording it as a
completion event would be recording the hardware cause, which the contract forbids.

Measuring host decompression time instead is closed by the contract's goals: "keep replay
independent of host decompression, rendering, I/O, and CPU speed."

**Verdict:** the smaller variant is not viable. The blocker is not the state machine — the
suspension point already exists — it is that no permitted source can supply the pre-card
duration. The recommendation below stands unchanged, and whoever builds this deliberately
should start from `consumeTitleCardRequest()`, not from `InitStep`.

## Recommendation

**Do not build this to make a trace test green.** The payoff is the tail of one S2 test,
and the cost is a cross-cutting change to shared level loading — the case
[CLAUDE.md](../../../CLAUDE.md) describes as broad architecture migration that must not
displace playable S3K progress.

Build it deliberately, as a designed feature, when it blocks a release gate or an S3K route.
The ROM's load-then-card ordering is **shared structure**, not an S2 nicety, so S3K seam
accuracy is likely to want the same machinery eventually. At that point the S2 emerald chain
goes green as a side effect — which is the correct order of causation: the architecture pulls
the test green, rather than the test pulling the architecture in.

## 2026-08-13, later: the fade is modelled; the residual is post-card, not pre-card

`edc396f5e` landed the ROM-derived half. **Delta 93 → 71.**

**What was wrong.** The chain's boundary wait stepped the engine through
`Pal_FadeToBlack` with the shared movie clock *frozen*. Those iterations are counted
V-blanks in the ROM — `move.w #$15,d4` with `bsr.w WaitForVint` per pass
(`s2.asm:3370-3383`, called from `Level:` at `:4765`) — and the card is not created until
`:4912`, so the fade consumes 22 movie rows before the card exists and the harness
consumed none. Instrumented, not reasoned: a per-frame probe showed 22 consecutive
iterations at frozen cursor 32761 with fade active, pinned by a stack-filtered probe to
the boundary-wait stepper rather than the gap loop. The fix introduces no constant — the
predicate is the fade's own liveness.

**A required gate, worth knowing about.** Without a source-coverage gate the same advance
reports **4** axes instead of 5 and segment 11 vanishes — because the cursor runs past rows
the `seg7_ehz2` source comparator has not consumed (it stops at 3970 of 3997), trading the
walk-failure for a non-atomic-publish error. That is hiding a failure, not fixing one.

**The card animation is NOT where the remaining rows are — this note previously implied it
was.** `Obj34`'s slide is fully derivable: zone name `xstart` = `screen_width+128` = `0x240`,
`xstop` = centred `0x120`, `anim_frame_duration` = `$1B`, and
`Obj34_MoveTowardsTargetPosition` steps `moveq #$10,d0` = 16px/pass — so 1 init pass + 26
`Obj34_Wait` skips + 18 moves = **45 iterations** to reach `titlecard_x_target`
(`s2.asm:27326-27510`). That is *below* EHZ's 52-row PLC drain, so implementing the ROM's
`x == target AND Plc_Buffer empty` exit (`s2.asm:4917-4920`) changes this seam by **zero**
rows. It would only help zones whose drain is under 45 (WFZ 28, ARZ 36, SCZ 40).

**Where the 71 actually are.** The gap's edge ledger shows edges 4–11 are the *new* level's
player art, submitted after `InitPlayers` (`s2.asm:4946`) and stamped across the 25-pass
leave loop (`:5060-5066`). `InitPlayers` sits behind `LoadZoneTiles`, `loadZoneBlockMaps`,
`LoadAnimatedBlocks`, `DrawInitialBG`, `ConvertCollisionArray` and `LoadCollisionIndexes`
(`:4939-4945`). ~~none of which contain a `WaitForVint`~~ — **CORRECTED 2026-08-13:
`LoadZoneTiles` DOES contain one** (`s2.asm:6519`, inside the `dbf` loop), so it costs
`floor(size/$1000)+1` V-blanks. The other five do not. See the derivation section below. So the residual decomposes as
~8 rows of pre-card interrupts-off `ClearScreen`/`LoadTitleCard`/`Level_ClrRam`
(`:4767-4806`) plus ~61 rows of post-card, payload-dependent level-art load.

Neither span has a counted ROM form. They stay an un-timed residual rather than a fitted
number — the same class the S1 path already absorbs as 34–40 rows
(`TraceRunPlaybackCoordinator.java:382-395`). Closing them would need the capture-side
change described above, which measurement shows is practical (recorder builds in 3s,
whole-movie replay 3m20s, fixture 34M) but is not required for any currently failing field
beyond this residual.

## 2026-08-13, final: end-anchoring is the right model and cannot be implemented legally

The remaining residual is a **phase-anchoring** error, confirmed by measurement across all
20 level-destination seams (lengths 156–198):

- title-card art sits at a fixed **33 rows from the seam START**;
- player art sits at a fixed **26–27 rows from the seam END** (len−26 on 17 of 20).

That len−26 is the ROM's 25-pass leave loop (`s2.asm:5060-5066`), which
`TitleCardManager`'s `LEAVE_*` passes already model correctly. Both ends are fixed; only
the payload-dependent middle varies. So anchoring the engine's leave phase to the seam
**end** would absorb the middle automatically, with no duration and no constant.

**It was implemented, and it works — and it is a hard-rule-4 violation.** Measured:
the eight player-art edges went **71 → 1**, segment 11 went **287 → 236**, and the
source-comparator frontier advanced 3970 → 3977, with all five axes still reported.

It is nonetheless **rejected and reverted**, because the anchor it needs is
`next.segment().bk2FrameOffset()` — a recorded BK2 frame index. Piping that into
`TitleCardManager` to drive a state transition breaks rule 4 three ways at once: it calls
a gameplay owner, it drives gameplay state, and it keys on a frame index. The
hardware-timing port does not cover it either — that exception may only release readiness
of a matching production-submitted ROM-backed art job.

**Why there is no legal variant.** A legitimate predicate must read *engine* state, as the
landed fade fix does (`FadeManager.isActive()`). But the seam's end is determined by the
duration of the ROM's level-art load, and **the engine's load is instantaneous** — so no
engine-side quantity corresponds to it. The anchor exists only in the recording.

That closes the question: the last ~71 rows cannot be recovered by anchoring, by capture,
or by derivation. They require the engine's level load to actually take time — the
frame-costed seam this document describes. The earlier escalation was correct, and is now
proven rather than asserted.

**One more rejected fake success.** Anchoring at exactly `LEAVE_PLAYABLE_PASSES` (without
the loop's fall-through pass) puts those edges at delta **0** — the acceptance target hit
exactly — but the run then collapses to **2 axes**, losing segment 11 and both
special-stage gap axes, because the walk dies earlier with "lost production ownership
before source closure". Hitting the target number while hiding three axes is not progress.

## 2026-08-13: blind rate derivation — ABANDON, with one salvage

Every rate below was read from the disassembly and written down before any comparison.
*Disclosure:* the seam lengths were already stated in this note, so the derivation was not
blind in the strict sense. Evidence against fitting: the one new derivable component lands
at **8 rows**, an order of magnitude below the 63 it would have needed to "explain" the
residual. A fitter would not have produced 8.

| component | throughput + citation | frames | bucket |
|---|---|---|---|
| `Pal_FadeToBlack` | `move.w #$15,d4` + `dbf`, one `WaitForVint`/pass (`s2.asm:3370-3383`) | 22 | ROM loop — **landed** `edc396f5e` |
| `Level_TtlCard` PLC drain | `move.w #6,(Plc_FramePatternsLeft).w` (`:2202-2213`), per-entry `ceil(patterns/6)` | 52 (EHZ; zone-keyed 28–81) | ROM immediate — **already exact** |
| **`LoadZoneTiles`** | **counted `dbf` with one `WaitForVint` per `$1000`-byte DMA chunk (`s2.asm:6505-6523`, `bsr.w WaitForVint` at `:6519`); `rol.w #4,d7 / andi.w #$F,d7` ⇒ `floor(size/$1000)`, loop runs `d7+1`** | **EHZ 8, MCZ 8, ARZ 8, MTZ/WFZ/HTZ/CNZ/CPZ/DEZ/SCZ 7, OOZ 6** | **ROM loop — NEW, derivable** |
| leave loop | `s2.asm:5060-5066` | 26 | ROM loop — already modelled |
| `ClearScreen` DMA fill | VDP DMA fill rate (hardware reference, not opened in-repo) | 0.71 | hardware rate — sub-frame |
| `LoadTitleCard` `NemDec`, `Level_ClrRam`, `loadZoneBlockMaps`, `LoadAnimatedBlocks`, `DrawInitialBG`, `ConvertCollisionArray`, `LoadCollisionIndexes` | **68000 cycle-bound; no ROM quantum, no hardware rate, no `WaitForVint`** | **not derivable** | **CPU-bound** |

**Derived total (EHZ): 22 + 52 + 8 + 26 = 108. Recorded seam: 171. Shortfall 63.**

That is not a near miss with a rate to nudge — **the 63 rows have no candidate throughput of
any kind.** They are entirely CPU-cycle-bound decode and array conversion, with no
`WaitForVint` and no polled readiness gate between `Level:` and the leave loop other than
the `Plc_Buffer` test already modelled.

### The sidecar cannot take them either

Verified this round: `hardware_timing.jsonl` is emitted only by the S3K path
(`tools/bizhawk-headless/src/Program.cs:25-38`); kinds are exactly `kos_module_queue` and
`kos_decompression_queue` (`HardwareTimingEventEngine.cs:18,21`); of 202 such files under
`src/test/resources/traces`, **zero** are under `s1/` or `s2/`.

More decisively, these jobs **fail contract 3 on preconditions**: the ROM exposes no
readiness value for `KosDec`/`NemDec` here — each is a synchronous `bsr` inside `Level:`
with no queue and no count word — and the main loop is not running at all during them
(interrupts masked at `s2.asm:4767`). The contract's own inventory pre-decides this twice:
*"Long synchronous decompression or level initialization … Lag … S1/S2 substantially
covered"* and *"Nemesis, Enigma, Saxman, raw map decompression … Lag … **No codec-specific
trace authority**"*.

So this is **not** a capture-side regeneration item — minting an S2 event kind here would
claim authority the contract explicitly denies — and emphatically not a licence to invent a
decompression rate. The authorised regeneration cannot be spent on this.

**Verdict: the frame-costed seam model is abandoned.** One good component may not carry a
bad one. The `LoadZoneTiles` V-int cost is landed on its own merits as ROM accuracy, closing
8 of the 71; the remaining 63 stay an un-timed residual, the same class S1 already absorbs
as 34–40 rows.

## 2026-08-13, measured: the 63 rows are `Vint_Lag` frames

The section above concludes the residual has "no candidate throughput of any kind". That is
false, and the error was reasoning about the seam instead of observing it. A throwaway probe
was added to the native S2 run recorder — logging `(frame, Game_Mode, IGpgxHost.IsLagged)`
for every physical emulator frame — and run against the canonical emerald BK2. It is not
committed; it existed only to answer this question.

### The seam, decomposed

`seg4_ehz1` closes at BK2 row 32760; `seg5_ehz2` opens at 32931. Every frame between reads
`Game_Mode = $8C`. Run-length encoding the probe over that span, and attributing each run to
the ROM:

| BK2 rows | n | lag | ROM span |
|---|---|---|---|
| 32761–32783 | 23 | no | `Pal_FadeToBlack`, `move.w #$15,d4` + `WaitForVint`/pass (`s2.asm:3370-3383`) |
| 32784–32794 | **11** | **YES** | `move #$2700,sr`; `ClearScreen`; `LoadTitleCard` (`s2.asm:4767-4770`) |
| 32795–32847 | 53 | no | `Level_TtlCard` loop, one `WaitForVint`/pass, PLC drain (`s2.asm:4914-4920`) |
| 32848–32861 | **14** | **YES** | `Hud_Base`, `PalLoad_ForFade`, `LevelSizeLoad`, `DeformBgLayer`, `Horiz_Scroll_Buf` clear (`s2.asm:4923-4936`) |
| 32862–32869 | 8 | no | `LoadZoneTiles`, one `WaitForVint` per `$1000`-byte chunk (`s2.asm:6519`) |
| 32870–32906 | **37** | **YES** | `loadZoneBlockMaps`, `LoadAnimatedBlocks`, `DrawInitialBG`, `ConvertCollisionArray`, `LoadCollisionIndexes`, `WaterEffects`, `InitPlayers` (`s2.asm:4938-4946`) |
| 32907–32930 | 24 | no | leave loop, `VintID_TitleCard` + `WaitForVint`/pass (`s2.asm:5060-5066`) |

**Lag frames: 11 + 14 + 37 = 62. The residual this note abandoned is 63.**

Two independent cross-checks fall out of the same table and were not used to build it:

- the 8 non-lag rows at 32862–32869 are exactly the `LoadZoneTiles` count `8695c029e`
  derived for EHZ from `ArtTile_ArtKos_NumTiles_*`, arrived at from the ROM with no
  reference to any recording;
- the 53 non-lag rows at 32795–32847 are the 52-row PLC drain the engine already models.

The failing edges land on the run boundaries, not near them — and this is checkable without
rerunning anything, straight out of the committed
`run_manifest.json` `dynamic_art_gap_transitions`, whose `movie_logical_frame` is in the same
absolute BK2 coordinates as the probe:

| edge | frame | phase/owner | falls on |
|---|---|---|---|
| 0,1 | 32760 | submitted `tails` | last recorded gameplay row |
| 2,3 | **32794** | completed | **last frame of the 11-frame masked `LoadTitleCard` lag run** |
| 4,5 | **32905** | submitted `sonic`/`tails` | **penultimate frame of the 37-frame `InitPlayers` lag run** |
| 6,7 | **32906** | completed | **last frame of that lag run** |
| 8,9 | 32921/32922 | submitted/completed | inside the leave loop (non-lag) |
| 10,11 | 32929/32930 | submitted/completed | inside the leave loop (non-lag) |

`run_gap.edge[2]`/`[3]`
(title-card art) is recorded at **32794** — the last frame of the masked `LoadTitleCard`
run. `edge[4]`–`[7]` (player art, submitted by `InitPlayers`) are recorded at **32905/32906**
— the last frames of the 37-frame lag run that ends at `InitPlayers`. The lag structure does
not merely total 63; it places every edge.

### A second seam, and the cross-check that proves the probe reads ROM structure

The probe was repeated over `seg7_ehz2 -> seg8_cpz1` (61028 → 61206, length 178), a
different zone with a different art payload:

| BK2 rows | n | lag | same ROM span as above |
|---|---|---|---|
| 61029–61051 | 23 | no | `Pal_FadeToBlack` |
| 61052–61062 | **11** | **YES** | masked `ClearScreen` / `LoadTitleCard` |
| 61063–61124 | 62 | no | `Level_TtlCard` PLC drain |
| 61125–61138 | **14** | **YES** | `Hud_Base` … `Horiz_Scroll_Buf` clear |
| 61139–61145 | **7** | no | `LoadZoneTiles` |
| 61146–61181 | **36** | **YES** | `loadZoneBlockMaps` … `InitPlayers` |
| 61182–61205 | 24 | no | leave loop |

Total lag **61** against EHZ's 62. Note what varies and what does not. The two fixed-code
lag runs are **11** and **14** at both seams. The payload-dependent runs move: the PLC drain
53 → 62, the art-conversion lag run 37 → 36.

And `LoadZoneTiles` reads **8 at the EHZ seam and 7 at the CPZ seam** — which is exactly the
per-zone table `8695c029e` derived from `ArtTile_ArtKos_NumTiles_*` and
`floor(size/$1000)+1`, with no reference to any recording (EHZ 8, CPZ 7). Two independent
routes to the same two numbers is the check that the probe is reading the ROM's scheduling
and not an emulator artefact.

The manifest's edges follow the same rule at this seam: card art completes at **61062**, the
last frame of the 11-frame masked run; player art is submitted at **61180** and completes at
**61181**, the last frame of the 36-frame `InitPlayers` run. Both anchors are fixed
(`+34` from the seam start, `len-26` from its end) and the middle is payload-dependent —
**111 rows at the EHZ seam, 118 at the CPZ seam** — which is why no constant can serve and
why the middle must come from the recording as an admission outcome.

### Why the flag discriminates, from the ROM

`V_Int` branches to `Vint_Lag` whenever `Vint_routine` is zero (`s2.asm:481-484`) and writes
`VintID_Lag` back after every dispatch (`s2.asm:501`), so a routine set by `WaitForVint`
runs exactly once. Straight-line 68K code between `WaitForVint` calls therefore takes the
`Vint_Lag` path, and neither `Vint_Lag` nor its in-level branch calls `ReadJoypads`
(`s2.asm:529-583`) — whereas `Vint_TitleCard` does (`s2.asm:1005-1008`). BizHawk's
`IInputPollable.IsLagFrame` is "no controller poll this frame", so the emulator flag and the
ROM's own `Vint_Lag` classification coincide **by construction**. This is not an emulator
artefact standing in for game behaviour; it is the game's own scheduling outcome.

`VintRet` still does `addq.l #1,(Vint_runcount).w` (`s2.asm:505-506`) on the lag path, which
is what replay must advance on such a row — and is the principled form of the
`interLevelVblankBudget` trap, which advances the same counter by a frame-index arithmetic
without spending the rows.

### Which precondition actually fails

Against the contract's five lag-sufficiency preconditions
([the cross-game contract](2026-07-27-cross-game-hardware-timing-trace-contract.md), "S1/S2
lag-frame coverage audit"):

1. **raw capture includes every physical emulator frame — FAILS.** `S2RunCaptureRunner`
   `continue`s without writing a row on every frame where no segment is armed
   (`tools/bizhawk-headless/src/Recording/S2RunCaptureRunner.cs`, the `if (!state.Started)`
   arm gate), so all 170 seam frames are recorded nowhere.
2. **the lag flag distinguishes a serviced interrupt from an executed gameplay loop —
   FAILS, and independently of (1).** `S2TraceCsvWriter` writes the level `lag_counter`
   column as a literal `Hex4(0)` placeholder; measured over `seg4_ehz1`'s 1288 rows the
   column has exactly one distinct value, `0000`. S2 **special-stage** rows carry a real
   `lag` column, so the discriminator exists in the harness and is simply not wired to the
   level writer. Both failures are capture defects, not modelling impossibilities: the
   underlying flag does discriminate, as measured above.
3. **replay advances the required VInt-owned counters and queues on that row** —
   implementable; `Vint_runcount` advances (`s2.asm:505-506`), `Level_frame_counter` does
   not (the level main loop owns it).
4. **input sampling/reuse follows the game's lag path** — holds; `Vint_Lag` performs no
   `ReadJoypads`, so no controller word is republished on those rows.
5. **no ordinary main-loop routine polls a still-pending readiness value across multiple
   non-lag rows** — holds. The one polled gate in the seam is `tst.l (Plc_Buffer).w`
   (`s2.asm:4919`) inside `Level_TtlCard`, and it is already reproduced deterministically as
   the 52-row drain.

So lag **is** sufficient here, and the two failures are both in the recorder.

### What this does and does not license

The consumed quantity is one bit per physical frame: *did the main loop run*. It carries no
position, speed, object state, or any physics/aux comparison value, and it changes only
*when* engine-created work becomes ready — the sanctioned shape. It is categorically not the
reverted end-anchoring attempt, which fed a recorded **frame index**
(`next.segment().bk2FrameOffset()`) into `TitleCardManager`, a gameplay owner. A per-seam
run-length census of that bit is *observation, not authority*, which is precisely what the
superseded S1 note
([2026-08-10-s1-pre-main-loop-load-span-timing-extension.md](2026-08-10-s1-pre-main-loop-load-span-timing-extension.md))
recommended for the same problem class: "Publish the tail rows, or a movie-wide V-int
census, as recorded fixture data."

No completion event, no new hardware-timing kind, and no codec authority is involved, in
line with the contract's inventory rows for synchronous decompression.

### Remaining work, unstarted

1. Populate the S2 level `lag_counter` column from `IGpgxHost.IsLagged` instead of the
   placeholder, and emit the unarmed seam frames' admission outcome (the smallest form is a
   per-transition run-length census in `run_manifest.json`; the fuller form is rows).
2. Regenerate the S2 emerald and halfpipe fixtures, payloads compressed, `trace_schema: 5`.
3. Spend the seam rows in replay as lag rows: advance `Vint_runcount`, service the V-int
   equivalent, run no gameplay, follow the lag input path, and hold the title-card request
   consumption behind the recorded admission outcomes rather than any constant.

Nothing above was implemented this round; only the measurement was taken. Note also that the
handover's "four of five axes trace to one defect" does not reproduce at `8c9adc250`: the
`ss_4 -> seg6_ehz2` axis is a 1-row offset and the `ss_5 -> seg7_ehz2` axis is an edge-count
and content/ordering mismatch (16 vs 18), neither of which is this seam.

## 2026-08-13, implemented: the admission census lands; the seam does not close

The two capture defects above are fixed, the emerald fixture is regenerated, and replay now
spends the recorded lag rows. Measured outcome: the seam moves substantially and does not
close, for a reason the measurement makes precise.

### What landed

1. **Capture (precondition 1).** `S2RunCaptureRunner` now records
   `IGpgxHost.IsLagged` for *every* physical emulator frame, before any arm gate, indexed by
   BK2 movie row. At run end each manifest transition gains
   `gap_admission_runs`: a run-length census, alternating and starting NON-lag, over the rows
   strictly between the source segment's end and the destination segment's first recorded
   row. Only lengths are emitted — never a row index.
2. **Replay (contract 1).** `AbstractRunChainTest.admitLevelWhenReady` expands the census
   (its origin is `destinationOffset - sum`, so no recorded index is consumed) and, on a lag
   row, runs no gameplay lifecycle and only advances the object-visible V-blank counter —
   the ROM's `addq.l #1,(Vint_runcount).w` at `VintRet` (`s2.asm:505-506`), which executes on
   the lag path too, while `Level_frame_counter` (`Level_MainLoop`, `s2.asm:5092`) does not.

The regenerated capture **independently reproduces this note's hand-taken decomposition**,
which is the strongest available check that the census reads ROM scheduling:

| transition | census | lag |
|---|---|---|
| `seg4_ehz1 -> seg5_ehz2` | `[23, 11, 53, 14, 8, 37, 25]` | 62 |
| `seg7_ehz2 -> seg8_cpz1` | `[23, 11, 62, 14, 7, 36, 25]` | 61 |

The two fixed-code lag runs are 11 and 14 at both seams; `LoadZoneTiles` reads 8 at EHZ and
7 at CPZ, matching the per-zone table `8695c029e` derived from `ArtTile_ArtKos_NumTiles_*`
with no reference to any recording.

Every one of the 70 payload files in the regenerated capture is **byte-identical** to the
committed fixture; only `run_manifest.json` (the new field) and `recording_date` differ. The
census is therefore purely additive: no segment offset or row count moved.

### The measured result, both directions

| edge | expected | before | after |
|---|---|---|---|
| `run_gap.edge[2]/[3]` (title-card art) | 32794 | 32804 (**+10**) | 32815 (**+21**) |
| `run_gap.edge[4]`–`[7]` (player art) | 32905/32906 | 32842/32843 (**-63**) | 32867/32868 (**-38**) |

Segment 11 physics errors 252 → **236**; the `seg7_ehz2` walk-failure comparator cursor
3975 → 3977. The axis count stays **5** with the same five fields — no comparator was
starved, which is the failure mode this note warned about twice.

### Why it does not close, precisely

Lag insertion delays the engine's subsequent work by the cumulative lag ahead of it, and
nothing else. That is right for the player-art edges, which sit after all 62 lag frames, and
it recovered 25 of their 63 rows. It is wrong for the title-card edges, because the ROM
submits that art **inside** the 11-frame masked `LoadTitleCard` run — on a frame whose main
loop never ran — whereas the engine's load is instantaneous and its submission lands on the
next row it is allowed to run on, so those 11 rows push it further away rather than closer.

Contract 1 forbids the fix: a lag row runs no gameplay, and the cause of the missed frame is
deliberately absent. Making the level-load pipeline progress *during* the lag rows is exactly
the frame-costing of the level load this note recommended deferring — but the census now
supplies the missing input it would need, which the earlier sections concluded did not exist.
