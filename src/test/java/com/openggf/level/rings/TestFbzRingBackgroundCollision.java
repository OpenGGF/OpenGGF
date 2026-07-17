package com.openggf.level.rings;

import com.openggf.audio.AudioManager;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.physics.BackgroundPlaneCollisionProvider;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestFbzRingBackgroundCollision {
    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void providerSelectsBackgroundRingSurfaceOnEqualOrNearerSignedDistance() {
        BackgroundPlaneCollisionProvider provider = () ->
                new BackgroundPlaneCollisionProvider.State(true, 0x20, 0x10);

        assertEquals(-2, provider.selectNearerDistance(4, -2));
        assertEquals(-2, provider.selectNearerDistance(-2, 4));
        assertEquals(3, provider.selectNearerDistance(3, 3));
    }

    @Test
    void scatteredRingFloorAndReverseGravityProductionProbesReadTranslatedBackgroundLayer() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
        GameplayModeContext mode = TestEnvironment.activeGameplayMode();
        LevelManager level = mock(LevelManager.class);
        ChunkDesc desc = mock(ChunkDesc.class);
        when(desc.isSolidityBitSet(anyInt())).thenReturn(true);
        when(desc.getChunkIndex()).thenReturn(9);
        byte[] full = new byte[16];
        java.util.Arrays.fill(full, (byte) 16);
        SolidTile tile = new SolidTile(9, full, full, (byte) 0);
        when(level.getSolidTileForChunkDesc(desc, 0x0C, false)).thenReturn(tile);
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt())).thenAnswer(invocation -> {
            byte layer = invocation.getArgument(0);
            int x = invocation.getArgument(1);
            int y = invocation.getArgument(2);
            return layer == 1 && x >= 0xE0 && x < 0x100 && y >= 0x100 && y < 0x130 ? desc : null;
        });
        mode.attachLevelManagers(mode.getWaterSystem(), mode.getParallaxManager(),
                mode.getTerrainCollisionManager(), mode.getCollisionSystem(), mode.getSpriteManager(), level);
        mode.getZoneRuntimeRegistry().install(new ExplicitState());
        mode.attachBackgroundPlaneCollisionProvider(mode.createDefaultBackgroundPlaneCollisionProvider());

        BackgroundPlaneCollisionProvider provider = mode.getBackgroundPlaneCollisionProvider();
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(
                level, provider, false, 0x100, 0x100);
        TerrainCheckResult reverse = ObjectTerrainUtils.checkReverseGravityRingDist(
                level, provider, false, 0x100, 0x100);

        assertTrue(floor.foundSurface());
        assertTrue(reverse.foundSurface());
        assertEquals(9, floor.tileIndex());
        assertEquals(9, reverse.tileIndex());

        clearInvocations(level);
        RingManager manager = new RingManager(java.util.List.of(), null, level,
                mock(TouchResponseTable.class), mock(AudioManager.class));
        Field poolField = RingManager.class.getDeclaredField("lostRings");
        poolField.setAccessible(true);
        Object pool = poolField.get(manager);
        Method poolFloor = pool.getClass().getDeclaredMethod("ringCheckFloorDist", int.class, int.class);
        Method poolCeiling = pool.getClass().getDeclaredMethod("ringCheckCeilingDist", int.class, int.class);
        poolFloor.setAccessible(true);
        poolCeiling.setAccessible(true);
        assertEquals(0, poolFloor.invoke(pool, 0x100, 0x100));
        assertEquals(0, poolCeiling.invoke(pool, 0x100, 0x100));
        verify(level, atLeastOnce()).getChunkDescAt(eq((byte) 1), anyInt(), anyInt());

        clearInvocations(level);
        ProbeRing objectRing = new ProbeRing();
        objectRing.setServices(new StubObjectServices() {
            @Override public LevelManager levelManager() { return level; }
            @Override public BackgroundPlaneCollisionProvider backgroundPlaneCollisionProvider() {
                return provider;
            }
        });
        assertEquals(floor.distance(), objectRing.floor(0x100, 0x100));
        assertEquals(reverse.distance(), objectRing.ceiling(0x100, 0x100));
        verify(level, atLeastOnce()).getChunkDescAt(eq((byte) 1), anyInt(), anyInt());
    }

    private static final class ProbeRing extends LostRingObjectInstance {
        private ProbeRing() {
            super(new ObjectSpawn(0x100, 0x100, 0x37, 0, 0, false, 0));
        }
        private int floor(int x, int y) { return ringCheckFloorDist(x, y); }
        private int ceiling(int x, int y) { return ringCheckCeilingDist(x, y); }
    }

    private static final class ExplicitState implements ZoneRuntimeState {
        @Override public String gameId() { return "s3k"; }
        @Override public int zoneIndex() { return 4; }
        @Override public int actIndex() { return 1; }
        @Override public BackgroundPlaneCollisionProvider.State backgroundPlaneCollisionStateOrNull() {
            return new BackgroundPlaneCollisionProvider.State(true, 0x20, 0);
        }
    }
}
