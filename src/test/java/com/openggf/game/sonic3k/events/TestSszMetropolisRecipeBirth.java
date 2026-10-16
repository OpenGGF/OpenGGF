package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.difficulty.SkySanctuaryMetropolisRecipe;
import com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectCallbackAbortException;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native optional intake only; actual ModContext fault/quarantine integration is root-owned. */
class TestSszMetropolisRecipeBirth {
    @Test void acceptedBirthSamplesBeforeFlagsAndOnlyOnce() throws Exception {
        World world = new World();
        world.available = SkySanctuaryMetropolisRecipe.FOURTH_PAIR;
        world.events.dynamicResize(world.state);
        SszMtzBossObjectInstance boss = world.boss();
        assertEquals(1, world.samples);
        assertEquals(SkySanctuaryMetropolisRecipe.FOURTH_PAIR, field(boss, "encounterRecipe"));
        assertEquals(true, field(boss, "encounterRecipeCommitted"));
        assertEquals(false, field(boss, "ordinaryUpdateStarted"));
        assertPublished(world);
        world.available = null; // Already unavailable after birth: the native lifetime is committed.
        world.events.dynamicResize(world.state);
        assertEquals(1, world.samples);
        assertEquals(1, world.manager.getActiveObjects().size());
        assertThrows(IllegalStateException.class,
                () -> boss.commitEncounterRecipe(SkySanctuaryMetropolisRecipe.STOCK));
    }

    @Test void firstProviderAbortPropagatesBeforeCommitmentAndFightingPublication() throws Exception {
        World world = new World();
        world.failure = new DeclinedRecipe();
        assertSame(world.failure, assertThrows(ObjectCallbackAbortException.class,
                () -> world.events.dynamicResize(world.state)));
        assertEquals(SkySanctuaryMetropolisRecipe.STOCK, field(world.boss(), "encounterRecipe"));
        assertEquals(false, field(world.boss(), "encounterRecipeCommitted"));
        assertEquals(false, field(world.boss(), "ordinaryUpdateStarted"));
        assertEquals(0x00FF, world.state.eventsBgWord(2), "native lock precedes optional lookup");
        assertEquals(0, world.state.eventsBgByte(5), "new fault cannot publish fighting ownership");
        assertEquals(1, world.samples);
        assertEquals(1, world.manager.getActiveObjects().size());
        // The accepted partial root belongs to Engine abort teardown; a native
        // event must not swallow the fault, retry, or invent local cleanup.
    }

    @Test void absentOrAlreadyUnavailableBeforeBirthUsesStock() throws Exception {
        World world = new World(); // A fresh lookup returning no value models both native absence and withdrawal.
        world.events.dynamicResize(world.state);
        assertEquals(SkySanctuaryMetropolisRecipe.STOCK, field(world.boss(), "encounterRecipe"));
        assertPublished(world);
        world.available = SkySanctuaryMetropolisRecipe.FOURTH_PAIR;
        world.events.dynamicResize(world.state);
        assertEquals(1, world.samples);
        assertEquals(SkySanctuaryMetropolisRecipe.STOCK, field(world.boss(), "encounterRecipe"));
    }

    @Test void failedActualSlotAllocationNeverSamplesAndKeepsOriginalRetryGate() {
        World world = new World();
        world.manager.reserveAllButNFreeSlots(0);
        world.events.dynamicResize(world.state);
        assertEquals(0, world.samples);
        assertTrue(world.manager.getActiveObjects().isEmpty());
        assertEquals(0x00FF, world.state.eventsBgWord(2), "pre-existing native lock remains until fighting word publication");
        assertEquals(0, world.state.eventsBgByte(5));
        // Native failed allocation retries, unlike FBZ's one-attempt controller.
        world.manager.releaseDynamicSlot(4);
        world.events.dynamicResize(world.state);
        assertEquals(1, world.samples);
        assertPublished(world);
    }

    @Test void unrelatedRuntimeFailureIsNotSwallowed() {
        World world = new World();
        var failure = new IllegalArgumentException("native witness");
        world.failure = failure;
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> world.events.dynamicResize(world.state)));
        assertEquals(1, world.manager.getActiveObjects().size());
        assertEquals(0x00FF, world.state.eventsBgWord(2), "pre-existing native lock remains until fighting word publication");
    }

    @Test void destructivePreUpdateRecreationPreservesCommitmentWithoutProviderExecution() throws Exception {
        World world = new World();
        world.available = SkySanctuaryMetropolisRecipe.FOURTH_PAIR;
        world.events.dynamicResize(world.state);
        SszMtzBossObjectInstance original = world.boss();
        int originalSlot = original.getSlotIndex();
        var registry = new RewindRegistry();
        registry.register(world.manager.rewindSnapshottable());
        var before = registry.capture();
        world.failure = new DeclinedRecipe();
        world.manager.setRewindInPlaceRestoreEnabledForTest(false);
        world.manager.getActiveObjects().stream().toList().forEach(world.manager::removeDynamicObject);
        registry.restore(before);
        var restored = world.boss();
        assertNotSame(original, restored);
        assertEquals(originalSlot, restored.getSlotIndex());
        assertEquals(SkySanctuaryMetropolisRecipe.FOURTH_PAIR, field(restored, "encounterRecipe"));
        assertEquals(true, field(restored, "encounterRecipeCommitted"));
        assertEquals(false, field(restored, "ordinaryUpdateStarted"));
        assertThrows(IllegalStateException.class,
                () -> restored.commitEncounterRecipe(SkySanctuaryMetropolisRecipe.STOCK));
        assertEquals(1, world.samples);
    }

    private static void assertPublished(World world) {
        assertEquals(0x7F00, world.state.eventsBgWord(2));
        assertEquals(-1, world.state.eventsBgByte(5));
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class DeclinedRecipe extends ObjectCallbackAbortException {
        DeclinedRecipe() { super("controlled optional provider abort", null); }
    }

    private static final class World {
        final Camera camera = new Camera(SonicConfigurationService.createStandalone());
        final TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1700, (short) 0x420);
        final SpriteManager sprites = mock(SpriteManager.class);
        final LevelManager level = mock(LevelManager.class);
        final GameModule module = mock(GameModule.class);
        final SszZoneRuntimeState state = new SszZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE);
        final ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null, null, camera,
                new StubObjectServices());
        SkySanctuaryMetropolisRecipe available;
        RuntimeException failure;
        int samples;
        final Sonic3kSSZEvents events = new Sonic3kSSZEvents() {
            @Override protected Camera camera() { return camera; }
            @Override protected SpriteManager spriteManager() { return sprites; }
            @Override protected LevelManager levelManager() { return level; }
            @Override protected GameModule module() { return module; }
        };
        World() {
            manager.reset(0);
            camera.setX((short) 0x1660); camera.setY((short) 0x380);
            player.setCentreX((short) 0x1700); player.setCentreY((short) 0x420); player.setAir(false);
            state.setEventsBgByte(3, 0xFF);
            when(sprites.getMainPlayable()).thenReturn(player);
            when(level.getObjectManager()).thenReturn(manager);
            when(module.getGameService(SkySanctuaryMetropolisRecipe.class)).thenAnswer(invocation -> {
                samples++;
                assertEquals(1, manager.activeObjectsOfType(SszMtzBossObjectInstance.class).size());
                assertTrue(boss().getSlotIndex() >= 0, "lookup occurs after actual accepted allocation");
                assertEquals(0x00FF, state.eventsBgWord(2), "pre-existing lock; fighting word publication follows optional intake");
                assertEquals(0, state.eventsBgByte(5));
                if (failure != null) throw failure;
                return available;
            });
        }
        SszMtzBossObjectInstance boss() { return manager.activeObjectsOfType(SszMtzBossObjectInstance.class).getFirst(); }
    }
}
