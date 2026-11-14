package com.openggf.game.soniccd.zones.ppz;

import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.player.CdPlayerStageTables;
import com.openggf.game.soniccd.player.CdSpecialChunks;
import com.openggf.game.soniccd.player.CdZonePlayerProfile;

/**
 * Palmtree Panic's part of the player routine (scddisasm src/Level/_Objects/Sonic/
 * Main.asm). Act 1 Present alone has the 3D ramps: {@code ObjSonic_Handle3DRamp} and
 * {@code ObjSonic_MoveAir} run their ramp code only for {@code timeZone == 1} and
 * {@code zoneAct == 0} (Main.asm:783-786, 1566-1569). Every act has the special chunks
 * with the special roll tunnel's Y test (Main.asm:2522-2532). {@code ObjSonic_SpecialCol}
 * is a bare {@code rts} ({@code Level/Palmtree Panic/Player Special Collision.asm:7-8}).
 */
final class PpzPlayerProfile implements CdZonePlayerProfile {
    /** {@code cmpi.w #$6C8 / #$840,oX(a0)}: the first ramp's launch window (Main.asm:1571-1575). */
    private static final int AIR_LOCK_FROM_X = 0x6C8;
    private static final int AIR_LOCK_TO_X = 0x840;
    /** {@code cmpi.w #$1B00,oX(a0)}: the ramp at the start of the level (Main.asm:241-242). */
    private static final int RAMP_FOCUS_RESET_BEFORE_X = 0x1B00;
    /** {@code cmpi.w #$90,d0} on {@code oY & $FF} (Main.asm:2527-2530). */
    private static final int SPECIAL_ROLL_MIN_Y = 0x90;

    /** {@code cmpi.w #$900,oX(a0)}: the first 3D ramp section (ObjSonic_TimeWarp, Main.asm:580-581). */
    private static final int TIME_WARP_FROM_X = 0x900;

    private final boolean rampStage;
    private final boolean act1;
    private final int[] rampChunks;
    private final CdSpecialChunks specialChunks;

    PpzPlayerProfile(CdStagePayload payload, CdStageImage image) {
        act1 = payload.stage().actNumber() == 1;
        rampStage = act1 && payload.stage().period() == CdPeriod.PRESENT;
        rampChunks = rampStage ? CdPlayerStageTables.ramp3dChunks(payload, image) : new int[0];
        int[] ids = CdPlayerStageTables.specialChunks(payload, image);
        specialChunks = ids == null ? null
                : CdSpecialChunks.of(ids, SPECIAL_ROLL_MIN_Y, CdSpecialChunks.SpecialLoopRule.AIRBORNE_LOWER_PATH);
    }

    @Override
    public boolean isPseudo3dRampChunk(int rawChunk) {
        for (int id : rampChunks) {
            if (id == rawChunk) return true;                 // .CheckChunk: cmp.b d0,d1
        }
        return false;
    }

    @Override
    public boolean locksAirMovementAt(int centreX) {
        return rampStage && centreX >= AIR_LOCK_FROM_X && centreX < AIR_LOCK_TO_X;
    }

    @Override
    public boolean rampCentresFocusAt(int centreX) {
        return centreX < RAMP_FOCUS_RESET_BEFORE_X;
    }

    /**
     * {@code ObjSonic_TimeWarp}: in act 1 ({@code zoneAct == 0}) the past and the future stop
     * the warp before x $900 (Main.asm:574-583); the test reads the live {@code timeZone}.
     */
    @Override
    public boolean stopsTimeWarpAt(com.openggf.game.soniccd.timetravel.CdTimeZone timeZone, int centreX) {
        return act1 && timeZone != com.openggf.game.soniccd.timetravel.CdTimeZone.PRESENT
                && centreX < TIME_WARP_FROM_X;
    }

    @Override
    public CdSpecialChunks specialChunks() {
        return specialChunks;
    }
}
