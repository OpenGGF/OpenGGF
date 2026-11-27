package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.camera.CameraBoundaryPresentation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLevelBoundsMaskGeometry {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path configDir;

    /** Built-in defaults: the worktree's config.yaml is the user's own and may be widescreen. */
    private com.openggf.configuration.SonicConfigurationService defaultConfig() {
        return com.openggf.configuration.SonicConfigurationService.createStandalone(configDir);
    }

    @Test void earlierBoundaryRemainsAuthoritativeUntilFinalLockArrives() {
        var level = mock(LevelManager.class);
        var camera = spy(new Camera(defaultConfig()));
        doReturn((short) 800).when(camera).getWidth();
        camera.setMinX((short) 800);
        camera.setMaxX((short) 1200);
        CameraBoundaryPresentation.approach(camera, 1000, 1000);
        var leader = mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        when(leader.getRenderCentreX()).thenReturn((short) 1100);
        camera.setFocusedSprite(leader);
        camera.setX((short) 760);
        level.camera = camera;
        assertEquals(40, LevelScrollPresentation.captureArenaMask(level).left(),
                "entering the destination must not hide the earlier playable strip");
        assertEquals(760, LevelScrollPresentation.captureArenaMask(level).right());
        camera.setMinX((short) 940);
        CameraBoundaryPresentation.approach(camera, 1000, 1000);
        assertEquals(180, LevelScrollPresentation.captureArenaMask(level).left());
        camera.setMinX((short) 1000);
        camera.setMaxX((short) 1000);
        assertEquals(240, LevelScrollPresentation.captureArenaMask(level).left());
        assertEquals(560, LevelScrollPresentation.captureArenaMask(level).right());
    }

    @Test void invertedCurrentDomainRemainsUnmasked() {
        var level = mock(LevelManager.class);
        var camera = spy(new Camera(defaultConfig()));
        doReturn((short) 800).when(camera).getWidth();
        camera.setMinX((short) 1200);
        camera.setMaxX((short) 1000);
        camera.setX((short) 760);
        level.camera = camera;
        assertFalse(LevelScrollPresentation.captureArenaMask(level).visible(800));
    }

    @Test void screenCoordinatePresentationUsesItsWindowWithoutRewritingWorldBounds() {
        var level=mock(LevelManager.class);var camera=spy(new Camera(defaultConfig()));
        doReturn((short)800).when(camera).getWidth();
        camera.setMinX((short)459);camera.setMaxX((short)459);camera.setX((short)0);
        level.camera=camera;
        var provider=mock(com.openggf.game.ZoneFeatureProvider.class,
                withSettings().extraInterfaces(com.openggf.game.internal.NativeArenaCameraFraming.class));
        when(level.getZoneFeatureProvider()).thenReturn(provider);
        var framing=(com.openggf.game.internal.NativeArenaCameraFraming)provider;
        when(framing.presentationNativeHorizontalOrigin()).thenReturn(java.util.OptionalInt.of(240));
        var mask=LevelScrollPresentation.captureArenaMask(level);
        assertEquals(240,mask.left());assertEquals(560,mask.right());
        assertEquals(459,camera.getMinX());assertEquals(459,camera.getMaxX());assertEquals(0,camera.getX());
        when(framing.presentationNativeHorizontalOrigin()).thenReturn(java.util.OptionalInt.empty());
        mask=LevelScrollPresentation.captureArenaMask(level);
        assertEquals(459,mask.left());assertEquals(779,mask.right());
    }
    @Test void sceneOwningItsWidescreenMarginsIsNotMaskedAtLockedNativeBounds() {
        var level=mock(LevelManager.class);var camera=spy(new Camera(defaultConfig()));
        doReturn((short)800).when(camera).getWidth();
        // A lock to one native window (min == max) masks both 240px wings by default.
        camera.setMinX((short)0x500);camera.setMaxX((short)0x500);camera.setX((short)(0x500-240));
        level.camera=camera;
        var provider=mock(com.openggf.game.ZoneFeatureProvider.class,
                withSettings().extraInterfaces(com.openggf.game.internal.NativeArenaCameraFraming.class));
        when(level.getZoneFeatureProvider()).thenReturn(provider);
        var framing=(com.openggf.game.internal.NativeArenaCameraFraming)provider;
        when(framing.presentationNativeHorizontalOrigin()).thenReturn(java.util.OptionalInt.empty());
        var mask=LevelScrollPresentation.captureArenaMask(level);
        assertEquals(240,mask.left());assertEquals(560,mask.right());
        when(framing.ownsWidescreenMargins()).thenReturn(true);
        for(int x:new int[]{-0x8000,0,0x500-240,0x7FFF}){
            camera.setX((short)x);
            assertFalse(LevelScrollPresentation.captureArenaMask(level).visible(800),"camera "+x);
        }
        assertEquals(0x500,camera.getMinX());assertEquals(0x500,camera.getMaxX());
    }
    /**
     * AIZ2_DoShipLoop (sonic3k.asm:105251-105272): the camera advances 4px a frame, both
     * bounds equal it, and at Events_bg+$02 everything moves back $200. In a 400px picture
     * the native window sits 40px in. Without the auto-scroll exemption the world-anchored
     * fade trails into the native window, and the wrap (a jump wider than the picture)
     * drops the fade history and snaps both margins to opaque static.
     */
    @Test void autoScrollLockFollowingTheCameraNeverMasksTheMarginsAcrossItsLoopWrap() {
        assertTrue(maxShipLoopMaskOpacity(false, 40, 360) > 0.5f,
                "negative control: the moving lock must darken the native window without the exemption");
        assertEquals(1f, maxShipLoopWrapMarginOpacity(false), 0.0001f,
                "negative control: the wrap must snap the margins opaque without the exemption");
        assertEquals(0f, maxShipLoopMaskOpacity(true, 0, 400), "auto-scroll lock must leave the whole picture unmasked");
        assertEquals(0f, maxShipLoopWrapMarginOpacity(true));
    }

    private float maxShipLoopMaskOpacity(boolean followsAutoScroll, int fromX, int toX) {
        float max = 0;
        for (float[] row : shipLoopSamples(followsAutoScroll))
            for (int x = fromX; x < toX; x++) max = Math.max(max, row[x]);
        return max;
    }

    private float maxShipLoopWrapMarginOpacity(boolean followsAutoScroll) {
        float[] wrap = shipLoopSamples(followsAutoScroll).get(SHIP_LOOP_WRAP_FRAME);
        float max = 0;
        for (int x = 0; x < 40; x++) max = Math.max(max, Math.max(wrap[x], wrap[399 - x]));
        return max;
    }

    private static final int SHIP_LOOP_WRAP_FRAME = 60;

    private java.util.List<float[]> shipLoopSamples(boolean followsAutoScroll) {
        // NATIVE 400px draw mode from an isolated configuration, never the user's config.yaml.
        var config = defaultConfig();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS, 400);
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.WIDESCREEN_DRAW_MODE, "NATIVE");
        var camera = new Camera(config);
        assertEquals(320, camera.getWidth());
        assertEquals(400, com.openggf.camera.CameraPresentationAccess.width(camera));
        var level = mock(LevelManager.class);
        level.camera = camera;
        var provider = mock(com.openggf.game.ZoneFeatureProvider.class,
                withSettings().extraInterfaces(com.openggf.game.internal.NativeArenaCameraFraming.class));
        when(level.getZoneFeatureProvider()).thenReturn(provider);
        var framing = (com.openggf.game.internal.NativeArenaCameraFraming) provider;
        when(framing.presentationNativeHorizontalOrigin()).thenReturn(java.util.OptionalInt.empty());
        when(framing.nativeBoundsFollowAutoScroll()).thenReturn(followsAutoScroll);
        var transition = new LevelBoundsMaskTransition();
        var samples = new java.util.ArrayList<float[]>();
        int nativeX = 0x4440 - 4 * (SHIP_LOOP_WRAP_FRAME + 1);
        for (int frame = 0; frame < 120; frame++) {
            nativeX += 4;
            if (nativeX >= 0x4440) nativeX -= 0x200;
            camera.setMinX((short) nativeX);
            camera.setMaxX((short) nativeX);
            camera.setX((short) nativeX);
            camera.copyLivePositionToScreenEventWords();
            assertEquals(40, com.openggf.camera.CameraPresentationAccess.inset(camera));
            level.frameCounter = frame;
            // LevelSpritePresentation.prepare's call.
            transition.advance(LevelScrollPresentation.captureArenaMask(level),
                    com.openggf.camera.CameraPresentationAccess.leftWithShake(camera),
                    com.openggf.camera.CameraPresentationAccess.width(camera));
            float[] row = new float[400];
            for (int x = 0; x < 400; x++) row[x] = transition.sample().opacityAt(x);
            samples.add(row);
            assertEquals(nativeX, camera.getMinX());
            assertEquals(nativeX, camera.getMaxX());
        }
        return samples;
    }

    @Test void nativeScreenProjectionUsesPublishedCopiesAndShakeAtEveryPresetWidth() {
        int[] widths={320,352,400,528,800},insets={0,16,40,104,240};
        for(int i=0;i<widths.length;i++){
            var camera=spy(new Camera(defaultConfig()));doReturn((short)widths[i]).when(camera).getWidth();
            camera.setX((short)0);camera.setY((short)204);
            camera.setXCopy((short)608);camera.setYCopy((short)269);camera.setShakeOffsets(3,-2);
            assertEquals(611+insets[i],com.openggf.game.sonic3k.ending.EndingScreenProjection.nativeHorizontalOrigin(camera));
            assertEquals(128+insets[i],com.openggf.game.sonic3k.ending.EndingScreenProjection.worldX(camera,256)-camera.getXWithShake());
            assertEquals(96,com.openggf.game.sonic3k.ending.EndingScreenProjection.worldY(camera,224)-camera.getYWithShake());
            assertEquals(0,camera.getX());assertEquals(204,camera.getY());
            assertEquals(608,camera.getXCopy());assertEquals(269,camera.getYCopy());
        }
    }
}
