package starpost.art;

import com.openggf.mods.scene.SceneImage;

/**
 * The valley's buildings, assembled from Green Hill's own pixels: checkered soil walls, a sod
 * roof of the zone's grass lip, eaves and lintels from the log bridge, plank doors from the dark
 * striped band of block 6, and windows of the background lake's sparkle. Nothing is drawn freehand
 * except shading, so the town looks as if it was dug out of the hills. Each picture's bottom row
 * stands on the floor; seasons recolour them like the terrain (a winter roof is snow).
 */
public final class Facades {
    private final SceneImage soil;      // 32x16: two periods of the 8-pixel checker
    private final SceneImage grass;     // 256 wide: blades, lip and shadow band
    private final SceneImage log;       // 256x12: the bridge log
    private final SceneImage planks;    // 64x64: dark vertical planks
    private final SceneImage water;     // 64x16: the lake's sparkle
    public final SceneImage farmhouse;
    public final SceneImage seedStall;
    public final SceneImage inn;
    public final SceneImage workshop;
    public final SceneImage robomart;

    Facades(Art art) {
        SceneImage flat = art.kit.blockImage(60);
        soil = flat.crop(0, Art.FLOOR + 40, 32, 16);
        grass = flat.crop(0, Art.FLOOR - 4, 256, 24);
        log = art.kit.blockImage(51).crop(0, 129, 256, 12);
        planks = art.kit.blockImage(6).crop(64, 128, 64, 64);
        SceneImage sky = art.kit.backdrop().image();
        water = sky.crop(0, Math.min(sky.height() - 16, 176), 64, 16);
        farmhouse = farmhouse();
        seedStall = stall();
        inn = inn();
        workshop = workshop();
        robomart = robomart();
    }

    /** A pixel buffer with blits from the ROM pieces. */
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

        /** Copies opaque pixels of a source region, tiling it over the destination rectangle. */
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

        /** Darkens (factor &lt; 1) or lightens a rectangle, snapping to Mega Drive levels. */
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

    private static final int OUTLINE = 0xFF240000;

    /** Checker walls with darker side edges. */
    private void walls(Pic p, int x, int y, int w, int h) {
        p.tile(soil, 0, 0, 32, 16, x, y, w, h);
        p.shade(x, y, 3, h, 0.7f);
        p.shade(x + w - 3, y, 3, h, 0.7f);
        p.shade(x, y, w, 4, 0.75f);           // the roof's shadow
    }

    /** A sod roof: Green Hill's grass lip laid along the top, overhanging both sides. */
    private void sodRoof(Pic p, int x, int y, int w) {
        p.tile(grass, 0, 0, 256, 24, x - 6, y, w + 12, 24);
    }

    private void logBeam(Pic p, int x, int y, int w) {
        p.tile(log, 0, 0, 256, 12, x, y, w, 12);
    }

    private void door(Pic p, int x, int y, int w, int h) {
        p.fill(x - 2, y - 2, w + 4, h + 2, OUTLINE);
        p.tile(planks, 0, 0, 64, 64, x, y, w, h);
        p.fill(x + w - 5, y + h / 2, 2, 2, 0xFFFFDB00);   // the handle
        logBeam(p, x - 6, y - 12, w + 12);
    }

    private void window(Pic p, int x, int y, int w, int h) {
        p.fill(x - 2, y - 2, w + 4, h + 4, OUTLINE);
        p.tile(water, 0, 0, 64, 16, x, y, w, h);
        p.fill(x + w / 2, y, 1, h, OUTLINE);
        p.fill(x, y + h / 2, w, 1, OUTLINE);
        logBeam(p, x - 4, y + h + 1, w + 8);
    }

    private SceneImage farmhouse() {
        Pic p = new Pic(132, 92);
        walls(p, 10, 34, 112, 58);
        p.tile(soil, 0, 0, 32, 16, 92, 8, 16, 26);      // the chimney
        p.shade(92, 8, 16, 26, 0.8f);
        sodRoof(p, 10, 16, 112);
        door(p, 56, 58, 20, 34);
        window(p, 22, 52, 18, 14);
        window(p, 92, 52, 18, 14);
        return p.image();
    }

    private SceneImage stall() {
        Pic p = new Pic(96, 76);
        p.tile(soil, 0, 0, 32, 16, 6, 24, 8, 52);       // posts
        p.tile(soil, 0, 0, 32, 16, 82, 24, 8, 52);
        p.shade(6, 24, 8, 52, 0.8f);
        p.shade(82, 24, 8, 52, 0.8f);
        sodRoof(p, 4, 6, 88);
        p.fill(14, 30, 68, 30, 0x60240000);             // the shaded inside
        logBeam(p, 2, 54, 92);                           // the counter
        walls(p, 8, 64, 80, 12);
        return p.image();
    }

    private SceneImage inn() {
        Pic p = new Pic(140, 112);
        walls(p, 8, 30, 124, 82);
        sodRoof(p, 8, 12, 124);
        logBeam(p, 8, 68, 124);                          // between the storeys
        window(p, 22, 42, 18, 14);
        window(p, 61, 42, 18, 14);
        window(p, 100, 42, 18, 14);
        door(p, 58, 86, 24, 26);
        window(p, 22, 88, 18, 12);
        window(p, 100, 88, 18, 12);
        return p.image();
    }

    private SceneImage workshop() {
        Pic p = new Pic(132, 96);
        walls(p, 8, 30, 116, 66);
        for (int y = 10; y < 34; y += 8) {
            logBeam(p, 4 + (y - 10) / 2, y, 124 - (y - 10));  // a stepped roof of logs
        }
        door(p, 36, 52, 60, 44);                          // the hangar door
        p.fill(36, 72, 60, 1, OUTLINE);
        window(p, 14, 52, 14, 12);
        window(p, 104, 52, 14, 12);
        return p.image();
    }

    private SceneImage robomart() {
        Pic p = new Pic(120, 96);
        walls(p, 6, 26, 108, 70);
        // Scrap Brain grey: the checker drained of colour, with a red stripe.
        for (int i = 0; i < p.px.length; i++) {
            int c = p.px[i];
            if (c >>> 24 != 0) {
                int v = ((c >>> 16 & 255) + (c >>> 8 & 255) + (c & 255)) / 3;
                p.px[i] = Tone.genesis(0xFF000000 | v * 3 / 4 << 16 | v * 3 / 4 << 8 | Math.min(255, v));
            }
        }
        p.fill(6, 20, 108, 8, 0xFF6D6D92);
        p.fill(6, 26, 108, 3, 0xFFDB0000);
        p.fill(6, 60, 108, 3, 0xFFDB0000);
        door(p, 48, 64, 24, 32);
        window(p, 16, 36, 22, 16);
        window(p, 82, 36, 22, 16);
        return p.image();
    }
}
