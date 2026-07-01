package com.openggf.game.rules;

import com.openggf.game.PhysicsFeatureSet;

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
        boolean levelBoundaryLockUsesScreenLockFlag) {

    public static PlayerMovementRules fromLegacy(PhysicsFeatureSet fs) {
        return new PlayerMovementRules(
                fs.fixedAnglePosThreshold(),
                fs.inputAlwaysCapsGroundSpeed(),
                fs.angleDiffCardinalSnap(),
                fs.movingCrouchThreshold(),
                fs.airSuperspeedPreserved(),
                fs.slopeResistStartsFromRest(),
                fs.slopeRepelChecksOnObject(),
                fs.slopeRepelUsesS3kSlipKick(),
                fs.pinballLandingPreservesRoll(),
                fs.pinballLandingPreservesPinballMode(),
                fs.rollingJumpPinballGateRequiresSpindashFlag(),
                fs.landingRollClearUsesCurrentYRadiusDelta(),
                fs.rollStopsBelowMinimumSpeed(),
                fs.rollControlledDecelUsesEffectiveDecelQuarter(),
                fs.levelBoundaryRightStrict(),
                fs.levelBoundaryUsesCentreY(),
                fs.controlLockLatchesLogicalInput(),
                fs.hurtRoutineLatchesLogicalInput(),
                fs.waterExitBoostSkipsFastUpwardVelocity(),
                fs.slopeResistAppliesAtZeroInertia(),
                fs.levelBoundaryLockUsesScreenLockFlag());
    }
}
