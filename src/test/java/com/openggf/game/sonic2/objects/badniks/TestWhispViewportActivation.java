package com.openggf.game.sonic2.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestWhispViewportActivation {

    @AfterEach
    void restoreNativeViewport() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
    }

    @Test
    void renderOverlapIsNativeExactAndWidensWithTheLiveViewport() throws Exception {
        WhispBadnikInstance whisp = new WhispBadnikInstance(
                new ObjectSpawn(700, 100, 0x8C, 0, 0, false, 0));
        whisp.setServices(new TestObjectServices().withCamera(cameraAtOrigin()));

        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        assertFalse(computeOnScreen(whisp), "x=700 is outside the native 320px Render_Sprites window");

        AbstractObjectInstance.updateCameraBounds(0, 0, 800, 224, 0);
        assertTrue(computeOnScreen(whisp), "x=700 is visible in an 800px viewport and must activate");
    }

    private static boolean computeOnScreen(WhispBadnikInstance whisp) throws Exception {
        Method method = WhispBadnikInstance.class.getDeclaredMethod("computeOnScreen");
        method.setAccessible(true);
        return (boolean) method.invoke(whisp);
    }

    private static Camera cameraAtOrigin() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
        };
    }
}
