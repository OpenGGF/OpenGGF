package com.openggf.tools;

import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.*;
import com.openggf.game.sonic3k.Sonic3kSessionResetPolicy;
import com.openggf.game.sonic3k.titlescreen.Sonic3kTitleScreenManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Actual EGL framebuffer and no-device SMPS output across repeated session reconstruction. */
@RequiresRom(SonicGame.SONIC_3K)
class TestTasS3kSessionResetOutput {
    @Test void reusedRendererAndNoDeviceAudioPublishNativeTitleAgainAfterWarmAndColdReset() throws Exception {
        TestEnvironment.resetEngineOnly();
        var services = EngineServices.current();
        var config = services.configuration();
        config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, true);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        try (var boot = new HeadlessGameBoot(320, 224, services)) {
            // The parent composes this registration into the native module.
            boot.setModuleDecorator(module -> new DelegatingGameModule(module, "test:reset-output-registration") {
                @Override public <T> T getGameService(Class<T> type) {
                    return type == NativeSessionResetPolicy.class ? type.cast(Sonic3kSessionResetPolicy.INSTANCE)
                            : super.getGameService(type);
                }
            });
            var loop = boot.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 7, 0);
            assertInstanceOf(HeadlessSmpsAudioBackend.class, services.audio().getBackend());
            var backend = services.audio().getBackend();
            var nativeTitle = assertInstanceOf(Sonic3kTitleScreenManager.class, loop.getTitleScreenProvider());
            var titleState = nativeTitle.nativeTitleState();
            titleState.setOption(1);
            resetOnly(loop, services, false);
            assertSame(titleState, nativeTitle.nativeTitleState(), "warm reset keeps the module-owned policy");
            assertEquals(1, titleState.option(), "warm entry retains the native selected icon byte");
            resetOnly(loop, services, true);
            assertEquals(0, titleState.option(), "cold power initializes the selected icon byte");
            RgbaImage warm = resetAndReachTitle(loop, services, false);
            assertTrue(Arrays.stream(warm.pixels()).distinct().count() > 8, "real title art/palettes must be visible");
            try (var capture = services.audio().beginLiveCaptureAudio(60)) {
                loop.step();
                Engine.presentOuterAudioFrame(loop, false, false);
                short[] pcm = new short[capture.maxStereoFramesPerPacket() * 2];
                assertEquals(services.audio().outputSampleRate() / 60, capture.drainPresentationFrame(pcm));
                boolean signal = false;
                for (short sample : pcm) signal |= sample != 0;
                assertTrue(signal, "native title synthesis must reach the no-device output");
                RgbaImage cold = resetAndReachTitle(loop, services, true,
                        () -> assertEquals(services.audio().outputSampleRate() / 60,
                                capture.drainPresentationFrame(pcm)));
                loop.step();
                Engine.presentOuterAudioFrame(loop, false, false);
                assertEquals(services.audio().outputSampleRate() / 60, capture.drainPresentationFrame(pcm),
                        "the existing capture lease follows the rebuilt presentation owner");
                assertTrue(Arrays.stream(cold.pixels()).distinct().count() > 8);
                assertArrayEquals(warm.pixels(), cold.pixels(), "same native title checkpoint after disposing old art");
            }
            assertSame(backend, services.audio().getBackend());
            assertTrue(boot.hasInstalledHeadlessAudioBackend());
        } finally {
            services.playbackDebug().endSession();
            services.audio().destroy();
        }
    }

    private static RgbaImage resetAndReachTitle(GameLoop loop, EngineContext services, boolean cold) throws Exception {
        return resetAndReachTitle(loop, services, cold, () -> { });
    }

    private static RgbaImage resetAndReachTitle(GameLoop loop, EngineContext services, boolean cold,
            Runnable drainAudio) throws Exception {
        resetOnly(loop, services, cold);
        var title = loop.getTitleScreenProvider();
        int steps = 0;
        while (title.getState() != TitleScreenProvider.State.ACTIVE && steps++ < 1800) {
            loop.step();
            Engine.presentOuterAudioFrame(loop, false, false);
            drainAudio.run();
        }
        assertEquals(TitleScreenProvider.State.ACTIVE, title.getState(), "native title state machine reached its menu");
        assertEquals(GameMode.TITLE_SCREEN, loop.getCurrentGameMode());
        var graphics = services.graphics();
        graphics.runPendingRenderThreadTasks();
        title.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        graphics.resetForFixedFunction();
        title.draw();
        graphics.flushScreenSpace();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static void resetOnly(GameLoop loop, EngineContext services, boolean cold) throws Exception {
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("reset-output.txt"), List.of(
                "[Input]", "LogKey:#Power|Reset|#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|",
                cold ? "|P.|........|" : "|.r|........|", "|..|........|", "[/Input]"));
        services.playbackDebug().startSession(movie, 0);
        loop.step();
        services.playbackDebug().endSession();
    }
}
