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
        boolean airAbilityAcceptsOverlappingJumpPress) {
}
