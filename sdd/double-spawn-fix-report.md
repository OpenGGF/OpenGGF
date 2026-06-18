# Boss-Child Double-Spawn Rewind Fix Report

**Branch:** `bugfix/ai-aiz2-rewind-loop-boss`
**Commit:** `800a9341d`
**Date:** 2026-06-18

## Problem

On a held-rewind restore, `ObjectManager.restore()` reconstructs placed/active objects
by calling `registry.create(spawn)` → constructor → `initializeBossState()`. If the
constructor/initializer spawns permanent child objects into `dynamicObjects`, those children
are added once by reconstruction. Then, the dynamic-object codec-restore loop runs — and any
construction-spawned child that ALSO had a `DynamicObjectRewindCodec` registered was added
a second time, doubling the count.

Confirmed doubles (from `double-spawn-verify-report.md`):
- DEZ Death Egg Robot (S2): 10 children → 18 after round-trip (ArticulatedChild×6: 6→12, HeadChild: 1→2, JetChild: 1→2; ForearmChild×2 had no codec so stayed at 2→2)
- S1 SYZ boss SYZBossSpike: 1 → 2
- S1 ScrapEggman ScrapEggmanButton: 1 → 2

## Root Cause Classification

| Child class | Where spawned | Had codec | Action |
|-------------|---------------|-----------|--------|
| `DEZ$ArticulatedChild` ×6 | `initializeBossState()` → `spawnChildren()` | YES (double-spawn bug) | REMOVED codec |
| `DEZ$HeadChild` ×1 | `initializeBossState()` → `spawnChildren()` | YES (double-spawn bug) | REMOVED codec |
| `DEZ$JetChild` ×1 | `initializeBossState()` → `spawnChildren()` | YES (double-spawn bug) | REMOVED codec |
| `DEZ$BombChild` | `fireBombs()` attack routine | YES (correct) | KEPT codec |
| `DEZ$ForearmChild` ×2 | `initializeBossState()` → `spawnChildren()` | NO (no double-spawn; final isFront excluded it) | no change |
| `SYZBossSpike` | `initializeBossState()` → `spawnSpikeChild()` | YES (double-spawn bug) | REMOVED codec |
| `ScrapEggmanButton` | constructor `spawnDynamicObject(button)` | YES (double-spawn bug) | REMOVED codec |
| WFZ family (3 codecs) | `updateSpawnChildren()` update routine | YES (correct) | KEPT codecs |

S3K bosses (AIZ miniboss/end-boss, HCZ end-boss, MHZ end-boss): all children are
routine-spawned from update methods — no double-spawn risk, codecs correct.

S2 MTZ boss: `MTZLaserShooter`/`MTZBossOrb` are construction-spawned BUT have NO codecs
(only `MTZBossLaser` the fired projectile has a codec, and it is routine-spawned) — no fix needed.

## Files Changed

- `src/main/java/com/openggf/game/sonic2/objects/Sonic2ObjectRegistry.java` — removed 3 codecs
- `src/main/java/com/openggf/game/sonic1/objects/Sonic1ObjectRegistry.java` — removed 2 codecs
- `src/test/java/com/openggf/game/rewind/TestBossChildNoDoubleSpawnParity.java` — NEW parity test (3 methods)
- `src/test/java/com/openggf/game/sonic2/objects/TestRewindFixS2InnerBatch2Codecs.java` — updated required list
- `src/test/java/com/openggf/game/sonic1/objects/TestRewindFixS1Batch2Codecs.java` — updated required list
- `src/test/java/com/openggf/game/sonic1/objects/TestRewindFixS1InnerBatch1Codecs.java` — updated required list
- `src/test/resources/rewind/coverage-baseline.txt` — added 5 `#recreate` gap keys
- `docs/KNOWN_DISCREPANCIES.md` — added "Construction-Spawned Boss/Object Children" section
- `CHANGELOG.md` — prepended fix entry

## TDD Cycle

**RED:** `TestBossChildNoDoubleSpawnParity` written first (3 tests). All 3 failed:
DEZ 10→18, SYZ 1→2, ScrapEggman 1→2.

**GREEN:** Removed 5 codecs from the two registries. All 3 tests passed: DEZ 10→10, SYZ 1→1, ScrapEggman 1→1.

## Verification Gate (all PASS)

```
TestBossChildNoDoubleSpawnParity    3/3 PASS
TestBossChildRewindDoubleSpawn      1/1 PASS
TestRewindCoverageGuard             1/1 PASS
TestArchUnitRules                  29/29 PASS
TestAiz2ShipLoopRewindRoundTrip     1/1 PASS
TestAiz2TransientChildRewind        1/1 PASS
TestRewindParityAgainstTrace        1/1 PASS
Total: 37 tests, 0 failures, 0 errors
```

## Principle

Only children spawned from **update/attack routines** need codecs. Children spawned
inside the constructor or `initializeBossState()` are construction-spawned: placed-boss
reconstruction already re-adds them. Adding a codec for such children produces a
guaranteed double every time the boss is restored from a rewind snapshot.

See `docs/KNOWN_DISCREPANCIES.md` section "Construction-Spawned Boss/Object Children:
Re-Established By Reconstruction, No Codec" for the authoritative list.

---

## COMPLETION PASS — EHZ/MTZ/MechaSonic (2026-06-18)

### Audit result (step 1 — definitive)
Construction-spawned children (constructor or `initializeBossState()`) that STILL had a codec, across all of S1/S2/S3K:
- **S2 EHZ boss only.** Its `initializeBossState()` → `spawnChildComponents()` spawns 7 children: `EHZBossVehicleTop`, `EHZBossGroundVehicle`, `EHZBossPropeller`, `EHZBossWheel`×3, `EHZBossSpike` — and all five distinct classes had codecs.
- MTZ (`MTZBossOrb`×7, `MTZLaserShooter`) and Mecha Sonic (`MechaSonicLEDWindow`, `MechaSonicTargetingSensor`, `MechaSonicDEZWindow`) construction children NEVER had codecs → never double-spawned. The session hypothesis that they were "unfixed" was wrong; they were simply codec-free already.
- S3K bosses spawn children from `update()`/camera-lock transitions, NOT construction → none affected (AIZ ship bomb `AizShipBombInstance` is runtime-spawned, keeps codec).
- **No other construction-spawner-with-codec remains** anywhere.

### Empirical RED→GREEN (TestBossChildNoDoubleSpawnParity)
| Boss | Before fix | After fix |
|------|-----------|-----------|
| EHZ  | 7 → **14** (double, FAIL) | 7 → **7** (PASS) |
| MTZ  | static no-codec guard PASS (never had codecs) | PASS |
| MechaSonic | static no-codec guard PASS (never had codecs) | PASS |

### Codecs REMOVED in this pass (S2, EHZ only)
- `ehzBossSpikeCodec` → `EHZBossSpike`
- `ehzBossWheelCodec` → `EHZBossWheel`
- `ehzBossGroundVehicleCodec` → `EHZBossGroundVehicle`
- `ehzBossPropellerCodec` → `EHZBossPropeller`
- `ehzBossVehicleTopCodec` → `EHZBossVehicleTop`
- (plus the now-unused `findEhzBossParentForRewind` helper and 5 unused imports)

### KEPT routine/fired children (correct, NOT touched)
- `MTZBossLaser` (fired in `fireLaser()`)
- `MechaSonicSpikeball` (fired in `fireSpikeballs()` — no codec exists anyway)
- DEZ `BombChild`, WFZ platform family — routine-spawned, codecs retained.

### Baseline + docs
- Added 5 `#recreate` keys (`EHZBoss{GroundVehicle,Propeller,Spike,VehicleTop,Wheel}`) to `coverage-baseline.txt`. No new `#finalScalar`/`#objectRef` gaps. `EHZBossGroundVehicle#finalScalar#initialY` was already baselined.
- Extended `docs/KNOWN_DISCREPANCIES.md` "Construction-Spawned Boss/Object Children" with EHZ + the MTZ/Mecha never-codec'd note.
- Updated `TestRewindFixS2Batch2Codecs`/`TestRewindFixS2Batch3Codecs` to drop their EHZ codec-presence assertions.

### Gate
`mvn -Dmse=off -Ds3k.rom.path=... -Dtest=TestBossChildNoDoubleSpawnParity,TestBossChildRewindDoubleSpawn,TestRewindCoverageGuard,TestArchUnitRules,TestAiz2ShipLoopRewindRoundTrip,TestAiz2TransientChildRewind,TestRewindParityAgainstTrace test`
→ **Tests run: 40, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS.**
