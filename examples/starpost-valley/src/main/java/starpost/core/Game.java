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
    /** Today's and tomorrow's weather (design doc §8): one of the {@code SUN}...{@code SWARM} values. */
    public int weather = SUN;
    public int weatherTomorrow = SUN;
    /** Tonight's sky shows the Emerald Aurora. */
    public boolean aurora;
    public static final int SUN = 0;
    public static final int RAIN = 1;
    public static final int STORM = 2;
    public static final int SNOW = 3;
    public static final int SWARM = 4;
    /** Rain or storm today: tilled soil is watered. */
    public boolean raining;
    /** The shipping signpost: item id to count, paid out overnight. */
    public final Map<String, Integer> shipping = new LinkedHashMap<>();
    public final Set<String> flags = new LinkedHashSet<>();
    public long totalEarned;
    /** The freed animals living in the valley (design doc §6.7): 6 at the start, up to 60. */
    public int population = 6;
    public static final int MAX_POPULATION = 60;
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
    /** Classic stamina instead of Momentum: laps, springs and rings no longer restore it. */
    public boolean stamina;

    /** Momentum from a Sonic thing (a lap, a ring); nothing in stamina mode. */
    public void restoreBySpeed(int amount) {
        if (!stamina) {
            restore(amount);
        }
    }

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

    /** The weather for a season from a roll of 0-99: summer storms, autumn rain, winter snow. */
    static int rollWeather(int season, int roll) {
        return switch (season) {
            case Calendar.SPRING -> roll < 18 ? RAIN : roll < 22 ? STORM : roll < 26 ? SWARM : SUN;
            case Calendar.SUMMER -> roll < 7 ? RAIN : roll < 17 ? STORM : roll < 21 ? SWARM : SUN;
            case Calendar.FALL -> roll < 20 ? RAIN : roll < 23 ? STORM : roll < 28 ? SWARM : SUN;
            default -> roll < 28 ? SNOW : SUN;
        };
    }

    public static String weatherName(int weather) {
        return switch (weather) {
            case RAIN -> "RAIN";
            case STORM -> "STORM";
            case SNOW -> "SNOW";
            case SWARM -> "BADNIK SWARM";
            default -> "SUNNY";
        };
    }

    /** Adds skill experience when the skills system is installed. */
    public void xp(int skill, int amount) {
        Skills skills = section(Skills.class);
        if (skills != null) {
            skills.add(skill, amount);
        }
    }

    /** Whether the farmer has a profession (false when skills are not installed). */
    public boolean has(String profession) {
        Skills skills = section(Skills.class);
        return skills != null && skills.has(profession);
    }

    /** What one of an item sells for, with the farmer's professions applied. */
    public int sellPrice(Item item) {
        float mult = switch (item.kind()) {
            case CROP -> has("ringgrower") ? 1.1f : 1;
            case ANIMAL_GOOD -> has("rancher") ? 1.2f : 1;
            case ARTISAN -> has("artisan") ? 1.4f : 1;
            case FORAGE -> has("botanist") ? 1.5f : 1;
            case FISH -> has("angler") ? 1.25f : 1;
            case MINERAL -> has("jeweller") ? 1.3f : 1;
            default -> 1;
        };
        return Math.round(item.price() * mult);
    }

    /** An animal freed from a badnik joins the valley. Returns true when the count went up. */
    public boolean free() {
        if (population >= MAX_POPULATION) {
            return false;
        }
        population++;
        return true;
    }

    public void ship(String id, int count) {
        shipping.merge(id, count, Integer::sum);
    }

    /** What the signpost pays tonight. */
    public int shippingValue() {
        int total = 0;
        for (Map.Entry<String, Integer> e : shipping.entrySet()) {
            total += sellPrice(catalog.item(e.getKey())) * e.getValue();
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
        weather = weatherTomorrow;
        raining = weather == RAIN || weather == STORM;
        weatherTomorrow = rollWeather(calendar.season(), rng.nextInt(100));
        aurora = weather == SUN && rng.nextInt(100) < 4;
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
