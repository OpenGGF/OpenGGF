# Mod Gameplay Policies Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add four owner-tagged Mod API 2.4 contributions for new-game destination, launch-local team, deterministic input filtering, and immutable HUD presentation.

**Architecture:** Contributions freeze inside `ModRegistrationPlan` and are composed by `ModBackedGamePatch` in effective owner order. Data select resolves game start before constructing its two fresh-start actions; launch team resolves after module decoration but before `WorldSession`/sprite bootstrap; input filters run in `SpriteManager` after raw live/replay snapshot acquisition; HUD profiles are selected by tagged destination and consumed by a row-driven `HudRenderManager`. A dedicated engine test proves mod-classloader dynamic-object recreation through `genericRecreate` before Flappy becomes the first sample consumer.

**Tech Stack:** Java 21, Maven, JUnit 5, Mockito, OpenGGF module decorators, rewind snapshots, Mod API signature guards.

**Design reference:** `docs/superpowers/specs/2026-07-14-flappy-native-tails-design.md`

**Prerequisite:** Complete `docs/superpowers/plans/2026-07-14-s3k-mod-zone-adapter.md` first, including its immutable published 2.3 baseline and exact 2.3 signature-test method names.

**Commit policy:** Keep the repository trailer block on every commit. For Tasks 1-8, use `Changelog: n/a: covered by the aggregate Mod API 2.4 policy entry in Task 9`; Task 9 stages `CHANGELOG.md` and uses `Changelog: updated` and `Guide: n/a: modding handbook is outside docs/guide`, with other mappings marked accurately. Never modify or regenerate `mods/mod-api-signatures-2.3.txt`.

**Mandatory expected-red signature gate for Tasks 1-8:** After each task's green feature command, run:

`mvn "-Dtest=com.openggf.mods.TestModApiSignatureSurface#publishedTwoThreeSurfaceIsPinnedToTheCurrentSurface+twoTwoToTwoThreeIsAnAdditiveMinorBump" test`

Expected: those two named 2.3 methods fail because the live surface contains unrefrozen 2.4 additions; no other signature method fails. Task 9 creates a new 2.4 baseline, closes 2.3 as historical, and makes the complete signature class green.

---

## File map

- Create `ModLaunchTeamContribution`, `ModInputFilterContribution`, and `ModHudProfileContribution` under `com.openggf.mods.code`.
- Create `GameplayInputFilter`, `GameplayPolicyProvider`, `GameplayLaunchTeam`, `HudProfile`, `HudRow`, `HudLabel`, `HudMetric`, and `HudWarningPolicy` under `com.openggf.game` / `com.openggf.level.objects`.
- Create owner-aware wrappers/resolvers under `com.openggf.mods.code`.
- Modify `ModZoneContribution` to carry the explicit game-start marker with a compatibility constructor.
- Modify `ModContext`, `ModRegistrationPlan`, and `ModBackedGamePatch` to stage, freeze, validate, and compose all four contributions.
- Modify `DataSelectHostProfile` and `DataSelectSessionController` for initial destination.
- Modify `Engine.openDataSelectPatchSession`, `GameplayLaunchRequest`, `GameplayTeamAvailability`, `SaveSessionContext`, and `GameplayTeamBootstrap` for session-local launch team.
- Modify `GameplayModeContext`, `LevelManager`, and `SpriteManager` for filter/profile installation and teardown.
- Modify `HudRenderManager` for immutable row-driven presentation.
- Add focused tests in data-select, patch, sprite, HUD, and rewind packages.
- Freeze a new Mod API 2.4 signature/docs lineage while preserving the closed 2.3 file, and update `CHANGELOG.md`.

### Task 1: Rewire both fresh-start data-select branches

**Files:**
- Modify: `src/main/java/com/openggf/game/dataselect/DataSelectHostProfile.java`
- Modify: `src/main/java/com/openggf/game/dataselect/DataSelectSessionController.java`
- Test: `src/test/java/com/openggf/game/dataselect/TestDataSelectSessionController.java`

- [ ] **Step 1: Write failing no-save/new-slot tests**

```java
@Test void bothFreshStartTypesUseHostInitialDestination() {
    DataSelectHostProfile host = hostWithNewGameDestination(new DataSelectDestination(7, 1));
    DataSelectSessionController controller = controller(host);

    controller.model().setSelectedRow(0);
    DataSelectAction noSave = controller.confirmSelection();
    assertEquals(DataSelectActionType.NO_SAVE_START, noSave.type());
    assertEquals(new DataSelectDestination(7, 1), new DataSelectDestination(noSave.zone(), noSave.act()));

    controller.model().setSelectedRow(1);
    DataSelectAction newSlot = controller.confirmSelection();
    assertEquals(DataSelectActionType.NEW_SLOT_START, newSlot.type());
    assertEquals(new DataSelectDestination(7, 1), new DataSelectDestination(newSlot.zone(), newSlot.act()));
}
```

Also assert `LOAD_SLOT` and `CLEAR_RESTART` still use payload/profile destinations.

- [ ] **Step 2: Run and verify the current `(0,0)` failure**

Run: `mvn "-Dtest=com.openggf.game.dataselect.TestDataSelectSessionController" test`

Expected: both fresh-start assertions report actual `(0,0)`.

- [ ] **Step 3: Add the default and use it in exactly two branches**

```java
default DataSelectDestination newGameDestination() {
    return new DataSelectDestination(0, 0);
}
```

```java
DataSelectDestination start = hostProfile.newGameDestination();
return new DataSelectAction(DataSelectActionType.NO_SAVE_START, -1,
        start.zone(), start.act(), currentTeam());
```

Use the same local construction for the empty-slot `NEW_SLOT_START` branch. Do not alter `LOAD_SLOT` or `CLEAR_RESTART`.

`DataSelectHostProfile` is already a recursive `@ModApi` type in the frozen 2.3 surface; do not add a second annotation or create a host-internal shadow interface. The new default method is deliberately creator-visible and must appear in the new 2.4 signature snapshot. Add an assertion in `TestModApiSignatureSurface` Task 9 for the exact canonical line `METHOD com.openggf.game.dataselect.DataSelectHostProfile public  com.openggf.game.dataselect.DataSelectDestination newGameDestination()`.

- [ ] **Step 4: Run data-select profile/controller tests**

Run: `mvn "-Dtest=com.openggf.game.dataselect.TestDataSelectSessionController,com.openggf.game.sonic3k.dataselect.TestS3kDataSelectProfile,com.openggf.game.sonic2.dataselect.TestS2DataSelectProfile" test`

Expected: feature tests pass. Then the mandatory signature command fails only `publishedTwoThreeSurfaceIsPinnedToTheCurrentSurface` and `twoTwoToTwoThreeIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/dataselect/DataSelectHostProfile.java src/main/java/com/openggf/game/dataselect/DataSelectSessionController.java src/test/java/com/openggf/game/dataselect/TestDataSelectSessionController.java
git commit -m "feat: resolve fresh-start data-select destination"
```

### Task 2: Add exclusive game-start contributions and shadow findings

**Files:**
- Modify: `src/main/java/com/openggf/mods/code/ModZoneContribution.java`
- Modify: `src/main/java/com/openggf/mods/code/PreparedModZone.java`
- Modify: `src/main/java/com/openggf/mods/code/ModZoneRegistry.java`
- Modify: `src/main/java/com/openggf/mods/code/ModContext.java`
- Modify: `src/main/java/com/openggf/mods/code/ModBackedGamePatch.java`
- Create: `src/main/java/com/openggf/mods/code/ModGameStartResolver.java`
- Test: `src/test/java/com/openggf/mods/code/TestModGameStartResolver.java`

- [ ] **Step 1: Write failing winner/fallback tests**

```java
@Test void lastEffectiveGameStartWinsAndReportsEveryShadowedOwner() {
    GameModule resolved = applyInOrder(base(), startZone("alpha"), startZone("beta"));
    assertEquals(zoneDestination(resolved, "beta"),
            resolved.getDataSelectHostProfile().newGameDestination());
    assertEquals(List.of("alpha"), findingOwners("MOD_GAME_START_SHADOWED"));
}

@Test void disablingWinnerRevealsPreviousThenStock() {
    assertEquals(zoneDestination(resolve(enabled("alpha")), "alpha"), start());
    assertEquals(new DataSelectDestination(0, 0), start(resolve(disabled("alpha"))));
}

@Test void anchorlessGameStartUsesTaggedDestinationWithoutProgressionInsertion() {
    GameModule resolved = applyInOrder(base(), anchorlessStartZone("alpha"));
    int custom = resolved.getZoneRegistry()
            .resolveZoneKey(ZoneKey.mod("alpha", "sky")).orElseThrow();
    assertEquals(new DataSelectDestination(custom, 0),
            resolved.getDataSelectHostProfile().newGameDestination());
    assertTrue(StockProgressionAnchors.anchorsFor("s3k").isEmpty());
    assertStockSuccessorUnchanged(resolved.getZoneRegistry().progressionPlan());
}
```

- [ ] **Step 2: Run and verify red**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModGameStartResolver" test`

Expected: compilation fails because `gameStart` and the resolver do not exist.

- [ ] **Step 3: Add the compatible record component and deterministic resolver**

```java
@ModApi
public record ModZoneContribution(String localKey, BakedLevelRef level,
        String insertAfter, ZoneEventFactory eventFactory, boolean gameStart) {
    public ModZoneContribution(String localKey, BakedLevelRef level,
            String insertAfter, ZoneEventFactory eventFactory) {
        this(localKey, level, insertAfter, eventFactory, false);
    }
}
```

`ModGameStartResolver` receives the fully assembled `ModZoneRegistry`, iterates frozen effective contribution order, records one owner finding for every marked zone except the last, and returns the winner's current runtime destination. If no marked contribution is enabled, delegate to the inherited host profile.

Consume Plan A's anchorless-zone seam: a game-start contribution may keep `insertAfter == null`, remains addressable by `ZoneKey.Mod`, and contributes no `ZoneProgressionPlan` edge. Do not call `withDefaultAnchor` for an anchorless S3K game-start contribution and do not add `aiz1` to `StockProgressionAnchors`. Existing anchored S2 contributions and their `mtz3` compatibility default stay unchanged.

- [ ] **Step 4: Run game-start, progression, and compatibility tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModGameStartResolver,com.openggf.mods.code.TestModZoneLoader,com.openggf.game.TestZoneProgressionPlan" test`

Expected: feature tests pass and no test observes a prepend edge in `ZoneProgressionPlan`. Then the mandatory signature command fails only `publishedTwoThreeSurfaceIsPinnedToTheCurrentSurface` and `twoTwoToTwoThreeIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/mods/code/ModZoneContribution.java src/main/java/com/openggf/mods/code/PreparedModZone.java src/main/java/com/openggf/mods/code/ModZoneRegistry.java src/main/java/com/openggf/mods/code/ModContext.java src/main/java/com/openggf/mods/code/ModBackedGamePatch.java src/main/java/com/openggf/mods/code/ModGameStartResolver.java src/test/java/com/openggf/mods/code/TestModGameStartResolver.java
git commit -m "feat: add mod game-start destinations"
```

### Task 3: Define and freeze destination-scoped gameplay policies

**Files:**
- Create: `src/main/java/com/openggf/game/GameplayInputFilter.java`
- Create: `src/main/java/com/openggf/game/GameplayLaunchTeam.java`
- Create: `src/main/java/com/openggf/game/GameplayPolicyProvider.java`
- Create: `src/main/java/com/openggf/level/objects/HudLabel.java`
- Create: `src/main/java/com/openggf/level/objects/HudMetric.java`
- Create: `src/main/java/com/openggf/level/objects/HudWarningPolicy.java`
- Create: `src/main/java/com/openggf/level/objects/HudRow.java`
- Create: `src/main/java/com/openggf/level/objects/HudProfile.java`
- Create: `src/main/java/com/openggf/mods/code/ModLaunchTeamContribution.java`
- Create: `src/main/java/com/openggf/mods/code/ModInputFilterContribution.java`
- Create: `src/main/java/com/openggf/mods/code/ModHudProfileContribution.java`
- Modify: `src/main/java/com/openggf/game/GameModule.java`
- Modify: `src/main/java/com/openggf/game/patch/DelegatingGameModule.java`
- Modify: `src/main/java/com/openggf/mods/code/ModContext.java`
- Modify: `src/main/java/com/openggf/mods/code/ModRegistrationPlan.java`
- Test: `src/test/java/com/openggf/mods/code/TestModGameplayPolicyRegistration.java`

- [ ] **Step 1: Write failing immutable/owner/duplicate tests**

```java
@Test void transactionFreezesAllPoliciesByTaggedDestination() {
    ZoneKey zone = ZoneKey.mod("alpha", "sky");
    ModContext context = context("alpha", "s3k");
    context.registerLaunchTeam(new ModLaunchTeamContribution(zone, CharacterKey.TAILS, List.of()));
    context.registerInputFilter(new ModInputFilterContribution(zone, input -> input));
    context.registerHudProfile(new ModHudProfileContribution(zone, HudProfile.stock()));
    ModRegistrationPlan plan = context.freeze();
    assertEquals(1, plan.launchTeams().size());
    assertEquals(1, plan.inputFilters().size());
    assertEquals(1, plan.hudProfiles().size());
}

@Test void foreignZoneAndDuplicatePolicyPoisonTheWholeTransaction() {
    assertPoisoned(ctx -> ctx.registerLaunchTeam(teamFor(ZoneKey.mod("other", "sky"))));
    assertPoisoned(ctx -> { ctx.registerHudProfile(hud()); ctx.registerHudProfile(hud()); });
}
```

- [ ] **Step 2: Run and verify missing types/methods**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModGameplayPolicyRegistration" test`

Expected: compilation fails.

- [ ] **Step 3: Add the closed policy vocabulary**

```java
@ModApi @FunctionalInterface
public interface GameplayInputFilter {
    PlayerInputState filter(PlayerInputState rawSnapshot);
    GameplayInputFilter IDENTITY = input -> input;
}

@ModApi
public record GameplayLaunchTeam(CharacterKey main, List<CharacterKey> sidekicks) {
    public GameplayLaunchTeam { sidekicks = List.copyOf(sidekicks); }
}

@ModApi
public record HudRow(boolean visible, HudLabel label, HudMetric metric,
        int labelX, int labelY, int valueRightX, int valueY,
        int maxDigits, HudWarningPolicy warning) {}
```

`HudProfile.stock()` returns four immutable rows matching current score/time/rings/lives positions. Contribution constructors require a `ZoneKey.Mod` owned by `ModContext.ownerModId()`. Freeze maps in insertion order and preserve the old `ModRegistrationPlan` constructors with empty policy maps.

Add `GameModule.getGameplayPolicyProvider()` with a stock-empty default and forward it from `DelegatingGameModule`, so patch composition never relies on game-name checks or downcasts.

- [ ] **Step 4: Run registration tests and the expected-red signature gate**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModGameplayPolicyRegistration" test`

Expected: policy tests pass. Then the mandatory signature command fails only `publishedTwoThreeSurfaceIsPinnedToTheCurrentSurface` and `twoTwoToTwoThreeIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/GameplayInputFilter.java src/main/java/com/openggf/game/GameplayLaunchTeam.java src/main/java/com/openggf/game/GameplayPolicyProvider.java src/main/java/com/openggf/game/GameModule.java src/main/java/com/openggf/game/patch/DelegatingGameModule.java src/main/java/com/openggf/level/objects/HudLabel.java src/main/java/com/openggf/level/objects/HudMetric.java src/main/java/com/openggf/level/objects/HudWarningPolicy.java src/main/java/com/openggf/level/objects/HudRow.java src/main/java/com/openggf/level/objects/HudProfile.java src/main/java/com/openggf/mods/code/ModLaunchTeamContribution.java src/main/java/com/openggf/mods/code/ModInputFilterContribution.java src/main/java/com/openggf/mods/code/ModHudProfileContribution.java src/main/java/com/openggf/mods/code/ModContext.java src/main/java/com/openggf/mods/code/ModRegistrationPlan.java src/test/java/com/openggf/mods/code/TestModGameplayPolicyRegistration.java
git commit -m "feat: define mod gameplay policy contributions"
```

### Task 4: Resolve launch team before opening the world session

**Files:**
- Modify: `src/main/java/com/openggf/game/patch/GameplayLaunchRequest.java`
- Modify: `src/main/java/com/openggf/game/patch/GameplayTeamAvailability.java`
- Modify: `src/main/java/com/openggf/mods/code/ModBackedGamePatch.java`
- Modify: `src/main/java/com/openggf/Engine.java`
- Modify: `src/main/java/com/openggf/game/save/SaveSessionContext.java`
- Modify: `src/main/java/com/openggf/game/session/GameplayTeamBootstrap.java`
- Test: `src/test/java/com/openggf/TestEngineDataSelectPatchResolution.java`
- Modify: `src/test/java/com/openggf/TestGameplayTeamBootstrapContext.java`

- [ ] **Step 1: Write failing session-local team tests**

```java
@Test void destinationPolicyReplacesOnlyTheLaunchContextTeam() {
    SaveSessionContext durable = noSaveContext(team("sonic", "tails"), flappyDestination());
    GameplayModeContext gameplay = engine.openDataSelectPatchSession(durable, available(), prepared());
    assertEquals(new SelectedTeam("tails", List.of()),
            gameplay.getWorldSession().getSaveSessionContext().selectedTeam());
    assertEquals(new SelectedTeam("sonic", List.of("tails")), durable.selectedTeam());
    assertConfigMain("sonic");
}

@Test void missingContributedCharacterAbortsInsteadOfFallingBack() {
    assertThrows(ModRegistrationException.class,
            () -> openWithTeam(CharacterKey.mod("missing", "hero")));
}
```

- [ ] **Step 2: Run and verify Sonic remains selected**

Run: `mvn "-Dtest=com.openggf.TestEngineDataSelectPatchResolution,com.openggf.TestGameplayTeamBootstrapContext" test`

Expected: the world session retains the data-select team.

- [ ] **Step 3: Resolve by tagged destination after module decoration**

```java
ZoneKey destination = resolvedModule.getZoneRegistry().zoneKey(action.zone());
GameplayLaunchTeam launchTeam = resolvedModule.getGameplayPolicyProvider()
        .launchTeam(destination).orElse(requestedTeam);
SaveSessionContext launchContext = sanitized.withSelectedTeam(
        new SelectedTeam(launchTeam.main().persisted(),
                launchTeam.sidekicks().stream().map(CharacterKey::persisted).toList()));
```

Validate every key against `resolvedModule.getPlayableCharacterRegistry()` before `SessionManager.openGameplaySession`. Route the selected team through `GameplayTeamAvailability` so its availability/sanitization check uses the resolved session-local request rather than the durable data-select value. Route creator/provider callbacks through `ModFaultBoundary`. Keep `config.yaml`, the original data-select selection, and durable save payload unchanged.

- [ ] **Step 4: Run launch and save-context tests**

Run: `mvn "-Dtest=com.openggf.TestEngineDataSelectPatchResolution,com.openggf.TestGameplayTeamBootstrapContext" test`

Expected: feature tests pass. Then the mandatory signature command fails only `publishedTwoThreeSurfaceIsPinnedToTheCurrentSurface` and `twoTwoToTwoThreeIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/patch/GameplayLaunchRequest.java src/main/java/com/openggf/game/patch/GameplayTeamAvailability.java src/main/java/com/openggf/mods/code/ModBackedGamePatch.java src/main/java/com/openggf/Engine.java src/main/java/com/openggf/game/save/SaveSessionContext.java src/main/java/com/openggf/game/session/GameplayTeamBootstrap.java src/test/java/com/openggf/TestEngineDataSelectPatchResolution.java src/test/java/com/openggf/TestGameplayTeamBootstrapContext.java
git commit -m "feat: apply destination-scoped launch teams"
```

### Task 5: Install deterministic post-snapshot input filtering

**Files:**
- Modify: `src/main/java/com/openggf/game/session/GameplayModeContext.java`
- Modify: `src/main/java/com/openggf/level/LevelManager.java`
- Modify: `src/main/java/com/openggf/sprites/managers/SpriteManager.java`
- Create: `src/main/java/com/openggf/mods/code/OwnerAwareGameplayInputFilter.java`
- Test: `src/test/java/com/openggf/sprites/managers/TestSpriteManagerGameplayInputFilter.java`
- Test: `src/test/java/com/openggf/game/rewind/TestGameplayInputFilterReplay.java`

- [ ] **Step 1: Write failing live/replay equivalence tests**

```java
@Test void filterSuppressesHorizontalAndPreservesJumpForMovementAndEvents() {
    install(input -> PlayerInputState.of(
            input.heldMask() & ~(INPUT_LEFT | INPUT_RIGHT),
            input.pressedMask() & ~(INPUT_LEFT | INPUT_RIGHT),
            input.actionHeldMask(), input.actionPressedMask(),
            input.startHeld(), input.startPressed()));
    drive(held(INPUT_LEFT | INPUT_JUMP));
    assertFalse(player.getInputLeft());
    assertTrue(player.getInputJump());
    assertFalse(levelEventInput().left());
}

@Test void liveRecordAndRewindReplayApplyTheSameFilterDownstream() {
    driveAndRecord(held(INPUT_RIGHT | INPUT_JUMP));
    assertEquals(INPUT_RIGHT | INPUT_JUMP, recordedBk2().p1Held());
    seekBackAndReplay();
    assertEquals(firstRunState(), replayedState());
}
```

- [ ] **Step 2: Run and verify raw input reaches movement**

Run: `mvn "-Dtest=com.openggf.sprites.managers.TestSpriteManagerGameplayInputFilter,com.openggf.game.rewind.TestGameplayInputFilterReplay" test`

Expected: left/right remain visible to the playable.

- [ ] **Step 3: Filter once from the logical snapshot in both SpriteManager paths**

```java
private PlayerInputState effectivePlayerOne(InputHandler handler) {
    PlayerInputState snapshot = handler.logical().player1();
    return gameplayInputFilter.filter(snapshot);
}
```

Use `effectivePlayerOne` in both `publishHeldInputForLevelEvents` and `update`. `LiveRewindInputSource.appendFrame` remains unchanged and records `handler.logical().player1()` raw. `LiveRewindStepper` and trace replay keep installing raw logical overrides; the normal SpriteManager path reapplies the filter. Reset to `GameplayInputFilter.IDENTITY` on context teardown/stock zone load.

Document and test the `PlayerInputState` reconstruction contract: its compact constructor sanitizes directions and re-derives `INPUT_JUMP` from `actionHeldMask` / `actionPressedMask`. A filter that intends to preserve jump must preserve those action masks; merely clearing the jump bit in `heldMask` / `pressedMask` cannot suppress it. The Flappy filter clears only left/right and reconstructs with `PlayerInputState.of(...)`, preserving both action masks and both start fields.

- [ ] **Step 4: Run input, rewind, and trace-input tests**

Run: `mvn "-Dtest=com.openggf.sprites.managers.TestSpriteManagerGameplayInputFilter,com.openggf.game.rewind.TestGameplayInputFilterReplay,com.openggf.game.rewind.TestLiveRewindInputSource" test`

Expected: feature tests pass. Then the mandatory signature command fails only `publishedTwoThreeSurfaceIsPinnedToTheCurrentSurface` and `twoTwoToTwoThreeIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/session/GameplayModeContext.java src/main/java/com/openggf/level/LevelManager.java src/main/java/com/openggf/sprites/managers/SpriteManager.java src/main/java/com/openggf/mods/code/OwnerAwareGameplayInputFilter.java src/test/java/com/openggf/sprites/managers/TestSpriteManagerGameplayInputFilter.java src/test/java/com/openggf/game/rewind/TestGameplayInputFilterReplay.java
git commit -m "feat: filter gameplay input after replay snapshots"
```

### Task 6: Render HUD rows from an immutable profile

**Files:**
- Modify: `src/main/java/com/openggf/level/objects/HudRenderManager.java`
- Modify: `src/main/java/com/openggf/level/LevelManager.java`
- Modify: `src/main/java/com/openggf/mods/code/ModBackedGamePatch.java`
- Test: `src/test/java/com/openggf/level/objects/TestHudRenderManager.java`
- Create: `src/test/java/com/openggf/mods/code/TestModHudProfileResolution.java`

- [ ] **Step 1: Write failing stock-parity and Flappy-profile tests**

```java
@Test void stockProfileProducesExistingDrawCoordinatesAndMetrics() {
    manager.setProfile(HudProfile.stock());
    manager.draw(levelState, player);
    assertEquals(existingStockCommands(), capturedCommands());
}

@Test void scoreLabelCanDisplayRingMetricInTheRingsSlot() {
    manager.setProfile(flappyProfile());
    manager.draw(levelStateWithRings(17), player);
    assertFalse(drewRowAtY(8));
    assertTrue(drewLabel(HudLabel.SCORE, 16, 40));
    assertTrue(drewNumber(17, 64, 40));
    assertFalse(drewWarningFrame());
}
```

- [ ] **Step 2: Run and verify profile methods are absent**

Run: `mvn "-Dtest=com.openggf.level.objects.TestHudRenderManager,com.openggf.mods.code.TestModHudProfileResolution" test`

Expected: compilation fails.

- [ ] **Step 3: Replace hardcoded row calls with profile dispatch**

```java
for (HudRow row : profile.rows()) {
    if (!row.visible()) continue;
    drawStaticFrame(selectLabelFrame(row, levelState), row.labelX(), row.labelY());
    drawMetric(row.metric(), row.valueRightX(), row.valueY(), row.maxDigits(), levelState);
}
```

Map labels to existing `HudStaticArt` frames. Map metrics to `GameStateManager.score`, `LevelState.displayTime`, `LevelState.rings`, and lives. Only `HudWarningPolicy.ZERO_FLASH` may select flash art. Preserve debug HUD behavior and bonus-stage layout outside profile mode. `LevelManager` resolves the current tagged zone profile after level publication and sets `HudProfile.stock()` for stock zones.

- [ ] **Step 4: Run HUD and UI render-order tests**

Run: `mvn "-Dtest=com.openggf.level.objects.TestHudRenderManager,com.openggf.mods.code.TestModHudProfileResolution,com.openggf.tests.graphics.RenderOrderTest" test`

Expected: feature tests pass. Then the mandatory signature command fails only `publishedTwoThreeSurfaceIsPinnedToTheCurrentSurface` and `twoTwoToTwoThreeIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/level/objects/HudRenderManager.java src/main/java/com/openggf/level/LevelManager.java src/main/java/com/openggf/mods/code/ModBackedGamePatch.java src/test/java/com/openggf/level/objects/TestHudRenderManager.java src/test/java/com/openggf/mods/code/TestModHudProfileResolution.java
git commit -m "feat: add destination-scoped HUD profiles"
```

### Task 7: Prove mod-owned independent dynamic recreation

**Files:**
- Create: `src/test/resources/mods/dynamic-rewind-src/META-INF/openggf-mod.yaml`
- Create: `src/test/resources/mods/dynamic-rewind-src/example/dynamicrewind/DynamicProbe.java`
- Create: `src/test/java/com/openggf/mods/code/TestModOwnedDynamicObjectRewind.java`
- Modify only if the test exposes a defect: `src/main/java/com/openggf/level/objects/ObjectManager.java`
- Modify only if the test exposes a defect: `src/main/java/com/openggf/mods/code/ModClassResolver.java`

- [ ] **Step 1: Add a real mod-classloader probe and failing restore test**

```java
public final class DynamicProbe extends AbstractObjectInstance implements RewindRecreatable {
    private int value;
    public DynamicProbe(ObjectSpawn spawn) { super(spawn, "dynamic-rewind:probe"); }
    public void setValue(int value) { this.value = value; }
    public int value() { return value; }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new DynamicProbe(context.dynamicEntry().spawn());
    }
}
```

```java
@Test void genericRecreateUsesOwnerClassloaderStableIdAndNoAdoptionDuplicate() throws Exception {
    LoadedProbe loaded = compileLoadAndRegisterProbe();
    ObjectRefId before = addIndependentDynamicProbe(loaded, 73);
    ObjectManagerSnapshot snapshot = objectManager.capture();
    removeAllDynamicObjects();
    objectManager.restore(snapshot);
    ObjectInstance restored = onlyDynamicObject();
    assertEquals(loaded.type(), restored.getClass());
    assertEquals(73, invokeInt(restored, "value"));
    assertEquals(before, restoredObjectRefId(restored));
    assertEquals(1, dynamicObjectCount());
}
```

- [ ] **Step 2: Run the test and record whether the existing path is already green**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModOwnedDynamicObjectRewind" test`

Expected: the first run may pass because `GameplaySessionFactory` already installs `ModClassResolver`; if it fails, the failure must identify owner lookup, stable-id registration, or duplication. Do not replace the test with a layout-reconstructed object.

- [ ] **Step 3: Fix only a demonstrated engine defect**

The intended restore remains:

```java
if (isRewindRecreatable(entry, context)) {
    return ObjectRewindDynamicCodecs.genericRecreate(entry, context);
}
```

Keep `registerRewindReconstructionChild`/adoption for objects a reconstructed parent actually spawns. Independent entries must resolve `entry.ownerModId()` plus `entry.className()`, register `entry.objectId()`, restore scalar state, and never enter the adoption pool.

- [ ] **Step 4: Run dynamic-chain, class-resolver, and coverage guards**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModOwnedDynamicObjectRewind,com.openggf.mods.code.TestModClassResolverRecreate,com.openggf.level.objects.TestObjectManagerDynamicChainRewindRestore,com.openggf.game.rewind.coverage.TestRewindCoverageGuard" test`

Expected: feature tests pass. Then the mandatory signature command fails only `publishedTwoThreeSurfaceIsPinnedToTheCurrentSurface` and `twoTwoToTwoThreeIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

Stage the two production files only when Step 3 actually changed them:

```powershell
git add src/test/resources/mods/dynamic-rewind-src src/test/java/com/openggf/mods/code/TestModOwnedDynamicObjectRewind.java
git add src/main/java/com/openggf/level/objects/ObjectManager.java src/main/java/com/openggf/mods/code/ModClassResolver.java # only changed paths; omit this line when both are unchanged
git commit -m "test: prove mod-owned dynamic rewind recreation"
```

### Task 8: Verify owner fault boundaries and teardown

**Files:**
- Create: `src/test/java/com/openggf/mods/code/TestModGameplayPolicyFaultBoundary.java`
- Modify: `src/main/java/com/openggf/mods/code/ModBackedGamePatch.java`
- Modify: `src/main/java/com/openggf/mods/code/OwnerAwareGameplayInputFilter.java`
- Modify: `src/main/java/com/openggf/game/session/GameplayModeContext.java`

- [ ] **Step 1: Write failing callback-failure and teardown tests**

```java
@Test void throwingRequiredFilterDisablesOwnerAndAbortsLaunch() {
    registerFilter("alpha", input -> { throw new IllegalStateException("boom"); });
    assertThrows(ModFaultBoundary.CallbackAborted.class, this::stepFirstGameplayFrame);
    assertOwnerDisabled("alpha", "MOD_CALLBACK_FAILED");
}

@Test void destroyingContextRemovesFilterAndHudProfile() {
    GameplayModeContext first = launchWithPolicies();
    first.destroy();
    GameplayModeContext stock = launchStock();
    assertSame(GameplayInputFilter.IDENTITY, stock.getGameplayInputFilter());
    assertEquals(HudProfile.stock(), stock.getHudProfile());
}
```

- [ ] **Step 2: Run and verify raw callbacks/retained policy fail**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModGameplayPolicyFaultBoundary" test`

Expected: callback ownership or teardown assertion fails.

- [ ] **Step 3: Route every creator callback and clear context state**

Task 5 owns the deterministic install/replay wiring; this task owns the owner-aware callback seam and its failure test. Complete `OwnerAwareGameplayInputFilter` so every filter invocation routes through the existing `ModFaultBoundary.call(owner, ...)`; its abort type is `ModFaultBoundary.CallbackAborted`. Treat launch team, input filter, and HUD profile as required for their matching destination: failure aborts the launch/session rather than continuing with a partial policy set. Clear installed values in `GameplayModeContext.destroy()` / `tearDownManagers()` and initialize identity/stock defaults in the constructor. Do not introduce `ModCallbackException` or a `close()` alias.

- [ ] **Step 4: Run fault-boundary regression tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModGameplayPolicyFaultBoundary,com.openggf.mods.code.TestModContextAndFaultBoundary" test`

Expected: feature tests pass. Then the mandatory signature command fails only `publishedTwoThreeSurfaceIsPinnedToTheCurrentSurface` and `twoTwoToTwoThreeIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/com/openggf/mods/code/TestModGameplayPolicyFaultBoundary.java src/main/java/com/openggf/mods/code/ModBackedGamePatch.java src/main/java/com/openggf/mods/code/OwnerAwareGameplayInputFilter.java src/main/java/com/openggf/game/session/GameplayModeContext.java
git commit -m "feat: fault-bound mod gameplay policies"
```

### Task 9: Freeze the complete Mod API 2.4 surface and document it

**Files:**
- Modify: `src/main/java/com/openggf/mods/ModApiVersion.java`
- Create: `src/test/resources/mods/mod-api-signatures-2.4.txt`
- Modify: `src/test/java/com/openggf/mods/TestModApiSignatureSurface.java`
- Modify: `docs/architecture/mod-api-compatibility.md`
- Modify: `docs/modding/content-mods.md`
- Modify: `docs/modding/index.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Run the signature test and capture the intended additive diff**

Run: `mvn "-Dtest=com.openggf.mods.TestModApiSignatureSurface" test`

Expected: the two live-2.3 pin methods fail and the diff lists only the game-start/default-destination and gameplay-policy additions from Tasks 1-8.

- [ ] **Step 2: Set 2.4 and freeze a new snapshot without changing 2.3**

```java
public static final SemanticVersion CURRENT = SemanticVersion.parse("2.4.0");
```

Compile the signature tool, then generate `mods/mod-api-signatures-2.4.txt` with its existing snapshot mode:

```powershell
mvn "-DskipTests" compile
java -cp target/classes com.openggf.mods.code.ModApiSignatureSurface --snapshot | Set-Content -Encoding utf8NoBOM src/test/resources/mods/mod-api-signatures-2.4.txt
```

Keep `mods/mod-api-signatures-2.3.txt` byte-for-byte unchanged as a closed historical baseline. The final 2.4 snapshot must contain every 2.3 line plus:

```text
METHOD com.openggf.game.dataselect.DataSelectHostProfile public  com.openggf.game.dataselect.DataSelectDestination newGameDestination()
GameplayInputFilter.filter(PlayerInputState)
GameplayLaunchTeam
HudProfile / HudRow / HudLabel / HudMetric / HudWarningPolicy
ModContext.registerLaunchTeam(...)
ModContext.registerInputFilter(...)
ModContext.registerHudProfile(...)
ModZoneContribution(..., boolean gameStart)
```

Retain source/binary compatibility constructors for all pre-2.4 records changed by an appended component. In `TestModApiSignatureSurface`, make 2.3 a named historical baseline with its frozen line/type counts, add `twoThreeToTwoFourIsAnAdditiveMinorBump`, and rename the live pin to `publishedTwoFourSurfaceIsPinnedToTheCurrentSurface`. Assert the 2.4 snapshot contains `DataSelectHostProfile.newGameDestination()`; the interface was already published, so this method is part of 2.4 rather than an untracked host-internal change.

- [ ] **Step 3: Document ordering, determinism, and non-persistence**

Document exclusive last-enabled game-start resolution, session-only launch team, raw-snapshot recording followed by deterministic filtering, the `PlayerInputState` jump re-derivation/action-mask contract, row-only HUD presentation, creator fault boundaries, and stock defaults. State that no movement/camera/fatigue framework was added.

- [ ] **Step 4: Run the policy completion gate**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModGameStartResolver,com.openggf.mods.code.TestModGameplayPolicyRegistration,com.openggf.TestEngineDataSelectPatchResolution,com.openggf.sprites.managers.TestSpriteManagerGameplayInputFilter,com.openggf.game.rewind.TestGameplayInputFilterReplay,com.openggf.level.objects.TestHudRenderManager,com.openggf.mods.code.TestModOwnedDynamicObjectRewind,com.openggf.mods.code.TestModGameplayPolicyFaultBoundary,com.openggf.mods.TestModApiSignatureSurface" test`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/mods/ModApiVersion.java src/test/resources/mods/mod-api-signatures-2.4.txt src/test/java/com/openggf/mods/TestModApiSignatureSurface.java docs/architecture/mod-api-compatibility.md docs/modding/content-mods.md docs/modding/index.md CHANGELOG.md
git commit -m "docs: publish Mod API 2.4 gameplay policies"
```

## Completion gate

- [ ] Run the Task 9 focused command.
- [ ] Run `mvn package`.
- [ ] Confirm `git diff --check` is clean.
- [ ] Confirm `LiveRewindInputSource` still records raw `InputHandler.logical()` snapshots.
- [ ] Confirm no config/save mutation is used to implement launch-team selection.
- [ ] Confirm `git diff --exit-code -- src/test/resources/mods/mod-api-signatures-2.3.txt` is clean.
- [ ] Confirm a stock S3K data-select launch still starts AIZ1 with the selected durable team and stock HUD/input.
