package com.openggf.level.objects;

import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import org.junit.jupiter.api.Test;
import static com.openggf.level.objects.TerrainSurface.Layer.*;
import static com.openggf.level.objects.TerrainSurface.Path.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestTerrainSurface {
    private ObjectServices services(LevelManager level) {
        return new StubObjectServices(){@Override public LevelManager levelManager(){return level;}};
    }
    private SolidTile tile(int height,byte angle) {
        byte[] heights=new byte[16];java.util.Arrays.fill(heights,(byte)height);
        return new SolidTile(7,heights,heights.clone(),angle);
    }
    @Test void explicitLayerAndPathUseTheirOwnSurface() {
        LevelManager level=mock(LevelManager.class);ChunkDesc fg=new ChunkDesc(7|0x1000),bg=new ChunkDesc(8|0x1000);
        when(level.getChunkDescAt(eq((byte)0),anyInt(),anyInt())).thenReturn(fg);
        when(level.getChunkDescAt(eq((byte)1),anyInt(),anyInt())).thenReturn(bg);
        when(level.getSolidTileForChunkDesc(fg,12,false)).thenReturn(tile(4,(byte)0));
        when(level.getSolidTileForChunkDesc(bg,12,false)).thenReturn(tile(8,(byte)0));
        when(level.getSolidTileForChunkDesc(bg,12,true)).thenReturn(tile(12,(byte)0));
        ObjectServices svc=services(level);
        // ROM sub_F30C: d1 = $F - (height + yInTile), not $10 - height.
        assertEquals(27,svc.floorSurface(2,16,FOREGROUND,PRIMARY).surfaceY());
        assertEquals(23,svc.floorSurface(2,16,BACKGROUND,PRIMARY).surfaceY());
        assertEquals(19,svc.floorSurface(2,16,BACKGROUND,SECONDARY).surfaceY());
        assertEquals(3,svc.floorSurface(2,16,BACKGROUND,SECONDARY).distance());
    }
    @Test void slopedHeightAndHorizontalFlipPreserveNativeFloorAndTransformedAngle() {
        LevelManager level=mock(LevelManager.class);ChunkDesc normal=new ChunkDesc(7|0x1000),flip=new ChunkDesc(7|0x1400);
        byte[] heights=new byte[16];for(int x=0;x<16;x++)heights[x]=(byte)(x+1);
        SolidTile slope=new SolidTile(7,heights,heights.clone(),(byte)0x20);
        when(level.getChunkDescAt(eq((byte)0),eq(2),anyInt())).thenReturn(normal);
        when(level.getSolidTileForChunkDesc(any(),eq(12),eq(false))).thenReturn(slope);
        assertEquals(28,services(level).floorSurface(2,16,FOREGROUND,PRIMARY).surfaceY());
        when(level.getChunkDescAt(eq((byte)0),eq(2),anyInt())).thenReturn(flip);
        TerrainSurface found=services(level).floorSurface(2,16,FOREGROUND,PRIMARY);
        assertEquals(17,found.surfaceY());assertEquals(slope.getAngle(true,false)&255,found.angle());
    }
    @Test void firstBoundedProbeRetainsTheConsumersSearchOriginAndSignedReferenceDistance() {
        LevelManager level=mock(LevelManager.class);ChunkDesc desc=new ChunkDesc(7|0x1000);
        when(level.getChunkDescAt(eq((byte)0),eq(2),eq(16))).thenReturn(desc);
        when(level.getSolidTileForChunkDesc(desc,12,false)).thenReturn(tile(8,(byte)0));
        TerrainSurface found=services(level).findFloorSurface(2,40,24,40,FOREGROUND,PRIMARY);
        assertTrue(found.foundSurface());assertEquals(23,found.surfaceY());assertEquals(-17,found.distance());
    }
    @Test void emptyMaximumRangeAndRejectedOverflowHaveFiniteWork() {
        LevelManager level=mock(LevelManager.class);ObjectServices svc=services(level);
        assertFalse(svc.findFloorSurface(2,1000,256,512,FOREGROUND,PRIMARY).foundSurface());
        long probes=mockingDetails(level).getInvocations().stream().filter(i->i.getMethod().getName().equals("getChunkDescAt")).count();
        assertTrue(probes<=100,"native current/adjacent samples stay bounded");clearInvocations(level);
        assertThrows(IllegalArgumentException.class,()->svc.findFloorSurface(2,1000,257,512,FOREGROUND,PRIMARY));
        assertThrows(IllegalArgumentException.class,()->svc.findFloorSurface(2,Integer.MAX_VALUE,0,16,FOREGROUND,PRIMARY));
        assertThrows(IllegalArgumentException.class,()->svc.floorSurface(2,Integer.MIN_VALUE,FOREGROUND,PRIMARY));
        verifyNoInteractions(level);
    }
    @Test void unavailableLiveLevelHasNoSurface() {
        assertFalse(services(null).floorSurface(2,16,FOREGROUND,PRIMARY).foundSurface());
        assertFalse(services(null).findFloorSurface(2,16,24,40,FOREGROUND,PRIMARY).foundSurface());
    }
}
