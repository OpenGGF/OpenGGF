package com.openggf.mods.code;

import com.openggf.io.ModAssetRoot;
import com.openggf.mods.mutators.MutatorCapability;
import com.openggf.mods.mutators.MutatorDefinition;
import com.openggf.mods.mutators.MutatorPolicy;
import com.openggf.mods.mutators.MutatorScope;
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
    void sharedScenesAndStandaloneCannotStageGameplayMutators() {
        assertThrows(ModRegistrationException.class,
                () -> context("owner", "any").registerMutator(definition("gravity")));
        ModContext standalone = new ModContext("owner", null, ModAssetRoot.forTests("owner"), null, true);
        assertThrows(ModRegistrationException.class, () -> standalone.registerMutator(definition("gravity")));
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
    private static MutatorDefinition definition(String id) {
        return new MutatorDefinition(id, "Gravity", "", MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(), Set.of(MutatorCapability.DRY_SONIC_GRAVITY),
                options -> List.of(new MutatorPolicy.DrySonicGravity(100)));
    }
}
