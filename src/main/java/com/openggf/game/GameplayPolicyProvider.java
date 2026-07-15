package com.openggf.game;

import com.openggf.level.objects.HudProfile;

import java.util.Optional;

/** Destination-scoped immutable gameplay policies exposed by a resolved module. */
@ModApi
public interface GameplayPolicyProvider {
    GameplayPolicyProvider EMPTY = new GameplayPolicyProvider() { };

    default Optional<GameplayLaunchTeam> launchTeam(ZoneKey destination) {
        return Optional.empty();
    }

    default Optional<GameplayInputFilter> inputFilter(ZoneKey destination) {
        return Optional.empty();
    }

    default Optional<HudProfile> hudProfile(ZoneKey destination) {
        return Optional.empty();
    }
}
