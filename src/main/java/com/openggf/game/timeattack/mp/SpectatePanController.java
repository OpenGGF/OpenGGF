package com.openggf.game.timeattack.mp;

import com.openggf.camera.Camera;

/** Free-camera pan available after the local attempt has finished. */
public final class SpectatePanController {
    public static final int PAN_SPEED_PX = 8;
    private boolean active;

    public void update(Camera camera, boolean shouldBeActive, int dx, int dy) {
        if (camera == null) {
            return;
        }
        if (shouldBeActive && !active) {
            active = true;
            camera.setFrozen(true);
        } else if (!shouldBeActive && active) {
            active = false;
            camera.setFrozen(false);
            return;
        }
        if (!active) {
            return;
        }
        int x = clamp(camera.getX() + dx * PAN_SPEED_PX,
                camera.getMinX(), camera.getMaxX());
        int y = clamp(camera.getY() + dy * PAN_SPEED_PX,
                camera.getMinY(), camera.getMaxY());
        camera.setX((short) x);
        camera.setY((short) y);
    }

    public boolean isActive() {
        return active;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
