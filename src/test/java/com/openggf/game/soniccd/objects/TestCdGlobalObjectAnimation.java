package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.objects.CdGlobalObjectAnimation.Extras;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Shipped UpdateGlobalAnims value witnesses, N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/Main.asm:513-549. Expectations are literal native BYTE/WORD arithmetic,
 * independent of the Ring and Spill helpers used by the composition.
 */
class TestCdGlobalObjectAnimation {
    @Test
    void actualClearValuesSelectTheFirstNativeFrames() {
        var extras = new Extras(0, 0, 0, 0, new CdRingSpillGlobals(0, 0, 0));

        var result = CdGlobalObjectAnimation.step(extras, ring(0, 0));

        assertEquals(new Extras(0x0B, 7, 7, 1, new CdRingSpillGlobals(0, 0, 0)), result.extras());
        assertEquals(ring(7, 1), result.ring());
    }

    @Test
    void timerSignTestsUseTheDecrementedByteAcrossAllThreeGates() {
        // Input timer; expected log timer/frame, Ring timer/frame, unused timer/frame.
        int[][] cases = {
                {0x80, 0x7F, 9, 0x7F, 7, 0x7F, 5},
                {0x81, 0x0B, 0, 7, 0, 7, 0}
        };
        var spill = new CdRingSpillGlobals(0, 0x4567, 0xAB);
        for (int[] c : cases) {
            var result = CdGlobalObjectAnimation.step(new Extras(c[0], 9, c[0], 5, spill), ring(c[0], 7));
            assertEquals(new Extras(c[1], c[2], c[5], c[6], spill), result.extras());
            assertEquals(ring(c[3], c[4]), result.ring());
        }
    }

    @Test
    void logFrameDecrementsBeforeItsMaskAndHeldFramesRemainDirty() {
        // Input timer/frame; expected timer/frame.
        int[][] cases = {
                {0, 0, 0x0B, 7},
                {0, 8, 0x0B, 7},
                {0, 0xFF, 0x0B, 6},
                {0, 0x80, 0x0B, 7},
                {1, 0xFE, 0, 0xFE}
        };
        var spill = new CdRingSpillGlobals(0, 0x1234, 0xFE);
        for (int[] c : cases) {
            var result = CdGlobalObjectAnimation.step(new Extras(c[0], c[1], 1, 0xFF, spill), ring(1, 0xFF));
            assertEquals(new Extras(c[2], c[3], 0, 0xFF, spill), result.extras());
            assertEquals(ring(0, 0xFF), result.ring());
        }
    }

    @Test
    void unusedFrameComparesAfterByteIncrementInsteadOfTakingModuloSix() {
        // Input timer/frame; expected timer/frame.
        int[][] cases = {
                {0, 4, 7, 5},
                {0, 5, 7, 0},
                {0, 0xFE, 7, 0},
                {0, 0xFF, 7, 0},
                {0, 0x80, 7, 0},
                {1, 0xFE, 0, 0xFE}
        };
        var spill = new CdRingSpillGlobals(0, 0x1234, 0xFE);
        for (int[] c : cases) {
            var result = CdGlobalObjectAnimation.step(new Extras(1, 0xFE, c[0], c[1], spill), ring(1, 0xFE));
            assertEquals(new Extras(0, 0xFE, c[2], c[3], spill), result.extras());
            assertEquals(ring(0, 0xFE), result.ring());
        }
    }

    @Test
    void ringProjectionAdvancesOnceAndPreservesEveryUnrelatedNativeField() {
        // Input timer/frame; expected timer/frame.
        int[][] cases = {
                {0, 0xFF, 7, 0},
                {0, 0xFE, 7, 3},
                {1, 0xFF, 0, 0xFF},
                {0xFF, 3, 7, 0}
        };
        var spill = new CdRingSpillGlobals(0, 0x1234, 0xFE);
        for (int[] c : cases) {
            var result = CdGlobalObjectAnimation.step(new Extras(1, 0xFE, 1, 0xFD, spill), ring(c[0], c[1]));
            assertEquals(new Extras(0, 0xFE, 0, 0xFD, spill), result.extras());
            assertEquals(new CdRingGlobals(0x0123, 0x45, 0x67, 0x89, 0xAB, 0xCDEF, c[2], c[3]), result.ring());
        }
    }

    @Test
    void zeroSpillTimerPreservesDirtyAccumulatorAndFrame() {
        var spill = new CdRingSpillGlobals(0, 0xFEDC, 0xA7);

        var result = CdGlobalObjectAnimation.step(new Extras(1, 0xFE, 1, 0xFD, spill), ring(1, 7));

        assertEquals(new Extras(0, 0xFE, 0, 0xFD, new CdRingSpillGlobals(0, 0xFEDC, 0xA7)), result.extras());
        assertEquals(ring(0, 7), result.ring());
    }

    @Test
    void spillUsesOldTimerWordWrapAndRolSevenBeforeTimerDecrement() {
        // Input timer/accumulator/frame; expected timer/accumulator/frame.
        int[][] cases = {
                {0xFF, 0xFFFF, 0xA7, 0xFE, 0x00FE, 0},
                {1, 0x01FF, 0xFF, 0, 0x0200, 1},
                {1, 0x03FF, 0xFF, 0, 0x0400, 2},
                {1, 0x05FF, 0xFF, 0, 0x0600, 3}
        };
        for (int[] c : cases) {
            var result = CdGlobalObjectAnimation.step(
                    new Extras(1, 0xFE, 1, 0xFD, new CdRingSpillGlobals(c[0], c[1], c[2])), ring(1, 7));
            assertEquals(new Extras(0, 0xFE, 0, 0xFD, new CdRingSpillGlobals(c[3], c[4], c[5])), result.extras());
            assertEquals(ring(0, 7), result.ring());
        }
    }

    @Test
    void oneCompletePassCombinesIndependentDirtyFieldsWithoutCrossWrites() {
        var extras = new Extras(0xFF, 0xAA, 2, 0xD9, new CdRingSpillGlobals(3, 0xFFFE, 0x7E));

        var result = CdGlobalObjectAnimation.step(extras, ring(0x82, 0x6F));

        // FF->FE is negative: log resets and AA-1 masks to1. Ring82->81 expires.
        // Unused2->1 holds D9. Spill FFFE+3 wraps0001, whose ROL7 low2 bits are0.
        assertEquals(new Extras(0x0B, 1, 1, 0xD9, new CdRingSpillGlobals(2, 1, 0)), result.extras());
        assertEquals(ring(7, 0), result.ring());
        assertEquals(new Extras(0xFF, 0xAA, 2, 0xD9, new CdRingSpillGlobals(3, 0xFFFE, 0x7E)), extras);
    }

    @Test
    void extrasRejectOutOfWidthFieldsAndAllPublicValueBoundariesRejectNull() {
        var spill = new CdRingSpillGlobals(0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new Extras(-1, 0, 0, 0, spill));
        assertThrows(IllegalArgumentException.class, () -> new Extras(0x100, 0, 0, 0, spill));
        assertThrows(IllegalArgumentException.class, () -> new Extras(0, -1, 0, 0, spill));
        assertThrows(IllegalArgumentException.class, () -> new Extras(0, 0x100, 0, 0, spill));
        assertThrows(IllegalArgumentException.class, () -> new Extras(0, 0, -1, 0, spill));
        assertThrows(IllegalArgumentException.class, () -> new Extras(0, 0, 0x100, 0, spill));
        assertThrows(IllegalArgumentException.class, () -> new Extras(0, 0, 0, -1, spill));
        assertThrows(IllegalArgumentException.class, () -> new Extras(0, 0, 0, 0x100, spill));
        assertThrows(NullPointerException.class, () -> new Extras(0, 0, 0, 0, null));
        var extras = new Extras(0, 0, 0, 0, spill);
        var knownRing = ring(0, 0);
        assertThrows(NullPointerException.class, () -> CdGlobalObjectAnimation.step(null, knownRing));
        assertThrows(NullPointerException.class, () -> CdGlobalObjectAnimation.step(extras, null));
        assertThrows(NullPointerException.class, () -> new CdGlobalObjectAnimation.Result(null, knownRing));
        assertThrows(NullPointerException.class, () -> new CdGlobalObjectAnimation.Result(extras, null));
    }

    private static CdRingGlobals ring(int timer, int frame) {
        return new CdRingGlobals(0x0123, 0x45, 0x67, 0x89, 0xAB, 0xCDEF, timer, frame);
    }
}
