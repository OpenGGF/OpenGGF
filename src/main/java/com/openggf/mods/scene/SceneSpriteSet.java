package com.openggf.mods.scene;

/**
 * All frames of one ROM sprite. Frames are rasterised on first use and cached, so asking
 * for a 250-frame character costs nothing until a frame is drawn.
 */
@com.openggf.game.ModApi
public interface SceneSpriteSet {
    int frameCount();

    /**
     * One frame, rasterised on first use; empty frames and indices outside
     * {@code 0 .. frameCount() - 1} come back as a 1x1 transparent sprite.
     */
    SceneSprite frame(int index);

    /**
     * The frames of a ROM animation script (characters only, by animation id such as
     * {@code 0x05} for waiting), or an empty array when unknown.
     */
    int[] animationFrames(int animationId);

    /**
     * Ticks per frame of that animation script: its first (delay) byte plus one, as the ROM's
     * animation routine counts it, or 8 when unknown. The players' speed-driven scripts
     * (walking, running, rolling, pushing: delay bytes {@code $FF}, {@code $FE}, {@code $FD})
     * have no fixed rate, because the ROM derives their delay from ground speed each frame;
     * for those this returns 256, 255 or 254 and a scene should pick its own rate.
     */
    int animationDelay(int animationId);
}
