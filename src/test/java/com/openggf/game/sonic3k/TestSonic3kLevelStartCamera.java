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

    @Test
    void freshDez1AddsIntroFocusBeforeHorizontalDeadzone() {
        assertEquals(320, camera.getWidth(), "The native H40 camera is 320 pixels wide");
        assertEquals(0, camera.getMinX(), "DEZ1's native minimum X is zero");

        // Get_LevelSizeStart loc_1BF70 (sonic3k.asm:38281-38300):
        // add $B0 to focus d1 before subtracting $A0, without moving the player.
        start(0x30, 0x9AC, false, Sonic3kZoneIds.ZONE_DEZ, 0, false);

        assertEquals(0x40, camera.getX() & 0xFFFF,
                "Get_LevelSizeStart must publish the intro focus before DeformBgLayer");
        assertEquals(0, camera.getMinX(),
                "The initial focus offset must not become a minimum-X boundary");
    }

    @Test
    void freshIntroFocusMatchesNativeZoneActAndCharacterPredicates() {
        int[][] unconditional = {{Sonic3kZoneIds.ZONE_DEZ, 0},
                {Sonic3kZoneIds.ZONE_HPZ, 1}};
        for (int[] identity : unconditional) {
            for (boolean knuckles : new boolean[]{false, true}) {
                start(0x30, 0x400, knuckles, identity[0], identity[1], false);
                assertEquals(0x40, camera.getX() & 0xFFFF,
                        "Fresh native intro focus applies to either player mode");
            }
        }
        for (int zone : new int[]{Sonic3kZoneIds.ZONE_CNZ, Sonic3kZoneIds.ZONE_LRZ}) {
            start(0x30, 0x400, true, zone, 0, false);
            assertEquals(0x40, camera.getX() & 0xFFFF,
                    "Knuckles has the native fresh first-act intro focus");
            start(0x30, 0x400, false, zone, 0, false);
            assertEquals(0, camera.getX(), "Sonic/Tails retain their ordinary focus");
        }
    }

    @Test
    void freshIntroFocusDoesNotLeakIntoOtherActsOrCheckpointEntries() {
        for (int[] identity : new int[][]{{Sonic3kZoneIds.ZONE_DEZ, 1},
                {Sonic3kZoneIds.ZONE_HPZ, 0}, {Sonic3kZoneIds.ZONE_CNZ, 1},
                {Sonic3kZoneIds.ZONE_LRZ, 1}, {Sonic3kZoneIds.ZONE_ICZ, 0}}) {
            start(0x30, 0x400, true, identity[0], identity[1], false);
            assertEquals(0, camera.getX(), "The exact native zone/act owns the intro focus");
        }
        for (int[] identity : new int[][]{{Sonic3kZoneIds.ZONE_DEZ, 0},
                {Sonic3kZoneIds.ZONE_HPZ, 1}, {Sonic3kZoneIds.ZONE_CNZ, 0},
                {Sonic3kZoneIds.ZONE_LRZ, 0}}) {
            start(0x30, 0x400, true, identity[0], identity[1], true);
            assertEquals(0, camera.getX(), "Saved positions bypass fresh intro focus");
        }
    }

    @Test
    void introFocusAdditionWrapsItsWordBeforeTheUnsignedDeadzoneSubtraction() {
        start(0xFF80, 0x400, false, Sonic3kZoneIds.ZONE_DEZ, 0, false);
        assertEquals(0, camera.getX(), "$FF80+$B0 wraps to $0030, then subtract borrows");
        start(0xFFFF, 0x400, false, Sonic3kZoneIds.ZONE_DEZ, 0, false);
        assertEquals(0xF, camera.getX(), "$FFFF+$B0 wraps to $00AF before subtracting $A0");
        camera.setMaxX((short) 0x20);
        start(0x30, 0x400, false, Sonic3kZoneIds.ZONE_DEZ, 0, false);
        assertEquals(0x20, camera.getX(), "The native unsigned maximum still clamps focus");
        assertEquals(0, camera.getMinX(), "Intro focus never rewrites the native minimum");
    }

    private void start(int x, int y, boolean knuckles, int zone, int act, boolean checkpoint) {
        Sonic3kLevelStartCamera.initialize(camera, x, y, knuckles, zone, act, checkpoint);
    }
}
