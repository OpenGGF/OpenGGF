package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestAizEndBossDebrisWidescreen {
    @ParameterizedTest
    @ValueSource(ints = {320, 352, 400, 528, 800})
    void debrisAtExactViewportCullBoundarySurvives(int width) {
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        when(camera.getY()).thenReturn((short) 0);
        when(camera.getWidth()).thenReturn((short) width);
        AizEndBossDebrisChild debris = new AizEndBossDebrisChild(width / 2 + width - 1, 112, 1);
        debris.setServices(new TestObjectServices().withCamera(camera));

        debris.update(0, new TestPlayableSprite());

        assertFalse(debris.isDestroyed());
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 352, 400, 528, 800})
    void debrisOnePixelPastViewportCullBoundaryIsDestroyed(int width) {
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        when(camera.getY()).thenReturn((short) 0);
        when(camera.getWidth()).thenReturn((short) width);
        AizEndBossDebrisChild debris = new AizEndBossDebrisChild(width / 2 + width, 112, 1);
        debris.setServices(new TestObjectServices().withCamera(camera));

        debris.update(0, new TestPlayableSprite());

        assertTrue(debris.isDestroyed());
    }
}
