package com.openggf.game.soniccd.timetravel;

import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.CdStagePayload;

import java.util.Objects;

/**
 * A time warp: leave stage {@code from} (running in {@code fromZone}) for {@code toZone},
 * carrying {@code carry}. The destination stage file follows IPX's {@code RunLevel} loop
 * ({@code CD Initial Program/IPX.asm:551-580}): past file, present file, or the good or
 * bad future file by {@code goodFuture} at the time the destination is chosen.
 *
 * <p>Between leaving and arriving the native game runs the warp cutscene program
 * ({@code SCMD_WARP}, IPX.asm:560-561). That program belongs to the program-flow lane;
 * {@link #cutscene()} names it as a step that is not hosted here.
 */
public record CdWarpRequest(CdStagePayload from, CdTimeZone fromZone, CdTimeZone toZone, CdWarpCarry carry) {
    /** The program run between the two stages. */
    public enum Step { WARP_CUTSCENE_NOT_HOSTED }

    public CdWarpRequest {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(fromZone, "fromZone");
        Objects.requireNonNull(toZone, "toZone");
        Objects.requireNonNull(carry, "carry");
        if (!(from.stage() instanceof CdStageKey.Traversal)) {
            throw new IllegalArgumentException("Only act 1 and 2 stages have time posts: " + from);
        }
    }

    public Step cutscene() {
        return Step.WARP_CUTSCENE_NOT_HOSTED;
    }

    /**
     * {@code RunLevel .LevelLoop}: {@code andi.b #$7F,d1; beq} past, {@code subq.b #1; beq}
     * present, else {@code tst.b goodFuture} picks good or bad (IPX.asm:563-578).
     */
    public CdStagePayload destination(boolean goodFuture) {
        CdPeriod period = switch (toZone) {
            case PAST -> CdPeriod.PAST;
            case PRESENT -> CdPeriod.PRESENT;
            case FUTURE -> goodFuture ? CdPeriod.GOOD_FUTURE : CdPeriod.BAD_FUTURE;
        };
        CdStageKey key = from.stage();
        return CdStagePayload.valueOf("R" + key.zone().nativeRound() + key.actNumber() + period.payloadSuffix());
    }
}
