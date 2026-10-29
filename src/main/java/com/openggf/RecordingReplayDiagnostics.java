package com.openggf;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.recording.UserRecordingPlaybackOptions;
import com.openggf.game.recording.UserRecordingPlaybackState;
import com.openggf.game.recording.UserRecordingSessionLauncher;
import com.openggf.game.recording.UserRecordingVerificationResult;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.testmode.TraceHudOverlay;
import com.openggf.testmode.TracePlaybackStatus;
import com.openggf.trace.Severity;
import com.openggf.trace.TraceHudModel;
import com.openggf.trace.live.MismatchEntry;

import java.util.List;
import java.util.function.BooleanSupplier;

/** Read-only bridge from recorded BK2 verification to the existing full trace diagnostics. */
final class RecordingReplayDiagnostics {
    private final UserRecordingSessionLauncher launcher;
    private final PlaybackDebugManager playback;
    private final BooleanSupplier paused;
    private UserRecordingVerificationResult verification;
    private UserRecordingPlaybackOptions options;
    private Bk2FrameInput input;
    private TraceHudOverlay overlay;

    RecordingReplayDiagnostics(UserRecordingSessionLauncher launcher, PlaybackDebugManager playback,
                               BooleanSupplier paused) {
        this.launcher = launcher;
        this.playback = playback;
        this.paused = paused;
    }

    void handleInput(com.openggf.control.InputHandler input) {
        if (prepare()) overlay.handleInput(input);
    }

    boolean detailsVisible() {
        return overlay != null && overlay.detailsVisible() && launcher.currentPlaybackOptions() != null
                && launcher.currentPlaybackState() != UserRecordingPlaybackState.STOPPED;
    }

    private boolean prepare() {
        options = launcher.currentPlaybackOptions();
        verification = launcher.currentPlaybackVerificationResult();
        if (options == null || launcher.currentPlaybackState() == UserRecordingPlaybackState.STOPPED) {
            overlay = null;
            input = null;
            return false;
        }
        if (overlay == null) {
            overlay = TraceHudOverlay.forRecordingReplay(new Model(), paused, this::playbackStatus,
                    () -> verification == null ? "Verification: pending"
                            : verification.clean() ? "Verified frames: " + verification.comparedFrames()
                            : verification.firstMismatchFrame() >= 0 ? null : "Verification: " + verification.status());
        }
        return true;
    }

    /** Full diagnostics are explicitly requested with Tab; errors never open this panel. */
    boolean renderIfNeeded(PixelFontTextRenderer text) {
        if (!prepare() || !overlay.detailsVisible()) return false;
        input = currentInput();
        overlay.render(text);
        return true;
    }

    private Bk2FrameInput currentInput() {
        int cursor = playback.getCursorFrame();
        if (cursor < 0 || cursor >= playback.getMovieFrameCount()) return null;
        try { return playback.currentFrameOrThrow(); }
        catch (IllegalStateException noActiveSession) { return null; }
    }

    private TracePlaybackStatus playbackStatus() {
        String name = playback.movieName();
        var mode = playback.observedMode();
        return new TracePlaybackStatus(name == null ? "Recording replay" : name,
                mode == null ? "Replay" : mode.name(), playback.getCursorFrame(), playback.getMovieFrameCount(),
                options.fastForward() ? "FF" : "1x");
    }

    private final class Model implements TraceHudModel {
        @Override public int errorCount() { return hasRecordingDesync() ? 1 : 0; }
        @Override public int warningCount() { return verification != null && !verification.clean() && !hasRecordingDesync() ? 1 : 0; }
        @Override public int laggedFrames() { return 0; } // Engine recordings submit one input per executed frame.
        @Override public int recentActionMask() { return input == null ? 0 : input.p1ActionMask(); }
        @Override public int recentInputMask() { return input == null ? 0 : input.p1InputMask(); }
        @Override public boolean recentStartPressed() { return input != null && input.p1StartPressed(); }
        @Override public boolean hasRecordingDesync() { return verification != null && verification.firstMismatchFrame() >= 0; }
        @Override public boolean isComplete() {
            return launcher.currentPlaybackState() == UserRecordingPlaybackState.PAUSED_AT_COMPLETION;
        }
        @Override public List<MismatchEntry> recentMismatches() {
            if (!hasRecordingDesync()) return List.of();
            String delta;
            try { delta = Long.toString(Long.parseLong(verification.actualValue()) - Long.parseLong(verification.expectedValue())); }
            catch (NumberFormatException nonNumeric) { delta = "?"; }
            return List.of(new MismatchEntry(verification.firstMismatchFrame(), verification.firstMismatchField(),
                    verification.expectedValue(), verification.actualValue(), delta, Severity.ERROR, 1));
        }
    }
}
