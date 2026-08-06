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
        PlayerLevelBoundaryRules levelBoundary,
        boolean rollingJumpPinballGateRequiresSpindashFlag,
        boolean rollStopsBelowMinimumSpeed,
        boolean rollControlledDecelUsesEffectiveDecelQuarter,
        boolean controlLockLatchesLogicalInput,
        boolean hurtRoutineLatchesLogicalInput,
        boolean waterExitBoostSkipsFastUpwardVelocity,
        boolean slopeResistAppliesAtZeroInertia) {

    public boolean objectSolidHurtLandingRetainsRoutine() {
        return landing.objectSolidHurtLandingRetainsRoutine();
    }

    public boolean levelBoundaryRightStrict() {
        return levelBoundary.rightStrict();
    }

    public boolean levelBoundaryUsesCentreY() {
        return levelBoundary.usesCentreY();
    }

    public boolean levelBoundaryLockUsesScreenLockFlag() {
        return levelBoundary.lockUsesScreenLockFlag();
    }

    public boolean levelBoundaryUsesPreEasedMaxXDuringBossLock() {
        return levelBoundary.usesPreEasedMaxXDuringBossLock();
    }
}
