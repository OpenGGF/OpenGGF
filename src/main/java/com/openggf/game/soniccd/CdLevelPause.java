package com.openggf.game.soniccd;

import com.openggf.control.InputHandler;
import com.openggf.control.RecordedInputOwnership;
import com.openggf.game.GameServices;
import com.openggf.game.LevelInputOverlay;
import com.openggf.game.NativeGameStateOps;
import com.openggf.game.internal.NativeLevelInputOwner;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.soniccd.audio.CdSoundRequests;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.program.CdStageExit;
import com.openggf.game.soniccd.zones.CdZones;

/** The pad branch of {@code Level_MainLoop} (Level/Main.asm:333-395). */
final class CdLevelPause implements NativeLevelInputOwner, RewindSnapshottable<Boolean> {
    private boolean restartRequested;

    final LevelInputOverlay recordedInputOverlay =
            input -> RecordedInputOwnership.active(input) && handleInput(input);

    @Override
    public boolean handleInput(InputHandler input) {
        return handlePauseInput(input, false);
    }

    @Override
    public boolean handleHostPausedInput(InputHandler input) {
        return handlePauseInput(input, true);
    }

    private boolean handlePauseInput(InputHandler input, boolean hostPaused) {
        var levels = GameServices.levelOrNull();
        if (levels == null || !(levels.getCurrentLevel() instanceof CdLevel level)) {
            return false;
        }
        var pad = input.logical().player1();
        boolean start = pad.startPressed();
        boolean action = pad.actionPressedMask() != 0;
        boolean nativeStart = !hostPaused && start;
        if (hostPaused && (start || !action)) {
            return false;
        }
        if (levels.isLevelInactiveForTransition() || GameServices.fade().isActive()) {
            return nativeStart;
        }
        var services = levels.getObjectManager().getObjectServices();
        var game = services.gameState();
        var player = services.camera().getFocusedSprite();
        boolean canPause = !level.flags().ctrlLocked && player != null && !player.getDead();
        boolean wasPaused = game.isGamePaused();
        boolean paused = nativeStart && canPause ? game.applyPauseToggle(true) : wasPaused;
        if (!hostPaused) {
            if (paused) CdSoundRequests.stopChargeSound(services);
            if (wasPaused != paused) CdSoundRequests.setLevelMusicPaused(services, player, paused);
        }
        if (!action || !(hostPaused ? canPause : paused)) {
            return nativeStart;
        }

        if (CdSceneCoordinator.isTimeAttack(services.gameModule())) {
            NativeGameStateOps.restoreLives(game, 0);
        } else {
            game.loseLife();
            var zone = level.payload().stage().zone();
            // StageMainLoopRestartLife (r4/main.asm:352-354) and loc_201858 (r5/main.asm:333-335).
            if (zone == CdZone.TIDAL_TEMPEST || zone == CdZone.QUARTZ_QUADRANT) {
                CdSoundRequests.releaseHeldFmEffects(services.gameModule());
            }
        }
        game.setGamePaused(false);
        if (game.getLives() == 0) {
            // LevelStart (Level/Main.asm:85-115) returns to the caller when lives is zero.
            services.checkpointState().clear();
            CdStageExit.outOfLives(services);
        } else {
            var payload = level.payload();
            if (!CdZones.fragment(payload.stage().zone()).retainsCheckpointOnPauseRestart(payload)) {
                services.checkpointState().clear();
            }
            // The shared fade accounts for the spent life. Carry the stage program's reload intent.
            restartRequested = true;
            levels.setLevelInactiveForTransition(true);
            levels.requestRespawn();
        }
        return true;
    }

    boolean consumeRestartRequest() {
        boolean requested = restartRequested;
        restartRequested = false;
        return requested;
    }

    @Override public String key() { return "soniccd.pauseRestart"; }
    @Override public Boolean capture() { return restartRequested; }
    @Override public void restore(Boolean requested) { restartRequested = requested; }
    @Override public void resetForMissingSnapshot() { restartRequested = false; }
}
