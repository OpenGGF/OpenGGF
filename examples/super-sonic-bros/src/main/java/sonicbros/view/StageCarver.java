package sonicbros.view;

import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;

/**
 * Cuts a floating island out of a stock act: a strip of the act's real floor, with a little of
 * the art standing on it (grass, flowers, posts) and a tapered, shaded underside of the earth
 * beneath, so every stage is drawn from the zone's own blocks. The floor line is read from the
 * act's collision (the first solid pixel of each column near the chosen height), so the
 * fighters walk exactly where the art's ground is.
 */
public final class StageCarver {
    /** Art kept above the floor line: tufts, rails and props standing on the ground. */
    public static final int KEEP_ABOVE = 28;
    /** Art kept above the floor where a column is opaque all the way up (walls, cave backdrops). */
    public static final int WALL_FOOT = 9;

    /**
     * One carved piece.
     *
     * @param image   the picture, transparent around the island
     * @param surface for each image column, the floor's image row
     * @param bottom  for each image column, the underside's image row (exclusive)
     */
    public record Piece(SceneImage image, int[] surface, int[] bottom) {
        public int width() { return image.width(); }
        public int height() { return image.height(); }
    }

    private final SceneLevelKit kit;
    private final int blockSize;
    private final java.util.Map<Integer, byte[]> solidity = new java.util.HashMap<>();

    public StageCarver(SceneLevelKit kit) {
        this.kit = kit;
        this.blockSize = kit.blockSize();
    }

    /** Collision at a level pixel: EMPTY, TOP_SOLID or SOLID. */
    public int solid(int x, int y) {
        if (x < 0 || y < 0) return SceneLevelKit.EMPTY;
        int id = kit.block(x / blockSize, y / blockSize);
        if (id == 0) return SceneLevelKit.EMPTY;
        byte[] mask = solidity.computeIfAbsent(id, kit::blockSolidity);
        return mask[(y % blockSize) * blockSize + x % blockSize];
    }

    /** The colour of a level pixel (transparent where the plane shows the background). */
    public int pixel(int x, int y) {
        if (x < 0 || y < 0) return 0;
        int id = kit.block(x / blockSize, y / blockSize);
        if (id == 0) return 0;
        return kit.blockImage(id).pixel(x % blockSize, y % blockSize);
    }

    /** The first floor at or below {@code fromY} in column x within {@code reach} rows, or -1. */
    public int floorBelow(int x, int fromY, int reach) {
        for (int y = Math.max(0, fromY); y < fromY + reach; y++) {
            if (solid(x, y) != SceneLevelKit.EMPTY && (y == 0 || solid(x, y - 1) == SceneLevelKit.EMPTY)) return y;
        }
        return -1;
    }

    /**
     * Carves an island {@code width} pixels wide whose left edge is level column {@code x}, finding
     * the floor near row {@code floorY} (within {@code reach} rows above and below), {@code depth}
     * pixels deep at its middle and tapering to {@code edgeDepth} at its ends. {@code seed} varies
     * the jagged underside.
     */
    public Piece carve(int x, int floorY, int width, int reach, int depth, int edgeDepth, long seed) {
        int[] floor = new int[width];
        int last = floorY;
        for (int i = 0; i < width; i++) {
            int f = floorBelow(x + i, floorY - reach, reach * 2);
            floor[i] = f < 0 ? last : f;
            last = floor[i];
        }
        // Smooth single-column spikes in the collision line (posts, spring stubs).
        for (int i = 1; i < width - 1; i++) {
            if (Math.abs(floor[i] - floor[i - 1]) > 6 && Math.abs(floor[i] - floor[i + 1]) > 6) {
                floor[i] = (floor[i - 1] + floor[i + 1]) / 2;
            }
        }
        int top = Integer.MAX_VALUE;
        int lowest = Integer.MIN_VALUE;
        int[] depthAt = new int[width];
        java.util.Random random = new java.util.Random(seed);
        int wobble = 0;
        for (int i = 0; i < width; i++) {
            double u = (i + 0.5) / width;
            double bowl = Math.sqrt(Math.max(0, 1 - Math.pow(2 * u - 1, 2)));
            if (i % 6 == 0) wobble = random.nextInt(9) - 4;
            depthAt[i] = (int) Math.round(edgeDepth + (depth - edgeDepth) * bowl) + wobble;
            top = Math.min(top, floor[i] - KEEP_ABOVE);
            lowest = Math.max(lowest, floor[i] + depthAt[i]);
        }
        int height = lowest - top + 2;
        int[] pixels = new int[width * height];
        int[] surface = new int[width];
        int[] bottom = new int[width];
        for (int i = 0; i < width; i++) {
            int lx = x + i;
            surface[i] = floor[i] - top;
            bottom[i] = floor[i] + depthAt[i] - top;
            int fill = 0;
            // A column opaque all the way up is a wall or cave backdrop, not a prop: keep its foot only.
            int run = 0;
            while (run < KEEP_ABOVE && (pixel(lx, floor[i] - 1 - run) >>> 24) != 0) run++;
            int keep = run >= KEEP_ABOVE ? WALL_FOOT : KEEP_ABOVE;
            for (int y = floor[i] - keep; y < floor[i] + depthAt[i]; y++) {
                int argb = pixel(lx, y);
                boolean below = y >= floor[i];
                if (below) {
                    if ((argb >>> 24) == 0) argb = fill == 0 ? 0xFF302018 : darken(fill, 0.8);
                    else fill = argb;
                    double shade = (double) (y - floor[i]) / Math.max(1, depthAt[i]);
                    argb = darken(argb, 1.0 - 0.45 * shade * shade);
                } else if (y < floor[i] - keep + 6) {
                    // Fade the top of the kept art so a cut through a tree reads as foliage.
                    int a = (argb >>> 24) * (y - (floor[i] - keep) + 1) / 7;
                    argb = (a << 24) | (argb & 0xFFFFFF);
                }
                pixels[(y - top) * width + i] = argb;
            }
            // A dark rim along the underside makes the island read against any sky.
            int rim = floor[i] + depthAt[i] - 1 - top;
            if (rim >= 0 && rim < height) pixels[rim * width + i] = 0xFF181018;
            if (rim + 1 < height) pixels[(rim + 1) * width + i] = 0x80000000;
        }
        // Rim the two ends too.
        for (int y = 0; y < height; y++) {
            for (int side = 0; side < 2; side++) {
                int i = side == 0 ? 0 : width - 1;
                int p = pixels[y * width + i];
                if ((p >>> 24) != 0 && y >= surface[i]) pixels[y * width + i] = darken(p, 0.45);
            }
        }
        return new Piece(new SceneImage(width, height, pixels), surface, bottom);
    }

    /**
     * Scores a candidate island for the probe that chose the stages: lower is better.
     * Penalises an uneven floor, solid terrain in the headroom and holes in the earth.
     */
    public double score(int x, int floorY, int width, int reach, int headroom) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int blocked = 0;
        int holes = 0;
        int missing = 0;
        for (int i = 0; i < width; i += 2) {
            int f = floorBelow(x + i, floorY - reach, reach * 2);
            if (f < 0) { missing++; continue; }
            min = Math.min(min, f);
            max = Math.max(max, f);
            for (int y = f - headroom; y < f - 2; y += 3) if (solid(x + i, y) != SceneLevelKit.EMPTY) { blocked++; break; }
            for (int y = f; y < f + 40; y += 4) if ((pixel(x + i, y) >>> 24) == 0) holes++;
        }
        if (min == Integer.MAX_VALUE) return Double.MAX_VALUE;
        return (max - min) * 2.0 + blocked * 30.0 + holes * 2.0 + missing * 200.0;
    }

    static int darken(int argb, double f) {
        int r = (int) (((argb >> 16) & 255) * f);
        int g = (int) (((argb >> 8) & 255) * f);
        int b = (int) ((argb & 255) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
