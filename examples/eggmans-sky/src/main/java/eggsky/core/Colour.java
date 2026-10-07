package eggsky.core;

/**
 * {@code 0xAARRGGBB} colour helpers. {@link #genesis} snaps a colour to the Mega Drive's 9-bit
 * palette (eight levels per channel) so every recoloured world still looks like it came out of
 * the VDP.
 */
public final class Colour {
    private Colour() {
    }

    public static int a(int argb) {
        return argb >>> 24;
    }

    public static int r(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int g(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int b(int argb) {
        return argb & 0xFF;
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int rgb(int r, int g, int b) {
        return argb(255, r, g, b);
    }

    public static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    /** The same colour with alpha {@code alpha} (0-255). */
    public static int alpha(int argb, int alpha) {
        return (clamp(alpha) << 24) | (argb & 0xFFFFFF);
    }

    /** The same colour with its alpha scaled by {@code f} (0-1). */
    public static int fade(int argb, float f) {
        return alpha(argb, Math.round(a(argb) * Math.max(0, Math.min(1, f))));
    }

    /** Linear mix: {@code t} 0 is {@code x}, 1 is {@code y}; alpha mixes too. */
    public static int lerp(int x, int y, float t) {
        t = Math.max(0, Math.min(1, t));
        return argb(Math.round(a(x) + (a(y) - a(x)) * t), Math.round(r(x) + (r(y) - r(x)) * t),
                Math.round(g(x) + (g(y) - g(x)) * t), Math.round(b(x) + (b(y) - b(x)) * t));
    }

    /** Multiplies the colour channels by {@code f}. */
    public static int scale(int argb, float f) {
        return argb(a(argb), Math.round(r(argb) * f), Math.round(g(argb) * f), Math.round(b(argb) * f));
    }

    /** Snaps to the Mega Drive's eight levels per channel (0, 36, 72, ... 252), keeping alpha. */
    public static int genesis(int argb) {
        return (argb & 0xFF000000) | (level(r(argb)) << 16) | (level(g(argb)) << 8) | level(b(argb));
    }

    private static int level(int v) {
        int step = Math.round(v / 36f);
        return Math.min(7, step) * 36;
    }

    /** Hue (0-360), saturation and value (0-1) of an opaque colour. */
    public static float[] hsv(int argb) {
        float r = r(argb) / 255f;
        float g = g(argb) / 255f;
        float b = b(argb) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) {
            h = 0;
        } else if (max == r) {
            h = 60 * (((g - b) / d) % 6);
        } else if (max == g) {
            h = 60 * ((b - r) / d + 2);
        } else {
            h = 60 * ((r - g) / d + 4);
        }
        if (h < 0) {
            h += 360;
        }
        return new float[] {h, max == 0 ? 0 : d / max, max};
    }

    public static int fromHsv(float h, float s, float v, int alpha) {
        h = ((h % 360) + 360) % 360;
        s = Math.max(0, Math.min(1, s));
        v = Math.max(0, Math.min(1, v));
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60) % 2 - 1));
        float m = v - c;
        float r;
        float g;
        float b;
        if (h < 60) {
            r = c; g = x; b = 0;
        } else if (h < 120) {
            r = x; g = c; b = 0;
        } else if (h < 180) {
            r = 0; g = c; b = x;
        } else if (h < 240) {
            r = 0; g = x; b = c;
        } else if (h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        return argb(alpha, Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }

    /** Perceived brightness 0-255. */
    public static int luma(int argb) {
        return (r(argb) * 299 + g(argb) * 587 + b(argb) * 114) / 1000;
    }
}
