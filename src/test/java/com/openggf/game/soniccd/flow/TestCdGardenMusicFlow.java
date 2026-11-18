package com.openggf.game.soniccd.flow;

import com.openggf.NativeMenuPresentationAccess;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.audio.SonicCdAudioProfile;
import com.openggf.game.soniccd.backupram.CdSaveRecord;
import com.openggf.game.soniccd.backupram.CdSaveVariables;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
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
            browser.setClearColor();
            glClear(GL_COLOR_BUFFER_BIT);
            browser.draw();
            GameServices.graphics().flushScreenSpace();
            assertTrue(Arrays.stream(ScreenshotCapture.captureFramebuffer(320, 224).pixels())
                    .distinct().count() > 8, "the music browser has drawn its track rows");

            harness.step(0, CdFlowHarness.A, false);
            assertEquals(3, audio.state().cdda().track());
            int peak = 0;
            try (var capture = manager.beginLiveCaptureAudio(manager.presentationFrameRate())) {
                var packet = new short[capture.maxStereoFramesPerPacket() * 2];
                for (int frame = 0; frame < 90 && peak <= 100; frame++) {
                    harness.idle(1);
                    HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                    int frames = capture.drainPresentationFrame(packet);
                    assertTrue(frames > 0, "each presented frame emits an audio packet");
                    for (int i = 0; i < frames * 2; i++) peak = Math.max(peak, Math.abs((int) packet[i]));
                }
                assertTrue(capture.totalStereoFrames() > 0);
            }
            assertTrue(peak > 100, "track three produces samples through the disc mixer");
            assertTrue(audio.state().cdda().position() > 0, "the producer advances the selected disc track");
            harness.step(0, CdFlowHarness.A, false);
            assertEquals(0, audio.state().cdda().track(), "confirming the playing track stops it");
            harness.step(CdFlowHarness.DOWN, 0, false);
            harness.step(0, CdFlowHarness.A, false);
            assertEquals(4, audio.state().cdda().track(), "the next selection plays the next disc track");

            harness.step(0, CdFlowHarness.C, false);
            harness.until(() -> harness.loop.getCurrentGameMode() == GameMode.TITLE_SCREEN, 600, 0);
            assertEquals(26, audio.state().cdda().track(), "returning to the title gives music back to the title");
            assertEquals(saved, harness.scenes.flow().backupRam().readSave());
        }
    }
}
