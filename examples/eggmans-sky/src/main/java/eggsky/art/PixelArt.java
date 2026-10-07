package eggsky.art;

import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import eggsky.core.Colour;
import eggsky.core.Rng;

/**
 * Procedural 16-bit sprites: plants, mushrooms, crystals, cacti and coral grown from a seed and
 * a colour, painted as material regions, lit from the upper left, outlined dark and snapped to
 * the Mega Drive palette, so they sit beside ROM art. Each species is one seed: the same planet
 * always grows the same flora.
 */
public final class PixelArt {
    private final int w;
    private final int h;
    /** Material per pixel: 0 empty, else 1-based index into {@link #ramps}. */
    private final byte[] mat;
    /** Shade per pixel: 0 darkest to 4 brightest. */
    private final byte[] shade;
    private final int[][] ramps = new int[6][];
    private int materials;

    private PixelArt(int w, int h) {
        this.w = w;
        this.h = h;
        this.mat = new byte[w * h];
        this.shade = new byte[w * h];
    }

    /** A five-step ramp (outline, shadow, base, light, highlight) around one colour. */
    public static int[] ramp(int base) {
        float[] hsv = Colour.hsv(base);
        int[] r = new int[5];
        r[0] = Colour.genesis(Colour.fromHsv(hsv[0] + 8, Math.min(1, hsv[1] * 1.1f), hsv[2] * 0.28f, 255));
        r[1] = Colour.genesis(Colour.fromHsv(hsv[0] + 4, Math.min(1, hsv[1] * 1.05f), hsv[2] * 0.62f, 255));
        r[2] = Colour.genesis(base | 0xFF000000);
        r[3] = Colour.genesis(Colour.fromHsv(hsv[0] - 6, hsv[1] * 0.85f, Math.min(1, hsv[2] * 1.25f + 0.08f), 255));
        r[4] = Colour.genesis(Colour.fromHsv(hsv[0] - 10, hsv[1] * 0.45f, Math.min(1, hsv[2] * 1.4f + 0.25f), 255));
        return r;
    }

    private int material(int colour) {
        ramps[materials] = ramp(colour);
        materials++;
        return materials;
    }

    private void set(int x, int y, int m, int s) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return;
        }
        mat[y * w + x] = (byte) m;
        shade[y * w + x] = (byte) s;
    }

    private void ellipse(float cx, float cy, float rx, float ry, int m) {
        for (int y = (int) (cy - ry - 1); y <= cy + ry + 1; y++) {
            for (int x = (int) (cx - rx - 1); x <= cx + rx + 1; x++) {
                float nx = (x + 0.5f - cx) / rx;
                float ny = (y + 0.5f - cy) / ry;
                float d = nx * nx + ny * ny;
                if (d <= 1) {
                    // Sphere lighting from the upper left.
                    float nz = (float) Math.sqrt(Math.max(0, 1 - d));
                    float light = -0.55f * nx - 0.6f * ny + 0.6f * nz;
                    set(x, y, m, shadeOf(light));
                }
            }
        }
    }

    private static int shadeOf(float light) {
        if (light > 0.75f) {
            return 4;
        }
        if (light > 0.38f) {
            return 3;
        }
        if (light > 0.0f) {
            return 2;
        }
        return 1;
    }

    /** A tapered stroke from (x0,y0) to (x1,y1), lit across its width. */
    private void stroke(float x0, float y0, float x1, float y1, float r0, float r1, int m) {
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        int steps = Math.max(1, (int) (len * 2));
        float px = -(y1 - y0) / Math.max(0.001f, len);
        float py = (x1 - x0) / Math.max(0.001f, len);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float cx = x0 + (x1 - x0) * t;
            float cy = y0 + (y1 - y0) * t;
            float r = r0 + (r1 - r0) * t;
            for (float o = -r; o <= r; o += 0.5f) {
                float across = o / Math.max(0.5f, r);
                float light = -across * (px < 0 ? -1 : 1) * 0.7f + 0.2f;
                set(Math.round(cx + px * o), Math.round(cy + py * o), m, shadeOf(light));
            }
        }
    }

    private void polygon(float[] xs, float[] ys, int m, float lightBias) {
        int minY = h;
        int maxY = 0;
        for (float y : ys) {
            minY = Math.min(minY, (int) Math.floor(y));
            maxY = Math.max(maxY, (int) Math.ceil(y));
        }
        float cx = 0;
        for (float x : xs) {
            cx += x;
        }
        cx /= xs.length;
        for (int y = Math.max(0, minY); y <= Math.min(h - 1, maxY); y++) {
            for (int x = 0; x < w; x++) {
                if (inside(xs, ys, x + 0.5f, y + 0.5f)) {
                    float light = (x < cx ? 0.55f : -0.1f) + lightBias;
                    set(x, y, m, shadeOf(light));
                }
            }
        }
    }

    private static boolean inside(float[] xs, float[] ys, float x, float y) {
        boolean in = false;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
                in = !in;
            }
        }
        return in;
    }

    /** Edge lighting, outline and conversion to an image whose origin is the bottom centre. */
    private SceneSprite finish(boolean outline) {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int m = mat[i];
                if (m == 0) {
                    continue;
                }
                int s = shade[i];
                // Rim light on top-left edges, shadow on bottom-right edges.
                if (empty(x - 1, y) || empty(x, y - 1)) {
                    s = Math.min(4, s + 1);
                } else if (empty(x + 1, y) || empty(x, y + 1)) {
                    s = Math.max(1, s - 1);
                }
                out[i] = ramps[m - 1][s];
            }
        }
        if (outline) {
            int[] lined = out.clone();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (out[y * w + x] != 0) {
                        continue;
                    }
                    int m = neighbourMaterial(x, y);
                    if (m > 0) {
                        lined[y * w + x] = ramps[m - 1][0];
                    }
                }
            }
            out = lined;
        }
        return new SceneSprite(new SceneImage(w, h, out), w / 2, h - 1);
    }

    private boolean empty(int x, int y) {
        return x < 0 || y < 0 || x >= w || y >= h || mat[y * w + x] == 0;
    }

    private int neighbourMaterial(int x, int y) {
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : d) {
            int nx = x + o[0];
            int ny = y + o[1];
            if (nx >= 0 && ny >= 0 && nx < w && ny < h && mat[ny * w + nx] != 0) {
                return mat[ny * w + nx];
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------- species

    public static final int SHAPE_TREE = 0;
    public static final int SHAPE_BUSH = 1;
    public static final int SHAPE_FLOWER = 2;
    public static final int SHAPE_MUSHROOM = 3;
    public static final int SHAPE_CRYSTAL = 4;
    public static final int SHAPE_CACTUS = 5;
    public static final int SHAPE_CORAL = 6;
    public static final int SHAPE_PINE = 7;
    public static final int SHAPE_STALK = 8;
    public static final int SHAPE_BULB = 9;

    /** A plant of {@code shape} grown from {@code seed} in {@code primary}/{@code secondary} colours. */
    public static SceneSprite plant(int shape, long seed, int primary, int secondary, int trunk, float scale) {
        Rng rng = new Rng(seed);
        return switch (shape) {
            case SHAPE_TREE -> tree(rng, primary, trunk, scale);
            case SHAPE_BUSH -> bush(rng, primary, secondary, scale);
            case SHAPE_FLOWER -> flower(rng, primary, secondary, scale);
            case SHAPE_MUSHROOM -> mushroom(rng, primary, secondary, scale);
            case SHAPE_CRYSTAL -> crystal(rng, primary, scale);
            case SHAPE_CACTUS -> cactus(rng, primary, secondary, scale);
            case SHAPE_CORAL -> coral(rng, primary, secondary, scale);
            case SHAPE_PINE -> pine(rng, primary, secondary, trunk, scale);
            case SHAPE_STALK -> stalk(rng, primary, secondary, scale);
            default -> bulb(rng, primary, secondary, scale);
        };
    }

    private static int dim(int base, float scale) {
        return Math.max(8, Math.round(base * scale));
    }

    private static SceneSprite tree(Rng rng, int leaf, int trunk, float scale) {
        int h = dim(rng.range(52, 84), scale);
        int w = dim(rng.range(44, 64), scale);
        PixelArt p = new PixelArt(w, h);
        int bark = p.material(trunk);
        int leaves = p.material(leaf);
        float bend = rng.range(-6f, 6f) * scale;
        float topX = w / 2f + bend;
        float topY = h * rng.range(0.22f, 0.34f);
        p.stroke(w / 2f, h - 1, topX, topY, 3.2f * scale + 1, 1.6f * scale + 0.8f, bark);
        int fronds = rng.range(5, 8);
        boolean round = rng.chance(0.4);
        if (round) {
            for (int i = 0; i < 5; i++) {
                p.ellipse(topX + rng.range(-w * 0.28f, w * 0.28f), topY + rng.range(-h * 0.14f, h * 0.06f),
                        w * rng.range(0.16f, 0.26f), h * rng.range(0.1f, 0.16f), leaves);
            }
        } else {
            for (int i = 0; i < fronds; i++) {
                double a = Math.PI * (1.05 + i / (double) (fronds - 1) * 0.9) + rng.range(-0.15f, 0.15f);
                float len = w * rng.range(0.34f, 0.5f);
                float ex = topX + (float) Math.cos(a) * len;
                float ey = topY + (float) Math.sin(a) * len * 0.35f + len * 0.35f;
                float midX = (topX + ex) / 2;
                float midY = Math.min(ey, topY) - 3 * scale;
                p.stroke(topX, topY, midX, midY, 3.4f * scale, 2.8f * scale, leaves);
                p.stroke(midX, midY, ex, ey, 2.8f * scale, 0.8f, leaves);
                // Leaflets hanging from the frond.
                for (int k = 1; k <= 3; k++) {
                    float t = k / 4f;
                    float lx = midX + (ex - midX) * t;
                    float ly = midY + (ey - midY) * t;
                    p.stroke(lx, ly, lx + (ex - topX) * 0.08f, ly + 4 * scale, 1.2f * scale, 0.5f, leaves);
                }
            }
            p.ellipse(topX, topY, 3 * scale + 1, 2.5f * scale + 1, bark);
        }
        return p.finish(true);
    }

    private static SceneSprite bush(Rng rng, int leaf, int berry, float scale) {
        int w = dim(rng.range(26, 40), scale);
        int h = dim(rng.range(16, 26), scale);
        PixelArt p = new PixelArt(w, h);
        int leaves = p.material(leaf);
        int fruit = p.material(berry);
        int blobs = rng.range(3, 5);
        for (int i = 0; i < blobs; i++) {
            float cx = w * (0.25f + 0.5f * i / Math.max(1, blobs - 1));
            p.ellipse(cx, h * rng.range(0.5f, 0.65f), w * rng.range(0.18f, 0.26f), h * rng.range(0.32f, 0.45f), leaves);
        }
        if (rng.chance(0.7)) {
            for (int i = 0; i < rng.range(3, 6); i++) {
                p.ellipse(rng.range(w * 0.15f, w * 0.85f), rng.range(h * 0.3f, h * 0.8f), 1.6f * scale + 0.4f,
                        1.6f * scale + 0.4f, fruit);
            }
        }
        return p.finish(true);
    }

    private static SceneSprite flower(Rng rng, int petal, int centre, float scale) {
        int h = dim(rng.range(22, 38), scale);
        int w = dim(rng.range(18, 26), scale);
        PixelArt p = new PixelArt(w, h);
        int stem = p.material(0xFF2E8B22);
        int pet = p.material(petal);
        int mid = p.material(centre);
        float fx = w / 2f + rng.range(-2f, 2f);
        float fy = h * 0.3f;
        p.stroke(w / 2f, h - 1, fx, fy, 1.2f * scale, 0.8f * scale, stem);
        p.ellipse(w / 2f - 3 * scale, h * 0.7f, 3.5f * scale, 1.6f * scale, stem);
        int petals = rng.range(4, 7);
        float pr = w * rng.range(0.22f, 0.3f);
        for (int i = 0; i < petals; i++) {
            double a = i * Math.PI * 2 / petals;
            p.ellipse(fx + (float) Math.cos(a) * pr * 0.8f, fy + (float) Math.sin(a) * pr * 0.8f, pr * 0.55f,
                    pr * 0.55f, pet);
        }
        p.ellipse(fx, fy, pr * 0.45f, pr * 0.45f, mid);
        return p.finish(true);
    }

    private static SceneSprite mushroom(Rng rng, int cap, int spots, float scale) {
        int h = dim(rng.range(24, 56), scale);
        int w = dim(rng.range(26, 50), scale);
        PixelArt p = new PixelArt(w, h);
        int stem = p.material(0xFFE8DCC0);
        int top = p.material(cap);
        int dot = p.material(spots);
        float capY = h * rng.range(0.25f, 0.4f);
        p.stroke(w / 2f, h - 1, w / 2f + rng.range(-3f, 3f), capY, w * 0.12f, w * 0.09f, stem);
        p.ellipse(w / 2f, capY, w * 0.48f, h * rng.range(0.18f, 0.26f), top);
        // Flatten the underside of the cap.
        for (int y = (int) capY + 1; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (p.mat[y * w + x] == top) {
                    p.mat[y * w + x] = 0;
                }
            }
        }
        for (int i = 0; i < rng.range(2, 5); i++) {
            p.ellipse(rng.range(w * 0.2f, w * 0.8f), capY - rng.range(1f, h * 0.15f), 2 * scale + 0.6f,
                    1.6f * scale + 0.6f, dot);
        }
        return p.finish(true);
    }

    private static SceneSprite crystal(Rng rng, int colour, float scale) {
        int h = dim(rng.range(22, 44), scale);
        int w = dim(rng.range(22, 36), scale);
        PixelArt p = new PixelArt(w, h);
        int c = p.material(colour);
        int shards = rng.range(3, 5);
        for (int i = 0; i < shards; i++) {
            float base = w * (0.2f + 0.6f * (i + rng.nextFloat() * 0.5f) / shards);
            float tilt = rng.range(-0.5f, 0.5f) + (base - w / 2f) / w;
            float len = h * rng.range(0.55f, 1f);
            float half = w * rng.range(0.08f, 0.13f);
            float tipX = base + tilt * len * 0.6f;
            float tipY = h - len;
            p.polygon(new float[] {base - half, base - half * 0.7f + tilt * len * 0.45f, tipX,
                            base + half * 0.7f + tilt * len * 0.45f, base + half},
                    new float[] {h, h - len * 0.75f, tipY, h - len * 0.75f, h}, c, i % 2 == 0 ? 0.2f : -0.1f);
        }
        return p.finish(true);
    }

    private static SceneSprite cactus(Rng rng, int colour, int flowerColour, float scale) {
        int h = dim(rng.range(30, 56), scale);
        int w = dim(rng.range(22, 36), scale);
        PixelArt p = new PixelArt(w, h);
        int c = p.material(colour);
        int fl = p.material(flowerColour);
        float r = w * 0.13f;
        p.stroke(w / 2f, h - 1, w / 2f, r + 1, r, r, c);
        p.ellipse(w / 2f, r + 1, r, r, c);
        for (int side = -1; side <= 1; side += 2) {
            if (rng.chance(0.75)) {
                float y = h * rng.range(0.35f, 0.6f);
                float x = w / 2f + side * w * 0.3f;
                p.stroke(w / 2f, y, x, y, r * 0.7f, r * 0.7f, c);
                float top = y - h * rng.range(0.15f, 0.28f);
                p.stroke(x, y, x, top, r * 0.7f, r * 0.7f, c);
                p.ellipse(x, top, r * 0.7f, r * 0.7f, c);
            }
        }
        if (rng.chance(0.5)) {
            p.ellipse(w / 2f, 1.5f * scale + 1, 2.2f * scale, 1.6f * scale + 0.4f, fl);
        }
        return p.finish(true);
    }

    private static SceneSprite coral(Rng rng, int colour, int tipColour, float scale) {
        int h = dim(rng.range(26, 46), scale);
        int w = dim(rng.range(26, 40), scale);
        PixelArt p = new PixelArt(w, h);
        int c = p.material(colour);
        int tip = p.material(tipColour);
        branch(p, rng, w / 2f, h - 1, -Math.PI / 2, h * 0.38f, 2.6f * scale, 3, c, tip);
        return p.finish(true);
    }

    private static void branch(PixelArt p, Rng rng, float x, float y, double angle, float len, float r, int depth,
            int m, int tip) {
        float ex = x + (float) Math.cos(angle) * len;
        float ey = y + (float) Math.sin(angle) * len;
        p.stroke(x, y, ex, ey, r, r * 0.75f, m);
        if (depth <= 0) {
            p.ellipse(ex, ey, r * 1.1f + 0.5f, r * 1.1f + 0.5f, tip);
            return;
        }
        int kids = rng.range(2, 3);
        for (int i = 0; i < kids; i++) {
            branch(p, rng, ex, ey, angle + rng.range(-0.7f, 0.7f), len * rng.range(0.55f, 0.75f), r * 0.75f,
                    depth - 1, m, tip);
        }
    }

    private static SceneSprite pine(Rng rng, int needles, int snow, int trunk, float scale) {
        int h = dim(rng.range(44, 76), scale);
        int w = dim(rng.range(26, 40), scale);
        PixelArt p = new PixelArt(w, h);
        int bark = p.material(trunk);
        int n = p.material(needles);
        int s = p.material(snow);
        p.stroke(w / 2f, h - 1, w / 2f, h * 0.7f, 2.5f * scale, 2f * scale, bark);
        int tiers = rng.range(3, 5);
        for (int i = 0; i < tiers; i++) {
            float top = h * 0.05f + i * h * 0.62f / tiers;
            float bottom = top + h * 0.3f;
            float half = w * (0.2f + 0.3f * (i + 1) / tiers);
            p.polygon(new float[] {w / 2f, w / 2f + half, w / 2f - half}, new float[] {top, bottom, bottom}, n, 0);
            p.polygon(new float[] {w / 2f, w / 2f + half * 0.45f, w / 2f - half * 0.45f},
                    new float[] {top, top + (bottom - top) * 0.35f, top + (bottom - top) * 0.35f}, s, 0.4f);
        }
        return p.finish(true);
    }

    private static SceneSprite stalk(Rng rng, int metal, int light, float scale) {
        int h = dim(rng.range(30, 60), scale);
        int w = dim(rng.range(14, 22), scale);
        PixelArt p = new PixelArt(w, h);
        int m = p.material(metal);
        int l = p.material(light);
        p.stroke(w / 2f, h - 1, w / 2f, h * 0.2f, 1.6f * scale + 0.5f, 1.2f * scale + 0.5f, m);
        for (int i = 0; i < rng.range(2, 4); i++) {
            float y = h * rng.range(0.35f, 0.8f);
            int side = rng.chance(0.5) ? 1 : -1;
            p.stroke(w / 2f, y, w / 2f + side * w * 0.38f, y - 4 * scale, 1 * scale + 0.4f, 0.8f * scale + 0.3f, m);
        }
        p.ellipse(w / 2f, h * 0.18f, w * 0.3f, w * 0.3f, l);
        return p.finish(true);
    }

    private static SceneSprite bulb(Rng rng, int colour, int leafColour, float scale) {
        int h = dim(rng.range(20, 30), scale);
        int w = dim(rng.range(18, 26), scale);
        PixelArt p = new PixelArt(w, h);
        int leaf = p.material(leafColour);
        int b = p.material(colour);
        p.stroke(w / 2f, h - 1, w / 2f - w * 0.32f, h * 0.55f, 1.6f * scale, 0.5f, leaf);
        p.stroke(w / 2f, h - 1, w / 2f + w * 0.32f, h * 0.6f, 1.6f * scale, 0.5f, leaf);
        p.stroke(w / 2f, h - 1, w / 2f, h * 0.5f, 1.2f * scale, 1 * scale, leaf);
        p.ellipse(w / 2f, h * 0.38f, w * 0.3f, h * 0.3f, b);
        return p.finish(true);
    }

    /** A mineral boulder with veins of {@code vein}, for deposits. */
    public static SceneSprite boulder(long seed, int rock, int vein, float scale) {
        Rng rng = new Rng(seed);
        int w = dim(rng.range(26, 40), scale);
        int h = dim(rng.range(18, 28), scale);
        PixelArt p = new PixelArt(w, h);
        int r = p.material(rock);
        int v = p.material(vein);
        int n = rng.range(5, 8);
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            double a = Math.PI + i * Math.PI / (n - 1);
            float rad = rng.range(0.8f, 1f);
            xs[i] = w / 2f + (float) Math.cos(a) * w * 0.48f * rad;
            ys[i] = h - 1 + (float) Math.sin(a) * h * 0.95f * rad;
        }
        p.polygon(xs, ys, r, 0.1f);
        for (int i = 0; i < rng.range(3, 6); i++) {
            float x = rng.range(w * 0.2f, w * 0.8f);
            float y = rng.range(h * 0.35f, h * 0.85f);
            p.ellipse(x, y, rng.range(1.5f, 3f) * scale, rng.range(1f, 2f) * scale, v);
        }
        return p.finish(true);
    }

    /** A round glowing icon ball (for resource pickups and HUD). */
    public static SceneSprite gem(int colour, int size) {
        PixelArt p = new PixelArt(size, size);
        int m = p.material(colour);
        p.ellipse(size / 2f, size / 2f, size / 2f - 1, size / 2f - 1, m);
        SceneSprite s = p.finish(true);
        return new SceneSprite(s.image(), size / 2, size / 2);
    }

    /** A cut Chaos Emerald: a faceted gem with a bright table and darker pavilion. */
    public static SceneSprite emerald(int colour) {
        int w = 20;
        int h = 16;
        PixelArt p = new PixelArt(w, h);
        int m = p.material(colour);
        // Crown (top) lit, pavilion (bottom) in shadow.
        p.polygon(new float[] {4, 16, 20, 0}, new float[] {0, 0, 6, 6}, m, 0.6f);
        p.polygon(new float[] {0, 20, 10}, new float[] {6, 6, 16}, m, -0.2f);
        p.polygon(new float[] {6, 14, 12, 8}, new float[] {1, 1, 5, 5}, m, 1.2f);
        SceneSprite s = p.finish(true);
        return new SceneSprite(s.image(), w / 2, h / 2);
    }
}
