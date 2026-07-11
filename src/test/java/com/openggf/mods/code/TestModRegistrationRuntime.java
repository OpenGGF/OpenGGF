package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.patch.PatchContext;
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
            ModuleResolutionService.PatchPlan first = runtime.newRegistrationPlan();
            assertEquals(List.of("good:content", "good:extra", "independent:content",
                            "independent:extra"),
                    first.registrations().stream().map(r -> r.namespacedId()).toList());
            assertEquals(List.of(0L, 1L, 0L, 1L),
                    first.registrations().stream().map(r -> r.registrationIndex()).toList());
            assertEquals(Set.of("bad", "dependent"), runtime.registrationFailures().keySet());
            assertTrue(runtime.runtimeDisabledOwners().containsAll(Set.of("bad", "dependent")));
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
}
