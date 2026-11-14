package com.openggf.game.internal;

/** Host-semantic transformation gates, supplied by native module composition. */
public record ProfileSuperHostRules(boolean explicitAirTrigger, boolean customEmeraldGate,
        int initialDrainCounter, com.openggf.game.GameId game) { }
