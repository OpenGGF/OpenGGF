package com.openggf.tests.trace.runs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transition-row V-blank invariant, asserted without a ROM, a level or a
 * trace fixture.
 *
 * <p>{@code VintRet: addq.l #1,(Vint_runcount).w}
 * (docs/s2disasm/s2.asm:507-508) sits after the {@code jsr Vint_SwitchTbl}
 * dispatch at :504, so every V-int the ROM takes advances the object-visible
 * clock exactly once, whichever routine the dispatch selected. A transition is
 * made of such frames — {@code Vint_Fade} during {@code Pal_FadeToBlack}
 * (:3370-3380), {@code Vint_TitleCard}, {@code Vint_Lag} — so a run of N
 * transition rows advances the clock by exactly N.
 *
 * <p>The engine plays some of those rows with the level body suppressed, which
 * leaves the clock still. {@link AbstractRunChainTest#suppressedRowOwesVint} is
 * the rule that restores the ROM's count, and these are its assertions. They
 * are deliberately fixture-free: the property is arithmetic about one counter,
 * so nothing about it should depend on a recording.
 */
class TestRunChainSuppressedRowVint {

    @Test
    @DisplayName("A row the engine left the clock still on still owes its VintRet tick")
    void stalledRowOwesTheTick() {
        assertTrue(AbstractRunChainTest.suppressedRowOwesVint(true, 100, 100));
    }

    @Test
    @DisplayName("A row whose own body already ticked owes nothing, so the rule cannot double-count")
    void alreadyTickedRowOwesNothing() {
        assertFalse(AbstractRunChainTest.suppressedRowOwesVint(true, 100, 101));
    }

    @Test
    @DisplayName("A row that advanced the clock more than once is left alone")
    void multiplyAdvancedRowOwesNothing() {
        assertFalse(AbstractRunChainTest.suppressedRowOwesVint(true, 100, 103));
    }

    @Test
    @DisplayName("A row on which the level load replaced the object manager owes nothing")
    void reseededRowOwesNothing() {
        assertFalse(AbstractRunChainTest.suppressedRowOwesVint(false, 100, 100));
        assertFalse(AbstractRunChainTest.suppressedRowOwesVint(false, 100, 0));
    }

    /**
     * The property the rule exists for: a gap of N rows advances the clock by
     * exactly N, no matter which of those rows the engine's own body happened
     * to tick on. The mix below is the shape measured at the S2
     * {@code seg4_ehz1 -> seg5_ehz2} boundary — a run of suppressed fade rows
     * inside a majority of self-ticking rows.
     */
    @Test
    @DisplayName("Every transition row reaches the tick: N rows advance the clock by N")
    void everyTransitionRowReachesTheTick() {
        boolean[] engineTickedTheRowItself = new boolean[171];
        for (int row = 0; row < engineTickedTheRowItself.length; row++) {
            // Rows 0..21 are Pal_FadeToBlack's 22 WaitForVint passes, which the
            // engine spends with the level body suppressed; row 170 is the
            // gap's last row. Every other row's body ticks for itself.
            engineTickedTheRowItself[row] =
                    row >= 22 && row < engineTickedTheRowItself.length - 1;
        }

        int counter = 0x2000;
        for (boolean bodyTicked : engineTickedTheRowItself) {
            int before = counter;
            if (bodyTicked) {
                counter++;
            }
            if (AbstractRunChainTest.suppressedRowOwesVint(true, before, counter)) {
                counter++;
            }
            assertEquals(before + 1, counter,
                    "every transition row must end one V-blank ahead of where it started");
        }
        assertEquals(0x2000 + engineTickedTheRowItself.length, counter,
                "a gap of N rows must advance the object-visible clock by exactly N");
    }
}
