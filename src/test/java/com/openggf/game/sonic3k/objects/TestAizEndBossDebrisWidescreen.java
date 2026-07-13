package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestAizEndBossDebrisWidescreen {
    @Test
    void debrisVisibleInWideViewportIsNotCulledAtNativeRightBoundary() {
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        when(camera.getY()).thenReturn((short) 0);
        when(camera.getWidth()).thenReturn((short) 528);
        AizEndBossDebrisChild debris = new AizEndBossDebrisChild(600, 112, 0);
        debris.setServices(new TestObjectServices().withCamera(camera));

        debris.update(0, new TestPlayableSprite());

        assertFalse(debris.isDestroyed());
    }

    @Test
    void nativeViewportRetainsRomCullBoundary() {
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        when(camera.getY()).thenReturn((short) 0);
        when(camera.getWidth()).thenReturn((short) 320);
        AizEndBossDebrisChild debris = new AizEndBossDebrisChild(600, 112, 0);
        debris.setServices(new TestObjectServices().withCamera(camera));

        debris.update(0, new TestPlayableSprite());

        assertTrue(debris.isDestroyed());
    }
}
