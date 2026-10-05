package com.openggf.mods.scene;

/**
 * All frames of one ROM sprite. Frames are rasterised on first use and cached, so asking
 * for a 250-frame character costs nothing until a frame is drawn.
 */
@com.openggf.game.ModApi
public interface SceneSpriteSet {
    int frameCount();

    /** One frame; empty frames come back as a 1x1 transparent sprite. */
    SceneSprite frame(int index);

    /**
     * The frames of a ROM animation script (characters only, by animation id such as
     * {@code 0x05} for waiting), or an empty array when unknown.
     */
    int[] animationFrames(int animationId);

    /** Ticks per frame of that animation script (its first byte), or 8 when unknown. */
    int animationDelay(int animationId);
}
