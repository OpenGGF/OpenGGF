package infinite;

/** Stateless seeded stepping stones across platform stretches; spacing is a mod design choice. */
public final class PlatformPlan {
    /** Widest open span left between stones: a 192px pit is a proven 3px/frame jump. */
    public static final int MAX_SPAN = 144;
    public static final int MAX_STONES = 3;

    /** One stock platform; {@code y} is the object centre (surface + {@link CoursePlatforms#SURFACE_OFFSET}). */
    public record Stone(long worldX, int y, int objectId, int subtype, int halfWidth) {
        public int surface() { return y - CoursePlatforms.SURFACE_OFFSET; }
    }

    /** Stones for the pit that ends in this section (4k+3 of a platform stretch), else the
     * section's high-route platforms ({@link RoutePlan}), left to right. */
    public static Stone[] at(TerrainLibrary terrain, long section) {
        if (Math.floorMod(section, 4) != 3 || !terrain.platformRun(section)) return RoutePlan.at(terrain, section);
        long stretch = Math.floorDiv(section, 4);
        long start = terrain.platformPitStart(stretch);
        long end = terrain.platformPitEnd(stretch);
        int width = (int) (end - start);
        var kinds = terrain.platformKinds();
        long random = TerrainLibrary.random(stretch + terrain.seed() + 0x53544f4e45L);
        var kind = kinds.get((int) Long.remainderUnsigned(random, kinds.size()));
        int span = kind.halfWidth() * 2;
        int count = 1;
        while (count < MAX_STONES && (width - count * span) / (count + 1) > MAX_SPAN) count++;
        int gap = (width - count * span) / (count + 1);
        // Stones sit level with the lower bank, so leaving one never climbs more than a tier.
        int surface = Math.max(terrain.floorAt(start - 1), terrain.floorAt(end));
        var stones = new Stone[count];
        for (int i = 0; i < count; i++) {
            long x = start + gap * (i + 1L) + (long) span * i + kind.halfWidth();
            stones[i] = new Stone(x, surface + CoursePlatforms.SURFACE_OFFSET, kind.objectId(), kind.subtype(),
                    kind.halfWidth());
        }
        return stones;
    }

    private PlatformPlan() { }
}
