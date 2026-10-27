package com.openggf.game.recording;

import com.openggf.game.presentation.TransportHud;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestUserRecordingHud {
    @Test
    void holdHasDistinctArmedGlyphAndProgressWithoutClaimingRecordingHasStarted() {
        var message = UserRecordingHud.message(UserRecordingHud.holdPromptState(30, 60));
        assertEquals(TransportHud.Status.HOLD, message.status());
        assertEquals("HOLD TO REC", message.title());
        assertEquals("SHIFT+RECORD", message.detail());
        assertEquals("50% OF 1 SEC", message.note());
        assertEquals(50, message.progressPercent());
        assertEquals(0, UserRecordingHud.message(UserRecordingHud.holdPromptState(-5, 60)).progressPercent());
        assertEquals(100, UserRecordingHud.message(UserRecordingHud.holdPromptState(100, 60)).progressPercent());
    }

    @Test
    void activeRecordingSeparatesStatusFrameAndFilename() {
        var message = UserRecordingHud.message(new UserRecordingHudState(true,
                "REC frame 1248", "ehz1-review.bk2", 1248, false, false));
        assertEquals(TransportHud.Status.RECORD, message.status());
        assertEquals("RECORDING", message.title());
        assertEquals("FRAME 1248", message.detail());
        assertEquals("ehz1-review.bk2", message.note());
        assertEquals(-1, message.progressPercent());
    }

    @Test
    void playbackKeepsFrameTotalFastForwardAndVerificationWarning() {
        var playing = UserRecordingHud.message(new UserRecordingHudState(true,
                "PLAYBACK 1248/3600", "VERIFY clean", 1248, false, false));
        assertEquals(TransportHud.Status.PLAY, playing.status());
        assertEquals("PLAYBACK", playing.title());
        assertEquals("1248/3600 FR", playing.detail());
        assertEquals("VERIFIED", playing.note());

        var warning = UserRecordingHud.message(new UserRecordingHudState(true,
                "PLAYBACK FF 1248/3600", "VERIFY missing-sidecar", 1248, true, false));
        assertEquals(TransportHud.Status.PLAY_WARNING, warning.status());
        assertEquals("PLAYBACK FF", warning.title());
        assertEquals(playing.detail(), warning.detail());
        assertEquals("NO SIDECAR", warning.note());
    }

    @Test
    void longCountersKeepEveryDigitAndVerificationReasonIsReadable() {
        var playback = UserRecordingHud.message(new UserRecordingHudState(true,
                "PLAYBACK FF 2147483646/2147483647", "VERIFY truncated-sidecar", Integer.MAX_VALUE - 1, true, false));
        assertEquals("F 2147483646\nOF 2147483647", playback.detail());
        assertEquals("SIDECAR SHORT", playback.note());
        var recording = UserRecordingHud.message(new UserRecordingHudState(true,
                "REC frame 2147483647", "long-name.bk2", Integer.MAX_VALUE, false, false));
        assertEquals("F 2147483647", recording.detail());
        var desync = UserRecordingHud.message(new UserRecordingHudState(true,
                "PLAYBACK 44/120", "VERIFY first-mismatch(frame=44, field=p1CentreX, expected=100, actual=321)",
                44, true, false));
        assertEquals("DESYNC F 44", desync.note());
    }

    @Test
    void errorAndStoppedStatesCannotLookLikeActiveRecording() {
        var error = UserRecordingHud.message(new UserRecordingHudState(true,
                "REC ERROR", "IO_ERROR: disk full", 42, false, true));
        assertEquals(TransportHud.Status.ERROR, error.status());
        assertEquals("REC ERROR", error.title());
        assertEquals("IO_ERROR: disk full", error.detail());
        var stopped = UserRecordingHud.message(new UserRecordingHudState(true,
                "REC STOPPED", "USER_STOPPED", 42, false, false));
        assertEquals(TransportHud.Status.STOP, stopped.status());
        assertEquals("FRAME 42", stopped.note());
    }

    @Test
    void hiddenOrMissingStatesDoNotOpenTextBatchOrDrawBacking() {
        var text = mock(PixelFontTextRenderer.class);
        var graphics = mock(GraphicsManager.class);
        UserRecordingHud.render(text, graphics, UserRecordingHudState.hidden());
        UserRecordingHud.render(text, graphics, null);
        UserRecordingHud.render(null, graphics, UserRecordingHud.holdPromptState(30, 60));
        verifyNoInteractions(text, graphics);
    }

    @Test
    void renderingUsesLiveProjectionAndNativeGlyphSize() {
        var text = mock(PixelFontTextRenderer.class);
        var graphics = mock(GraphicsManager.class);
        when(graphics.getProjectionWidth()).thenReturn(400);
        UserRecordingHud.render(text, graphics, new UserRecordingHudState(true,
                "REC frame 1248", "ehz1-review.bk2", 1248, false, false));
        verify(text).drawShadowedText(eq("RECORDING"), eq(270), eq(172), any(), eq(1f));
        verify(text).drawShadowedText(eq("FRAME 1248"), eq(256), eq(186), any(), eq(1f));
        verify(text).endBatch();
    }
}
