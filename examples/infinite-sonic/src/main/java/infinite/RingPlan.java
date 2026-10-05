package infinite;

/**
 * Seeded ring trails: four-ring rows or hop arcs on walkable approaches, arcs over corridor
 * pits, rows above stepping stones and a launch line rising off each spring. All spacing and
 * shapes are mod design choices.
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
    /** Launch-line rings sit on a spring flight 5, 9, 13 and 17 frames after launch. */
    private static final int LAUNCH_FIRST = 5;
    private static final int LAUNCH_STEP = 4;
    /** Launch-line horizontal speed, between the 0x400 minimum and the 0x540 course top speed. */
    private static final double LAUNCH_SPEED = 0x4a0 / 256.0;
    /** ROM gravity $38 per frame. */
    private static final double GRAVITY = 0x38 / 256.0;
    /** Sonic's standing radius: his centre at launch is 19px above the plate's floor. */
    private static final int STANDING_RADIUS = 19;

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
        if (stones.length > 0) return aboveStones(stones);
        var spring = SpringPlan.at(terrain, section - 1);
        if (spring != null) return launchLine(spring);
        int gap = terrain.gapWidth(section);
        if (gap > 0) return arc(terrain, section, gap);
        // Try either approach before the middle, keeping rings away from centred gaps.
        int preferred = (random & 1) == 0 ? 64 : 368;
        boolean springHere = SpringPlan.at(terrain, section) != null;
        for (int offset : new int[] { preferred, preferred == 64 ? 368 : 64, 208 }) {
            // Keep the late row off a launch spring's plate.
            if (springHere && offset == 368) continue;
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

    /** Four rings along the start of a spring flight, a steep line pointing up over the chasm. */
    private static Row launchLine(SpringPlan.Spring spring) {
        long[] x = new long[COUNT];
        int[] y = new int[COUNT];
        int launchY = spring.floor() - STANDING_RADIUS;
        double rising = -CourseSpring.STRENGTH / 256.0;
        for (int i = 0; i < COUNT; i++) {
            int t = LAUNCH_FIRST + LAUNCH_STEP * i;
            x[i] = spring.worldX() + Math.round(LAUNCH_SPEED * t);
            y[i] = launchY - (int) Math.round(rising * t - GRAVITY * t * t / 2);
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
