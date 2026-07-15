# Sonic 3&K Mod-Zone Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a host-owned adapter that loads bounded, original-data mod zones in Sonic 3&K without changing stock S3K loading or the existing Sonic 2/standalone contracts.

**Architecture:** Replace the S2-specific `ModZoneLoader.load(...)` call with a typed `ModZoneAdapter` capability forwarded by `GameModule`. Format v2 adds typed S3K zone-set metadata and sparse creator palette claims; `Sonic3kLevel.InMemoryBuilder` consumes prepared arrays while the S3K adapter supplies host character/ring assets and an explicit empty runtime profile. Palette reservations are validated before publication and HUD palette uploads join the shared ownership registry only for custom S3K zones.

**Tech Stack:** Java 21, Maven, JUnit 5, Jackson, OpenGGF Mod API/signature guards, headless gameplay fixtures.

**Design reference:** `docs/superpowers/specs/2026-07-14-s3k-mod-zone-adapter-design.md`

**Commit policy:** Keep the repository trailer block on every commit. For Tasks 1-9, use `Changelog: n/a: covered by the aggregate S3K mod-zone entry in Task 10`; Task 10 stages `CHANGELOG.md` and uses `Changelog: updated` and `Guide: n/a: modding handbook is outside docs/guide`, with other mappings marked accurately. Never use a blanket `git add` path.

**Mandatory expected-red signature gate for Tasks 1-9:** The live 2.2 surface must drift while the additive 2.3 types are under construction. After each task's green feature command, run:

`mvn "-Dtest=com.openggf.mods.TestModApiSignatureSurface#publishedTwoTwoSurfaceIsPinnedToTheCurrentSurface+twoOneToTwoTwoIsAnAdditiveMinorBump" test`

Expected: those two named methods fail because the current surface has unrefrozen additions; no other `TestModApiSignatureSurface` method fails. Task 10 is the only task that changes `ModApiVersion.CURRENT`, creates the 2.3 baseline, and makes the whole signature class green.

**Mandatory S3K regression gate:** At Tasks 9 and 10 completion, run `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils" test`. Missing `s3k.gen` is a blocked verification, not a green skip.

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
- Create: `src/main/java/com/openggf/game/modzone/ModZoneAdapter.java`
- Create: `src/main/java/com/openggf/game/modzone/ModZoneLevelData.java`
- Create: `src/main/java/com/openggf/game/modzone/ModZoneRegistrationException.java`
- Create: `src/main/java/com/openggf/game/modzone/ModZoneRuntimeProfile.java`
- Create: `src/main/java/com/openggf/game/modzone/ModZoneRuntimeContribution.java`
- Create: `src/main/java/com/openggf/game/sonic2/Sonic2ModZoneAdapter.java`
- Modify: `src/main/java/com/openggf/mods/code/ModZoneLoader.java`
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
            () -> GameModule.EMPTY_MOD_ZONE_ADAPTER.validate("alpha", levelDefinition()));
}
```

- [ ] **Step 2: Run the test and verify the missing method/type failure**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModZoneAdapterRouting" test`

Expected: compilation fails because `ModZoneAdapter` and `GameModule.getModZoneAdapter()` do not exist.

- [ ] **Step 3: Add the minimal closed capability types and forwarding method**

```java
@ModApi
public interface ModZoneAdapter {
    void validate(String ownerModId, ModZoneLevelData level);
    Level load(String ownerModId, ModZoneLevelData level) throws IOException;
    ModZoneRuntimeProfile runtimeProfile(String ownerModId, ModZoneLevelData level);
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

Add `GameModule.getModZoneAdapter()` with an unsupported singleton and add an explicit forwarding override to `DelegatingGameModule`. Keep private parser types and engine-owned `PreparedModZone` out of the game package: `ModZoneLoader` converts the parsed definition once into immutable `ModZoneLevelData`, and adapters consume only that game-owned view plus an owner id supplied by the engine caller. `ModZoneRuntimeContribution` carries the resolved data/profile through `ZoneRegistry` so shared level code never depends on `mods.code`. Create the initial `Sonic2ModZoneAdapter` as a thin host wrapper and make `Sonic2GameModule` return it. Task 5 changes the shared caller to use this capability; do not put an `s2` comparison in shared mod code and never accept an owner id from creator code.

- [ ] **Step 4: Run the focused test and forwarding guard**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModZoneAdapterRouting" test`

Expected: feature tests pass. Then the mandatory signature command fails only `publishedTwoTwoSurfaceIsPinnedToTheCurrentSurface` and `twoOneToTwoTwoIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/modzone/ModZoneAdapter.java src/main/java/com/openggf/game/modzone/ModZoneLevelData.java src/main/java/com/openggf/game/modzone/ModZoneRegistrationException.java src/main/java/com/openggf/game/modzone/ModZoneRuntimeProfile.java src/main/java/com/openggf/game/modzone/ModZoneRuntimeContribution.java src/main/java/com/openggf/game/GameModule.java src/main/java/com/openggf/game/patch/DelegatingGameModule.java src/main/java/com/openggf/game/sonic2/Sonic2GameModule.java src/main/java/com/openggf/game/sonic2/Sonic2ModZoneAdapter.java src/main/java/com/openggf/mods/code/ModZoneLoader.java src/test/java/com/openggf/mods/code/TestModZoneAdapterRouting.java
git commit -m "feat: add typed mod-zone adapter capability"
```

### Task 2: Add format-v2 S3K metadata and sparse palette claims

**Files:**
- Create: `src/main/java/com/openggf/game/modzone/ModPaletteClaim.java`
- Create: `src/main/java/com/openggf/game/modzone/ModObjectZoneSet.java`
- Create: `src/main/java/com/openggf/game/modzone/ModZoneHostMetadata.java`
- Modify: `src/main/java/com/openggf/mods/code/ModLevelDefinition.java`
- Modify: `src/main/java/com/openggf/mods/code/ModLevelDefinitionParser.java`
- Test: `src/test/java/com/openggf/mods/code/TestModLevelDefinitionParser.java`
- Test: `src/test/java/com/openggf/tools/modsdk/TestLevelConverter.java`

- [ ] **Step 1: Add failing v1-compatibility and v2 strictness tests**

```java
@Test void v2ParsesTypedS3kMetadataAndSparseClaims() throws Exception {
    ModLevelDefinition level = readFixture("s3k-v2-valid");
    assertEquals(2, level.formatVersion());
    assertEquals(ModObjectZoneSet.S3KL,
            level.hostMetadata().orElseThrow().objectZoneSet());
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
    assertTrue(level.hostMetadata().isEmpty());
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

@ModApi public record ModZoneHostMetadata(ModObjectZoneSet objectZoneSet) {}
@ModApi public enum ModObjectZoneSet { S3KL, SKL }
```

Use separate exact-key sets for v1 and v2. V1 keeps `assets.palettes`; v2 removes that asset and requires `hostMetadata.s3k` plus `paletteClaims`. Preserve the existing `read(root, ref)` method and constructor overloads so S2 and standalone callers remain source/binary compatible.

- [ ] **Step 4: Run parser and converter tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModLevelDefinitionParser,com.openggf.tools.modsdk.TestLevelConverter" test`

Expected: feature tests pass, including all v1 fixtures. Then the mandatory signature command fails only `publishedTwoTwoSurfaceIsPinnedToTheCurrentSurface` and `twoOneToTwoTwoIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/modzone/ModPaletteClaim.java src/main/java/com/openggf/game/modzone/ModObjectZoneSet.java src/main/java/com/openggf/game/modzone/ModZoneHostMetadata.java src/main/java/com/openggf/mods/code/ModLevelDefinition.java src/main/java/com/openggf/mods/code/ModLevelDefinitionParser.java src/test/java/com/openggf/mods/code/TestModLevelDefinitionParser.java src/test/java/com/openggf/tools/modsdk/TestLevelConverter.java src/test/resources/mods/formats
git commit -m "feat: add strict S3K mod-level metadata"
```

### Task 3: Validate indexed palette use before publication

**Files:**
- Create: `src/main/java/com/openggf/mods/code/ModPaletteUsageValidator.java`
- Test: `src/test/java/com/openggf/mods/code/TestModPaletteUsageValidator.java`

- [ ] **Step 1: Write failing palette-usage tests**

```java
@Test void everyOpaquePatternNibbleNeedsAClaimForItsDescriptorPaletteLine() {
    ModLevelDefinition definition = fixtureWithBlockPalette(2)
            .patternPixels(0, 0, 0, 5)
            .claims(new ModPaletteClaim(2, 5, 0x00E0))
            .build();
    assertDoesNotThrow(() -> ModPaletteUsageValidator.validate("alpha", definition));
}

@Test void missingClaimAndCharacterLineUseAreRejected() {
    assertThrows(ModRegistrationException.class,
            () -> ModPaletteUsageValidator.validate("alpha", fixtureWithUnclaimedColor()));
    assertThrows(ModRegistrationException.class,
            () -> ModPaletteUsageValidator.validate("alpha", fixtureUsingLineZero()));
}
```

- [ ] **Step 2: Run the test and verify red**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModPaletteUsageValidator" test`

Expected: compilation fails because the validator is absent.

- [ ] **Step 3: Implement deterministic usage scanning**

```java
public static void validate(String ownerModId, ModLevelDefinition level) {
    Set<Cell> claims = level.paletteClaims().stream()
            .map(c -> new Cell(c.line(), c.color())).collect(Collectors.toUnmodifiableSet());
    for (int block : blocksReferencedByMaps(level)) {
        for (PatternUse use : decodeBlockPatternUses(level, block)) {
            for (int color : nonZeroNibbles(level.patternBytes(), use.patternIndex())) {
                if (use.paletteLine() == 0 || !claims.contains(new Cell(use.paletteLine(), color))) {
                    throw invalid(ownerModId,
                            "Unclaimed indexed color line=" + use.paletteLine() + " color=" + color);
                }
            }
        }
    }
}
```

The validator is engine-internal and receives `ownerModId` from the owning registration/adapter path; creator data never supplies or reports that identity. Use it on every `ModRegistrationException` so a palette failure disables the correct transaction. Task 5 calls this exact owner-aware signature from `Sonic3kModZoneAdapter.validate(...)`.

Walk only blocks referenced by the foreground map or optional background map, then their chunks and pattern descriptors. Decode descriptors with the engine's existing Genesis masks and validate every raw reference before indexing; do not rely on runtime sanitization. Nonzero nibbles use the descriptor's palette line, and any nonzero use of host-owned line 0 is rejected.

Pattern nibble 0 is not a color from the descriptor line: both tile shaders discard it, and `Level.getBackdropColor()` independently exposes palette line 2, color 0. Conservatively treat any zero nibble in reachable level patterns as possible backdrop exposure and require the creator claim `(2, 0)` exactly once. Unreachable blocks/patterns do not create claims. This deliberately favors a bounded, deterministic pre-publication rule over trying to predict future plane overlap or scrolling.

- [ ] **Step 4: Run the focused tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModPaletteUsageValidator,com.openggf.mods.code.TestModLevelDefinitionParser" test`

Expected: feature tests pass. Then the mandatory signature command fails only `publishedTwoTwoSurfaceIsPinnedToTheCurrentSurface` and `twoOneToTwoTwoIsAnAdditiveMinorBump`.

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

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.game.sonic3k.TestSonic3kLevelInMemoryConstruction,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.tests.TestSonic3kLevelLoading" test`

Expected: feature tests pass with the supplied S3K ROM; a missing ROM is not accepted as a pass. Then the mandatory signature command fails only `publishedTwoTwoSurfaceIsPinnedToTheCurrentSurface` and `twoOneToTwoTwoIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/sonic3k/Sonic3kLevel.java src/test/java/com/openggf/game/sonic3k/TestSonic3kLevelInMemoryConstruction.java
git commit -m "feat: construct S3K levels from bounded mod data"
```

### Task 5: Implement S2 and S3K adapters and remove shared game-name branching

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/Sonic2ModZoneAdapter.java`
- Create: `src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneAdapter.java`
- Modify: `src/main/java/com/openggf/mods/code/ModZoneLoader.java`
- Modify: `src/main/java/com/openggf/mods/code/ModContext.java`
- Modify: `src/main/java/com/openggf/mods/code/PreparedModZone.java`
- Modify: `src/main/java/com/openggf/mods/code/ModZoneRegistry.java`
- Modify: `src/main/java/com/openggf/mods/code/ModRuntime.java`
- Modify: `src/main/java/com/openggf/mods/code/ModBackedGamePatch.java`
- Modify: `src/main/java/com/openggf/mods/StockProgressionAnchors.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kGameModule.java`
- Test: `src/test/java/com/openggf/mods/code/TestS3kModZoneAdapter.java`
- Test: `src/test/java/com/openggf/mods/code/TestModZoneLoader.java`
- Test: `src/test/java/com/openggf/game/TestZoneProgressionPlan.java`

- [ ] **Step 1: Write failing routing and owner-finding tests**

```java
@Test void patchDelegatesPreparedZoneToResolvedModuleAdapter() throws Exception {
    ModZoneAdapter adapter = mock(ModZoneAdapter.class);
    ModLevelDefinition definition = preparedDefinition();
    GameModule resolved = applyPlan(moduleWithAdapter(adapter), s3kPreparedPlan(definition));
    resolved.loadLevelOverride(MOD_LEVEL_INDEX);
    verify(adapter).validate(eq("alpha"), same(definition));
    verify(adapter).load(eq("alpha"), same(definition));
}

@Test void unsupportedModuleFailsTheOwnerTransactionBeforePublication() {
    RegistrationResult result = register(zoneEntrypoint(), moduleWithoutAdapter());
    assertEquals("MOD_ZONE_HOST_UNSUPPORTED", result.finding().code());
    assertFalse(result.publishedOwners().contains("alpha"));
}

@Test void anchorlessS3kZoneIsAddressableButAddsNoProgressionEdge() {
    ZoneRegistry stock = s3kStockRegistry();
    ZoneRegistry decorated = ModZoneRegistry.decorate(stock,
            List.of(anchorlessPreparedS3kZone()));
    int custom = decorated.resolveZoneKey(ZoneKey.mod("alpha", "sky")).orElseThrow();
    ZoneProgressionPlan.ProgressionResult next = decorated.progressionPlan().next(
            decorated.progressionTopology(), stockResultsZone(), stockResultsLastAct());
    assertNotEquals(new ZoneProgressionPlan.Successor(custom, 0), next);
    assertEquals(stock.getZoneCount(), custom);
    assertTrue(StockProgressionAnchors.anchorsFor("s3k").isEmpty());
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
        adapter.validate(zone.ownerModId(), zone.definition());
        return adapter.load(zone.ownerModId(), zone.definition());
    }
}
return super.loadLevelOverride(levelIndex);
```

`Sonic2ModZoneAdapter` must call the existing S2 builder and accept v1. `Sonic3kModZoneAdapter` must require v2, blockGridSide 8, typed S3K metadata, validated palette use, and host-owned character/ring assets. A namespaced-only object list may omit metadata and defaults to `S3KL`; any stock object requires the explicit declaration. Task 6 owns the factory-compatibility predicate and registration-time rejection.

Generalize anchor handling without inventing an S3K stock boundary. Add `StockProgressionAnchors.defaultAnchorFor(gameId)`: it returns `mtz3` for S2 and empty for S1/S3K, alongside the existing anchor sets. `ModContext` uses an explicit manifest default first, then this registry default; it validates only a non-null result. Remove the raw `"s2"` gate and hardcoded `"mtz3"` fallback from `ModContext.registerZone`. For S3K, retain `insertAfter == null` in `PreparedModZone` (remove its current non-null constructor check); `ModZoneRegistry` publishes the tagged zone but calls `ZoneProgressionPlan.Builder.insertAfter(...)` only for non-null anchors:

```java
if (contribution.insertAfter() != null) {
    int anchor = stock.resolveStockZoneAnchor(contribution.insertAfter());
    builder.insertAfter(anchor, stock.getZoneCount() + i);
}
```

An explicitly supplied S3K anchor still fails because `StockProgressionAnchors.anchorsFor("s3k")` is empty. This is the addressable-but-unsequenced seam later consumed by the game-start marker; do not add `aiz1`.

- [ ] **Step 4: Run adapter, S2 compatibility, and hostile-input tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestS3kModZoneAdapter,com.openggf.mods.code.TestModZoneLoader,com.openggf.game.sonic2.TestSonic2LevelInMemoryConstruction,com.openggf.game.TestZoneProgressionPlan" test`

Expected: feature tests pass. Then the mandatory signature command fails only `publishedTwoTwoSurfaceIsPinnedToTheCurrentSurface` and `twoOneToTwoTwoIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/sonic2/Sonic2ModZoneAdapter.java src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneAdapter.java src/main/java/com/openggf/game/sonic3k/Sonic3kGameModule.java src/main/java/com/openggf/mods/StockProgressionAnchors.java src/main/java/com/openggf/mods/code/ModZoneLoader.java src/main/java/com/openggf/mods/code/ModContext.java src/main/java/com/openggf/mods/code/PreparedModZone.java src/main/java/com/openggf/mods/code/ModZoneRegistry.java src/main/java/com/openggf/mods/code/ModRuntime.java src/main/java/com/openggf/mods/code/ModBackedGamePatch.java src/test/java/com/openggf/mods/code/TestS3kModZoneAdapter.java src/test/java/com/openggf/mods/code/TestModZoneLoader.java src/test/java/com/openggf/game/TestZoneProgressionPlan.java
git commit -m "feat: load additive zones through host adapters"
```

### Task 6: Make S3K zone-set identity explicit at object creation

**Files:**
- Create: `src/main/java/com/openggf/game/sonic3k/objects/S3kObjectCreationContext.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneAdapter.java`
- Test: `src/test/java/com/openggf/game/sonic3k/objects/TestSonic3kModZoneObjectSet.java`
- Modify: `src/test/java/com/openggf/mods/code/TestS3kModZoneAdapter.java`

- [ ] **Step 1: Write failing set and registration-compatibility tests**

```java
@Test void syntheticZoneUsesLevelDeclaredSetNotSyntheticIndex() {
    assertEquals(S3kZoneSet.SKL, loadCustomLevel(S3kZoneSet.SKL).getObjectZoneSet());
    assertEquals(S3kZoneSet.S3KL, loadCustomLevel(S3kZoneSet.S3KL).getObjectZoneSet());
}

@Test void zoneIdGatedMhzAndLbzFactoriesRejectCustomZonesBeforePublication() {
    assertFalse(registry.canCreateInCustomZone(S3kZoneSet.SKL,
            Sonic3kObjectIds.MHZ_MUSHROOM_PLATFORM));
    assertFalse(registry.canCreateInCustomZone(S3kZoneSet.S3KL,
            Sonic3kObjectIds.LBZ_PIPE_PLUG));
    assertRegistrationFinding("MOD_S3K_STOCK_OBJECT_INCOMPATIBLE",
            customZoneWithStockObject(S3kZoneSet.S3KL, Sonic3kObjectIds.LBZ_PIPE_PLUG));
}

@Test void setOnlyFactoryAndNamespacedObjectRemainValid() {
    assertTrue(registry.canCreateInCustomZone(S3kZoneSet.S3KL, setOnlyObjectId()));
    assertPublishes(customZoneWithNamespacedObject("alpha:controller"));
}
```

- [ ] **Step 2: Run and verify the synthetic-index failure**

Run: `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestSonic3kModZoneObjectSet,com.openggf.mods.code.TestS3kModZoneAdapter" test`

Expected: the synthetic zone is incorrectly inferred as `SKL` or defaults without consulting the level, and zone-id-bound MHZ/LBZ stock placements are not rejected before publication.

- [ ] **Step 3: Read the set from the level and make factory compatibility explicit**

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

Replace raw factory predicates with entries evaluated from one internal context:

```java
record S3kObjectCreationContext(S3kZoneSet zoneSet, OptionalInt stockRomZoneId) {
    static S3kObjectCreationContext custom(S3kZoneSet set) {
        return new S3kObjectCreationContext(set, OptionalInt.empty());
    }
}
```

Store `FactoryEntry(ObjectFactory factory, Predicate<S3kObjectCreationContext> compatibility)` rather than a bare factory. Provide explicit `registerSetOnly(...)` and `registerStockZoneBound(...)` helpers and migrate every existing factory branch that reads `currentRomZoneId()` to the latter; a registry test inventories those entries so a future zone-id-bound factory cannot silently use the set-only default. The normal stock path supplies its real ROM zone id. `canCreateInCustomZone(set, id)` evaluates the same predicate with an empty id; MHZ/LBZ factories that require a specific `currentRomZoneId()` therefore reject before publication, while set-only factories remain available. `Sonic3kModZoneAdapter.validate` calls this predicate for every stock placement and emits `MOD_S3K_STOCK_OBJECT_INCOMPATIBLE`. Namespaced objects bypass the stock predicate. Never infer a fake MHZ/LBZ id from a synthetic custom-zone index.

- [ ] **Step 4: Run object-set and S3K object registry suites**

Run: `mvn "-Dtest=com.openggf.game.sonic3k.objects.TestSonic3kModZoneObjectSet,com.openggf.mods.code.TestS3kModZoneAdapter" test`

Expected: feature tests pass. Then the mandatory signature command fails only `publishedTwoTwoSurfaceIsPinnedToTheCurrentSurface` and `twoOneToTwoTwoIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/sonic3k/objects/S3kObjectCreationContext.java src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneAdapter.java src/test/java/com/openggf/game/sonic3k/objects/TestSonic3kModZoneObjectSet.java src/test/java/com/openggf/mods/code/TestS3kModZoneAdapter.java
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
    assertTrue(animatedTileChannelGraph().channels().isEmpty());
    assertTrue(specialRenderRegistry().isEmpty());
    assertTrue(advancedRenderController().isEmpty());
    assertNull(activeEvents());
    assertFalse(gameplay.getRewindRegistry().capture().containsKey("s3k-plc-art"));
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

Store the immutable profile in `PreparedModZone`. In `ModBackedGamePatch`, decorate scroll, animation, event, zone-feature, PLC-art, and render providers so a mod-zone lookup uses the profile and a stock lookup delegates unchanged. For flat scroll return `{cameraX, cameraY}`. An explicit creator event factory still routes through `ModFaultBoundary`; absence returns `null`, which is the engine's existing no-events convention. Passing a null/non-snapshottable object-art provider through `GameplayModeContext.registerPlcArtAdapter` removes stale `s3k-plc-art` state. Do not introduce a `NoOpLevelEventProvider`, a PLC queue facade, or test-only registry methods.

- [ ] **Step 4: Run runtime-profile, stock S3K init, and rewind registry tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModZoneRuntimeProfile,com.openggf.game.sonic3k.TestSonic3kLevelInitProfile,com.openggf.game.session.TestGameplayModeContextRewindRegistry" test`

Expected: feature tests pass. Then the mandatory signature command fails only `publishedTwoTwoSurfaceIsPinnedToTheCurrentSurface` and `twoOneToTwoTwoIsAnAdditiveMinorBump`.

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

Expected: feature tests pass and stock expected palette bytes remain unchanged. Then the mandatory signature command fails only `publishedTwoTwoSurfaceIsPinnedToTheCurrentSurface` and `twoOneToTwoTwoIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/sonic3k/S3kCustomZonePaletteBridge.java src/main/java/com/openggf/game/palette/PaletteOwnershipRegistry.java src/main/java/com/openggf/level/objects/HudRenderManager.java src/main/java/com/openggf/level/LevelManager.java src/main/java/com/openggf/LevelFrameStep.java src/test/java/com/openggf/game/sonic3k/TestS3kCustomZonePaletteBridge.java src/test/java/com/openggf/level/objects/TestHudRenderManager.java
git commit -m "feat: compose custom S3K host palettes"
```

### Task 9: Prove lifecycle, save identity, rewind, and compatibility

**Files:**
- Create: `src/main/java/com/openggf/game/sonic3k/dataselect/S3kSavedZone.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/dataselect/S3kSaveSnapshotProvider.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/dataselect/S3kDataSelectProfile.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneAdapter.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/dataselect/TestS3kSaveSnapshotProvider.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/dataselect/TestS3kDataSelectProfile.java`
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

- [ ] **Step 2: Run the lifecycle assertions against the existing tagged-identity seam**

First add failing focused tests proving that S3K stock saves retain the historical numeric `zone`
field, live custom-zone saves instead persist a tagged `ZoneKey.Mod`, available owners resolve the
tag through the effective decorated `ZoneRegistry`, and a missing/disabled owner falls back to AIZ1
without interpreting a stale synthetic index. The stock `S3kDataSelectPresentation` remains inherited
unchanged.

The implementation owner is the S3K data-select package: `S3kSavedZone` owns the strict tagged/legacy
payload codec, `S3kSaveSnapshotProvider` asks the active module registry for the live `ZoneKey`, and
`S3kDataSelectProfile` receives an effective-zone supplier for load resolution. Make
`Sonic3kModZoneAdapter` implement the existing internal `ModZoneDataSelectDecorator`, returning the
registry-aware S3K host profile while returning the inherited native S3K presentation unchanged.
Do not reuse the S2-named codec or donated S2 profile, and do not add synthetic indices to payloads.

Run: `mvn "-Dtest=com.openggf.mods.code.TestS3kModZoneLifecycle" test`

Expected: tests pass through the now-explicit S3K tagged persistence seam. Any further failure is a
blocker that must be assigned to an exact owning file before continuing, not permission to stage a
shared source tree.

- [ ] **Step 3: Inspect the test diff for synthetic-index coupling**

The committed test must call `ZoneRegistry.zoneKey(...)` / `resolveZoneKey(...)` and assert the persisted identity is `ZoneKey.Mod`; it must not add a synthetic index to payloads or hydrate runtime state from the assertion fixture. If a production change is genuinely required, stop and amend this task with its exact path and a red-green test before editing it.

- [ ] **Step 4: Run lifecycle and cross-mode suites**

Run: `mvn "-Dtest=com.openggf.game.sonic3k.dataselect.TestS3kSaveSnapshotProvider,com.openggf.game.sonic3k.dataselect.TestS3kDataSelectProfile,com.openggf.mods.code.TestS3kModZoneLifecycle,com.openggf.mods.code.TestS3kModZoneAdapter,com.openggf.mods.code.TestModZoneLoader,com.openggf.mods.integration.TestPhase3StandaloneSampleIntegration" test`

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils" test`

Expected: feature and must-keep-green tests pass with the supplied ROM. Then the mandatory signature command fails only `publishedTwoTwoSurfaceIsPinnedToTheCurrentSurface` and `twoOneToTwoTwoIsAnAdditiveMinorBump`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/openggf/game/sonic3k/dataselect/S3kSavedZone.java src/main/java/com/openggf/game/sonic3k/dataselect/S3kSaveSnapshotProvider.java src/main/java/com/openggf/game/sonic3k/dataselect/S3kDataSelectProfile.java src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneAdapter.java src/test/java/com/openggf/game/sonic3k/dataselect/TestS3kSaveSnapshotProvider.java src/test/java/com/openggf/game/sonic3k/dataselect/TestS3kDataSelectProfile.java src/test/java/com/openggf/mods/code/TestS3kModZoneLifecycle.java src/test/java/com/openggf/mods/code/TestS3kModZoneAdapter.java src/test/java/com/openggf/mods/code/TestModZoneLoader.java src/test/java/com/openggf/mods/integration/TestPhase3StandaloneSampleIntegration.java
git commit -m "test: cover S3K mod-zone lifecycle"
```

### Task 10: Advance Mod API 2.3 and document the format

**Files:**
- Modify: `src/main/java/com/openggf/mods/ModApiVersion.java`
- Create: `src/test/resources/mods/mod-api-signatures-2.3.txt`
- Modify: `src/test/java/com/openggf/mods/TestModApiSignatureSurface.java`
- Modify: `src/test/java/com/openggf/mods/TestSemanticVersionAndRange.java`
- Modify: `src/test/java/com/openggf/mods/TestEffectiveCatalogBuilder.java`
- Modify: `src/test/java/com/openggf/mods/code/TestS3kModZoneLifecycle.java`
- Modify: `src/main/java/com/openggf/tools/modsdk/LevelConverter.java`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestLevelConverter.java`
- Modify: `docs/modding/formats/level-definition.md`
- Modify: `docs/modding/content-mods.md`
- Modify: `docs/modding/index.md`
- Modify: `docs/architecture/mod-api-compatibility.md`
- Modify: `CHANGELOG.md`
- Create: `src/main/java/com/openggf/game/dataselect/ModZoneSaveFinding.java`
- Create: `src/main/java/com/openggf/game/modzone/ModObjectZoneSet.java`
- Create: `src/main/java/com/openggf/game/modzone/ModPaletteClaim.java`
- Create: `src/main/java/com/openggf/game/modzone/ModPaletteUsageValidator.java`
- Create: `src/main/java/com/openggf/game/modzone/ModZoneAdapter.java`
- Create: `src/main/java/com/openggf/game/modzone/ModZoneHostMetadata.java`
- Create: `src/main/java/com/openggf/game/modzone/ModZoneLevelData.java`
- Create: `src/main/java/com/openggf/game/modzone/ModZoneRegistrationException.java`
- Create: `src/main/java/com/openggf/game/modzone/ModZoneRuntimeContribution.java`
- Create: `src/main/java/com/openggf/game/modzone/ModZoneRuntimeProfile.java`
- Create: `src/main/java/com/openggf/game/palette/CustomZonePaletteBridge.java`
- Modify: `src/main/java/com/openggf/game/GameModule.java`
- Modify: `src/main/java/com/openggf/game/ZoneRegistry.java`
- Modify: `src/main/java/com/openggf/game/patch/DelegatingGameModule.java`
- Modify: `src/main/java/com/openggf/game/sonic2/Sonic2GameModule.java`
- Modify: `src/main/java/com/openggf/game/sonic2/Sonic2ModZoneAdapter.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/S3kCustomZonePaletteBridge.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kGameModule.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kLevel.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneAdapter.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kModZoneRuntimeProfile.java`
- Modify: `src/main/java/com/openggf/level/LevelManager.java`
- Modify: `src/main/java/com/openggf/mods/code/ModBackedGamePatch.java`
- Modify: `src/main/java/com/openggf/mods/code/ModLevelDefinition.java`
- Modify: `src/main/java/com/openggf/mods/code/ModLevelDefinitionParser.java`
- Modify: `src/main/java/com/openggf/mods/code/ModPaletteUsageValidator.java`
- Modify: `src/main/java/com/openggf/mods/code/ModZoneLoader.java`
- Modify: `src/main/java/com/openggf/mods/code/ModZoneRegistry.java`
- Modify: `src/main/java/com/openggf/mods/code/PreparedModZone.java`
- Delete before publication: `src/main/java/com/openggf/mods/code/ModPaletteClaim.java`
- Delete before publication: `src/main/java/com/openggf/mods/code/ModZoneAdapter.java`
- Delete before publication: `src/main/java/com/openggf/mods/code/ModZoneDataSelectDecorator.java`
- Delete before publication: `src/main/java/com/openggf/mods/code/ModZoneRuntimeProfile.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/TestS3kCustomZonePaletteBridge.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/TestSonic3kLevelInMemoryConstruction.java`
- Modify: `src/test/java/com/openggf/mods/code/TestModLevelDefinitionParser.java`
- Modify: `src/test/java/com/openggf/mods/code/TestModPaletteUsageValidator.java`
- Modify: `src/test/java/com/openggf/mods/code/TestModZoneAdapterRouting.java`
- Modify: `src/test/java/com/openggf/mods/code/TestModZoneLoader.java`
- Modify: `src/test/java/com/openggf/mods/code/TestModZoneRuntimeProfile.java`
- Modify: `src/test/java/com/openggf/mods/code/TestS3kModZoneAdapter.java`

- [ ] **Step 1: Generate the candidate signature and verify the version guard is red**

Run: `mvn "-Dtest=com.openggf.mods.TestModApiSignatureSurface" test`

Expected: failure reports the additive types/methods missing from the closed 2.2 snapshot.

- [ ] **Step 2: Set the version and freeze the exact 2.3 surface**

```java
public static final SemanticVersion CURRENT = SemanticVersion.parse("2.3.0");
```

Compile the signature tool, then generate `mods/mod-api-signatures-2.3.txt` with its existing snapshot mode:

```powershell
mvn "-DskipTests" compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/mod-api-snapshot-classpath.txt"
$cp = "target/classes;$((Get-Content target/mod-api-snapshot-classpath.txt -Raw).Trim())"
java -cp $cp com.openggf.mods.code.ModApiSignatureSurface --snapshot | Set-Content -Encoding utf8NoBOM src/test/resources/mods/mod-api-signatures-2.3.txt
```

Retain every 1.1/1.2/2.0/2.1/2.2 snapshot, and update the compatibility test so 2.3 is an additive superset.

In `TestModApiSignatureSurface`, keep `mod-api-signatures-2.2.txt` as `BASELINE_22`, make 2.3 the only `PUBLISHED_BASELINE`, rename the live pin to `publishedTwoThreeSurfaceIsPinnedToTheCurrentSurface`, and add `twoTwoToTwoThreeIsAnAdditiveMinorBump`. The older `twoOneToTwoTwoIsAnAdditiveMinorBump` becomes a closed historical-to-historical comparison and must no longer compare 2.2 directly to the live surface. Plan B depends on these exact two 2.3 method names for its expected-red 2.4 choreography.

- [ ] **Step 3: Document exact v2 JSON, ownership, and host behavior**

Document `hostMetadata.s3k.objectZoneSet`, sparse `paletteClaims`, namespaced-only `S3KL` default, stock-object explicit-set requirement, host line-0 ownership, HUD reservations, empty runtime defaults, and the unchanged v1 S2/standalone path. Update `LevelConverter` inventory validation so v1 requires `palettes.bin` and v2 forbids it.

- [ ] **Step 4: Run the complete adapter gate**

Run: `mvn "-Dtest=com.openggf.mods.code.TestS3kModZoneAdapter,com.openggf.mods.code.TestS3kModZoneLifecycle,com.openggf.game.sonic3k.TestSonic3kLevelInMemoryConstruction,com.openggf.game.sonic3k.TestS3kCustomZonePaletteBridge,com.openggf.mods.code.TestModZoneLoader,com.openggf.mods.integration.TestPhase3StandaloneSampleIntegration,com.openggf.mods.TestModApiSignatureSurface,com.openggf.tools.modsdk.TestLevelConverter" test`

Expected: all tests pass.

- [ ] **Step 5: Run representative stock S3K regression spots**

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.tests.TestSonic3kLevelLoading,com.openggf.tests.trace.s3k.TestS3kAizTraceReplay,com.openggf.tests.trace.s3k.TestS3kHczCompleteRunTraceReplay" test`

Run: `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils" test`

Expected: the must-keep-green suite passes; trace tests pass or retain their checked-in accepted frontier without moving backward. A missing ROM is a blocked gate, never a silent green.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/openggf/mods/ModApiVersion.java src/test/resources/mods/mod-api-signatures-2.3.txt src/test/java/com/openggf/mods/TestModApiSignatureSurface.java src/test/java/com/openggf/mods/TestSemanticVersionAndRange.java src/test/java/com/openggf/mods/TestEffectiveCatalogBuilder.java src/test/java/com/openggf/mods/code/TestS3kModZoneLifecycle.java src/main/java/com/openggf/tools/modsdk/LevelConverter.java src/test/java/com/openggf/tools/modsdk/TestLevelConverter.java docs/modding/formats/level-definition.md docs/modding/content-mods.md docs/modding/index.md docs/architecture/mod-api-compatibility.md docs/superpowers/plans/2026-07-14-s3k-mod-zone-adapter.md CHANGELOG.md
git commit -m "docs: publish S3K mod-zone adapter contract"
```

## Completion gate

- [ ] Run `mvn "-Dtest=com.openggf.mods.code.TestS3kModZoneAdapter,com.openggf.mods.code.TestS3kModZoneLifecycle,com.openggf.game.sonic3k.TestSonic3kLevelInMemoryConstruction,com.openggf.game.sonic3k.TestS3kCustomZonePaletteBridge,com.openggf.mods.code.TestModZoneLoader,com.openggf.mods.TestModApiSignatureSurface,com.openggf.tools.modsdk.TestLevelConverter" test`.
- [ ] Run `mvn package`.
- [ ] Run the mandatory S3K must-keep-green command with `-Ds3k.rom.path=s3k.gen`.
- [ ] Confirm `git diff --check` is clean.
- [ ] Confirm stock S2 sample-zone and standalone fixtures still accept format v1.
- [ ] Confirm no shared mod-runtime source branches on `s2`, `s3k`, `Sonic2GameModule`, or `Sonic3kGameModule` for adapter selection.
