package com.openggf;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.control.InputHandler;
import static org.lwjgl.glfw.GLFW.*;
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

    private void tab() {
        var input=new InputHandler();input.handleKeyEvent(GLFW_KEY_TAB,GLFW_PRESS);
        diagnostics.handleInput(input);input.update();
        input.handleKeyEvent(GLFW_KEY_TAB,GLFW_RELEASE);diagnostics.handleInput(input);input.update();
    }

    @Test
    void cleanOrAbsentReplayKeepsFullDiagnosticsClosed() {
        assertFalse(diagnostics.renderIfNeeded(text));
        active(UserRecordingVerificationResult.clean(1248));
        assertFalse(diagnostics.renderIfNeeded(text));
        verifyNoInteractions(text);
    }

    @Test
    void mismatchStaysCompactUntilTabRequestsReadableRecordedAndActualValues() {
        active(UserRecordingVerificationResult.firstMismatch(1248, 44, "p1CentreX", "100", "321"));
        assertFalse(diagnostics.renderIfNeeded(text));
        tab();
        assertTrue(diagnostics.renderIfNeeded(text));
        verify(text).drawShadowedText(eq("Field: p1CentreX"), anyInt(), anyInt(), any(), eq(1f));
        verify(text).drawShadowedText(eq("Recorded: 100"), anyInt(), anyInt(), any(), eq(1f));
        verify(text).drawShadowedText(eq("Engine: 321"), anyInt(), anyInt(), any(), eq(1f));
        verify(text).drawShadowedText(eq("Input: A......."), anyInt(), anyInt(), any(), anyFloat());
        verify(launcher, never()).updateActivePlaybackState(any());
        verify(launcher, never()).endPlaybackSession();
    }

    @Test
    void unavailableVerificationShowsReasonWithoutInventingAMismatch() {
        active(UserRecordingVerificationResult.missingSidecar(0));
        assertFalse(diagnostics.renderIfNeeded(text));
        tab();
        assertTrue(diagnostics.renderIfNeeded(text));
        verify(text).drawShadowedText(eq("Errors: 0   Warnings: 1   Lag: 0"), anyInt(), anyInt(), any(), anyFloat());
        verify(text).drawShadowedText(eq("Verification: missing-sidecar"), anyInt(), anyInt(), any(), anyFloat());
        verify(text, never()).drawShadowedText(contains("rec="), anyInt(), anyInt(), any(), anyFloat());
    }

    @Test
    void fastForwardOnlyRendersFullDetailsWhenExplicitlyOpened() throws Exception {
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
        assertTrue(loop.shouldSuppressUserRecordingSceneRendering(), "errors must not open details");
        tab();
        assertFalse(loop.shouldSuppressUserRecordingSceneRendering(), "requested details must get a rendered frame");
        tab();
        assertTrue(loop.shouldSuppressUserRecordingSceneRendering(), "closing details restores ordinary fast-forward");
        assertTrue(controls.shouldPumpFastForward(), "presentation must not turn off fast-forward");
        verify(launcher, never()).updateActivePlaybackState(any());
    }

    @Test
    void endingReplayClearsThePanelEvenIfLastVerificationWasAnError() {
        active(UserRecordingVerificationResult.firstMismatch(1248, 44, "p1CentreX", "100", "321"));
        tab();
        assertTrue(diagnostics.renderIfNeeded(text));
        when(launcher.currentPlaybackState()).thenReturn(UserRecordingPlaybackState.STOPPED);
        clearInvocations(text);
        assertFalse(diagnostics.renderIfNeeded(text));
        verifyNoInteractions(text);
    }
}
