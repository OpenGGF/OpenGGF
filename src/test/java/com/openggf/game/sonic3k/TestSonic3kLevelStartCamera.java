package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSonic3kLevelStartCamera {
    private Camera camera;

    @BeforeEach
    void setup() throws Exception {
        TestEnvironment.resetAll();
        camera = GameServices.camera();
        camera.setMaxX((short) 0x6000);
        camera.setMaxY((short) 0x1000);
    }

    @Test
    void aizStartsBelowMinimumWithoutChangingRuntimeBoundary() {
        camera.setMinX((short) 0x1308);
        camera.setMaxY((short) 0x390);
        start(0x13A0, 0x400, true, Sonic3kZoneIds.ZONE_AIZ, 0, false);
        assertEquals(0x1300, camera.getX());
        assertEquals(0x390, camera.getY());
        assertEquals(0x1308, camera.getMinX());
    }

    @Test
    void loadUsesZeroInsteadOfPositiveOrNegativeMinimum() {
        camera.setMinX((short) 0x100);
        camera.setMinY((short) 0x580);
        start(0x40, 0x40, false, Sonic3kZoneIds.ZONE_CNZ, 1, true);
        assertEquals(0, camera.getX());
        assertEquals(0, camera.getY());
        camera.setMinY((short) -0x100);
        start(0x40, 0x40, false, Sonic3kZoneIds.ZONE_MGZ, 0, false);
        assertEquals(0, camera.getY());
    }

    @Test
    void maximumStillClampsBothAxes() {
        camera.setMaxX((short) 0x200);
        camera.setMaxY((short) 0x100);
        start(0x400, 0x400, false, Sonic3kZoneIds.ZONE_AIZ, 0, true);
        assertEquals(0x200, camera.getX());
        assertEquals(0x100, camera.getY());
    }

    @Test
    void mhzFocusOverrideIsColdAndNonKnucklesOnly() {
        camera.setMinX((short) 0xC0);
        start(0x60, 0x400, false, Sonic3kZoneIds.ZONE_MHZ, 0, false);
        assertEquals(0xC0, camera.getX());
        start(0x60, 0x400, false, Sonic3kZoneIds.ZONE_MHZ, 0, false);
        assertEquals(0xC0, camera.getX());
        start(0x60, 0x400, true, Sonic3kZoneIds.ZONE_MHZ, 0, false);
        assertEquals(0, camera.getX());
        start(0x60, 0x400, false, Sonic3kZoneIds.ZONE_MHZ, 0, true);
        assertEquals(0, camera.getX());
    }

    @Test
    void mhzCameraUsesCharacterIdentityIndependentlyOfInstanceName() {
        camera.setMinX((short) 0xC0);
        var provider = new Sonic3kZoneFeatureProvider();
        var knuckles = new com.openggf.sprites.playable.Knuckles(
                "renamed-leader", (short) 0x60, (short) 0x400);
        provider.initializeLevelStartCamera(camera, knuckles, Sonic3kZoneIds.ZONE_MHZ, 0, false);
        assertEquals(0, camera.getX(), "Knuckles bypasses the Sonic/Tails focus override");
        var sonic = new com.openggf.sprites.playable.Sonic(
                "knuckles", (short) 0x60, (short) 0x400);
        provider.initializeLevelStartCamera(camera, sonic, Sonic3kZoneIds.ZONE_MHZ, 0, false);
        assertEquals(0xC0, camera.getX(), "An instance name cannot select Knuckles' branch");
    }

    @Test
    void verticalMaximumComparisonUsesSignedSubtractionWord() {
        camera.setMaxY((short) 0x1000);
        start(0x200, 0x8060, true, Sonic3kZoneIds.ZONE_MGZ, 0, true);
        assertEquals((short) 0x8000, camera.getY(),
                "loc_1BF9C uses signed blt, so a negative result remains below maxY");
        camera.setMaxY((short) -0x100);
        start(0x200, 0x400, true, Sonic3kZoneIds.ZONE_MGZ, 0, true);
        assertEquals(-0x100, camera.getY(), "The maximum word is signed too");
    }

    private void start(int x, int y, boolean knuckles, int zone, int act, boolean checkpoint) {
        Sonic3kLevelStartCamera.initialize(camera, x, y, knuckles, zone, act, checkpoint);
    }
}
