# S3K Sandopolis Act 2 coverage matrix

Canonical slot: `S3K_SANDOPOLIS_2`; ROM zone `$08`, act index 1, SKL pointer set.
Status: partial bring-up; no full-act, native-parity or visual certification.
Owning [v2 execution plan](../../plans/2026-09-15-soz-methodology-v2.md) and
[placed inventory](../../research/s3k-zones/soz-object-inventory.md).

## Route and configuration obligations

| Route/dimension | Obligation | Current evidence / gap |
| --- | --- | --- |
| Sonic solo / Sonic + Tails | Cold entry, ordinary traversal, checkpoint/death, boss and exit | Native-character checkpoint coverage and positioned mechanisms/boss boundaries implemented; Act 1 has short cold quicksand/vine routes. Connected cold completion remains open |
| Tails solo | Same, including native character branches and flight interactions | Every authored post activates/reloads; selected traversal and boss branches covered. Full flight-sensitive route and victory remain open |
| Knuckles solo | Verify distinct start/capsule/boss/progression branches from ROM | Every authored post activates/reloads; local native character branches and selected mechanisms covered. Full distinct route/progression remains open |
| Mixed / maximum / duplicate followers | Independent held state, authority, release, death and leader chain | Selected mechanisms and three-player terrain restore/replay covered; repeated checkpoint team reset evidence below. No finite follower maximum is declared by the production team contract; full multi-owner interaction breadth remains open |
| Widths 320/400/512/640/800 | Actual camera/render widths; entry/reset × every supported donor; sensitive interactions and rewind | Representative checkpoint covers all five actual widths × three leaders × off/S1/S2 in this act. Selected sensitive interactions also cover widths; this is not full traversal/render coverage at every width |
| Donors off/S1/S2 | Confirm production support, actual movement profile, mandatory mechanics and rewind | Representative checkpoint covers each donor × five widths × three leaders, asserting movement capability and reload state. Full donor interaction/boss/route product remains open |

Decoded checkpoint placements: `$02` at `($0860,$05C8)`, `$03` at `($13F0,$0428)`, `$04` at `($1F00,$0108)`, `$05` at `($3280,$01A8)`, `$06` at `($4EC0,$04A8)`.
`TestSozCheckpointReloadProduction` now physically activates post2 at `($860,$5C8)`,
recreates/restores the activation state, then exercises production death/reload
for Sonic, Tails and Knuckles. All five posts have native-character activation/reload checks. The representative post also covers the complete five-width × three-donor × three-leader product; other posts use native 320 configurations.

## Behavioral obligations

| Boundary | Implementation / test binding | Reachability | Rewind | Native behavior | Pixels |
| --- | --- | --- | --- | --- | --- |
| PARALLAX / BACKGROUND MODES | `SwScrlSoz` normal half-scroll, rising rooms, boss wall and saved background modes | `TestSozScreenEvents` exercises room/boss modes and native position gates; connected route open | Sand collision offset, ROM layout mutation and boss-wall solid recreation tested; full connected wrap/exit replay open | `sub_566D2`, `sub_566E8`, `sub_56706`, `SOZ2_BGDrawArray` source checks | Standard/wide matched native sequence comparison open |
| ANIMATED TORCH TILES / PALETTE | Custom torch DMA and captured master/fade/sand clocks implemented, including boss hold/release | `TestSozLightGhostCompatibility` covers actual light hold/release and ghost/fade behavior | Palette/torch clocks and art state covered by focused lighting/animation and production replay; full cadence movie open | `AnimateTiles_SOZ2`: old-byte frame sequence 0/1/2, eight-pass period, six-tile DMA; pinned intensity branch | Torch pixels must agree with palette at adjacent steps; GPU visibility open |
| ENTRY / LOAD / RESET | ROM loading and event/scroll owners implemented; cold sand intro in Act1 and title-owned ghosts in Act2 | Fresh/seamless entry and title-owned ghosts tested; full incoming cold route open | Act1→Act2 destination replay, all checkpoints and selected repeated team reloads covered; repeated seamless-transition cycles open | Unmatched | Unmatched |
| Quicksand entry/held/release | `TestSozQuicksand`: four variant branches, unsigned bounds, input and clock tests | Act 1 short cold route: `TestSozAct1QuicksandRoute`; Act 2 binding/traversal open | Local slide cooldown reconstruction; full registered-state before/contact/release spots open | Native Act 1 acquisition/held force observations corroborate source; no full engine sequence match | Invisible owner; terrain/palette presentation unverified |
| Sand-rock rolling landing / breakup / removal | `SozBreakableSandRockObjectInstance`; saved animation and owner standing latch | `TestSozSandRockProduction`: positioned first-rock spot; cold reachability open | All registered state restored and replayed twice at break, phase 6 and phase 24 removal | Source-derived; mixed-rider and offscreen retained-latch unit checks; native trajectory unmatched | ROM mapping/art checks pass; Act 1 shares the inspected mapping; Act 2 visual comparison open |
| Pushable rock / edge fall / track ride / stop | `SozPushableRockObjectInstance`; ROM track and native push priority | `TestSozPushableRockProduction`: first-rock positioned push, board/brake and complete ride at 320; cold reachability open | All registered state restored/replayed twice at push, initial fall, horizontal start and terminal; boarding also covered | Source-reviewed and real rider carry tested; native trajectory open; subtype `$87` coupling is covered by the connected-mechanism continuation | ROM mapping/art checks pass; shared Act 1 display inspected; Act 2 pixel comparison open |
| Other traversal objects / badniks | All 599 Act1/490 Act2 placed records bind to concrete factories; family production tests listed below | Positioned family reachability; connected cold routes remain open | Short graph/contact/creation/deletion restore/replay, including forced recreation; complete per-placement/participant product open | Source-backed branches; matched native sequences remain open | Local ROM-art captures; full pixel comparison open |
| CHECKPOINT / DEATH | `TestSozCheckpointReloadProduction`: all five authored posts × three native leaders; representative post also covers five widths and three donors | Physical activation from positioned approaches; cold route between posts open | Activation recreated/replayed, production death/reload; selected repeated mixed/duplicate-team reset checks below | Source checkpoint placement/respawn assertions; matched native death movie open | Native pixel comparison open |
| WORLD / CAMERA / EVENTS | Captured `SozEventState`/lighting/wall owners and mutation pipeline | Positioned cork/room/wall and boss stimuli exercised | `TestSozScreenEvents` covers cork layout, fractional sand collision and eight-solid wall graph; connected full-route sequence open | ROM tables and native event thresholds checked; matched sequence open | Local captures; whole-route comparison open |
| BOSS / EXIT | Endboss eight-hit combat, wall reconstruction, capsule/results and LRZ load implemented | `TestSozEndBossProduction`: positioned combat and real-player hits through results, escape and GameLoop LRZ load; cold approach open | Graph/charge and killing-hit/results/post-results replay; outgoing timeline reset and LRZ title/control readiness verified. Repeated exits and donor combat breadth open | Native source graph/combat/escape; matched native trajectory open | Seven actual widths exercised; sparse boss stills, not matched continuous native combat film |
| Darkness / switch / ghosts / torches | Native pilot plus engine switch/capsule/ghost and coupled palette/torch owners implemented | Native ordinary-input Tails pull plus positioned engine switch/capsule/ghost behavior in 19 configurations; connected cold route open | Hold/release, capsule opening and actual multi-player ghost contacts recreated/replayed; complete lighting journey open | 900-frame darkness and four-frame palette cadence observed; independent P2 switch ownership proven | Native PNGs inspected; engine comparison open |
| Vertical wrap / rising sand | Runtime extended wrap and fractional rising-sand collision implemented | Source-gated positioned room/sand tests; connected cork→wrap→loop-exit route open | Fractional collision offset, terrain mutation and wrapped slide lookup recreated/replayed; connected cross-wrap movement open | Source-derived arithmetic; native sequence match open | Matched sequence open |

## Spring-vine continuation

All five placed `$3F` vines now bind to the shared SOZ spring-vine implementation
and ROM-backed art. The Act 1 first-vine route supplies representative local
mechanic/rewind evidence; it does not establish ordinary reachability, respawn,
phase coverage or rendered parity for these Act 2 placements. Those remain open.

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

## Connected mechanism continuation

All 41 placed `$42` pillars, 19 `$45` push switches and 20 `$46` doors have
concrete ROM-backed owners. `TestSozMechanismsProduction` starts before the switch
at `($2630,$1B0)`, charges shared channel 8, jumps across the switch and downstream
door at `($268C,$1C0)`, then continues until the switch becomes an invisible decay
owner. Whole-registry restore/replay spots cover charge, opening, traversal and
retention. Cases cover Sonic/Tails at 320/640, solo Tails, solo Knuckles, mixed
followers and an S1-donor Sonic/Tails route; each verifies the configured roster,
width and donor movement capability. This is representative breadth, not the full
viewport × donor × character product.

A separate placed horizontal pillar at `($303F,$2C0)` covers landing and carried
movement with whole-registry rewind. Local mechanism checks cover both door
orientations/signs, shared trigger interoperability, exact decay boundaries,
retained replacement, native slot invalidation and upper/lower spiked faces.
The special rock at `($4770,$5B5)` publishes `_unkF7C4` for the switch at
`($4830,$5B0)`; the consumer is `SOZPushSwitch.sub_41AA8`, correcting the earlier
attribution to the door. Its positioned production spot covers link publication and invalidation on
fall, independently of the ordinary channel-8 puzzle. Positive contact is covered
locally; full positive placed-puzzle reachability remains open. Exact outcomes
belong to the execution plan.

Native channel-8 observations corroborate charging, door displacement and passage.
They are not matched engine trajectories or pixel certification. Cold reachability,
all puzzle placements, full load/death/checkpoint breadth, lighting/ghost events
and boss/exit obligations remain open.

Push-switch visual follow-up: the fixed frame-0 track is painted before the
frame-1 moving body, matching native main-before-child SAT precedence. The
connected-mechanism capture is repeated at the same entry/input for overlap QA;
this local correction does not close the inherited whole-act visual gaps.


Animation ownership follow-up: both SOZ custom routines return without executing
`AniPLC_LRZ1`, despite its table pointer. `TestS3kSozPatternAnimation` now checks
that Act2's static `$350..$357` tiles survive48 update/VBlank passes. The unused
list is excluded; the completion batch now implements the separate custom torch animation.

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

`6f10f848e` adds light-switch hold/release, Hyudoro controller/body, both art
triggers, the capsule/button/escape graph and boss-area mask. Eleven focused
unit cases and five production cases passed across targeted invocations, with
no skips; mapping sanity also passed. Switch and capsule films are local
scenarios. The attempted ordinary capsule approach stalled near `($B1C,$234)`;
the positioned opening capture therefore does not certify that approach.

`9a26b6f9e` adds the boss wall's coupled thirteen deformation rows/eight solids,
queued terrain/art and background restoration. Five screen-event checks plus
wall-state and comparator checks pass with no skips (12 total). Integrated
combat and results-to-LRZ-request are covered by `TestSozEndBossProduction`;
complete-route traversal and remaining matrix breadth are pending.

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

Ghost/light compatibility checks cover19 actual configurations across native
characters, widths320/400/512/640/800, Sonic+Tails, S1/S2 donors and extra
followers, with real switch holds, ghost behavior and capsule opening.57cases
passed across corrected invocations; this is selected breadth, not a complete
Cartesian product. Final boss breadth asserts seven actual viewport widths and
follower counts;68latest boss tests passed with no skips.

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

Remaining obligations include connected cold victory routes, matched native
trajectories/pixels, repeated seamless/next-zone transitions, every checkpoint's
full width/donor/team product, and coupled mechanism ownership across those
routes. Selected configuration and local graph tests are not full-act certification.

## Connected incoming Act1 victory

`TestSozAct1VictoryProduction` now drives the positioned Sonic/Tails Act1 arena
through pursuit, native positional sink, results, alignment and door fade into
this act, without seeded boss/defeat/transition state. It restores/replays eight
outgoing graph edges and the incoming Act2 graph, and verifies restored palette
lines after control release. This is a native-donor 320-pixel incoming route;
full cold-zone and repeated seamless transition products remain open.

## Connected lower rising-sand escape

`aa90938e9` extends the positioned lower-room route through cork activation,
trigger8 and its door, the placed swinging platform at`($2800,$130)`, trigger9
and door`($29C0,$D2)`, then the native exit gate and completed background redraw.
The same ordinary controller sequence drives production checks and the capture;
both consume the pending initial Process_Sprites pass before input. The setup
wait supplies neutral input while the native background initializer is at0.
No trigger, sand height, collision plane or exit state is written by the route.

Whole-graph forced recreation and replay cover activation, both switches,
platform boarding, collision release and redraw20. The final combined selection
`TestSozConnectedMechanismsProduction,TestSozConnectedMechanismCapture` passes
8tests,0failures/errors/skips. The initial lower-room run is Sonic plus fallback Sonic,width400,donoroff:
its attempted `none` follower identifier resolved to Sonic. `496f44d99` corrects
the solo setting to blank and asserts the actual live roster in production and
capture; the same8checks pass with zero skips. The three other scenarios assert
Sonic+Tails. Definitive movies are under `verified-rosters`; the initial
`aligned-complete-route` lower capture remains a labelled fallback-team attempt. Other characters/configurations and
cold reachability remain open. Root inspected external
`completion-connected-mechanisms/aligned-complete-route/lower/frames/03060.png`:
Sonic is alive beyond the exit, with the temple floor correctly drawn. All four
scenario movies and their input/state records remain beside the originals.

Five additional ordinary-input trials of the late switchB passage did not prove
crossing: the subtype9B charge decays before the attempted door crossing, while
low ceilings constrain acceleration and the final jump. Source-owned collision
or track defects were not established, so no geometry or timing was altered.
`switch-passage-probe1` through`5` preserve the attempted input/state records.
Positive rock/switch coupling is implemented but its connected puzzle route
remains unverified; direct player charging alone does not prove it optional.
