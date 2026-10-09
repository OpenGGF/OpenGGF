package com.openggf.mods.scene;

import java.util.Objects;

/**
 * An RGBA picture a scene can draw: decoded from a PNG, built from pixels in code, or
 * rasterised from ROM sprite art. Pixels are {@code 0xAARRGGBB}; alpha 0 is transparent. Each
 * side is 1 to 4096 pixels.
 *
 * <p>Images are immutable, except {@link #streaming} ones. The engine uploads each one to the
 * GPU the first time it is drawn, and deletes the GPU copy once the image has gone about two
 * seconds (120 frames) without being drawn, or when the scene closes; drawing it again uploads
 * it again. So build images once (in {@link ModScene#enter} or lazily) and reuse them: an image
 * made every frame is uploaded every frame.
 *
 * <p>For pictures a scene renders itself every frame (software 3D, warp effects, a planet
 * turning), make one {@link #streaming} image and {@link #update} its pixels in
 * {@link ModScene#update}: the engine re-uploads it into the same GPU texture the next time
 * it is drawn after a change.
 */
@com.openggf.game.ModApi
public final class SceneImage {
    private final int width;
    private final int height;
    private final int[] argb;
    private final boolean streaming;
    private long revision;

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
        this.streaming = false;
    }

    private SceneImage(int width, int height) {
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
            throw new IllegalArgumentException("Image size out of range: " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.argb = new int[width * height];
        this.streaming = true;
    }

    /**
     * A transparent image whose pixels the scene replaces with {@link #update}, for pictures it
     * renders itself every frame. Keep and reuse it: each one holds a GPU texture while drawn.
     *
     * @throws IllegalArgumentException when a side is outside 1-4096
     */
    public static SceneImage streaming(int width, int height) {
        return new SceneImage(width, height);
    }

    /** Whether this image came from {@link #streaming} and accepts {@link #update}. */
    public boolean isStreaming() {
        return streaming;
    }

    /**
     * Replaces every pixel of a {@link #streaming} image ({@code width * height}, row by row).
     * Call it from {@link ModScene#update}, not {@code draw}.
     *
     * @throws IllegalStateException    when the image is not streaming
     * @throws IllegalArgumentException when the pixel count differs
     */
    public void update(int[] pixels) {
        if (!streaming) {
            throw new IllegalStateException("Only streaming images can be updated");
        }
        Objects.requireNonNull(pixels, "pixels");
        if (pixels.length != argb.length) {
            throw new IllegalArgumentException("Expected " + argb.length + " pixels, got " + pixels.length);
        }
        System.arraycopy(pixels, 0, argb, 0, argb.length);
        revision++;
    }

    /** How many times {@link #update} has replaced the pixels; 0 for ordinary images. */
    public long revision() {
        return revision;
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
