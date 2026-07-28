# Native-Tails Flappy Sample Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild Flappy as a complete Mod API 2.4 S3K game-start sample where visible native Tails flies in place and stable dynamic pipes approach and recycle.

**Architecture:** The atomic cutover creates a strict format-v2 S3K level with nested equal bounds, one layout controller, no rings, and an anchorless tagged destination. Scoped policies select Tails, filter horizontal input downstream of recorded snapshots, and render a ring-backed `SCORE` HUD. The controller places Tails at a fixed screen anchor, maintains native flight with the MGZ2 `0xF0` refill, and owns movement/recycling of six independently rewind-recreatable pipes.

**Tech Stack:** Java 21, Maven, JUnit 5, `ggfmod`, Mod API 2.4, S3K custom-zone adapter, compact rewind snapshots, Mockito render-call interception.

**Design reference:** `docs/superpowers/specs/2026-07-14-flappy-native-tails-design.md`

**Prerequisites:** Complete the converter, S3K adapter (2.3), gameplay policies (2.4), and ROM-art remix sample in that order. `TestSampleRomArtRemixIntegration` must be green before this plan removes Flappy's ROM-art request or old guide.

**Commit policy:** Keep the repository trailer block. Tasks 1-5 use `Changelog: n/a: covered by the aggregate native-Flappy entry in Task 6`. Task 6 stages `CHANGELOG.md` and uses `Changelog: updated` and `Guide: n/a: modding guides are outside docs/guide`, with other trailers accurate.

---

## File map

- Atomically switch the manifest, registration, level format, ROM-gated integration boot, and basic controller from S2 replacement art to native S3K Tails.
- Keep the existing corrected `pipe.png` / `pipe-sheet.yaml` source and verify it structurally rather than claiming headless framebuffer pixels.
- Rewrite `FlappyController` and `FlappyPipe` for fixed-camera movement, deterministic scoring, restart, and rewind.
- Delete `flappy-remix.md` only after creating `native-tails-flappy.md` and updating every remaining anchored link.
- Build and copy a local ignored `mods/sample-flappy-mod.jar` only after source tests pass.

### Task 1: Atomically cut the sample over to a valid fixed-camera S3K native-Tails level

**Files:**
- Modify: `src/test/resources/mods/sample-flappy-src/sample.properties`
- Modify: `src/test/resources/mods/sample-flappy-src/README.md`
- Modify: `src/test/resources/mods/sample-flappy-src/project/README.md`
- Modify: `src/test/resources/mods/sample-flappy-src/project/pom.xml`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/resources/META-INF/openggf-mod.yaml`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappySampleMod.java`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/mod/level-source/level.json`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/mod/level-source/binary-assets.properties`
- Modify: `src/test/java/com/openggf/tools/modsdk/SampleFlappyAssetGenerator.java`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleFlappyLevelSource.java`
- Create: `src/test/java/com/openggf/mods/code/TestSampleFlappyRegistration.java`
- Modify: `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java`

- [ ] **Step 1: Write the failing atomic-cutover contract**

```java
@Test void flappyIsAnAnchorlessS3kApi24GameStartWithoutRomArt() throws Exception {
    ModDescriptor descriptor = scan(FLAPPY);
    assertEquals(ModType.PATCH, descriptor.manifest().type());
    assertEquals("s3k", descriptor.manifest().baseGame());
    assertEquals(">=2.4.0 <3.0.0", descriptor.manifest().engineApiRange().toString());
    ModRegistrationPlan plan = compileAndRegister(FLAPPY);
    ModZoneContribution zone = plan.zones().getFirst();
    assertTrue(zone.gameStart());
    assertNull(zone.insertAfter());
    assertTrue(plan.romObjectArt().isEmpty());
    assertEquals(CharacterKey.TAILS, onlyLaunchTeam(plan).main());
    assertEquals(1, plan.inputFilters().size());
    assertEquals(1, plan.hudProfiles().size());
}

@Test void levelUsesExactV2ShapeOneControllerNoRingsAndEqualNestedBounds() throws Exception {
    JsonNode level = readSource();
    assertEquals(Set.of("formatVersion", "zoneName", "zoneIndex", "levelIndex",
            "blockGridSide", "width", "height", "bounds", "start", "music",
            "assets", "objects", "rings", "hostMetadata", "paletteClaims"),
            fieldNames(level));
    assertEquals(2, level.path("formatVersion").asInt());
    assertEquals("S3KL", level.at("/hostMetadata/s3k/objectZoneSet").asText());
    assertEquals(1, level.path("objects").size());
    assertEquals("sample-flappy:controller",
            level.path("objects").get(0).path("objectKey").asText());
    assertEquals(0, level.path("rings").size());
    assertEquals(level.at("/bounds/minX"), level.at("/bounds/maxX"));
    assertEquals(level.at("/bounds/minY"), level.at("/bounds/maxY"));
    assertFalse(level.has("game"));
    assertFalse(level.has("cameraMinX"));
    assertFalse(level.has("visibleHeight"));
}
```

In `TestSampleFlappyIntegration`, replace every S2 fixture with the real S3K equivalents in this same task: capture `File romFile = RomTestUtils.ensureSonic3kRomAvailable()` and call `assumeTrue(romFile != null, "Sonic 3&K ROM unavailable")`, then use `Sonic3kGameModule`, launch base id `s3k`, and `-Ds3k.rom.path=s3k.gen`. Remove borrowed-bird renderer assertions and start native-player assertions. The focused test follows repository convention and may assumption-skip; Task 6's completion gate is responsible for rejecting that skip.

- [ ] **Step 2: Run and verify the old S2 contract is red**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyRegistration,com.openggf.tools.modsdk.TestSampleFlappyLevelSource,com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.tools.modsdk.TestSampleModsPackage" test`

Expected: manifest, exact v2 shape, S3K boot, game-start, and native-player assertions fail against the old S2 sample. A missing ROM blocks this step.

- [ ] **Step 3: Implement registration, strict v2 source, and basic native-flight controller together**

Manifest:

```yaml
engineApiRange: ">=2.4.0 <3.0.0"
type: patch
baseGame: s3k
entrypoint: example.flappysample.FlappySampleMod
```

Registration uses no stock progression anchor:

```java
ZoneKey flappy = ZoneKey.mod("sample-flappy", "flappy-garden");
context.registerObject("controller", (spawn, registry) -> new FlappyController(spawn));
context.registerObject("pipe", (spawn, registry) -> new FlappyPipe(spawn));
context.registerObjectArt("pipe", new BakedSheetRef("art/pipe.ggfs"));
context.registerZone(new ModZoneContribution("flappy-garden",
        new BakedLevelRef("levels/flappy/level.json"), null, null, true));
context.registerLaunchTeam(new ModLaunchTeamContribution(
        flappy, CharacterKey.TAILS, List.of()));
context.registerInputFilter(new ModInputFilterContribution(flappy,
        FlappySampleMod::suppressHorizontal));
context.registerHudProfile(new ModHudProfileContribution(flappy, flappyHud()));
```

Do not add `aiz1` to `StockProgressionAnchors`. The zone is resolved by tagged game-start destination and contributes no progression edge.

V2 keeps the v1 root fields and replaces `assets.palettes` with `hostMetadata.s3k` plus sparse `paletteClaims`. It does not add `game`, `cameraMinX`, or `visibleHeight`. Use nested equal `bounds`; because strict parsing requires `start` inside those equal bounds, the first controller activation relocates Tails to `camera.getX() + 96`, `camera.getY() + 112` before flight and death are armed.

```java
private void activateNativeRun(AbstractPlayableSprite tails) {
    tails.setCentreX((short) (services().camera().getX() + 96));
    tails.setCentreY((short) (services().camera().getY() + 112));
    anchorX = tails.getCentreX();
    tails.setXSpeed((short) 0);
    tails.setGSpeed((short) 0);
    tails.getTailsFlightController().activate();
    tails.setDoubleJumpProperty((byte) 0xF0);
    routine = RUNNING;
}
```

Every running frame reapplies anchor X, zero X/G speed, and `0xF0`. Never call `setHidden`, object-control APIs, replacement rendering, direct vertical motion, or forced scrolling. The input contribution clears left/right from held and pressed masks only; jump and all other fields remain.

- [ ] **Step 4: Generate and run the complete atomic-cutover gate**

Run: `mvn "-DskipTests" test-compile`

Run: `java -cp target/test-classes com.openggf.tools.modsdk.SampleFlappyAssetGenerator`

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyRegistration,com.openggf.tools.modsdk.TestSampleFlappyLevelSource,com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.sprites.managers.TestTailsFlightController" test`

Expected: all tests execute and pass. The package validator sees a coherent S3K manifest and S3K-v2 level in the same task; there is no interim S3K-manifest/S2-level commit.

- [ ] **Step 5: Commit**

```powershell
git add src/test/resources/mods/sample-flappy-src src/test/java/com/openggf/tools/modsdk/SampleFlappyAssetGenerator.java src/test/java/com/openggf/tools/modsdk/TestSampleFlappyLevelSource.java src/test/java/com/openggf/mods/code/TestSampleFlappyRegistration.java src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java
git commit -m "feat: atomically retarget Flappy to native S3K Tails"
```

### Task 2: Create and recycle six independent dynamic pipes

**Files:**
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyPipe.java`
- Modify: `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java`

- [ ] **Step 1: Write failing pool, order, viewport, and identity tests**

```java
@Test void firstForwardUpdateCreatesSixIndependentEntriesExactlyOnce() {
    launchBeforeControllerUpdate();
    assertEquals(0, pipes().size());
    stepFrame();
    assertEquals(6, pipes().size());
    assertDistinctStableIds(pipes());
    assertEquals(1, layoutObjects().size());
    resizeToSuper32By9Width();
    stepFrames(3);
    assertEquals(6, pipes().size());
}

@Test void recycleMovesTheSameEntryAndAdvancesCounterPermutation() {
    FlappyPipe pipe = leftmostPipe();
    ObjectRefId id = objectRefId(pipe);
    forceRightEdgeLeftOfViewport(pipe);
    int before = controller().generationCounter();
    stepFrame();
    assertEquals(id, objectRefId(rightmostPipe()));
    assertEquals(before + 1, controller().generationCounter());
    assertEquals(expectedVariant(before), rightmostPipe().gapVariant());
    assertFalse(rightmostPipe().gateConsumed());
}
```

`resizeToSuper32By9Width()` is a test-fixture helper over the viewport-width configuration seam, not a new engine API: on the configured `SonicConfigurationService` instance, call `setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "SUPER_32_9")` before gameplay bootstrap, let the existing aspect resolver derive `SCREEN_WIDTH_PIXELS`, and assert the active camera width is 800 before checking that the six-entry pool still covers the viewport. Clear the session override during fixture teardown.

- [ ] **Step 2: Run and verify no dynamic pool exists**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration" test`

Expected: pipe-count and recycle assertions fail.

- [ ] **Step 3: Implement controller-owned movement and live recycling**

```java
private static final int PIPE_POOL_SIZE = 6;
private static final int PIPE_SPACING = 224;
private static final int PIPE_SPEED = 0x200;
private static final int[] GAP_VARIANTS = {2, 0, 4, 1, 3};

private void ensurePipePool() {
    if (poolInitialized) return;
    for (int slot = 0; slot < PIPE_POOL_SIZE; slot++) {
        int variant = GAP_VARIANTS[Math.floorMod(generationCounter++, GAP_VARIANTS.length)];
        int x = firstLeadX + slot * PIPE_SPACING;
        spawnFreeChild(() -> new FlappyPipe(buildSpawnAt(x, cameraMidY()), variant));
    }
    poolInitialized = true;
}
```

Provide `FlappyPipe(ObjectSpawn)` for generic probe construction and an overload receiving the fresh variant. Pipe mutable state is non-final centre X, subpixel X remainder, gap variant, and gate-consumed. `FlappyController` calls `pipe.advance(PIPE_SPEED)` in stable object-id order, finds the rightmost live pipe, and calls `pipe.recycleAfter(rightmostX + PIPE_SPACING, nextVariant)`. `FlappyPipe.update` is a no-op so motion happens exactly once.

The controller keeps no pipe references and queries `activeObjectsOfType(FlappyPipe.class)`. Six equals `ceil(800 / 224) + 2`, covering the variable width axis through `SUPER_32_9`; height remains the native 224.

```java
@Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
    return new FlappyController(context.spawn()); // never spawns pipes
}

@Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
    return new FlappyPipe(context.dynamicEntry().spawn());
}
```

Restored `poolInitialized=true` prevents controller respawn; each independent dynamic entry restores through `genericRecreate` with owner classloader and stable ID.

- [ ] **Step 4: Run pool and engine-level dynamic recreation tests**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.mods.code.TestModOwnedDynamicObjectRewind" test`

Expected: all tests pass with one controller, six stable dynamic entries, and no adoption duplicate.

- [ ] **Step 5: Commit**

```powershell
git add src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyPipe.java src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java
git commit -m "feat: recycle stable Flappy pipe entries"
```

### Task 3: Add exactly-once score, unconditional death, and deterministic restart

**Files:**
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyPipe.java`
- Modify: `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java`

- [ ] **Step 1: Write failing score/death/restart tests**

```java
@Test void onePipeScoresOncePerCycleAndResetEnablesTheNextCycle() {
    FlappyPipe pipe = putGateJustRightOfTails();
    stepUntilGatePasses();
    assertEquals(1, rings());
    assertTrue(pipe.gateConsumed());
    stepFrames(20);
    assertEquals(1, rings());
    forceRecycle(pipe);
    assertFalse(pipe.gateConsumed());
    passSameStableIdAgain(pipe);
    assertEquals(2, rings());
}

@Test void scoreCrossingOneHundredDoesNotRunCollectibleRingBonusLogic() {
    setRings(99);
    int livesBefore = lives();
    passOneGate();
    assertEquals(100, rings());
    assertEquals(livesBefore, lives());
    assertFalse(extraLifeMusicPlayed());
}

@Test void pipeAndVisibleBoundsAlwaysUseCrushDeath() {
    for (Protection protection : allRingShieldInvincibilitySuperStates()) {
        launchWith(protection);
        collideWithPipeBody();
        assertTrue(mainPlayer().getDead());
    }
    assertDiesAtY(camera().getMinY() + 0x10);
    assertDiesAtY(camera().getY() + 224);
}

@Test void engineRestartResetsRunAndRecreatesTheSameInitialSequence() {
    launchFlappy();
    List<Integer> initialVariants = gapVariants();
    int livesBefore = lives();
    collideWithPipeBody();
    stepUntilLevelRestartCompletes();
    stepFrame();
    assertEquals(livesBefore - 1, lives());
    assertEquals(0, rings());
    assertEquals(6, pipes().size());
    assertEquals(initialVariants, gapVariants());
    assertTrue(mainPlayer().getTailsFlightController().isActive());
    assertEquals((byte) 0xF0, mainPlayer().getDoubleJumpProperty());
}
```

- [ ] **Step 2: Run and verify scoring/death/restart failures**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration" test`

Expected: score, unconditional death, and restart assertions fail before implementation.

- [ ] **Step 3: Implement the inverted moving-gate rule and restart state**

```java
if (!pipe.gateConsumed() && pipe.centreX() < tails.getCentreX()) {
    LevelState score = services().levelGamestate();
    score.setRings(score.getRings() + 1);
    services().playSfx(GameSound.RING);
    pipe.consumeGate();
}

if (pipe.overlapsPlayableBounds(tails)
        || tails.getCentreY() <= services().camera().getMinY() + 0x10
        || tails.getCentreY() >= services().camera().getY() + 224) {
    tails.applyCrushDeath();
}
```

Use centre-coordinate APIs for ROM `x_pos` / `y_pos`; collision bounds may use explicit sprite extents. Recycling assigns the next gap variant and resets `gateConsumed=false`. Rings are the sole score and stock score remains untouched. Use `setRings`, not `addRings`, so 100/200-point crossings do not invoke collectible-ring extra-life/music side effects. Normal level restart reconstructs controller/pool state: routine false, `poolInitialized=false`, generation zero, rings zero, then the first normal update recreates the same six-variant prefix and activates native flight.

- [ ] **Step 4: Run gameplay, HUD counter, and native-flight regression tests**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.level.objects.TestHudRenderManager,com.openggf.sprites.managers.TestTailsFlightController" test`

Expected: all tests pass, including the explicit restart test.

- [ ] **Step 5: Commit**

```powershell
git add src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyPipe.java src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java
git commit -m "feat: add Flappy score death and restart"
```

### Task 4: Prove rewind exactness across construction, crossing, and recycle

**Files:**
- Modify: `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java`

- [ ] **Step 1: Add exact rewind transition assertions**

```java
@Test void rewindRestoresPoolIdentityGenerationGateAndScore() {
    launchAndCreatePool();
    ObjectRefId[] ids = pipeIds();
    RunState before = captureRunState();
    stepThroughScoreAndRecycle();
    RunState after = captureRunState();

    seek(before.frame());
    assertArrayEquals(ids, pipeIds());
    assertEquals(before, captureRunState());
    replayTo(after.frame());
    assertArrayEquals(ids, pipeIds());
    assertEquals(after, captureRunState());
}

@Test void seekAroundFirstUpdateNeverDuplicatesPool() {
    seek(keyframeBeforeFirstControllerUpdate());
    stepFrame();
    assertEquals(6, pipes().size());
    seek(keyframeAfterPoolConstruction());
    assertEquals(6, pipes().size());
}
```

- [ ] **Step 2: Run focused rewind and coverage tests**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.game.rewind.coverage.TestRewindCoverageGuard" test`

Expected: any uncaptured final scalar, object reference, duplicate entry, or unstable ID fails.

- [ ] **Step 3: Keep all mutable sample state in compact scalar capture**

Controller fields are routine, pool-initialized, anchor X, and generation counter. Pipe fields are centre X, subpixel X remainder, gap variant, and gate-consumed. Keep pipe references out of the controller. Do not baseline ordinary sample state; the engine-level `TestModOwnedDynamicObjectRewind` remains the classloader-path owner.

- [ ] **Step 4: Run the full dynamic rewind gate**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.mods.code.TestModOwnedDynamicObjectRewind,com.openggf.level.objects.TestObjectManagerDynamicChainRewindRestore,com.openggf.game.rewind.coverage.TestRewindCoverageGuard" test`

Expected: all tests pass with identical IDs and scalar/run state after replay.

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java
git commit -m "test: prove native Flappy rewind exactness"
```

### Task 5: Verify presentation through palette state, HUD calls, and decoded patterns

**Files:**
- Modify: `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleFlappyLevelSource.java`
- Modify: `src/test/java/com/openggf/level/objects/TestHudRenderManager.java`

- [ ] **Step 1: Add feasible headless presentation assertions**

```java
@Test void customZoneComposesTailsLifeHudSkyAndPipeData() {
    launchFlappy();
    assertEquals(expectedTailsLine0(), segaColors(level().getPalette(0)));
    assertEquals("host:s3k-hud",
            paletteOwnership().ownerAt(PaletteSurface.NORMAL, 0, livesHudColorIndex()));
    assertClaimedSegaWordsApplied(levelSourceClaims(), level());

    ObjectSpriteSheet pipe = resolvedObjectArt().getSheet("sample-flappy:pipe");
    assertEquals(2, pipe.getFrameCount());
    assertPieceSize(pipe.getFrame(0).pieces().getFirst(), 4, 4);
    assertPieceSize(pipe.getFrame(1).pieces().getFirst(), 4, 2);
    assertTrue(Arrays.stream(pipe.getPatterns()).anyMatch(this::hasNonZeroNibble));
}
```

`assertClaimedSegaWordsApplied` iterates the parsed sparse `paletteClaims` and
compares each declared Sega word with the corresponding `level().getPalette(line)`
color; do not invent a bulk `Level.getPalettes()` API. The two exact pipe piece
sizes come from the maintained `pipe-sheet.yaml`: 32x32 pixels and 32x16 pixels.

Extend `TestHudRenderManager` using its existing Mockito seam:

```java
manager.setProfile(flappyProfile());
manager.draw(levelStateWithRings(17), tails);
verify(graphicsManager, never()).renderPatternWithId(scoreRowPatternId(), any(), eq(16), eq(8));
verify(graphicsManager).renderPatternWithId(scoreLabelPatternId(), any(), eq(16), eq(40));
verifyRingMetricDigitsAtRightX(17, 64, 40);
verifyNoZeroRingsFlash();
```

Do not assert framebuffer pixels: `GraphicsManager.executeCapturedCommands` returns before GL execution in headless mode. Do not add framebuffer capture work to this plan.

- [ ] **Step 2: Run palette/HUD/art structural tests**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.tools.modsdk.TestSampleFlappyLevelSource,com.openggf.level.objects.TestHudRenderManager,com.openggf.tools.modsdk.TestArtConverter" test`

Expected: tests identify palette ownership, HUD row calls, or baked mapping/pattern ordering independently.

- [ ] **Step 3: Correct only the owning declaration or bridge**

Flappy declares no character colors. If creator claims mismatch, change the v2 level claims/generator. If HUD ownership mismatches, fix the adapter's custom-zone bridge while keeping stock S3K tests green. If pipe structure mismatches, fix `pipe-sheet.yaml`/source art; never compensate in `SpritePieceRenderer` or undo the column-major converter fix.

- [ ] **Step 4: Run sample and stock presentation regression tests**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.tools.modsdk.TestSampleFlappyLevelSource,com.openggf.level.objects.TestHudRenderManager,com.openggf.game.sonic3k.TestSonic3kLivesHudPaletteOverride,com.openggf.game.sonic3k.TestS3kPaletteOwnershipRegistryIntegration,com.openggf.tools.modsdk.TestArtConverter" test`

Expected: all tests pass without a framebuffer-capture dependency.

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java src/test/java/com/openggf/tools/modsdk/TestSampleFlappyLevelSource.java src/test/java/com/openggf/level/objects/TestHudRenderManager.java
git commit -m "test: lock Flappy palette HUD and pipe presentation"
```

### Task 6: Replace the old guide atomically, package, and install locally

**Files:**
- Create: `docs/modding/guides/native-tails-flappy.md`
- Delete: `docs/modding/guides/flappy-remix.md`
- Modify: `docs/modding/guides/standalone-platformer.md`
- Modify: `docs/modding/guides/ai-art.md`
- Modify: `docs/modding/index.md`
- Modify: `docs/modding/samples/index.md`
- Modify: `docs/modding/content-mods.md`
- Modify: `src/test/resources/mods/sample-flappy-src/README.md`
- Modify: `src/test/resources/mods/sample-flappy-src/project/README.md`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java`
- Modify: `CHANGELOG.md`
- Modify: `.gitignore`
- Generate locally, do not commit: `mods/sample-flappy-mod.jar`

- [ ] **Step 1: Write the native guide from final source**

Cover anchorless game-start resolution, scoped Tails/input/HUD policies, nested equal bounds, initial screen-anchor relocation, `0xF0` refill, independent dynamic-entry recreation, stable recycling, counter variants, gate reset, ring-backed score, fatal bounds, and sparse palette ownership. State that the background is stationary and a later recycling cloud/ground object can add motion without a scroll framework.

- [ ] **Step 2: Update every remaining old-guide link before deletion**

Move `standalone-platformer.md`'s Chapter-4 level link to the matching native-guide anchor and reword its introductory plain-text `flappy-remix` mention. Update all three `ai-art.md` references (project structure, Chapter-6 pipe/score/death, and pipe swap target) to `native-tails-flappy.md`. Update the Flappy source README and both indexes/content reference. The Chapter-3 ROM borrowing link already points to `rom-art-remix.md` from Plan C.

Only after all replacements exist, delete `flappy-remix.md`. In the same change, remove Plan C's temporary “old guide still exists” assertion from `TestSampleModsPackage`, then arm the repository-wide check that fails on any remaining `flappy-remix` text under `docs/modding` or `sample-flappy-src`.

- [ ] **Step 3: Run the complete cross-sample gate**

Run: `mvn "-Dsonic2.rom.path=s2.gen" "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.tools.modsdk.TestSampleFlappyLevelSource,com.openggf.mods.code.TestSampleFlappyRegistration,com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.mods.code.TestSampleRomArtRemixIntegration,com.openggf.mods.code.TestModOwnedDynamicObjectRewind,com.openggf.mods.TestModApiSignatureSurface" test`

Expected: all tests execute and pass; gallery count remains eight, the remix owns ROM-art intake, and no old-guide reference remains.

After Maven returns, parse both `target/surefire-reports/TEST-com.openggf.mods.code.TestSampleFlappyIntegration.xml` and `target/surefire-reports/TEST-com.openggf.mods.code.TestSampleRomArtRemixIntegration.xml`; require `skipped="0"` for each. Their test methods use `assumeTrue` when the relevant ROM is unavailable, per repository convention, but a skipped ROM-gated integration is a completion-gate failure.

- [ ] **Step 4: Build engine/sample and copy the ignored local package**

```powershell
mvn package
$build = Join-Path $env:TEMP ("sample-flappy-" + [guid]::NewGuid())
& src/test/resources/mods/sample-flappy-src/build.ps1 `
  -EngineJar (Resolve-Path target/OpenGGF-0.6.prerelease.jar) `
  -SdkJar (Resolve-Path target/OpenGGF-0.6.prerelease-openggf-mod-sdk.jar) `
  -OutputDirectory $build
if ($LASTEXITCODE -ne 0) { throw "sample-flappy build failed" }
New-Item -ItemType Directory -Force mods | Out-Null
Copy-Item -Force (Join-Path $build 'target/sample-flappy-mod.jar') mods/sample-flappy-mod.jar
git check-ignore mods/sample-flappy-mod.jar
```

Add `/mods/` to `.gitignore` before this packaging check; the directory is currently merely untracked, so `git check-ignore` cannot pass without this explicit local-package rule.

Run: `java -jar target/OpenGGF-0.6.prerelease-jar-with-dependencies.jar`

Manual acceptance: Flappy is the initial S3K data-select destination, visible Tails flies natively, horizontal input is suppressed, pipes approach/recycle, pipe/top/bottom contact kills, restart is deterministic, and HUD shows ring-backed `SCORE` with correct palette composition.

- [ ] **Step 5: Commit maintained docs/source only**

```powershell
git add .gitignore docs/modding/guides/native-tails-flappy.md docs/modding/guides/flappy-remix.md docs/modding/guides/standalone-platformer.md docs/modding/guides/ai-art.md docs/modding/index.md docs/modding/samples/index.md docs/modding/content-mods.md src/test/resources/mods/sample-flappy-src/README.md src/test/resources/mods/sample-flappy-src/project/README.md src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java CHANGELOG.md
git commit -m "docs: publish native-Tails Flappy sample"
```

## Completion gate

- [ ] Run Task 6's cross-sample command with both explicit ROM properties and require `skipped="0"` in both ROM-gated integration XML reports; missing ROMs block the gate even though each test uses `assumeTrue`.
- [ ] Run `mvn package`.
- [ ] Confirm the sample requires `>=2.4.0 <3.0.0` and 2.3 remains untouched.
- [ ] Confirm S3K `StockProgressionAnchors` remains empty and Flappy's `insertAfter` is null.
- [ ] Confirm fresh and rewind-restored sessions each have one controller and six pipes.
- [ ] Confirm no ROM-art, forced-scroll, hidden-player, direct vertical-physics, world-wrap, or framebuffer-capture code remains in Flappy.
- [ ] Confirm raw recorded input retains left/right while effective gameplay input suppresses them.
- [ ] Confirm top death uses `cameraMinY + 0x10`, bottom death uses camera Y + 224, and pipe death uses `applyCrushDeath()`.
- [ ] Confirm all former `flappy-remix.md` links resolve to one of the two new guides.
- [ ] Confirm `mods/sample-flappy-mod.jar` exists locally but is ignored and unstaged.
- [ ] Confirm `git diff --check` is clean.
