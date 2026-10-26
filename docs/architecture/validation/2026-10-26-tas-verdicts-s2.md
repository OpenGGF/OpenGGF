# Sonic 2 and official KiS2 executed TAS mechanism verdicts — 2026-10-26

This supersedes the classification in [the earlier obligations](2026-10-26-tas-tricks-s2.md). All 24 original families have executed core outcomes: **15 supported, 6 unsupported, 3 mixed**. A mixed family is an aggregation of the explicitly named tested children below. It is not an untested category. No production code changed.

The source baseline is `c69447d77baddd8d3f7e4a177d73c8d327dd223d`, branch `feature/ai-tas-verdicts-s2-20261026`, worktree `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-b2a013f5`. Native citations below use immutable S2 disassembly `34fc7f130a96db171e1a1c43e28e0bad81cd6ca7` read with `git show`; inherited dirty disassemblies were preserved and not used as proof. Native authoring probes use an isolated archive/build of TraceChaser `703bd92d32634ce5ebac60625b48c60ef5500427` and read-only BizHawk2.11.0.0 at `${TAS_USER_ROOT}/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64`. They are comparison-only, never engine hydration.

Supported means an actual mechanism reaches its observable exit with a meaningful boundary/control. Valid authored inlets are declared per scenario. Unsupported means a reached native contradiction or an exercised unavailable capability. Passing current-limitation regressions confirm incompatibility; they do not mean native parity passed. Failed fixture construction and rejected hypotheses are recorded below and never count as unsupported.

Representative mechanism proof closes the core technique without requiring every route, historical revision, total score or full movie. The original5163/5174-ring and seven-emerald campaign scopes remain explicit below; their constituent outcomes do not certify those campaigns. All full movies remain unverified, independently of gameplay verdicts and GMV/BK2 format support.

5647M/8670S remains a **source placeholder**: its brief notes promise absent stage comments and link5373S as a comparison. They name no additional concrete mechanism. No engine-unsupported verdict or inherited movie-specific trick attribution is manufactured for it.

## Executed family matrix

| Stable family | Outcome | Named executed scenarios |
| --- | --- | --- |
| `s2-spindash-momentum` | **supported** | Charge/release speed and slope projection (supported); KiS2 air steering retains existing superspeed (supported) |
| `s2-monitor-momentum` | **supported** | Left-pixel monitor ride, fast roll-off and running landing (supported) |
| `s2-p2-cooperation` | **supported** | Recorded P2 jump defeats an enemy while P1 waits (supported) |
| `s2-zips` | **supported** | MTZ platform side-entry followed by continuous reverse wall zip (supported) |
| `s2-horizontal-underflow` | **unsupported** | Unsigned left-boundary underflow wrap (S2 and KiS2 rules) (unsupported) |
| `s2-slope-storage` | **supported** | Deleted support retains grounded travel over empty terrain (supported) |
| `s2-spindash-storage` | **supported** | HTZ seesaw launch retains incoming charge (supported); WFZ slider charges while carried and retains charge at launch (supported) |
| `s2-oil-speed-corruption` | **unsupported** | Hurt-state entry into OOZ launcher (unsupported) |
| `s2-oil-hops` | **supported** | Repeated short oil hops preserve useful superspeed and avoid sinking (supported) |
| `s2-pause-cycles` | **unsupported** | Pause gameplay while V-int-dependent object phase advances (unsupported) |
| `s2-camera-streaming` | **supported** | Outrun the camera to postpone object admission (supported) |
| `s2-capsule-rng` | **mixed** | Optional enemy defeat advances RNG and changes last-animal results delay (supported); Pause deletes the final V-int-masked animal spawn (unsupported) |
| `s2-boss-optimization` | **mixed** | Attacking contact damages a boss and shapes the rebound (supported); ARZ final-hit travel-endpoint explosion skip (unsupported) |
| `s2-path-switch` | **supported** | Miss a layer-switch span or consume its grounded-only latch in air (supported) |
| `s2-knuckles-movement` | **supported** | KiS2 glide-wall grab, climb/ledge and wall-jump rules (supported) |
| `s2-ring-attack` | **mixed** | Ordinary ring collection uses native contact box (supported); Knuckles monitor conversion and no-jump SCZ scoring constituents (supported); OOZ buried-ring launcher corruption constituent (unsupported) |
| `s2-emerald-route` | **supported** | 50-ring post invitation and real jump enter stock/KiS2 halfpipe (supported); KiS2 preserves checkpoint ring bank; stock S2 clears it (supported); Halfpipe emerald arrival and terminal pass (supported) |
| `s2-special-stage-tally` | **supported** | Collect minimum rings to shorten results tally (supported) |
| `s2-special-stage-lag` | **unsupported** | Jump-dependent live halfpipe pacing capability (unsupported) |
| `s2-super-forms` | **supported** | Stock S2 airborne jump automatically transforms with inventory (supported); KiS2 fresh action button transforms and uses lock-on powered rules (supported) |
| `s2-damage-bounce` | **supported** | Enemy rebound branches preserve horizontal momentum (supported); Hurt motion and oil landing keep routine4 until recovery (supported) |
| `s2-combo-score` | **supported** | Real plane support retains the kill chain; jumping and relanding resets it (supported) |
| `s2-dez-art-corruption` | **unsupported** | DEZ animal PLC overwrites explosion art at shared tile580 (unsupported) |
| `s2-intro-p2-pause` | **unsupported** | P2-dependent introductory-text/title transition capability (unsupported) |

## Mechanisms, native expectations and reached results

### `s2-spindash-momentum` — supported

Original scope: Spindash charging, slope acceleration and momentum management. Original publication IDs: 600, 1779, 2037, 3038, 3325, 3339, 3584, 4682, 5412, 5640, 5675.

**Charge/release speed and slope projection — supported.** Publications: [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S), [1779M](https://tasvideos.org/1779M) / [3101S](https://tasvideos.org/3101S), [2037M](https://tasvideos.org/2037M) / [3557S](https://tasvideos.org/3557S), [3038M](https://tasvideos.org/3038M) / [4932S](https://tasvideos.org/4932S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S), [4682M](https://tasvideos.org/4682M) / [7453S](https://tasvideos.org/7453S), [5412M](https://tasvideos.org/5412M) / [8376S](https://tasvideos.org/8376S), [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S), [5675M](https://tasvideos.org/5675M) / [8621S](https://tasvideos.org/8621S).

Native expectation: Fresh charge edges increase the bounded release speed; slope projection changes the released x/y components.

Executed: `com.openggf.sprites.managers.TestPlayableSpriteMovement#testSpindashVelocityCalculation+testSpindashVelocityOnSlope`. Both stock-S2 charge/slope methods pass; the WFZ recorded-input test additionally creates charge through the actual input/animation gateway.

Evidence: Sonic_Spindash / Sonic_ReleaseSpindash, pinned S2 disassembly.

**KiS2 air steering retains existing superspeed — supported.** Publications: [3038M](https://tasvideos.org/3038M) / [4932S](https://tasvideos.org/4932S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S).

Native expectation: Steering in the existing direction preserves above-cap speed for KiS2; stock S2 caps it.

Executed: `com.openggf.sprites.managers.TestKis2MovementRules#airSteeringRetainsExistingSuperspeedBothDirectionsButStockS2Caps`. Both signs and stock-S2 controls pass.

Evidence: KiS2 per-game air-steering rules, existing native comparison regression.

Scope/edges: Route-specific slope jumps and publication frame savings are not asserted.

### `s2-monitor-momentum` — supported

Original scope: Monitor-edge rolling-to-running conversion (monitor madness). Original publication IDs: 3325, 3339, 3584.

**Left-pixel monitor ride, fast roll-off and running landing — supported.** Publications: [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S).

Native expectation: Acquire monitor support at x=monitor-15; Down with enough speed clears its far edge in one pass, enters air, then lands running without losing horizontal speed. A slower roll remains supported.

Executed: `com.openggf.game.sonic2.kis2.TestTasKis2MonitorMadness#leftPixelRideThenFastRollClearsTheMonitorAndReturnsToRunning`. Both actual GameplayCaptureSession controls pass. Fast clears the intact monitor and lands running with retained speed; slow stays riding. Native fast: x201->264, air/roll, then x520 grounded/run with vx16372; slow x201->204 remains riding.

Evidence: Fresh KiS2 GPGX probe (monitor-native.csv.gz and monitor-native-slow.csv.gz); Obj26_ChkOverEdge (s2.asm:25647-25672), plus actual lock-on ROM capture.

Scope/edges: Native probe uses authored monitor/falling inlet and a legal seeded superspeed, not the published EHZ approach. Monitor breaking is not this technique; a preliminary break-only inlet also stayed rolling natively and was rejected.

### `s2-p2-cooperation` — supported

Original scope: Manually controlled Tails, object assistance and respawn scheduling. Original publication IDs: 600, 1779, 2037, 3584, 4682, 5640.

**Recorded P2 jump defeats an enemy while P1 waits — supported.** Publications: [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S), [1779M](https://tasvideos.org/1779M) / [3101S](https://tasvideos.org/3101S), [2037M](https://tasvideos.org/2037M) / [3557S](https://tasvideos.org/3557S), [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S), [4682M](https://tasvideos.org/4682M) / [7453S](https://tasvideos.org/7453S), [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S).

Native expectation: Manual controller-2 input overrides the first Tails CPU and can independently attack an active enemy.

Executed: `com.openggf.game.sonic2.TestTasS2ManualTails#manualP2JumpHitsTheEnemyAboveTailsWhileNeutralP2DoesNot`. Two controls pass: P2 jump kills real Nebula for100, neutral leaves it alive; P1 never jumps.

Evidence: Native Ctrl_2/sidekick manual-input path and Touch_KillEnemy; actual ROM touch table.

Scope/edges: Other gates, boss schedules and offscreen respawn routes remain route variants, not prerequisites for the manual-assistance verdict.

### `s2-zips` — supported

Original scope: Object clips, terrain ejection and local zips. Original publication IDs: 600, 1779, 2037, 3038, 3325, 3339, 3584, 4682, 5412, 5640, 5675.

**MTZ platform side-entry followed by continuous reverse wall zip — supported.** Publications: [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S), [1779M](https://tasvideos.org/1779M) / [3101S](https://tasvideos.org/3101S), [2037M](https://tasvideos.org/2037M) / [3557S](https://tasvideos.org/3557S), [3038M](https://tasvideos.org/3038M) / [4932S](https://tasvideos.org/4932S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S), [4682M](https://tasvideos.org/4682M) / [7453S](https://tasvideos.org/7453S), [5412M](https://tasvideos.org/5412M) / [8376S](https://tasvideos.org/8376S), [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S), [5675M](https://tasvideos.org/5675M) / [8621S](https://tasvideos.org/8621S).

Native expectation: A moving solid can push a valid outside player inside full terrain; holding Left then causes repeated rightward wall corrections and an exit.

Executed: `com.openggf.game.sonic2.TestTasS2PlatformZip#platformEntryThenOppositeInputProducesTerrainEjectionWithEmptyWallControl`. Real Obj6B pushes x272 to310. Left yields x330,362,394,426, grounded; the full body exits wall x288..415. Empty-wall control moves left and has no reverse ejection.

Evidence: Obj6B oscillating platform, native SolidObject side correction and Sonic wall-scan ejection; author5373S describes opposite-direction corrections up to32pixels.

Scope/edges: Authored valid floor/wall, real active LevelManager, real GroundSensor and object/movement ordering. Other crusher, nut, flipper and monitor inlets are unexecuted edges; horizontal underflow has its separate unsupported family.

### `s2-horizontal-underflow` — unsupported

Original scope: Left-edge coordinate underflow / level wrap. Original publication IDs: 2037, 3325, 3584.

**Unsigned left-boundary underflow wrap (S2 and KiS2 rules) — unsupported.** Publications: [2037M](https://tasvideos.org/2037M) / [3557S](https://tasvideos.org/3557S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S).

Native expectation: Predicted x=$FFE8 must fail the unsigned left comparison and clamp to the far-right bound $2168.

Executed: `com.openggf.game.sonic2.TestTasS2CurrentLimitations#currentLimitationUnderflowClampsLeftInsteadOfNativeFarRight`. Both rule variants reach the boundary owner but clamp to$10 and zero speeds; slow in-bounds control preserves x24/vx-100. Native parity assertion red: expected8552, actual16.

Evidence: Sonic_LevelBound (s2.asm:37233-37254), unsigned bcc/bcs word comparisons; pin 34fc7f130a96db171e1a1c43e28e0bad81cd6ca7

Scope/edges: No level/camera warp destination route is claimed after this failed core boundary step.

### `s2-slope-storage` — supported

Original scope: Stale object support / slope glitch. Original publication IDs: 2037, 3325, 3339, 4682, 5640.

**Deleted support retains grounded travel over empty terrain — supported.** Publications: [2037M](https://tasvideos.org/2037M) / [3557S](https://tasvideos.org/3557S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), [4682M](https://tasvideos.org/4682M) / [7453S](https://tasvideos.org/7453S), [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S).

Native expectation: After actual support is deleted, native Status_OnObj can suppress terrain detachment and retain grounded travel in the air; a jump switches to airborne movement.

Executed: `com.openggf.game.sonic2.objects.TestTasS2SupportUnload#deletedSupportRetainsGroundedTravelUntilARealJumpReleasesIt`. Real monitor landing and deletion;20 real Sonic movement/object passes move horizontally at constant height, grounded/on-object; a fresh jump sets air and negative y velocity. Live-support control also passes.

Evidence: AnglePos (s2.asm:43006-43019) trusts Status_OnObj; Sonic_Jump (s2.asm:37345-37404) sets InAir but does not itself clear OnObj.

Scope/edges: Representative retained angle is flat; exact MTZ sloped-object/ring-group paths are not replayed. A manually manufactured stale bit without an acquired owner is a different fixture and is not used for this verdict.

### `s2-spindash-storage` — supported

Original scope: Stored spindash state on seesaws or moving launchers. Original publication IDs: 600, 3339.

**HTZ seesaw launch retains incoming charge — supported.** Publications: [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S).

Native expectation: The descending ball reaches contact and launches a charging rider without clearing its spindash flag/counter.

Executed: `com.openggf.game.sonic2.objects.TestTasS2StoredSpindash`. Three cases pass: charged, uncharged and no-tilt controls; real ball contact exits airborne with retained flag/counter and unchanged horizontal velocity.

Evidence: Obj14_Ball_Fly / Obj14_LaunchCharacter, fixBugs=0, immutable S2 source.

**WFZ slider charges while carried and retains charge at launch — supported.** Publications: [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S).

Native expectation: Down followed by fresh A edges while riding ObjC0 creates a charge; endpoint launch preserves it.

Executed: `com.openggf.game.sonic2.objects.TestTasS2WfzSpindashStorage#catapultTransportsAndLaunchesTheInputChargedRiderWithoutClearingStorage`. Two GameplayCaptureSession cases pass: actual landing, input-created charge,128pixel platform travel, endpoint x speed>C00/y=-400, charged flag/counter retained; uncharged control stays uncharged.

Evidence: loc_3BF66 / loc_3C020 (s2.asm:80906-80980); separate fresh S2 GPGX slider authoring probe corroborates input charging and launch retention.

Scope/edges: Seesaw uses a valid pre-contact charged rider; WFZ catapult is authored on a clear EHZ inlet. Later landing/release routing is separate from storing charge across the actual object launch.

### `s2-oil-speed-corruption` — unsupported

Original scope: Oil Ocean hurt-to-cannon speed glitch and repeated stacking. Original publication IDs: 335, 3339.

**Hurt-state entry into OOZ launcher — unsupported.** Publications: [335M](https://tasvideos.org/335M) / [658S](https://tasvideos.org/658S), [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S).

Native expectation: With fixBugs=0 the launcher accepts routine4 hurt entry (only routine>=6 is rejected), enabling the reported speed-corruption sequence.

Executed: `com.openggf.game.sonic2.TestTasS2CurrentLimitations#currentLimitationHurtLauncherRejectsNativeRoutineFourEntry`. Actual hurt is reached alive; both S2/KiS2 rule variants reject capture and retain hurt velocities. Clearing hurt at the same position permits capture/inertia1000. Native capture assertion was red before retaining the limitation.

Evidence: loc_252F0 (s2.asm:51309-51387), including the explicit fixBugs conditional for the hurt glitch.

Scope/edges: The later buried-ring route cannot execute this required launcher entry; no claim that every alternative ring route is impossible.

### `s2-oil-hops` — supported

Original scope: Oil-surface hopping and boss-approach vertical repositioning. Original publication IDs: 600, 1779, 2037, 3038, 3325, 3584.

**Repeated short oil hops preserve useful superspeed and avoid sinking — supported.** Publications: [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S), [1779M](https://tasvideos.org/1779M) / [3101S](https://tasvideos.org/3101S), [2037M](https://tasvideos.org/2037M) / [3557S](https://tasvideos.org/3557S), [3038M](https://tasvideos.org/3038M) / [4932S](https://tasvideos.org/4932S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S).

Native expectation: Jump off real oil support, release jump, land through Obj07, and repeat before submersion kills; landing copies horizontal speed into running inertia.

Executed: `com.openggf.game.sonic2.TestTasS2OilHops#twoRealShortHopsRetainSuperspeedAndAvoidTheStandingSinkDeath,com.openggf.game.sonic2.TestTasS2OilMomentum`. Two complete jump/air/landing cycles pass with g>600; standing control dies from submersion. Seven landing/contact boundary cases also pass, including both signs and ascending rejection.

Evidence: Obj07_Main, PlatformObject_ChkYRange and RideObject_SetRide in immutable S2 source.

Scope/edges: Authored open oil column starts at legal superspeed3000. Oil contact does not erase speed, but normal airborne drag still applies; earlier C00 entry lost enough speed over two hops to invalidate that chosen threshold. Pillar routes are not required.

### `s2-pause-cycles` — unsupported

Original scope: Pause manipulation of power-on cycles. Original publication IDs: 1779, 2037, 3038, 3325, 3339, 3584, 5640, 5675.

**Pause gameplay while V-int-dependent object phase advances — unsupported.** Publications: [1779M](https://tasvideos.org/1779M) / [3101S](https://tasvideos.org/3101S), [2037M](https://tasvideos.org/2037M) / [3557S](https://tasvideos.org/3557S), [3038M](https://tasvideos.org/3038M) / [4932S](https://tasvideos.org/4932S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S), [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S), [5675M](https://tasvideos.org/5675M) / [8621S](https://tasvideos.org/8621S).

Native expectation: Pause and held/released Start freeze object gameplay while V-int continues; a later fresh Start resumes at a different power-on phase.

Executed: `com.openggf.game.sonic2.objects.TestTasS2CapsuleManipulation#currentLimitationPauseFreezesVintAndCannotOmitTheLateAnimal`. Recorded Start, release and resume execute. Bound V-int freezes at7 during both paused samples, then resumes at8; an animal spawns and blocks results. Running off-mask control omits it and reaches results. Initial native clock parity expected8/actual7 was red.

Evidence: PauseGame/WaitForVint and loc_3F3A8; engine GameLoop.prepareAdmittedIteration PAUSED -> LevelFrameStep.serviceVBlankOnly does not advance bound ObjectManager V-int.

Scope/edges: Ordinary pause itself works. This verdict concerns phase manipulation, not absence of pause or all movie timing.

### `s2-camera-streaming` — supported

Original scope: Camera delay, offscreen object avoidance and level-ending camera tricks. Original publication IDs: 600, 1779, 3038, 3325, 3339, 3584.

**Outrun the camera to postpone object admission — supported.** Publications: [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S), [1779M](https://tasvideos.org/1779M) / [3101S](https://tasvideos.org/3101S), [3038M](https://tasvideos.org/3038M) / [4932S](https://tasvideos.org/4932S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S).

Native expectation: Camera motion caps at16pixels; faster movement can pass an object position before its camera-based loading window, while normal speed loads it.

Executed: `com.openggf.game.sonic2.TestTasS2CameraStreaming#superspeedPassesThePlacementBeforeItLoadsButOrdinarySpeedDoesNot`. Actual movement/camera/object passes: fast route sees no monitor before crossing its position; slow control loads it; letting camera catch up then admits the same object.

Evidence: Author5373S camera/streaming description; native camera speed and object placement window owners.

Scope/edges: Signpost/capsule/boss-barrier layouts are variants, not claimed individually synchronized.

### `s2-capsule-rng` — mixed

Original scope: Capsule animal/explosion RNG and tally manipulation. Original publication IDs: 3038, 3325, 3584, 5675.

**Optional enemy defeat advances RNG and changes last-animal results delay — supported.** Publications: [3038M](https://tasvideos.org/3038M) / [4932S](https://tasvideos.org/4932S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S).

Native expectation: A badnik defeat releases an animal and consumes RNG; changing this stream before the capsule changes its animals and can reduce the wait for results.

Executed: `com.openggf.game.sonic2.objects.TestTasS2CapsuleRng#precedingEnemyDefeatChangesTheLastAnimalAndItsResultsDelay`. Actual touch defeat and spawned enemy animal execute and leave; same late capsule state then spawns offset-21/results133 versus optional-kill omitted offset-19/results132. Real animal departure releases actual ResultsScreenObjectInstance.

Evidence: 5735S/5373S explicitly name destroying badniks as RNG consumers; RandomNumber (s2.asm:3975), Obj28 initialization, loc_3F3A8 (85096) and loc_3F406 (85128).

**Pause deletes the final V-int-masked animal spawn — unsupported.** Publications: [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S), [5675M](https://tasvideos.org/5675M) / [8621S](https://tasvideos.org/8621S).

Native expectation: Paused V-ints should shift the final admitted capsule pass off the spawn mask without consuming that animal RNG draw.

Executed: `com.openggf.game.sonic2.objects.TestTasS2CapsuleManipulation#currentLimitationPauseFreezesVintAndCannotOmitTheLateAnimal`. Current pause clock freezes, so resume reaches the original spawn mask; animal/RNG draw remain and results wait. Off-mask running control reaches results.

Evidence: 5373S/5735S capsule notes and8621S pause acknowledgement; loc_3F3A8/loc_3F406 and shared bound-clock limitation.

Scope/edges: Capsule uses a declared valid late-release child: earlier animals already left. It never seeds clock advancement or the exploit exit. Full capsule/camera schedules, other RNG consumers and every animal species are edges.

### `s2-boss-optimization` — mixed

Original scope: Early boss hits, bounce shaping and defeat-sequence skips. Original publication IDs: 600, 1779, 2037, 3038, 3325, 3339, 3584.

**Attacking contact damages a boss and shapes the rebound — supported.** Publications: [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S), [1779M](https://tasvideos.org/1779M) / [3101S](https://tasvideos.org/3101S), [2037M](https://tasvideos.org/2037M) / [3557S](https://tasvideos.org/3557S), [3038M](https://tasvideos.org/3038M) / [4932S](https://tasvideos.org/4932S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S).

Native expectation: Actual attacking overlap damages the boss, rebounds the player and starts defeat countdown on the following boss pass.

Executed: `com.openggf.tests.TestHTZBossTouchResponse#attackingTouchResponseDamagesBossAndAppliesBossBounce+defeatRoutineCountdownStartsOnFrameAfterFinalHit`. Both existing ROM-backed tests pass.

Evidence: Native boss collision/hit and deferred-defeat owner; ROM collision-size table.

**ARZ final-hit travel-endpoint explosion skip — unsupported.** Publications: [1779M](https://tasvideos.org/1779M) / [3101S](https://tasvideos.org/3101S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S).

Native expectation: Final hit writes defeat routine8, then reaching the travel endpoint adds2, reachingA and skipping explosions.

Executed: `com.openggf.game.sonic2.objects.TestTasS2ArzBossEndpoint#currentLimitationEndpointFinalHitRestartsAttackInsteadOfSkippingExplosions`. Actual final touch reduces hitCount1->0, later boss slot marks defeated and awards1000. Ordinary approach ends8; endpoint ends4 instead of nativeA. Independent parity was red.

Evidence: Obj89_Main_KillBoss (s2.asm:65237) and Obj89_Main_Sub2_AtTarget (65070), addq rather than assignment.

Scope/edges: Other boss-specific attack schedules and whole-fight frame totals are not asserted. Unsupported endpoint variant is not attributed to every publication.

### `s2-path-switch` — supported

Original scope: Collision-path desynchronization / layer-switch bypass. Original publication IDs: 4682, 5412, 5640.

**Miss a layer-switch span or consume its grounded-only latch in air — supported.** Publications: [4682M](https://tasvideos.org/4682M) / [7453S](https://tasvideos.org/7453S), [5412M](https://tasvideos.org/5412M) / [8376S](https://tasvideos.org/8376S), [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S).

Native expectation: Crossing outside Obj03 vertical span, or airborne across a grounded-only switch, retains the collision path; re-entering the span alone does not retroactively switch it.

Executed: `com.openggf.game.sonic2.objects.TestTasS2PathSwitch`. Five cases pass: span boundaries, outside miss, airborne consumed latch, re-entry no-change and genuine recross control.

Evidence: Obj03 crossing/latch/span branches; 7453S CPZ retained-path route and8376S character-height restriction.

Scope/edges: This proves the bypass mechanism itself. Exact three-clip CPZ course and Knuckles jump-height constraints remain route edges.

### `s2-knuckles-movement` — supported

Original scope: Knuckles glide/climb contacts and landing-delay avoidance. Original publication IDs: 3038, 3325, 3339.

**KiS2 glide-wall grab, climb/ledge and wall-jump rules — supported.** Publications: [3038M](https://tasvideos.org/3038M) / [4932S](https://tasvideos.org/4932S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S).

Native expectation: Glide collision selects the lock-on wall response; signed-width contact grabs the wall; ledge and wall-jump routines change shape/position as specified.

Executed: `com.openggf.sprites.managers.TestKis2MovementRules#glideUsesSmallTerrainRadiiThenStandingTouchRadiiWithoutMovingCentre+idleClimbKeepsFrameAndDoesNotDetachFromFloorButStockDoes+glideGrabsSignedWidthWallAndPreservesNativePositionWords+ledgeTableHoldsSixSlotsAndGroundsWithNativeWordAdds+wallJumpChangesShapeWithoutMovingEitherNativePositionWord,com.openggf.sprites.managers.TestKnucklesGlideCollisionContract#kis2CollectsFloorButWallResponseWinsWithGlideRadiiThroughAlignment,com.openggf.sprites.managers.TestKnucklesWallClimbVerticalContract#realVerticalProfilesIncludeRadiusForAllFourNativeBranches`. Selected real movement/terrain profile tests pass, including both wall directions and stock-game controls.

Evidence: Existing KiS2 native movement/vertical-profile regressions; original notes distinguish glide/climb and height limits.

Scope/edges: This is ordinary character-specific movement support, not certification of every climb/glide clip route.

### `s2-ring-attack` — mixed

Original scope: Ring attack, extra lock-on monitors and buried-ring routes. Original publication IDs: 3339.

**Ordinary ring collection uses native contact box — supported.** Publications: [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S).

Native expectation: Ring contact follows the native mapping-dependent player box, with boundary/non-contact controls.

Executed: `com.openggf.tests.TestRingManager#testS2StageRingDuckBoxUsesMappingFrameInsteadOfCrouchingState`. Actual RingManager collection boundary test passes; separate KiS2 CNZ placement oracle confirms chip-backed layout counts.

Evidence: S2 ring collection hitbox; original5385S ring collection objective.

**Knuckles monitor conversion and no-jump SCZ scoring constituents — supported.** Publications: [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S).

Native expectation: Use the monitor running conversion and preserve enemy chain across real plane support without jumping.

Executed: `com.openggf.game.sonic2.kis2.TestTasKis2MonitorMadness,com.openggf.game.sonic2.kis2.TestTasKis2SkyChaseChain`. Both constituent mechanisms reach their exits and controls; see their dedicated family records.

Evidence: 5385S ring-attack notes; native monitor probe and real KiS2 Tornado/kill/relanding tests.

**OOZ buried-ring launcher corruption constituent — unsupported.** Publications: [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S).

Native expectation: The documented route requires the hurt-state launcher entry that fixBugs=0 permits.

Executed: `com.openggf.game.sonic2.TestTasS2CurrentLimitations#currentLimitationHurtLauncherRejectsNativeRoutineFourEntry`. KiS2 rule variant rejects the reached hurt entry; normal control captures.

Evidence: 5385S OOZ ring route and loc_252F0 native hurt-entry branch.

Scope/edges: Original5163/5174 campaign totals and18 buried rings remain explicitly unexecuted totals. This mixed record aggregates named executed constituents; it does not certify or silently replace the whole ring-attack campaign.

### `s2-emerald-route` — supported

Original scope: Seven emeralds, checkpoint entry and lock-on ring retention. Original publication IDs: 4682, 5412, 5640, 5675.

**50-ring post invitation and real jump enter stock/KiS2 halfpipe — supported.** Publications: [4682M](https://tasvideos.org/4682M) / [7453S](https://tasvideos.org/7453S), [5412M](https://tasvideos.org/5412M) / [8376S](https://tasvideos.org/8376S), [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S), [5675M](https://tasvideos.org/5675M) / [8621S](https://tasvideos.org/8621S).

Native expectation: Actual post contact at50 rings spawns stars; a real jump into the active stars enters the special stage.49 rings cannot.

Executed: `com.openggf.game.sonic2.kis2.TestTasS2StarpostEntry#fiftyRingPostAndRealJumpEnterTheSpecialStage`. Four cases pass: Sonic and Knuckles enter SPECIAL_STAGE only at50; both49 controls stay in LEVEL; actual KiS2 provider asserted.

Evidence: Obj79 activation/ring requirement/Star touch, native special-stage entry owner.

**KiS2 preserves checkpoint ring bank; stock S2 clears it — supported.** Publications: [4682M](https://tasvideos.org/4682M) / [7453S](https://tasvideos.org/7453S), [5412M](https://tasvideos.org/5412M) / [8376S](https://tasvideos.org/8376S), [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S), [5675M](https://tasvideos.org/5675M) / [8621S](https://tasvideos.org/8621S).

Native expectation: Reload after checkpoint/special-stage return restores the lock-on bank but not stock S2 bank.

Executed: `com.openggf.game.sonic2.kis2.TestKis2HeadlessBoot#checkpointReloadRestoresOnlyTheGamesSavedRingBank,com.openggf.game.sonic2.kis2.TestKis2SpecialStageControllerLifecycle#entryResultsReturnKeepsChipProvider`. Actual stock/KiS2 reload passes; existing actual results/return lifecycle retains47 rings and chip provider. That lifecycle explicitly stimulates completion and is not an input-played halfpipe win.

Evidence: Native checkpoint save/restore game distinction; KiS2 ROM-backed provider.

**Halfpipe emerald arrival and terminal pass — supported.** Publications: [4682M](https://tasvideos.org/4682M) / [7453S](https://tasvideos.org/7453S), [5412M](https://tasvideos.org/5412M) / [8376S](https://tasvideos.org/8376S), [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S), [5675M](https://tasvideos.org/5675M) / [8621S](https://tasvideos.org/8621S).

Native expectation: Approach animation reaches the award phase, then the success countdown finishes only after crossing zero; insufficient rings take failure.

Executed: `com.openggf.game.sonic2.specialstage.Sonic2SpecialStageStreamedObjectCadenceTest#emeraldChecksAnimationBeforeMovementAndAwardsOnTheFollowingPass+emeraldSuccessCountdownCompletesOnlyAfterZero+emeraldFailureSeedsFourFAndCompletesOnlyAfterZero`. Three actual emerald-update controls pass; all-seven KiS2 target table test independently passes.

Evidence: Obj59 emerald approach/award/countdown native owners, existing source-backed regression.

Scope/edges: The original seven-emerald campaign is preserved as scope, but no seven-stage input movie was executed. These are named entry, completion and return mechanisms, not a claim that concatenating isolated fixtures reproduces the full campaign.

### `s2-special-stage-tally` — supported

Original scope: Minimum-ring special-stage tally optimization. Original publication IDs: 5412.

**Collect minimum rings to shorten results tally — supported.** Publications: [5412M](https://tasvideos.org/5412M) / [8376S](https://tasvideos.org/8376S).

Native expectation: Beyond the emerald tally floor, each surplus ring costs a tally update.

Executed: `com.openggf.game.sonic2.objects.TestTasS2SpecialStageTally`. Actual result lifecycle comparison at40/41/100/101/120 passes: floor behavior and surplus-ring incremental cost are distinguished.

Evidence: 8376S minimum-ring route note; native special-stage result tally loop.

Scope/edges: Exact campaign timing and shared sound/perfect-bonus presentation are outside the tally-duration claim.

### `s2-special-stage-lag` — unsupported

Original scope: Jump-dependent special-stage lag reduction. Original publication IDs: 5640.

**Jump-dependent live halfpipe pacing capability — unsupported.** Publications: [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S).

Native expectation: 8629S reports jumping reduces native lag; the native main loop executes input-dependent object/sprite work between V-int waits. A live approximation that never observes jump/render work cannot represent that dependency.

Executed: `com.openggf.game.sonic2.specialstage.TestTasS2JumpLag#currentLimitationJumpingDoesNotChangeTheLiveLagSchedule`. Actual ROM-backed playable halfpipe,0 versus>20 jumping frames, real lag present; paired240-update schedules are equal. The exercised owner shouldSkipLiveUpdate takes only frameCounter/speedFactor/segmentType and fixed ratios.

Evidence: 8629S author claim; native SpecialStage loop (s2.asm:6677-6725) calls RunObjects/BuildSprites between WaitForVint. Engine Sonic2SpecialStageLagModel:34-38 ignores jump/render cost.

Scope/edges: No native capture of this exact240-update window; equality alone is not a native counterexample or measured frame delta. Verdict is the exercised unavailable jump-dependent live-pacing capability. Recorded timing admission and full movie emulation format are separate.

### `s2-super-forms` — supported

Original scope: Super Sonic / Super Knuckles transformation and routes. Original publication IDs: 4682, 5412, 5640.

**Stock S2 airborne jump automatically transforms with inventory — supported.** Publications: [4682M](https://tasvideos.org/4682M) / [7453S](https://tasvideos.org/7453S), [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S).

Native expectation: Seven emeralds and sufficient rings permit the stock S2 automatic jump trigger.

Executed: `com.openggf.sprites.managers.TestPlayableSpriteMovement#s2JumpTriggerStillActivatesSuperSonic`. Existing actual S2 jump-trigger test passes.

Evidence: Sonic_CheckGoSuper, stock S2 trigger.

**KiS2 fresh action button transforms and uses lock-on powered rules — supported.** Publications: [5412M](https://tasvideos.org/5412M) / [8376S](https://tasvideos.org/8376S).

Native expectation: Fresh overlapping physical action buttons reach explicit super activation; transform freeze/powered profile/ring timer follow KiS2 rules.

Executed: `com.openggf.sprites.managers.TestKis2MovementRules#recordedOverlappingButtonsReachSuperThroughProductionInputPublication,com.openggf.game.sonic2.kis2.TestKis2SuperStateController#superProfileKeepsNormalJumpAndAnimationThresholdAndSeedsRingTimer+transformWaitsSixteenPassesThenCyclesAndRewindsItsTimer`. Actual recorded action gateway and two ROM-backed profile/timer/rewind tests pass.

Evidence: KiS2 super-state and input contracts, existing native regression evidence.

Scope/edges: No whole powered-form route or speculative one-frame SCZ causal attribution is certified.

### `s2-damage-bounce` — supported

Original scope: Intentional damage, enemy rebounds and jump/bounce shaping. Original publication IDs: 335, 600, 1779, 2037, 3038, 3325, 3339, 3584, 4682, 5412, 5640, 5675.

**Enemy rebound branches preserve horizontal momentum — supported.** Publications: [335M](https://tasvideos.org/335M) / [658S](https://tasvideos.org/658S), [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S), [1779M](https://tasvideos.org/1779M) / [3101S](https://tasvideos.org/3101S), [2037M](https://tasvideos.org/2037M) / [3557S](https://tasvideos.org/3557S), [3038M](https://tasvideos.org/3038M) / [4932S](https://tasvideos.org/4932S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S), [4682M](https://tasvideos.org/4682M) / [7453S](https://tasvideos.org/7453S), [5412M](https://tasvideos.org/5412M) / [8376S](https://tasvideos.org/8376S), [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S), [5675M](https://tasvideos.org/5675M) / [8621S](https://tasvideos.org/8621S).

Native expectation: Attacking enemy contact kills/scores and changes vertical velocity according to relative centre and incoming velocity without erasing x/inertia.

Executed: `com.openggf.game.sonic2.objects.TestTasS2EnemyContact#realEnemyContactUsesCentreAndVelocityBranchesWithoutLosingHorizontalMomentum`. Four native-table touch controls pass: above/equal/below and rising variants retain xA00/gB00.

Evidence: Touch_KillEnemy native centre/velocity branches.

**Hurt motion and oil landing keep routine4 until recovery — supported.** Publications: [335M](https://tasvideos.org/335M) / [658S](https://tasvideos.org/658S), [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S), [1779M](https://tasvideos.org/1779M) / [3101S](https://tasvideos.org/3101S), [2037M](https://tasvideos.org/2037M) / [3557S](https://tasvideos.org/3557S), [3038M](https://tasvideos.org/3038M) / [4932S](https://tasvideos.org/4932S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S), [4682M](https://tasvideos.org/4682M) / [7453S](https://tasvideos.org/7453S), [5412M](https://tasvideos.org/5412M) / [8376S](https://tasvideos.org/8376S), [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S), [5675M](https://tasvideos.org/5675M) / [8621S](https://tasvideos.org/8621S).

Native expectation: Actual damage creates hurt velocities; oil support copies x velocity while retaining hurt routine until HurtStop.

Executed: `com.openggf.game.sonic2.TestTasS2CurrentLimitations#currentLimitationHurtLauncherRejectsNativeRoutineFourEntry,com.openggf.tests.TestOilSurfaceManager#hurtLandingOnOilKeepsRoutineFourUntilHurtStop`. Reached alive hurt state and velocities verified; separate native oil landing/recovery regression passes.

Evidence: Native HurtCharacter, Obj07/RideObject_SetRide and HurtStop ordering.

Scope/edges: Ordinary injury support does not make the distinct hurt-launcher corruption supported. Individual damage routes/ceiling bumps are not all replayed.

### `s2-combo-score` — supported

Original scope: Sky Chase enemy-combo retention. Original publication IDs: 3339.

**Real plane support retains the kill chain; jumping and relanding resets it — supported.** Publications: [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S).

Native expectation: Kill while rolling on Tornado, remain supported, and kill again for the next award. Jump/reland before the second kill resets the chain.

Executed: `com.openggf.game.sonic2.kis2.TestTasKis2SkyChaseChain`. Actual KiS2 SCZ plane landing, roll, Nebula kill and second kill pass: no jump100+200; actual jump/air/plane relanding100+100. Native16th-kill10000 and preceding awards also pass freshly in TestTasS2EnemyContact.

Evidence: 5385S no-jump SCZ chain claim; Touch_KillEnemy and native grounded jump reset.

Scope/edges: Representative two-kill chain resolves retention. The372900 maximum and whole SCZ kill schedule are not claimed executed.

### `s2-dez-art-corruption` — unsupported

Original scope: Death Egg explosions rendered as animals. Original publication IDs: 335.

**DEZ animal PLC overwrites explosion art at shared tile580 — unsupported.** Publications: [335M](https://tasvideos.org/335M) / [658S](https://tasvideos.org/658S).

Native expectation: Loading DezAnimals after FieryExplosion overwrites the same native VRAM tile580; explosion mappings then read animal pixels.

Executed: `com.openggf.game.sonic2.TestTasS2DezArtAlias#currentLimitationAnimalPlcCannotOverwriteTheExplosionSheet`. Actual ROM PLCs identify580 overlap; both real provider load orders execute. Current dedicated explosion sheet remains fiery, differing from Pig pixels in animals-last order. Reversed-order control correctly remains fiery; parity red at pixel3 expected10/actual0.

Evidence: LevEvents_DEZ_Routine1 (21698), Obj34 animal PLC, PlrList_DezAnimals (89909), Obj58_Init (61314); constants ArtTile_FieryExplosion and Animal1 both580. Author658S describes animal explosions.

Scope/edges: Native VRAM alias is independently specified. The author’s fast-boss-kill causal explanation is not established; no quick-kill movie is claimed.

### `s2-intro-p2-pause` — unsupported

Original scope: Controller-2 pause at the introductory text boundary. Original publication IDs: 3584.

**P2-dependent introductory-text/title transition capability — unsupported.** Publications: [3584M](https://tasvideos.org/3584M) / [5735S](https://tasvideos.org/5735S).

Native expectation: 5735S reports P2 Start at the last visible white text saves one frame and corrupts title loading. The engine intro transition has no input-dependent path.

Executed: `com.openggf.game.sonic2.titlescreen.TestTasS2IntroP2Start#currentLimitationP2StartAtIntroBoundarySavesNoFrame`. Natural engine INTRO_TEXT_FADE_OUT timer21 is reached. Actual recorded P2 Start and neutral both reach ACTIVE after17 updates. TitleScreenManager advances its fixed22-step fade regardless of input.

Evidence: Author5735S "Intro" paragraph. Engine TitleScreenManager INTRO_TEXT_FADE_DURATION=22, updateIntroTextFadeOut and drawCreditText overlay alpha=timer/22; native TitleScreen text load/fade (s2.asm:4289-4330).

Scope/edges: Timer21 is the engine final modeled non-black fade step (overlay21/22), not a synchronized native frame. No native delta at that exact window or corrupted-title pixels were captured; unsupported is the exercised absent P2-dependent intro capability.

## Validation, rejected fixtures and handoff

All executions used Java25 and Lua5.4 through the shared Maven queue; every completed run had **zero skips**. No broad/full suite ran. Build-only category inspection (`python3 tools/testing/run_categories.py --base c69447d77baddd8d3f7e4a177d73c8d327dd223d`) proposed3030 smoke classes; that is disproportionate for this test-only ownership slice. The user explicitly required exact focused selectors; parent owns the combined new-class run and integration gates.

ROMs were used in place, never linked/copied: S2 CRC32`7B905383`, SHA1`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`; S3K CRC32`63522553`, SHA1`CFBF98C36C776677290A872547AC47C53D2761D6`; KiS2 CRC32`2AC1E7C6`, SHA1`6CD0537A3AEE0E012BB86D5837DDFF9342595004`. KiS2 tests configure the explicit production catalogue with all three absolute originals. GameplayCaptureSession and native presentation cases require `-Dopenggf.headless.gl=surfaceless-egl` with DISPLAY and WAYLAND_DISPLAY unset. Logic-only ROM cases also actually executed.

Counts below are per invocation, not unique tests. Final successful methods are reused without ceremonial reruns; later changes to JumpLag/Intro only clarify comments/assertion messages, not behavior. The evidence commit is the local commit containing this document; the external JSON records its full SHA after committing.

| Run | Tests/failures/errors/skips | Result and interpretation |
| --- | --- | --- |
| R0 | 4/4/0/0 | Native underflow and hurt-launcher assertions red in both rule variants; converted to explicit current-limitations. |
| B1 | 8/3/0/0 | Current-limitations4 green; SCZ score-observable fixture wrong in2; real ARZ endpoint nativeA/current4 mismatch. |
| B2 | 6/1/0/0 | SCZ2 and ARZ2 green. Old manually dispatched capsule case1 passed but was rejected as proof and replaced. Support inlet1 failed. |
| R1 | 1/1/0/0 | Support entry still failed; missing actual inline object collision, not an unsupported verdict. |
| B3 | 11/6/0/0 | P2 assistance2 and camera1 green. Native pause clock, jump-pacing hypothesis, P2-intro hypothesis and DEZ art assertions red. Oil lacked fresh physical jump; monitor missed contact. Support pending-slot case passed but was later strengthened. |
| B4 | 9/3/0/0 | Capsule production pause/resume/off-mask controls, lag/intro capability regressions and DEZ2 green. Support next-slot case green. Oil entry speed threshold, MTZ exit pixel and monitor edge remained fixture failures. |
| B5 | 53/2/0/0 | 50 reused cases plus new oil-hops1 green. MTZ actually zipped to fully-clear x426 but asserted one extra pixel; monitor-break inlet remained rolling and was rejected after native comparison. |
| B6 | 6/1/1/0 | MTZ zip1, stock super1, hurt oil1 and fast monitor1 green. Slow monitor expectation wrong (native remains riding). Support movement failed on TestableSprite null sensors. |
| B7 | 6/3/0/0 | Both monitor controls green. Support20-frame travel passed, but jump OnObj-clear assertion contradicted native Sonic_Jump. WFZ and capsule-kill entry fixtures not yet reached. |
| B8 | 7/2/0/0 | Capsule RNG1 and native emerald3 green. Support jump flag expectation and WFZ input fixture remained wrong. |
| B9 | 8/3/0/0 | Final sustained support1 and final capsule RNG1 green. WFZ charge and two50-ring starpost cases lacked physical A bits in recorded input;49-ring controls passed. |
| B10 | no execution | No tests executed: diagnostic used nonexistent isCrouching() accessor; corrected to getCrouching(). |
| B11 | 6/3/0/0 | Six executed,3 fixture failures: both50-ring entries and WFZ charge. Diagnostics isolated missing per-button actionMask; logical Jump alone was not physical A. |
| B12 | 6/0/0/0 | Final WFZ2 and actual Sonic/KiS2 starpost4 all green. No production fix. |

For portability in the committed document, `${TAS_USER_ROOT}` denotes the operator home containing `code/projects/OpenGGF` and `scratch/agent-tmp/codex` (for example, set `TAS_USER_ROOT="$HOME"`). The external `s2-verdicts.json` retains the literal absolute executed commands. These command bodies, with that variable expanded, were run from this worktree, with output redirected to temporary `target/tas-s2-*.log` files. Logs were inspected (including skips) and removed after consumption; no separate pass cache or raw result archive is retained. Queue wait and actual execution are distinct: early normal-lane waits included approximately14min for R1 and8min for B3; later qualified lean invocations admitted promptly. Total delivery elapsed time is not inferred from overlapping jobs.

**R0**

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.game.sonic2.TestTasS2CurrentLimitations' test
```

**B1**

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.game.sonic2.TestTasS2CurrentLimitations,com.openggf.game.sonic2.kis2.TestTasKis2SkyChaseChain,com.openggf.game.sonic2.objects.TestTasS2ArzBossEndpoint' -Dsonic2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s2.gen -Ds3k.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s3k.gen -Dkis2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/kis2.gen test
```

**B2**

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.game.sonic2.kis2.TestTasKis2SkyChaseChain,com.openggf.game.sonic2.objects.TestTasS2ArzBossEndpoint,com.openggf.game.sonic2.objects.TestTasS2SupportUnload,com.openggf.game.sonic2.objects.TestTasS2CapsuleManipulation' -Dsonic2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s2.gen -Ds3k.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s3k.gen -Dkis2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/kis2.gen test
```

**R1**

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.game.sonic2.objects.TestTasS2SupportUnload' test
```

**B3**

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.game.sonic2.objects.TestTasS2SupportUnload,com.openggf.game.sonic2.objects.TestTasS2CapsuleManipulation,com.openggf.game.sonic2.specialstage.TestTasS2JumpLag,com.openggf.game.sonic2.titlescreen.TestTasS2IntroP2Start,com.openggf.game.sonic2.TestTasS2OilHops,com.openggf.game.sonic2.TestTasS2ManualTails,com.openggf.game.sonic2.TestTasS2CameraStreaming,com.openggf.game.sonic2.kis2.TestTasKis2MonitorMadness,com.openggf.game.sonic2.TestTasS2DezArtAlias' -Dsonic2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s2.gen -Ds3k.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s3k.gen -Dkis2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/kis2.gen test
```

**B4**

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.game.sonic2.TestTasS2PlatformZip,com.openggf.game.sonic2.objects.TestTasS2SupportUnload,com.openggf.game.sonic2.objects.TestTasS2CapsuleManipulation,com.openggf.game.sonic2.specialstage.TestTasS2JumpLag,com.openggf.game.sonic2.titlescreen.TestTasS2IntroP2Start,com.openggf.game.sonic2.TestTasS2OilHops,com.openggf.game.sonic2.kis2.TestTasKis2MonitorMadness,com.openggf.game.sonic2.TestTasS2DezArtAlias' -Dsonic2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s2.gen -Ds3k.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s3k.gen -Dkis2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/kis2.gen test
```

**B5**

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.game.sonic2.TestTasS2PlatformZip,com.openggf.game.sonic2.TestTasS2OilHops,com.openggf.game.sonic2.kis2.TestTasKis2MonitorMadness,com.openggf.game.sonic2.objects.TestTasS2StoredSpindash,com.openggf.game.sonic2.objects.TestTasS2PathSwitch,com.openggf.game.sonic2.objects.TestTasS2SpecialStageTally,com.openggf.game.sonic2.objects.TestTasS2EnemyContact,com.openggf.game.sonic2.TestTasS2OilMomentum,com.openggf.sprites.managers.TestPlayableSpriteMovement#spindashAnglePosPublishesGroundSeedAndOnObjectClearAtPlayerTail+spindashChargeAndReleaseFramesEaseTheCameraBiasLikeTheStandingPath+testSpindashVelocityCalculation+testSpindashVelocityOnSlope+testS2LandingPreservesActiveSpindashAnimationThroughNativeAlias,com.openggf.game.sonic2.kis2.TestKis2HeadlessBoot#checkpointReloadRestoresOnlyTheGamesSavedRingBank,com.openggf.game.sonic2.kis2.TestKis2SpecialStageControllerLifecycle#entryResultsReturnKeepsChipProvider,com.openggf.game.sonic2.kis2.TestKis2SpecialStageDataLoader#allSevenStagesUseChipTargetsIncludingConfiguredTeams,com.openggf.game.sonic2.kis2.TestKis2PlacementOracle#withTheLockOnDumpCasinoNightResolvesFromTheChipWithTheCatalogueCounts,com.openggf.game.sonic2.kis2.TestKis2SuperStateController#superProfileKeepsNormalJumpAndAnimationThresholdAndSeedsRingTimer+transformWaitsSixteenPassesThenCyclesAndRewindsItsTimer,com.openggf.sprites.managers.TestKis2MovementRules#airSteeringRetainsExistingSuperspeedBothDirectionsButStockS2Caps+glideUsesSmallTerrainRadiiThenStandingTouchRadiiWithoutMovingCentre+idleClimbKeepsFrameAndDoesNotDetachFromFloorButStockDoes+recordedOverlappingButtonsReachSuperThroughProductionInputPublication+glideGrabsSignedWidthWallAndPreservesNativePositionWords+ledgeTableHoldsSixSlotsAndGroundsWithNativeWordAdds+wallJumpChangesShapeWithoutMovingEitherNativePositionWord,com.openggf.sprites.managers.TestKnucklesGlideCollisionContract#kis2CollectsFloorButWallResponseWinsWithGlideRadiiThroughAlignment,com.openggf.sprites.managers.TestKnucklesWallClimbVerticalContract#realVerticalProfilesIncludeRadiusForAllFourNativeBranches,com.openggf.tests.TestHTZBossTouchResponse#attackingTouchResponseDamagesBossAndAppliesBossBounce+defeatRoutineCountdownStartsOnFrameAfterFinalHit,com.openggf.tests.TestRingManager#testS2StageRingDuckBoxUsesMappingFrameInsteadOfCrouchingState' -Dsonic2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s2.gen -Ds3k.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s3k.gen -Dkis2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/kis2.gen test
```

**B6**

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.game.sonic2.TestTasS2PlatformZip,com.openggf.game.sonic2.kis2.TestTasKis2MonitorMadness,com.openggf.game.sonic2.objects.TestTasS2SupportUnload,com.openggf.sprites.managers.TestPlayableSpriteMovement#s2JumpTriggerStillActivatesSuperSonic,com.openggf.tests.TestOilSurfaceManager#hurtLandingOnOilKeepsRoutineFourUntilHurtStop' -Dsonic2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s2.gen -Ds3k.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s3k.gen -Dkis2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/kis2.gen test
```

**B7**

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.game.sonic2.kis2.TestTasKis2MonitorMadness,com.openggf.game.sonic2.objects.TestTasS2SupportUnload,com.openggf.game.sonic2.objects.TestTasS2WfzSpindashStorage,com.openggf.game.sonic2.objects.TestTasS2CapsuleRng' -Dsonic2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s2.gen -Ds3k.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s3k.gen -Dkis2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/kis2.gen test
```

**B8**

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.game.sonic2.objects.TestTasS2SupportUnload,com.openggf.game.sonic2.objects.TestTasS2WfzSpindashStorage,com.openggf.game.sonic2.objects.TestTasS2CapsuleRng,com.openggf.game.sonic2.specialstage.Sonic2SpecialStageStreamedObjectCadenceTest#emeraldSuccessCountdownCompletesOnlyAfterZero+emeraldFailureSeedsFourFAndCompletesOnlyAfterZero+emeraldChecksAnimationBeforeMovementAndAwardsOnTheFollowingPass' -Dsonic2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s2.gen -Ds3k.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s3k.gen -Dkis2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/kis2.gen test
```

**B9**

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.game.sonic2.objects.TestTasS2SupportUnload,com.openggf.game.sonic2.objects.TestTasS2WfzSpindashStorage,com.openggf.game.sonic2.objects.TestTasS2CapsuleRng,com.openggf.game.sonic2.kis2.TestTasS2StarpostEntry' -Dsonic2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s2.gen -Ds3k.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s3k.gen -Dkis2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/kis2.gen test
```

**B10**

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.game.sonic2.objects.TestTasS2WfzSpindashStorage,com.openggf.game.sonic2.kis2.TestTasS2StarpostEntry' -Dsonic2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s2.gen -Ds3k.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s3k.gen -Dkis2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/kis2.gen test
```

**B11**

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.game.sonic2.objects.TestTasS2WfzSpindashStorage,com.openggf.game.sonic2.kis2.TestTasS2StarpostEntry' -Dsonic2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s2.gen -Ds3k.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s3k.gen -Dkis2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/kis2.gen test
```

**B12**

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.game.sonic2.objects.TestTasS2WfzSpindashStorage,com.openggf.game.sonic2.kis2.TestTasS2StarpostEntry' -Dsonic2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s2.gen -Ds3k.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/s3k.gen -Dkis2.rom.path=${TAS_USER_ROOT}/code/projects/OpenGGF/kis2.gen test
```

### Owned class selection for parent combined validation

The following16 classes are new; no shared production/helper file was edited. Parent can append this exact class list to its combined selector and use the environment above. This list is a handoff, not an additional run claimed here.

```text
com.openggf.game.sonic2.TestTasS2CameraStreaming,com.openggf.game.sonic2.TestTasS2CurrentLimitations,com.openggf.game.sonic2.TestTasS2DezArtAlias,com.openggf.game.sonic2.TestTasS2ManualTails,com.openggf.game.sonic2.TestTasS2OilHops,com.openggf.game.sonic2.TestTasS2PlatformZip,com.openggf.game.sonic2.kis2.TestTasKis2MonitorMadness,com.openggf.game.sonic2.kis2.TestTasKis2SkyChaseChain,com.openggf.game.sonic2.kis2.TestTasS2StarpostEntry,com.openggf.game.sonic2.objects.TestTasS2ArzBossEndpoint,com.openggf.game.sonic2.objects.TestTasS2CapsuleManipulation,com.openggf.game.sonic2.objects.TestTasS2CapsuleRng,com.openggf.game.sonic2.objects.TestTasS2SupportUnload,com.openggf.game.sonic2.objects.TestTasS2WfzSpindashStorage,com.openggf.game.sonic2.specialstage.TestTasS2JumpLag,com.openggf.game.sonic2.titlescreen.TestTasS2IntroP2Start
```

### Native artifacts and interpretation limits

External durable authoring diagnostics live at `${TAS_USER_ROOT}/scratch/agent-tmp/codex/tas-trick-verdicts-2026-10-26/s2/`: `MonitorProbe.cs`, `SliderProbe.cs`, their provenance JSON, and compressed `monitor-native.csv.gz`, `monitor-native-slow.csv.gz`, `slider-native.csv.gz`. These are bounded state observations, not canonical V5 replay fixtures. Neither observed rows nor native savestates hydrate engine gameplay. The shared BizHawk runtime was read only; producer source/build output is isolated. Native probe source is retained because it precisely declares the authored inlet and controller sequence.

The monitor probe starts from actual normal boot, authors a fresh monitor and valid falling character, acquires the standing latch via native collision, then seeds only legal running momentum before input. Fast and slow controls independently explain why the earlier breaking-monitor inlet was wrong. The slider probe similarly acquires real contact, then Down/A inputs create charge. Native right-facing slider output reaches playerx318 with vx4224/vy-1024 and charge1/counter875; the platform has already taken its first4pixel return step (316), whereas the engine test sees the destination320 on its launch pass. This incidental return-order edge is not a charge-storage failure and was not repaired.

The pause comparison originally failed a legitimate native V-int assertion (8 versus7). The final test retains that exact limitation and exercises release/resume and running controls; it never manually advances V-int or skips a production dispatch. The DEZ/ARZ/underflow/hurt-launcher negative assertions likewise reached independently specified native results before being retained as named incompatibilities. In contrast, the initial jump-lag inequality and intro one-frame expectation were hypotheses without synchronized native windows; their final scope is the exercised unavailable capability, as stated in their family records. No unmeasured native frame delta is claimed.

Worktree safety: main remained untouched; inherited dirty `docs/s1disasm` and `docs/s2disasm` were preserved. No fetch, pull, push, PR, shared ledger, production fix, ROM alias or extra agent was used. Parent owns integration, combined validation, push and worktree cleanup.
