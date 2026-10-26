# Sonic 2 TAS trick test obligations — 2026-10-26

Implementation base: `c50876b184612428ccaf22763412f88ffe505432`.
App-created worktree `t3-b3209dce`, branch
`feature/ai-tas-s2-tests-20261026`. That initial test-only slice left production
unchanged. The scoring-owner follow-up below starts at local commit
`391b671e383e4f329f278aa7b174c39c1bdd1d22` in app-created worktree
`t3-417fb95a`, branch `bugfix/ai-tas-chain-score-20261026`.

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

Current family disposition after the scoring follow-up: **0 exact published-trick
regressions, 12 component-only, 0 unresolved observed scoring mismatches in this
fixture**. All 12 have native route fixtures pending (an outstanding
obligation, not an additional mutually exclusive family count). The initial
test-only slice supplied **21 passing component cases** in five new classes and selected **24 existing passing cases**, including four non-S2 camera
controls in a reused parameterized method. The follow-up restores the sixth N5
case; its focused validation is recorded separately below.

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
| `s2-combo-score` | [3339M](https://tasvideos.org/3339M) / [5385S](https://tasvideos.org/5385S), Sky Chase Knuckles no-jump chain; author claims 372900 maximum. | **Component-only**, N5: the observed stock-S2 normal-score mismatch (kill 16: 1000 instead of native 10000) is corrected by the shared scoring-owner follow-up below. The restored real-enemy regression, early-chain and rebound checks do not certify the SCZ route or KiS2 maximum. | Native KiS2 SCZ entry through final enemy: each defeated slot/species, score award and chain word, jump/glide/landing events, plane standing state and final score. A forced jump/landing control must reset the chain. Stock S2 component scoring cannot certify the claimed KiS2 maximum. |

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

**Initial red, corrected by the follow-up below: expected 10000, actual 1000 at kill 16.** The stock S2 ROM checks the incremented
`Chain_Bonus_counter` against `$20` in `Touch_KillEnemy (s2.asm:85514-85529)`;
`Enemy_Points (s2.asm:85565)` supplies the earlier 100/200/500/1000 progression.
At the test-only baseline, the engine's normal destruction path called
`incrementBadnikChain()`, which stayed at 1000. The separate powered-screen
producer's sixteen-kill behavior did not cover normal touches. This test uses real Nebula destruction through a native-ROM
touch table, verifies every kill, and never invokes the score increment directly.
It is a stock-S2 mismatch; KiS2's exact route maximum remains unverified.

The original test-only slice **did not weaken the diagnostic to expect 1000**
and left production unchanged. It removed this temporary failing method after
recording the failure. The separately authorized scoring follow-up restores the
complete body below to `TestTasS2EnemyContact`, using its unchanged `defeat`
helper and setup, proves it red again against unchanged production, and fixes
the normal-score owner. The command below is now the retained regression selector.

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

In the initial test-only slice, that single-method command was a reproduction
instruction; its observed failure came from the six-case run above. The follow-up
actually executes the single-method red proof, as recorded below.
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
combined integration and broad checks. The original slice contained only S2 tests
and this matrix. The authorized follow-up additionally changes the shared scoring
owner and focused scoring/rewind tests; it does not edit shared ledgers.

## Scoring-owner follow-up — 2026-10-26

The fix belongs in `AbstractPlayableSprite.incrementBadnikChain()`, reached by
ordinary `DestructionEffects` scoring, the S2 Turtloid rider and Rexon head.
`PoweredBadnikScoring` reaches the same captured counter through
`incrementPoweredBadnikChain()`; that method now delegates to the normal owner.
No touch, bounce, destruction, allocation or game-selection behavior changes.

Immutable source proof, using the gitlinks listed at the top of this document
(S1 `28872a46e2d4c94c5c4818cbd3c04e6eb17edb67`, S3K
`8b2cf3c4178f2382f844da67798de4befc50c5e0`, and the S2 pin already recorded):

- S1 `React_BadnikHit (_incObj/Sonic ReactToItem.asm:274-292)` and
  `React_PointsCombo (_incObj/Sonic ReactToItem.asm:317-322)` give
  100/200/500/1000, then 10000 when the updated unsigned word reaches `$20`.
  Invincibility reaches that same routine; S1 has no separate powered-screen
  scoring producer.
- S2 `Touch_KillEnemy (s2.asm:85514-85529)` and
  `Enemy_Points (s2.asm:85565)` use the identical threshold and table.
  Invincibility/spindash/roll enter through `Touch_Enemy (s2.asm:85479-85488)`.
- S3K `Touch_EnemyNormal (sonic3k.asm:20989-21007)` and powered
  `HyperTouch_DestroyEnemy (sonic3k.asm:21403-21427)` use identical arithmetic.
  All these paths add two to a **word**, look up the table using the old
  counter capped at six, then test the incremented counter with unsigned `blo`.
  There is no FixBugs-dependent scoring branch in these routines.

Keep the existing snapshot field's unit as kills, half the native even word.
It now wraps modulo `$8000` kills: the `$FFFE -> $0000` native transition awards
1000 from the **old** capped index, and the following kills award 100/200/500.
The wrap award comes from the old capped index even though the updated counter
is zero. Saturation or an unbounded Java increment would disagree with the ROM.
Both normal and powered paths now use this logic.

Reset ownership is unchanged. S1
`Sonic_ResetOnFloor (_incObj/01 Sonic.asm:1868-1872)`, S2
`Sonic_ResetOnFloor_Part3 (s2.asm:38142-38147)` and
`Tails_ResetOnFloor_Part3 (s2.asm:41033-41044)`, and S3K
`loc_121D8 (sonic3k.asm:24405-24410)`,
`loc_1565E (sonic3k.asm:29198-29203)` and
`loc_17B6A (sonic3k.asm:32894-32899)` clear the native chain on landing.
S2's shipped `fixBugs=0` omits the optional AI-Tails ownership check, so his
landing can clear the shared native chain; the fixed branch restricts that reset.
This bounded arithmetic fix retains the engine's existing per-player captured
counter and separate smash-block `GameStateManager.itemBonus` storage. It does
not establish native P1/P2 or enemy/block shared-counter parity, level-load
lifecycle coverage, or a KiS2 route/maximum-score guarantee.

The restored N5 method is byte-for-byte the preserved method body (indentation
only). `TestBadnikChainScoring` adds nine ROM-free cases over real Sonic players:
normal/powered 1–20 boundaries, unsigned high-word and wrap behavior, explicit
reset, takeoff/native-control-restore retention versus floor-landing reset, and
mixed normal/powered chains in both orders. One new case in
`TestAbstractPlayableSpriteRewindCapture` captures naturally reached kill 15 and
kill 32767, restores/replays normal then powered awards twice, and exercises a
landing/reset between replays. No new snapshot field or format is introduced.

### Follow-up execution record

All commands below ran in `t3-417fb95a` on `391b671e3` plus this follow-up's
uncommitted inputs, with `JAVA_HOME=/usr/lib/jvm/java-25-openjdk` and
`LUA_BIN=/usr/bin/lua5.4`. `${OPENGGF_MAIN}` has the portable meaning above;
the S2 property expanded to the existing original `s2.gen`, CRC32 `7B905383`.
The measurement-hazard table was read before reporting, and each completed
run's exact Surefire identities, assertions and skips were inspected.

1. Unchanged production, restored original method only: **1 test, 1 failure,
   0 errors, 0 skips**, completed `2026-10-26T10:55:04Z`, Maven `1:28`;
   queue wait `0s`. The failure was exactly
   `sixteenthNormalKillAwardsTenThousandNativePoints`: expected 10000, actual
   1000 after all sixteen `defeat` calls verified real Nebula destruction.

   ```bash
   python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.game.sonic2.objects.TestTasS2EnemyContact#sixteenthNormalKillAwardsTenThousandNativePoints' "-Dsonic2.rom.path=${OPENGGF_MAIN}/s2.gen" test
   ```

2. Unchanged production, new owner and rewind regressions: **10 tests,
   6 failures, 0 errors, 0 skips**, completed `2026-10-26T10:56:51Z`, Maven
   `33.538s`; queue wait `0s`. Failed identities in `TestBadnikChainScoring`:
   `normalAndPoweredAwardsRespectTheFifteenSixteenBoundary(false)` (kill 16),
   `wordWrapUsesTheOldWordForTheWrapAwardThenRestartsEarlyAwards(false)`
   (high unsigned word still got 1000), that same method with `true`
   (wrap got 10000 instead of 1000),
   `landingResetsTheChainButTakeoffAndNativeControlRestoreRetainIt(false)`
   (retained chain kill 16), and
   `normalAndPoweredKillsContributeToTheSameChainInEitherOrder`
   (normal kill 16 after powered kills). The sixth failure was
   `TestAbstractPlayableSpriteRewindCapture#rewindReplaysNormalAndPoweredAwardsAcrossBonusAndWordWrapBoundaries`
   at the restored normal kill 16. The two explicit-reset cases, powered
   boundary case and powered landing/control case passed.

   ```bash
   python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.sprites.playable.TestBadnikChainScoring,com.openggf.sprites.playable.TestAbstractPlayableSpriteRewindCapture#rewindReplaysNormalAndPoweredAwardsAcrossBonusAndWordWrapBoundaries' test
   ```

3. Fixed production, complete focused selection: **55 tests passed, 0 failures,
   0 errors, 0 skips**, completed `2026-10-26T10:58:51Z`, Maven `1:34`;
   queue wait `0s`. Exact class counts: N5 `TestTasS2EnemyContact` 6,
   `TestBadnikChainScoring` 9, `TestAbstractPlayableSpriteRewindCapture` 15,
   `TestPoweredScreenAttack` 9, `TestDestructionEffects` 6,
   `TestTurtloidBadnikInstance` 5, `TestPlayableGroundTransitions` 5.
   This includes all ten red/control cases from run 2 and the original ROM-backed
   failing method from run 1. Existing four bounce cases and early-chain awards
   remain unchanged. Only comment/citation formatting and this evidence document
   changed after the completed green run; executable production/tests are unchanged.

   ```bash
   python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.game.sonic2.objects.TestTasS2EnemyContact,com.openggf.sprites.playable.TestBadnikChainScoring,com.openggf.sprites.playable.TestAbstractPlayableSpriteRewindCapture,com.openggf.level.objects.TestPoweredScreenAttack,com.openggf.level.objects.TestDestructionEffects,com.openggf.game.sonic2.objects.badniks.TestTurtloidBadnikInstance,com.openggf.sprites.playable.TestPlayableGroundTransitions' "-Dsonic2.rom.path=${OPENGGF_MAIN}/s2.gen" test
   ```

These are bounded component checks, not smoke/full-suite or movie playback.
The parent owns combined change-based validation, the shared changelog/index
and any shared discrepancy/status updates. The stock-S2 kill-16 mismatch is
resolved; KiS2 SCZ and the native global-counter sharing limits above remain
outside the demonstrated scope. No trace frontier was run or moved.
Consumed Surefire XML/text diagnostics were inspected and removed after each run;
no category-runner diagnostics or child jobs were created.


### Follow-up static checks and inherited citation debt

```bash
python3 tools/disasm/disasm_citations.py check --strict src/test/java/com/openggf/sprites/playable/TestBadnikChainScoring.java src/test/java/com/openggf/game/sonic2/objects/TestTasS2EnemyContact.java docs/architecture/validation/2026-10-26-tas-tricks-s2.md
git diff --cached --check
```

Strict citation checking of the two scoring test files and this matrix passes
with 29 anchored citations (24 `ok`, 5 `near`). Checking all of the changed
production file also reports **11 stale citations and 1 missing-file citation**
elsewhere in that large pre-existing file. A bounded in-memory comparison using
`disasm_citations.py`'s parser/classifier against
`git show 391b671e383e4f329f278aa7b174c39c1bdd1d22:src/main/java/com/openggf/sprites/playable/AbstractPlayableSprite.java`
confirmed the identical failure multiset by kind, target path/range and detail;
none was added or worsened. All four added production scoring citations resolve
and anchor (`ok` or `near`) at the pinned revisions. Those unrelated baseline
citations were left unchanged. The restored original regression body was also
mechanically matched to the preserved fenced body, allowing indentation only.

## Popup consumers and bespoke producers — 2026-10-26 follow-up

Implementation base: `5160b94bb3967f6a52280c6675c8c4a5fc7f2862`, in the same
`t3-417fb95a` worktree and `bugfix/ai-tas-chain-score-20261026` branch. Parent
consumer review identified that correcting the award alone did not establish
correct popup frame selection. The same three immutable disassembly gitlinks
above remain the source authority; dirty main-checkout working files were not read.

S2 `Touch_KillEnemy (s2.asm:85514-85529)` stores the old capped table-byte
offset six for 1000 points and explicitly stores `$A` for 10000. The explosion
`Obj27_InitWithAnimal (s2.asm:46712-46719)` copies that word to the animal.
`Obj28_InitRandom (s2.asm:24628-24635)` copies it into `d0`, shifts it right
once, and stores the result as the new Obj29 `mapping_frame`. Therefore these
are **frame 3 for 1000** and **frame 5 for 10000**, with 100/200/500 using
frames 0/1/2. `Obj29_Init (s2.asm:24918-24926)` installs its mapping pointer
without overwriting that selected frame.
`Map_obj29 (mappings/sprite/obj29.asm:1-8)` lists these entries in the same
order; `Map_obj29_002C (mappings/sprite/obj29.asm:22-25)` is frame 3, and
`Map_obj29_0048 (mappings/sprite/obj29.asm:31-34)` is frame 5. The latter
combines the 100 prefix with the trailing zeroes, not an alternate large 1000.

The requested cross-game check confirmed S1's existing 1000→3 and 10000→5
cases against `Map_Poi_internal (_maps/Points.asm:4-11)`; S1 production needs
no change. S3K also lacked a 10000 case and incorrectly fell back to frame 0.
Its normal and powered producers store the same six/`$A` offsets in
`Touch_EnemyNormal (sonic3k.asm:20989-21007)` and
`HyperTouch_DestroyEnemy (sonic3k.asm:21403-21421)`.
Animal code `loc_2C940 (sonic3k.asm:61121-61128)` performs the same right shift
before creating the `Obj_EnemyScore` frame.
`Map_EnemyScore_ (General/Sprites/Enemy Misc/Map - Enemy Points.asm:1-8)`
lists frame 3 and frame 5 independently of Java's score lookup. The follow-up
therefore corrects S2 1000→3 and 10000→5, and adds only S3K 10000→5. No art,
priority, motion, allocation, chain-counter or P1/P2 behavior is changed.

`TestEnemyChainPointsMapping` supplies explicit native expectations for
100/200/500/1000/10000 in each game, independently exercising both constructor
payload selection and `setScore` replacement. Each of its 30 cases passes the
actual popup through `appendRenderCommands` and checks the frame sent to the
mocked `PatternSpriteRenderer`. These are draw-payload assertions, not rendered
pixel claims. S1 is an unchanged cross-game control; both S2 and S3K have all
five requested score boundaries.

`TestS2ChainScoreConsumers` closes the earlier null-player coverage limitation.
Every case first performs **15 actual Nebula destruction callbacks with a real
player**, checks every destruction and the independent 12800-point preroll sum,
and retires the deliberately unexecuted aftermath between preroll contacts.
It then separately exercises the actual Turtloid rider destruction, the real
parent-created attackable Rexon tip, or a sixteenth ordinary Nebula destruction.
All assert the 10000 score delta and the actual points object's frame-5 draw
payload. The bespoke Turtloid/Rexon paths currently allocate their popup directly;
the ordinary path separately verifies no popup before the animal's first
routine pass and observes the real explosion→animal→points deferred transfer.
The tests use public parent updates to create riders/heads, not reflection or
private counter injection. They remain synthetic contact-entry component tests;
no touch geometry, movie route, native allocation-order parity, pixels, or
P1/P2 counter-sharing guarantee is inferred from them.


### Popup follow-up execution record

The following commands used Java 25 and Lua 5.4 via the same `JAVA_HOME` and
`LUA_BIN` settings recorded above, in `t3-417fb95a` at `5160b94bb` plus these
follow-up inputs. They are ROM-free callback/allocation/draw-payload checks;
no ROM-dependent case was silently omitted. The parent owns the separate
25-class combined run on `506c7b4e` and final integration/S3K gates; this worker
does not duplicate that run.

1. **Red**, unchanged production popup mappings: **33 tests, 9 failures,
   0 errors, 0 skips**, completed `2026-10-26T11:15:30Z`, Maven `1:34`;
   queue wait `148s` (separate from execution).

   ```bash
   python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.level.objects.TestEnemyChainPointsMapping,com.openggf.level.objects.TestS2ChainScoreConsumers' test
   ```

   `TestEnemyChainPointsMapping`: 30 cases, six failures. Both constructor and
   `setScore` methods failed at CSV row 9 (S2 1000: expected frame 3, actual 5),
   row 10 (S2 10000: expected 5, actual 0), and row 15
   (S3K 10000: expected 5, actual 0). The other 24 cases passed, including all
   ten S1 controls and the lower S2/S3K awards.
   All three `TestS2ChainScoreConsumers` methods reached their final draw-payload
   assertion after passing every preroll destruction, the 12800 score control,
   the actual sixteenth destruction and the 10000 score delta. They each failed
   expected frame 5 versus actual frame 0:
   `turtloidRiderSixteenthKillAwardsTenThousandAndDrawsFrameFive`,
   `rexonTipSixteenthKillAwardsTenThousandAndDrawsFrameFive`, and
   `ordinarySixteenthKillCarriesBonusThroughDeferredAnimalIntoPopup`.
   The ordinary case also passed its no-popup-before-animal checks.


2. **Green**, corrected S2/S3K popup mappings: **39 passed, 0 failures,
   0 errors, 0 skips**, completed `2026-10-26T11:17:39Z`, Maven `1:33`;
   queue wait `2s`. The exact 33 new cases from the red run all passed, along
   with all six existing `TestS3kEnemyScorePresentation` priority/header controls.
   Class counts: `TestEnemyChainPointsMapping` 30,
   `TestS2ChainScoreConsumers` 3, `TestS3kEnemyScorePresentation` 6.
   No executable source/test changed after this completed run.

   ```bash
   python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.level.objects.TestEnemyChainPointsMapping,com.openggf.level.objects.TestS2ChainScoreConsumers,com.openggf.level.objects.TestS3kEnemyScorePresentation' test
   ```

The completed Surefire XML was checked by class and method/invocation identity,
including all requested native boundary controls and all three reached producer
paths. Consumed XML/text reports were removed after each run. No category run,
broad smoke, full suite, rendering context, or additional agent was launched.
The measurement hazards were reread for source-state attribution, zero skips,
actual producer reach and the distinction between draw payload and pixels.

```bash
python3 tools/disasm/disasm_citations.py check --strict src/main/java/com/openggf/game/sonic2/objects/PointsObjectInstance.java src/main/java/com/openggf/game/sonic3k/objects/Sonic3kPointsObjectInstance.java src/test/java/com/openggf/level/objects/TestEnemyChainPointsMapping.java docs/architecture/validation/2026-10-26-tas-tricks-s2.md
git diff --cached --check
```

Both checks pass; strict citation checking reports **44 anchored citations**
(33 `ok`, 11 `near`) across the two changed popup implementations, new mapping
regressions and this matrix. This follow-up does not alter the earlier
production scoring file or its separately attributed baseline citation debt.
Parent delivery still owns shared changelog/index/discrepancy updates, integration
and final-source S3K gates. The earlier 25-class job is evidence for its earlier
tested source; it is not claimed as coverage for these popup edits.
