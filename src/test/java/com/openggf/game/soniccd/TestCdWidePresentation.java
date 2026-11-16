package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.warp.CdWarpCutscene;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestCdWidePresentation {
    @org.junit.jupiter.api.BeforeEach void reset() { com.openggf.tests.TestEnvironment.resetAll(); }
    @org.junit.jupiter.api.AfterEach void cleanup() {
        GameServices.configuration().clearSessionOverrides();
        com.openggf.tests.TestEnvironment.resetAll();
    }
    private static void configureWidth(int width) {
        var config = GameServices.configuration();
        var aspect = java.util.Arrays.stream(com.openggf.configuration.WidescreenAspect.values())
                .filter(value -> value.pixelWidth() == width).findFirst().orElseThrow();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.setSessionOverride(SonicConfiguration.WIDESCREEN_DRAW_MODE, "EDGE");
        config.resolveDisplayAspect();
    }

    @Test void tunnelExtendsPlaneAndCentresSpritesWithoutChangingNativeState() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var tunnel = new CdWarpCutscene(CdWarpCutscene.assets(disc), 0);
            for (int frame = 0; frame < 400 && !tunnel.done(); frame++) {
                tunnel.frame(0);
                var nativeFrame = tunnel.screen();
                for (int width : new int[] {400, 528}) {
                    var wide = tunnel.screen(width);
                    assertEquals(width, wide.width());
                    assertArrayEquals(nativeFrame.palette(), wide.palette());
                    assertSame(nativeFrame.planeB(), wide.planeB());
                    for (int i = 0; i < wide.sprites().size(); i++) {
                        assertEquals(nativeFrame.sprites().get(i).x() + (width - 320) / 2, wide.sprites().get(i).x());
                    }
                }
            }
        }
    }

    @Test void tunnelFadeCoversColumnsBeyondNativePlane() throws Exception {
        configureWidth(528);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(528, 224)) {
            boot.boot(disc, new SonicCdGameModule(), 0, 0);
            var tunnel = new CdWarpCutscene(CdWarpCutscene.assets(disc), 0);
            var renderer = new com.openggf.game.soniccd.program.CdProgramScreenRenderer();
            var graphics = GameServices.graphics();
            for (int frame = 0; frame < 354; frame++) {
                tunnel.frame(0);
                if (frame != 91 && frame != 100 && frame != 330 && frame != 345 && frame != 353) continue;
                var screen = tunnel.screen(528);
                renderer.setClearColor(screen);
                org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT);
                renderer.draw(screen);
                graphics.flush();
                org.lwjgl.opengl.GL11.glFinish();
                var picture = com.openggf.graphics.ScreenshotCapture.captureFramebuffer(528, 224);
                for (int y : new int[] {10, 100, 200}) {
                    if (frame == 353) assertEquals(0xFFFFFF, picture.argb(520, y) & 0xFFFFFF);
                    assertEquals(picture.argb(8, y), picture.argb(520, y),
                            "tunnel repeat and fade at frame " + frame + " y=" + y);
                }
            }
        }
    }

    @Test void ppzStaticAndMovingBandsFillExtraColumns() throws Exception {
        var config = GameServices.configuration();
        boolean audio = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            for (int width : new int[] {400, 528}) {
                configureWidth(width);
                for (var payload : new CdStagePayload[] {CdStagePayload.R11A, CdStagePayload.R11D, CdStagePayload.R12D}) {
                    try (var boot = new HeadlessGameBoot(width, 224)) {
                        var loop = boot.boot(disc, new SonicCdGameModule(), 0, SonicCdZoneRegistry.actIndex(payload));
                        var camera = GameServices.camera();
                        assertEquals(width, camera.getWidth());
                        camera.setFrozen(true);
                        for (int x = camera.getX(); x < 1800; x += 16) {
                            camera.setX((short) x);
                            camera.setY((short) 400);
                            loop.step();
                        }
                        assertTrue(TestPpzBackgroundRowSampling.verifyPicture(payload, camera.getY(), width) > 20,
                                payload + " needs exposed background at width " + width);
                    }
                }
            }
        } finally { config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, audio); }
    }
}
