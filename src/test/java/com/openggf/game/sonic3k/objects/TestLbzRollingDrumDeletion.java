package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestLbzRollingDrumDeletion {
    @AfterEach void resetSession() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void nativeRidersUpdateBeforeDeletionAndKeepStandingUnlessDead(int deadNativeSlot) {
        TestEnvironment.resetAll();
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        var main = rider();
        var nativeP2 = rider();
        nativeP2.setCpuControlled(true);
        var extension = rider();
        var players = List.of(main, nativeP2, extension);
        var sidekicks = List.of(nativeP2, extension);
        var camera = GameServices.camera();
        camera.setFocusedSprite(main);
        camera.setX((short) 0x1700);
        camera.setY((short) 0x0500);
        var services = new TestObjectServices().withCamera(camera).withSidekicks(sidekicks);
        var manager = new ObjectManager(List.of(), null, 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        services.withDirectObjectManager(manager);
        manager.reset(0x1700);
        manager.addDynamicObject(new LbzRollingDrumInstance(new ObjectSpawn(
                0x1800, 0x0600, Sonic3kObjectIds.LBZ_ROLLING_DRUM, 0x40, 0, false, 0)));
        var objectAdapter = manager.rewindSnapshottable();
        var objectBefore = objectAdapter.capture();
        var playersBefore = players.stream().map(TestablePlayableSprite::captureRewindState).toList();

        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                objectAdapter.restore(objectBefore);
                for (int i = 0; i < players.size(); i++) players.get(i).restoreRewindState(playersBefore.get(i));
            }
            camera.setX((short) 0x1700);
            manager.update(0x1700, main, sidekicks, 0, false, true, false);
            for (var player : players) assertTrue(player.isOnObject(), "real capture before unload");
            if (deadNativeSlot >= 0) players.get(deadNativeSlot).setDead(true);

            // The drum is beyond the native coarse $280 unload window.
            // loc_2C3CA must still execute each rider before deleting its SST.
            camera.setX((short) 0x1400);
            manager.update(0x1400, main, sidekicks, 1, false, true, false);
            assertTrue(manager.getActiveObjects().stream()
                    .noneMatch(LbzRollingDrumInstance.class::isInstance), "drum must actually unload");
            for (int slot = 0; slot < 2; slot++) {
                var player = players.get(slot);
                if (slot == deadNativeSlot) {
                    assertFalse(player.isOnObject());
                    assertTrue(player.getAir());
                } else {
                    assertTrue(player.isOnObject(), "Delete_Current_Sprite does not release native rider");
                    assertFalse(player.getAir());
                    assertEquals(1, player.getFlipAngle() & 0xFF,
                            "loc_2C4BA publishes seed $81 + $80 before deletion");
                }
            }
            assertFalse(extension.isOnObject(), "non-native participant ownership must be cleaned up");
            assertTrue(extension.getAir());
        }
    }

    private static TestablePlayableSprite rider() {
        var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x1800);
        player.setCentreY((short) 0x05AD);
        return player;
    }
}
