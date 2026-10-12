package com.openggf.level;

import com.openggf.graphics.GraphicsManager;

/** Publishes an additive act's decoded CPU graphics before its tilemaps are built. */
final class LevelGraphicsPublisher {
    private LevelGraphicsPublisher() {
    }

    static void publish(Level level, GraphicsManager graphicsManager) {
        if (!graphicsManager.isGlInitialized()) return;
        for (int line = 0; line < level.getPaletteCount(); line++) {
            graphicsManager.cachePaletteTexture(level.getPalette(line), line);
        }
        graphicsManager.beginPatternAtlasBatch();
        try {
            for (int index = 0; index < level.getPatternCount(); index++) {
                Pattern pattern = level.getPattern(index);
                if (pattern != null) graphicsManager.cachePatternTexture(pattern, index);
            }
        } finally {
            graphicsManager.endPatternAtlasBatch();
        }
    }
}
