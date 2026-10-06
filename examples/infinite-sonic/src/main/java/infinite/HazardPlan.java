package infinite;

/**
 * Seeded zone hazards, drawn with the zone's own ROM art by {@link CourseHazard}. Each zone has
 * a signature hazard that needs timing, and every zone has spike beds:
 * <ul>
 *   <li>Green Hill: a checkered wrecking ball swinging on a chain over the ground.</li>
 *   <li>Marble and Star Light: fireballs leaping out of jump-corridor pits.</li>
 *   <li>Spring Yard: a giant spiked ball rolling back and forth along the ground.</li>
 *   <li>Labyrinth: a spiked ball and chain circling over the ground.</li>
 *   <li>Scrap Brain: a floor pipe that bursts into a column of flame.</li>
 * </ul>
 * Placement, frequency and every size below are mod design choices.
 */
public final class HazardPlan {
    public static final int SPIKES = 0;
    public static final int FIREBALL = 1;
    public static final int BIG_BALL = 2;
    public static final int CHAIN = 3;
    public static final int FLAME = 4;
    public static final int WRECKING_BALL = 5;
    public static final int KINDS = 6;
    /** No hazard before this section, so the opening teaches running, jumping and badniks first. */
    public static final int FIRST_SECTION = 5;
    /** One in this many eligible open sections holds a ground hazard. */
    public static final int GROUND_ODDS = 3;
    /** Fireballs need room to rise between the banks; from the third corridor on, half of them. */
    public static final int FIREBALL_MIN_GAP = 96;
    public static final int FIREBALL_FIRST_SECTION = 11;

    /** {@code floor} is the surface the hazard is measured from (for a fireball, the lower bank). */
    public record Hazard(long worldX, int floor, int kind, int phase) { }

    // S1 zone ids (Sonic1Constants).
    private static final int GHZ = 0, LZ = 1, MZ = 2, SLZ = 3, SYZ = 4, SBZ = 5;

    /** The zone's ground signature hazard, or -1 when its signature lives in the pits. */
    static int groundSignature(int romZone) {
        return switch (romZone) {
            case GHZ -> WRECKING_BALL;
            case SYZ -> BIG_BALL;
            case LZ -> CHAIN;
            case SBZ -> FLAME;
            default -> -1;
        };
    }

    /** Marble and Star Light load the Obj14 fireball art (Nem_MzFire). */
    static boolean pitFireballs(int romZone) { return romZone == MZ || romZone == SLZ; }

    public static Hazard at(TerrainLibrary terrain, long section) {
        return terrain.memo(TerrainLibrary.HAZARD_PLAN, section, () -> plan(terrain, section));
    }

    private static Hazard plan(TerrainLibrary terrain, long section) {
        if (section < FIRST_SECTION || terrain.platformRun(section)) return null;
        long random = TerrainLibrary.random(section + terrain.seed() + 0x48415a415244L);
        int phase = (int) Long.remainderUnsigned(random >>> 40, 256);
        if (terrain.isCorridor(section)) {
            int gap = terrain.gapWidth(section);
            if (!pitFireballs(terrain.romZone()) || section < FIREBALL_FIRST_SECTION || gap < FIREBALL_MIN_GAP
                    || (random & 1) != 0) return null;
            long x = section * 512 + 256;
            int floor = Math.max(terrain.floorAt(x - gap / 2 - 1), terrain.floorAt(x + gap / 2));
            return new Hazard(x, floor, FIREBALL, phase);
        }
        if (RoutePlan.has(terrain, section)) return null;
        if (Long.remainderUnsigned(random, GROUND_ODDS) != 0) return null;
        int signature = groundSignature(terrain.romZone());
        // Three in four ground hazards are the zone's signature; the rest (and all of Marble's and
        // Star Light's) are spike beds, which also stand in where the signature does not fit.
        int kind = signature >= 0 && Long.remainderUnsigned(random >>> 8, 4) != 0 ? signature : SPIKES;
        var found = place(terrain, section, kind, phase);
        return found != null || kind == SPIKES ? found : place(terrain, section, SPIKES, phase);
    }

    private static Hazard place(TerrainLibrary terrain, long section, int kind, int phase) {
        // Ground offsets tried in a section, middle first.
        for (int offset : new int[] { 256, 224, 288, 192, 320, 160, 352 }) {
            long x = section * 512 + offset;
            int floor = terrain.floorAt(x);
            if (floor >= 0 && fits(terrain, x, floor, kind)) return new Hazard(x, floor, kind, phase);
        }
        return null;
    }

    /**
     * The ground is level where the hazard meets Sonic's path: under a spike bed or flame pipe, and
     * under the low point of a chain's or wrecking ball's sweep. The rolling ball follows the
     * floor, which only has to exist and stay gentle along its run.
     */
    private static boolean fits(TerrainLibrary terrain, long x, int floor, int kind) {
        if (kind == BIG_BALL) {
            int previous = floor;
            for (long dx = 0; dx <= CourseHazard.BALL_TRAVEL; dx += 2) {
                int other = terrain.floorAt(x - dx);
                if (other < 0 || Math.abs(other - previous) > 2 || Math.abs(other - floor) > 24) return false;
                previous = other;
            }
            return true;
        }
        int reach = switch (kind) {
            case CHAIN, WRECKING_BALL -> 48;
            case FLAME -> 16;
            default -> 24;
        };
        int relief = kind == CHAIN || kind == WRECKING_BALL ? 12 : 2;
        for (long dx = -reach; dx <= reach; dx += 2) {
            int other = terrain.floorAt(x + dx);
            if (other < 0 || Math.abs(other - floor) > relief) return false;
        }
        return true;
    }

    private HazardPlan() { }
}
