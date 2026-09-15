package com.openggf.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestTraceCaptureFrameWindow {
    @Test
    void frameWindowIsInclusiveAndLeavesSemanticClipsAlone() {
        assertArrayEquals(new int[]{0, 0}, TraceCaptureTool.parseFrameWindow("frames:0:0"));
        assertArrayEquals(new int[]{100, 160}, TraceCaptureTool.parseFrameWindow("frames:100:160"));
        assertNull(TraceCaptureTool.parseFrameWindow("aiz-fire-transition"));
        assertNull(TraceCaptureTool.parseFrameWindow("aiz-battleship-to-boss"));
    }

    @Test
    void malformedAndReversedWindowsFailInsteadOfCapturingAnotherInterval() {
        for (String spec : new String[]{"frames:", "frames:1", "frames:1:2:3",
                "frames:-1:3", "frames:3:2", "frames:a:2"}) {
            assertThrows(IllegalArgumentException.class, () -> TraceCaptureTool.parseFrameWindow(spec), spec);
        }
    }
}
