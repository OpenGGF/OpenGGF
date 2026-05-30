package com.openggf.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Verifies the level-select widescreen centering math.
 *
 * <p>Each level-select manager applies {@code xOffset = (viewportWidth - 320) / 2}
 * to all rendered elements so the 320-px-wide content block is centered within the
 * current projection viewport. At native width 320 the offset must be exactly 0
 * (byte-identical output). At widescreen widths the offset must equal half the
 * extra pixels.
 */
class TestLevelSelectWidescreenCentering {

    /** Mirrors the xOffset() helper in the three level-select managers. */
    private static int xOffset(int viewportWidth) {
        return (viewportWidth - 320) / 2;
    }

    @Test
    void offsetIsZeroAtNative() {
        assertEquals(0, xOffset(320), "native 320px must produce zero offset");
    }

    @Test
    void offsetCentersContentAt426px() {
        // 426 − 320 = 106  →  offset = 53
        assertEquals(53, xOffset(426));
    }

    @Test
    void offsetCentersContentAt480px() {
        // 480 − 320 = 160  →  offset = 80
        assertEquals(80, xOffset(480));
    }

    @Test
    void offsetCentersContentAt640px() {
        // 640 − 320 = 320  →  offset = 160
        assertEquals(160, xOffset(640));
    }

    @Test
    void offsetCentersContentAt1280px() {
        // 1280 − 320 = 960  →  offset = 480
        assertEquals(480, xOffset(1280));
    }

    /**
     * Spot-check that the "element within content" arithmetic is also correct.
     *
     * <p>In S2 level-select the left zone column starts at x=24 within the 320-px
     * content block. At viewport width 480 the content is offset by 80, so the
     * absolute screen x must be 80 + 24 = 104.
     */
    @Test
    void absoluteElementPositionAtWidescreen() {
        int nativeX = 24; // leftZoneX within 320-px content
        int vp = 480;
        int expected = xOffset(vp) + nativeX; // 80 + 24 = 104
        assertEquals(104, expected);
    }

    /**
     * Verify that at native width the element position is unchanged (byte-identical).
     */
    @Test
    void absoluteElementPositionAtNativeIsUnchanged() {
        int nativeX = 24;
        assertEquals(nativeX, xOffset(320) + nativeX);
    }
}
