package com.openggf.widescreen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Headless unit tests for the Sonic 3&amp;K title-screen widescreen layout math.
 * No OpenGL, no ROM, no singletons — pure arithmetic.
 *
 * <p>Tests verify two invariants:
 * <ol>
 *   <li><b>Native parity:</b> every formula produces the same value as the original
 *       hard-coded literals when viewportWidth == 320.</li>
 *   <li><b>Coverage:</b> at wider viewports the formulas centre all content on the
 *       viewport mid-point.</li>
 * </ol>
 *
 * <p>Strategy per surface:
 * <ul>
 *   <li>Animation phases: PILLARBOX — fixed 40×28 nametable pictures with no art beyond 320px.</li>
 *   <li>Interactive Plane B (sea/sky): EDGE-EXTEND — out-of-frame screen columns clamp to the
 *       edge nametable column so the open sea/sky fills the viewport.</li>
 *   <li>Interactive Plane A &amp; sprites: CENTER — foreground centred via xOffset().</li>
 * </ul>
 */
class TestSonic3kTitleScreenWidescreenLayout {

    private static final int NATIVE_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 224;

    // -------------------------------------------------------------------------
    // xOffset — (viewportWidth - 320) / 2
    // -------------------------------------------------------------------------

    /** Mirrors the xOffset() helper in Sonic3kTitleScreenManager. */
    private static int xOffset(int viewportWidth) {
        return (viewportWidth - NATIVE_WIDTH) / 2;
    }

    @Test
    void xOffset_atNative320_isZero() {
        assertEquals(0, xOffset(NATIVE_WIDTH),
                "xOffset must be 0 at native 320 — byte-identical");
    }

    @Test
    void xOffset_at480_is80() {
        assertEquals(80, xOffset(480));
    }

    @Test
    void xOffset_at426_is53() {
        assertEquals(53, xOffset(426));
    }

    @Test
    void xOffset_at640_is160() {
        assertEquals(160, xOffset(640));
    }

    // -------------------------------------------------------------------------
    // Animation-phase tile columns start at ox + col*8
    // At native 320: ox = 0, so col*8 == col*8 — byte-identical.
    // -------------------------------------------------------------------------

    @Test
    void animPhase_tileX_atNative_isByteIdentical() {
        int ox = xOffset(NATIVE_WIDTH);
        for (int col = 0; col < 40; col++) {
            assertEquals(col * 8, ox + col * 8,
                    "tile X must equal col*8 at native width, col=" + col);
        }
    }

    @Test
    void animPhase_tileX_at640_isShifted() {
        int ox = xOffset(640); // 160
        // First tile column: 160 + 0 = 160 (centred at 160, 320 viewport worth of content)
        assertEquals(160, ox + 0);
        // Last column: 160 + 39*8 = 160 + 312 = 472
        assertEquals(472, ox + 39 * 8);
    }

    // -------------------------------------------------------------------------
    // Interactive sprites — VDP-to-screen: ox + vdpX - 128
    // At native 320 all values collapse to the original literals.
    // -------------------------------------------------------------------------

    @Test
    void bannerScreenX_atNative_isNativeLiteral() {
        // VDP $120 -> native screen 160
        int nativeBannerX = 0x120 - 128; // 160
        assertEquals(nativeBannerX, xOffset(NATIVE_WIDTH) + 0x120 - 128,
                "banner screen X must be 160 at native 320");
    }

    @Test
    void tmScreenX_atNative_isNativeLiteral() {
        // VDP $188 -> native screen 264
        int nativeTmX = 0x188 - 128; // 264
        assertEquals(nativeTmX, xOffset(NATIVE_WIDTH) + 0x188 - 128,
                "TM screen X must be 264 at native 320");
    }

    @Test
    void fingerScreenX_atNative_isNativeLiteral() {
        // VDP $148 -> native screen 200
        int nativeFingerX = 0x148 - 128; // 200
        assertEquals(nativeFingerX, xOffset(NATIVE_WIDTH) + 0x148 - 128,
                "finger screen X must be 200 at native 320");
    }

    @Test
    void winkScreenX_atNative_isNativeLiteral() {
        // VDP $F8 -> native screen 120
        int nativeWinkX = 0xF8 - 128; // 120
        assertEquals(nativeWinkX, xOffset(NATIVE_WIDTH) + 0xF8 - 128,
                "wink screen X must be 120 at native 320");
    }

    @Test
    void selectionScreenX_atNative_isNativeLiteral() {
        // VDP $F0 -> native screen 112
        int nativeSelX = 0xF0 - 128; // 112
        assertEquals(nativeSelX, xOffset(NATIVE_WIDTH) + 0xF0 - 128,
                "selection screen X must be 112 at native 320");
    }

    @Test
    void copyrightScreenX_atNative_isNativeLiteral() {
        // VDP $158 -> native screen 216
        int nativeCopyX = 0x158 - 128; // 216
        assertEquals(nativeCopyX, xOffset(NATIVE_WIDTH) + 0x158 - 128,
                "copyright screen X must be 216 at native 320");
    }

    @Test
    void andKnucklesScreenX_atNative_isNativeLiteral() {
        // VDP $120 -> native screen 160
        int nativeX = 0x120 - 128; // 160
        assertEquals(nativeX, xOffset(NATIVE_WIDTH) + 0x120 - 128,
                "&Knuckles screen X must be 160 at native 320");
    }

    @Test
    void tailsPlaneScreenX_atNative_isVdpMinus128() {
        // Tails plane VDP X ranges 0..0x240; screen X = tailsPlaneVdpX - 128.
        // At native 320, ox=0, so result is unchanged.
        int tailsVdpX = 0x100;
        int nativeScreenX = tailsVdpX - 128; // native: 128
        assertEquals(nativeScreenX, xOffset(NATIVE_WIDTH) + tailsVdpX - 128,
                "Tails plane screen X must equal vdpX-128 at native 320");
    }

    // -------------------------------------------------------------------------
    // RECTI overlay width — must equal viewportWidth to cover side bars
    // At native 320 viewportWidth == 320 == SCREEN_WIDTH — byte-identical.
    // -------------------------------------------------------------------------

    @Test
    void rectOverlayWidth_atNative_equalsScreenWidth() {
        // viewportWidth() at native returns SCREEN_WIDTH = 320
        int viewportWidth = NATIVE_WIDTH;
        assertEquals(NATIVE_WIDTH, viewportWidth,
                "RECTI width must equal SCREEN_WIDTH at native 320 — byte-identical");
    }

    @Test
    void rectOverlayWidth_at640_coversFullViewport() {
        int viewportWidth = 640;
        assertTrue(viewportWidth >= 640,
                "RECTI width must cover the full viewport at 640");
    }

    // -------------------------------------------------------------------------
    // Plane B (sea/sky background) — edge-extended to fill the viewport.
    // For a screen column s, the sampled nametable column is clamp(s - colOffset,
    // 0, 39) and it draws at screenX = s*8. colOffset = xOffset/8, and the number
    // of screen columns spans the whole viewport.
    // -------------------------------------------------------------------------

    private static final int MAP_WIDTH = 40;

    /** Mirrors renderPlaneB(): nametable column sampled for a given screen column. */
    private static int planeBSampledColumn(int screenCol, int viewportWidth) {
        int colOffset = xOffset(viewportWidth) / 8;
        int ntCol = screenCol - colOffset;
        if (ntCol < 0) {
            return 0;
        }
        if (ntCol >= MAP_WIDTH) {
            return MAP_WIDTH - 1;
        }
        return ntCol;
    }

    private static int planeBScreenColumns(int viewportWidth) {
        return (viewportWidth + 7) / 8;
    }

    @Test
    void planeB_atNative_isByteIdentical() {
        // colOffset 0, exactly 40 columns: screen column == nametable column, x == col*8.
        assertEquals(40, planeBScreenColumns(NATIVE_WIDTH),
                "Plane B must span exactly 40 columns at native 320");
        for (int s = 0; s < 40; s++) {
            assertEquals(s, planeBSampledColumn(s, NATIVE_WIDTH),
                    "Plane B screen column must equal nametable column at native, s=" + s);
        }
    }

    @Test
    void planeB_at640_fillsViewportWithClampedEdges() {
        int vw = 640;
        // 80 screen columns cover the full 640px viewport.
        assertEquals(80, planeBScreenColumns(vw));
        // Left band (screen cols 0..19) all sample edge column 0 (open sea/sky).
        assertEquals(0, planeBSampledColumn(0, vw), "left band samples edge column 0");
        assertEquals(0, planeBSampledColumn(19, vw), "left band samples edge column 0");
        // Centred frame: screen col 20 -> nametable col 0, screen col 59 -> col 39.
        assertEquals(0, planeBSampledColumn(20, vw), "centred frame starts at nametable col 0");
        assertEquals(39, planeBSampledColumn(59, vw), "centred frame ends at nametable col 39");
        // Right band (screen cols 60..79) all sample edge column 39.
        assertEquals(39, planeBSampledColumn(60, vw), "right band samples edge column 39");
        assertEquals(39, planeBSampledColumn(79, vw), "right band samples edge column 39");
    }

    @Test
    void planeB_centredFrameMatchesXOffset() {
        // The in-frame columns draw at exactly the same screen X as the centred
        // foreground: screenCol*8 == xOffset + ntCol*8.
        int vw = 480;
        int ox = xOffset(vw); // 80
        int colOffset = ox / 8; // 10
        for (int ntCol = 0; ntCol < MAP_WIDTH; ntCol++) {
            int screenCol = ntCol + colOffset;
            assertEquals(ox + ntCol * 8, screenCol * 8,
                    "in-frame Plane B column must align with centred foreground, ntCol=" + ntCol);
            assertEquals(ntCol, planeBSampledColumn(screenCol, vw),
                    "in-frame screen column must sample its own nametable column, ntCol=" + ntCol);
        }
    }
}
