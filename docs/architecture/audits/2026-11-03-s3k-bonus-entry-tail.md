# S3K bonus-entry tail: represented readiness and the Slots witness

Historical investigation preserved from `ca10bc6cd`. The later
[exact-clock audit](2026-11-04-mega-exact-clock.md) records the completed
recorder census and re-admission of this tail; the withheld status below
is the result at this investigation's original base.

Base: `86d173323` with develop `32d4e9aae` merged (`a8318f3b6`). Branch:
`bugfix/ai-trace-s3k-bonus-entry-tail`. This follows the
[gap lag census](2026-11-03-gap-lag-census.md). The goal was to close the
S3K bonus-entry gap, where the engine finishes about five native rows early in
the parked-cursor gap while the ROM is still in `loc_7870`.

**Result.** Representing the entry tail is inside the contract and makes the
entry clocks native apart from one unrecorded frame per boundary. Together with
the withheld bonus-return lag walk, Mega's Slots clock deficit falls from 50 to
3. The Slots witness still moves its first error earlier, so both stay
withheld. Evidence is in `d8baf2675`; `bb9403ff3` withholds it.

## Contract fit

No amendment is needed. The level segment's rows after its reload
(`levelLoopRowCount` to `rowCount`, split at the recorded `zone_act_state`
reload bit) are the interior's own `Level:` entry. They lie inside the source
segment's recorded coverage, and its `hardware_timing.jsonl` carries their
completion edges, for example Gumball entry rows 4570-4577 and 4640-4648.

Driving those rows as represented rows keeps row authority active, so work the
entry submits waits for its recorded edge. This is the existing rule
"membership is fixed at submission" (2026-08-21 section of the
[timing contract](../designs/2026-07-27-cross-game-hardware-timing-trace-contract.md)).
Readiness is released by kind, ordinal, fingerprint and boundary. No gameplay
input is added, and nothing keys on frame, zone, route or game. The represented
bonus return already worked this way over its own tail.

On develop those entry edges go unconsumed: the source closes at its reload
row. Recorded rows left after the engine's title release are admitted with the
recorded-lag walk (CONTRACT 1, `da4970e73`).

## Measured witness (TestS3kMegaRunChain)

Admission clocks, against native 5529 / 6957 / 9097:

| Variant (all measured on develop `32d4e9aae` content) | Gumball | AIZ return | Slots | Slots first non-camera mismatch |
| --- | ---: | ---: | ---: | --- |
| Develop | 5519 | 6925 | 9046 | 268 `y_speed` (7891) |
| Delivered tip `bb9403ff3` (+ release VInt, special-stage hold) | 5520 | 6927 | 9049 | 232 `rings` 75/76 (7947) |
| + represented entry tail | 5528 | 6935 | 9074 | 220 `x_speed` (7974) |
| + entry tail + return walk (`d8baf2675`) | 5528 | 6955 | 9094 | 252 `x_speed` (8129) |

With both, each boundary is one V-int short. That is the segment-switch frame,
which the S3K recorder does not write. The deficit is no longer the load.

Develop's `69ec429fc` (blocking-fade ownership at bonus entry) already adds
one V-int per bonus entry. On current develop, the accepted release VInt
therefore produces exactly the clock pattern of the rejected `32f9ce34f` trial,
and the same Slots result, 232 `rings`. This is earlier than develop's 268.

**The wave-5 batch hits this gate on Mega Slots.** All other r6/r7/seg outcomes
and reports are identical to develop.

Across every variant measured, the Slots first error does not track the
residual deficit: 51 gives 268, 50 gives 300, 48 gives 232, 30 gives 236,
23 gives 220, and 3 gives 252.

Slots' first error still moves earlier than 300, so the kill condition holds:
the return walk stays withheld. The entry tail moves it earlier on its own
too, so it is withheld as well. The reel seed reads the clock at Slots
initialization. Any clock that is still short (by 3, 23 or 50) gives a
different wrong payout, so this witness cannot rank partial corrections. It
can validate only an exact clock.

## Other effects (full profiles, d8baf2675 against develop)

- r6/r7/seg test outcomes are identical (889 includes 3 new unit tests; 119;
  70).
- Every other S3K chain with bonus entries is unchanged: Knuckles SE, Tails
  full chain, S+T complete and `s3k-multibonus`.
- The S2/KiS2 deltas are the special-stage-return hold already accepted from
  `86d173323`.
- Mega segment 0 gains 16 errors (first 4568 `x`, ROM `$0000`, engine
  `$0100`). Segment 2 gains 16 (first 2025 `x`, ROM `$0000`, engine `$0460`)
  and becomes a chain axis.
- These errors are on newly compared tail rows. From R+1 the ROM has cleared
  object RAM (`loc_60DE`, sonic3k.asm:7649-7651), while the engine has
  already built the destination player at its load step.
- The same class already exists on develop at Gumball row 1300: 16 errors
  over the represented return tail.
- No row where engine and ROM agreed gains an error.

## Follow-ups

1. **Segment-switch frame (recorder).** Write the frame between S3K segments,
   or publish a per-boundary census, so its main-loop admission is recorded.
   With it, the Mega clock becomes exact at every boundary, and the Slots
   witness can validate `d8baf2675`.
2. **S1 and S2 halfpipe census (recorder).** Re-record these fixtures with
   `gap_admission_runs`.
3. **Destination player placement in the entry tail.** The engine builds the
   destination player at its load step; the ROM spawns it in `loc_6468`
   (sonic3k.asm:7880-7915). This decides whether the 16-row tail errors are an
   engine ordering defect or acceptable choreography.

## Validation

Full `-Ptrace-replay`, `-Ptrace-replay-r7` and `-Ptrace-segments` runs were
made on develop `32d4e9aae`, evidence `d8baf2675` and tip `bb9403ff3`. The
admission clocks come from a temporary print, since removed. Develop gives
886/119/70 tests: 14F 1E 7S / 84F 11E / 46F 8E. The tip adds one unit test
(887) and otherwise has the same outcomes.

The tip differs from develop only in the S2/KiS2 special-stage-return axes
(accepted in `86d173323`) and Mega Slots (above). The six classes from the
rejected title deferral stay green in every variant: AIZ trace, the AIZ, HCZ
and MGZ slices, SOZ complete run and reference closure.
