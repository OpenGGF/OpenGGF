package com.openggf.game;

/**
 * A results screen drawn over the currently loaded level instead of a blank backdrop.
 *
 * <p>S3K's {@code SpecialStage_Results} rebuilds the Hidden Palace sanctuary behind a Super
 * Emerald results screen and scrolls it; the renderer draws that level's tiles and objects
 * (never the player sprites or HUD) at the live camera before the results sprites.
 */
public interface LevelBackdropResultsScreen {
    /** Whether the loaded level should be drawn behind the results this frame. */
    boolean drawsLevelBackdrop();

    /** Called on the render thread before the backdrop level pass, e.g. to upload palettes. */
    default void prepareLevelBackdropDraw() {
    }
}
