package com.openggf.physics;

import com.openggf.game.GameServices;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.sonic1.objects.Sonic1MovingBlockObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.level.*;
import com.openggf.level.objects.*;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Authored full-tile wall, real S1 Obj52 stand-triggered shuttle and real sensors.
 * Native oracle: MBlock_NextWhenStoodOn/MBlock_SlideFast and Sonic_WallSpeedAdjust.
 * The S3K worker supplied the geometry idea; this executes S1 rules and an S1
 * object, never its LBZ lift. No player embedding or zip velocity is seeded. */
class TestTasS1ContinuousZip {
    @AfterEach void cleanup() { GroundSensor.setLevelManager(null); SessionManager.clear(); }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void s1ShuttleWallEntryProducesRepeatedReverseCorrectionsAndExit(boolean wallPresent) {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_1);
        var mode = TestEnvironment.activeGameplayMode();
        var terrain = mode.getTerrainCollisionManager();
        var collision = new CollisionSystem(terrain);
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(mock(Level.class));
        when(level.getRomZoneId()).thenReturn(5);
        var desc = new ChunkDesc(1 | 0x3000);
        byte[] heights = new byte[16];
        Arrays.fill(heights, (byte) 16);
        var tile = new SolidTile(1, heights, heights, (byte) 0);
        when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt()))
                .thenAnswer(c -> desc.isSolidityBitSet(c.getArgument(1)) ? tile : null);
        when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt(), anyBoolean()))
                .thenAnswer(c -> desc.isSolidityBitSet(c.getArgument(1)) ? tile : null);
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean())).thenAnswer(c ->
                wallPresent && (int)c.getArgument(1)>=0x200 && (int)c.getArgument(1)<0x400
                        && (int)c.getArgument(2)>=0x140 && (int)c.getArgument(2)<0x240 ? desc : null);
        mode.attachLevelManagers(mode.getWaterSystem(), mode.getParallaxManager(), terrain,
                collision, mode.getSpriteManager(), level);
        GroundSensor.setLevelManager(level);
        var p = new Sonic("sonic", (short)0, (short)0);
        assertSame(GameRules.SONIC_1, p.getGameRules());
        p.setCentreX((short)0x430);p.setCentreY((short)0x1d0);p.setAir(true);
        var camera = GameServices.camera();
        camera.setX((short)0x300);camera.setY((short)0x140);
        camera.setMinX((short)0);camera.setMaxX((short)0x1000);camera.setMaxY((short)0x1000);
        var services = new TestObjectServices().withCamera(camera).withLevelManager(level)
                .withSolidExecutionRegistry(new DefaultSolidExecutionRegistry());
        var manager = new ObjectManager(List.of(), new Sonic1ObjectRegistry(), 0,
                null, null, null, camera, services);
        services.withDirectObjectManager(manager);
        collision.setObjectManager(manager);
        var shuttle = manager.createDynamicObject(() -> new Sonic1MovingBlockObjectInstance(
                new ObjectSpawn(0x430,0x200,0x52,0x39,1,false,0)));
        var movement = (PlayableSpriteMovement)p.getMovementManager();
        boolean acquiredRide = false;
        for (int frame=0;frame<100;frame++) {
            movement.handleMovement(false,false,false,false,false,false,false,false);
            manager.update(camera.getX(),p,List.of(),frame,false,true,true);
            acquiredRide |= p.isOnObject();
        }
        assertTrue(acquiredRide, "A real landing activates the native stand-triggered shuttle");
        assertTrue(p.isOnObject());assertFalse(p.getAir());
        assertEquals(0x3b0,shuttle.getX());assertEquals(0x3b0,p.getCentreX());
        int corrections=0;
        for (int frame=100;frame<112;frame++) {
            int before=p.getCentreX();boolean grounded=!p.getAir();
            movement.handleMovement(false,false,true,false,false,false,false,false);
            manager.update(camera.getX(),p,List.of(),frame,false,true,true);
            if(grounded && p.getCentreX()-before>=16)corrections++;
        }
        if(wallPresent) {
            assertTrue(corrections>=2, "Repeated grounded corrections move right while Left is held");
            assertTrue(p.getCentreX() - p.getXRadius() >= 0x400,
                    "The player left extent clears the authored wall ending at 0x3ff");
        } else {
            assertEquals(0,corrections);assertTrue(p.getCentreX()<0x3b0);
        }
    }
}
