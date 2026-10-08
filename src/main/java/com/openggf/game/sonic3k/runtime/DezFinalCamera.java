package com.openggf.game.sonic3k.runtime;

import com.openggf.camera.Camera;
import com.openggf.camera.NativeViewportFraming;

/**
 * DEZ3 routines address the original 320px camera window: the moving support is
 * Camera_X+$B0, the entry gate Camera_X+$98, and the escape ship likewise uses
 * fixed native offsets. Using the wider viewport's left edge drops the player
 * off that support. Preserve the ROM words for events/objects and project only
 * the visible origin through the shared widescreen framing utility.
 */
public final class DezFinalCamera {
    private DezFinalCamera() { }
    public static int nativeX(Camera camera) {
        return NativeViewportFraming.nativeLeft(camera.getX(), camera.getWidth()) & 0xFFFF;
    }
    public static int nativeCopyX(Camera camera) {
        return NativeViewportFraming.nativeLeft(camera.getXCopy(), camera.getWidth()) & 0xFFFF;
    }
    public static short visibleX(Camera camera, int nativeX) {
        return (short) NativeViewportFraming.visibleLeft(nativeX, camera.getWidth());
    }
}
