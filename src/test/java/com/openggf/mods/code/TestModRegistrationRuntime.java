package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.patch.RegisteredPatch;
import com.openggf.mods.*;
import com.openggf.mods.validation.ModValidationReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TestModRegistrationRuntime {
    @TempDir Path temp;

    @Test
    void standaloneRegistrationPublishesAtomicallyThroughOwnerBoundary() throws Exception {
        ModDescriptor good = standaloneDescriptor("standalone-good", StandaloneEntrypoint.class);
        ModDescriptor bad = standaloneDescriptor("standalone-bad", ThrowingStandaloneEntrypoint.class);
        EffectiveModCatalog catalog = new EffectiveModCatalog(List.of(good, bad));
        ModClassLoaderFactory factory = new ModClassLoaderFactory(getClass().getClassLoader(),
                (descriptor, snapshot) -> new ModValidationReport(List.of()));

        try (ModRuntime runtime = factory.create(catalog, Set.of("standalone-good", "standalone-bad"))) {
            runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                    owners -> new ModStateSaveResult.Saved(), owners -> { }));

            GameModule module = runtime.prepareStandaloneModule("standalone-good").orElseThrow();

            assertEquals(com.openggf.game.GameId.STANDALONE, module.getGameId());
            assertEquals("standalone-good", module.getGameCode());
            com.openggf.game.CharacterKey hero = com.openggf.game.CharacterKey.mod(
                    "standalone-good", "hero");
            com.openggf.game.CharacterDefinition registered = module.getPlayableCharacterRegistry()
                    .find(hero).orElseThrow();
            assertThrows(ModFaultBoundary.CallbackAborted.class,
                    () -> registered.spriteFactory().create(hero.persisted(), 0, 0));
            assertEquals(Set.of("standalone-good"), runtime.standaloneModules().keySet());
            assertEquals(Set.of("standalone-bad"), runtime.registrationFailures().keySet());
            assertThrows(UnsupportedOperationException.class,
                    () -> runtime.standaloneModules().clear());
            assertNotNull(runtime.standaloneAssetSnapshot("standalone-good"));
        }
    }

    @Test
    void unavailableDependencyPublishesNoStagedStandaloneModule() throws Exception {
        ModDescriptor blocked = standaloneDescriptor("standalone-blocked",
                StandaloneEntrypoint.class,
                List.of(new ModDependency("missing-owner", VersionRange.parse("*"))));
        ModDependencyClassLoader loader = new ModDependencyClassLoader("standalone-blocked",
                new java.net.URL[] { blocked.jarPath().toUri().toURL() },
                getClass().getClassLoader(), List.of());
        com.openggf.io.SnapshotModAssetRoot assets =
                org.mockito.Mockito.mock(com.openggf.io.SnapshotModAssetRoot.class);

        try (ModRuntime runtime = new ModRuntime(Map.of("standalone-blocked", loader),
                Map.of("standalone-blocked", assets), Map.of("standalone-blocked", blocked),
                Map.of())) {
            runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                    owners -> new ModStateSaveResult.Saved(), owners -> { }));
            runtime.newRegistrationPlan();

            assertTrue(runtime.standaloneModules().isEmpty());
            assertEquals(Set.of("standalone-blocked"), runtime.registrationFailures().keySet());
        }
    }

    @Test
    void createsFreshAtomicTransactionsWithBackingFirstAndIsolatesDependents() throws Exception {
        GoodEntrypoint.instances.set(0);
        GoodEntrypoint.retainedAssets.clear();
        ModDescriptor good = descriptor("good", GoodEntrypoint.class, List.of());
        ModDescriptor bad = descriptor("bad", ThrowingEntrypoint.class, List.of());
        ModDescriptor dependent = descriptor("dependent", GoodEntrypoint.class,
                List.of(new ModDependency("bad", VersionRange.parse("*"))));
        ModDescriptor independent = descriptor("independent", GoodEntrypoint.class, List.of());
        EffectiveModCatalog catalog = new EffectiveModCatalog(List.of(good, bad, dependent, independent));
        ModClassLoaderFactory factory = new ModClassLoaderFactory(getClass().getClassLoader(),
                (descriptor, snapshot) -> new ModValidationReport(List.of()));

        try (ModRuntime runtime = factory.create(catalog,
                Set.of("good", "bad", "dependent", "independent"))) {
            var availability = runtime.characterAvailability();
            assertTrue(availability.isKnownOwner("good"));
            assertTrue(availability.isEnabledOwner("bad"));
            assertFalse(availability.isKnownOwner("absent"));
            ModuleResolutionService.PatchPlan first = runtime.newRegistrationPlan();
            assertEquals(List.of("good:content", "good:extra", "independent:content",
                            "independent:extra"),
                    first.registrations().stream().map(r -> r.namespacedId()).toList());
            assertEquals(List.of(0L, 1L, 0L, 1L),
                    first.registrations().stream().map(r -> r.registrationIndex()).toList());
            assertEquals(Set.of("bad", "dependent"), runtime.registrationFailures().keySet());
            assertTrue(runtime.runtimeDisabledOwners().containsAll(Set.of("bad", "dependent")));
            assertFalse(availability.isEnabledOwner("bad"));
            assertFalse(availability.isEnabledOwner("dependent"));
            assertTrue(availability.isEnabledOwner("good"));
            assertThrows(java.io.IOException.class,
                    () -> GoodEntrypoint.retainedAssets.get(0).readBounded("fixture.bin", 16));

            runtime.disableOwnersForProcess(Set.of("good", "dependent"));
            ModuleResolutionService.PatchPlan second = runtime.newRegistrationPlan();
            assertEquals(List.of("independent:content", "independent:extra"),
                    second.registrations().stream().map(r -> r.namespacedId()).toList());
            assertNotSame(first.registrations().get(2).patch(), second.registrations().get(0).patch());
            assertEquals(3, GoodEntrypoint.instances.get());
        }
    }

    @Test
    void effectiveCatalogEnablementUsesExplicitOwnersAndFrozenOrder() throws Exception {
        ModDescriptor first = descriptor("first", GoodEntrypoint.class, List.of());
        ModDescriptor second = descriptor("second", GoodEntrypoint.class, List.of());
        EffectiveCatalogPatchEnablement policy = new EffectiveCatalogPatchEnablement(
                new EffectiveModCatalog(List.of(first, second)));
        assertEquals(com.openggf.game.patch.PatchEnablement.BUILTIN_ORDER,
                policy.orderOf(new com.openggf.game.patch.PatchOwner.BuiltIn("engine")));
        assertEquals(0, policy.orderOf(new com.openggf.game.patch.PatchOwner.Mod("first")));
        assertEquals(1, policy.orderOf(new com.openggf.game.patch.PatchOwner.Mod("second")));
        assertThrows(IllegalArgumentException.class,
                () -> policy.isEnabled(new com.openggf.game.patch.PatchOwner.Mod("unknown")));
    }

    @Test
    void caughtRegistrationErrorsStillDiscardOwnerAndDependentsWithoutHarmingIndependent() throws Exception {
        ModDescriptor duplicate = descriptor("poison-duplicate", HostileCatchingEntrypoint.class, List.of());
        ModDescriptor base = descriptor("poison-base", HostileCatchingEntrypoint.class, List.of());
        ModDescriptor foreign = descriptor("poison-foreign", HostileCatchingEntrypoint.class, List.of());
        ModDescriptor dependent = descriptor("poison-dependent", GoodEntrypoint.class,
                List.of(new ModDependency("poison-duplicate", VersionRange.parse("*"))));
        ModDescriptor independent = descriptor("poison-independent", GoodEntrypoint.class, List.of());
        EffectiveModCatalog catalog = new EffectiveModCatalog(
                List.of(duplicate, base, foreign, dependent, independent));
        ModClassLoaderFactory factory = new ModClassLoaderFactory(getClass().getClassLoader(),
                (descriptor, snapshot) -> new ModValidationReport(List.of()));

        try (ModRuntime runtime = factory.create(catalog, Set.of("poison-duplicate", "poison-base",
                "poison-foreign", "poison-dependent", "poison-independent"))) {
            ModuleResolutionService.PatchPlan plan = runtime.newRegistrationPlan();
            assertEquals(List.of("poison-independent:content", "poison-independent:extra"),
                    plan.registrations().stream().map(r -> r.namespacedId()).toList());
            assertEquals(Set.of("poison-duplicate", "poison-base", "poison-foreign",
                            "poison-dependent"), runtime.registrationFailures().keySet());
        }
    }

    @Test
    void aggregateAuthoredIdCollisionExcludesOwnerAndDependentButPublishesEarlierAndIndependent()
            throws Exception {
        ModDescriptor earlier = zoneDescriptor("zone-earlier", 0x40, 0x400, List.of());
        ModDescriptor collision = zoneDescriptor("zone-collision", 0x40, 0x401, List.of());
        ModDescriptor dependent = zoneDescriptor("zone-dependent", 0x42, 0x402,
                List.of(new ModDependency("zone-collision", VersionRange.parse("*"))));
        ModDescriptor independent = zoneDescriptor("zone-independent", 0x43, 0x403, List.of());
        EffectiveModCatalog catalog = new EffectiveModCatalog(
                List.of(earlier, collision, dependent, independent));
        ModClassLoaderFactory factory = new ModClassLoaderFactory(getClass().getClassLoader(),
                (descriptor, snapshot) -> new ModValidationReport(List.of()));

        try (ModRuntime runtime = factory.create(catalog,
                Set.of("zone-earlier", "zone-collision", "zone-dependent", "zone-independent"))) {
            ModuleResolutionService.PatchPlan plan = runtime.newRegistrationPlan();
            assertEquals(List.of("zone-earlier:content", "zone-independent:content"),
                    plan.registrations().stream().map(RegisteredPatch::namespacedId).toList());
            assertEquals(Set.of("zone-collision", "zone-dependent"),
                    runtime.registrationFailures().keySet());
            assertTrue(runtime.registrationFailures().get("zone-collision").getMessage()
                    .contains("zoneIndex"));
            assertTrue(runtime.runtimeDisabledOwners().containsAll(
                    Set.of("zone-collision", "zone-dependent")));
        }
    }

    public static final class GoodEntrypoint implements GgfMod {
        static final AtomicInteger instances = new AtomicInteger();
        static final List<com.openggf.io.ModAssetRoot> retainedAssets = new ArrayList<>();
        public GoodEntrypoint() { instances.incrementAndGet(); }
        @Override public void register(ModContext context) {
            context.registerObject("thing", (spawn, registry) -> null);
            context.registerGamePatch(new Patch("extra", context.baseGameId()));
            retainedAssets.add(context.modAssets());
        }
    }

    public static final class ThrowingEntrypoint implements GgfMod {
        @Override public void register(ModContext context) {
            context.registerObject("partial", (spawn, registry) -> null);
            throw new IllegalStateException("registration failed");
        }
    }

    public static final class HostileCatchingEntrypoint implements GgfMod {
        @Override public void register(ModContext context) {
            context.registerObject("staged", (spawn, registry) -> null);
            try {
                if (context.ownerModId().endsWith("duplicate")) {
                    context.registerObject("staged", (spawn, registry) -> null);
                } else if (context.ownerModId().endsWith("base")) {
                    context.registerGamePatch(new Patch("bad-base", "s1"));
                } else {
                    context.registerGamePatch(new Patch("another-owner:patch", "s2"));
                }
            } catch (ModRegistrationException ignored) {
                // Hostile creator attempts to keep and publish the earlier staged object.
            }
        }
    }

    public static final class ZoneEntrypoint implements GgfMod {
        @Override public void register(ModContext context) {
            context.registerZone(new ModZoneContribution(
                    "zone", new BakedLevelRef("level.json"), null, null, false));
        }
    }

    public static final class StandaloneEntrypoint implements GgfMod {
        @Override public void register(ModContext context) {
            context.registerGameModule(new StandaloneModule(context.ownerModId()));
            com.openggf.game.CharacterKey key = com.openggf.game.CharacterKey.mod(
                    context.ownerModId(), "hero");
            context.registerCharacter("hero", new com.openggf.game.CharacterDefinition(
                    key, "Hero", (code, x, y) -> {
                        throw new IllegalStateException("character callback");
                    }, null, com.openggf.game.PlayerCharacter.SONIC_ALONE,
                    com.openggf.sprites.playable.SecondaryAbility.NONE, false,
                    code -> org.mockito.Mockito.mock(com.openggf.sprites.art.SpriteArtSet.class)));
        }
    }

    public static final class ThrowingStandaloneEntrypoint implements GgfMod {
        @Override public void register(ModContext context) {
            context.registerGameModule(new StandaloneModule(context.ownerModId()));
            throw new IllegalStateException("discard staged standalone module");
        }
    }

    private static final class StandaloneModule extends com.openggf.game.AbstractStandaloneGameModule {
        private final String owner;
        private StandaloneModule(String owner) { this.owner = owner; }
        @Override public String getIdentifier() { return owner; }
        @Override public com.openggf.data.Game createGame(com.openggf.game.GameDataSource source) {
            return new com.openggf.game.ModGame(owner, source) {
                @Override public com.openggf.level.Level loadLevel(int levelIdx) { return null; }
                @Override public int getMusicId(int levelIdx) { return 0; }
            };
        }
        @Override public com.openggf.level.objects.TouchResponseTable createTouchResponseTable(
                com.openggf.game.GameDataSource source) {
            return org.mockito.Mockito.mock(com.openggf.level.objects.TouchResponseTable.class);
        }
        @Override public com.openggf.level.objects.ObjectRegistry createObjectRegistry() {
            return org.mockito.Mockito.mock(com.openggf.level.objects.ObjectRegistry.class);
        }
        @Override public com.openggf.level.objects.ObjectPlacementEncoding getObjectPlacementEncoding() {
            return org.mockito.Mockito.mock(com.openggf.level.objects.ObjectPlacementEncoding.class);
        }
        @Override public com.openggf.audio.GameAudioProfile getAudioProfile() {
            return org.mockito.Mockito.mock(com.openggf.audio.GameAudioProfile.class);
        }
        @Override public com.openggf.game.ZoneRegistry getZoneRegistry() {
            return org.mockito.Mockito.mock(com.openggf.game.ZoneRegistry.class);
        }
        @Override public com.openggf.game.PhysicsProvider getPhysicsProvider() {
            return org.mockito.Mockito.mock(com.openggf.game.PhysicsProvider.class);
        }
    }

    private record Patch(String id, String baseGameId) implements GamePatch {
        @Override public String displayName() { return id; }
        @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) { return base; }
    }

    private ModDescriptor descriptor(String id, Class<?> entrypoint,
                                     List<ModDependency> dependencies) throws Exception {
        Path jar = temp.resolve(id + ".jar");
        try (OutputStream output = Files.newOutputStream(jar); JarOutputStream archive = new JarOutputStream(output)) {
            archive.putNextEntry(new JarEntry("fixture.bin"));
            archive.write(new byte[] {1, 2, 3});
            archive.closeEntry();
        }
        ModManifest manifest = new ModManifest(1, id, id, SemanticVersion.parse("1.0.0"),
                List.of("test"), "test", VersionRange.parse("*"), ModType.PATCH, "s2",
                entrypoint.getName(), dependencies, Map.of(), Map.of(), null, OptionalInt.empty());
        String hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(jar)));
        return new ModDescriptor(jar, manifest, hash, true, List.of());
    }

    private ModDescriptor standaloneDescriptor(String id, Class<?> entrypoint) throws Exception {
        return standaloneDescriptor(id, entrypoint, List.of());
    }

    private ModDescriptor standaloneDescriptor(String id, Class<?> entrypoint,
                                               List<ModDependency> dependencies) throws Exception {
        Path jar = temp.resolve(id + ".jar");
        try (OutputStream output = Files.newOutputStream(jar);
             JarOutputStream archive = new JarOutputStream(output)) {
            archive.putNextEntry(new JarEntry("fixture.bin"));
            archive.write(new byte[] { 1, 2, 3 });
            archive.closeEntry();
        }
        ModManifest manifest = new ModManifest(1, id, id, SemanticVersion.parse("1.0.0"),
                List.of("test"), "test", VersionRange.parse("*"), ModType.STANDALONE, null,
                entrypoint.getName(), dependencies, Map.of(), Map.of(), null, OptionalInt.empty());
        String hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(jar)));
        return new ModDescriptor(jar, manifest, hash, true, List.of());
    }

    private ModDescriptor zoneDescriptor(String id, int zoneIndex, int levelIndex,
                                         List<ModDependency> dependencies) throws Exception {
        Path jar = temp.resolve(id + ".jar");
        Map<String, byte[]> entries = minimalLevelEntries(zoneIndex, levelIndex);
        try (OutputStream output = Files.newOutputStream(jar);
             JarOutputStream archive = new JarOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                archive.putNextEntry(new JarEntry(entry.getKey()));
                archive.write(entry.getValue());
                archive.closeEntry();
            }
        }
        ModManifest manifest = new ModManifest(1, id, id, SemanticVersion.parse("1.0.0"),
                List.of("test"), "test", VersionRange.parse("*"), ModType.PATCH, "s2",
                ZoneEntrypoint.class.getName(), dependencies, Map.of(), Map.of(), null,
                OptionalInt.empty());
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(jar)));
        return new ModDescriptor(jar, manifest, hash, true, List.of());
    }

    static Map<String, byte[]> minimalLevelEntries(int zoneIndex, int levelIndex)
            throws Exception {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        String json = "{" +
                "\"formatVersion\":1,\"zoneName\":\"Runtime Zone\"," +
                "\"zoneIndex\":" + zoneIndex + ",\"levelIndex\":" + levelIndex + "," +
                "\"blockGridSide\":8,\"width\":1,\"height\":1," +
                "\"bounds\":{\"minX\":0,\"maxX\":127,\"minY\":0,\"maxY\":96}," +
                "\"start\":{\"x\":32,\"y\":48},\"music\":{\"stockId\":129}," +
                "\"assets\":{\"patterns\":\"patterns.bin\",\"chunks\":\"chunks.bin\"," +
                "\"blocks\":\"blocks.bin\",\"foregroundMap\":\"fg-map.bin\"," +
                "\"solidHeights\":\"solid-heights.bin\",\"solidWidths\":\"solid-widths.bin\"," +
                "\"solidAngles\":\"solid-angles.bin\",\"collisionPrimary\":\"collision-primary.bin\"," +
                "\"collisionSecondary\":\"collision-secondary.bin\",\"palettes\":\"palettes.bin\"}," +
                "\"objects\":[],\"rings\":[]}";
        entries.put("level.json", json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("patterns.bin", binary(out -> { out.writeBytes("GPTN"); out.writeShort(1);
            out.writeShort(32); out.writeInt(1); out.write(new byte[32]); }));
        entries.put("chunks.bin", binary(out -> { out.writeBytes("GCHK"); out.writeShort(1);
            out.writeShort(8); out.writeInt(1); out.write(new byte[8]); }));
        entries.put("blocks.bin", binary(out -> { out.writeBytes("GBLK"); out.writeShort(1);
            out.writeByte(8); out.writeByte(0); out.writeInt(1); out.write(new byte[128]); }));
        entries.put("fg-map.bin", binary(out -> { out.writeBytes("GMAP"); out.writeShort(1);
            out.writeShort(1); out.writeShort(1); out.writeShort(1); out.writeInt(1); out.writeByte(0); }));
        entries.put("solid-heights.bin", records("GSHG", 16, new byte[16]));
        entries.put("solid-widths.bin", records("GSWD", 16, new byte[16]));
        entries.put("solid-angles.bin", records("GSAN", 1, new byte[1]));
        entries.put("collision-primary.bin", collision(0));
        entries.put("collision-secondary.bin", collision(1));
        entries.put("palettes.bin", binary(out -> { out.writeBytes("GPAL"); out.writeShort(1);
            out.writeShort(4); out.writeShort(16); out.writeShort(0); out.write(new byte[128]); }));
        return entries;
    }

    private static byte[] records(String magic, int size, byte[] payload) throws Exception {
        return binary(out -> { out.writeBytes(magic); out.writeShort(1); out.writeShort(size);
            out.writeInt(1); out.write(payload); });
    }

    private static byte[] collision(int path) throws Exception {
        return binary(out -> { out.writeBytes("GCOL"); out.writeShort(1); out.writeByte(path);
            out.writeByte(2); out.writeInt(1); out.writeShort(0); });
    }

    private static byte[] binary(IoWriter writer) throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) { writer.write(out); }
        return bytes.toByteArray();
    }

    @FunctionalInterface private interface IoWriter {
        void write(java.io.DataOutputStream out) throws Exception;
    }
}
