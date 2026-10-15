package com.openggf.mods.objects;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic3k.difficulty.*;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.function.Function;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Facade inputs must keep native kind identity; generic mod ID0 cannot become a malformed boss. */
class TestCampaignNativeFactories {
    private record Case(String name, int id, Function<ObjectSpawn, AbstractObjectInstance> create) {
        @Override public String toString() { return name; }
    }
    private static Stream<Case> cases() {
        return Stream.of(
                new Case("bridge", 0x0F, s -> CollapsingBridgeFactory.create(s, CollapsingBridgeRecipe.BRITTLE)),
                new Case("turbo-spiker", 0x96, s -> HydrocityFactory.turboSpiker(s, HydrocityEnemyRecipe.ALERT)),
                new Case("hcz-mini", 0x99, s -> HydrocityFactory.miniboss(s, HydrocityBossRecipe.COUNTER_CADENCE)),
                new Case("hcz-end", 0x9A, s -> HydrocityFactory.endBoss(s, HydrocityBossRecipe.COUNTER_CADENCE)),
                new Case("mantis", 0x9D, s -> MarbleGardenFactory.mantis(s, MarbleGardenEnemyRecipe.LANDING_GUARD)),
                new Case("spiker", 0x9C, s -> MarbleGardenFactory.spiker(s, MarbleGardenEnemyRecipe.LANDING_GUARD)),
                new Case("mgz-mini", 0x9F, s -> MarbleGardenFactory.miniboss(s, MarbleGardenBossRecipe.COUNTER_CADENCE)));
    }
    private static ObjectSpawn spawn(int id) { return new ObjectSpawn(1000, 500, id, 0x12, 0, true, 500, 7); }
    @ParameterizedTest(name = "{0} rejects another native kind") @MethodSource("cases")
    void rejectsWrongNativeKindBeforeConstructingAnyNativeOwner(Case recipe) {
        var error = assertThrows(IllegalArgumentException.class, () -> recipe.create.apply(spawn(0)));
        assertTrue(error.getMessage().contains("native"), error::getMessage);
    }
    @ParameterizedTest(name = "{0} retains its original native placement") @MethodSource("cases")
    void correctKindRetainsExactOriginalSpawn(Case recipe) {
        var services = new TestObjectServices().withConfiguration(SonicConfigurationService.createStandalone());
        ObjectSpawn spawn = spawn(recipe.id);
        var nativeOwner = ObjectConstructionContext.construct(services, () -> recipe.create.apply(spawn));
        // Native badniks publish a dynamic spawn copy at construction. Its
        // complete record, including original placement index/owner, must match.
        assertEquals(spawn, nativeOwner.getSpawn());
        assertEquals(recipe.id, nativeOwner.getSpawn().objectId());
        assertFalse(nativeOwner.isDestroyed());
    }
}
