package com.openggf.mods.scene;

/**
 * Makes {@link SceneImage}s. Mod assets are read during registration
 * ({@code ModContext.modAssets()}) and decoded here at runtime:
 *
 * <pre>{@code
 * // In GgfMod.register:
 * byte[] cardsPng = context.modAssets().readBounded("art/cards.png", 1 << 20);
 * context.registerStartupScene(() -> new MyScene(cardsPng));
 *
 * // In MyScene.enter:
 * cards = ctx.art().png(cardsPng);
 * }</pre>
 */
@com.openggf.game.ModApi
public interface SceneArt {
    /** Decodes a PNG. Throws {@link IllegalArgumentException} for invalid data. */
    SceneImage png(byte[] pngBytes);

    /** An image from {@code width * height} {@code 0xAARRGGBB} pixels. */
    SceneImage image(int width, int height, int[] argb);

    /** ROM art for the running game, or {@code null} when no ROM is loaded (standalone games). */
    SceneRomArt rom();
}
