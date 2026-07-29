# S3K Super Emeralds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use
> `subagent-driven-development` or `executing-plans`; use
> `test-driven-development`, `s3k-disasm-guide`, `s3k-implement-object`, and
> `s3k-plc-system` for the relevant tasks. Every behavior step is RED before
> GREEN.

**Goal:** Deliver the ROM-accurate MHZ giant-ring → Hidden Palace sanctuary →
indexed Special Stage → Super Emerald progression loop and complete Hyper
Sonic, Super Tails, and Hyper Knuckles behavior.

**Architecture:** A canonical S3K four-state emerald adapter owns durable
progression. A game-agnostic typed Special Stage request carries optional forced
stage/reward semantics. HPZ is an S3K nonlinear ROM resource profile with
injected-service sanctuary objects. Character-specific form presentation and
abilities hang from an explicit S3K form tier.

**Tech Stack:** Java 21, Maven, JUnit 5, Mockito only where production
collaborators cannot be used, ROM-backed S3K loaders, runtime PLC/palette/object
frameworks, rewind snapshots/codecs.

**Baseline:** `mvn -Dmse=off test` on `next`/`beb7b64c1` is already red with
unrelated bootstrap-delegation guards, stale rewind inventories, object tests,
and compatibility rows. Focused tests must be green; final sweep results must be
compared against this baseline rather than treated as feature regressions.

---

## File ownership map

| Task | Primary ownership |
|---|---|
| 1 | `GameStateManager`, S3K progression adapter, save/rewind tests |
| 2 | Special Stage request/result contracts, coordinator, `GameLoop`, provider/manager |
| 3 | HPZ descriptor/registry/resource loading and loading tests |
| 4 | Giant-ring routing and transition return state |
| 5 | HPZ sanctuary controller/pedestals/teleporter objects and focused tests |
| 6 | HPZ Master Emerald/palette/art/PLC presentation |
| 7 | Explicit form tier and character-correct transformation presentation |
| 8 | Hyper Sonic effects, Super Flickies, Hyper Knuckles effects |
| 9 | End-to-end integration, rewind guards, documentation, regression sweep |

Tasks 1-3 establish shared contracts. Tasks 4, 6, and the research portion of 7
may then proceed in parallel. Task 5 depends on 1-4. Task 8 depends on 7. Task 9
depends on all implementation tasks.

## Task 1: Canonical four-state S3K emerald progression

**Files:**

- Create: `src/main/java/com/openggf/game/sonic3k/S3kEmeraldProgression.java`
- Modify: `src/main/java/com/openggf/game/GameStateManager.java`
- Modify: `src/main/java/com/openggf/game/rewind/snapshot/GameStateSnapshot.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/dataselect/S3kSaveSnapshotProvider.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/dataselect/S3kSavePayload.java`
- Test: `src/test/java/com/openggf/game/sonic3k/TestS3kEmeraldProgression.java`
- Test: `src/test/java/com/openggf/game/TestGameStateManager.java`
- Test: `src/test/java/com/openggf/game/TestGameStateRewindSnapshot.java`

- [ ] **Step 1: RED — define the ROM state-domain tests.**

  Add tests that construct mixed state `[0,1,1,0,1,0,3]`, assert Chaos count is
  every nonzero entry, Super count is state 3 only, and assert conversion
  changes only state 1:

  ```java
  S3kEmeraldProgression p = S3kEmeraldProgression.restore(
          state, List.of(0, 1, 1, 0, 1, 0, 3), false);
  assertEquals(List.of(0, 1, 1, 0, 1, 0, 3), p.states());
  assertTrue(p.beginSanctuaryConversion());
  assertTrue(p.isConverted());
  assertEquals(2, p.convert(1));
  assertEquals(3, p.awardSuper(6));
  ```

- [ ] **Step 2: Verify RED.**

  Run:

  ```bash
  mvn "-Dtest=TestS3kEmeraldProgression,TestGameStateManager,TestGameStateRewindSnapshot" test
  ```

  Expected: compilation failure because `S3kEmeraldProgression` does not exist.

- [ ] **Step 3: GREEN — add the adapter and preserve public compatibility.**

  Implement:

  ```java
  public enum EmeraldState {
      ABSENT(0), CHAOS(1), GRAY_SUPER(2), SUPER(3);
  }

  public boolean beginSanctuaryConversion() {
      if (states.stream().noneMatch(s -> s == EmeraldState.CHAOS)) return false;
      gameState.setEmeraldsConverted(true);
      return true;
  }
  ```

  Keep `hasEmerald`, `hasSuperEmerald`, list payloads, and explicit
  `emeraldsConverted` readable by existing consumers. Normalize save payloads
  so state 2 or 3 implies conversion, while zero-Super converted saves remain
  representable.

- [ ] **Step 4: Verify GREEN and rewind/save round trips.**

  Run the command from Step 2 and
  `mvn "-Dtest=TestS3kSaveSnapshotProvider,TestSaveManager" test`.

- [ ] **Step 5: Commit Task 1 with policy trailers.**

## Task 2: Typed exact-stage and reward contract

**Files:**

- Create: `src/main/java/com/openggf/game/SpecialStageEntryRequest.java`
- Create: `src/main/java/com/openggf/game/EmeraldRewardKind.java`
- Modify: `src/main/java/com/openggf/level/LevelTransitionCoordinator.java`
- Modify: `src/main/java/com/openggf/level/LevelManager.java`
- Modify: `src/main/java/com/openggf/level/objects/ObjectServices.java`
- Modify: `src/main/java/com/openggf/level/objects/DefaultObjectServices.java`
- Modify: `src/main/java/com/openggf/game/SpecialStageProvider.java`
- Modify: `src/main/java/com/openggf/GameLoop.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/specialstage/Sonic3kSpecialStageProvider.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/specialstage/Sonic3kSpecialStageManager.java`
- Test: `src/test/java/com/openggf/TestGameLoopSpecialStageEntryRequest.java`
- Test: `src/test/java/com/openggf/game/sonic3k/specialstage/TestSonic3kSuperEmeraldConversion.java`

- [ ] **Step 1: RED — test ordinary and exact-stage request consumption.**

  Specify:

  ```java
  var request = new SpecialStageEntryRequest(4, EmeraldRewardKind.SUPER_EMERALD);
  coordinator.requestSpecialStageEntry(request);
  assertEquals(request, coordinator.consumeSpecialStageEntryRequest());
  assertNull(coordinator.consumeSpecialStageEntryRequest());
  ```

  Add a `GameLoop` seam test proving ordinary requests use provider cursor
  selection, forced requests use their exact index, and successful S3K reward
  publication happens once.

- [ ] **Step 2: Verify RED** with the two focused test classes.

- [ ] **Step 3: GREEN — implement the generic contract.**

  Use:

  ```java
  public record SpecialStageEntryRequest(
          Integer forcedStageIndex,
          EmeraldRewardKind rewardKind) {
      public static SpecialStageEntryRequest ordinary() {
          return new SpecialStageEntryRequest(null, EmeraldRewardKind.CHAOS_EMERALD);
      }
  }
  ```

  Replace the boolean coordinator flag with a nullable request while retaining
  the existing ordinary convenience method. `GameLoop` selects the forced index
  when present and delegates award ownership to the provider/manager instead of
  unconditionally calling `markEmeraldCollected`.

- [ ] **Step 4: Verify GREEN**, including S1/S2 request compatibility and S3K
  debug completion.

- [ ] **Step 5: Commit Task 2.**

## Task 3: Nonlinear HPZ level descriptor and ROM resources

**Files:**

- Create: `src/main/java/com/openggf/game/sonic3k/Sonic3kSublevelDescriptor.java`
- Modify: `src/main/java/com/openggf/level/LevelData.java` only if the existing
  descriptor interface cannot carry HPZ metadata
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kZoneRegistry.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3k.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kConstants.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kPlcArtRegistry.java`
- Modify: S3K level resource profile/plan owners discovered during RED
- Test: `src/test/java/com/openggf/game/sonic3k/TestSonic3kHpzLevelLoading.java`
- Test: `src/test/java/com/openggf/game/sonic3k/TestSonic3kLevelLoading.java`
- Test: `src/test/java/com/openggf/game/sonic3k/TestSonic3kBootstrapResolver.java`

- [ ] **Step 1: RED — register canonical zone `0x16`, act 1.**

  Assert its descriptor reports canonical HPZ identity but resource word
  `$1701`, SKL zone set, start/camera `(0x15A0,0x0240)`, X/Y boundaries, LRZ2
  music, HPZ layout/chunk/block/art/palette sources, and PLC `0x48`.

- [ ] **Step 2: Verify RED** with `TestSonic3kHpzLevelLoading`.

- [ ] **Step 3: GREEN — implement typed nonlinear resource resolution.**

  The descriptor API must expose semantic fields:

  ```java
  public record Sonic3kSublevelDescriptor(
          int levelIndex, int startX, int startY,
          int romZone, int romAct, S3kZoneSet zoneSet,
          int minX, int maxX, int minY, int maxY) implements LevelDescriptor {}
  ```

  Resource planners consume `romZone/romAct`; gameplay, events, title cards, and
  saves continue to observe canonical HPZ. Do not add `if (zone == HPZ)` in
  shared level code.

- [ ] **Step 4: Verify GREEN** with HPZ, general S3K loading, bootstrap resolver,
  PLC mapping-shape, and corruption-guard tests using the discovered ROM path.

- [ ] **Step 5: Commit Task 3.**

## Task 4: ROM giant-ring sanctuary routing and origin snapshot

**Files:**

- Modify: `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kSSEntryRingObjectInstance.java`
- Modify: `src/main/java/com/openggf/level/BigRingReturnState.java` or add an
  S3K-owned sanctuary return record if Saved2 state cannot be represented
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kLevelEventManager.java`
- Test: `src/test/java/com/openggf/game/sonic3k/objects/TestSonic3kSSEntryRingFormation.java`
- Test: `src/test/java/com/openggf/game/sonic3k/TestS3kSanctuaryReturnState.java`

- [ ] **Step 1: RED — replace disabled-route assertions with ROM matrix tests.**

  Cover negative subtype unconditional HPZ route; positive subtype S&K-side
  route only with seven Chaos and incomplete Super; S3-side, SK-alone, completed
  Super, ordinary Special Stage, 50-ring reward, physical ring bit, and time
  attack.

- [ ] **Step 2: Verify RED** and confirm failures are the hardcoded
  `hiddenPalaceRouteAvailable() == false` path.

- [ ] **Step 3: GREEN — port `SSEntry_CheckLevel` and flash-completion timing.**

  Routing happens after the existing flash/`Save_Level_Data2` sequence, not
  directly in collision:

  ```java
  boolean sanctuary = subtype < 0
          || (isLockedOnSkLevel && state.getEmeraldCount() == 7
              && !state.hasAllSuperEmeralds());
  ```

  Preserve S3/FBZ classification, bit-index masking, player/camera lock timing,
  starpost sentinel, origin snapshot, and physical ring collection.

- [ ] **Step 4: Verify GREEN** with ring, water/resize return, and rewind tests.

- [ ] **Step 5: Commit Task 4.**

## Task 5: Sanctuary controller, pedestals, teleporter, and re-entry

**Files:**

- Create: `src/main/java/com/openggf/game/sonic3k/objects/HPZSSEntryControlObjectInstance.java`
- Create: `src/main/java/com/openggf/game/sonic3k/objects/HPZSuperEmeraldObjectInstance.java`
- Create: `src/main/java/com/openggf/game/sonic3k/objects/SSZHPZTeleporterObjectInstance.java`
- Create: `src/main/java/com/openggf/game/sonic3k/S3kSanctuaryRuntimeState.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`
- Modify: `src/main/java/com/openggf/tools/Sonic3kObjectProfile.java`
- Modify: gameplay runtime/rewind registration owner
- Test: `src/test/java/com/openggf/game/sonic3k/objects/TestHpzSanctuaryObjects.java`
- Test: `src/test/java/com/openggf/game/sonic3k/TestHpzSanctuaryRewind.java`

- [ ] **Step 1: RED — test the controller state machine.**

  Prove the intro signal, scan order `[5,3,1,0,2,4,6]`, exact flag timing,
  signed countdown boundaries, sequential `1 -> 2`, zero-emerald exit, mixed
  states, and no replay on re-entry.

- [ ] **Step 2: RED — test pedestal selection.**

  State 0 deletes; state 1/2 renders gray but only state 2 is selectable after
  conversion; state 3 renders colored and is inert. Standing on a selectable
  top for the native fifteen-frame delay requests its exact subtype and
  `SUPER_EMERALD`.

- [ ] **Step 3: Verify RED** with both focused classes.

- [ ] **Step 4: GREEN — implement objects with injected services.**

  Use native centre positions from the ROM altar table, `SolidObjectTop`,
  `ObjectControlState`, `NativePositionOps`, and normal spawn helpers. Capture
  routine, timers, subtype, positions, child roles, selected stage, intro signal,
  and origin/re-entry state. Relink parents after rewind settles.

- [ ] **Step 5: GREEN — implement success/failure return and centre exit.**

  Success calls `awardSuper(selectedIndex)` exactly once; failure leaves state
  2. Both set HPZ re-entry context. Centre exit restores Saved2/origin state only
  when no state 1 or 2 remains and teleporter readiness permits it.

- [ ] **Step 6: Verify GREEN**, object registration, real ObjectManager rewind
  round trip, and rewind coverage guards.

- [ ] **Step 7: Commit Task 5.**

## Task 6: HPZ ROM art, Master Emerald, and palette ownership

**Files:**

- Create: `src/main/java/com/openggf/game/sonic3k/objects/HPZMasterEmeraldObjectInstance.java`
- Create: `src/main/java/com/openggf/game/sonic3k/objects/HPZPaletteControlObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kConstants.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtKeys.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArt.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kPlcArtRegistry.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`
- Test: `src/test/java/com/openggf/game/sonic3k/TestS3kHpzPaletteOwnershipModel.java`
- Test: `src/test/java/com/openggf/game/sonic3k/TestSonic3kPlcArtRegistry.java`
- Test: `src/test/java/com/openggf/game/sonic3k/objects/TestHpzSanctuaryPresentation.java`

- [ ] **Step 1: RED — use RomOffsetFinder/intake to pin all S&K-half sources.**

  Add exact frame/piece/tile-shape tests for Master Emerald, small emeralds, and
  teleporter mappings plus PLC `0x48`. Include signed backward mapping pointers
  when present.

- [ ] **Step 2: Verify RED** against the real S3K ROM.

- [ ] **Step 3: GREEN — add ROM-only art and mappings.**

  Use existing `S3kPaletteOwners.HPZ_MASTER_EMERALD`,
  `HPZ_PALETTE_CONTROL`, and `HPZ_ZONE_CYCLE`. Target-palette writes must
  materialize through the fade lifecycle before their first visible frame.

- [ ] **Step 4: GREEN — port Master Emerald glow and camera-X palette swap.**

  Preserve the `$460` threshold, palette destinations, timers, mapping frames,
  and controller-owned spawn order.

- [ ] **Step 5: Verify GREEN** with focused presentation tests, the complete S3K
  art crawler, and `TestPatternSpriteRendererCorruptionGuard`.

- [ ] **Step 6: Commit Task 6.**

## Task 7: Explicit S3K form tier and character-correct presentation

**Files:**

- Create: `src/main/java/com/openggf/game/sonic3k/S3kFormTier.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kSuperStateController.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kPlayerArt.java`
- Modify: `src/main/java/com/openggf/sprites/playable/SuperState.java` only if
  the existing public state cannot expose the tier without an S3K provider
- Modify: relevant player palette/art providers
- Test: `src/test/java/com/openggf/game/sonic3k/TestSonic3kSuperTransformationEligibility.java`
- Test: `src/test/java/com/openggf/game/sonic3k/TestSonic3kFormPresentation.java`
- Test: S3K super-state rewind test

- [ ] **Step 1: RED — add the complete eligibility/tier matrix.**

  Cover Sonic/Knuckles Chaos-before-conversion = `SUPER`,
  Chaos-after-conversion = none, partial Super = none, seven Super = `HYPER`;
  primary Tails seven Super = `SUPER_TAILS`; sidekick Tails = none.

- [ ] **Step 2: RED — prove character-correct resources.**

  Transform each character and assert it does not receive Super Sonic's
  renderer/animation/palette. Capture/restore every tier through rewind and
  verify normal reversion restores the same character.

- [ ] **Step 3: Verify RED.**

- [ ] **Step 4: GREEN — implement tier selection and presentation strategies.**

  `Sonic3kSuperStateController` owns tier lifecycle; per-character presentation
  helpers own ROM art/animation/palette selection. Do not infer active tier on
  every render from mutable emerald counts.

- [ ] **Step 5: Verify GREEN**, including underwater palettes, ring drain,
  boss/miniboss forced reversion, save restoration, and rewind.

- [ ] **Step 6: Commit Task 7.**

## Task 8: Hyper Sonic, Super Flickies, and Hyper Knuckles

**Files:**

- Create: S3K effect/support object classes under
  `src/main/java/com/openggf/game/sonic3k/objects/`
- Modify: `src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kSuperStateController.java`
- Modify: S3K ROM art/constants/registry files required by the object families
- Test: `src/test/java/com/openggf/game/sonic3k/objects/TestHyperSonicEffects.java`
- Test: `src/test/java/com/openggf/game/sonic3k/objects/TestSuperTailsFlickies.java`
- Test: `src/test/java/com/openggf/game/sonic3k/objects/TestHyperKnucklesEffects.java`
- Test: real ObjectManager rewind round-trip coverage

- [ ] **Step 1: RED — Hyper Sonic exact behavior.**

  Test directional dash velocity/second-press gate, history positions, camera
  delay, SFX, flash lifetime, trail/star spawn cadence, cleanup, allocation
  failure, and rewind.

- [ ] **Step 2: GREEN — port Hyper Sonic effects**, using ROM-backed mappings
  and role-complete recreation metadata; verify focused tests.

- [ ] **Step 3: RED — four Super Flickies.**

  Test exact four-child orbit phases, P1 Tails ownership, closest eligible
  target policy, attack/return phases, damage authority, no-target behavior,
  partial allocation, cleanup, and rewind graph reconstruction.

- [ ] **Step 4: GREEN — port Super Flickies** and verify focused tests.

- [ ] **Step 5: RED — Hyper Knuckles.**

  Test only the ROM glide-impact/climb branches publish earthquake, screen
  shake, flash, and enemy effects; ordinary Super/normal Knuckles must not.

- [ ] **Step 6: GREEN — port Hyper Knuckles effects** and verify focused tests.

- [ ] **Step 7: Run form-family regression and rewind guards.**

- [ ] **Step 8: Commit Task 8.**

## Task 9: Integration, review, and release documentation

**Files:**

- Create: `src/test/java/com/openggf/game/sonic3k/TestS3kSuperEmeraldRouteIntegration.java`
- Modify: `CHANGELOG.md`
- Modify: `README.md` only when preparing the eventual merge into `develop`
- Modify: `docs/status/s3k-known-discrepancies.md` if an existing discrepancy
  is closed or a proven residual difference remains
- Create: `docs/architecture/validation/s3k-zones/2026-07-29-s3k-super-emeralds-integration.md`

- [ ] **Step 1: RED — production-owner end-to-end route test.**

  Drive real owners through MHZ ring -> HPZ -> conversion -> pedestal N ->
  Special Stage success/failure -> HPZ -> origin exit. Assert physical ring,
  mixed emerald states, exact stage, camera/player/ring/solidity/resize/water,
  and no duplicate reward.

- [ ] **Step 2: GREEN — fix only integration gaps**, never test-route or zone
  carve-outs in shared code.

- [ ] **Step 3: Run all focused commands from the design.**

  Supply all three discovered ROM properties where the selected classes require
  them.

- [ ] **Step 4: Run `mvn test` and `mvn package`.**

  Compare failures with the recorded `next` baseline. Investigate every new
  failure; do not claim full green if baseline failures remain.

- [ ] **Step 5: Independent disassembly/spec review.**

  Review routing, flag timing, state transitions, signed countdowns, art
  addresses, mappings, PLC ownership, abilities, lifecycle, and rewind against
  `sonic3k.asm`. Fix and re-review until no feature blocker remains.

- [ ] **Step 6: Independent code-quality review.**

  Review shared-boundary placement, API compatibility, Mod API descriptor
  obligations, allocation behavior, object ownership, documentation, and
  regression evidence.

- [ ] **Step 7: Update changelog/status and write the integration report.**

- [ ] **Step 8: Commit final integration with required trailers.**

- [ ] **Step 9: Present the branch for explicit human review.**

  Do not merge to `develop`; merge only after the user's explicit confirmation.
