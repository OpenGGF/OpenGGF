package com.openggf.game.sonic3k.runtime;

import com.openggf.camera.Camera;
import com.openggf.camera.NativeViewportFraming;

/** Native camera words for SSZ2 gameplay after its widescreen crane-pan projection. */
public final class SszArenaCamera {
    private SszArenaCamera() { }

    public static int nativeX(Camera camera, SszZoneRuntimeState state) {
        return (state != null && state.centerNativeArenaCamera()
                ? NativeViewportFraming.nativeLeft(camera.getX(), camera.getWidth())
                : camera.getX()) & 0xFFFF;
    }
}
