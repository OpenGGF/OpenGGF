package starpost.ruins;

import java.util.Map;
import starpost.core.Catalog;
import starpost.core.Game;
import starpost.core.SaveSection;

/**
 * What the save remembers about the Ruins (keys under {@code ruins.}): the deepest Star Post
 * elevator reached, the seed the chambers are built from, the badniks popped and animals freed
 * there (the valley population's Ruins share), the deepest chamber ever entered, and whether Pud's
 * seed has been found. Records found are story flags ({@link RuinsContent#recordFlag}).
 */
public final class RuinsSection implements SaveSection {
    /** Set by eating a Fire Shield Pepper: lava does not hurt until the next morning. */
    public static final String LAVA_FLAG = "ruins.lava_immune";

    /** 0, or the deepest elevator chamber whose Star Post has been touched (5, 10, ... 40). */
    public int deepest;
    /** Chamber layouts derive from this, the day and the chamber number; 0 until first visit. */
    public long seed;
    public int popped;
    public int freed;
    public int bestChamber;
    public boolean seedFound;

    @Override
    public String prefix() {
        return "ruins";
    }

    @Override
    public void save(Map<String, String> out) {
        out.put("deepest", Integer.toString(deepest));
        out.put("seed", Long.toString(seed));
        out.put("popped", Integer.toString(popped));
        out.put("freed", Integer.toString(freed));
        out.put("best", Integer.toString(bestChamber));
        out.put("seedFound", seedFound ? "1" : "0");
    }

    @Override
    public void load(Map<String, String> in, Catalog catalog) {
        int d = Integer.parseInt(in.getOrDefault("deepest", "0"));
        deepest = Math.max(0, Math.min(RuinsRules.CHAMBERS, d - Math.floorMod(d, 5)));
        seed = Long.parseLong(in.getOrDefault("seed", "0"));
        popped = Math.max(0, Math.min(999_999, Integer.parseInt(in.getOrDefault("popped", "0"))));
        freed = Math.max(0, Math.min(999_999, Integer.parseInt(in.getOrDefault("freed", "0"))));
        bestChamber = Math.max(0, Math.min(RuinsRules.CHAMBERS, Integer.parseInt(in.getOrDefault("best", "0"))));
        seedFound = "1".equals(in.get("seedFound"));
    }

    /** The Fire Shield Pepper wears off overnight. */
    @Override
    public void nextDay(Game game) {
        game.flags.remove(LAVA_FLAG);
    }

    /** Touching an elevator's Star Post. Returns true when it is a new deepest. */
    public boolean reachElevator(int chamber) {
        if (RuinsRules.landmark(chamber) && chamber > deepest) {
            deepest = chamber;
            return true;
        }
        return false;
    }
}
