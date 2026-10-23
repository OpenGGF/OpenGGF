package com.openggf.game.sonic1.objects;

import com.openggf.game.GameStateManager;
import com.openggf.level.objects.HostObjectKeyRegistry;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic1AuthoredGoal {
    @Test void explicitHostGoalActivatesInBossAndLaterSlotsAndRetainsItsKeyAcrossRewind() {
        for (int act : new int[] {2, 5}) {
            var player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x300);
            var spawn = new ObjectSpawn(player.getCentreX(), 0x300, 0, 0, 0, true, 0x300, 12,
                    "openggf", Sonic1SignpostObjectInstance.AUTHORED_GOAL_KEY);
            var registry = new Sonic1ObjectRegistry();
            var object = (Sonic1SignpostObjectInstance) registry.create(spawn);
            var gameState = new GameStateManager();
            var services = new StubObjectServices() {
                @Override public int currentAct() { return act; }
                @Override public GameStateManager gameState() { return gameState; }
            };
            object.setServices(services);
            var before = object.captureRewindState();
            object.update(0, player);
            assertFalse(object.isDestroyed());
            assertTrue(gameState.isActCompletionSignalActive());
            assertFalse(gameState.isEndOfLevelActive());
            assertEquals(act, services.currentAct(), "display/completion never rewrites logical campaign position");
            assertTrue(registry.browsableObjectKeys().contains(spawn.objectKey()));
            assertEquals(ObjectArtKeys.SIGNPOST, registry.editorPreviewArtKey(spawn.objectKey()).orElseThrow());
            assertTrue(HostObjectKeyRegistry.trusted(registry).ownsHostObject(spawn, object));
            var recreated = object.recreateForRewind(new RewindRecreateContext(spawn, before, services));
            recreated.setServices(services); recreated.restoreRewindState(before);
            assertEquals(spawn, recreated.getSpawn());
            gameState.setActCompletionSignalActive(false);
            recreated.update(1, player);
            assertFalse(recreated.isDestroyed()); assertTrue(gameState.isActCompletionSignalActive());
        }
    }

    @Test void numericStockSignpostsStillRetireInBossAndLaterActs() {
        for (int act : new int[] {2, 5}) {
            var object = new Sonic1SignpostObjectInstance(new ObjectSpawn(100, 100, 0x0d, 0, 0, true, 100, 1));
            object.setServices(new StubObjectServices() { @Override public int currentAct() { return act; } });
            object.update(0, null);
            assertTrue(object.isDestroyed());
        }
    }
}
