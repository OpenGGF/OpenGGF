package com.openggf.sprites.playable;

/** Trusted engine bridge for split native control and global palette owners. */
public final class SuperStateInternalAccess {
    private SuperStateInternalAccess() { }

    public static void updateNativeControlFrame(SuperStateController controller) {
        controller.updateNativeControlFrame();
    }

    public static void finishNativeTransformationAfterPalette(SuperStateController controller) {
        controller.finishNativeTransformationAfterPalette();
    }
}
