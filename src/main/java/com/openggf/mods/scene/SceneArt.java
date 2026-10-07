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

    /**
     * Stock games whose ROM art is available for this scene, in {@code s1}, {@code s2},
     * {@code s3k} order. The host resolves supplied images through its ROM catalogue;
     * installing a mod never supplies a missing game's assets.
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
        if (gameId == null || !java.util.List.of("s1", "s2", "s3k").contains(gameId)) {
            throw new IllegalArgumentException("Unknown stock game: " + gameId);
        }
        SceneRomArt running = rom();
        return running != null && gameId.equals(running.gameId()) ? running : null;
    }
}
