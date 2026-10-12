package eggsky.space;

import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import eggsky.core.Colour;
import eggsky.core.Recolor;
import eggsky.core.Rng;
import eggsky.world.Biome;
import eggsky.world.PlanetSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * A planet's surface as seen from orbit: an equirectangular map whose continents are a mosaic
 * of the source act's own layout blocks, shrunk and recoloured into the planet's palette, with
 * seas in the colours of the zone's sky, ice caps on cold worlds and a separate cloud layer. Seen
 * from space, a planet looks like the zone it was remixed from.
 */
public final class PlanetTexture {
    public static final int W = 256;
    public static final int H = 128;

    public final int[] surface = new int[W * H];
    /** Cloud cover alpha 0-255 per texel. */
    public final byte[] clouds = new byte[W * H];
    public final int atmosphere;
    public final int cloudColour;

    private PlanetTexture(int atmosphere, int cloudColour) {
        this.atmosphere = atmosphere;
        this.cloudColour = cloudColour;
    }

    /** From the act's blocks (the real look). {@code kit} may be null for a quick stand-in. */
    public static PlanetTexture build(PlanetSpec spec, SceneLevelKit kit) {
        Rng rng = new Rng(Rng.hash(spec.seed, 0x54455854L));
        Recolor ground = spec.ground;
        int sea;
        int sky;
        List<int[]> tiles = new ArrayList<>();
        if (kit != null) {
            SceneImage bg = spec.sky.apply(kit.backdrop().image());
            sea = average(bg, bg.height() * 3 / 4, bg.height());
            sky = average(bg, 0, Math.max(1, bg.height() / 4));
            int[] area = kit.playableArea();
            int size = kit.blockSize();
            int c0 = area[0] / size;
            int r0 = area[1] / size;
            int cols = Math.max(1, area[2] / size);
            int rows = Math.max(1, area[3] / size);
            for (int attempt = 0; attempt < 220 && tiles.size() < 36; attempt++) {
                int id = kit.block(c0 + rng.nextInt(cols), r0 + rng.nextInt(rows));
                if (id <= 0) {
                    continue;
                }
                int[] px = kit.blockImage(id).pixels();
                int[] tile = shrink(px, size, 16, ground);
                if (tile != null) {
                    tiles.add(tile);
                }
            }
        } else {
            sea = Colour.fromHsv(210 + ground.hueShift(), 0.6f, 0.6f, 255);
            sky = Colour.fromHsv(200 + ground.hueShift(), 0.4f, 0.9f, 255);
        }
        if (tiles.isEmpty()) {
            int land = spec.signature;
            int[] tile = new int[16 * 16];
            for (int i = 0; i < tile.length; i++) {
                tile[i] = Colour.scale(land, 0.75f + 0.25f * ((i * 7919) % 13) / 13f);
            }
            tiles.add(tile);
        }
        boolean cold = spec.biome.climate() == Biome.CLIMATE_COLD;
        boolean hot = spec.biome.climate() == Biome.CLIMATE_HOT;
        boolean dead = spec.dead;
        float seaLevel = dead ? -1f : hot ? -0.25f : cold ? 0.05f : rng.range(-0.1f, 0.15f);
        int cloudColour = Colour.lerp(0xFFFFFFFF, sky, 0.25f);
        if (spec.biome.climate() == Biome.CLIMATE_TOXIC) {
            cloudColour = Colour.lerp(cloudColour, 0xFFC0FF60, 0.4f);
        } else if (hot) {
            cloudColour = Colour.lerp(cloudColour, 0xFF806050, 0.5f);
        }
        PlanetTexture t = new PlanetTexture(Colour.lerp(sky, 0xFFFFFFFF, 0.2f), cloudColour);
        long ns = rng.nextLong();
        long cs = rng.nextLong();
        long ts = rng.nextLong();
        int cloudiness = spec.storms;
        for (int y = 0; y < H; y++) {
            float lat = (y + 0.5f) / H * 2 - 1;
            for (int x = 0; x < W; x++) {
                float h = 0;
                float amp = 1;
                int f = 6;
                for (int o = 0; o < 5; o++) {
                    h += Rng.noise2Wrapped(ns + o, x * f / (float) W, y * f / (float) W, f) * amp;
                    amp *= 0.5f;
                    f *= 2;
                }
                h = h * 0.65f;
                int colour;
                if (h > seaLevel) {
                    // Land: a zone block shrunk to a texel patch, chosen per 16x16 cell.
                    int cell = (int) Math.floorMod(Rng.mix(ts + (x / 16) * 131L + (y / 16) * 7919L), (long) tiles.size());
                    int[] tile = tiles.get(cell);
                    colour = tile[(y % 16) * 16 + (x % 16)];
                    float shade = 0.85f + Math.min(0.3f, (h - seaLevel) * 0.6f);
                    colour = Colour.scale(colour, shade);
                } else {
                    float depth = Math.min(1, (seaLevel - h) * 2.5f);
                    colour = Colour.lerp(Colour.lerp(sea, 0xFFFFFFFF, 0.15f), Colour.scale(sea, 0.55f), depth);
                }
                float cap = Math.abs(lat) - (cold ? 0.45f : hot ? 0.95f : 0.78f) + h * 0.15f;
                if (cap > 0) {
                    colour = Colour.lerp(colour, 0xFFF0F4FF, Math.min(1, cap * 8));
                }
                t.surface[y * W + x] = colour | 0xFF000000;
                float cl = 0;
                amp = 1;
                f = 8;
                for (int o = 0; o < 4; o++) {
                    cl += Rng.noise2Wrapped(cs + o, x * f / (float) W, y * f * 1.6f / W, f) * amp;
                    amp *= 0.5f;
                    f *= 2;
                }
                float cover = cl - 0.25f + cloudiness * 0.12f - (dead ? 1 : 0);
                t.clouds[y * W + x] = (byte) Math.max(0, Math.min(255, Math.round(cover * 600)));
            }
        }
        return t;
    }

    /** A block shrunk to {@code n}x{@code n} by averaging opaque pixels; null when mostly empty. */
    private static int[] shrink(int[] px, int size, int n, Recolor recolor) {
        int[] out = new int[n * n];
        int step = size / n;
        int opaqueCells = 0;
        for (int ty = 0; ty < n; ty++) {
            for (int tx = 0; tx < n; tx++) {
                long r = 0;
                long g = 0;
                long b = 0;
                int count = 0;
                for (int y = 0; y < step; y++) {
                    for (int x = 0; x < step; x++) {
                        int p = px[(ty * step + y) * size + tx * step + x];
                        if ((p >>> 24) != 0) {
                            r += Colour.r(p);
                            g += Colour.g(p);
                            b += Colour.b(p);
                            count++;
                        }
                    }
                }
                if (count > step * step / 3) {
                    opaqueCells++;
                    out[ty * n + tx] = recolor.apply(Colour.rgb((int) (r / count), (int) (g / count), (int) (b / count)));
                }
            }
        }
        if (opaqueCells < n * n * 3 / 4) {
            return null;
        }
        // Fill any gaps with the tile's average.
        long r = 0;
        long g = 0;
        long b = 0;
        int count = 0;
        for (int p : out) {
            if (p != 0) {
                r += Colour.r(p);
                g += Colour.g(p);
                b += Colour.b(p);
                count++;
            }
        }
        int avg = Colour.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        for (int i = 0; i < out.length; i++) {
            if (out[i] == 0) {
                out[i] = avg;
            }
        }
        return out;
    }

    private static int average(SceneImage img, int y0, int y1) {
        long r = 0;
        long g = 0;
        long b = 0;
        int n = 0;
        for (int y = Math.max(0, y0); y < Math.min(img.height(), y1); y += 2) {
            for (int x = 0; x < img.width(); x += 3) {
                int p = img.pixel(x, y);
                if ((p >>> 24) != 0) {
                    r += Colour.r(p);
                    g += Colour.g(p);
                    b += Colour.b(p);
                    n++;
                }
            }
        }
        return n == 0 ? 0xFF203060 : Colour.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }

    /** A metal sphere for the space station: panels, a trench and a gold Eggman emblem band. */
    public static PlanetTexture station(long seed) {
        PlanetTexture t = new PlanetTexture(0xFF8090B0, 0xFFFFFFFF);
        Rng rng = new Rng(seed);
        for (int y = 0; y < H; y++) {
            float lat = (y + 0.5f) / H * 2 - 1;
            for (int x = 0; x < W; x++) {
                int base = (x / 8 + y / 8) % 2 == 0 ? 0xFF9098A8 : 0xFF8088A0;
                if (x % 8 == 0 || y % 8 == 0) {
                    base = 0xFF606878;
                }
                if (Math.abs(lat) < 0.06f) {
                    base = 0xFF303848;
                } else if (Math.abs(lat) < 0.12f) {
                    base = (x / 4) % 2 == 0 ? 0xFFE0C040 : 0xFF303030;
                }
                // The giant Eggman face on the front.
                float fx = (x - W * 0.25f) / (W * 0.09f);
                float fy = (y - H * 0.4f) / (H * 0.18f);
                if (fx * fx + fy * fy < 1) {
                    base = 0xFFF0C8A0;
                    if (Math.abs(fy + 0.35f) < 0.15f && Math.abs(Math.abs(fx) - 0.35f) < 0.18f) {
                        base = 0xFF101010;
                    }
                    if (fy > 0.25f && fy < 0.6f && Math.abs(fx) < 0.85f) {
                        base = 0xFFD06020;
                    }
                }
                if (rng.chance(0.004)) {
                    base = 0xFFFFE080;
                }
                t.surface[y * W + x] = base;
            }
        }
        return t;
    }
}
