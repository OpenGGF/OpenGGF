# S3K Sandopolis Act 1 coverage matrix

Canonical slot: `S3K_SANDOPOLIS_1`; ROM zone `$08`, act index 0, SKL pointer set.
Status: partial bring-up; no full-act, native-parity or visual certification.
Owning [v2 execution plan](../../plans/2026-09-15-soz-methodology-v2.md) and
[placed inventory](../../research/s3k-zones/soz-object-inventory.md).

## Route and configuration obligations

| Route/dimension | Obligation | Current evidence / gap |
| --- | --- | --- |
| Sonic solo / Sonic + Tails | Cold entry, ordinary traversal, checkpoint/death, boss and exit | Full route open; short quicksand checks tracked below |
| Tails solo | Same, including native character branches and flight interactions | Unassessed |
| Knuckles solo | Verify distinct start/capsule/boss/progression branches from ROM | Unassessed; no unsupported classification |
| Mixed / maximum / duplicate followers | Independent held state, authority, release, death and leader chain | Local quicksand participant test only; production breadth open. Resolve current maximum from production team contract |
| Widths 320/400/512/640/800 | Actual camera/render widths; entry/reset × every supported donor; sensitive interactions and rewind | Representative Act 1 quicksand test only; full per-act breadth open |
| Donors off/S1/S2 | Confirm production support, actual movement profile, mandatory mechanics and rewind | Representative Act 1 S1 quicksand test only; remaining per-act breadth open |

Decoded checkpoint placements: `$02` at `($1780,$0708)`, `$01` at `($1A30,$0428)`, `$03` at `($2760,$0228)`, `$04` at `($2C60,$0628)`, `$05` at `($3F00,$06E8)`.
`TestSozCheckpointReloadProduction` now physically activates post1 at `($1A30,$428)`,
recreates/restores the activation state, then exercises production death/reload
for Sonic, Tails and Knuckles. All other posts now have native-character activation/reload checks; repeated-reset breadth remains open.

## Behavioral obligations

| Boundary | Implementation / test binding | Reachability | Rewind | Native behavior | Pixels |
| --- | --- | --- | --- | --- | --- |
| PARALLAX / HEAT SHIMMER | `SwScrlSoz`: ROM tables, seven fractional bands and independent FG/BG phases; SOZ1 render mode enables foreground rows | Normal desert moving-camera capture; all-line boundary regression | Camera/frame reconstruction; restored foreground mode reproduces GPU frame; existing production route restore/replay passes | 600 native rows / 134,400 scroll words match source-derived arithmetic; BG copies and art phase also agree | 320/528 corrected scenes inspected; all five viewport period checks, exposed-sky seam and320/528 foreground row-displacement regressions; exact native pixel match and full route breadth open |
| ANIMATED TILES / PALETTE | Corrected SOZ1 DMA/channel range `$330..$341`; `AnPal_SOZ1` cycle; unused LRZ scripts excluded | All32 secondary-art phases and 49 palette passes checked against ROM | Six-pass timer/offset restore checked; quicksand/vine/mechanism world replay passes | Full six-tile secondary transfer; native phase/cadence corroboration | Static `$350..$357` preserved across update/VBlank cycles; purple flame overwrite removed in captures; matched cadence/pixel sequence still open |
| ARENA PRESENTATION | `SozAct1Events`, source window, background priority replay, shake/sand and phase handoff implemented | Positioned approach reaches native arena admission; production test covers post-results seamless reload | Before/during/after redraw and animation handoff open | `sub_55DB6`, `sub_55E4C` differ from normal desert | Temple doorway capture inspected; partial-row VDP redraw and matched native sequence open |
| ENTRY / LOAD / RESET | ROM loading and event/scroll owners implemented; cold sand intro in Act1 and title-owned ghosts in Act2 | Seeded FBZ EXIT_READY → fresh SOZ load verified; full incoming route open | `TestFbzSandopolisTimelineHeadless` verifies load reset and destination replay; checkpoint/death breadth open | Unmatched | Unmatched |
| Quicksand entry/held/release | `TestSozQuicksand`: four variant branches, unsigned bounds, input and clock tests | Act 1 short cold route: `TestSozAct1QuicksandRoute`; Act 2 binding/traversal open | Registered acquisition/held/release capture-restore and forward replay twice for the first strip in all four representative configurations; local slide cooldown reconstruction; other variant production spots open | Native Act 1 acquisition/held force observations corroborate source; no full engine sequence match | Invisible owner; terrain/palette presentation unverified |
| Spring-vine acquisition/tension/launch | `SozSpringVineObjectInstance`; native P2-before-P1 tension, pixel slope and eight-piece child | `TestSozAct1SpringVineRoute`: cold first-vine approach/launch at 320/640 widths, S1 donor and extra follower | All registered state restored and replayed twice at acquisition/tension/launch in each configuration; unit child recreation and independent participant state | 473 native slope/child-height observations match the source-derived arithmetic; full trajectory parity open | Native image and engine eight-piece display inspected; short engine capture ends before vine acquisition, no matched pixel certification |
| Sand-rock rolling landing / breakup / removal | `SozBreakableSandRockObjectInstance`; saved animation and owner standing latch | `TestSozSandRockProduction`: positioned first-rock spot; cold reachability open | All registered state restored and replayed twice at break, phase 6 and phase 24 removal | Source-derived; mixed-rider and offscreen retained-latch unit checks; native trajectory unmatched | ROM mapping/art checks pass; positioned engine capture inspected at intact frame 40 and breakup frame 100; native pixel comparison open |
| Pushable rock / edge fall / track ride / stop | `SozPushableRockObjectInstance`; ROM track and native push priority | `TestSozPushableRockProduction`: first-rock positioned track at 320/640, S1 donor and extra follower; cold reachability open | All registered state restored/replayed twice at push, initial fall, horizontal start and terminal | 785 contiguous native rows corroborate push cadence and authored track/stop; full trajectory parity and subtype `$87` door coupling open | ROM mapping/art checks pass; 320/528 engine captures inspected, including terminal at wide frame 550; no pixel certification |
| Other traversal objects / badniks | All599 Act1/490 Act2 placed records bind to concrete factories; local production graphs covered | Open | Before/contact/held/release and creation/deletion open | Unmatched | Unmatched |
| CHECKPOINT / DEATH | Five authored checkpoint records; physical activation and production death/reload at one post for all three native leaders | Open | Respawn/reset isolation open | Unmatched | Unmatched |
| WORLD / CAMERA / EVENTS | Captured `SozEventState`/lighting/wall owners and mutation pipeline implemented | Open | Before/active/after sand rise, camera lock, terrain/palette changes open | Unmatched | Unmatched |
| BOSS / EXIT | Egg Golem, native positional sink defeat, door and seamless Act2 entry implemented | Open | Before spawn/attack/hit/defeat/cleanup open | Unmatched | Unmatched |

## Execution evidence

See the execution plan for exact command, commit, configurations, results/skips
and external native capture directory. Pending tests are not passing evidence.
The unit and short-route checks do not satisfy the remaining full act matrix.

## Loop-exit and solid-sprite continuation

`TestSozRouteControllersProduction` exercises positioned landings on both `$49`
shapes in both acts, with whole-registry restoration and forward replay twice at
landing. Representative width 640, S1 donor and mixed followers supplement native
320 checks. Static dimensions are viewport-independent; no donor movement branch
exists in either new owner. Full width/donor/team and cold-route coverage remain open.

Act 2's first `$3B` loop exit has independent positioned capture, held movement and
release checks for Sonic, Tails and Knuckles, plus Sonic at width 640. The captured
player bypasses ordinary movement using the existing native full-control contract.
Local tests cover speed/bounds/routine gates, literal radii, fixed-point fractions,
release equality, subtype bit 7 masking and independent participant restoration.
No full route or matched native trajectory/pixel certification is implied.

## Floating-pillar continuation

All 55 placed `$42` pillars use the ROM shape table and three native oscillator
amplitudes along either axis. `TestSozPillarAndRockSwitch` binds movement/flip,
spiked contact faces, invulnerability, stale airborne riders and reconstruction.
`TestSozMechanismsProduction` adds a positioned vertical ride at `($8E0,$670)`
with whole-registry restore and forward replay at landing and carried movement.
See the execution plan for measured results; this is not cold-route certification.

The rejected `($5A0,$660)` and `($1380,$560)` landing setups overlap the
invisible hurt blocks placed directly above those pillars. It is retained as a rejected setup
in the execution record, not used to weaken collision or pillar behavior.
Other pillar phases, hazard geometry, all-character/donor/viewport rides and
native-matched trajectories remain inherited coverage gaps.

## Completion campaign integration

The integrated first object batches (`2cd537cf4`, `9b9738d9c`, `6728de471`)
cover swinging platforms/wires, sand blocks/path swaps/rising walls/corks, and
Skorp/Sandworm/Rockn. Their short production checks include whole-registry
recreation and forward replay; reached subtypes are exercised by focused tests.
These replace the earlier placeholder classification, not the open cold-route
or native-matched trajectory obligations.

`47fc96483` preserves lower object-state bits across kept bonus/special-stage
returns, including layout entries above255. Tests distinguish a kept return
from a normal reset and preserve ring state alongside mechanism state.

`9c27e46a2` adds the dynamically created Egg Golem and its native positional
sink defeat, results owner and post-results alignment. Thirteen focused cases
passed with no skips; two subsequent production/capture assertions also passed.
The current awakening film is a positioned, camera-pinned scenario. Native arena admission and seamless reload are now covered by `TestSozAct1ArenaProduction`;
full cold player victory route remains pending.

## Integrated completion evidence

`a203872dc` makes graph-reference ownership explicit and forces actual recreation
in badnik, swing/wire and ghost production replay helpers.58guard/production
checks and24strengthened recreation checks passed without skips. Local passing
checks do not establish a connected cold route.

`37260aa17` adds physical checkpoint activation, out-of-place full-state replay
and production death/reload for all three native leaders at one post per act
(six cases, zero skips). The final boss GameLoop test consumes the native exit,
loads LRZ1, finishes the destination title/fade, releases controls and verifies
that the outgoing rewind timeline was reset (one case, zero skips).

Checkpoint breadth now covers both acts × three native leaders × five widths
(320/400/512/640/800) × donors off/S1/S2:90physical activation, full-state
recreation/replay and death/reload cases passed, no skips (21.253s). Actual
width/donor/capability is asserted before interaction and after reload. All remaining checkpoint placements have24additional native-character cases
(19.432s,0skips). Mixed/max-team reset breadth remains open.

`1bd8dfcb5` implements the separate layout-driven `sub_730C` sand slide,
including speed, facing, radii, animation, exit lock and per-act row mask.
Twenty-seven focused ROM/shared-provider/production checks pass with zero skips,
including both acts, wrapped coordinates, three actual players and recreation.
This closes an inventory omission; ordinary route replay is being re-evaluated.
