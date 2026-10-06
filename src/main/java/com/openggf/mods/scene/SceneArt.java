package com.openggf.mods.scene;

/**
 * Decodes images: PNGs from the mod's own files, and art from the player's ROM. (Images built
 * from pixels in code are plain {@code new SceneImage(width, height, argb)}.) Mod assets are
 * read during registration ({@code ModContext.modAssets()}) and decoded here at runtime:
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
     * ROM art for the running game, or {@code null} when the engine could not prepare it (for
     * example the ROM could not be read); the engine logs why. Startup scenes belong to patch
     * mods, so a ROM is always loaded.
     */
    SceneRomArt rom();
}
