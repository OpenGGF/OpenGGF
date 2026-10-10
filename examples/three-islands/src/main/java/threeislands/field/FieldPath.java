package threeislands.field;

/**
 * The route the party walks through a real act: one floor height per level column from the
 * act's start position towards its end, found from the act's own collision.
 *
 * <p>Column by column, the walker looks for floor surfaces (a solid pixel with clear headroom
 * above it) within reach of where it stood, preferring the nearest one and going up only a
 * little more reluctantly than going down. Where no floor is in reach (a pit or a tall wall)
 * it keeps its height and the column is marked as a jump. The drawn height is then eased so
 * the party never sinks below a floor: it rises early before steps and falls gradually after
 * ledges, and jumps over gaps follow an arc.
 */
public final class FieldPath {
    /** Rows of empty space a surface needs above it to count as standable. */
    public static final int HEADROOM = 24;
    public static final int MAX_RISE = 160;
    public static final int MAX_DROP = 192;
    /** Height changes larger than this are hopped quickly rather than eased. */
    public static final int BIG_STEP = 64;
    public static final int HOP = 16;

    private final int x0;
    private final int[] floor;
    private final int[] height;
    private final boolean[] airborne;

    private FieldPath(int x0, int[] floor, int[] height, boolean[] airborne) {
        this.x0 = x0;
        this.floor = floor;
        this.height = height;
        this.airborne = airborne;
    }

    /**
     * Builds the route from {@code startX} to {@code endX}. {@code hintY} is the character's
     * start height (its centre); the first floor chosen is the one nearest below it.
     */
    public static FieldPath build(Terrain terrain, int startX, int endX, int hintY) {
        int[] area = terrain.area();
        int top = area[1] + HEADROOM;
        int bottom = area[1] + area[3] - 1;
        int left = Math.max(area[0], startX);
        int right = Math.max(left + 1, Math.min(area[0] + area[2] - 1, endX));
        int width = right - left + 1;
        int[] floor = new int[width];
        boolean[] gap = new boolean[width];
        int y = firstFloor(terrain, left, hintY, top, bottom);
        boolean grounded = y >= 0;
        if (!grounded) y = Math.max(top, Math.min(bottom, hintY + 16));
        for (int i = 0; i < width; i++) {
            int x = left + i;
            int found = nearestFloor(terrain, x, y, top, bottom);
            if (found >= 0) {
                y = found;
            } else {
                gap[i] = true;
            }
            floor[i] = y;
        }
        // Ease: never below a floor. Small steps rise up to 2px per column before them and fall
        // 3px per column after ledges; big height changes become quick hops (16px per column) so
        // the party does not glide through the scenery between distant floors.
        int[] eased = new int[width];
        for (int i = 0; i < width; i++) {
            if (i == 0) {
                eased[0] = floor[0];
                continue;
            }
            int fall = floor[i] - eased[i - 1] > BIG_STEP ? HOP : 3;
            eased[i] = Math.min(floor[i], eased[i - 1] + fall);
        }
        for (int i = width - 2; i >= 0; i--) {
            int rise = eased[i] - eased[i + 1] > BIG_STEP ? HOP : 2;
            eased[i] = Math.min(eased[i], eased[i + 1] + rise);
        }
        // Arc over each run of gap columns.
        boolean[] air = new boolean[width];
        int i = 0;
        while (i < width) {
            if (!gap[i]) {
                i++;
                continue;
            }
            int start = Math.max(0, i - 6);
            int end = i;
            while (end < width && gap[end]) end++;
            end = Math.min(width - 1, end + 6);
            int length = Math.max(1, end - start);
            int lift = Math.min(56, Math.max(16, length / 3));
            for (int j = start; j <= end; j++) {
                double t = (j - start) / (double) length;
                eased[j] -= (int) Math.round(4 * lift * t * (1 - t));
                air[j] = true;
            }
            i = end + 1;
        }
        for (int j = 0; j < width; j++) if (eased[j] < floor[j] - 3) air[j] = true;
        return new FieldPath(left, floor, eased, air);
    }

    private static boolean standable(Terrain terrain, int x, int y) {
        if (!terrain.solid(x, y) || terrain.solid(x, y - 1)) return false;
        for (int k = 2; k <= HEADROOM; k++) if (terrain.solid(x, y - k)) return false;
        return true;
    }

    private static int firstFloor(Terrain terrain, int x, int hintY, int top, int bottom) {
        for (int d = 0; d < 1024; d++) {
            int below = hintY + d;
            if (below <= bottom && below >= top && standable(terrain, x, below)) return below;
            int above = hintY - d;
            if (d > 0 && d < 256 && above >= top && above <= bottom && standable(terrain, x, above)) return above;
        }
        return -1;
    }

    /** The nearest floor below (cost: distance) or above (cost: 1.5 x distance), or -1. */
    private static int nearestFloor(Terrain terrain, int x, int y, int top, int bottom) {
        int down = -1;
        for (int r = Math.max(top, y); r <= Math.min(bottom, y + MAX_DROP); r++) {
            if (standable(terrain, x, r)) {
                down = r;
                break;
            }
        }
        int limit = down < 0 ? MAX_RISE : Math.min(MAX_RISE, (down - y) * 2 / 3);
        for (int r = y - 1; r >= Math.max(top, y - limit); r--) {
            if (standable(terrain, x, r)) return r;
        }
        if (down >= 0) return down;
        // Nothing within a step or a short drop: a tall wall or a deep fall. Take the nearest
        // floor anywhere in the column (a climb costs half again a fall); only a column with no
        // floor at all (a bottomless pit) is jumped over at the current height.
        int below = -1;
        for (int r = y + MAX_DROP + 1; r <= bottom; r++) {
            if (standable(terrain, x, r)) {
                below = r;
                break;
            }
        }
        int above = -1;
        for (int r = y - MAX_RISE - 1; r >= top; r--) {
            if (standable(terrain, x, r)) {
                above = r;
                break;
            }
        }
        if (below < 0) return above;
        if (above < 0) return below;
        return (y - above) * 3 / 2 < below - y ? above : below;
    }

    public int startX() { return x0; }
    public int endX() { return x0 + floor.length - 1; }
    public int length() { return floor.length; }

    private int index(double x) {
        return (int) Math.max(0, Math.min(floor.length - 1, Math.round(x - x0)));
    }

    /** Where a character's feet are drawn at level column {@code x}. */
    public int heightAt(double x) { return height[index(x)]; }

    /** The floor surface found at column {@code x} (the feet rest here unless airborne). */
    public int floorAt(double x) { return floor[index(x)]; }

    /** True where the party is jumping rather than walking. */
    public boolean airborneAt(double x) { return airborne[index(x)]; }
}
