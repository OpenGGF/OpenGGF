package sonicbros.view;

import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;

/**
 * Pictures made in code for things the ROMs do not draw the way a platform fighter needs:
 * shield bubbles, glows, the wrecking ball, flames, bombs and missiles. Each is built once.
 */
public final class Gfx {
    private Gfx() { }

    /** A filled disc with a soft edge, white so it can be tinted. */
    public static SceneImage disc(int radius, boolean soft) {
        int d = radius * 2 + 1;
        int[] px = new int[d * d];
        for (int y = 0; y < d; y++) {
            for (int x = 0; x < d; x++) {
                double r = Math.hypot(x - radius, y - radius);
                if (r > radius + 0.5) continue;
                int a = soft ? (int) (255 * Math.max(0, Math.min(1, 1 - r / (radius + 0.5)))) : (r > radius - 0.5 ? 160 : 255);
                px[y * d + x] = (a << 24) | 0xFFFFFF;
            }
        }
        return new SceneImage(d, d, px);
    }

    /** A ring outline of the given thickness, white. */
    public static SceneImage ring(int radius, double thickness) {
        int d = radius * 2 + 3;
        int c = radius + 1;
        int[] px = new int[d * d];
        for (int y = 0; y < d; y++) {
            for (int x = 0; x < d; x++) {
                double r = Math.hypot(x - c, y - c);
                double edge = Math.abs(r - (radius - thickness / 2));
                if (edge > thickness / 2 + 0.5) continue;
                int a = (int) (255 * Math.max(0, Math.min(1, thickness / 2 + 0.5 - edge)));
                px[y * d + x] = (a << 24) | 0xFFFFFF;
            }
        }
        return new SceneImage(d, d, px);
    }

    /** A shield bubble: a translucent sphere with a bright rim and a highlight. */
    public static SceneImage bubble(int radius) {
        int d = radius * 2 + 1;
        int[] px = new int[d * d];
        for (int y = 0; y < d; y++) {
            for (int x = 0; x < d; x++) {
                double dx = x - radius;
                double dy = y - radius;
                double r = Math.hypot(dx, dy) / radius;
                if (r > 1.0) continue;
                double rim = Math.pow(r, 6);
                int a = (int) (60 + 170 * rim);
                double hl = Math.hypot(dx + radius * 0.4, dy + radius * 0.45) / radius;
                int white = hl < 0.18 ? (int) (255 * (1 - hl / 0.18)) : 0;
                a = Math.min(255, a + white);
                px[y * d + x] = (a << 24) | 0xFFFFFF;
            }
        }
        return new SceneImage(d, d, px);
    }

    /** The checkered wrecking ball of the original boss, shaded. */
    public static SceneSprite wreckingBall(int radius) {
        int d = radius * 2 + 1;
        int[] px = new int[d * d];
        for (int y = 0; y < d; y++) {
            for (int x = 0; x < d; x++) {
                double dx = (x - radius) / (double) radius;
                double dy = (y - radius) / (double) radius;
                double r = Math.hypot(dx, dy);
                if (r > 1.0) continue;
                double z = Math.sqrt(1 - r * r);
                // Checks wrapped on the sphere.
                double u = Math.atan2(dx, z) / Math.PI * 4;
                double v = Math.asin(Math.max(-1, Math.min(1, dy))) / Math.PI * 4;
                boolean red = (((int) Math.floor(u + 8) + (int) Math.floor(v + 8)) & 1) == 0;
                double light = 0.35 + 0.65 * Math.max(0, (-dx * 0.5 - dy * 0.6 + z * 0.6));
                int base = red ? 0xD82020 : 0xF0F0F0;
                if (r > 0.92) light *= 0.5;
                px[y * d + x] = 0xFF000000 | shade(base, light);
            }
        }
        return new SceneSprite(new SceneImage(d, d, px), radius, radius);
    }

    /** A bomb: a dark sphere with a highlight and a short fuse. */
    public static SceneSprite bomb(int radius) {
        int d = radius * 2 + 1;
        int h = d + 5;
        int[] px = new int[d * h];
        for (int y = 0; y < d; y++) {
            for (int x = 0; x < d; x++) {
                double dx = (x - radius) / (double) radius;
                double dy = (y - radius) / (double) radius;
                double r = Math.hypot(dx, dy);
                if (r > 1.0) continue;
                double light = 0.25 + 0.75 * Math.max(0, -dx * 0.5 - dy * 0.7);
                int c = shade(0x5060A0, light);
                if (r > 0.9) c = 0x101018;
                px[(y + 5) * d + x] = 0xFF000000 | c;
            }
        }
        for (int i = 0; i < 5; i++) px[i * d + radius + (i < 2 ? 1 : 0)] = 0xFFC8A060;
        return new SceneSprite(new SceneImage(d, h, px), radius, radius + 5);
    }

    /** A missile pointing right. */
    public static SceneSprite missile() {
        int w = 20;
        int h = 9;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double cy = Math.abs(y - 4) / 4.0;
                int c = 0;
                if (x >= 15) { if (cy <= (19 - x) / 4.0) c = 0xFFD02020; }
                else if (x >= 3 && cy <= 0.75) c = 0xFF000000 | shade(0xC0C8D8, 1.1 - cy * 0.6);
                else if (x < 4 && (y <= 1 || y >= 7)) c = 0xFF505870;
                px[y * w + x] = c;
            }
        }
        return new SceneSprite(new SceneImage(w, h, px), 10, 4);
    }

    /** A flame tongue pointing down, {@code length} long, in one of a few flicker phases. */
    public static SceneSprite flame(int width, int length, int phase) {
        int[] px = new int[width * length];
        java.util.Random random = new java.util.Random(phase * 977L + width);
        for (int y = 0; y < length; y++) {
            double t = y / (double) length;
            double half = width / 2.0 * (1 - t * t) * (0.85 + random.nextDouble() * 0.3);
            for (int x = 0; x < width; x++) {
                double dx = Math.abs(x - (width - 1) / 2.0);
                if (dx > half) continue;
                double core = 1 - dx / Math.max(0.5, half);
                int c = t < 0.25 && core > 0.4 ? 0xFFFFFFC0 : core > 0.55 ? 0xFFFFE040 : 0xFFF06010;
                if (t > 0.7) c = (int) (255 * (1 - t) / 0.3) << 24 | (c & 0xFFFFFF);
                px[y * width + x] = c;
            }
        }
        return new SceneSprite(new SceneImage(width, length, px), width / 2, 0);
    }

    /** A spring: red base, yellow coil top. */
    public static SceneSprite spring() {
        int w = 22;
        int h = 14;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = 0;
                if (y < 4) c = 0xFFF8D010;
                else if (y < 10) c = (y % 2 == 0 && x > 3 && x < w - 4) ? 0xFFB0B0C0 : (x > 5 && x < w - 6 ? 0xFF606878 : 0);
                else c = 0xFFD02020;
                if (y == 0 || y == h - 1) c = c == 0 ? 0 : 0xFF301010;
                px[y * w + x] = c;
            }
        }
        return new SceneSprite(new SceneImage(w, h, px), w / 2, h);
    }

    /** A soft elliptical shadow. */
    public static SceneImage shadow(int width, int height) {
        int[] px = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double dx = (x - (width - 1) / 2.0) / (width / 2.0);
                double dy = (y - (height - 1) / 2.0) / (height / 2.0);
                double r = dx * dx + dy * dy;
                if (r > 1) continue;
                px[y * width + x] = ((int) (110 * (1 - r)) << 24);
            }
        }
        return new SceneImage(width, height, px);
    }

    /** A four-pointed sparkle star. */
    public static SceneImage sparkle(int size) {
        int d = size * 2 + 1;
        int[] px = new int[d * d];
        for (int y = 0; y < d; y++) {
            for (int x = 0; x < d; x++) {
                double dx = Math.abs(x - size);
                double dy = Math.abs(y - size);
                double v = Math.max(0, 1 - (dx * dy) / (size * 0.6) - Math.hypot(dx, dy) / (size + 1) * 0.6);
                if (dx > 0 && dy > 0 && dx * dy > size * 0.6) continue;
                int a = (int) Math.min(255, 255 * v * 1.4);
                if (a > 8) px[y * d + x] = (a << 24) | 0xFFFFFF;
            }
        }
        return new SceneImage(d, d, px);
    }

    /** A thin speed line, white, fading to its tail. */
    public static SceneImage streak(int length) {
        int[] px = new int[length * 3];
        for (int x = 0; x < length; x++) {
            int a = 255 * x / length;
            px[length + x] = (a << 24) | 0xFFFFFF;
            px[x] = px[2 * length + x] = ((a / 3) << 24) | 0xFFFFFF;
        }
        return new SceneImage(length, 3, px);
    }

    static int shade(int rgb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 255) * f));
        int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 255) * f));
        int b = (int) Math.max(0, Math.min(255, (rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Recolours a sprite: pixels within {@code range} degrees of hue {@code center} (and with some
     * saturation) are rotated by {@code shift} degrees and their saturation and brightness scaled.
     */
    public static SceneSprite recolor(SceneSprite sprite, double center, double range, double shift, double satMul,
            double valMul) {
        SceneImage img = sprite.image();
        int[] px = img.pixels();
        float[] hsv = new float[3];
        for (int i = 0; i < px.length; i++) {
            int argb = px[i];
            if ((argb >>> 24) == 0) continue;
            int r = (argb >> 16) & 255;
            int g = (argb >> 8) & 255;
            int b = argb & 255;
            toHsv(r, g, b, hsv);
            double hue = hsv[0] * 360;
            double diff = Math.abs(((hue - center) % 360 + 540) % 360 - 180);
            if (hsv[1] < 0.28 || diff > range) continue;
            double nh = ((hue + shift) % 360 + 360) % 360 / 360.0;
            float ns = (float) Math.max(0, Math.min(1, hsv[1] * satMul));
            float nv = (float) Math.max(0, Math.min(1, hsv[2] * valMul));
            px[i] = (argb & 0xFF000000) | fromHsv(nh, ns, nv);
        }
        return new SceneSprite(new SceneImage(img.width(), img.height(), px), sprite.originX(), sprite.originY());
    }

    /** RGB (0-255) to hue (0-1), saturation and value. */
    static void toHsv(int r, int g, int b, float[] out) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        float v = max / 255f;
        float s = max == 0 ? 0 : (max - min) / (float) max;
        float h = 0;
        if (max != min) {
            float d = max - min;
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6;
            if (h < 0) h += 1;
        }
        out[0] = h;
        out[1] = s;
        out[2] = v;
    }

    /** Hue (0-1), saturation, value to 0xRRGGBB. */
    static int fromHsv(double h, double s, double v) {
        double c = v * s;
        double hp = (h * 6) % 6;
        double x = c * (1 - Math.abs(hp % 2 - 1));
        double r = 0, g = 0, b = 0;
        if (hp < 1) { r = c; g = x; }
        else if (hp < 2) { r = x; g = c; }
        else if (hp < 3) { g = c; b = x; }
        else if (hp < 4) { g = x; b = c; }
        else if (hp < 5) { r = x; b = c; }
        else { r = c; b = x; }
        double m = v - c;
        int ri = (int) Math.round((r + m) * 255);
        int gi = (int) Math.round((g + m) * 255);
        int bi = (int) Math.round((b + m) * 255);
        return (ri << 16) | (gi << 8) | bi;
    }
}
