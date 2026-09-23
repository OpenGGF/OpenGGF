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

    @Test void ordinaryScrollDrawsOnlyTheLeadingColumnOrRow() {
        var plane = new DezFinalPlaneState();
        plane.refresh(0, 0, (x, y) -> 1);
        plane.resetDrawPosition(0x380, 0xA8);
        int[] reads = {0};
        plane.drawAsYouMove(0x390, 0xA8, (x, y) -> { reads[0]++; return x; });
        assertEquals(60, reads[0]); // 15 blocks, four descriptors each.
        assertEquals(0x4D0, plane.descriptor(0x4D0, 0xA0));
        assertEquals(1, plane.descriptor(0x4C0, 0xA0));
        plane.drawAsYouMove(0x390, 0xB0, (x, y) -> y);
        assertEquals(0x190, plane.descriptor(0x390, 0x90));
        assertEquals(0x198, plane.descriptor(0x390, 0x98));
        // Twenty-one blocks end at $4DF; the adjacent column retains its old cell.
        assertEquals(1, plane.descriptor(0x4E0, 0x90));
    }

    @Test void largeScrollUsesTheNativeByteDirectionAndTwoWriteCap() {
        var plane = new DezFinalPlaneState();
        plane.resetDrawPosition(0x380, 0xA0);
        int[] reads = {0};
        plane.drawAsYouMove(0x480, 0xA0, (x, y) -> { reads[0]++; return x; });
        // $380-$480 = $FF00: tst.b is zero, so ROM chooses the new left edge.
        assertEquals(120, reads[0]);
        assertEquals(0x480, plane.descriptor(0x480, 0xA0));
        assertEquals(0x490, plane.descriptor(0x490, 0xA0));
        assertEquals(0, plane.descriptor(0x4A0, 0xA0));
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
    @org.junit.jupiter.api.Test void movingFloorBandRetainsSkyAndUsesNativeTwoColumnCap() {
        var plane = new DezFinalPlaneState();
        plane.refresh(0, 0, (x, y) -> 0x111);
        plane.resetDrawPosition(0x80, 0x20);
        var reads = new java.util.ArrayList<Integer>();
        plane.drawHorizontalBand(0xA0, 0xE0, 3, (x, y) -> {
            reads.add(x); return 0x222;
        });
        org.junit.jupiter.api.Assertions.assertEquals(24, reads.size());
        org.junit.jupiter.api.Assertions.assertEquals(0x1D0, reads.getFirst());
        org.junit.jupiter.api.Assertions.assertEquals(0x1E8, reads.getLast());
        org.junit.jupiter.api.Assertions.assertEquals(0x111, plane.descriptor(0x1D0, 0xD0));
        org.junit.jupiter.api.Assertions.assertEquals(0x222, plane.descriptor(0x1D0, 0xE0));
        org.junit.jupiter.api.Assertions.assertEquals(0x222, plane.descriptor(0x1E0, 0x100));
        plane.writeRow(0x20, 0x1E0, 2, (x, y) -> 0x333);
        org.junit.jupiter.api.Assertions.assertEquals(0x333, plane.descriptor(0x20, 0xE0));
        org.junit.jupiter.api.Assertions.assertEquals(0x111, plane.descriptor(0x40, 0xE0));
    }

}
