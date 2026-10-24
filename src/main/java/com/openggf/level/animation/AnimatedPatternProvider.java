package com.openggf.level.animation;

import com.openggf.level.Level;

import java.io.IOException;

/**
 * Provides zone animated pattern managers for a game.
 */
public interface AnimatedPatternProvider {
    AnimatedPatternManager loadAnimatedPatternManager(Level level, int zoneIndex) throws IOException;
}
