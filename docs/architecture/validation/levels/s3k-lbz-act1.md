# S3K LBZ act 1 — focused object and transition coverage

Sonic 3 & Knuckles, Launch Base act 1 (zone 6, act 0). These are focused
corrections from the 2026-10-03 trace campaign and 2026-10-07 Ribot follow-up. The act as a whole is not
certified. The recorded route is Sonic+Tails at native 320px with donor off. The
Knuckles that appears in it is the NPC cutscene, not a playable Knuckles.

| Obligation | Evidence | Remaining scope |
|---|---|---|
| Cup side contact (P1 and CPU) | `TestLbzCupElevatorSolidDispatch`: real dispatch stops an airborne rolling player at the cup edge, then repeats after restore. | Other character geometry and donors |
| Cup capture/hold control and animation bit | `TestLbzCupElevatorInstance` (native P1/P2, extension, rewind) and `TestS3kLbz1KnucklesSequenceHeadless` | Full-route rewind and load/death boundaries |
| Rolling drum deletion | `TestLbzRollingDrumDeletion`: real capture and range unload, dead/extension cleanup, rewind | — |
| Miniboss fatal hit | `TestS3kLbz1MinibossAndTransitionHeadless`: real P1/P2 sixth hit, explosion cadence, restore/replay | — |
| Carried results → title | `TestS3kMgzLbzCarriedResultsTitleOwnership`: twelve real children through carry, rewind, publication, control release and title init; 58/59-ring tally oracle | MGZ ring difference that starts at row 9260 |
| Retained title counter reset | `TestSonic3kTitleCardKosQueue#retainedResetWaitsForArtAndLastChildMovementAndRestoresThatGate` | Full native `Obj_TitleCardWait2` presentation timing |
| Act 1→2 camera hold and size workers | `TestS3kLbz1MinibossAndTransitionHeadless#eventsFg5ReloadsLbz2WithRomWorldOffsetAndAdjustedLayout`, `TestSonic3kLbzRewindRoundTrip` | — |
| Ribot initialization and child graph | `TestRibotBadnikInstance` and `TestSonic3kLbzRewindRoundTrip`: initialization returns before orbit, non-four gravity retained, visual children restored | Other character geometry, route/load/death boundaries and presentation |
| Recorded Sonic+Tails route | `TestS3kLbzZoneSliceTraceReplay`, 46,075 rows | Red: 1,665 errors, first row 30582 `tails_y` native `$013D`, engine `$012C`. Other widths, rosters and donors are open. |

Trace command:
`python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-segments -Dtest=TestS3kLbzZoneSliceTraceReplay "-Ds3k.rom.path=<rom>" test`.
The [frontier log](../../../status/trace-frontier-log.md#2026-10-03--s3k-trace-green-campaign-summary)
has the error count after each fix.

## Corrections and ROM owners

**Cup contact (`bda587a99b`).** `loc_26EEA` calls `SolidObjectFull2_1P` inside
each player's control routine. It runs after the cooldown and angle gates and
before the capture test. `MANUAL_CHECKPOINT` only installs the resolver, and the
cup never called it. Older tests hid this by seeding the standing flag. At row
3714 native Sonic stops at `$11CB` (cup `$11A0` + width `$20` + padding `$0B`).

**Rolling drum (`7a85c01222`).** `loc_2C3CA` runs both `sub_2C3E8` participant
updates before `Delete_Sprite_If_Not_In_Range`. `Delete_Current_Sprite` clears
only the drum's SST and performs no `loc_2C48A` release writes. At row 9867,
native Tails is still on the object (status `$09`). The engine's synthetic
release had set the air bit, which delayed the CPU despawn.

**Cup control and animation bit (`3fa9c0a88a`, `c54cbfdf93`).** The writers are:

- capture `loc_26F26`: `$03`, which sets bit 1
- NPC helper `sub_62800`: `$81`, which clears bit 1
- parent exit `loc_6278A`: `$00`

The held routine `loc_26FF4` publishes only position, priority and mapping.
Rewriting control every held tick froze Sonic's acceleration after the cutscene
release (rows 18938–18940 natively: X speed `$000C`, `$0018`, `$0024`).

**Miniboss (`588999752d`).** `loc_7289A` installs `Wait_NewDelay`, and
`BossDefeated` writes `$3F` and returns. The first decrement happens on the next
dispatch. The engine was one row early through the whole end-sign chain (sign
allocation 21507 vs 21508, ending pose 21662 vs 21663). The explosion helper
restores through its own snapshot and rebinds to the session RNG.

**Carried results (`aea1bb0206`).** `Obj_LevelResultsWait2` tests the live
twelve-child `$30` count. `loc_2DD06` clears `_unkFAA8`, swaps the code pointer
to `Obj_TitleCard` and returns. `Obj_EndSignControlAwaitStart` restores P1/P2 when
it next observes the cleared latch, and `Obj_TitleCardInit` runs on the following
owner dispatch. Removing the old embedded-render retirement tail, the early
control shortcut and the same-pass title-init shortcut lines LBZ up exactly:

| Boundary | Native row | Before | After |
|---|---:|---:|---:|
| Last child deleted | 22187 | 22187 | 22187 |
| Publish `_unkFAA8` clear | 22188 | 22191 | 22188 |
| Control restored | 22188 | 22190 | 22188 |
| Title init | 22189 | 22192 | 22189 |

HCZ, CNZ, ICZ and MHZ error spans were unchanged. MGZ's slice went from 10,046 to
10,634 errors with the same first row (5255). Native MGZ enters results with 59
rings and the engine with 58. That makes the tally one dispatch shorter, so the
last child retires at 16509 instead of 16510. The synthetic tail had been
masking this.

**Retained title reset (`030f66cb40`).** `Obj_TitleCardWait/loc_2D810` clears
the children's `$34` movement latch. On the next stationary poll it resets
Timer and Ring_count without touching the owner's `$2E`. LBZ now resets rings at
22228, matching native; the old countdown reset them at 22227.

**Act 1→2 camera (`7324b9c50e`).** `LBZ1BGE_DoTransition` subtracts `$3A00`
from the inherited X bounds and leaves Y alone. Bounds are released only when
`Obj_EndSignControlDoStart` reaches `Change_Act2Sizes`. The three size workers
it creates (`CreateChild1_Normal`) run later in the same `Process_Sprites` pass.
Native row 22331 shows slots 35, 36 and 37 already advanced to `$4000`, `$4000`
and `$8000`. The first max-X increment is at 22334.

## Ribot initialization return — 2026-10-07

Commit `3e7e75785c8b` adds the return after visual child creation: native
`loc_8C396` initializes, branches to `loc_8C594`, then returns. Active child
`loc_8C3BC`/orbit `loc_8C41E` begins on the next dispatch. The base incorrectly
orbited during creation, leading native contact by one pass. The native hurt
is row23534; the previous row23533 claim described an early engine hurt, not a
contact absent from the ROM.

The matched 46,075-row `-Ptrace-segments` replay changed from 4,585 errors
(3,955 physics-group,630 animation) to1,665 (1,419 physics-group,246 animation),
with zero warnings. First error advanced from23533 `x_speed` (`$016F`/`$0200`)
to30582 `tails_y` (`$013D`/`$012C`). Both invocations execute one assertion-failing
test without errors/skips; the remaining trace is not green. Sixty-eight focused
object/child-graph and mandatory S3K loading/bootstrap/decoding checks pass,
without skips. See the [lane audit](../../audits/2026-10-07-s3k-parity-gap-verification.md)
for exact commands, native creation evidence and unexecuted breadth.
