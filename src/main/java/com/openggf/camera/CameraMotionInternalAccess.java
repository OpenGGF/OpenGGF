package com.openggf.camera;

/** Read-only presentation observation without adding members to the Camera mod API. */
public final class CameraMotionInternalAccess {
    private CameraMotionInternalAccess() { }
    public static long forcedRecentreSerial(Camera camera) { return camera.forcedRecentreSerial; }
}
