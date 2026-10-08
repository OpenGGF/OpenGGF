package com.openggf.mods.scene;

import java.util.Objects;

/**
 * An RGBA picture a scene can draw: decoded from a PNG, built from pixels in code, or
 * rasterised from ROM sprite art. Pixels are {@code 0xAARRGGBB}; alpha 0 is transparent. Each
 * side is 1 to 4096 pixels.
 *
 * <p>Images are immutable. The engine uploads each one to the GPU the first time it is drawn,
 * and deletes the GPU copy once the image has gone about two seconds (120 frames) without
 * being drawn, or when the scene closes; drawing it again uploads it again. So build images
 * once (in {@link ModScene#enter} or lazily) and reuse them: an image made every frame is
 * uploaded every frame.
 */
@com.openggf.game.ModApi
public final class SceneImage {
    private final int width;
    private final int height;
    private final int[] argb;

    /**
     * Copies {@code argb} ({@code width * height} pixels, row by row from the top).
     *
     * @throws IllegalArgumentException when a side is outside 1-4096 or the pixel count differs
     */
    public SceneImage(int width, int height, int[] argb) {
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
            throw new IllegalArgumentException("Image size out of range: " + width + "x" + height);
        }
        Objects.requireNonNull(argb, "argb");
        if (argb.length != width * height) {
            throw new IllegalArgumentException("Expected " + width * height + " pixels, got " + argb.length);
        }
        this.width = width;
        this.height = height;
        this.argb = argb.clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** One pixel as {@code 0xAARRGGBB}; out-of-range coordinates are transparent. */
    public int pixel(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return argb[y * width + x];
    }

    /** A copy of all pixels. */
    public int[] pixels() {
        return argb.clone();
    }

    /**
     * A new {@code w} x {@code h} image cut from this one at ({@code x}, {@code y}); parts of the
     * rectangle outside this image come out transparent.
     */
    public SceneImage crop(int x, int y, int w, int h) {
        int[] out = new int[w * h];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                out[row * w + col] = pixel(x + col, y + row);
            }
        }
        return new SceneImage(w, h, out);
    }
}
