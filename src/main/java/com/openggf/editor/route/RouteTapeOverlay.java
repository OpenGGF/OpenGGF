package com.openggf.editor.route;

import com.openggf.route.PlayerSlot;

import java.util.List;
import java.util.Objects;

/**
 * The observed path of a route replay: one post-row centre per row for each participant, in
 * world pixels. Painting reads values only. A gap in rows or an epoch change (a level load or
 * mode change) breaks the line; the overlay never invents the path between observations.
 * Strokes use the same ink outline and ground/air colours as the Stageplay route overlay.
 */
public final class RouteTapeOverlay {
    public static final int GROUND = 0x61E5ED, AIR = 0xFFD077, SIDEKICK = 0xB9A4FF, FAILURE = 0xFF7887;
    public static final int INK = 0x101A2B, WHITE = 0xFFFFFF;

    public interface Painter {
        void line(float x1, float y1, float x2, float y2, int rgb, float width);
    }

    /** A post-row observation: after row {@code row}, in boundary epoch {@code epoch}. */
    public record Point(int row, long epoch, int x, int y, boolean air) { }

    /** World-to-screen mapping: {@code screen = origin + (world - camera) * unit}. */
    public record Camera(float left, float top, int cameraX, int cameraY, float unit) {
        public float x(int world) {
            return left + (world - cameraX) * unit;
        }

        public float y(int world) {
            return top + (world - cameraY) * unit;
        }
    }

    private final List<Point> p1;
    private final List<Point> p2;
    private final int stride;
    private final int divergedRow;

    /**
     * @param stride      row spacing of the observations (1 = every row); a larger gap breaks the line
     * @param divergedRow the first row replay could not complete, or -1; the path stops there
     *                    and a failure cross marks the last observation
     */
    public RouteTapeOverlay(List<Point> p1, List<Point> p2, int stride, int divergedRow) {
        this.p1 = List.copyOf(Objects.requireNonNull(p1, "p1"));
        this.p2 = List.copyOf(Objects.requireNonNull(p2, "p2"));
        if (stride < 1) throw new IllegalArgumentException("stride must be positive: " + stride);
        this.stride = stride;
        this.divergedRow = divergedRow;
    }

    public List<Point> path(PlayerSlot slot) {
        return slot == PlayerSlot.P1 ? p1 : p2;
    }

    public int divergedRow() {
        return divergedRow;
    }

    public boolean isEmpty() {
        return p1.isEmpty() && p2.isEmpty();
    }

    /** The P1 observation after {@code row}, or the nearest earlier one; null when none exists. */
    public Point at(int row) {
        Point best = null;
        int low = 0, high = p1.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            Point point = p1.get(mid);
            if (point.row() <= row) {
                best = point;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    /** Paints both paths and a cursor marker at {@code cursorRow}. The frontend clips to its level view. */
    public void paint(Painter painter, Camera camera, int cursorRow) {
        stroke(painter, camera, p2, stride, true);
        stroke(painter, camera, p1, stride, false);
        if (!p1.isEmpty()) {
            Point start = p1.getFirst();
            float sx = camera.x(start.x()), sy = camera.y(start.y());
            outlined(painter, sx - 4, sy - 4, sx + 4, sy - 4, GROUND);
            outlined(painter, sx + 4, sy - 4, sx + 4, sy + 4, GROUND);
            outlined(painter, sx + 4, sy + 4, sx - 4, sy + 4, GROUND);
            outlined(painter, sx - 4, sy + 4, sx - 4, sy - 4, GROUND);
            if (divergedRow >= 0) {
                Point stop = p1.getLast();
                float x = camera.x(stop.x()), y = camera.y(stop.y());
                outlined(painter, x - 5, y - 5, x + 5, y + 5, FAILURE);
                outlined(painter, x - 5, y + 5, x + 5, y - 5, FAILURE);
            }
        }
        Point cursor = at(cursorRow);
        if (cursor != null) {
            float x = camera.x(cursor.x()), y = camera.y(cursor.y());
            outlined(painter, x - 4, y, x + 4, y, WHITE);
            outlined(painter, x, y - 4, x, y + 4, WHITE);
        }
    }

    private static void stroke(Painter painter, Camera camera, List<Point> path, int stride, boolean sidekick) {
        for (int i = 1; i < path.size(); i++) {
            Point a = path.get(i - 1), b = path.get(i);
            int gap = b.row() - a.row();
            if (gap < 1 || gap > stride || b.epoch() != a.epoch()) continue;
            int rgb = sidekick ? SIDEKICK : a.air() || b.air() ? AIR : GROUND;
            painter.line(camera.x(a.x()), camera.y(a.y()), camera.x(b.x()), camera.y(b.y()), INK, sidekick ? 3 : 4);
            painter.line(camera.x(a.x()), camera.y(a.y()), camera.x(b.x()), camera.y(b.y()), rgb, sidekick ? 1 : 2);
        }
    }

    private static void outlined(Painter painter, float x1, float y1, float x2, float y2, int rgb) {
        painter.line(x1, y1, x2, y2, INK, 4);
        painter.line(x1, y1, x2, y2, rgb, 2);
    }
}
