package com.openggf.game.save;

import com.openggf.game.GameplayLaunchTeam;

import java.util.Objects;

/** Engine-internal bridge for applying a launch-only team to a save context copy. */
public final class SaveSessionLaunchTeamAccess {
    private SaveSessionLaunchTeamAccess() {
    }

    public static SaveSessionContext withLaunchTeam(
            SaveSessionContext context, GameplayLaunchTeam replacement) {
        return Objects.requireNonNull(context, "context").withLaunchTeam(replacement);
    }
}
