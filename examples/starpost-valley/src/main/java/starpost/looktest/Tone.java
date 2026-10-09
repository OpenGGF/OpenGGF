package starpost.looktest;

import com.openggf.mods.scene.SceneImage;
import java.util.HashMap;

/**
 * A season's recolouring of Green Hill: every colour of the zone's art is mapped once (zone art
 * uses a few dozen colours) and snapped back to the Mega Drive's nine-bit palette, so a recoloured
 * picture is still something the hardware could show. Greens carry the season; the checkered
 * soil, water and flowers mostly keep their own colours, so the valley always reads as Green Hill.
 */
public final class Tone {
    public static final int SPRING = 0;
    public static final int SUMMER = 1;
    public static final int FALL = 2;
    public static final int WINTER = 3;
    public static String seasonName(int season) {
        return switch (season) {
            case SPRING -> "SPRING";
            case SUMMER -> "SUMMER";
            case FALL -> "FALL";
            default -> "WINTER";
        };
    }

    private final int season;
    private final HashMap<Integer, Integer> cache = new HashMap<>();

    public Tone(int season) {
        this.season = season;
    }

    public int season() {
        return season;
    }

    /** One colour in this season. */
    public int apply(int argb) {
        int alpha = argb >>> 24;
        if (alpha == 0 || season == SPRING) {
            return argb;
        }
        Integer cached = cache.get(argb);
        if (cached != null) {
            return cached;
        }
        float[] hsv = hsv(argb);
        float h = hsv[0], s = hsv[1], v = hsv[2];
        boolean green = h >= 70 && h <= 170 && s > 0.25f;
        boolean water = h > 185 && h < 250 && s > 0.3f;
        float[] out = {h, s, v};
        switch (season) {
            case SUMMER -> {
                if (green) {
                    out[0] = h - 10;
                    out[1] = Math.min(1, s * 1.1f);
                    out[2] = Math.min(1, v * 1.04f);
                }
            }
            case FALL -> {
                if (green) {
                    // Bright leaves turn amber, shadowed leaves rust: the same ramp, re-hued.
                    out[0] = v > 0.6f ? 38 : v > 0.4f ? 26 : 12;
                    out[1] = Math.min(1, s * 1.05f + 0.1f);
                    out[2] = Math.min(1, v * 1.02f);
                }
            }
            case WINTER -> {
                if (green) {
                    // Snow over the grass: highlights white, shadows pale steel blue.
                    out[0] = 215;
                    out[1] = v > 0.6f ? 0.06f : 0.22f;
                    out[2] = Math.min(1, 0.55f + v * 0.5f);
                } else if (water) {
                    out[1] = s * 0.55f;
                    out[2] = Math.min(1, v * 1.1f);
                } else if (h < 45 && s > 0.3f) {
                    out[1] = s * 0.8f;   // frosted soil
                }
            }
            default -> {
            }
        }
        int result = genesis(fromHsv(out[0], out[1], out[2], alpha));
        cache.put(argb, result);
        return result;
    }

    /**
     * The background sky at dusk (1) or night (2), as a palette swap would do it: the blues of
     * the sky and lake turn sunset orange and pink, or deep navy; clouds catch the light or
     * go grey; everything else dims and warms (dusk) or cools (night).
     */
    public static int sky(int argb, int time) {
        int alpha = argb >>> 24;
        if (alpha == 0 || time == 0) {
            return argb;
        }
        float[] hsv = hsv(argb);
        float h = hsv[0], s = hsv[1], v = hsv[2];
        boolean blue = h >= 190 && h <= 275 && s > 0.2f;
        boolean cloud = s < 0.2f && v > 0.7f;
        float[] out;
        if (time == 1) {
            out = blue ? new float[] {v < 0.75f ? 8 : 28, v < 0.75f ? 0.8f : 0.55f, Math.min(1, v * 0.8f + 0.25f)}
                    : cloud ? new float[] {345, 0.25f, v}
                    : new float[] {h < 60 ? h : h * 0.6f, s, v * 0.75f};
        } else {
            out = blue ? new float[] {232, 0.85f, v * 0.42f}
                    : cloud ? new float[] {225, 0.25f, v * 0.5f}
                    : new float[] {h, s * 0.7f, v * 0.4f};
        }
        return genesis(fromHsv(out[0], out[1], out[2], alpha));
    }

    /** A whole picture in this season (spring returns the original). */
    public SceneImage apply(SceneImage image) {
        if (season == SPRING) {
            return image;
        }
        int[] pixels = image.pixels();
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = apply(pixels[i]);
        }
        return new SceneImage(image.width(), image.height(), pixels);
    }

    /** A palette in this season. */
    public int[] apply(int[] palette) {
        int[] out = palette.clone();
        for (int i = 0; i < out.length; i++) {
            out[i] = apply(out[i] | 0xFF000000);
        }
        return out;
    }

    /** Snaps a colour to the Mega Drive's eight levels per channel. */
    public static int genesis(int argb) {
        int a = argb >>> 24;
        int r = level(argb >>> 16 & 255), g = level(argb >>> 8 & 255), b = level(argb & 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** The engine's Mega Drive levels: 0x00, 0x24, 0x49, 0x6D, 0x92, 0xB6, 0xDB, 0xFF. */
    private static int level(int c) {
        int step = Math.round(c * 7 / 255f);
        return Math.round(step * 255 / 7f);
    }

    static float[] hsv(int argb) {
        float r = (argb >>> 16 & 255) / 255f, g = (argb >>> 8 & 255) / 255f, b = (argb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) {
                h = 60 * (((g - b) / d) % 6);
            } else if (max == g) {
                h = 60 * ((b - r) / d + 2);
            } else {
                h = 60 * ((r - g) / d + 4);
            }
        }
        if (h < 0) {
            h += 360;
        }
        return new float[] {h, max == 0 ? 0 : d / max, max};
    }

    static int fromHsv(float h, float s, float v, int alpha) {
        h = ((h % 360) + 360) % 360;
        s = Math.max(0, Math.min(1, s));
        v = Math.max(0, Math.min(1, v));
        float c = v * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return alpha << 24 | Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }
}
