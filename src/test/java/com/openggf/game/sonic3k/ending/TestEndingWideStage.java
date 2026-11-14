package com.openggf.game.sonic3k.ending;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** The presentation-only placement of ending sprite groups in a picture wider than 320 pixels. */
class TestEndingWideStage {
    private static int px(double pixels) { return (int) Math.round(pixels * 65536); }

    @Test void theNativeWidthMovesNothing() {
        for (int x = -200; x <= 520; x += 7) {
            assertEquals(0, EndingWideStage.shift(0, px(x), px(x + 90)), "group at " + x);
            assertEquals(0, EndingWideStage.spread(0, px(x)), "particle at " + x);
        }
    }

    @ParameterizedTest @ValueSource(ints = {16, 40, 104, 240})
    void aGroupClearOfBothEdgesStandsWhereTheCartridgePutsIt(int margin) {
        assertEquals(0, EndingWideStage.shift(margin, px(EndingWideStage.BAND), px(320 - EndingWideStage.BAND)));
        assertEquals(0, EndingWideStage.shift(margin, px(96), px(128)), "the Tornado meets Sonic unmoved");
        assertEquals(0, EndingWideStage.shift(margin, px(44), px(224)), "the towed emerald during the close shot");
    }

    @ParameterizedTest @ValueSource(ints = {16, 40, 104, 240})
    void aGroupOutsideANativeEdgeIsAsFarOutsideThePicture(int margin) {
        // Parked 64 pixels left of the cartridge's screen: 64 pixels left of the wider one.
        assertEquals(-margin, EndingWideStage.shift(margin, px(-64), px(-64)));
        assertEquals(-margin, EndingWideStage.shift(margin, px(0), px(0)));
        // Turning round 96 pixels past the right edge, towing something still nearer the edge.
        assertEquals(margin, EndingWideStage.shift(margin, px(356), px(416)));
        assertEquals(margin, EndingWideStage.shift(margin, px(300), px(320)));
    }

    @ParameterizedTest @ValueSource(ints = {16, 40, 104, 240})
    void aLongGroupFollowsItsTailInAndItsHeadOut(int margin) {
        // Entering from the left: the whole margin until the tail is inside the native window.
        assertEquals(-margin, EndingWideStage.shift(margin, px(-10), px(90)));
        // Leaving to the right: the head reaches the real edge when it reaches the native one.
        assertEquals(margin, EndingWideStage.shift(margin, px(220), px(320)));
        // Leaving to the left with the head already at the native edge.
        assertEquals(-margin, EndingWideStage.shift(margin, px(0), px(180)));
    }

    @ParameterizedTest @ValueSource(ints = {16, 40, 104, 240})
    void aCrossingGroupNeverTurnsBackAndNeverJumps(int margin) {
        int length = 60, previous = Integer.MIN_VALUE;
        // The steepest the eased band gets is 1.5 margins per band, per native pixel.
        double steepest = 1 + 1.5 * margin / EndingWideStage.BAND;
        for (double tail = -150; tail <= 420; tail += 0.25) {
            int shift = EndingWideStage.shift(margin, px(tail), px(tail + length));
            int drawn = (int) Math.floor(tail) + shift;
            assertTrue(drawn >= previous, "moved back at tail " + tail);
            if (previous != Integer.MIN_VALUE)
                assertTrue(drawn - previous <= Math.ceil(steepest * 0.25) + 1, "jumped at tail " + tail);
            previous = drawn;
        }
    }

    @ParameterizedTest @ValueSource(ints = {16, 40, 104, 240})
    void particlesSpreadEvenlyAcrossThePicture(int margin) {
        assertEquals(0, EndingWideStage.spread(margin, px(160)));
        assertEquals(-margin, EndingWideStage.spread(margin, px(0)));
        assertEquals(margin, EndingWideStage.spread(margin, px(320)));
        assertEquals(-margin, EndingWideStage.spread(margin, px(-48)), "spawned 48 pixels left of the screen");
        assertEquals(margin, EndingWideStage.spread(margin, px(368)), "spawned 48 pixels right of the screen");
        assertEquals(margin / 2, EndingWideStage.spread(margin, px(240)));
        int previous = Integer.MIN_VALUE;
        for (int x = -64; x <= 384; x++) {
            int drawn = x + EndingWideStage.spread(margin, px(x));
            assertTrue(drawn > previous, "particle order kept at " + x);
            previous = drawn;
        }
    }

    @Test void nativeWordsBecomeScreenPositions() {
        assertEquals(px(-64), EndingWideStage.screenFromVdp(0x40 << 16), "$40 is 64 pixels left of the screen");
        assertEquals(px(0), EndingWideStage.screenFromVdp(0x80 << 16));
        assertEquals(px(416), EndingWideStage.screenFromVdp(0x220 << 16));
        assertEquals(px(-63.5), EndingWideStage.screenFromVdp(0x40 << 16 | 0x8000), "the fraction is kept");
        assertEquals(px(-129), EndingWideStage.screenFromVdp(0xFFFF << 16), "a word that wrapped below zero");
        assertEquals(px(192), EndingWideStage.screenFromWorld(0x28B << 16, 0x1CB));
        assertEquals(px(-63.25), EndingWideStage.screenFromWorld(0x18B << 16 | 0xC000, 0x1CB), "the fraction is kept");
    }
}
