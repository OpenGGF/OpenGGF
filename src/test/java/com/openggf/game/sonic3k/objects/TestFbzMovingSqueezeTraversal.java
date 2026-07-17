package com.openggf.game.sonic3k.objects;

import com.openggf.game.GroundMode;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestFbzMovingSqueezeTraversal {

    @Test
    void exactLiveFlatButtonLatchIsLaunchFloorAuthority() {
        Sonic3kButtonObjectInstance button = new Sonic3kButtonObjectInstance(
                new ObjectSpawn(0x1D38, 0x077A, 0x33, 0x20,
                        0, false, 273));
        button.snapshotPreUpdatePosition();
        AbstractPlayableSprite player = ordinaryFlatPlayer();
        int surfaceY = button.getY() + button.getSolidParams().offsetY()
                - button.getSolidParams().groundHalfHeight();
        when(player.isOnObject()).thenReturn(true);
        when(player.getLatchedSolidObjectInstance()).thenReturn(button);
        when(player.getCentreX()).thenReturn((short) button.getX());
        when(player.getCentreY()).thenReturn((short) (surfaceY - 0x13 - 1));
        when(player.getStandYRadius()).thenReturn((short) 0x13);
        when(player.getYRadius()).thenReturn((short) 0x13);

        assertTrue(FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player));
    }

    @Test
    void rollingButtonLatchUsesLiveRadiusAndPreservedFeet() {
        Sonic3kButtonObjectInstance button = liveButton();
        AbstractPlayableSprite player = ordinaryFlatPlayer();
        int surfaceY = button.getY() + button.getSolidParams().offsetY()
                - button.getSolidParams().groundHalfHeight();
        when(player.isOnObject()).thenReturn(true);
        when(player.getLatchedSolidObjectInstance()).thenReturn(button);
        when(player.getCentreX()).thenReturn((short) button.getX());
        when(player.getCentreY()).thenReturn((short) (surfaceY - 0x0E - 1));
        when(player.getStandYRadius()).thenReturn((short) 0x13);
        when(player.getYRadius()).thenReturn((short) 0x0E);
        when(player.getRolling()).thenReturn(true);

        assertTrue(FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player));
    }

    @Test
    void retainedButtonRideAtMathematicalSurfaceIsLaunchFloorAuthority() {
        Sonic3kButtonObjectInstance button = liveButton();
        AbstractPlayableSprite player = ordinaryFlatPlayer();
        int surfaceY = button.getY() + button.getSolidParams().offsetY()
                - button.getSolidParams().groundHalfHeight();
        when(player.isOnObject()).thenReturn(true);
        when(player.getLatchedSolidObjectInstance()).thenReturn(button);
        when(player.getCentreX()).thenReturn((short) button.getX());
        when(player.getCentreY()).thenReturn((short) (surfaceY - 0x0E));
        when(player.getStandYRadius()).thenReturn((short) 0x13);
        when(player.getYRadius()).thenReturn((short) 0x0E);
        when(player.getRolling()).thenReturn(true);

        assertTrue(FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player));
    }

    @Test
    void buttonLatchTwoPixelsAboveSurfaceIsNotLaunchFloorAuthority() {
        Sonic3kButtonObjectInstance button = liveButton();
        AbstractPlayableSprite player = ordinaryFlatPlayer();
        int surfaceY = button.getY() + button.getSolidParams().offsetY()
                - button.getSolidParams().groundHalfHeight();
        when(player.isOnObject()).thenReturn(true);
        when(player.getLatchedSolidObjectInstance()).thenReturn(button);
        when(player.getCentreX()).thenReturn((short) button.getX());
        when(player.getCentreY()).thenReturn((short) (surfaceY - 0x0E - 2));
        when(player.getStandYRadius()).thenReturn((short) 0x13);
        when(player.getYRadius()).thenReturn((short) 0x0E);
        when(player.getRolling()).thenReturn(true);

        assertFalse(FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player));
    }

    @Test
    void buttonLatchOnePixelBelowSurfaceIsNotLaunchFloorAuthority() {
        Sonic3kButtonObjectInstance button = liveButton();
        AbstractPlayableSprite player = ordinaryFlatPlayer();
        int surfaceY = button.getY() + button.getSolidParams().offsetY()
                - button.getSolidParams().groundHalfHeight();
        when(player.isOnObject()).thenReturn(true);
        when(player.getLatchedSolidObjectInstance()).thenReturn(button);
        when(player.getCentreX()).thenReturn((short) button.getX());
        when(player.getCentreY()).thenReturn((short) (surfaceY - 0x0E + 1));
        when(player.getStandYRadius()).thenReturn((short) 0x13);
        when(player.getYRadius()).thenReturn((short) 0x0E);
        when(player.getRolling()).thenReturn(true);

        assertFalse(FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player));
    }

    @Test
    void arbitraryObjectLatchIsNotLaunchFloorAuthority() {
        AbstractPlayableSprite player = ordinaryFlatPlayer();
        when(player.isOnObject()).thenReturn(true);
        when(player.getLatchedSolidObjectInstance()).thenReturn(mock(ObjectInstance.class));

        assertFalse(FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player));
    }

    private static AbstractPlayableSprite ordinaryFlatPlayer() {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getGroundMode()).thenReturn(GroundMode.GROUND);
        when(player.getAngle()).thenReturn((byte) 0);
        return player;
    }

    private static Sonic3kButtonObjectInstance liveButton() {
        Sonic3kButtonObjectInstance button = new Sonic3kButtonObjectInstance(
                new ObjectSpawn(0x1D38, 0x077A, 0x33, 0x20,
                        0, false, 273));
        button.snapshotPreUpdatePosition();
        return button;
    }

    @Test
    void terrainApproachPairsOnlyOverlappingHorizontalSupport() {
        assertTrue(FbzMovingSqueezeTraversal.spansOverlap(100, 140, 120, 180));
        assertTrue(FbzMovingSqueezeTraversal.spansOverlap(100, 140, 140, 180));
        assertFalse(FbzMovingSqueezeTraversal.spansOverlap(100, 139, 140, 180));
    }

    @Test
    void terrainFeetAcquireOnlyAReachableCarSurfaceBelowThem() {
        assertFalse(FbzMovingSqueezeTraversal.canAcquireCarSurface(0x780, 0x77F, 0x23));
        assertTrue(FbzMovingSqueezeTraversal.canAcquireCarSurface(0x780, 0x780, 0x23));
        assertTrue(FbzMovingSqueezeTraversal.canAcquireCarSurface(0x780, 0x7A3, 0x23));
        assertFalse(FbzMovingSqueezeTraversal.canAcquireCarSurface(0x780, 0x7A4, 0x23));
    }

    @Test
    void clearanceRequiresActualCarAcquisitionAndOverlapTraversal() {
        assertFalse(new FbzMovingSqueezeTraversal.Projection(
                true, true, false, false, false,
                0x1D90, 0x30, 0x0800, 0x1E00).clears());
        assertFalse(new FbzMovingSqueezeTraversal.Projection(
                true, true, true, false, false,
                0x1D90, 0x30, 0x0800, 0x1E00).clears());
        assertTrue(new FbzMovingSqueezeTraversal.Projection(
                true, true, true, true, false,
                0x1D90, 0x30, 0x0800, 0x1E00).clears());
    }
}
