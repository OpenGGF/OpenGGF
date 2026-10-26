# Sonic 1 TAS partial-family test obligations — 2026-10-26

Implementation base: `c50876b184612428ccaf22763412f88ffe505432`.
Local branch: `feature/ai-tas-s1-tests-20261026`.
Worktree: the isolated checkout bound to this task (reported in the handoff).
Scope: tests and this S1 matrix only; no production changes.

The completed audit read all 11 S1 publications and their author notes. This
implementation consumes its 15 partial-family IDs; it does not repeat the census.
Source audit: `$AUDIT_DIR/s1.json` (`AUDIT_DIR` is the completed audit directory supplied in the task brief)
and `audit.json`. The audit was source inspection, not a fresh test result.
Publication/submission links below identify author intent; disassembly supplies
mechanism expectations. No test here claims full movie synchronization.

## Dispositions and scope

The matrix has **15 distinct families: 3 exact local regressions, 9 component-only
evidence, 3 native-fixture-pending, and 0 observed engine mismatches**. An exact
local regression qualifies the explicitly stated setup, not every publication
using that family. Every row names the remaining publication-level obligation.
A component pass must not promote its pending route to verified.

All new assertions exercise production behavior. Movement enters rolling through
Down and executes actual frame steps. Special-stage contacts go through complete
`update()` calls; only initial layout/player state is constructed. Push-block
contacts use the actual ObjectManager checkpoint; only external terrain responses
are controlled. Damage tests call real hurt, timer and spike owners. They do not
prove ring-loss allocation or hazard geometry; the existing LZ3 test covers an
actual spike encounter separately.

| Stable audit ID | Disposition and executed evidence | Concrete remaining native fixture obligation |
|---|---|---|
| `S1_REVERSE_START` | **Exact local regression**, M1–M3: GHZ1 flat centre `(384,940)`, Sonic, zero inertia; Left then Right gives `-$C → $80`; combined Left+Right gives `$80` versus right-only `$C`; `$80` left-braking stops at zero while `-$80` right-braking produces `$80`. | [795M/1437S](https://tasvideos.org/1437S), [1066M/1847S](https://tasvideos.org/1847S), [1401M/2457S](https://tasvideos.org/2457S): capture the native first controllable GHZ1 frame, exact sequential/simultaneous pad words, initial fractions and held/pressed state through a fixed downstream X crossing. Compare native VBlank, executed updates and crossing time before claiming the reported start saving. Resolve 795's conflicting revision labels. |
| `S1_ROLL_JUMP` | **Exact local regression**, M4: flat GHZ1 terrain, entry inertia `$200`, Down initiates roll; jump restores radii `9/19`, retains centre Y and horizontal position on launch, sets `-$680`, and locks left steering. Standing-jump control uses `7/14`, adds five to centre Y, and accelerates left by `$18` on the next air tick. | [1948M/3402S](https://tasvideos.org/3402S), [3570M/5702S](https://tasvideos.org/5702S): record terrain versus solid-object launch, especially SYZ3 boss blocks and the FZ final hit. Capture object slot, standing flags, centre/radii/mapping, launch/first-air boundaries and boss collision time. Larger hitbox reach is not itself a centre-position write or proof of a three-frame boss saving. |
| `S1_ROLL_REJUMP` | **Exact local regression**, M5: actual roll-jump and landing on flat GHZ1; landing reaches standing centre `floorY−5`, clears rolling/roll-jump, then a fresh jump launches five pixels above the delayed-jump control. | [1948M/3402S](https://tasvideos.org/3402S): record the cited MZ3/SYZ3 boss approaches from last airborne tick through landing, fresh pad edge, next floor correction and target collision. Match attack timing and boss phase; do not infer either fight from GHZ1 alone. |
| `S1_DAMAGE` | **Component-only evidence**, D1–D4 plus E1: dry/wet source-side recoil including equality, inertia cancellation, acquired flash timer, second-hit rejection, ringless/shield outcomes, last flash tick and retail spike exception. E1 reaches an LZ3 ceiling spike with rings and asserts survival. | All 11 audited S1 publications declare damage use. For [447M/907S](https://tasvideos.org/907S), capture REV00 MZ1 lava entry before the hit with ring count, source slot, centre/fractions, routine, shield and clock; follow recoil through landing/obstacle exit. Compare ring-count controls to resolve the author's bounce-direction observation. Other routes need their own reached hazard and exit anchors. |
| `S1_PUSH_BLOCK` | **Component-only evidence**, P1–P3 and E2: actual Obj33 checkpoint at horizontal overlap 2 and vertical penetration 4/5; expected player displacement `+1/−1`, block `+1`, inertia `$40`, X velocity zero, airborne cases, preserved fractions, wrong-facing and wall-distance controls. E2 checks only final player X at MZ2 credits trace row 341. | [447M/907S](https://tasvideos.org/907S) elevated MZ3 block and [783M/1383S](https://tasvideos.org/1383S) enemy-assisted MZ1 block both cite REV00. Capture the last pre-contact player/block/enemy slot state, collision penetration, native object order and input, through block crossing its destination edge. Compare ordinary pushing at matched entry/exit. A REV01 primitive is not REV00 route proof. |
| `S1_OBJECT_PHASE` | **Component-only evidence**, E3: the real flapping-door closing animation reaches and holds fully closed frame zero. | [447M/907S](https://tasvideos.org/907S) MZ1 delayed jumps before the emerging wall platform: native capture starts before the first delay, includes spawn/load camera bounds, platform/chandelier slots and V_int clock, and ends at successful chandelier landing. Add zero-delay/one-frame-offset controls. For [783M/1383S](https://tasvideos.org/1383S) LZ1 first rising block, capture standing/sink offset and step-off/re-entry through maximum rise. SYZ moving hazards require separately anchored phase captures. |
| `S1_EJECTION` | **Component-only evidence**, P1–P3 distinguish preliminary solid separation from Obj33's push; E4 reaches a real SLZ staircase side obstruction, and E5 checks monitor top landing after upward bounce. These do not assert a published wall entry. | [1937M/3378S](https://tasvideos.org/3378S) explicitly permits ejections in its no-zips route; [2410M/3994S](https://tasvideos.org/3994S) describes a 64-pixel downward displacement. Capture each SBZ2/SBZ3/FZ door/platform/crusher or falling-monitor entry with object slot/pre-update position, signed overlap, player position and velocity before/after correction. Exit assertion is separation/displacement, not invented zip speed. Include a one-pixel non-overlap control. |
| `S1_ZIP` | **Native fixture pending**. No executed selector here asserts an embedded-terrain zip. Existing probe-orientation tests are deliberately not counted as zip evidence. | [447M/907S](https://tasvideos.org/907S) SBZ1 duck-on-platform entry then Left-to-right zip is a concrete first fixture; add [1948M/3402S](https://tasvideos.org/3402S) cave/loop variants and LZ3 cork entry separately. Capture just before embedding through first sustained exit, centre/fractions, ground mode/angle, wall probe distances, velocity, inertia and camera. Negative controls: nonembedded state and opposite/no input. Assert native signed penetration response and subsequent movement, not a generic 16–32 px/frame assumption. |
| `S1_COG` | **Native fixture pending**. E6 verifies junction subpixel preservation only; mapping author “cog/wheel” language to this object remains a candidate. | [2410M/3994S](https://tasvideos.org/3994S) SBZ2 cog launch into a wall and [3570M/5702S](https://tasvideos.org/5702S) first-wheel forward clip: capture before wheel engagement, identify native object ID/slot/subtype/phase, then record release input, stick-to-convex/on-object state, velocities and wall crossing. Exit is first clear far-side position with clock. Adjacent-frame release control must distinguish carry, launch, ejection and subsequent zip. |
| `S1_BOSS` | **Component-only evidence**, E7/E8: FZ combat contact uses frame-start boss position; SLZ killing edge contact precedes movement and preserves fractions. | [3570M/5702S](https://tasvideos.org/5702S) FZ final rolling hit: capture the penultimate-hit exit through the final hit/descent/end trigger, including boss routine, HP, invulnerability timer, velocities and player radii. Compare the stated three-frame-later hit against ordinary jump. GHZ/MZ/SYZ/SLZ schedules require their own slot/HP/clock anchors; a contact-order pass is not a cadence proof. |
| `S1_CAPSULE_RNG` | **Component-only evidence**, E9: released capsule checks animals immediately and scans native slots 1–63. | [1066M/1847S](https://tasvideos.org/1847S) SLZ3 eight-frame capsule delay, [1401M/2457S](https://tasvideos.org/2457S), [1948M/3402S](https://tasvideos.org/3402S) and [2629M/4348S](https://tasvideos.org/4348S): capture deterministic initial RNG, native occupied slots and pause/Start edges before release through last counted animal departure and end-act transition. Record both VBlanks and active updates. Compare delayed/no-delay/pause controls; a seed alone does not establish the published animal schedule. |
| `S1_WATER` | **Native fixture pending**. Audit-cited `WaterPhysicsTest` recoil/constants tests use a Sonic 2 fixture; they are not counted as fresh S1 route coverage. D1 verifies S1 underwater recoil only. | [447M/907S](https://tasvideos.org/907S) REV00 LZ3 final-survivable water exit: capture before last ascent with air counter/subtimer, water height, routine and fractions, through dry-state transition or drowning. Compare one-frame later. [783M/1383S](https://tasvideos.org/1383S) LZ2 delayed exit jump needs water-level phase and earlier-jump control. [1066M/1847S](https://tasvideos.org/1847S) air routing remains independently unqualified. |
| `S1_POWERUPS` | **Component-only evidence**, E5's monitor activation control and D4's real invincibility grant/expiry versus spike contact. Neither is the published route. | [1937M/3378S](https://tasvideos.org/3378S): SYZ1 speed-shoes pickup and LZ2 invincibility route. Record before monitor contact through grant and the affected acceleration/hazard crossings, with monitor type, animation/mapping, native timer and input. Include pickup miss and expiry-boundary controls; verify actual speed/hazard response, not only a power-up flag. |
| `S1_SPECIAL_COLLISION` | **Component-only evidence**, S1–S4: public special-stage update scans all four cells, later solid wins, non-solids do not overwrite; side-only contact grounds and permits a fresh A/B/C jump next update. Airborne-frame press, held-only and non-solid controls reject a jump. E10 checks existing response/animation contracts with injected contacts. | [5121M/7988S](https://tasvideos.org/7988S): notes do not identify precise shortcut cells. For each claimed shortcut, capture stage number, native maze cells, angle/rotation, position/fractions, velocity/inertia, contact ID/order and pad edges from one update before the exploited contact through next unobstructed movement/emerald/goal. Native stage 1–6 committed traces are other captures; no publication-specific shortcut is certified by their presence. |
| `S1_CAMERA_END` | **Component-only evidence**, E11/E12: FZ escape writes held/pressed input correctly, branches/clamps at ledge, and optional hit publishes damaged cue. | [3570M/5702S](https://tasvideos.org/5702S) SBZ camera wait after zip and final input cutoff, plus [447M/907S](https://tasvideos.org/907S) LZ2 sign/bonus-tag end: record player/camera positions and bounds, signpost/end-object slot, final input edge and finish/results transition. Assert offscreen trigger admission and completion clock against native capture. Saving active gameplay frames does not establish shorter movie length. |

## Native oracle and test entry contracts

Immutable reference pin: `docs/s1disasm` at
`28872a46e2d4c94c5c4818cbd3c04e6eb17edb67`; read using
`git -C "$MAIN_WORKSPACE/docs/s1disasm" show <pin>:<path>`.
The main reference checkout was dirty and was not modified. Shipped `FixBugs=0`
is the oracle throughout.

- **Movement:** `Sonic_MoveLeft` / `Sonic_MoveRight` (`_incObj/01 Sonic.asm:636-755`) distinguish subtract borrow from add carry.
  `Sonic_Jump` (`_incObj/01 Sonic.asm:1205-1264`) writes standing radii before
  the rolling branch, adds five to Y only on the ordinary jump branch, and
  skips the remainder of the grounded mode. `Sonic_JumpDirection` (`_incObj/01 Sonic.asm:939-967`) skips steering for roll-jump.
  `Sonic_ResetOnFloor` (`_incObj/01 Sonic.asm:1839-1868`) unconditionally
  subtracts five in its rolling branch. Tests assert reached roll/air/landing
  states, not setter effects. Initial inertia is an explicit scenario seed.
- **Damage:** `HurtSonic` (`_incObj/Sonic ReactToItem.asm:375-414`) specifies
  protection, recoil, unsigned source-side comparison, inertia and 120-frame
  flash timer. `Sonic_Display` (`_incObj/01 Sonic.asm:142-147`) decrements
  before touch response. Spike callbacks exercise the retail immunity exception
  after a supplied solid-side contact; the LZ3 integration assertion independently
  reaches real geometric contact.
- **Push:** `Solid_Collision` / `Solid_SideAir` (`_incObj/sub SolidObject.asm:193-248`) precedes
  `PushB_SolidAction_NotOnPlatform` (`_incObj/33 MZ, LZ Pushable Blocks.asm:352-405`). A two-pixel horizontal
  overlap and vertical penetration four versus five distinguish no separation
  from `−2`; Obj33 then adds one to both X positions. Tests observe the real
  checkpoint before the push as well as final positions, flags and fractions.
- **Special stage:** `SonicSS_FindWall` / `SonicSS_FindWall_CheckType` (`_incObj/09 Sonic in Special Stage.asm:521-583`) scan TL/TR/BL/BR and retain
  the last solid. `SonicSS_ChkBumper` (`_incObj/09 Sonic in Special Stage.asm:780-808`) uses `−$700`; at a `(24,24)`
  offset the native `$B5` sine/cosine gives `−1267` on both axes. The swapped
  wall suppresses bounce; later `$28/$3A` non-solids retain the earlier bumper.
  `SonicSS_Jump` (`_incObj/09 Sonic in Special Stage.asm:294-318`) and
  `SonicSS_Fall` (`_incObj/09 Sonic in Special Stage.asm:442-503`) establish
  dispatch-before-contact and the side-only ground state. At angle zero, the
  next fresh jump ends with `−$680 + $2A = −$656` Y velocity. Startup completes
  through normal updates before constructing the controlled layout; no
  `lastCollision*` result is injected.

The special-stage layout is an explicit synthetic branch fixture with real ROM
initialization. It neither substitutes runtime assets nor supplies comparison
rows as gameplay state. Actual publication routes require native captures.

## Exact selectors

Each alias below is an exact Maven selector, not a filename-presence claim.

```text
M1 com.openggf.tests.TestTasS1Movement#oneLeftFrameThenRightUsesBrakingInsteadOfOrdinaryAcceleration
M2 com.openggf.tests.TestTasS1Movement#simultaneousDirectionsHaveLeftThenRightOrder
M3 com.openggf.tests.TestTasS1Movement#brakingZeroBoundaryRetainsNativeBorrowCarryAsymmetry
M4 com.openggf.tests.TestTasS1Movement#jumpUsesNativeRadiiAndOnlyRollingLaunchLocksAirSteering
M5 com.openggf.tests.TestTasS1Movement#rollingJumpLandingHasOneFrameElevatedRejumpWindow
D1 com.openggf.game.sonic1.TestTasS1Damage#nativeRecoilUsesSourceSideIncludingEqualityAndWater
D2 com.openggf.game.sonic1.TestTasS1Damage#zeroRingsDiesButShieldAllowsTheSameContactToRecoil
D3 com.openggf.game.sonic1.TestTasS1Damage#finalFlashTickExpiresBeforeOrdinaryTouchResponse
D4 com.openggf.game.sonic1.TestTasS1Damage#retailSpikesBypassRecoveredFlashButRespectInvincibility
P1 com.openggf.game.sonic1.objects.TestTasS1PushBlock#heightChangesPlayerDisplacementButAirDoesNotPreventObj33Push
P2 com.openggf.game.sonic1.objects.TestTasS1PushBlock#oppositeFacingRejectsObj33PushAfterTheRealSolidCheckpoint
P3 com.openggf.game.sonic1.objects.TestTasS1PushBlock#negativeWallDistanceRejectsObj33PushAfterTheRealSolidCheckpoint
S1 com.openggf.game.sonic1.specialstage.TestTasS1SpecialStageContacts#lastSolidOfFourCellsOwnsBumperResponse
S2 com.openggf.game.sonic1.specialstage.TestTasS1SpecialStageContacts#sideContactWithoutFloorAllowsFreshJumpOnFollowingUpdate
S3 com.openggf.game.sonic1.specialstage.TestTasS1SpecialStageContacts#airborneContactFramePressIsNotBufferedAndHeldJumpDoesNotRetrigger
S4 com.openggf.game.sonic1.specialstage.TestTasS1SpecialStageContacts#emptyAndNonSolidCellsCannotCreateTheSideContactJumpWindow
E1 com.openggf.tests.TestS1SpikeDoubleHit
E2 com.openggf.game.sonic1.TestS1PushBlockSideContact
E3 com.openggf.game.sonic1.objects.TestSonic1LabyrinthObjectsBasic#flappingDoorClosingAnimationHoldsFullyClosedFrame
E4 com.openggf.game.sonic1.objects.TestSonic1StaircaseWallCollision
E5 com.openggf.tests.TestSonic1MonitorObjectInstance#monitorTopContactStillLandsWhenPlayerWasBouncedUpward+touchFromAboveRequiresRollAnimationNotJustRollingStatus
E6 com.openggf.game.sonic1.objects.TestSonic1JunctionSubpixelPreservation
E7 com.openggf.game.sonic1.objects.bosses.TestSonic1FzBossCombatContact#combatSolidUsesFrameStartBossPositionBeforeCylinderSlotsMoveParent
E8 com.openggf.game.sonic1.objects.bosses.TestS1SlzSpikeballNativeContact#killingEdgeContactHappensBeforeMovementAndPreservesBothFractions
E9 com.openggf.game.sonic1.objects.TestSonic1EggPrisonObjectInstance#endActChecksForAnimalsImmediatelyWithoutPrototypeDelay+releasedEndActBugScansOnlyNativeSlotsOneThroughSixtyThree
E10 com.openggf.game.sonic1.specialstage.Sonic1SpecialStageManagerTest#testHeldJumpActionMakesGroundedSonicAirborneWithJumpForceVelocity+testGlassBlockUsesAnimationCooldownBeforeAdvancingState+testUpDownBlockOnlyFlipsWhenItActuallyChangesRotation+testRBlockPlaysTouchedAnimationWhileReversingRotation
E11 com.openggf.game.sonic1.objects.bosses.TestS1FzEscapeNativeInput
E12 com.openggf.game.sonic1.objects.bosses.TestSonic1FzBossEscapeHitCue#escapeHitClearsCollisionFlagsAndEnablesDamagedCue
```

## Execution record

All runs used the worktree above, unchanged production at `c50876b1` plus the
new tests, `JAVA_HOME=/usr/lib/jvm/java-25-openjdk`, and the focused Maven queue.
Measurement hazards were read before reporting. Counts include parameterized
invocations. ROM-backed selections used the original
`$MAIN_WORKSPACE/s1.gen` (CRC32 `AFE05EEE`). The other original
`Sonic The Hedgehog (W) (REV01) [!].gen` was independently read and verified:
524288 bytes, CRC32 `AFE05EEE`, SHA-1
`69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`. No ROMs were copied or linked.
Damage and push-block components are ROM-free. Movement, special-stage and the
reused ROM-backed tests require REV01. There was no desktop rendering.

Commands and completed outcomes follow. To keep machine-local home paths out of
the repository, the original checkout prefix is represented by `MAIN_WORKSPACE`.
Set it to the absolute main-checkout path containing the original ROM; all other
arguments and selectors below match the executed commands. `JAVA_HOME` is the
actual launch setting. This normalization does not mean the ROM was relocated.

1. `JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.tests.TestTasS1Movement' "-Dsonic1.rom.path=$MAIN_WORKSPACE/s1.gen" test`
   initially ran 11 cases, all failing the **setup** angle assertion (expected
   zero, actual `$FC`). No trick assertion was reached. The default GHZ1 start
   was rejected as a flat-floor fixture; no assertion was weakened.
2. `JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.tests.TestTasS1TerrainProbe,com.openggf.game.sonic1.TestTasS1Damage' "-Dsonic1.rom.path=$MAIN_WORKSPACE/s1.gen" test`
   passed 10 cases: 9 damage regressions and one temporary bounded terrain probe;
   zero failures/errors/skips. The probe established angle-zero floor at
   `(384,940)` and was removed after use. It is not counted as retained coverage.
3. `JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.tests.TestTasS1Movement,com.openggf.game.sonic1.specialstage.TestTasS1SpecialStageContacts' "-Dsonic1.rom.path=$MAIN_WORKSPACE/s1.gen" test`
   passed **22 cases** (11 movement, 11 special-stage), zero failures/errors/skips.
4. The focused existing-coverage command below passed **28 cases**, zero
   failures/errors/skips. The MZ2 test's only trace equality is final centre X
   at row 341; its historical prose claiming earlier-row checks is not used
   to expand this result's coverage.
5. `JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.game.sonic1.objects.TestTasS1PushBlock' test`
   passed **8 cases**, zero failures/errors/skips.

Retained executed coverage totals **67 cases: 39 new and 28 existing**, all
passing with zero skips. The temporary probe is excluded. These completed runs
qualify the final test-source contents; subsequent edits affect this prose only.
The matrix/selector completeness check passes, `git diff --cached --check` is
clean, and `python3 tools/disasm/disasm_citations.py check src/test/java/com/openggf/game/sonic1/objects/TestTasS1PushBlock.java docs/architecture/validation/2026-10-26-tas-tricks-s1.md --strict` verifies all 15 citations.

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.tests.TestS1SpikeDoubleHit,com.openggf.game.sonic1.TestS1PushBlockSideContact,com.openggf.game.sonic1.objects.TestSonic1LabyrinthObjectsBasic#flappingDoorClosingAnimationHoldsFullyClosedFrame,com.openggf.game.sonic1.objects.TestSonic1StaircaseWallCollision,com.openggf.tests.TestSonic1MonitorObjectInstance#monitorTopContactStillLandsWhenPlayerWasBouncedUpward+touchFromAboveRequiresRollAnimationNotJustRollingStatus,com.openggf.game.sonic1.objects.TestSonic1JunctionSubpixelPreservation,com.openggf.game.sonic1.objects.bosses.TestSonic1FzBossCombatContact#combatSolidUsesFrameStartBossPositionBeforeCylinderSlotsMoveParent,com.openggf.game.sonic1.objects.bosses.TestS1SlzSpikeballNativeContact#killingEdgeContactHappensBeforeMovementAndPreservesBothFractions,com.openggf.game.sonic1.objects.TestSonic1EggPrisonObjectInstance#endActChecksForAnimalsImmediatelyWithoutPrototypeDelay+releasedEndActBugScansOnlyNativeSlotsOneThroughSixtyThree,com.openggf.game.sonic1.objects.bosses.TestS1FzEscapeNativeInput,com.openggf.game.sonic1.objects.bosses.TestSonic1FzBossEscapeHitCue#escapeHitClearsCollisionFlagsAndEnablesDamagedCue,com.openggf.game.sonic1.specialstage.Sonic1SpecialStageManagerTest#testHeldJumpActionMakesGroundedSonicAirborneWithJumpForceVelocity+testGlassBlockUsesAnimationCooldownBeforeAdvancingState+testUpDownBlockOnlyFlipsWhenItActuallyChangesRotation+testRBlockPlaysTouchedAnimationWhileReversingRotation' "-Dsonic1.rom.path=$MAIN_WORKSPACE/s1.gen" test
```

No broad smoke/full-suite or exhaustive trace sweep ran in this worker. Parent
owns combined integration/validation. New classes are test-only changes. Passing
selected assertions leave the matrix's native capture obligations unresolved.

## Findings and rejected shortcuts

- No observed production mismatch was established by the completed assertions.
  The initial movement failures were invalid terrain setup, not engine failures.
- Shared/S2 physics assertions were not promoted to S1 exploit coverage. Existing
  special-stage response tests with injected contact fields were retained as
  component evidence; new tests obtain contact through normal update dispatch.
- Position ejection and zip are separate obligations: SolidObject separation
  changes position, whereas terrain zip requires native penetration response,
  velocity and subsequent movement. Neither the staircase test nor a probe
  orientation test proves a zip.
- A source-level ceiling concern remains **unexecuted**: shipped `FixBugs=0`
  skips `Sonic_WallSpeedAdjust` at exact ceiling angle `$80`
  (`Sonic_WallSpeedAdjust`, `_incObj/01 Sonic.asm:540-551`), while the shared
  ground-wall routine admits cardinal angles. Call-path reach and an exact
  native setup must be established before classifying an observed mismatch.
- Publication 447/783 setups are REV00. Publication 795 has conflicting revision
  provenance. REV01 local passes do not resolve either issue.
- The temporary terrain diagnostic was removed. No failing, empty, disabled or
  TODO placeholder tests were introduced; no production constants were changed
  to fit the tests. Raw diagnostic logs stay temporary under `target/` and are
  removed after their summaries are consumed.
