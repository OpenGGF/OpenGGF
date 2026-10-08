package starfall;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpansionTest {
    @Test void largeGeographySeparatesEveryRegionAndKeepsStarterProgressionReachable() {
        World w=new World(73);
        assertEquals(8192,w.width);assertEquals(384,w.height);
        assertEquals(128,(long)w.tiles.length/(256*96));
        assertTrue((w.width-40)*World.T/6.0/60>270,"even flat-out travel takes over four minutes");
        int[] centers={40,80,112,144,176,208,240};
        Biome[] regions={Biome.ANGEL_ISLAND,Biome.MARBLE_GARDEN,Biome.MUSHROOM_HILL,
                Biome.CARNIVAL_NIGHT,Biome.ICECAP,Biome.SANDOPOLIS,Biome.LAUNCH_BASE};
        for(int i=0;i<centers.length;i++) {
            int tx=w.geographyTile(centers[i]);assertEquals(regions[i],Biome.at(w,tx,w.surface(tx)));
            if(i>0)assertTrue(tx-w.geographyTile(centers[i-1])>=1024);
        }
        int west=w.geographyTile(68),mid=w.geographyTile(140),desert=w.geographyTile(208);
        assertEquals(Biome.HYDROCITY,Biome.at(w,west,w.surface(west)+32));
        assertEquals(Biome.HYDROCITY,Biome.at(w,west,w.surface(west)+119));
        assertEquals(Biome.LAVA_REEF,Biome.at(w,west,w.surface(west)+120));
        assertEquals(Biome.HIDDEN_PALACE,Biome.at(w,mid,w.surface(mid)+172));
        assertEquals(Biome.SANDOPOLIS,Biome.at(w,desert,w.surface(desert)+32));
        assertEquals(Biome.SKY_SANCTUARY,Biome.at(w,mid,w.surface(mid)-57));
        for(int i=0;i<3;i++)assertEquals(World.SHRINE,w.tile(w.shrineX[i],w.shrineY[i]-1));
        assertEquals(World.COPPER,w.tile(44,w.surface(40)+8));
        assertEquals(World.IRON,w.tile(55,w.surface(40)+16));
        assertEquals(World.AIR,w.tile(50,w.surface(40)+24));
        assertTrue(w.recall());assertEquals(Biome.ANGEL_ISLAND,w.region());
        assertFalse(w.blocked(w.x,w.y,4,10));
    }
    @Test void expandedSaveRestoresFarEasternConstructionDeepDiscoveryAndEveryEnemyKind() {
        World a=new World(73);a.x=7800*World.T;a.y=310*World.T;a.quest=7;
        a.set(7802,309,World.PLANK);a.walls[309*a.width+7802]=1;a.seen[310*a.width+7800]=1;
        for(EnemyType type:EnemyType.values()) {
            World.Enemy enemy=new World.Enemy(a.x+30+type.ordinal(),a.y-20,type.ordinal());
            if(type==EnemyType.SENTINEL)enemy.shrine=2;
            a.enemies.add(enemy);
        }
        World b=SaveCodec.decode(SaveCodec.encode(a)).orElseThrow();
        assertEquals(a.width,b.width);assertEquals(a.height,b.height);
        assertArrayEquals(a.tiles,b.tiles);assertArrayEquals(a.walls,b.walls);assertArrayEquals(a.seen,b.seen);
        assertEquals(a.musicId(),b.musicId());
        for(int n=0;n<90;n++) {
            var input=new World.Input(n<30?1:-1,n==1,false);a.step(input);b.step(input);
        }
        assertEquals(SaveCodec.encode(a),SaveCodec.encode(b));
    }
    @Test void realVersionOneDocumentRetainsItsOriginalDimensionsBuildsAndProgress() throws Exception {
        World legacy=new World(73,true,256,96);legacy.set(80,26,World.PLANK);legacy.add(Content.Item.WOOD,37);
        legacy.x=176*World.T;legacy.y=(legacy.surface(176)-1)*World.T;
        // Recreate the actual V1 wire header (no dimensions), not a V2 compact-world save.
        byte[] raw;
        try(var in=new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(SaveCodec.encode(legacy))))) {raw=in.readAllBytes();}
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(0x53544631);out.writeInt(1);out.write(raw,16,raw.length-16);
        }
        World loaded=SaveCodec.decode(Base64.getEncoder().encodeToString(bytes.toByteArray())).orElseThrow();
        assertEquals(256,loaded.width);assertEquals(96,loaded.height);
        assertArrayEquals(legacy.tiles,loaded.tiles);assertArrayEquals(legacy.inventory,loaded.inventory);
        assertEquals(Biome.ICECAP,loaded.region());assertEquals(legacy.x,loaded.x);
        assertEquals(legacy.shrineX[2],loaded.shrineX[2]);
    }
    @Test void actualSpawnPassSelectsEveryBiomesEnemyAndDoesNotSpawnInSolidTiles() {
        World w=new World(73,false);w.x=400*World.T;w.ticks=15000;
        for(Biome biome:Biome.values()) {
            int original=switch(biome) {
                case ANGEL_ISLAND -> 40;case MARBLE_GARDEN -> 80;case MUSHROOM_HILL -> 112;
                case CARNIVAL_NIGHT -> 144;case ICECAP -> 176;case SANDOPOLIS -> 208;
                case LAUNCH_BASE -> 240;case HYDROCITY -> 68;case LAVA_REEF -> 215;
                case HIDDEN_PALACE -> 140;case SKY_SANCTUARY -> 116;
            };
            int tx=w.geographyTile(original),ty=w.surface(tx)+(biome==Biome.HYDROCITY?40:
                    biome==Biome.LAVA_REEF?140:biome==Biome.HIDDEN_PALACE?192:biome==Biome.SKY_SANCTUARY?-76:-1);
            Arrays.fill(w.tiles,(byte)World.AIR);w.enemies.clear();w.x=tx*World.T+6;w.y=(ty+1)*World.T-10;
            for(int xx=tx-45;xx<=tx+45;xx++)w.set(xx,ty+1,World.STONE);
            for(int n=0;n<8;n++)w.spawnEnemy();
            assertFalse(w.enemies.isEmpty(),biome.label);
            for(World.Enemy e:w.enemies) {
                assertFalse(w.blocked(e.x,e.y,6,6),biome.label);
                if(biome==Biome.ANGEL_ISLAND)assertTrue(e.kind<3);
                else assertEquals(EnemyType.forBiome(biome,0),e.type());
                assertEquals(e.type().health,e.hp);
            }
        }
    }
    @Test void turretsTelegraphAndShootChargersCloseDistanceAndFliersStayWithinCaveCollision() {
        World w=new World(73,false,256,96);w.x=100*World.T;w.y=28*World.T;w.quest=7;
        for(int tx=1;tx<w.width-1;tx++)w.set(tx,29,World.STONE);
        World.Enemy skorp=new World.Enemy(w.x+90,29*World.T-6,EnemyType.SKORP.ordinal());
        skorp.timer=EnemyType.SKORP.shotPeriod-21;w.enemies.add(skorp);
        w.step(new World.Input(0,false,false));assertFalse(w.particles.isEmpty(),"visible attack warning");
        double start=skorp.x;for(int n=0;n<20;n++)w.step(new World.Input(0,false,false));
        assertEquals(start,skorp.x);assertTrue(w.shots.stream().anyMatch(s->s.hostile));
        w.enemies.clear();World.Enemy penguin=new World.Enemy(w.x+90,29*World.T-6,EnemyType.PENGUINATOR.ordinal());
        w.enemies.add(penguin);double distance=Math.abs(penguin.x-w.x);
        for(int n=0;n<20;n++)w.step(new World.Input(0,false,false));assertTrue(Math.abs(penguin.x-w.x)<distance-20);
        w.enemies.clear();World.Enemy jawz=new World.Enemy(w.x+70,w.y-10,EnemyType.JAWZ.ordinal());w.enemies.add(jawz);
        for(int n=0;n<90;n++){w.step(new World.Input(0,false,false));assertFalse(w.blocked(jawz.x,jawz.y,5,5));}
    }
}
