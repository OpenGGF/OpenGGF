package com.openggf.game.presentation;

import java.util.List;

/** Internal admission bound in pixel visits, plus one unit per tile/vertex inspected.
 * The same clipped segments are used by the estimator and rasterizer, so offscreen
 * coordinate distance never becomes iteration work. No public Mod API changes.
 */
final class SceneRasterWork {
    static final long MAX_WORK = 8_000_000;
    private SceneRasterWork() { }

    static void requireBudget(int width, int height, List<ScenePresentationFrame.Tile> tiles,
                              List<ScenePresentationFrame.Primitive> primitives, int dx, int dy) {
        if (estimate(width, height, tiles, primitives, dx, dy) > MAX_WORK)
            throw new IllegalArgumentException("Scene exceeds aggregate raster work limit");
    }

    static long estimate(int width, int height, List<ScenePresentationFrame.Tile> tiles,
                         List<ScenePresentationFrame.Primitive> primitives, int dx, int dy) {
        long work = (long) width * height;
        for (var tile : tiles) {
            long left = (long) tile.x() - dx, top = (long) tile.y() - dy;
            work += 1 + area(left, top + (tile.rowStart() * tile.height() + 7) / 8,
                    left + tile.width(), top + (tile.rowEnd() * tile.height() + 7) / 8, width, height);
            if (work > MAX_WORK) return work;
        }
        for (var primitive : primitives) {
            var vertices = primitive.vertices();
            for (int i = 0; i < vertices.size(); i++) {
                var v = vertices.get(i); work++;
                if (primitive.kind() == ScenePresentationFrame.PrimitiveKind.RECTANGLE) {
                    work += area((long)Math.min(v.x1(), v.x2()) - dx, (long)Math.min(v.y1(), v.y2()) - dy,
                            (long)Math.max(v.x1(), v.x2()) - dx, (long)Math.max(v.y1(), v.y2()) - dy, width, height);
                } else if (primitive.method() == 0) {
                    long x = (long)v.x1() - dx, y = (long)v.y1() - dy;
                    if (x >= 0 && x < width && y >= 0 && y < height) work++;
                } else if (i > 0 && (primitive.method() != 1 || i % 2 == 1)) {
                    work += segmentWork(vertices.get(i - 1), v, width, height, dx, dy);
                }
                if (work > MAX_WORK) return work;
            }
            if (primitive.kind() == ScenePresentationFrame.PrimitiveKind.VERTEX
                    && primitive.method() == 2 && vertices.size() > 1)
                work += segmentWork(vertices.getLast(), vertices.getFirst(), width, height, dx, dy);
            if (work > MAX_WORK) return work;
        }
        return work;
    }

    private static long area(long x0, long y0, long x1, long y1, int width, int height) {
        return Math.max(0, Math.min(width, x1) - Math.max(0, x0))
                * Math.max(0, Math.min(height, y1) - Math.max(0, y0));
    }
    private static long segmentWork(ScenePresentationFrame.Vertex a, ScenePresentationFrame.Vertex b,
                                    int width, int height, int dx, int dy) {
        long[] line = clip(width, height, (long)a.x1() - dx, (long)a.y1() - dy,
                (long)b.x1() - dx, (long)b.y1() - dy);
        return line == null ? 0 : Math.max(Math.abs(line[2] - line[0]), Math.abs(line[3] - line[1])) + 1;
    }

    /** Cohen-Sutherland; frame coordinates and survey offsets are bounded before entry.
     * Long differences/products avoid overflow and clipping happens before Bresenham.
     */
    static long[] clip(int width, int height, long x0, long y0, long x1, long y1) {
        while (true) {
            int a = outside(x0, y0, width, height), b = outside(x1, y1, width, height);
            if ((a | b) == 0) return new long[]{x0, y0, x1, y1};
            if ((a & b) != 0) return null;
            int edge = a != 0 ? a : b;
            long x, y;
            if ((edge & 8) != 0) { y = height - 1L; x = x0 + (x1 - x0) * (y - y0) / (y1 - y0); }
            else if ((edge & 4) != 0) { y = 0; x = x0 + (x1 - x0) * -y0 / (y1 - y0); }
            else if ((edge & 2) != 0) { x = width - 1L; y = y0 + (y1 - y0) * (x - x0) / (x1 - x0); }
            else { x = 0; y = y0 + (y1 - y0) * -x0 / (x1 - x0); }
            if (edge == a) { x0 = x; y0 = y; } else { x1 = x; y1 = y; }
        }
    }
    private static int outside(long x, long y, int width, int height) {
        return (x < 0 ? 1 : x >= width ? 2 : 0) | (y < 0 ? 4 : y >= height ? 8 : 0);
    }
}
