package com.openggf.game.internal;

import com.openggf.mods.mutators.CharacterSuperFormPolicy;
import java.util.function.Predicate;

/** Host-semantic transformation gates, supplied by native module composition. */
public record ProfileSuperHostRules(boolean explicitAirTrigger, boolean customEmeraldGate,
        int initialDrainCounter, Predicate<CharacterSuperFormPolicy> enabled) { }
