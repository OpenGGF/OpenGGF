package com.openggf.game;

import com.openggf.level.objects.HudProfile;

import java.util.Optional;

/** Destination-scoped immutable gameplay policies exposed by a resolved module. */
@ModApi
public interface GameplayPolicyProvider {
    GameplayPolicyProvider EMPTY = new GameplayPolicyProvider() {
        @Override public Optional<GameplayLaunchTeam> launchTeam(ZoneKey destination) {
            return Optional.empty();
        }

        @Override public Optional<GameplayInputFilter> inputFilter(ZoneKey destination) {
            return Optional.empty();
        }

        @Override public Optional<HudProfile> hudProfile(ZoneKey destination) {
            return Optional.empty();
        }
    };

    Optional<GameplayLaunchTeam> launchTeam(ZoneKey destination);

    Optional<GameplayInputFilter> inputFilter(ZoneKey destination);

    Optional<HudProfile> hudProfile(ZoneKey destination);
}
