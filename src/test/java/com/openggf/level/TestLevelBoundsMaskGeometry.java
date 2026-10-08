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
}
