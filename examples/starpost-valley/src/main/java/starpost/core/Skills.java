package starpost.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The farmer's five skills (design doc §6.8): Farming, Ranging (foraging), Fishing, Scrapping
 * (the Ruins' rocks and ore) and Bopping (popping badniks). Experience comes from doing; each
 * skill has ten levels, and levels 5 and 10 each offer a choice of two professions (the
 * level-10 pair follows the level-5 choice). Level-ups are announced at night.
 */
public final class Skills implements SaveSection {
    public static final int FARMING = 0;
    public static final int RANGING = 1;
    public static final int FISHING = 2;
    public static final int SCRAPPING = 3;
    public static final int BOPPING = 4;
    public static final int COUNT = 5;
    /** Experience needed for levels 1 to 10. */
    private static final int L1 = 100, L2 = 380, L3 = 770, L4 = 1300, L5 = 2150, L6 = 3300, L7 = 4800, L8 = 6900,
            L9 = 10000, L10 = 15000;

    private final int[] xp = new int[COUNT];
    private final int[] announced = new int[COUNT];
    /** Chosen professions by id (e.g. "ringgrower"). */
    private final List<String> professions = new ArrayList<>();

    public static String name(int skill) {
        return switch (skill) {
            case FARMING -> "FARMING";
            case RANGING -> "RANGING";
            case FISHING -> "FISHING";
            case SCRAPPING -> "SCRAPPING";
            default -> "BOPPING";
        };
    }

    private static int threshold(int level) {
        return switch (level) {
            case 1 -> L1;
            case 2 -> L2;
            case 3 -> L3;
            case 4 -> L4;
            case 5 -> L5;
            case 6 -> L6;
            case 7 -> L7;
            case 8 -> L8;
            case 9 -> L9;
            default -> L10;
        };
    }

    public void add(int skill, int amount) {
        xp[skill] = Math.min(threshold(10) * 2, xp[skill] + Math.max(0, amount));
    }

    public int xp(int skill) {
        return xp[skill];
    }

    public int level(int skill) {
        int level = 0;
        while (level < 10 && xp[skill] >= threshold(level + 1)) {
            level++;
        }
        return level;
    }

    /** Progress toward the next level, 0 to 1 (1 at level 10). */
    public float progress(int skill) {
        int level = level(skill);
        if (level >= 10) {
            return 1;
        }
        int from = level == 0 ? 0 : threshold(level);
        return (xp[skill] - from) / (float) (threshold(level + 1) - from);
    }

    public boolean has(String profession) {
        return professions.contains(profession);
    }

    /** The two professions offered at a level (5 or 10) of a skill, given the earlier choice. */
    public String[] choices(int skill, int level) {
        if (level == 5) {
            return choicesAt5(skill);
        }
        return choicesAt10(skill, professions.contains(choicesAt5(skill)[0]));
    }

    private static String[] choicesAt10(int skill, boolean firstAt5) {
        return switch (skill) {
            case FARMING -> firstAt5 ? new String[] {"supergrower", "artisan"} : new String[] {"cuddler", "shepherd"};
            case RANGING -> firstAt5 ? new String[] {"palm_tapper", "lumberjack"} : new String[] {"botanist", "tracker"};
            case FISHING -> firstAt5 ? new String[] {"reef_hand", "diver"} : new String[] {"pot_master", "lure_maker"};
            case SCRAPPING -> firstAt5 ? new String[] {"smelter", "prospector"} : new String[] {"shard_hunter", "jeweller"};
            default -> firstAt5 ? new String[] {"hyper_bopper", "brawler"} : new String[] {"ring_keeper", "acrobat"};
        };
    }

    private static String[] choicesAt5(int skill) {
        return switch (skill) {
            case FARMING -> new String[] {"ringgrower", "rancher"};
            case RANGING -> new String[] {"forester", "gatherer"};
            case FISHING -> new String[] {"angler", "trapper"};
            case SCRAPPING -> new String[] {"scrapper", "geologist"};
            default -> new String[] {"insta_shield", "drop_dash"};
        };
    }

    public static String describe(String profession) {
        return switch (profession) {
            case "ringgrower" -> "CROPS SELL FOR 10% MORE.";
            case "rancher" -> "ANIMAL GOODS SELL FOR 20% MORE.";
            case "supergrower" -> "1 IN 10 HARVESTS COMES UP DOUBLE.";
            case "artisan" -> "ARTISAN GOODS SELL FOR 40% MORE.";
            case "cuddler" -> "ANIMALS GROW FOND OF YOU TWICE AS FAST.";
            case "shepherd" -> "POCKIES GIVE FLUFF MORE OFTEN.";
            case "forester" -> "25% MORE PALM WOOD.";
            case "gatherer" -> "1 IN 5 FINDS COMES IN PAIRS.";
            case "palm_tapper" -> "PALMS DROP COCONUTS WHEN CLEARED.";
            case "lumberjack" -> "STUMPS GIVE DOUBLE WOOD.";
            case "botanist" -> "FORAGE SELLS FOR 50% MORE.";
            case "tracker" -> "FORAGE SPARKLES FROM FAR AWAY.";
            case "angler" -> "FISH SELL FOR 25% MORE.";
            case "trapper" -> "CHEAPER CRAB POTS.";
            case "reef_hand" -> "BADNIK CATCHES SELL FOR 50% MORE.";
            case "diver" -> "MORE AIR UNDERWATER.";
            case "pot_master" -> "CRAB POTS NEED NO BAIT.";
            case "lure_maker" -> "LURES LAST TWICE AS LONG.";
            case "scrapper" -> "ONE MORE SCRAP FROM EVERY BADNIK.";
            case "geologist" -> "GEMS SOMETIMES COME IN PAIRS.";
            case "smelter" -> "SMELTED BARS SELL FOR 50% MORE.";
            case "prospector" -> "ORE NODES APPEAR MORE OFTEN.";
            case "shard_hunter" -> "EMERALD SHARDS TURN UP MORE OFTEN.";
            case "jeweller" -> "GEMS SELL FOR 30% MORE.";
            case "insta_shield" -> "A MOMENT'S SAFETY AFTER EVERY BOP.";
            case "drop_dash" -> "LAND FROM A JUMP INTO A ROLL.";
            case "hyper_bopper" -> "BOPS CHAIN INTO BIGGER BOUNCES.";
            case "brawler" -> "BADNIKS POP IN ONE HIT, EVEN BIG ONES.";
            case "ring_keeper" -> "LOSE HALF AS MANY RINGS WHEN HIT.";
            default -> "BOUNCE HIGHER OFF BADNIKS.";
        };
    }

    /** Skills that reached a new level since they were last announced: {skill, level} pairs. */
    public List<int[]> pendingLevels() {
        List<int[]> out = new ArrayList<>();
        for (int s = 0; s < COUNT; s++) {
            for (int l = announced[s] + 1; l <= level(s); l++) {
                out.add(new int[] {s, l});
            }
        }
        return out;
    }

    public void announce(int skill, int level, String chosen) {
        announced[skill] = Math.max(announced[skill], level);
        if (chosen != null && !professions.contains(chosen)) {
            professions.add(chosen);
        }
    }

    @Override
    public String prefix() {
        return "skills";
    }

    @Override
    public void save(Map<String, String> out) {
        for (int s = 0; s < COUNT; s++) {
            out.put("xp." + s, xp[s] + "," + announced[s]);
        }
        out.put("professions", String.join(",", professions));
    }

    @Override
    public void load(Map<String, String> in, Catalog catalog) {
        for (int s = 0; s < COUNT; s++) {
            String v = in.get("xp." + s);
            if (v != null) {
                String[] parts = v.split(",");
                xp[s] = Math.max(0, Math.min(threshold(10) * 2, Integer.parseInt(parts[0])));
                announced[s] = Math.max(0, Math.min(level(s), Integer.parseInt(parts[1])));
            }
        }
        professions.clear();
        for (String p : in.getOrDefault("professions", "").split(",")) {
            if (!p.isBlank() && validProfession(p) && !professions.contains(p)) {
                professions.add(p);
            }
        }
    }

    private static boolean validProfession(String p) {
        for (int s = 0; s < COUNT; s++) {
            for (String[] set : new String[][] {choicesAt5(s), choicesAt10(s, true), choicesAt10(s, false)}) {
                if (set[0].equals(p) || set[1].equals(p)) {
                    return true;
                }
            }
        }
        return false;
    }
}
