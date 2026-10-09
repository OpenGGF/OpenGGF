package starpost.fishing;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.HashMap;
import java.util.Map;
import starpost.art.Art;
import starpost.art.ItemIcons;
import starpost.art.Tone;
import starpost.core.Item;

/**
 * Fishing's pictures. The badniks, the air bubbles and the countdown digits are the ROMs' own:
 * Sonic 1's Chopper (Nem_Chopper, Map_Chop) in Green Hill's palette; Jaws (Nem_Jaws, Map_Jaws,
 * palette line 1) and the Labyrinth bubbles (Nem_Bubbles, Map_Bub) under Labyrinth's water palette
 * (Pal_LZWater); Labyrinth's splash (Nem_Splash, Map_Splash) in Pal_LZ; and S3K's Jawz and
 * Blastoid (ArtKosM_Jawz/Map_Jawz, ArtKosM_Blastoid/Map_Blastoid, line 1) under Hydrocity's
 * (Pal_HCZ1_Water). The fish themselves are original, the one kind of creature the design lets
 * us draw: small pictures in Mega Drive colours. Built when fishing is installed, never in draw.
 */
public final class FishArt {
    /** Map_Bub: bubbles growing (0-6, 6 the full 32-pixel bubble), bursting (7-8), digits 5..1 (14-18). */
    public static final int BUBBLE_FULL = 6;
    public static final int DIGIT_FIVE = 14;

    private static final int PAL_LZ = 0x2400;            // Pal_LZ: lines 2-4 (sonic.lst)
    private static final int PAL_LZ_WATER = 0x2460;      // Pal_LZWater (sonic.lst)
    private static final int PAL_HCZ1_WATER = 0x0A8E5C;  // Pal_HCZ1_Water (sonic3k.lst)

    public final SceneSpriteSet chopper;
    public final SceneSpriteSet jaws;
    public final SceneSpriteSet jawz;
    public final SceneSpriteSet blastoid;
    public final SceneSpriteSet bubbles;
    /** Labyrinth's water splash (Nem_Splash, Map_Splash, palette line 2): 0 small, 1-2 big. */
    public final SceneSpriteSet splash;
    /** The Red Chopper: the Chopper's frames in deeper reds (drawn at twice the size). */
    private final SceneImage[] redChopper = new SceneImage[2];
    private final Map<String, SceneImage> pictures = new HashMap<>();
    private final Map<String, SceneImage> icons = new HashMap<>();

    public FishArt(Art art) {
        chopper = load(art.s1, RomSpriteRequest.of(0x37016, Compression.NEMESIS, 0xB254, 0), art.ghzPalette);
        int[] lzWater = palette(art.s1, PAL_LZ_WATER, art.ghzPalette);
        jaws = load(art.s1, RomSpriteRequest.of(0x3727E, Compression.NEMESIS, 0xB2FA, 1), lzWater);
        bubbles = load(art.s1, RomSpriteRequest.of(0x30EE8, Compression.NEMESIS, 0x130A0, 0), lzWater);
        int[] lz = art.ghzPalette.clone();
        try {
            System.arraycopy(art.s1.palette(PAL_LZ, 48), 0, lz, 16, 48);
        } catch (RuntimeException e) {
            // keep Green Hill's colours
        }
        splash = load(art.s1, RomSpriteRequest.of(0x3040A, Compression.NEMESIS, 0x14D34, 2), lz);
        int[] hczWater = palette(art.s3k, PAL_HCZ1_WATER, art.aizPalette);
        jawz = load(art.s3k, RomSpriteRequest.of(0x36A552, Compression.KOSINSKI_MODULED, 0x361364, 1), hczWater);
        blastoid = load(art.s3k, RomSpriteRequest.of(0x36A7C6, Compression.KOSINSKI_MODULED, 0x360DD0, 1), hczWater);
        for (int i = 0; i < redChopper.length; i++) {
            redChopper[i] = chopper != null && i < chopper.frameCount() ? crimson(chopper.frame(i).image()) : null;
        }
        for (String id : new String[] {"spring_minnow", "checker_perch", "bubble_bass", "ring_carp", "green_sunfish",
                "drizzle_trout", "snow_smelt", "scrap_sucker", "emerald_koi", "loop_pike", "spindash_shad",
                "marble_catfish", "starlight_eel", "ice_cap_char", "labyrinth_gar", "aurora_angelfish"}) {
            pictures.put(id, fish(id));
        }
        pictures.put(Fishing.HAT, ItemIcons.picture(hat(), 0xFFDB2400, 0xFF6D0000));
        for (String key : new String[] {"chopper", "jaws", "jawz", "blastoid", "red_chopper"}) {
            SceneImage frame = badnikFrame(key, 0);
            if (frame != null) {
                icons.put(key, fit(frame));
            }
        }
    }

    private static int[] palette(SceneRomArt rom, int address, int[] fallback) {
        try {
            return rom.palette(address, 64);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static SceneSpriteSet load(SceneRomArt rom, RomSpriteRequest request, int[] palette) {
        try {
            return rom.sprites(request, palette);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The icon source for fishing's items (null for items it does not own). */
    public SceneImage icon(Item item, FishTable table) {
        FishDef def = table.get(item.id());
        if (def != null && def.isBadnik()) {
            return icons.get(def.badnik());
        }
        return pictures.get(item.id());
    }

    /** A fish's picture (also the catch shown in the Bubble Bar and held up after landing). */
    public SceneImage picture(String id) {
        return pictures.get(id);
    }

    /** A badnik's animation frame image, or null when its art is missing. */
    public SceneImage badnikFrame(String key, int frame) {
        if (key.equals("red_chopper")) {
            return redChopper[frame % 2];
        }
        SceneSpriteSet set = switch (key) {
            case "chopper" -> chopper;
            case "jaws" -> jaws;
            case "jawz" -> jawz;
            default -> blastoid;
        };
        if (set == null || set.frameCount() == 0) {
            return null;
        }
        return set.frame(frame % set.frameCount()).image();
    }

    /** The badnik's frames to loop: Ani_Chop's fast mouth, Ani_Jaws's four, Jawz's two, the Blastoid's firing frame. */
    public static int frameAt(String key, long ticks, int firing) {
        return switch (key) {
            case "jaws" -> (int) (ticks / 7 % 4);
            case "blastoid" -> firing;
            case "jawz" -> (int) (ticks / 4 % 2);
            default -> (int) (ticks / 4 % 2);
        };
    }

    public SceneSprite bubble(int frame) {
        return bubbles == null || frame >= bubbles.frameCount() ? null : bubbles.frame(frame);
    }

    /** The Chopper drawn in crimson: every coloured pixel turned to a darker, deeper red. */
    private static SceneImage crimson(SceneImage image) {
        int[] px = image.pixels();
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            if (c >>> 24 == 0) {
                continue;
            }
            int r = c >>> 16 & 255, g = c >>> 8 & 255, b = c & 255;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            if (max - min < 40) {
                continue;                       // whites, greys and black stay (teeth, eye, outline)
            }
            int v = (r + g + b) / 3;
            px[i] = Tone.genesis(0xFF000000 | Math.min(255, v * 2) << 16 | v / 4 << 8 | v / 5);
        }
        return new SceneImage(image.width(), image.height(), px);
    }

    /** A picture shrunk to 16 pixels by dropping evenly spaced rows and columns, after trimming. */
    private static SceneImage fit(SceneImage image) {
        int x0 = image.width(), y0 = image.height(), x1 = -1, y1 = -1;
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                if (image.pixel(x, y) >>> 24 != 0) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        if (x1 < x0) {
            return image;
        }
        SceneImage trimmed = image.crop(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
        int big = Math.max(trimmed.width(), trimmed.height());
        if (big <= 16) {
            return trimmed;
        }
        int w = Math.max(1, trimmed.width() * 16 / big), h = Math.max(1, trimmed.height() * 16 / big);
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = trimmed.pixel(x * trimmed.width() / w, y * trimmed.height() / h);
            }
        }
        return new SceneImage(w, h, px);
    }

    // ------------------------------------------------------------------ the fish (original art)

    private static SceneImage fish(String id) {
        return switch (id) {
            case "spring_minnow" -> ItemIcons.picture(small(false), 0xFFB6B6DB, 0xFF6D6D92);
            case "snow_smelt" -> ItemIcons.picture(small(false), 0xFFDBFFFF, 0xFF92B6DB);
            case "scrap_sucker" -> ItemIcons.picture(small(true), 0xFFB6B6B6, 0xFF926D49);
            case "checker_perch" -> ItemIcons.picture(round("checker"), 0xFFDBB66D, 0xFF924900);
            case "bubble_bass" -> ItemIcons.picture(round("bubbles"), 0xFF6DB649, 0xFF246D24);
            case "ring_carp" -> ItemIcons.picture(round("ring"), 0xFFFF9249, 0xFFB64900);
            case "green_sunfish" -> ItemIcons.picture(round("plain"), 0xFFDBFF49, 0xFF6DB600);
            case "emerald_koi" -> ItemIcons.picture(round("patches"), 0xFF49FF92, 0xFF00926D);
            case "drizzle_trout" -> ItemIcons.picture(longFish("spots"), 0xFF92B6FF, 0xFF4949B6);
            case "loop_pike" -> ItemIcons.picture(longFish("stripes"), 0xFF92DB49, 0xFF496D24);
            case "spindash_shad" -> ItemIcons.picture(longFish("streak"), 0xFF2492FF, 0xFF0024B6);
            case "marble_catfish" -> ItemIcons.picture(catfish(), 0xFFB692DB, 0xFF6D4992);
            case "starlight_eel" -> ItemIcons.picture(longFish("stars"), 0xFF4949B6, 0xFF242449);
            case "ice_cap_char" -> ItemIcons.picture(longFish("belly"), 0xFFB6DBFF, 0xFFDB6D92);
            case "labyrinth_gar" -> ItemIcons.picture(gar(), 0xFF49B6B6, 0xFF246D6D);
            default -> ItemIcons.picture(angelfish(), 0xFF49FFDB, 0xFF2492DB);
        };
    }

    private static String[] small(boolean rusty) {
        String o = rusty ? "O" : "F";
        return new String[] {
            "......kkkk......",
            "....kkFFFFkk..kk",
            "...kFF" + o + "FFFFFkkFk",
            "..kwkFFFFF" + o + "FFFfk",
            "...kffFFFFFfkkfk",
            "....kkffffkk..kk",
            "......kkkk......",
        };
    }

    private static String[] round(String pattern) {
        String[] rows = {
            "......kkkk......",
            "....kkFFFFkk....",
            "...kFFFFFFFFk.kk",
            "..kFFFFFFFFFFkfk",
            ".kwkFFFFFFFFFFfk",
            ".kFFFFFFFFFFFFfk",
            "..kfFFFFFFFFfkfk",
            "...kfffffffk.kk.",
            "....kkkkkkkk....",
        };
        switch (pattern) {
            case "ring" -> {
                rows[3] = "..kFFFFFyyFFFkfk";
                rows[4] = ".kwkFFFyFFyFFFfk";
                rows[5] = ".kFFFFFFyyFFFFfk";
            }
            case "checker" -> {
                rows[3] = "..kFfFfFfFfFFkfk";
                rows[4] = ".kwkfFfFfFfFFFfk";
                rows[5] = ".kFFFfFfFfFfFFfk";
            }
            case "patches" -> {
                rows[2] = "...kFFwwFFFFk.kk";
                rows[4] = ".kwkFFFFFwwFFFfk";
                rows[5] = ".kFFFFFFwwwFFFfk";
            }
            case "bubbles" -> {
                rows[0] = "...Y..kkkk......";
                rows[1] = ".Y..kkFFFFkk....";
            }
            default -> {
            }
        }
        return rows;
    }

    private static String[] longFish(String pattern) {
        String[] rows = {
            "...kkkkkkkk.....",
            ".kkFFFFFFFFkk.kk",
            "kwkFFFFFFFFFFkFk",
            "kFFFFFFFFFFFFFfk",
            ".kkfffffffffkkkk",
            "...kkkkkkkkk....",
        };
        switch (pattern) {
            case "spots" -> {
                rows[1] = ".kkFFfFFFfFFkk.kk".substring(0, 16);
                rows[3] = "kFFFFfFFFFfFFFfk";
            }
            case "stripes" -> {
                rows[1] = ".kkFfFFfFFfFkk.kk".substring(0, 16);
                rows[2] = "kwkFfFFfFFfFFkFk";
                rows[3] = "kFFFfFFfFFfFFFfk";
            }
            case "streak" -> rows[2] = "kwkFwwwwwwwFFkFk";
            case "stars" -> {
                rows[1] = ".kkFFyFFFFyFkk.kk".substring(0, 16);
                rows[3] = "kFFFFFFyFFFFFFfk";
            }
            default -> {
            }
        }
        return rows;
    }

    private static String[] catfish() {
        return new String[] {
            "k...kkkkkkkk....",
            ".k.kFFFFFFFFkk.k",
            "..kwkFFFFFFFFFkF",
            "kkkFFFFFFFFFFFFk",
            "..kffffffffffkkk",
            ".k.kkkkkkkkkk...",
            "k...............",
        };
    }

    private static String[] gar() {
        return new String[] {
            ".....kkkkkkkk...",
            "...kkFFFFFFFFk.k",
            "kkkwkFfFfFfFFFkF",
            ".kkFFFFFFFFFFFfk",
            "...kkfffffffkkkk",
            ".....kkkkkkk....",
        };
    }

    private static String[] angelfish() {
        return new String[] {
            ".....kk.....",
            "....kFFk....",
            "...kFfFFk...",
            "..kFFfFFFk.k",
            ".kwkFfFFFFkF",
            "kFFFFfFFFFFk",
            ".kFFFfFFFFkF",
            "..kFFfFFFk.k",
            "...kFfFFk...",
            "....kFFk....",
            ".....kk.....",
        };
    }

    private static String[] hat() {
        return new String[] {
            "......kkkk..Yw..",
            ".....kFFFFk.wY..",
            "....kFFFFFFkY...",
            "....kffffffk....",
            "..kkFFFFFFFFkk..",
            ".kFFFFFFFFFFFFk.",
            "..kkkkkkkkkkkk..",
        };
    }
}
