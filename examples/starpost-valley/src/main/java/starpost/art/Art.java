package starpost.art;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.scene.art.StockSceneArt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything drawn from the player's ROMs. Green Hill comes from Sonic 1's level kit: its 256-pixel
 * blocks with their per-pixel collision, background, palette and decorations cut from the blocks.
 * Characters, springs, Star Posts, monitors, rings, Flickies and the Egg Robo come from Sonic 3 &amp;
 * Knuckles; the signpost, animals and badniks from Sonic 1, in Green Hill's palette. Terrain is
 * recoloured per season by {@link Tone}; built once in the scene's {@code enter}, never in draw.
 */
public final class Art {
    // Sonic 3 & Knuckles palettes (sonic3k.lst).
    private static final int PAL_SONIC_TAILS = 0x0A8A3C;  // Pal_SonicTails
    private static final int PAL_AIZ = 0x0A8B7C;          // Pal_AIZ
    private static final int PAL_CONTINUE = 0x05CBCA;     // Pal_ContinueScreen (Egg Robo on line 1)

    /** The floor row of Green Hill's flat blocks. */
    public static final int FLOOR = 192;
    public static final int BLOCK = 256;

    public final SceneRomArt s1;
    public final SceneRomArt s3k;
    public final SceneLevelKit kit;
    public final int[] ghzPalette;
    public final int[] aizPalette;
    private final Map<String, SceneSpriteSet> farmers = new HashMap<>();
    public final SceneSpriteSet tailsTails;
    public final SceneSpriteSet spring;
    public final SceneSpriteSet starpost;
    public final SceneSpriteSet monitor;
    public final SceneSpriteSet ring;
    public final SceneSpriteSet flicky;
    public final SceneSpriteSet eggRobo;
    public final SceneSpriteSet signpost;
    public final SceneSpriteSet motobug;
    public final SceneSpriteSet buzzBomber;
    public final SceneSpriteSet crabmeat;
    public final SceneSpriteSet purpleRock;
    public final SceneSpriteSet lamppost;
    public final SceneSpriteSet capsule;
    public final SceneSpriteSet bridge;
    private final Map<String, SceneSpriteSet> animals = new HashMap<>();

    /** Rows below the floor line that are grass (the lip a tilled plot replaces). */
    public final int lipDepth;
    private final byte[][] solidity;
    private final SceneImage soil;
    private final SceneImage palm;
    private final SceneImage totem;
    private final SceneImage plant;
    private final Seasonal[] seasons = new Seasonal[4];
    public final ItemIcons icons;
    public final HudArt hud;
    /** Sonic 3 &amp; Knuckles title-card lettering and the card's red banner (null if unavailable). */
    public final CardFont cardFont;
    public final SceneImage cardBanner;
    private final Facades facades;

    /** A season's recoloured pictures, built on first use. */
    public final class Seasonal {
        public final Tone tone;
        public final CropArt crops;
        public final SceneImage tilledDry;
        public final SceneImage tilledWet;
        public final SceneImage palm;
        public final SceneImage totem;
        public final SceneImage plant;
        public final SceneImage field;
        public final SceneImage farmhouse;
        public final SceneImage seedStall;
        public final SceneImage inn;
        public final SceneImage workshop;
        public final SceneImage eggStore;
        private final SceneImage[] blocks;
        private final SceneBackdrop[] backdrops = new SceneBackdrop[3];

        Seasonal(Tone tone) {
            this.tone = tone;
            SceneImage stock = kit.backdrop().image();
            SceneImage sky = tone.apply(stock.crop(0, 0, stock.width(), Math.min(256, stock.height())));
            backdrops[0] = new SceneBackdrop(sky, greenHillBands(sky.height()));
            crops = new CropArt(tone);
            tilledDry = tone.apply(tilled(false));
            tilledWet = tone.apply(tilled(true));
            palm = tone.apply(Art.this.palm);
            totem = tone.apply(Art.this.totem);
            plant = tone.apply(Art.this.plant);
            field = tone.apply(field());
            farmhouse = tone.apply(facades.farmhouse);
            seedStall = tone.apply(facades.seedStall);
            inn = tone.apply(facades.inn);
            workshop = tone.apply(facades.workshop);
            eggStore = tone.apply(facades.eggStore);
            blocks = new SceneImage[kit.blockCount()];
        }

        /** Green Hill's background at a light (0 day, 1 dusk, 2 night), built on first use. */
        public SceneBackdrop backdrop(int light) {
            if (backdrops[light] == null) {
                SceneImage day = backdrops[0].image();
                int[] px = day.pixels();
                Map<Integer, Integer> seen = new HashMap<>();
                for (int i = 0; i < px.length; i++) {
                    px[i] = seen.computeIfAbsent(px[i], c -> Tone.sky(c, light));
                }
                backdrops[light] = new SceneBackdrop(new SceneImage(day.width(), day.height(), px),
                        backdrops[0].bands());
            }
            return backdrops[light];
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

    public Art(SceneRomArt s1, SceneRomArt s3k) {
        this.s1 = s1;
        this.s3k = s3k;
        kit = s1.levelKit(0, 0);
        if (kit == null || kit.blockSize() != BLOCK) {
            throw new IllegalStateException("Green Hill's level kit is unavailable");
        }
        ghzPalette = kit.palette();
        solidity = new byte[kit.blockCount()][];

        aizPalette = new int[64];
        System.arraycopy(s3k.palette(PAL_SONIC_TAILS, 16), 0, aizPalette, 0, 16);
        System.arraycopy(s3k.palette(PAL_AIZ, 48), 0, aizPalette, 16, 48);
        for (String code : new String[] {"sonic", "tails", "knuckles"}) {
            farmers.put(code, s3k.character(code));
        }
        tailsTails = s3k.characterAccessory("tails");
        spring = s3k.sprites(RomSpriteRequest.of(0x1927FE, Compression.NEMESIS,  // ArtNem_SpikesSprings
                0x02375C, 0).withTileOffset(-0x10), aizPalette);                       // Map_Spring
        starpost = s3k.sprites(RomSpriteRequest.of(0x192D2A, Compression.NEMESIS, // ArtNem_EnemyPtsStarPost
                0x02D348, 0).withTileOffset(-8), aizPalette);                          // Map_StarPost
        monitor = s3k.sprites(RomSpriteRequest.of(0x190F4A, Compression.NEMESIS,  // ArtNem_Monitors
                0x01DBA2, 0), aizPalette);                                              // Map_Monitor
        ring = s3k.sprites(StockSceneArt.S3K_RING.request(s3k), aizPalette);
        flicky = s3k.sprites(StockSceneArt.S3K_BLUE_FLICKY.request(s3k), aizPalette);
        eggRobo = s3k.sprites(StockSceneArt.S3K_EGG_ROBO.request(s3k), s3k.palette(PAL_CONTINUE, 64));
        // Sonic 1 objects draw in Green Hill's own palette (sonic.lst addresses).
        signpost = s1.sprites(RomSpriteRequest.of(0x3A9E8, Compression.NEMESIS, 0xF3C4, 0), ghzPalette); // Nem_SignPost, Map_Sign
        motobug = s1.sprites(RomSpriteRequest.of(0x37A2C, Compression.NEMESIS, 0xFE2C, 0), ghzPalette);  // Nem_Motobug, Map_Moto
        buzzBomber = s1.sprites(RomSpriteRequest.of(0x3639E, Compression.NEMESIS, 0xA0B4, 0), ghzPalette); // Nem_Buzz, Map_Buzz
        crabmeat = s1.sprites(RomSpriteRequest.of(0x35EB0, Compression.NEMESIS, 0x9DCE, 0), ghzPalette); // Nem_Crabmeat, Map_Crab
        purpleRock = s1.sprites(RomSpriteRequest.of(0x300BA, Compression.NEMESIS, 0xD79C, 3), ghzPalette); // Nem_PplRock, Map_PRock
        lamppost = s1.sprites(RomSpriteRequest.of(0x3AE64, Compression.NEMESIS, 0x178A4, 0), ghzPalette); // Nem_Lamp, Map_Lamp
        capsule = s1.sprites(RomSpriteRequest.of(0x5DC4A, Compression.NEMESIS, 0x1B52A, 0), ghzPalette); // Nem_Prison, Map_Pri
        bridge = s1.sprites(RomSpriteRequest.of(0x2FA2C, Compression.NEMESIS, 0x7FB2, 2), ghzPalette);  // Nem_Bridge, Map_Bri
        // Anml_Variables: the animals Sonic 1 frees (Nem_Rabbit ... Nem_Squirrel, Map_Animal1-3).
        animal("pocky", 0x3B884, 0x9AE4);
        animal("cucky", 0x3B9DC, 0x9AFC);
        animal("pecky", 0x3BB38, 0x9AE4);
        animal("rocky", 0x3BCB4, 0x9AFC);
        animal("picky", 0x3BDD0, 0x9B14);
        animal("flicky", 0x3BF06, 0x9AFC);
        animal("ricky", 0x3C040, 0x9B14);

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
        icons = new ItemIcons(this);
        facades = new Facades(this);
        hud = new HudArt(s1, ghzPalette);
        cardFont = new CardFont(s3k);
        cardBanner = s3k.hasTitleCard(0, 0) ? plainBanner(s3k.titleCard(0, 0).frame(0).image()) : null;
    }

    /**
     * The title card's red banner without the game's name at its foot: the banner's own red is
     * carried down over the lettering, so the card reads as Starpost Valley's.
     */
    private static SceneImage plainBanner(SceneImage banner) {
        int[] px = banner.pixels();
        int w = banner.width(), h = banner.height();
        int red = banner.pixel(w / 2, h / 3);
        for (int y = h * 3 / 4; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (px[y * w + x] >>> 24 != 0) {
                    px[y * w + x] = red;
                }
            }
        }
        return new SceneImage(w, h, px);
    }

    private void animal(String name, int art, int map) {
        animals.put(name, s1.sprites(RomSpriteRequest.of(art, Compression.NEMESIS, map, 0), ghzPalette));
    }

    public SceneSpriteSet farmer(String code) {
        return farmers.getOrDefault(code, farmers.get("sonic"));
    }

    public SceneSpriteSet animal(String name) {
        return animals.get(name);
    }

    public Seasonal season(int season) {
        if (seasons[season] == null) {
            seasons[season] = new Seasonal(new Tone(season));
        }
        return seasons[season];
    }

    /** Whether a pixel of a block is solid (top-solid floors count). Rows beyond the block are solid below. */
    public boolean solid(int block, int x, int y) {
        if (y >= BLOCK) {
            return true;
        }
        if (block <= 0 || y < 0) {
            return false;
        }
        if (solidity[block] == null) {
            solidity[block] = kit.blockSolidity(block);
        }
        return solidity[block][y * BLOCK + x] != SceneLevelKit.EMPTY;
    }

    /**
     * Green Hill's parallax as bands of its background picture, after Sonic 1's Deform_GHZ:
     * three cloud strips drifting at their own rates, distant mountains, nearer hills, then the
     * water in strips that move faster toward the screen.
     */
    static List<SceneBackdrop.Band> greenHillBands(int height) {
        List<SceneBackdrop.Band> bands = new ArrayList<>();
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

    private static boolean isGreen(int argb) {
        if (argb >>> 24 == 0) {
            return false;
        }
        float[] hsv = Tone.hsv(argb);
        return hsv[0] >= 70 && hsv[0] <= 170 && hsv[1] > 0.3f;
    }

    static SceneImage opaqueBounds(SceneImage image, int x, int y, int w, int h) {
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
     * The farm's ground: a field of Green Hill's three grass greens seen from above, as a
     * 64-pixel tile. Deterministic tufts, so it reads as grass rather than noise.
     */
    private static SceneImage field() {
        int base = 0xFF49B600, light = 0xFF92FF00, dark = 0xFF006D00;
        int[] px = new int[64 * 64];
        java.util.Arrays.fill(px, base);
        long seed = 0x5EED_6A11L;
        for (int i = 0; i < 90; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int x = (int) ((seed >>> 33) & 63), y = (int) ((seed >>> 45) & 63);
            boolean bright = (seed & 3) != 0;
            int colour = bright ? light : dark;
            for (int k = 0; k < 3; k++) {
                px[((y - k) & 63) * 64 + (x & 63)] = colour;
                px[((y - k) & 63) * 64 + ((x - 2 + (k == 2 ? 0 : -k + 1)) & 63)] = bright ? base : colour;
                px[((y - k / 2) & 63) * 64 + ((x + 2) & 63)] = colour;
            }
        }
        return new SceneImage(64, 64, px);
    }
}
