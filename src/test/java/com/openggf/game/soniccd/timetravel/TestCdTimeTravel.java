package com.openggf.game.soniccd.timetravel;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.CdZone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The campaign owner's writers and lifetimes as the disassembly gives them. These are
 * focused checks of the owner's bookkeeping; the native checks are in
 * {@code TestSonicCdTimeTravel}.
 */
class TestCdTimeTravel {
    private static final CdWarpCarry CARRY = new CdWarpCarry(0x6EE, 0x262, 0x4E, (short) 0x624, (short) 0x52D,
            (short) -0x1B3, 6, 0, 99_999, 0, 0x310, 0x68E, 0x208, 0, false, false);

    @Test
    void stepClampsToPastAndFuture() {
        // .GetNewTime (Sonic/Main.asm:625-636).
        assertEquals(CdTimeZone.PAST, CdTimeZone.PRESENT.step(-1));
        assertEquals(CdTimeZone.FUTURE, CdTimeZone.PRESENT.step(1));
        assertEquals(CdTimeZone.PAST, CdTimeZone.PAST.step(-1));
        assertEquals(CdTimeZone.FUTURE, CdTimeZone.FUTURE.step(1));
    }

    @Test
    void carryClearsOnObjectAndUnderwaterAndCapsTheTime() {
        // bclr #3 / #6 of warpPlayerFlags; cmpi.l #(5<<16) (Sonic/Main.asm:539-561).
        assertEquals(0x06, CARRY.playerFlags());
        assertEquals(CdWarpCarry.TIME_CAP_FRAMES, CARRY.timeFrames());
    }

    @Test
    void destinationFollowsRunLevelAndGoodFuture() {
        var future = new CdWarpRequest(CdStagePayload.R11A, CdTimeZone.PRESENT, CdTimeZone.FUTURE, CARRY);
        assertEquals(CdStagePayload.R11D, future.destination(false));
        assertEquals(CdStagePayload.R11C, future.destination(true));
        var past = new CdWarpRequest(CdStagePayload.R52A, CdTimeZone.PRESENT, CdTimeZone.PAST, CARRY);
        assertEquals(CdStagePayload.R52B, past.destination(false));
        assertEquals(CdWarpRequest.Step.WARP_CUTSCENE_NOT_HOSTED, past.cutscene());
    }

    @Test
    void levelStartClearsLevelValuesButKeepsGoodFutureAcrossARestart() {
        CdTimeTravel tt = new CdTimeTravel();
        tt.levelStart(CdStagePayload.R11B);
        assertEquals(CdTimeZone.PAST, tt.timeZone());
        tt.touchTimePost(false);
        tt.startTimer();
        tt.vIntTick(false);
        tt.destroyGenerator();
        tt.destroyProjector();
        assertEquals(1, tt.warpDirection());
        assertEquals(2, tt.warpTimer());
        tt.levelStart(CdStagePayload.R11B);                            // death: same stage again
        assertEquals(0, tt.warpDirection());
        assertEquals(0, tt.warpTimer());
        assertTrue(tt.goodFuture(), "goodFuture survives a restart");
        assertTrue(tt.projectorDestroyed(), "projDestroyed survives a restart");
    }

    @Test
    void chargingHoldsTheTimer() {
        CdTimeTravel tt = new CdTimeTravel();
        tt.startTimer();
        tt.vIntTick(true);
        assertEquals(1, tt.warpTimer());
        tt.vIntTick(false);
        assertEquals(2, tt.warpTimer());
    }

    @Test
    void warpArrivalReturnsTheCarryAndClearsTheMarker() {
        CdTimeTravel tt = new CdTimeTravel();
        tt.levelStart(CdStagePayload.R11A);
        tt.trigger(new CdWarpRequest(CdStagePayload.R11A, CdTimeZone.PRESENT, CdTimeZone.FUTURE, CARRY));
        assertTrue(tt.warping());
        assertEquals(CdTimeZone.FUTURE, tt.timeZone());
        assertNull(tt.readyWarp());
        tt.requestRestart();
        assertSame(CARRY, tt.levelStart(CdStagePayload.R11D));
        assertFalse(tt.warping());
        assertNull(tt.pendingWarp());
        assertEquals(CdTimeZone.FUTURE, tt.timeZone());
        assertEquals(java.util.List.of(CdTimeTravel.Sound.FADE_OUT_MUSIC), tt.drainSounds());
    }

    @Test
    void resultsCreditGoodFuturesForAct3() {
        // ObjResults_NextLevel .CheckGoodFuture (Results/Main.asm:316-341).
        CdTimeTravel tt = new CdTimeTravel();
        tt.levelStart(CdStagePayload.R11A);
        tt.destroyGenerator();
        tt.actCompleted(CdZone.values()[0], 1);
        assertFalse(tt.goodFuture());
        assertEquals(0b01, tt.goodFutureFlags());
        tt.levelStart(CdStagePayload.R12A);
        tt.destroyGenerator();
        tt.actCompleted(CdZone.values()[0], 2);
        assertTrue(tt.goodFuture(), "both acts: good future for act 3");
        assertEquals(CdTimeZone.FUTURE, tt.timeZone());
        assertEquals(CdStagePayload.R13C, tt.act3Payload(CdZone.values()[0]));
        tt.actCompleted(CdZone.values()[0], 3);
        assertFalse(tt.goodFuture());
        assertEquals(1, tt.goodFutures());
    }

    @Test
    void allTimeStonesForceTheGoodFuture() {
        // cmpi.b #$7F,timeStones (Level/Main.asm:22-26).
        CdTimeTravel tt = new CdTimeTravel();
        tt.setTimeStones(CdTimeTravel.ALL_TIME_STONES);
        tt.levelStart(CdStagePayload.R31A);
        assertTrue(tt.goodFuture());
    }

    @Test
    void rewindRestoresEveryValue() {
        CdTimeTravel tt = new CdTimeTravel();
        tt.levelStart(CdStagePayload.R11A);
        tt.touchTimePost(true);
        tt.startTimer();
        var snapshot = tt.capture();
        tt.stopTimeWarp();
        tt.destroyGenerator();
        tt.restore(snapshot);
        assertEquals(-1, tt.warpDirection());
        assertEquals(1, tt.warpTimer());
        assertFalse(tt.goodFuture());
    }
}
