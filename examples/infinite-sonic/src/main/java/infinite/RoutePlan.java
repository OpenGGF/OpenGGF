package infinite;

/**
 * Seeded high routes: a ledge of three of the act's own stock platforms hung above an open
 * section, with rings and often a monitor up top while a ground badnik patrols below. One jump
 * takes the high line; the platforms are close enough to run straight across, and missing them
 * only drops Sonic back onto the ground route. Every constant here is a mod design choice.
 */
public final class RoutePlan {
    public static final int FIRST_SECTION = 6;
    /** One in this many eligible sections carries a high route. */
    public static final int ODDS = 5;
    /** Platform surface above the highest floor beneath. A held jump rises about 96px on level
     * ground but only about 70px from a 30-degree hill, whose tilt turns part of it forward; with
     * {@link #MAX_RELIEF} the ledge is at most 64px above any floor in the section. */
    public static final int RISE = 48;
    public static final int MAX_RELIEF = 16;
    public static final int COUNT = 3;
    /** Space between platforms: running off one at 4px/frame drops about 4px before the next, well
     * inside PlatformObject's 16px catch, so Sonic runs across without jumping. */
    public static final int GAP = 24;

    /** The route's platforms in this section, left to right, or none. */
    public static PlatformPlan.Stone[] at(TerrainLibrary terrain, long section) {
        return terrain.memo(TerrainLibrary.ROUTE_PLAN, section, () -> plan(terrain, section)).clone();
    }

    private static PlatformPlan.Stone[] plan(TerrainLibrary terrain, long section) {
        var none = new PlatformPlan.Stone[0];
        if (section < FIRST_SECTION || terrain.isCorridor(section) || terrain.platformRun(section)) return none;
        // Stationary kinds only: a falling platform would drop a rider onto the badnik below.
        var kinds = terrain.platformKinds().stream().filter(k -> !k.falls()).toList();
        if (kinds.isEmpty()) return none;
        long random = TerrainLibrary.random(section + TerrainLibrary.SEED + 0x524f555445L);
        if (Long.remainderUnsigned(random, ODDS) != 0) return none;
        int high = Integer.MAX_VALUE;
        int low = Integer.MIN_VALUE;
        for (int x = 0; x < 512; x += 4) {
            int floor = terrain.floorAt(section * 512 + x);
            if (floor < 0) return none;
            high = Math.min(high, floor);
            low = Math.max(low, floor);
        }
        if (low - high > MAX_RELIEF) return none;
        var kind = kinds.get((int) Long.remainderUnsigned(random >>> 16, kinds.size()));
        int surface = high - RISE;
        var stones = new PlatformPlan.Stone[COUNT];
        int width = kind.halfWidth() * 2;
        long left = section * 512 + 256 - (COUNT * width + (COUNT - 1) * GAP) / 2;
        for (int i = 0; i < COUNT; i++) {
            long x = left + kind.halfWidth() + (long) i * (width + GAP);
            stones[i] = new PlatformPlan.Stone(x, surface + CoursePlatforms.SURFACE_OFFSET, kind.objectId(),
                    kind.subtype(), kind.halfWidth());
        }
        return stones;
    }

    /** True when this section carries a high route. */
    public static boolean has(TerrainLibrary terrain, long section) {
        return at(terrain, section).length > 0;
    }

    private RoutePlan() { }
}
