package com.openggf.mods.code;

import com.openggf.game.CharacterKey;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.ModApi;
import com.openggf.game.ZoneKey;

import java.util.List;
import java.util.Objects;

/** One owner-validated, destination-scoped launch-team contribution. */
@ModApi
public record ModLaunchTeamContribution(ZoneKey destination, CharacterKey main,
                                        List<CharacterKey> sidekicks) {
    public ModLaunchTeamContribution {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(main, "main");
        sidekicks = List.copyOf(Objects.requireNonNull(sidekicks, "sidekicks"));
    }

    GameplayLaunchTeam team() {
        return new GameplayLaunchTeam(main, sidekicks);
    }
}
