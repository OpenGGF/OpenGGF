package com.openggf.game.rules;

@com.openggf.game.ModApi
public record PlayerLandingRules(
        boolean pinballLandingPreservesRoll,
        boolean pinballLandingPreservesPinballMode,
        boolean landingRollClearUsesCurrentYRadiusDelta,
        boolean objectSolidHurtLandingRetainsRoutine) {
}
