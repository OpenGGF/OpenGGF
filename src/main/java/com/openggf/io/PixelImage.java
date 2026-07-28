package com.openggf.io;

import java.util.Objects;

/**
 * A decoded ARGB image.
 *
 * <p>Deliberately not {@code java.awt.image.BufferedImage}: AWT is barred from
 * production so native images stay buildable, and the mod SDK only ever needs
 * per-pixel ARGB access over a known-size surface.
 */
public final class PixelImage {

    private final int width;
    private final int height;
    private final int[] argb;

    public PixelImage(int width, int height, int[] argb) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("image must have positive extents");
        }
        Objects.requireNonNull(argb, "argb");
        if (argb.length != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("argb length does not match extents");
        }
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    public static PixelImage blank(int width, int height) {
        return new PixelImage(width, height, new int[Math.multiplyExact(width, height)]);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** Packed {@code 0xAARRGGBB}, matching what the AWT accessor returned. */
    public int getRGB(int x, int y) {
        checkBounds(x, y);
        return argb[y * width + x];
    }

    public void setRGB(int x, int y, int value) {
        checkBounds(x, y);
        argb[y * width + x] = value;
    }

    /** The backing pixels, row-major. Not copied: this is an internal SDK type. */
    public int[] pixels() {
        return argb;
    }

    private void checkBounds(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException("pixel " + x + "," + y
                    + " is outside " + width + "x" + height);
        }
    }
}
