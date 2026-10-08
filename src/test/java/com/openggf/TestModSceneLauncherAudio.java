package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.mods.code.OwnedSceneFactory;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A startup scene must produce ROM audio without first loading a title or level. */
@RequiresRom(SonicGame.SONIC_3K)
class TestModSceneLauncherAudio {
    private GameLoop loop;

    @AfterEach
    void cleanup() {
        if (loop != null) loop.modSceneHost.close();
        TestEnvironment.resetAll();
    }

    @Test
    void startupInstallsRomSourceBeforeEnterAndProducesMusicAndEffects() throws Exception {
        var rom = GameServices.rom().getRom();
        var module = spy(GameServices.module());
        var factory = mock(OwnedSceneFactory.class);
        var scene = new AudioScene();
        when(factory.ownerModId()).thenReturn("audio-probe");
        when(factory.create()).thenReturn(scene);
        doReturn(factory).when(module).getGameService(OwnedSceneFactory.class);
        TestEnvironment.configureGameModuleFixture(module);
        GameServices.rom().setRom(rom);
        AudioManager audio = GameServices.audio();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(null);
        audio.setRom(null);
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        loop = new GameLoop(new InputHandler());

        assertTrue(ModSceneLauncher.openStartupScene(loop, config, 0,
                GameServices.graphics(), 400, 224));
        assertSame(module.getAudioProfile(), audio.getAudioProfile());
        try (LiveCaptureAudioHandle capture = audio.beginLiveCaptureAudio(audio.presentationFrameRate())) {
            assertTrue(presentUntil(capture, true), "enter music reaches final PCM");
            scene.context.audio().stopMusic();
            assertTrue(presentUntil(capture, false), "music stops before isolated SFX check");
            scene.context.audio().playSfx(0x33);
            assertTrue(presentUntil(capture, true), "ring SFX reaches final PCM independently");
        }
    }

    private boolean presentUntil(LiveCaptureAudioHandle capture, boolean audible) {
        short[] samples = new short[capture.maxStereoFramesPerPacket() * 2];
        for (int frame = 0; frame < 180; frame++) {
            Engine.presentOuterAudioFrame(loop, false, false);
            int count = capture.drainPresentationFrame(samples);
            int min = Short.MAX_VALUE;
            int max = Short.MIN_VALUE;
            // Check variation within each channel, not a constant/DC packet or
            // merely different constant offsets between the stereo channels.
            boolean signal = false;
            for (int channel = 0; channel < 2; channel++) {
                min = Short.MAX_VALUE;
                max = Short.MIN_VALUE;
                for (int i = channel; i < count * 2; i += 2) {
                    min = Math.min(min, samples[i]);
                    max = Math.max(max, samples[i]);
                }
                signal |= max - min > 32;
            }
            if (count > 0 && signal == audible) return true;
        }
        return false;
    }

    private static final class AudioScene implements ModScene {
        private SceneContext context;
        public void enter(SceneContext context) {
            this.context = context;
            context.audio().playMusic(0x25);
        }
        public void update(SceneContext context) { }
        public void draw(SceneContext context, SceneCanvas canvas) { }
    }
}
