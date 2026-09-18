package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;

/**
 * {@code LRZ2_BackgroundEvent}'s stage machine: {@code LRZ2_BackgroundEvent_Index}
 * (sonic3k.asm:115682-115690) and its three entries, {@code loc_5700C} (:115692-115710),
 * {@code loc_57040} (:115712-115722) and {@code loc_57058} (:115724-115738).
 *
 * <p>Stage 0 is reached only by the seamless act change. {@code loc_56CAA} ends with
 * {@code clr.w (Events_routine_bg)} (:115374) and the act word is already {@code $901}, so the
 * next background dispatch is this index's first entry; a direct {@code $901} load never sees it,
 * because {@code LRZ2_BackgroundInit} writes {@code #8} into the routine word (:115661) after its
 * own {@code Refresh_PlaneFull}.
 *
 * <p>What stage 0 does is arm a plane-B refill and start it on the same frame:
 * {@code Reset_TileOffsetPositionEff}, {@code Draw_delayed_position} =
 * {@code (Camera_Y_pos_BG_copy + $E0) & Camera_Y_pos_mask}, {@code Draw_delayed_rowcount} =
 * {@code $F}, {@code addq.w #4} to stage 4, then {@code bra.s loc_57044} straight into the first
 * {@code Draw_PlaneVertBottomUp}. Stage 4 keeps calling it until it reports the counter negative
 * and then steps to stage 8, the steady state.
 *
 * <p><b>What the engine models, and what it does not.</b> The engine's background plane is rebuilt
 * from the act's own layout rather than uploaded row by row, so the observable half of stage 0 is
 * the {@code Reset_TileOffsetPositionEff} that makes it rebuild; the eight frames of row uploads
 * have no engine counterpart and are modelled as the counter alone. {@link LrzBackgroundStageMachine}
 * makes the same split for act 1's dome refresh and says so for the same reason.
 * {@code Draw_delayed_position} is deliberately not modelled: it selects which plane rows the ROM
 * writes next, and an engine that rebuilds the whole plane has nothing to select.
 *
 * <p>Two pieces of stage 0 are <b>not</b> here and are recorded as owed: {@code loc_5711E}, the
 * act-2 Death Egg background sprite the stage allocates into {@code Events_bg+$06}, and the
 * {@code sub_57082} call, whose background camera copies {@code SwScrlLrz} already publishes from
 * the same arithmetic.
 */
public final class Lrz2BackgroundStageMachine {

    /** {@code LRZ2_BackgroundEvent_Index}'s first entry, {@code loc_5700C}. */
    public static final int BG_STAGE_ARM_REFRESH = 0;
    /** Its second, {@code loc_57040}: the bottom-up refill. */
    public static final int BG_STAGE_REFRESH = 4;
    /** Its third, {@code loc_57058}: the steady state a direct {@code $901} load starts in. */
    public static final int BG_STAGE_STEADY = 8;
    /** {@code move.w #$F,(Draw_delayed_rowcount).w} at {@code loc_5701E} (:115707). */
    public static final int REFRESH_ROWCOUNT = 0x0F;

    private Lrz2BackgroundStageMachine() {
    }

    /**
     * One act-2 background-event dispatch.
     *
     * @param onResetTileOffsets {@code loc_5701E}'s {@code jsr (Reset_TileOffsetPositionEff)}
     */
    public static void advance(LrzZoneRuntimeState lrz, Runnable onResetTileOffsets) {
        switch (lrz.backgroundRoutine()) {
            case BG_STAGE_ARM_REFRESH -> {
                onResetTileOffsets.run();
                lrz.setDelayedRowcount(REFRESH_ROWCOUNT);
                lrz.setBackgroundRoutine(BG_STAGE_REFRESH);
                // bra.s loc_57044: the first pass is on this frame, not the next.
                drainBottomUpRows(lrz);
            }
            case BG_STAGE_REFRESH -> drainBottomUpRows(lrz);
            default -> {
                // loc_57058 has no addq and no rowcount: Draw_TileRow, ApplyDeformation and
                // ShakeScreen_Setup, all of which the engine's scroll pass already owns.
            }
        }
    }

    /**
     * {@code Draw_PlaneVertBottomUp} (sonic3k.asm:103429-103434) calls
     * {@code Draw_PlaneVertSingleBottomUp} once and then, while that call's
     * {@code subq.w #1,(Draw_delayed_rowcount)} (:103456) left the counter non-negative, a second
     * time. {@code loc_57044}'s {@code bpl} keeps stage 4; the call that takes the counter
     * negative runs {@code addq.w #4,(Events_routine_bg)} (:115718) and parks on stage 8.
     */
    private static void drainBottomUpRows(LrzZoneRuntimeState lrz) {
        int count = lrz.delayedRowcount() - 1;
        if (count >= 0) {
            count--;
        }
        lrz.setDelayedRowcount(count);
        if (count < 0) {
            lrz.setBackgroundRoutine(BG_STAGE_STEADY);
        }
    }
}
