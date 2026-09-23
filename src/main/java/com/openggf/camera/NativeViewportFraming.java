package com.openggf.camera;

/**
 * Projects a 320-pixel ROM camera window into a wider viewport without changing
 * the native boundary words consumed by player movement and event logic.
 * Origin: LRZ1 / DEZ2 arena centering, September 2026.
 */
public final class NativeViewportFraming {
    private NativeViewportFraming() { }

    public static int inset(int viewportWidth) {
        return Math.max(0, viewportWidth - 320) / 2;
    }

    public static int visibleLeft(int nativeLeft, int viewportWidth) {
        return nativeLeft - inset(viewportWidth);
    }

    public static int nativeLeft(int visibleLeft, int viewportWidth) {
        return visibleLeft + inset(viewportWidth);
    }
}
