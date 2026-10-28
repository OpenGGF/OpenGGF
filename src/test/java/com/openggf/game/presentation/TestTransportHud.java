package com.openggf.game.presentation;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestTransportHud {
    private final PixelFontTextRenderer text = PixelFontTextRenderer.compactMenu();

    @Test
    void statusAndValueUseTheSameWordSpacingAsTheFrameCount() {
        for (var status : new TransportHud.Status[] {TransportHud.Status.RECORD, TransportHud.Status.REWIND}) {
            var renderer = spy(PixelFontTextRenderer.compactMenu());
            doNothing().when(renderer).beginBatch();
            doNothing().when(renderer).endBatch();
            doNothing().when(renderer).drawShadowedText(anyString(), anyInt(), anyInt(), any(), anyFloat());
            String label = status == TransportHud.Status.RECORD ? "REC" : "REW";
            TransportHud.render(renderer, null, 320, TransportHud.Placement.BOTTOM_RIGHT,
                    new TransportHud.Message(status, label, "F:1248", "", -1));
            var labelX = ArgumentCaptor.forClass(Integer.class);
            var valueX = ArgumentCaptor.forClass(Integer.class);
            verify(renderer).drawShadowedText(eq(label), labelX.capture(), anyInt(), any(), eq(1f));
            verify(renderer).drawShadowedText(eq("F:1248"), valueX.capture(), anyInt(), any(), eq(1f));
            assertEquals(renderer.measureWidth(" "), valueX.getValue() - labelX.getValue()
                    - renderer.measureWidth(label), "status/value boundary must read as an ordinary word space");
        }
    }

    @Test
    void warningAndErrorUseTheExistingFontPunctuation() {
        var renderer = mock(PixelFontTextRenderer.class);
        for (var status : new TransportHud.Status[] {TransportHud.Status.WARNING, TransportHud.Status.ERROR}) {
            clearInvocations(renderer);
            TransportHud.render(renderer, null, 320, TransportHud.Placement.BOTTOM_RIGHT,
                    new TransportHud.Message(status, "REC ERR", "F:42", "disk full", -1));
            verify(renderer).drawShadowedText(eq("!"), anyInt(), anyInt(), any(), eq(1f));
        }
    }

    @Test
    void bothLanesClearOriginalHudAndEachOtherAtNativeAndWideWidths() {
        var rewind = new TransportHud.Message(TransportHud.Status.REWIND, "REW", "F:1248", "", -1);
        var recording = new TransportHud.Message(TransportHud.Status.HOLD, "HOLD", "SHIFT+REC", "", 50);
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
    void largestCountersStayCompleteAndSeparateAtEverySupportedWidth() {
        for (int width : new int[]{320,352,400}) {
            var left = TransportHud.layout(text,width,TransportHud.Placement.ABOVE_LIVES,
                    new TransportHud.Message(TransportHud.Status.REWIND,"REW","F:"+Integer.MAX_VALUE,"",-1));
            var right = TransportHud.layout(text,width,TransportHud.Placement.BOTTOM_RIGHT,
                    new TransportHud.Message(TransportHud.Status.PLAY,"PLAY","2147483646/2147483647","Tab: Details",-1));
            assertEquals("F:2147483647",left.value());
            assertEquals("2147483646/2147483647",right.value());
            assertEquals("Tab: Details",right.note());
            assertTrue(left.x()+left.width()+8<=right.x());
            assertTrue(right.x()+right.width()<=width-8);
        }
    }

    @Test
    void wideNativeHudOriginIsReservedEvenForLongExceptionNotes() {
        for(int width:new int[]{352,400}) {
            var left=TransportHud.layout(text,width,TransportHud.Placement.ABOVE_LIVES,
                    new TransportHud.Message(TransportHud.Status.REWIND,"REW","F:2147483647","",-1));
            var right=TransportHud.layout(text,width,TransportHud.Placement.BOTTOM_RIGHT,
                    new TransportHud.Message(TransportHud.Status.ERROR,"ERR","F:44","diagnostic ".repeat(50),-1));
            assertTrue(left.x()+left.width()+8<=right.x());
        }
    }

    @Test
    void longUntrustedLabelsStayInsidePlateWithNoLineBreakEscape() {
        var message = new TransportHud.Message(TransportHud.Status.ERROR,
                "REC ERROR WITH A LONG TITLE", "IO_ERROR: a very long failure message",
                "a filename with\nline breaks and a very long tail.bk2", -1);
        var layout = TransportHud.layout(text, 320, TransportHud.Placement.BOTTOM_RIGHT, message);
        int textRoom = layout.width() - 17 - 4;
        assertAll(
                () -> assertTrue(layout.width() <= 179),
                () -> assertTrue(text.measureWidth(layout.title()) + (layout.value().isEmpty() ? 0 : text.measureWidth(" ") + text.measureWidth(layout.value())) <= textRoom),
                () -> assertTrue(text.measureWidth(layout.note()) <= textRoom),
                () -> assertFalse(layout.note().contains("\n")),
                () -> assertTrue(layout.title().startsWith("REC ERROR")),
                () -> assertTrue(layout.note().endsWith("...")));
    }

    @Test
    void routinePlatesUseOneRowAndExceptionsAtMostTwo() {
        var oneRow = new TransportHud.Message(TransportHud.Status.RECORD, "REC", "F:2147483647", "", -1);
        var twoRows = new TransportHud.Message(TransportHud.Status.PLAY_WARNING, "FF",
                "2147483646/2147483647", "SIDECAR SHORT", -1);
        for (int width : new int[] {320, 400}) {
            var single = TransportHud.layout(text, width, TransportHud.Placement.BOTTOM_RIGHT, oneRow);
            var dual = TransportHud.layout(text, width, TransportHud.Placement.BOTTOM_RIGHT, twoRows);
            assertEquals(16, single.height());
            assertEquals(28, dual.height());
            assertEquals("2147483646/2147483647", dual.value(), "complete counters must remain visible");
            assertEquals("SIDECAR SHORT", dual.note());
            assertEquals(216, dual.y() + dual.height());
            assertTrue(single.width() < 144, "plates size to their content");
        }
    }

    @Test
    void sessionStatusPlatesStackInTheTopRightLaneClearOfTheNativeHudColumn() {
        var video = new TransportHud.Message(TransportHud.Status.RECORD, "VIDEO", "", "", -1);
        var pause = new TransportHud.Message(TransportHud.Status.PAUSE, "PAUSE", "", "", -1);
        for (int width : new int[] {320, 400}) {
            var first = TransportHud.layout(text, width, TransportHud.Placement.TOP_RIGHT, video);
            var second = TransportHud.layout(text, width, TransportHud.Placement.TOP_RIGHT_SECOND, pause);
            assertEquals(8, first.y());
            assertEquals(16, first.height(), "one row");
            assertEquals(width - 8, first.x() + first.width(), "right-aligned to the margin");
            assertTrue(second.y() >= first.y() + first.height() + 2, "the second slot sits below the first");
            assertTrue(second.x() > 160, "clear of score, time and rings at the upper left");
            assertEquals("PAUSE", second.title());
        }
    }

    @Test
    void backingAndTransportGlyphFlushBeforeTextAndBatchClosesOnFailure() {
        var graphics = mock(GraphicsManager.class);
        var renderer = mock(PixelFontTextRenderer.class);
        when(graphics.isGlInitialized()).thenReturn(true);
        doThrow(new IllegalStateException("draw failed")).when(renderer)
                .drawShadowedText(eq("REC"), anyInt(), anyInt(), any(), eq(1f));
        var message = new TransportHud.Message(TransportHud.Status.RECORD, "REC", "F:42", "", -1);

        assertThrows(IllegalStateException.class, () -> TransportHud.render(renderer, graphics, 320,
                TransportHud.Placement.BOTTOM_RIGHT, message));

        var order = inOrder(graphics, renderer);
        order.verify(graphics, atLeastOnce()).registerCommand(any());
        order.verify(graphics).flushScreenSpace();
        order.verify(renderer).beginBatch();
        order.verify(renderer).drawShadowedText(eq("REC"), anyInt(), anyInt(), any(), eq(1f));
        order.verify(renderer).endBatch();
    }
}
