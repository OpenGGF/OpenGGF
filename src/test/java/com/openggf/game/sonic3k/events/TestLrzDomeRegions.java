package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.events.LrzDomeRegions.Transition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code sub_56DCA} and {@code word_56F88} (sonic3k.asm:115457-115497, :115645-115649).
 *
 * <p>The three boundaries the ROM's branches actually draw -- {@code $1AFF}/{@code $1B00},
 * {@code $22BF}/{@code $22C0}, {@code $79F}/{@code $7A0} -- plus the two box edges that differ
 * from each other: {@code cmp/bhi} makes X max inclusive and {@code cmp/blo} makes Y max
 * exclusive. Each case names its label.
 */
class TestLrzDomeRegions {

    /** {@code word_56F88} row 0: {@code $1AC0,$1B40,$840,$8C0,$1B00}. */
    @Test
    void regionZeroLocksAtItsThresholdAndReleasesOnePixelBelow() {
        assertEquals(0, LrzDomeRegions.regionIndexAt(0x1B00, 0x860), "inside row 0's box");
        assertEquals(Transition.NONE, LrzDomeRegions.evaluate(0x1AFF, 0x860, false),
                "loc_56E0A: cmp.w (a5),d0 / bhs -- $1AFF is below $1B00, so no lock");
        assertEquals(Transition.LOCK, LrzDomeRegions.evaluate(0x1B00, 0x860, false),
                "loc_56E0A locks at the threshold itself");
        assertEquals(Transition.NONE, LrzDomeRegions.evaluate(0x1B00, 0x860, true),
                "loc_56E16: already locked and still at or above $1B00");
        assertEquals(Transition.RELEASE, LrzDomeRegions.evaluate(0x1AFF, 0x860, true),
                "loc_56E16: blo releases below $1B00");
    }

    /** Row 1: {@code $2240,$2340,$840,$880,$22C0}, the only row that locks on the LOW side. */
    @Test
    void regionOneLocksBelowItsThresholdAndReleasesAtIt() {
        assertEquals(1, LrzDomeRegions.regionIndexAt(0x22C0, 0x850), "inside row 1's box");
        assertEquals(Transition.LOCK, LrzDomeRegions.evaluate(0x22BF, 0x850, false),
                "loc_56E1C: blo locks below $22C0");
        assertEquals(Transition.NONE, LrzDomeRegions.evaluate(0x22C0, 0x850, false),
                "and not at the threshold itself");
        assertEquals(Transition.RELEASE, LrzDomeRegions.evaluate(0x22C0, 0x850, true),
                "loc_56E28: bhs releases at or above $22C0");
        assertEquals(Transition.NONE, LrzDomeRegions.evaluate(0x22BF, 0x850, true),
                "and holds below it");
    }

    /** Row 2: {@code $20C0,$2180,$740,$800,$7A0}, the only row whose threshold is on Y. */
    @Test
    void regionTwoThresholdsOnY() {
        assertEquals(2, LrzDomeRegions.regionIndexAt(0x2100, 0x7A0), "inside row 2's box");
        assertEquals(Transition.NONE, LrzDomeRegions.evaluate(0x2100, 0x79F, false),
                "loc_56E2E: bhs on d1, so $79F does not lock");
        assertEquals(Transition.LOCK, LrzDomeRegions.evaluate(0x2100, 0x7A0, false),
                "and $7A0 does");
        assertEquals(Transition.RELEASE, LrzDomeRegions.evaluate(0x2100, 0x79F, true),
                "loc_56E3A: blo releases above the line again");
    }

    /**
     * {@code loc_56DDC} tests X with {@code bhi} and Y with {@code blo}, so the two maxima are
     * not the same kind of edge. Row 0 has both in reach: X max {@code $1B40}, Y max {@code $8C0}.
     */
    @Test
    void theBoxHasAnInclusiveXMaxAndAnExclusiveYMax() {
        assertTrue(LrzDomeRegions.region(0).containsPlayer(0x1B40, 0x860),
                "cmp.w (a5)+,d0 / bhi: X max is inclusive");
        assertFalse(LrzDomeRegions.region(0).containsPlayer(0x1B41, 0x860), "one past X max is out");
        assertTrue(LrzDomeRegions.region(0).containsPlayer(0x1B00, 0x8BF),
                "one below Y max is in");
        assertFalse(LrzDomeRegions.region(0).containsPlayer(0x1B00, 0x8C0),
                "cmp.w (a5)+,d1 / blo loc_56DFA: Y max is exclusive");
        assertTrue(LrzDomeRegions.region(0).containsPlayer(0x1AC0, 0x840), "both minima are in");
        assertFalse(LrzDomeRegions.region(0).containsPlayer(0x1ABF, 0x840), "one below X min is out");
    }

    /**
     * Outside every box {@code sub_56DCA} returns without touching {@code Events_bg+$00}, so a
     * locked background survives leaving the box and is only released across the threshold.
     */
    @Test
    void outsideEveryBoxNothingHappensEvenWhileLocked() {
        assertEquals(-1, LrzDomeRegions.regionIndexAt(0x0100, 0x0200));
        assertEquals(Transition.NONE, LrzDomeRegions.evaluate(0x0100, 0x0200, false));
        assertEquals(Transition.NONE, LrzDomeRegions.evaluate(0x0100, 0x0200, true),
                "a locked background is not released by walking out of the box");
    }

    /** {@code sub_56DAC} (sonic3k.asm:115442-115452). */
    @Test
    void theLockedBackgroundCopiesAreTheRomArithmetic() {
        assertEquals((0x900 - 0x788 + 0x40) & 0xFFFF,
                LrzDomeRegions.lockedBackgroundY(0x900, 0x40),
                "Camera_Y_pos_copy - $788 + _unkEE9C");
        assertEquals((0x1E00 - 0x1500) & 0xFFFF, LrzDomeRegions.lockedBackgroundX(0x1E00),
                "Camera_X_pos_copy - $1500");
    }
}
