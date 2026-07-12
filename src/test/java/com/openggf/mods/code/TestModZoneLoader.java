package com.openggf.mods.code;

import com.openggf.game.ZoneKey;
import com.openggf.game.MusicReference;
import com.openggf.game.ZoneProgressionPlan;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.GameModule;
import com.openggf.level.LevelDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class TestModZoneLoader {
    @TempDir Path temp;
    @Test
    void zoneKeysRetainStableStockAndOwnerLocalIdentity() {
        assertEquals(new ZoneKey.Stock(3), ZoneKey.stock(3));
        assertEquals(new ZoneKey.Mod("alpha", "sky-palace"), ZoneKey.mod("alpha", "sky-palace"));
        assertThrows(IllegalArgumentException.class, () -> ZoneKey.mod("alpha", "beta:sky-palace"));
    }

    @Test
    void registrationIsOwnerScopedOrderedAndDuplicatePoisonsTransaction() {
        ModContext context = new ModContext("alpha", "s2", nullAssets(), "mtz3");
        context.registerZone(new ModZoneContribution("first", new BakedLevelRef("one/level.json"), null, null));
        context.registerZone(new ModZoneContribution("second", new BakedLevelRef("two/level.json"), "cpz2", null));
        assertThrows(ModRegistrationException.class, context::freeze,
                "missing level bytes must prevent publication of the transaction");

        assertEquals("mtz3", new ModZoneContribution("first", new BakedLevelRef("level.json"), null, null)
                .withDefaultAnchor("mtz3").insertAfter());

        ModContext duplicate = new ModContext("alpha", "s2", nullAssets(), "mtz3");
        duplicate.registerZone(new ModZoneContribution("same", new BakedLevelRef("one/level.json"), null, null));
        assertThrows(ModRegistrationException.class, () -> duplicate.registerZone(
                new ModZoneContribution("same", new BakedLevelRef("two/level.json"), null, null)));
        assertThrows(ModRegistrationException.class, duplicate::freeze);
    }

    @Test
    void zoneRegistrationDefaultsToMtz3AndRejectsNonSonic2Transactionally() {
        ModZoneContribution declaration = new ModZoneContribution(
                "zone", new BakedLevelRef("level.json"), null, null);
        assertEquals("mtz3", declaration.withDefaultAnchor("mtz3").insertAfter());
        ModContext wrongGame = new ModContext("alpha", "s1", nullAssets(), null);
        assertThrows(ModRegistrationException.class, () -> wrongGame.registerZone(declaration));
        assertThrows(ModRegistrationException.class, wrongGame::freeze);
    }

    @Test
    void validNoAnchorRegistrationFreezesWithMtz3AndPlanMismatchIsRejected() throws Exception {
        Path jar = temp.resolve("zone.ggmod");
        try (var output = Files.newOutputStream(jar);
             var archive = new java.util.jar.JarOutputStream(output)) {
            for (var entry : TestModRegistrationRuntime.minimalLevelEntries(0x40, 0x400).entrySet()) {
                archive.putNextEntry(new java.util.jar.JarEntry(entry.getKey()));
                archive.write(entry.getValue());
                archive.closeEntry();
            }
        }
        try (var assets = com.openggf.io.ModAssetRoot.jar(
                temp, jar, com.openggf.io.ModInputLimits.production())) {
            ModContext context = new ModContext("alpha", "s2", assets, null);
            context.registerZone(new ModZoneContribution(
                    "zone", new BakedLevelRef("level.json"), null, null));
            ModRegistrationPlan plan = context.freeze();
            assertEquals("mtz3", plan.zones().getFirst().insertAfter());
            assertEquals("mtz3", plan.preparedZones().getFirst().insertAfter());
        }

        ModZoneContribution declared = new ModZoneContribution(
                "declared", new BakedLevelRef("level.json"), "mtz3", null);
        PreparedModZone wrong = prepared("alpha", "different", 0x400, 0x40);
        assertThrows(IllegalArgumentException.class, () -> new ModRegistrationPlan(
                "alpha", "s2", Map.of(), Map.of(), Map.of(), List.of(),
                List.of(declared), List.of(wrong)));
    }

    @Test
    void aggregateRegistryAppendsInEffectiveOwnerOrderAndBuildsIndependentChains() {
        ZoneRegistry stock = stockRegistry();
        List<PreparedModZone> contributions = List.of(
                PreparedModZone.metadata("alpha", "one", "mtz3", "ONE", 0x400, 0x40, 10, 20),
                PreparedModZone.metadata("alpha", "two", "cpz2", "TWO", 0x401, 0x41, 30, 40),
                PreparedModZone.metadata("beta", "three", "mtz3", "THREE", 0x402, 0x42, 50, 60));

        ZoneRegistry decorated = ModZoneRegistry.decorate(stock, contributions);
        assertEquals(6, decorated.getZoneCount());
        assertEquals(3, decorated.resolveZoneKey(ZoneKey.mod("alpha", "one")).orElseThrow());
        assertEquals(5, decorated.resolveZoneKey(ZoneKey.mod("beta", "three")).orElseThrow());
        assertEquals(ZoneKey.mod("alpha", "two"), decorated.zoneKey(4));
        assertArrayEquals(new int[]{50, 60}, decorated.getStartPosition(5, 0));

        ZoneProgressionPlan plan = decorated.progressionPlan();
        ZoneProgressionPlan.ZoneTopology topology = decorated.progressionTopology();
        assertEquals(new ZoneProgressionPlan.Successor(3, 0), plan.next(topology, 1, 0));
        assertEquals(new ZoneProgressionPlan.Successor(5, 0), plan.next(topology, 3, 0));
        assertEquals(new ZoneProgressionPlan.Successor(2, 0), plan.next(topology, 5, 0));
        assertEquals(new ZoneProgressionPlan.Successor(4, 0), plan.next(topology, 0, 0));
        assertEquals(new ZoneProgressionPlan.Successor(1, 0), plan.next(topology, 4, 0));
    }

    @Test
    void eventChainedAnchorIsRefused() {
        ZoneRegistry stock = stockRegistry();
        PreparedModZone bad = PreparedModZone.metadata("alpha", "one", "tail", "ONE", 0x400, 0x40, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> ModZoneRegistry.decorate(stock, List.of(bad)));
    }

    @Test
    void sonic2PublishesStableStockAnchorsAndExactCompletionTail() {
        ZoneRegistry registry = new com.openggf.game.sonic2.Sonic2ZoneRegistry();
        assertEquals(0, registry.resolveStockZoneAnchor("ehz2"));
        assertEquals(7, registry.resolveStockZoneAnchor("mtz3"));
        assertEquals(ZoneProgressionPlan.Completion.RESULTS_DRIVEN,
                registry.progressionTopology().zone(7).completion());
        assertEquals(ZoneProgressionPlan.Completion.EVENT_CHAINED,
                registry.progressionTopology().zone(8).completion());
        assertEquals(ZoneProgressionPlan.Completion.EVENT_CHAINED,
                registry.progressionTopology().zone(9).completion());
        assertEquals(ZoneProgressionPlan.Completion.TERMINAL,
                registry.progressionTopology().zone(10).completion());
    }

    @Test
    void preparedFixtureBuildsPlayableLevelAndRetainsTaggedSpawnIdentity() throws Exception {
        ModLevelDefinition definition = minimalDefinition();
        PreparedModZone prepared = PreparedModZone.prepared("alpha",
                new ModZoneContribution("zone", new BakedLevelRef("level.json"), "mtz3", null),
                definition);
        var sheet = new com.openggf.level.rings.RingSpriteSheet(
                new com.openggf.level.Pattern[0], List.of(), 1, 8, 0, 0);

        var level = ModZoneLoader.load(prepared, sheet);
        assertEquals(0x40, level.getZoneIndex());
        assertEquals(1, level.getPatternCount());
        assertEquals(2, level.getObjects().size());
        assertNull(level.getObjects().getFirst().objectKey());
        assertEquals("alpha:badnik", level.getObjects().getLast().objectKey());
        assertEquals("alpha", level.getObjects().getLast().ownerModId());
        assertEquals(7, level.getRings().getFirst().placementId());
    }

    @Test
    void creatorEventCallbacksCrossTheRealFaultBoundary() {
        com.openggf.mods.ModRuntimeFindingStore findings = new com.openggf.mods.ModRuntimeFindingStore();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new com.openggf.mods.ModStateSaveResult.Saved(), owners -> {});
        PreparedModZone zone = new PreparedModZone("alpha", "zone", "mtz3", null,
                () -> new com.openggf.game.LevelEventProvider() {
                    public void initLevel(int ignoredZone, int ignoredAct) {}
                    public void update() { throw new IllegalStateException("boom"); }
                }, "ZONE", 0x400, 0x40, 0, 0);
        ZoneRegistry registry = ModZoneRegistry.decorate(stockRegistry(), List.of(zone));
        ModZoneEventProvider provider = new ModZoneEventProvider(null, (ModZoneRegistry) registry, boundary);
        provider.initLevel(3, 0);

        ModFaultBoundary.CallbackAborted aborted = assertThrows(
                ModFaultBoundary.CallbackAborted.class, provider::update);
        assertEquals("alpha", aborted.owner());
        assertEquals(Set.of("alpha"), aborted.disabledOwners());
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("alpha").getFirst().code());
    }

    @Test
    void disableRebuildResolvesPersistedKeysWithoutBindingOldSyntheticIndices() {
        PreparedModZone alpha = PreparedModZone.metadata("alpha", "zone", "mtz3", "A", 0x400, 0x40, 0, 0);
        PreparedModZone beta = PreparedModZone.metadata("beta", "zone", "mtz3", "B", 0x401, 0x41, 0, 0);
        ZoneKey betaKey = ZoneKey.mod("beta", "zone");
        ZoneRegistry both = ModZoneRegistry.decorate(stockRegistry(), List.of(alpha, beta));
        int oldSyntheticIndex = both.resolveZoneKey(betaKey).orElseThrow();

        ZoneRegistry rebuilt = ModZoneRegistry.decorate(stockRegistry(), List.of(beta));
        assertEquals(4, oldSyntheticIndex);
        assertEquals(3, rebuilt.resolveZoneKey(betaKey).orElseThrow());
        assertEquals(betaKey, rebuilt.zoneKey(3));
        assertTrue(rebuilt.resolveZoneKey(ZoneKey.mod("alpha", "zone")).isEmpty());
        assertTrue(rebuilt.resolveZoneKey(ZoneKey.stock(oldSyntheticIndex)).isEmpty(),
                "legacy numeric synthetic values must not bind to a rebuilt mod zone");
    }

    @Test
    void decoratedRegistryRoutesNamespacedMusicWithoutNumericAllocation() {
        ModLevelDefinition definition = minimalDefinition(new ModLevelDefinition.TrackMusic(
                new com.openggf.mods.TrackKey("alpha", "zone-theme")));
        PreparedModZone prepared = PreparedModZone.prepared("alpha",
                new ModZoneContribution("zone", new BakedLevelRef("level.json"), "mtz3", null), definition);
        ZoneRegistry registry = ModZoneRegistry.decorate(stockRegistry(), List.of(prepared));

        assertEquals(MusicReference.namespaced("alpha", "zone-theme"),
                registry.getMusicReference(3, 0));
        assertEquals(-1, registry.getMusicId(3, 0));
    }

    @Test
    void sequentialBackingPatchesFlattenOwnersAndLeaveStockRoutesUntouched() throws Exception {
        GameModule base = moduleProxy(stockRegistry());
        GameModule alpha = new ModBackedGamePatch(zonePlan("alpha", "a", 0x400, 0x40))
                .apply(base, patchContext());
        GameModule beta = new ModBackedGamePatch(zonePlan("beta", "b", 0x401, 0x41))
                .apply(alpha, patchContext());

        ZoneRegistry registry = beta.getZoneRegistry();
        assertEquals(List.of(ZoneKey.mod("alpha", "a"), ZoneKey.mod("beta", "b")),
                List.of(registry.zoneKey(3), registry.zoneKey(4)));
        assertEquals(base.getLevelMusicReference(0, 0), beta.getLevelMusicReference(0, 0));
        assertNull(beta.loadLevelOverride(0));
        assertNull(beta.getBackgroundScrollOverride(0, 12, 34));
    }

    @Test
    void aggregateAuthoredIdCollisionIsRejectedBeforeARegistryCanPublish() {
        PreparedModZone alpha = prepared("alpha", "a", 0x400, 0x40);
        PreparedModZone betaSameZone = prepared("beta", "b", 0x401, 0x40);
        PreparedModZone betaSameLevel = prepared("beta", "c", 0x400, 0x41);
        assertThrows(IllegalArgumentException.class,
                () -> ModZoneRegistry.decorate(stockRegistry(), List.of(alpha, betaSameZone)));
        assertThrows(IllegalArgumentException.class,
                () -> ModZoneRegistry.decorate(stockRegistry(), List.of(alpha, betaSameLevel)));
    }

    private static ModRegistrationPlan zonePlan(String owner, String local, int level, int zone) {
        ModZoneContribution declared = new ModZoneContribution(
                local, new BakedLevelRef(local + "/level.json"), "mtz3", null);
        PreparedModZone prepared = PreparedModZone.prepared(owner, declared,
                definitionWithIds(level, zone));
        return new ModRegistrationPlan(owner, "s2", Map.of(), Map.of(), Map.of(), List.of(),
                List.of(declared), List.of(prepared));
    }

    private static PreparedModZone prepared(String owner, String local, int level, int zone) {
        ModZoneContribution declared = new ModZoneContribution(
                local, new BakedLevelRef(local + "/level.json"), "mtz3", null);
        return PreparedModZone.prepared(owner, declared, definitionWithIds(level, zone));
    }

    private static ModLevelDefinition definitionWithIds(int levelIndex, int zoneIndex) {
        ModLevelDefinition source = minimalDefinition();
        return new ModLevelDefinition(1, source.zoneName(), zoneIndex, levelIndex, 8, 1, 1,
                source.bounds(), source.start(), source.music(), source.objects(), source.rings(),
                source.patternBytes(), source.chunkBytes(), source.blockBytes(), source.foregroundMap(),
                null, source.solidHeights(), source.solidWidths(), source.solidAngles(),
                source.primaryCollisionIndices(), source.secondaryCollisionIndices(), source.paletteLines(),
                1, 1, 1, 1);
    }

    private static com.openggf.game.patch.PatchContext patchContext() {
        return new com.openggf.game.patch.PatchContext(ignored -> null,
                com.openggf.configuration.SonicConfigurationService.createStandalone());
    }

    private static GameModule moduleProxy(ZoneRegistry registry) {
        return (GameModule) java.lang.reflect.Proxy.newProxyInstance(
                TestModZoneLoader.class.getClassLoader(), new Class<?>[]{GameModule.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getZoneRegistry" -> registry;
                    case "getLevelMusicReference" -> registry.getMusicReference((int) args[0], (int) args[1]);
                    case "loadLevelOverride", "getBackgroundScrollOverride", "getLevelEventProvider",
                         "getObjectArtProvider", "getAdditiveLevelRingSpriteSheet" -> null;
                    case "getIdentifier" -> "Sonic2";
                    case "getGameId" -> com.openggf.game.GameId.S2;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static ModLevelDefinition minimalDefinition() {
        return minimalDefinition(new ModLevelDefinition.StockMusic(1));
    }

    private static ModLevelDefinition minimalDefinition(ModLevelDefinition.Music music) {
        return new ModLevelDefinition(1, "ZONE", 0x40, 0x400, 8, 1, 1,
                new ModLevelDefinition.Bounds(0, 0x100, 0, 0x100),
                new ModLevelDefinition.Start(0x20, 0x20), music,
                List.of(new ModLevelDefinition.StockObjectSpawn(1, 10, 20, 3, 0, 0, false, 20),
                        new ModLevelDefinition.KeyedObjectSpawn(2, 30, 40, "alpha:badnik", 0, 0, false, 40)),
                List.of(new ModLevelDefinition.RingEntry(7, 50, 60)),
                new byte[32], new byte[8], new byte[128], new byte[1], null,
                new byte[16], new byte[16], new byte[1], new int[]{0}, new int[]{0},
                new byte[][]{new byte[32], new byte[32], new byte[32], new byte[32]},
                1, 1, 1, 1);
    }

    private static com.openggf.io.ModAssetRoot nullAssets() {
        return com.openggf.io.ModAssetRoot.forTests("zone-test");
    }

    private static ZoneRegistry stockRegistry() {
        return new ZoneRegistry() {
            private final List<List<LevelDescriptor>> zones = List.of(
                    List.of(descriptor(0, 1, 2)), List.of(descriptor(1, 3, 4)),
                    List.of(descriptor(2, 5, 6)));
            public int getZoneCount() { return 3; }
            public int getActCount(int zone) { return 1; }
            public String getZoneName(int zone) { return switch (zone) { case 0 -> "FIRST"; case 1 -> "MIDDLE"; default -> "TAIL"; }; }
            public int[] getStartPosition(int zone, int act) { return switch (zone) { case 0 -> new int[]{1, 2}; case 1 -> new int[]{3, 4}; default -> new int[]{5, 6}; }; }
            public List<LevelDescriptor> getLevelDataForZone(int zone) { return zones.get(zone); }
            public List<List<LevelDescriptor>> getAllZones() { return zones; }
            public int getMusicId(int zone, int act) { return zone; }
            public int resolveStockZoneAnchor(String key) { return switch (key) { case "cpz2" -> 0; case "mtz3" -> 1; case "tail" -> 2; default -> throw new IllegalArgumentException(); }; }
            public ZoneProgressionPlan.ZoneTopology progressionTopology() {
                return ZoneProgressionPlan.ZoneTopology.of(List.of(
                        new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.RESULTS_DRIVEN),
                        new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.RESULTS_DRIVEN),
                        new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.TERMINAL)));
            }
        };
    }

    private static LevelDescriptor descriptor(int index, int x, int y) {
        return new LevelDescriptor() { public int levelIndex() { return index; } public int startX() { return x; } public int startY() { return y; } };
    }
}
