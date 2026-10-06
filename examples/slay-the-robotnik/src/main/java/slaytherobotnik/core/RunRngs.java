package slaytherobotnik.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The run's random streams. Persistent streams (card rewards, relics, potions, events,
 * encounters, the Egg Robo's stock, treasure, start bonuses) carry on through the run and are
 * saved. Per-floor streams (enemy HP, enemy AI, shuffles, random card effects) are derived
 * from the seed and the floor number, so replaying a floor from a save gives the same fight.
 */
public final class RunRngs {
    public static final String CARDS = "cards";
    public static final String RELICS = "relics";
    public static final String POTIONS = "potions";
    public static final String EVENTS = "events";
    public static final String MONSTERS = "monsters";
    public static final String MERCHANT = "merchant";
    public static final String TREASURE = "treasure";
    public static final String START = "start";

    private final long seed;
    private final Map<String, Rng> streams = new LinkedHashMap<>();
    private int floor;

    public RunRngs(long seed) {
        this.seed = seed;
        for (String name : new String[] {CARDS, RELICS, POTIONS, EVENTS, MONSTERS, MERCHANT, TREASURE, START}) {
            streams.put(name, new Rng(Rng.derive(seed, name.hashCode())));
        }
    }

    public long seed() { return seed; }

    public Rng stream(String name) {
        Rng rng = streams.get(name);
        if (rng == null) {
            throw new IllegalArgumentException("Unknown stream " + name);
        }
        return rng;
    }

    /** Sets the floor used to derive per-floor streams. */
    public void setFloor(int floor) {
        this.floor = floor;
    }

    /** A fresh stream for {@code purpose} on the current floor. */
    public Rng floorRng(String purpose) {
        return new Rng(Rng.derive(seed, floor * 7919L + purpose.hashCode()));
    }

    /** The map stream for an act (maps are regenerated from the seed, never saved). */
    public Rng mapRng(int act) {
        return new Rng(Rng.derive(seed, 0x6D617000L + act));
    }

    /** Saved states of the persistent streams, by name. */
    public Map<String, Long> states() {
        Map<String, Long> out = new LinkedHashMap<>();
        streams.forEach((k, v) -> out.put(k, v.state()));
        return out;
    }

    public void restore(Map<String, Long> states) {
        states.forEach((k, v) -> {
            Rng rng = streams.get(k);
            if (rng != null) {
                rng.restore(v);
            }
        });
    }
}
