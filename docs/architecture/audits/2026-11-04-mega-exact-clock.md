# Mega chain: exact S3K bonus-boundary clock

Base: `fcea23818` with the recorder census `d89e30b8d` (TraceChaser `a9f8b94`)
merged (`a548d83b3`). Branch: `bugfix/ai-trace-mega-exact-clock`. Follow-up to
the [bonus-entry tail audit](2026-11-03-s3k-bonus-entry-tail.md).

**Result: the clock is exact, and Slots validates.** Mega's admission clocks
equal the recording at every bonus boundary the walk reaches. Slots' gameplay
rows all match, and its stage_exit walk failure clears. The walk now reaches
`aiz_3`, whose first divergence at row 105 is a new gameplay frontier and is
not clock-related.

## Change (`dacdaafec`, test harness only)

1. Re-applies the withheld represented bonus-entry tail and the bonus-return
   lag walk (`d8baf2675`).
2. Consumes the new per-boundary `gap_admission_runs` at S3K bonus entries
   and returns, which `expandGapAdmissionCensus` previously read only on
   level-to-level and special-stage-return gaps. The census is driven when
   the walk stands on the gap's first row (so the recorded tail was fully
   walked) and every census row is non-admitted. Each such row services only
   its V-int, under CONTRACT 1. A census containing an admitted row is left to
   the existing re-anchor, because its owning loop is not identified there.

The re-recorded Mega run carries `[0, 1]` at each bonus boundary: the
unrecorded segment-switch frame.

## Witness (TestS3kMegaRunChain)

| Admission | Native | Before (`a548d83b3`) | After (`dacdaafec`) |
| --- | ---: | ---: | ---: |
| Gumball | 5529 | 5520 | **5529** |
| AIZ return (`aiz_2`) | 6957 | 6927 | **6957** |
| Slots | 9097 | 9049 | **9097** |
| Slots return (`aiz_3`) | 10295 | not reached | **10295** |

The "before" clocks were measured on the equivalent tree `bb9403ff3`, which is
develop plus the release VInt. Neither `fcea23818` nor the unconsumed census
changes the clock, and Slots' error list here is identical (7947@232).

| Segment | Before | After |
| --- | --- | --- |
| 0, AIZ (entry tail newly compared) | 0 | 12, first 4568 queue |
| 1, Gumball | 12 | 12 (unchanged) |
| 2, returned AIZ (entry tail newly compared) | 0 | 12, first 2025 queue |
| 3, Slots | 7947, first 232 `rings` | **14, first 1052 queue; all gameplay rows match** |
| 4, `aiz_3` | not reached | 11194, first 105 `x` |

The walk failure was "Interior exit boundary (stage_exit) was never observed",
the early cage release. It is now an `IllegalStateException` (duplicate
hardware service boundary at `aiz_3` raw 1124), which comes after the row-105
divergence.

## Remaining errors

- **Entry and return tails (12 each).** Title KosM and terrain queue state one
  row early. This is the Level-setup lag V-int (`Clear_DisplayData` under
  `#$2700`), proven cycle-bound in the
  [load-time trial](2026-11-03-load-vint-trial.md).
- **Slots row 1052.** Two of its 14 errors are camera: the return boundary's
  held source camera is `$0456/$033D` against ROM `$04D2/$02C8`. The engine's
  bonus camera at exit differs from the ROM's last bonus camera. This is a
  small separate item.
- **`aiz_3` row 105.** Rows 0-104 match. The ROM keeps running on the ground
  at `$03D7` (x speed `$07F5`) and collects a ring (87). The engine's player
  is airborne at x `$1050` with zero x speed and 86 rings, pinned there by an
  object. This is a gameplay frontier for the next lane.

## Validation

Full `-Ptrace-replay`, `-Ptrace-replay-r7` and `-Ptrace-segments` runs were
made on `a548d83b3` and on `dacdaafec`, comparing full messages and every
report:

| Profile | Before | After |
| --- | --- | --- |
| r6 | 887 tests: 14F 0E 7S | 889 tests (2 new unit tests pass), otherwise identical |
| r7 | 119 tests: 81F 2E | identical except Mega |
| seg | 70 tests: 46F 2E | identical |

Every other S3K chain is identical. A temporary comparator dump (since
removed) gives the Mega gate attribution:

- **Slots.** The 14 remaining errors are on rows 1052 and 1151. Both rows
  already carried errors before, when Slots' errors ran from 232 to 1151.
  The first error moves later, from 232 to 1052. Nothing is added on a clean
  row.
- **Segments 0 and 2.** The 12 errors each are only on newly compared
  post-reload tail rows (4568/4638 and 2025/2108). They are the same two-row
  queue pattern as segment 1's existing 1300/1380. Previously compared rows
  stay clean.
- **`aiz_3`.** This segment is newly reached coverage. Rows 0-104 match.
