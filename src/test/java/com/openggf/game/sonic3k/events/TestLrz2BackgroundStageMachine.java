package com.openggf.game.sonic3k.events;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code LRZ2_BackgroundEvent}'s three stages: {@code loc_5700C} (sonic3k.asm:115692-115710),
 * {@code loc_57040} (:115712-115722) and {@code loc_57058} (:115724-115738), dispatched through
 * {@code LRZ2_BackgroundEvent_Index} (:115682-115690).
 *
 * <p>Only the seamless act change enters at stage 0: {@code loc_56CAA} ends with
 * {@code clr.w (Events_routine_bg)} (:115374) while {@code LRZ2_BackgroundInit} starts a direct
 * {@code $901} load at stage 8 (`move.w #8,(Events_routine_bg)`, :115661).
 *
 * <p>The drain arithmetic is the ROM's own. {@code Draw_PlaneVertBottomUp} (:103429-103434) calls
 * {@code Draw_PlaneVertSingleBottomUp} once and, while the {@code subq.w #1,(Draw_delayed_rowcount)}
 * at :103456 left the counter non-negative, a second time: two rows a call. Seeded with {@code $F}
 * the counter runs 15, 13, 11, 9, 7, 5, 3, 1, -1, so the pass ends on the eighth call -- and the
 * first of those eight is stage 0's own, because {@code loc_5700C} leaves through
 * {@code bra.s loc_57044} rather than waiting for the next frame.
 */
class TestLrz2BackgroundStageMachine {

    private LrzZoneRuntimeState actTwoState() {
        return new LrzZoneRuntimeState(Sonic3kZoneIds.ZONE_LRZ, 1, PlayerCharacter.SONIC_AND_TAILS);
    }

    @Test
    void stageZeroArmsTheRefreshAndDrainsItsOwnFirstRows() {
        LrzZoneRuntimeState lrz = actTwoState();
        AtomicInteger refreshes = new AtomicInteger();

        Lrz2BackgroundStageMachine.advance(lrz, refreshes::incrementAndGet);

        assertEquals(1, refreshes.get(),
                "loc_5701E calls Reset_TileOffsetPositionEff once as it arms the pass");
        assertEquals(Lrz2BackgroundStageMachine.BG_STAGE_REFRESH, lrz.backgroundRoutine(),
                "addq.w #4,(Events_routine_bg) takes stage 0 to stage 4 (sonic3k.asm:115709)");
        assertEquals(13, lrz.delayedRowcount(),
                "the #$F seed minus loc_57044's own two rows on this same frame");
    }

    @Test
    void theBottomUpPassEndsOnItsEighthCallAndParksOnStageEight() {
        LrzZoneRuntimeState lrz = actTwoState();
        AtomicInteger refreshes = new AtomicInteger();

        Lrz2BackgroundStageMachine.advance(lrz, refreshes::incrementAndGet);
        for (int call = 2; call <= 7; call++) {
            Lrz2BackgroundStageMachine.advance(lrz, refreshes::incrementAndGet);
            assertEquals(Lrz2BackgroundStageMachine.BG_STAGE_REFRESH, lrz.backgroundRoutine(),
                    "call " + call + " of eight is still inside the pass");
        }
        assertEquals(1, lrz.delayedRowcount(), "seven calls of two rows from #$F");

        Lrz2BackgroundStageMachine.advance(lrz, refreshes::incrementAndGet);
        assertEquals(Lrz2BackgroundStageMachine.BG_STAGE_STEADY, lrz.backgroundRoutine(),
                "the call that takes the counter negative runs the second addq.w #4 (:115718)");
        assertEquals(1, refreshes.get(),
                "only stage 0 resets the tile offsets; loc_57040 and loc_57058 never do");
    }

    @Test
    void stageEightIsWhereADirectLoadLivesAndItNeverRearms() {
        LrzZoneRuntimeState lrz = actTwoState();
        // LRZ2_BackgroundInit's move.w #8,(Events_routine_bg).w (sonic3k.asm:115661).
        lrz.setBackgroundRoutine(Lrz2BackgroundStageMachine.BG_STAGE_STEADY);
        AtomicInteger refreshes = new AtomicInteger();

        for (int frame = 0; frame < 30; frame++) {
            Lrz2BackgroundStageMachine.advance(lrz, refreshes::incrementAndGet);
        }

        assertEquals(Lrz2BackgroundStageMachine.BG_STAGE_STEADY, lrz.backgroundRoutine(),
                "loc_57058 has no addq: it is the act's steady state");
        assertEquals(0, refreshes.get(),
                "a direct $901 load already had LRZ2_BackgroundInit's Refresh_PlaneFull");
    }
}
