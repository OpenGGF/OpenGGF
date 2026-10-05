package com.openggf.mods.scene;

/**
 * How to draw an image: scale, mirror, tint and flash. Start from {@link #plain()} and chain
 * the {@code with...} methods; each returns a new value.
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
public record SceneDraw(float scaleX, float scaleY, boolean flipX, boolean flipY, int tint, int flash) {
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    public static SceneDraw plain() {
        return new SceneDraw(1f, 1f, false, false, OPAQUE_WHITE, 0);
    }

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

    public SceneDraw withTint(int argb) {
        return new SceneDraw(scaleX, scaleY, flipX, flipY, argb, flash);
    }

    /** Multiplies alpha (0-1) into the tint. */
    public SceneDraw withAlpha(float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((tint >>> 24) & 0xFF) * alpha)));
        return new SceneDraw(scaleX, scaleY, flipX, flipY, (a << 24) | (tint & 0xFFFFFF), flash);
    }

    public SceneDraw withFlash(int argb) {
        return new SceneDraw(scaleX, scaleY, flipX, flipY, tint, argb);
    }
}
