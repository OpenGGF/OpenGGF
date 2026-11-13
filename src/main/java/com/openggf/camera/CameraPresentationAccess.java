package com.openggf.camera;

/**
 * Engine renderer view of the drawn picture, without adding members to the Camera mod
 * API. In NATIVE widescreen draw mode the picture is wider than the camera's 320-pixel
 * simulation window; otherwise every value equals the camera's own.
 */
public final class CameraPresentationAccess {
    private CameraPresentationAccess() { }

    /** True when the picture is wider than the simulation window (NATIVE draw mode). */
    public static boolean hasMargin(Camera camera) { return camera.hasPresentationMargin(); }

    /** Display width of the drawn picture. */
    public static short width(Camera camera) { return camera.getPresentationWidth(); }

    /** Picture pixels left of the simulation window. */
    public static int inset(Camera camera) { return camera.getPresentationInset(); }

    /** Left edge of the drawn picture, including shake. */
    public static short leftWithShake(Camera camera) { return camera.getPresentationXWithShake(); }

    /** Detached camera whose window is the drawn picture; never used for gameplay. */
    public static Camera projection(Camera camera) { return camera.presentationProjection(); }
}
