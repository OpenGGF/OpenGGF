package infinite;

/** Stateless seeded encounter selection; all constants here are mod design choices. */
public final class EncounterPlan {
    public static final int PATROL_RADIUS = 64;
    public static final int AIR_CLEARANCE = 48;
    public static final int FIRST_SECTION = 3;

    /** Bob amplitude below a flyer's anchor; {@link CourseBadnik} bobs from -8 to +7. */
    public static final int BOB = 8;

    /** {@code species} is a {@link CourseSpecies} id. */
    public record Encounter(long worldX, int y, int species) {
        public boolean flying() { return CourseSpecies.of(species).flying(); }
    }

    public static Encounter at(TerrainLibrary terrain, long section) {
        if (section < FIRST_SECTION || terrain.isCorridor(section)) return null;
        long random = TerrainLibrary.random(section + TerrainLibrary.SEED + 0x4241444e494bL);
        // A quarter of sections are rest space. Separate randomness keeps terrain unchanged.
        int choice = (int) Long.remainderUnsigned(random, 8);
        if (choice < 2) return null;
        boolean flying = choice >= 6;
        var line = flying ? CourseSpecies.air(terrain.romZone()) : CourseSpecies.ground(terrain.romZone());
        var species = CourseSpecies.of(line[(int) Long.remainderUnsigned(random >>> 24, line.length)]);
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
                return new Encounter(x, low - AIR_CLEARANCE - species.depth() - BOB, species.id());
            }
            if (gentle && high - low <= 16) return new Encounter(x, terrain.floorAt(x) - species.depth(), species.id());
        }
        return null; // No appropriate ground habitat: leave the section empty.
    }

    private EncounterPlan() { }
}
