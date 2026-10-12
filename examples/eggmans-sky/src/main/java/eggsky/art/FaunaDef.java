package eggsky.art;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.art.StockSceneArt;

/**
 * One kind of creature body from a ROM: where its art and mappings are, which frames animate
 * it, and how it moves. Planets turn these bodies into species with their own colours, sizes,
 * names and temperaments. Badniks are robotic fauna; the freed animals are organic fauna.
 *
 * @param key     stable id, also the save key
 * @param game    "s1", "s2" or "s3k"
 * @param zones   the zones (of {@code game}) it is native to, as a bit mask of zone ids
 * @param motion  {@link #WALK}, {@link #FLY}, {@link #HOP} or {@link #ROLL}
 * @param frames  frames to cycle while moving
 * @param animal  true for the freed animals (organic, harmless)
 */
public record FaunaDef(String key, String name, String game, long zones, RomSpriteRequest request, int motion,
        int[] frames, int frameTicks, boolean animal, StockSceneArt recipe) {
    public FaunaDef(String key,String name,String game,long zones,RomSpriteRequest request,int motion,
            int[] frames,int frameTicks,boolean animal) {
        this(key,name,game,zones,request,motion,frames,frameTicks,animal,null);
    }
    public FaunaDef(String key,String name,long zones,StockSceneArt recipe,int motion,int[] frames,int frameTicks) {
        this(key,name,recipe.gameId(),zones,null,motion,frames,frameTicks,false,recipe);
    }
    /** Named banks enforce their ROM identity; expert requests retain their own mapping/DPLC dialect. */
    public RomSpriteRequest request(SceneRomArt rom) { return recipe==null?request:recipe.request(rom); }
    public static final int WALK = 0;
    public static final int FLY = 1;
    public static final int HOP = 2;
    public static final int ROLL = 3;

    public boolean nativeTo(int zone) {
        return zone >= 0 && zone < 64 && (zones & (1L << zone)) != 0;
    }
}
