package com.openggf.game.soniccd.special;

import com.openggf.CdSpecialLiveFrameTestDriver;
import com.openggf.Engine;
import com.openggf.audio.AudioManager;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.session.PreparedSmpsSfxProgram;
import com.openggf.audio.session.SmpsSessionCommand;
import com.openggf.audio.session.SmpsSessionTestSupport;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSfxData;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.audio.SonicCdAudioProfile;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

class TestCdSpecialStageRewindAudio {
    @Test
    void roughFloorDustReachesForwardSessionWithoutRewind() throws Exception {
        TestEnvironment.resetAll();
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, true);
        try (var disc = CdSpecialNative.disc();
             var boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, new SonicCdGameModule(), 0, 0);
            var liveFrame = new CdSpecialLiveFrameTestDriver(loop);
            loop.enterSpecialStage();
            for (int i = 0; i < 600; i++) liveFrame.run();
            var provider = (CdSpecialStageProvider) GameServices.module().getSpecialStageProvider();
            var stage = provider.session().stage();
            assertFalse(stage.stageInactive, "title card has released the player");
            // Place Sonic on an actual disc-backed rough tile, then let ordinary
            // collision, provider dispatch and forward session admission run.
            boolean found = false;
            for (int y = 0x350; y < 0xCB0 && !found; y += 32) {
                for (int x = 0x350; x < 0xCB0; x += 32) {
                    if ((stage.floor().stampAt(x, y) & 0xFF) != 4) continue;
                    stage.sonic().x = x << 16;
                    stage.sonic().y = y << 16;
                    stage.sonic().speed = 0x500;
                    stage.sonic().routine = CdSpecialStageSonic.GROUND;
                    stage.sonic().flags = 0;
                    found = true;
                    break;
                }
            }
            assertTrue(found, "SS1 contains rough floor");
            var emitted = new ArrayList<Integer>();
            AudioManager.getInstance().setRequestObserver((kind, id) -> emitted.add(id));
            for (int i = 0; i < 12; i++) liveFrame.run();
            assertTrue(emitted.contains(CdSpecialSound.DUST.fmId()), "rough-floor collision requests dust");
        } finally {
            config.resetToDefaults();
            TestEnvironment.resetAll();
        }
    }

    @ParameterizedTest
    @EnumSource(value = CdSpecialSound.class, names = {"JUMP", "RING", "SPRING", "DUST", "TIME_WARNING"})
    void effectImmediatelyBeforeRewindPassesRealSessionAdmission(CdSpecialSound sound) throws Exception {
        TestEnvironment.resetAll();
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setConfigValue(SonicConfiguration.REWIND_BUDGET_SECONDS, "unlimited");
        config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, true);
        try (var disc = CdSpecialNative.disc();
             var boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, new SonicCdGameModule(), 0, 0);
            var liveFrame = new CdSpecialLiveFrameTestDriver(loop);
            loop.enterSpecialStage();
            for (int i = 0; i < 120; i++) {
                liveFrame.run();
            }
            var audio = AudioManager.getInstance();
            audio.playSfx(sound.fmId());
            Engine.presentOuterAudioFrame(loop, false, false);
            assertTrue(audio.captureLogicalSnapshot().presentation().smpsLogical().sequencers().stream()
                    .anyMatch(entry -> entry.sfx() && entry.smpsData().getId() == sound.fmId()),
                    "requested effect is admitted before rewind");
            var rewind = com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindController();
            int before = rewind.currentFrame();
            // Rewind on the next service while the newly admitted effect is playing.
            loop.getInputHandler().handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_PRESS);
            liveFrame.run();
            assertTrue(rewind.currentFrame() < before, "rewind service traverses recorded history");
        } finally {
            config.resetToDefaults();
            TestEnvironment.resetAll();
        }
    }

    @Test
    void everyDiscFmProgramPassesAdmission() throws Exception {
        try (var disc = CdSpecialNative.disc()) {
            var profile = new SonicCdAudioProfile(() -> disc);
            var loader = profile.createRomlessSmpsLoader();
            var failures = new ArrayList<String>();
            for (int id = 0; id <= 0xFF; id++) {
                var data = loader.loadSfx(id);
                if (data == null) continue;
                var sequencer = new SmpsSequencer(
                        data, loader.loadDacData(), AudioManager.getInstance(), profile.getSequencerConfig());
                sequencer.setSfxMode(true);
                sequencer.setIsSfx(true);
                var entry = new SmpsDriverSnapshot.SequencerEntry(
                        true, sequencer.getSourceDescriptor(), null, data, loader.loadDacData(),
                        AudioManager.getInstance(), profile.getSequencerConfig(), sequencer.captureSnapshot());
                try (var session = SmpsSessionTestSupport.installed(48_000)) {
                    session.applyCommand(new SmpsSessionCommand.AdmitSfx(
                            new PreparedSmpsSfxProgram(entry, 0, 0)));
                } catch (IllegalArgumentException e) {
                    var entries = ((SmpsSfxData) data).getTrackEntries();
                    failures.add("id=0x%02X entries=%s length=%d: %s".formatted(
                            id, entries, data.getData().length, e.getMessage()));
                }
            }
            assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 30, 59, 61, 120, 600})
    void normallyBootedStageRewindsThroughOuterPresentation(int pressFrame) throws Exception {
        TestEnvironment.resetAll();
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setConfigValue(SonicConfiguration.REWIND_BUDGET_SECONDS, "unlimited");
        config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, true);
        try (var disc = CdSpecialNative.disc();
             var boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, new SonicCdGameModule(), 0, 0);
            var liveFrame = new CdSpecialLiveFrameTestDriver(loop);
            var audio = AudioManager.getInstance();
            audio.playSfx(CdSpecialSound.SPRING.fmId());
            liveFrame.run();
            loop.enterSpecialStage();
            for (int i = 0; loop.getCurrentGameMode() != GameMode.SPECIAL_STAGE; i++) {
                assertTrue(i < 120, "normal special-stage entry completes");
                liveFrame.run();
            }
            var provider = (CdSpecialStageProvider) GameServices.module().getSpecialStageProvider();
            var input = loop.getInputHandler();
            for (int i = 0; i < pressFrame; i++) {
                input.handleKeyEvent(config.getInt(SonicConfiguration.JUMP), i % 60 < 10 ? GLFW_PRESS : GLFW_RELEASE);
                liveFrame.run();
            }
            if (pressFrame <= 10) assertTrue(provider.session().fadingIn(), "early press covers fade-in");
            if (pressFrame == 30) assertTrue(provider.session().stage().titleCard().barAlive, "press covers title card");
            input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_PRESS);
            var rewind = com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindController();
            int before = rewind.currentFrame();
            for (int i = 0; i < Math.max(240, pressFrame + 120); i++) {
                liveFrame.run();
            }
            if (pressFrame >= 120) {
                assertTrue(rewind.currentFrame() < before, "held input traverses recorded history");
                // Entry's callback-bearing fade is an intentional replay barrier;
                // the oldest stored keyframe need not be a legal landing frame.
                assertFalse(rewind.stepBackward(), "held input reaches the accessible history boundary");
            }
            input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_RELEASE);
            for (int i = 0; i < 120; i++) {
                liveFrame.run();
            }
            assertFalse(audio.isReverseAudioPresentationActive());
            int resumed = provider.session().frameCount();
            var profile = (SonicCdAudioProfile) audio.getAudioProfile();
            profile.subCpuAudio().submit(CdSubCpuAudio.commandId("SCMD_STOPCDDA"));
            audio.stopAllSfx();
            Engine.presentOuterAudioFrame(loop, false, false);
            try (var capture = audio.beginLiveCaptureAudio(60)) {
                short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
                for (var sound : new CdSpecialSound[] {CdSpecialSound.JUMP, CdSpecialSound.RING, CdSpecialSound.DUST}) {
                    audio.stopAllSfx();
                    Engine.presentOuterAudioFrame(loop, false, false);
                    capture.drainPresentationFrame(packet);
                    audio.playSfx(sound.fmId());
                    boolean audible = false;
                    for (int i = 0; i < 30; i++) {
                        liveFrame.run();
                        int n = capture.drainPresentationFrame(packet);
                        for (int j = 0; j < n * 2; j++) audible |= Math.abs(packet[j]) > 64;
                    }
                    assertTrue(audible, sound + " reaches final PCM after rewind release");
                }
            }
            assertTrue(provider.session().frameCount() > resumed);
        } finally {
            config.resetToDefaults();
            TestEnvironment.resetAll();
        }
    }

}
