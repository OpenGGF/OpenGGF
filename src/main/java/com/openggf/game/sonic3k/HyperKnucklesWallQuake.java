package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;

/** ROM {@code Glide_screen_shake}: twenty horizontal copied-camera samples. */
public final class HyperKnucklesWallQuake {
    private static final int[] OFFSETS = {
            1, -1, 1, -1, 2, -2, 2, -2, 3, -3,
            3, -3, 4, -4, 4, -4, 5, -5, 5, -5
    };

    public record Snapshot(int framesRemaining) {}

    private int framesRemaining;

    public void trigger() {
        framesRemaining = OFFSETS.length;
    }

    /** Applies the ROM timer-minus-one indexed sample to Camera_X_pos_copy. */
    public void updateCopiedCamera(Camera camera) {
        if (framesRemaining == 0 || camera == null) {
            return;
        }
        framesRemaining--;
        camera.setXCopy((short) (camera.getXCopy() + OFFSETS[framesRemaining]));
    }

    public boolean isActive() {
        return framesRemaining != 0;
    }

    public Snapshot capture() {
        return new Snapshot(framesRemaining);
    }

    public void restore(Snapshot snapshot) {
        framesRemaining = snapshot.framesRemaining();
    }
}
