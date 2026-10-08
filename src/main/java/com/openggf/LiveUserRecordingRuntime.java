package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.recording.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Live recording controls bridge; session and playback owners retain all state. */
final class LiveUserRecordingRuntime implements UserRecordingRuntimeControls.Runtime {
    private final SonicConfigurationService configService;
    private final UserRecordingSessionLauncher launcher;
    private final PlaybackDebugManager playback;
    private final Supplier<GameMode> mode;
    private final BooleanSupplier inputOwned;
    private final Runnable pause, resetPlayback;

    LiveUserRecordingRuntime(SonicConfigurationService configService,
            UserRecordingSessionLauncher launcher, PlaybackDebugManager playback,
            Supplier<GameMode> mode, BooleanSupplier inputOwned, Runnable pause, Runnable resetPlayback) {
        this.configService = configService;
        this.launcher = launcher;
        this.playback = playback;
        this.mode = mode;
        this.inputOwned = inputOwned;
        this.pause = pause;
        this.resetPlayback = resetPlayback;
    }

    @Override
    public int recordKey() {
        return configService.getInt(SonicConfiguration.RECORDING_RECORD_KEY);
    }

    @Override
    public GameMode currentGameMode() {
        return mode.get();
    }

    @Override
    public boolean traceOrDebugSurfaceOwnsRecordingInput() {
        return inputOwned.getAsBoolean();
    }

    @Override
    public boolean hasActiveRecording() {
        return launcher.hasActiveRecordingSession();
    }

    @Override
    public void beginRecordingFromCurrentLevel() {
        launcher.beginRecordingFromCurrentLevel();
    }

    @Override
    public void stopActiveRecording(UserRecordingStopReason reason) {
        launcher.stopActiveRecording(reason);
    }

    @Override
    public void beforeActiveRecordingLevelFrame(InputHandler input) {
        launcher.beforeActiveRecordingLevelFrame(input);
    }

    @Override
    public void afterActiveRecordingLevelFrame() {
        launcher.afterActiveRecordingLevelFrame();
    }

    @Override
    public UserRecordingHudState activeRecordingHudState() {
        return launcher.activeRecordingHudState();
    }

    @Override
    public com.openggf.game.recording.UserRecordingPlaybackOptions activePlaybackOptions() {
        return launcher.currentPlaybackOptions();
    }

    @Override
    public UserRecordingPlaybackState activePlaybackState() {
        return launcher.currentPlaybackState();
    }

    @Override
    public boolean playbackHasDesynced() {
        return launcher.activePlaybackHasDesynced();
    }

    @Override
    public UserRecordingVerificationResult activePlaybackVerificationResult() {
        return launcher.currentPlaybackVerificationResult();
    }

    @Override
    public int currentPlaybackFrame() {
        return playback.getCursorFrame();
    }

    @Override
    public int playbackFrameCount() {
        return playback.getMovieFrameCount();
    }

    @Override
    public void updatePlaybackState(UserRecordingPlaybackState state) {
        launcher.updateActivePlaybackState(state);
    }

    @Override
    public void pauseEngineForPlayback() {
        pause.run();
    }

    @Override
    public void endPlaybackDebugSession() {
        launcher.endPlaybackSession();
        resetPlayback.run();
    }

}
