package com.openggf;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.recording.UserRecordingPlaybackOptions;
import com.openggf.game.recording.UserRecordingPlaybackState;
import com.openggf.game.recording.UserRecordingSessionLauncher;
import com.openggf.game.recording.UserRecordingVerificationResult;
import com.openggf.game.recording.UserRecordingRuntimeControls;
import com.openggf.graphics.PixelFontTextRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestRecordingReplayDiagnostics {
    private final UserRecordingSessionLauncher launcher = mock(UserRecordingSessionLauncher.class);
    private final PlaybackDebugManager playback = mock(PlaybackDebugManager.class);
    private final PixelFontTextRenderer text = mock(PixelFontTextRenderer.class);
    private final RecordingReplayDiagnostics diagnostics = new RecordingReplayDiagnostics(launcher, playback, () -> true);

    private void active(UserRecordingVerificationResult verification) {
        when(launcher.currentPlaybackOptions()).thenReturn(new UserRecordingPlaybackOptions(3599, true, false));
        when(launcher.currentPlaybackState()).thenReturn(UserRecordingPlaybackState.PLAYING);
        when(launcher.currentPlaybackVerificationResult()).thenReturn(verification);
        when(playback.getCursorFrame()).thenReturn(1248);
        when(playback.getMovieFrameCount()).thenReturn(3600);
        when(playback.movieName()).thenReturn("ehz1-review.bk2");
        when(playback.observedMode()).thenReturn(GameMode.LEVEL);
        when(playback.currentFrameOrThrow()).thenReturn(new Bk2FrameInput(1248, 0, 1, false, ""));
    }

    @Test
    void cleanOrAbsentReplayKeepsFullDiagnosticsClosed() {
        assertFalse(diagnostics.renderIfNeeded(text));
        active(UserRecordingVerificationResult.clean(1248));
        assertFalse(diagnostics.renderIfNeeded(text));
        verifyNoInteractions(text);
    }

    @Test
    void mismatchAutomaticallyShowsFullTraceDetailsWithRecordedAndActualValues() {
        active(UserRecordingVerificationResult.firstMismatch(1248, 44, "p1CentreX", "100", "321"));
        assertTrue(diagnostics.renderIfNeeded(text));
        verify(text).drawShadowedText(eq("ERRORS    1"), anyInt(), anyInt(), any(), anyFloat());
        verify(text).drawShadowedText(contains("p1CentreX rec=100 eng=321"), anyInt(), anyInt(), any(), anyFloat());
        verify(text).drawShadowedText(eq("A......."), anyInt(), anyInt(), any(), anyFloat());
        verify(launcher, never()).updateActivePlaybackState(any());
        verify(launcher, never()).endPlaybackSession();
    }

    @Test
    void unavailableVerificationShowsReasonWithoutInventingAMismatch() {
        active(UserRecordingVerificationResult.missingSidecar(0));
        assertTrue(diagnostics.renderIfNeeded(text));
        verify(text).drawShadowedText(eq("WARN      1"), anyInt(), anyInt(), any(), anyFloat());
        verify(text).drawShadowedText(eq("Verification: missing-sidecar"), anyInt(), anyInt(), any(), anyFloat());
        verify(text, never()).drawShadowedText(contains("rec="), anyInt(), anyInt(), any(), anyFloat());
    }

    @Test
    void fastForwardCannotHideFailureDiagnosticsAndRetainsItsPlaybackPolicy() throws Exception {
        var loop = mock(GameLoop.class, CALLS_REAL_METHODS);
        var controls = mock(UserRecordingRuntimeControls.class);
        when(controls.shouldSuppressSceneRendering()).thenReturn(true);
        when(controls.shouldPumpFastForward()).thenReturn(true);
        var controlsField = GameLoop.class.getDeclaredField("userRecordingControls");
        controlsField.setAccessible(true);
        controlsField.set(loop, controls);
        var diagnosticsField = GameLoop.class.getDeclaredField("recordingReplayDiagnostics");
        diagnosticsField.setAccessible(true);
        diagnosticsField.set(loop, diagnostics);
        active(UserRecordingVerificationResult.clean(1248));
        assertTrue(loop.shouldSuppressUserRecordingSceneRendering());
        when(launcher.currentPlaybackVerificationResult()).thenReturn(
                UserRecordingVerificationResult.firstMismatch(1248, 44, "p1CentreX", "100", "321"));
        assertFalse(loop.shouldSuppressUserRecordingSceneRendering(), "failure panel must get a rendered frame");
        assertTrue(controls.shouldPumpFastForward(), "presentation must not turn off fast-forward");
        verify(launcher, never()).updateActivePlaybackState(any());
    }

    @Test
    void endingReplayClearsThePanelEvenIfLastVerificationWasAnError() {
        active(UserRecordingVerificationResult.firstMismatch(1248, 44, "p1CentreX", "100", "321"));
        assertTrue(diagnostics.renderIfNeeded(text));
        when(launcher.currentPlaybackState()).thenReturn(UserRecordingPlaybackState.STOPPED);
        clearInvocations(text);
        assertFalse(diagnostics.renderIfNeeded(text));
        verifyNoInteractions(text);
    }
}
