package eggsky.world;

import com.openggf.mods.scene.SceneLevelKit;
import eggsky.core.Rng;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Remixes a stock act into a new planet's terrain. The act's foreground layout is read as
 * columns of layout blocks. A planet is a loop of runs of consecutive source columns; a run
 * may jump to another source column only where every row of the new pair of neighbours also
 * sits side by side somewhere in the stock layout (or both sides are open air), so each seam is
 * one the original level already contains and the art joins without a visible cut. The loop
 * closes the same way, so walking all the way round the planet comes back to the start.
 */
public final class TerrainGen {
    /** Shortest and longest stretch of the act copied before considering a jump. */
    private static final int MIN_RUN = 2;
    private static final int MAX_RUN = 6;

    private TerrainGen() {
    }

    /**
     * The planet's blocks, {@code cells[column + row * columns]}, and for each planet column the
     * source column it was copied from.
     */
    public record Layout(int[] cells, int[] sources, int rows) {
        public int columns() {
            return sources.length;
        }
    }

    /**
     * Builds about {@code targetColumns} columns (the loop may end a little longer or shorter).
     */
    public static Layout generate(SceneLevelKit kit, long seed, int targetColumns) {
        int size = kit.blockSize();
        int[] area = kit.playableArea();
        int c0 = Math.max(0, area[0] / size);
        int c1 = Math.min(kit.columns() - 1, (area[0] + area[2] - 1) / size);
        int r0 = Math.max(0, area[1] / size);
        int r1 = Math.min(kit.rows() - 1, (area[1] + area[3] - 1) / size);
        int cols = c1 - c0 + 1;
        int rows = r1 - r0 + 1;
        int[][] sig = new int[cols][rows];
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                sig[c][r] = kit.block(c0 + c, r0 + r);
            }
        }
        // Every horizontal neighbour pair in the whole layout (not just the playable area).
        HashSet<Long> pairs = new HashSet<>();
        for (int r = 0; r < kit.rows(); r++) {
            for (int c = 0; c + 1 < kit.columns(); c++) {
                pairs.add(pair(kit.block(c, r), kit.block(c + 1, r)));
            }
        }
        boolean[] blank = new boolean[Math.max(1, kit.blockCount())];
        Edges edges = new Edges(kit);
        boolean[] known = new boolean[blank.length];
        Rng rng = new Rng(seed);
        // compat[a][b]: rows where source column a may be followed by source column b.
        int[][] compat = new int[cols][cols];
        for (int a = 0; a < cols; a++) {
            for (int b = 0; b < cols; b++) {
                int ok = 0;
                for (int r = 0; r < rows; r++) {
                    int x = sig[a][r];
                    int y = sig[b][r];
                    if (x == y && x == 0 || pairs.contains(pair(x, y))
                            || isBlank(kit, x, blank, known) && isBlank(kit, y, blank, known)
                            || edges.matches(x, y)) {
                        ok++;
                    }
                }
                compat[a][b] = ok;
            }
        }
        if (Boolean.getBoolean("eggsky.debugGen")) {
            int[] hist = new int[rows + 1];
            for (int a = 0; a < cols; a++) {
                int best = 0;
                for (int b = 0; b < cols; b++) {
                    if (Math.abs(b - (a + 1)) >= 3) {
                        best = Math.max(best, compat[a][b]);
                    }
                }
                hist[best]++;
            }
            System.out.println("best-jump histogram " + java.util.Arrays.toString(hist));
        }
        // Columns worth starting from: not entirely empty.
        List<Integer> starts = new ArrayList<>();
        for (int c = 0; c < cols; c++) {
            boolean any = false;
            for (int r = 0; r < rows && !any; r++) {
                any = !isBlank(kit, sig[c][r], blank, known);
            }
            if (any) {
                starts.add(c);
            }
        }
        if (starts.isEmpty()) {
            starts.add(0);
        }
        int first = starts.get(rng.nextInt(starts.size()));
        List<Integer> out = new ArrayList<>();
        int[] lastUsed = new int[cols];
        java.util.Arrays.fill(lastUsed, -1000);
        int cur = first;
        int target = Math.max(8, targetColumns);
        int budget = target + 48;
        while (out.size() < budget) {
            int len = rng.range(MIN_RUN, MAX_RUN);
            int end = cur;
            for (int i = 0; i < len && out.size() < budget; i++) {
                int column = Math.min(cols - 1, cur + i);
                out.add(column);
                lastUsed[column] = out.size();
                end = column;
                if (column == cols - 1) {
                    break;
                }
            }
            // Close the loop once long enough: the next column would be the first.
            if (out.size() >= target && compat[end][first] >= rows - slack(rows)) {
                break;
            }
            int next = chooseNext(compat, rows, end, cols, lastUsed, out.size(), rng);
            cur = next;
        }
        int[] columns = trimToClosure(out, compat, rows, first, target);
        int n = columns.length;
        int[] cells = new int[n * rows];
        int[] sources = new int[n];
        for (int i = 0; i < n; i++) {
            sources[i] = columns[i] + c0;
            for (int r = 0; r < rows; r++) {
                cells[i + r * n] = sig[columns[i]][r];
            }
        }
        repairSeams(kit, cells, n, rows, pairs, edges, blank, known);
        return new Layout(cells, sources, rows);
    }

    /** Seam rows the generator may leave for {@link #repairSeams}. */
    private static int slack(int rows) {
        return Math.max(1, rows / 4);
    }

    /**
     * Where a seam row's neighbours never meet in the stock act, swaps the right-hand cell for a
     * block that does fit: one seen beside its left and right neighbours and above and below its
     * vertical ones (or matching their edges).
     */
    private static void repairSeams(SceneLevelKit kit, int[] cells, int n, int rows, HashSet<Long> pairs, Edges edges,
            boolean[] blank, boolean[] known) {
        HashSet<Long> vertical = new HashSet<>();
        java.util.TreeSet<Integer> used = new java.util.TreeSet<>();
        for (int r = 0; r + 1 < kit.rows(); r++) {
            for (int c = 0; c < kit.columns(); c++) {
                vertical.add(pair(kit.block(c, r), kit.block(c, r + 1)));
                used.add(kit.block(c, r));
            }
        }
        for (int i = 0; i < n; i++) {
            int prev = Math.floorMod(i - 1, n);
            int next = (i + 1) % n;
            for (int r = 0; r < rows; r++) {
                int left = cells[prev + r * n];
                int cur = cells[i + r * n];
                if (fits(kit, left, cur, pairs, edges, blank, known)) {
                    continue;
                }
                int right = cells[next + r * n];
                int up = r > 0 ? cells[i + (r - 1) * n] : -1;
                int down = r + 1 < rows ? cells[i + (r + 1) * n] : -1;
                for (int x : used) {
                    if (fits(kit, left, x, pairs, edges, blank, known) && fits(kit, x, right, pairs, edges, blank, known)
                            && (up < 0 || vertical.contains(pair(up, x)))
                            && (down < 0 || vertical.contains(pair(x, down)))) {
                        cells[i + r * n] = x;
                        break;
                    }
                }
            }
        }
    }

    private static boolean fits(SceneLevelKit kit, int a, int b, HashSet<Long> pairs, Edges edges, boolean[] blank,
            boolean[] known) {
        return a == 0 && b == 0 || pairs.contains(pair(a, b))
                || isBlank(kit, a, blank, known) && isBlank(kit, b, blank, known) || edges.matches(a, b);
    }

    /** Where to continue after source column {@code end}: a seamless jump or the next column. */
    private static int chooseNext(int[][] compat, int rows, int end, int cols, int[] lastUsed, int position, Rng rng) {
        List<Integer> jumps = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (int b = 0; b < cols; b++) {
            if (Math.abs(b - (end + 1)) < 3 || compat[end][b] < rows - slack(rows)) {
                continue;
            }
            // Prefer columns not visited recently, for variety.
            int age = position - lastUsed[b];
            jumps.add(b);
            weights.add(Math.max(1, Math.min(40, age)) * (compat[end][b] == rows ? 3 : 1));
        }
        boolean canContinue = end + 1 < cols;
        if (!jumps.isEmpty() && (!canContinue || rng.chance(0.85))) {
            int[] w = new int[weights.size()];
            for (int i = 0; i < w.length; i++) {
                w[i] = weights.get(i);
            }
            return jumps.get(Math.max(0, rng.weighted(w)));
        }
        if (canContinue) {
            return end + 1;
        }
        // The act's last column with no seamless way on: take the best imperfect join.
        int best = 0;
        int bestScore = -1;
        for (int b = 0; b < cols; b++) {
            int score = compat[end][b] * 64 + rng.nextInt(32);
            if (b != end && score > bestScore) {
                bestScore = score;
                best = b;
            }
        }
        return best;
    }

    /**
     * The sequence cut where it rejoins its first column best (seamlessly when it can), keeping
     * at least most of {@code target}.
     */
    private static int[] trimToClosure(List<Integer> out, int[][] compat, int rows, int first, int target) {
        int bestEnd = out.size();
        int bestScore = -1;
        int from = Math.max(1, Math.min(out.size(), target * 3 / 4));
        for (int i = from; i <= out.size(); i++) {
            int last = out.get(i - 1);
            int score = compat[last][first] * 1000 - Math.abs(i - target);
            if (score > bestScore) {
                bestScore = score;
                bestEnd = i;
            }
        }
        int[] columns = new int[bestEnd];
        for (int i = 0; i < bestEnd; i++) {
            columns[i] = out.get(i);
        }
        return columns;
    }

    /**
     * Facing edges of blocks: a block may sit left of another when the art along the shared
     * edge has the same shape (transparent where the other is transparent) and similar colours,
     * and the collision meets at the same heights.
     */
    private static final class Edges {
        private final SceneLevelKit kit;
        private final int size;
        private final int[][] left;
        private final int[][] right;
        private final byte[][] leftSolid;
        private final byte[][] rightSolid;

        Edges(SceneLevelKit kit) {
            this.kit = kit;
            this.size = kit.blockSize();
            int n = Math.max(1, kit.blockCount());
            left = new int[n][];
            right = new int[n][];
            leftSolid = new byte[n][];
            rightSolid = new byte[n][];
        }

        private void load(int id) {
            if (left[id] != null) {
                return;
            }
            int[] px = kit.blockImage(id).pixels();
            byte[] m = kit.blockSolidity(id);
            int[] l = new int[size];
            int[] r = new int[size];
            byte[] ls = new byte[size];
            byte[] rs = new byte[size];
            for (int y = 0; y < size; y++) {
                l[y] = px[y * size];
                r[y] = px[y * size + size - 1];
                ls[y] = m[y * size];
                rs[y] = m[y * size + size - 1];
            }
            left[id] = l;
            right[id] = r;
            leftSolid[id] = ls;
            rightSolid[id] = rs;
        }

        boolean matches(int a, int b) {
            if (a <= 0 || b <= 0 || a >= left.length || b >= left.length) {
                return false;
            }
            load(a);
            load(b);
            int[] ea = right[a];
            int[] eb = left[b];
            byte[] sa = rightSolid[a];
            byte[] sb = leftSolid[b];
            int shapeMiss = 0;
            int colourMiss = 0;
            int solidMiss = 0;
            int opaque = 0;
            for (int y = 0; y < size; y++) {
                boolean ta = (ea[y] >>> 24) == 0;
                boolean tb = (eb[y] >>> 24) == 0;
                if (ta != tb) {
                    shapeMiss++;
                } else if (!ta) {
                    opaque++;
                    if (colourDistance(ea[y], eb[y]) > 90) {
                        colourMiss++;
                    }
                }
                if ((sa[y] == SceneLevelKit.EMPTY) != (sb[y] == SceneLevelKit.EMPTY)) {
                    solidMiss++;
                }
            }
            return shapeMiss <= size / 32 && solidMiss <= size / 32
                    && colourMiss <= Math.max(2, opaque / 4);
        }

        private static int colourDistance(int x, int y) {
            return Math.abs(((x >> 16) & 255) - ((y >> 16) & 255)) + Math.abs(((x >> 8) & 255) - ((y >> 8) & 255))
                    + Math.abs((x & 255) - (y & 255));
        }
    }

    private static long pair(int a, int b) {
        return ((long) a << 20) | b;
    }

    /** Whether a block has no art and no collision (open air). */
    private static boolean isBlank(SceneLevelKit kit, int id, boolean[] blank, boolean[] known) {
        if (id <= 0) {
            return true;
        }
        if (id >= blank.length) {
            return false;
        }
        if (!known[id]) {
            known[id] = true;
            boolean empty = true;
            for (byte b : kit.blockSolidity(id)) {
                if (b != SceneLevelKit.EMPTY) {
                    empty = false;
                    break;
                }
            }
            if (empty) {
                for (int p : kit.blockImage(id).pixels()) {
                    if (p != 0) {
                        empty = false;
                        break;
                    }
                }
            }
            blank[id] = empty;
        }
        return blank[id];
    }
}
