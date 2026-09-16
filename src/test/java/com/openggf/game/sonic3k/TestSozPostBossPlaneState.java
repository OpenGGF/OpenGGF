package com.openggf.game.sonic3k;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSozPostBossPlaneState {
    @Test void twoRowsPerDispatchRetainOldDescriptorsAcrossArtReplacementAndReplay() {
        var state = new SozZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);
        var plane = state.events().postBossPlane();
        plane.begin(0x1200, 0x440, 0x340, band -> -0x100, (x,y) -> 0x1111);
        assertEquals(0x1111, plane.descriptor(0x208, 0x420));
        plane.advance(0x340, (x,y) -> 0x2222);
        assertEquals(0x2222, plane.descriptor(0x208, 0x420));
        assertEquals(0x2222, plane.descriptor(0x208, 0x410));
        assertEquals(0x1111, plane.descriptor(0x208, 0x400));
        byte[] before = state.captureBytes();
        plane.advance(0x340, (x,y) -> 0x3333);
        byte[] after = state.captureBytes();
        state.restoreBytes(before);
        assertArrayEquals(before, state.captureBytes());
        plane.advance(0x340, (x,y) -> 0x3333);
        assertArrayEquals(after, state.captureBytes());
        assertEquals(0x2222, plane.descriptor(0x208, 0x420));
        assertEquals(0x3333, plane.descriptor(0x208, 0x400));
        assertEquals(0x1111, plane.descriptor(0x208, 0x3E0));
    }

    @Test void movingCameraWritesOrdinaryEnteringRowAfterDelayedRows() {
        var state = new SozZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);
        var plane = state.events().postBossPlane();
        plane.begin(0, 0x440, 0x340, band -> 0, (x,y) -> 0x1111);
        plane.advance(0x350, (x,y) -> y);
        assertEquals(0x430, plane.descriptor(0x200, 0x430));
        assertEquals(0x420, plane.descriptor(0x200, 0x420));
        assertEquals(0x410, plane.descriptor(0x200, 0x410));
        assertEquals(0x1111, plane.descriptor(0x200, 0x400));
        // Physical row and column identity remains native regardless of host viewport.
        assertEquals(plane.descriptor(0x200,0x430), plane.descriptor(0x600,0x530));
        plane.clear(); assertEquals(0,plane.revision());
    }

    @Test void nativeUnsignedClipDoesNotTreatWrappedIntervalAsContinuous() {
        var state = new SozZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);
        var plane = state.events().postBossPlane();
        plane.begin(0, 0x440, 0x7E0, band -> 0, (x,y) -> 0x1111);
        plane.advance(0x7E0, (x,y) -> 0x2222);
        // Native lower=$7E0, upper=$0D0: both delayed rows fail the unsigned bounds.
        assertEquals(0x1111, plane.descriptor(0x200,0xC0));
        plane.advance(0x7D0, (x,y) -> y);
        // The subsequent ordinary upward entering-row write still occurs.
        assertEquals(0x7D0, plane.descriptor(0x200,0x7D0));
        assertEquals(0x1111, plane.descriptor(0x200,0x7E0));
    }
}
