package com.openggf.game.rules;

@com.openggf.game.ModApi
public record PlayerLandingRules(
        boolean pinballLandingPreservesRoll,
        boolean pinballLandingPreservesPinballMode,
        boolean landingRollClearUsesCurrentYRadiusDelta,
        boolean objectSolidHurtLandingRetainsRoutine,
        /** S2/S3K HurtStop clears the native object-control byte; S1 does not. */
        boolean hurtLandingClearsObjectControl) {

    /** Retains the original control-preserving behavior for existing creator profiles. */
    public PlayerLandingRules(boolean pinballLandingPreservesRoll,
                              boolean pinballLandingPreservesPinballMode,
                              boolean landingRollClearUsesCurrentYRadiusDelta,
                              boolean objectSolidHurtLandingRetainsRoutine) {
        this(pinballLandingPreservesRoll, pinballLandingPreservesPinballMode,
                landingRollClearUsesCurrentYRadiusDelta, objectSolidHurtLandingRetainsRoutine, false);
    }
}
