package com.openggf.game.internal;

/**
 * A level animator that presents the level's own palette fade from black.
 *
 * <p>A host that would otherwise reveal a freshly loaded level with a
 * whole-screen fade leaves that reveal to the level while it is fading, so
 * the palette lines it owns are never faded twice.
 */
public interface LevelPaletteFadeOwner {
    /** Whether the level is fading its own palette in from black on this frame. */
    boolean presentsLevelFadeFromBlack();
}
