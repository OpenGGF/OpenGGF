package starpost.orchard;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import java.util.HashMap;
import java.util.Map;
import starpost.art.Art;
import starpost.art.ItemIcons;
import starpost.art.Tone;
import starpost.core.Item;

/**
 * The trees' and sneakers' pictures, from the ROMs:
 * <ul>
 *   <li>The <b>Green Hill Palm</b> is Sonic 1's own palm from Green Hill's block 1 (the crown at
 *       x 96-170, its trunk's 8-pixel segment repeated): grown at the ROM's height, young with a
 *       short trunk, and as a sapling the little star-leaved plant beside it (x 64-94).</li>
 *   <li>The <b>Ring Fruit Tree</b> is Mushroom Hill's (Sonic 3 &amp; Knuckles zone 7 act 1): a
 *       crown of block 135's leaf clump, mirrored so both sides are round, on a slice of block 37's
 *       bark; younger trees use smaller pieces of the same clump. The rings that grow on it are the
 *       S3K ring sprite.</li>
 *   <li>The <b>Chaos Cherry</b> is the same clump in three puffs, recoloured to blossom pink.</li>
 * </ul>
 * Only the fruit (coconuts, cherries) is original, as the design allows for fruit icons. Pictures
 * are built when first needed and recoloured per season like the terrain.
 */
public final class TreeArt {
    private final Art art;
    private SceneImage clump;
    private SceneImage bark;
    private SceneImage palmCrown;
    private SceneImage palmTrunk;
    private SceneImage palmSprout;
    private boolean built;
    private com.openggf.mods.scene.SceneSpriteSet splash;
    private boolean splashTried;
    private final Map<String, SceneImage> pictures = new HashMap<>();
    private final Map<String, SceneImage> icons = new HashMap<>();

    TreeArt(Art art) {
        this.art = art;
    }

    public static String name(String kind) {
        return switch (kind) {
            case Orchard.PALM -> "GREEN HILL PALM";
            case Orchard.RING_FRUIT -> "RING FRUIT TREE";
            default -> "CHAOS CHERRY";
        };
    }

    private void build() {
        if (built) {
            return;
        }
        built = true;
        SceneImage ghz = art.kit.blockImage(1);
        palmCrown = ghz.crop(96, 0, 76, 46);
        palmTrunk = ghz.crop(126, 46, 12, 8);
        palmSprout = trim(ghz.crop(62, 102, 34, 25));
        try {
            SceneLevelKit mhz = art.s3k.levelKit(7, 0);              // Mushroom Hill act 1
            if (mhz != null) {
                clump = mhz.blockImage(135).crop(0, 61, 64, 56);
                bark = mhz.blockImage(37).crop(80, 72, 32, 56);
            }
        } catch (RuntimeException e) {
            clump = null;
        }
    }

    // ------------------------------------------------------------------ the pictures

    /** A tree's picture at a growth stage, in a season's colours. */
    public SceneImage picture(String kind, int stage, Tone tone) {
        String key = kind + ":" + stage + ":" + tone.season();
        SceneImage image = pictures.get(key);
        if (image == null) {
            build();
            image = tone.apply(raw(kind, stage));
            pictures.put(key, image);
        }
        return image;
    }

    private SceneImage raw(String kind, int stage) {
        if (kind.equals(Orchard.PALM) || clump == null || bark == null) {
            return switch (stage) {
                case 0 -> palmSprout;
                case 1 -> palm(3);
                default -> palm(7);
            };
        }
        boolean cherry = kind.equals(Orchard.CHAOS_CHERRY);
        SceneImage out = switch (stage) {
            case 0 -> sapling();
            case 1 -> young();
            default -> cherry ? blossom() : dome();
        };
        return cherry ? pink(out) : out;
    }

    /** The palm's crown over {@code segments} of its trunk (Green Hill's trunk repeats every 8 rows). */
    private SceneImage palm(int segments) {
        int w = palmCrown.width(), h = palmCrown.height() + segments * 8;
        Pic p = new Pic(w, h);
        int tx = 126 - 96;
        for (int i = 0; i < segments; i++) {
            p.blit(palmTrunk, 0, 0, 12, 8, tx, palmCrown.height() + i * 8, false);
        }
        p.blit(palmCrown, 0, 0, w, palmCrown.height(), 0, 0, false);
        return trim(p.image());
    }

    /** Mushroom Hill's clump's round right half and its mirror: a whole crown {@code 2 * half} wide. */
    private void crown(Pic p, int srcX, int srcY, int half, int h, int x, int y) {
        p.blit(clump, srcX, srcY, half, h, x + half, y, false);
        p.blit(clump, srcX, srcY, half, h, x, y, true);
    }

    /** The grown Ring Fruit Tree: a 64-pixel crown on a slice of bark. */
    private SceneImage dome() {
        Pic p = new Pic(64, 90);
        p.blit(bark, 10, 0, 12, 50, 26, 40, false);
        crown(p, 32, 0, 32, 56, 0, 0);
        return trim(p.image());
    }

    /** A young tree: the clump's upper right corner and its mirror on a thinner trunk. */
    private SceneImage young() {
        Pic p = new Pic(48, 60);
        p.blit(bark, 12, 0, 8, 30, 20, 30, false);
        crown(p, 40, 0, 24, 39, 0, 0);
        return trim(p.image());
    }

    private SceneImage sapling() {
        Pic p = new Pic(36, 30);
        p.blit(bark, 14, 0, 4, 14, 16, 16, false);
        crown(p, 46, 0, 18, 18, 0, 0);
        return trim(p.image());
    }

    /** The grown Chaos Cherry: three puffs of blossom over a forked trunk. */
    private SceneImage blossom() {
        Pic p = new Pic(84, 96);
        p.blit(bark, 10, 0, 12, 46, 36, 50, false);
        crown(p, 40, 0, 24, 39, 0, 26);
        crown(p, 40, 0, 24, 39, 36, 26);
        crown(p, 40, 0, 24, 39, 18, 0);
        return trim(p.image());
    }

    /**
     * Green leaves to blossom: the leaf's brightness ramp onto five pinks from near-white to deep
     * rose (Mega Drive levels), so the clump keeps its light and shade; bark is kept.
     */
    private static SceneImage pink(SceneImage image) {
        int[] px = image.pixels();
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            if (c >>> 24 == 0) {
                continue;
            }
            int r = c >>> 16 & 255, g = c >>> 8 & 255, b = c & 255;
            if (g > r && g >= b || r > 150 && g > 150 && b < 100) {
                int v = Math.max(r, g);
                px[i] = r > 150 && g > 150 ? 0xFFFFDBDB          // the leaves' yellow glints: petals in the sun
                        : v >= 200 ? 0xFFFFB6DB
                        : v >= 150 ? 0xFFFF6DB6
                        : v >= 100 ? 0xFFDB4992
                        : 0xFF92246D;
            }
        }
        return new SceneImage(image.width(), image.height(), px);
    }

    // ------------------------------------------------------------------ drawing a tree

    /** Draws a tree standing at ({@code x}, {@code feet}) with what hangs on it. Must not change state. */
    public void draw(SceneCanvas canvas, Art.Seasonal look, SceneDraw style, Orchard.Tree tree, float x, float feet,
            long ticks, int season) {
        SceneImage image = picture(tree.kind, tree.stage(), look.tone);
        float left = x - image.width() / 2f, top = feet - image.height() + 1;
        canvas.fill(Math.round(x) - image.width() / 4, Math.round(feet) - 2, image.width() / 2, 4, 0x50000000);
        canvas.draw(image, left, top, style);
        if (!tree.grown() || tree.fruit <= 0) {
            return;
        }
        switch (tree.kind) {
            case Orchard.PALM -> {
                // Coconuts cluster under the fronds, round the top of the trunk.
                int[][] spots = {{-7, 46}, {5, 47}, {-1, 51}, {-11, 52}, {9, 53}};
                for (int i = 0; i < Math.min(tree.fruit, spots.length); i++) {
                    coconut(canvas, x + spots[i][0], top + spots[i][1]);
                }
            }
            case Orchard.RING_FRUIT -> {
                var ring = art.ring;
                if (ring == null || ring.frameCount() < 4) {
                    return;
                }
                int[][] spots = {{-18, 18}, {10, 14}, {-4, 30}, {16, 32}, {-22, 36}, {2, 6}};
                int shown = Math.min(spots.length, (tree.fruit + 4) / 5);
                for (int i = 0; i < shown; i++) {
                    int frame = (int) ((ticks / 8 + i) % 4);
                    canvas.draw(ring.frame(frame), x + spots[i][0] - 8, top + spots[i][1] - 8, SceneDraw.plain());
                }
            }
            default -> {
                // Cherries hang from the blossom's lower edge, each glinting through the emeralds' colours.
                int[][] spots = {{-24, 56}, {20, 58}, {-6, 62}, {32, 50}};
                for (int i = 0; i < Math.min(tree.fruit, spots.length); i++) {
                    cherry(canvas, x + spots[i][0], top + spots[i][1], emerald((int) (ticks / 20 + i * 2)));
                }
            }
        }
    }

    /** One of the seven Chaos Emeralds' colours (original fruit colouring). */
    private static int emerald(int i) {
        return switch (Math.floorMod(i, 7)) {
            case 0 -> 0xFF24DB24;
            case 1 -> 0xFFDB2424;
            case 2 -> 0xFF2449FF;
            case 3 -> 0xFFDBDB24;
            case 4 -> 0xFF24DBDB;
            case 5 -> 0xFFDB24DB;
            default -> 0xFFDBDBDB;
        };
    }

    /** A coconut: an original fruit picture, 7 pixels round. */
    private static void coconut(SceneCanvas canvas, float x, float y) {
        int cx = Math.round(x), cy = Math.round(y);
        canvas.fill(cx - 2, cy - 4, 5, 1, 0xFF240000);
        canvas.fill(cx - 3, cy - 3, 7, 6, 0xFF240000);
        canvas.fill(cx - 2, cy + 3, 5, 1, 0xFF240000);
        canvas.fill(cx - 2, cy - 3, 5, 6, 0xFF924900);
        canvas.fill(cx - 3, cy - 2, 7, 4, 0xFF924900);
        canvas.fill(cx - 1, cy - 2, 2, 1, 0xFFDB9249);
        canvas.fill(cx + 1, cy + 1, 2, 1, 0xFF6D2400);
    }

    /** One cherry on its stalk: an original fruit picture, 6 pixels round. */
    private static void cherry(SceneCanvas canvas, float x, float y, int colour) {
        int cx = Math.round(x), cy = Math.round(y);
        canvas.fill(cx, cy - 7, 1, 4, 0xFF244900);
        canvas.fill(cx + 1, cy - 8, 2, 1, 0xFF244900);
        canvas.fill(cx - 2, cy - 3, 5, 1, 0xFF240000);
        canvas.fill(cx - 3, cy - 2, 7, 4, 0xFF240000);
        canvas.fill(cx - 2, cy + 2, 5, 1, 0xFF240000);
        canvas.fill(cx - 2, cy - 2, 5, 4, colour);
        canvas.fill(cx - 1, cy - 1, 1, 1, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ icons

    /** Icons for the orchard's items (saplings, the cherry, the sneaker tiers); null for others. */
    public SceneImage icon(Item item) {
        String id = item.id();
        if (!Orchard.isTree(id) && !id.equals(Orchard.CHERRY) && !id.endsWith("sneakers") && !id.equals("speed_shoes")) {
            return null;
        }
        return icons.computeIfAbsent(id, k -> buildIcon(k));
    }

    private SceneImage buildIcon(String id) {
        if (Orchard.isTree(id)) {
            return fit(picture(id, 1, new Tone(Tone.SPRING)));
        }
        if (id.equals(Orchard.CHERRY)) {
            return ItemIcons.picture(new String[] {
                "................",
                ".........ff.....",
                "........f..f....",
                ".......f....f...",
                "......f......f..",
                ".....f.......f..",
                "..kkkkk...kkkkk.",
                ".kFFFFFk.kFFFFFk",
                ".kFwFFFk.kFwFFFk",
                ".kFFFFFk.kFFFFFk",
                ".kFFFFFk.kFFFFFk",
                "..kkkkk...kkkkk.",
            }, 0xFFDB2449, 0xFF244900);
        }
        // Sneakers: the S3K Speed Shoes monitor's screen (Map_Monitor frame 5), coloured by tier.
        var monitor = art.monitor;
        if (monitor == null || monitor.frameCount() <= 5) {
            return null;
        }
        SceneImage screen = monitor.frame(5).image();
        screen = screen.crop(Math.max(0, (screen.width() - 16) / 2), Math.min(3, Math.max(0, screen.height() - 14)),
                Math.min(16, screen.width()), Math.min(14, screen.height()));
        return switch (id) {
            case "sneakers" -> recolour(screen, 0xFF929292);
            case "speed_shoes" -> recolour(screen, 0xFF2449FF);
            case "chaos_sneakers" -> recolour(screen, 0xFF24DB49);
            default -> screen;                                       // Power Sneakers: the ROM's red
        };
    }

    /** The shoes' reds in another colour (the screen's frame and whites are kept). */
    private static SceneImage recolour(SceneImage image, int to) {
        int[] px = image.pixels();
        int tr = to >>> 16 & 255, tg = to >>> 8 & 255, tb = to & 255;
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = c >>> 16 & 255, g = c >>> 8 & 255, b = c & 255;
            if (c >>> 24 != 0 && r > 100 && r > g + 60 && r > b + 60) {
                float k = r / 255f;
                px[i] = Tone.genesis(0xFF000000 | Math.round(tr * k) << 16 | Math.round(tg * k) << 8 | Math.round(tb * k));
            }
        }
        return new SceneImage(image.width(), image.height(), px);
    }

    /** A picture shrunk to fit 16 pixels by dropping evenly spaced rows and columns. */
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

    /** The picture cut to its opaque pixels. */
    static SceneImage trim(SceneImage image) {
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
        return x1 < 0 ? new SceneImage(1, 1, new int[1]) : image.crop(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    /** A pixel buffer that ROM pieces are copied into (opaque pixels only), optionally mirrored. */
    static final class Pic {
        final int w;
        final int h;
        final int[] px;

        Pic(int w, int h) {
            this.w = w;
            this.h = h;
            px = new int[w * h];
        }

        void blit(SceneImage src, int sx, int sy, int sw, int sh, int dx, int dy, boolean mirror) {
            for (int y = 0; y < sh; y++) {
                for (int x = 0; x < sw; x++) {
                    int c = src.pixel(sx + (mirror ? sw - 1 - x : x), sy + y);
                    int tx = dx + x, ty = dy + y;
                    if (c >>> 24 != 0 && tx >= 0 && ty >= 0 && tx < w && ty < h) {
                        px[ty * w + tx] = c;
                    }
                }
            }
        }

        SceneImage image() {
            return new SceneImage(w, h, px);
        }
    }

    /**
     * Labyrinth's water splash (Nem_Splash, Map_Splash, palette line 2 of Pal_LZ; sonic.lst), for
     * the spray at a Chaos Sneakers farmer's feet on the pond; null when it cannot be decoded.
     */
    public com.openggf.mods.scene.SceneSpriteSet splash() {
        if (splash == null && !splashTried) {
            splashTried = true;
            try {
                int[] palette = art.ghzPalette.clone();
                System.arraycopy(art.s1.palette(0x2400, 48), 0, palette, 16, 48);   // Pal_LZ: lines 2-4
                splash = art.s1.sprites(com.openggf.mods.scene.RomSpriteRequest.of(0x3040A,
                        com.openggf.mods.scene.RomSpriteRequest.Compression.NEMESIS, 0x14D34, 2), palette);
            } catch (RuntimeException e) {
                splash = null;
            }
        }
        return splash;
    }
}
