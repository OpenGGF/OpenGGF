package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Sensor;
import com.openggf.physics.SensorResult;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 3325M/5373S: native AnglePos (s2.asm:43006-43019) trusts Status_OnObj after deletion. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestTasS2SupportUnload {
    @Test
    void deletedSupportRetainsGroundedTravelUntilARealJumpReleasesIt() {
        try (var reel = com.openggf.tests.reel.S2ComponentTestReel.open(0)) {
            TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
            var monitor = new MonitorObjectInstance(new ObjectSpawn(0x100, 0x100, 0x26, 3, 0, false, 0), "Monitor");
            var objects = new ObjectManager(List.of(), null, 0, null, null);
            objects.addDynamicObject(monitor);
            var player = new com.openggf.sprites.playable.Sonic("sonic", (short) 0, (short) 0);
            player.setCentreX((short) 0x100);
            player.setCentreY((short) (0x100 - 0x0F - player.getYRadius() + 5));
            player.setAir(true);
            player.setYSpeed((short) 0x100);
            objects.update(0, player, List.of(), 0, false, true, true);
            com.openggf.tests.reel.S2ComponentTestReel.frame(player, objects.getActiveObjects(), 0, 0, "support acquisition");
            assertTrue(objects.isRidingObject(player), "must acquire actual solid top contact: cx=" + player.getCentreX() + " cy=" + player.getCentreY() + " yr=" + player.getYRadius() + " anim=" + player.getAnimationId() + " solid=" + monitor.isSolidFor(player) + " active=" + objects.isActiveObjectInstance(monitor) + " on=" + player.isOnObject() + " air=" + player.getAir());
            assertTrue(player.isOnObject());
            assertFalse(player.getAir());
            var terrain = new TerrainCollisionManager() {
                @Override public SensorResult[] getSensorResult(Sensor[] ignored) {
                    return new SensorResult[] {null, null}; // a legitimate empty terrain column
                }
            };
            var collision = new CollisionSystem(terrain);
            collision.setObjectManager(objects);
            collision.resolveGroundAttachment(player, 14, () -> collision.hasObjectSupport(player));
            assertFalse(player.getAir(), "live-support control must bypass the empty terrain column");
            objects.removeDynamicObject(monitor);
            assertFalse(objects.isActiveObjectInstance(monitor), "support must actually be retired");
            collision.resolveGroundAttachment(player, 14, () -> collision.hasObjectSupport(player));
            assertFalse(player.getAir(), "the pending removal marker preserves the first player slot");
            objects.update(0, player, List.of(), 1, false, true, true);
            collision.resolveGroundAttachment(player, 14, () -> collision.hasObjectSupport(player));
            com.openggf.tests.reel.S2ComponentTestReel.frame(player, objects.getActiveObjects(), 0, 0, "post-removal frame1; empty terrain");
            assertFalse(player.getAir(), "native stale Status_OnObj must survive later slots and keep running in air");
            assertTrue(player.isOnObject());
            var mode = TestEnvironment.activeGameplayMode();
            var level = org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
            org.mockito.Mockito.when(level.getCurrentLevel()).thenReturn(org.mockito.Mockito.mock(com.openggf.level.Level.class));
            org.mockito.Mockito.when(level.getObjectManager()).thenReturn(objects);
            com.openggf.physics.GroundSensor.setLevelManager(level);
            mode.attachLevelManagers(mode.getWaterSystem(), mode.getParallaxManager(),
                    terrain, collision, mode.getSpriteManager(), level);
            com.openggf.game.GameServices.camera().setMaxX((short) 0x2000);
            com.openggf.game.GameServices.camera().setMaxY((short) 0x1000);
            var movement = new com.openggf.sprites.managers.PlayableSpriteMovement(player, collision,
                    com.openggf.game.GameServices.gameState());
            int startX = player.getCentreX(), startY = player.getCentreY();
            for (int frame = 2; frame < 22; frame++) {
                movement.handleMovement(false, false, false, true, false, false, false, false);
                objects.update(0, player, List.of(), frame, false, true, true);
                com.openggf.tests.reel.S2ComponentTestReel.frame(player, objects.getActiveObjects(), 8, 0, "stale support travel; empty terrain");
                assertFalse(player.getAir(), "stale support must allow sustained grounded travel above empty terrain");
                assertTrue(player.isOnObject());
                assertEquals(startY, player.getCentreY());
            }
            assertTrue(player.getCentreX() > startX);
            player.setJumpInputPressed(true);
            movement.handleMovement(false, false, false, true, true, false, false, false);
            com.openggf.tests.reel.S2ComponentTestReel.frame(player, objects.getActiveObjects(), 24, 1, "jump releases stale support");
            assertTrue(player.getAir(), "a real jump is the release control");
            // Sonic_Jump sets InAir and clears Push; the deleted owner cannot
            // clear OnObj, and native Sonic_Jump does not clear that bit itself.
            assertTrue(player.isOnObject());
            assertTrue(player.getYSpeed() < 0);
        }
    }
}
