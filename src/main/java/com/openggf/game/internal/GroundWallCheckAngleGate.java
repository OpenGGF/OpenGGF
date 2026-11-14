package com.openggf.game.internal;

import com.openggf.game.GameModule;

/**
 * Which ground angles skip the player's wall check in front ({@code CalcRoomInFront}). Sonic 3&amp;K
 * skips when {@code angle + $40} is negative unless the angle is an exact cardinal, so a run on
 * a flat ceiling ($80) still checks (sonic3k.asm:22743-22751). A host whose check skips on the
 * sign alone registers {@link #SIGN}: Sonic CD's {@code addi.b #$40,d0; bmi.s} before the probe
 * (scddisasm src/Level/_Objects/Sonic/Main.asm:1179-1182; src/objects/player.asm:1127-1130).
 * Without a registration the shared check keeps the cardinal exception it is certified on.
 */
public enum GroundWallCheckAngleGate {
    /** Skip while {@code angle + $40} is negative, except on an exact cardinal angle. */
    SIGN_EXCEPT_CARDINAL,
    /** Skip while {@code angle + $40} is negative: a ceiling run at $80 never checks. */
    SIGN;

    public static GroundWallCheckAngleGate forHost(GameModule host) {
        GroundWallCheckAngleGate gate = host == null ? null : host.getGameService(GroundWallCheckAngleGate.class);
        return gate == null ? SIGN_EXCEPT_CARDINAL : gate;
    }
}
