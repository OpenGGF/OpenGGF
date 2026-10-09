package com.openggf.mods.scene;

/**
 * An act's building blocks, for scenes that assemble terrain of their own from a stock level:
 * the foreground layout as a grid of <em>layout blocks</em> (128 pixels square; 256 in Sonic 1),
 * each block drawn on its own with transparent sky, each block's collision as a pixel mask, the
 * playable area, the zone's background and its palette. Blocks that sit side by side in the
 * stock layout join seamlessly, so new arrangements that keep to the act's own neighbours look
 * like the zone. From {@link SceneRomArt#levelKit}; built from the level data without starting a
 * level, and never touches a running one.
 *
 * <pre>{@code
 * SceneLevelKit kit = rom.levelKit(1, 0);                  // Sonic 2 Chemical Plant act 1
 * int id = kit.block(10, 3);                               // the block at layout cell (10, 3)
 * SceneImage picture = kit.blockImage(id);                 // its art, 128x128
 * byte[] solid = kit.blockSolidity(id);                    // its collision, row by row
 * boolean floor = solid[64 * kit.blockSize() + 20] != SceneLevelKit.EMPTY;
 * }</pre>
 *
 * <p>Pictures are the art the act loads with (animated tiles at their first frame, no objects,
 * rings or water tint; both tile priorities). Not thread-safe: use it only from the scene's own
 * calls. The engine implements this interface; mods use it.
 */
@com.openggf.game.ModApi
public interface SceneLevelKit {
    /** {@link #blockSolidity} value: nothing solid. */
    byte EMPTY = 0;
    /** {@link #blockSolidity} value: a floor that holds from above only (a platform). */
    byte TOP_SOLID = 1;
    /** {@link #blockSolidity} value: solid from every side. */
    byte SOLID = 2;

    /** The side of one layout block in pixels: 128, or 256 in Sonic 1. */
    int blockSize();

    /** Foreground layout columns. */
    int columns();

    /** Foreground layout rows. */
    int rows();

    /** How many blocks the act has; ids run from 0, and block 0 is empty. */
    int blockCount();

    /** The block id at a foreground layout cell, or 0 outside the layout. */
    int block(int column, int row);

    /**
     * One block drawn on its own, {@link #blockSize} pixels square, transparent where the
     * plane shows the background. Built on first request and cached; block 0 and ids out of
     * range are fully transparent.
     */
    SceneImage blockImage(int block);

    /**
     * One block's collision on the primary path as the floor sensors see it,
     * {@code blockSize * blockSize} values row by row: {@link #EMPTY}, {@link #TOP_SOLID} or
     * {@link #SOLID}. A fresh copy each call (cache it).
     */
    byte[] blockSolidity(int block);

    /**
     * The area of the layout the camera can show in the stock act,
     * {@code {x, y, width, height}} in level pixels.
     */
    int[] playableArea();

    /**
     * The zone's background with parallax bands: its zone backdrop where
     * {@link SceneRomArt#hasZonePictures} has one, otherwise its background plane as laid out
     * (at most 1024x512) in a single band. Cached.
     */
    SceneBackdrop backdrop();

    /** The four palette lines the act loads with: 64 {@code 0xFFRRGGBB} colours. */
    int[] palette();
}
