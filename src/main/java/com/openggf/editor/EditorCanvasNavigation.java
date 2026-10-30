package com.openggf.editor;

/**
 * Editor-only canvas framing. Pan requests are independent of the authoring cursor
 * and history. Fractional zoom reveals additional world area through native-sized
 * render tiles; whole-number zoom retains native pixel framing.
 */
public final class EditorCanvasNavigation {
    public static final float MIN_ZOOM = .25f;
    public static final int MAX_ZOOM = 8;
    private static final float[] ZOOMS = {.25f, .5f, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int NATIVE_ZOOM_INDEX = 2;
    private int zoomIndex = NATIVE_ZOOM_INDEX;
    private int viewportWidth = 320;
    private int viewportHeight = 224;
    private double pendingX, pendingY;

    public record Pan(double dx, double dy) {
        public boolean moved() { return dx != 0 || dy != 0; }
    }

    public float zoom() { return ZOOMS[zoomIndex]; }
    public String zoomLabel() { return zoom() < 1 ? Float.toString(zoom()) : Integer.toString((int) zoom()); }

    public void setViewportSize(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Canvas dimensions must be positive");
        viewportWidth = width;
        viewportHeight = height;
    }

    /** Moves the view by logical screen pixels; positive X reveals world to the right. */
    public void panBy(double screenDx, double screenDy) {
        requireFinite(screenDx, screenDy);
        pendingX += screenDx / zoom();
        pendingY += screenDy / zoom();
    }

    /** Steps through the supported scales while keeping the pointer's world point stationary. */
    public void zoomAt(int steps, double logicalX, double logicalY) {
        requireFinite(logicalX, logicalY);
        int next = (int) Math.clamp((long) zoomIndex + steps, 0, ZOOMS.length - 1);
        pendingX += logicalX / zoom() - logicalX / ZOOMS[next];
        pendingY += logicalY / zoom() - logicalY / ZOOMS[next];
        zoomIndex = next;
    }

    public void zoomIn() { zoomAt(1, viewportWidth * .5, viewportHeight * .5); }
    public void zoomOut() { zoomAt(-1, viewportWidth * .5, viewportHeight * .5); }
    public void resetZoom() { zoomAt(NATIVE_ZOOM_INDEX - zoomIndex, viewportWidth * .5, viewportHeight * .5); }

    public double worldOffset(double logicalCoordinate) {
        if (!Double.isFinite(logicalCoordinate)) throw new IllegalArgumentException("Canvas coordinate must be finite");
        return logicalCoordinate / zoom();
    }

    /** Called once per editor sync. Retains subpixel deltas until they form a native pixel. */
    public Pan consumePan() {
        double x = (int) pendingX, y = (int) pendingY;
        pendingX -= x;
        pendingY -= y;
        return new Pan(x, y);
    }

    public void reset() {
        zoomIndex = NATIVE_ZOOM_INDEX;
        pendingX = pendingY = 0;
    }

    private static void requireFinite(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Canvas movement must be finite");
    }
}
