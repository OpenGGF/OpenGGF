package com.openggf.game.rules;

@com.openggf.game.ModApi
public record PlayerMovementRules(
        boolean fixedAnglePosThreshold,
        boolean inputAlwaysCapsGroundSpeed,
        boolean angleDiffCardinalSnap,
        short movingCrouchThreshold,
        boolean airSuperspeedPreserved,
        boolean slopeResistStartsFromRest,
        boolean slopeRepelChecksOnObject,
        boolean slopeRepelUsesS3kSlipKick,
        boolean pinballLandingPreservesRoll,
        boolean pinballLandingPreservesPinballMode,
        boolean rollingJumpPinballGateRequiresSpindashFlag,
        boolean landingRollClearUsesCurrentYRadiusDelta,
        boolean rollStopsBelowMinimumSpeed,
        boolean rollControlledDecelUsesEffectiveDecelQuarter,
        boolean levelBoundaryRightStrict,
        boolean levelBoundaryUsesCentreY,
        boolean controlLockLatchesLogicalInput,
        boolean hurtRoutineLatchesLogicalInput,
        boolean waterExitBoostSkipsFastUpwardVelocity,
        boolean slopeResistAppliesAtZeroInertia,
        boolean levelBoundaryLockUsesScreenLockFlag,
        boolean levelBoundaryUsesPreEasedMaxXDuringBossLock,
        boolean sidekickHurtRestoresRadiiWithoutRoll) {
    /** Compatibility constructor for API 1.1. */
    public PlayerMovementRules(boolean fixedAnglePosThreshold, boolean inputAlwaysCapsGroundSpeed,
                               boolean angleDiffCardinalSnap, short movingCrouchThreshold,
                               boolean airSuperspeedPreserved, boolean slopeResistStartsFromRest,
                               boolean slopeRepelChecksOnObject, boolean slopeRepelUsesS3kSlipKick,
                               boolean pinballLandingPreservesRoll, boolean pinballLandingPreservesPinballMode,
                               boolean rollingJumpPinballGateRequiresSpindashFlag,
                               boolean landingRollClearUsesCurrentYRadiusDelta, boolean rollStopsBelowMinimumSpeed,
                               boolean rollControlledDecelUsesEffectiveDecelQuarter, boolean levelBoundaryRightStrict,
                               boolean levelBoundaryUsesCentreY, boolean controlLockLatchesLogicalInput,
                               boolean hurtRoutineLatchesLogicalInput, boolean waterExitBoostSkipsFastUpwardVelocity,
                               boolean slopeResistAppliesAtZeroInertia, boolean levelBoundaryLockUsesScreenLockFlag,
                               boolean levelBoundaryUsesPreEasedMaxXDuringBossLock) {
        this(fixedAnglePosThreshold, inputAlwaysCapsGroundSpeed, angleDiffCardinalSnap, movingCrouchThreshold,
                airSuperspeedPreserved, slopeResistStartsFromRest, slopeRepelChecksOnObject,
                slopeRepelUsesS3kSlipKick, pinballLandingPreservesRoll, pinballLandingPreservesPinballMode,
                rollingJumpPinballGateRequiresSpindashFlag, landingRollClearUsesCurrentYRadiusDelta,
                rollStopsBelowMinimumSpeed, rollControlledDecelUsesEffectiveDecelQuarter, levelBoundaryRightStrict,
                levelBoundaryUsesCentreY, controlLockLatchesLogicalInput, hurtRoutineLatchesLogicalInput,
                waterExitBoostSkipsFastUpwardVelocity, slopeResistAppliesAtZeroInertia,
                levelBoundaryLockUsesScreenLockFlag, levelBoundaryUsesPreEasedMaxXDuringBossLock, false);
    }
}
