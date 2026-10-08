package com.openggf.mods.code;

import com.openggf.io.ModAssetRoot;
import com.openggf.mods.mutators.MutatorCapability;
import com.openggf.mods.mutators.MutatorDefinition;
import com.openggf.mods.mutators.MutatorPolicy;
import com.openggf.mods.mutators.MutatorScope;
import com.openggf.game.GameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestMutatorRegistration {
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
    void sharedCatalogueRoutesEachDecoratorOnlyToItsNativeGame() {
        var shared = context("owner", "any");
        shared.registerMutator(definition("gravity"));
        shared.registerGamePatch(patch("s3k"));
        shared.registerGamePatch(patch("s1"));
        shared.registerGamePatch(patch("s2"));
        for (var plan : shared.freeze().stockScenePlans()) {
            assertEquals(1, plan.explicitPatches().size());
            assertEquals(plan.baseGameId(), plan.explicitPatches().getFirst().baseGameId());
            assertEquals("owner:lab-" + plan.baseGameId(), plan.explicitPatches().getFirst().id());
            assertEquals(Set.of("owner:gravity"), plan.mutators().keySet());
        }
    }

    @Test
    void sharedDecoratorsRequireTypedContentAndCannotSmuggleGameSpecificRegistrations() {
        var noCatalogue = context("owner", "any");
        noCatalogue.registerGamePatch(patch("s2"));
        var failure = assertThrows(ModRegistrationException.class, noCatalogue::freeze);
        assertSame(failure, assertThrows(ModRegistrationException.class, noCatalogue::freeze));

        var foreign = context("owner", "any");
        foreign.registerMutator(definition("gravity"));
        failure = assertThrows(ModRegistrationException.class, () -> foreign.registerGamePatch(patch("custom")));
        assertSame(failure, assertThrows(ModRegistrationException.class, foreign::freeze));

        var gameSpecific = context("owner", "any");
        gameSpecific.registerMutator(definition("gravity"));
        gameSpecific.registerGamePatch(patch("s1"));
        gameSpecific.registerObject("object", (spawn, registry) -> null);
        failure = assertThrows(ModRegistrationException.class, gameSpecific::freeze);
        assertSame(failure, assertThrows(ModRegistrationException.class, gameSpecific::freeze));
    }

    @Test
    void compatibilityPlanDefaultsToNoMutators() {
        ModRegistrationPlan old = new ModRegistrationPlan("owner", "s2", Map.of(), Map.of(),
                Map.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(), null, Map.of(),
                Map.of(), Map.of(), Map.of(), null, null);
        assertTrue(old.mutators().isEmpty());
        assertFalse(old.hasContent());
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
