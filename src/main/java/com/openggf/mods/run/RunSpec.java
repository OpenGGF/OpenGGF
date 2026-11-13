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
 * @param area      a fenced part of the act the run is held in, or null for the whole act from
 *                  its usual start
 */
@ModApi
public record RunSpec(String gameId, int zone, int act, String character, GameplayRunPolicy policy,
                      RunArea area, TrackKind kind, boolean swapEnabled) {
    public RunSpec(String gameId, int zone, int act, String character, GameplayRunPolicy policy, RunArea area, TrackKind kind) {
        this(gameId, zone, act, character, policy, area, kind, policy.swapEnabled());
    }
    public RunSpec(String gameId, int zone, int act, String character, GameplayRunPolicy policy, RunArea area) {
        this(gameId, zone, act, character, policy, area, TrackKind.ACT);
    }
    /** The whole act from its usual start. */
    public RunSpec(String gameId, int zone, int act, String character, GameplayRunPolicy policy) {
        this(gameId, zone, act, character, policy, null);
    }

    public RunSpec {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(policy, "policy");
        // The launch descriptor owns frozen round eligibility; native services read the policy.
        policy = new GameplayRunPolicy(policy.specialStageEntry(), policy.bonusStageEntry(),
                policy.actCompletion(), policy.liveRewind(), policy.editorEntry(), policy.deathRestart(),
                policy.livesCounter(), policy.zoneIntros(), policy.monitors(), swapEnabled);
        if (zone < 0 || act < 0) {
            throw new IllegalArgumentException("zone and act must be non-negative");
        }
    }
}
