package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.camera.CameraBoundaryPresentation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLevelBoundsMaskGeometry {
    @Test void earlierBoundaryRemainsAuthoritativeUntilFinalLockArrives() {
        var level = mock(LevelManager.class);
        var camera = spy(new Camera());
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
        var camera = spy(new Camera());
        doReturn((short) 800).when(camera).getWidth();
        camera.setMinX((short) 1200);
        camera.setMaxX((short) 1000);
        camera.setX((short) 760);
        level.camera = camera;
        assertFalse(LevelScrollPresentation.captureArenaMask(level).visible(800));
    }

    @Test void screenCoordinatePresentationUsesItsWindowWithoutRewritingWorldBounds() {
        var level=mock(LevelManager.class);var camera=spy(new Camera());
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
    @Test void nativeScreenProjectionUsesPublishedCopiesAndShakeAtEveryPresetWidth() {
        int[] widths={320,352,400,528,800},insets={0,16,40,104,240};
        for(int i=0;i<widths.length;i++){
            var camera=spy(new Camera());doReturn((short)widths[i]).when(camera).getWidth();
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
