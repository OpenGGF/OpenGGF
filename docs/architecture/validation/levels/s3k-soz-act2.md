# S3K Sandopolis Act 2 coverage matrix

Canonical slot: `S3K_SANDOPOLIS_2`; ROM zone `$08`, act index 1, SKL pointer set.
Status: native-movement Sonic + Tails cold completion at width 320 verified;
full methodology acceptance, broader routes and native/visual parity remain open.
“Native” configuration here means the engine movement profile, not emulator
parity. Dated evidence retains its original scope; the final
[Cold controller completion](#cold-controller-completion) closes only that route.
Owning [v2 execution plan](../../plans/2026-09-15-soz-methodology-v2.md) and
[placed inventory](../../research/s3k-zones/soz-object-inventory.md).

## Supported donor roster (corrected 2026-09-16)

The production [launch policy](../../../../src/main/java/com/openggf/game/launch/LaunchProfile.java)
and [configuration contract](../../../../CONFIGURATION.md) allow Sonic/Tails/Knuckles
with donation off, Sonic only with S1, and Sonic/Tails with S2. This yields six
supported character/donor combinations, not nine. `TestLaunchProfile` checks that
S1 Tails/Knuckles and S2 Knuckles requests are clamped by the production policy.
These combinations are unsupported by an existing contract, not excluded because
a route failed.

Earlier dated counts below are historical and included raw debug overrides that
bypassed this gate. They do not certify unsupported participants. The current SOZ
checks derive eligibility from `LaunchProfile.sanitizedFor`, assert each live
participant's ROM-backed renderer, animation profile/scripts and decoded mappings,
and repeat those assertions after tested loads. Single/mixed/six-follower stress
rows retain participant counts using supported characters: S1 uses Sonic duplicates;
S2 replaces Knuckles with Sonic. The native/off rosters are unchanged.

## Route and configuration obligations

| Route/dimension | Obligation | Current evidence / gap |
| --- | --- | --- |
| Sonic solo / Sonic + Tails | Cold entry, ordinary traversal, checkpoint/death, boss and exit | Sonic + Tails at native 320 completes cold entry through eight natural boss hits and the Lava Reef transition using fixed controller input. Sonic solo full completion remains open; checkpoint and positioned boundaries supplement the route |
| Tails solo | Same, including native character branches and flight interactions | Every authored post activates/reloads; positioned solo victory covers off/S2 donors at all five widths. Full flight-sensitive cold route remains open; S1-donor Tails is outside the production roster |
| Knuckles solo | Verify distinct start/capsule/boss/progression branches from ROM | Every authored post activates/reloads; positioned solo victory covers all five widths with donation off. Full distinct cold route/progression remains open |
| Mixed / maximum / duplicate followers | Independent held state, authority, release, death and leader chain | Selected mechanisms and three-player terrain restore/replay covered; repeated checkpoint team reset evidence below. No finite follower maximum is declared by the production team contract; full multi-owner interaction breadth remains open |
| Widths 320/400/512/640/800 | Actual camera/render widths; entry/reset × every supported donor; sensitive interactions and rewind | Every authored checkpoint covers all five actual widths × the six supported character/donor combinations in this act. Selected sensitive interactions also cover widths; this is not full traversal/render coverage at every width |
| Donors off/S1/S2 | Confirm production support, actual movement profile, mandatory mechanics and rewind | Every authored checkpoint covers the six supported character/donor combinations × five widths, asserting movement capability and reload state. Thirty positioned boss-to-LRZ cases cover all six supported character/donor combinations at each width; full traversal/interaction breadth remains open |

Decoded checkpoint placements: `$02` at `($0860,$05C8)`, `$03` at `($13F0,$0428)`, `$04` at `($1F00,$0108)`, `$05` at `($3280,$01A8)`, `$06` at `($4EC0,$04A8)`.
`TestSozCheckpointReloadProduction` now physically activates post2 at `($860,$5C8)`,
recreates/restores the activation state, then exercises production death/reload
for Sonic, Tails and Knuckles. All five posts have native-character activation/reload checks. Every post covers the complete five-width × six-supported-character/donor product, including restored activation and production death/reload.

## Behavioral obligations

| Boundary | Implementation / test binding | Reachability | Rewind | Native behavior | Pixels |
| --- | --- | --- | --- | --- | --- |
| PARALLAX / BACKGROUND MODES | `SwScrlSoz` normal half-scroll, rising rooms, boss wall and saved background modes | `TestSozScreenEvents` exercises room/boss modes and native position gates; connected route open | Sand collision offset, ROM layout mutation and boss-wall solid recreation tested; positioned upper wrap and lower-room exit replay cover five widths × three donors; cold/native sequence comparison open | `sub_566D2`, `sub_566E8`, `sub_56706`, `SOZ2_BGDrawArray` source checks | Standard/wide matched native sequence comparison open |
| ANIMATED TORCH TILES / PALETTE | Custom torch DMA and captured master/fade/sand clocks implemented, including boss hold/release | `TestSozLightGhostCompatibility` covers actual light hold/release and ghost/fade behavior | Palette/torch clocks and art state covered by focused lighting/animation and production replay; full cadence movie open | `AnimateTiles_SOZ2`: old-byte frame sequence 0/1/2, eight-pass period, six-tile DMA; pinned intensity branch | Torch pixels must agree with palette at adjacent steps; GPU visibility open |
| ENTRY / LOAD / RESET | ROM loading and event/scroll owners implemented; cold sand intro in Act1 and title-owned ghosts in Act2 | Fresh/seamless entry and title-owned ghosts tested; cold Act 1 Sonic + Tails route reaches playable Act 2; broader incoming routes open | Act1→Act2 destination replay, all checkpoints and selected repeated team reloads covered; repeated seamless-transition cycles open | Unmatched | Unmatched |
| Quicksand entry/held/release | `TestSozQuicksand`: four variant branches, unsigned bounds, input and clock tests | Act 1 short cold route: `TestSozAct1QuicksandRoute`; Act 2 binding/traversal open | Local slide cooldown reconstruction; full registered-state before/contact/release spots open | Native Act 1 acquisition/held force observations corroborate source; no full engine sequence match | Invisible owner; terrain/palette presentation unverified |
| Sand-rock rolling landing / breakup / removal | `SozBreakableSandRockObjectInstance`; saved animation and owner standing latch | `TestSozSandRockProduction`: positioned first-rock spot; cold reachability open | All registered state restored and replayed twice at break, phase 6 and phase 24 removal | Source-derived; mixed-rider and offscreen retained-latch unit checks; native trajectory unmatched | ROM mapping/art checks pass; Act 1 shares the inspected mapping; Act 2 visual comparison open |
| Pushable rock / edge fall / track ride / stop | `SozPushableRockObjectInstance`; ROM track and native push priority | `TestSozPushableRockProduction`: first-rock positioned push, board/brake and complete ride at 320; cold reachability open | All registered state restored/replayed twice at push, initial fall, horizontal start and terminal; boarding also covered | Source-reviewed and real rider carry tested; native trajectory open; subtype `$87` connected cork/rock/switch/door passage now passes a2512-input positioned ordinary Sonic route with15 full-world replay spots; other route products remain open | ROM mapping/art checks pass; shared Act 1 display inspected; Act 2 pixel comparison open |
| Other traversal objects / badniks | All 599 Act1/490 Act2 placed records bind to concrete factories; family production tests listed below | Positioned family reachability plus the recorded cold Sonic + Tails route; per-family cold milestone coverage and other routes remain open | Short graph/contact/creation/deletion restore/replay, including forced recreation; complete per-placement/participant product open | Source-backed branches; matched native sequences remain open | Local ROM-art captures; full pixel comparison open |
| CHECKPOINT / DEATH | `TestSozCheckpointReloadProduction`: all five authored posts × six supported character/donor combinations × five widths | Physical activation from positioned approaches; cold route between posts open | Activation recreated/replayed, production death/reload; selected repeated mixed/duplicate-team reset checks below | Source checkpoint placement/respawn assertions; matched native death movie open | Native pixel comparison open |
| WORLD / CAMERA / EVENTS | Captured `SozEventState`/lighting/wall owners and mutation pipeline | Positioned cork/room/wall and boss stimuli exercised | `TestSozScreenEvents` covers cork layout, fractional sand collision and eight-solid wall graph; connected full-route sequence open | ROM tables and native event thresholds checked; matched sequence open | Local captures; whole-route comparison open |
| BOSS / EXIT | Endboss eight-hit combat, wall reconstruction, capsule/results and LRZ load implemented | `TestSozColdAct2Capture`: cold Sonic + Tails route through natural combat, capsule, results and LRZ load; positioned `TestSozEndBossProduction` supplies short independent boundary checks | Graph/charge and killing-hit/results/post-results replay; outgoing timeline reset and LRZ title/control readiness verified. Thirty solo combat/exit cases cover all six supported character/donor combinations × five widths; repeated exits remain open | Native source graph/combat/escape; matched native trajectory open | Seven actual widths exercised; sparse boss stills, not matched continuous native combat film |
| Darkness / switch / ghosts / torches | Native pilot plus engine switch/capsule/ghost and coupled palette/torch owners implemented | Native ordinary-input Tails pull plus positioned engine switch/capsule/ghost behavior in 19 configurations; cold Sonic + Tails traversal recorded, full lighting/participant sequence comparison open | Hold/release, capsule opening and actual multi-player ghost contacts recreated/replayed; complete lighting journey open | 900-frame darkness and four-frame palette cadence observed; independent P2 switch ownership proven | Native PNGs inspected; engine comparison open |
| Vertical wrap / rising sand | Runtime extended wrap and fractional rising-sand collision implemented | Connected upper cork→carry→wrap and lower cork→switches→swing→room-exit routes cover five widths × three donors; cold reachability remains separate | Fractional collision offset, terrain mutation and connected cross-wrap/room-exit movement recreated/replayed; full per-placement/participant product remains open | Source-derived arithmetic; native sequence match open | Matched sequence open |

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

Remaining obligations include cold victory routes beyond width-320 Sonic + Tails, matched native
trajectories/pixels, repeated seamless/next-zone transitions, every checkpoint's
full width/donor/team product, and coupled mechanism ownership across those
routes. Selected configuration and local graph tests are not full-act certification.

## Connected incoming Act1 victory

`TestSozAct1VictoryProduction` now drives the positioned Sonic/Tails Act1 arena
through pursuit, native positional sink, results, alignment and door fade into
this act, without seeded boss/defeat/transition state. It restores/replays eight
outgoing graph edges and the incoming Act2 graph, and verifies restored palette
lines after control release. This is a native-donor 320-pixel incoming route;
the later cold Act 1 route also reaches this destination. Broader cold routes
and repeated seamless transition products remain open.

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
`completion-connected-mechanisms/verified-rosters/lower/frames/03060.png`:
Sonic is alive beyond the exit, with the temple floor correctly drawn. All four
scenario movies and their input/state records remain beside the originals.

Five additional ordinary-input trials of the late switchB passage did not prove
crossing: the subtype9B charge decays before the attempted door crossing, while
low ceilings constrain acceleration and the final jump. Source-owned collision
or track defects were not established, so no geometry or timing was altered.
`switch-passage-probe1` through`5` preserve the attempted input/state records.
Positive rock/switch coupling is implemented but its connected puzzle route
remains unverified; direct player charging alone does not prove it optional.

## Controller-driven final boss and outgoing transition

`9fc8974f8` adds `TestSozEndBossInputRoute` and the matching
`TestSozEndBossVictoryCapture`, sharing `SozEndBossVictoryRoute`. From the
positioned `$51C0/$620` approach with99rings, ordinary controller input reaches
the placed boss, delivers eight hits and proceeds through capsule, results,
forced walk and Lava Reef. No position, velocity, boss HP or phase writes occur
after setup. This is actual Sonic solo, native320, donor off; both harnesses
assert the live roster. It does not establish cold-route reachability.

Forced object recreation and forward replay cover the shell, every hit, capsule,
results and exit walk. Continuous capture snapshots additionally caught the
independent exit helper retaining the retired boss. The helper now follows the
captured native global fall signal. The worker's final63-test route/boss/capture
selection and required58-test S3K quartet passed without skips. In the3050-frame
capture, hits occur at257/318/586/634/883/925/1144/1270 and Lava Reef loads at2869.
Root inspected frame03048: Sonic and the destination cavern are visible.
The complete movie and input/state records are in
`completion-endboss-victory/native-320` in the unified external capture folder.

## Post-boss presentation during art admission

`60526be71` retains the native BG2C descriptor plane through resource admission
and its two-row redraws. This removes the first-entry art flash at native width
and reproduces the visible partial redraw beside the arena at widths 528/800.
`TestSozPostBossPlaneState` covers row cadence, moving-camera maintenance and
wrap/clipping. `TestSozEndBossInputRoute` recreates/replays all eight redraw
boundaries. `TestSozPostBossRedrawCapture` verifies identical full-registry
restoration at the same revision and checks every descriptor after returning to
normal caching. The final three source and three graphics cases pass without
skips; required bootstrap/cache checks also pass. Slow-motion clips remain in
the unified capture folder. Matched native framebuffer certification remains open.

## Cold controller completion

`de765d83d` adds `TestSozColdAct2Capture` and
`src/test/resources/routes/s3k/soz2-cold-sonic-tails.bk2`: 32,432 fixed input
frames from cold native Sonic + Tails at width320, with intros enabled. The
route crosses three corks, the lower sand-room escape, late wires and timed
doors, then uses the upper late switch/swing route. It does not certify the
unverified lower subtype-$87 rock passage.

The real boss takes eight controller-generated hits at frames30,265 / 30,469 /
30,517 / 30,562 / 30,836 / 30,868 / 31,092 / 31,141. Results begin at31,420,
the capsule opens at31,465, results finish at32,007 and LRZ loads at32,252.
The remaining180 frames settle the destination. Assertions cover no death,
actual Sonic+Tails, boss damage, capsule/results, visible target palette/world,
and cleared control lock/object control at the final frame. There are no
post-boot state writes or trace timing inputs.

The dense explicit capture passed one test without skips (137.1s test, 2:35
Maven); the added control-release assertions passed a sparse repeat without
skips (4.747s test, 23.416s Maven). Root inspected the visible LRZ destination
with both characters. Full native parity and other character/donor/viewport
products remain open; short graph tests provide rewind coverage separately.

## Non-trace acceptance follow-up (2026-09-16)

Strict trace replay is deferred at the user's request. With the corrected support
contract, `TestSozCheckpointReloadProduction` covers 300 cases across both acts:
every authored post × widths 320/400/512/640/800 × six supported character/donor
combinations. Each case
physically activates the post, restores/replays activation and uses the production
death/reload loop. Reload assertions check position, actual width, leader identity,
donor identity and movement capability. These are positioned lifecycle checks,
not full routes or rendered-width certification.

`TestSozTeamCheckpointResetProduction` passes 90 cases without failures/errors/skips:
each act × five widths × three donors × one-, two- and six-follower rosters
adapted to the supported donor characters described above. Each performs two consecutive
real checkpoint death/reloads, asserting live CPU chains and identities, donor
capabilities, usable participant art/animations, control release, timeline reset and stale rock-owner cleanup.
The stress roster is not a declared maximum; the engine has no finite maximum.

`TestSozConnectedMechanismsProduction` passes 60 cases without failures/errors/skips:
four positioned Act 2 scenarios × five widths × three donors. It covers upper
cork/carry/wrap, the connected lower sand-room escape, rock fall/link invalidation,
and direct switch charging with forced graph recreation and forward replay.
The S1-donor pair is Sonic + Sonic; off/S2 use Sonic + Tails.
The lower sand-room escape is distinct from the late subtype-$87 puzzle.

The modified capture tests assert requested width and actual follower identities.
Matched sparse-capture controls still complete both acts at native width 320.
Reusing those fixed inputs at 400/800 does not establish wider completion: Act 1
dies at frames 10,536/1,804 respectively, and Act 2 does not reach the boss at
either width. This is failed input-route portability, not an attributed engine
regression. Further authored routes and native/pixel comparison remain open.


`TestSozEndBossInputRoute` covers 30 positioned, controller-only victories:
Sonic with off/S1/S2 donors, Tails with off/S2, and Knuckles with donation off, each
at widths 320/400/512/640/800. Each case asserts the actual solo character, width
and donor movement capability, delivers eight natural hits, opens the capsule,
finishes results and reaches playable LRZ through the real GameLoop-owned load
and title/fade release. Destination character, width and movement capability are
asserted again. Full registry restoration/replay covers shell
opening, hits, capsule/results and background redraw milestones. No damage,
boss-phase or post-setup player-position writes are used. The authoring setup
starts at the arena approach with 99 rings; this is not a cold whole-act route.
S1-donor Tails is not a supported launch combination. A paired probe found its
raw debug fixture lacked an animation profile: at the first pilot contact, the
S2 control attacked while the S1 fixture took damage. The failed route search
therefore did not establish a supported gameplay defect. Mixed-team combat,
repeated exits and native trajectory/pixel comparison remain open.

## Sprite composition follow-up (2026-09-16)

The [priority audit](../../plans/2026-09-15-soz-methodology-v2.md#2026-09-16-sprite-priority-and-masking-pass)
corrects shared slot/piece ordering and activates SOZ's native sprite masks.
Positioned upper/lower/rock/switch moving captures complete with the corrected
shared renderer. Door, generic-spike and floating/spiked-pillar bucket/terrain
metadata match their disassembly owners. This does not certify every placement,
laser-mask phase or native pixel parity; those presentation obligations remain.

A native held-scene probe independently corroborates pillar/spike occlusion by
rising sand. `TestSozFloatingPillarArtWord` checks both spike directions' full-word
carry and ROM pattern identity; `TestSozSandPriorityPixels` compares submerged
spike pixels against terrain-only rendering and requires exposed body pixels.
This fixes both raw mapping priority and the missing BG-high mask contribution.
The probe is declared positioned/held presentation, not a completed native route.

The boss-entry lighting regression starts with live ghosts, rejects camera
Y `$4FF`, admits `$500`, and verifies brightening, eight wall solids, ghost
fade-out and restored forward replay. The cold320px Sonic+Tails route also
reaches the boss-background mode with darkness/fade zero and continues to LRZ
with live rewind enabled. The reported missing outer shell and persistent ghosts
were traced to the coordinate-only last-checkpoint debug shortcut, which retained
early-room event state. The shortcut now uses the production checkpoint reload.
`TestSozLastCheckpointShortcutProduction` covers native Sonic+Tails at 320/400/800px,
unchanged lives, isolated rewind history, destination events, wall solids and art
submission. Broader donor/leader products for the shortcut remain untested.


## Lower rock puzzle prerequisite reproduced (2026-09-25)

On campaign commit `f631b1832`, the old fresh rock-side reproduction still takes
the falling-track path. The missing prerequisite is now identified in production:
breaking the subtype `$9C` cork at `($4940,$450)` publishes `Events_fg_4` and
`openAct2ForegroundPassage` copies layout columns `$AB..$B3`, rows10..13, into
`$8C..$94`. This changes the floor throughout the lower rock/switch corridor,
including the spot where the fresh rock previously fell. The same event also
copies four chunks on row7. It is a terrain transition, not a different track
pointer or a switch eligibility exception.

A positioned Knuckles run at320, no donor/follower, starts at `$4940/$430` with
37 declared rings. `100 -;30 A;70 -` falls to the ledge and jumps into the actual
cork in rolling animation. By input120 the cork is broken and its real falling
columns exist; by240 the lower column has reached `$580`. Subsequent ordinary
movement reaches the lower corridor at player Y=`$5AC`, whereas the reproduction
without breaking the cork falls to `$66C/$6AC`. No gameplay state was hydrated
from the native trace, no terrain was injected, and no engine behavior changed.
Native comparison-only observations remain the earlier Knuckles segment:
rows29870 onward show the same falling-column routine `loc_41E6A`, and the later
rock remains in `loc_405D6` while it crosses this corridor.

External attempts live under `$VIDEO_ROOT/soz-bring-up/`:
`campaign-20260925-cork-jump-return-320/` contains the1400-plus-frame positioned
presentation attempt and state CSV; `cork-rock-door-v2.bk2` through
`cork-rock-door-v6.bk2` preserve the subsequent input variations. They are
exploration, not completed puzzle evidence. The latest route breaks the cork,
returns over the switch, and reaches the rock's right side. The scorpion beside
the rock hits the return jump (inspected inputs930/945); knockback prevents landing
on its left. Pushing the switch alone reaches full charge, but it decays before
Knuckles reaches the door. That negative control is expected and does not prove
rock coupling. Full connected rock-held switch/door passage and its replay checks
remain open. Next author the safe return to the rock's left, then hold the switch
with the rock and cross the raised door; do not retune the ROM track to compensate
for the missing cork event.


## Connected lower rock puzzle passage (2026-09-25)

On `fc68cbcb7`, an ordinary Sonic-solo positioned route completes the previously
unverified lower puzzle. It starts at `$4940/$430`, native320, donation off,
37 declared rings, no shield or emeralds. The preserved2512 controller inputs
are `src/test/resources/routes/s3k/soz2-lower-rock-sonic-320.script` and `.bk2`.
After breaking the real cork and returning west, Sonic uses a spindash to kill
the scorpion rather than trying to push the rock while being shot from behind.
He pushes the rock to `$4834/$5B4`, which holds the switch at `$4850/$5B0`, then
jumps over and crosses the door at `$4A0D`. At input2400 the player is more than
80px beyond the switch, the charge remains `$80` and the door is at Y=`$500`.
The route uses ordinary inputs throughout and includes damage/ring recovery;
it is not a no-hurt or cold-full-act claim.

The committed native Knuckles segment corroborates the mechanism independently:
auxiliary object rows31240/31300 show `loc_405D6` rock at `$4834/$5B4` and
`loc_418E4` switch at `$4850/$5B0`; row31420 shows `loc_41BE0` door at `$4A0D/$514`
as it starts closing after the rock leaves the camera's lifetime window. These
are comparison-only observations. The native run is Hyper Knuckles (its trail
owner is `Obj_HyperSonicKnux_Trail_Main`), whereas this engine route is ordinary
Sonic, so this proves the shared puzzle mechanism and passage, not synchronized
character movement, slot timing or pixel parity. Ordinary Knuckles and broader
route products remain open.

`TestSozLowerRockPuzzleCapture` checks the actual floor replacement, terminal
rock/switch coordinates, held charge with the player away, raised door, no death
and passage. Fifteen45-input full-registry replay windows surround the cork
break/falling columns, return, scorpion encounter, first pushes, switch coupling,
charge saturation, jump-off, held door and passage. Queued focused command
`-Dmse=off -Dtest=TestSozLowerRockPuzzleCapture -Ds3k.rom.path=$REPO_ROOT/s3k.gen test`
passes1 test, zero failures/errors/skips, on `fc68cbcb7` plus the new fixture.
All15 replay windows pass. No production engine behavior was modified.

The full2512-frame capture has zero death rows, ends at `$4A75/$5AC` with one
ring and control, and fully decodes with ffmpeg. Inputs2400 (rock-held switch
behind Sonic) and2490 (past the raised door) were inspected:
`$VIDEO_ROOT/soz-bring-up/campaign-20260925-lower-puzzle-complete-320/capture.mp4`.
This continuous native320 video is positioned-route evidence, not full-act or
native framebuffer acceptance.

The change-based plan selects all2914 ordinary classes plus guards because the
new route/test resources fall under broad fallback rules. Focused validation is
proportionate for this addition: no production, build, shared algorithm or test
selection policy changes; the new test directly runs every input and all15
registry replay windows. The combined campaign's required broad verification
remains separate and pending.
