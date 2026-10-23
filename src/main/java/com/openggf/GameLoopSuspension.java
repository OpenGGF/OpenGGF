package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRouting;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.recording.UserRecordingPlaybackState;

import java.util.Objects;

/**
 * GameLoop's side of keeping a game in memory behind the master title. Holding Escape suspends an
 * ordinary stock game instead of tearing it down; runs that own their own ending (traces, hosted
 * runs, recordings, mod scenes and acts, editor playtests, standalone games) keep the full return.
 *
 * <p>While a game is parked, GameLoop resolves no gameplay context, so the hub runs on the bootstrap
 * camera and fade exactly as it does with no game loaded, and gameplay frames never step. The
 * session itself stays published; {@link Host} restores or ends it.
 */
final class GameLoopSuspension {
    /** Engine-owned capture and restore of what the hub would otherwise change. */
    interface Host {
        /** Captures the music and picture before parking; false means use the full return instead. */
        boolean prepareSuspend(GameMode mode);

        /** Runs after its last picture is captured: parks it and shows the master title. */
        void suspend();

        /** Runs after the resume zoom finishes: removes the hub and brings the game back. */
        void resume();
    }

    private final GameLoop loop;
    private Host host;
    private boolean parked;
    private boolean preparing;
    private boolean userPausedWhenParked;
    private com.openggf.game.session.GameplayModeContext parkedOwner;

    GameLoopSuspension(GameLoop loop) {
        this.loop = Objects.requireNonNull(loop, "loop");
    }

    void setHost(Host host) {
        this.host = host;
    }

    boolean parked() {
        return parked;
    }

    com.openggf.game.session.GameplayModeContext parkedOwner() { return parkedOwner; }

    /** The Escape hold's completion: suspend when the run allows it, otherwise return as before. */
    void leave() {
        if (host != null && suspendable() && host.prepareSuspend(loop.getCurrentGameMode())) {
            preparing = true;
        } else {
            loop.fadeOutTo(loop::returnToMasterTitle);
        }
    }

    /** Freeze until the renderer has captured the final gameplay frame. */
    boolean preparing() { return preparing; }

    void completePreparedSuspend() {
        if (!preparing) return;
        preparing = false;
        host.suspend();
    }

    boolean suspendable() {
        var gameplay = loop.resolveGameplayModeContext();
        return !parked && !preparing && gameplay != null && gameplay.isGameplayRuntimeReady()
                && loop.getCurrentGameMode() != GameMode.MOD_SCENE
                && !loop.modSceneActBridge.ownsAct()
                && TraceSessionLauncher.active() == null
                && !loop.hostedRuns.isActive()
                && !loop.userRecordingSessionLauncher.hasActiveRecordingSession()
                && loop.userRecordingSessionLauncher.currentPlaybackState() == UserRecordingPlaybackState.STOPPED
                && !loop.configService.getBoolean(SonicConfiguration.TEST_MODE_ENABLED);
    }

    /** Mod scenes, their acts and standalone mod games read a short Escape press themselves. */
    boolean modOwnsEscapeTap() {
        if (loop.getCurrentGameMode() == GameMode.MOD_SCENE || loop.modSceneActBridge.ownsAct()) return true;
        var gameplay = loop.resolveGameplayModeContext();
        return gameplay != null && gameplay.getWorldSession() != null
                && GameModuleRouting.isStandalone(gameplay.getWorldSession().getGameModule());
    }

    /**
     * Stops binding the game and shows the master title mode without a rewind boundary. Returns
     * the mode it left: outside levels the game can change mode during the fade-out.
     */
    GameMode park() {
        loop.getEscapeToMasterTitleController().reset();
        userPausedWhenParked = loop.isUserPaused();
        if (userPausedWhenParked) loop.toggleUserPause();
        parkedOwner = loop.resolveGameplayModeContext();
        parked = true;
        loop.refreshRuntimeBindings();
        return changeMode(GameMode.MASTER_TITLE_SCREEN);
    }

    /** Binds the game again and returns to the mode it was suspended from. */
    void unpark(GameMode mode) {
        parked = false;
        loop.setGameplayMode(parkedOwner);
        parkedOwner = null;
        changeMode(mode);
        if (userPausedWhenParked) loop.toggleUserPause();
        userPausedWhenParked = false;
    }

    /** Ends the parked game's run-scoped state before the normal teardown closes its session. */
    void discard() {
        if (!parked) return;
        parked = false;
        parkedOwner = null;
        userPausedWhenParked = false;
        loop.retireModScene();
        loop.tearDownGameplayForReturn();
    }

    /** Called between display frames: restore gameplay only after the full-size picture was shown. */
    void routeResume(MasterTitleScreen screen) {
        if (screen == null || host == null || loop.resolveFadeManager().isActive()) return;
        if (screen.consumeResumeRequest()) host.resume();
    }

    private GameMode changeMode(GameMode mode) {
        GameMode old = loop.changeGameModeWithoutRewindBoundary(mode);
        if (loop.gameModeChangeListener != null) loop.gameModeChangeListener.onGameModeChanged(old, mode);
        return old;
    }
}
