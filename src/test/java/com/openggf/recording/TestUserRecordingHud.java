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
        assertEquals("REC:", message.title());
        assertEquals("HOLD SHIFT+F9", message.value());
        assertEquals("", message.note());
        assertEquals(50, message.progressPercent());
        assertEquals(0, UserRecordingHud.message(UserRecordingHud.holdPromptState(-5, 60)).progressPercent());
        assertEquals(100, UserRecordingHud.message(UserRecordingHud.holdPromptState(100, 60)).progressPercent());
    }

    @Test
    void activeRecordingIsOneAbbreviatedRowWithoutTheFilename() {
        var message = UserRecordingHud.message(new UserRecordingHudState(true,
                "REC frame 1248", "ehz1-review.bk2", 1248, false, false));
        assertEquals(TransportHud.Status.RECORD, message.status());
        assertEquals("REC", message.title());
        assertEquals("F:1248", message.value());
        assertEquals("", message.note());
        assertEquals(-1, message.progressPercent());
    }

    @Test
    void playbackKeepsFrameTotalFastForwardAndOnlyWarnsOnVerificationProblems() {
        var playing = UserRecordingHud.message(new UserRecordingHudState(true,
                "PLAYBACK 1248/3600", "VERIFY clean", 1248, false, false));
        assertEquals(TransportHud.Status.PLAY, playing.status());
        assertEquals("PLAY", playing.title());
        assertEquals("1248/3600", playing.value());
        assertEquals("Tab: Details", playing.note());

        var warning = UserRecordingHud.message(new UserRecordingHudState(true,
                "PLAYBACK FF 1248/3600", "VERIFY missing-sidecar", 1248, true, false));
        assertEquals(TransportHud.Status.PLAY_WARNING, warning.status());
        assertEquals("FF", warning.title());
        assertEquals(playing.value(), warning.value());
        assertEquals("NO SIDECAR  Tab: Details", warning.note());
    }

    @Test
    void longCountersKeepEveryDigitAndVerificationReasonIsReadable() {
        var playback = UserRecordingHud.message(new UserRecordingHudState(true,
                "PLAYBACK FF 2147483646/2147483647", "VERIFY truncated-sidecar", Integer.MAX_VALUE - 1, true, false));
        assertEquals("2147483646/2147483647", playback.value());
        assertEquals("SIDECAR SHORT  Tab: Details", playback.note());
        var recording = UserRecordingHud.message(new UserRecordingHudState(true,
                "REC frame 2147483647", "long-name.bk2", Integer.MAX_VALUE, false, false));
        assertEquals("F:2147483647", recording.value());
        var desync = UserRecordingHud.message(new UserRecordingHudState(true,
                "PLAYBACK 44/120", "VERIFY first-mismatch(frame=44, field=p1CentreX, expected=100, actual=321)",
                44, true, false));
        assertEquals("DESYNC", desync.title());
        assertEquals("F:44", desync.value());
        assertEquals("Tab: Details", desync.note());
    }

    @Test
    void errorAndStoppedStatesCannotLookLikeActiveRecording() {
        var error = UserRecordingHud.message(new UserRecordingHudState(true,
                "REC ERROR", "IO_ERROR: disk full", 42, false, true));
        assertEquals(TransportHud.Status.ERROR, error.status());
        assertEquals("ERR", error.title());
        assertEquals("IO_ERROR: disk full", error.note());
        var stopped = UserRecordingHud.message(new UserRecordingHudState(true,
                "REC STOPPED", "USER_STOPPED", 42, false, false));
        assertEquals(TransportHud.Status.STOP, stopped.status());
        assertEquals("STOP", stopped.title());
        assertEquals("F:42", stopped.value());
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
        verify(text).drawShadowedText(eq("REC"), anyInt(), eq(203), any(), eq(1f));
        verify(text).drawShadowedText(eq("F:1248"), anyInt(), eq(203), any(), eq(1f));
        verify(text).endBatch();
    }
}
