package com.openggf.level.render;

import com.openggf.level.Pattern;

/**
 * Common sprite sheet contract for pattern-based renderers.
 */
@com.openggf.game.ModApi
public interface SpriteSheet<F extends SpriteFrame<? extends SpriteFramePiece>> {
    Pattern[] getPatterns();
    int getFrameCount();
    F getFrame(int index);
    int getPaletteIndex();
    int getFrameDelay();
}
