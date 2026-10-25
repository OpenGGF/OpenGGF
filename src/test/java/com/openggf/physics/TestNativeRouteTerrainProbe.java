package com.openggf.physics;

import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestNativeRouteTerrainProbe {
    @Test void nativeHeightMapsPathsAndFlipsDetermineFloorWithoutAnyGlobalOverride() {
        var level = mock(LevelManager.class);
        var normal = new ChunkDesc(7 | 0x1000);
        var flipped = new ChunkDesc(7 | 0x1400);
        byte[] heights = new byte[16]; for (int i = 0; i < 16; i++) heights[i] = (byte) (i + 1);
        var slope = new SolidTile(7, heights, heights.clone(), (byte) 0x20);
        var secondary = tile(12);
        when(level.getChunkDescAt(eq((byte) 0), anyInt(), anyInt())).thenReturn(normal);
        when(level.getSolidTileForChunkDesc(any(), eq(12), eq(false))).thenReturn(slope);
        when(level.getSolidTileForChunkDesc(any(), eq(12), eq(true))).thenReturn(secondary);
        var probe = new NativeRouteTerrainProbe(level, BackgroundPlaneCollisionProvider.FOREGROUND_ONLY);
        assertEquals(28, probe.floor(2, 24, 0).y());
        assertEquals(19, probe.floor(2, 24, 1).y());
        when(level.getChunkDescAt(eq((byte) 0), anyInt(), anyInt())).thenReturn(flipped);
        assertEquals(17, probe.floor(2, 24, 0).y());
        assertEquals(slope.getAngle(true, false) & 255, probe.floor(2, 24, 0).angle());
        verify(level, never()).getChunkDescAt(eq((byte) 1), anyInt(), anyInt());
    }

    @Test void wideFootprintDetectsAnInteriorWallAndCeilingUsesTheSelectedPath() {
        var level = mock(LevelManager.class);
        var solid = new ChunkDesc(7 | 0x3000); var full = tile(16);
        when(level.getChunkDescAt(eq((byte) 0), anyInt(), anyInt())).thenAnswer(call -> {
            int x = call.getArgument(1), y = call.getArgument(2);
            return x >= 32 && x < 48 && y >= 32 && y < 48 ? solid : null;
        });
        when(level.getSolidTileForChunkDesc(solid, 13, false)).thenReturn(full);
        var probe = new NativeRouteTerrainProbe(level, BackgroundPlaneCollisionProvider.FOREGROUND_ONLY);
        assertFalse(probe.clear(0, 34, 63, 42, 0), "Native adjacent-tile scans must cover the interior of a multipart body");
        assertTrue(probe.clear(0, 34, 31, 42, 0), "The last air pixel beside a wall remains clear");
        when(level.getChunkDescAt(eq((byte) 0), anyInt(), anyInt())).thenAnswer(call -> {
            int y = call.getArgument(2); return y >= 16 && y < 32 ? solid : null;
        });
        when(level.getSolidTileForChunkDesc(solid, 13, true)).thenReturn(null);
        assertFalse(probe.clear(1, 20, 3, 25, 0));
        assertTrue(probe.clear(1, 20, 3, 25, 1), "Secondary collision has no ceiling shape in this fixture");
        assertThrows(IllegalArgumentException.class, () -> probe.clear(0, 0, 300, 5, 0));
    }

    @Test void activeDualPlaneCollisionIsAnExplicitUnsupportedStaticGeometryState() {
        var level = mock(LevelManager.class);
        var provider = mock(BackgroundPlaneCollisionProvider.class);
        when(provider.state(level)).thenReturn(new BackgroundPlaneCollisionProvider.State(true, 31, 17));
        assertTrue(new NativeRouteTerrainProbe(level, provider).dynamicCollision());
        verifyNoInteractions(level);
    }
    @Test void boundedProbeChargesInsideClearanceAndFloorLoops() {
        var level = mock(LevelManager.class);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        Runnable stop = () -> { if (calls.incrementAndGet() == 3) throw new IllegalStateException("cancelled inside scan"); };
        var probe = new NativeRouteTerrainProbe(level,BackgroundPlaneCollisionProvider.FOREGROUND_ONLY,stop);
        assertThrows(IllegalStateException.class,() -> probe.clear(0,0,200,200,0));
        assertEquals(3,calls.get());
        calls.set(0);
        assertThrows(IllegalStateException.class,() -> probe.floor(100,100,0));
        assertEquals(3,calls.get());
    }
    private static SolidTile tile(int height) {
        byte[] values = new byte[16]; Arrays.fill(values, (byte) height);
        return new SolidTile(7, values, values.clone(), (byte) 0);
    }
}
