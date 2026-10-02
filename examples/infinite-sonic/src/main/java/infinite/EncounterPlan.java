package infinite;

/** Stateless seeded encounter selection; all constants here are mod design choices. */
public final class EncounterPlan {
    public static final int PATROL_RADIUS = 64;
    public static final int AIR_CLEARANCE = 48;
    public static final int FIRST_SECTION = 3;

    public record Encounter(long worldX, int y, boolean flying) { }

    public static Encounter at(TerrainLibrary terrain, long section) {
        if (section < FIRST_SECTION) return null;
        long random = TerrainLibrary.random(section + TerrainLibrary.SEED + 0x4241444e494bL);
        // A quarter of sections are rest space. Separate randomness keeps terrain unchanged.
        int choice = (int) Long.remainderUnsigned(random, 8);
        if (choice < 2) return null;
        boolean flying = choice >= 6;
        long centre = section * 512 + 256;
        int jitter = (int) Long.remainderUnsigned(random >>> 8, 97) - 48;
        // Search near the section centre rather than forcing a ground enemy onto a steep hill.
        for (int attempt = 0; attempt < 5; attempt++) {
            long x = centre + (attempt == 0 ? jitter : (attempt % 2 == 0 ? 1 : -1) * 24 * ((attempt + 1) / 2));
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            int previous = terrain.floorAt(x - PATROL_RADIUS - 24);
            boolean gentle = true;
            for (int dx = -PATROL_RADIUS - 24; dx <= PATROL_RADIUS + 24; dx++) {
                int floor = terrain.floorAt(x + dx);
                low = Math.min(low, floor);
                high = Math.max(high, floor);
                if (Math.abs(floor - previous) > 2) gentle = false;
                previous = floor;
            }
            if (flying) {
                // The entire sprite's patrol + bob stays above the highest surface in its corridor.
                return new Encounter(x, low - AIR_CLEARANCE - 12 - 8, true);
            }
            if (gentle && high - low <= 16) return new Encounter(x, terrain.floorAt(x) - 14, false);
        }
        return null; // No appropriate ground habitat: leave the section empty.
    }

    private EncounterPlan() { }
}
