package com.openggf.game.rules;

/**
 * How each game's player boundary code treats the edges of the level: the side
 * clamps run by {@code Sonic_LevelBound} / {@code Player_LevelBound}, and the
 * two rows below them — the bottom kill plane and, once the corpse is falling,
 * the row that hands off to the restart countdown.
 *
 * @param rightStrict
 *        S3K's {@code blo} right-edge test with no normal-play {@code +$40}
 *        extension (docs/skdisasm/sonic3k.asm:23183-23186), against S1/S2's
 *        {@code bls} plus the extension.
 * @param usesCentreY
 *        the bottom test compares the ROM {@code y_pos} word, i.e. centre-Y
 *        (docs/s1disasm/_incObj/01 Sonic.asm:1094; docs/s2disasm/s2.asm:36950;
 *        docs/skdisasm/sonic3k.asm:23195).
 * @param lockUsesScreenLockFlag
 *        the right-edge extension is removed by S1's persistent
 *        {@code f_lockscreen} (docs/s1disasm/_incObj/01 Sonic.asm:1071-1073)
 *        rather than S2's boss-alive {@code Current_Boss_ID}
 *        (docs/s2disasm/s2.asm:37247-37250).
 * @param deathFallBottomReferenceIsCameraBottomBoundary
 *        the death-restart row is measured from the camera's bottom boundary —
 *        S1 {@code v_limitbtm2} (docs/s1disasm/_incObj/01 Sonic.asm:2004) and S2
 *        {@code Camera_Max_Y_pos} (docs/s2disasm/s2.asm:38277) — rather than
 *        S3K's {@code Camera_Y_pos} (docs/skdisasm/sonic3k.asm:24541,24581).
 * @param deathFallRestartHandoffCancelsGravity
 *        the crossing frame writes {@code y_vel = -gravity} so the fall that
 *        follows it nets to no movement. S1 alone does this
 *        (docs/s1disasm/_incObj/01 Sonic.asm:2010); S2 and S3K leave the
 *        velocity alone and the corpse keeps falling that frame.
 */
public record PlayerLevelBoundaryRules(
        boolean rightStrict,
        boolean usesCentreY,
        boolean lockUsesScreenLockFlag,
        boolean usesPreEasedMaxXDuringBossLock,
        boolean deathFallBottomReferenceIsCameraBottomBoundary,
        boolean deathFallRestartHandoffCancelsGravity) {
}
