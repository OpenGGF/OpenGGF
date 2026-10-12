package com.openggf.level;

import com.openggf.data.Game;
import com.openggf.game.GameModule;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.playable.AbstractPlayableSprite;

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

    /** Selects the native/additive start before applying an optional fresh-entry position. */
    StartPosition placeLevelStart(GameModule module, Game game, LevelLoadContext ctx,
            LevelTransitionCoordinator transitions, int zone, int act, Sprite player,
            LevelDescriptor descriptor, Logger logger) {
        boolean additiveAct = module.getZoneRegistry()
                .modZoneRuntimeContribution(ctx.getLevelIndex()) != null;
        int[] nativeStart = LevelSceneActAccess.nativeStart(game, zone, act, descriptor, additiveAct);
        StartPosition start = placeStartPosition(module, ctx, transitions, zone, act, player,
                nativeStart[0], nativeStart[1]);
        logger.info("Set player position from level start: X=" + start.centreX() +
                ", Y=" + start.centreY() + " (center coordinates), level: " + descriptor);
        return start;
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
