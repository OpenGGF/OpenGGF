package com.openggf.game.presentation;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestTransportHud {
    private final PixelFontTextRenderer text = new PixelFontTextRenderer();

    @Test
    void bothLanesClearOriginalHudAndEachOtherAtNativeAndWideWidths() {
        var rewind = new TransportHud.Message(TransportHud.Status.REWIND, "LIVE REWIND", "FRAME 1248", "", -1);
        var recording = new TransportHud.Message(TransportHud.Status.HOLD, "HOLD TO REC", "SHIFT+RECORD", "50% OF 1 SEC", 50);
        for (int width : new int[] {320, 352, 400}) {
            var left = TransportHud.layout(text, width, TransportHud.Placement.ABOVE_LIVES, rewind);
            var right = TransportHud.layout(text, width, TransportHud.Placement.BOTTOM_RIGHT, recording);
            assertAll("width=" + width,
                    () -> assertTrue(left.x() >= 8),
                    () -> assertTrue(left.y() >= 64, "leave score/time/rings clear"),
                    () -> assertTrue(left.y() + left.height() <= 196, "leave lives at y=200 clear"),
                    () -> assertTrue(right.y() >= 64, "leave score/time/rings clear"),
                    () -> assertTrue(right.x() > Math.max(0, (width - 320) / 2) + 100,
                            "clear centred or left-anchored lives"),
                    () -> assertTrue(left.x() + left.width() + 8 <= right.x(), "simultaneous transports need separate lanes"),
                    () -> assertTrue(right.x() + right.width() <= width - 8),
                    () -> assertTrue(right.y() + right.height() <= 216));
        }
    }

    @Test
    void longUntrustedLabelsStayInsidePlateWithNoLineBreakEscape() {
        var message = new TransportHud.Message(TransportHud.Status.ERROR,
                "REC ERROR WITH A LONG TITLE", "IO_ERROR: a very long failure message",
                "a filename with\nline breaks and a very long tail.bk2", -1);
        var layout = TransportHud.layout(text, 320, TransportHud.Placement.BOTTOM_RIGHT, message);
        assertAll(
                () -> assertTrue(text.measureWidth(layout.title()) <= layout.width() - 30),
                () -> assertTrue(layout.details().stream().allMatch(line -> text.measureWidth(line) <= layout.width() - 16)),
                () -> assertTrue(text.measureWidth(layout.note()) <= layout.width() - 16),
                () -> assertFalse(layout.note().contains("\n")),
                () -> assertTrue(layout.title().startsWith("REC ERROR")),
                () -> assertTrue(layout.details().getFirst().startsWith("IO_ERROR")),
                () -> assertTrue(layout.note().endsWith("...")));
    }

    @Test
    void longFrameCountersUseTwoExactRowsInsideTheSameLane() {
        var message = new TransportHud.Message(TransportHud.Status.PLAY_WARNING, "PLAYBACK FF",
                "F 2147483646\nOF 2147483647", "SIDECAR SHORT", -1);
        for (int width : new int[] {320, 400}) {
            var layout = TransportHud.layout(text, width, TransportHud.Placement.BOTTOM_RIGHT, message);
            assertEquals(List.of("F 2147483646", "OF 2147483647"), layout.details());
            assertEquals("SIDECAR SHORT", layout.note());
            assertTrue(layout.y() >= 64);
            assertEquals(216, layout.y() + layout.height());
        }
    }

    @Test
    void backingAndTransportGlyphFlushBeforeTextAndBatchClosesOnFailure() {
        var graphics = mock(GraphicsManager.class);
        var renderer = mock(PixelFontTextRenderer.class);
        when(graphics.isGlInitialized()).thenReturn(true);
        doThrow(new IllegalStateException("draw failed")).when(renderer)
                .drawShadowedText(eq("RECORDING"), anyInt(), anyInt(), any(), eq(1f));
        var message = new TransportHud.Message(TransportHud.Status.RECORD, "RECORDING", "FRAME 42", "", -1);

        assertThrows(IllegalStateException.class, () -> TransportHud.render(renderer, graphics, 320,
                TransportHud.Placement.BOTTOM_RIGHT, message));

        var order = inOrder(graphics, renderer);
        order.verify(graphics, atLeastOnce()).registerCommand(any());
        order.verify(graphics).flushScreenSpace();
        order.verify(renderer).beginBatch();
        order.verify(renderer).drawShadowedText(eq("RECORDING"), anyInt(), anyInt(), any(), eq(1f));
        order.verify(renderer).endBatch();
    }
}
