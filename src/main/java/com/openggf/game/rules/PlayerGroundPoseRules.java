package com.openggf.game.rules;

/** Movement predicates and native script writes for grounded crouch, skid and balance poses. */
@com.openggf.game.ModApi
public record PlayerGroundPoseRules(
        short movingCrouchThreshold,
        /** Preserve the full inertia word across the skid angle-band probe. */
        boolean skidThresholdPreservesLowByte,
        /** Facing away at a single-state balance edge starts script position four. */
        boolean balanceFacingFlipRestartsScript) {
}
