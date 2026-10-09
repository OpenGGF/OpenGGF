package starpost.looktest;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.scene.art.StockSceneArt;

/**
 * Everything the look test draws, from the player's ROMs. Green Hill comes from Sonic 1's level
 * kit: the valley is a row of the act's own 256-pixel blocks with their per-pixel collision, its
 * background and palette, and decorations cut from those blocks (a palm, the totem, a plant).
 * Sonic, springs, the Star Post, monitors, rings and Flickies come from Sonic 3 &amp; Knuckles;
 * the signpost, animals and Motobug from Sonic 1. Every terrain picture is recoloured per season
 * by {@link Tone}.
 */
public final class ValleyArt {
    // Sonic 3 & Knuckles palettes (sonic3k.lst).
    private static final int PAL_SONIC_TAILS = 0x0A8A3C;  // Pal_SonicTails
    private static final int PAL_AIZ = 0x0A8B7C;          // Pal_AIZ

    /** The look test's valley, left to right: Green Hill act 1 block ids. */
    public final int[] sideRow = {13, 45, 60, 3, 60, 45, 53, 38, 1};
    /** The floor row of Green Hill's flat blocks (blocks 13, 45, 60 and the edges of 3). */
    public static final int FLOOR = 192;
    public static final int BLOCK = 256;

    public final SceneLevelKit kit;
    public final int[] ghzPalette;
    public final SceneSpriteSet sonic;
    public final SceneSpriteSet spring;
    public final SceneSpriteSet starpost;
    public final SceneSpriteSet monitor;
    public final SceneSpriteSet ring;
    public final SceneSpriteSet flicky;
    public final SceneSpriteSet signpost;
    public final SceneSpriteSet cucky;
    public final SceneSpriteSet pocky;
    public final SceneSpriteSet motobug;

    /** Rows below the floor line that are grass (the lip a tilled plot replaces). */
    public final int lipDepth;
    private final byte[][] solidity;
    private final SceneImage soil;
    private final SceneImage palm;
    private final SceneImage totem;
    private final SceneImage plant;
    private final Seasonal[] seasons = new Seasonal[4];

    /** A season's recoloured pictures, built on first use. */
    public static final class Seasonal {
        public final Tone tone;
        private final SceneBackdrop[] backdrops = new SceneBackdrop[3];
        public final CropArt crops;
        public final SceneImage tilledDry;
        public final SceneImage tilledWet;
        public final SceneImage palm;
        public final SceneImage totem;
        public final SceneImage plant;
        public final SceneImage field;
        private final SceneImage[] blocks;
        private final SceneLevelKit kit;

        Seasonal(Tone tone, ValleyArt art) {
            this.tone = tone;
            this.kit = art.kit;
            SceneImage stock = art.kit.backdrop().image();
            SceneImage sky = tone.apply(stock.crop(0, 0, stock.width(), Math.min(256, stock.height())));
            backdrops[0] = new SceneBackdrop(sky, greenHillBands(sky.height()));
            crops = new CropArt(tone, null);
            tilledDry = tone.apply(art.tilled(false));
            tilledWet = tone.apply(art.tilled(true));
            palm = tone.apply(art.palm);
            totem = tone.apply(art.totem);
            plant = tone.apply(art.plant);
            field = tone.apply(art.field());
            blocks = new SceneImage[art.kit.blockCount()];
        }

        /** Green Hill's background at a time of day (0 day, 1 dusk, 2 night), built on first use. */
        public SceneBackdrop backdrop(int time) {
            if (backdrops[time] == null) {
                SceneImage day = backdrops[0].image();
                int[] px = day.pixels();
                java.util.HashMap<Integer, Integer> seen = new java.util.HashMap<>();
                for (int i = 0; i < px.length; i++) {
                    int c = px[i];
                    px[i] = seen.computeIfAbsent(c, k -> Tone.sky(k, time));
                }
                SceneImage sky = new SceneImage(day.width(), day.height(), px);
                backdrops[time] = new SceneBackdrop(sky, backdrops[0].bands());
            }
            return backdrops[time];
        }

        public SceneImage block(int id) {
            if (id <= 0 || id >= blocks.length) {
                return null;
            }
            if (blocks[id] == null) {
                blocks[id] = tone.apply(kit.blockImage(id));
            }
            return blocks[id];
        }
    }

    /**
     * Green Hill's parallax as bands of its background picture, after Sonic 1's Deform_GHZ:
     * three cloud strips drifting at their own rates, distant mountains, nearer hills, then the
     * water in strips that move faster toward the screen.
     */
    static java.util.List<SceneBackdrop.Band> greenHillBands(int height) {
        java.util.List<SceneBackdrop.Band> bands = new java.util.ArrayList<>();
        bands.add(new SceneBackdrop.Band(0, 32, 0.03, 0.25));
        bands.add(new SceneBackdrop.Band(32, 16, 0.03, 0.18));
        bands.add(new SceneBackdrop.Band(48, 16, 0.03, 0.12));
        bands.add(new SceneBackdrop.Band(64, 48, 0.06, 0));
        bands.add(new SceneBackdrop.Band(112, 40, 0.12, 0));
        int top = 152;
        double speed = 0.2;
        while (top < height) {
            int rows = Math.min(8, height - top);
            bands.add(new SceneBackdrop.Band(top, rows, speed, 0));
            top += rows;
            speed += 0.03;
        }
        return bands;
    }

    public ValleyArt(SceneRomArt s1, SceneRomArt s3k) {
        kit = s1.levelKit(0, 0);
        if (kit == null || kit.blockSize() != BLOCK) {
            throw new IllegalStateException("Green Hill's level kit is unavailable");
        }
        ghzPalette = kit.palette();
        solidity = new byte[kit.blockCount()][];

        int[] aiz = new int[64];
        System.arraycopy(s3k.palette(PAL_SONIC_TAILS, 16), 0, aiz, 0, 16);
        System.arraycopy(s3k.palette(PAL_AIZ, 48), 0, aiz, 16, 48);
        sonic = s3k.character("sonic");
        spring = s3k.sprites(RomSpriteRequest.of(0x1927FE, Compression.NEMESIS,  // ArtNem_SpikesSprings
                0x02375C, 0).withTileOffset(-0x10), aiz);                              // Map_Spring
        starpost = s3k.sprites(RomSpriteRequest.of(0x192D2A, Compression.NEMESIS, // ArtNem_EnemyPtsStarPost
                0x02D348, 0).withTileOffset(-8), aiz);                                 // Map_StarPost
        monitor = s3k.sprites(RomSpriteRequest.of(0x190F4A, Compression.NEMESIS,  // ArtNem_Monitors
                0x01DBA2, 0), aiz);                                                     // Map_Monitor
        ring = s3k.sprites(StockSceneArt.S3K_RING.request(s3k), aiz);
        flicky = s3k.sprites(StockSceneArt.S3K_BLUE_FLICKY.request(s3k), aiz);
        // Sonic 1 objects draw in Green Hill's own palette (sonic.lst addresses).
        signpost = s1.sprites(RomSpriteRequest.of(0x3A9E8, Compression.NEMESIS, 0xF3C4, 0), ghzPalette); // Nem_SignPost, Map_Sign
        cucky = s1.sprites(RomSpriteRequest.of(0x3B9DC, Compression.NEMESIS, 0x9AFC, 0), ghzPalette);    // Nem_Chicken
        pocky = s1.sprites(RomSpriteRequest.of(0x3B884, Compression.NEMESIS, 0x9AE4, 0), ghzPalette);    // Nem_Rabbit
        motobug = s1.sprites(RomSpriteRequest.of(0x37A2C, Compression.NEMESIS, 0xFE2C, 0), ghzPalette);  // Nem_Motobug, Map_Moto

        // Block 60 is plain flat ground: grass from the floor down, a shadow band, then soil.
        SceneImage flat = kit.blockImage(60);
        int lip = 0;
        while (FLOOR + lip < BLOCK && isGreen(flat.pixel(40, FLOOR + lip))) {
            lip++;
        }
        lipDepth = Math.max(4, lip);
        soil = flat.crop(0, FLOOR + 32, 32, 16);
        // Decorations cut from their blocks above the floor: a palm on block 55's pillar, the
        // winged totem on block 56's ledge, the leafy plant at block 1's left edge.
        palm = opaqueBounds(kit.blockImage(55), 0, 0, 96, FLOOR - 6);
        totem = opaqueBounds(kit.blockImage(56), 192, 0, 64, 124);
        plant = opaqueBounds(kit.blockImage(1), 0, 60, 34, 66);
    }

    public Seasonal season(int season) {
        if (seasons[season] == null) {
            seasons[season] = new Seasonal(new Tone(season), this);
        }
        return seasons[season];
    }

    public int worldWidth() {
        return sideRow.length * BLOCK;
    }

    public int blockAt(int worldX) {
        int column = Math.floorDiv(worldX, BLOCK);
        return column < 0 || column >= sideRow.length ? 0 : sideRow[column];
    }

    /** Whether a world pixel of the side valley is solid ground (top-solid floors count). */
    public boolean solid(int worldX, int worldY) {
        if (worldY < 0 || worldY >= BLOCK) {
            return worldY >= BLOCK;
        }
        int id = blockAt(worldX);
        if (id <= 0) {
            return false;
        }
        if (solidity[id] == null) {
            solidity[id] = kit.blockSolidity(id);
        }
        return solidity[id][worldY * BLOCK + Math.floorMod(worldX, BLOCK)] != SceneLevelKit.EMPTY;
    }

    /** The first solid row at or below {@code fromY} in a column, or {@link #BLOCK} * 2 if none. */
    public int floorBelow(int worldX, int fromY) {
        for (int y = Math.max(0, fromY); y < BLOCK; y++) {
            if (solid(worldX, y)) {
                return y;
            }
        }
        return BLOCK * 2;
    }

    private static boolean isGreen(int argb) {
        if (argb >>> 24 == 0) {
            return false;
        }
        float[] hsv = Tone.hsv(argb);
        return hsv[0] >= 70 && hsv[0] <= 170 && hsv[1] > 0.3f;
    }

    private static SceneImage opaqueBounds(SceneImage image, int x, int y, int w, int h) {
        int x0 = x + w, y0 = y + h, x1 = x - 1, y1 = y - 1;
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                if (image.pixel(xx, yy) >>> 24 != 0) {
                    x0 = Math.min(x0, xx);
                    y0 = Math.min(y0, yy);
                    x1 = Math.max(x1, xx);
                    y1 = Math.max(y1, yy);
                }
            }
        }
        if (x1 < x0) {
            return new SceneImage(1, 1, new int[1]);
        }
        return image.crop(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    /** Tilled soil: Green Hill's own checker, lightened (dry) or darkened (watered). */
    private SceneImage tilled(boolean wet) {
        int[] px = soil.pixels();
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = c >>> 16 & 255, g = c >>> 8 & 255, b = c & 255;
            if (wet) {
                r = r * 3 / 5;
                g = g * 3 / 5;
                b = b * 3 / 4;
            } else {
                r = Math.min(255, r + 24);
                g = Math.min(255, g + 18);
            }
            px[i] = Tone.genesis(0xFF000000 | r << 16 | g << 8 | b);
        }
        return new SceneImage(soil.width(), soil.height(), px);
    }

    /**
     * The belt view's ground: a field of Green Hill's three grass greens seen from above, as a
     * 64-pixel tile. Deterministic tufts, so it reads as grass rather than noise.
     */
    private SceneImage field() {
        int base = 0xFF49B600, light = 0xFF92FF00, dark = 0xFF006D00;
        int[] px = new int[64 * 64];
        java.util.Arrays.fill(px, base);
        long seed = 0x5EED_6A11L;
        for (int i = 0; i < 90; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int x = (int) ((seed >>> 33) & 63), y = (int) ((seed >>> 45) & 63);
            boolean bright = (seed & 3) != 0;
            int colour = bright ? light : dark;
            // A tuft: three short blades leaning apart.
            for (int k = 0; k < 3; k++) {
                px[((y - k) & 63) * 64 + (x & 63)] = colour;
                px[((y - k) & 63) * 64 + ((x - 2 + (k == 2 ? 0 : -k + 1)) & 63)] = bright ? base : colour;
                px[((y - k / 2) & 63) * 64 + ((x + 2) & 63)] = colour;
            }
        }
        return new SceneImage(64, 64, px);
    }
}
