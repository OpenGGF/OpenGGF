package com.openggf.game.soniccd.objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before CdRingSpillGlobals; every case is UNCOMPILED and UNRUN by the external author.
 * UpdateGlobalAnims.RingSpill (Level/Main.asm:536-549) at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff;
 * the three bytes are Main CPU Variables.inc:163-165. Expectations are hand-derived from the source
 * instructions. The fresh-loss series also equals the native L7 observation, which is comparison-only:
 * it is never a runtime input, frame table or fitted constant.
 */
class TestCdRingSpillGlobals {
    @Test
    void freshLossSeriesAddsTheTimerBeforeDecrementingAndRotatesTheAccumulator() {
        // ObjLostRing_Init leaves timer $FF; .ClearFlowers zeroed the accumulator and frame at level init.
        var spill = new CdRingSpillGlobals(0xFF, 0x0000, 0);
        int[][] expected = {
                {0xFE, 0x00FF, 0}, {0xFD, 0x01FD, 0}, {0xFC, 0x02FA, 1}, {0xFB, 0x03F6, 1},
                {0xFA, 0x04F1, 2}, {0xF9, 0x05EB, 2}, {0xF8, 0x06E4, 3}, {0xF7, 0x07DC, 3},
                {0xF6, 0x08D3, 0}};
        for (int[] row : expected) {
            spill = spill.advanceTail();
            assertEquals(new CdRingSpillGlobals(row[0], row[1], row[2]), spill,
                    "timer $" + Integer.toHexString(row[0]));
        }
    }

    @Test
    void zeroTimerReturnsBeforeAnyAccumulatorOrFrameWrite() {
        // TST.B ringLossAnimTimer / BEQ.S .End: the stored frame is not re-derived, even when it is not 0..3.
        for (var idle : new CdRingSpillGlobals[]{new CdRingSpillGlobals(0, 0x1234, 3),
                new CdRingSpillGlobals(0, 0xFFFF, 0xA5), new CdRingSpillGlobals(0, 0, 0)}) {
            assertEquals(idle, idle.advanceTail());
        }
    }

    @Test
    void timerIsZeroExtendedSoNegativeBytesAddUnsignedValues() {
        // MOVEQ #0,d0 / MOVE.B ringLossAnimTimer,d0: $80 adds $0080, never a sign-extended $FF80.
        assertEquals(new CdRingSpillGlobals(0x7F, 0x0080, 0), new CdRingSpillGlobals(0x80, 0x0000, 0).advanceTail());
        assertEquals(new CdRingSpillGlobals(0xFE, 0x01FE, 0), new CdRingSpillGlobals(0xFF, 0x00FF, 2).advanceTail());
        assertEquals(new CdRingSpillGlobals(0x00, 0x0200, 1), new CdRingSpillGlobals(0x01, 0x01FF, 0).advanceTail());
    }

    @Test
    void accumulatorWrapsAsANativeWordAcrossLossesWithinALevel() {
        // ADD.W ringLossAnimAccum,d0 / MOVE.W d0,ringLossAnimAccum: no carry into a wider counter.
        assertEquals(new CdRingSpillGlobals(0xFE, 0x00EF, 0), new CdRingSpillGlobals(0xFF, 0xFFF0, 1).advanceTail());
        assertEquals(new CdRingSpillGlobals(0x00, 0x0000, 0), new CdRingSpillGlobals(0x01, 0xFFFF, 3).advanceTail());
    }

    @ParameterizedTest(name = "accumulator after ADD.W {0} -> ROL.W #7 frame {1}")
    @CsvSource({"0x0000, 0", "0x01FF, 0", "0x0200, 1", "0x03FF, 1", "0x0400, 2", "0x05FF, 2", "0x0600, 3",
            "0x07FF, 3", "0x0800, 0", "0x0E00, 3", "0x0180, 0", "0x8000, 0", "0x8600, 3", "0xC180, 0", "0xFFFF, 3"})
    void frameIsTheTwoBitsRotatedOutOfBitsNineAndTen(String sum, int frame) {
        int total = Integer.decode(sum);
        // Timer 1 adds exactly one, so the stored accumulator is total - 1 as a word. ANDI.W #3 after ROL.W #7.
        var before = new CdRingSpillGlobals(1, (total - 1) & 0xFFFF, 0xEE);
        assertEquals(new CdRingSpillGlobals(0, total, frame), before.advanceTail());
    }

    @Test
    void aFullLossDrainsAfterTwoHundredFiftyFiveTailsHoldsThenCarriesIntoTheNextLoss() {
        var spill = new CdRingSpillGlobals(0xFF, 0x0000, 0);
        for (int i = 0; i < 255; i++) { spill = spill.advanceTail(); }
        assertEquals(new CdRingSpillGlobals(0, 0x7F80, 3), spill, "255+254+...+1 = $7F80, last ROL frame 3");
        assertEquals(spill, spill.advanceTail(), "a drained timer holds");
        // A later loss rewrites only the timer byte (Ring/Main.asm:376); the accumulator carries within the level.
        var second = new CdRingSpillGlobals(0xFF, spill.accumWord(), spill.frameByte()).advanceTail();
        assertEquals(new CdRingSpillGlobals(0xFE, 0x807F, 0), second);
    }

    @Test
    void valuesAreImmutableNativeWidths() {
        var original = new CdRingSpillGlobals(0x10, 0x0100, 2);
        var next = original.advanceTail();
        assertEquals(new CdRingSpillGlobals(0x10, 0x0100, 2), original, "old boundary is unchanged");
        assertEquals(new CdRingSpillGlobals(0x0F, 0x0110, 0), next);
        assertThrows(IllegalArgumentException.class, () -> new CdRingSpillGlobals(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdRingSpillGlobals(0x100, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdRingSpillGlobals(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdRingSpillGlobals(0, 0x10000, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdRingSpillGlobals(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new CdRingSpillGlobals(0, 0, 0x100));
    }
}
