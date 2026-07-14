# Sonic 3&K Mod-Zone Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a host-owned adapter that loads bounded, original-data mod zones in Sonic 3&K without changing stock S3K loading or the existing Sonic 2/standalone contracts.

**Architecture:** Replace the S2-specific `ModZoneLoader.load(...)` call with a typed `ModZoneAdapter` capability forwarded by `GameModule`. Format v2 adds typed S3K zone-set metadata and sparse creator palette claims; `Sonic3kLevel.InMemoryBuilder` consumes prepared arrays while the S3K adapter supplies host character/ring assets and an explicit empty runtime profile. Palette reservations are validated before publication and HUD palette uploads join the shared ownership registry only for custom S3K zones.

**Tech Stack:** Java 21, Maven, JUnit 5, Jackson, OpenGGF Mod API/signature guards, headless gameplay fixtures.

**Design reference:** `docs/superpowers/specs/2026-07-14-s3k-mod-zone-adapter-design.md`

**Commit policy:** Keep the repository trailer block on every commit. For Tasks 1-9, use `Changelog: n/a: covered by the aggregate S3K mod-zone entry in Task 10`; Task 10 stages `CHANGELOG.md` and uses `Changelog: updated`, `Guide: updated`, with other mappings marked accurately.

---

## File map

- Create `src/main/java/com/openggf/mods/code/ModZoneAdapter.java`: typed base-game capability.
- Create `src/main/java/com/openggf/mods/code/ModZoneRuntimeProfile.java`: immutable explicit runtime obligations for a prepared custom zone.
- Create `src/main/java/com/openggf/mods/code/ModPaletteClaim.java`: sparse creator-owned Genesis palette entry.
- Create `src/main/java/com/openggf/mods/code/ModPaletteUsageValidator.java`: indexed-art-to-claim validation.
- Create `src/main/java/com/openggf/game/sonic2/Sonic2ModZoneAdapter.java`: preserves the current S2 construction path.
- Create `src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneAdapter.java`: validates S3K metadata and builds an in-memory S3K level.
- Create `src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneRuntimeProfile.java`: flat scroll and explicit empty animation/PLC/render/event defaults.
- Create `src/main/java/com/openggf/game/sonic3k/S3kCustomZonePaletteBridge.java`: host character/HUD reservations and registry submissions.
- Create `src/test/java/com/openggf/game/sonic3k/TestSonic3kLevelInMemoryConstruction.java`.
- Create `src/test/java/com/openggf/mods/code/TestS3kModZoneAdapter.java`.
- Create `src/test/java/com/openggf/game/sonic3k/TestS3kCustomZonePaletteBridge.java`.
- Modify `GameModule`, `DelegatingGameModule`, `Sonic2GameModule`, and `Sonic3kGameModule` to expose/forward the capability.
- Modify `ModLevelDefinition`, `ModLevelDefinitionParser`, `PreparedModZone`, `ModZoneContribution`, `ModContext`, `ModRegistrationPlan`, `ModRuntime`, `ModBackedGamePatch`, and `ModZoneLoader` to prepare and route typed data.
- Modify `Sonic3kLevel` and `Sonic3kObjectRegistry` for in-memory data and explicit `S3KL`/`SKL` identity.
- Modify `LevelManager`, `HudRenderManager`, `PaletteOwnershipRegistry`, `LevelFrameStep`, and S3K level-init providers to install the custom-zone runtime/palette bridge without affecting stock zones.
- Modify `LevelConverter`, its tests, Mod API snapshots, modding format/reference docs, and `CHANGELOG.md`.

### Task 1: Introduce and forward the typed adapter capability

**Files:**
- Create: `src/main/java/com/openggf/mods/code/ModZoneAdapter.java`
- Create: `src/main/java/com/openggf/mods/code/ModZoneRuntimeProfile.java`
- Modify: `src/main/java/com/openggf/game/GameModule.java`
- Modify: `src/main/java/com/openggf/game/patch/DelegatingGameModule.java`
- Modify: `src/main/java/com/openggf/game/sonic2/Sonic2GameModule.java`
- Test: `src/test/java/com/openggf/mods/code/TestModZoneAdapterRouting.java`

- [ ] **Step 1: Write the failing forwarding and no-capability tests**

```java
@Test void delegatingModuleForwardsTheExactAdapterInstance() {
    ModZoneAdapter adapter = mock(ModZoneAdapter.class);
    GameModule base = moduleWithAdapter(adapter);
    GameModule decorated = new DelegatingGameModule(base, "test") {};
    assertSame(adapter, decorated.getModZoneAdapter());
}

@Test void aModuleWithoutAnAdapterRejectsAdditiveZones() {
    assertTrue(GameModule.EMPTY_MOD_ZONE_ADAPTER.isUnsupported());
    assertThrows(ModRegistrationException.class,
            () -> GameModule.EMPTY_MOD_ZONE_ADAPTER.validate(preparedZone()));
}
```

- [ ] **Step 2: Run the test and verify the missing method/type failure**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModZoneAdapterRouting" test`

Expected: compilation fails because `ModZoneAdapter` and `GameModule.getModZoneAdapter()` do not exist.

- [ ] **Step 3: Add the minimal closed capability types and forwarding method**

```java
@ModApi
public interface ModZoneAdapter {
    void validate(PreparedModZone zone);
    Level load(PreparedModZone zone) throws IOException;
    ModZoneRuntimeProfile runtimeProfile(PreparedModZone zone);
    default boolean isUnsupported() { return false; }
}

@ModApi
public record ModZoneRuntimeProfile(
        ScrollPolicy scroll,
        boolean animatedTiles,
        boolean plcLoads,
        boolean specialRenderEffects,
        boolean advancedRenderModes) {
    public static ModZoneRuntimeProfile flatEmpty() {
        return new ModZoneRuntimeProfile(ScrollPolicy.FLAT, false, false, false, false);
    }
    @ModApi public enum ScrollPolicy { FLAT }
}
```

Add `GameModule.getModZoneAdapter()` with an unsupported singleton and add an explicit forwarding override to `DelegatingGameModule`. Make `Sonic2GameModule` return a new `Sonic2ModZoneAdapter(this)`; do not put an `s2` comparison in shared mod code.

- [ ] **Step 4: Run the focused test and forwarding guard**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModZoneAdapterRouting" test`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/mods/code/ModZoneAdapter.java src/main/java/com/openggf/mods/code/ModZoneRuntimeProfile.java src/main/java/com/openggf/game/GameModule.java src/main/java/com/openggf/game/patch/DelegatingGameModule.java src/main/java/com/openggf/game/sonic2/Sonic2GameModule.java src/main/java/com/openggf/game/sonic2/Sonic2ModZoneAdapter.java src/test/java/com/openggf/mods/code/TestModZoneAdapterRouting.java
git commit -m "feat: add typed mod-zone adapter capability"
```

### Task 2: Add format-v2 S3K metadata and sparse palette claims

**Files:**
- Create: `src/main/java/com/openggf/mods/code/ModPaletteClaim.java`
- Modify: `src/main/java/com/openggf/mods/code/ModLevelDefinition.java`
- Modify: `src/main/java/com/openggf/mods/code/ModLevelDefinitionParser.java`
- Test: `src/test/java/com/openggf/mods/code/TestModLevelDefinitionParser.java`
- Test: `src/test/java/com/openggf/tools/modsdk/TestLevelConverter.java`

- [ ] **Step 1: Add failing v1-compatibility and v2 strictness tests**

```java
@Test void v2ParsesTypedS3kMetadataAndSparseClaims() throws Exception {
    ModLevelDefinition level = readFixture("s3k-v2-valid");
    assertEquals(2, level.formatVersion());
    assertEquals(ModLevelDefinition.S3kObjectZoneSet.S3KL,
            level.s3kMetadata().orElseThrow().objectZoneSet());
    assertEquals(List.of(new ModPaletteClaim(1, 0, 0x0EEE)), level.paletteClaims());
}

@Test void v2RejectsUnknownZoneSetDuplicateClaimAndLineZero() {
    assertFormatError("s3k-v2-unknown-set", "objectZoneSet");
    assertFormatError("s3k-v2-duplicate-claim", "Duplicate palette claim");
    assertFormatError("s3k-v2-line-zero", "creator palette line must be 1..3");
}

@Test void v1StillProducesFourCompleteLegacyPaletteLines() throws Exception {
    ModLevelDefinition level = readFixture("s2-v1-valid");
    assertEquals(1, level.formatVersion());
    assertEquals(4, level.paletteLines().length);
    assertTrue(level.s3kMetadata().isEmpty());
}
```

- [ ] **Step 2: Run the parser tests and verify red**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModLevelDefinitionParser" test`

Expected: v2 fixtures fail with `Unsupported level formatVersion: 2`.

- [ ] **Step 3: Implement the exact v2 JSON shape**

```json
{
  "formatVersion": 2,
  "hostMetadata": {"s3k": {"objectZoneSet": "S3KL"}},
  "paletteClaims": [{"line": 1, "color": 0, "sega": 3822}]
}
```

```java
@ModApi
public record ModPaletteClaim(int line, int color, int segaColor) {
    public ModPaletteClaim {
        if (line < 1 || line > 3) throw new IllegalArgumentException("creator palette line must be 1..3");
        if (color < 0 || color > 15) throw new IllegalArgumentException("palette color must be 0..15");
        if ((segaColor & ~0x0EEE) != 0) throw new IllegalArgumentException("invalid Genesis color");
    }
}

@ModApi public record S3kMetadata(S3kObjectZoneSet objectZoneSet) {}
@ModApi public enum S3kObjectZoneSet { S3KL, SKL }
```

Use separate exact-key sets for v1 and v2. V1 keeps `assets.palettes`; v2 removes that asset and requires `hostMetadata.s3k` plus `paletteClaims`. Preserve the existing `read(root, ref)` method and constructor overloads so S2 and standalone callers remain source/binary compatible.

- [ ] **Step 4: Run parser and converter tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModLevelDefinitionParser,com.openggf.tools.modsdk.TestLevelConverter" test`

Expected: all tests pass, including all v1 fixtures.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/mods/code/ModPaletteClaim.java src/main/java/com/openggf/mods/code/ModLevelDefinition.java src/main/java/com/openggf/mods/code/ModLevelDefinitionParser.java src/test/java/com/openggf/mods/code/TestModLevelDefinitionParser.java src/test/java/com/openggf/tools/modsdk/TestLevelConverter.java src/test/resources/mods/formats
git commit -m "feat: add strict S3K mod-level metadata"
```

### Task 3: Validate indexed palette use before publication

**Files:**
- Create: `src/main/java/com/openggf/mods/code/ModPaletteUsageValidator.java`
- Test: `src/test/java/com/openggf/mods/code/TestModPaletteUsageValidator.java`

- [ ] **Step 1: Write failing palette-usage tests**

```java
@Test void everyOpaquePatternNibbleNeedsAClaimForItsBlockPaletteLine() {
    ModLevelDefinition definition = fixtureWithBlockPalette(2)
            .patternPixels(0, 0, 0, 5)
            .claims(new ModPaletteClaim(2, 5, 0x00E0))
            .build();
    assertDoesNotThrow(() -> ModPaletteUsageValidator.validate(definition));
}

@Test void missingClaimAndCharacterLineUseAreRejected() {
    assertThrows(ModRegistrationException.class,
            () -> ModPaletteUsageValidator.validate(fixtureWithUnclaimedColor()));
    assertThrows(ModRegistrationException.class,
            () -> ModPaletteUsageValidator.validate(fixtureUsingLineZero()));
}
```

- [ ] **Step 2: Run the test and verify red**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModPaletteUsageValidator" test`

Expected: compilation fails because the validator is absent.

- [ ] **Step 3: Implement deterministic usage scanning**

```java
public static void validate(ModLevelDefinition level) {
    Set<Cell> claims = level.paletteClaims().stream()
            .map(c -> new Cell(c.line(), c.color())).collect(Collectors.toUnmodifiableSet());
    for (int block = 0; block < level.blockCount(); block++) {
        for (PatternUse use : decodeBlockPatternUses(level, block)) {
            for (int color : nonZeroNibbles(level.patternBytes(), use.patternIndex())) {
                if (use.paletteLine() == 0 || !claims.contains(new Cell(use.paletteLine(), color))) {
                    throw invalid("Unclaimed indexed color line=" + use.paletteLine() + " color=" + color);
                }
            }
        }
    }
}
```

Decode block descriptors with the engine's existing Genesis descriptor masks; validate raw block data before any runtime sanitization. Color index 0 is transparent/background and still requires a claim when the block uses it as visible level backdrop; encode that rule explicitly in the fixture and validator rather than silently inventing black.

- [ ] **Step 4: Run the focused tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModPaletteUsageValidator,com.openggf.mods.code.TestModLevelDefinitionParser" test`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/mods/code/ModPaletteUsageValidator.java src/test/java/com/openggf/mods/code/TestModPaletteUsageValidator.java
git commit -m "feat: validate mod level palette ownership"
```

### Task 4: Build `Sonic3kLevel` entirely from prepared data

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kLevel.java`
- Test: `src/test/java/com/openggf/game/sonic3k/TestSonic3kLevelInMemoryConstruction.java`

- [ ] **Step 1: Write the failing in-memory construction tests**

```java
@Test void buildsS3kLevelWithoutDummyRomAddresses() throws Exception {
    Palette character = characterPalette(0x0EEE);
    Sonic3kLevel level = Sonic3kLevel.inMemoryBuilder(0x40, patterns(), chunks(), blocks())
            .layout(2, 1, new byte[]{0, 1}, new byte[]{1, 0})
            .characterPalette(character)
            .paletteClaims(List.of(new ModPaletteClaim(1, 0, 0x000E)))
            .solidProfiles(heights(), widths(), angles())
            .collisionIndices(new int[]{0}, new int[]{0})
            .boundaries(0, 800, 0, 224)
            .objectZoneSet(S3kZoneSet.S3KL)
            .spawns(List.of(controllerSpawn()), List.of(), ringSheet())
            .build();
    assertSame(character, level.getPalette(0));
    assertEquals(S3kZoneSet.S3KL, level.getObjectZoneSet());
    assertEquals(2, level.getMap().getWidth());
}
```

Add array-copy, invalid-reference, collision-count, dimensions, and sparse-palette tests matching `TestSonic2LevelInMemoryConstruction`.

- [ ] **Step 2: Run the test and verify red**

Run: `mvn "-Dtest=com.openggf.game.sonic3k.TestSonic3kLevelInMemoryConstruction" test`

Expected: compilation fails because `inMemoryBuilder` does not exist.

- [ ] **Step 3: Extract shared private loaders and add the builder**

```java
public static InMemoryBuilder inMemoryBuilder(int zoneIndex,
        byte[] patterns, byte[] chunks, byte[] blocks) {
    return new InMemoryBuilder(zoneIndex, patterns, chunks, blocks);
}

public S3kZoneSet getObjectZoneSet() {
    return objectZoneSet;
}
```

The builder must clone every caller-owned array, require an 8x8 block grid, decode prepared Genesis bytes with the same `Pattern`/`Block`/`Chunk` classes as stock loading, create a blank second map layer when absent, and call `validateResourceReferences()`. Keep the current ROM constructor and `LevelResourcePlan` path byte-for-byte behaviorally unchanged.

- [ ] **Step 4: Run in-memory and stock level tests**

Run: `mvn "-Dtest=com.openggf.game.sonic3k.TestSonic3kLevelInMemoryConstruction,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.tests.TestSonic3kLevelLoading" test`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/sonic3k/Sonic3kLevel.java src/test/java/com/openggf/game/sonic3k/TestSonic3kLevelInMemoryConstruction.java
git commit -m "feat: construct S3K levels from bounded mod data"
```

### Task 5: Implement S2 and S3K adapters and remove shared game-name branching

**Files:**
- Create: `src/main/java/com/openggf/game/sonic2/Sonic2ModZoneAdapter.java`
- Create: `src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneAdapter.java`
- Modify: `src/main/java/com/openggf/mods/code/ModZoneLoader.java`
- Modify: `src/main/java/com/openggf/mods/code/ModContext.java`
- Modify: `src/main/java/com/openggf/mods/code/ModRuntime.java`
- Modify: `src/main/java/com/openggf/mods/code/ModBackedGamePatch.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kGameModule.java`
- Test: `src/test/java/com/openggf/mods/code/TestS3kModZoneAdapter.java`
- Test: `src/test/java/com/openggf/mods/code/TestModZoneLoader.java`

- [ ] **Step 1: Write failing routing and owner-finding tests**

```java
@Test void patchDelegatesPreparedZoneToResolvedModuleAdapter() throws Exception {
    ModZoneAdapter adapter = mock(ModZoneAdapter.class);
    GameModule resolved = applyPlan(moduleWithAdapter(adapter), s3kPreparedPlan());
    resolved.loadLevelOverride(MOD_LEVEL_INDEX);
    verify(adapter).validate(argThat(z -> z.ownerModId().equals("alpha")));
    verify(adapter).load(any(PreparedModZone.class));
}

@Test void unsupportedModuleFailsTheOwnerTransactionBeforePublication() {
    RegistrationResult result = register(zoneEntrypoint(), moduleWithoutAdapter());
    assertEquals("MOD_ZONE_HOST_UNSUPPORTED", result.finding().code());
    assertFalse(result.publishedOwners().contains("alpha"));
}
```

Also assert there is no `"s2"`/`"s3k"` branch in `ModBackedGamePatch` or `ModZoneLoader`.

- [ ] **Step 2: Run focused tests and verify red**

Run: `mvn "-Dtest=com.openggf.mods.code.TestS3kModZoneAdapter,com.openggf.mods.code.TestModZoneLoader" test`

Expected: S3K registration is rejected by the existing S2-only branch.

- [ ] **Step 3: Route through the adapter and validate the S3K contract**

```java
if (registry instanceof ModZoneRegistry mods) {
    PreparedModZone zone = mods.levelContribution(levelIndex);
    if (zone != null) {
        ModZoneAdapter adapter = super.getModZoneAdapter();
        adapter.validate(zone);
        return adapter.load(zone);
    }
}
return super.loadLevelOverride(levelIndex);
```

`Sonic2ModZoneAdapter` must call the existing S2 builder and accept v1. `Sonic3kModZoneAdapter` must require v2, blockGridSide 8, typed S3K metadata, validated palette use, and host-owned character/ring assets. A namespaced-only object list may omit metadata and defaults to `S3KL`; any stock object requires the explicit declaration. Reject a stock ID whose registered factory is incompatible with the selected set.

- [ ] **Step 4: Run adapter, S2 compatibility, and hostile-input tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestS3kModZoneAdapter,com.openggf.mods.code.TestModZoneLoader,com.openggf.game.sonic2.TestSonic2LevelInMemoryConstruction,com.openggf.mods.TestModApiSignatureSurface" test`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/sonic2/Sonic2ModZoneAdapter.java src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneAdapter.java src/main/java/com/openggf/game/sonic3k/Sonic3kGameModule.java src/main/java/com/openggf/mods/code/ModZoneLoader.java src/main/java/com/openggf/mods/code/ModContext.java src/main/java/com/openggf/mods/code/ModRuntime.java src/main/java/com/openggf/mods/code/ModBackedGamePatch.java src/test/java/com/openggf/mods/code/TestS3kModZoneAdapter.java src/test/java/com/openggf/mods/code/TestModZoneLoader.java
git commit -m "feat: load additive zones through host adapters"
```

### Task 6: Make S3K zone-set identity explicit at object creation

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`
- Test: `src/test/java/com/openggf/game/sonic3k/objects/TestSonic3kModZoneObjectSet.java`

- [ ] **Step 1: Write failing same-ID/different-set tests**

```java
@Test void syntheticZoneUsesLevelDeclaredSetNotSyntheticIndex() {
    loadCustomLevel(S3kZoneSet.SKL, stockSpawn(0x14));
    assertEquals("Updraft", registry.getPrimaryName(0x14, registry.currentZoneSetForTest()));
    loadCustomLevel(S3kZoneSet.S3KL, stockSpawn(0x14));
    assertEquals("LBZTriggerBridge", registry.getPrimaryName(0x14, registry.currentZoneSetForTest()));
}
```

- [ ] **Step 2: Run and verify the synthetic-index failure**

Run: `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestSonic3kModZoneObjectSet" test`

Expected: the synthetic zone is incorrectly inferred as `SKL` or defaults without consulting the level.

- [ ] **Step 3: Read the set from the active S3K level first**

```java
private S3kZoneSet getCurrentZoneSet() {
    Level level = GameServices.levelOrNull() == null ? null : GameServices.level().getLevel();
    if (level instanceof Sonic3kLevel s3k && s3k.getObjectZoneSet() != null) {
        return s3k.getObjectZoneSet();
    }
    int romZoneId = currentRomZoneId();
    return romZoneId < 0 ? S3kZoneSet.S3KL : S3kZoneSet.forZone(romZoneId);
}
```

Do not special-case a zone id, level index, or owner.

- [ ] **Step 4: Run object-set and S3K object registry suites**

Run: `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestSonic3kModZoneObjectSet" test`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java src/test/java/com/openggf/game/sonic3k/objects/TestSonic3kModZoneObjectSet.java
git commit -m "feat: preserve S3K mod-zone object set identity"
```

### Task 7: Install explicit custom-zone runtime profiles

**Files:**
- Create: `src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneRuntimeProfile.java`
- Modify: `src/main/java/com/openggf/mods/code/PreparedModZone.java`
- Modify: `src/main/java/com/openggf/mods/code/ModBackedGamePatch.java`
- Modify: `src/main/java/com/openggf/level/LevelManager.java`
- Test: `src/test/java/com/openggf/mods/code/TestModZoneRuntimeProfile.java`

- [ ] **Step 1: Write failing no-stock-inheritance tests**

```java
@Test void customS3kZoneInstallsExplicitEmptyRuntimeContracts() throws Exception {
    loadMinimalCustomS3kZone();
    assertArrayEquals(new int[]{cameraX(), cameraY()}, backgroundScroll());
    assertNull(levelManager.getAnimatedPatternManager());
    assertNull(levelManager.getAnimatedPaletteManager());
    assertFalse(specialRenderRegistry().hasContributions());
    assertFalse(advancedRenderController().hasOverride());
    assertEquals(0, plcQueue().pendingCount());
    assertInstanceOf(NoOpLevelEventProvider.class, activeEvents());
}
```

- [ ] **Step 2: Run and verify inherited S3K providers make the test fail**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModZoneRuntimeProfile" test`

Expected: at least one stock provider is selected from the synthetic zone index.

- [ ] **Step 3: Install the profile at the level-load choke point**

```java
public final class Sonic3kModZoneRuntimeProfile {
    public static ModZoneRuntimeProfile flatEmpty() {
        return ModZoneRuntimeProfile.flatEmpty();
    }
}
```

Store the immutable profile in `PreparedModZone`. In `ModBackedGamePatch`, decorate scroll, animation, event, zone-feature, PLC, and render providers so a mod-zone lookup uses the profile and a stock lookup delegates unchanged. For flat scroll return `{cameraX, cameraY}`. An explicit creator event factory still routes through `ModFaultBoundary`; absence returns the no-event provider, not the stock provider.

- [ ] **Step 4: Run runtime-profile, stock S3K init, and rewind registry tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModZoneRuntimeProfile,com.openggf.game.sonic3k.TestSonic3kLevelInitProfile,com.openggf.game.session.TestGameplayModeContextRewindRegistry" test`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneRuntimeProfile.java src/main/java/com/openggf/mods/code/PreparedModZone.java src/main/java/com/openggf/mods/code/ModBackedGamePatch.java src/main/java/com/openggf/level/LevelManager.java src/test/java/com/openggf/mods/code/TestModZoneRuntimeProfile.java
git commit -m "feat: isolate custom S3K runtime profiles"
```

### Task 8: Add host palette reservations and the HUD ownership bridge

**Files:**
- Create: `src/main/java/com/openggf/game/sonic3k/S3kCustomZonePaletteBridge.java`
- Modify: `src/main/java/com/openggf/game/palette/PaletteOwnershipRegistry.java`
- Modify: `src/main/java/com/openggf/level/objects/HudRenderManager.java`
- Modify: `src/main/java/com/openggf/level/LevelManager.java`
- Modify: `src/main/java/com/openggf/LevelFrameStep.java`
- Test: `src/test/java/com/openggf/game/sonic3k/TestS3kCustomZonePaletteBridge.java`
- Test: `src/test/java/com/openggf/level/objects/TestHudRenderManager.java`

- [ ] **Step 1: Write failing reservation and composition tests**

```java
@Test void customZoneComposesCharacterCreatorAndHudClaims() {
    bridge.install(level, tailsPalette(), creatorClaims(), hudProvider());
    stepPaletteFrame();
    assertPaletteEquals(tailsPalette(), level.getPalette(0));
    assertEquals("sample:level", registry.ownerAt(NORMAL, 1, 3));
    assertEquals("host:s3k-hud", registry.ownerAt(NORMAL, 0, HUD_LIVES_COLOR));
}

@Test void creatorCannotClaimCharacterOrHudReservation() {
    assertThrows(ModRegistrationException.class,
            () -> bridge.validate(List.of(new ModPaletteClaim(0, 2, 0x0EEE))));
    assertThrows(ModRegistrationException.class,
            () -> bridge.validate(List.of(hudReservedClaim())));
}
```

- [ ] **Step 2: Run tests and verify direct HUD upload bypasses ownership**

Run: `mvn "-Dtest=com.openggf.game.sonic3k.TestS3kCustomZonePaletteBridge,com.openggf.level.objects.TestHudRenderManager" test`

Expected: owner metadata is absent because `HudRenderManager` uploads its override directly.

- [ ] **Step 3: Implement custom-zone-only host claims**

```java
public void submitHudClaims(PaletteOwnershipRegistry registry, Palette palette) {
    registry.submit(PaletteWrite.normal("host:s3k-hud", HOST_HUD_PRIORITY,
            hudLine, hudStartColor, segaBytes(palette, hudStartColor, hudColorCount)));
}
```

Add immutable reservations to `PaletteOwnershipRegistry` or the bridge, validate them before level publication, and submit HUD writes before the frame's single `resolveInto`. `HudRenderManager` receives the bridge for custom S3K zones and stops calling `cachePaletteTexture` directly in that mode. Preserve the existing direct stock path exactly when no bridge is installed.

- [ ] **Step 4: Run palette integration and stock HUD tests**

Run: `mvn "-Dtest=com.openggf.game.sonic3k.TestS3kCustomZonePaletteBridge,com.openggf.game.sonic3k.TestS3kPaletteOwnershipRegistryIntegration,com.openggf.level.objects.TestHudRenderManager" test`

Expected: all tests pass; stock expected palette bytes remain unchanged.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/sonic3k/S3kCustomZonePaletteBridge.java src/main/java/com/openggf/game/palette/PaletteOwnershipRegistry.java src/main/java/com/openggf/level/objects/HudRenderManager.java src/main/java/com/openggf/level/LevelManager.java src/main/java/com/openggf/LevelFrameStep.java src/test/java/com/openggf/game/sonic3k/TestS3kCustomZonePaletteBridge.java src/test/java/com/openggf/level/objects/TestHudRenderManager.java
git commit -m "feat: compose custom S3K host palettes"
```

### Task 9: Prove lifecycle, save identity, rewind, and compatibility

**Files:**
- Modify: `src/test/java/com/openggf/mods/code/TestS3kModZoneAdapter.java`
- Create: `src/test/java/com/openggf/mods/code/TestS3kModZoneLifecycle.java`
- Modify: `src/test/java/com/openggf/mods/code/TestModZoneLoader.java`
- Modify: `src/test/java/com/openggf/mods/integration/TestPhase3StandaloneSampleIntegration.java`

- [ ] **Step 1: Add failing end-to-end lifecycle tests**

```java
@Test void taggedIdentitySurvivesSaveReopenEditorAndRewind() throws Exception {
    ZoneKey key = ZoneKey.mod("alpha", "sky");
    launchCustomZone(key);
    mutateOneMapCellAndCaptureRewind();
    enterAndExitEditor();
    assertEquals(key, activeZoneKey());
    seekBackward();
    assertEquals(originalCell(), currentCell());
    assertEquals(key, activeZoneKey());
}

@Test void disabledOwnerFallsBackWithoutTrustingSyntheticIndex() {
    SavePayload saved = saveAt(ZoneKey.mod("alpha", "sky"));
    disable("alpha");
    assertEquals(ZoneKey.stock(0), resolveResume(saved));
}
```

- [ ] **Step 2: Run and verify red at the missing lifecycle seam**

Run: `mvn "-Dtest=com.openggf.mods.code.TestS3kModZoneLifecycle" test`

Expected: one or more lifecycle assertions fail before adapter state is threaded through save/editor/rewind.

- [ ] **Step 3: Wire only missing tagged-identity/profile state**

Use `ZoneRegistry.zoneKey(...)` and `resolveZoneKey(...)` at save/resume boundaries. Do not persist or reinterpret the synthetic index. Register custom runtime state with the existing gameplay `RewindRegistry`; keep immutable adapter/profile data outside snapshots.

- [ ] **Step 4: Run lifecycle and cross-mode suites**

Run: `mvn "-Dtest=com.openggf.mods.code.TestS3kModZoneLifecycle,com.openggf.mods.code.TestS3kModZoneAdapter,com.openggf.mods.code.TestModZoneLoader,com.openggf.mods.integration.TestPhase3StandaloneSampleIntegration" test`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf src/test/java/com/openggf/mods/code/TestS3kModZoneLifecycle.java src/test/java/com/openggf/mods/code/TestS3kModZoneAdapter.java src/test/java/com/openggf/mods/code/TestModZoneLoader.java src/test/java/com/openggf/mods/integration/TestPhase3StandaloneSampleIntegration.java
git commit -m "test: cover S3K mod-zone lifecycle"
```

### Task 10: Advance Mod API 2.3 and document the format

**Files:**
- Modify: `src/main/java/com/openggf/mods/ModApiVersion.java`
- Create: `src/test/resources/mods/mod-api-signatures-2.3.txt`
- Modify: `src/test/java/com/openggf/mods/TestModApiSignatureSurface.java`
- Modify: `src/main/java/com/openggf/tools/modsdk/LevelConverter.java`
- Modify: `docs/modding/formats/level-definition.md`
- Modify: `docs/modding/content-mods.md`
- Modify: `docs/modding/index.md`
- Modify: `docs/architecture/mod-api-compatibility.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Generate the candidate signature and verify the version guard is red**

Run: `mvn "-Dtest=com.openggf.mods.TestModApiSignatureSurface" test`

Expected: failure reports the additive types/methods missing from the closed 2.2 snapshot.

- [ ] **Step 2: Set the version and freeze the exact 2.3 surface**

```java
public static final SemanticVersion CURRENT = SemanticVersion.parse("2.3.0");
```

Generate `mods/mod-api-signatures-2.3.txt`, retain every 1.1/1.2/2.0/2.1/2.2 snapshot, and update the compatibility test so 2.3 is an additive superset.

- [ ] **Step 3: Document exact v2 JSON, ownership, and host behavior**

Document `hostMetadata.s3k.objectZoneSet`, sparse `paletteClaims`, namespaced-only `S3KL` default, stock-object explicit-set requirement, host line-0 ownership, HUD reservations, empty runtime defaults, and the unchanged v1 S2/standalone path. Update `LevelConverter` inventory validation so v1 requires `palettes.bin` and v2 forbids it.

- [ ] **Step 4: Run the complete adapter gate**

Run: `mvn "-Dtest=com.openggf.mods.code.TestS3kModZoneAdapter,com.openggf.mods.code.TestS3kModZoneLifecycle,com.openggf.game.sonic3k.TestSonic3kLevelInMemoryConstruction,com.openggf.game.sonic3k.TestS3kCustomZonePaletteBridge,com.openggf.mods.code.TestModZoneLoader,com.openggf.mods.integration.TestPhase3StandaloneSampleIntegration,com.openggf.mods.TestModApiSignatureSurface,com.openggf.tools.modsdk.TestLevelConverter" test`

Expected: all tests pass.

- [ ] **Step 5: Run representative stock S3K regression spots**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.tests.TestSonic3kLevelLoading,com.openggf.tests.trace.s3k.TestS3kAizTraceReplay,com.openggf.tests.trace.s3k.TestS3kHczCompleteRunTraceReplay" test`

Expected: all tests pass or retain their checked-in accepted frontier without moving backward.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/openggf/mods/ModApiVersion.java src/test/resources/mods/mod-api-signatures-2.3.txt src/test/java/com/openggf/mods/TestModApiSignatureSurface.java src/main/java/com/openggf/tools/modsdk/LevelConverter.java docs/modding/formats/level-definition.md docs/modding/content-mods.md docs/modding/index.md docs/architecture/mod-api-compatibility.md CHANGELOG.md
git commit -m "docs: publish S3K mod-zone adapter contract"
```

## Completion gate

- [ ] Run `mvn "-Dtest=com.openggf.mods.code.TestS3kModZoneAdapter,com.openggf.mods.code.TestS3kModZoneLifecycle,com.openggf.game.sonic3k.TestSonic3kLevelInMemoryConstruction,com.openggf.game.sonic3k.TestS3kCustomZonePaletteBridge,com.openggf.mods.code.TestModZoneLoader,com.openggf.mods.TestModApiSignatureSurface,com.openggf.tools.modsdk.TestLevelConverter" test`.
- [ ] Run `mvn package`.
- [ ] Confirm `git diff --check` is clean.
- [ ] Confirm stock S2 sample-zone and standalone fixtures still accept format v1.
- [ ] Confirm no shared mod-runtime source branches on `s2`, `s3k`, `Sonic2GameModule`, or `Sonic3kGameModule` for adapter selection.
