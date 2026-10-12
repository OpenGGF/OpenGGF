package com.openggf.mods.scene;

/**
 * An independent ROM-backed background using the engine's stock scanline scrolling.
 * Create one per view with {@link SceneRomArt#levelBackground}; retain it while that view
 * is active. Its scroll state never reads or changes the running level's events or camera.
 * Art and palettes are the detached act's initial presentation, not a simulation of its
 * gameplay-triggered art changes. The engine owns the supported steady-state profiles.
 */
@com.openggf.game.ModApi
public interface SceneBackground {
    /**
     * Draw the complete 224-line viewport behind the scene's foreground, at native scale.
     * Camera coordinates are stock foreground-camera pixels, and ticks are scene ticks.
     * The native handler owns horizontal/vertical scrolling; callers must not scale its
     * input again. Wider canvases repeat the profile's populated background period.
     * Duplicate calls with the same inputs do not advance the scroll state.
     */
    void draw(SceneCanvas canvas, int cameraX, int cameraY, long ticks);
}
