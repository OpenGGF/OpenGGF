package starpost.orchard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import starpost.core.Calendar;
import starpost.core.Catalog;
import starpost.core.Farm;
import starpost.core.Game;
import starpost.core.Plot;
import starpost.core.SaveSection;

/**
 * The farm's trees (design doc §6.3): long-term investments planted from saplings on a plot and
 * grown overnight. Engine-free, so every tree's rule is tested without a ROM. Each tree has a
 * rule of its own (pillar 3):
 * <ul>
 *   <li><b>Green Hill Palm</b> (14 days): a coconut every other day in summer and fall, three at
 *       most; a stormy day shakes two more loose in any season it storms (five at most).</li>
 *   <li><b>Ring Fruit Tree</b> (28 days): ten rings a day from spring to fall, thirty at most.
 *       They pay out as rings, not fruit: shaking the tree bursts them out like a hit's
 *       scattered rings, and what is not caught before they blink out is gone ({@link RingBurst}).</li>
 *   <li><b>Chaos Cherry</b> (21 days): the only tree that bears in winter. Each morning it bears
 *       a cherry with the chance of the valley's population out of sixty: the more animals are
 *       free, the more it gives (four at most).</li>
 * </ul>
 * A tree stands on its plot as the sapling item's placed object, so the farm's own save keeps
 * where it stands; this section keeps its age and what hangs on it, keyed "row.column". Trees
 * need no water and grow in every season.
 */
public final class Orchard implements SaveSection {
    public static final String PREFIX = "orchard";
    /** The saplings (placeable items) and so the trees' kinds. */
    public static final String PALM = "palm_sapling";
    public static final String RING_FRUIT = "ring_fruit_sapling";
    public static final String CHAOS_CHERRY = "chaos_cherry_sapling";
    /** What the trees bear. */
    public static final String COCONUT = "palm_coconut";
    public static final String CHERRY = "chaos_cherry";
    public static final int PALM_DAYS = 14;
    public static final int RING_FRUIT_DAYS = 28;
    public static final int CHAOS_CHERRY_DAYS = 21;
    public static final int RINGS_A_DAY = 10;
    /** The story flag that offers the Chaos Cherry's recipe at Tails's workshop (the museum's minerals set it). */
    public static final String CHERRY_FLAG = "orchard_chaos_cherry";
    public static final int STORM_COCONUTS = 2;

    /** One tree: its kind (the sapling's id), nights grown and what hangs on it (rings for the Ring Fruit Tree). */
    public static final class Tree {
        public final String kind;
        public int age;
        public int fruit;

        Tree(String kind) {
            this.kind = kind;
        }

        public boolean grown() {
            return age >= days(kind);
        }

        /** The picture to draw: 0 sapling, 1 young, 2 grown. */
        public int stage() {
            return Orchard.stage(kind, age);
        }
    }

    /** What picking a tree gave: an item and count, or rings to burst out ({@code item} null). */
    public record Pick(String item, int count) {
        public boolean rings() {
            return item == null;
        }
    }

    private final Map<String, Tree> trees = new LinkedHashMap<>();

    public static boolean isTree(String id) {
        return PALM.equals(id) || RING_FRUIT.equals(id) || CHAOS_CHERRY.equals(id);
    }

    /** Nights from planting to grown. */
    public static int days(String kind) {
        return switch (kind) {
            case PALM -> PALM_DAYS;
            case RING_FRUIT -> RING_FRUIT_DAYS;
            default -> CHAOS_CHERRY_DAYS;
        };
    }

    /** The most a tree holds before it stops bearing. */
    public static int cap(String kind, boolean storm) {
        return switch (kind) {
            case PALM -> storm ? 5 : 3;
            case RING_FRUIT -> 3 * RINGS_A_DAY;
            default -> 4;
        };
    }

    /** 0 sapling (the first half), 1 young, 2 grown. */
    public static int stage(String kind, int age) {
        int days = days(kind);
        return age >= days ? 2 : age * 2 >= days ? 1 : 0;
    }

    /** The tree on a plot, or null; a sapling placed today gets its record here. Keeps records honest with the field. */
    public Tree tree(Game game, int row, int column) {
        Plot plot = game.farm.plot(row, column);
        String key = row + "." + column;
        if (plot == null || !isTree(plot.object)) {
            return null;
        }
        Tree tree = trees.get(key);
        if (tree == null || !tree.kind.equals(plot.object)) {
            tree = new Tree(plot.object);
            trees.put(key, tree);
        }
        return tree;
    }

    /** Every tree standing on the field, as {row, column} pairs. */
    public List<int[]> standing(Game game) {
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r < Farm.ROWS; r++) {
            for (int c = 0; c < Farm.COLUMNS; c++) {
                if (tree(game, r, c) != null) {
                    out.add(new int[] {r, c});
                }
            }
        }
        return out;
    }

    /** Drops records for trees no longer standing (knocked loose) and adds ones for new saplings. */
    void reconcile(Game game) {
        trees.keySet().removeIf(key -> {
            String[] rc = key.split("\\.");
            Plot plot = game.farm.raw(Integer.parseInt(rc[0]), Integer.parseInt(rc[1]));
            return plot == null || !trees.get(key).kind.equals(plot.object);
        });
        standing(game);
    }

    /** Overnight, after the crops: every tree grows a night, then the grown ones bear by their own rule. */
    @Override
    public void nextDay(Game game) {
        reconcile(game);
        for (Tree tree : trees.values()) {
            if (!tree.grown()) {
                tree.age++;
                continue;
            }
            bear(game, tree);
        }
    }

    /** Today's crop for a grown tree; {@code game} has already moved on to the new morning and its weather. */
    static void bear(Game game, Tree tree) {
        int season = game.calendar.season();
        switch (tree.kind) {
            case PALM -> {
                boolean storm = game.weather == Game.STORM;
                if ((season == Calendar.SUMMER || season == Calendar.FALL) && game.calendar.dayNumber() % 2 == 0) {
                    tree.fruit = Math.max(tree.fruit, Math.min(cap(PALM, false), tree.fruit + 1));
                }
                if (storm) {
                    tree.fruit = Math.min(cap(PALM, true), tree.fruit + STORM_COCONUTS);
                }
            }
            case RING_FRUIT -> {
                if (season != Calendar.WINTER) {
                    tree.fruit = Math.min(cap(RING_FRUIT, false), tree.fruit + RINGS_A_DAY);
                }
            }
            default -> {
                if (game.rng.nextInt(Game.MAX_POPULATION) < game.population) {
                    tree.fruit = Math.min(cap(CHAOS_CHERRY, false), tree.fruit + 1);
                }
            }
        }
    }

    /**
     * Picks a tree with the action button: coconuts and cherries go into the monitor slots (what
     * does not fit stays on the tree); the Ring Fruit Tree's rings are shaken out to be caught.
     * Null when there is nothing to pick.
     */
    public Pick pick(Game game, int row, int column) {
        Tree tree = tree(game, row, column);
        if (tree == null || !tree.grown() || tree.fruit <= 0) {
            return null;
        }
        if (tree.kind.equals(RING_FRUIT)) {
            int rings = tree.fruit;
            tree.fruit = 0;
            return new Pick(null, rings);
        }
        String item = tree.kind.equals(PALM) ? COCONUT : CHERRY;
        int left = game.inventory.add(game.item(item), tree.fruit);
        int got = tree.fruit - left;
        tree.fruit = left;
        return got > 0 ? new Pick(item, got) : null;
    }

    // ------------------------------------------------------------------ save

    @Override
    public String prefix() {
        return PREFIX;
    }

    @Override
    public void save(Map<String, String> out) {
        for (Map.Entry<String, Tree> e : trees.entrySet()) {
            Tree t = e.getValue();
            out.put("tree." + e.getKey(), t.kind + "," + t.age + "," + t.fruit);
        }
    }

    /**
     * Unknown kinds and positions off the field are dropped; ages and fruit are clamped. Whether a
     * tree still stands is checked against the field each time it is asked for.
     */
    @Override
    public void load(Map<String, String> in, Catalog catalog) {
        trees.clear();
        for (Map.Entry<String, String> e : in.entrySet()) {
            if (!e.getKey().startsWith("tree.")) {
                continue;
            }
            String[] key = e.getKey().substring(5).split("\\.");
            String[] parts = e.getValue().split(",");
            if (key.length != 2 || parts.length != 3) {
                continue;
            }
            int row = Integer.parseInt(key[0]), column = Integer.parseInt(key[1]);
            if (!isTree(parts[0]) || row < 0 || row >= Farm.ROWS || column < 0 || column >= Farm.COLUMNS) {
                continue;
            }
            Tree tree = new Tree(parts[0]);
            tree.age = Math.max(0, Math.min(days(tree.kind), Integer.parseInt(parts[1])));
            tree.fruit = Math.max(0, Math.min(cap(tree.kind, true), Integer.parseInt(parts[2])));
            trees.put(row + "." + column, tree);
        }
    }
}
