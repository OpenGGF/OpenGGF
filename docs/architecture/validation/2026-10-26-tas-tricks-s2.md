# Sonic 2 TAS trick test obligations — 2026-10-26

Implementation base: `c50876b184612428ccaf22763412f88ffe505432`.
App-created worktree `t3-b3209dce`, branch
`feature/ai-tas-s2-tests-20261026`. Production code is unchanged.

The completed audit is the census authority, not a new research pass:
`${TAS_AUDIT_DIR}/audit.json`
and `s2.json` (original source audit `99b16240df435f6034880aef29f9271ec8d7f659`).
Both identify the same **12 partial families** below. Publication links and the
saved submission notes identify the requested scenarios; native disassembly
supplies the component expectations. All native citations use immutable S2 pin
`34fc7f130a96db171e1a1c43e28e0bad81cd6ca7`, read with `git show` from the main
checkout's submodule object database. Its dirty working files were not used.

## Meaning of coverage

An **exact regression** would execute the identified publication mechanism from
an established entry through its observable exit, with a native oracle and a
boundary control. None of these twelve *whole families* is certified that way.
**Component-only evidence** means a narrower production branch is exercised;
passing it cannot establish an exploit trajectory. **Native fixture pending**
names the missing route recording/setup/oracle. **Observed mismatch** requires an
executed reproducer against a native expectation, not a source suspicion.

Final family disposition: **0 exact published-trick regressions, 11 component-only,
1 observed mismatch**. All 12 have native route fixtures pending (an outstanding
obligation, not an additional mutually exclusive family count). Five new classes
provide **21 passing component cases**; **24 existing cases** were selected and
passed, including four non-S2 camera controls in a reused parameterized method.

The new tests exercise public object updates, oil contacts, the real switcher
controller, results dispatch, and ROM-backed enemy touch dispatch. They seed
explicit native component entry states. They do not feed recorded physics into
the engine, compare output to itself, or claim full movie synchronization.

## Exhaustive family matrix

Every partial audit ID appears once in this matrix. `N` selectors are new;
`E` selectors reuse existing assertions, with exact names below.

| Stable audit ID | Publication / submission and scope | Evidence and current disposition | Concrete native fixture still required |
|---|---|---|---|
| `s2-monitor-momentum` | [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S); also 3339M/5385S and 3584M/5735S. EHZ1 Knuckles, monitor madness. | **Component-only**, E1: recorded stock-S2 acceleration and monitor-break windows. This is not the high-speed roll-to-run conversion. | KiS2 EHZ1 approach to the monitor used in 5385S around author movie frame 2084 (0:27), including preroll, monitor slot/standing latch, center/subpixel position, inertia, x/y velocity, rolling/animation and collision path. Exit must show loss/reacquisition of support, running state and retained speed. Compare one-pixel/subpixel edge offsets that stay rolling or break the box normally. |
| `s2-zips` | [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S), HTZ1 and MTZ2 Sonic; later CNZ/ARZ/MCZ/MTZ variants remain separate. | **Component-only**, E2: native push-release animation branches. No zip entry or large displacement is established. | First bounded fixture: 1198S HTZ1 Sonic platform/terrain entry. Capture exact platform slot/routine, terrain path, angle, radii, position fractions and input through first ejection and regained control; compare a nonpenetrating approach. Separate fixtures are required for screw, crusher, monitor, flipper and loop entries; one contact branch cannot certify them all. |
| `s2-spindash-storage` | [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), HTZ seesaw; [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S), WFZ slider. | **Component-only**, N1: native ball descent actually launches the rider, retains flag/counter under `fixBugs=0`, and preserves horizontal velocity/inertia. Uncharged and no-tilt controls. | Capture KiS2 HTZ charge input, standing latch, ball velocity/position and plank frame before launch, then airborne charge preservation through landing/release. Exit oracle includes released inertia and camera delay. WFZ slider is a distinct object/control sequence and needs its own recording; seesaw success cannot cover it. |
| `s2-oil-hops` | [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S), [1779M](https://tasvideos.org/1779M) / [3101S](https://tasvideos.org/3101S), [2037M](https://tasvideos.org/2037M) / [3557S](https://tasvideos.org/3557S), OOZ1/2 Sonic; later KiS2 variants. | **Component-only**, N2 and E3: both-sign fast landings preserve x velocity and copy it to running inertia, preserve subpixels, and respect the exact contact window; existing release/hurt/boss-lowered controls. | Record consecutive OOZ1 oil hops through the under-pillar exit, including input releases, submersion, slide chunk, headroom, radii, all velocity components and landed state. Separately capture OOZ2 boss-boundary entry with camera bounds and oil Y changing to `$2D8`; assert the claimed vertical reposition and recovery. No hop timing or upward warp is inferred here. |
| `s2-camera-streaming` | [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S), WFZ duck/barrier; [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), delayed obstacles and ending. | **Component-only**, E4: S2 grounded-roll camera reset (the existing selector also includes S1/S3K cases). | Native 320×224 WFZ barrier approach, camera X/Y/bias/delay, barrier spawn/slot lifetime, exact Down/neutral timing. Assert barrier unload before camera return and successful passage; neutral-camera control retains obstruction. Separate fixtures for look-up boss entry, animal clearance and early WFZ end, with the corresponding boss/transition clock. |
| `s2-capsule-rng` | [3038M](https://tasvideos.org/3038M) / [4932S](https://tasvideos.org/4932S), [3325M](https://tasvideos.org/3325M) / [5373S](https://tasvideos.org/5373S), animal/explosion manipulation. | **Component-only**, E5: capsule broken-piece countdown and allocation order, including restore/replay. No RNG optimization demonstrated. | First fixture: KiS2 EHZ2 final boss hit through capsule completion, native RNG seed and every RNG-consuming explosion/animal, V-int and executed-frame clocks, pauses, camera and occupied slots. Assert final live animal departure and results-entry tick against neutral/pause-shifted control. Preserve other-act obligations separately. |
| `s2-boss-optimization` | [1779M](https://tasvideos.org/1779M) / [3101S](https://tasvideos.org/3101S), ARZ2 endpoint defeat; 1198S/3557S DEZ and P2 hit timing. | **Component-only**, E6: actual attacking touch decrements HTZ boss HP and reflects velocity. No ARZ explosion skip or DEZ schedule certified. | ARZ2 Sonic final hit with HP=1 at the documented hammer endpoint, boss routine/phase/position and animation timer, both players' coordinates/velocity and touch order. Assert next routine, explosion count and escape/results clock versus one tick before/after. Separate DEZ held-jump/ceiling final-hit and MCZ early-P2 fixtures. |
| `s2-path-switch` | [4682M](https://tasvideos.org/4682M) / [7453S](https://tasvideos.org/7453S), [5640M](https://tasvideos.org/5640M) / [8629S](https://tasvideos.org/8629S), CPZ2/HTZ2 Sonic; 5412M/8376S KiS2 limitation. | **Component-only**, N3 and E7: missed/airborne crossings consume the side latch, half-open span boundaries, real recrossing positive control; CPZ ROM dual-table evidence. | CPZ2 three-clip or HTZ2 badnik-assisted bypass, exact marker placement/subtype and previous-side latch, top/LRB bits, priority, geometry and input. Exit must traverse the intended wall/loop on the retained path. Include normal crossing and Knuckles' unavailable third CPZ2 clip as distinct controls; do not silently substitute Sonic for KiS2. |
| `s2-ring-attack` | [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), Knuckles 5163/5174-ring route. | **Component-only**, E8: stock ring hitbox and KiS2 CNZ placement counts with the actual lock-on image. No maximum-ring guarantee. | Start with KiS2 OOZ1 Aquis-assisted fourth monitor at author frames 104310–104573; record per-ring/monitor identities and count deltas through collection/recovery, then the distinct OOZ/MTZ buried-ring entries. Full totals require a per-act ledger, respawn/recollection policy and all missed-ring controls. The endlessly respawning OOZ monitor was explicitly not farmed in the notes. |
| `s2-special-stage-tally` | [5412M](https://tasvideos.org/5412M) / [8376S](https://tasvideos.org/8376S), KiS2 minimum required rings. | **Component-only**, N4 and E9: executes the real exhausted tally transition and tests surplus-ring savings above the emerald floor; separate P1/P2 parallel countdowns already covered. | KiS2 special stage 1 final-checkpoint requirement, exact collected P1/P2 counts and player mode, got-emerald/emerald-count/perfect flags at result entry. Record native results-entry through exit with V-int/lag clocks; compare an otherwise identical successful surplus-ring route. Test samples here are controlled counts, not publication minimums or KiS2 presentation proof. |
| `s2-damage-bounce` | [600M](https://tasvideos.org/600M) / [1198S](https://tasvideos.org/1198S), intentional damage and ceiling rebounds; [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), enemy rebounds. | **Component-only**, N5 and E3/E6: real enemy contact chooses above/equal/below/rising rebound branches without changing horizontal momentum; existing hurt-oil and boss reflection. | 1198S DEZ ceiling-assisted final jump: exact hit/ceiling/input-release sequence and resultant landing/next-hit clock. For injury, record the selected spike/enemy entry with rings/shield and hurt routine through recovery. KiS2 ring-attack rebound does not itself prove intentional injury; its OOZ1 Aquis approach needs the separate route fixture above. |
| `s2-combo-score` | [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), Sky Chase Knuckles no-jump chain; author claims 372900 maximum. | **Observed mismatch in stock-S2 normal scoring**, shared producer used by this family: actual kill 16 gives 1000 points instead of native 10000. N5 retains passing early-chain and rebound checks; it does not certify the full chain. | Native KiS2 SCZ entry through final enemy: each defeated slot/species, score award and chain word, jump/glide/landing events, plane standing state and final score. A forced jump/landing control must reset the chain. Stock S2 component scoring cannot certify the claimed KiS2 maximum. |

## Selected executable coverage

Class prefixes below are fully qualified; `#` separates a method selector.

| Key | Exact selector(s) | Scope |
|---|---|---|
| N1 | `com.openggf.game.sonic2.objects.TestTasS2StoredSpindash` | Three cases; seeded pre-contact seesaw SST, real ball update. |
| N2 | `com.openggf.game.sonic2.TestTasS2OilMomentum` | Seven cases; native landing velocity transfer and five contact controls. |
| N3 | `com.openggf.game.sonic2.objects.TestTasS2PathSwitch` | Five cases; object dispatch and real controller, no terrain route. |
| N4 | `com.openggf.game.sonic2.objects.TestTasS2SpecialStageTally` | One test, five count scenarios, each executes the empty transition. |
| N5 | `com.openggf.game.sonic2.objects.TestTasS2EnemyContact` | ROM touch table, real Nebula, scoring and rebound; no autoscroller route. |
| E1 | `com.openggf.tests.trace.s2.TestS2Ehz1MonitorBreakRegression#monitorEdgeContactPreservesRecordedAccelerationBeforeBreakWindow+monitorBreaksOnRecordedFrame` | Existing non-TAS trace from level entry to frames 2921 and 2954. |
| E2 | `com.openggf.level.objects.TestSolidObjectManager#sonic2SolidPushReleasePreservesRollAnimation+sonic2SolidPushReleasePublishesNativeWalkRunAnimationWord` | Push-release state, not terrain ejection. |
| E3 | `com.openggf.tests.TestOilSurfaceManager#jumpReleaseClearsOilSupport+jumpReleaseTicksSubmersionBeforeClearingSupport+hurtLandingOnOilKeepsRoutineFourUntilHurtStop+deadFallingSidekickDoesNotLandOnBossLoweredOilSurface` | Support release, hurt recovery ownership and dead-player rejection. |
| E4 | `com.openggf.tests.TestGroundedRollCameraBias#groundedRollUsesTheRetailCameraResetBranch` | Six parameterized cases, two per game; only the two S2 cases support this matrix. |
| E5 | `com.openggf.game.sonic2.objects.TestEggPrisonNativeAllocation#lowerBrokenPieceKeepsItsFullTimerOnTheArmingPass+laterBrokenPieceCountsDownOnTheArmingPass+lowerComponentGraphRecreatesAndReplaysTheArmingBoundaryTwice` | Exact capsule scheduling controls, no RNG sequence. |
| E6 | `com.openggf.tests.TestHTZBossTouchResponse#attackingTouchResponseDamagesBossAndAppliesBossBounce` | Native boss hit/rebound. |
| E7 | `com.openggf.tests.TestTodo2_DualCollisionAddresses#testCpzHasDualCollisionPaths` | Actual ROM collision tables, not just a flag setter. |
| E8 | `com.openggf.tests.TestRingManager#testS2StageRingDuckBoxUsesMappingFrameInsteadOfCrouchingState`, `com.openggf.game.sonic2.kis2.TestKis2PlacementOracle#withTheLockOnDumpCasinoNightResolvesFromTheChipWithTheCatalogueCounts` | Ring overlap and lock-on object placements. |
| E9 | `com.openggf.game.sonic2.objects.TestSonic2SpecialStageResultsTallyCadence` | Three existing count/score checks; their helper adds the empty pass arithmetically, hence N4 executes it explicitly. |

## Native expectations and setup limits

- N1: `Obj14_Ball_Fly (s2.asm:47667-47725)` advances the seeded descending
  `$600` velocity by gravity `$38` before launching at `-$638`.
  `Obj14_LaunchCharacter (s2.asm:47732-47747)` preserves x velocity, inertia and
  charge. Shipped `fixBugs=0` omits the conditional charge clear. The seeded
  contact has a real standing rider and ball crossing, rather than directly
  calling a flag setter or private launch method. Approach and release are pending.
- N2: `Obj07_Main (s2.asm:50145-50203)` supplies submersion to
  `PlatformObject_ChkYRange (s2.asm:35968-35984)` and
  `RideObject_SetRide (s2.asm:35986-36040)`. Falling distance `-16..-1`
  lands, zero/`-17` and ascending velocity reject; x velocity becomes inertia.
  Both `$C00` and `-$C00` are tested without clamping to running speed. No
  OilSlides chunk trajectory or boss event is manufactured.
- N3: `Obj03_MainX (s2.asm:45682-45761)` latches a changed side before checking
  span and grounded-only status. A missed crossing cannot be repaired by merely
  reentering the span; a new crossing is needed. Marker initialization at exact
  equality is a separate untested source concern, not a measured failure here.
- N4: `SpecialStage (s2.asm:6537-6788)`, result writes at lines 6782-6788, loads player counts
  and 1000 emerald units. `Obj6F_TallyScore (s2.asm:28376-28424)` drains both
  ring counts by one and emerald units by ten in parallel, then advances only on
  the already-empty invocation: `max(P1, P2, emerald ? 100 : 0) + 1`.
- N5: `Touch_KillEnemy (s2.asm:85514-85565)` awards chain points and changes
  only y velocity on rebound: rising adds `$100`, falling above negates, equal
  or below subtracts `$100`. It does not force an airborne transition. The
  fixture supplies native ROM collision sizes, runs real player touch dispatch,
  verifies destruction and retires each dead body between synthetic contacts,
  replacing the later object-loop cleanup intentionally omitted from this fixture.

## Execution record

The repository forbids committing machine-local home paths. In the portable
command spellings below, `OPENGGF_MAIN` is the absolute original main-checkout
path, and `TAS_AUDIT_DIR` is the absolute directory of the supplied completed
`tasvideos-trick-audit-2026-10-26` audit. The recorded runs used those expanded
absolute paths. Set these two shell variables to the existing directories on the
host; do not create ROM copies or links. Original filenames and verified hashes:

| Property | Original path | Identity |
|---|---|---|
| `sonic2.rom.path` | `${OPENGGF_MAIN}/Sonic The Hedgehog 2 (W) (REV01) [!].gen` (also existing `s2.gen`) | CRC32 `7B905383`; SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| `s3k.rom.path` | `${OPENGGF_MAIN}/s3k.gen` | CRC32 `63522553`; SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` |
| `kis2.rom.path` | `${OPENGGF_MAIN}/kis2.gen` | CRC32 `2AC1E7C6`; SHA-1 `6CD0537A3AEE0E012BB86D5837DDFF9342595004` |

N1–N3 are ROM-free. N4/N5/E1/E7/E9 require S2. E8's placement check also
requires S3K and KiS2; supplying only S2 would silently skip that obligation.
No display/rendering is launched. Maven's Java-25 build guard ran. Parent owns
combined validation; no smoke/full suite is requested by this worktree.

Initial focused run at base plus N1–N4: 16 cases, 0 failures, 2 errors, 0 skips;
the two launch cases lacked the parent object's injected services. Corrected
fixture, not production. Subsequent N1/N5 run: 9 cases, 2 failures, 0 errors,
0 skips; N1 passed, all four bounce cases passed, both repeated-kill checks
stopped at kill two because this isolated touch fixture had omitted the later
object-loop retirement of the destroyed first enemy. A bounded diagnostic rerun
confirmed pass 2, animation 2, rolling true, hurt false, score 100. The fixture
now explicitly retires that dead body after verifying destruction.

Recorded commands, with only the portable path substitution described above (all on base `c50876b1` plus the scoped
tests; final code is the commit containing this document):

```bash
# Initial N1–N4 run: 16 cases, 0 failures, 2 fixture errors, 0 skips.
# N2 (7), N3 (5) and N4 (1) passed; their executable code did not change afterward.
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.game.sonic2.TestTasS2OilMomentum,com.openggf.game.sonic2.objects.TestTasS2StoredSpindash,com.openggf.game.sonic2.objects.TestTasS2PathSwitch,com.openggf.game.sonic2.objects.TestTasS2SpecialStageTally' "-Dsonic2.rom.path=${OPENGGF_MAIN}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test

# Corrected N1 (3 passed) plus developing N5 (4 passed, 2 fixture failures):
# total 9 cases, 2 failures, 0 errors/skips. N1 executable code is final here.
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.game.sonic2.objects.TestTasS2StoredSpindash,com.openggf.game.sonic2.objects.TestTasS2EnemyContact' "-Dsonic2.rom.path=${OPENGGF_MAIN}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test

# Bounded two-method diagnostic: 2 failures at contact pass 2, 0 errors/skips.
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.game.sonic2.objects.TestTasS2EnemyContact#consecutiveGroundedKillsRetainTheFirstFourNativeComboAwards+sixteenthNormalKillAwardsTenThousandNativePoints' "-Dsonic2.rom.path=${OPENGGF_MAIN}/s2.gen" test

# Corrected contact fixture WITH the temporary kill-16 diagnostic below:
# 6 cases, 5 passed, 1 genuine native mismatch, 0 errors/skips, at 10:35:20 UTC.
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.game.sonic2.objects.TestTasS2EnemyContact' "-Dsonic2.rom.path=${OPENGGF_MAIN}/s2.gen" test

# Final N5 (5 cases) plus E1–E9 (24 cases): 29 passed, 0 failures/errors/skips,
# completed 10:40:00 UTC. Maven 36.332s; queue wait 29s reported separately.
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.game.sonic2.objects.TestTasS2EnemyContact,com.openggf.tests.trace.s2.TestS2Ehz1MonitorBreakRegression#monitorEdgeContactPreservesRecordedAccelerationBeforeBreakWindow+monitorBreaksOnRecordedFrame,com.openggf.level.objects.TestSolidObjectManager#sonic2SolidPushReleasePreservesRollAnimation+sonic2SolidPushReleasePublishesNativeWalkRunAnimationWord,com.openggf.tests.TestOilSurfaceManager#jumpReleaseClearsOilSupport+jumpReleaseTicksSubmersionBeforeClearingSupport+hurtLandingOnOilKeepsRoutineFourUntilHurtStop+deadFallingSidekickDoesNotLandOnBossLoweredOilSurface,com.openggf.tests.TestGroundedRollCameraBias#groundedRollUsesTheRetailCameraResetBranch,com.openggf.game.sonic2.objects.TestEggPrisonNativeAllocation#lowerBrokenPieceKeepsItsFullTimerOnTheArmingPass+laterBrokenPieceCountsDownOnTheArmingPass+lowerComponentGraphRecreatesAndReplaysTheArmingBoundaryTwice,com.openggf.tests.TestHTZBossTouchResponse#attackingTouchResponseDamagesBossAndAppliesBossBounce,com.openggf.tests.TestTodo2_DualCollisionAddresses#testCpzHasDualCollisionPaths,com.openggf.tests.TestRingManager#testS2StageRingDuckBoxUsesMappingFrameInsteadOfCrouchingState,com.openggf.game.sonic2.kis2.TestKis2PlacementOracle#withTheLockOnDumpCasinoNightResolvesFromTheChipWithTheCatalogueCounts,com.openggf.game.sonic2.objects.TestSonic2SpecialStageResultsTallyCadence' "-Dsonic2.rom.path=${OPENGGF_MAIN}/s2.gen" "-Ds3k.rom.path=${OPENGGF_MAIN}/s3k.gen" "-Dkis2.rom.path=${OPENGGF_MAIN}/kis2.gen" test
```

These are **45 distinct passing cases across focused runs**, not a single combined
45-case run. Initial fixture errors remain part of the record. The red scoring
diagnostic is an additional case, not counted as passing. No full movie, broad
smoke or full suite was run; no TASVideos publication synchronization is claimed.
The measurement hazards in `docs/agent-workflow/briefing-trace-rounds.md` were
consulted: explicit ROM identities, reached-state assertions, completed-run totals
and zero skips were checked. FAST-load-manifest warnings concern an unmeasured
timing mode; these tests make no loading/lag-time claim. Ring sound was not tested.

## Observed normal-enemy score mismatch

**Expected 10000, actual 1000 at kill 16.** The stock S2 ROM checks the incremented
`Chain_Bonus_counter` against `$20` in `Touch_KillEnemy (s2.asm:85514-85529)`;
`Enemy_Points (s2.asm:85565)` supplies the earlier 100/200/500/1000 progression.
The engine's normal destruction path calls `incrementBadnikChain()`, which stays
at 1000. The separate powered-screen producer's sixteen-kill behavior does not
cover normal touches. This test uses real Nebula destruction through a native-ROM
touch table, verifies every kill, and never invokes the score increment directly.
It is a stock-S2 mismatch; KiS2's exact route maximum remains unverified.

The failing diagnostic was **not weakened to expect 1000** and production was not
changed. It was a temporary investigation, removed from the normal regression
class after recording the failure so this test-only task does not introduce an
always-red suite. Its complete body is preserved here: insert it into
`TestTasS2EnemyContact` (it uses that class's unchanged `defeat` helper and setup),
then use the focused command immediately below. This explicit diagnostic remains
red until the normal-score owner is fixed in a separately authorized task.

```java
@Test
void sixteenthNormalKillAwardsTenThousandNativePoints() {
    int before = 0;
    for (int i = 1; i <= 16; i++) {
        before = services.gameState().getScore();
        defeat(i);
    }
    assertEquals(10000, services.gameState().getScore() - before,
            "Touch_KillEnemy compares the incremented Chain_Bonus_counter against $20");
}
```

```bash
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.game.sonic2.objects.TestTasS2EnemyContact#sixteenthNormalKillAwardsTenThousandNativePoints' "-Dsonic2.rom.path=${OPENGGF_MAIN}/s2.gen" test
```

That final single-method command is a reproduction instruction, not a claim that
it was separately executed: the observed failure came from the six-case run above.
Passing early-chain tests do not discharge this mismatch or the pending SCZ
jump/landing/reset, KiS2 and maximum-score obligations.

## Static checks and delivery boundary

The staged matrix was mechanically compared with the audit: all 12 partial IDs
occur exactly once as table rows. `git diff --cached --check` passed.
`python3 tools/disasm/disasm_citations.py check --strict` over the five new Java
files and this document passed with 20 anchored citations at the pinned revision.
Final Surefire XML inspection (retained latest per-class reports from the separate
runs, not a new combined run) confirmed all 45 retained cases and zero skips.
No category-runner jobs or diagnostics were created. Consumed direct-Maven
Surefire XML/text reports are removed after inspection; the worktree remains for
the parent's integration/cleanup.

The parent owns shared changelog, discrepancy and central validation-index edits,
combined integration and broad checks. This branch contains only S2 tests and this
matrix. In particular any new mismatch below must be carried into the parent's
shared discrepancy record; this worker does not edit it or change production to
force a green run.
