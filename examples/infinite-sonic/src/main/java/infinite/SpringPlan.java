package infinite;

/**
 * Seeded launch springs at spring chasms (see {@link TerrainLibrary#springStretch}). The spring
 * stands on the approach bank just before the wide pit; placement is a mod design choice.
 */
public final class SpringPlan {
    /** The spring's centre sits this far before the pit, leaving 32px of bank past its plate. */
    public static final int PIT_LEAD = 48;
    /** Map_Spring frame 0 spans y -8 to +7, so the spring stands on floor 8px below its centre. */
    public static final int FLOOR_OFFSET = 8;

    /** {@code y} is the object centre; {@code floor} the bank surface it stands on. */
    public record Spring(long worldX, int y, int floor) { }

    /** The launch spring standing in this section (4k+2 of a spring chasm), or null. */
    public static Spring at(TerrainLibrary terrain, long section) {
        if (Math.floorMod(section, 4) != 2) return null;
        long stretch = Math.floorDiv(section, 4);
        if (!terrain.springStretch(stretch)) return null;
        long x = terrain.platformPitStart(stretch) - PIT_LEAD;
        int floor = terrain.floorAt(x);
        return new Spring(x, floor - FLOOR_OFFSET, floor);
    }

    private SpringPlan() { }
}
