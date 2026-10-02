package infinite;

/** Seeded four-ring trails on walkable approaches; all spacing is a mod design choice. */
public final class RingPlan {
    public static final int COUNT = 4;
    public static final int SPACING = 24;
    public static final int CLEARANCE = 24;

    public record Row(long worldX, int count) { }

    public static Row at(TerrainLibrary terrain, long section) {
        if (section < 0) return null;
        long random = TerrainLibrary.random(section + TerrainLibrary.SEED + 0x52494e4753L);
        // The opening trail teaches collection; thereafter leave a third of sections empty.
        if (section != 0 && Long.remainderUnsigned(random, 3) == 0) return null;
        // Try either approach before the middle, keeping rings away from centred gaps.
        int preferred = (random & 1) == 0 ? 64 : 368;
        for (int offset : new int[] { preferred, preferred == 64 ? 368 : 64, 208 }) {
            long start = section * 512 + offset;
            boolean safe = true;
            for (int dx = -12; dx <= (COUNT - 1) * SPACING + 12; dx++) {
                if (terrain.floorAt(start + dx) < 0) { safe = false; break; }
            }
            if (safe) return new Row(start, COUNT);
        }
        return null;
    }

    private RingPlan() { }
}
