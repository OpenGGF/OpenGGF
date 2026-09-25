# S3K The Doomsday Zone coverage matrix

Game / canonical zone / act: S3K `S3K_DOOMSDAY`, engine zone `$0C` act index 0,
ROM `Current_zone_and_act = $0C00`, SKL object set.
Character route: Sonic (Player 2 cleared by `loc_81554`): fall-in → transformation (Super, Hyper
with seven Super Emeralds) → free flight through the autoscrolling asteroid field → end boss phase
1 (turrets, launchers, missiles into the body) → phase 2 chase with two `$7400 → $5400` wraps →
defeat → exit fade → `StartNewLevel $D01`. Owning plan:
[DDZ bring-up](../../plans/2026-09-17-ddz-bring-up.md).
Status: fresh Super and positioned DEZ2-incoming Hyper routes reach both boss
phases and the accepted ending request at320/800. A new native320 Sonic+Tails
route starts at cold DEZ1 and reaches the same request after64648 controller
frames, with emeralds declared only at boot; its replay verification is recorded
in the dated follow-up below. Native scene matching, remaining roster/donor breadth remain open. Death-reload and seeded ending-load
history isolation now pass at320/800. Nothing below certifies the zone.

Incoming: DEZ final boss → `$C00` verified at320/800, including a positioned
DEZ2-boss start with no reseeding across either load. Full cold DEZ1→DEZ2→final
DEZ→DDZ completion now has a preserved native320 team movie. Level select is
also supported. Outgoing: `$D01` ending (the request is asserted; ending
presentation remains outside this route's accepted scope).

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`, same roster
as HPZ): widths 320/400/512/640/800; supported character/donor pairs off×{Sonic,Tails,Knuckles},
S1×Sonic, S2×{Sonic,Tails}; teams: solo, Sonic+Tails, S1 Sonic+Sonic duplicate, S2 Sonic+Tails,
Sonic+Tails+Knuckles at 800 (every follower suppressed in the zone). The controller has no character
branch; only Sonic is the native route.

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| ENTRY: controller, Player 2 clear, fall-in, transformation timing, release | `loc_81554`/`loc_8160A`/`loc_8167C`; `SuperHyper_PalCycle`; native pass-1 frames 1/24/49/50/51 | native, Sonic + Super Emeralds | `TestS3kDdzFlightControllerHeadless` | implemented; native behaviour matched | pass | — |
| ENTRY breadth: fall-in, transformation, release, 400 flight frames through the asteroid field | same | 34 width × character/donor/team rows | `TestS3kDdzCompatibilityMatrix` | implemented | see execution evidence | Hyper upgrade asserted only on the Sonic route |
| ROUTE (Sonic cold, seeded clocks): entry → both phases → three wraps → defeat → `$D01` request | Complete-run BK2 input from movie frame 514214 (`zone0c` row 0); physics rows compared every frame; declared inherited `V_int_run_count` 512489 and camera fraction `$2700` | native 320, Sonic (+Tails configured, suppressed) | `TestS3kDdzColdRoutes#seededNativeRouteMatchesPositionsCameraAndRingsThroughTheExitRequest` | implemented; native behaviour matched (player x/y, camera x/y and rings identical for all 10058 gameplay rows; boss exit routines on the native frames) | see execution evidence | Without the seeds the route diverges at 4178 (turret aim phase) and dies in phase 1 |
| ROUTE (Sonic cold, seeded clocks, Super): entry through `$D01` | Same controller movie with declared Chaos-only progression; no Hyper-trace parity claim | 320 | `TestS3kDdzColdRoutes#seededSuperRouteCompletesWithItsFixedStarsAndReplaysTheFight` | implemented | passes, with fight/wrap/fade restore checks | 800px reuse of the native movie dies6340; independent fresh routes below now complete both widths |
| ROUTE / REWIND (fresh Super): entry → both phases → wraps → `$D01` | Authored controller-only BK2; seven Chaos Emeralds, no inherited clock/fraction/position seed; restore and 45-frame replay at first body damage, first chase wrap and exit | Sonic solo, donor off, actual320/800 | `TestS3kDdzAuthoredRoutes` | implemented | 2 passed, no skips, 2026-09-23 19:08 BST | Completion evidence, not native parity or full incoming DEZ continuity |
| REWIND: phase-1 fight, first wrap, exit fade | Restore equals capture (object graph summary); 45 divergent frames discarded; recorded route continues with exact native parity to the exit | native 320 | `TestS3kDdzColdRoutes#rewindAtBossWrapAndExitRestoresTheNativeRoute` | implemented | see execution evidence | Hurt spin, asteroid split and final-hit spots are covered only inside these windows |
| REWIND: mid-transformation, mid-flight | Registry restore equals capture; forward replay equals original | 34 rows × 2 spots | `TestS3kDdzCompatibilityMatrix` | implemented | see execution evidence | — |
| PRESENT: background bands, FG-plane boss body, explosions, wrap | `sub_596EA` six speeds from `Events_bg+6`, `DDZ_BGDeformArray`; `DDZ_ScreenEvent` stages 0/4/8/`$C`; `PLC_BossExplosion` | native 320 every frame at entry, boss arrival, first wrap, exit | side-by-side clips `30-33-ddz-native-vs-engine-*.mp4` (engine capture exact to native positions) | implemented | historical whole-scene inspection left Hyper sparkle/HUD entry and Master Emerald flicker phase / white-fade tint; Hyper queue phase and HUD entry now have matching native follow-ups below | Pixel comparison not automated |
| OBJECT: asteroids, missiles, boss graph, slot/load order | `Obj_DDZAsteroid`, `Obj_DDZMissile`, `Obj_DDZEndBoss`; native slot histories `probe-slots0/1` | native route | seeded route test (slot order drives hit order) | implemented; native behaviour matched through the route | pass | Super branch now has `loc_8242A/82452` fixed-slot stars and six ROM frames; Hyper owner now waits for native art-queue completion;506 entry child rows match (dated follow-up below) |
| LIFE: ring-out death and restart | Ring drain ends the form; `loc_8179E` fall below `Camera_Y + $F0`; `Kill_Character`; death countdown reload with one fresh controller | 320, 800 | `TestS3kDdzLifecycleProduction` | implemented | pass, 2 | Live history isolation now asserted at both widths (follow-up below); donor/team rows only through the breadth matrix |
| LOAD: `$D01` handover freeze | `StartNewLevel` leaves the level loop; native fade frozen | GameLoop and recording driver | `TestS3kDdzColdRoutes` | recording driver now honors the shared inactive-transition flag | focused route check passes | Exit fade freezes source gameplay while palette work continues |
| LOAD / HISTORY: ending `$D01` | `loc_81CA4` progression-save request, real load and outgoing timeline reset | 320, 800; declared post-white-fade boss state | `TestS3kDdzLifecycleProduction#endingRequestSavesProgressAndStartsAnIsolatedTimeline` | implemented | 2 passed, no skips (2026-09-25 follow-up) | Seeded boundary evidence; no on-disk save or ending-scene claim |
| ORACLE: strict segment replay | `TestS3kSonicTailsZone0cSegmentTraceReplay` | — | `-Ptrace-segments` | — | red: bootstrap camera Y and missing clock seeds (plan evidence) | Replay harness bootstrap |
| LOAD: DEZ → `$C00` incoming | `loc_803D6` and source camera-policy retirement | solo320/800 direct final-DEZ routes | `TestDezFinalScreenEntry`, capture115 | connected | corrected load/initial flight;30-case destination selection passes without skips (2026-09-23) | Full cold native320 team incoming continuity is now recorded below; remaining roster/width breadth stays open |

## Execution evidence

Worktree `.worktrees/ai-ddz-bring-up`, ROMs by absolute path, `maven_queue.py -Dmse=off`.

`-Dtest=TestS3kDdzColdRoutes,TestS3kDdzCompatibilityMatrix,TestS3kDdzFlightControllerHeadless` with S1/S2/S3K
ROM paths: 37 tests, 0 failures, 0 errors, 0 skips (working tree after `2e0067d9a`). The route comparison is
live: before the fixes it reported the first mismatch (8249 exact position, then 10027 after the exit-fade
rewind defect), and the matrix caught four restore/breadth defects (reinstalled runtime state and duplicate
controller on restore, turret recreate probes, Super physics lost when restoring mid-transformation, `$280`
cull churn at 800 px). Shared checks in the same tree (`TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
`TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`, `TestEveryObjectRewindRoundTrip`,
`TestRewindHarnessCoverageRatchet`, `TestRewindRoundTripHarnessConstruction`, `*SuperState*`,
`TestGameplayCaptureSmoke`, `TestS3kHpzColdRoutes`): 1220 tests, 0 failures, 0 skips.

Donor rows (S1/S2 Sonic, S2 Tails) have no S3K powered form. As an engine extension the controller releases them on
the Sonic palette-fade schedule and leaves `$38` bit 7 clear, so they fly un-powered (no asteroid shattering,
ordinary hurt). Wide viewports widen the DDZ `Sprite_OnScreen_Test` window with the viewport
(`coarseXCullRange`), as FBZ does.

Start locations (`loc_1BE5E`): every breadth row asserts frame-1 y from the ROM table — `$C0` for Sonic and Tails
(`Sonic_Start_Locations` `$0,$100`), `$20` for Knuckles (`Knux_Start_Locations` `$140,$20`). A capture-tool donor
session started S2 Tails at (69,656); the production fixture does not, so the donor clip was discarded and the
pitfall recorded in the `gameplay-capture` skill.

Delivery validation (`run_categories.py --base 4569e5406 --run` on merge `9fd6f65dd`, full selection 2695 classes):
ordinary 21942 tests, 4 failures, 27 skips (all opt-in capture/measurement/GL-surface properties); guards 669 tests,
5 failures. All nine failures were inventories and ratchets that the DDZ additions had to update — rewind tail
inventory (+23 classes, all passing the isolated sweep), DDZ PLC plan size, stock zone-bound object inventory
(S3KL/SKL `$B6-$B8` pointer-table collisions; ICZ's ice factories now use the same S3KL set binding as their
neighbours), object profile ids for zone 12, strict `GameServices` null checks, rewind architecture baselines for
the `AbstractDdzObjectInstance` parent link, and 14 final-scalar coverage gaps (fields made non-final). Rerun:
the four ordinary classes with the DDZ suites and `TestEveryObjectRewindRoundTrip` (1243 tests) and the four guard
classes under `-Pguards` (8 tests) pass. Four ICZ trace replays swept in by a `*Icz*` filter are red identically with
and without the registry change (inherited).

### 2026-09-23 ROM palette follow-up

Removed embedded Master Emerald and boss-flash color assets. Emerald scripts
`$8141E` now read ROM pointers, destinations, words and delays, with the shared
cursors in captured `DdzZoneRuntimeState`; flash destinations/rows use
`$82D86/$82D9E`. Two focused `TestDdzRomPalettes` cases pass without skips for
both flash rows and the actual emerald owner, including restoration/reset.
The corresponding DEZ helper tests cover both scripts' 94-tick repetition and
palette-disable freezing. Existing DDZ compatibility (34) and lifecycle (2)
checks passed without skips. These are focused checks, not a rerun of the full
DDZ route or proof of native pixels. Incoming DEZ and other open rows remain.

The `$81D44 -> $81D4A` exit edge is now repaired: the first following update
applies wrap, the P1 clamp and camera delta in the publication dispatch. A new
regression reproduced the old delay (X 9500 instead of 9527), then passed for
both zero and `$2000` wrap. The emerald drops its unused parent identity, and
capture/restore plus forward replay succeeds after ship/root retirement.
Queued `TestDdzRomPalettes,TestS3kDdzLifecycleProduction`: five passed, zero
skips. This remains focused component/lifecycle evidence, not an updated movie
or end-to-end route/native-pixel comparison.


Incoming final-DEZ check (2026-09-23, after `37cba17bc`): both direct final-boss
controller routes reach DDZ, but the 800px load initially inherited the previous
arena's -240px camera projection. The native DDZ autoscroll masks it to $7F11.
A short load-boundary regression isolates that stale source policy before DDZ
runs; the source framing is being restricted to current $1700. This is not yet
wide DDZ route certification. See the DEZ plan for corrected incoming evidence.

The destination-framing selection subsequently passed30 cases without skips
(`TestDezFinalScreenEntry`, `TestNativeArenaCameraFraming`, `TestS3kDdzColdRoutes`).
Capture115 verifies the corrected actual800px final-DEZ→DDZ load and initial
flight, with no death and exact replay of the authored state. Full widescreen
DDZ completion and other incoming-route breadth remain open.


2026-09-23 Super-star follow-up: the six-frame `loc_8242A/82452` effect now
uses the reserved Super_stars slot and ROM art/mappings, with separate bit15
hardware priority and queue$80. A native entry probe with declared zero Super
Emeralds confirms first initialization at50, anchor at51, two-pass animation
and reanchor at63. An800px ordinary-entry capture has300no-death rows, no
follower and a fully decoded video; its frame57 star-region pixels match all
2401native pixels after 3-bit RGB quantization. This is a bounded effect match,
not whole-scene certification. The Hyper phase/HUD items stay open.

Art checks corrected a DMA-word/byte mistake ($1A0words=$340bytes=26tiles).
Corrected mapping/length checks pass. Five actual viewport presets now pass
release-boundary restore/replay; together with Hyper selection, native cadence
and wrap/reanchor components this is8passing cases, zero skips. Every-object
rewind passes1313cases including the new effect. Initial wide fixture rows
were still320px until SCREEN_WIDTH_PIXELS and session reset were explicit;
all width claims now assert the live camera width. Complete Super routes are
being checked separately from Hyper-native timing assertions.


Full Super-route result: the seeded320px controller route reaches the exit with
fight/wrap/fade restore checks. Actual800px reuse of those same native-width
inputs dies at6340; it is not a completed wide route. An intermediate run first
hit a native-width-only orphan-burst assertion at5495; gating that native oracle
to the matching Hyper reference allowed the independent wide run to expose its
real death frontier. The committed completion test covers320 only. Wide route
authoring/diagnosis stays open; no gameplay was tuned to make those inputs pass.
The final five-preset star selection passes8cases, zero skips.


The isolated800px controller probe confirms ring exhaustion: first boss fight
routine4 begins3957 with39rings versus native-width3806 with77rings; the wide
route lands one body hit at4296, drains its last ring at6307 and dies6340.
The320px route lands seven body hits3919..5133 and completes. These are observed
route differences, not proof of a runtime bug: viewport-dependent loading and
object lifetime need matched inspection before revising gameplay. Adaptive
wide input authoring and the actual final-DEZ incoming route remain open.


### 2026-09-23 — fresh DDZ controller completion

At `b6c1147a2` plus campaign edits, independently authored Super Sonic inputs
now complete fresh level-select entry at both actual320/800 widths. The only
declared gameplay setup is seven Chaos Emeralds; there is no inherited V-int,
camera fraction, position, health or ring seed. The scripts and reproducible BK2s
are `routes/s3k/ddz-super-fresh-{320,800}`. Native320 reaches the ending request
after10396 capture passes with12rings; wide800 after9923 with20rings. Three
rings during the wide exit explain the earlier17ring observation at fade entry.
No production gameplay was changed to make these inputs complete.

`TestS3kDdzAuthoredRoutes` checks no death, the `$D01` request, and restore plus
45-input forward replay at first body damage, first chase wrap and exit. It
compares player/camera/object summaries, palette words and all DDZ runtime bytes.
Queued Java21 Maven `-Dmse=off -Dtest=TestS3kDdzAuthoredRoutes test`, with the
absolute S3K ROM, passes2cases with zero failures/errors/skips at19:08 BST. The
first diagnostic failure was a null-spawn fixed-object summary; the next was
the stale17ring endpoint expectation. Neither required a gameplay change.

This closes fresh native/wide controller completion, independently of the
seeded Hyper movie parity result. It does not close strict trace bootstrap,
full incoming DEZ2 continuity, Hyper/HUD presentation, or load-history isolation.
The wide `campaign-20260923-fresh-completion-800` recording has9923 state rows,
zero deaths/followers and1673 images (8250..9922); full video decode passes.
Visual inspection exposed intermittent background wrap seams; diagnosis and
corrected presentation evidence are recorded separately.


Corrected presentation verification: queued Java21 Maven with the absolute S3K
ROM, `DISPLAY=:0`, `-Dopenggf.test.gl.native=true` and
`-Dtest=TestDdzBackgroundWrapCapture,TestBackgroundScrollWrapPixels,TestShaderPixelCentreSampling`
passes3cases with zero failures/errors/skips at19:18 BST. The background tests
include all normalized scroll words -32767..32767 and the actual route render;
pixel-centre coverage includes native, integer and fractional scaling. These
are focused checks; the campaign's combined category/guard run is still owed.

`campaign-20260923-fresh-completion-800-wrap-fixed` supersedes the earlier
wide fresh-route movie. All9923 CSV rows are byte-identical to the original,
including zero deaths/followers and20 final rings. The1673-frame movie fully
decodes; stills8400/9000/9681/9922 were inspected. Controller source and provenance
are alongside the external video. No native whole-scene parity claim is made.


### Incoming DEZ2 encounter chain (2026-09-23)

`TestDezIncomingFinalRouteCapture` native320 passes1test with zero failures,
errors or skips at22:29:25 BST on b6c1147a2 plus campaign edits. Positioned
DEZ2 ($34B0,$300), solo Sonic, donor off, boot-only200rings/sevenSuperEmeralds
continues through actual final-arena and DDZ loads without reseeding. The
21102-frame BK2 independently replays without death, matching all20862 author
rows before its240-input DDZ tail. Eight whole-registry restore/45-input replay
spots cover hands, core, escape ship and live destination flight. Captures116/117
show the two handoffs; see the [campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md).
This closes that native positioned continuity row, not cold DEZ2 traversal,
complete incoming DDZ combat, native parity or roster/donor/lifecycle breadth.


Two-width incoming follow-up: queued `-Dtest=TestDezIncomingFinalRouteCapture`
passes2cases, zero failures/errors/skips,22:34:53 BST (52.002s Maven). Both320
and800 verify every registry key at all eight restore/45-input replay spots.
The wide21109-input movie independently matches all20869 author rows, no deaths
or follower; capture118 fully decodes and stills20582/21050 were inspected.
The remaining240inputs show actual DDZ flight. No gameplay change was needed.
Complete incoming DDZ combat and cold DEZ2 traversal remain separate open rows.


Complete incoming DDZ verification now passes at320/800: queued Java21 with
absolute S3K ROM, `-Dtest=TestDezIncomingFinalRouteCapture test`,2cases, zero
failures/errors/skips, BUILD SUCCESS22:43:32 BST (62s Maven). The30918/31531
controller inputs run from the positioned DEZ2 boss through the final arena,
both DDZ phases and actual $D01 request, without deaths or reseeds. All11
required spots per width compare every registered key on restore and45-input
replay, including DDZ body damage, chase wrap and defeat. The11-family child
spawn regression plus59 mandatory S3K checks separately pass70cases, no skips
(22:42:16 BST). Earlier freshSuper route cases also pass; these are focused
checks, not the combined campaign suite. Wide incoming completion video is
`$VIDEO_ROOT/ddz-bring-up/campaign-20260923-incoming-dez2-completion-800/capture.mp4`:
31531state rows, no deaths/followers,7finalrings,1531filmed frames, full decode
passed;30369/31291/31530 inspected (last is white exit fade). It predates the
recreation-only fix, which does not run during normal forward playback.
Cold DEZ2 traversal, roster/donor breadth, history isolation and native whole-scene
matching remain open. Ending/credits remain excluded.


### Hyper-star sprite-list correction (2026-09-25)

On `edc0ec81b`, the Hyper-star owner incorrectly inherited the player's display
list. `Obj_HyperSonic_Stars_Init` instead assigns the fixed word `$80` (bucket1);
`loc_19458` copies only the player's art-word high-priority bit. The correction
keeps those independent, so overlapping star pixels sort ahead of ordinary
player bucket2 while terrain occlusion continues to follow the player.

`TestHyperSonicStarsObjectInstance#starsKeepNativeDisplayListWhileFollowingThePlayersPlanePriority`
reproduces the old defect (8tests,1failure,0errors/skips) and checks four player
buckets with both plane-priority states. Existing orbit/spark/recreation tests
exercise the unchanged effect lifecycle. This does not close the separate
native Hyper-star animation-phase/size or HUD ring-refresh discrepancies.

The change-based plan was inspected. Focused validation is proportionate for
this single object's constant display-list assignment: no renderer algorithm,
state schema, physics, or art data changes. Combined campaign validation remains
required before integration.

Focused verification on `edc0ec81b` plus this correction, Java21 and the absolute
locked-on ROM path:

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  -Dtest=TestHyperSonicStarsObjectInstance,TestDdzSuperStars,TestS3kDdzColdRoutes,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test
```

Result: 78tests,0failures/errors/skips, including the seeded DDZ route and its
rewind cases. This is focused validation, not a whole-suite or visual-parity
claim. The inspected category plan selected2442classes; the production change
is confined to the single fixed priority return described above.

The separate fresh-JVM check
`python3 tools/testing/maven_queue.py -Dmse=off -Pguards -Dtest=TestObjectPriorityBucketGuard test`
also passes (1test,0failures/errors/skips).


### Recording-driver exit freeze (2026-09-25)

The existing native Hyper completion route now continues into the exit fade.
On `ce4c23f62`, its new assertion fails at the first checked fade row: level
frame10057 becomes10059. `DdzEndBossObjectInstance` already requests `$D01` with
`deactivateLevelNow=true`, but `RecordingFrameDriver` ignored that semantic
flag. The live `GameLoop` already freezes on it.

The driver now skips source gameplay while `isLevelInactiveForTransition()` is
true, leaving the outer lifecycle responsible for fade/VBlank updates. This is
shared transition behavior, with no DDZ-specific gate and no new captured state.
The ROM reference is `loc_81CA4` → `StartNewLevel` → `Pal_FadeToBlack`, which
leaves the level loop. Active palette effects without the inactive-level flag
continue normally. The route regression checks player/camera/object positions,
rings and level frame remain fixed while the fade advances for20color steps.
Ending-scene presentation is outside this check.

The change-based plan selects the full2915-class ordinary suite and guards
because the recording driver is shared. Focused iteration below is not final
certification; the combined campaign run must include this change.

Focused iteration on `ce4c23f62` plus this change, Java21/absolute locked-on ROM:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  -Dtest=TestS3kDdzColdRoutes,TestRecordingFrameDriverInputOnly,TestRecordingFrameDriverHardwareTiming,TestRecordingFrameDriverDynamicArt,TestPlcFrameLifecycleCoordinator,TestPlcObjectOwnedFadeLifecycle,TestGameLoop test
python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  -Dtest=TestS3kDdzColdRoutes,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test
```

The first selection ran136tests:135passed and the new route assertion failed
because it assumed a zero starting fade counter (expected1/actual2). The test
now checks increments relative to the fade's actual starting count; production
code was unchanged by that correction. The second selection passes62tests with
0failures/errors/skips, including all three DDZ routes. The other133tests from
the first selection were not repeated on unchanged code. These are focused
checks; full shared-driver/campaign validation remains pending.


### Hyper-star native queue phase (2026-09-25)

At `60a28830f`, native entry save514214 reveals why the stars looked larger:
the engine's frame/angle arithmetic was correct but started two gameplay ticks
early. `Obj_HyperSonic_Stars` queues the ROM archive; Init then polls global
`Kos_modules_left` before decrementing its 1/2/3/4 delays. A decoded standalone
renderer was incorrectly treated as completion. The object now submits to the
existing session module scheduler, retains the job ordinal across rewind,
claims completion and waits on the global init gate. Main.child deliberately
does not poll again. A new regression reproduces the old later-upload freeze
(expected angle224, actual240) before that distinction is fixed. A pending
submission retires before an inactive owner expires; rendering stops immediately.

The native exporter changes no gameplay RAM. The engine probe uses ordinary
DDZ boot, native Sonic solo,320px, seven Super Emeralds and neutral inputs.
After accounting for the capture boot's initial non-gameplay step, all506
initialized child rows agree in frame, timer, angle and both accumulators.
Native child starts are52/53/54/55. No native row supplies engine gameplay state.
The external `ddz-bring-up/campaign-20260925-hyper-phase/` directory contains
source-hashed native results, before/after engine observations and a3-second,
60fps side-by-side `comparison.mp4`. The native save retains score/camera fraction;
fresh engine setup does not. Inspected frames and full decode support this
sparkle comparison, not whole-scene pixel or trajectory certification.

Queued Java21/S3K ROM validation:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  '-Dtest=TestHyperSonicStarsObjectInstance,TestDdzSuperStars,TestKosinskiModuleQueue,TestKosinskiModuleQueueGameplayIntegration,TestSonic3kPlcArtRegistry#s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits,TestPatternSpriteRendererCorruptionGuard' test
python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  -Dtest=TestS3kDdzColdRoutes,TestS3kDdzAuthoredRoutes,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test
python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  -Dtest=TestRewindFieldDispositionGuard,TestHelperStateRewindCoverageGuard test
```

Results:28 focused tests,64 route/stability tests and2 guards pass with zero
failures/errors/skips. The inspected category plan selects2442 ordinary classes
plus guards; combined campaign validation is still owed. HUD redraw timing,
strict trace bootstrap and remaining matrix obligations stay open.

The explicit pending-load replay test was rerun after its final expansion:
`-Dtest=TestDdzSuperStars#hyperInitWaitsForNativeArtQueueBeforeStartingEachChild`
passes1 test, zero skips. Restoring the pass50 composite snapshot reproduces
all four child phases through65 without resubmitting the art job.


### Native ring redraw admission (2026-09-25)

At `30bfafb64`, gameplay Ring_count matches the native route but the HUD reads
that live value every publication. `loc_8160A` adds50 without setting
`Update_HUD_ring_count`; native digits remain zero until a later request.
The shared `LevelGamestate` now retains display value and dirty state separately.
Ordinary mutations request redraw, a semantic silent write preserves an existing
request, and the existing counter-publication profile consumes it at VBlank.
Unlatched HUD paths keep their prior live-counter behavior. Labels still inspect
live gameplay rings as before. The internal display adapter restores after the
level gameplay state; the Mod API LevelState and LevelSnapshot contracts are
unchanged. The DDZ owner uses a ring-free scripted transformation entry and its
native direct add, avoiding debug/GiveRing life-threshold effects.

A native BizHawk2.11 observation resumes the unchanged514214 movie state,
with no RAM writes. `Ring_count`/redraw changes are0/1 at0,0/0 at1,50/0 at24,
49/1 at85 and49/0 at86. Actual screenshots retain zero through85 and show49
at86. The ordinary engine capture agrees after its documented one-step boot
offset. The external `ddz-bring-up/campaign-20260925-hud-refresh/` directory
contains hashes, observations, images and a121-frame60fps `comparison.mp4`.
Full decode and selected before/after frames were inspected. Score and inherited
camera-fraction differences remain declared; this is HUD evidence, not whole-scene
pixel certification.

The real DDZ loop test pins those exact transitions and restores the pass25
composite snapshot before replaying them. Shared tests cover retained values,
pre-existing dirty requests, ring loss and immediate-display fallback. The HUD
renderer test checks the actual zero/49 digit draws. An initial implementation
edit targeted the debug wrapper instead of the scripted call; the integration
test caught the remaining50 display and the edit was corrected before delivery.
The new renderer assertion initially assumed a right-edge coordinate; observed
commands showed the helper uses a start coordinate plus digit padding. Only
the test coordinates were corrected.

Queued Java21/S3K-ROM focused selection:
`TestLevelRingDisplay,TestS3kDdzFlightControllerHeadless,TestHudRenderManager,TestHudRenderManagerWidescreen,TestLevelRewindSnapshotAdapter,TestDdzSuperStars`
ran50 tests:49 passed and the new renderer coordinate assertion failed.
The route expansion
`TestHudRenderManager,TestLevelRingDisplay,TestS3kDdzFlightControllerHeadless,TestS3kDdzColdRoutes,TestS3kDdzAuthoredRoutes,TestSonic3kSuperStateRewind,TestSuperStateController,TestKis2SuperStateController,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
ran128:127 passed with only that same renderer assertion failing, zero skips.
Separate `-Pguards -Dtest=TestRewindFieldDispositionGuard,TestHelperStateRewindCoverageGuard`
passes2 tests, zero skips. Final renderer correction is checked separately below.
The change-based plan selects all2916 ordinary classes plus guards; focused
iteration does not replace the pending combined delivery gate.

Final `maven_queue.py -Dmse=off -Dtest=TestHudRenderManager test` passes all28
renderer tests, zero failures/errors/skips, after correcting the assertion
coordinates. Production code is unchanged from the127 passing route/state cases
and the two passing guards; those completed checks were not repeated.


### 2026-09-25 — full cold DEZ1 through incoming DDZ completion

Based on `c3e0f520f`, `dez-sonic-tails-cold-ddz-320.{script,bk2}` preserves64648
ordinary controller frames: cold DEZ1, both main acts, final hands/core/ship,
DDZ approach, eight missile hits, eight chase hits and the actual `$D01` request.
Setup is native320 Sonic+Tails, donor off, seven Super Emeralds declared once at
DEZ1 boot. There are no position, rings, health, clock or camera-fraction overrides,
no state hydration and no reseeding at any load. Tails is registered during DEZ
and suppressed by the existing DDZ module policy after arrival.

Milestones: final DEZ loads at40315; DDZ at54691; missile-body destruction at
62358, chase begins63162, final chase hit64404 with12 rings, ending request64647.
A fresh fixed-input capture matches all64648 authored rows for player position,
velocity, ground speed, mapping, rings, death and camera position; zero deaths.
The ending freeze preserves18 rings after later exit pickups. This is cold
engine completion evidence, not native full-route parity.

Rejected inputs: appending the fresh Super flight movie exhausts rings at64520;
neutral hovering allows repeated knockback away from the first boss. Sustained
forward flight below the body lands all eight missile hits. For the chase,
steering toward the boss object's origin misses: `loc_82DCE` uses the positive
`(+32,+32)`64×64 rectangle. A temporary read-only controller author targeted
its centre and tapped dash, then recorded the resulting ordinary input. Only
the fixed BK2 is used by the preserved test and final video; no adaptive driver
or new gameplay logic was added to the engine.

Verification:3 tests pass, zero failures/errors/skips,33 full-registry replay
spots. The first run passed completion and10 cold replay spots but failed its
checkpoint inventory: the persistent defeat routine shadowed the one-frame wrap
edge, because this faster chase finishes before its first wrap. Prioritizing the
wrap edge fixes the test selection; the corrected three-case rerun passes.
Command (Java21, `DISPLAY=:0`, absolute ROM):
`python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.test.gl.native=true
-Ds3k.rom.path=$S3K_ROM '-Dtest=TestDezIncomingFinalRouteCapture#coldEmeraldTeamClearsBothActsFinalFightAndDoomsday+incomingFinalFightRestoresAndReplaysEveryPhase' test`.
The helper is shared with the positioned320/800 routes, which are included in
that selection. It asserts the actual initial roster, no deaths,11 whole-registry
restore/45-input replay spots and the zone12→zone13/act1 ending request.

Video: `$VIDEO_ROOT/ddz-bring-up/campaign-20260925-full-cold-completion-320/capture.mp4`.
The preceding62100 inputs replay from cold entry;2548 recorded frames at60fps
show the last missile hits, phase transition, complete chase and final white fade.
Complete video decode passes; phase-change/final-hit/end frames are inspected.
This closes this native320 incoming route, not donor/wider incoming products,
load-history isolation, full-scene native pixels or strict trace bootstrap.
Combined campaign validation and integration remain outstanding.

### 2026-09-25 live death-reload history

`TestS3kDdzLifecycleProduction` now enables live rewind and drives transformation,
ring-out, falling death and reload through `GameLoop`. It asserts that flight
built more than ten history frames before the ring-out stimulus, then checks
that the actual object-manager replacement resets the outgoing timeline. Both
320 and800 cases retain the existing fresh runtime/controller assertions.
The initial test attempt used the old fixture stepping and did not enable live
rewind; it correctly failed the history prerequisite. The corrected test passes
two cases with zero failures, errors or skips on `19ba6835a` plus this test-only
change. No production behavior changed.

Command: `JAVA_HOME=<JDK21> LUA_BIN=lua5.4 DISPLAY=:0 python3
tools/testing/maven_queue.py -Dmse=off -Dopenggf.test.gl.native=true
-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen -Dtest=TestS3kDdzLifecycleProduction test`.
The change-based plan selects1959 classes through gameplay test ownership.
Focused validation is proportionate for this assertion/driver-only change; no
full-suite rerun is claimed. Ending `$D01` history isolation remains open.

### 2026-09-25 ending-load history boundary

`TestS3kDdzLifecycleProduction#endingRequestSavesProgressAndStartsAnIsolatedTimeline`
now covers the production `loc_81CA4` handoff at 320 and800. It builds live DDZ
flight history, then declares the already-finished-white-fade boss state
(`exitMode`, routine6, timer0) to isolate the boundary. The actual object update
requests `PROGRESSION_SAVE` and `$D01`; `GameLoop` consumes that request and
loads zone13/act1 with an isolated timeline. The spy observes the real save
request without replacing its implementation. This checks the request, not
persistence to a selected on-disk save slot, and does not replay the preceding
fight or certify the ending scene. Existing full routes own the fight.

On `ae21cd421` plus this test-only addition,
`JAVA_HOME=<JDK21> LUA_BIN=lua5.4 DISPLAY=:0 python3 tools/testing/maven_queue.py
-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen
-Dtest=TestS3kDdzLifecycleProduction test` passes all four death/ending cases,
zero failures/errors/skips. The inspected plan selects1959 gameplay classes;
focused validation is proportionate for this bounded test-only extension. No
production change or broad-suite rerun is claimed.
