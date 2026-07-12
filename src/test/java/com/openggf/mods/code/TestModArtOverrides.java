package com.openggf.mods.code;

import com.openggf.ModSubsystem;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.GameId;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.patch.PatchContext;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.Pattern;
import com.openggf.level.objects.BakedSheetReader;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.art.ObjectArtBundle;
import com.openggf.mods.*;
import com.openggf.mods.validation.ModValidationReport;
import com.openggf.tools.modsdk.BakedSheetWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TestModArtOverrides {
    @TempDir Path temp;

    @Test
    void validatedSheetsOverrideBaseAndLaterBackingPatchWins() throws Exception {
        ObjectSpriteSheet stock = sheet(1);
        ObjectArtProvider baseArt = provider(Map.of("shared", stock), null);
        GameModule base = module(baseArt);

        ModBackedGamePatch first = artPatch("first", "shared", baked(2));
        ModBackedGamePatch second = artPatch("second", "shared", baked(3));
        GameModule firstApplied = first.apply(base, null);
        GameModule secondApplied = second.apply(firstApplied, null);

        assertSame(stock, base.getObjectArtProvider().getSheet("shared"));
        assertEquals(2, firstApplied.getObjectArtProvider().getSheet("shared")
                .getPatterns()[0].getPixel(0, 0));
        assertEquals(3, secondApplied.getObjectArtProvider().getSheet("shared")
                .getPatterns()[0].getPixel(0, 0));
        assertEquals(List.of("shared", "stock-only"),
                secondApplied.getObjectArtProvider().getRendererKeys());
        assertSame(stock, secondApplied.getObjectArtProvider().getSheet("stock-only"));
        assertSame(secondApplied.getObjectArtProvider(), secondApplied.getObjectArtProvider(),
                "applied module must retain one overlay and renderer cache");
    }

    @Test
    void effectiveCatalogBuildsManifestOnlyBackingPatchesAndResolvesInOwnerOrder() throws Exception {
        ModDescriptor first = dataDescriptor("first-data", Map.of("EndSign", "art/first.ggfsheet"),
                Map.of("art/first.ggfsheet", BakedSheetWriter.write(baked(2))));
        ModDescriptor code = codeDescriptor("code-owner", ExplicitPatchEntrypoint.class,
                Map.of("EndSign", "art/code.ggfsheet"),
                Map.of("art/code.ggfsheet", BakedSheetWriter.write(baked(4))));
        ModDescriptor last = dataDescriptor("last-data", Map.of("EndSign", "art/last.ggfsheet"),
                Map.of("art/last.ggfsheet", BakedSheetWriter.write(baked(3))));
        EffectiveModCatalog effective = new EffectiveModCatalog(List.of(first, code, last));

        ModClassLoaderFactory factory = new ModClassLoaderFactory(getClass().getClassLoader(),
                (descriptor, snapshot) -> new ModValidationReport(List.of()));
        try (ModRuntime runtime = factory.create(effective, Set.of("code-owner"))) {
            ModuleResolutionService.PatchPlan plan = runtime.newRegistrationPlan();
            assertEquals(List.of("first-data:content", "code-owner:content", "code-owner:extra",
                            "last-data:content"),
                    plan.registrations().stream().map(r -> r.namespacedId()).toList());

            var enablement = new EffectiveCatalogPatchEnablement(effective);
            ModuleResolutionService resolver = new ModuleResolutionService(List.of(), enablement,
                    new LogicalRomResolver(() -> null), SonicConfigurationService.createStandalone(),
                    ignored -> plan);
            GameModule resolved = resolver.resolveForLaunch(module(provider(
                            Map.of("EndSign", sheet(1)), null)),
                    new GameplayLaunchRequest("s2", "sonic", List.of()),
                    ModuleResolutionService.LaunchPolicy.STANDARD);

            assertEquals(3, resolved.getObjectArtProvider().getSheet("EndSign")
                    .getPatterns()[0].getPixel(0, 0), "later effective data owner wins");
            assertNull(resolved.getObjectArtProvider().getSheet("endsign"),
                    "stock art keys remain case-exact");
            assertEquals(1, ExplicitPatchEntrypoint.applies);
            assertSame(resolved.getObjectArtProvider(), resolved.getObjectArtProvider());
        }
    }

    @Test
    void untrustedCodeOwnerCannotActivateManifestArtWhileDataOnlyOwnerCan() throws Exception {
        ModDescriptor hostile = codeDescriptor("hostile", ExplicitPatchEntrypoint.class,
                Map.of("EndSign", "art/hostile.ggfsheet"),
                Map.of("art/hostile.ggfsheet", BakedSheetWriter.write(baked(7))));
        EffectiveModCatalog effective = new EffectiveModCatalog(List.of(hostile));
        ModClassLoaderFactory factory = new ModClassLoaderFactory(getClass().getClassLoader(),
                (descriptor, snapshot) -> new ModValidationReport(List.of()));

        try (ModRuntime runtime = factory.create(effective, Set.of())) {
            assertTrue(runtime.owners().isEmpty());
            assertTrue(runtime.newRegistrationPlan().registrations().isEmpty());
            assertThrows(ClassNotFoundException.class,
                    () -> runtime.loadOwned("hostile", ExplicitPatchEntrypoint.class.getName()));
        }
    }

    @Test
    void artOwnerIsRejectedWhenItsCodeDependencyIsUntrusted() throws Exception {
        ModDescriptor dependency = codeDescriptor("code-dependency", ExplicitPatchEntrypoint.class,
                Map.of(), Map.of("placeholder.bin", new byte[] {1}));
        ModDescriptor dependent = dataDescriptor("dependent", Map.of("EndSign", "art/end.ggfsheet"),
                Map.of("art/end.ggfsheet", BakedSheetWriter.write(baked(5))),
                List.of(new ModDependency("code-dependency", VersionRange.parse("*"))));
        EffectiveModCatalog effective = new EffectiveModCatalog(List.of(dependency, dependent));

        try (ModRuntime runtime = new ModClassLoaderFactory(getClass().getClassLoader(),
                (descriptor, snapshot) -> new ModValidationReport(List.of()))
                .create(effective, Set.of())) {
            assertTrue(runtime.owners().isEmpty());
            assertTrue(runtime.newRegistrationPlan().registrations().isEmpty());
            assertEquals(ModRuntime.RejectionReason.DEPENDENCY_UNAVAILABLE,
                    runtime.rejectedOwners().get("dependent").reason());
        }
    }

    @Test
    void artOwnerIsRejectedWhenItsDataDependencyFailsSnapshotIdentity() throws Exception {
        ModDescriptor validDependency = dataDescriptor("data-dependency",
                Map.of("EndSign", "art/base.ggfsheet"),
                Map.of("art/base.ggfsheet", BakedSheetWriter.write(baked(2))));
        ModDescriptor changedDependency = new ModDescriptor(validDependency.jarPath(),
                validDependency.manifest(), "0".repeat(64), false, List.of());
        ModDescriptor dependent = dataDescriptor("dependent", Map.of("EndSign", "art/end.ggfsheet"),
                Map.of("art/end.ggfsheet", BakedSheetWriter.write(baked(5))),
                List.of(new ModDependency("data-dependency", VersionRange.parse("*"))));
        EffectiveModCatalog effective = new EffectiveModCatalog(List.of(changedDependency, dependent));

        try (ModRuntime runtime = new ModClassLoaderFactory(getClass().getClassLoader(),
                (descriptor, snapshot) -> new ModValidationReport(List.of()))
                .create(effective, Set.of())) {
            assertTrue(runtime.owners().isEmpty());
            assertTrue(runtime.newRegistrationPlan().registrations().isEmpty());
            assertEquals(ModRuntime.RejectionReason.HASH_MISMATCH,
                    runtime.rejectedOwners().get("data-dependency").reason());
            assertEquals(ModRuntime.RejectionReason.DEPENDENCY_UNAVAILABLE,
                    runtime.rejectedOwners().get("dependent").reason());
        }
    }

    @Test
    void structuredPreparationFailureIsPublishedWithoutFlattening() {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        ModSubsystem subsystem = new ModSubsystem(
                new ModCatalog(List.of(), EffectiveModCatalog.EMPTY), findings,
                (rate, game) -> com.openggf.SessionExternalContentView.EMPTY);
        ModRegistrationException failure = new ModRegistrationException("owner",
                "MOD_ART_ASSET_INVALID", "Missing or invalid object-art asset: art/missing.ggfsheet",
                "art/missing.ggfsheet", new IOException("missing"));

        subsystem.recordRegistrationFailures(Map.of("owner", failure));

        ModFinding finding = findings.findingsFor("owner").get(0);
        assertEquals("MOD_ART_ASSET_INVALID", finding.code());
        assertEquals("art/missing.ggfsheet", finding.assetPath());
        assertEquals(failure.getMessage(), finding.message());
    }

    @Test
    void artOnlyRegistrationStillGetsExactlyOneEngineOwnedBackingPatchFirst() {
        ModRegistrationPlan plan = new ModRegistrationPlan("art-owner", "s2", Map.of(),
                Map.of("art-owner:shared", new BakedSheetRef("art/shared.ggfsheet")),
                Map.of("art-owner:shared", baked(4)), List.of());
        assertTrue(plan.hasContent());

        ModBackedGamePatch backing = new ModBackedGamePatch(plan);
        var registrations = com.openggf.game.patch.ModPatchPlanAssembler.backingFirst(
                new com.openggf.game.patch.PatchOwner.Mod("art-owner"), backing,
                plan.explicitPatches());

        assertEquals(1, registrations.size());
        assertSame(backing, registrations.get(0).patch());
        assertEquals("art-owner:content", registrations.get(0).namespacedId());
        assertEquals(0L, registrations.get(0).registrationIndex());
    }

    @Test
    void missingAndInvalidDeclaredAssetsAreStructuredPreparationFailures() throws Exception {
        Path missingJar = jar(Map.of());
        try (var assets = ModAssetRoot.jar(temp, missingJar, ModInputLimits.production())) {
            ModRegistrationPlan declared = declared("owner", "art/missing.ggfsheet");
            ModRegistrationException failure = assertThrows(ModRegistrationException.class,
                    () -> declared.prepareObjectArt(assets));
            assertEquals("MOD_ART_ASSET_INVALID", failure.findingCode());
            assertEquals("art/missing.ggfsheet", failure.path());
            assertThrows(IllegalArgumentException.class, () -> new ModBackedGamePatch(declared));
        }

        Path invalidJar = jar(Map.of("art/bad.ggfsheet", new byte[] {1, 2, 3}));
        try (var assets = ModAssetRoot.jar(temp, invalidJar, ModInputLimits.production())) {
            ModRegistrationException failure = assertThrows(ModRegistrationException.class,
                    () -> declared("owner", "art/bad.ggfsheet").prepareObjectArt(assets));
            assertEquals("MOD_ART_ASSET_INVALID", failure.findingCode());
            assertEquals("art/bad.ggfsheet", failure.path());
        }
    }

    @Test
    void runtimeLoadFailurePropagatesWithoutFallingBackOrDiscardingOverlay() throws Exception {
        IOException expected = new IOException("runtime read failed");
        ObjectArtProvider base = provider(Map.of("shared", sheet(1)), expected);
        ObjectArtProvider overlay = ModArtOverlayProvider.decorate(base, Map.of("shared", baked(2)));

        IOException thrown = assertThrows(IOException.class, () -> overlay.loadArtForZone(0));
        assertSame(expected, thrown);
        assertEquals(2, overlay.getSheet("shared").getPatterns()[0].getPixel(0, 0));
    }

    private static ModBackedGamePatch artPatch(String owner, String key,
                                                BakedSheetReader.BakedSheet sheet) {
        String owned = owner + ":" + key;
        return new ModBackedGamePatch(new ModRegistrationPlan(owner, "s2", Map.of(),
                Map.of(key, new BakedSheetRef("art/" + owner + ".ggfsheet")),
                Map.of(key, sheet), List.of()));
    }

    private static ModRegistrationPlan declared(String owner, String path) {
        return new ModRegistrationPlan(owner, "s2", Map.of(),
                Map.of(owner + ":shared", new BakedSheetRef(path)), List.of());
    }

    private ModDescriptor dataDescriptor(String id, Map<String, String> art,
                                         Map<String, byte[]> entries) throws Exception {
        return dataDescriptor(id, art, entries, List.of());
    }

    private ModDescriptor dataDescriptor(String id, Map<String, String> art,
                                         Map<String, byte[]> entries,
                                         List<ModDependency> dependencies) throws Exception {
        return descriptor(id, null, false, art, entries, dependencies);
    }

    private ModDescriptor codeDescriptor(String id, Class<?> entrypoint, Map<String, String> art,
                                         Map<String, byte[]> entries) throws Exception {
        return descriptor(id, entrypoint, true, art, entries, List.of());
    }

    private ModDescriptor descriptor(String id, Class<?> entrypoint, boolean containsCode,
                                     Map<String, String> art, Map<String, byte[]> entries,
                                     List<ModDependency> dependencies)
            throws Exception {
        Path path = jar(entries);
        ModManifest manifest = new ModManifest(1, id, id, SemanticVersion.parse("1.0.0"),
                List.of("test"), "test", VersionRange.parse("*"), ModType.PATCH, "s2",
                entrypoint == null ? null : entrypoint.getName(), dependencies, Map.of(), art,
                null, OptionalInt.empty());
        String hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(java.nio.file.Files.readAllBytes(path)));
        return new ModDescriptor(path, manifest, hash, containsCode, List.of());
    }

    private Path jar(Map<String, byte[]> entries) throws Exception {
        Path jar = temp.resolve("fixture-" + System.nanoTime() + ".jar");
        try (JarOutputStream output = new JarOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return jar;
    }

    private static BakedSheetReader.BakedSheet baked(int color) {
        Pattern pattern = new Pattern();
        pattern.setPixel(0, 0, (byte) color);
        return new BakedSheetReader.BakedSheet(new Pattern[] {pattern}, List.of(), Optional.empty());
    }

    private static ObjectSpriteSheet sheet(int color) {
        return baked(color).toObjectSpriteSheet();
    }

    private static ObjectArtProvider provider(Map<String, ObjectSpriteSheet> sheets,
                                              IOException loadFailure) {
        Map<String, ObjectSpriteSheet> copy = new LinkedHashMap<>(sheets);
        copy.putIfAbsent("stock-only", sheets.values().iterator().next());
        return (ObjectArtProvider) Proxy.newProxyInstance(TestModArtOverrides.class.getClassLoader(),
                new Class<?>[] {ObjectArtProvider.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "loadArtForZone" -> {
                        if (loadFailure != null) throw loadFailure;
                        yield null;
                    }
                    case "getSheet" -> copy.get((String) args[0]);
                    case "getRenderer", "getAnimations", "getHudStaticArt",
                            "getHudLivesPaletteOverride" -> null;
                    case "getRendererKeys" -> List.copyOf(copy.keySet());
                    case "getArtBundle" -> ObjectArtBundle.builder().sheets(copy).build();
                    case "getZoneData" -> -1;
                    case "getHudDigitPatterns", "getHudTextPatterns", "getHudLivesPatterns",
                            "getHudLivesNumbers", "getHudHexDigitPatterns" -> null;
                    case "ensurePatternsCached" -> (int) args[1];
                    case "isReady" -> true;
                    case "getHudTextPaletteLine" -> 1;
                    case "getHudFlashPaletteLine" -> 0;
                    case "registerLevelTileArt", "reloadStandaloneArtForActTransition" -> null;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static GameModule module(ObjectArtProvider art) {
        return (GameModule) Proxy.newProxyInstance(TestModArtOverrides.class.getClassLoader(),
                new Class<?>[] {GameModule.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getObjectArtProvider" -> art;
                    case "getGameId" -> GameId.S2;
                    default -> defaultValue(method.getReturnType());
                });
    }

    public static final class ExplicitPatchEntrypoint implements GgfMod {
        static int applies;
        @Override public void register(ModContext context) {
            applies = 0;
            context.registerGamePatch(new GamePatch() {
                @Override public String id() { return "extra"; }
                @Override public String displayName() { return "extra"; }
                @Override public String baseGameId() { return "s2"; }
                @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
                @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
                @Override public List<String> providedMainCharacters() { return List.of(); }
                @Override public GameModule apply(GameModule base, PatchContext context) {
                    applies++;
                    return base;
                }
            });
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        throw new AssertionError(type);
    }
}
