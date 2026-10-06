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

    /**
     * An image from {@code width * height} {@code 0xAARRGGBB} pixels, row by row from the top
     * (copied). Each side is 1 to 4096 pixels.
     */
    SceneImage image(int width, int height, int[] argb);

    /**
     * ROM art for the running game, or {@code null} when the engine could not prepare it (for
     * example the ROM could not be read); the engine logs why. Startup scenes belong to patch
     * mods, so a ROM is always loaded.
     */
    SceneRomArt rom();
}
