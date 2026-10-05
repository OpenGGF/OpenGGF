package infinite;

/** Seeded shield monitors standing on level ground; frequency and placement are mod design choices. */
public final class ShieldPlan {
    public static final int FIRST_SECTION = 6;
    /** One in this many eligible sections holds a monitor: about one every 25 seconds at 1x. */
    public static final int ODDS = 10;
    /** Map_Monitor pieces span y -$11 to +$E, so the box stands on floor 15px below its centre. */
    public static final int FLOOR_OFFSET = 15;
    public static final int HALF_WIDTH = 16;
    /** Between the ring-row runs (64, 208 and 368, each 72px plus 12px margins). */
    private static final int OFFSET = 304;
    private static final int FALLBACK_OFFSET = 160;

    public record Monitor(long worldX, int y) { }

    public static Monitor at(TerrainLibrary terrain, long section) {
        if (section < FIRST_SECTION || terrain.isCorridor(section) || terrain.platformRun(section)) return null;
        long random = TerrainLibrary.random(section + TerrainLibrary.SEED + 0x534849454cL);
        if (Long.remainderUnsigned(random, ODDS) != 0) return null;
        for (int offset : new int[] { OFFSET, FALLBACK_OFFSET }) {
            long x = section * 512 + offset;
            int floor = terrain.floorAt(x);
            boolean flat = floor >= 0;
            for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH && flat; dx++) {
                int other = terrain.floorAt(x + dx);
                flat = other >= 0 && Math.abs(other - floor) <= 4;
            }
            if (flat) return new Monitor(x, floor - FLOOR_OFFSET);
        }
        return null;
    }

    private ShieldPlan() { }
}
