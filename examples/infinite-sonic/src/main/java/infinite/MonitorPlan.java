package infinite;

/**
 * Seeded monitors standing on level ground or on a high route's last platform: shields, Super
 * Rings and Invincibility. Frequency, mix and placement are mod design choices.
 */
public final class MonitorPlan {
    public static final int FIRST_SECTION = 6;
    /** One in this many eligible sections holds a monitor: about one every 20 seconds at 1x. */
    public static final int ODDS = 8;
    /** Monitor contents, numbered as the S1 monitor subtypes (Pow_ChkX). */
    public static final int RINGS = 6;
    public static final int SHIELD = 4;
    public static final int INVINCIBLE = 5;
    /** Map_Monitor pieces span y -$11 to +$E, so the box stands on floor 15px below its centre. */
    public static final int FLOOR_OFFSET = 15;
    public static final int HALF_WIDTH = 16;
    /** Between the ring-row runs (64, 208 and 368, each 72px plus 12px margins). */
    private static final int OFFSET = 304;
    private static final int FALLBACK_OFFSET = 160;

    /** {@code kind} is {@link #SHIELD}, {@link #RINGS} or {@link #INVINCIBLE}. */
    public record Monitor(long worldX, int y, int kind) { }

    public static Monitor at(TerrainLibrary terrain, long section) {
        if (section < FIRST_SECTION || terrain.isCorridor(section) || terrain.platformRun(section)) return null;
        if (HazardPlan.at(terrain, section) != null) return null;
        long random = TerrainLibrary.random(section + TerrainLibrary.SEED + 0x534849454cL);
        var route = RoutePlan.at(terrain, section);
        if (route.length > 0) {
            // Half of high routes carry a monitor on their last platform: the reward for the high line.
            if ((random >>> 32 & 1) != 0) return null;
            var stone = route[route.length - 1];
            return new Monitor(stone.worldX(), stone.surface() - FLOOR_OFFSET, kind(section));
        }
        if (Long.remainderUnsigned(random, ODDS) != 0) return null;
        for (int offset : new int[] { OFFSET, FALLBACK_OFFSET }) {
            long x = section * 512 + offset;
            int floor = terrain.floorAt(x);
            boolean flat = floor >= 0;
            for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH && flat; dx++) {
                int other = terrain.floorAt(x + dx);
                flat = other >= 0 && Math.abs(other - floor) <= 4;
            }
            if (flat) return new Monitor(x, floor - FLOOR_OFFSET, kind(section));
        }
        return null;
    }

    /** Half shields, three in ten Super Rings, one in five Invincibility. */
    private static int kind(long section) {
        long random = TerrainLibrary.random(section + TerrainLibrary.SEED + 0x4b494e44L);
        int pick = (int) Long.remainderUnsigned(random, 10);
        return pick < 5 ? SHIELD : pick < 8 ? RINGS : INVINCIBLE;
    }

    private MonitorPlan() { }
}
