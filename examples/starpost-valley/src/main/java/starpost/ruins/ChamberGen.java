package starpost.ruins;

import com.openggf.mods.state.SnapshotRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a chamber from a zone's stock blocks (design doc §6.5). A window of an act's layout,
 * two to four blocks wide and one or two high, is copied with its own collision; now and then a
 * column jumps to another part of the act where every row of the new pair of neighbours also
 * sits side by side in the stock layout, and any seam row that still disagrees is repaired with a
 * block seen next to both neighbours (Eggman's Sky's remixer, at chamber scale). Windows that are
 * mostly air or mostly pit are refused.
 *
 * <p>Then the chamber is played ({@link Reach}) from its entry, a spring is added where it opens
 * more of the room, and the exit goes on the reached spot farthest from the entry, so it is
 * always reachable. Rings, rocks, badniks, spikes and air bubbles go only on reached spots, and
 * a spike that would cut the route to the exit is taken out again. Everything comes from one seed,
 * so a chamber is the same all day.
 */
public final class ChamberGen {
    /** Where kits come from: the ROM's ({@link RuinsArt}) or a test's. */
    public interface Kits {
        /** The zone's act kit, or null when unavailable. */
        Kit kit(int zone, int act);
    }

    private static final int ATTEMPTS = 30;
    private static final int GOOD_SPOTS = 32;

    private final Kits kits;

    public ChamberGen(Kits kits) {
        this.kits = kits;
    }

    /** The acts a band's chambers are cut from (Scrap Brain act 3 is Labyrinth-built, so not used). */
    public static int[] acts(int band) {
        return band == RuinsRules.SCRAP_BRAIN ? new int[] {0, 1} : new int[] {0, 1, 2};
    }

    public Chamber generate(int number, long seed) {
        if (RuinsRules.landmark(number)) {
            Chamber landmark = new Landmarks(kits).build(number, seed);
            if (landmark != null) {
                return landmark;
            }
        }
        Chamber chamber = procedural(number, seed);
        for (int retry = 1; chamber == null && retry <= 3; retry++) {
            chamber = procedural(number, seed * 31 + retry);
        }
        return chamber;
    }

    /** A chamber cut from the stock layout. Never null while the band has a kit. */
    public Chamber procedural(int number, long seed) {
        SnapshotRandom rng = new SnapshotRandom(seed);
        int band = RuinsRules.band(number);
        int zone = RuinsRules.zone(band);
        int[] acts = acts(band);
        Chamber best = null;
        Reach bestReach = null;
        int bestEntry = -1;
        int bestScore = -1;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int act = acts[rng.nextInt(acts.length)];
            Kit kit = kits.kit(zone, act);
            if (kit == null) {
                continue;
            }
            int w = 2 + rng.nextInt(3);
            int h = w <= 3 && rng.nextInt(100) < 35 ? 2 : 1;
            int[] cells = window(kit, w, h, rng);
            if (cells == null) {
                continue;
            }
            Chamber chamber = new Chamber(number, zone, act, kit, w, h, cells);
            if (band == RuinsRules.MARBLE) {
                chamber.findLava();
            }
            if (band == RuinsRules.LABYRINTH) {
                flood(chamber, rng);
            }
            Reach reach = new Reach(chamber);
            boolean good = false;
            for (int entry : entries(reach)) {
                reach.explore(entry);
                int far = farthest(reach, entry);
                int score = reach.reachedCount() + 2 * reachedColumns(reach) + far / 4;
                if (medianReachedY(reach) < 100) {
                    score /= 3;   // play hugging the top edge: the room above was cut off
                }
                if (score > bestScore) {
                    best = chamber;
                    bestReach = reach;
                    bestEntry = entry;
                    bestScore = score;
                }
                if (reach.reachedCount() >= GOOD_SPOTS && far >= Math.max(200, chamber.width * 45 / 100)
                        && medianReachedY(reach) >= 100) {
                    good = true;
                    break;
                }
            }
            if (good) {
                break;
            }
        }
        if (best == null) {
            return null;
        }
        bestReach.explore(bestEntry);
        furnish(best, bestReach, bestEntry, rng);
        return best;
    }

    /** The middle height of the reached spots. */
    private static int medianReachedY(Reach reach) {
        List<Integer> ys = new ArrayList<>();
        for (int i = 0; i < reach.spotCount(); i++) {
            if (reach.reached(i)) {
                ys.add(reach.y(i));
            }
        }
        if (ys.isEmpty()) {
            return 0;
        }
        java.util.Collections.sort(ys);
        return ys.get(ys.size() / 2);
    }

    /** How many spot columns have a reached spot: how much of the room's width is playable. */
    private static int reachedColumns(Reach reach) {
        java.util.BitSet columns = new java.util.BitSet();
        for (int i = 0; i < reach.spotCount(); i++) {
            if (reach.reached(i)) {
                columns.set(reach.x(i) / Reach.STEP);
            }
        }
        return columns.cardinality();
    }

    /** How far the farthest reached spot with room for the exit is from the entry, in pixels. */
    private static int farthest(Reach reach, int entry) {
        int far = 0;
        for (int i = 0; i < reach.spotCount(); i++) {
            if (reach.flat(i, Reach.STEP)) {
                far = Math.max(far, distance(reach, entry, i));
            }
        }
        return far;
    }

    static int distance(Reach reach, int a, int b) {
        double dx = reach.x(a) - reach.x(b), dy = reach.y(a) - reach.y(b);
        return (int) Math.sqrt(dx * dx + dy * dy);
    }

    // ------------------------------------------------------------------ the window

    /**
     * {@code w x h} blocks from the act's playable area: a start column, then runs of the stock
     * columns with an occasional seamless jump; null when the window is mostly air or pit.
     */
    static int[] window(Kit kit, int w, int h, SnapshotRandom rng) {
        // The whole layout, not just the act's opening camera area: Sonic 1 widens its bounds
        // with level events (Marble Zone's underground is outside the area at the start).
        int c0 = 0;
        int c1 = kit.columns() - 1;
        int r0 = 0;
        int r1 = kit.rows() - 1;
        if (c1 - c0 + 1 < w || r1 - r0 + 1 < h) {
            return null;
        }
        Set<Long> pairs = new HashSet<>();
        Set<Long> vertical = new HashSet<>();
        for (int r = 0; r < kit.rows(); r++) {
            for (int c = 0; c < kit.columns(); c++) {
                if (c + 1 < kit.columns()) {
                    pairs.add(pair(kit.block(c, r), kit.block(c + 1, r)));
                }
                if (r + 1 < kit.rows()) {
                    vertical.add(pair(kit.block(c, r), kit.block(c, r + 1)));
                }
            }
        }
        int row = r0 + rng.nextInt(r1 - r0 - h + 2);
        int[] columns = new int[w];
        columns[0] = c0 + rng.nextInt(c1 - c0 + 1);
        for (int i = 1; i < w; i++) {
            int prev = columns[i - 1];
            int next = prev + 1 <= c1 ? prev + 1 : -1;
            if (rng.nextInt(100) < 40 || next < 0) {
                List<Integer> jumps = new ArrayList<>();
                for (int b = c0; b <= c1; b++) {
                    if (Math.abs(b - (prev + 1)) >= 2 && seamless(kit, prev, b, row, h, pairs)) {
                        jumps.add(b);
                    }
                }
                if (!jumps.isEmpty()) {
                    next = jumps.get(rng.nextInt(jumps.size()));
                }
            }
            columns[i] = next < 0 ? c0 + rng.nextInt(c1 - c0 + 1) : next;
        }
        int[] cells = new int[w * h];
        for (int i = 0; i < w; i++) {
            for (int r = 0; r < h; r++) {
                cells[i + r * w] = kit.block(columns[i], row + r);
            }
        }
        repair(kit, cells, w, h, pairs, vertical);
        return acceptable(kit, cells, w, h) ? cells : null;
    }

    private static boolean seamless(Kit kit, int a, int b, int row, int h, Set<Long> pairs) {
        for (int r = row; r < row + h; r++) {
            int x = kit.block(a, r), y = kit.block(b, r);
            if (!(x == 0 && y == 0) && !pairs.contains(pair(x, y))) {
                return false;
            }
        }
        return true;
    }

    /** Swaps a seam cell whose neighbours never meet in the stock act for one that meets both. */
    private static void repair(Kit kit, int[] cells, int w, int h, Set<Long> pairs, Set<Long> vertical) {
        Set<Integer> used = new java.util.TreeSet<>();
        for (int r = 0; r < kit.rows(); r++) {
            for (int c = 0; c < kit.columns(); c++) {
                used.add(kit.block(c, r));
            }
        }
        for (int i = 1; i < w; i++) {
            for (int r = 0; r < h; r++) {
                int left = cells[i - 1 + r * w];
                int cur = cells[i + r * w];
                if (left == 0 && cur == 0 || pairs.contains(pair(left, cur))) {
                    continue;
                }
                int right = i + 1 < w ? cells[i + 1 + r * w] : -1;
                int up = r > 0 ? cells[i + (r - 1) * w] : -1;
                int down = r + 1 < h ? cells[i + (r + 1) * w] : -1;
                for (int x : used) {
                    if (pairs.contains(pair(left, x)) && (right < 0 || pairs.contains(pair(x, right)))
                            && (up < 0 || vertical.contains(pair(up, x)))
                            && (down < 0 || vertical.contains(pair(x, down)))) {
                        cells[i + r * w] = x;
                        break;
                    }
                }
            }
        }
    }

    /**
     * A window worth playing: at least a third of it solid, and floor under most of its width
     * (a column with nothing to stand on from top to bottom is a pit).
     */
    private static boolean acceptable(Kit kit, int[] cells, int w, int h) {
        int size = kit.size();
        long solid = 0;
        int floored = 0, columns = 0;
        for (int x = 4; x < w * size; x += 8) {
            columns++;
            boolean any = false;
            for (int r = 0; r < h && !any; r++) {
                byte[] mask = kit.solidity(cells[x / size + r * w]);
                if (mask == null) {
                    continue;
                }
                for (int y = 0; y < size; y += 4) {
                    if (mask[y * size + x % size] != Chamber.EMPTY) {
                        any = true;
                        break;
                    }
                }
            }
            if (any) {
                floored++;
            }
        }
        for (int i = 0; i < cells.length; i++) {
            byte[] mask = kit.solidity(cells[i]);
            if (mask == null) {
                continue;
            }
            for (int p = 0; p < mask.length; p += 61) {
                if (mask[p] != Chamber.EMPTY) {
                    solid++;
                }
            }
        }
        long samples = (long) cells.length * ((long) size * size / 61 + 1);
        return floored * 10 >= columns * 7 && solid * 100 >= samples * 8 && solid * 10 <= samples * 8;
    }

    private static long pair(int a, int b) {
        return ((long) a << 20) | (b & 0xFFFFF);
    }

    // ------------------------------------------------------------------ placing things

    /**
     * Entries worth trying: the highest floors near the left edge (Sonic drops in from the shaft
     * above), then the lowest, at most three, each a little apart.
     */
    static List<Integer> entries(Reach reach) {
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < reach.spotCount(); i++) {
            if (!reach.hazard(i) && reach.x(i) <= 160) {
                left.add(i);
            }
        }
        left.sort((a, b) -> reach.y(a) != reach.y(b) ? Integer.compare(reach.y(a), reach.y(b))
                : Integer.compare(reach.x(a), reach.x(b)));
        List<Integer> out = new ArrayList<>();
        for (int s : left) {
            boolean apart = true;
            for (int o : out) {
                apart &= Math.abs(reach.y(o) - reach.y(s)) > 24 || Math.abs(reach.x(o) - reach.x(s)) > 64;
            }
            if (apart) {
                out.add(s);
            }
            if (out.size() == 2) {
                break;
            }
        }
        if (!left.isEmpty() && !out.contains(left.get(left.size() - 1))) {
            out.add(left.get(left.size() - 1));
        }
        if (out.isEmpty()) {
            int any = chooseEntry(reach);
            if (any >= 0) {
                out.add(any);
            }
        }
        return out;
    }

    /** The entry: the highest floor near the left edge (Sonic drops in from the shaft above). */
    static int chooseEntry(Reach reach) {
        int best = -1;
        for (int i = 0; i < reach.spotCount(); i++) {
            if (reach.hazard(i) || reach.x(i) > 160) {
                continue;
            }
            if (best < 0 || reach.y(i) < reach.y(best) - 8 || Math.abs(reach.y(i) - reach.y(best)) <= 8 && reach.x(i) < reach.x(best)) {
                best = i;
            }
        }
        if (best < 0) {
            for (int i = 0; i < reach.spotCount(); i++) {
                if (!reach.hazard(i) && (best < 0 || reach.x(i) < reach.x(best))) {
                    best = i;
                }
            }
        }
        return best;
    }

    /** A flooded Labyrinth chamber: the water line somewhere in the room, or over all of it. */
    private static void flood(Chamber chamber, SnapshotRandom rng) {
        if (rng.nextInt(100) < 25) {
            chamber.waterY = -64;
        } else {
            chamber.waterY = chamber.height * (30 + rng.nextInt(50)) / 100;
        }
    }

    /** Springs, the exit, then rings, rocks, monitors, badniks, spikes and air. */
    void furnish(Chamber chamber, Reach reach, int entry, SnapshotRandom rng) {
        chamber.entryX = reach.x(entry);
        chamber.entryY = reach.y(entry);
        addSprings(chamber, reach, entry, rng);
        Placer place = new Placer(chamber, reach, entry, rng);
        place.exit();
        if (RuinsRules.landmark(chamber.number)) {
            place.elevator();
            place.prizes();
        }
        place.contents();
    }

    /** Where a yellow spring under an unreached ledge opens more of the room, keep it (two at most). */
    private static void addSprings(Chamber chamber, Reach reach, int entry, SnapshotRandom rng) {
        for (int n = 0; n < 2; n++) {
            int before = reach.reachedCount();
            List<int[]> candidates = new ArrayList<>();
            for (int s = 0; s < reach.spotCount(); s++) {
                if (!reach.reached(s) || reach.hazard(s) || Math.abs(reach.x(s) - reach.x(entry)) < 24
                        || chamber.hasThingNear(-1, reach.x(s), reach.y(s), 32)) {
                    continue;
                }
                for (int u = 0; u < reach.spotCount(); u++) {
                    int rise = reach.y(s) - reach.y(u);
                    if (!reach.reached(u) && Math.abs(reach.x(u) - reach.x(s)) <= 40 && rise > 56 && rise < 200
                            && clearAbove(chamber, reach.x(s), reach.y(s), reach.y(u) - 48)) {
                        candidates.add(new int[] {s, u});
                        break;
                    }
                }
            }
            if (candidates.isEmpty()) {
                return;
            }
            boolean kept = false;
            for (int tries = 0; tries < 4 && !candidates.isEmpty() && !kept; tries++) {
                int[] c = candidates.remove(rng.nextInt(candidates.size()));
                chamber.add(Chamber.SPRING, reach.x(c[0]), reach.y(c[0]), 0);
                reach.explore(entry);
                if (reach.reachedCount() >= before + Math.max(6, before / 7)) {
                    kept = true;
                } else {
                    chamber.things.remove(chamber.things.size() - 1);
                    reach.explore(entry);
                }
            }
            if (!kept) {
                return;
            }
        }
    }

    private static boolean clearAbove(Chamber chamber, int x, int fromY, int toY) {
        for (int y = fromY - 1; y >= Math.max(0, toY); y--) {
            if (chamber.ceiling(x, y)) {
                return false;
            }
        }
        return toY >= 0;
    }
}
