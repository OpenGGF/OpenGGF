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

    /** Display width of the drawn picture; the camera's own width without a margin. */
    public static short width(Camera camera) {
        return camera.hasPresentationMargin() ? camera.getPresentationWidth() : camera.getWidth();
    }

    /** Picture pixels left of the simulation window. */
    public static int inset(Camera camera) { return camera.getPresentationInset(); }

    /** {@link #inset} when the picture is centred on the window, before any bound holds it. */
    public static int centredInset(Camera camera) {
        return Math.max(0, camera.getPresentationWidth() - camera.getWidth()) / 2;
    }

    /**
     * Draw-only camera X for sprites the ROM places on screen at a parallax rate from
     * {@code Camera_X_pos}. It equals {@link Camera#getX()} except while a NATIVE widescreen
     * picture is held at a camera bound ({@link #inset} differs from {@link #centredInset});
     * then it is the native camera the held picture is centred on, which is also the camera
     * the background is resampled at, so such a sprite holds still with the background
     * instead of sliding over a still foreground. Never use it for gameplay or culling.
     */
    public static int pictureCameraX(Camera camera) {
        return camera.getX() + centredInset(camera) - inset(camera);
    }

    /** Left edge of the drawn picture, including shake; the camera's own without a margin. */
    public static short leftWithShake(Camera camera) {
        return camera.hasPresentationMargin() ? camera.getPresentationXWithShake() : camera.getXWithShake();
    }

    /**
     * Screen width for ROM lifetime and visibility checks (Render_Sprites bounds that end or
     * start gameplay work): the gameplay camera's width, or 320 without a gameplay runtime.
     * Drawing offsets that centre a 320 composition use the projection width instead, so a
     * wider picture never changes when the ROM considers something to have left the screen.
     */
    public static int simulationScreenWidth() {
        return com.openggf.game.GameServices.hasRuntime()
                ? Math.max(NATIVE_WIDTH, com.openggf.game.GameServices.camera().getWidth()) : NATIVE_WIDTH;
    }

    private static final int NATIVE_WIDTH = 320;

    /**
     * Pixels to draw a wide picture further left while the focused player runs off the right
     * of the ROM screen at the camera's right limit; see {@code Camera.rightWallRunOffSlide}.
     * {@code rightMargin} is the picture width drawn right of the 320-pixel window. For a
     * renderer that draws a wider picture from the camera's own left edge (recorded-run
     * capture); it is not part of {@link #inset}.
     */
    public static int runOffSlide(Camera camera, int rightMargin) {
        return camera.rightWallRunOffSlide(rightMargin);
    }

    /** Detached camera whose window is the drawn picture; never used for gameplay. */
    public static Camera projection(Camera camera) { return camera.presentationProjection(); }
}
