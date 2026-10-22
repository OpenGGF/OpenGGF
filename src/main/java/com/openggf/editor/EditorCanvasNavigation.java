package com.openggf.editor;

/**
 * Editor-only canvas framing. Pan requests are independent of the authoring cursor
 * and history. Zoom magnifies the complete rendered world; native 1x is the minimum
 * because the engine's stock render window does not expose additional world area.
 */
public final class EditorCanvasNavigation {
    public static final int MIN_ZOOM = 1;
    public static final int MAX_ZOOM = 8;
    private int zoom = MIN_ZOOM;
    private int viewportWidth = 320;
    private int viewportHeight = 224;
    private double pendingX, pendingY;

    public record Pan(double dx, double dy) {
        public boolean moved() { return dx != 0 || dy != 0; }
    }

    public int zoom() { return zoom; }

    public void setViewportSize(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Canvas dimensions must be positive");
        viewportWidth = width;
        viewportHeight = height;
    }

    /** Moves the view by logical screen pixels; positive X reveals world to the right. */
    public void panBy(double screenDx, double screenDy) {
        requireFinite(screenDx, screenDy);
        pendingX += screenDx / zoom;
        pendingY += screenDy / zoom;
    }

    /** Integer zoom preserves pixel framing and keeps the pointer's world point stationary. */
    public void zoomAt(int steps, double logicalX, double logicalY) {
        requireFinite(logicalX, logicalY);
        int next = (int) Math.clamp((long) zoom + steps, MIN_ZOOM, MAX_ZOOM);
        pendingX += logicalX / zoom - logicalX / next;
        pendingY += logicalY / zoom - logicalY / next;
        zoom = next;
    }

    public void zoomIn() { zoomAt(1, viewportWidth * .5, viewportHeight * .5); }
    public void zoomOut() { zoomAt(-1, viewportWidth * .5, viewportHeight * .5); }
    public void resetZoom() { zoomAt(MIN_ZOOM - zoom, viewportWidth * .5, viewportHeight * .5); }

    public double worldOffset(double logicalCoordinate) {
        if (!Double.isFinite(logicalCoordinate)) throw new IllegalArgumentException("Canvas coordinate must be finite");
        return logicalCoordinate / zoom;
    }

    /** Called once per editor sync. Retains subpixel deltas until they form a native pixel. */
    public Pan consumePan() {
        double x = (int) pendingX, y = (int) pendingY;
        pendingX -= x;
        pendingY -= y;
        return new Pan(x, y);
    }

    public void reset() {
        zoom = MIN_ZOOM;
        pendingX = pendingY = 0;
    }

    private static void requireFinite(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Canvas movement must be finite");
    }
}
