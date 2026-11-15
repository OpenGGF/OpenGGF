package com.openggf.level;

import com.openggf.graphics.GraphicsManager;

/**
 * Runs art registration inside one pattern-atlas batch, so the atlas stages each
 * dirty page once at the end instead of once per 8x8 tile. The work must not draw:
 * tiles written inside the batch reach the GPU only when it ends.
 */
final class PatternAtlasBatchScope {
    private PatternAtlasBatchScope() { }

    static void run(GraphicsManager graphics, Runnable work) {
        graphics.beginPatternAtlasBatch();
        try {
            work.run();
        } finally {
            graphics.endPatternAtlasBatch();
        }
    }
}
