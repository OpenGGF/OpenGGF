package com.openggf.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestLevelBoundsMaskGeometry {
    @Test void destinationIsUsedOnlyWhenTheWholeBodyIsInside() {
        var target = new LevelBoundsMaskGeometry.Bounds(1000, 1000);
        assertEquals(target, target.protectPlayer(800, 1200, 1000, 1048));
        assertEquals(new LevelBoundsMaskGeometry.Bounds(800, 1000),
                target.protectPlayer(800, 1200, 999, 1047));
        assertEquals(new LevelBoundsMaskGeometry.Bounds(1000, 1200),
                target.protectPlayer(800, 1200, 1290, 1338));
    }
    @Test void returningPlayerChangesSelectionImmediatelyAndCurrentBoundCanCatchUp() {
        var target = new LevelBoundsMaskGeometry.Bounds(1000, 1000);
        assertEquals(1000, target.protectPlayer(800, 1200, 1040, 1088).minX());
        assertEquals(800, target.protectPlayer(800, 1200, 980, 1028).minX());
        assertEquals(940, target.protectPlayer(940, 1200, 980, 1028).minX());
        assertEquals(1000, target.protectPlayer(1000, 1000, 1016, 1064).minX());
    }
    @Test void secondParticipantProtectsItsOwnEdgeAndExpandingBoundsRemainOpen() {
        var target = new LevelBoundsMaskGeometry.Bounds(1000, 1000);
        var leader = target.protectPlayer(800, 1200, 980, 1028);
        assertEquals(new LevelBoundsMaskGeometry.Bounds(800, 1200),
                leader.protectPlayer(800, 1200, 1300, 1348));
        var opening = new LevelBoundsMaskGeometry.Bounds(600, 1400);
        assertEquals(opening, opening.protectPlayer(800, 1200, 700, 748));
    }
    @Test void productionProjectionUsesRenderedBodyAndActiveParticipants() {
        var level = org.mockito.Mockito.mock(LevelManager.class);
        var camera = org.mockito.Mockito.spy(new com.openggf.camera.Camera());
        org.mockito.Mockito.doReturn((short) 800).when(camera).getWidth();
        camera.setX((short) 760);
        camera.setMinX((short) 800);
        camera.setMaxX((short) 1200);
        com.openggf.camera.CameraBoundaryPresentation.approach(camera, 1000, 1000);
        var leader = player(1040);
        camera.setFocusedSprite(leader);
        camera.setX((short) 760); // Attaching a focus performs an initial camera update.
        level.camera = camera;
        level.spriteManager = org.mockito.Mockito.mock(com.openggf.sprites.managers.SpriteManager.class);
        assertEquals(240, LevelScrollPresentation.captureArenaMask(level).left());
        var follower = player(1000);
        org.mockito.Mockito.when(follower.isCpuControlled()).thenReturn(true);
        org.mockito.Mockito.when(level.spriteManager.getAllSprites()).thenReturn(java.util.List.of(leader, follower));
        org.mockito.Mockito.when(level.spriteManager.getSidekicks()).thenReturn(java.util.List.of(follower));
        assertEquals(40, LevelScrollPresentation.captureArenaMask(level).left());
        org.mockito.Mockito.when(level.spriteManager.getSidekicks()).thenReturn(java.util.List.of());
        assertEquals(240, LevelScrollPresentation.captureArenaMask(level).left(),
                "suppressed follower must not hold the mask open");
        org.mockito.Mockito.when(leader.getRenderCentreX()).thenReturn((short) 1023);
        assertEquals(40, LevelScrollPresentation.captureArenaMask(level).left(),
                "centre is inside but the rendered body still overlaps transition space");
        com.openggf.camera.CameraBoundaryPresentation.approach(camera, 1200, 1000);
        assertFalse(LevelScrollPresentation.captureArenaMask(level).visible(800),
                "participant selection must not normalize an inverted domain");
    }
    private static com.openggf.sprites.playable.AbstractPlayableSprite player(int x) {
        var p = org.mockito.Mockito.mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        org.mockito.Mockito.when(p.getRenderCentreX()).thenReturn((short) x);
        org.mockito.Mockito.when(p.getWidth()).thenReturn(32);
        org.mockito.Mockito.when(p.getRenderFlagWidthPixels()).thenReturn(24);
        return p;
    }
}
