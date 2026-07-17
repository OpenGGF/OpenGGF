package com.openggf.camera;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.rewind.snapshot.CameraSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCameraRewindSnapshot {
    private Camera camera;

    @BeforeEach
    void setUp() {
        camera = new Camera(SonicConfigurationService.getInstance());
    }

    @Test
    void testCameraSnapshotRoundTrip() {
        // Set up initial state
        camera.setX((short) 100);
        camera.setY((short) 200);
        camera.setXCopy((short) 0x1234);
        camera.setYCopy((short) 0x0567);
        camera.setMinX((short) 0);
        camera.setMinY((short) 0);
        camera.setMaxX((short) 2000);
        camera.setMaxY((short) 1000);
        camera.setShakeOffsets(5, 10);
        camera.setMinXTarget((short) 50);
        camera.setMinYTarget((short) 25);
        camera.setMaxXTarget((short) 1900);
        camera.setMaxYTarget((short) 950);
        camera.setHorizScrollDelay(8);
        camera.setFrozen(true);
        camera.setLevelStarted(false);
        camera.setVerticalWrapEnabled(true, 0x800);
        camera.setYPosBias((short) 150);
        camera.setFastScrollCap(24);

        // Capture snapshot
        CameraSnapshot snapshot = camera.capture();

        // Mutate state
        camera.setX((short) 500);
        camera.setY((short) 600);
        camera.setXCopy((short) 0x2222);
        camera.setYCopy((short) 0x0333);
        camera.setMinX((short) 100);
        camera.setFrozen(false);
        camera.setLevelStarted(true);
        camera.setYPosBias((short) 96);

        // Restore from snapshot
        camera.restore(snapshot);

        // Verify all fields match captured state
        assertEquals((short) 100, camera.getX());
        assertEquals((short) 200, camera.getY());
        assertEquals((short) 0x1234, camera.getXCopy());
        assertEquals((short) 0x0567, camera.getYCopy());
        assertEquals((short) 0, camera.getMinX());
        assertEquals((short) 0, camera.getMinY());
        assertEquals((short) 2000, camera.getMaxX());
        assertEquals((short) 1000, camera.getMaxY());
        assertEquals(5, camera.getShakeOffsetX());
        assertEquals(10, camera.getShakeOffsetY());
        assertEquals((short) 50, camera.getMinXTarget());
        assertEquals((short) 25, camera.getMinYTarget());
        assertEquals((short) 1900, camera.getMaxXTarget());
        assertEquals((short) 950, camera.getMaxYTarget());
        assertEquals(8, camera.getHorizScrollDelay());
        assertTrue(camera.getFrozen());
        assertFalse(camera.isLevelStarted());
        assertTrue(camera.isVerticalWrapEnabled());
        assertEquals(0x800, camera.getVerticalWrapRange());
        assertEquals((short) 150, camera.getYPosBias());
        assertEquals(24, camera.getFastScrollCap());
    }

    @Test
    void testCameraSnapshotKey() {
        assertEquals("camera", camera.key());
    }

    @Test
    void testBoundaryEasingState() {
        camera.setMaxY((short) 1000);
        camera.setMaxYTarget((short) 800);
        // Mark maxY as changing
        camera.updateBoundaryEasing();

        CameraSnapshot snapshot = camera.capture();
        camera.setMaxY((short) 1500); // Change state

        camera.restore(snapshot);
        // Verify easing state is restored
        assertEquals(snapshot.maxYChanging(), camera.isMaxYChanging());
    }

    @Test
    void screenEventCopyWordsAreIndependentFromLivePositionAndResetExplicitly() {
        camera.setX((short) 0x2EA0);
        camera.setY((short) 0x0540);
        camera.setXCopy((short) 0x1111);
        camera.setYCopy((short) 0x0222);

        camera.setX((short) 0x2EA2);
        camera.setY((short) 0x0542);
        assertEquals(0x1111, camera.getXCopy() & 0xFFFF);
        assertEquals(0x0222, camera.getYCopy() & 0xFFFF);

        camera.copyLivePositionToScreenEventWords();
        assertEquals(0x2EA2, camera.getXCopy() & 0xFFFF);
        assertEquals(0x0542, camera.getYCopy() & 0xFFFF);

        camera.resetState();
        assertEquals(0, camera.getXCopy());
        assertEquals(0, camera.getYCopy());
    }
}
