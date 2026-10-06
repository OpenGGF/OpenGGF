package com.openggf.mods.scene;

/**
 * How to draw an image: scale, mirror, tint and flash. Start from {@link #plain()} and chain
 * the {@code with...} methods; each returns a new value (styles are immutable, so keep and
 * share them freely).
 *
 * <ul>
 *   <li>{@code tint} multiplies every pixel ({@code 0xAARRGGBB}); its alpha fades the image.
 *       {@code 0xFFFFFFFF} leaves the image unchanged.</li>
 *   <li>{@code flash} mixes the image towards a solid colour by the flash colour's alpha
 *       ({@code 0xFFFFFFFF} turns the sprite pure white, {@code 0x80FF0000} half red),
 *       keeping the image's own transparency: a hit flash or a silhouette.</li>
 * </ul>
 *
 * <pre>{@code
 * canvas.draw(sprite, x, y, SceneDraw.plain().withFlipX(facingLeft).withFlash(hit ? 0xC0FFFFFF : 0));
 * }</pre>
 */
@com.openggf.game.ModApi
public final class SceneDraw {
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final SceneDraw PLAIN = new SceneDraw(1f, 1f, false, false, OPAQUE_WHITE, 0);

    private final float scaleX;
    private final float scaleY;
    private final boolean flipX;
    private final boolean flipY;
    private final int tint;
    private final int flash;

    private SceneDraw(float scaleX, float scaleY, boolean flipX, boolean flipY, int tint, int flash) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.flipX = flipX;
        this.flipY = flipY;
        this.tint = tint;
        this.flash = flash;
    }

    /** Unscaled, unmirrored, untinted, no flash. */
    public static SceneDraw plain() {
        return PLAIN;
    }

    public float scaleX() {
        return scaleX;
    }

    public float scaleY() {
        return scaleY;
    }

    public boolean flipX() {
        return flipX;
    }

    public boolean flipY() {
        return flipY;
    }

    /** The tint, {@code 0xAARRGGBB}. */
    public int tint() {
        return tint;
    }

    /** The flash colour, {@code 0xAARRGGBB}; alpha 0 is no flash. */
    public int flash() {
        return flash;
    }

    /** The same scale on both axes. */
    public SceneDraw withScale(float scale) {
        return new SceneDraw(scale, scale, flipX, flipY, tint, flash);
    }

    public SceneDraw withScale(float sx, float sy) {
        return new SceneDraw(sx, sy, flipX, flipY, tint, flash);
    }

    public SceneDraw withFlipX(boolean value) {
        return new SceneDraw(scaleX, scaleY, value, flipY, tint, flash);
    }

    public SceneDraw withFlipY(boolean value) {
        return new SceneDraw(scaleX, scaleY, flipX, value, tint, flash);
    }

    /** Replaces the whole tint, alpha included. */
    public SceneDraw withTint(int argb) {
        return new SceneDraw(scaleX, scaleY, flipX, flipY, argb, flash);
    }

    /**
     * Multiplies alpha (0-1) into the current tint's alpha. Order matters: a later
     * {@link #withTint} replaces the whole tint, alpha included, so call this after it.
     */
    public SceneDraw withAlpha(float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((tint >>> 24) & 0xFF) * alpha)));
        return new SceneDraw(scaleX, scaleY, flipX, flipY, (a << 24) | (tint & 0xFFFFFF), flash);
    }

    public SceneDraw withFlash(int argb) {
        return new SceneDraw(scaleX, scaleY, flipX, flipY, tint, argb);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SceneDraw o && Float.compare(scaleX, o.scaleX) == 0
                && Float.compare(scaleY, o.scaleY) == 0 && flipX == o.flipX && flipY == o.flipY
                && tint == o.tint && flash == o.flash;
    }

    @Override
    public int hashCode() {
        int h = Float.hashCode(scaleX);
        h = 31 * h + Float.hashCode(scaleY);
        h = 31 * h + (flipX ? 1 : 0);
        h = 31 * h + (flipY ? 1 : 0);
        h = 31 * h + tint;
        return 31 * h + flash;
    }

    @Override
    public String toString() {
        return "SceneDraw[scale " + scaleX + "x" + scaleY + (flipX ? " flipX" : "") + (flipY ? " flipY" : "")
                + " tint " + Integer.toHexString(tint) + " flash " + Integer.toHexString(flash) + "]";
    }
}
