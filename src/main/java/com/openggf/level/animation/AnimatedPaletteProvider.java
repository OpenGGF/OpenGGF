package com.openggf.level.animation;

import com.openggf.level.Level;

import java.io.IOException;

/**
 * Provides palette animation managers for a given level/zone.
 */
public interface AnimatedPaletteProvider {
    AnimatedPaletteManager loadAnimatedPaletteManager(Level level, int zoneIndex) throws IOException;
}
