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
| Special-stage return | S2 halfpipe, S1 complete, S1 maze | None: no census in these manifests | Not expressible. The fixture needs a census-bearing re-record |
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
- **S1 and the S2 halfpipe.** These have no census. The halfpipe's first
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
