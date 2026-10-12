package starpost.museum;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.HashMap;
import java.util.Map;
import starpost.art.Art;
import starpost.art.ItemIcons;
import starpost.art.Tone;
import starpost.core.Item;

/**
 * The museum's pictures, from the ROMs. Badniks are their Sonic 1 objects' first frames in their
 * zone's palette (sonic.lst: art, mappings and Pal_* addresses), the S3K Jawz and Blastoid in
 * Hydrocity's water palette (sonic3k.lst); the Red Chopper is the Chopper in deeper reds. Relics
 * are crops of ROM pictures: Green Hill's totem pole (block 56), the S3K ring, Star Post ball,
 * yellow spring and broken monitor, Sonic 1's giant ring, signpost plate and lamppost globe. The
 * annex is assembled from Green Hill's pixels like the town ({@code art.Facades}), with the totem
 * pole for columns. Only the Records' discs are original (UI icons).
 */
final class MuseumArt {
    // Sonic 1 palettes (sonic.lst): zone lines 2-4.
    private static final int PAL_MZ = 0x24E0;
    private static final int PAL_LZ = 0x2400;
    private static final int PAL_SBZ1 = 0x2600;
    private static final int PAL_HCZ1_WATER = 0x0A8E5C;  // Pal_HCZ1_Water (sonic3k.lst)

    private final Art art;
    private final Map<String, SceneSpriteSet> badniks = new HashMap<>();
    private final Map<String, SceneImage> pictures = new HashMap<>();
    private final Map<String, SceneImage> icons = new HashMap<>();
    private final SceneImage[] annex = new SceneImage[4];
    private SceneImage totem;

    MuseumArt(Art art) {
        this.art = art;
    }

    // ------------------------------------------------------------------ badniks

    /** A badnik's sprite set by key ("motobug", "red_chopper"...), loaded on first use; null when missing. */
    SceneSpriteSet badnik(String key) {
        if (badniks.containsKey(key)) {
            return badniks.get(key);
        }
        SceneSpriteSet set = switch (key) {
            case "motobug" -> art.motobug;
            case "buzz_bomber" -> art.buzzBomber;
            case "caterkiller" -> art.caterkiller;
            case "batbrain" -> load(art.s1, RomSpriteRequest.of(0x386BC, Compression.NEMESIS, 0x108CA, 0), s1(PAL_MZ));    // Nem_Basaran, Map_Bas
            case "yadrin" -> load(art.s1, RomSpriteRequest.of(0x382D4, Compression.NEMESIS, 0xFFBA, 1), s1(PAL_MZ));       // Nem_Yadrin, Map_Yad
            case "jaws" -> load(art.s1, RomSpriteRequest.of(0x3727E, Compression.NEMESIS, 0xB2FA, 1), s1(PAL_LZ));         // Nem_Jaws, Map_Jaws
            case "burrobot" -> load(art.s1, RomSpriteRequest.of(0x3692C, Compression.NEMESIS, 0xB4E6, 0), s1(PAL_LZ));     // Nem_Burrobot, Map_Burro
            case "orbinaut" -> load(art.s1, RomSpriteRequest.of(0x38E98, Compression.NEMESIS, 0x125B8, 0), s1(PAL_LZ));    // Nem_Orbinaut, Map_Orb
            case "bomb" -> load(art.s1, RomSpriteRequest.of(0x38C00, Compression.NEMESIS, 0x122FC, 0), s1(PAL_SBZ1));      // Nem_Bomb, Map_Bomb
            case "ball_hog" -> load(art.s1, RomSpriteRequest.of(0x35AF0, Compression.NEMESIS, 0x94E0, 1), s1(PAL_SBZ1));   // Nem_BallHog, Map_Hog
            case "chopper", "red_chopper" -> load(art.s1, RomSpriteRequest.of(0x37016, Compression.NEMESIS, 0xB254, 0), art.ghzPalette); // Nem_Chopper, Map_Chop
            case "jawz" -> load(art.s3k, RomSpriteRequest.of(0x36A552, Compression.KOSINSKI_MODULED, 0x361364, 1), hcz());
            case "blastoid" -> load(art.s3k, RomSpriteRequest.of(0x36A7C6, Compression.KOSINSKI_MODULED, 0x360DD0, 1), hcz());
            default -> null;
        };
        badniks.put(key, set);
        return set;
    }

    /** Sonic 1's palette with a zone's three lines (Sonic's own line from Green Hill's). */
    private int[] s1(int address) {
        int[] palette = art.ghzPalette.clone();
        try {
            System.arraycopy(art.s1.palette(address, 48), 0, palette, 16, 48);
        } catch (RuntimeException e) {
            // keep Green Hill's colours
        }
        return palette;
    }

    private int[] hcz() {
        try {
            return art.s3k.palette(PAL_HCZ1_WATER, 64);
        } catch (RuntimeException e) {
            return art.aizPalette;
        }
    }

    private static SceneSpriteSet load(SceneRomArt rom, RomSpriteRequest request, int[] palette) {
        try {
            return rom.sprites(request, palette);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The badnik key an exhibit item shows ({@code "part:motobug"}, {@code "badnik:jawz"}), or null. */
    static String badnikKey(Item item) {
        String icon = item.icon();
        if (icon.startsWith(MuseumContent.PART_ICON)) {
            return icon.substring(MuseumContent.PART_ICON.length());
        }
        if (icon.startsWith("badnik:")) {
            return icon.substring("badnik:".length());
        }
        return null;
    }

    /** A badnik's first frame as a picture (the Red Chopper's in deeper reds), or null. */
    SceneImage badnikPicture(String key) {
        String cacheKey = "badnik:" + key;
        if (pictures.containsKey(cacheKey)) {
            return pictures.get(cacheKey);
        }
        SceneSpriteSet set = badnik(key);
        SceneImage image = null;
        if (set != null && set.frameCount() > 0) {
            try {
                image = set.frame(0).image();
                if (key.equals("red_chopper")) {
                    image = crimson(image);
                }
            } catch (RuntimeException e) {
                image = null;
            }
        }
        pictures.put(cacheKey, image);
        return image;
    }

    private static SceneImage crimson(SceneImage image) {
        int[] px = image.pixels();
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = c >>> 16 & 255, g = c >>> 8 & 255, b = c & 255;
            if (c >>> 24 != 0 && b > r && b > g) {
                px[i] = Tone.genesis(0xFF000000 | Math.min(255, b + 40) << 16 | g / 3 << 8 | g / 3);
            }
        }
        return new SceneImage(image.width(), image.height(), px);
    }

    // ------------------------------------------------------------------ relics

    /** The totem pole on Green Hill's block 56's ledge (48x44 at x 208, y 80). */
    private SceneImage totem() {
        if (totem == null) {
            totem = art.kit.blockImage(56).crop(208, 80, 48, 44);
        }
        return totem;
    }

    /** A relic's picture at the ROM's size (null for unknown ids). */
    SceneImage relic(String id) {
        if (pictures.containsKey(id)) {
            return pictures.get(id);
        }
        SceneImage image;
        try {
            image = switch (id) {
                case "totem_chip" -> trim(totem().crop(16, 30, 16, 14));
                case "totem_wing" -> trim(totem().crop(0, 0, 15, 12));
                case "totem_face" -> trim(totem().crop(14, 10, 20, 20));
                case "ring_mould" -> stone(frame(art.ring, 0));
                case "giant_ring_shard" -> trim(frame(art.s1.sprites(RomSpriteRequest.uncompressed(0x6A2E4, 0xC40, 0xA654, 1),
                        art.ghzPalette), 0).crop(32, 24, 32, 40));       // Art_BigRing, Map_GRing (Tile_Pal2)
                case "spring_coil" -> frame(art.spring, 13);
                case "old_monitor" -> frame(art.monitor, 11);
                case "signpost_plate" -> trim(frame(art.signpost, 0).crop(0, 0, 48, 32));
                case "lamppost_globe" -> frame(art.lamppost, 2);
                case MuseumContent.CAP -> frame(art.starpost, 2);
                default -> null;
            };
        } catch (RuntimeException e) {
            image = null;
        }
        pictures.put(id, image);
        return image;
    }

    private static SceneImage frame(SceneSpriteSet set, int frame) {
        return set == null || frame >= set.frameCount() ? null : set.frame(frame).image();
    }

    /** The ring's shape cut in grey stone, its gold left in a glint: a mould. */
    private static SceneImage stone(SceneImage ring) {
        if (ring == null) {
            return null;
        }
        int[] px = ring.pixels();
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            if (c >>> 24 == 0) {
                continue;
            }
            int v = ((c >>> 16 & 255) + (c >>> 8 & 255) + (c & 255)) / 3;
            boolean glint = (c >>> 16 & 255) > 230 && (c >>> 8 & 255) > 200 && i % 5 == 0;
            px[i] = glint ? c : Tone.genesis(0xFF000000 | (v * 3 / 4) << 16 | (v * 3 / 4) << 8 | Math.min(255, v * 4 / 5));
        }
        return new SceneImage(ring.width(), ring.height(), px);
    }

    // ------------------------------------------------------------------ icons

    /** Icons for the museum's items: badnik parts, relics, Records. Null for other items. */
    SceneImage icon(Item item) {
        String icon = item.icon();
        if (!icon.startsWith(MuseumContent.PART_ICON) && !icon.startsWith(MuseumContent.RELIC_ICON)
                && MuseumContent.recordSong(item.id()) == null) {
            return null;
        }
        if (icons.containsKey(item.id())) {
            return icons.get(item.id());
        }
        SceneImage image;
        if (MuseumContent.recordSong(item.id()) != null) {
            image = switch (item.id()) {
                case "record_lava_reef" -> ItemIcons.picture(disc(), 0xFFFF6D00, 0xFFB62400);
                case "record_mini_boss" -> ItemIcons.picture(disc(), 0xFFB66DFF, 0xFF6D24B6);
                default -> ItemIcons.picture(disc(), 0xFFFFDB92, 0xFFB69249);
            };
        } else {
            SceneImage picture = icon.startsWith(MuseumContent.PART_ICON) ? badnikPicture(badnikKey(item)) : relic(item.id());
            image = picture == null ? null : fit(picture);
        }
        icons.put(item.id(), image);
        return image;
    }

    /** A vinyl disc with a label in the Record's colours (original UI art, as the festivals' Records). */
    private static String[] disc() {
        return new String[] {
            "................",
            ".....kkkkkk.....",
            "...kkkkkkkkkk...",
            "..kkmkkkkkkkkk..",
            ".kkmkkkFFkkkkkk.",
            ".kmkkkFFFFkkkkk.",
            "kkkkkFFfwFFkkkkk",
            "kkkkkFFwfFFkkkkk",
            ".kkkkkFFFFkkkmk.",
            ".kkkkkkFFkkkmkk.",
            "..kkkkkkkkkmkk..",
            "...kkkkkkkkkk...",
            ".....kkkkkk.....",
        };
    }

    // ------------------------------------------------------------------ the annex

    /** Where the annex's three display windows are, relative to its top left: {x, y, w, h}. */
    static int[] window(int which) {
        return switch (which) {
            case 0 -> new int[] {21, 62, 16, 16};
            case 1 -> new int[] {63, 62, 16, 16};
            default -> new int[] {42, 40, 16, 12};
        };
    }

    /** The annex in a season's colours (built once a season). */
    SceneImage annex(int season) {
        if (annex[season] == null) {
            annex[season] = new Tone(season).apply(buildAnnex());
        }
        return annex[season];
    }

    /**
     * A little Green Hill museum, 100 pixels wide: checker walls, a pediment of stepped bridge
     * logs over a frieze log, totem-pole columns at the corners, a plank door and three windows of
     * the lake's sparkle (where finished collections show their prizes).
     */
    private SceneImage buildAnnex() {
        SceneImage flat = art.kit.blockImage(60);
        SceneImage soil = flat.crop(0, Art.FLOOR + 40, 32, 16);
        SceneImage log = art.kit.blockImage(51).crop(0, 129, 256, 12);
        SceneImage planks = art.kit.blockImage(6).crop(64, 128, 64, 64);
        SceneImage sky = art.kit.backdrop().image();
        SceneImage water = sky.crop(0, Math.min(sky.height() - 16, 176), 64, 16);
        int w = 100, h = 108;
        int[] px = new int[w * h];
        tile(px, w, h, soil, 8, 38, 84, 70);
        shade(px, w, h, 8, 38, 84, 5, 0.72f);
        for (int i = 0; i < 4; i++) {
            int inset = 6 + i * 12;
            tile(px, w, h, log, inset, 26 - i * 8, w - inset * 2, 12);
        }
        tile(px, w, h, log, 2, 30, w - 4, 12);
        for (int k = 0; k < 3; k++) {
            int[] r = window(k);
            fill(px, w, h, r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4, 0xFF240000);
            tile(px, w, h, water, r[0], r[1], r[2], r[3]);
            if (k < 2) {
                tile(px, w, h, log, r[0] - 3, r[1] + r[3] + 2, r[2] + 6, 6);
            }
        }
        fill(px, w, h, 39, 72, 22, 36, 0xFF240000);
        tile(px, w, h, planks, 41, 74, 18, 34);
        fill(px, w, h, 55, 90, 2, 2, 0xFFFFDB00);
        SceneImage pole = totem().crop(14, 10, 20, 34);
        for (int x : new int[] {0, w - 20}) {
            tile(px, w, h, pole, x, h - 68, 20, 34);
            tile(px, w, h, pole, x, h - 34, 20, 34);
        }
        return new SceneImage(w, h, px);
    }

    private static void tile(int[] px, int w, int h, SceneImage src, int dx, int dy, int dw, int dh) {
        if (src == null) {
            return;
        }
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int c = src.pixel(Math.floorMod(x, src.width()), Math.floorMod(y, src.height()));
                int tx = dx + x, ty = dy + y;
                if (c >>> 24 != 0 && tx >= 0 && ty >= 0 && tx < w && ty < h) {
                    px[ty * w + tx] = c;
                }
            }
        }
    }

    private static void fill(int[] px, int w, int h, int x, int y, int fw, int fh, int argb) {
        for (int yy = Math.max(0, y); yy < Math.min(h, y + fh); yy++) {
            for (int xx = Math.max(0, x); xx < Math.min(w, x + fw); xx++) {
                px[yy * w + xx] = argb;
            }
        }
    }

    private static void shade(int[] px, int w, int h, int x, int y, int fw, int fh, float factor) {
        for (int yy = Math.max(0, y); yy < Math.min(h, y + fh); yy++) {
            for (int xx = Math.max(0, x); xx < Math.min(w, x + fw); xx++) {
                int c = px[yy * w + xx];
                if (c >>> 24 != 0) {
                    int r = Math.round((c >>> 16 & 255) * factor), g = Math.round((c >>> 8 & 255) * factor);
                    int b = Math.round((c & 255) * factor);
                    px[yy * w + xx] = Tone.genesis(0xFF000000 | r << 16 | g << 8 | b);
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    static SceneImage fit(SceneImage image) {
        int size = Math.max(image.width(), image.height());
        if (size <= 16) {
            return image;
        }
        int w = Math.max(1, image.width() * 16 / size), h = Math.max(1, image.height() * 16 / size);
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = image.pixel(x * image.width() / w, y * image.height() / h);
            }
        }
        return new SceneImage(w, h, px);
    }

    static SceneImage trim(SceneImage image) {
        if (image == null) {
            return null;
        }
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
        return x1 < 0 ? image : image.crop(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }
}
