package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.game.GameId;
import com.openggf.game.launch.SuspendedSettings;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.RgbaImage;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRouting;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.recording.UserRecordingPlaybackState;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * The complete visit kept in memory behind the master title, owned for the loop lifetime. Holding Escape suspends an
 * ordinary stock game instead of tearing it down; runs that own their own ending (traces, hosted
 * runs, recordings, mod scenes and acts, editor playtests, standalone games) keep the full return.
 *
 * <p>While a game is parked, GameLoop resolves no gameplay context, so the hub runs on the bootstrap
 * camera and fade exactly as it does with no game loaded, and gameplay frames never step. The
 * session itself stays published; this owner restores or ends the visit.
 */
final class GameLoopSuspension {
    // Preserve the application's existing log category without depending on its root class.
    private static final Logger LOGGER = Logger.getLogger("com.openggf.Engine");

    interface CaptureEffects {
        WorldSession publishedWorld();
        boolean editorPlaytestActive();
        AudioLogicalSnapshot captureAudioIfEnabled();
        void fadeMusic();
        RgbaImage captureViewportThumbnail();
        int thumbnailWidth();
        int thumbnailHeight();
        SuspendedSettings suspendSettings();
        SuspendedGame.CameraPosition captureCameraPosition();
        void resumeSettings(SuspendedSettings settings, MasterTitleScreen.GameEntry entry, boolean inLevel);
        void restoreCamera(SuspendedGame.CameraPosition position);
        void restoreAudio(AudioLogicalSnapshot snapshot);
    }

    interface HubEffects {
        MasterTitleScreen currentScreen();
        void publishScreen(MasterTitleScreen screen);
        MasterTitleScreen createScreen();
        void refreshCachedConfig();
        void applyDisplayDimensions();
        void cancelBootstrapFade();
    }

    interface RetirementEffects {
        void resetForGameplayFromMasterTitle();
        void clearGameplayReference();
        void ensureTitleAudioBackend();
    }

    /** References only: installing a binding never samples any application state. */
    record Effects(CaptureEffects capture, HubEffects hub, RetirementEffects retirement) { }

    private final GameLoop loop;
    private Effects effects;
    private SuspendedGame suspendedGame;
    private SuspendedGame.Pending pendingSuspend;
    private SuspendedGame.Thumbnail pendingSuspendThumbnail;
    private boolean suspendThumbnailRequested;
    private boolean parked;
    private boolean preparing;
    private boolean userPausedWhenParked;
    private com.openggf.game.session.GameplayModeContext parkedOwner;

    GameLoopSuspension(GameLoop loop) {
        this.loop = Objects.requireNonNull(loop, "loop");
    }

    void setEffects(Effects effects) {
        this.effects = effects;
    }

    boolean parked() {
        return parked;
    }

    com.openggf.game.session.GameplayModeContext parkedOwner() { return parkedOwner; }

    /** The Escape hold's completion: suspend when the run allows it, otherwise return as before. */
    void leave() {
        // Argument evaluation captures the binding before the virtual mode read can replace it.
        if (effects != null && suspendable() && prepareSuspend(effects, loop.getCurrentGameMode())) {
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
        suspendToMasterTitle(Objects.requireNonNull(effects));
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
        if (screen == null || effects == null || loop.resolveFadeManager().isActive()) return;
        if (screen.consumeResumeRequest()) resumeSuspendedGame(Objects.requireNonNull(effects));
    }

    /** The display host supplies its own binding; resume/completion select the live binding inside. */
    void betweenFrames(Effects frameEffects) {
        if (parked) routeResume(frameEffects.hub().currentScreen());
        if (preparing && !suspendThumbnailRequested) completePreparedSuspend();
    }

    /** Samples the published world at admission, independently of the loop's later parking identity. */
    boolean prepareSuspend(Effects operation, GameMode mode) {
        var world = operation.capture().publishedWorld();
        if (operation.capture().editorPlaytestActive() || suspendedGame != null || world == null) return false;
        if (GameModuleRouting.isStandalone(world.getGameModule())) return false;
        GameId game = world.getGameModule().getGameId();
        AudioLogicalSnapshot audio = null;
        String zone = null;
        try {
            audio = operation.capture().captureAudioIfEnabled();
            zone = world.getGameModule().getZoneRegistry().getZoneName(world.getCurrentZone());
        } catch (RuntimeException failure) {
            LOGGER.warning("Suspending without part of its state: " + failure.getMessage());
        }
        pendingSuspend = new SuspendedGame.Pending(audio,
                SuspendedGame.card(game.code(), mode, SuspendedGame.location(mode, zone, world.getApparentAct() + 1)));
        pendingSuspendThumbnail = null;
        suspendThumbnailRequested = true;
        operation.capture().fadeMusic();
        return true;
    }

    /** After scene drawing/flush/SCENE; the request is consumed even when capture fails. */
    void afterScene(CaptureEffects capture) {
        if (!suspendThumbnailRequested) return;
        suspendThumbnailRequested = false;
        try {
            pendingSuspendThumbnail = SuspendedGame.thumbnail(capture.captureViewportThumbnail(),
                    capture.thumbnailWidth(), capture.thumbnailHeight());
        } catch (RuntimeException failure) {
            LOGGER.warning("Suspended game has no picture: " + failure.getMessage());
        }
    }

    void suspendToMasterTitle(Effects operation) {
        SuspendedGame.Pending pending = pendingSuspend;
        pendingSuspend = null;
        if (pending == null) { loop.returnToMasterTitle(); return; }
        SuspendedSettings settings = operation.capture().suspendSettings();
        SuspendedGame.CameraPosition position = operation.capture().captureCameraPosition();
        suspendedGame = new SuspendedGame(park(), pending, pendingSuspendThumbnail, settings, position);
        pendingSuspendThumbnail = null;
        operation.hub().refreshCachedConfig();
        operation.hub().applyDisplayDimensions();
        if (operation.hub().currentScreen() != null) operation.hub().currentScreen().cleanup();
        operation.hub().publishScreen(operation.hub().createScreen());
        operation.hub().currentScreen().initialize();
        operation.hub().cancelBootstrapFade();
        operation.hub().currentScreen().animateSuspendedGame();
        LOGGER.info("Suspended " + pending.card().menuLabel() + " to the master title");
    }

    void resumeSuspendedGame(Effects operation) {
        SuspendedGame game = suspendedGame;
        if (game == null) return;
        suspendedGame = null;
        if (operation.hub().currentScreen() != null) {
            operation.hub().currentScreen().cleanup();
            operation.hub().publishScreen(null);
        }
        operation.capture().resumeSettings(game.settings(), game.card().game().game(), game.mode() == GameMode.LEVEL);
        operation.hub().refreshCachedConfig();
        unpark(game.mode());
        operation.hub().applyDisplayDimensions();
        if (game.camera() != null) operation.capture().restoreCamera(game.camera());
        try {
            if (game.audio() != null) operation.capture().restoreAudio(game.audio());
        } catch (RuntimeException failure) {
            LOGGER.warning("Resumed without its music state: " + failure.getMessage());
        }
        loop.resolveFadeManager().cancel();
        LOGGER.info("Resumed " + game.card().menuLabel());
    }

    /** Engine calls this at the original card-publication point in its screen factory. */
    void installCard(MasterTitleScreen screen, Effects operation) {
        if (suspendedGame != null) {
            SuspendedGame.Thumbnail thumbnail = suspendedGame.thumbnail();
            screen.setSuspendedGame(suspendedGame.card(), thumbnail == null ? 0 : thumbnail.width(),
                    thumbnail == null ? 0 : thumbnail.height(), thumbnail == null ? null : thumbnail.rgba());
        }
        screen.setSuspendedGameEndHandler(() -> discardSuspendedGame(operation));
    }

    void discardSuspendedGame(Effects operation) {
        if (suspendedGame == null) return;
        operation.retirement().resetForGameplayFromMasterTitle();
        loop.setGameplayMode(null);
        operation.retirement().clearGameplayReference();
        operation.retirement().ensureTitleAudioBackend();
    }

    /** One-way retirement handshake, called before the application's common teardown. */
    void retireGameplay() {
        if (suspendedGame != null) {
            suspendedGame = null;
            discard();
        }
    }

    private GameMode changeMode(GameMode mode) {
        GameMode old = loop.changeGameModeWithoutRewindBoundary(mode);
        if (loop.gameModeChangeListener != null) loop.gameModeChangeListener.onGameModeChanged(old, mode);
        return old;
    }
}
