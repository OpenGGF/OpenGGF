package com.openggf.game.internal;

/**
 * Engine-only: a level whose vertical scroll wraps when it reaches row 0 at or above its top
 * boundary, rather than only when the top boundary is negative (Sonic 1's {@code ScrollVertical}
 * wraps at -$100). The level manager enables the camera's wrap with this range at each load.
 */
public interface VerticalScrollWrapAtTop {
    /** The wrap range in pixels, or 0 when the level's scroll does not wrap at its top boundary. */
    int verticalScrollWrapRange();

    /**
     * At each load: a level that wraps this way enables the camera's wrap with its range and that
     * trigger; any other level clears the trigger. Returns whether the camera's wrap is enabled.
     */
    static boolean apply(Object level, com.openggf.camera.Camera camera) {
        int range = level instanceof VerticalScrollWrapAtTop wrap ? wrap.verticalScrollWrapRange() : 0;
        if (range > 0) {
            camera.setVerticalWrapEnabled(true, range);
        }
        com.openggf.camera.CameraMotionInternalAccess.setVerticalWrapAtTopBoundary(camera, range > 0);
        return camera.isVerticalWrapEnabled();
    }
}
