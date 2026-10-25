package com.openggf.mods.scene;

import java.util.List;
import java.util.Objects;

/**
 * A stock game session: the game ({@code s1}, {@code s2} or {@code s3k}) and its playable team
 * as character codes ({@code sonic}, {@code tails}, {@code knuckles}, ...). Read the running
 * session with {@link SceneContext#currentLaunch()}; ask for another with
 * {@link SceneContext#relaunch(SceneLaunch)}.
 */
@com.openggf.game.ModApi
public record SceneLaunch(String gameId, String mainCharacter, List<String> sidekicks) {
    public SceneLaunch {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(mainCharacter, "mainCharacter");
        sidekicks = List.copyOf(Objects.requireNonNull(sidekicks, "sidekicks"));
        if (!List.of("s1", "s2", "s3k").contains(gameId)) {
            throw new IllegalArgumentException("Unknown stock game: " + gameId);
        }
        if (mainCharacter.isBlank() || sidekicks.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Character codes must not be blank");
        }
    }
}
