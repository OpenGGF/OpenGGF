package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestHyperKnucklesWallQuake {
    @Test
    void consumesRomTableBackwardsForExactlyTwentyCopiedCameraFrames() {
        HyperKnucklesWallQuake quake = new HyperKnucklesWallQuake();
        Camera camera = new Camera();
        quake.trigger();

        int[] expected = {-5, 5, -5, 5, -4, 4, -4, 4, -3, 3,
                -3, 3, -2, 2, -2, 2, -1, 1, -1, 1};
        for (int offset : expected) {
            camera.setXCopy((short) 100);
            quake.updateCopiedCamera(camera);
            assertEquals(100 + offset, camera.getXCopy());
        }
        assertFalse(quake.isActive());
        camera.setXCopy((short) 100);
        quake.updateCopiedCamera(camera);
        assertEquals(100, camera.getXCopy());
    }

    @Test
    void rewindRestoresRemainingSequencePosition() {
        HyperKnucklesWallQuake quake = new HyperKnucklesWallQuake();
        Camera camera = new Camera();
        quake.trigger();
        quake.updateCopiedCamera(camera);
        HyperKnucklesWallQuake.Snapshot snapshot = quake.capture();
        quake.updateCopiedCamera(camera);

        quake.restore(snapshot);
        camera.setXCopy((short) 0);
        quake.updateCopiedCamera(camera);
        assertEquals(5, camera.getXCopy());
    }
}
