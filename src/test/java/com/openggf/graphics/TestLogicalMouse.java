package com.openggf.graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestLogicalMouse {
    @Test void framebufferScaleAndLetterboxingUseTheGameViewport() {
        // A 2x framebuffer, with a 320x224 picture scaled 4x and centred in 1920x1080.
        assertArrayEquals(new int[]{0, 0, 1}, map(160, 46));
        assertArrayEquals(new int[]{160, 112, 1}, map(480, 270));
        assertArrayEquals(new int[]{319, 223, 1}, map(799, 493));
        assertArrayEquals(new int[]{-1, 0, 0}, map(159, 46));
        assertArrayEquals(new int[]{320, 0, 0}, map(800, 46));
        assertArrayEquals(new int[]{0, -1, 0}, map(160, 45));
        assertArrayEquals(new int[]{0, 224, 0}, map(160, 494));
    }

    @Test void everySupportedLogicalWidthHasExclusiveRightAndBottomEdges() {
        for (int width : new int[]{320, 352, 400, 528, 800}) {
            assertArrayEquals(new int[]{width - 1, 223, 1}, LogicalMouse.map(
                    width, 224, width, 224, 0, 0, width, 224, width - 0.5, 223.5, width, 224));
            assertArrayEquals(new int[]{width, 224, 0}, LogicalMouse.map(
                    width, 224, width, 224, 0, 0, width, 224, width, 224, width, 224));
        }
        assertNull(LogicalMouse.map(0, null, 0, 0, 320, 224));
    }

    private int[] map(double x, double y) {
        return LogicalMouse.map(960, 540, 1920, 1080, 320, 92, 1280, 896, x, y, 320, 224);
    }
}
