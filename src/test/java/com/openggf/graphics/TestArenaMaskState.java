package com.openggf.graphics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestArenaMaskState {
    @Test void boundaryHasNoSpatialFeatherOffset() {
        var state = new ArenaMaskState(240,560,1);
        assertEquals(1,state.targetOpacity(239));
        assertEquals(0,state.targetOpacity(240));
        assertEquals(0,state.targetOpacity(559));
        assertEquals(1,state.targetOpacity(560));
    }
    @Test void fixedArenaKeepsNativeViewAtEverySupportedWidth() {
        for (int width : new int[]{320, 352, 400, 528, 800}) {
            int inset = (width - 320) / 2;
            var mask = ArenaMaskState.fromBounds(352, 352, 352 - inset, width, 100, false);
            assertEquals(inset, mask.left());
            assertEquals(inset + 320, mask.right());
            assertEquals(width > 320, mask.visible(width));
        }
    }
    @Test void cameraTravelDefinesWholeArenaRatherThanOneCentredScreen() {
        // FBZ1's 128px camera travel represents 448px of native world.
        var mask = ArenaMaskState.fromBounds(0x2E20, 0x2EA0, 0x2E20 - 100, 800, 1, false);
        assertEquals(100, mask.left());
        assertEquals(548, mask.right());
        var moved = ArenaMaskState.fromBounds(0x2E20, 0x2EA0, 0x2E20, 800, 2, false);
        assertEquals(0, moved.left());
        assertEquals(448, moved.right());
    }
    @Test void ordinaryRightEdgeAndBoundsExpansionReleaseWithoutAnEventSignal() {
        var edge = ArenaMaskState.fromBounds(0, 1000, 900, 800, 9, false);
        assertEquals(0, edge.left()); assertEquals(420, edge.right());
        var released = ArenaMaskState.fromBounds(0, 1600, 900, 800, 10, false);
        assertFalse(released.visible(800));
        assertEquals(9, edge.noiseFrame(), "retained samples never advance during drawing");
    }
    @Test void nativeWrapAndTransientInvertedBoundsDoNotInventAnArena() {
        assertFalse(ArenaMaskState.fromBounds(100, 100, 50, 320, 1, false).visible(320));
        assertFalse(ArenaMaskState.fromBounds(100, 100, 50, 800, 1, true).visible(800));
        assertFalse(ArenaMaskState.fromBounds(200, 100, 50, 800, 1, false).visible(800));
    }
}
