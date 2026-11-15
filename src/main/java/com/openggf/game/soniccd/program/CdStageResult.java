package com.openggf.game.soniccd.program;

/**
 * What a stage program leaves for IPX when {@code RunMMD} returns: the shared variables
 * {@code RunLevel} and {@code RunBossLevel} read (IPX.asm:542-629). The stage owners that end a
 * stage (results, time warp, game over) report these; IPX itself never reads stage state.
 *
 * @param lives        {@code lives}; zero means game over
 * @param timeZone     {@code timeZone}; bit 7 set means a time warp was started
 * @param goodFuture   {@code goodFuture} bit 0
 * @param specialStage {@code specialStage}: the stage was left through a special-stage ring
 * @param timeStones   {@code timeStones} after the stage (the special-stage owner updates it)
 * @param time         {@code time} ({@code 00 mm ss ff}, Main CPU Variables.inc:66-69) when the stage returned;
 *                     {@code TimeAttack} copies it to {@code timeAttackTime} (IPX.asm:1236). Only a Time Attack
 *                     run reads it; {@link #withTime} sets it
 */
public record CdStageResult(int lives, int timeZone, boolean goodFuture, boolean specialStage, int timeStones, int time) {
    public static final int TIME_PAST = 0;
    public static final int TIME_PRESENT = 1;
    public static final int TIME_FUTURE = 2;
    /** {@code btst #7,timeZone} (IPX.asm:552). */
    public static final int WARP_FLAG = 0x80;

    public CdStageResult {
        if (lives < 0 || lives > 0xFF) throw new IllegalArgumentException("lives is a byte");
        if (timeZone < 0 || timeZone > 0xFF) throw new IllegalArgumentException("timeZone is a byte");
    }

    public CdStageResult(int lives, int timeZone, boolean goodFuture, boolean specialStage, int timeStones) {
        this(lives, timeZone, goodFuture, specialStage, timeStones, 0);
    }

    /** The same result with the level's {@code time} long, which a Time Attack run records. */
    public CdStageResult withTime(int levelTime) {
        return new CdStageResult(lives, timeZone, goodFuture, specialStage, timeStones, levelTime);
    }

    /** The act was finished normally in the given period. */
    public static CdStageResult cleared(int lives, int timeZone, boolean goodFuture, int timeStones) {
        return new CdStageResult(lives, timeZone, goodFuture, false, timeStones);
    }

    /** A time post was run: the stage left with the warp flag set and the destination period. */
    public static CdStageResult warp(int lives, int destinationTimeZone, boolean goodFuture, int timeStones) {
        return new CdStageResult(lives, destinationTimeZone | WARP_FLAG, goodFuture, false, timeStones);
    }

    public static CdStageResult gameOver(int timeZone, boolean goodFuture, int timeStones) {
        return new CdStageResult(0, timeZone, goodFuture, false, timeStones);
    }

    public boolean timeWarp() { return (timeZone & WARP_FLAG) != 0; }
}
