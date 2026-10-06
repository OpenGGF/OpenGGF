package infinite;

/**
 * Stateless seeded crossings for platform stretches; spacing, odds and bounce placement are mod
 * design choices. A stretch is crossed on a raft of stock platforms flush together in the middle
 * of the pit, by bouncing off a hovering flyer, or on a raft with a flyer below the far gap.
 * (Mod classes may not hold static state, so the crossings are ids rather than an enum.)
 */
public final class PlatformPlan {
    /** Widest open span left between stones: a 192px pit is a proven 3px/frame jump. */
    public static final int MAX_SPAN = 144;
    public static final int MAX_STONES = 3;
    /** Stones only: one raft in the middle of the pit. */
    public static final int RAFT = 0;
    /** No stones: one hovering flyer to bounce off. */
    public static final int BOUNCE = 1;
    /** The raft, plus a flyer below the far gap that bounces an overshoot on to the far bank. */
    public static final int BOTH = 2;
    /**
     * A held jump from the lip at the course's top speed (0x540) comes back down to bank height
     * about 310px out; a flyer this far into the pit meets that fall (and, with air steering,
     * jumps from up to about 64px back from the lip, or a little slower).
     */
    public static final int BOUNCE_REACH = 288;
    /** Bounce flyers hover with their centre this far below the bank: Sonic meets them falling, so
     * the ROM rebound (EnemyDefeated's neg.w y_vel) sends him back up a full jump. */
    public static final int BOUNCE_DEPTH = 24;
    /** Keeps a bounce flyer this far clear of the far bank's wall. */
    private static final int FAR_CLEARANCE = 48;

    /** One stock platform; {@code y} is the object centre (surface + {@link CoursePlatforms#SURFACE_OFFSET}). */
    public record Stone(long worldX, int y, int objectId, int subtype, int halfWidth) {
        public int surface() { return y - CoursePlatforms.SURFACE_OFFSET; }
    }

    /** A hovering flyer to bounce off; {@code species} is a {@link CourseSpecies} id. */
    public record Bouncer(long worldX, int y, int species) { }

    /** How the stretch's pit is crossed: {@link #RAFT}, {@link #BOUNCE} or {@link #BOTH}. Acts
     * whose sky has no flyer Sonic can bounce off (only spiked Orbinauts) always use a raft. */
    public static int crossing(TerrainLibrary terrain, long stretch) {
        if (bounceSpecies(terrain).length == 0) return RAFT;
        long random = TerrainLibrary.random(stretch + terrain.seed() + 0x424f554e4345L);
        return switch ((int) Long.remainderUnsigned(random, 4)) {
            case 0, 1 -> RAFT;
            case 2 -> BOUNCE;
            default -> BOTH;
        };
    }

    /** Stones for the pit that ends in this section (4k+3 of a platform stretch), else the
     * section's high-route platforms ({@link RoutePlan}), left to right. */
    public static Stone[] at(TerrainLibrary terrain, long section) {
        if (Math.floorMod(section, 4) != 3 || !terrain.platformRun(section)) return RoutePlan.at(terrain, section);
        long stretch = Math.floorDiv(section, 4);
        if (crossing(terrain, stretch) == BOUNCE) return new Stone[0];
        return raft(terrain, stretch);
    }

    /**
     * The hovering flyer of the pit that ends in this section, or null. Only bounce crossings and
     * rafts with a bounce safety net have one.
     */
    public static Bouncer bouncer(TerrainLibrary terrain, long section) {
        if (Math.floorMod(section, 4) != 3 || !terrain.platformRun(section)) return null;
        long stretch = Math.floorDiv(section, 4);
        int crossing = crossing(terrain, stretch);
        if (crossing == RAFT) return null;
        long start = terrain.platformPitStart(stretch);
        long end = terrain.platformPitEnd(stretch);
        var line = bounceSpecies(terrain);
        long random = TerrainLibrary.random(stretch + terrain.seed() + 0x424f554e4345L);
        int species = line[(int) Long.remainderUnsigned(random >>> 16, line.length)];
        if (crossing == BOUNCE) {
            int bank = terrain.floorAt(start - 1);
            return new Bouncer(Math.min(start + BOUNCE_REACH, end - FAR_CLEARANCE), bank + BOUNCE_DEPTH, species);
        }
        // Below the middle of the far gap, where a jump that overshoots the raft comes down.
        var stones = raft(terrain, stretch);
        var last = stones[stones.length - 1];
        long raftEnd = last.worldX() + last.halfWidth();
        return new Bouncer((raftEnd + end) / 2, last.surface() + BOUNCE_DEPTH, species);
    }

    /** The act's flyers Sonic can bounce off: destroyable, without Orbinaut spikes. */
    static int[] bounceSpecies(TerrainLibrary terrain) {
        return java.util.Arrays.stream(terrain.airSpecies()).filter(id -> CourseSpecies.of(id).bounceable()).toArray();
    }

    /**
     * The fewest stones that leave both outer spans jumpable, flush together ({@link RoutePlan#GAP}
     * apart, so Sonic runs straight across) in the middle of the pit: one wide landing rather than
     * stepping stones that each need a short, timed hop.
     */
    private static Stone[] raft(TerrainLibrary terrain, long stretch) {
        long start = terrain.platformPitStart(stretch);
        long end = terrain.platformPitEnd(stretch);
        int width = (int) (end - start);
        var kinds = terrain.platformKinds();
        long random = TerrainLibrary.random(stretch + terrain.seed() + 0x53544f4e45L);
        var kind = kinds.get((int) Long.remainderUnsigned(random, kinds.size()));
        int span = kind.halfWidth() * 2;
        int count = 1;
        while (count < MAX_STONES && (width - raftWidth(count, span)) / 2 > MAX_SPAN) count++;
        long left = start + (width - raftWidth(count, span)) / 2;
        // Stones sit level with the lower bank, so leaving one never climbs more than a tier.
        int surface = Math.max(terrain.floorAt(start - 1), terrain.floorAt(end));
        var stones = new Stone[count];
        for (int i = 0; i < count; i++) {
            long x = left + (long) (span + RoutePlan.GAP) * i + kind.halfWidth();
            stones[i] = new Stone(x, surface + CoursePlatforms.SURFACE_OFFSET, kind.objectId(), kind.subtype(),
                    kind.halfWidth());
        }
        return stones;
    }

    private static int raftWidth(int count, int span) {
        return count * span + (count - 1) * RoutePlan.GAP;
    }

    private PlatformPlan() { }
}
