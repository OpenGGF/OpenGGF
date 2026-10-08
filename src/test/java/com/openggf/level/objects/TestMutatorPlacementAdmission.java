package com.openggf.level.objects;

import com.openggf.game.GameModule;
import com.openggf.game.mutators.*;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.tests.LevelMutatorTestWorld;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.mutators.MonitorContent.*;

class TestMutatorPlacementAdmission {
    @Test void nativeMonitorValuesUseSemanticContentsAcrossAllThreeGames() {
        check(new Sonic1ObjectRegistry(), new Sonic1GameModule(), 0x26,
                STATIC, EGGMAN, LIFE, SPEED_SHOES, BASIC_SHIELD, INVINCIBILITY, RINGS,
                S_MONITOR, GOGGLES, BROKEN_SHELL, STATIC, STATIC, STATIC, STATIC, STATIC, STATIC);
        check(new Sonic2ObjectRegistry(), new Sonic2GameModule(), 0x26,
                STATIC, LIFE, LIFE, EGGMAN, RINGS, SPEED_SHOES, BASIC_SHIELD, INVINCIBILITY,
                TELEPORT, RANDOM, BROKEN_SHELL, STATIC, STATIC, STATIC, STATIC, STATIC);
        check(new Sonic3kObjectRegistry(), new Sonic3kGameModule(), 0x01,
                EGGMAN, LIFE, EGGMAN, RINGS, SPEED_SHOES, FIRE_SHIELD, LIGHTNING_SHIELD,
                BUBBLE_SHIELD, INVINCIBILITY, SUPER, EGGMAN, EGGMAN, EGGMAN, EGGMAN, EGGMAN, EGGMAN);
    }

    private void check(ObjectRegistry registry, GameModule module, int id, MonitorContent... expected) {
        var services = new LevelMutatorTestWorld(module);
        var classifier = (MutatorPlacementClassifier) registry;
        for (int subtype = 0; subtype < expected.length; subtype++) {
            var spawn = new ObjectSpawn(160, 112, id, subtype | 0xA0, 0, true, 112, subtype);
            assertEquals(expected[subtype], classifier.monitorContent(spawn), module.getIdentifier() + " subtype " + subtype);
            services.policy(new LevelMutatorPolicy(Set.of(expected[subtype]), false, false, false, false));
            assertFalse(new ObjectPlacementAdmission(registry, services).allows(spawn));
            services.policy(LevelMutatorPolicy.STOCK);
            assertTrue(new ObjectPlacementAdmission(registry, services).allows(spawn));
        }
    }

    @Test void hiddenMonitorIsDeniedBeforeItsSignpostRevealAndCreatorPlacementIsNotNative() {
        var services = new LevelMutatorTestWorld(new Sonic3kGameModule());
        services.policy(new LevelMutatorPolicy(Set.of(LIGHTNING_SHIELD), false, false, false, false));
        var admission = new ObjectPlacementAdmission(new Sonic3kObjectRegistry(), services);
        assertFalse(admission.allows(new ObjectSpawn(160, 112, 0x80, 6, 0, true, 112, 3)));
        assertTrue(admission.allows(new ObjectSpawn(160, 112, 0x01, 6, 0, true, 112, 3,
                "creator", "creator:object")));
    }

    @Test void decoratedNativeRegistryStillFiltersNativePlacementsButPreservesCreatorOverride() {
        var services = new LevelMutatorTestWorld(new Sonic1GameModule());
        services.policy(new LevelMutatorPolicy(Set.of(LIFE), true, false, false, false));
        var keys = new com.openggf.mods.code.ModObjectKeyRegistry(List.of(
                new com.openggf.mods.code.ModObjectKeyRegistry.Registration("creator", "creator:monitor",
                        (spawn, registry) -> new AbstractObjectInstance(spawn, "CreatorMonitor") {
                            @Override public void update(int frame, com.openggf.game.PlayableEntity player) { }
                            @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
                        })));
        var decorated = new com.openggf.mods.code.ModDecoratedObjectRegistry(new Sonic1ObjectRegistry(), keys);
        var nativeMonitor = new ObjectSpawn(160,112,0x26,2,0,true,112,0);
        var nativeRing = new ObjectSpawn(160,112,0x25,0,0,true,112,1);
        var creatorMonitor = new ObjectSpawn(160,112,0x26,2,0,true,112,2,"creator","creator:monitor");
        var source = List.of(nativeMonitor, nativeRing, creatorMonitor);
        assertEquals(LIFE, decorated.monitorContent(nativeMonitor));
        assertTrue(decorated.isRingPlacement(nativeRing));
        assertNull(decorated.monitorContent(creatorMonitor));
        var manager = new ObjectManager(source,decorated,0,null,null,null,null,services);
        manager.reset(0);
        assertEquals(List.of(creatorMonitor), manager.getActiveObjects().stream().map(ObjectInstance::getSpawn).toList());
        assertEquals("CreatorMonitor", manager.getActiveObjects().iterator().next().getName());
        assertEquals(3, source.size());
        services.policy(LevelMutatorPolicy.STOCK);
        var enabled = new ObjectPlacementAdmission(decorated,services);
        assertTrue(enabled.allows(nativeMonitor));
        assertTrue(enabled.allows(nativeRing));
        assertTrue(enabled.allows(creatorMonitor));
    }

    @Test void denialDoesNotCollapseDuplicateSourceIdentitiesOrAllocateNativeSlots() {
        var services = new LevelMutatorTestWorld(new Sonic2GameModule());
        services.policy(new LevelMutatorPolicy(Set.of(LIFE), false, false, false, false));
        var denied = new ObjectSpawn(160, 112, 0x26, 1, 0, true, 112, 0);
        var first = new ObjectSpawn(160, 112, 0x26, 4, 0, true, 112, 1);
        var duplicate = new ObjectSpawn(160, 112, 0x26, 4, 0, true, 112, 2);
        var source = List.of(denied, first, duplicate);
        var manager = new ObjectManager(source, new Sonic2ObjectRegistry(), 0, null, null, null, null, services);
        manager.reset(0);
        assertEquals(3, source.size());
        var active = manager.getActiveObjects().stream().map(ObjectInstance::getSpawn).toList();
        assertEquals(2, active.size());
        assertTrue(active.contains(first));
        assertTrue(active.contains(duplicate));
        assertFalse(active.contains(denied));
        assertEquals(2, manager.getActiveObjects().stream().map(object -> ((AbstractObjectInstance) object).getSlotIndex()).distinct().count());
    }
}
