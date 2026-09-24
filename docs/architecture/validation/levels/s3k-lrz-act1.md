# S3K Lava Reef Act 1 coverage matrix

Game / canonical zone / act: S3K `S3K_LAVA_REEF_1`, engine zone `$09` act index 0,
ROM `Current_zone_and_act = $900`, SKL object set (`Sprite_ListingK`, SK Set 2).
Character routes: Sonic + Tails, Sonic, Tails (falling intro at `($100,$20)`) and Knuckles
(start `($10,$7AD)`, intro run) through the act to the miniboss, results and the seamless
`-$2C00` handover to Act 2. Owning plan:
[LRZ bring-up](../../plans/2026-09-17-lrz-bring-up.md); starting inventory:
[LRZ placement inventory](../../research/s3k-zones/lrz-object-inventory.md).
Status: traversal families, miniboss, results and seamless handoff implemented.
Positioned320/800 boss-to-Act2 routes and palette-ramp replay pass. Fourteen preserved
native320 ordinary Sonic+Tails cold routes now include miniboss defeat, results and
playable Act2 at31440inputs, with185 full-registry replay spots. The complete route
uses no gameplay seeds and has zero deaths. Other character/donor/width products,
lifecycle coverage and native whole-scene acceptance remain open.

Incoming: level select / data select `$900`, SOZ2 end boss -> `$900` (verified as a request and
load at the end of the campaign, not the route entry). Outgoing: seamless `$901`.

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`,
same roster as HPZ and DDZ): current widths 320/352/400/528/800 (older rows retain historical presets); supported character/donor pairs
off x {Sonic, Tails, Knuckles}, S1 x Sonic, S2 x {Sonic, Tails}; teams: solo, Sonic+Tails,
S1 Sonic+Sonic duplicate, S2 Sonic+Tails, Sonic+Tails+Knuckles at 800.

Five claims are tracked separately and never aggregated: **implemented**, **cold-reachable**,
**rewind-verified**, **native behaviour matched**, **visually matched**. Nothing below certifies
the act.

Current `TestS3kLrzPlacementCensus` passes with609 placed objects and zero placeholders.
Historical slice3 baseline: **21 built a `PlaceholderObjectInstance`**, `$9A` and `$9B` (239 at `035e48a58`, 205 after slice 1,
199 after the dash elevator, 171 after slice 3b, 170 after the corkscrew, 98 after the rest of
3a and 3c, 83 after `$21`, 77 after `$22`, 75 after `$9C`); 331 live rings (332 records minus
the leading `(0,0)` sentinel). The baseline only ratchets down.

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| BASELINE: placed object and ring census | `LRZ1_Sprites` `$1F6E50` (609), `LRZ1_Rings` `$1F874C` (332 records, 331 live; `loc_EB52`/`loc_E8BE`) | native | `TestS3kLrzPlacementCensus` | implemented | pass, `3418eba6e` | Ratchet target 0 placeholders |
| ENTRY: `$900` resources, bounds, object set, title card | Registry/sprite/screen-event tables; `Sonic3kLevelResourceProfile` | native | `TestSonic3kLevelLoading`, `TestS3kLrzFallingIntroBootstrap` | implemented (inherited) | pass | Not re-verified for this campaign |
| ENTRY: falling intro / Knuckles intro run | `loc_68A6`; `LRZ1_BackgroundInit` Knuckles `$F6` chunk | native 320 | `TestS3kLrzFallingIntroBootstrap`, `TestS3kLrzDomeBackgroundHeadless#knucklesGetsTheBackgroundChunkAndSonicDoesNot` | implemented | pass | The `$F6` write is background row 1 column 4, and the Sonic half asserts the placed layout does not already hold it |
| PRESENT: parallax, bands and shake | `LRZ1_Deform`, `LRZ1_BGDeformArray` `$40,$20,$10x5,$100,$10x3,$20`, `ApplyDeformation` at `HScroll_table+$00C`; hand-walked band runs for camera `($800,$320)` | 320 and 640 | `SwScrlLrzTest`, `TestS3kLrzScrollRegistrationHeadless` | implemented | pass, `bbd156d37` | Visually matched only against the previous fallback (clip `02`), not against native |
| PRESENT: animated tiles ch0/ch1 and `AniPLC_LRZ1` | `AnimateTiles_LRZ1` / `loc_282D0` (ch0 phase, unsigned `mod $30`), `loc_28364` (ch1 `& $1F`), split tables `word_2834C`/`word_283D2`, `Anim_Counters+1/+3` seeded `-1` by `Animate_Init`, `AniPLC_LRZ1` `$28A6A` after them | native | `TestS3kLrzPatternAnimation` (all 48 ch0 and 32 ch1 phases against the ROM art bytes; channel order before the scripts) | implemented | pass, `1ef1256ca` | Visually matched by clip `03`; not compared with a native capture |
| PRESENT: palette cycles | `AnPal_LRZ1` | native | `TestS3kLrzPaletteCycling` | implemented (inherited) | pass | Not re-verified for this campaign |
| PRESENT: rock sprites | `Draw_LRZ_Special_Rock_Sprites`, `sub_1CB68`, window `loc_1CAF4` (front `Camera_X-8` forced to 1, back `+$150`), vertical `0 <= y - Camera_Y + 8 < 240`, emitted at the end of priority level 0 (`Render_Sprites_NextLevel`) | native + wide | `TestLrzRockSpriteRenderer` | implemented | pass, `fbbb793f7` | Wide viewports widen the back key only (presentation choice, recorded in the renderer); the ROM's missing sprite-budget check is reproduced, not guarded |
| EVENT: runtime state and rewind adapter | `Events_routine_bg`, `Events_bg+$0C/$10/$12`, background camera copies, `LRZ_rocks_routine`, stored camera bounds, `ShakeScreen_Setup` | native | `TestS3kLrzScrollRegistrationHeadless#runtimeStateCaptureRestoreRoundTrips` | implemented | pass | Words for later slices join the same state; no route rewind spot yet |
| EVENT: screen-event chunk edits and rock crusher | `LRZ1_ScreenEvent` `Events_bg+$0C` both signs (`a3` is `Level_layout_main`, long entries, so `$38`/`$3C`/`$40(a3)` are foreground rows 14/15/16 and `$1D(a1)` is column 29); `loc_90512`/`loc_9056E` | native | `TestS3kLrzCrusherChunkEditHeadless` | implemented | pass, `cfa443e13` | The headless case asserts the placed layout differs at the first edited cell before the request, so a no-op could not pass |
| EVENT: dome regions and locked background | `sub_56DCA`/`word_56F88` (three 5-word rows), `sub_56DAC`, `Obj_56EA0`, `LRZ1_BackgroundEvent_Index` stages 0/4/8, `Draw_delayed_rowcount $F` | native + wide | `TestLrzDomeRegions`, `TestLrzBackgroundStageMachine` (5), `SwScrlLrzTest` locked and pinned modes, `TestS3kLrzDomeBackgroundHeadless#lockingInsideARegionRewinds`, `TestLrzDomeLavaPlatform` | implemented | pass | State, stage machine, scroll and a whole-composite rewind spot inside a locked region. **Not visually verified and not verifiable today**: the engine's act 1 background plane draws no visible pixels at the dome, proved by an absurd-offset ablation, so no clip can show the lock ([s3k-known-bugs](../../../status/s3k-known-bugs.md)) |
| OBJECT: lava blocks `$6E` (34 placements, 4 subtypes) | `Obj_InvisibleLavaBlock` -> `bset #4,shield_reaction` -> `Obj_InvisibleHurtBlockHorizontal`; `sub_1F58C` mask `$73` | native, all five shield states | `TestSonic3kInvisibleHurtBlockHObjectInstance` | implemented | pass, `bbd156d37` | Fire-shield clip deferred to slice 3 (no teleport-and-walk route from a `$05` monitor to a `$6E`); clip `05` shows the hurt |
| OBJECT: dash elevator `$1E` (6 placements, 6 subtypes) | `Obj_LRZDashElevator` / `sub_4301C`: latch on `anim == 9`, ride on `anim` 2 or 9, push `8 + spin_dash_counter` negated when facing right, position clamped to `(subtype & $7F) * 8` | native 320, Sonic + Tails | `TestLrzDashElevatorObjectInstance` | implemented | pass, `1e01edaa0` | Clip `08` and a capture of the `($8A0,$50C)` placement travelling exactly 400 px; no wide or donor row yet, and no rewind spot mid-ride |
| OBJECT: doors and switches `$19` (15), `$1A` (1), `$1C` (10), `$1D` (2) | `Obj_LRZDoor` (`tst.b Level_trigger_array[subtype & $F]`, one-way latch, `GetSineCosine($2E) asr #2` negated over 64 frames), `Obj_LRZBigDoor` (unsigned Y band `[y+$40,y+$C0)` and signed X `>= $50`, `asr #1` added, `Screen_shake_flag` held at `-1`), `Obj_LRZButtonHorizontal` (`swap d6 / andi.w #3` side touch; subtype bit 6 -> bit 7, bit 4 -> latch), `Obj_LRZShootingTrigger` (`(subtype & $F0) >> 2` period, `Touch_Special` `collision_property`, `sub_42EC0` only for `anim == 2`) | native 320 | `TestLrzDoorsButtonsAndTriggers` | implemented | pass, `d2c58f148` | Cold-reachable for `$1C`/`$19` (route v5 opens the `$04` door from the level start); rewind-verified by `TestLrzDoorButtonRewindSpots` (before/active/after + forward replay). The `$1C` buttons are now solid to land on as well as to walk into: `loc_1E154` re-reads `width_pixels(a0)`, which this caller sets equal to its `d1`, and the shared `d1 - $B` reconstruction gave a ten-pixel landing strip (`TestS3kLrzButtonHorizontalLandingHeadless`, cold-route row 3154). Still owed: wide and donor rows, a cold-route spot for `$1A` and `$1D`, and `sub_42EC0` on a route. The big door's ROM respawn-table bit has no engine home (see [s3k-known-bugs](../../../status/s3k-known-bugs.md)) |
| OBJECT: corkscrew `$15` at `($1240,$3D8)` | `Obj_LRZCorkscrew`: half-open horizontal and inclusive vertical capture box, `ground_vel` floored to `$600` then `+$10` a frame to `$1000`, accumulator high word as the ride parameter, `$700` end, both exits `neg.w ground_vel` | native 320 | `TestLrzCorkscrewObjectInstance`, `TestLrzCorkscrewRewindSpot` | implemented | pass, `9b0608d96` | Capture floor and acceleration confirmed against native rows 3393-3400; clip `14`; rewind-verified. No wide or donor row, and no cold-route spot |
| OBJECT: traversal families `$16 $17 $18 $1B $1F $20 $21 $22` | Per-id `Obj_LRZ*` routines and tables: `$16` `sub_42636` capture/ride/eject with the leftward speed floor at `-$400`; `$17` the `$2E` sine angle; `$18` the subtype-in-pixels trigger distance; `$1B` `render_flags` bit 7 gating the shot; `$1F` the four-second cycle; `$20` `sub_43604`'s chain; `$21` `loc_43128`'s `y_vel` accumulator, `RawAni_43196` and the `loc_1E10E` crush branch; `$22` `loc_4397E`'s grind and `loc_4389E`'s roll | native 320 | `TestLrzWallRideObjectInstance`, `TestLrzSinkingRockObjectInstance`, `TestLrzFallingSpikeObjectInstance`, `TestLrzFireballLauncher`, `TestLrzLavaFall`, `TestLrzSwingingSpikeBall`, `TestLrzSmashingSpikePlatformObjectInstance`, `TestLrzSpikeBall` | implemented | pass, `f0b7a6eff` | Clips `16`-`23`. Rewind spots for `$16`, `$17`, `$18`, `$1B`, `$1F`, `$20`, `$21`. Owed: wide and donor rows, act 2 skins, a rewind spot on `$18`'s landing boundary (needs real terrain) and route spots |
| OBJECT: rock crusher `$9C` subtypes 0 and 2 | `Obj_LRZRockCrusher`: `Check_CameraInRange` over `word_901B8`/`word_901C4`, the two `loc_901F4` camera latches, the `bchg #0,$38` rumble, `loc_90512`'s two request shapes, `word_902EC` drop targets, `byte_904AC` piece shake | native 320 | `TestLrzRockCrusher`, `TestS3kLrzCrusherChunkEditHeadless` | implemented | pass, `cfa443e13` / `98a8c7261` | Clip `24`. `loc_90368`'s badnik art requeue waits for slice 4's remaining consumers. Cold controller contact/release and full-registry replay are covered by the crusher/lower-east routes below; subtype and breadth gaps remain |
| OBJECT: badniks `$99 $9A $9B` (74 placements) | `Obj_Fireworm`, `Obj_Iwamodoki`, `Obj_Toxomister`; `PLCKosM_LRZ` | native 320 + 400, S1 donor, four rosters | `TestFirewormBadnikInstance`, `TestIwamodokiBadnikInstance`, `TestToxomisterBadnikInstance`, `TestS3kLrzCompatibilityMatrix` | implemented (all three) | pass, `cad4a2e07` | Clips `25`, `26`, `27` (the worm) and `28` (the mist catching a player and pinning him). `$9A` has no touch collision at all: it is a solid block with a fuse. `$9B`'s mist is a player hook -- an eighth off the speed a frame and a ring a second, escaped by a spindash or six left/right reversals. `$99` is four ROM objects: an invisible spawner, the head (the only attackable part, and the only one with DPLC art), four segments that each wait `word_8F940` frames before joining, and a flame on each segment. Later cold routes cover retired Fireworm contact, Iwamodoki fragments and crusher encounters; the cloud special-response correction and pending-contact rewind are recorded under2e56d7141 below. Full placement/roster breadth remains open |
| REWIND: route-position spots for `$18`, `$1A`, `$9C` and `$9A` | `loc_428D6`'s `MoveSprite` + `ObjCheckFloorDist` landing (needs real floor data), `Obj_LRZBigDoor`'s opening ramp, `Check_CameraInRange`'s rumble release, `Obj_Iwamodoki`'s lit fuse | native 320, act 1 entered at fixture route rows | `TestS3kLrzRouteRewindSpots` (4) | implemented | pass | `$18` (mid-fall and landed) and `$1A` compare the whole composite and require the diverging frame to change something first. `$9C` and `$9A` compare the parent's own ROM fields instead, so these legacy checks alone do not prove child recreation; later cold-route checks compare the full registry across live crusher and Iwamodoki interactions. **Coverage limit:** these enter the act at a route position, they are not walked to from the level start. Later cold shield-route checks cover `$1D` projectile interaction; the original attempted placement latch is not credited by that result |
| BREADTH: rosters, viewports and the S1 donor for every slice 3/4 class | Configured roster, `SCREEN_WIDTH_PIXELS`, `CrossGameFeatureProvider`; the S1 donor's own `playerCapability().spindashEnabled() == false` | Sonic / Sonic + Tails / Tails / Knuckles, 320 and 400, donor off and `s1` | `TestS3kLrzCompatibilityMatrix` (10 rows) | implemented | pass | Asserts the live roster, the viewport reaching the camera, the donor's capability rules reaching the playable, and a ready ROM-backed renderer for every art key a slice 3/4 class draws from. Broken on purpose once with a nonexistent art key: all ten rows failed. It does NOT re-assert registry id resolution, which `TestS3kLrzPlacementCensus` pins exactly |
| OBJECT: shared families already concrete (105 rows, 340 placements) | SK Set 2 pointer table | native | `TestS3kLrzPlacementCensus` (classification only) | implemented | classification pass | Per-subtype behaviour unverified |
| BOSS: miniboss `$9D` at `($2CA0,$880)` | `Obj_LRZMiniboss`, `off_7854C` 11 slots, `collision_property` 6; `sub_78C14`'s `$20` invulnerability; `sub_78CF4`'s `collision_property 4` hands and `loc_78D2C`'s `$38` flags | native320 +800 positioned | `TestLrzMinibossInstance`, `TestLrzMinibossHitPath`, `TestS3kLrzBossRewindHeadless`, `TestLrzPostBossPaletteRouteCapture` | implemented | pass, clips `29`-`32` | The whole fight is filmed end to end from one capture (`raw-45-lrz1-miniboss-full-fight`): arrival and arms (`29`), a hit and its flash (`30`), the right hand's fourth hit and its arm peeling (`31`), and the sixth drill hit and the defeat (`32`). Three hits fit one 95-frame slam window; the hands take one hit per pass through jump height. **Coverage limits**: the capture is a positioned entry at `($2C00,$600)`, not a cold walk-in, and it is Super Sonic with all seven emeralds, which is what the recorded native run is from its row 24174 but is not the ordinary-Sonic fight. Later follow-ups add native/wide positioned controller fights and boss-graph restoration; the ordinary native320 cold team fight is now verified below; donor/roster breadth remains open |
| LOAD: results and seamless `$901` handover | `loc_56CAA` rebases players/objects/camera; `loc_78AA8` post-results palette ramp and release | positioned320/800 Sonic with declared355rings/seven emeralds | `TestS3kLrzSeamlessActChangeHeadless`, `TestS3kBossDefeatSignpostFlow`, `TestLrzPostBossPaletteRouteCapture` | implemented | Positioned boss/results/Act2 routes and mid-ramp whole-registry replay pass; see 2026-09-23 handoff follow-up | The original stale lava plane and stuck results claims are superseded by the implemented ramp/release. Ordinary native320 cold team handoff is verified below; broader roster/donor checks and seamless timeline policy evidence remain open. |
| ROUTE: cold act 1 from the level start | Controller-driven from `($100,$20)`, no teleport or ring seeds | native320 Sonic+Tails | `TestLrzColdRouteCapture` (14 independent input routes) | native320 cold team complete | Through playable Act2(2357,1980) at31440inputs, zero rings/deaths;185 full-registry replay spots, clear follow-up below | Other route products remain open. Earlier corkscrew/door/shield/crusher frontiers are closed by the dated follow-ups below; strict native parity remains a separate claim. |
| ORACLE: strict segment replay | `TestS3kSonicTailsLrzSegmentTraceReplay`, `TestS3kTailsFullChainLrzSegmentTraceReplay` | `-Ptrace-segments` | — | — | see [trace frontier log](../../../status/trace-frontier-log.md) | Slice 11 |

## Execution evidence

Worktree `.worktrees/ai-lrz-bring-up`, ROMs by absolute path, `maven_queue.py -Dmse=off`.
Slice 0 (`3418eba6e`): `-Dtest=TestS3kLrzPlacementCensus,TestSonic3kRingPlacement,TestS3kLrzFallingIntroBootstrap,TestS3kLrzPaletteCycling,TestSonic3kLevelLoading`
= 65 tests, 0 failures, 0 errors, 0 skips. Shared ring change re-checked with
`TestS3kAiz1SkipHeadless,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestRingManager,TestS3kAiz2BigRingCollision,TestS3kAiz2BigRingFormation,TestS3kCnzLateSSEntryRingPlacement,TestRingSparkleDelay`
= 59 tests, 0 failures, 0 errors, 0 skips.
Slice 1 (`bbd156d37`): focused batch of 1263 tests, 0 failures, 0 skips, plus `-Pguards` 669 tests,
0 failures. Media: `raw-00-lrz1-before/`, clips `00a-lrz1-baseline-before-work.mp4`,
`02-lrz1-parallax-before-after.mp4`, `05-lrz1-lava-block-before-after.mp4`.

### 2026-09-22 continuation: actual boss graph restoration

`TestS3kLrzBossRewindHeadless` uses a declared arena entry at `(2C00,600)`.
It captures unfolded arms, production hit flashes and defeat debris, explicitly
removes live children, then restores and compares every world snapshot entry and
one-frame forward replay. Both tests pass; direct eligible touch calls make this
rewind evidence, not controller-route completion. The inherited missing child
constructors and resurrection of retired arms are fixed; see the
[campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md#rewind-findings-from-the-continuation).
Cold route, donor/team/viewport fight breadth and native comparison remain open.


### Runtime art queue follow-up (2026-09-23)

The miniboss now submits its ROM module and explosion PLC at `loc_78592`,
retaining the native 48-update wait. `TestLrzMinibossResources` tests the actual
boss's one-time submission and all module pixels. The resource/boss/rewind/art
selection passed 108 tests with no skips; three separate rewind structural
checks also passed without skips. Native Nemesis scheduling is not established
by this repair because the existing S3K PLC application is synchronous.

#### Arrival capture correction (2026-09-23)

The runtime-art arrival videos starting at `$2C00,$600` omitted the ROM
priority switch at `$2BA0,$750`; their low-priority Sonic is invalid approach
setup, not priority-parity evidence. Replacement setup `$2B70,$750` crosses
that marker using production controller input. Capture CSV includes
`high_priority`; the latch regression also exercises restore/forward crossing.
The shared arena-centering follow-up below supersedes the then-open widescreen
framing issue; native gameplay bounds remain authoritative.


#### Shared arena centering (2026-09-23)

The native viewport is centered at wide resolutions without changing player
boundary words. `TestNativeArenaCameraFraming` covers LRZ1 and DEZ2 at all five
widths, both view limits, unchanged bounds, fresh state and captured policy
restoration. The LRZ arm regression retains world anchors `$2C20/$2D20`;
release checks exercise either side of both rebased thresholds and the
production seamless transition carries the framing flag. DEZ encounter tests
cover the native exit wall and the original final-act request threshold.
Positioned captures live under the campaign archive: LRZ
`miniboss-centered-20260923-800-v2`, DEZ `104-end-boss-centered-800`.
The 600-frame LRZ native run is unchanged and its wide gameplay CSV matches
exactly except for camera X minus 240. The DEZ 800px controller replay completes
eight real enemy hits and loads zone 23 after 6837 steps, no hurt/death rows.
These bounded checks do not close inherited cold-route, donor/team, native
parity or whole-zone rewind obligations. Combined delivery validation remains due.


### Post-results palette and handoff follow-up (2026-09-23)

The13-row ROM palette ramp and shared clock freeze/release are implemented.
Native111-frame observation corroborates all colors/durations, callback68 and
post-AnPal write ordering (CRAM one frame later). Native/wide actual positioned
boss/results/Act2 routes have no hurt/death and pass whole-registry mid-ramp
restore/45-input replay. Final corrected selection passes77cases;24S3K palette
consumer classes pass128cases, no skips. Earlier LRZ/required-S3K selection
passed558cases before the native timer-order correction. Full campaign checks
remain owed. See the [campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md).
This is not cold full-act or whole-scene native certification. Subsequent
[Act2 follow-ups](s3k-lrz-act2.md#death-egg-background-follow-up-2026-09-23) implement
the Death Egg and clip repeated terrain outside the finite layout. The remaining
Plane B seam is presentation polish, not the former missing-owner/repeated-terrain defect.

### Automatic bounds mask follow-up (2026-09-24)

The user replaced per-arena activation with a shared bounds-derived default.
Native camera/player words remain unchanged; presentation masks outside the
native view union, with world-relative fade history for newly covered visible
pixels and deterministic rewind. The 99-case focused geometry/GPU/presentation/
SSZ-route/required-S3K selection passes without skips. Positioned800 activation
footage is under `campaign-20260924-bounds-activation-800` in this zone's external
capture directory. SSZ additionally has GHZ800/400 and MTZ800/352 defeat/release
captures; all zero deaths. This does not close cold-route or cross-game breadth
obligations; combined delivery checks remain pending.

The follow-up destination-bound trial uses the shared boss gate's already-known
`_unkFAB4/_unkFAB6` rectangle, preserving `loc_85D06`'s live native ramp. Static
fades on level columns toward that destination rather than following the moving
current boundary. A 12px edge strip fades more slowly (45–90 ticks); interruptions
reverse from current opacity. The refreshed positioned800 preview is
`campaign-20260924-bounds-destination-feather-800/capture.mp4`; all 600 gameplay
rows match the prior capture and no deaths occur. Shared presentation/GPU/gate
checks pass 46 tests; LRZ boss/rewind/camera and required S3K regressions pass 109
with no skips. Full delivery and inherited breadth obligations remain open.

Player-aware bounds trial: two positioned800 simple-crossfade demos in external
`campaign-20260924-player-aware-mask/{original-input,turn-back}/capture.mp4`
cover entering destination bounds and returning to the current-bound strip.
Both have600 frames, unchanged probe/input gameplay and zero deaths. A return
at129 reverses opacity on that frame and clears by146; re-entry near289 resumes
fade-in. TestLevelBoundsMaskGeometry covers body extent, both edges, multiple
participants, follower suppression and inverted bounds. The focused78-test
selection plus corrected geometry-fixture rerun is described in the arena design;
this adds no cold-route, donor or complete viewport-breadth claim.


Latest mask revision supersedes the destination/player-aware trials above:
current native bounds remain authoritative throughout staged entry, and each
side has one shared crossfade deadline. Production 800px previews are in
`campaign-20260924-shared-edge-mask/{original-input,turn-back}/capture.mp4`
under the same external LRZ archive. Both600-frame runs retain identical gameplay,
zero deaths and complete decodes. Sixteen focused transition/geometry/GPU/gate/
presentation tests pass without skips; see the arena design for exact selection.
This is positioned presentation evidence, not additional cold-route coverage.


Source/target shape refinement replaces the carried fade deadline from911aad01b:
each changed effective boundary crossfades from the displayed mask with fresh
shared progress. Latest production previews are
`campaign-20260924-lock-shape-crossfade/{original-input,turn-back}/capture.mp4`.
Both600-row gameplay sequences remain identical, with zero deaths and complete
video decodes. The same16 focused tests pass without skips. Earlier/current
bounds remain authoritative until the final gameplay lock takes over.


## Cold corkscrew continuation (2026-09-24)

From campaign base `24813a647`, the cold ordinary Sonic+Tails route exposes a
missing `move.b #0,angle(a1)` in `loc_422E6`. The stale approach angle `$12`
survived the controlled ride and contaminated the first free step after
`loc_42396`. Clearing it at capture reproduces the ROM operation, with no
widescreen or fixture-specific behavior. A focused assertion fails before the
fix (expected0,actual18). Corkscrew unit/rewind plus the four mandatory S3K
bootstrap classes pass72 tests with zero failures/errors/skips.

`lrz1-sonic-tails-cold-corkscrew-320.{script,bk2}` preserves4501 controller frames
from the normal falling intro through rocks/door, the horizontal button,
corkscrew capture/ride/release and lower westbound platforms. No setup overrides
are used. `TestLrzColdRouteCapture` checks21 whole-registry45-frame replay spots
and the ROM's release row: input3558 centre`$1235,$057C`, velocity`$F000,0`,
ground speed`$F000`. The endpoint is `(2746,1186)`,93 rings, live Sonic+Tails.
This is a partial act route, not a completion claim.

Its first rewind run failed at input950: eight retired Fireworm body segments
changed `preUpdateCollisionFlags` from0 to`$98` after recreation. Their deleted
heads were absent, so consulting only the old head link lost the ROM's permanent
`loc_849D8` collision clear. The segment's already-captured `retired` state now
keeps it harmless independently of that link; a focused detached-head check and
the real cold route exercise the boundary. Final verification: queued Maven `-Dmse=off -Dopenggf.test.gl.native=true`
`-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen -Dtest=TestFirewormBadnikInstance,TestLrzColdRouteCapture test`
passes11 tests,zero failures/errors/skips,75 seconds including compilation; the
4501-frame route and all21 replay checkpoints pass in7.72 seconds.

The change-based plan against `24813a647` falls back to full ordinary categories
because of the new route-test owner. Focused iteration is appropriate for these
two local object changes: capture state, actual release, segment retirement and
real registry recreation are directly exercised. The full combined campaign
selection remains required before integration, and is not replaced by these
focused checks.

Capture `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-corkscrew-corrected-320/capture.mp4`
shows3350–4500; all4501 state rows have zero deaths. Stills3394/3565/4400 are
inspected and the full MP4 decodes cleanly. The subsequent Fireworm change only
corrects restored worlds and does not alter this uninterrupted forward capture.
See the [frontier log](../../../status/trace-frontier-log.md) for the bounded
native comparison and later unresolved position/ring disagreements.


### Shooting-trigger shield response (2026-09-24)

After `71e341556`, the next cold-route disagreement is a projectile hit at4568.
Native aux slot23 (`loc_42EE8`) travels `(2409,1221)` at4564, then deflects to
`(2401,1220)`, `(2393,1219)`, `(2385,1219)`, `(2377,1218)` at4565–4568.
The engine continued to `(2417,1229)` and hurt Sonic at `(2424,1233)`.
Native `status_secondary=$11` identifies a fire shield. The initial informal
lightning-shield description was wrong; deflection uses bit3 independently of
the fire-immunity bit4.

`loc_42E00` writes `bset #3,$2B(a1)` when creating this shot. The projectile
omitted both that shield-reaction bit and the deflection callback. The new
response uses the existing canonical single-region shield profile and transcribes
`Touch_ChkHurt_Bounce_Projectile`: word deltas, native angle/trig, multiplication
by`-$800`, arithmetic shift8, then permanent collision clear. Motion fractions
remain intact for the existing `MoveSprite2` update and the new collision-enabled
state is captured by the generic rewind owner. A focused four-direction test
fails before the change (expected reaction8,actual0).

Queued Maven `-Dtest=TestLrzShootingTriggerProjectile,TestLrzDoorsButtonsAndTriggers,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
with `-Dmse=off` and absolute S3K ROM passes79 tests,zero failures/errors/skips
(75 seconds including compilation). The subsequent native-GL `TestLrzColdRouteCapture`
run passes both routes,zero failures/errors/skips (11.64 seconds test,30.868 total).
`lrz1-sonic-tails-cold-shield-320` preserves4901 inputs from normal entry and adds
8 full-registry replay spots around projectile deflection and its following
route (29 across the two cold routes). At4568 the test requires a live deflected
shot with collision0 and negative X velocity, Sonic's retained shield, no hurt,
and native centre `(2424,1233)`. Endpoint `(2206,1334)`,95 rings, live team.

`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-shield-deflection-320/capture.mp4`
shows4500–4900. All4901 state rows have zero deaths; stills4563/4568/4630/4820
were inspected and the full video decoded cleanly. The longer unchanged input
now first diverges in player Y at4951 (1417 versus1421), X at5021 (2188 versus2187),
and dies at7040. The dash-elevator continuation is the next cold-route frontier;
this does not erase the earlier ring-count or intro comparison differences.
Combined campaign checks and native whole-scene acceptance remain pending.


### Dash-elevator jump-off (2026-09-24)

After `3e7f4f80b`, the next difference is input4951: native Y1421 with launch
velocity`-$680`, engine Y1417 with the same velocity. The collision-trace hook
shows Y1421 before the dash elevator's solid checkpoint and Y1417 afterwards;
the checkpoint reports no contact. Every preceding checkpoint retains Y1421.
The lift's movement itself matches through4950.

`loc_43000` calls `SolidObjectFull`, whose standing-bit/airborne branch
`loc_1DC98` clears support and returns. The engine's old generic standing record
had already been cleared by an earlier object checkpoint. With no provider
declaration for the remaining per-object standing bit, the lift fell through
into new-contact overlap correction. `airborneStaleStandingBitReturnsNoContact`
already models the native early return; the elevator now opts into it together
with `airborneRiderUnseatRequiresOwnCheckpoint`. The first flag alone fails the
same regression: earlier checkpoints have already consumed the riding record.
Both declarations are required to retain ownership through the native return.
Shared collision code is unchanged. The real cold-route regression fails before this
change (expected1421,actual1417), preserving the exact native launch assertion.

`lrz1-sonic-tails-cold-elevator-320` preserves5001 ordinary input frames, with4
additional whole-registry replay spots around charging, descent and jump-off.
The earlier two route tests remain independently runnable. Final queued native-GL
`-Dtest=TestLrzDashElevatorObjectInstance,TestLrzColdRouteCapture test` with
`-Dmse=off -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen` passes10 tests,zero failures/errors/
skips (85 seconds including compilation; three route tests19.71 seconds).
The preceding broader focused run passed the mandatory S3K loading/bootstrap
classes but failed the new launch assertion; it is not reported as a green run.
There are33 full-registry replay spots across the three cold route fixtures.

The longer unchanged input now matches player Y through5175 and X through5591
in this local post-elevator comparison. The next disagreement is Y5176:
engine1474,native1444; X5592:engine2785,native2790. The route dies9527 versus7040
before this fix. Earlier intro/ring disagreements remain; strict whole-prefix
parity and full-act completion are not claimed.

The selection plan against `3e7f4f80b` falls back to full ordinary categories
for the route-test change. This iteration uses focused validation because the
production change only declares the existing per-provider full-solid contract;
actual launch, descent, contact release and rewind are exercised directly.
The combined campaign still requires its full delivery selection.

Verified engine recording: `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-elevator-jump-fixed-320/capture.mp4`
shows inputs4800–5000 (201 rendered frames); the5001-row state log has zero
deaths. Inspected descent4880, launch4951 and airborne5000; the full video
decodes successfully. This is engine presentation evidence, not native pixel matching.

### Dash-elevator charge-byte selection (2026-09-24)

At `1c9269fbf`, ordinary input5176 first disagrees vertically: engine1474,
native1444. Collision checkpoint observation shows the lift itself moving31
pixels, then correctly carrying its rider; this is a separate arithmetic defect.
`loc_43082` adds byte `spin_dash_counter(a1)`, the high byte of the big-endian
8.8 counter used by the player's word-sized charge/decay operations. The engine
used `counter & $FF`, converting fractional decay into whole push. It now reads
`(counter >>> 8) & $FF`; signed direction, 16.16 integration and range clamps
are unchanged. The existing unit test had seeded8 instead of`$0800`, reproducing
the same interpretation error. Correcting the seed fails before the production
fix (expected196608,actual131072); coverage now also checks fractional`$01E0`.

The independent5501-frame `lrz1-sonic-tails-cold-charge-320` fixture preserves
ordinary cold input and adds5 whole-registry replay spots around recharge and
decay. Its native comparison assertion expects grounded rider2208,1444 at5176.
This does not certify whole-prefix parity or the remaining act.

Charge-byte verification at the working tree based on `1c9269fbf`: queued
`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen
-Dtest=TestLrzDashElevatorObjectInstance,TestLrzColdRouteCapture test` passes11
tests,zero failures/errors/skips (90 seconds). Four cold routes now cover38
full-registry replay spots. Engine video
`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-charge-corrected-320/capture.mp4`
contains381 rendered frames (5120–5500),5501 state rows and zero deaths;
5176/5250/5450 inspected and full decode succeeds.

The corrected longer input has a transient one-pixel Y difference5248
(engine1631,native1632), matching again5249; the cause is not yet attributed.
A later hurt event5591 is absent natively (engine2787,1701 with recoil,
native2787,1706 still descending), causing first X difference5592. That
contact is the next route target. Unchanged long input dies9181; route duration
alone is not a correctness measure. Cold full-act completion remains open.

### Exploding-rock fragment shield response (2026-09-24)

After `163781be9`, the consequential contact at5591 is an Iwamodoki fragment
near2790,1717. The engine loses its fire shield and recoils toY1701, while
native continues descending atY1706. `loc_8FB90` explicitly sets
`shield_reaction` bit3 before installing `S3KBadnikProjectile_Main`. The class
described this in its comment but implemented neither the reaction bit nor
the deflection callback/profile. It now uses the existing canonical deflection
profile, applies `Touch_ChkHurt_Bounce_Projectile`'s signed-word angle and
`-$800` velocity, and permanently clears collision. Position fractions and
the existing `loc_8FBB8` gravity/animation remain intact; no shared touch logic
changes. The new four-direction unit oracle fails before the change (expected
reaction8,actual0). A5701-frame ordinary cold route adds5 full-registry replay
spots across detonation/deflection and checks actual fragment damage clearing,
retained shield and native player position5591.

Validation: queued native-GL absolute-ROM object/route/mandatory-S3K run
(`-Dtest=TestIwamodokiBadnikInstance,TestLrzColdRouteCapture,TestS3kAiz1SkipHeadless,
TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`)
ran71 tests with one test-expectation failure: the longer route inherited95
rings, but both native and engine collect99 by5700. Object/mandatory-S3K tests
passed. Corrected only that endpoint expectation, then queued
`-Dtest=TestLrzColdRouteCapture test`:5 tests,zero failures/errors/skips,46.258
seconds. All43 replay spots across5 cold routes pass. The selection plan against
`163781be9` is broad; focused iteration covers this object-only change directly,
while the combined campaign still owes full delivery validation.

`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-shrapnel-deflection-320/capture.mp4`
shows5510–5700 (191 rendered frames),5701 state rows,zero deaths. Inspected
5589/5591/5640 and decoded the full video successfully. Subsequent long ordinary
input first disagrees after5500 atY5776 (1802vs1807), thenX5777 (3222vs3226).
Nearby objects identify the lava-fall drops at3264,1824/1776 during the hit.
Their documented bit4 is fire-shield immunity, not the bit3 deflection used
above; the drop currently lacks that reaction declaration. This is the next
implementation target, together with the same omission on launcher fireballs.
The long run dies7040; survival duration is not proof of parity. Earlier
transientY5248, intro/ring differences and full-act completion remain open.

### Fire-shield immunity on falling lava and fireballs (2026-09-24)

At `51f6670eb`, contact5776 takes the fire shield while overlapping falling
lava at3264,1824/1776. Native remains at3224,1807 without recoil.
`loc_436EE` and `loc_42BF6` both write shield-reaction bit4 on their respective
children. The drop/fireball providers omitted it; their comments incorrectly
called this a missing deflection capability. Bit4 selects fire-shield immunity
in `Touch_ChkHurt`; bit3 selects the distinct projectile-deflection path.
Both providers now publish`$10` through the existing touch-profile mapper;
shared damage handling is unchanged. Unit tests fail before the change
(expected16,actual0 for both) and distinguish immunity from deflection.
A6001-frame cold fixture adds5 whole-registry replay spots around the real
falling-lava crossing, checking retained shield and native position5776.

Queued verification with native GL and absolute ROM:
`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen
-Dtest=TestLrzFireballLauncher,TestLrzLavaFall,TestTouchResponseManager,TestLrzColdRouteCapture test`
passes95 tests,zero failures/errors/skips,108seconds (six cold routes38.60s).
There are48 full-registry replay spots. The selection plan against`51f6670eb`
is broad; focused iteration exercises the two providers, shared touch contracts
and real crossing without claiming a full-suite pass. Campaign delivery
selection remains required.

Verified video`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-fire-immunity-320/capture.mp4`
contains271 rendered frames5730–6000,6001 state rows,zero deaths; inspected
5776/5805/5950 and full decode passes. The unchanged12000-frame diagnostic
input now reaches its budget alive, including special-stage return; this does
not certify the act or special-stage parity. The next local post5700 difference
isX5940 (3573vs3574): collision tracing reports a side hit on
`LrzDoorObjectInstance` while standing on the button. Engine speed is reset to0
where native advances with72. First laterY5968 is1826vs1827. Select the
door/button interaction next; earlier transient and intro/ring gaps remain.

### Slot lifetime behind the button-operated door (2026-09-24)

At `bc4e3285d`, door3600,1817 starts moving5909 while native starts5908.
At5940 engineY1772/native1771 produces the first side blockage. Native button
slot5 precedes door10; engine door10 precedes button27. A cold observation
shows old bridge328, sinking rocks1272/1344/1416 and landed spikes640/704/768
still allocated long after the camera has left them.

`Sprite_OnScreen_Test` is not drawing-only: its unsigned coarse-X comparison
branches to `loc_1B5A0`, clears respawn bit7 and deletes. The LRZ implementations
had disabled shared unload after following that mistaken guide assertion.
Removed the overrides for sinking rocks, falling spikes and smashing platforms;
intact collapsing bridges use normal unload, while their post-collapse
`loc_39CE8` countdown genuinely has no range tail and remains independently
owned. Door timing is not patched or keyed to the route.

The guide claim originated in`e45a2428cf`; the sinking-rock override was
introduced in`d38a4aa343`. Both skill mirrors now explain the helper body and
phase distinction. The sibling audit finds an unresolved HCZWaterWall rationale
and records it in S3K discrepancies without changing HCZ behavior. Checked
DEZ energy-bridge and LBZ cup providers already have real range predicates.
The cold regression at2000 fails before correction on stale bridgeX328.

Verification: queued native-GL absolute-ROM
`-Dtest=TestLrzColdRouteCapture,TestLrzSinkingRockObjectInstance,TestLrzFallingSpikeObjectInstance,
TestLrzSmashingSpikePlatformObjectInstance,TestLrzSinkingRockRewindSpot,
TestLrzFallingSpikeRewindSpot,TestLrzSmashingSpikePlatformRewindSpot,TestS3kAiz1SkipHeadless,
TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
with`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`
passes96 tests,zero failures/errors/skips,104seconds. Added3 full-registry
spots5907/5908/5939 and a5940 native X/speed assertion to the existing6001-frame
fixture; focused `TestLrzColdRouteCapture#coldTeamCrossesFallingLavaWithoutLosingFireShield`
then passes1 test,zero skips,26.944seconds. Six cold routes now cover51 spots.
The development selection plan against`bc4e3285d` is full; these are iteration
checks, and combined delivery validation remains required for slot-lifetime impact.

Corrected observation: the button occupies slot5 and runs before the door;
the door opens5908, matching native. Player coordinates match locally from5700
through6066. NextX6067 is3941vs3956 andY1758vs1759, in the rock-crusher area
with falling pieces nearby. That interaction is the next target, not attributed
yet. The12000-frame diagnostic remains alive but does not finish the act.
Earlier transient/intro/ring differences remain open.

Verified`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-door-slot-release-320/capture.mp4`:
131 rendered frames5870–6000,6001 state rows,zero deaths; inspected5908/5940/5990
and full video decode succeeds. Native-state comparison supports timing and
coordinates; the engine recording alone is not native pixel matching.

### Rock-crusher hit lockout (2026-09-24)

At `ce6c2741a`, positions first differ6067, but the causative velocity change
is6066: both players are3948,1758 after fire dash; nativeX velocity`$800`,
engine`-$800`. The body and upper pieces expose nonzero collision property
but did not implement `Touch_Enemy`'s collision clearing or `sub_905A8`.
They could rebound again during the native32-tick disabled interval.

Implemented per-body/per-upper-piece collision-disable and flash timer. The
shared touch owner still applies player rebound; each object's native update
then starts32, plays BossHit, alternates `word_905FC` banks into line2 colors
11/13/14, decrements and restores its saved collision byte at zero. Palette
bytes come from verified ROM address`$905FC` (12bytes); normal/white banks
match the disassembly and writes use the existing palette registry. Position
and gravity behavior are unchanged. The independent unit test fails before
correction because the crusher lacks the attack callback; it covers all32
ticks for body and an upper piece. The6201-frame ordinary cold fixture adds
5 whole-registry replay spots around first hit, renewed dash and recovery.

Queued native-GL absolute-ROM
`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen
-Dtest=TestLrzRockCrusher,TestLrzColdRouteCapture test` passes19 tests,zero
failures/errors/skips,100seconds (seven routes37.62s). There are56
whole-registry replay spots. The plan against`ce6c2741a` is broad; these are
focused iteration checks, not the still-pending combined delivery selection.

Verified engine video
`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-crusher-hit-recovery-320/capture.mp4`
contains201 rendered frames6000–6200,6201 state rows,zero deaths; inspected
6055/6066/6090 and full decode succeeds. It demonstrates the corrected rebound
exchange; complete native visual acceptance of the underground crusher/flash
remains open. Corrected player coordinates match locally from6000 through6233.
Next input6234 is engine3889,1820 vs native3887,1826; ownership is not yet
attributed. The unchanged long input dies11040; duration is not a correctness
metric. Earlier transient/intro/ring differences remain.

Source follow-up: `loc_903F4` falls directly into `loc_90408` when a piece is
released; the existing Java switch advances its routine without that same-pass
countdown. Audit this adjacent shake phase and its current unit expectations
before certifying the entire crusher sequence.

### Crusher piece release and native lag boundary (2026-09-24)

At `8f01665a5`, the existing unit expectation delayed `loc_90408` one object
pass after `loc_903F4` releases a piece. ROM falls through immediately: a zero
delay enters shake routine6 in that same pass; a four-frame delay is already3.
Correcting the oracle fails before production change (piece0 expected6,actual4).
Both release and later countdown dispatch now use the same routine helper.

The prior next difference6234 is now attributed to native lag, not a new
movement defect: native gameplay counter remains`$185A` while VBlank advances
`$517`→`$518` and lag counter becomes1. At6235 native resumes with the engine's
6234 position. Ordinary authored route capture executes a gameplay step for
each input; it is not the hardware-timed canonical replay. No frame-number
gate, state synchronization or delay is introduced to make this comparison
align. Continue ordinary route authoring from the verified prefix; strict
hardware timing acceptance remains a separate obligation.

Focused verification on `8f01665a5` plus this correction used queued Java21,
native GL, absolute S3K ROM and
`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen -Dtest=TestLrzRockCrusher,TestLrzColdRouteCapture test`:
19 tests passed, zero failures/errors/skips,102seconds. This includes seven
cold routes and56 full-registry replay spots. Combined campaign validation
remains owed.

### Cold crusher descent and second elevator (2026-09-24)

On `5f70b27ea` plus route/test additions, `lrz1-sonic-tails-cold-lower-east-320`
extends the6201-input prefix to7671 ordinary controller frames. A short retreat
and timed jump clear the lower passage; charging the second dash elevator raises
Sonic to the eastern ledge. No engine change, state override or native hydration
is used. The endpoint is(4917,1712),103rings,live Sonic+Tails,zero deaths.
The script recompiles to identical BK2 inputs.

`TestLrzColdRouteCapture#coldTeamLeavesCrusherAndRidesSecondDashElevator`
passes with zero failures/errors/skips using queued Java21, native GL, absolute
S3K ROM and `-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen -Dtest=TestLrzColdRouteCapture#coldTeamLeavesCrusherAndRidesSecondDashElevator test`
(28.594seconds). Nine new45-frame full-registry replay spots are6230,6320,6450,
6540,6700,7180,7260,7420,7500. Earlier56 spots remain independently covered.
This validates authored reachability and replay, not native pixel/trace parity.

Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-cold-lower-east-320/capture.mp4`
films6200–7670 after the full cold prefix;7671state rows,zero deaths, inspected
6600/7300stills and successful full MP4 decode. The native recording takes a
different route after the6234lag boundary; no raw-row parity claim is made.
Continue east from the ledge. Full-act completion, roster/viewport breadth and
combined campaign integration remain open.

### Cold rock wall and spike-platform ascent (2026-09-24)

On `01e3ac68f` plus these route/test additions, the ordinary cold Sonic+Tails
route extends to8750 inputs, ending(5696,1484)with107rings,zero deaths and
the live sidekick. `lrz1-sonic-tails-cold-upper-ledge-320` preserves the authored
inputs: spin dash through the rock wall, timed crossing of the spike platform,
then board it from the eastern ledge, ride upward and jump into the upper tier.
Running underneath and jumping too early were rejected controller attempts:
the low ceiling cuts the jump short, and the platform can crush the player.
No runtime change was needed. The later eastern giant-ring entry is excluded
from the committed prefix; the onward upper route remains open.

Queued Java21, native GL and absolute S3K ROM:
`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen -Dtest=TestLrzColdRouteCapture#coldTeamBreaksRockWallAndRidesSpikePlatformToUpperLedge test`
passes1 test,zero failures/errors/skips. Nine45-frame full-registry replay
spots are7800,8030,8210,8300,8364,8390,8550,8620,8700;74 across the nine
independent cold routes. Authored script is compiled and round-trip checked
by InputLogAuthorTool.

Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-cold-upper-ledge-320/capture.mp4`
films8000–8749 after the full cold prefix.8750state rows,zero deaths,8665/8749
stills inspected and whole MP4 decode passes. The upper corridor's foreground
obscures Sonic in the latter still; matched native visual acceptance remains
open. This is ordinary route/replay evidence, not strict trace or pixel parity.
Full Act1/Act2 cold completion and campaign integration remain owed.

### Cold upper crusher climb and fire dash (2026-09-24)

On `7a2604421` plus these route/test additions, `lrz1-sonic-tails-cold-high-climb-320`
takes the western ascent from the earlier platform prefix, rather than the
giant-ring corridor. It reaches(5557,940),111rings at9915 inputs,zero deaths,
live Sonic+Tails. Waiting for the platform's high phase reaches the first rock;
waiting for the next platform's low phase avoids the overhead rock clipping the
jump. The fire-shield dash then crosses the upper gap. Failed controller
attempts overshot ledges or went beneath a crusher; no engine change was made.

Queued Java21/native GL/absolute S3K ROM with
`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen -Dtest=TestLrzColdRouteCapture#coldTeamClimbsUpperCrushersAndCrossesWithFireDash test`
passes1 test,zero failures/errors/skips. Fourteen45-frame full-registry replay
spots:8700,8730,8775,8890,8920,9030,9080,9130,9300,9380,9410,9500,9720,9830.
InputLogAuthorTool compiles and round-trip verifies the preserved script/BK2.

Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-cold-high-climb-320/capture.mp4`
films8680–9914 after the cold prefix;9915state rows,zero deaths,9130still
inspected and full MP4 decode passes. Native route observations corroborate the
platform/ledge sequence, not frame timing or complete pixel parity.

Next route prerequisite: ordinary eastward attempts stop atX5637. ROM placement
$1F74D4 is `$19:07` LRZ door(5664,860), not an unexplained terrain wall or lava
fall. The matching `$1C:07` button is(5444,912); native rows8770–8810 detour
west toward it. This authored route bypassed it. Reach the button before
judging door behavior; full-act completion and combined integration remain open.

### Door7, bridge descent and upper-route spring (2026-09-24)

On `b9efc99ef` plus route/test additions, `lrz1-sonic-tails-cold-middle-spring-320`
reaches(7652,1201),119rings at11950inputs,zero deaths and a live sidekick.
The side button is contacted in flight; trigger7 becomes active and the door
opens. A first attempt landed on the button's top without activating it.
The route then descends through the collapsing bridge near(5792,1248),
returns east and approaches spring(6851,1457) from the right to launch onto
the upper route. Continuing east below that spring leads into a lower loop;
it is traversal coverage, not progress toward the boss. Native rows18000–18300
identify the successful spring approach after earlier unsuccessful passes.
No runtime changes or gameplay overrides are used.

Queued Java21/native GL/absolute S3K ROM:
`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen -Dtest=TestLrzColdRouteCapture#coldTeamOpensDoorSevenDropsBridgeAndTakesUpperSpring test`
passes1 test,zero failures/errors/skips. It asserts trigger7 and opening travel
at10105, completed travel with the momentary trigger released at10165, and17
full-registry45-frame replay spots:10085,10097,10105,10125,10165,10325,10400,
10650,10700,10960,11190,11220,11570,11730,11775,11820,11900.
InputLogAuthorTool compiles and round-trip verifies the script/BK2.

Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-cold-middle-spring-320/capture.mp4`
films9915–11949 after the full cold prefix;11950state rows,zero deaths,
10105/11790stills inspected and full MP4 decode passes. Native full-scene
acceptance remains open. Exploratory continuation eventually reaches the
X8600area but dies15391; neither that duration nor the lower loop is certified
completion. Continue the successful upper route and audit its next hazard.
Full Act1/Act2 cold completion and campaign integration remain owed.

### Late spike-platform ascent and damage recovery (2026-09-24)

On `c3d5a01f2` plus route/test additions, `lrz1-sonic-tails-cold-late-ascent-320`
reaches(8039,624),7rings at16275inputs with no deaths and a live sidekick.
The route loses its shield and rings during the approach and recovers loose
rings; those ordinary damage states are retained rather than reset. This is
not a no-hit route. The previous exploratory death15391 was in the
`$21:11` spike-platform area(8576,926), not the `$9C` rock crusher.

The successful ascent settles on the small rock before jumping to the platform
during its low phase, rides upward and exits west. Earlier jumps either
overshot that rock or met the platform while it was too high. A spin dash
clears the upper rock wall and three jumps ascend the western steps. Native
rows18900–19500 corroborate the route shape; controller timing, damage history
and scene pixels are not claimed to match. No runtime correction was required.
The exploratory top-left detour later became stuck beside an Iwamodoki and
took damage; the committed endpoint precedes it. Continue east from(8039,624).

Queued Java21/native GL/absolute S3K ROM:
`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen -Dtest=TestLrzColdRouteCapture#coldTeamRecoversFromDamageAndClimbsLateSpikePlatform test`
passes1 test,zero failures/errors/skips. Twenty-one45-frame full-registry
replay spots:11980,12300,13200,14100,14680,14860,14868,14930,15040,15410,
15445,15580,15620,15655,15730,15780,15850,15900,16120,16190,16220.
They include the damage transition, recovery, rock landing, platform ride and
upper steps. InputLogAuthorTool compiles and round-trip verifies script/BK2.

Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-cold-late-ascent-320/capture.mp4`
films14680–16274 after the full cold prefix;16275state rows,zero deaths,
15680/16274stills inspected and whole MP4 decode passes. This establishes
ordinary reachability and replay, not complete act/native visual acceptance.
Full Act1/Act2 completion and combined campaign integration remain owed.


### Cloud special contact and revised late ascent (2026-09-24)

On35b3e481d plus this correction, the real touch controller now sends the
Toxomister cloud's `$D8` to S3K `Touch_Special`, not BOSS. `loc_103FA` publishes
P1/P2 contact; `sub_8FF8C` consumes it on the next object pass and selects P1
for simultaneous contact. Continuous overlap is reconsidered after unrolling;
rolling and bubble shields are checked when consuming the byte. Pending contact
survives snapshot/restore. Hover/settled attachment still executes `Obj_Wait`,
so its newly written59 timer becomes58 in the same update.

`TestS3kLrzToxomisterReboundHeadless` failed before the correction: rising Y speed
-1024 became+1024 on cloud contact. It now covers rolling contact, unrolling
without leaving, zero-ring non-damaging contact, deferred attachment and timer
fallthrough through the actual controller. `TestToxomisterBadnikInstance` adds
pending-contact rewind and native P2/simultaneous P1 selection. Existing tests
now consume the contact before checking attachment instead of validating an
immediate callback mutation.

The first changed ordinary-route state is14973, delayed slowdown. Eleven earlier
routes passed unchanged; the former late-ascent input ended at6975 instead of8039.
The reauthored late-ascent fixture keeps the cold prefix and uses ordinary inputs
to recover, wait for the spike platform, clear the upper rock and climb west.
It now ends at(8042,624),8rings,17067inputs,zero deaths. Its23 full-registry
45-frame restore/replay spots are11980,12300,13200,14100,14680,14860,14868,14930,
14972,14985,15040,15330,15940,16075,16180,16220,16320,16440,16575,16650,16760,
16958,17010. These replace the prior21 late-ascent spots; other105 remain.

Queued Java21 commands used `-Dmse=off -Dopenggf.test.gl.native=true`, absolute
`-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`, and:

- `-Dtest=TestToxomisterBadnikInstance,TestS3kLrzToxomisterReboundHeadless,TestLrzColdRouteCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`:
  85tests,1failure,0errors/skips; only the superseded late-ascent endpoint failed.
- After reauthoring, `-Dtest=TestToxomisterBadnikInstance,TestS3kLrzToxomisterReboundHeadless,TestLrzColdRouteCapture#coldTeamRecoversFromDamageAndClimbsLateSpikePlatform test`:
  15passed,0failures/errors/skips; route15.72s.

The change-based plan against35b3e481d selected2909 ordinary classes plus guards.
This checkpoint uses focused validation because the runtime change is confined
to this object's existing response profile and contact latch; real controller,
P1/P2, rewind and all existing cold route consumers were exercised. Combined
campaign validation remains owed; these results are not a full-suite claim.
Other viewport/donor/character lifecycle and complete cold-act obligations remain
open. The exploratory later preboss input sequence is not certified by this run.

Replacement video:
`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-cloud-corrected-ascent-320/capture.mp4`,
frames14940–17066,17067state rows,zero deaths. Inspected platform and upper-ledge
stills16240/17066 and decoded the complete MP4. This supersedes the earlier late
ascent demonstration for current runtime behavior; it is not native trace parity.


### Cold miniboss arrival and final lower-route door (2026-09-24)

On2e56d7141 plus these inputs/tests, `lrz1-sonic-tails-cold-miniboss-arrival-320`
reaches(11338,1968),six rings after21111inputs,zero deaths, from normal native320
Sonic+Tails entry. It extends the corrected late ascent across the upper eastern
route, beneath the timed spike platform at9600, down the elevators at9056 and10720,
over the left-facing spring, through the Toxomister encounter, under the final
elevator, through the breakable rock and lower button/door11, and across the
placed `$02/$22` priority switch at(11168,1872). No position, ring, clock, emerald,
player-state or camera override is used. Ordinary damage occurs on the approach;
this is not a no-hit route. The gate has loaded the miniboss owner at the endpoint,
but the initial arena wait has not yet completed: this is arrival, not fight proof.

The failed leftward exit from the last elevator returns to closed door10. The
successful path walks onto the lower right ledge, then back left beneath the
platform. Jumping directly from the elevator's released spindash leaves Sonic
without air steering (`Sonic_ChgJumpDir` tests `Status_RollJump`); waiting for the
roll to finish is ordinary control, not an engine workaround. Both rejected
inputs and the old short/full repeated-jump boss attempts are not implementation
or parity defects. The first authored dodge/return lands one drill hit. A subsequent moving patrol
destroys the left hand and reduces the drill from six to four hits before dying
at25857; neither attempt certifies defeat. Full ordinary defeat remains open.

`TestLrzColdRouteCapture#coldTeamOpensFinalDoorAndEntersMinibossArena` asserts
door11 fully open at20995, high player priority and exactly one boss owner at21090,
final position/rings/roster, and no deaths. Its31 whole-registry45-frame replay
spots are17080,17103,17270,17425,17890,18108,18270,18450,18530,18625,18655,
18835,18925,19100,19310,19600,19735,19865,19902,20025,20160,20340,20415,
20555,20720,20745,20880,20910,20995,21030,21050. This brings the independent
cold-route total to159 spots; previous128 remain. InputLogAuthorTool verified
the authored BK2 round trip.

Queued Java21 verification used `-Dmse=off -Dopenggf.test.gl.native=true`
`-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`
`-Dtest=TestLrzColdRouteCapture#coldTeamOpensFinalDoorAndEntersMinibossArena test`:
1pass,0failures/errors/skips,20.35s test time. Production is unchanged from the
cloud fix, so this is focused route validation, not a repeated broad campaign run.

Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-cold-miniboss-arrival-320/capture.mp4`
films19860–21110 after the full cold prefix;21111state rows,zero deaths. Stills20750
and21110 were inspected and the entire MP4 decoded successfully. The arrival's
high priority is reached through the native marker, not a capture seed.

The obligation table was reconciled with its later evidence: old claims of no
route class, no crusher route rewind, no boss graph restoration, missing handoff
ramp and unimplemented Death Egg no longer describe current code. Their older
execution records remain historical; donor/roster/lifecycle/native breadth,
seamless timeline policy and whole-act completion are not silently credited.


### Miniboss hand-shot shield response (2026-09-24)

On a0bcd643c plus this correction, hand shots opt into the existing shield
response profile. `loc_78A02` sets bit3 of `$2B` (`shield_reaction`), not a
lifetime flag as the old comment claimed. `Touch_ChkHurt_Bounce_Projectile`
computes the signed radial velocity through GetArcTan/GetSineCosine and clears
collision_flags permanently. The child keeps its existing 8.8 movement and
independent Sprite_CheckDeleteTouchXY retirement. Rewind captures both the new
harmless state and reflected velocity. No shared touch algorithm changed.

The new real-controller test explicitly snapshots the shot before touch, asserts
admission, and checks outward eight-pixel motion and recreation/replay. Its first
harness version omitted that snapshot and therefore could not establish a
production regression. With the harness corrected, unchanged HEAD production
still failed with damage `$98` instead of zero; fixed production passed the
contact/recreation check (1 test, zero failures/errors/skips). The preceding
combined focused invocation passed93 of94 tests; its sole failure was the
unsnapshotted new test. Passing coverage included the complete21111-input cold
arrival, both palette handoff routes, boss graph rewind and required S3K smoke
classes. Commands used queued Java21, native GL, the absolute S3K ROM, and:

- Combined: `-Dtest=TestLrzMinibossHitPath,TestLrzMinibossInstance,TestS3kLrzBossRewindHeadless,TestLrzPostBossPaletteRouteCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestLrzColdRouteCapture#coldTeamOpensFinalDoorAndEntersMinibossArena test`.
- Matched old/fixed: `-Dtest=TestLrzMinibossHitPath#shieldContactDeflectsAHandShotAndRewindsItsHarmlessFlight test`.
- Final fixed class: `-Dtest=TestLrzMinibossHitPath test`:5passed, zero failures/errors/skips,7.011s test time.

The local change-based plan selected2909 classes plus guards. Focused validation
is used for this bounded child response: real dispatch, native radial arithmetic,
recreation and existing route consumers are exercised. Combined campaign
validation remains owed. Shield-contact moving footage and broader roster/native
acceptance remain open; this check does not certify ordinary cold boss defeat.


### Ordinary cold miniboss clear and Act2 handoff (2026-09-24)

The uninterrupted native320 Sonic+Tails route now reaches playable Act2 at
(2357,1980),zero rings after31440inputs,zero deaths. It preserves the entire
ordinary cold approach, destroys the left hand, lands six drill hits and uses
timed Insta-Shield taps during later dangerous contacts. Damage and ring loss
remain part of this route; it is not a no-hit run and uses no position, shield,
ring, emerald, clock or camera seed. The last hit is visible by29681, the world
rebases at30285, and ordinary rightward movement resumes after results.

The first successful fight exposed a real handoff exception: a stale crusher
piece survived to the world-offset scan. The missing loc_903BA tail now switches
pieces to harmless Obj_FlickerMove debris when the parent retires. All eight
subtypes retain the ROM velocity table, first-pass draw/no-move, gravity, flicker
and delayed deletion. Detached art priority survives parent-free recreation.
The new input fixture is `lrz1-sonic-tails-cold-miniboss-clear-320`; authoring
round-trip verification passed. The cold-clear regression checks stale-piece
absence, fatal hit, world rebase, control release, Death Egg owner and26 new
whole-registry45-frame replay spots:
21120,21300,21570,21950,21970,22820,23700,24500,25400,25848,26260,26676,
27100,27535,27960,28435,28800,29670,29760,30220,30290,30740,30880,30940,
31100,31360. Replay does not straddle the reload; seamless timeline policy remains
an explicit separate obligation.

The first combined run on19d490a00 plus this patch used queued Java21/native GL,
`-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`, and
`-Dtest=TestLrzRockCrusher,TestLrzColdRouteCapture,TestLrzPostBossPaletteRouteCapture test`:
30tests,1failure,1error,0skips. The error was missing post-recreation service
injection in the new isolated harness; its corrected rerun passed. All previous
13 cold routes and both positioned handoffs passed. The new cold-clear replay
exposed an obsolete cameraGate DEFERRED policy at21120: restoring its empty gate
skipped the wait and submitted art early (6 versus10 timing jobs). The policy is
removed and the helper now retains a final identity for supported state capture.
Final focused verification used the same launch environment and
`-Dtest=TestLrzColdRouteCapture#coldTeamDefeatsMinibossAndReachesPlayableActTwo,TestS3kLrzBossRewindHeadless,TestLrzMinibossInstance,TestLrzMinibossHitPath test`:
33passed,0failures/errors/skips; the cold route and26 replay spots took29.15s.
The final crusher class also passed14 tests,0failures/errors/skips. The earlier
policy-only attempt failed capture on the replaceable helper's legacy codec
wrapper; making the stateful holder final uses its supported compact path.
The change-based plan selects all2909 ordinary classes plus guards because the
policy catalogue changed. This checkpoint records focused evidence only; the
combined campaign validation remains owed. The separate final guard JVM used
`-Pguards -Dtest=TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard test`:
2passed,0failures/errors/skips.

Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-cold-miniboss-clear-320/capture.mp4`
films29580–31439 after the full cold prefix;31440state rows,zero deaths. Inspected
stills29690,30300,31020,31439 and decoded the complete MP4. The native emulator
also shows the apparently sparse floor after the palette change; see the audit's
read-only416490–417600 observation. This is not a strict native pixel/trajectory
match. Other rosters, widescreen cold traversal, donor/lifecycle breadth and
combined campaign delivery remain open.


### Native drill replacement and detached debris (2026-09-24)

The continued Act2 cold route still listed a persistent, invisible drill after
the title. `loc_787E0` instead jumps into `Obj_EndSignControl` in the existing
SST slot; `Obj_EndSignControlDoStart` finally deletes that slot. On ac3464114 plus
this fix the drill transfers its slot to the shared controller and installs its
native flag/timer in the same dispatch, preserving the existing cold input timing.
The eleven `loc_78A70` pieces are standalone objects: their arcs no longer depend
on the deleted parent's update clock or rewind identity. Their `$80` priority
word now selects bucket1, rather than silently clamping the raw value to7.
Initialization draws without moving; the first movement pass is invisible, and
Go_Delete_Sprite_3 delays slot deletion until the next dispatch.

Both new regressions fail on ac3464114: the drill was not destroyed, and debris
priority was7 instead of1. The first implementation attempt called the shared
initializer before ObjectManager bound its services, causing6 errors; registering
first fixes that ordering. The direct fixture now supplies GameStateManager
because the native initialization happens immediately. The final queued Java21,
native GL, `-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen` checks are:

- `-Dtest=TestLrzMinibossInstance,TestS3kLrzBossRewindHeadless,TestLrzColdRouteCapture#coldTeamDefeatsMinibossAndReachesPlayableActTwo,TestLrzPostBossPaletteRouteCapture,TestS3kBossDefeatSignpostFlow test`:36passed, zero failures/errors/skips.
- `-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`:59passed, zero failures/errors/skips.
- Separate JVM, `-Pguards -Dtest=TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard,TestObjectPriorityBucketGuard test`:3passed, zero failures/errors/skips.

The cold endpoint remains2357,1980 with zero deaths and now asserts no drill
owner survives; its26 full-registry replay spots and both320/800 positioned
handoffs pass. The debris graph test forces removal/recreation after the parent
has retired. The inspected change-based plan selects2567 ordinary classes plus
guards; combined campaign delivery validation remains open. These are focused
checks, not a full-suite result.

Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-miniboss-slot-release-320/capture.mp4`
films29580–31439, with31440 state rows and1860 rendered frames. Inspected29760 and
29800 stills and decoded the entire video. This is a refreshed engine demo, not
strict native whole-scene pixel acceptance. Other route products and lifecycle
breadth remain open.


### Retiring arm priority follow-up (2026-09-24)

The same raw-word mistake also existed in the inherited field write at
`LrzMinibossRingChildBase.sub78B46`: `$80` became bucket7. It now uses
`RenderPriority.fromS3kWord`, selecting bucket1 only for the retiring ring;
the other ring stays in bucket0. The hand-peel regression fails on16b34ba8b
(expected1, actual7), then passes with the fix. Queued Java21/native GL,
`-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`,
`-Dtest=TestLrzMinibossInstance,TestS3kLrzBossRewindHeadless,TestLrzColdRouteCapture#coldTeamDefeatsMinibossAndReachesPlayableActTwo test`
passes30 tests, zero failures/errors/skips, including the unchanged cold endpoint
and26 full-registry replay spots. This is focused follow-up evidence.

A read-only engine probe locates the first `$38` bit6 transition at input21593.
Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-hand-peel-priority-320/capture.mp4`
films21540–21719 (180frames after21720cold inputs); the21594 still shows Sonic
behind the retiring arm. The guessed21890 window was too late and is discarded.
The final drill breakup is also refreshed in
`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-miniboss-retirement-priority-320/capture.mp4`
(29580–29939,360frames). Both videos fully decode, with zero deaths; inspected
21594 and29700 stills. These clips exercise the corrected overlap/sequence but
are not strict native whole-scene pixel matching.
