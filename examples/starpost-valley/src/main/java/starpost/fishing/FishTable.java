package starpost.fishing;

import com.openggf.mods.state.SnapshotRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import starpost.core.Calendar;
import starpost.core.Game;

/**
 * Everything that bites in the valley (design doc §6.6): sixteen original fish and five submerged
 * badniks, each with its spot, seasons, hours and weather. Built per instance (the mod validator
 * forbids static tables). Engine-free: the selection rules are tested without a ROM.
 */
public final class FishTable {
    /** The junk catch: a can somebody dropped in the water. */
    public static final String JUNK = "robo_cola";
    /** Chance in a hundred that a bite is junk. */
    public static final int POND_JUNK = 12;
    public static final int LAKE_JUNK = 7;
    /** Minutes of the day. */
    private static final int DAWN = 6 * 60;
    private static final int NOON = 12 * 60;
    private static final int DUSK = 18 * 60;
    private static final int NIGHT = 20 * 60;
    private static final int LATE = 26 * 60;
    private static final int SP = 1;
    private static final int SU = 2;
    private static final int FA = 4;
    private static final int WI = 8;
    private static final int ALL = 15;

    /** Where and when a line is in the water. {@code depth} is the cast's depth, 0-100. */
    public record Waters(int spot, int season, int minutes, int weather, boolean aurora, int depth, Set<String> flags,
            Set<String> landedOnce) {
        /** The farmer's waters now: today's weather and season, the story so far. */
        public static Waters of(Game game, int spot, int depth, Set<String> landedOnce) {
            return new Waters(spot, game.calendar.season(), game.calendar.minutes(), game.weather, game.aurora, depth,
                    game.flags, landedOnce);
        }
    }

    private final List<FishDef> all = new ArrayList<>();

    public FishTable() {
        int both = FishDef.POND | FishDef.LAKE;
        fish("spring_minnow", "SPRING MINNOW", 30, both, SP | SU, DAWN, NIGHT, FishDef.ANY, 12, FishDef.SMOOTH, 40, 0,
                "QUICK TO BITE, EASY TO LAND. EVERYONE'S FIRST FISH.");
        fish("checker_perch", "CHECKER PERCH", 55, both, SP | FA, DAWN, LATE, FishDef.ANY, 28, FishDef.MIXED, 30, 0,
                "CHECKERED LIKE TILLED SOIL. BITES DAY AND NIGHT.");
        fish("bubble_bass", "BUBBLE BASS", 80, both, ALL, DAWN, NIGHT, FishDef.ANY, 35, FishDef.SMOOTH, 30, 0,
                "FULL OF AIR. LANDING ONE ADDS 5 TO THE WATER SHIELD.");
        fish("ring_carp", "RING CARP", 70, both, SP | SU | FA, DAWN, LATE, FishDef.ANY, 40, FishDef.SINKER, 25, 0,
                "ALWAYS HAS A RING IN ITS MOUTH: 5 RINGS A CATCH.");
        fish("green_sunfish", "GREEN HILL SUNFISH", 60, FishDef.POND, SU, 8 * 60, DUSK, FishDef.DRY, 25, FishDef.FLOATER,
                30, 0, "SUNNY SUMMER DAYS ONLY. BASKS NEAR THE TOP.");
        fish("drizzle_trout", "DRIZZLE TROUT", 110, both, SP | FA, DAWN, LATE, FishDef.WET, 50, FishDef.DART, 25, 0,
                "ONLY BITES WHEN IT RAINS.");
        fish("snow_smelt", "SNOW SMELT", 50, both, WI, DAWN, LATE, FishDef.ANY, 30, FishDef.MIXED, 35, 0,
                "WINTER'S POND FISH. COLD TO THE TOUCH.");
        fish("scrap_sucker", "SCRAP SUCKER", 40, FishDef.POND, SP | SU | FA, DAWN, LATE, FishDef.SWARM, 45,
                FishDef.SINKER, 40, 0, "FEEDS ON RUST AFTER A SWARM. COMES UP WITH SCRAP.");
        fish("emerald_koi", "EMERALD KOI", 420, FishDef.POND, SU, DAWN, DUSK, FishDef.DRY, 72, FishDef.DART, 3, 0,
                "RARE AND LUCKY: 30 MOMENTUM WHEN LANDED.");
        fish("loop_pike", "LOOP PIKE", 150, FishDef.LAKE, SU | FA, DAWN, NIGHT, FishDef.ANY, 58, FishDef.LOOPS, 22, 0,
                "SWIMS IN LOOPS. READ THE LOOP, LAND THE PIKE.");
        fish("spindash_shad", "SPINDASH SHAD", 125, FishDef.LAKE, SU, DAWN, LATE, FishDef.ANY, 65, FishDef.DART, 18, 0,
                "BURSTS LIKE A SPIN DASH, THEN STOPS DEAD.");
        fish("marble_catfish", "MARBLE CATFISH", 200, FishDef.LAKE, FA | WI, DAWN, LATE, FishDef.WET, 68,
                FishDef.SINKER, 14, 0, "HUGS THE BOTTOM. RAINY DAYS ONLY.");
        fish("starlight_eel", "STAR LIGHT EEL", 240, FishDef.LAKE, ALL, NIGHT, LATE, FishDef.ANY, 74, FishDef.MIXED, 12,
                0, "ONLY AFTER DARK. GLOWS LIKE STAR LIGHT ZONE.");
        fish("ice_cap_char", "ICE CAP CHAR", 130, FishDef.LAKE, WI, DAWN, LATE, FishDef.ANY, 52, FishDef.SMOOTH, 25, 0,
                "WINTER'S LAKE FISH, DOWN FROM ICE CAP.");
        fish("labyrinth_gar", "LABYRINTH GAR", 320, FishDef.LAKE, ALL, DAWN, LATE, FishDef.ANY, 84, FishDef.DART, 4, 60,
                "OLD AS THE RUINS. BITES ONLY ON A LONG CAST.");
        fish("aurora_angelfish", "AURORA ANGELFISH", 500, FishDef.LAKE, ALL, NIGHT, LATE, FishDef.AURORA, 80,
                FishDef.FLOATER, 20, 0, "RISES ONLY UNDER THE EMERALD AURORA.");
        // Submerged badniks (the legends): landing one pops it, frees an animal and leaves its shell.
        badnik("chopper_shell", "CHOPPER SHELL", 180, both, SP | SU | FA, DAWN, NIGHT, FishDef.ANY, 55, FishDef.CHOPPER, 7,
                "chopper", null, 0, "A GREEN HILL CHOPPER'S SHELL. LANDING IT FREED AN ANIMAL.");
        badnik("jaws_fin", "JAWS FIN", 220, FishDef.LAKE, FA | WI, DAWN, LATE, FishDef.ANY, 62, FishDef.JAWS, 6, "jaws",
                null, 30, "A LABYRINTH JAWS'S FIN. LANDING IT FREED AN ANIMAL.");
        badnik("jawz_torpedo", "JAWZ TORPEDO", 260, FishDef.LAKE, SP | SU, DAWN, LATE, FishDef.WET, 70, FishDef.JAWZ, 6,
                "jawz", null, 30, "A HYDROCITY JAWZ. IT CHARGES THE BUBBLE. IT FREED AN ANIMAL.");
        badnik("blastoid_cannon", "BLASTOID CANNON", 300, FishDef.LAKE, ALL, DAWN, LATE, FishDef.STORM, 76,
                FishDef.BLASTOID, 10, "blastoid", null, 50, "A BLASTOID LOOSED BY THE STORM. IT FREED AN ANIMAL.");
        all.add(new FishDef("red_chopper_shell", "RED CHOPPER SHELL", 3000, FishDef.LAKE, SU | FA, DAWN, NOON,
                FishDef.ANY, 95, FishDef.RED_CHOPPER, 3, "red_chopper", "red_chopper_story", true, 70,
                "THE GIANT CHOPPER UNDER THE JETTY. IT HAD BARNABY'S HAT."));
    }

    private void fish(String id, String name, int price, int spots, int seasons, int from, int to, int weather,
            int difficulty, int motion, int weight, int deep, String text) {
        all.add(new FishDef(id, name, price, spots, seasons, from, to, weather, difficulty, motion, weight, null, null,
                false, deep, text));
    }

    private void badnik(String id, String name, int price, int spots, int seasons, int from, int to, int weather,
            int difficulty, int motion, int weight, String badnik, String flag, int deep, String text) {
        all.add(new FishDef(id, name, price, spots, seasons, from, to, weather, difficulty, motion, weight, badnik, flag,
                false, deep, text));
    }

    public List<FishDef> all() {
        return new ArrayList<>(all);
    }

    /** The fish with this item id, or null. */
    public FishDef get(String id) {
        for (FishDef def : all) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }

    /** Whether the weather suits a fish today. */
    static boolean weatherFits(int want, int weather, boolean aurora) {
        return switch (want) {
            case FishDef.DRY -> weather == Game.SUN;
            case FishDef.WET -> weather == Game.RAIN || weather == Game.STORM;
            case FishDef.STORM -> weather == Game.STORM;
            case FishDef.SNOW -> weather == Game.SNOW;
            case FishDef.SWARM -> weather == Game.SWARM;
            case FishDef.AURORA -> aurora;
            default -> true;
        };
    }

    /** Everything that could bite in these waters now. */
    public List<FishDef> candidates(Waters w) {
        List<FishDef> out = new ArrayList<>();
        for (FishDef def : all) {
            if ((def.spots() & w.spot()) != 0 && def.bitesIn(w.season()) && def.bitesAt(w.minutes())
                    && weatherFits(def.weather(), w.weather(), w.aurora()) && w.depth() >= def.deep()
                    && (def.flag() == null || w.flags().contains(def.flag()))
                    && !(def.once() && w.landedOnce().contains(def.id()))) {
                out.add(def);
            }
        }
        return out;
    }

    /**
     * What bites: an item id, {@link #JUNK} now and then (always when nothing lives here now).
     * Deep casts favour the badniks: their weight is scaled by half plus the depth.
     */
    public String choose(Waters w, SnapshotRandom rng) {
        List<FishDef> options = candidates(w);
        int junk = w.spot() == FishDef.POND ? POND_JUNK : LAKE_JUNK;
        if (options.isEmpty() || rng.nextInt(100) < junk) {
            return JUNK;
        }
        int[] weights = new int[options.size()];
        int total = 0;
        for (int i = 0; i < options.size(); i++) {
            FishDef def = options.get(i);
            weights[i] = def.isBadnik() ? Math.max(1, def.weight() * (50 + w.depth()) / 100) : def.weight();
            total += weights[i];
        }
        int roll = rng.nextInt(total);
        for (int i = 0; i < options.size(); i++) {
            roll -= weights[i];
            if (roll < 0) {
                return options.get(i).id();
            }
        }
        return options.get(options.size() - 1).id();
    }

    /** A fish only (no junk, no badnik): what Rocky brings up from the pond, or null when none bite. */
    public String chooseFish(Waters w, SnapshotRandom rng) {
        List<FishDef> options = new ArrayList<>();
        int total = 0;
        for (FishDef def : candidates(w)) {
            if (!def.isBadnik()) {
                options.add(def);
                total += def.weight();
            }
        }
        if (total == 0) {
            return null;
        }
        int roll = rng.nextInt(total);
        for (FishDef def : options) {
            roll -= def.weight();
            if (roll < 0) {
                return def.id();
            }
        }
        return options.get(options.size() - 1).id();
    }

    /** The name of a season mask, for the collection page ("SPRING/FALL"). */
    public static String seasons(int mask) {
        if (mask == ALL) {
            return "ALL YEAR";
        }
        StringBuilder out = new StringBuilder();
        for (int s = 0; s < 4; s++) {
            if ((mask & (1 << s)) != 0) {
                out.append(out.isEmpty() ? "" : "/").append(Calendar.seasonName(s));
            }
        }
        return out.toString();
    }
}
