package com.openggf.game.soniccd.flow;

import com.openggf.NativeMenuPresentationAccess;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.audio.SonicCdAudioProfile;
import com.openggf.game.soniccd.backupram.CdSaveRecord;
import com.openggf.game.soniccd.backupram.CdSaveVariables;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.Palette;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F1;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;

@EnabledOnOs(OS.LINUX)
class TestCdGardenMusicFlow {
    @Test
    void unlockedTitlePlaysDiscTracksAndRestoresTitleMusicOnBack() throws Exception {
        var saved = CdSaveRecord.decode(new byte[CdSaveRecord.FILE_BYTES]);
        saved.store(new CdSaveVariables(0x0215, 3, 0x08, 0x15, 0, 4, 0x0f));
        try (var harness = new CdFlowHarness(module -> {
            CdFlowHarness.attachReadOnlySave(module, saved);
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, true);
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_MUSIC_VOLUME, 100);
        })) {
            harness.selectTitleOption(1);
            harness.until(() -> harness.loop.getCurrentGameMode() == GameMode.OPTIONS
                    && !GameServices.fade().isActive(), 600, 0);
            var manager = GameServices.audio();
            var profile = assertInstanceOf(SonicCdAudioProfile.class, manager.getAudioProfile());
            var audio = profile.subCpuAudio();
            assertEquals(0, audio.state().cdda().track(), "entry stops the title music");

            var browser = NativeMenuPresentationAccess.screen(harness.loop);
            assertNotNull(browser);
            assertTrue(Arrays.stream(draw(browser)).distinct().count() > 8,
                    "the music browser has drawn its track rows");

            harness.step(0, CdFlowHarness.A, false);
            assertEquals(3, audio.state().cdda().track());
            harness.idle(1);
            harness.step(0, CdFlowHarness.A, false);
            assertEquals(0, audio.state().cdda().track(), "confirming the playing track stops it");
            harness.step(CdFlowHarness.DOWN, 0, false);
            harness.step(0, CdFlowHarness.A, false);
            assertEquals(4, audio.state().cdda().track(), "the next selection plays the next disc track");

            try (var capture = manager.beginLiveCaptureAudio(manager.presentationFrameRate())) {
                var packet = new short[capture.maxStereoFramesPerPacket() * 2];
                assertTrue(peak(harness, capture, packet, 90) > 100, "track four produces samples through the disc mixer");
                long beforeView = audio.state().cdda().position();
                assertTrue(beforeView > 0, "the producer advances the selected disc track");

                var input = harness.loop.getInputHandler();
                input.handleKeyEvent(GLFW_KEY_F1, GLFW_PRESS);
                harness.idle(1);
                input.handleKeyEvent(GLFW_KEY_F1, GLFW_RELEASE);
                assertEquals(4, audio.state().cdda().track());
                assertTrue(audio.state().cdda().position() >= beforeView, "opening the view keeps the play position");
                int[] planet = draw(browser);
                assertPlanetColors(planet, harness);

                assertTrue(peak(harness, capture, packet, 30) > 100, "the disc mixer stays audible in the view");
                long inView = audio.state().cdda().position();
                assertTrue(inView > beforeView, "CDDA advances in the view");
                assertArrayEquals(planet, draw(browser), "the art view stays static while CDDA advances");

                harness.step(0, CdFlowHarness.C, false);
                assertEquals(GameMode.OPTIONS, harness.loop.getCurrentGameMode());
                assertSame(browser, NativeMenuPresentationAccess.screen(harness.loop));
                assertEquals(4, audio.state().cdda().track());
                assertTrue(audio.state().cdda().position() >= inView, "returning to the browser keeps the play position");
                assertFalse(Arrays.equals(planet, draw(browser)), "Back restores the track rows");
            }
            harness.step(0, CdFlowHarness.A, false);
            assertEquals(0, audio.state().cdda().track(), "confirm still stops the selected track after returning");
            harness.idle(1);
            harness.step(0, CdFlowHarness.A, false);
            assertEquals(4, audio.state().cdda().track(), "confirm plays the retained selection");

            harness.step(0, CdFlowHarness.C, false);
            harness.until(() -> harness.loop.getCurrentGameMode() == GameMode.TITLE_SCREEN, 600, 0);
            assertEquals(26, audio.state().cdda().track(), "returning to the title gives music back to the title");
            assertEquals(saved, harness.scenes.flow().backupRam().readSave());
        }
    }

    private static int[] draw(NativeMenuScreen screen) {
        screen.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT);
        screen.draw();
        GameServices.graphics().flushScreenSpace();
        return ScreenshotCapture.captureFramebuffer(320, 224).pixels();
    }

    private static void assertPlanetColors(int[] image, CdFlowHarness harness) throws Exception {
        var palette = new Palette();
        palette.fromSegaFormat(harness.disc.assets().read(CdNativeFile.DAGARDMAIN, 0x2A8A, Palette.PALETTE_SIZE_IN_ROM));
        long visible = 0;
        int colors = 0;
        for (int i = 1; i < Palette.PALETTE_SIZE; i++) {
            var color = palette.colors[i];
            int rgb = ((color.r & 0xFF) << 16) | ((color.g & 0xFF) << 8) | (color.b & 0xFF);
            long pixels = Arrays.stream(image, 46 * 320, 200 * 320)
                    .filter(pixel -> (pixel & 0xFFFFFF) == rgb).count();
            visible += pixels;
            if (pixels > 0) colors++;
        }
        assertTrue(visible > 5_000 && colors >= 8,
                "the view draws the disc's Present art and palette, pixels=" + visible + ", colors=" + colors);
    }

    private static int peak(CdFlowHarness harness, LiveCaptureAudioHandle capture,
                            short[] packet, int budget) {
        int peak = 0;
        for (int frame = 0; frame < budget && peak <= 100; frame++) {
            harness.idle(1);
            HeadlessGameBoot.presentHeadlessOuterAudioFrame();
            int frames = capture.drainPresentationFrame(packet);
            assertTrue(frames > 0, "each presented frame emits an audio packet");
            for (int i = 0; i < frames * 2; i++) peak = Math.max(peak, Math.abs((int) packet[i]));
        }
        return peak;
    }
}
