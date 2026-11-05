package com.openggf.camera;

import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeCameraInitialization {
    private Camera camera;
    private AbstractPlayableSprite player;

    @BeforeEach
    void setup() {
        TestEnvironment.resetAll();
        camera = GameServices.camera();
        player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) 0x1640);
        when(player.getCentreY()).thenReturn((short) 0x03AC);
        camera.setFocusedSprite(player);
        camera.setX((short) 0x15A0);
        camera.setY((short) 0x0320);
        camera.setMinX((short) 0x1500);
        camera.setMaxX((short) 0x1640);
        camera.setMinY((short) 0x0320);
        camera.setMaxY((short) 0x0320);
        camera.setFastScrollCap(24);
    }

    @Test
    void initialWordSubtractCannotBorrowAndOrdinaryClampIsUnchanged() {
        CameraSubpixelAccess.setXFraction(camera, 0xA400);
        var before = camera.capture();
        NativeCameraInitialization.moveBeforeLevelSetup(camera);
        assertEquals(0x0326, camera.getY());
        assertEquals(0x15A0, camera.getX());
        assertEquals(before.renderCopyX(), camera.getXCopy());
        assertEquals(before.renderCopyY(), camera.getYCopy());
        assertEquals(0xA400, CameraSubpixelAccess.xFraction(camera));
        assertEquals(before.minY(), camera.getMinY());
        assertEquals(before.maxY(), camera.getMaxY());
        camera.updatePosition();
        assertEquals(0x0320, camera.getY(), "no pre-setup semantics survive the call");
        camera.restore(before);
        camera.updatePosition();
        assertEquals(0x0320, camera.getY(), "rewind does not arm an initial dispatch");
        camera.restore(before);
        NativeCameraInitialization.moveBeforeLevelSetup(camera);
        assertEquals(0x0326, camera.getY(), "explicit initial movement replays deterministically");
    }

    @Test
    void initialMovementUsesPlayerStateRatherThanFittingAShrineValue() {
        camera.setY((short) 0x0500);
        camera.setMaxY((short) 0x0500);
        when(player.getCentreY()).thenReturn((short) 0x0600);
        NativeCameraInitialization.moveBeforeLevelSetup(camera);
        assertEquals(0x0506, camera.getY());
        camera.setY((short) 0x0500);
        when(player.getAir()).thenReturn(true);
        NativeCameraInitialization.moveBeforeLevelSetup(camera);
        assertEquals(0x0518, camera.getY());
    }

    @Test
    void scrollLockSuppressesInitialMovementAndDoesNotRetainIt() {
        camera.setScrollLocked(true);
        NativeCameraInitialization.moveBeforeLevelSetup(camera);
        assertEquals(0x0320, camera.getY());
        camera.setScrollLocked(false);
        camera.updatePosition();
        assertEquals(0x0320, camera.getY());
    }

    @Test
    void initialMaskIgnoresDestinationLoopGeometryWithoutChangingIt() {
        camera.setVerticalWrapEnabled(true, 0x800);
        camera.setMinY((short) -0x100);
        camera.setMaxY((short) 0x800);
        camera.setY((short) 0x7FE);
        when(player.getCentreY()).thenReturn((short) 0x900);
        var before = camera.capture();
        NativeCameraInitialization.moveBeforeLevelSetup(camera);
        assertEquals(0x804, camera.getY());
        assertFalse(camera.didWrapLastFrame());
        assertEquals(0x800, camera.getVerticalWrapRange());
        camera.restore(before);
        camera.updatePosition();
        assertEquals(4, camera.getY());
        assertTrue(camera.didWrapLastFrame());
    }

    @Test
    void initialUpwardMaskRetainsNegativeWord() {
        camera.setMinY((short) -0x100);
        camera.setY((short) -0xFF);
        when(player.getCentreY()).thenReturn((short) -0x200);
        NativeCameraInitialization.moveBeforeLevelSetup(camera);
        assertEquals(-0x105, camera.getY());
    }
}
