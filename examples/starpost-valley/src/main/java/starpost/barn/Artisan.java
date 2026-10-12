package starpost.barn;

import starpost.core.Catalog;
import starpost.core.Game;
import starpost.core.Item;
import starpost.core.Kind;
import starpost.core.Machine;

/**
 * The artisan machines' rules (design doc §6.8), engine-free. Each takes one item and works
 * overnight; acting on it again when it is ready takes the goods.
 * <ul>
 *   <li>Monitor Jar: any crop or forage, three days, a jar worth twice the input plus 50.</li>
 *   <li>Spring Yard Keg: fruit makes a fizz worth three times the fruit in five days; Spring Yard
 *       Hops make the keg's own Spring Yard Fizz in two.</li>
 *   <li>Fluff Loom: Pocky fluff into Hill Cloth overnight (Tails builds the big coop and pen from it).</li>
 *   <li>Sunflower Press: a sunflower into oil overnight; a Hill Truffle into Truffle Oil in two days.</li>
 * </ul>
 * Sunflower oil dabbed on a working machine makes it finish a day sooner.
 */
public final class Artisan {
    public static final String JAR = "monitor_jar";
    public static final String KEG = "spring_yard_keg";
    public static final String LOOM = "fluff_loom";
    public static final String PRESS = "sunflower_press";
    public static final String OIL = "sunflower_oil";

    private Artisan() {
    }

    /** What a machine makes from an input: output, count and days. */
    public record Job(String output, int count, int days) {
    }

    public static boolean machine(String id) {
        return id.equals(JAR) || id.equals(KEG) || id.equals(LOOM) || id.equals(PRESS);
    }

    /** The fruit a keg takes. */
    public static boolean fruit(String id) {
        return switch (id) {
            case "emerald_melon", "bluesphere_berry", "ruby_berry", "marble_grape", "motobug_tomato", "loop_berry",
                    "palm_coconut" -> true;
            default -> false;
        };
    }

    /** The job a machine would do with an input, or null when it does not take it. */
    public static Job job(Catalog catalog, String machine, String input) {
        if (input == null || !catalog.hasItem(input)) {
            return null;
        }
        Item item = catalog.item(input);
        return switch (machine) {
            case JAR -> (item.kind() == Kind.CROP || item.kind() == Kind.FORAGE) && catalog.hasItem("jar_" + input)
                    ? new Job("jar_" + input, 1, 3) : null;
            case KEG -> input.equals("spring_yard_hops") ? new Job("spring_yard_fizz", 1, 2)
                    : fruit(input) && catalog.hasItem("fizz_" + input) ? new Job("fizz_" + input, 1, 5) : null;
            case LOOM -> input.equals("pocky_fluff") ? new Job("hill_cloth", 1, 1) : null;
            case PRESS -> input.equals("sunflower") ? new Job(OIL, 1, 1)
                    : input.equals("hill_truffle") ? new Job("truffle_oil", 1, 2) : null;
            default -> null;
        };
    }

    /** What a machine wants, for its prompt. */
    public static String wants(String machine) {
        return switch (machine) {
            case JAR -> "THE MONITOR JAR TAKES A CROP OR FORAGE";
            case KEG -> "THE KEG TAKES FRUIT OR SPRING YARD HOPS";
            case LOOM -> "THE LOOM TAKES POCKY FLUFF";
            default -> "THE PRESS TAKES SUNFLOWERS OR TRUFFLES";
        };
    }

    private static String key(int row, int column) {
        return row + "." + column;
    }

    /**
     * The action button on a machine holding {@code held}: collect what is ready, oil or report on
     * one at work, or load an input. Returns the message to show.
     */
    public static String use(Game game, int row, int column, String machine, String held) {
        Machine work = game.farm.machine(row, column);
        if (work != null && work.ready(game.calendar)) {
            Item out = game.item(work.output());
            if (!game.inventory.fits(out, work.count())) {
                return "NO ROOM FOR " + out.name();
            }
            game.inventory.add(out, work.count());
            game.farm.machines.remove(key(row, column));
            game.xp(starpost.core.Skills.FARMING, 3 + out.price() / 50);
            return "GOT " + (work.count() > 1 ? work.count() + " " : "") + out.name() + "!";
        }
        if (work != null) {
            if (OIL.equals(held) && work.daysLeft(game.calendar) > 1) {
                game.inventory.remove(OIL, 1);
                game.farm.machines.put(key(row, column),
                        new Machine(work.input(), work.output(), work.count(), work.readyDay() - 1));
                return "OILED: A DAY SOONER";
            }
            int days = work.daysLeft(game.calendar);
            return game.item(work.output()).name() + ": " + (days <= 1 ? "READY TOMORROW" : "READY IN " + days + " DAYS");
        }
        Job job = job(game.catalog, machine, held);
        if (job == null) {
            return wants(machine);
        }
        game.inventory.remove(held, 1);
        game.farm.machines.put(key(row, column),
                new Machine(held, job.output(), job.count(), game.calendar.dayNumber() + job.days()));
        return "LOADED: " + game.item(job.output()).name();
    }
}
