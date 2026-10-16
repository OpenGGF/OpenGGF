package com.openggf.mods.objects;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.difficulty.*;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;
import com.openggf.game.sonic3k.objects.CollapsingBridgeObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossInstance;
import com.openggf.game.sonic3k.runtime.HczZoneRuntimeState;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.lang.reflect.Field;
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
        ObjectSpawn spawn = spawn(recipe.id);
        var nativeOwner = assertDoesNotThrow(() -> recipe.create.apply(spawn),
                "a public native factory must not need an ambient construction context");
        // Native badniks publish a dynamic spawn copy at construction. Its
        // complete record, including original placement index/owner, must match.
        assertEquals(spawn, nativeOwner.getSpawn());
        assertEquals(recipe.id, nativeOwner.getSpawn().objectId());
        assertFalse(nativeOwner.isDestroyed());
    }

    private record BridgeCase(int zone, int subtype, int width, String mode, int timer, boolean brittle) { }
    private static Stream<BridgeCase> bridgeCases() {
        return Stream.of(
                new BridgeCase(2, 0x12, 0x30, "STANDARD", 16, true),
                new BridgeCase(2, 0x22, 0x40, "MGZ_STOMP", 40, false),
                new BridgeCase(1, 0x91, 0x50, "TRIGGER", 8, false));
    }

    @ParameterizedTest(name = "zone {0} configures before its first managed frame")
    @MethodSource("bridgeCases")
    void bridgeUsesInjectedZoneAndOnlyBrittlesOrdinarySupports(BridgeCase recipe) throws Exception {
        var bridge = (CollapsingBridgeObjectInstance) CollapsingBridgeFactory.create(
                new ObjectSpawn(1000, 500, 0x0F, recipe.subtype, 0, true, 500, 7),
                CollapsingBridgeRecipe.BRITTLE);
        bridge.setServices(zoneServices(recipe.zone));
        assertEquals(recipe.width, bridge.getSolidParams().halfWidth());
        assertEquals(recipe.mode, field(bridge, "mode").toString());
        assertEquals(recipe.timer, field(bridge, "collapseTimer"));
        assertEquals(recipe.brittle, field(bridge, "brittleSupport"));
    }

    @Test
    void bridgeRecreationRestoresConfigurationAndCountdownWithoutReinitializingOnReinjection() throws Exception {
        var services = zoneServices(2).withIsolatedObjectManager();
        var manager = services.objectManager();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var bridge = manager.createDynamicObject(() -> (CollapsingBridgeObjectInstance)
                CollapsingBridgeFactory.create(spawn(0x0F), CollapsingBridgeRecipe.BRITTLE));
        set(bridge, "state", 1);
        set(bridge, "collapseTimer", 7);
        var rewind = new RewindRegistry();
        rewind.register(manager.rewindSnapshottable());
        var snapshot = rewind.capture();
        for (int tick = 0; tick < 3; tick++) bridge.update(tick, null);
        assertEquals(4, field(bridge, "collapseTimer"));

        rewind.restore(snapshot);
        var restored = manager.getActiveObjects().stream()
                .filter(CollapsingBridgeObjectInstance.class::isInstance)
                .map(CollapsingBridgeObjectInstance.class::cast).findFirst().orElseThrow();
        assertNotSame(bridge, restored);
        assertEquals(7, field(restored, "collapseTimer"));
        assertEquals(true, field(restored, "brittleSupport"));
        restored.setServices(services);
        assertEquals(0x30, restored.getSolidParams().halfWidth());
        for (int tick = 0; tick < 3; tick++) restored.update(tick, null);
        assertEquals(4, field(restored, "collapseTimer"));
    }

    @Test
    void endBossResolvesNativeCharacterTargetsOnInjectionAndRetainsThemAcrossRecreation() throws Exception {
        var services = zoneServices(1).withIsolatedObjectManager();
        services.zoneRuntimeRegistry().install(new HczZoneRuntimeState(
                1, PlayerCharacter.KNUCKLES, new Sonic3kHCZEvents(() -> 0)));
        var manager = services.objectManager();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var boss = manager.createDynamicObject(() -> (HczEndBossInstance)
                HydrocityFactory.endBoss(spawn(0x9A), HydrocityBossRecipe.COUNTER_CADENCE));
        assertEquals(0x02B8, field(boss, "targetLockYTop"));
        assertEquals(0x02B8, field(boss, "targetLockYBottom"));
        assertEquals(0x4540, field(boss, "targetLockXLeft"));
        assertEquals(0x4590, field(boss, "targetLockXRight"));
        assertEquals(0, boss.getState().routine);
        assertEquals(1, manager.getActiveObjects().size(), "injection must not spawn native boss children");

        var rewind = new RewindRegistry();
        rewind.register(manager.rewindSnapshottable());
        var snapshot = rewind.capture();
        services.zoneRuntimeRegistry().install(new HczZoneRuntimeState(
                1, PlayerCharacter.SONIC_ALONE, new Sonic3kHCZEvents(() -> 0)));
        var sonicBoss = (HczEndBossInstance) HydrocityFactory.endBoss(
                spawn(0x9A), HydrocityBossRecipe.COUNTER_CADENCE);
        sonicBoss.setServices(services);
        assertEquals(0x0738, field(sonicBoss, "targetLockYTop"));
        assertEquals(0x0738, field(sonicBoss, "targetLockYBottom"));
        assertEquals(0x4000, field(sonicBoss, "targetLockXLeft"));
        assertEquals(0x4050, field(sonicBoss, "targetLockXRight"));
        boss.setServices(services);
        assertEquals(0x4540, field(boss, "targetLockXLeft"), "live reinjection is not a new encounter");
        rewind.restore(snapshot);
        var restored = manager.getActiveObjects().stream().filter(HczEndBossInstance.class::isInstance)
                .map(HczEndBossInstance.class::cast).findFirst().orElseThrow();
        assertNotSame(boss, restored);
        restored.setServices(services);
        assertEquals(0x4540, field(restored, "targetLockXLeft"), "captured fight targets win over recreation defaults");
        assertEquals(0x4590, field(restored, "targetLockXRight"));
        assertEquals(1, manager.getActiveObjects().size());
    }

    private static TestObjectServices zoneServices(int zone) {
        return new TestObjectServices() {
            @Override public int romZoneId() { return zone; }
        }.withConfiguration(SonicConfigurationService.createStandalone());
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}
