package com.openggf.game.sonic2;

import com.openggf.game.GameServices;
import com.openggf.game.OscillationManager;
import com.openggf.game.sonic2.objects.MTZPlatformObjectInstance;
import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Authored level geometry: a real moving Obj6B pushes a valid standing player into terrain, then Left zips right. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestTasS2PlatformZip {
    @Test
    void platformEntryThenOppositeInputProducesTerrainEjectionWithEmptyWallControl() {
        int zipped = run(true);
        int ordinary = run(false);
        assertTrue(zipped >= 16, "native FindWall regression must eject right against Left input");
        assertTrue(ordinary <= 0, "without the wall Left must move left normally");
    }
    private static int run(boolean wall) {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        OscillationManager.reset();
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(mock(com.openggf.level.Level.class));
        byte[] full = new byte[16]; Arrays.fill(full, (byte) 16);
        var tile = new SolidTile(1, full, full.clone(), (byte) 0);
        var solid = new ChunkDesc(0xF001);
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean())).thenAnswer(call -> {
            int x = call.getArgument(1), y = call.getArgument(2);
            return y >= 256 || wall && x >= 288 && x < 416 && y >= 192 ? solid : null;
        });
        when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt())).thenReturn(tile);
        when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt(), anyBoolean())).thenReturn(tile);
        GroundSensor.setLevelManager(level);
        var player = new Sonic("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 272); player.setCentreY((short) 236);
        player.setAir(false); player.setAngle((byte) 0);
        assertTrue(player.getCentreX() + player.getXRadius() < 288,
                "fixture starts fully outside terrain; it must not seed the exploit");
        var camera = GameServices.camera();
        camera.setX((short) 160); camera.setY((short) 128);
        camera.setMaxX((short) 0x2000); camera.setMaxY((short) 0x1000);
        var services = new TestObjectServices().withCamera(camera).withGameModule(GameServices.module());
        var objects = new ObjectManager(List.of(), GameServices.module().createObjectRegistry(),
                0, null, null, null, camera, services);
        services.withDirectObjectManager(objects);
        // X-flipped $40 oscillator begins at base-64 and naturally travels right.
        var platform = new MTZPlatformObjectInstance(new ObjectSpawn(288, 236, 0x6B, 1, 1, false, 0), "MTZPlatform");
        objects.addDynamicObject(platform);
        var collision = new CollisionSystem(new TerrainCollisionManager());
        collision.setObjectManager(objects);
        TestEnvironment.activeGameplayMode().attachLevelManagers(new com.openggf.level.WaterSystem(),
                new com.openggf.level.ParallaxManager(), new TerrainCollisionManager(), collision,
                GameServices.sprites(), level);
        assertSame(level, player.currentLevelManager());
        var movement = new PlayableSpriteMovement(player, collision, GameServices.gameState());
        int ticks = 0;
        while (player.getCentreX() < 310 && ticks++ < 200) {
            movement.handleMovement(false, false, false, false, false, false, false, false);
            OscillationManager.update(ticks);
            objects.update(camera.getX(), player, List.of(), ticks, false, true, true);
        }
        assertTrue(ticks < 200, "moving platform must actually push the player into the wall column; x="
                + player.getCentreX() + " platform=" + platform.getX() + " air=" + player.getAir());
        assertFalse(player.getDead()); assertFalse(player.getAir());
        assertFalse(objects.isRidingObject(player), "entry is side ejection, not a riding-position seed");
        objects.removeDynamicObject(platform); // isolate the terrain response after the moving blocker has passed
        int before = player.getCentreX();
        movement.handleMovement(false, false, true, false, false, false, false, false);
        int after = player.getCentreX();
        int firstDisplacement = after - before;
        if (wall) {
            int zipPasses = 0;
            int corrections = firstDisplacement >= 16 ? 1 : 0;
            while (player.getCentreX() - player.getXRadius() < 416 && zipPasses++ < 12) {
                int prior = player.getCentreX();
                movement.handleMovement(false, false, true, false, false, false, false, false);
                assertFalse(player.getAir(), "zip remains on the grounded movement path");
                if (player.getCentreX() - prior >= 16) corrections++;
            }
            assertTrue(corrections >= 2, "continuous terrain must produce repeated opposite-input ejections");
            assertTrue(player.getCentreX() - player.getXRadius() >= 416,
                    "the entire collision box must exit the far wall face");
            int exit = player.getCentreX();
            movement.handleMovement(false, false, false, true, false, false, false, false);
            assertTrue(Math.abs(player.getCentreX() - exit) < 4, "ordinary control resumes after exit");
            verify(level, atLeastOnce()).getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean());
        }
        assertFalse(player.getDead());
        return firstDisplacement;
    }
}
