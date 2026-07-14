# Native-Tails Flappy Sample Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Flappy gallery mod as an S3K custom start level where native playable Tails remains visible and flies in place while a stable pool of pipes approaches and recycles.

**Architecture:** The S3K format-v2 baked level is a fixed 224px-high sky strip containing only a layout controller. Destination-scoped Mod API 2.3 policies select Tails, suppress horizontal input after the recorded input snapshot, and remap the HUD rings metric to a `SCORE` row. The controller anchors native Tails, refills the ROM-authored flight property to `0xF0`, and creates six independent dynamic pipes once during fresh forward play. Pipes move/recycle in screen world space with counter-derived gaps and stable rewind identities.

**Tech Stack:** Java 21, Maven, JUnit 5, `ggfmod`, Mod API 2.3, S3K custom-zone adapter, compact rewind snapshots, headless gameplay integration.

**Design reference:** `docs/superpowers/specs/2026-07-14-flappy-native-tails-design.md`

**Prerequisites:** Complete the converter, S3K adapter, gameplay-policy, and ROM-art-remix plans in that order. In particular, `TestSampleRomArtRemixIntegration` must be green before removing Flappy's `registerRomObjectArt` call.

**Commit policy:** Keep the repository trailer block on every commit. Tasks 1-7 use `Changelog: n/a: covered by the aggregate native-Flappy entry in Task 8`; Task 8 stages the guide and changelog and uses `Changelog: updated`, `Guide: updated`, with other mappings marked accurately.

---

## File map

- Rewrite `FlappySampleMod`, `FlappyController`, and `FlappyPipe` in the existing sample project.
- Replace the S2 format-v1 level source with an S3K format-v2 fixed-camera export.
- Keep the corrected rectangular pipe sheet and column-major bake contract.
- Rewrite Flappy package, level-source, registration, runtime, rewind, and pixel tests for native Tails.
- Add a native-Tails Flappy guide and update the maintained gallery documentation.
- Build and copy the final packaged mod into local `mods/` only after the source/test contract is green; do not commit generated packages.

### Task 1: Switch the sample contract from S2 ROM art to S3K Mod API 2.3

**Files:**
- Modify: `src/test/resources/mods/sample-flappy-src/sample.properties`
- Modify: `src/test/resources/mods/sample-flappy-src/README.md`
- Modify: `src/test/resources/mods/sample-flappy-src/project/README.md`
- Modify: `src/test/resources/mods/sample-flappy-src/project/pom.xml`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/resources/META-INF/openggf-mod.yaml`
- Create: `src/test/java/com/openggf/mods/code/TestSampleFlappyRegistration.java`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java`

- [ ] **Step 1: Write failing manifest/registration expectations**

```java
@Test void flappyIsAnS3kApi23PatchWithNoRomArtRequest() throws Exception {
    ModDescriptor descriptor = scan(FLAPPY);
    assertEquals("s3k", descriptor.manifest().baseGame());
    assertEquals(">=2.3.0 <3.0.0", descriptor.manifest().engineApiRange().toString());
    ModRegistrationPlan plan = compileAndRegister(FLAPPY);
    assertTrue(plan.romObjectArt().isEmpty());
    assertEquals(1, plan.zones().size());
    assertTrue(plan.zones().getFirst().gameStart());
    assertEquals(CharacterKey.TAILS, onlyLaunchTeam(plan).main());
}
```

Also assert one input filter, one HUD profile, controller/pipe creators, and pipe baked art are registered for the same owner-tagged zone.

- [ ] **Step 2: Run and verify current S2/2.1/ROM-art failures**

Run: `mvn "-Dtest=com.openggf.mods.code.TestSampleFlappyRegistration,com.openggf.tools.modsdk.TestSampleModsPackage" test`

Expected: the manifest says S2/API 2.1, the zone is not game-start, and a Tails ROM-art request remains.

- [ ] **Step 3: Update manifest and transactional registration**

```java
ZoneKey flappy = ZoneKey.mod("sample-flappy", "flappy-garden");
context.registerObject("controller", (spawn, registry) -> new FlappyController(spawn));
context.registerObject("pipe", (spawn, registry) -> new FlappyPipe(spawn));
context.registerObjectArt("pipe", new BakedSheetRef("art/pipe.ggfs"));
context.registerZone(new ModZoneContribution("flappy-garden",
        new BakedLevelRef("levels/flappy/level.json"), "aiz1", null, true));
context.registerLaunchTeam(new ModLaunchTeamContribution(
        flappy, CharacterKey.TAILS, List.of()));
context.registerInputFilter(new ModInputFilterContribution(flappy,
        FlappySampleMod::suppressHorizontal));
context.registerHudProfile(new ModHudProfileContribution(flappy, flappyHud()));
```

The filter returns a new `PlayerInputState` with left/right cleared from held and pressed masks and every other field preserved. The HUD profile hides stock score, relabels the rings metric as `SCORE` at the rings row, keeps time/lives, and disables zero-rings flashing.

- [ ] **Step 4: Run registration, gallery, and replacement-consumer tests**

Run: `mvn "-Ds2.rom.path=s2.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyRegistration,com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.mods.code.TestSampleRomArtRemixIntegration" test`

Expected: all pass; ROM-art executable coverage remains owned by the remix sample.

- [ ] **Step 5: Commit**

```powershell
git add src/test/resources/mods/sample-flappy-src src/test/java/com/openggf/mods/code/TestSampleFlappyRegistration.java src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java
git commit -m "feat: retarget Flappy sample to S3K"
```

### Task 2: Replace the level with a fixed-camera S3K format-v2 sky strip

**Files:**
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/mod/level-source/level.json`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/mod/level-source/binary-assets.properties`
- Modify: `src/test/java/com/openggf/tools/modsdk/SampleFlappyAssetGenerator.java`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleFlappyLevelSource.java`

- [ ] **Step 1: Rewrite the test first for the fixed model**

```java
@Test void s3kLevelHasOnlyControllerNoRingsAndPinnedCamera() throws Exception {
    JsonNode level = readSource();
    assertEquals(2, level.path("formatVersion").asInt());
    assertEquals("s3k", level.path("game").asText());
    assertEquals(1, level.path("objects").size());
    assertEquals("sample-flappy:controller",
            level.path("objects").get(0).path("key").asText());
    assertEquals(0, level.path("rings").size());
    assertEquals(level.path("cameraMinX"), level.path("cameraMaxX"));
    assertEquals(level.path("cameraMinY"), level.path("cameraMaxY"));
    assertEquals(224, level.path("visibleHeight").asInt());
}
```

Assert S3K zone-set metadata, a host-supplied character palette, sparse creator claims for sky/pipe colors only, and no character-colored entries in those claims.

- [ ] **Step 2: Run and verify S2/static-pipe failures**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleFlappyLevelSource" test`

Expected: format/game/palette metadata fail and `objects[]` still contains pipe placements.

- [ ] **Step 3: Generate deterministic v2 assets and one controller placement**

Use the S3K adapter's explicit zone-set/runtime profile fields. Pin both camera axes to the spawn viewport; make the baked world wide enough for the `SUPER_32_9` viewport plus offscreen lead/trailing margins without relying on scrolling. Height remains 224. The source has no ring placements and no pipe placements.

Retain `pipe.png` and `pipe-sheet.yaml`, including the non-square column-major marker verified by the converter plan. Declare only the palette entries actually indexed by sky and pipes.

- [ ] **Step 4: Regenerate, convert, and validate source determinism**

Run: `mvn "-DskipTests" test-compile`

Run: `java -cp target/test-classes com.openggf.tools.modsdk.SampleFlappyAssetGenerator`

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleFlappyLevelSource,com.openggf.tools.modsdk.TestLevelConverter" test`

Expected: tests pass and a second generator run produces no tracked diff.

- [ ] **Step 5: Commit**

```powershell
git add src/test/resources/mods/sample-flappy-src/project/src/main/mod src/test/java/com/openggf/tools/modsdk/SampleFlappyAssetGenerator.java src/test/java/com/openggf/tools/modsdk/TestSampleFlappyLevelSource.java
git commit -m "feat: add fixed-camera S3K Flappy level"
```

### Task 3: Drive native Tails without replacing or hiding the playable

**Files:**
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java`
- Modify: `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java`

- [ ] **Step 1: Write failing native-player behavior tests**

```java
@Test void controllerKeepsNativeTailsVisibleAnchoredAndFlying() {
    launchFlappy();
    AbstractPlayableSprite tails = mainPlayer();
    int anchor = tails.getCentreX();
    drive(leftAndJump());
    stepFrames(120);
    assertEquals(CharacterKey.TAILS, mainCharacterKey());
    assertFalse(tails.isHidden());
    assertEquals(anchor, tails.getCentreX());
    assertEquals(0, tails.getXSpeed());
    assertEquals(0, tails.getGSpeed());
    assertTrue(tails.getTailsFlightController().isActive());
    assertEquals((byte) 0xF0, tails.getDoubleJumpProperty());
    assertEquals(pinnedCameraX(), camera().getX());
    assertEquals(pinnedCameraY(), camera().getY());
}
```

Assert jump still affects native vertical flight, left/right never reaches playable movement or level-event input, and no forced-scroll request is made.

- [ ] **Step 2: Run and verify hidden/direct-physics behavior fails**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration" test`

Expected: current controller hides Tails, advances X, renders borrowed art, and requests forced scrolling.

- [ ] **Step 3: Replace seizure/render physics with native flight maintenance**

```java
private void maintainNativeFlight(AbstractPlayableSprite tails) {
    if (!tails.getTailsFlightController().isActive()) {
        tails.getTailsFlightController().activate();
    }
    tails.setDoubleJumpProperty((byte) 0xF0);
    tails.setCentreX(anchorX);
    tails.setXSpeed((short) 0);
    tails.setGSpeed((short) 0);
}
```

Capture `anchorX` at fresh activation, never call `setHidden`, never draw a replacement bird, never apply object control, and never request camera scroll. Run maintenance every active frame after normal native flight has observed filtered input. On post-death respawn, reacquire the native Tails instance, reset run state/score, and reactivate flight through the same path.

- [ ] **Step 4: Prove the MGZ2-authored refill and stock isolation**

Add an assertion that Flappy writes `0xF0` every active frame, matching `SidekickCpuController.updateMgzBossTransitionCarryInput()`. Add a stock S3K test launch outside the mod destination and verify Tails' flight counter decreases normally; no global fatigue rule or character patch may be introduced.

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.sprites.managers.TestTailsFlightController" test`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java
git commit -m "feat: use native Tails flight in Flappy"
```

### Task 4: Create six independent dynamic pipes once and recycle live instances

**Files:**
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyPipe.java`
- Modify: `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java`

- [ ] **Step 1: Write failing pool, order, and identity tests**

```java
@Test void firstForwardUpdateCreatesFixedIndependentPoolOnlyOnce() {
    launchFlappyBeforeFirstObjectUpdate();
    assertEquals(0, pipes().size());
    stepFrame();
    assertEquals(6, pipes().size());
    assertDistinctStableIds(pipes());
    assertEquals(1, layoutObjects().size());
    stepFrames(10);
    assertEquals(6, pipes().size());
}

@Test void recyclingMovesSameInstanceAndUsesCounterPermutation() {
    FlappyPipe pipe = leftmostPipe();
    ObjectRefId id = objectRefId(pipe);
    forceOffLeft(pipe);
    int oldGeneration = controller().generationCounter();
    stepFrame();
    assertSameObjectId(id, rightmostPipe());
    assertEquals(oldGeneration + 1, controller().generationCounter());
    assertEquals(expectedVariant(oldGeneration + 1), rightmostPipe().gapVariant());
    assertFalse(rightmostPipe().gateConsumed());
}
```

- [ ] **Step 2: Run and verify layout/static implementation fails**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration" test`

Expected: no controller-created pool exists and current pipes derive immutable gaps from placements.

- [ ] **Step 3: Implement fresh-forward construction and mutable pipe state**

```java
private static final int PIPE_POOL_SIZE = 6;
private static final int PIPE_SPACING = 224;
private static final int PIPE_SPEED = 0x200;
private static final int[] GAP_VARIANTS = {2, 0, 4, 1, 3};

private void ensurePipePool() {
    if (poolInitialized) return;
    for (int slot = 0; slot < PIPE_POOL_SIZE; slot++) {
        int x = firstLeadX + slot * PIPE_SPACING;
        int variant = GAP_VARIANTS[Math.floorMod(generationCounter++, GAP_VARIANTS.length)];
        spawnFreeChild(() -> new FlappyPipe(buildSpawnAt(x, initialPipeY), variant));
    }
    poolInitialized = true;
}
```

Adapt constructor syntax to the frozen creator surface; keep centre X, subpixel remainder, gap variant, and gate-consumed flag as non-final scalar state. `FlappyPipe.update` shifts left by fixed subpixel speed. The controller queries `activeObjectsOfType(FlappyPipe.class)`, finds the rightmost live instance, and calls a pipe-local recycle method; it holds no pipe references.

Pool sizing is fixed for the variable width axis through `SUPER_32_9`; height is always 224. Resizing does not spawn or remove objects.

- [ ] **Step 4: Keep reconstruction independent from controller adoption**

```java
@Override
public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
    return new FlappyController(context.spawn()); // never ensurePipePool here
}

@Override
public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
    return new FlappyPipe(context.dynamicEntry().spawn());
}
```

The restored controller receives captured `poolInitialized=true`; restored pipe entries come through `genericRecreate` with owner classloader, captured object ID, and scalar restoration. A genuinely fresh controller with false creates the pool on its first ordinary update.

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.mods.code.TestModOwnedDynamicObjectRewind" test`

Expected: all tests pass with six stable independent entries and no adoption duplicates.

- [ ] **Step 5: Commit**

```powershell
git add src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyPipe.java src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java
git commit -m "feat: recycle stable Flappy pipe pool"
```

### Task 5: Score each pipe cycle once and make every boundary fatal

**Files:**
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyPipe.java`
- Modify: `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java`

- [ ] **Step 1: Write failing inverted-gate and unconditional-death tests**

```java
@Test void gateScoresOnceWhenPipePassesTailsAndResetsOnRecycle() {
    FlappyPipe pipe = placeGateJustRightOfTails();
    stepUntilGatePasses();
    assertEquals(1, rings());
    assertTrue(pipe.gateConsumed());
    stepFrames(20);
    assertEquals(1, rings());
    recycle(pipe);
    assertFalse(pipe.gateConsumed());
    passSamePipeAgain();
    assertEquals(2, rings());
}

@Test void pipeTopAndBottomKillRegardlessOfProtection() {
    for (Protection state : allProtectionStates()) {
        launchWith(state);
        collideWithPipeBody();
        assertTrue(mainPlayer().getDead());
    }
}
```

Add exact top boundary `camera.getMinY() + 0x10` and visible bottom-boundary cases.

- [ ] **Step 2: Run and verify old right-edge/ring-sensitive behavior fails**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration" test`

Expected: scoring follows the old moving-player rule and collision uses ordinary hurt/death.

- [ ] **Step 3: Implement the moving-gate rule and unconditional death**

```java
if (!pipe.gateConsumed() && pipe.centreX() < tails.getCentreX()) {
    services().levelGamestate().addRings(1);
    services().playSfx(GameSound.RING);
    pipe.consumeGate();
}

if (pipe.overlapsPlayableBounds(tails)
        || tails.getCentreY() <= services().camera().getMinY() + 0x10
        || tails.getCentreY() >= visibleBottomY) {
    tails.applyCrushDeath();
}
```

Use playable collision/render bounds consistently; do not mix top-left `getX()/getY()` with ROM centre coordinates. Recycling must set the next gap variant before clearing `gateConsumed`. The ring counter is the sole run score; leave stock score untouched and place no rings.

- [ ] **Step 4: Run scoring, collision, lives, and respawn tests**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.level.objects.TestHudRenderManager" test`

Expected: all tests pass; death decrements lives through the engine lifecycle and a fresh run resets rings/generation deterministically.

- [ ] **Step 5: Commit**

```powershell
git add src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyPipe.java src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java
git commit -m "feat: add Flappy scoring and fatal bounds"
```

### Task 6: Prove rewind exactness across spawn, crossing, and recycle

**Files:**
- Modify: `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java`
- Modify only if coverage identifies a sample field: `src/test/resources/rewind/coverage-baseline.txt`

- [ ] **Step 1: Add rewind assertions at all state transitions**

```java
@Test void rewindRestoresStablePoolGenerationAndGateState() {
    launchAndCreatePool();
    PoolState beforeCrossing = capturePoolState();
    ObjectRefId[] ids = pipeIds();
    stepThroughScoreAndRecycle();
    PoolState afterRecycle = capturePoolState();

    seek(beforeCrossing.frame());
    assertArrayEquals(ids, pipeIds());
    assertEquals(beforeCrossing, capturePoolState());
    replayTo(afterRecycle.frame());
    assertArrayEquals(ids, pipeIds());
    assertEquals(afterRecycle, capturePoolState());
}
```

Also seek to a keyframe before the controller's first normal update, then forward once and assert exactly six entries; seek to a keyframe after pool construction and assert reconstruction does not spawn a second pool.

- [ ] **Step 2: Run the focused rewind test and coverage analyzer**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.game.rewind.coverage.TestRewindCoverageGuard" test`

Expected: tests expose any final scalar, object reference, missing stable ID, or duplicate recreation.

- [ ] **Step 3: Fix sample state ownership, not the baseline**

Controller mutable state is limited to routine, pool-initialized, anchor X, generation counter, and restart coordination. Pipe mutable state is centre X/subpixel remainder, gap variant, and gate-consumed. Keep direct pipe references out of the controller. Add an intentional baseline entry only if the analyzer reports a provably derived/transient field and document its derivation inline; ordinary sample state must be captured.

- [ ] **Step 4: Run the dynamic and full sample rewind gates**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.mods.code.TestModOwnedDynamicObjectRewind,com.openggf.level.objects.TestObjectManagerDynamicChainRewindRestore,com.openggf.game.rewind.coverage.TestRewindCoverageGuard" test`

Expected: all tests pass with identical IDs, positions, variants, gate flags, generation, and rings before/after replay.

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java src/test/resources/rewind/coverage-baseline.txt
git commit -m "test: prove native Flappy rewind exactness"
```

If the baseline is unchanged, omit it from `git add`.

### Task 7: Lock palette, HUD, and rectangular-pipe presentation

**Files:**
- Modify: `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleFlappyLevelSource.java`
- Modify: `src/test/resources/mods/sample-flappy-src/project/src/main/mod/pipe-sheet.yaml`
- Modify only if a generated correction is needed: `src/test/resources/mods/sample-flappy-src/project/src/main/mod/pipe.png`

- [ ] **Step 1: Add exact palette and rendered-pixel probes**

```java
@Test void hostAndCreatorPaletteClaimsComposeWithoutCorruption() {
    launchFlappy();
    assertPaletteProbe(TAILS_BODY_INDEX, EXPECTED_TAILS_RGB);
    assertPaletteProbe(TAILS_LIFE_ICON_INDEX, EXPECTED_TAILS_RGB);
    assertRenderedPixel(tailsProbe(), EXPECTED_TAILS_RGB);
    assertRenderedPixel(lifeIconProbe(), EXPECTED_TAILS_RGB);
    assertRenderedPixel(scoreLabelProbe(), EXPECTED_HUD_RGB);
    assertRenderedPixel(skyProbe(), EXPECTED_SKY_RGB);
    assertRenderedPixel(rectangularPipeProbe(), EXPECTED_PIPE_RGB);
}
```

Record draw commands to assert stock score is absent, `SCORE` uses the rings-row coordinates/value, time/lives remain, and zero-score flashing is disabled.

- [ ] **Step 2: Run and capture any presentation mismatch**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.tools.modsdk.TestSampleFlappyLevelSource,com.openggf.tools.modsdk.TestArtConverter" test`

Expected: all probes pass after adapter/policy work; any mismatch identifies palette ownership, HUD row selection, or baked art ordering separately.

- [ ] **Step 3: Correct only the owning layer**

Do not ship character colors in creator claims, change `SpritePieceRenderer`, or compensate for column-major ordering in sample code. Correct a pipe source/sheet declaration only if the converter output is wrong for that source. Correct host HUD/character claim wiring only in the adapter plan's ownership bridge and retain its stock-zone tests.

- [ ] **Step 4: Run stock S3K and sample presentation regression tests**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.game.sonic3k.TestSonic3kLivesHudPaletteOverride,com.openggf.level.objects.TestHudRenderManager,com.openggf.tools.modsdk.TestArtConverter" test`

Expected: native Tails, life icon, HUD, sky, and pipes render correctly while stock S3K remains unchanged.

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java src/test/java/com/openggf/tools/modsdk/TestSampleFlappyLevelSource.java src/test/resources/mods/sample-flappy-src/project/src/main/mod/pipe-sheet.yaml src/test/resources/mods/sample-flappy-src/project/src/main/mod/pipe.png
git commit -m "test: lock Flappy palette and HUD presentation"
```

Omit unchanged generated art from `git add`.

### Task 8: Finish documentation, package, and local test installation

**Files:**
- Create: `docs/modding/guides/native-tails-flappy.md`
- Modify: `docs/modding/index.md`
- Modify: `docs/modding/samples/index.md`
- Modify: `docs/modding/content-mods.md`
- Modify: `src/test/resources/mods/sample-flappy-src/README.md`
- Modify: `src/test/resources/mods/sample-flappy-src/project/README.md`
- Modify: `CHANGELOG.md`
- Generate locally, do not commit: `mods/sample-flappy-mod.jar`

- [ ] **Step 1: Write the guide against the final source**

Explain fixed-camera obstacle motion, the four scoped policy contributions, native Tails flight with per-frame `0xF0` refill, independent dynamic-entry rewind, stable live recycling, counter-derived variants, gate reset, sparse S3K palette ownership, and ring-backed score. State the v1 trade-off: the background is stationary; a later recycling cloud/ground object may add motion without a scroll framework.

- [ ] **Step 2: Run the complete focused suite before packaging**

Run: `mvn "-Ds2.rom.path=s2.gen" "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.tools.modsdk.TestSampleFlappyLevelSource,com.openggf.mods.code.TestSampleFlappyRegistration,com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.mods.code.TestSampleRomArtRemixIntegration,com.openggf.mods.code.TestModOwnedDynamicObjectRewind,com.openggf.mods.TestModApiSignatureSurface" test`

Expected: all tests pass; both maintained samples own their distinct contracts.

- [ ] **Step 3: Build engine artifacts and the sample package**

Run: `mvn package`

Run:

```powershell
$build = Join-Path $env:TEMP ("sample-flappy-" + [guid]::NewGuid())
& src/test/resources/mods/sample-flappy-src/build.ps1 `
  -EngineJar (Resolve-Path target/OpenGGF-0.6.prerelease.jar) `
  -SdkJar (Resolve-Path target/OpenGGF-0.6.prerelease-openggf-mod-sdk.jar) `
  -OutputDirectory $build
if ($LASTEXITCODE -ne 0) { throw "sample-flappy build failed" }
```

Expected: engine package and `sample-flappy` `.ggfmod`/JAR output build successfully against the SDK artifact without bundling the SDK or any ROM.

- [ ] **Step 4: Install the generated mod for manual testing**

```powershell
New-Item -ItemType Directory -Force mods | Out-Null
Copy-Item -Force (Join-Path $build 'target/sample-flappy-mod.jar') mods/sample-flappy-mod.jar
```

Verify `mods/sample-flappy-mod.jar` is ignored/untracked, then launch:

Run: `java -jar target/OpenGGF-0.6.prerelease-jar-with-dependencies.jar`

Manual acceptance: Flappy starts before AIZ1, Tails is visible, horizontal input does nothing, jump drives native flight, pipes approach/recycle, contact/top/bottom kill, and HUD shows ring-backed `SCORE` with correct palettes.

- [ ] **Step 5: Commit only maintained source/docs**

```powershell
git add docs/modding/guides/native-tails-flappy.md docs/modding/index.md docs/modding/samples/index.md docs/modding/content-mods.md src/test/resources/mods/sample-flappy-src/README.md src/test/resources/mods/sample-flappy-src/project/README.md CHANGELOG.md
git commit -m "docs: publish native-Tails Flappy sample"
```

## Completion gate

- [ ] Run the Task 8 focused command.
- [ ] Run `mvn package` with S2/S3K ROM properties where required.
- [ ] Confirm fresh and rewind-restored sessions each contain exactly one controller and six pipes.
- [ ] Confirm Flappy contains no `registerRomObjectArt`, forced-scroll, hidden-player, direct vertical-physics, or world-wrap code.
- [ ] Confirm raw recorded input retains left/right while effective gameplay input suppresses them.
- [ ] Confirm top death uses `cameraMinY + 0x10` and pipe death uses `applyCrushDeath()`.
- [ ] Confirm host character/HUD palette probes and rectangular pipe probes pass.
- [ ] Confirm `mods/sample-flappy-mod.jar` exists locally but is not staged.
- [ ] Confirm `git diff --check` is clean.
