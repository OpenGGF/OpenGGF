package com.openggf.game.rules;

public record PlayerMovementRules(
        boolean fixedAnglePosThreshold,
        boolean inputAlwaysCapsGroundSpeed,
        boolean angleDiffCardinalSnap,
        short movingCrouchThreshold,
        boolean airSuperspeedPreserved,
        boolean slopeResistStartsFromRest,
        boolean slopeRepelChecksOnObject,
        boolean slopeRepelUsesS3kSlipKick,
        PlayerLandingRules landing,
        boolean rollingJumpPinballGateRequiresSpindashFlag,
        boolean rollStopsBelowMinimumSpeed,
        boolean rollControlledDecelUsesEffectiveDecelQuarter,
        boolean levelBoundaryRightStrict,
        boolean levelBoundaryUsesCentreY,
        boolean controlLockLatchesLogicalInput,
        boolean hurtRoutineLatchesLogicalInput,
        boolean waterExitBoostSkipsFastUpwardVelocity,
        boolean slopeResistAppliesAtZeroInertia,
        boolean levelBoundaryLockUsesScreenLockFlag,
        boolean levelBoundaryUsesPreEasedMaxXDuringBossLock) {

    public boolean objectSolidHurtLandingRetainsRoutine() {
        return landing.objectSolidHurtLandingRetainsRoutine();
    }
}
