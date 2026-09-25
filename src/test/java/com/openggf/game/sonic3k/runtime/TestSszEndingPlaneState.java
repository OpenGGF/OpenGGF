package com.openggf.game.sonic3k.runtime;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSszEndingPlaneState {
    @Test void redrawReplacesTwoRowsPerPassAndRestoresMixedPlane() {
        var plane = new SszEndingPlaneState();
        plane.begin(0x400, (x, y) -> y + x / 8);
        assertEquals(0x4F0, plane.descriptor(0, 0xF0));
        assertFalse(plane.advance((x, y) -> y + x / 8));
        assertEquals(0x230, plane.descriptor(0, 0xF0));
        assertEquals(0x4D0, plane.descriptor(0, 0xD0));
        var saved = ByteBuffer.allocate(SszEndingPlaneState.CAPTURE_BYTES);
        plane.capture(saved);
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) plane.restore(ByteBuffer.wrap(saved.array()));
            for (int i = 0; i < 6; i++) assertFalse(plane.advance((x, y) -> y + x / 8));
            assertTrue(plane.advance((x, y) -> y + x / 8));
            assertEquals(-1, plane.remaining());
            for (int y = 0; y < 256; y += 8)
                for (int x = 0; x < 512; x += 8)
                    assertEquals(0x100 + y + (0x200 + x) / 8, plane.descriptor(x, y));
        }
    }
    @Test void secondRedrawPreservesUnwrittenRowsAndReplaysItsPartialPlane() {
        var plane = new SszEndingPlaneState();
        plane.begin(0x100, (x, y) -> y);
        plane.beginSecondRedraw();
        assertFalse(plane.advanceSecondRedraw((x, y) -> y + x / 8));
        assertEquals(0x7F0, plane.descriptor(0, 0xF0));
        assertEquals(0x1D0, plane.descriptor(0, 0xD0));
        var saved = ByteBuffer.allocate(SszEndingPlaneState.CAPTURE_BYTES); plane.capture(saved);
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) plane.restore(ByteBuffer.wrap(saved.array()));
            for (int i = 0; i < 6; i++) assertFalse(plane.advanceSecondRedraw((x, y) -> y + x / 8));
            assertTrue(plane.advanceSecondRedraw((x, y) -> y + x / 8));
            for (int y = 0; y < 256; y += 8)
                for (int x = 0; x < 512; x += 8)
                    assertEquals(0x700 + y + x / 8, plane.descriptor(x, y));
        }
    }

    @Test void incomingWaterRowsStreamAtTileBoundariesAndRestoreTheirCursor() {
        var plane = new SszEndingPlaneState();
        plane.begin(0x700, (x, y) -> y);
        plane.finishSecondRedraw();
        int initial = plane.revision();
        plane.streamRows(0x72F, (x, y) -> y);
        assertEquals(initial, plane.revision());
        var saved = ByteBuffer.allocate(SszEndingPlaneState.CAPTURE_BYTES); plane.capture(saved);
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) plane.restore(ByteBuffer.wrap(saved.array()));
            plane.streamRows(0x730, (x, y) -> y);
            assertEquals(0x810, plane.descriptor(0, 0x10));
            assertEquals(initial + 1, plane.revision());
            plane.streamRows(0x750, (x, y) -> y);
            assertEquals(0x820, plane.descriptor(0, 0x20));
            assertEquals(0x830, plane.descriptor(0, 0x30));
            plane.streamRows(0x740, (x, y) -> y);
            assertEquals(0x740, plane.descriptor(0, 0x40));
        }
    }

    @Test void emeraldCycleKeepsTheWaterCyclesTimerAndUnalignedCursor() {
        var plane = new SszEndingPlaneState();
        plane.begin(0, (x, y) -> 0);
        assertEquals(6, plane.advanceWaterPalette());
        for (int i = 0; i < 7; i++) assertEquals(-1, plane.advanceEmeraldPalette());
        assertEquals(10, plane.advanceEmeraldPalette(), "shared byte offset is not rounded to a pair index");
        for (int row = 0; row < 12; row++) {
            for (int i = 0; i < 3; i++) assertEquals(-1, plane.advanceEmeraldPalette());
            int expected = row == 11 ? 0 : 14 + 4 * row;
            assertEquals(expected, plane.advanceEmeraldPalette());
        }
    }

    @Test void waterPaletteGateCadenceWrapAndReplayFollowNativeWords() {
        var plane = new SszEndingPlaneState();
        for (int i = 0; i < 20; i++) assertEquals(-1, plane.advanceWaterPalette());
        plane.begin(0, (x, y) -> 0);
        assertEquals(6, plane.advanceWaterPalette(), "first selection increments before reading");
        var saved = ByteBuffer.allocate(SszEndingPlaneState.CAPTURE_BYTES); plane.capture(saved);
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) plane.restore(ByteBuffer.wrap(saved.array()));
            for (int row = 2; row <= 8; row++) {
                for (int i = 0; i < 7; i++) assertEquals(-1, plane.advanceWaterPalette());
                assertEquals((row * 6) % 0x30, plane.advanceWaterPalette());
            }
        }
    }

}
