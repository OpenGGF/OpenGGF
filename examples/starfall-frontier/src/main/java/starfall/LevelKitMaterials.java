package starfall;

import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.art.StockSceneArt;

/**
 * Native material identities qualified against the locked-on ROM's 16px block decoder.
 * Coordinates are layout-block id, pixel x/y and native flips, not procedural geometry.
 * TestStarfallLevelKitEquivalence compares all pixels to the original block ids.
 */
final class LevelKitMaterials {
    private LevelKitMaterials() { }
    static SceneImage[] samples(SceneRomArt rom,Biome biome,int act) {
        StockSceneArt.S3K_RING.request(rom); // The same verified ROM identity as the stock recipe bank.
        SceneLevelKit kit=rom.levelKit(biome.zone,biome==Biome.HIDDEN_PALACE?1:act);
        if(kit==null)throw new IllegalStateException("No material kit: "+biome+":"+act);
        int[][] cells=switch(biome) {
            case MARBLE_GARDEN -> new int[][]{{141,80,80,3},{25,64,48,0},{2,48,64,0}}; // native 280,2,36
            case MUSHROOM_HILL -> new int[][]{{2,64,48,0},{2,80,48,0},{2,96,32,0}}; // 50,51,44
            case CARNIVAL_NIGHT -> new int[][]{{17,96,80,0},{17,64,80,0},{3,32,96,0}}; // 56,20,45
            case ICECAP -> new int[][]{{66,16,80,1},{44,64,0,0},{2,0,0,0}}; // 36,70,71
            case SANDOPOLIS -> new int[][]{{1,0,64,0},{1,16,80,0},{159,16,64,0}}; // 1,4,162; both acts
            case LAUNCH_BASE -> new int[][]{{183,16,16,0},{68,48,0,1},{183,96,32,0}}; // 4,11,14
            case HYDROCITY -> new int[][]{{19,32,0,0},{act==0?24:23,0,112,0},{6,96,0,0}}; // 36,33,38
            case LAVA_REEF -> new int[][]{{2,0,64,1},{2,64,80,1},{2,32,96,1}}; // 5,17,23; both acts
            case HIDDEN_PALACE -> new int[][]{{1,16,32,0},{1,0,0,0},{1,32,0,0}}; // 13,1,3
            case SKY_SANCTUARY -> new int[][]{{3,112,80,0},{3,64,80,0},{3,80,80,0}}; // 4,1,2
            default -> throw new IllegalArgumentException("Angel Island has its own material samples");
        };
        SceneImage[] result=new SceneImage[cells.length];
        for(int n=0;n<cells.length;n++) {
            int[] cell=cells[n],pixels=new int[256];SceneImage source=kit.blockImage(cell[0]);
            for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
                int pixel=source.pixel(cell[1]+((cell[3]&1)==0?x:15-x),cell[2]+((cell[3]&2)==0?y:15-y));
                // Preserve SceneRomArt.palette's truncation; Palette.Color/levelKit round by one
                // channel at native intensities 2,4,6. No engine-wide palette policy changes.
                pixels[y*16+x]=pixel>>>24==0?0:pixel&0xFF000000
                        |channel(pixel>>>16&255)<<16|channel(pixel>>>8&255)<<8|channel(pixel&255);
            }
            result[n]=new SceneImage(16,16,pixels);
        }
        return result;
    }
    private static int channel(int rounded) { return ((rounded*7+127)/255)*255/7; }
}
