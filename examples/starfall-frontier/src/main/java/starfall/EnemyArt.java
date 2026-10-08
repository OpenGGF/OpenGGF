package starfall;

import com.openggf.mods.scene.*;
import static com.openggf.mods.scene.RomSpriteRequest.Compression.KOSINSKI_MODULED;

/** Verified locked-on Art/Map labels, with each source zone's own ROM palette. */
public final class EnemyArt {
    public final SceneSpriteSet[] sprites=new SceneSpriteSet[EnemyType.values().length];
    public EnemyArt(SceneRomArt rom,AngelIslandArt aiz) {
        sprites[0]=aiz.rhinobot;sprites[1]=aiz.monkeys;sprites[2]=aiz.blooms;sprites[3]=aiz.ship;
        load(rom,EnemyType.SPIKER,Biome.MARBLE_GARDEN,0x36E0C4,0x361CB8,1); // ArtKosM_Spiker / Map_Spiker
        load(rom,EnemyType.DRAGONFLY,Biome.MUSHROOM_HILL,0x166386,0x08DFDA,1); // ArtKosM_Dragonfly / Map_Dragonfly
        load(rom,EnemyType.BATBOT,Biome.CARNIVAL_NIGHT,0x3703EC,0x361BD0,1); // ArtKosM_Batbot / Map_Batbot
        sprites[EnemyType.PENGUINATOR.ordinal()]=rom.sprites(RomSpriteRequest.streamed(
                0x374154,4064,0x361E90,0x361E4E,RomSpriteRequest.DplcLayout.OBJECT,1),palette(rom,Biome.ICECAP));
        load(rom,EnemyType.SKORP,Biome.SANDOPOLIS,0x16ADC6,0x186C84,1); // ArtKosM_Skorp / Map_Skorp
        load(rom,EnemyType.RIBOT,Biome.LAUNCH_BASE,0x377BE8,0x3604B8,1); // ArtKosM_Ribot / Map_Ribot
        load(rom,EnemyType.JAWZ,Biome.HYDROCITY,0x36A552,0x361364,1); // ArtKosM_Jawz / Map_Jawz
        load(rom,EnemyType.TOXOMISTER,Biome.LAVA_REEF,0x16F7E6,0x09008E,1); // ArtKosM_Toxomister / Map_Toxomister
        load(rom,EnemyType.ORBINAUT,Biome.HIDDEN_PALACE,0x377D1A,0x3604A4,1); // ArtKosM_Orbinaut / Map_Orbinaut
        load(rom,EnemyType.EGG_ROBO,Biome.SKY_SANCTUARY,0x17B17E,0x184F34,0); // ArtKosM_EggRoboBadnik / Map_EggRobo
    }
    private void load(SceneRomArt rom,EnemyType type,Biome biome,int art,int map,int line) {
        sprites[type.ordinal()]=rom.sprites(RomSpriteRequest.of(art,KOSINSKI_MODULED,map,line),palette(rom,biome));
    }
    private static int[] palette(SceneRomArt rom,Biome biome) {
        int index=biome==Biome.HIDDEN_PALACE?47:biome.zone*2;
        byte[] entry=rom.read(0x091F0C+index*24,24);
        byte[] pointer=rom.read(0x0A872C+(entry[8]&255)*8,4);
        int address=(pointer[1]&255)<<16|(pointer[2]&255)<<8|pointer[3]&255;
        int[] colors=new int[64];
        System.arraycopy(rom.palette(0x0A8A3C,16),0,colors,0,16);
        System.arraycopy(rom.palette(address,48),0,colors,16,48);
        return colors;
    }
    int frame(World.Enemy enemy,int tick) {
        // These are body poses, not detached spike/shot mapping frames.
        return switch(enemy.type()) {
            case TOXOMISTER -> 1; // ObjDat_Toxomister body; frame 0 is not its active pose.
            case EGG_ROBO -> 1+(tick/12)%2*2; // sub_91988 selects native body frames 1/3; frame 0 is blank.
            case RHINOBOT,PENGUINATOR,RIBOT -> Math.abs(enemy.vx)>.1?(tick/8)%2:0;
            case MONKEY_DUDE,BLOOMINATOR,DRAGONFLY,BATBOT,JAWZ -> (tick/12)%2;
            default -> 0;
        };
    }
}
