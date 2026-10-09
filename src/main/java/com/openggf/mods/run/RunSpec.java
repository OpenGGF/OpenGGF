package com.openggf.mods.run;

import com.openggf.game.ModApi;
import com.openggf.game.session.GameplayRunPolicy;

import java.util.Objects;

/**
 * A gameplay run to launch: one stock game, starting act and character, played under
 * {@code policy}. The engine resolves the ROM, game module and display; the run never saves and
 * contains no mod gameplay content, so its simulation is the shipped game's.
 *
 * @param gameId    {@code s1}, {@code s2} or {@code s3k}
 * @param zone      engine zone index of the starting act
 * @param act       act index within the zone
 * @param character stock main character ({@code sonic}, {@code tails}, {@code knuckles})
 * @param policy    what the run permits beyond its starting act
 */
@ModApi
public record RunSpec(String gameId, int zone, int act, String character, GameplayRunPolicy policy) {
    public RunSpec {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(policy, "policy");
        if (zone < 0 || act < 0) {
            throw new IllegalArgumentException("zone and act must be non-negative");
        }
    }
}
