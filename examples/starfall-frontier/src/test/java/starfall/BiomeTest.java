package starfall;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BiomeTest {
    @Test void geographyCoversAllBiomesAndLavaReefNeverAppearsAtTheSurface() {
        World w=new World(73,true,256,96);boolean[] found=new boolean[Biome.values().length];
        for(int tx=1;tx<w.width-1;tx++) {
            assertEquals(Biome.surface(tx),Biome.at(w,tx,w.surface(tx)-1));
            for(int ty=0;ty<w.height-2;ty++) {
                Biome biome=Biome.at(w,tx,ty);found[biome.ordinal()]=true;
                if(biome==Biome.LAVA_REEF)assertTrue(ty-w.surface(tx)>=30);
                if(biome==Biome.HIDDEN_PALACE)assertTrue(ty-w.surface(tx)>=43);
            }
        }
        for(Biome biome:Biome.values())assertTrue(found[biome.ordinal()],biome.label);
        assertEquals(Biome.MARBLE_GARDEN,Biome.at(w,64,w.surface(64)));
        assertEquals(Biome.ANGEL_ISLAND,Biome.at(w,63,w.surface(63)));
        assertEquals(Biome.HYDROCITY,Biome.at(w,63,w.surface(63)+8));
        assertEquals(Biome.LAVA_REEF,Biome.at(w,63,w.surface(63)+30));
        assertEquals(Biome.SANDOPOLIS,Biome.at(w,208,w.surface(208)+8));
        assertEquals(Biome.SKY_SANCTUARY,Biome.at(w,116,w.surface(116)-18));
    }
    @Test void musicFollowsHorizontalAndVerticalBordersAndBossesReleaseItAfterDefeat() {
        World w=new World(73,true,256,96);
        int[] tx={40,80,112,144,176,208,240};int[] music={1,5,15,7,11,17,13};
        for(int i=0;i<tx.length;i++){w.x=(tx[i]+.5)*World.T;w.y=(w.surface(tx[i])-1)*World.T;assertEquals(music[i],w.musicId());}
        w.x=68*World.T;w.y=(w.surface(68)+8)*World.T;assertEquals(3,w.musicId());
        w.y=(w.surface(68)+20)*World.T;assertEquals(4,w.musicId());
        w.y=(w.surface(68)+30)*World.T;assertEquals(0x13,w.musicId());
        w.y=(w.surface(68)+45)*World.T;assertEquals(0x14,w.musicId());
        var boss=new World.Enemy(w.x+30,w.y,3);boss.shrine=0;w.enemies.add(boss);assertEquals(0x19,w.musicId());
        boss.hp=0;assertEquals(0x14,w.musicId(),"defeated enemies cannot hold boss music");
        w.enemies.clear();assertEquals(0x14,w.musicId());
        w.won=true;assertEquals(0x14,w.musicId(),"beacon completion must keep biome music");
        assertTrue(w.recall());assertEquals(1,w.musicId());
    }
    @Test void savesPreserveEditsAndResolveTheSameUndergroundBiomeAndMusic() {
        World w=new World(73,true,256,96);w.x=140*World.T;w.y=(w.surface(140)+48)*World.T;
        w.set(176,w.surface(176),World.PLANK);w.set(215,70,World.AIR);
        World restored=SaveCodec.decode(SaveCodec.encode(w)).orElseThrow();
        assertArrayEquals(w.tiles,restored.tiles);assertArrayEquals(w.walls,restored.walls);
        assertEquals(Biome.HIDDEN_PALACE,restored.region());assertEquals(w.musicId(),restored.musicId());
        for(int n=0;n<120;n++){var input=new World.Input(n%2==0?1:-1,false,false);w.step(input);restored.step(input);}
        assertEquals(SaveCodec.encode(w),SaveCodec.encode(restored));
    }
    @Test void everyEnemyFacesTheExplorerThroughRecoilAndZeroSpeed() {
        World w=new World(73,true,256,96);w.x=400;
        for(int kind=0;kind<4;kind++)for(double speed:new double[]{-2,0,2}) {
            var enemy=new World.Enemy(430,200,kind);enemy.vx=speed;
            assertFalse(w.enemyFlip(enemy),"native left-facing pose needs no flip");
            enemy.x=370;assertTrue(w.enemyFlip(enemy),"native pose flips to face right");
        }
    }
    @Test void blockArchivesRejectTruncatedAndInvalidMatchesAndDecodeLiterals() {
        assertThrows(IllegalArgumentException.class,()->new BiomeArt.BlockArchive(new byte[]{0}).decode());
        assertThrows(IllegalArgumentException.class,()->new BiomeArt.BlockArchive(new byte[]{0,0,0}).decode());
        // Descriptor 1: literal A then long-copy end marker.
        assertArrayEquals(new byte[]{65},new BiomeArt.BlockArchive(new byte[]{5,0,65,0,0,0}).decode());
    }
}
