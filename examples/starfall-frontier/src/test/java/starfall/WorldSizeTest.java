package starfall;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldSizeTest {
    @Test void allSelectableLengthsGenerateScaledRegionsShrinesAndStableSavedGeography() {
        for(WorldSize size:WorldSize.values()) {
            World w=new World(73,size);assertEquals(size.width,w.width);assertEquals(384,w.height);
            int[] borders={64,96,128,160,192,224};
            for(int border:borders) {
                int tx=w.geographyTile(border);
                assertEquals(Biome.surface(border-1),Biome.at(w,tx-1,w.surface(tx-1)));
                assertEquals(Biome.surface(border),Biome.at(w,tx,w.surface(tx)));
            }
            for(int i=0;i<3;i++)assertEquals(World.SHRINE,w.tile(w.shrineX[i],w.shrineY[i]-1));
            assertTrue(w.shrineX[1]-w.shrineX[0]>=size.width/4);
            assertEquals(World.COPPER,w.tile(44,w.surface(40)+8));
            assertEquals(World.IRON,w.tile(55,w.surface(40)+16));
            assertFalse(w.blocked(w.x,w.y,4,10));
            w.x=(size.width-50)*World.T+6;w.y=(w.surface(size.width-50)-1)*World.T;
            w.set(size.width-48,310,World.PLANK);w.seen[310*w.width+size.width-48]=1;
            assertEquals(Biome.LAUNCH_BASE,w.region());
            World loaded=SaveCodec.decode(SaveCodec.encode(w)).orElseThrow();
            assertEquals(size.width,loaded.width);assertEquals(w.height,loaded.height);
            assertEquals(w.x,loaded.x);assertArrayEquals(w.tiles,loaded.tiles);assertArrayEquals(w.seen,loaded.seen);
            assertEquals(w.musicId(),loaded.musicId());assertArrayEquals(w.shrineX,loaded.shrineX);
            int center=w.geographyTile(140);
            assertEquals(Biome.HIDDEN_PALACE,Biome.at(loaded,center,loaded.surface(center)+192));
            assertEquals(Biome.SKY_SANCTUARY,Biome.at(loaded,center,loaded.surface(center)-76));
            assertTrue(loaded.recall());assertEquals(Biome.ANGEL_ISLAND,loaded.region());
        }
    }
    @Test void unsupportedLengthsAndDepthsAreRejectedBeforeAllocation() {
        for(int width:new int[]{0,-1,256,12288,Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->new World(1,false,width,World.H));
        assertThrows(IllegalArgumentException.class,()->new World(1,false,4096,96));
        assertEquals(8192,new World(1,false).width,"existing default remains Medium");
    }
}
