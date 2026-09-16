# S3K Sandopolis Act 1 coverage matrix

Canonical slot: `S3K_SANDOPOLIS_1`; ROM zone `$08`, act index 0, SKL pointer set.
Status: native-movement Sonic + Tails cold completion at width 320 verified;
full methodology acceptance, broader routes and native/visual parity remain open.
“Native” configuration here means the engine movement profile, not emulator
parity. Dated evidence retains its original scope; the final
[Cold controller completion](#cold-controller-completion) closes only that route.
Owning [v2 execution plan](../../plans/2026-09-15-soz-methodology-v2.md) and
[placed inventory](../../research/s3k-zones/soz-object-inventory.md).

## Route and configuration obligations

| Route/dimension | Obligation | Current evidence / gap |
| --- | --- | --- |
| Sonic solo / Sonic + Tails | Cold entry, ordinary traversal, checkpoint/death, boss and exit | Sonic + Tails at native 320 completes cold entry through the boss and playable Act 2 using the fixed controller asset; Sonic solo full completion remains open. Native-character checkpoint and positioned boundary checks supplement the route |
| Tails solo | Same, including native character branches and flight interactions | Every authored post activates/reloads; positioned golem victory and playable Act 2 entry cover all five widths and three donors. Full flight-sensitive cold route remains open |
| Knuckles solo | Verify distinct start/capsule/boss/progression branches from ROM | Every authored post activates/reloads; positioned golem victory and playable Act 2 entry cover all five widths and three donors. Full distinct cold route/progression remains open |
| Mixed / maximum / duplicate followers | Independent held state, authority, release, death and leader chain | Selected mechanisms and three-player terrain restore/replay covered; repeated checkpoint team reset evidence below. No finite follower maximum is declared by the production team contract; full multi-owner interaction breadth remains open |
| Widths 320/400/512/640/800 | Actual camera/render widths; entry/reset × every supported donor; sensitive interactions and rewind | Every authored checkpoint covers all five actual widths × three leaders × off/S1/S2 in this act. Selected sensitive interactions also cover widths; this is not full traversal/render coverage at every width |
| Donors off/S1/S2 | Confirm production support, actual movement profile, mandatory mechanics and rewind | Every authored checkpoint covers each donor × five widths × three leaders, asserting movement capability and reload state. Positioned boss-to-Act2 victory covers all three donors and leaders at each width; full donor traversal and interaction breadth remain open |

Decoded checkpoint placements: `$02` at `($1780,$0708)`, `$01` at `($1A30,$0428)`, `$03` at `($2760,$0228)`, `$04` at `($2C60,$0628)`, `$05` at `($3F00,$06E8)`.
`TestSozCheckpointReloadProduction` now physically activates post1 at `($1A30,$428)`,
recreates/restores the activation state, then exercises production death/reload
for Sonic, Tails and Knuckles. All five posts have native-character activation/reload checks. Every post covers the complete five-width × three-donor × three-leader product, including restored activation and production death/reload.

## Behavioral obligations

| Boundary | Implementation / test binding | Reachability | Rewind | Native behavior | Pixels |
| --- | --- | --- | --- | --- | --- |
| PARALLAX / HEAT SHIMMER | `SwScrlSoz`: ROM tables, seven fractional bands and independent FG/BG phases; SOZ1 render mode enables foreground rows | Normal desert moving-camera capture; all-line boundary regression | Camera/frame reconstruction; restored foreground mode reproduces GPU frame; existing production route restore/replay passes | 600 native rows / 134,400 scroll words match source-derived arithmetic; BG copies and art phase also agree | 320/528 corrected scenes inspected; all five viewport period checks, exposed-sky seam and320/528 foreground row-displacement regressions; exact native pixel match and full route breadth open |
| ANIMATED TILES / PALETTE | Corrected SOZ1 DMA/channel range `$330..$341`; `AnPal_SOZ1` cycle; unused LRZ scripts excluded | All32 secondary-art phases and 49 palette passes checked against ROM | Six-pass timer/offset restore checked; quicksand/vine/mechanism world replay passes | Full six-tile secondary transfer; native phase/cadence corroboration | Static `$350..$357` preserved across update/VBlank cycles; purple flame overwrite removed in captures; matched cadence/pixel sequence still open |
| ARENA PRESENTATION | `SozAct1Events`, source window, background priority replay, shake/sand and phase handoff implemented | Positioned approach reaches native arena admission; production test covers post-results seamless reload | `TestSozAct1ArenaProduction` covers admission, redraw boundaries, successful allocation prefixes and destination replay after seamless handoff; connected positioned fight-to-handoff replay is covered by `TestSozAct1VictoryProduction` | `sub_55DB6`, `sub_55E4C` differ from normal desert | Temple doorway capture inspected; 42 phased/whole arena A/B images at native Y and widths320/528/800 are identical because foreground hides partial writes; seamless redraw is behind fade. Native pixel identity remains open |
| ENTRY / LOAD / RESET | ROM loading and event/scroll owners implemented; cold sand intro in Act1 and title-owned ghosts in Act2 | Seeded FBZ EXIT_READY → fresh SOZ load verified; full incoming route open | `TestFbzSandopolisTimelineHeadless` verifies incoming load reset/destination replay; all checkpoints and selected repeated team reloads covered below | Unmatched | Unmatched |
| Quicksand entry/held/release | `TestSozQuicksand`: four variant branches, unsigned bounds, input and clock tests | Act 1 short cold route: `TestSozAct1QuicksandRoute`; Act 2 binding/traversal open | Registered acquisition/held/release capture-restore and forward replay twice for the first strip in all four representative configurations; local slide cooldown reconstruction; other variant production spots open | Native Act 1 acquisition/held force observations corroborate source; no full engine sequence match | Invisible owner; terrain/palette presentation unverified |
| Spring-vine acquisition/tension/launch | `SozSpringVineObjectInstance`; native P2-before-P1 tension, pixel slope and eight-piece child | `TestSozAct1SpringVineRoute`: cold first-vine approach/launch at 320/640 widths, S1 donor and extra follower | All registered state restored and replayed twice at acquisition/tension/launch in each configuration; unit child recreation and independent participant state | 473 native slope/child-height observations match the source-derived arithmetic; full trajectory parity open | Native image and engine eight-piece display inspected; short engine capture ends before vine acquisition, no matched pixel certification |
| Sand-rock rolling landing / breakup / removal | `SozBreakableSandRockObjectInstance`; saved animation and owner standing latch | `TestSozSandRockProduction`: positioned first-rock spot; cold reachability open | All registered state restored and replayed twice at break, phase 6 and phase 24 removal | Source-derived; mixed-rider and offscreen retained-latch unit checks; native trajectory unmatched | ROM mapping/art checks pass; positioned engine capture inspected at intact frame 40 and breakup frame 100; native pixel comparison open |
| Pushable rock / edge fall / track ride / stop | `SozPushableRockObjectInstance`; ROM track and native push priority | `TestSozPushableRockProduction`: first-rock positioned track at 320/640, S1 donor and extra follower; cold reachability open | All registered state restored/replayed twice at push, initial fall, horizontal start and terminal | 785 contiguous native rows corroborate push cadence and authored track/stop; full trajectory parity and subtype `$87` door coupling open | ROM mapping/art checks pass; 320/528 engine captures inspected, including terminal at wide frame 550; no pixel certification |
| Other traversal objects / badniks | All 599 Act1/490 Act2 placed records bind to concrete factories; family production tests listed below | Positioned family reachability plus the recorded cold Sonic + Tails route; per-family cold milestone coverage and other routes remain open | Short graph/contact/creation/deletion restore/replay, including forced recreation; complete per-placement/participant product open | Source-backed branches; matched native sequences remain open | Local ROM-art captures; full pixel comparison open |
| CHECKPOINT / DEATH | `TestSozCheckpointReloadProduction`: all five authored posts × three leaders × five widths × three donors | Physical activation from positioned approaches; cold route between posts open | Activation recreated/replayed, production death/reload; selected repeated mixed/duplicate-team reset checks below | Source checkpoint placement/respawn assertions; matched native death movie open | Native pixel comparison open |
| WORLD / CAMERA / EVENTS | Captured `SozEventState`, mutation pipeline and arena owners | Positioned arena admission and source camera gates covered | Event/camera/art graph and terrain restore/replay in focused tests; connected full-route event sequence open | Source-backed state/threshold checks; native sequence comparison open | Arena/cold scenes inspected; matched native sequence open |
| BOSS / EXIT | Egg Golem positional sink defeat, door and seamless Act2 entry implemented | Controller-only cold Sonic + Tails plus 45 positioned admission → pursuit/sink → results → door → playable Act2 cases verified: Sonic + Tails, solo Tails, solo Knuckles × five widths × three donors | Eight forced graph reconstruction/replay milestones through admission, articulation, attack, sink, signpost, results, alignment and fade, plus Act2 destination replay | Native source positional defeat; matched combat trajectory open | Positioned awakening/door and connected victory captures; full native pixel sequence open |

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
width and donor activation are asserted before interaction and after reload;
donor identity and movement capability are asserted before interaction. All remaining checkpoint placements have24additional native-character cases
(19.432s,0skips). Repeated mixed/duplicate-team checks are recorded below; a finite maximum roster is not specified by production.

`1bd8dfcb5` implements the separate layout-driven `sub_730C` sand slide,
including speed, facing, radii, animation, exit lock and per-act row mask.
Twenty-seven focused ROM/shared-provider/production checks pass with zero skips,
including both acts, wrapped coordinates, three actual players and recreation.
`36af09e67` moves the handler after camera scrolling and screen events; both-act production frame-order and full-state replay checks pass. Ordinary route replay is still being re-evaluated.

## Repeated team checkpoint resets and remaining limits

`TestSozTeamCheckpointResetProduction` physically activates the representative
post with Sonic plus `tails,knuckles`, and with Sonic plus
`tails,tails,knuckles,sonic,knuckles,sonic`. Both acts run two consecutive
GameLoop death/reloads in the same session (four cases, eight reloads). The
checks assert independent duplicate sprites, the exact live CPU leader chain,
new object/runtime owners, retirement of a deliberately stale rock-slot pointer,
outgoing live-rewind timeline reset, and native title/fade control release.
These are native-donor, 320-pixel cases with Sonic leading; they do not close
all-character/donor/viewport team reset coverage.

There is no finite maximum follower count in `ActiveGameplayTeamResolver`,
`GameplayTeamBootstrap` or `SelectedTeam`; the seven-player case is a bounded
stress roster, not proof of a maximum-supported boundary.

This check exposed an audio-clock lifecycle bug with live rewind enabled:
external recording moved the host command clock back to the smaller rewind
frame number, so death-fade completion could append load audio out of order.
The correction retains logical-to-audio frame coordinates for seek/truncation,
including pruning and reroots, without suppressing the timeline invariant.
The shared controller/audio regression selection passed 75 tests with zero skips
(including these four production cases); this is focused validation, not a full suite.

Remaining obligations include cold victory routes beyond width-320 Sonic + Tails, matched native
trajectories/pixels, repeated seamless/next-zone transitions, every checkpoint's
full width/donor/team product, and coupled mechanism ownership across those
routes. Selected configuration and local graph tests are not full-act certification.

## Connected positioned Act1 victory

`TestSozAct1VictoryProduction` starts Sonic/Tails at the ordinary arena approach
(320 pixels, donor off, 99 starting rings), lets native events create the golem,
and supplies controller input only. Pursuit carries the golem into the native
positional sink; Sonic escapes during its final committed jump. No boss phase,
defeat flag, results state or transition state is seeded. Results, alignment,
door fade and the seamless loader reach playable Act2. Eight transition-edge
snapshots force complete object recreation and exact forward replay; the
incoming Act2 graph also restores and replays. The focused victory, arena and
miniboss selection passed seven tests with zero skips.

This closes the connected positioned battle-to-handoff obligation for this
configuration. Cold full-act victory, other leader/donor/width combinations,
repeated seamless transitions and matched native trajectories/pixels remain open.

The connected render exposed a shared load-order bug: target zone initialization
ran after the resource handoff and cleared its staged fade palettes. Moving
zone initialization before the post-target handoff preserves SOZ target colors
and ICZ transferred queue ownership. The strengthened selection (including
shared executor/handoff, zone-feature and ICZ rewind consumers) passed 29 tests,
zero skips. The change-based plan selects 2,651 ordinary classes plus guards;
combined broad validation belongs to the SOZ integration owner.

`TestSozAct1VictoryCapture` passed separately with zero skips, recording 5,349
actual GameLoop frames and rendering every fourth frame (15 fps). The durable
capture is `$SOZ_CAPTURE_ROOT/completion-act1-victory/native-320/`:
`capture.mp4`, original PNG frames and `state.csv` including controller masks.
Frame 3344 shows the sink/escape, 5069 enters Act2, and 5348 shows Sonic/Tails
in the visible dark temple after native control release. Destination palette
and non-HUD world-pixel assertions prevent the previously black readiness state
from passing. This engine film is not a native pixel-comparison oracle.

The independent full trace exposed a spring-vine landing boundary at19411.
Native `loc_1E45A` admits positive overlap1..16, excluding exact contact. A
regression reproduces the old zero-overlap acquisition and covers both grounded
and airborne players at all relevant boundary values and three radii. The
99-test solid/vine/short-route selection passes without skips after correction;
short-route graph replay remains included. The separate full-trace result is
recorded in the frontier log rather than inferred from these focused checks.

## Cold controller completion

`9f779282f` adds `TestSozColdAct1Capture` and the compressed controller asset
`src/test/resources/routes/s3k/soz1-cold-sonic-tails.bk2`. The 26,716-frame
route starts from cold SOZ1 with the intro enabled, uses native Sonic + Tails
at width 320, crosses the real level, defeats the naturally spawned Egg Golem
by sinking it, and reaches released Act2 control at frame 26,535. It retains
180 destination frames and asserts no death, the live roster, boss/sink events,
destination palette and visible world pixels. No post-boot position, physics,
phase or damage writes drive the route.

The final merged runtime rerun at `f43f63425` passed one explicit capture test
with no skips (29.10s test; 1:21 Maven). The full route is fixed input; source
trace timing data is not consumed. This closes the native Sonic + Tails cold
route obligation, not solo/donor/width products or native pixel parity. Local
forced-recreation checks above provide rewind evidence separately; this cold
movie does not assert rewind at every point in its route.

## Non-trace acceptance follow-up (2026-09-16)

Strict trace replay is deferred at the user's request. On the acceptance tree
based on `e25269d0e`, the expanded `TestSozCheckpointReloadProduction` passes
450 cases across both acts without failures/errors/skips: every authored post,
Sonic/Tails/Knuckles, widths 320/400/512/640/800 and donors off/S1/S2. Each case
physically activates the post, restores/replays activation and uses the production
death/reload loop. Reload assertions check position, actual width, leader identity,
donor identity and movement capability. These are positioned lifecycle checks,
not full routes or rendered-width certification.

`TestSozTeamCheckpointResetProduction` passes 90 cases without failures/errors/skips:
each act × five widths × three donors × native pair, mixed followers and a
six-follower duplicate-character stress roster. Each performs two consecutive
real checkpoint death/reloads, asserting live CPU chains and identities, donor
capabilities, control release, timeline reset and stale rock-owner cleanup.
The stress roster is not a declared maximum; the engine has no finite maximum.

`TestSozConnectedMechanismsProduction` passes 60 cases without failures/errors/skips:
four positioned Act 2 scenarios × five widths × three donors. It covers upper
cork/carry/wrap, the connected lower sand-room escape, rock fall/link invalidation,
and direct switch charging with forced graph recreation and forward replay.
The lower sand-room escape is distinct from the late subtype-$87 puzzle.

The modified capture tests assert requested width and actual follower identities.
Matched sparse-capture controls still complete both acts at native width 320.
Reusing those fixed inputs at 400/800 does not establish wider completion: Act 1
dies at frames 10,536/1,804 respectively, and Act 2 does not reach the boss at
either width. This is failed input-route portability, not an attributed engine
regression. Further authored routes and native/pixel comparison remain open.


`TestSozAct1VictoryProduction` passes 45 positioned victories without failures,
errors or skips: Sonic + Tails, solo Tails and solo Knuckles × all five required
widths × off/S1/S2. Ordinary controller inputs lure the golem into the sand and
reach playable Act 2. Eight event/boss milestones force graph recreation and
forward replay; destination state also restores/replays. The configured roster,
width and donor capability are asserted before the battle. The initial arena
approach and 99 rings are setup only, not cold-route completion evidence.

`TestSozAct1ArenaAdmission` proves the exact admission threshold and the pixel
before it at all five widths. The event uses the centered native viewport for
`sub_55E96`'s `$4310` test, fixing the width-640/800 wall deadlock while leaving
native-320 behavior unchanged. An explicit 800-pixel moving capture passes through
natural sinking and visible, unlocked Act 2; its battle, sink and destination
frames were inspected. Native pixel matching remains open.
