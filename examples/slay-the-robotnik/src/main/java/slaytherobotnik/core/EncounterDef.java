package slaytherobotnik.core;

import java.util.List;

/**
 * A fight that can appear on the map. {@code pool} decides when: {@link #WEAK} for the first
 * fights of an act, {@link #STRONG} afterwards, {@link #ELITE}, {@link #BOSS}, or
 * {@link #EVENT} for fights only events start.
 *
 * @param spawner creates fresh enemies; {@code hp} is the floor's HP stream, so the same
 *                seed always gives the same HP rolls
 */
public record EncounterDef(String id, String name, int act, String pool, int weight, Spawner spawner) {
    public static final String WEAK = "Weak";
    public static final String STRONG = "Strong";
    public static final String ELITE = "Elite";
    public static final String BOSS = "Boss";
    public static final String EVENT = "Event";

    @FunctionalInterface
    public interface Spawner {
        List<Enemy> spawn(Rng hp);
    }
}
