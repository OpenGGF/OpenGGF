# Level-entry seam frame costing

**Status:** diagnosed, not built. Escalated — the decision is a priority call, not a
technical one.

**Date:** 2026-08-13

## Summary

The engine's level-entry seam runs its phases in the wrong order relative to the ROM. The
gap between two recorded segments is the correct *length*, but the engine spends it
front-loaded: it runs the entire title card first and then idles. The ROM fades, clears
and decompresses first, and shows the card last.

Correcting this requires giving the level load a **frame cost**, which today it cannot
have. That is a cross-cutting change, and this document exists so the decision to make it
is taken deliberately rather than as a side effect of chasing a trace test.

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

The engine executes all 20 level-init steps synchronously in one loop
(`LevelManager.java:385-394`), so the title card necessarily begins at gap row 0.
`InitStep` is `record(String, String, Runnable)` — **there is no frame-cost concept
anywhere in the type**.

Giving the seam ROM ordering therefore means converting the shared level load into a
**suspendable cross-frame state machine**, used by all three games, every test, the editor
and level select.

Two tempting shortcuts are both wrong:

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
case it collapses back to the full job. It has not been attempted.

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
