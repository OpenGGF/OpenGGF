package starpost.ruins;

import com.openggf.mods.state.SnapshotRandom;
import java.util.List;

/**
 * The hand-built chambers, one every fifth, each with a Star Post elevator and a find in a monitor
 * (design doc §6.5, §7). Each is a window of a stock act chosen by eye from the kit surveys (Sonic
 * 1's own rooms, uncut), with its water line, and a point the find's monitor is placed nearest to.
 * Doorways, rings and badniks are placed by the traversal check as in every chamber, so the
 * elevator and the find are always reachable; a kit without the window (a test's) falls back to a
 * generated chamber with the same elevator and finds.
 */
final class Landmarks {
    /**
     * One landmark: its title, the act and window (columns and rows of the stock layout), the
     * water line ({@link Chamber#NO_WATER}, a row, or -64 for flooded), and its finds with the
     * point each monitor should sit nearest to.
     */
    record Spec(int number, String name, int act, int column, int row, int cols, int rows, int water,
            int entryX, int entryY, String[] prizes, int[] hints) {
    }

    private final ChamberGen.Kits kits;

    Landmarks(ChamberGen.Kits kits) {
        this.kits = kits;
    }

    /** The eight landmarks (windows from the S1 kit surveys of 2026-10-09). */
    static Spec spec(int number) {
        int dry = Chamber.NO_WATER;
        return switch (number) {
            // MZ2: grassy pillars stepping up to a ruined temple.
            case 5 -> new Spec(5, "THE BROKEN TEMPLE", 1, 11, 1, 4, 2, dry, 96, 270,
                    new String[] {"lava_ruby"}, new int[] {900, 190});
            // MZ1 underground: the long hall under the battlements. Pud's seed is at the far end (§7).
            case 10 -> new Spec(10, "THE BATTLEMENTS", 0, 9, 3, 4, 2, dry, 310, 320,
                    new String[] {"super_sunflower_seeds"}, new int[] {960, 220});
            // MZ3: a stepped mound above a pillared shrine, a lava pool to the west.
            case 15 -> new Spec(15, "THE PILLARED SHRINE", 2, 18, 5, 4, 2, dry, 575, 64,
                    new String[] {"record_marble"}, new int[] {640, 448});
            // LZ1: crystal beds, the lower galleries flooded.
            case 20 -> new Spec(20, "THE CRYSTAL GALLERY", 0, 19, 2, 4, 2, 300, 64, 128,
                    new String[] {"tide_sapphire"}, new int[] {350, 380});
            // LZ3: the current's tunnel, under water end to end.
            case 25 -> new Spec(25, "THE DROWNED TUNNEL", 2, 14, 3, 4, 2, -64, 80, 250,
                    new String[] {"record_labyrinth"}, new int[] {520, 300});
            // LZ3: golden stairs and poles above the water.
            case 30 -> new Spec(30, "THE GOLDEN STAIR", 2, 7, 3, 4, 2, 400, 330, 190,
                    new String[] {"emerald_shard"}, new int[] {330, 160});
            // SBZ2: platforms over the factory floor.
            case 35 -> new Spec(35, "THE CONVEYOR HALL", 1, 27, 4, 4, 2, dry, 100, 100,
                    new String[] {"record_invincible"}, new int[] {700, 440});
            // SBZ1: the sealed depths; nothing goes further down yet (Scrap Brain Depths, §6.5).
            case 40 -> new Spec(40, "THE SEALED DEPTHS", 0, 20, 1, 3, 2, dry, 200, 165,
                    new String[] {"record_final", "record_boss"}, new int[] {320, 340, 640, 400});
            default -> null;
        };
    }

    Chamber build(int number, long seed) {
        Spec spec = spec(number);
        if (spec == null) {
            return null;
        }
        int band = RuinsRules.band(number);
        int zone = RuinsRules.zone(band);
        Kit kit = kits.kit(zone, spec.act());
        if (kit == null || spec.column() + spec.cols() > kit.columns() || spec.row() + spec.rows() > kit.rows()) {
            return null;
        }
        int[] cells = new int[spec.cols() * spec.rows()];
        for (int r = 0; r < spec.rows(); r++) {
            for (int c = 0; c < spec.cols(); c++) {
                cells[c + r * spec.cols()] = kit.block(spec.column() + c, spec.row() + r);
            }
        }
        Chamber chamber = new Chamber(number, zone, spec.act(), kit, spec.cols(), spec.rows(), cells);
        chamber.landmark = spec.name();
        if (band == RuinsRules.MARBLE) {
            chamber.findLava();
        }
        chamber.waterY = spec.water();
        Reach reach = new Reach(chamber);
        int bestEntry = -1;
        int bestReached = 0;
        // The hand-picked entry, unless the generator's own picks open far more of the room.
        int hinted = -1;
        // The nearest safe arrival to the hand-picked entry (level floor either side, no lava a step away).
        for (boolean needFooting : new boolean[] {true, false}) {
            for (int i = 0; i < reach.spotCount(); i++) {
                if (!reach.hazard(i) && (!needFooting || reach.footing(i, ChamberGen.ARRIVAL_FOOTING))
                        && (hinted < 0 || Math.abs(reach.x(i) - spec.entryX()) + Math.abs(reach.y(i) - spec.entryY())
                        < Math.abs(reach.x(hinted) - spec.entryX()) + Math.abs(reach.y(hinted) - spec.entryY()))) {
                    hinted = i;
                }
            }
            if (hinted >= 0) {
                break;
            }
        }
        int hintedReached = 0;
        if (hinted >= 0) {
            reach.explore(hinted);
            hintedReached = reach.reachedCount();
        }
        for (int entry : ChamberGen.entries(reach)) {
            reach.explore(entry);
            if (reach.reachedCount() > bestReached) {
                bestReached = reach.reachedCount();
                bestEntry = entry;
            }
        }
        if (hinted >= 0 && hintedReached >= 24 && hintedReached * 2 >= bestReached) {
            bestEntry = hinted;
            bestReached = hintedReached;
        }
        if (bestEntry < 0 || bestReached < 12) {
            return null;
        }
        reach.explore(bestEntry);
        SnapshotRandom rng = new SnapshotRandom(seed);
        new ChamberGen(kits).furnish(chamber, reach, bestEntry, rng);
        return chamber;
    }

    /** A landmark's finds (also used when its chamber had to be generated). */
    static List<String> prizes(int number) {
        Spec spec = spec(number);
        return spec == null ? List.of() : List.of(spec.prizes());
    }

    /** Where a landmark's find should sit, or null for anywhere. */
    static int[] hint(int number, int prize) {
        Spec spec = spec(number);
        if (spec == null || spec.hints().length < prize * 2 + 2) {
            return null;
        }
        return new int[] {spec.hints()[prize * 2], spec.hints()[prize * 2 + 1]};
    }
}
