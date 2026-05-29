package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.camera.Camera;

/**
 * Shared S3K boss-camera gate used by bosses that call
 * {@code Check_CameraInRange}, {@code sub_85D6A}, and {@code loc_85CA4}.
 */
final class S3kSharedBossCameraGate {
    private static final int APPROACH_FROM_BELOW_Y_TOLERANCE = 0x60;

    record LockBounds(int minY, int maxY, int minX, int maxX) {
    }

    private LockBounds lockBounds;
    private boolean approachFromBelow;
    private boolean approachFromRight;
    private boolean yLocked;
    private boolean xLocked;
    private boolean musicStarted;
    private boolean complete;
    private int musicWaitTimer;

    void reset() {
        lockBounds = null;
        approachFromBelow = false;
        approachFromRight = false;
        yLocked = false;
        xLocked = false;
        musicStarted = false;
        complete = false;
        musicWaitTimer = -1;
    }

    void begin(Camera camera, LockBounds lockBounds, int musicWaitFrames) {
        this.lockBounds = lockBounds;
        approachFromBelow = camera != null && unsigned(camera.getY()) > lockBounds.minY();
        approachFromRight = camera != null && unsigned(camera.getX()) > lockBounds.minX();
        yLocked = false;
        xLocked = false;
        musicStarted = false;
        complete = false;
        musicWaitTimer = musicWaitFrames;
    }

    boolean update(Camera camera, Runnable onMusicStart) {
        if (complete) {
            return true;
        }
        if (lockBounds == null || camera == null) {
            complete = true;
            return true;
        }

        if (!musicStarted && musicWaitTimer-- <= 0) {
            musicStarted = true;
            if (onMusicStart != null) {
                onMusicStart.run();
            }
        }

        updateY(camera);
        updateX(camera);
        complete = musicStarted && yLocked && xLocked;
        return complete;
    }

    boolean isComplete() {
        return complete;
    }

    private void updateY(Camera camera) {
        if (yLocked) {
            return;
        }
        int cameraY = unsigned(camera.getY());
        if (!approachFromBelow) {
            if (cameraY >= lockBounds.minY()) {
                lockY(camera);
            } else {
                camera.setMinY((short) cameraY);
            }
            return;
        }

        if (cameraY <= lockBounds.maxY() + APPROACH_FROM_BELOW_Y_TOLERANCE) {
            lockY(camera);
        }
    }

    private void lockY(Camera camera) {
        yLocked = true;
        camera.setMinY((short) lockBounds.minY());
        camera.setMaxYTarget((short) lockBounds.maxY());
    }

    private void updateX(Camera camera) {
        if (xLocked) {
            return;
        }
        int cameraX = unsigned(camera.getX());
        if (!approachFromRight) {
            if (cameraX >= lockBounds.minX()) {
                lockX(camera);
            } else {
                camera.setMinX((short) cameraX);
            }
            return;
        }

        if (cameraX <= lockBounds.maxX()) {
            lockX(camera);
        } else {
            camera.setMaxX((short) cameraX);
        }
    }

    private void lockX(Camera camera) {
        xLocked = true;
        camera.setMinX((short) lockBounds.minX());
        camera.setMaxX((short) lockBounds.maxX());
    }

    private static int unsigned(short value) {
        return value & 0xFFFF;
    }
}
