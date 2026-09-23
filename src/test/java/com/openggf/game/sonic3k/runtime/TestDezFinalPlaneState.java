package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestDezFinalPlaneState {
    @Test void replacementStartsAtF0AndRetainsUpperRowsForEightPasses() {
        var plane = new DezFinalPlaneState();
        plane.refresh(0, 0, (x, y) -> 0x8001);
        plane.beginRedraw();
        for (int pass = 0; pass < 8; pass++) {
            plane.advanceRedraw((x, y) -> 0x6002);
            int boundary = 224 - pass * 32;
            for (int y = 0; y < 256; y += 8)
                assertEquals(y < boundary ? 0x8001 : 0x6002, plane.descriptor(0, y));
            assertEquals(pass < 7, plane.redrawing());
        }
        assertEquals(-16, plane.delayedPosition());
        assertEquals(-1, plane.delayedRowCount());
        int revision = plane.revision();
        plane.advanceRedraw((x, y) -> fail("Completed redraw must not read layout"));
        assertEquals(revision, plane.revision());
    }

    @Test void rowWriteWrapsTheDestinationButReadsOriginalLayoutCoordinates() {
        var plane = new DezFinalPlaneState();
        plane.writeRow(0x3F7, 0x1F7, 2, (x, y) -> (y / 8) * 128 + x / 8);
        assertEquals(62 * 128 + 126, plane.descriptor(0x1F0, 0xF0));
        assertEquals(62 * 128 + 128, plane.descriptor(0, 0xF0));
        assertEquals(63 * 128 + 129, plane.descriptor(8, 0xF8));
        assertEquals(0, plane.descriptor(16, 0xF0));
        assertEquals(0, plane.descriptor(0, 0));
    }

    @Test void fullRefreshReadsEveryCellOnceWithCameraAlignmentAndUnsignedDescriptors() {
        var plane = new DezFinalPlaneState();
        int[] reads = {0};
        plane.refresh(0x387, 0xAB, (x, y) -> { reads[0]++; return 0x8000 | ((x / 8 + y / 8) & 0x7FF); });
        assertEquals(2048, reads[0]);
        assertEquals(0x8000 | (0x380 / 8 + 0xA0 / 8), plane.descriptor(0x380, 0xA0));
        assertEquals(0x8000 | (0x578 / 8 + 0x198 / 8), plane.descriptor(0x578, 0x198));
    }

    @Test void runtimeRewindRestoresRetainedCellsAndResumesTheSameRedraw() {
        var state = new DezFinalBossZoneRuntimeState(PlayerCharacter.SONIC_ALONE);
        state.plane().refresh(0, 0, (x, y) -> 0xFFFF);
        state.plane().beginRedraw();
        state.plane().advanceRedraw((x, y) -> y);
        byte[] saved = state.captureBytes();
        for (int i = 0; i < 7; i++) state.plane().advanceRedraw((x, y) -> y);
        byte[] completed = state.captureBytes();
        state.restoreBytes(saved);
        assertArrayEquals(saved, state.captureBytes());
        assertEquals(0xFFFF, state.plane().descriptor(0, 0));
        for (int i = 0; i < 7; i++) state.plane().advanceRedraw((x, y) -> y);
        assertArrayEquals(completed, state.captureBytes());
    }
}
