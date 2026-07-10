package com.openggf.game.timeattack.mp;

import com.openggf.camera.Camera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSpectatePanController {
    @Test
    void activatesFreezesPansClampsAndReleases() {
        Camera camera = camera();
        SpectatePanController controller = new SpectatePanController();
        controller.update(camera, true, 1, 0);
        assertTrue(controller.isActive());
        assertTrue(camera.getFrozen());
        assertEquals(308, camera.getX());
        for (int i = 0; i < 200; i++) {
            controller.update(camera, true, 1, 1);
        }
        assertEquals(1000, camera.getX());
        assertEquals(500, camera.getY());
        controller.update(camera, false, 0, 0);
        assertFalse(controller.isActive());
        assertFalse(camera.getFrozen());
    }

    @Test
    void inactiveDoesNotTouchCamera() {
        Camera camera = camera();
        new SpectatePanController().update(camera, false, 1, 1);
        assertEquals(300, camera.getX());
        assertFalse(camera.getFrozen());
    }

    private static Camera camera() {
        Camera camera = new Camera();
        camera.setMinX((short) 0);
        camera.setMinY((short) 0);
        camera.setMaxX((short) 1000);
        camera.setMaxY((short) 500);
        camera.setX((short) 300);
        camera.setY((short) 200);
        return camera;
    }
}
