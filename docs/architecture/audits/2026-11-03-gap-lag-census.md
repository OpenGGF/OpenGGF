# Recorded lag admission across chain gaps

Base: `origin/develop` at `e07b75a90`, merged with the load-VInt trial
(`d45daa5a9`). Branch: `bugfix/ai-trace-gap-lag-census`. Follow-up to the
[load-time V-int trial](2026-11-03-load-vint-trial.md). The sanctioned route is
CONTRACT 1 of the
[hardware-timing contract](../designs/2026-07-27-cross-game-hardware-timing-trace-contract.md#1-main-loop-admission):
a recorded per-row lag outcome selects whether the main loop ran. Admission
releases or holds frames of an existing ROM loop. It supplies no gameplay value
and keys on no frame, zone, route or game.

## Where a recorded lag outcome exists

| Gap kind | Fixtures | Recorded lag source | Result |
| --- | --- | --- | --- |
| Level to level | S2 complete, KiS2 | `gap_admission_runs` census | Already consumed by `admitLevelWhenReady` |
| Special-stage return | S2 complete, KiS2 | `gap_admission_runs` on every `stage_exit` | **Implemented**: player-art hold added |
| Special-stage return | S2 halfpipe | `gap_admission_runs` since `0b380c382` | Consumed by the same walk. 1-row residual is the title-card exit (see below) |
| Special-stage return, level to level, terminal tail | S1 complete, S1 maze | Census only on the 1-row entry and exit gaps; none where the load runs | Not expressible. The recorder must publish a census for every inter-segment gap (see below) |
| Bonus/special entry and return, S3K | All five S3K chains | No census. Interior tail rows carry `Lag_frame_count` | Return tail: expressible, implemented then withheld (see below). Entry and switch frame: not expressible |

## Special-stage returns (implemented)

The return walk already consumed the census for VInt-only lag rows. It did not
apply the level-to-level ordering: a fresh `Level:` load creates the playables
at its `InitPlayers` call (`Level`, s2.asm:4946), so the destination's first player-art decision
belongs to the end of the synchronous load. The walk now arms the same
`DynamicArtLifecycleService` hold and releases it on that census row.

The load row is located structurally, as the last non-admitted run longer than
one row. A return census can carry lone lag rows after the load. These are
passes of the title-card leave loop (s2.asm:5060-5066) that overran their wait,
and taking the very last lag row misplaced the release:

- `ss_6`: release at the gap end. 4 edges moved to destination row 0 and a new
  segment-15 axis appeared.
- `ss_7`: 7 rows late.

A single overrun pass yields one lag row. The load spans consecutive V-ints.

Witness result (TestS2CompleteEmeraldRunChain, TestKis2CompleteEmeraldRunChain):

| Return | Develop first-edge delta | After |
| --- | ---: | ---: |
| S2 `ss`, `ss_2`, `ss_3` | -40 | edge[0] exact. Residual edge[4] -1 |
| S2 `ss_4`, `ss_5` | -38 | edge[0] +1 |
| S2 `ss_6` | -36 | edge[0] +1 |
| S2 `ss_7` | -37 | edge[0] +1 |
| KiS2 `ss` to `ss_6` (6 axes) | -37 to -39 | 5 axes cleared; `ss_4` +1 |

The +1/-1 residual is where `InitPlayers` falls inside the load's lag run's last
V-int. It depends on the CPU time of the code after it, the same residual the
existing level-to-level gaps show (for example `seg4_ehz1 -> seg5_ehz2`
edge[8] -1). It is not reduced further.

## S3K bonus-stage return tail (withheld)

After the title release, the Gumball return's recorded rows 1407-1429 are lag
rows (`Lag_frame_count` +1, `VInt_0`, sonic3k.asm:588-592) or masked rows (no
V-int, `j_LevelSetup` under `#$2700` in `loc_6310`, sonic3k.asm:7789-7797). The
walk skipped them and re-anchored at the destination. Walking them as gap rows
is expressible: a lag row services only its V-int, and a masked row services
none. A tail containing any row that waited in a loop keeps the re-anchor.
`da4970e73` implements this, and `3c4d3d5e1` withholds it.

With the walk, Mega Slots' first non-camera mismatch moves **earlier**: from
268 on develop and 300 with the release VInt, to 236 (`y_speed`, 7958 errors).
The reel seed clock is still about 30 V-ints short. Each partial correction
changes the payout, so the Slots witness cannot validate a partial clock fix.
This is a validation-gate block, not a contract violation.

## Not expressible

- **S3K bonus entry.** `prepareIntoInterior` parks the cursor at the
  interior's offset. The engine then finishes the title and terrain wait about
  five native rows early, because Kos pieces natively overrun frames. Those
  rows are non-lag (`loc_7870` waits), so lag admission cannot hold them. They
  need the source segment's recorded hardware-timing authority over its tail,
  which is a coordinator ownership change rather than lag admission.
- **Segment switch frame.** The S3K recorder does not write the frame between
  segments (TraceChaser `s3k-profiles-and-hooks.md`), and S3K manifests have
  no census. That frame's admission is unknown: one V-int per boundary.
- **S1 and the S2 halfpipe (superseded 2026-11-04, below).** These have no census. The halfpipe's first
  dynamic-art edge stays 65 rows early. The S1 `sbz3` terminal tail keeps its
  4704 versus 4784 edge count.

## Validation

Full `-Ptrace-replay`, `-Ptrace-replay-r7` and `-Ptrace-segments` runs were
made on develop `e07b75a90` and on `3c4d3d5e1`:

| Profile | Develop | Tip |
| --- | --- | --- |
| r6 | 886 tests: 14F 1E 7S | 887 tests (+1 new unit test), same failures |
| r7 | 119 tests: 84F 11E | identical |
| seg | 70 tests: 46F 8E | identical |

Every other test outcome, message and trace report is identical. The only
changes are:

- the S2 and KiS2 special-stage-return axes above. S2 keeps its 15 axes, now
  with 1-row residuals. KiS2 goes from 10 axes to 5;
- Mega Slots, 7891@268 to 7505@300, from the merged release VInt.

A temporary comparator dump (since removed) was run against develop. Mega
segment 1 is identical, and segments 0 and 2 have no errors on either side.
For Slots, 6917 errors are removed, 6531 added and 974 retained. Every
addition lies on a row that already had a develop error. The first addition,
row 300 `y_speed`, comes after develop's row-268 divergence: there develop's
engine is at `$046A/$035A`, speeds `$00B8/-$0940`, camera `$03CA/$02EA`,
against ROM `$0460/$0430`, 0/0, `$03C0/$03C0`.

The six classes the rejected title-card deferral broke stay green on both
sides: the AIZ trace (17), the AIZ/HCZ/MGZ slices, the SOZ complete run and
reference closure. `TestUncomparedInteriorPhysicalRows` covers the run
locator. The withheld walk's unit test (`TestUnadmittedRecordedTail`, 5
passing) remains in `da4970e73`.

## S1 census coverage and the S2 title-card exit (2026-11-04)

Branch `bugfix/ai-trace-s1-gap-census`, cut from `0b380c382` (the S1 and
halfpipe re-records) and merged with develop `aa26247c0` as `f1663190e`.

### S1: the census does not reach the load

TraceChaser `10f1e09` attaches `GapAdmissionCensus` to manifest
`transitions` only. `S1RunCaptureRunner` creates transitions for
special-stage entry and exit and nothing else. S2 also creates a
`level_advance` reload transition. Plain level-to-level gaps carry no
transition record (`TraceRunReplayWalker.pairBoundaries`). In the S1
complete-emerald manifest:

| Gaps | Rows | Census |
| --- | --- | --- |
| 12 `giant_ring` / `stage_exit` | 1 each | `[1]`: one main-loop row |
| 21 level to level, including each results bridge to its gameplay segment | 216-236 | none |
| terminal tail after `sbz3` | to movie end | none |

The S1 recorder starts a special-stage return's segment at the mode change,
and that segment is the results screen (`SS_NormalExit`). The `GM_Level`
load runs in the following 216-236-row gap. That load covers the title-card
loop (`Level_TtlCardLoop`), the synchronous load and `id_SonicPlayer`, then
`Level_LoadObj` and `Level_DelayLoop` (docs/s1disasm/sonic.asm:2814-2966).
The destination's row 0 is its first `Level_MainLoop` pass
(`gameplay_frame_counter` 1). The chain walks that gap in
`replaySpecialStagePresentationBridge` with `SHARED_GAP` rows and no
census. The one-row censuses say "main loop ran", which is how the bridge
already steps them, so consuming them changes nothing.

None of the five S1 chain axes can move through the census as recorded:

- the `sbz3` terminal tail (`run_tail.edge_count` 4704 vs 4784) has no census;
- the segment 12/15/26/32 axes (`queue.s1_nemesis_plc.prepared` at rows
  101, 102, 10185 and 11) are inside segments. They are S1 PLC service, whose
  per-segment timing streams `0b380c382` withheld.

The recorder already keeps every movie row's lag flag (`physicalLag`). The
next action is TraceChaser-side: publish a census for every gap between
consecutive segments, and for the terminal tail, not only for transitions.
Then re-record. The chain's level-to-level and bridge walks would then need
the same census ordering as `admitLevelWhenReady`. No S1 engine or harness
change was made.

### S2 halfpipe edges 4-7: a missing title-card exit V-int

The halfpipe's remaining axes were the same on every return and on the
`level_advance` gap. Tails' mapping-frame 16 and 17 edges arrived one row
early, while the held first-art edges 0-3 were exact. The S2 complete chain
shows the same residual on `ss`-`ss_3` edge 4 and on every `level_advance`
gap (edges 5-9). It is not the `InitPlayers`-inside-the-last-V-int residual
proposed above. The cause is a missing V-int.

The census for `ss -> seg2_ehz1` is `[23,11,53,14,8,39,25]`, and it maps onto
`Level` row for row:

- gap row 86 is the `WaitForVint` after the exit test of `Level_TtlCard`
  (docs/s2disasm/s2.asm:4914-4927);
- rows 87-100 are the zone-art `KosDec`;
- rows 101-108 are the eight DMA waits of `LoadZoneTiles`
  (docs/s2disasm/s2.asm:6471-6524);
- rows 109-147 are the rest of the synchronous load, which ends at
  `InitPlayers`.

A temporary per-row probe printed title-card state and Tails' animation tick
on each gap row. It showed `ZONE_TILE_UPLOAD` counting row 86 as the first
DMA chunk. `EXIT_LEFT_SWOOSH`, the players' pre-loop pass, therefore ran at
row 107, and Tails ran at 107 and at 108 (tick 7 to 6). That is two passes
before the lag run where the ROM takes one, at `InitPlayers` on row 147.

`9098f21b4` counts the exit wait. TestS2EhzHalfpipeRoundTripChain goes from
3 axes to green. TestS2CompleteEmeraldRunChain goes from 15 axes to 4: the
`ss`-`ss_3` and seven `level_advance` one-row axes clear. The change is
withheld by the following commit because it moves two first errors earlier:

| Class | Before (`f1663190e`) | With `9098f21b4` |
| --- | --- | --- |
| S2 complete | walk failure at segment 20 (`seg20` ownership) | walk failure at `ss_7`, the walk exceeds destination 101691 in `TITLE_CARD` |
| KiS2 complete | 5 axes, walk failure at segment 14 | walk failure at the first `ss` return (destination 9366) |

The probe explains both. In those returns the engine's title-card loop is
one pass long. `ss_7` (ARZ, `[23,11,46,20,8,38,5,1,20]`) has a
zone-name-bound loop of 45 passes on rows 34-78, with the exit wait at 79.
The engine enters `ZONE_TILE_UPLOAD` at 79, so its first loop iteration was
row 35. KiS2 `ss` (`[23,11,54,14,8,39,25]`) behaves the same way, entering at
87 against an exit wait at 87. In the S2 EHZ returns the engine is in
`SLIDE_IN` before the row 23-33 lag run, so it starts early instead. The
missing exit wait was cancelling the late start in ARZ and KiS2.

The next blocker is where the engine's title-card loop starts within the
census gap: the first main-loop row after the lag run that holds `Level`'s
title-card art decompression. The engine's start comes from its own
results/fade timing. Holding the loop to that census row would be a new
CONTRACT 1 ordering rule, like the `InitPlayers` one, together with
`9098f21b4`. The ROM's per-pass title-card and PLC state inside the gap is
unrecorded, so the loop length itself cannot be checked against native data
with these fixtures.
