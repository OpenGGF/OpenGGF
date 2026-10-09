package starpost.core;

import com.openggf.mods.state.SnapshotRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything a save holds: the calendar, the farmer, rings, Momentum, the monitor slots, the
 * farm, what is in the shipping signpost, and story flags. Engine-free, so rules can be tested
 * without a ROM.
 */
public final class Game {
    public static final int START_RINGS = 500;
    public static final int BASE_MOMENTUM = 100;

    public final Catalog catalog;
    public final Calendar calendar = new Calendar();
    public final Inventory inventory = new Inventory();
    public final Farm farm = new Farm();
    public final SnapshotRandom rng;
    /** Sonic, Tails or Knuckles: "sonic", "tails", "knuckles". */
    public String farmer = "sonic";
    public String farmName = "STARPOST";
    public int rings = START_RINGS;
    public int momentum = BASE_MOMENTUM;
    public int maxMomentum = BASE_MOMENTUM;
    public boolean raining;
    public boolean rainTomorrow;
    /** The shipping signpost: item id to count, paid out overnight. */
    public final Map<String, Integer> shipping = new LinkedHashMap<>();
    public final Set<String> flags = new LinkedHashSet<>();
    public long totalEarned;
    public int waterCharges = 10;
    public int waterCapacity = 10;
    /** The game's other systems, each saving its own keys (see {@link SaveSection}). */
    public final List<SaveSection> sections = new ArrayList<>();

    public Game(Catalog catalog, long seed) {
        this.catalog = catalog;
        this.rng = new SnapshotRandom(seed);
    }

    /** A brand-new farm: starter tools and seeds, an overgrown field. */
    public static Game fresh(Catalog catalog, long seed, String farmer) {
        Game game = new Game(catalog, seed);
        game.farmer = farmer;
        game.inventory.add(catalog.item("water_shield"), 1);
        game.inventory.add(catalog.item("ring_radish_seeds"), 15);
        game.farm.overgrow(game.rng);
        return game;
    }

    /** A system's section, or null when it is not installed. */
    public <T extends SaveSection> T section(Class<T> type) {
        for (SaveSection section : sections) {
            if (type.isInstance(section)) {
                return type.cast(section);
            }
        }
        return null;
    }

    public Item item(String id) {
        return catalog.item(id);
    }

    /** Spends Momentum on a chore; false (and nothing spent) when there is not enough. */
    public boolean spend(int cost) {
        if (momentum < cost) {
            return false;
        }
        momentum -= cost;
        return true;
    }

    public void restore(int amount) {
        momentum = Math.min(maxMomentum, momentum + amount);
    }

    public void ship(String id, int count) {
        shipping.merge(id, count, Integer::sum);
    }

    /** What the signpost pays tonight. */
    public int shippingValue() {
        int total = 0;
        for (Map.Entry<String, Integer> e : shipping.entrySet()) {
            total += catalog.item(e.getKey()).price() * e.getValue();
        }
        return total;
    }

    /**
     * Sleeping: pays the signpost, grows the farm, rolls the weather and starts the next day.
     * Momentum refills fully after a good night, partly after fainting or staying up late.
     * Returns the rings paid out.
     */
    public int sleep(boolean fainted) {
        int paid = shippingValue();
        rings += paid;
        totalEarned += paid;
        shipping.clear();
        calendar.nextDay();
        raining = rainTomorrow;
        rainTomorrow = calendar.season() != Calendar.WINTER && rng.nextInt(100) < (calendar.season() == Calendar.SUMMER ? 12 : 20);
        farm.garden = flags.contains("capsule_garden");
        farm.nextDay(catalog, calendar.season(), raining, rng);
        for (SaveSection section : sections) {
            section.nextDay(this);
        }
        waterCharges = waterCapacity;
        momentum = fainted ? maxMomentum / 2 : maxMomentum;
        if (fainted) {
            rings -= Math.min(rings / 10, 1000);
        }
        return paid;
    }
}
