package com.openggf.camera;

/** Camera motion state for engine-internal owners, without adding members to the Camera mod API. */
public final class CameraMotionInternalAccess {
    private CameraMotionInternalAccess() { }
    public static long forcedRecentreSerial(Camera camera) { return camera.forcedRecentreSerial; }

    /**
     * For a level-event tail that runs before the scroll and moves the bottom boundary
     * itself: this frame's scroll treats the boundary as shifting, as it does the frame
     * after the shared boundary easing moved it.
     */
    public static void markBottomBoundaryShifting(Camera camera) { camera.markBottomBoundaryShifting(); }

    /**
     * For a level whose vertical scroll wraps at row 0 of the top boundary rather than at -$100
     * ({@link com.openggf.game.internal.VerticalScrollWrapAtTop}): set at each level load.
     */
    public static void setVerticalWrapAtTopBoundary(Camera camera, boolean atTopBoundary) {
        camera.verticalWrapAtTopBoundary = atTopBoundary;
    }

    /**
     * For an object that writes Sonic CD's {@code camXCenter} itself (e.g. {@code sub_20C97C},
     * objects/r7/boss.asm:1268-1286): the focus column's offset from the screen centre (camXCenter - 160).
     * The player's pan ({@link Camera#updateHorizontalFocusPan}) steps it from there on its next frame.
     */
    public static void setHorizontalFocusOffset(Camera camera, short offset) { camera.setHorizontalFocusOffset(offset); }
}
