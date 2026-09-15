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
Physical activation, every native team's death/reload and repeated reset are open.

## Behavioral obligations

| Boundary | Implementation / test binding | Reachability | Rewind | Native behavior | Pixels |
| --- | --- | --- | --- | --- | --- |
| PARALLAX / HEAT SHIMMER | Dedicated normal-desert owner missing; generic scroll fallback in use | Moving-camera native/engine sequence and band-boundary checks open | Camera/frame reconstruction and replay across art-phase changes open | `sub_55D56`, `loc_55DF2`, ROM wave/band tables; all 224 lines and distinct FG/BG phases required | Standard/wide viewport seams and matched moving background open |
| ANIMATED TILES / PALETTE | Existing Act 1 custom DMA path is partial; secondary byte count/range incorrect; audit AnPal owner | All 32 scroll phases and reverse movement open | Art/channel restore, first update and act reset open | `AnimateTiles_SOZ1`: 12 main + 6 secondary tiles, word-count transfers; `AnPal_SOZ1` cadence open | CPU-to-GPU visibility and native sequence comparison open |
| ARENA PRESENTATION | Event-selected background replacement, shake/sand offset and phase handoff open | Arena entry/exit open | Before/during/after redraw and animation handoff open | `sub_55DB6`, `sub_55E4C` differ from normal desert | Matched arena background/art open |
| ENTRY / LOAD / RESET | Existing ROM loading; SOZ event/scroll owners remain missing at baseline | Seeded FBZ EXIT_READY → fresh SOZ load verified; full incoming route open | `TestFbzSandopolisTimelineHeadless` verifies load reset and destination replay; checkpoint/death breadth open | Unmatched | Unmatched |
| Quicksand entry/held/release | `TestSozQuicksand`: four variant branches, unsigned bounds, input and clock tests | Act 1 short cold route: `TestSozAct1QuicksandRoute`; Act 2 binding/traversal open | Registered acquisition/held/release capture-restore and forward replay twice for the first strip in all four representative configurations; local slide cooldown reconstruction; other variant production spots open | Native Act 1 acquisition/held force observations corroborate source; no full engine sequence match | Invisible owner; terrain/palette presentation unverified |
| Spring-vine acquisition/tension/launch | `SozSpringVineObjectInstance`; native P2-before-P1 tension, pixel slope and eight-piece child | `TestSozAct1SpringVineRoute`: cold first-vine approach/launch at 320/640 widths, S1 donor and extra follower | All registered state restored and replayed twice at acquisition/tension/launch in each configuration; unit child recreation and independent participant state | 473 native slope/child-height observations match the source-derived arithmetic; full trajectory parity open | Native image and engine eight-piece display inspected; short engine capture ends before vine acquisition, no matched pixel certification |
| Sand-rock rolling landing / breakup / removal | `SozBreakableSandRockObjectInstance`; saved animation and owner standing latch | `TestSozSandRockProduction`: positioned first-rock spot; cold reachability open | All registered state restored and replayed twice at break, phase 6 and phase 24 removal | Source-derived; mixed-rider and offscreen retained-latch unit checks; native trajectory unmatched | ROM mapping/art checks pass; positioned engine capture inspected at intact frame 40 and breakup frame 100; native pixel comparison open |
| Pushable rock / edge fall / track ride / stop | `SozPushableRockObjectInstance`; ROM track and native push priority | `TestSozPushableRockProduction`: first-rock positioned track at 320/640, S1 donor and extra follower; cold reachability open | All registered state restored/replayed twice at push, initial fall, horizontal start and terminal | 785 contiguous native rows corroborate push cadence and authored track/stop; full trajectory parity and subtype `$87` door coupling open | ROM mapping/art checks pass; 320/528 engine captures inspected, including terminal at wide frame 550; no pixel certification |
| Other traversal objects / badniks | Inventory lists concrete shared factories versus placeholders | Open | Before/contact/held/release and creation/deletion open | Unmatched | Unmatched |
| CHECKPOINT / DEATH | Five authored checkpoint records; live activation/reload tests needed | Open | Respawn/reset isolation open | Unmatched | Unmatched |
| WORLD / CAMERA / EVENTS | Dedicated coupled owners required | Open | Before/active/after sand rise, camera lock, terrain/palette changes open | Unmatched | Unmatched |
| BOSS / EXIT | Route slice 2–3: miniboss, door and Act 2 entry | Open | Before spawn/attack/hit/defeat/cleanup open | Unmatched | Unmatched |

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
