package com.openggf.game.rules;

/** Air steering and the glide/climb ability's input and terrain-collision policy. */
@com.openggf.game.ModApi
public record PlayerAirMovementRules(
        boolean airSuperspeedPreserved,
        /** Use standing touch radii outside the glide/slide/climb terrain probes. */
        boolean glideRestoresStandingRadiiAfterCollision,
        /** Probe below an idle wall climber, including the shipped animation-delta clobber. */
        boolean idleWallClimbChecksFloor,
        /** Air abilities consume a fresh A/B/C edge without requiring all buttons released. */
        boolean airAbilityAcceptsOverlappingJumpPress,
        /** Resolve a simultaneous glide floor contact before the wall-grab response (S3K). */
        boolean glideFloorContactPrecedesWallGrab,
        /**
         * A jump begun from a roll keeps left/right air control. Sonic CD's
         * {@code ObjSonic_MoveAir} has no roll-jump test (scddisasm
         * src/Level/_Objects/Sonic/Main.asm:1559-1603); Sonic 1/2/3&amp;K skip steering
         * while {@code Status_RollJump} is set. Only Sonic CD sets it.
         */
        boolean rollJumpKeepsAirControl,
        /**
         * Terminal fall speed written back after the gravity step, or 0 for none. Sonic
         * CD's {@code ObjMoveGrv} caps a non-negative {@code oYVel} at {@code $1000}
         * (scddisasm src/Level/Object Functions.asm:68-73), still moving by the
         * pre-gravity speed. Sonic 1's {@code ObjectFall}, Sonic 2's
         * {@code ObjectMoveAndFall} and Sonic 3&amp;K's {@code MoveSprite_TestGravity}
         * have no cap. Only Sonic CD sets it.
         */
        int fallSpeedCap,
        /**
         * A jump begun from a roll keeps the rolling radii. Sonic CD's
         * {@code ObjSonic_CheckJump} branches to {@code .RollJump} before the radius writes
         * (scddisasm src/Level/_Objects/Sonic/Main.asm:1837-1838, 1847-1849, 1858-1860);
         * Sonic 1's {@code Sonic_Jump}, Sonic 2 and Sonic 3&amp;K write the standing radii
         * first. Only Sonic CD sets it.
         */
        boolean rollJumpKeepsRollRadii) {

    /** Keeps the standing-radius roll jump for callers of the seven-argument API. */
    public PlayerAirMovementRules(boolean airSuperspeedPreserved,
                                  boolean glideRestoresStandingRadiiAfterCollision,
                                  boolean idleWallClimbChecksFloor,
                                  boolean airAbilityAcceptsOverlappingJumpPress,
                                  boolean glideFloorContactPrecedesWallGrab,
                                  boolean rollJumpKeepsAirControl,
                                  int fallSpeedCap) {
        this(airSuperspeedPreserved, glideRestoresStandingRadiiAfterCollision, idleWallClimbChecksFloor,
                airAbilityAcceptsOverlappingJumpPress, glideFloorContactPrecedesWallGrab, rollJumpKeepsAirControl,
                fallSpeedCap, false);
    }

    public PlayerAirMovementRules(boolean airSuperspeedPreserved,
                                  boolean glideRestoresStandingRadiiAfterCollision,
                                  boolean idleWallClimbChecksFloor,
                                  boolean airAbilityAcceptsOverlappingJumpPress,
                                  boolean glideFloorContactPrecedesWallGrab,
                                  boolean rollJumpKeepsAirControl) {
        this(airSuperspeedPreserved, glideRestoresStandingRadiiAfterCollision, idleWallClimbChecksFloor,
                airAbilityAcceptsOverlappingJumpPress, glideFloorContactPrecedesWallGrab, rollJumpKeepsAirControl, 0);
    }

    /** Keeps the roll-jump steering lock for callers of the five-argument API. */
    public PlayerAirMovementRules(boolean airSuperspeedPreserved,
                                  boolean glideRestoresStandingRadiiAfterCollision,
                                  boolean idleWallClimbChecksFloor,
                                  boolean airAbilityAcceptsOverlappingJumpPress,
                                  boolean glideFloorContactPrecedesWallGrab) {
        this(airSuperspeedPreserved, glideRestoresStandingRadiiAfterCollision, idleWallClimbChecksFloor,
                airAbilityAcceptsOverlappingJumpPress, glideFloorContactPrecedesWallGrab, false, 0);
    }
    /** Retains wall-first behavior for callers of the original candidate constructor. */
    public PlayerAirMovementRules(boolean airSuperspeedPreserved,
                                  boolean glideRestoresStandingRadiiAfterCollision,
                                  boolean idleWallClimbChecksFloor,
                                  boolean airAbilityAcceptsOverlappingJumpPress) {
        this(airSuperspeedPreserved, glideRestoresStandingRadiiAfterCollision,
                idleWallClimbChecksFloor, airAbilityAcceptsOverlappingJumpPress, false);
    }
}
