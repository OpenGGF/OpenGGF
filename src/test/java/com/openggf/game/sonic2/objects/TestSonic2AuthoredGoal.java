package com.openggf.game.sonic2.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.level.objects.HostObjectKeyRegistry;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic2AuthoredGoal {
    @Test void explicitHostGoalActivatesInSecondAndLaterSlotsAndRetainsItsKeyAcrossRewind() {
        for (int act : new int[] {1, 2, 5}) {
            var player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x300);
            var spawn = new ObjectSpawn(player.getCentreX(), 0x300, 0, 0, 0, true, 0x8300, 12,
                    "openggf", SignpostObjectInstance.AUTHORED_GOAL_KEY);
            var registry = new Sonic2ObjectRegistry();
            var object = (SignpostObjectInstance) registry.create(spawn);
            var gameState = new GameStateManager();
            var config = configuration();
            var services = new StubObjectServices() {
                @Override public int currentAct() { return act; }
                @Override public GameStateManager gameState() { return gameState; }
                @Override public SonicConfigurationService configuration() { return config; }
                @Override public com.openggf.game.session.WorldSession worldSession() {
                    throw new AssertionError("An authored campaign goal must not consult the native competition world");
                }
            };
            object.setServices(services);
            var before = object.captureRewindState();
            object.update(0, player);
            object.updatePostPlayer(0, player);
            assertFalse(object.isDestroyed()); assertTrue(gameState.isActCompletionSignalActive());
            assertFalse(gameState.isEndOfLevelActive());
            assertEquals(act, services.currentAct());
            assertTrue(registry.browsableObjectKeys().contains(spawn.objectKey()));
            assertEquals(ObjectArtKeys.SIGNPOST, registry.editorPreviewArtKey(spawn.objectKey()).orElseThrow());
            assertTrue(HostObjectKeyRegistry.trusted(registry).ownsHostObject(spawn, object));
            var recreated = object.recreateForRewind(new RewindRecreateContext(spawn, before, services));
            recreated.setServices(services); recreated.restoreRewindState(before);
            assertEquals(spawn, recreated.getSpawn());
            gameState.setActCompletionSignalActive(false); recreated.update(1, player);
            recreated.updatePostPlayer(1, player);
            assertFalse(recreated.isDestroyed()); assertTrue(gameState.isActCompletionSignalActive());
        }
    }

    @Test void numericStockGateAndMetropolisSecondActExceptionRemainUnchanged() {
        var config = configuration();
        for (int act : new int[] {1, 2, 5}) {
            var object = new SignpostObjectInstance(new ObjectSpawn(100, 100, 0x0d, 0, 0, true, 0x8064, 1), "Signpost");
            object.setServices(new StubObjectServices() {
                @Override public int currentAct() { return act; }
                @Override public SonicConfigurationService configuration() { return config; }
            });
            object.update(0, null); assertTrue(object.isDestroyed());
        }
        var metropolis = new SignpostObjectInstance(new ObjectSpawn(100, 100, 0x0d, 0, 0, true, 0x8064, 1), "Signpost");
        metropolis.setServices(new StubObjectServices() {
            @Override public int currentAct() { return 1; }
            @Override public int currentZone() { return Sonic2ZoneConstants.ROM_ZONE_MTZ; }
            @Override public SonicConfigurationService configuration() { return config; }
        });
        metropolis.update(0, null); assertFalse(metropolis.isDestroyed());
    }

    private static SonicConfigurationService configuration() {
        var config = mock(SonicConfigurationService.class);
        when(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE)).thenReturn("sonic");
        return config;
    }
}
