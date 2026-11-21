package com.openggf.mods.scene;

import com.openggf.catalog.StockGameCatalog;
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

    /** A bounded file view from the active stock medium (for CD, an ISO program name).
     * Addresses in the returned view are file-relative; no bytes come from mod assets. */
    default SceneRomArt program(String gameId, String assetPath) {
        throw new IllegalArgumentException("No file-addressed scene art for " + gameId);
    }


    /**
     * Stock games whose art is available for this scene. The host resolves supplied
     * cartridges and discs. Installing a mod cannot supply a missing game's assets.
     */
    default java.util.List<String> availableGames() {
        SceneRomArt running = rom();
        return running == null ? java.util.List.of() : java.util.List.of(running.gameId());
    }

    /**
     * Art from one supplied stock ROM without changing the running game. Returns
     * {@code null} when that game is unavailable. Unknown game codes are rejected.
     */
    default SceneRomArt rom(String gameId) {
        if (!StockGameCatalog.isStockCode(gameId)) {
            throw new IllegalArgumentException("Unknown stock game: " + gameId);
        }
        SceneRomArt running = rom();
        return running != null && gameId.equals(running.gameId()) ? running : null;
    }
}
