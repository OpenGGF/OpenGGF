package starpost.museum;

import java.util.ArrayList;
import java.util.List;
import starpost.core.Catalog;

/**
 * Tails's Workshop Museum's three collections (design doc §6.8) and their milestones. Built per
 * catalogue: an item a system has not registered is left out (as the Capsule does), and a
 * milestone never asks for more than its collection holds. The Sound Test is the Lamppost Inn's
 * jukebox; the museum only shows its Records and points there.
 */
public final class Exhibits {
    public static final String MINERALS = "minerals";
    public static final String SCRAP = "scrap";
    public static final String RELICS = "relics";
    /** Set by the minerals' second milestone: Tails can grow Chaos Cherry saplings. */
    public static final String CHERRY_FLAG = starpost.orchard.Orchard.CHERRY_FLAG;

    /**
     * A milestone's reward: rings, an item (a Record also opens its jukebox song), a story flag.
     *
     * @param count donations needed
     * @param text  what the page shows, e.g. "800 RINGS"
     */
    public record Milestone(int count, int rings, String item, int items, String flag, String text) {
    }

    /** One collection: its items in display order and its milestones in order. */
    public record Exhibit(String id, String name, List<String> items, List<Milestone> milestones) {
        public boolean holds(String id) {
            return items.contains(id);
        }
    }

    private Exhibits() {
    }

    /** The collections the catalogue can fill, each with at least one item. */
    public static List<Exhibit> all(Catalog catalog) {
        List<Exhibit> out = new ArrayList<>();
        List<String> minerals = known(catalog, List.of("marble_ore", "lava_ruby", "tide_sapphire", "spark_topaz",
                "emerald_shard", "marble_geode", "tide_geode", "scrap_geode"));
        add(out, catalog, new Exhibit(MINERALS, "MINERALS", minerals, List.of(
                new Milestone(3, 800, null, 0, null, "800 RINGS"),
                new Milestone(6, 0, "chaos_cherry_sapling", 1, CHERRY_FLAG, "CHERRY SAPLING AND ITS RECIPE"),
                new Milestone(minerals.size(), 0, "record_lava_reef", 1, null, "RECORD: LAVA REEF"))));
        List<String> scrap = new ArrayList<>(MuseumContent.parts());
        scrap.add(scrap.indexOf("burrobot_drill"), "jaws_fin");
        scrap.addAll(List.of("chopper_shell", "red_chopper_shell", "jawz_torpedo", "blastoid_cannon"));
        scrap = known(catalog, scrap);
        add(out, catalog, new Exhibit(SCRAP, "SCRAP COLLECTION", scrap, List.of(
                new Milestone(4, 0, "caterkiller_crawler", 1, null, "A CATERKILLER CRAWLER"),
                new Milestone(9, 2500, null, 0, null, "2500 RINGS"),
                new Milestone(scrap.size(), 0, "record_mini_boss", 1, null, "RECORD: MINI-BOSS"))));
        List<String> relics = known(catalog, MuseumContent.relics());
        add(out, catalog, new Exhibit(RELICS, "RELICS", relics, List.of(
                new Milestone(3, 500, null, 0, null, "500 RINGS"),
                new Milestone(6, 0, "star_post", 2, null, "2 STAR POSTS"),
                new Milestone(relics.size(), 0, "record_sandopolis", 1, null, "RECORD: SANDOPOLIS"))));
        return out;
    }

    /** The exhibit holding an item, or null. */
    public static Exhibit of(Catalog catalog, String id) {
        for (Exhibit e : all(catalog)) {
            if (e.holds(id)) {
                return e;
            }
        }
        return null;
    }

    private static List<String> known(Catalog catalog, List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (catalog.hasItem(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * Adds a collection with its milestones fitted to what it holds: counts clamped to its size,
     * rewards whose item is unknown dropped, and no two milestones at the same count.
     */
    private static void add(List<Exhibit> out, Catalog catalog, Exhibit e) {
        if (e.items().isEmpty()) {
            return;
        }
        List<Milestone> fitted = new ArrayList<>();
        int last = 0;
        for (Milestone m : e.milestones()) {
            int count = Math.max(1, Math.min(e.items().size(), m.count()));
            if (count <= last || m.item() != null && !catalog.hasItem(m.item())) {
                continue;
            }
            fitted.add(new Milestone(count, m.rings(), m.item(), m.items(), m.flag(), m.text()));
            last = count;
        }
        out.add(new Exhibit(e.id(), e.name(), List.copyOf(e.items()), List.copyOf(fitted)));
    }
}
