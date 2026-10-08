package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;

/**
 * {@code LRZ1_BackgroundEvent}'s stage machine for the dome regions:
 * {@code LRZ1_BackgroundEvent_Index} (sonic3k.asm:115264-115271) and the two tails
 * {@code sub_56DCA} jumps into, {@code loc_56E40} (:115538-115551) and {@code loc_56E66}
 * (:115552-115567).
 *
 * <p>Three of the four index entries belong here. Stage 0 ({@code loc_56C28}) runs
 * {@code sub_56DCA} and then the ordinary {@code LRZ1_Deform}; stage 4 ({@code loc_56C6E}) runs
 * {@code sub_56DCA} and then {@code sub_56DAC}, the locked dome; stage 8 ({@code loc_56C88}) runs
 * neither and spends its frames in {@code Draw_PlaneVertBottomUpComplex}. Stage {@code $C}
 * ({@code loc_56CAA}) is the seamless act change and is not this class's.
 *
 * <p>Separated from {@link Sonic3kLRZEvents} because everything it touches is the runtime state
 * and Player 1's position: no level, no services, no ROM.
 */
public final class LrzBackgroundStageMachine {

    /** {@code LRZ1_BackgroundEvent_Index} (sonic3k.asm:115264-115271). */
    public static final int BG_STAGE_NORMAL = 0;
    public static final int BG_STAGE_LOCKED = 4;
    public static final int BG_STAGE_REFRESH = 8;
    /** {@code move.w #$F,(Draw_delayed_rowcount).w} at {@code loc_56E66}. */
    public static final int REFRESH_ROWCOUNT = 0x0F;

    private LrzBackgroundStageMachine() {
    }

    /**
     * One background-event dispatch.
     *
     * @param onLock {@code loc_56E40}'s {@code AllocateObject} / {@code Obj_56EA0}
     */
    public static void advance(LrzZoneRuntimeState lrz, int player1X, int player1Y, Runnable onLock) {
        // loc_56C88 does not call sub_56DCA at all: a region boundary crossed during the
        // bottom-up refresh is simply not seen.
        if (lrz.backgroundRoutine() == BG_STAGE_REFRESH) {
            drainDelayedRows(lrz);
            return;
        }
        switch (LrzDomeRegions.evaluate(player1X, player1Y, lrz.domeRegionLocked())) {
            case LOCK -> {
                // loc_56E40: st (Events_bg+$00), AllocateObject -> Obj_56EA0, sub_56DAC,
                // Reset_TileOffsetPositionEff, addq.w #4,(Events_routine_bg) -> stage 4, then
                // straight into loc_56C76 on this same frame.
                lrz.setDomeRegionLocked(true);
                lrz.setDomePlatformPhase(0);
                onLock.run();
                lrz.setBackgroundRoutine(BG_STAGE_LOCKED);
            }
            case RELEASE -> {
                // loc_56E66: clr.w (Events_bg+$00), and the background camera copies are saved
                // BEFORE LRZ1_Deform runs again -- so what is pinned is the locked dome view and
                // the refresh walks the real background up underneath it. loc_56EC2 deletes the
                // platform on its own next dispatch, exactly as the ROM does.
                lrz.setDomeRegionLocked(false);
                lrz.saveBackgroundCamera(lrz.backgroundCameraX(), lrz.backgroundCameraY());
                lrz.setDelayedRowcount(REFRESH_ROWCOUNT);
                lrz.setBackgroundRoutine(BG_STAGE_REFRESH);
                // addq.w #4,sp / jmp loc_56C8C: the first bottom-up pass is on this frame.
                drainDelayedRows(lrz);
            }
            case NONE -> { }
        }
    }

    /**
     * {@code Draw_PlaneVertBottomUpComplex} (sonic3k.asm:103557-103562) calls {@code sub_4F03E}
     * once and then again while the {@code subq.w #1,(Draw_delayed_rowcount)} at :103590 left the
     * counter non-negative: two rows a frame, and the pass ends on the call that takes the counter
     * negative, which is where {@code loc_56C88} clears {@code Events_bg+$02} as a long and the
     * routine word.
     *
     * <p>What the engine models is the pinning, not the row upload: its background plane is
     * rebuilt from the layout, so the eight frames show as the scroll staying on the dome copies
     * rather than as rows arriving one at a time.
     */
    private static void drainDelayedRows(LrzZoneRuntimeState lrz) {
        int count = lrz.delayedRowcount() - 1;
        if (count >= 0) {
            count--;
        }
        lrz.setDelayedRowcount(count);
        if (count < 0) {
            lrz.clearSavedBackgroundCamera();
            lrz.setBackgroundRoutine(BG_STAGE_NORMAL);
        }
    }
}
