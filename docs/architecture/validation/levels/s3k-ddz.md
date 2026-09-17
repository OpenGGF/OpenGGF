# S3K The Doomsday Zone coverage matrix

Game / canonical zone / act: S3K `S3K_DOOMSDAY`, engine zone `$0C` act index 0,
ROM `Current_zone_and_act = $0C00`, SKL object set.
Character route: Sonic (Player 2 cleared by `loc_81554`): fall-in → transformation (Super, Hyper
with seven Super Emeralds) → free flight through the autoscrolling asteroid field → end boss phase
1 (turrets, launchers, missiles into the body) → phase 2 chase with two `$7400 → $5400` wraps →
defeat → exit fade → `StartNewLevel $D01`. Owning plan:
[DDZ bring-up](../../plans/2026-09-17-ddz-bring-up.md).
Status: in progress. Nothing below certifies the zone.

Incoming: DEZ final boss → `$C00` (blocked: DEZ campaign), level select. Outgoing: `$D01` ending
(not implemented; the request is asserted, the destination is out of scope).

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
| REWIND: phase-1 fight, first wrap, exit fade | Restore equals capture (object graph summary); 45 divergent frames discarded; recorded route continues with exact native parity to the exit | native 320 | `TestS3kDdzColdRoutes#rewindAtBossWrapAndExitRestoresTheNativeRoute` | implemented | see execution evidence | Hurt spin, asteroid split and final-hit spots are covered only inside these windows |
| REWIND: mid-transformation, mid-flight | Registry restore equals capture; forward replay equals original | 34 rows × 2 spots | `TestS3kDdzCompatibilityMatrix` | implemented | see execution evidence | — |
| PRESENT: background bands, FG-plane boss body | `sub_596EA` six speeds from `Events_bg+6`, `DDZ_BGDeformArray`; `DDZ_ScreenEvent` stages 0/4/8/`$C` | native | engine captures `~/Videos/OGGF/ddz-bring-up` (moving inspection) | implemented | visual inspection only | Native pixel comparison open |
| OBJECT: asteroids, missiles, boss graph, slot/load order | `Obj_DDZAsteroid`, `Obj_DDZMissile`, `Obj_DDZEndBoss`; native slot histories `probe-slots0/1` | native route | seeded route test (slot order drives hit order) | implemented; native behaviour matched through the route | pass | Super/Hyper transformation stars (`$2D690`/`$2D95C`) not implemented |
| LIFE: ring-out death and restart | `Kill_Character` via ring drain | — | — | not verified | unrun | Slice 5 open: death reload and timeline isolation |
| LOAD: `$D01` handover freeze | `StartNewLevel` leaves the level loop; native fade frozen | GameLoop only | — | GameLoop freezes (`isNonRewindableTransitionPending`); recording frame driver keeps stepping | open | Harness gap, frames 10059-10079 |
| ORACLE: strict segment replay | `TestS3kSonicTailsZone0cSegmentTraceReplay` | — | `-Ptrace-segments` | — | red: bootstrap camera Y and missing clock seeds (plan evidence) | Replay harness bootstrap |
| LOAD: DEZ → `$C00` incoming | DEZ events | — | — | missing (DEZ) | blocked | DEZ campaign |

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
