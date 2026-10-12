package eggsky.core;

import com.openggf.mods.scene.SceneImage;
import java.util.HashMap;

/**
 * A planet's colour scheme: a hue rotation, saturation and brightness change and an optional
 * tint, applied to every colour of the zone's art and snapped back to the Mega Drive's 9-bit
 * palette. Zone art only uses a few dozen colours, so results are cached per colour.
 */
public final class Recolor {
    private final float hueShift;
    private final float saturation;
    private final float brightness;
    private final int tint;
    private final float tintAmount;
    private final HashMap<Integer, Integer> cache = new HashMap<>();

    public Recolor(float hueShift, float saturation, float brightness, int tint, float tintAmount) {
        this.hueShift = hueShift;
        this.saturation = saturation;
        this.brightness = brightness;
        this.tint = tint;
        this.tintAmount = tintAmount;
    }

    /** The zone's own colours. */
    public static Recolor identity() {
        return new Recolor(0, 1, 1, 0, 0);
    }

    public boolean isIdentity() {
        return hueShift == 0 && saturation == 1 && brightness == 1 && tintAmount == 0;
    }

    public float hueShift() {
        return hueShift;
    }

    /** This scheme with a different hue rotation (skies often differ from the ground). */
    public Recolor withHue(float hue) {
        return new Recolor(hue, saturation, brightness, tint, tintAmount);
    }

    public Recolor darker(float factor) {
        return new Recolor(hueShift, saturation, brightness * factor, tint, tintAmount);
    }

    public int apply(int argb) {
        int alpha = argb >>> 24;
        if (alpha == 0) {
            return 0;
        }
        if (isIdentity()) {
            return argb;
        }
        Integer cached = cache.get(argb);
        if (cached != null) {
            return cached;
        }
        float[] hsv = Colour.hsv(argb);
        int out = Colour.fromHsv(hsv[0] + hueShift, hsv[1] * saturation, hsv[2] * brightness, alpha);
        if (tintAmount > 0) {
            out = Colour.lerp(out, Colour.alpha(tint, alpha), tintAmount);
        }
        out = Colour.genesis(out);
        cache.put(argb, out);
        return out;
    }

    public int[] apply(int[] pixels) {
        int[] out = new int[pixels.length];
        int last = 0;
        int lastOut = 0;
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            if (p == 0) {
                continue;
            }
            if (p != last) {
                last = p;
                lastOut = apply(p);
            }
            out[i] = lastOut;
        }
        return out;
    }

    public SceneImage apply(SceneImage image) {
        if (isIdentity()) {
            return image;
        }
        return new SceneImage(image.width(), image.height(), apply(image.pixels()));
    }
}
