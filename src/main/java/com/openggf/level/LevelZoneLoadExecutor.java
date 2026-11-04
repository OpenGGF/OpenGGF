package com.openggf.level;

import com.openggf.game.LevelLoadMode;

import java.io.IOException;

/** Owns outer zone-load carry and cleanup around the profile load owner. */
final class LevelZoneLoadExecutor {
    private final LevelManager levelManager;
    private final LevelTransitionCoordinator transitions;

    LevelZoneLoadExecutor(LevelManager levelManager, LevelTransitionCoordinator transitions) {
        this.levelManager = levelManager;
        this.transitions = transitions;
    }

    void execute(
            int zone, int act, LevelLoadMode loadMode,
            boolean titleCardRequiredInHeadlessMode,
            boolean queueFreshLevelRuntimeArt) throws IOException {
        LevelContinuationCarry.beginLoad(transitions, zone, act);
        boolean succeeded = false;
        try {
            levelManager.writeCurrentAct(act);
            // loc_6034 (sonic3k.asm:7580-7582): bonus returns already restored
            // their saved apparent act. Fresh loads present the loaded act.
            if (!transitions.isBonusStageReturn()) {
                levelManager.writeApparentAct(act);
            }
            levelManager.writeCurrentZone(zone);
            // Manual changes discard checkpoints. A bonus return has already
            // prepared its saved position for the load profile, before ScreenInit.
            if (!transitions.isBonusStageReturn()) {
                levelManager.checkpointCoordinator.clear();
            }
            levelManager.loadCurrentLevel(
                    loadMode != LevelLoadMode.PREVIEW_CAPTURE,
                    loadMode,
                    false,
                    titleCardRequiredInHeadlessMode,
                    queueFreshLevelRuntimeArt);
            succeeded = true;
        } finally {
            // BEGIN HCZ1_DIRECT_FF_OUTER_ZONE_FINISH: an enclosing carry/cleanup failure revokes, never admits.
            boolean plcZoneCleanupCompleted = false;
            try {
                LevelContinuationCarry.finishLoad(transitions, succeeded);
                // A load that fails before initCameraBounds must not leak a stage-return
                // respawn table into a later, potentially different, level.
                levelManager.checkpointCoordinator.clearPendingPersistentRespawn();
                plcZoneCleanupCompleted = true;
            } finally {
                if (!succeeded || !plcZoneCleanupCompleted)
                    levelManager.revokeNativePlcTargetAdmission();
            }
            // END HCZ1_DIRECT_FF_OUTER_ZONE_FINISH
        }
    }

}
