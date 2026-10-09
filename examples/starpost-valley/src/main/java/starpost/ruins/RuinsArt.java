package starpost.ruins;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.scene.art.StockSceneArt;
import java.util.HashMap;
import java.util.Map;
import starpost.art.Art;

/**
 * The Ruins' pictures, all from the player's ROMs: Sonic 1's Marble, Labyrinth and Scrap Brain
 * level kits (blocks, collision, backgrounds, palettes), Labyrinth's underwater palette, the
 * zones' badniks in their own palettes, spikes, air bubbles and the water surface (addresses
 * from s1disasm's sonic.lst); Sonic 3 &amp; Knuckles' explosion, rings, monitors, springs and Star
 * Post come from {@link Art}. Kits are built the first time a band is entered (up to a second
 * each), never in draw.
 */
public final class RuinsArt implements ChamberGen.Kits {
    /** Pal_LZWater: Labyrinth's four palette lines below the water line. */
    private static final int PAL_LZ_WATER = 0x2460;

    public final Art art;
    public final SceneRomArt s1;
    private final Map<Integer, RomKit> kits = new HashMap<>();
    private final Map<Long, SceneImage> blocks = new HashMap<>();
    private final Map<Integer, Map<Integer, Integer>> waterMaps = new HashMap<>();
    private final Map<Integer, SceneSpriteSet> badniks = new HashMap<>();
    private final Map<Integer, SceneSpriteSet> missiles = new HashMap<>();
    private final Map<Integer, SceneSpriteSet> spikes = new HashMap<>();
    public final SceneSpriteSet explosion;
    private SceneSpriteSet bubbles;
    private SceneSpriteSet surface;
    private SceneSpriteSet greenBlock;
    private final SceneImage[] lavaSurface = new SceneImage[3];
    private final SceneImage[] magma = new SceneImage[3];

    public RuinsArt(Art art) {
        this.art = art;
        this.s1 = art.s1;
        this.explosion = art.s3k.sprites(StockSceneArt.S3K_EXPLOSION.request(art.s3k), art.aizPalette);
    }

    @Override
    public Kit kit(int zone, int act) {
        return romKit(zone, act);
    }

    public RomKit romKit(int zone, int act) {
        int key = zone * 8 + act;
        RomKit kit = kits.get(key);
        if (kit == null && !kits.containsKey(key)) {
            SceneLevelKit stock = s1.levelKit(zone, act);
            kit = stock == null ? null : new RomKit(stock);
            kits.put(key, kit);
        }
        return kit;
    }

    /** A block's picture, recoloured with Labyrinth's underwater palette when {@code wet}. */
    public SceneImage block(int zone, int act, int id, boolean wet) {
        long key = ((long) zone * 8 + act) << 32 | (long) id << 1 | (wet ? 1 : 0);
        SceneImage image = blocks.get(key);
        if (image == null) {
            RomKit kit = romKit(zone, act);
            if (kit == null || id <= 0) {
                return null;
            }
            image = kit.kit.blockImage(id);
            if (wet) {
                image = recolour(image, waterMap(zone, act));
            }
            blocks.put(key, image);
        }
        return image;
    }

    /** The act's own palette mapped, entry by entry, to Pal_LZWater (how the ROM recolours below the line). */
    private Map<Integer, Integer> waterMap(int zone, int act) {
        int key = zone * 8 + act;
        Map<Integer, Integer> map = waterMaps.get(key);
        if (map == null) {
            map = new HashMap<>();
            int[] dry = romKit(zone, act).kit.palette();
            int[] wet = s1.palette(PAL_LZ_WATER, 64);
            for (int i = 0; i < Math.min(dry.length, wet.length); i++) {
                map.putIfAbsent(dry[i] & 0xFFFFFF, wet[i] & 0xFFFFFF);
            }
            waterMaps.put(key, map);
        }
        return map;
    }

    private static SceneImage recolour(SceneImage image, Map<Integer, Integer> map) {
        int[] px = image.pixels();
        for (int i = 0; i < px.length; i++) {
            if (px[i] >>> 24 != 0) {
                Integer to = map.get(px[i] & 0xFFFFFF);
                if (to != null) {
                    px[i] = (px[i] & 0xFF000000) | to;
                }
            }
        }
        return new SceneImage(image.width(), image.height(), px);
    }

    public SceneBackdrop backdrop(int zone, int act) {
        RomKit kit = romKit(zone, act);
        return kit == null ? null : kit.kit.backdrop();
    }

    /** A band's palette: its first act's four lines (badniks and hazards are coloured with it). */
    private int[] palette(int band) {
        RomKit kit = romKit(RuinsRules.zone(band), 0);
        return kit == null ? art.ghzPalette : kit.kit.palette();
    }

    /**
     * A badnik's frames in its band's palette, on the palette line its object uses (Tile_Pal2 is
     * line 1). Art and mappings from sonic.lst.
     */
    public SceneSpriteSet badnik(int kind, int band) {
        int key = kind * 4 + band;
        SceneSpriteSet set = badniks.get(key);
        if (set == null && !badniks.containsKey(key)) {
            RomSpriteRequest request = switch (kind) {
                case Badnik.CATERKILLER -> RomSpriteRequest.of(0x39076, Compression.NEMESIS, 0x1751A, 1); // Nem_Cater, Map_Cat
                case Badnik.BATBRAIN -> RomSpriteRequest.of(0x386BC, Compression.NEMESIS, 0x108CA, 0);   // Nem_Basaran, Map_Bas
                case Badnik.BUZZ_BOMBER -> RomSpriteRequest.of(0x3639E, Compression.NEMESIS, 0xA0B4, 0);  // Nem_Buzz, Map_Buzz
                case Badnik.YADRIN -> RomSpriteRequest.of(0x382D4, Compression.NEMESIS, 0xFFBA, 1);      // Nem_Yadrin, Map_Yad
                case Badnik.JAWS -> RomSpriteRequest.of(0x3727E, Compression.NEMESIS, 0xB2FA, 1);        // Nem_Jaws, Map_Jaws
                case Badnik.BURROBOT -> RomSpriteRequest.of(0x3692C, Compression.NEMESIS, 0xB4E6, 0);    // Nem_Burrobot, Map_Burro
                case Badnik.ORBINAUT -> RomSpriteRequest.of(0x38E98, Compression.NEMESIS, 0x125B8, 0);   // Nem_Orbinaut, Map_Orb
                case Badnik.BOMB -> RomSpriteRequest.of(0x38C00, Compression.NEMESIS, 0x122FC, 0);       // Nem_Bomb, Map_Bomb
                default -> RomSpriteRequest.of(0x35AF0, Compression.NEMESIS, 0x94E0, 1);                 // Nem_BallHog, Map_Hog
            };
            set = load(request, palette(band));
            badniks.put(key, set);
        }
        return set;
    }

    /** The Buzz Bomber's missile (Map_Missile, palette line 1). */
    public SceneSpriteSet missile(int band) {
        return missiles.computeIfAbsent(band, b -> load(RomSpriteRequest.of(0x3639E, Compression.NEMESIS, 0xA184, 1),
                palette(b)));
    }

    /** Upright spikes (Nem_Spikes, Map_Spike). */
    public SceneSpriteSet spikes(int band) {
        return spikes.computeIfAbsent(band, b -> load(RomSpriteRequest.of(0x2FCFE, Compression.NEMESIS, 0xD676, 0),
                palette(b)));
    }

    /** Labyrinth's air bubbles and countdown numbers (Nem_Bubbles, Map_Bub). */
    public SceneSpriteSet bubbles() {
        if (bubbles == null) {
            bubbles = load(RomSpriteRequest.of(0x30EE8, Compression.NEMESIS, 0x130A0, 0), palette(RuinsRules.LABYRINTH));
        }
        return bubbles;
    }

    /** Labyrinth's water surface (Nem_Water, Map_Surf, palette line 2). */
    public SceneSpriteSet surface() {
        if (surface == null) {
            surface = load(RomSpriteRequest.of(0x302E6, Compression.NEMESIS, 0x11840, 2), palette(RuinsRules.LABYRINTH));
        }
        return surface;
    }

    /** Marble Zone's smashable green block (Nem_MzBlock, Map_Smab, palette line 2). */
    public SceneSpriteSet greenBlock() {
        if (greenBlock == null) {
            greenBlock = load(RomSpriteRequest.of(0x33670, Compression.NEMESIS, 0x10460, 2), palette(RuinsRules.MARBLE));
        }
        return greenBlock;
    }

    /**
     * Marble Zone's lava surface (Art_MzLava1: three frames of 4x2 tiles, column by column, on
     * palette line 3), which AniArt_MZ swaps every 20 frames. The kit leaves these tiles blank.
     */
    public SceneImage lavaSurface(int frame) {
        if (lavaSurface[frame] == null) {
            lavaSurface[frame] = lavaTiles(0x67516, frame * 8, 2);
        }
        return lavaSurface[frame];
    }

    /** The magma under it (Art_MzLava2: 4x4 tiles a frame, following the surface's frame). */
    public SceneImage magma(int frame) {
        if (magma[frame] == null) {
            magma[frame] = lavaTiles(0x67816, frame * 16, 4);
        }
        return magma[frame];
    }

    private SceneImage lavaTiles(int address, int first, int heightTiles) {
        int[] line = new int[16];
        System.arraycopy(palette(RuinsRules.MARBLE), 48, line, 0, 16);
        try {
            return s1.tiles(address, Compression.UNCOMPRESSED, first, 4, heightTiles, true, line);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private SceneSpriteSet load(RomSpriteRequest request, int[] palette) {
        try {
            return s1.sprites(request, palette);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
