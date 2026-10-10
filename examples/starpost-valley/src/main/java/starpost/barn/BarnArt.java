package starpost.barn;

import com.openggf.mods.scene.SceneImage;
import java.util.HashMap;
import java.util.Map;
import starpost.art.Art;
import starpost.art.ItemIcons;
import starpost.art.Tone;
import starpost.core.Item;
import starpost.core.Kind;

/**
 * The barn's pictures. The Cucky Coop, the Pocky Pen, the machines and the Flicky Roost are
 * assembled from Green Hill's own pixels as {@code Facades} does the town: checkered soil walls,
 * the grass lip as a sod roof, the bridge log as beams and fences, block 6's dark planks for
 * doors. The animals and Flickies on and around them are the ROMs' sprites, drawn by the actors.
 * Item icons for the goods are small original pictures (eggs, fluff, jars, bottles), the kind of
 * creator art the design allows. Built when the barn is installed (per season), never in draw.
 */
final class BarnArt {
    private static final int OUTLINE = 0xFF240000;

    private final SceneImage soil;
    private final SceneImage grass;
    private final SceneImage log;
    private final SceneImage planks;
    final SceneImage coop;
    final SceneImage coopBig;
    final SceneImage pen;
    final SceneImage penBig;
    final SceneImage penFence;
    final SceneImage keg;
    final SceneImage loom;
    final SceneImage press;
    final SceneImage roost;
    final SceneImage heart;
    final SceneImage heartEmpty;
    private final Map<String, SceneImage> icons = new HashMap<>();

    BarnArt(Art art, Tone tone) {
        SceneImage flat = art.kit.blockImage(60);
        soil = flat.crop(0, Art.FLOOR + 40, 32, 16);
        grass = flat.crop(0, Art.FLOOR - 4, 256, 24);
        log = art.kit.blockImage(51).crop(0, 129, 256, 12);
        planks = art.kit.blockImage(6).crop(64, 128, 64, 64);
        coop = tone.apply(coop(96));
        coopBig = tone.apply(coop(150));
        pen = tone.apply(shelter(112));
        penBig = tone.apply(shelter(168));
        penFence = tone.apply(fence());
        keg = tone.apply(keg());
        loom = tone.apply(loom());
        press = tone.apply(press());
        roost = tone.apply(roost());
        heart = ItemIcons.picture(heartRows(), 0xFFFF2449, 0xFFB60024);
        heartEmpty = ItemIcons.picture(heartRows(), 0xFF492449, 0xFF241024);
        icons.put(Artisan.KEG, keg);
        icons.put(Artisan.LOOM, loom);
        icons.put(Artisan.PRESS, press);
        icons.put(BarnContent.ROOST, roost);
        if (art.monitor != null && art.monitor.frameCount() > 0) {
            icons.put(Artisan.JAR, art.monitor.frame(0).image());
        }
    }

    // ------------------------------------------------------------------ icons

    /** The barn's item icons (null for items it does not own); built pictures are cached. */
    SceneImage icon(Item item) {
        SceneImage cached = icons.get(item.id());
        if (cached != null) {
            return fit(cached);
        }
        SceneImage made = switch (item.id()) {
            case "cucky_egg" -> ItemIcons.picture(egg(), 0xFFFFFFFF, 0xFFDBDBB6);
            case "ice_egg" -> ItemIcons.picture(egg(), 0xFFDBFFFF, 0xFF92B6DB);
            case "pocky_fluff" -> ItemIcons.picture(fluff(), 0xFFFFFFFF, 0xFFDBDBDB);
            case "hill_truffle" -> ItemIcons.picture(truffle(), 0xFF926D49, 0xFF492400);
            case "hill_cloth" -> ItemIcons.picture(cloth(), 0xFFDBB66D, 0xFF924900);
            case "spring_yard_fizz" -> ItemIcons.picture(bottle(), 0xFFFFDB49, 0xFFDB9200);
            case Artisan.OIL -> ItemIcons.picture(bottle(), 0xFFFFFF49, 0xFFDBB600);
            case "truffle_oil" -> ItemIcons.picture(bottle(), 0xFFB6926D, 0xFF6D4924);
            default -> null;
        };
        if (made == null && item.kind() == Kind.ARTISAN && item.id().startsWith("jar_")) {
            int[] c = colours(item.id().substring(4));
            made = ItemIcons.picture(jar(), c[0], c[1]);
        } else if (made == null && item.kind() == Kind.ARTISAN && item.id().startsWith("fizz_")) {
            int[] c = colours(item.id().substring(5));
            made = ItemIcons.picture(bottle(), c[0], c[1]);
        }
        if (made != null) {
            icons.put(item.id(), made);
        }
        return made;
    }

    /** The light and dark colour of what went into a jar or a fizz. */
    static int[] colours(String input) {
        return switch (input) {
            case "palm_bean", "spring_yard_hops", "totem_leek" -> new int[] {0xFF92FF00, 0xFF49B600};
            case "checker_cauliflower", "snow_spud" -> new int[] {0xFFFFFFFF, 0xFFB6B6B6};
            case "spring_tulip", "motobug_tomato", "ruby_berry" -> new int[] {0xFFFF2400, 0xFFB60000};
            case "spin_spud", "palm_coconut" -> new int[] {0xFFDB9249, 0xFF924900};
            case "fire_pepper" -> new int[] {0xFFFF6D00, 0xFFDB2400};
            case "bluesphere_berry", "frost_ring" -> new int[] {0xFF2492FF, 0xFF0024B6};
            case "egg_plant", "marble_grape", "loop_berry" -> new int[] {0xFFB66DFF, 0xFF6D24B6};
            case "totem_choke" -> new int[] {0xFF92DB6D, 0xFF6D24B6};
            case "scrap_amaranth" -> new int[] {0xFFDB2449, 0xFF6D0024};
            case "emerald_melon" -> new int[] {0xFF24DB92, 0xFF00926D};
            case "eggman_pumpkin" -> new int[] {0xFFFF9200, 0xFFB64900};
            default -> new int[] {0xFFFFDB00, 0xFFDB9200};
        };
    }

    private static SceneImage fit(SceneImage image) {
        if (image.width() <= 16 && image.height() <= 16) {
            return image;
        }
        int big = Math.max(image.width(), image.height());
        int w = Math.max(1, image.width() * 16 / big), h = Math.max(1, image.height() * 16 / big);
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = image.pixel(x * image.width() / w, y * image.height() / h);
            }
        }
        return new SceneImage(w, h, px);
    }

    private static String[] egg() {
        return new String[] {
            "......kkkk......",
            ".....kFFFFk.....",
            "....kFFwFFfk....",
            "....kFwFFFfk....",
            "...kFFFFFFFfk...",
            "...kFFFFFFffk...",
            "...kfFFFFfffk...",
            "....kffffffk....",
            ".....kkkkkk.....",
        };
    }

    private static String[] fluff() {
        return new String[] {
            ".....kkk..kkk...",
            "...kkFFFkkFFFk..",
            "..kFFFFFFFFwFFk.",
            ".kFFwFFFFFFFFFfk",
            ".kFFFFFFFFFFfffk",
            "..kfFFFFfFFFffk.",
            "...kkfffkkfffk..",
            ".....kkk..kkk...",
        };
    }

    private static String[] truffle() {
        return new String[] {
            ".....kkkkk......",
            "...kkFFfFFkk....",
            "..kFFfFFFfFFk...",
            ".kFfFFFfFFFfFk..",
            ".kFFFfFFFfFFfk..",
            "..kfFFfFFfFfk...",
            "...kkffffffk....",
            ".....kkkkkk.....",
        };
    }

    private static String[] cloth() {
        return new String[] {
            "..kkkkkkkkkkkk..",
            ".kFFfFFfFFfFFfk.",
            ".kFfFFfFFfFFfFk.",
            ".kffffffffffffk.",
            ".kFFfFFfFFfFFfk.",
            ".kFfFFfFFfFFfFk.",
            "..kkkkkkkkkkkk..",
        };
    }

    private static String[] jar() {
        return new String[] {
            ".....kkkkkk.....",
            ".....kbbbbk.....",
            "....kkkkkkkk....",
            "...kwFFFFFFfk...",
            "...kwFFFFFFfk...",
            "...kFFYYYYFfk...",
            "...kFFYYYYFfk...",
            "...kFFFFFFffk...",
            "...kfFFFFfffk...",
            "....kkkkkkkk....",
        };
    }

    private static String[] bottle() {
        return new String[] {
            "......kkkk......",
            "......kbbk......",
            "......kwFk......",
            ".....kwFFfk.....",
            "....kwFFFFfk....",
            "....kFYYYYfk....",
            "....kFYYYYfk....",
            "....kFFFFffk....",
            "....kfFFfffk....",
            ".....kkkkkk.....",
        };
    }

    private static String[] heartRows() {
        return new String[] {
            ".kk.kk.",
            "kFFkFFk",
            "kFFFFfk",
            ".kFFfk.",
            "..kfk..",
            "...k...",
        };
    }

    // ------------------------------------------------------------------ Green Hill pieces

    /** A pixel buffer with blits from the ROM pieces (as {@code Facades} builds the town). */
    private static final class Pic {
        final int w;
        final int h;
        final int[] px;

        Pic(int w, int h) {
            this.w = w;
            this.h = h;
            px = new int[w * h];
        }

        void set(int x, int y, int argb) {
            if (x >= 0 && y >= 0 && x < w && y < h) {
                px[y * w + x] = argb;
            }
        }

        void tile(SceneImage src, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh) {
            for (int y = 0; y < dh; y++) {
                for (int x = 0; x < dw; x++) {
                    int c = src.pixel(sx + Math.floorMod(x, sw), sy + Math.floorMod(y, sh));
                    if (c >>> 24 != 0) {
                        set(dx + x, dy + y, c);
                    }
                }
            }
        }

        void fill(int x, int y, int fw, int fh, int argb) {
            for (int yy = y; yy < y + fh; yy++) {
                for (int xx = x; xx < x + fw; xx++) {
                    set(xx, yy, argb);
                }
            }
        }

        void shade(int x, int y, int fw, int fh, float factor) {
            for (int yy = Math.max(0, y); yy < Math.min(h, y + fh); yy++) {
                for (int xx = Math.max(0, x); xx < Math.min(w, x + fw); xx++) {
                    int c = px[yy * w + xx];
                    if (c >>> 24 == 0) {
                        continue;
                    }
                    int r = Math.min(255, Math.round((c >>> 16 & 255) * factor));
                    int g = Math.min(255, Math.round((c >>> 8 & 255) * factor));
                    int b = Math.min(255, Math.round((c & 255) * factor));
                    px[yy * w + xx] = Tone.genesis(0xFF000000 | r << 16 | g << 8 | b);
                }
            }
        }

        SceneImage image() {
            return new SceneImage(w, h, px);
        }
    }

    private void walls(Pic p, int x, int y, int w, int h) {
        p.tile(soil, 0, 0, 32, 16, x, y, w, h);
        p.shade(x, y, 3, h, 0.7f);
        p.shade(x + w - 3, y, 3, h, 0.7f);
        p.shade(x, y, w, 4, 0.75f);
    }

    private void sodRoof(Pic p, int x, int y, int w) {
        p.tile(grass, 0, 0, 256, 24, x - 6, y, w + 12, 24);
    }

    private void logBeam(Pic p, int x, int y, int w) {
        p.tile(log, 0, 0, 256, 12, x, y, w, 12);
    }

    /** An upright post: a slice of the log stood on end. */
    private void post(Pic p, int x, int y, int h) {
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < 5; xx++) {
                int c = log.pixel(40 + yy % 24, 2 + xx * 2);
                if (c >>> 24 != 0) {
                    p.set(x + xx, y + yy, c);
                }
            }
        }
        p.fill(x - 1, y, 1, h, OUTLINE);
        p.fill(x + 5, y, 1, h, OUTLINE);
    }

    private void door(Pic p, int x, int y, int w, int h) {
        p.fill(x - 2, y - 2, w + 4, h + 2, OUTLINE);
        p.tile(planks, 0, 0, 64, 64, x, y, w, h);
    }

    /**
     * The Cucky Coop: a hen house on log stilts with a sod roof, a hatch with its ramp and a plank
     * door; the big coop is wider, with a second hatch.
     */
    private SceneImage coop(int width) {
        Pic p = new Pic(width + 16, 78);
        post(p, 14, 52, 26);
        post(p, width - 4, 52, 26);
        walls(p, 8, 28, width, 30);
        sodRoof(p, 8, 10, width);
        door(p, 8 + width / 2 + 10, 38, 16, 20);
        for (int hatch = 0; hatch < (width > 120 ? 2 : 1); hatch++) {
            int hx = 8 + width / 2 - 14 + hatch * 54;
            p.fill(hx, 38, 12, 10, OUTLINE);              // the hatch's dark mouth
            p.fill(hx + 1, 39, 10, 8, 0xFF240000);
            p.fill(hx + 2, 36, 8, 2, OUTLINE);
            for (int i = 0; i < 20; i++) {                // its ramp: a log laid down to the grass
                p.fill(hx - 2 + i, 50 + i * 3 / 5, 3, 3, i % 4 < 2 ? 0xFFB66D24 : 0xFF6D2400);
            }
        }
        logBeam(p, 4, 56, width + 8);                     // the floor beam
        p.fill(18, 34, 14, 10, OUTLINE);                  // a window of shade
        p.fill(19, 35, 12, 8, 0xFF492400);
        return p.image();
    }

    /** The Pocky Pen's shelter: checker walls, a sod roof, a wide plank door and two windows. */
    private SceneImage shelter(int width) {
        Pic p = new Pic(width + 20, 64);
        walls(p, 10, 24, width, 40);
        sodRoof(p, 10, 6, width);
        door(p, 10 + width / 2 - 14, 34, 28, 30);
        for (int wx : new int[] {18, width - 20}) {
            p.fill(wx, 32, 22, 12, OUTLINE);
            p.fill(wx + 1, 33, 20, 10, 0xFF492400);
        }
        return p.image();
    }

    /** The paddock's log fence along the back wall, open in the middle. */
    private SceneImage fence() {
        Pic fence = new Pic(236, 26);
        for (int x = 0; x <= 230; x += 23) {
            if (x < 92 || x > 138) {
                post(fence, x, 0, 26);
            }
        }
        logBeam(fence, 0, 4, 96);
        logBeam(fence, 140, 4, 96);
        logBeam(fence, 0, 15, 96);
        logBeam(fence, 140, 15, 96);
        return fence.image();
    }

    /** The Spring Yard Keg: a checker barrel with log hoops (its spring is drawn on top). */
    private SceneImage keg() {
        Pic p = new Pic(22, 20);
        p.fill(1, 0, 20, 20, OUTLINE);
        p.tile(soil, 0, 0, 32, 16, 2, 1, 18, 18);
        p.shade(2, 1, 4, 18, 0.7f);
        p.shade(16, 1, 4, 18, 0.7f);
        p.tile(log, 0, 3, 256, 4, 0, 3, 22, 3);
        p.tile(log, 0, 3, 256, 4, 0, 13, 22, 3);
        p.fill(9, 8, 4, 4, OUTLINE);
        p.fill(10, 9, 2, 2, 0xFFDB9249);
        return p.image();
    }

    /** The Fluff Loom: two posts and a beam, the cloth on its roller below. */
    private SceneImage loom() {
        Pic p = new Pic(24, 26);
        post(p, 1, 0, 26);
        post(p, 18, 0, 26);
        logBeam(p, 0, 0, 24);
        p.fill(6, 14, 12, 8, OUTLINE);
        p.fill(7, 15, 10, 6, 0xFFDBB66D);
        p.fill(7, 17, 10, 1, 0xFF924900);
        p.fill(7, 19, 10, 1, 0xFF924900);
        return p.image();
    }

    /** The Sunflower Press: a checker block frame with a log screw and a jar beneath. */
    private SceneImage press() {
        Pic p = new Pic(24, 26);
        walls(p, 0, 0, 24, 6);
        post(p, 1, 4, 22);
        post(p, 18, 4, 22);
        p.fill(10, 6, 4, 10, OUTLINE);
        p.fill(11, 6, 2, 10, 0xFFB66D24);
        p.tile(soil, 0, 0, 32, 16, 7, 14, 10, 4);          // the press plate
        p.fill(8, 20, 8, 6, OUTLINE);
        p.fill(9, 21, 6, 5, 0xFFFFDB49);
        return p.image();
    }

    /** The Flicky Roost: a little checker house under a sod roof, on a log pole, a basket at its foot. */
    private SceneImage roost() {
        Pic p = new Pic(30, 60);
        post(p, 12, 30, 30);
        walls(p, 4, 16, 22, 16);
        sodRoof(p, 6, 0, 18);
        p.fill(11, 21, 8, 7, OUTLINE);                     // the round door
        p.fill(12, 22, 6, 6, 0xFF240000);
        logBeam(p, 2, 31, 26);                             // the perch under the house
        p.tile(planks, 0, 0, 64, 64, 4, 51, 22, 9);        // the basket
        p.fill(3, 50, 24, 1, OUTLINE);
        p.fill(3, 59, 24, 1, OUTLINE);
        p.fill(3, 50, 1, 10, OUTLINE);
        p.fill(26, 50, 1, 10, OUTLINE);
        return p.image();
    }
}
