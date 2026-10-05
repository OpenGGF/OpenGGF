package infinite;

import java.util.ArrayList;

/**
 * Seeded high roads: a second path over the open sections of a stretch (4k to 4k+2), built from
 * the act's own stationary stock platforms. Steps climb from the ground, each at most 48px above
 * the last, to a level road 128px above the highest floor beneath it, which runs to the middle
 * of the stretch's third section. Rings and often a monitor ride the road while ground badniks
 * patrol the low path; missing a step only leaves Sonic on the low path. Every constant here is
 * a mod design choice.
 */
public final class RoutePlan {
    /** The first two stretches teach jumps and pits on the ground. */
    public static final int FIRST_STRETCH = 2;
    /** One in this many eligible stretches carries a high road. */
    public static final int ODDS = 2;
    /** Road surface above the highest floor beneath it: more than three Sonics tall. */
    public static final int ROAD_RISE = 128;
    /** A held jump rises about 96px on level ground and about 70px from a 30-degree hill. */
    public static final int STEP_RISE = 48;
    /** The floor along the whole route may vary by at most this much (so at most three steps). */
    public static final int MAX_RELIEF = 64;
    /** Road platforms sit 8px apart: Sonic runs straight across, and a landing cannot drop through
     * a gap (a 24px gap once caught a landing and dropped Sonic to the low path). A step's pair is flush. */
    public static final int GAP = 8;
    /** Between steps: a held jump at the course's top speed lands about 260px past its takeoff, so
     * with this gap a takeoff from anywhere on a 128px step lands on the next (at 4px/frame, from
     * its back half). At 100px a late landing left no full jump. */
    public static final int STEP_GAP = 120;
    /** Platforms narrower than this pair up into one step, leaving runway to land and take off again. */
    private static final int WIDE_STEP = 128;
    private static final int START_OFFSET = 96;
    private static final int END_OFFSET = 320;
    private static final int APPROACH = 160;
    private static final int MIN_ROAD = 3;

    /** All the stretch's route platforms, left to right; {@code road} is the index of the first road platform. */
    public record Route(PlatformPlan.Stone[] stones, int road) {
        public int roadSurface() { return stones[road].surface(); }
        public long roadEnd() { var last = stones[stones.length - 1]; return last.worldX() + last.halfWidth(); }
    }

    /** The route platforms whose centres lie in this section, left to right, or none. */
    public static PlatformPlan.Stone[] at(TerrainLibrary terrain, long section) {
        var route = route(terrain, Math.floorDiv(section, 4));
        if (route == null) return new PlatformPlan.Stone[0];
        var here = new ArrayList<PlatformPlan.Stone>();
        for (var stone : route.stones()) {
            if (Math.floorDiv(stone.worldX(), 512) == section) here.add(stone);
        }
        return here.toArray(PlatformPlan.Stone[]::new);
    }

    /** True for the open sections a high road runs over (4k to 4k+2 of a route stretch). */
    public static boolean has(TerrainLibrary terrain, long section) {
        return Math.floorMod(section, 4) != 3 && route(terrain, Math.floorDiv(section, 4)) != null;
    }

    public static Route route(TerrainLibrary terrain, long stretch) {
        return terrain.memo(TerrainLibrary.ROUTE_PLAN, stretch, () -> plan(terrain, stretch));
    }

    private static Route plan(TerrainLibrary terrain, long stretch) {
        if (stretch < FIRST_STRETCH) return null;
        for (long section = stretch * 4; section < stretch * 4 + 3; section++) {
            if (terrain.platformRun(section)) return null;
        }
        // Stationary kinds only: a falling platform would drop a rider onto the low path.
        var kinds = terrain.platformKinds().stream().filter(k -> !k.falls()).toList();
        if (kinds.isEmpty()) return null;
        long random = TerrainLibrary.random(stretch + TerrainLibrary.SEED + 0x524f555445L);
        if (Long.remainderUnsigned(random, ODDS) != 0) return null;
        long start = stretch * 4 * 512 + START_OFFSET;
        long end = (stretch * 4 + 2) * 512 + END_OFFSET;
        int high = Integer.MAX_VALUE;
        int low = Integer.MIN_VALUE;
        for (long x = start - APPROACH; x <= end; x += 4) {
            int floor = terrain.floorAt(x);
            if (floor < 0) return null;
            high = Math.min(high, floor);
            low = Math.max(low, floor);
        }
        if (low - high > MAX_RELIEF) return null;
        var kind = kinds.get((int) Long.remainderUnsigned(random >>> 16, kinds.size()));
        int width = kind.halfWidth() * 2;
        int road = high - ROAD_RISE;
        // Climb from the lowest floor of the approach in equal steps of at most STEP_RISE.
        int base = low;
        int rise = base - road;
        int steps = (rise + STEP_RISE - 1) / STEP_RISE - 1;
        var stones = new ArrayList<PlatformPlan.Stone>();
        long x = start;
        boolean pair = width < WIDE_STEP;
        for (int i = 1; i <= steps; i++) {
            int surface = base - rise * i / (steps + 1);
            stones.add(stone(kind, x + kind.halfWidth(), surface));
            if (pair) stones.add(stone(kind, x + width + kind.halfWidth(), surface));
            x += (pair ? 2 * width : width) + STEP_GAP;
        }
        int first = stones.size();
        while (x + width <= end) {
            stones.add(stone(kind, x + kind.halfWidth(), road));
            x += width + GAP;
        }
        if (stones.size() - first < MIN_ROAD) return null;
        // The controller spawns at most SECTION_STONES (8) per section; 64px platforms 8px apart fit 7.
        return new Route(stones.toArray(PlatformPlan.Stone[]::new), first);
    }

    private static PlatformPlan.Stone stone(CoursePlatforms.Kind kind, long x, int surface) {
        return new PlatformPlan.Stone(x, surface + CoursePlatforms.SURFACE_OFFSET, kind.objectId(), kind.subtype(),
                kind.halfWidth());
    }

    private RoutePlan() { }
}
