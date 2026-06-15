package com.openggf.level;

/**
 * Advances level-owned runtime state that must tick during logic frames, even
 * when no renderer is active.
 */
final class LevelFrameRuntimeUpdater {
    private final LevelManager levelManager;

    LevelFrameRuntimeUpdater(LevelManager levelManager) {
        this.levelManager = levelManager;
    }

    void updateParallaxAndAnimatedContent() {
        int bgScrollY = computeBackgroundScrollY();
        if (levelManager.parallaxManager != null) {
            levelManager.parallaxManager.update(
                    levelManager.currentZone,
                    levelManager.currentAct,
                    levelManager.camera,
                    levelManager.frameCounter,
                    bgScrollY,
                    levelManager.level);
        }
        if (levelManager.animatedPatternManager != null) {
            levelManager.animatedPatternManager.update();
        }
        if (levelManager.animatedPaletteManager != null
                && levelManager.animatedPaletteManager != levelManager.animatedPatternManager) {
            levelManager.animatedPaletteManager.update();
        }
        if (levelManager.camera != null && levelManager.parallaxManager != null) {
            levelManager.camera.setShakeOffsets(
                    levelManager.parallaxManager.getShakeOffsetX(),
                    levelManager.parallaxManager.getShakeOffsetY());
        }
    }

    int computeBackgroundScrollY() {
        int bgScrollY = levelManager.camera != null ? (int) (levelManager.camera.getY() * 0.1f) : 0;
        if (levelManager.camera == null
                || levelManager.game == null
                || levelManager.currentZone < 0
                || levelManager.currentZone >= levelManager.levels.size()
                || levelManager.currentAct < 0
                || levelManager.currentAct >= levelManager.levels.get(levelManager.currentZone).size()) {
            return bgScrollY;
        }
        int levelIdx = levelManager.levels
                .get(levelManager.currentZone)
                .get(levelManager.currentAct)
                .getLevelIndex();
        int[] scroll = levelManager.game.getBackgroundScroll(
                levelIdx,
                levelManager.camera.getX(),
                levelManager.camera.getY());
        return scroll[1];
    }

    void recomputeParallaxAfterRewindRestore() {
        if (levelManager.parallaxManager == null || levelManager.camera == null) {
            return;
        }
        int bgScrollY = computeBackgroundScrollY();
        levelManager.parallaxManager.update(
                levelManager.currentZone,
                levelManager.currentAct,
                levelManager.camera,
                levelManager.frameCounter,
                bgScrollY,
                levelManager.level);
        levelManager.camera.setShakeOffsets(
                levelManager.parallaxManager.getShakeOffsetX(),
                levelManager.parallaxManager.getShakeOffsetY());
    }
}
