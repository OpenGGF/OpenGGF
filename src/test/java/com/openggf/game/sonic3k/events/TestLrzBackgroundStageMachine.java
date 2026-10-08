package com.openggf.game.sonic3k.events;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LRZ1_BackgroundEvent}'s dome stages against {@code loc_56E40} (sonic3k.asm:115538-115551),
 * {@code loc_56E66} (:115552-115567), {@code loc_56C88} (:115333-115345) and
 * {@code Draw_PlaneVertBottomUpComplex} (:103557-103591).
 *
 * <p>Every expectation here is a ROM immediate or a ROM branch: the {@code addq.w #4} steps, the
 * {@code #$F} seed, the two {@code subq.w #1} decrements a
 * {@code Draw_PlaneVertBottomUpComplex} call makes, and the {@code clr.l}/{@code clr.w} pair the
 * negative result triggers.
 */
class TestLrzBackgroundStageMachine {

    /** Region 0 ({@code word_56F88} row 0): the box, and the {@code $1B00} lock threshold. */
    private static final int INSIDE_X_BELOW_THRESHOLD = 0x1AD0;
    private static final int INSIDE_X_AT_THRESHOLD = 0x1B00;
    private static final int INSIDE_Y = 0x0880;

    private LrzZoneRuntimeState state() {
        return new LrzZoneRuntimeState(Sonic3kZoneIds.ZONE_LRZ, 0, PlayerCharacter.SONIC_AND_TAILS);
    }

    @Test
    void crossingTheThresholdLocksTheBackgroundAndAllocatesTheLavaSurface() {
        LrzZoneRuntimeState lrz = state();
        AtomicInteger allocations = new AtomicInteger();

        LrzBackgroundStageMachine.advance(lrz, INSIDE_X_BELOW_THRESHOLD, INSIDE_Y,
                allocations::incrementAndGet);
        assertEquals(0, allocations.get(), "inside the box but short of $1B00 nothing happens");
        assertEquals(LrzBackgroundStageMachine.BG_STAGE_NORMAL, lrz.backgroundRoutine());

        LrzBackgroundStageMachine.advance(lrz, INSIDE_X_AT_THRESHOLD, INSIDE_Y,
                allocations::incrementAndGet);
        assertEquals(1, allocations.get(), "loc_56E40 allocates Obj_56EA0 once");
        assertTrue(lrz.domeRegionLocked(), "st (Events_bg+$00).w");
        assertEquals(LrzBackgroundStageMachine.BG_STAGE_LOCKED, lrz.backgroundRoutine(),
                "addq.w #4,(Events_routine_bg).w");
    }

    @Test
    void leavingTheRegionPinsTheLockedCopiesAndSeedsTheBottomUpRefresh() {
        LrzZoneRuntimeState lrz = state();
        LrzBackgroundStageMachine.advance(lrz, INSIDE_X_AT_THRESHOLD, INSIDE_Y, () -> { });
        // The locked frame's sub_56DAC copies, as SwScrlLrz publishes them.
        lrz.publishLockedBackgroundCamera(0x0620, 0x00D8);

        LrzBackgroundStageMachine.advance(lrz, INSIDE_X_BELOW_THRESHOLD, INSIDE_Y, () -> { });

        assertFalse(lrz.domeRegionLocked(), "clr.w (Events_bg+$00).w");
        assertEquals(0x0620, lrz.savedBackgroundCameraX(), "Events_bg+$02 = Camera_X_pos_BG_copy");
        assertEquals(0x00D8, lrz.savedBackgroundCameraY(), "Events_bg+$04 = Camera_Y_pos_BG_copy");
        assertTrue(lrz.backgroundCameraPinned(), "tst.l (Events_bg+$02).w is non-zero");
        assertEquals(LrzBackgroundStageMachine.BG_STAGE_REFRESH, lrz.backgroundRoutine());
        // #$F seeded, then this same frame's first Draw_PlaneVertBottomUpComplex takes two rows.
        assertEquals(0x0D, lrz.delayedRowcount(), "the release frame already drains two rows");
    }

    @Test
    void theRefreshRunsForEightFramesAndThenClearsBothWords() {
        LrzZoneRuntimeState lrz = state();
        LrzBackgroundStageMachine.advance(lrz, INSIDE_X_AT_THRESHOLD, INSIDE_Y, () -> { });
        lrz.publishLockedBackgroundCamera(0x0620, 0x00D8);
        LrzBackgroundStageMachine.advance(lrz, INSIDE_X_BELOW_THRESHOLD, INSIDE_Y, () -> { });

        // $F drained two a frame ends on the eighth call: the release frame plus seven more.
        for (int frame = 1; frame <= 6; frame++) {
            LrzBackgroundStageMachine.advance(lrz, INSIDE_X_BELOW_THRESHOLD, INSIDE_Y, () -> { });
            assertEquals(LrzBackgroundStageMachine.BG_STAGE_REFRESH, lrz.backgroundRoutine(),
                    "still refreshing after frame " + frame);
            assertTrue(lrz.backgroundCameraPinned(), "still pinned after frame " + frame);
            assertEquals(0x0D - 2 * frame, lrz.delayedRowcount(), "rowcount after frame " + frame);
        }

        LrzBackgroundStageMachine.advance(lrz, INSIDE_X_BELOW_THRESHOLD, INSIDE_Y, () -> { });
        assertEquals(-1, lrz.delayedRowcount(), "the eighth call takes the counter negative");
        assertEquals(LrzBackgroundStageMachine.BG_STAGE_NORMAL, lrz.backgroundRoutine(),
                "clr.w (Events_routine_bg).w");
        assertFalse(lrz.backgroundCameraPinned(), "clr.l (Events_bg+$02).w");
    }

    /**
     * {@code loc_56C88} never calls {@code sub_56DCA}, so a region re-entered during the refresh
     * does not lock again -- and the allocation callback must not fire.
     */
    @Test
    void theRefreshIgnoresRegionTransitions() {
        LrzZoneRuntimeState lrz = state();
        LrzBackgroundStageMachine.advance(lrz, INSIDE_X_AT_THRESHOLD, INSIDE_Y, () -> { });
        lrz.publishLockedBackgroundCamera(0x0620, 0x00D8);
        LrzBackgroundStageMachine.advance(lrz, INSIDE_X_BELOW_THRESHOLD, INSIDE_Y, () -> { });

        AtomicInteger allocations = new AtomicInteger();
        LrzBackgroundStageMachine.advance(lrz, INSIDE_X_AT_THRESHOLD, INSIDE_Y,
                allocations::incrementAndGet);
        assertEquals(0, allocations.get(), "no Obj_56EA0 while the plane is being redrawn");
        assertFalse(lrz.domeRegionLocked(), "Events_bg+$00 stays clear");
        assertEquals(LrzBackgroundStageMachine.BG_STAGE_REFRESH, lrz.backgroundRoutine());
    }

    /**
     * {@code sub_56DCA} returns without touching {@code Events_bg+$00} outside every box
     * (loc_56DEE's {@code dbf} falls through to {@code rts}), so a locked background survives
     * walking out of the region and is only released across the region's own threshold.
     */
    @Test
    void walkingOutOfTheBoxDoesNotReleaseALockedBackground() {
        LrzZoneRuntimeState lrz = state();
        LrzBackgroundStageMachine.advance(lrz, INSIDE_X_AT_THRESHOLD, INSIDE_Y, () -> { });

        LrzBackgroundStageMachine.advance(lrz, 0x0400, 0x0400, () -> { });

        assertTrue(lrz.domeRegionLocked(), "outside every box Events_bg+$00 is untouched");
        assertEquals(LrzBackgroundStageMachine.BG_STAGE_LOCKED, lrz.backgroundRoutine());
    }
}
