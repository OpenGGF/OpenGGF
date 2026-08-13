# S2 emerald chain — handover

**Date:** 2026-08-13
**Status:** halfpipe chain green; emerald chain red with one root cause, boundary proven.
**Asks a decision.** Everything reachable within the project's rules has been done.

## Summary

`TestS2EhzHalfpipeRoundTripChain` is **green** and stayed green through every change.

`TestS2CompleteEmeraldRunChain` reports **5 axis slots**, and four of them trace to a single
defect: the engine's level-entry seam completes its level load instantly where the ROM
spends real frames on it, so the destination attaches early and every art edge after it is
stamped early.

Two ROM-derived fixes landed today, closing 30 of the 93 rows:

| commit | mechanism | effect |
|---|---|---|
| `edc396f5e` | `Pal_FadeToBlack`'s 22 counted V-blanks (`s2.asm:3370-3383`) were stepped through with the movie clock frozen | delta 93 → 71 |
| `8695c029e` | `LoadZoneTiles` spends one V-blank per `$1000`-byte DMA chunk (`s2.asm:6519`); the engine spent none | delta 71 → 63; segment 11 **287 → 252** |

Neither introduces a constant. The second cross-checks against the ROM's own
`ArtTile_ArtKos_NumTiles_*` constants across all eleven zones.

## The remaining 63 rows are unreachable within the rules

Every avenue was measured, not assumed:

- **Derivation — abandoned on evidence.** A blind rate derivation totals 108 frames against
  a recorded 171. The 63-row shortfall is entirely 68000 cycle-bound Kosinski/Nemesis decode
  and array conversion: no ROM immediate, no ROM loop structure, no documented hardware rate.
  There is no rate to nudge. Full component table in
  [the design note](../designs/2026-08-13-level-entry-seam-frame-costing.md).
- **The timing sidecar — does not and may not cover it.** The stream is S3K-only today (zero
  of 202 files under `s1/` or `s2/`), but more decisively these jobs fail contract 3's
  preconditions: a synchronous `bsr` with no queue and no count word exposes no readiness
  value, and the main loop is not running with interrupts masked. The contract's own
  inventory assigns long synchronous decompression to the **lag** contract and states there
  is **no codec-specific trace authority**. The authorised fixture regeneration cannot be
  spent here.
- **End-anchoring — works, and is illegal.** Anchoring the leave phase to the seam end gives
  edges **71 → 1** and segment 11 **287 → 236**. It was reverted: its anchor is
  `bk2FrameOffset()`, a recorded frame index, and driving `TitleCardManager` from it breaks
  hard rule 4 three ways (gameplay owner, gameplay state, frame index). No legal variant
  exists, because a legitimate predicate must read engine state and the engine's load is
  instantaneous — the anchor exists only in the recording.

## The decision

The only remaining path is **frame-costing the level load** so the engine's seam takes as
long as the ROM's. That is a cross-cutting change to shared level loading, and
[CLAUDE.md](../../../CLAUDE.md) directs that broad architecture migration must not displace
playable S3K progress.

**Recommendation:** build it when it blocks an S3K route or a release gate, not to green a
trace test. The ROM's load-then-card ordering is shared structure, so S3K seam accuracy is
likely to want the same machinery; at that point the emerald chain closes as a side effect.
The architecture should pull the test green rather than the test pull the architecture in.

**One correction to an earlier version of that argument:** the blocker is *not* a suspendable
20-step state machine. The seam is already split at one boundary — `LevelManager.loadLevel`
completes every init step and the card is raised by a request flag consumed on a later frame
via `consumeTitleCardRequest()`. Start there, not at `InitStep`.

## Traps recorded for whoever picks this up

- **The one-line trap.** `TraceRunReplayWalker.interLevelVblankBudget` already computes the
  correct movie-row count and is gated off for S2. Enabling it makes the counter say ~190
  while the sidekick position-record buffer says 78 — one object on two clocks — and
  produces a byte-identical **122,139 errors at segment 7, frame 524, `sidekick_y`**. It has
  been produced five times under four descriptions. The number is right; the mechanism is
  fatal.
- **Two comparator-starvation false wins.** One reported **4** axes instead of 5; the other
  hit delta **0** exactly while hiding segment 11 and both special-stage axes. Fewer axes is
  not better. If an axis disappears, name the field that now matches.
- **`Sonic2SpecialStageBootstrapCadenceTest`** is red (7F/1E) because it encodes a superseded
  model, **not** because the engine is wrong — the recordings outrank it and three engine
  "fixes" are already rejected there. Details are in the class javadoc.
- **Do not re-run:** the act-advance code-path hypothesis, the event-count factoring of the
  special-stage deficit, the "invented 78-frame constant" reading, the bonus-tally
  explanation of seam variance, or a verbatim re-land of the LBZ tube elevator ordering
  (it regresses `TestS3kLbzCompleteRunTraceReplay`; re-derive against the current
  `ObjectExecutionController` contract instead).

## Also from this session

- `bad614ae6` — corrected a genuinely wrong test expectation (the ROM records the full
  `Ctrl_1_Logical` word, `s2.asm:69070`).
- Merge `1e576c5bd` **audited in full** and closed: one unre-landed victim, not the
  open-ended risk it had been.
- Entry 27 of [known-discrepancies](../../status/known-discrepancies.md) rewritten — three
  of its claims were retracted after measurement, including a "32-tick loss" that is not in
  the recording at all.
