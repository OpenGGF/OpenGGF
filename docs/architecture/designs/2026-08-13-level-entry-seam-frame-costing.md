# Level-entry seam frame costing

**Status:** diagnosed; blocked on a **capture-side** change, not an engine one. The
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
(`:4939-4945`) — **none of which contain a `WaitForVint`**. So the residual decomposes as
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
