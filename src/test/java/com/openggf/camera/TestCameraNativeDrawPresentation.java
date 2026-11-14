package com.openggf.camera;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** NATIVE widescreen draw mode splits the simulation window from the drawn picture. */
class TestCameraNativeDrawPresentation {
    private static Camera camera(int displayWidth, String drawMode) {
        var config = mock(SonicConfigurationService.class);
        when(config.getShort(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn((short) displayWidth);
        when(config.getShort(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn((short) 224);
        when(config.getString(SonicConfiguration.WIDESCREEN_DRAW_MODE)).thenReturn(drawMode);
        return new Camera(config);
    }

    private static Camera at(Camera camera, int x, int minX, int maxX) {
        camera.setMinX((short) minX);
        camera.setMaxX((short) maxX);
        camera.setX((short) x);
        return camera;
    }

    @Test
    void edgeModeSimulatesTheWholeDisplay() {
        var camera = at(camera(800, "EDGE"), 0x1000, 0, 0x4000);
        assertEquals(800, camera.getWidth());
        assertEquals(800, camera.getPresentationWidth());
        assertFalse(camera.hasPresentationMargin());
        assertEquals(0, camera.getPresentationInset());
    }

    @Test
    void nativeModeSimulatesTheRomWindowAndCentresThePicture() {
        var camera = at(camera(800, "NATIVE"), 0x1000, 0, 0x4000);
        assertEquals(320, camera.getWidth());
        assertEquals(800, camera.getPresentationWidth());
        assertTrue(camera.hasPresentationMargin());
        assertEquals(240, camera.getPresentationInset());
        assertEquals(0x1000 - 240, camera.getPresentationXWithShake());
    }

    @Test
    void pictureSlidesInsideTheLevelAtEitherEnd() {
        // Level start: nothing left of minX is shown; the native window sits at the picture's left.
        assertEquals(0, at(camera(800, "NATIVE"), 0, 0, 0x4000).getPresentationInset());
        assertEquals(100, at(camera(800, "NATIVE"), 100, 0, 0x4000).getPresentationInset());
        // Level end: the picture stops at maxX + 320; the native window sits at its right.
        assertEquals(480, at(camera(800, "NATIVE"), 0x4000, 0, 0x4000).getPresentationInset());
    }

    @Test
    void aLockedArenaNarrowerThanThePictureIsCentred() {
        var camera = at(camera(800, "NATIVE"), 0x2C00, 0x2C00, 0x2C00);
        assertEquals(240, camera.getPresentationInset());
        var wider = at(camera(800, "NATIVE"), 0x2C00, 0x2C00, 0x2C60);
        // Centre of [0x2C00, 0x2C60 + 320) less half the picture.
        assertEquals(0x2C00 - (0x2C30 - 240), wider.getPresentationInset());
    }

    private static Camera followed(Camera camera, int playerCentreX) {
        var player = mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) playerCentreX);
        short x = camera.getX();
        camera.setFocusedSprite(player); // snaps the camera to the sprite
        camera.setX(x);
        return camera;
    }

    /**
     * Sonic_LevelBound stops the S1/S2 act-end run-off $128 + $40 past the right limit, off
     * a 320-pixel screen. Recorded-run capture draws a wider picture from the camera's left
     * edge, which still shows that spot, so the picture gives up its right margin as the
     * player crosses the extra $40 and they leave the picture as they leave the ROM screen.
     */
    @Test
    void theRunOffSlideOnlyActsAtTheRightLimit() {
        // A player ahead of a camera that is still scrolling is ordinary play.
        var scrolling = followed(at(camera(320, "EDGE"), 0x1000, 0x1000, 0x2AC0), 0x1000 + 0x168);
        assertEquals(0, CameraPresentationAccess.runOffSlide(scrolling, 80));
        // No picture right of the ROM screen: nothing to give up.
        var native320 = followed(at(camera(320, "EDGE"), 0x2AC0, 0x2AC0, 0x2AC0), 0x2AC0 + 0x168);
        assertEquals(0, CameraPresentationAccess.runOffSlide(native320, 0));
    }

    @Test
    void aLeftAnchoredWidePictureGivesUpItsWholeRightMargin() {
        int limit = 0x2AC0;
        var camera = at(camera(320, "EDGE"), limit, limit, limit);
        assertEquals(0, CameraPresentationAccess.runOffSlide(camera, 80), "no followed player");
        assertEquals(0, CameraPresentationAccess.runOffSlide(followed(camera, limit + 0x128), 80));
        assertEquals(40, CameraPresentationAccess.runOffSlide(followed(camera, limit + 0x128 + 0x20), 80));
        assertEquals(80, CameraPresentationAccess.runOffSlide(followed(camera, limit + 0x128 + 0x40), 80));
        assertEquals(limit, camera.getX(), "the gameplay camera is not moved");
    }

    @Test
    void nativeAspectHasNoMarginInEitherMode() {
        var camera = at(camera(320, "NATIVE"), 0x1000, 0, 0x4000);
        assertEquals(320, camera.getWidth());
        assertFalse(camera.hasPresentationMargin());
        assertEquals(0, camera.getPresentationInset());
    }

    @Test
    void presentationProjectionIsTheDrawnPicture() {
        var projection = at(camera(400, "NATIVE"), 0x1000, 0, 0x4000).presentationProjection();
        assertEquals(400, projection.getWidth());
        assertEquals(0x1000 - 40, projection.getX());
        assertEquals(0, projection.getPresentationInset());
    }
}
