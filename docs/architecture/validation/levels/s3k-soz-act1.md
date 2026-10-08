# S3K Sandopolis Act 1 coverage matrix

Canonical slot: `S3K_SANDOPOLIS_1`; ROM zone `$08`, act index 0, SKL pointer set.
Status: native-movement solo Sonic, solo Tails, solo Knuckles and Sonic + Tails cold completion at width320 verified;
full methodology acceptance, broader routes and native/visual parity remain open.
“Native” configuration here means the engine movement profile, not emulator
parity. Dated evidence retains its original scope; the final
[Cold controller completion](#cold-controller-completion) closes only that route.
Owning [v2 execution plan](../../plans/2026-09-15-soz-methodology-v2.md) and
[placed inventory](../../research/s3k-zones/soz-object-inventory.md).



## Solo Knuckles cold completion (2026-09-27)

Base `84b819ab3`, `feature/ai-soz-knuckles-cold`. The24,059-input
`src/test/resources/routes/s3k/soz1-cold-knuckles.bk2` cold-boots native320
solo Knuckles with no donor, follower or gameplay seed and intro enabled.
It enters a real bonus stage at3204, resumes ordinary SOZ play at3698,
reaches the boss, sinks it at22521, loads Act2 at23758 and releases playable
Act2 at23857. The frozen asset retains201 subsequent destination inputs.
Native movie excerpts supply controller inputs only; authored traversal and
the existing controller-only golem route supply the rest. No runtime fix or
native timing/physics/position hydration is part of this delivery.

`TestSozColdRouteCapture` observes29 full-world45-input restore/replay windows:
24 periodic source observations, four before/during/after the bonus visit,
and one destination observation. Both actual bonus entry and return loads
have nonempty production live history before them and a cleared outgoing
timeline afterward. All windows avoid loading another world while restoring.
The actual Knuckles identity, roster, viewport, golem defeat, finished results,
playable destination and no deaths are asserted.

Fresh `TestSozColdAct1Capture` passes and matches all24,059 author rows on16
movement/animation/camera/mode fields. The capture verifier now queries SOZ
runtime only in the actual SOZ2 destination, rather than during bonus play.
`$VIDEO_ROOT/soz-bring-up/campaign-20260927-knuckles-cold-act1-clear-320/capture.mp4`
shows inputs19000..24058 at60fps960x672 (84.316667s). Full decode/ffprobe pass;
sinking and destination stills inspected. This is engine presentation evidence,
not a native pixel match. Combined checks and integration are recorded in the
[campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md).
Knuckles Act2, broader width/donor routes and native presentation remain open.

## Solo Sonic cold completion (2026-09-27)

Task base `057fb498e`, checkout `feature/ai-soz-solo-cold-routes`. The new
`src/test/resources/routes/s3k/soz1-cold-sonic.bk2` contains31,671 ordinary controller
inputs, compiled and round-trip checked by `InputLogAuthorTool`. It starts cold
with the intro enabled, native320, donor off and no followers, position, ring,
health, boss-state or clock seeds. Solo routes need different rock, swing, sand
column and pillar departures from the paired movie. No gameplay implementation
was changed to fit these inputs.

A fresh uninterrupted rendered replay has zero deaths, observes the golem's
sinking phase at30063, and reaches playable Act2 at31490, retaining180 destination
inputs. All31,671 state rows agree with the selected author on movement, rings,
animation, camera, act and boss state. The external movie is
`$VIDEO_ROOT/soz-bring-up/campaign-20260927-solo-cold-act1-clear-320/capture.mp4`:
inputs27300..31670,4371 frames,60fps,960×672,72.85s. Full FFmpeg decode passes;
both defeat and destination frames were inspected. This is engine presentation
evidence, not native-emulator pixel parity.

`TestSozColdRouteCapture.soloColdAct1CompletesWithTraversalReplayAndPlayableDestination`
adds an ordinary acceptance row, asserting the live solo roster throughout,
actual golem defeat/results/destination release and33 whole-registry45-input
restore/forward-replay windows, plus the results-start observation. Focused
verification results are recorded in the campaign audit. Native Tails/Knuckles
cold completion, solo Act2, broader width/donor routes and native presentation
remain open; existing positioned and lifecycle evidence retains its own scope.

## Production display lifecycle refresh (2026-09-27)

The earlier five-width matrices used 320/400/512/640/800, including two custom
widths rather than the current menu's 352/528 presets. The checkpoint, repeated
team reload and connected-mechanism tests now enumerate `WidescreenAspect.values()`
and select `DISPLAY_ASPECT` through `resolveDisplayAspect()`, without overriding
the derived pixel width. They assert the selected preset, resolved width and live
camera width after boot and each tested reload. Historical 512/640 results below
retain their original scope; other unchanged suites' “five widths” still refer
to those historical rows, not newly executed 352/528 coverage.

At base `9c0360625`, worktree `feature/ai-soz-display-lifecycle`, the queued Java21
explicit-S3K/S1/S2-ROM command selecting `TestSozCheckpointReloadProduction`,
`TestSozTeamCheckpointResetProduction`, `TestSozConnectedMechanismsProduction`
and the four required S3K gates passes 509 tests, zero failures/errors/skips,
2m 04s. The eight completed XML classes include both level-loading classes.
Both acts together contribute 300 physical post contact/recreation/replay/death
cases (all ten posts × six supported character/donor combinations × five
presets), 90 one-/two-/six-follower cases with two consecutive real deaths each,
and 60 connected Act2 mechanism cases. That is 480 real death/reloads, with
ROM-backed roster/art checks; team reloads additionally check control release,
owner replacement and outgoing rewind-history isolation.

This is focused validation of test configuration and its direct production
consumers, not a full-suite pass or new cold-route/native-parity certification.
The change-based planner's 2,929-class fallback is caused by the SOZ test helper;
no runtime code, shared timing/physics, save contract or build policy changed.
Complete solo/Tails/Knuckles routes, broader incoming transitions and matched
native presentation remain open. The positioned controller starts and existing
ring setup are unchanged; they do not establish cold reachability.

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
| Sonic solo / Sonic + Tails | Cold entry, ordinary traversal, checkpoint/death, boss and exit | Sonic + Tails at native 320 completes cold entry through the boss and playable Act 2 using the fixed controller asset; Sonic solo full completion remains open. Native-character checkpoint and positioned boundary checks supplement the route |
| Tails solo | Same, including native character branches and flight interactions | Every authored post activates/reloads; positioned golem victory and playable Act 2 entry cover all five widths with off/S2 donors. Full flight-sensitive cold route remains open |
| Knuckles solo | Verify distinct start/capsule/boss/progression branches from ROM | Every authored post activates/reloads; positioned golem victory and playable Act 2 entry cover all five widths with donation off. Full distinct cold route/progression remains open |
| Mixed / maximum / duplicate followers | Independent held state, authority, release, death and leader chain | Selected mechanisms and three-player terrain restore/replay covered; repeated checkpoint team reset evidence below. No finite follower maximum is declared by the production team contract; full multi-owner interaction breadth remains open |
| Production presets 320/352/400/528/800 | Actual selected preset and camera width; entry/reset × every supported donor; sensitive interactions and rewind | Checkpoint and team lifecycle matrices now select `WidescreenAspect.values()` through the production resolver, including previously omitted352/528. See current execution below; historical512/640 checks remain historical custom-width evidence, not menu coverage |
| Donors off/S1/S2 | Confirm production support, actual movement profile, mandatory mechanics and rewind | Every authored checkpoint covers the six supported character/donor combinations × five widths, asserting movement capability and reload state. Positioned boss-to-Act2 victory covers all six supported character/donor combinations at each width; full donor traversal and interaction breadth remain open |

Decoded checkpoint placements: `$02` at `($1780,$0708)`, `$01` at `($1A30,$0428)`, `$03` at `($2760,$0228)`, `$04` at `($2C60,$0628)`, `$05` at `($3F00,$06E8)`.
`TestSozCheckpointReloadProduction` now physically activates post1 at `($1A30,$428)`,
recreates/restores the activation state, then exercises production death/reload
for Sonic, Tails and Knuckles. All five posts have native-character activation/reload checks. Every post covers the complete five-width × six-supported-character/donor product, including restored activation and production death/reload.

## Behavioral obligations

| Boundary | Implementation / test binding | Reachability | Rewind | Native behavior | Pixels |
| --- | --- | --- | --- | --- | --- |
| PARALLAX / HEAT SHIMMER | `SwScrlSoz`: ROM tables, seven fractional bands and independent FG/BG phases; SOZ1 render mode enables foreground rows | Normal desert moving-camera capture; all-line boundary regression | Camera/frame reconstruction; restored foreground mode reproduces GPU frame; existing production route restore/replay passes | 600 native rows / 134,400 scroll words match source-derived arithmetic; BG copies and art phase also agree | 320/528 corrected scenes inspected; all five viewport period checks, exposed-sky seam and320/528 foreground row-displacement regressions; exact native pixel match and full route breadth open |
| ANIMATED TILES / PALETTE | Corrected SOZ1 DMA/channel range `$330..$341`; `AnPal_SOZ1` cycle; unused LRZ scripts excluded | All32 secondary-art phases and 49 palette passes checked against ROM | Six-pass timer/offset restore checked; quicksand/vine/mechanism world replay passes | Full six-tile secondary transfer; native phase/cadence corroboration | Static `$350..$357` preserved across update/VBlank cycles; purple flame overwrite removed in captures; matched cadence/pixel sequence still open |
| ARENA PRESENTATION | `SozAct1Events`, source window, background priority replay, shake/sand and phase handoff implemented | Positioned approach reaches native arena admission; production test covers post-results seamless reload | `TestSozAct1ArenaProduction` covers admission, redraw boundaries, successful allocation prefixes and destination replay after seamless handoff; connected positioned fight-to-handoff replay is covered by `TestSozAct1VictoryProduction` | `sub_55DB6`, `sub_55E4C` differ from normal desert | Temple doorway capture inspected; 42 phased/whole arena A/B images at native Y and widths320/528/800 are identical because foreground hides partial writes; seamless redraw is behind fade. Native pixel identity remains open |
| ENTRY / LOAD / RESET | ROM loading and event/scroll owners implemented; cold sand intro in Act1 and title-owned ghosts in Act2 | Seeded FBZ EXIT_READY → fresh SOZ load reached in the ordinary recheck; full incoming route open | The 2026-10-07 repaired candidate passes 125 focused cases, including twelve shield regressions and the unchanged `TestFbzSandopolisTimelineHeadless` destination restore and both complete eight-frame replay cycles. The transition adapter clears stale deferred state at the captured floor. The separate ROM-backed `TestFreshLevelBoundaryRewindHeadless#restoredBoundaryRetainsPublicationPhaseAndDispatchesDeferredAssembly` passes both explicitly captured unpublished and published phases: boundary-adapter restoration writes no player/camera state, unpublished publication restores native held state, published publication is a no-op, and completion performs deferred assembly and one initial sprite pass. Its final focused selection passes 16 cases with zero failures/errors/skips (`a33b8029`, session 58727); the identical final fixture fails both phases on the old transition owner (session 44424). See the [S1 lane audit](../../audits/2026-10-07-s1-parity-gap-verification.md). [Actual-main qualification](../../audits/2026-10-07-stock-parity-gap-verification.md#actual-main-delivery-qualification) passes this destination-restoration case without a skip and all 672 fresh guards; 27 other concrete inherited ordinary failures and 62 literal skips remain. Checkpoint and selected repeated team reload evidence is recorded below | Unmatched | Unmatched |
| Quicksand entry/held/release | `TestSozQuicksand`: four variant branches, unsigned bounds, input and clock tests | Act 1 short cold route: `TestSozAct1QuicksandRoute`; Act 2 binding/traversal open | Registered acquisition/held/release capture-restore and forward replay twice for the first strip in all four representative configurations; local slide cooldown reconstruction; other variant production spots open | Native Act 1 acquisition/held force observations corroborate source; no full engine sequence match | Invisible owner; terrain/palette presentation unverified |
| Spring-vine acquisition/tension/launch | `SozSpringVineObjectInstance`; native P2-before-P1 tension, pixel slope and eight-piece child | `TestSozAct1SpringVineRoute`: cold first-vine approach/launch at 320/640 widths, S1 donor and extra follower | All registered state restored and replayed twice at acquisition/tension/launch in each configuration; unit child recreation and independent participant state | 473 native slope/child-height observations match the source-derived arithmetic; full trajectory parity open | Native image and engine eight-piece display inspected; short engine capture ends before vine acquisition, no matched pixel certification |
| Sand-rock rolling landing / breakup / removal | `SozBreakableSandRockObjectInstance`; saved animation and owner standing latch | `TestSozSandRockProduction`: positioned first-rock spot; cold reachability open | All registered state restored and replayed twice at break, phase 6 and phase 24 removal | Source-derived; mixed-rider and offscreen retained-latch unit checks; native trajectory unmatched | ROM mapping/art checks pass; positioned engine capture inspected at intact frame 40 and breakup frame 100; native pixel comparison open |
| Pushable rock / edge fall / track ride / stop | `SozPushableRockObjectInstance`; ROM track and native push priority | `TestSozPushableRockProduction`: first-rock positioned track at 320/640, S1 donor and extra follower; cold reachability open | All registered state restored/replayed twice at push, initial fall, horizontal start and terminal | 785 contiguous native rows corroborate push cadence and authored track/stop; full trajectory parity and subtype `$87` door coupling open | ROM mapping/art checks pass; 320/528 engine captures inspected, including terminal at wide frame 550; no pixel certification |
| Other traversal objects / badniks | All 599 Act1/490 Act2 placed records bind to concrete factories; family production tests listed below | Positioned family reachability plus the recorded cold Sonic + Tails route; per-family cold milestone coverage and other routes remain open | Short graph/contact/creation/deletion restore/replay, including forced recreation; complete per-placement/participant product open | Source-backed branches; matched native sequences remain open | Local ROM-art captures; full pixel comparison open |
| CHECKPOINT / DEATH | `TestSozCheckpointReloadProduction`: all five authored posts × six supported character/donor combinations × five widths | Physical activation from positioned approaches; cold route between posts open | Activation recreated/replayed, production death/reload; selected repeated mixed/duplicate-team reset checks below | Source checkpoint placement/respawn assertions; matched native death movie open | Native pixel comparison open |
| WORLD / CAMERA / EVENTS | Captured `SozEventState`, mutation pipeline and arena owners | Positioned arena admission and source camera gates covered | Event/camera/art graph and terrain restore/replay in focused tests; connected full-route event sequence open | Source-backed state/threshold checks; native sequence comparison open | Arena/cold scenes inspected; matched native sequence open |
| BOSS / EXIT | Egg Golem positional sink defeat, door and seamless Act2 entry implemented | Controller-only cold Sonic + Tails plus 30 positioned admission → pursuit/sink → results → door → playable Act2 cases: six supported character/donor combinations × five widths | Eight forced graph reconstruction/replay milestones through admission, articulation, attack, sink, signpost, results, alignment and fade, plus Act2 destination replay | Native source positional defeat; matched combat trajectory open | Positioned awakening/door and connected victory captures; full native pixel sequence open |

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


`TestSozAct1VictoryProduction` covers 30 positioned victories: all six supported
character/donor combinations × five required widths. Sonic has a Tails follower
with off/S2 and leads solo with S1; Tails and Knuckles lead solo. Ordinary controller inputs lure the golem into the sand and
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

## Sprite composition follow-up (2026-09-16)

The [priority audit](../../plans/2026-09-15-soz-methodology-v2.md#2026-09-16-sprite-priority-and-masking-pass)
corrects shared slot/piece ordering and activates SOZ's native sprite masks.
The production arena-door mask is asserted at320/400/512/640/800. The final800px
moving victory capture has5325 gameplay-state rows identical to the earlier run;
opening-door and sinking-golem clipping were visually inspected. Dust retains
its independent bucket/terrain priority on both sides of the sand boundary.

The priority audit also covers detached golem parts retaining their last bucket
and art priority through movement and forced rewind reconstruction, plus the
hit-reaction child's inherited art priority and independent bucket. Native
owners are `loc_76F24`, `loc_849D8`/`Obj_FlickerMove`, and `loc_76F6A`.

Playtest follow-up verifies Rockn's shell/eye facing through repeated turns and
publishes the same shaken camera copy to foreground/background terrain and
sprites during pyramid rise. A refreshed800px victory/handoff movie passes.

## Widescreen pyramid follow-up (2026-09-16)

`TestSozPyramidWindow` checks the event-selected residency width at 320, 400,
512, 528, 640 and 800 pixels and its return to the normal 512-pixel desert
period after whole-registry restore. Every scanline at all 16 horizontal alignments
keeps both visible edges resident, including the shimmer pixel before an aligned
column. `TestHczOverlayCommandPool` checks that
the high-priority replay uses the same source period as the main pass.
The 800px positioned Sonic+Tails capture reaches the rising pyramid, fight,
defeat and Act2 entry with a continuous source window. Media are under
`pyramid-allocation/act1-widescreen-final/` in the unified external task directory.
That residency fix alone left the unauthored right margin visible. The subsequent
presentation extension repeats the final 64px ROM masonry strip beyond BG `$780`
and caps the wider camera before foreground `$4500`, leaving native player
movement bounds unchanged. `TestSozPyramidWindow` also checks entry through the
ROM priority switch, arena admission with the view cap, descriptor provenance,
and full-state rewind/replay at all six widths. The capture starts above the
`$4308,$918` switch rather than spawning below it with fresh-load low priority.
Native pixel certification and the wider cold-route product remain open.
See the execution plan for verification details.


## Frozen cold-route replay revalidation (2026-09-27)

At runtime base`7a00b2915`, `TestSozColdRouteCapture` replays the unchanged native320
Sonic + Tails recording from cold intro-enabled entry with donor off. It reaches
playable Act2 at input26538, observes the golem's native positional sand defeat
and actual results completion, and verifies the live two-player roster throughout.
All28 periodic/destination45-input whole-registry restore/replay windows pass,
with a further semantic results-start replay. This supplements the short local
checks; it does not certify solo routes, other widths/donors or emulator parity.


## Solo Tails cold route (2026-09-27)

`soz1-cold-tails.bk2` has16,901 ordinary controller inputs from a native320
Tails cold start, no follower/donor, intro enabled and no gameplay-state seeds.
The first3900 inputs derive from the existing Tails movie's SOZ segment; later
inputs are authored against production gameplay. Flight routes respect the
Knuckles-only walls and solid terrain; the selected lower passage uses normal
rolling contact to defeat Rockn and break the rising sand wall. The route wakes
the golem, draws it into sand and reaches released Act2 control.

Fresh `TestSozColdAct1Capture` execution records golem sinking at15293, playable
Act2 at16626 and zero deaths across all16,901 inputs. All16,528 shared branch/
fresh rows through the level replacement match on position, velocity, inertia,
air/roll/hurt/death/rings, mapping, camera and mode. The movie shows every input
12530..16900:4371 frames,60fps,960x672,72.85s. Full ffmpeg decode and ffprobe
pass; sinking, late-defeat and destination images inspected.

`TestSozColdRouteCapture` includes this route with18 periodic/destination
45-input whole-registry restore/replay windows and the semantic results-start
window. Its source windows end at16000 before the actual seamless Act2 load;
no outgoing registry is restored across that load. Candidate/integration test
results belong to the campaign audit. This adds native320 Tails Act1 only;
Knuckles cold routes, other products and native visual parity remain open.
Tails Act2 is covered by the [Act2 matrix](s3k-soz-act2.md#solo-tails-cold-route-2026-09-27). Video: `$VIDEO_ROOT/soz-bring-up/`
`campaign-20260927-tails-cold-act1-clear-320/capture.mp4`.


### Seamless sprite-publication follow-up (2026-09-27, candidate)

`TestLevelSpritePresentationLifecycle` independently exercises FBZ/MHZ/SOZ/LRZ/
DEZ reloads: pending/published SAT and HUD counters survive, while fresh loads
still clear them. Shared validation and exact route limitations are recorded in
[the campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md#seamless-sprite-publication-and-mhz-scroll-carry--2026-09-27).
This adds transition coverage, not another whole-act or native-pixel certification.

The fresh native320 Sonic handoff uses `S3K_SKIP_INTROS=false` and one ordinary
neutral setup frame before the unchanged `soz1-cold-sonic.bk2`. It reaches Act2
at31392 with zero deaths; all31492 baseline/candidate state rows match. The
initial default-tool failure was an intro-configuration mismatch, not a failure
of this native-start route. Video:
`$HOME/Videos/OGGF/seamless-presentation/campaign-20260927-soz-handoff-320/capture.mp4`.

## Bonus-return controller refresh (2026-10-03)

This route is an engine-authored controller movie, not a native trace. Commit
`5566b8db17` made bonus title release run the initial `Process_Sprites` before
`LevelLoop`, as native `Level/loc_6468` does. Before that change, the engine
treated the first playable input row as setup. Afterwards the Pachinko return
reached SOZ's title 7 inputs earlier (3566 instead of 3573), and the old movie
died at input 15657.

`soz1-cold-knuckles.bk2` was re-authored to match. It drops seven identical
held-Right inputs from the locked return title, cutting the hold from 30 inputs
to 23. Total inputs fall from 24,059 to 24,052, and no other input changed.
Runtime behaviour and assertions are unchanged.
`TestSozColdRouteCapture#soloColdActCompletesWithTraversalReplayAndPlayableDestination`
passes all five solo cases with zero skips. Knuckles reaches Act 2 at input 23850
with all 29 full-world replay windows.

Running these checks after a trace prefix in the same JVM used to fail. The
cause was a leaked playback session, fixed in `d427fdd9ba`.

## Shorter solo Sonic inputs (2026-10-08)

At task base `d740b7a0fadd97b2e7c104d56481a0235bdffb4c`, the nine current cold
routes pass with zero failures, errors or skips. The solo Sonic movie now retains
original input ranges `[0,22350)`, `[23896,24207)` and `[25718,31671)`:
**28,614 inputs rather than 31,671**. The removed 3,057 inputs are an obstructed
approach pause and repeated stationary hopping. Its committed `.script` is the
reproducible controller source; `InputLogAuthorTool` checked every emitted pad
against the production loader. It changes no runtime state or rules.

A cold-prefix whole-registry branch probe reaches the real sand defeat at27006,
matching the original route's owning input30063 after the removed intervals.
This probe is authoring evidence. Fresh uninterrupted acceptance then passes
all nine routes with no skips: solo Sonic reaches playable Act2 at28436 rather
than31490, retains180 playable destination frames (including three neutral
inputs after the movie ends), and passes the full-world replay comparisons.
The other eight routes keep their original ready frames and outcomes.

The more aggressive variant also removed arena-entry inputs and died at27483
without winning; it was rejected. The existing paired Act1 and Tails Act1/Act2
movies also died early when tried with solo Sonic, so they were not reused.

The shortened route retains a traversal window at100 and every1000 inputs
through28000, a destination window, and semantic full-world45-input replays at
boss entry, actual sand defeat and results start. These are30 traversal/destination
and three semantic windows. Source observations stop before the replacement world.
Every frame still draws; real victory, finished results, control release and the
solo roster remain required. The other eight movies, Knuckles puzzle/bonus checks
and inherited native/visual parity limits retain their existing scope. Commands,
timings and final delivery checks are recorded in the
[memory/throughput research](../../research/2026-10-07-ordinary-suite-memory-cause.md).

## Cold-route drawing checkpoints (2026-10-08)

`TestSozColdRouteCapture` now simulates every movie input and services queued
render work every tick, while drawing traversal checkpoints and both complete
45-frame rewind replay branches. It also draws the final playable destination.
The matched nine-case comparison retains every ready frame, replay-window count
and semantic event set, passing without skips; class time falls from 138.309 to
45.423 seconds. Real completion and full-world restore/replay obligations remain.

Four bounded state/pixel controls compare fully drawn and skipped SOZ traversal
at 320/800 widths for Sonic Act 1 and Knuckles Act 2, with a poisoned framebuffer
that rejects stale-image success. These controls add checkpoint reconstruction
evidence and do not close broader character/team/donor or native visual gaps.
Default traversal no longer certifies presentation on every intermediate frame.
Use `-Dopenggf.soz.drawEveryFrame=true` with the same focused command for every-frame
drawing; screenshot/capture tools keep their own rendering behavior. Exact methods,
commands and coverage limits are in the [memory/throughput research](../../research/2026-10-07-ordinary-suite-memory-cause.md#soz-drawing-gap-follow-up-2026-10-08).
