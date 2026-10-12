package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.camera.DeadzoneGeometry;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;

/** Get_LevelSizeStart's camera focus registers, before ScreenInit runs. */
final class Sonic3kLevelStartCamera {
    private Sonic3kLevelStartCamera() { }

    static void initialize(Camera camera, int playerX, int playerY, boolean knuckles,
                           int zone, int act, boolean retainedPlayerStart) {
        int focusX = playerX;
        // loc_1BE46: restored starpost/Saved2 positions bypass every intro override.
        // An admitted module entry retains its centre through this same camera handoff.
        if (!retainedPlayerStart) {
            // loc_1BF1E: locked-on MHZ1 changes d1, not the player's x_pos.
            if (zone == Sonic3kZoneIds.ZONE_MHZ && act == 0 && !knuckles) {
                focusX = 0x160;
            }
        }
        // loc_1BF74: zero/max saturation, with no Camera_min_X/Y_pos clamp.
        // Widescreen projection retains the existing native-arena ownership.
        var framing = com.openggf.game.internal.NativeArenaCameraFraming.current();
        int width = camera.getWidth();
        int inset = framing != null && framing.centerNativeArenaCamera()
                ? com.openggf.camera.NativeViewportFraming.inset(width) : 0;
        int initialX = Math.max(-inset, (focusX & 0xFFFF) - DeadzoneGeometry.rightEdge(width));
        int x = Math.min(initialX, (camera.getMaxX() & 0xFFFF) - inset);
        if (width > 320 && framing != null) {
            var lock = framing.lockedNativeHorizontalCamera();
            if (lock.isPresent()) {
                x = com.openggf.camera.NativeViewportFraming.visibleLeft(lock.getAsInt(), width);
            }
        }
        int initialY = Math.max(0, (playerY & 0xFFFF) - 96);
        camera.setX((short) x);
        camera.setY((short) Math.min((short) initialY, camera.getMaxY()));
    }
}
