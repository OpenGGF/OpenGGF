package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.LevelManager.LevelRenderOptions;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.trace.TraceReplayFrameClosureDriver;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceVIntServiceCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

@EnabledOnOs(OS.LINUX)
class TestCczLeftEdgePicture {
    @Test
    void visibleSpringAndBlockSurviveTheNativeSpriteBudget() throws Exception {
        var movie = CdTestMedia.nativeFile("rehost-long/R31A/movie.bk2");
        var traceDir = CdTestMedia.nativeFile("rehost-long/R31A/trace/metadata.json").getParent();
        var trace = TraceData.load(traceDir);
        var config = GameServices.configuration();
        boolean oldAudio = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        boolean oldLimits = config.getBoolean(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boots = new CdFreshStageBoots(320, 224)) {
            boots.boot(disc, CdStagePayload.R31A);
            var sonic = GameServices.camera().getFocusedSprite();
            var runner = new HeadlessTestRunner(sonic);
            runner.setBk2Movie(new Bk2MovieLoader().load(movie), trace.metadata().bk2FrameOffset());
            var vint = TraceVIntServiceCoordinator.open(traceDir, trace, SessionManager.requireCurrentGameModule());
            int pictureRow = 2926;  // Native row 2927 displays this completed object's picture.
            runner.consumeRecordingFrameInputOnly();
            for (int row = 1; row <= pictureRow; row++) {
                if (!vint.vBlankServiced(row)) {
                    runner.consumeRecordingFrameInputOnly();
                } else {
                    var phase = TraceReplayBootstrap.phaseForReplay(trace, trace.getFrame(row - 1), trace.getFrame(row));
                    TraceReplayFrameClosureDriver.driveGeneral(phase, runner::stepFrameFromRecording,
                            runner::skipFrameFromRecording, () -> { });
                }
            }
            var nativeRow = trace.getFrame(pictureRow);
            assertEquals(nativeRow.gameplayFrameCounter(), GameServices.level().getFrameCounter(), "level frame");
            assertEquals(nativeRow.x(), sonic.getCentreX(), "Sonic X");
            assertEquals(nativeRow.y(), sonic.getCentreY(), "Sonic Y");
            assertEquals(nativeRow.cameraX(), GameServices.camera().getX(), "camera X");
            assertEquals(nativeRow.cameraY(), GameServices.camera().getY(), "camera Y");
            assertObject(68, 0x0A, 6, 0x1A60, 0x0590);
            assertObject(69, 0x21, 1, 0x1A78, 0x0590);

            RgbaImage tiles = render(LevelRenderOptions.tilesOnly());
            config.setSessionOverride(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, false);
            RgbaImage unrestricted = render(LevelRenderOptions.gameplay());
            config.setSessionOverride(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, true);
            RgbaImage limited = render(LevelRenderOptions.gameplay());
            assertAll(
                    () -> assertSprite("spring", 5, 21, tiles, unrestricted, limited),
                    () -> assertSprite("block", 21, 53, tiles, unrestricted, limited),
                    () -> assertLightBand("left", 35, 85, 0x04C87E18L, limited),
                    () -> assertLightBand("right", 215, 280, 0xFCBA7112L, limited));
        } finally {
            config.setSessionOverride(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, oldLimits);
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
        }
    }

    private static void assertObject(int slot, int id, int subtype, int x, int y) {
        var object = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(AbstractObjectInstance.class::isInstance)
                .map(AbstractObjectInstance.class::cast)
                .filter(o -> !o.isDestroyed() && o.getSlotIndex() == slot)
                .findFirst().orElse(null);
        assertNotNull(object, "empty native slot " + slot);
        assertEquals(id, object.getSpawn().objectId(), "ID in slot " + slot);
        assertEquals(subtype, object.getSpawn().subtype(), "subtype in slot " + slot);
        assertEquals(x, object.getX(), "X in slot " + slot);
        assertEquals(y, object.getY(), "Y in slot " + slot);
    }

    private static void assertSprite(String name, int left, int right, RgbaImage tiles, RgbaImage unrestricted,
                                     RgbaImage limited) {
        int spritePixels = 0;
        int mismatches = 0;
        for (int y = 112; y < 144; y++) {
            for (int x = left; x < right; x++) {
                int expected = unrestricted.argb(x, y);
                if (tiles.argb(x, y) != expected) spritePixels++;
                if (limited.argb(x, y) != expected) mismatches++;
            }
        }
        assertTrue(spritePixels > 0, name + " must draw over the terrain");
        assertEquals(0, mismatches, name + " pixels changed when native sprite limits were enabled");
    }

    private static void assertLightBand(String side, int left, int right, long nativeCrc, RgbaImage image) {
        // CRC of the native row 2927 crop, with each 3-bit RGB channel packed into a 16-bit word.
        CRC32 colors = new CRC32();
        for (int y = 56; y < 64; y++) {
            for (int x = left; x < right; x++) {
                int rgb = CczNativePictureCheck.level(image.argb(x, y));
                colors.update(rgb >>> 8);
                colors.update(rgb);
            }
        }
        assertEquals(nativeCrc, colors.getValue(), side + " background lights below the HUD");
    }

    private static RgbaImage render(LevelRenderOptions options) {
        var level = GameServices.level();
        var graphics = GameServices.graphics();
        level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        level.drawWithRenderOptions(GameServices.sprites(), options);
        graphics.flush();
        if (graphics.getUiRenderPipeline() != null) graphics.getUiRenderPipeline().renderFadePass();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }
}
