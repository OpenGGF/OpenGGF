package com.openggf.level;

import com.openggf.data.Game;
import com.openggf.game.DynamicStartPositionProvider;
import com.openggf.game.GameModule;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Owns module hooks at the decoded-level and native fresh-entry boundaries.
 * One LevelManager owns one controller. Its flags are load-attempt-only:
 * reload suppression unwinds in finally, and fresh camera focus is consumed
 * during the same load before any frame or rewind boundary.
 */
final class ModuleLevelLoadController {
    private boolean nativeCurrentReloadInProgress;
    private boolean freshEntryCameraPending;

    void resetFreshEntryCamera() {
        freshEntryCameraPending = false;
    }

    boolean consumeFreshEntryCamera() {
        boolean retainedFreshEntry = freshEntryCameraPending;
        freshEntryCameraPending = false;
        return retainedFreshEntry;
    }

    void installPlacements(GameModule module, int levelIndex, Level loaded, boolean stockDecoded) {
        RegisteredLevelPlacements placements = module == null ? null
                : module.getGameService(RegisteredLevelPlacements.class);
        if (placements != null) placements.install(module.getGameCode(), levelIndex, loaded, stockDecoded);
    }

    StartPosition nativeStartPosition(Game game, LevelDescriptor levelData, int zone, int act, Logger logger) {
        int spawnX = levelData.startX();
        int spawnY = levelData.startY();
        if (game instanceof DynamicStartPositionProvider dynamicStartProvider) {
            try {
                int[] dynamicStart = dynamicStartProvider.getStartPosition(zone, act);
                if (dynamicStart != null && dynamicStart.length >= 2) {
                    spawnX = dynamicStart[0];
                    spawnY = dynamicStart[1];
                    logger.info("Set player position from dynamic start provider: X=" + spawnX +
                            ", Y=" + spawnY + " (zone=" + zone + ", act=" + act + ")");
                } else {
                    logger.info("Dynamic start provider unavailable, using levelData fallback for " +
                            levelData.toString());
                }
            } catch (IOException e) {
                logger.warning("DynamicStartPositionProvider failed, using levelData fallback: " + e.getMessage());
            }
        }
        return new StartPosition(spawnX, spawnY);
    }

    /** Called only after native checkpoint and saved-origin positions have been ruled out. */
    StartPosition placeStartPosition(GameModule module, LevelLoadContext ctx,
            LevelTransitionCoordinator transitions, int zone, int act, Sprite player, int spawnX, int spawnY) {
        if (ctx.getLoadMode() == LevelLoadMode.FULL
                && !nativeCurrentReloadInProgress && !transitions.isLevelRoutineReentry()
                && !transitions.hasBigRingReturn() && !transitions.isBonusStageReturn()
                && transitions.sanctuaryReentryStage().isEmpty()) {
            var freshPosition = Objects.requireNonNull(module.freshLevelStartPosition(zone, act),
                    "freshLevelStartPosition must return an Optional");
            if (freshPosition.isPresent()) {
                spawnX = freshPosition.orElseThrow().centreX();
                spawnY = freshPosition.orElseThrow().centreY();
                freshEntryCameraPending = true;
            }
        }
        if (player instanceof AbstractPlayableSprite playable) {
            NativePositionOps.writeXPosResetSubpixel(playable, spawnX);
            NativePositionOps.writeYPosResetSubpixel(playable, spawnY);
        } else {
            player.setCentreX((short) spawnX);
            player.setCentreY((short) spawnY);
        }
        return new StartPosition(spawnX, spawnY);
    }

    void reloadCurrentNativeState(Runnable reload) {
        boolean previousNativeReload = nativeCurrentReloadInProgress;
        nativeCurrentReloadInProgress = true;
        try {
            reload.run();
        } finally {
            nativeCurrentReloadInProgress = previousNativeReload;
        }
    }

    // Native dynamic starts retain their existing signed/wrapping coordinate semantics.
    record StartPosition(int centreX, int centreY) { }
}
