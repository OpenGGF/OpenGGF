package infinite;

/**
 * Seeded ring trails: four-ring rows or hop arcs on walkable approaches, arcs over corridor
 * pits and rows above stepping stones. All spacing and shapes are mod design choices.
 */
public final class RingPlan {
    public static final int COUNT = 4;
    public static final int SPACING = 24;
    public static final int CLEARANCE = 24;
    /** Arc rings rise from 40px to about 94px above the higher bank, tracing a held jump. */
    public static final int ARC_BASE = 40;
    public static final int ARC_RISE = 56;
    /** Rings above a stone are spaced closer so a 64px platform holds two. */
    public static final int STONE_SPACING = 20;
    /** Hop arcs lift the middle rings this far above a plain row: a short jump collects them. */
    public static final int HOP_RISE = 28;

    /** Ring centres, at most {@link #COUNT}; index order is stable for collection tracking. */
    public record Row(long[] x, int[] y) {
        public int count() { return x.length; }
    }

    public static Row at(TerrainLibrary terrain, long section) {
        if (section < 0) return null;
        long random = TerrainLibrary.random(section + TerrainLibrary.SEED + 0x52494e4753L);
        // The opening trail teaches collection; thereafter leave a third of sections empty.
        if (section != 0 && Long.remainderUnsigned(random, 3) == 0) return null;
        var stones = PlatformPlan.at(terrain, section);
        if (stones.length > 0) {
            // A high route's monitor stands on its last platform; the rings take the others.
            var monitor = RoutePlan.has(terrain, section) ? MonitorPlan.at(terrain, section) : null;
            if (monitor != null) stones = java.util.Arrays.copyOf(stones, stones.length - 1);
            return aboveStones(stones);
        }
        int gap = terrain.gapWidth(section);
        if (gap > 0) return arc(terrain, section, gap);
        // Try either approach before the middle, keeping rings away from centred gaps.
        int preferred = (random & 1) == 0 ? 64 : 368;
        for (int offset : new int[] { preferred, preferred == 64 ? 368 : 64, 208 }) {
            long start = section * 512 + offset;
            boolean safe = true;
            for (int dx = -12; dx <= (COUNT - 1) * SPACING + 12; dx++) {
                if (terrain.floorAt(start + dx) < 0) { safe = false; break; }
            }
            if (!safe) continue;
            long[] x = new long[COUNT];
            int[] y = new int[COUNT];
            // A third of rows are hop arcs: the middle pair rises out of running reach.
            boolean hop = Long.remainderUnsigned(random >>> 40, 3) == 0;
            for (int i = 0; i < COUNT; i++) {
                x[i] = start + (long) i * SPACING;
                y[i] = terrain.floorAt(x[i]) - CLEARANCE - (hop && i > 0 && i < COUNT - 1 ? HOP_RISE : 0);
            }
            return new Row(x, y);
        }
        return null;
    }

    /** Four rings over a centred corridor pit, highest in the middle like the jump that clears it. */
    private static Row arc(TerrainLibrary terrain, long section, int gap) {
        long start = section * 512 + (512 - gap) / 2;
        int bank = Math.min(terrain.floorAt(start - 1), terrain.floorAt(start + gap));
        long[] x = new long[COUNT];
        int[] y = new int[COUNT];
        // The arc overhangs each bank by 32px so even a 64px pit spaces its rings apart.
        int span = gap + 64;
        for (int i = 0; i < COUNT; i++) {
            x[i] = start - 32 + (long) span * (i + 1) / (COUNT + 1);
            double t = (x[i] - start - gap / 2.0) / (span / 2.0);
            y[i] = bank - ARC_BASE - (int) Math.round(ARC_RISE * (1 - t * t));
        }
        return new Row(x, y);
    }

    /** Up to four rings shared between the stones, a centred row above each. */
    private static Row aboveStones(PlatformPlan.Stone[] stones) {
        int each = Math.max(1, COUNT / stones.length);
        var x = new java.util.ArrayList<Long>();
        var y = new java.util.ArrayList<Integer>();
        for (var stone : stones) {
            int fit = Math.min(each, 1 + stone.halfWidth() * 2 / STONE_SPACING / 2);
            for (int i = 0; i < fit && x.size() < COUNT; i++) {
                x.add(stone.worldX() + (2L * i - (fit - 1)) * STONE_SPACING / 2);
                y.add(stone.surface() - CLEARANCE);
            }
        }
        return new Row(x.stream().mapToLong(Long::longValue).toArray(), y.stream().mapToInt(Integer::intValue).toArray());
    }

    private RingPlan() { }
}
