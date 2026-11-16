package com.openggf.game.soniccd.objects.common;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestCdWideWindowing {
    @org.junit.jupiter.api.BeforeEach void reset() {
        com.openggf.tests.TestEnvironment.resetAll();
        GameServices.configuration().setSessionOverride(SonicConfiguration.WIDESCREEN_DRAW_MODE, "edge");
    }
    @org.junit.jupiter.api.AfterEach void cleanup() { com.openggf.tests.TestEnvironment.resetAll(); }

    private static final class Probe extends CdObject {
        Probe(int x) { super(new ObjectSpawn(x, 80, 1, 0, 0, false, 0), "probe"); width = 8; yRadius = 8; }
        @Override public void update(int tick, com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
    }

    @Test void drawAndTouchReachWideRightEdge() throws Exception {
        var config = GameServices.configuration();
        Object old = config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        try {
            var draw = CdObject.class.getDeclaredMethod("drawObjectOnScreen", int.class, int.class);
            draw.setAccessible(true);
            for (int width : new int[] {320, 400, 528}) {
                config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
                GameServices.camera().refreshViewportDimensions(config);
                assertEquals(width > 320, draw.invoke(new Probe(360), 0, 0), "width=" + width);
                assertTrue((boolean) draw.invoke(new Probe(width + 7), 0, 0));
                assertFalse((boolean) draw.invoke(new Probe(width + 8), 0, 0));
            }
        } finally { config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, old); GameServices.camera().refreshViewportDimensions(config); }
    }

    @Test void unloadRetainsVisibleWideObjects() {
        var config = GameServices.configuration();
        int old = config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        try {
            for (int width : new int[] {320, 400, 528}) {
                config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
                GameServices.camera().refreshViewportDimensions(config);
                assertEquals(width < 528, CdObject.outsideDespawnRange(0x1280, 0x1000, false));
                assertFalse(CdObject.outsideDespawnRange(0, 0x1000, true));
                assertTrue(CdObject.outsideDespawnRange(0x800, 0x1000, false));
            }
        } finally { config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, old); GameServices.camera().refreshViewportDimensions(config); }
    }
    @Test void registryLoadsNativeChunksWithCappedWideLead() {
        var strategy = new com.openggf.game.soniccd.objects.SonicCdObjectRegistry().objectWindowingStrategy();
        assertInstanceOf(com.openggf.level.objects.ViewportAwareObjectWindowingStrategy.class, strategy);
        var wide = (com.openggf.level.objects.ViewportAwareObjectWindowingStrategy) strategy;
        for (int width : new int[] {320, 400, 528, 800}) {
            assertEquals(0x1000 + Math.max(640, width + 128), wide.loadWindowForwardEdge(0x1040, width));
            assertEquals(0xF80, wide.loadWindowLeftTrimEdge(0x1040, width));
        }
        assertFalse(strategy.overridesUnloadWindow(), "CD routines own deletion");
    }

    @Test void bossStartCentresOnSimulationWidth() {
        for (int width : new int[] {320, 400, 528}) {
            var camera = org.mockito.Mockito.mock(com.openggf.camera.Camera.class);
            var player = org.mockito.Mockito.mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
            org.mockito.Mockito.when(camera.getWidth()).thenReturn((short) width);
            org.mockito.Mockito.when(camera.getMaxX()).thenReturn((short) 2000);
            org.mockito.Mockito.when(player.getCentreX()).thenReturn((short) 1000);
            assertTrue(com.openggf.game.soniccd.objects.boss.CdBossLevelEvents.checkBossStart(
                    new com.openggf.game.soniccd.objects.boss.CdBossLevelEvents.Context(null, camera, player, null), 900, 224));
            org.mockito.Mockito.verify(camera).setMinX((short) (1000 - width / 2));
        }
    }

}
