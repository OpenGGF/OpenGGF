package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic2.objects.bosses.ARZBossPillar;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The solid helpers' on-screen gate reads {@code render_flags.on_screen} as the
 * previous BuildSprites pass wrote it (S2 {@code SolidObject_OnScreenTest},
 * s2.asm:35336-35341). BuildSprites tests the object against
 * {@code Camera_X_pos_copy} / {@code Camera_Y_pos_copy}, which the zone's scroll
 * routine has already moved by the screen-shake offset (SwScrl_ARZ,
 * s2.asm:17709-17728), with the approximate 32-px Y band when the object leaves
 * {@code render_flags.explicit_height} clear (s2.asm:30580-30611).
 */
class TestSolidOnScreenRenderFlag {
    private static final int CAMERA_X = 0x2AC0;
    private static final int CAMERA_Y = 0x400;

    @AfterEach
    void resetBounds() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    @Test
    void risingArzPillarIsOnScreenOnlyThroughTheShakenCameraCopy() {
        Fixture f = new Fixture();
        // Obj89 pillar at y $501: y - Camera_Y_pos_copy = $101 lies past the
        // approximate band [-32, 256) unless the shake moved the copy down.
        ARZBossPillar pillar = f.pillarAt(0x501);

        f.camera.setShakeOffsets(0, 2);
        f.objects.refreshPostCameraRenderState();
        assertTrue(pillar.isWithinSolidContactBounds(),
                "BuildSprites saw Camera_Y_pos_copy = $402, so y - copy = $FF is inside the band");

        f.camera.setShakeOffsets(0, 0);
        f.objects.refreshPostCameraRenderState();
        assertFalse(pillar.isWithinSolidContactBounds(),
                "without the shake the same pillar is below the band");
    }

    @Test
    void gateKeepsThePreviousPassBitUntilTheNextPass() {
        Fixture f = new Fixture();
        ARZBossPillar pillar = f.pillarAt(0x4F0);
        f.objects.refreshPostCameraRenderState();
        assertTrue(pillar.isWithinSolidContactBounds());

        // The object pass and the camera step of the next frame do not rewrite
        // the bit; only the next BuildSprites pass does.
        f.camera.setY((short) 0x300);
        AbstractObjectInstance.updateCameraBounds(CAMERA_X, 0x300, CAMERA_X + 320, 0x300 + 224, 0);
        assertTrue(pillar.isWithinSolidContactBounds(),
                "the gate answers from the previous pass, not the live camera");

        f.objects.refreshPostCameraRenderState();
        assertFalse(pillar.isWithinSolidContactBounds());
    }

    @Test
    void retainedBitRoundTripsThroughRewind() {
        Fixture f = new Fixture();
        ARZBossPillar pillar = f.pillarAt(0x4F0);
        f.objects.refreshPostCameraRenderState();
        PerObjectRewindSnapshot onScreen = pillar.captureRewindState();
        assertTrue(onScreen.renderFlagPassSeen());
        assertTrue(onScreen.renderFlagOnScreen());

        f.camera.setY((short) 0x300);
        f.objects.refreshPostCameraRenderState();
        assertFalse(pillar.isWithinSolidContactBounds());

        pillar.restoreRewindState(onScreen);
        assertTrue(pillar.isWithinSolidContactBounds());
        assertEquals(onScreen.renderFlagOnScreen(), pillar.captureRewindState().renderFlagOnScreen());
    }

    private static final class Fixture {
        final Camera camera = new Camera();
        final ObjectManager objects;

        Fixture() {
            ObjectManager[] holder = new ObjectManager[1];
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
            };
            objects = new ObjectManager(List.of(), null, 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = objects;
            camera.setX((short) CAMERA_X);
            camera.setY((short) CAMERA_Y);
        }

        ARZBossPillar pillarAt(int y) {
            // Right pillar (x-flipped) at its ROM x; ObjectSpawn(x, y, id, subtype, renderFlags, ...).
            ARZBossPillar pillar = new ARZBossPillar(
                    new ObjectSpawn(0x2B70, y, 0x89, 4, 1, false, 0), null);
            objects.addDynamicObject(pillar);
            return pillar;
        }
    }
}
