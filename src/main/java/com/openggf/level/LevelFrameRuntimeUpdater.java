package com.openggf.level;

import com.openggf.game.OscillationManager;
import com.openggf.level.animation.StageRingAnimationFrameProvider;
import com.openggf.level.rings.RingManagerInternalAccess;

/**
 * Advances level-owned runtime state that must tick during logic frames, even
 * when no renderer is active.
 */
final class LevelFrameRuntimeUpdater {
    private final LevelManager levelManager;

    LevelFrameRuntimeUpdater(LevelManager levelManager) {
        this.levelManager = levelManager;
    }

    void advanceGlobalOscillation() {
        if (OscillationManager.consumeSuppressedUpdate(levelManager.frameCounter)) {
            return;
        }
        int featureZone = levelManager.getFeatureZoneId();
        int featureAct = levelManager.getFeatureActId();
        if (levelManager.zoneFeatureProvider != null
                && !levelManager.zoneFeatureProvider.shouldAdvanceGlobalOscillation(
                        featureZone, featureAct)) {
            return;
        }
        OscillationManager.update(levelManager.frameCounter);
    }

    void updateLevelState(com.openggf.sprites.playable.AbstractPlayableSprite playable,
            com.openggf.game.session.WorldSession worldSession) {
        if (levelManager.levelGamestate != null) {
            var roster = com.openggf.game.competition.NativeCompetitionRoster.from(worldSession);
            if (roster == null) {
                if (!levelManager.isHudSuppressed()) levelManager.levelGamestate.update();
                if (levelManager.levelGamestate.isTimeOver() && playable != null && !playable.getDead()) {
                    playable.applyHurtOrDeath(0, com.openggf.game.DamageCause.TIME_OVER, false);
                }
            } else {
                // One canonical world tick owns both native timer banks. Split
                // rendering never advances either timer or global animation.
                for (int slot = 0; slot < 2; slot++) {
                    var actor = roster.player(slot);
                    var state = roster.levelState(slot);
                    if (actor == null || state == null) continue;
                    if (!levelManager.isHudSuppressed()) state.update();
                    if (state.isTimeOver() && !actor.getDead()) {
                        actor.applyHurtOrDeath(0, com.openggf.game.DamageCause.TIME_OVER, false);
                    }
                }
            }
        }
    }

    void updateParallaxAndAnimatedContent() {
        if (levelManager.parallaxManager != null) {
            levelManager.parallaxManager.update(
                    levelManager.currentZone,
                    levelManager.currentAct,
                    levelManager.camera,
                    levelManager.frameCounter,
                    levelManager.level);
        }
        if (levelManager.animatedPatternManager != null) {
            levelManager.animatedPatternManager.update();
        }
        levelManager.updateContributedAnimatedTiles();
        // Native powered-form palettes are world logic, after RunObjects and
        // before the zone palette owner. Rewind's derived-output refresh below
        // never enters this method and must not advance these captured timers.
        if (levelManager.spriteManager != null) {
            for (var sprite : levelManager.spriteManager.getAllSprites()) {
                if (sprite instanceof com.openggf.sprites.playable.AbstractPlayableSprite playable
                        && playable.getSuperStateController() instanceof
                        com.openggf.game.internal.NativeSuperStateBeforeAnimation nativeOwner) {
                    nativeOwner.updateNativeGlobalPaletteAfterObjects();
                }
            }
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

    void updateSharedSpillAnimation() {
        if (levelManager.animatedPatternManager instanceof StageRingAnimationFrameProvider provider
                && !provider.advancesSharedSpillAnimation()) {
            RingManagerInternalAccess.updateLostRingHousekeeping(levelManager.ringManager);
        } else {
            levelManager.ringManager.updateLostRingPhysics(levelManager.frameCounter + 1);
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
                .levelIndex();
        int[] scroll = levelManager.gameModule.getBackgroundScrollOverride(
                levelIdx, levelManager.camera.getX(), levelManager.camera.getY());
        if (scroll == null) {
            scroll = levelManager.game.getBackgroundScroll(
                    levelIdx, levelManager.camera.getX(), levelManager.camera.getY());
        }
        return scroll[1];
    }

    void recomputeParallaxAfterRewindRestore() {
        refreshParallaxState(true);
    }

    void recomputeParallaxOnlyForCurrentFrame() {
        refreshParallaxState();
    }

    void refreshParallaxState() {
        refreshParallaxState(false);
    }

    private void refreshParallaxState(boolean rebuild) {
        if (levelManager.parallaxManager == null || levelManager.camera == null) {
            return;
        }
        if (rebuild) levelManager.parallaxManager.rebuildDerivedOutput(
                levelManager.currentZone,
                levelManager.currentAct,
                levelManager.camera,
                levelManager.frameCounter,
                levelManager.level);
        else levelManager.parallaxManager.update(
                levelManager.currentZone,
                levelManager.currentAct,
                levelManager.camera,
                levelManager.frameCounter,
                levelManager.level);
        levelManager.camera.setShakeOffsets(
                levelManager.parallaxManager.getShakeOffsetX(),
                levelManager.parallaxManager.getShakeOffsetY());
    }
}
