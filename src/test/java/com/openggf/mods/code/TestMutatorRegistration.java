package com.openggf.mods.code;

import com.openggf.io.ModAssetRoot;
import com.openggf.mods.mutators.MutatorCapability;
import com.openggf.mods.mutators.MutatorDefinition;
import com.openggf.mods.mutators.MutatorPolicy;
import com.openggf.mods.mutators.MutatorScope;
import com.openggf.game.GameModule;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.level.LevelPatch;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.mutators.MutatorCatalog;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestMutatorRegistration {
    @AfterEach void resetNativeServices() { com.openggf.tests.TestEnvironment.resetAll(); }
    @Test
    void ownerIsDerivedAndPlanIsImmutableContentWithoutOrdinaryPatch() {
        ModContext context = context("owner", "s2");
        context.registerMutator(definition("gravity"));
        context.registerMutator(definition("second"));
        ModRegistrationPlan plan = context.freeze();
        assertTrue(plan.hasContent());
        assertEquals(List.of("owner:gravity", "owner:second"), List.copyOf(plan.mutators().keySet()));
        assertEquals("owner", plan.mutators().get("owner:gravity").ownerModId());
        assertThrows(UnsupportedOperationException.class, () -> plan.mutators().clear());
        assertThrows(ModRegistrationException.class, () -> context.registerMutator(definition("later")));
    }

    @Test
    void duplicateAndInvalidGraphPoisonAllContributions() {
        ModContext duplicate = context("owner", "s2");
        duplicate.registerMutator(definition("gravity"));
        ModRegistrationException failure = assertThrows(ModRegistrationException.class,
                () -> duplicate.registerMutator(definition("gravity")));
        assertSame(failure, assertThrows(ModRegistrationException.class, duplicate::freeze));
        ModContext invalid = context("owner", "s2");
        invalid.registerMutator(new MutatorDefinition("bad", "Bad", "", 1, MutatorScope.LIVE,
                MutatorScope.LIVE, List.of(), List.of(), Set.of("absent"), Set.of(),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY), options -> List.of(new MutatorPolicy.DrySonicGravity(100))));
        failure = assertThrows(ModRegistrationException.class, invalid::freeze);
        assertSame(failure, assertThrows(ModRegistrationException.class, invalid::freeze));
    }

    @Test
    void sharedCatalogueExpandsOwnedPoliciesWhileStandaloneStillRejectsGameplayMutators() {
        var shared = context("owner", "any");
        shared.registerMutator(definition("gravity"));
        var expanded = shared.freeze().stockScenePlans();
        assertEquals(List.of("s1", "s2", "s3k"), expanded.stream().map(ModRegistrationPlan::baseGameId).toList());
        assertTrue(expanded.stream().allMatch(plan -> plan.mutators().get("owner:gravity").ownerModId().equals("owner")));
        ModContext standalone = new ModContext("owner", null, ModAssetRoot.forTests("owner"), null, true);
        assertThrows(ModRegistrationException.class, () -> standalone.registerMutator(definition("gravity")));
    }

    @Test
    void mixedCodeCatalogueSharesContentAndRoutesEachDecoratorOnlyToItsNativeGame() {
        var shared = context("owner", "any");
        shared.registerMutator(definition("gravity"));
        shared.registerGamePatch(patch("s3k"));
        shared.registerGamePatch(patch("s1"));
        shared.registerGamePatch(patch("s2"));
        var factories = new AtomicInteger();
        shared.registerObject("object", (spawn, registry) -> { factories.incrementAndGet(); return null; });
        shared.registerObjectArt("sheet", new BakedSheetRef("art/sheet.bin"));
        shared.registerObjectPreview("object", "sheet");
        shared.registerServiceBundle("session", () -> {
            factories.incrementAndGet();
            return GameServiceBundle.builder().build();
        });
        var plans = shared.freeze().stockScenePlans();
        assertEquals(List.of("s1", "s2", "s3k"), plans.stream().map(ModRegistrationPlan::baseGameId).toList());
        assertEquals(0, factories.get(), "Registration and expansion must defer creator factories");
        for (var plan : plans) {
            assertNull(plan.startupScene());
            assertEquals(Set.of("owner:object"), plan.objectFactories().keySet());
            assertEquals(Set.of("owner:sheet"), plan.objectArt().keySet());
            assertEquals(Map.of("owner:object", "owner:sheet"), plan.objectPreviewArtKeys());
            assertEquals(Set.of("session"), plan.serviceBundles().keySet());
            assertEquals(1, plan.explicitPatches().size());
            assertEquals(plan.baseGameId(), plan.explicitPatches().getFirst().baseGameId());
            assertEquals("owner:lab-" + plan.baseGameId(), plan.explicitPatches().getFirst().id());
            assertEquals(Set.of("owner:gravity"), plan.mutators().keySet());
        }
    }

    @Test
    void sharedCodeCatalogueRejectsForeignPatchTargetsAndPoisonsTheTransaction() {
        var foreign = context("owner", "any");
        foreign.registerMutator(definition("gravity"));
        var failure = assertThrows(ModRegistrationException.class,
                () -> foreign.registerGamePatch(patch("custom")));
        assertSame(failure, assertThrows(ModRegistrationException.class, foreign::freeze));
    }

    @Test
    void compatibilityPlanDefaultsToNoMutators() {
        ModRegistrationPlan old = new ModRegistrationPlan("owner", "s2", Map.of(), Map.of(),
                Map.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(), null, Map.of(),
                Map.of(), Map.of(), Map.of(), null, null);
        assertTrue(old.mutators().isEmpty());
        assertFalse(old.hasContent());
    }

    @ParameterizedTest @ValueSource(strings={"object", "art", "service"})
    void catalogueOnlyCannotPublishSharedRuntimeContent(String contribution) {
        var catalogue = context("owner", "any");
        catalogue.registerMutator(definition("gravity"));
        var factories = new AtomicInteger();
        switch (contribution) {
            case "object" -> catalogue.registerObject("object", (spawn, registry) -> {
                factories.incrementAndGet(); return null;
            });
            case "art" -> catalogue.registerObjectArt("sheet", new BakedSheetRef("art/sheet.bin"));
            case "service" -> catalogue.registerServiceBundle("session", () -> {
                factories.incrementAndGet(); return GameServiceBundle.builder().build();
            });
            default -> throw new AssertionError(contribution);
        }
        var failure = assertThrows(ModRegistrationException.class, catalogue::freeze);
        assertSame(failure, assertThrows(ModRegistrationException.class, catalogue::freeze));
        assertSame(failure, assertThrows(ModRegistrationException.class,
                () -> catalogue.registerGamePatch(patch("s1"))));
        assertEquals(0, factories.get(), "Rejected registration must never invoke creator factories");
    }

    @ParameterizedTest @ValueSource(strings={"catalogue", "scene", "title", "patch"})
    void decodedPatchesNeedASingleGameEvenWithAValidCodeRole(String role) {
        var placements = context("owner", "any");
        placements.registerMutator(definition("gravity"));
        switch (role) {
            case "scene" -> placements.registerStartupScene(() -> { fail("Scene must stay deferred"); return null; });
            case "title" -> placements.registerTitleEntry("Code", () -> { fail("Title must stay deferred"); return null; });
            case "patch" -> placements.registerGamePatch(patch("s1"));
            case "catalogue" -> { }
            default -> throw new AssertionError(role);
        }
        placements.decodedLevelPatch("placements", LevelPatch.empty());
        var failure = assertThrows(ModRegistrationException.class, placements::freeze);
        assertSame(failure, assertThrows(ModRegistrationException.class, placements::freeze));
    }

    public interface ProbeService { String callbackOwner(); int application(); }

    @ParameterizedTest @CsvSource({"s1,false", "s2,false", "s3k,false", "s1,true", "s2,true", "s3k,true"})
    void nativeServiceWrapperRetainsCatalogueAndWorldProvider(String game, boolean shared) {
        var context = context("owner", shared ? "any" : game);
        context.registerMutator(definition("gravity"));
        var factories = new AtomicInteger();
        context.registerServiceBundle("session", () -> {
            int application = factories.incrementAndGet();
            return GameServiceBundle.builder().service(ProbeService.class, new ProbeService() {
                public String callbackOwner() { return OwnerCallbackScope.current(); }
                public int application() { return application; }
            }).build();
        });
        var patch = LevelPatch.empty();
        if (shared) {
            context.registerGamePatch(patch("s1"));
            context.registerGamePatch(patch("s2"));
            context.registerGamePatch(patch("s3k"));
            context.registerObject("object", (spawn, registry) -> null);
        } else context.decodedLevelPatch("placements", patch);
        var plans = context.freeze().stockScenePlans();
        var plan = plans.stream().filter(candidate -> game.equals(candidate.baseGameId())).findFirst().orElseThrow();
        assertEquals(0, factories.get(), "Freeze and expansion must not execute creator service factories");
        if (shared) {
            assertNull(plan.startupScene());
            assertEquals(List.of(game), plan.explicitPatches().stream().map(GamePatch::baseGameId).toList());
        } else assertSame(patch, plan.decodedLevelPatches().get("placements"));
        assertThrows(UnsupportedOperationException.class, () -> plan.serviceBundles().clear());
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
        GameModule nativeModule = switch (game) {
            case "s1" -> new com.openggf.game.sonic1.Sonic1GameModule();
            case "s2" -> new com.openggf.game.sonic2.Sonic2GameModule();
            case "s3k" -> new com.openggf.game.sonic3k.Sonic3kGameModule();
            default -> throw new AssertionError(game);
        };
        com.openggf.tests.TestEnvironment.configureGameModuleFixture(nativeModule);
        GameModule decorated = new ModBackedGamePatch(plan, boundary).apply(nativeModule, null);
        assertEquals(1, factories.get());
        var service = decorated.getGameService(ProbeService.class);
        assertEquals("owner", service.callbackOwner());
        assertEquals(1, service.application());
        var catalogue = decorated.getGameService(MutatorCatalog.class);
        assertSame(boundary, catalogue.faults());
        assertEquals(List.of("owner:gravity"), catalogue.definitions().stream().map(owned -> owned.key()).toList());
        var provider = decorated.getGameService(WorldSessionPolicyProvider.class);
        assertNotNull(provider, "The outer service decorator must retain the native mutator world provider");
        assertSame(provider, decorated.getGameService(WorldSessionPolicyProvider.class));
        GameModule reapplied = new ModBackedGamePatch(plan, boundary).apply(nativeModule, null);
        assertEquals(2, factories.get(), "Each application needs a fresh service bundle");
        var fresh = reapplied.getGameService(ProbeService.class);
        assertNotSame(service, fresh);
        assertEquals(2, fresh.application());
        assertEquals("owner", fresh.callbackOwner());
        var freshProvider = reapplied.getGameService(WorldSessionPolicyProvider.class);
        assertNotNull(freshProvider);
        assertSame(freshProvider, reapplied.getGameService(WorldSessionPolicyProvider.class));
        assertEquals(List.of("owner:gravity"), reapplied.getGameService(MutatorCatalog.class)
                .definitions().stream().map(owned -> owned.key()).toList());
    }

    private static ModContext context(String owner, String game) {
        return new ModContext(owner, game, ModAssetRoot.forTests(owner));
    }
    private static GamePatch patch(String game) {
        return new GamePatch() {
            @Override public String id() { return "lab-" + game; }
            @Override public String displayName() { return "Lab " + game; }
            @Override public String baseGameId() { return game; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            @Override public List<String> providedMainCharacters() { return List.of(); }
            @Override public GameModule apply(GameModule base, PatchContext context) { return base; }
        };
    }
    private static MutatorDefinition definition(String id) {
        return new MutatorDefinition(id, "Gravity", "", MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(), Set.of(MutatorCapability.DRY_SONIC_GRAVITY),
                options -> List.of(new MutatorPolicy.DrySonicGravity(100)));
    }
}
