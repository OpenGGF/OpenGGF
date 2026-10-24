package com.openggf.tools;

import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameId;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.CdGameDataSource;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStockLaunch;
import com.openggf.game.soniccd.player.CdPlayableSonic;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerResidentBanks;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Optional real EGL/capture acceptance plus actual reused-boot audio ownership. */
@Isolated
class TestSonicCdTerrainPlayerBoot {
    @BeforeEach void setup() {
        TestEnvironment.resetAll();
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH, 320);
        config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT, 224);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        config.resolveDisplayAspect();
    }

    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void realEglCaptureUsesTheOrdinaryPlayerRendererAndRestoresVisibleResidency(CdReleaseVariant variant) throws Exception {
        var source = source(variant);
        Assumptions.assumeTrue("surfaceless-egl".equals(System.getProperty("openggf.headless.gl")),
                "Select the display-free rendering owner explicitly");
        assertTrue(System.getenv("DISPLAY") == null && System.getenv("WAYLAND_DISPLAY") == null,
                "this test must not connect to the shared desktop");
        var previousBackend = new RetirementBackend();
        GameServices.audio().setBackend(previousBackend);
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.bootSonicCdTerrainPlayer(source);
            var mode = SessionManager.getCurrentGameplayMode();
            var module = mode.getWorldSession().getGameModule();
            var frame = assertInstanceOf(CdPlayerFrameRuntime.class, module.getGameService(PlcLifecycleService.class));
            var actor = assertInstanceOf(CdPlayableSonic.class, capture.player());
            assertSame(source, mode.getWorldSession().getDataSource());
            assertEquals(GameMode.LEVEL, capture.loop().getCurrentGameMode());
            assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
            assertEquals(1, previousBackend.destroyCalls);
            assertEquals("scd", GameServices.audio().getAudioProfile().presentationGameId());
            assertNull(GameServices.audio().captureLogicalSnapshot().presentation().activeMusic());
            assertEquals(0, actor.nativeState().motion().routine());
            var coldActor = frame.captureActor();
            var coldShared = frame.captureShared();
            var cold = capture.render();
            assertEquals(320, cold.width());
            assertEquals(224, cold.height());
            assertTrue(Arrays.stream(cold.pixels()).distinct().count() > 8);
            assertArrayEquals(cold.pixels(), capture.render().pixels());
            assertArrayEquals(cold.pixels(), capture.render(false).pixels(), "cold routine0 has no fallback player drawing");
            assertEquals(coldActor, frame.captureActor());
            assertEquals(coldShared, frame.captureShared());
            capture.step(null); // First V-int + Init only.
            assertEquals(2, actor.nativeState().motion().routine());
            assertTrue(frame.display().isEmpty());
            assertArrayEquals(new byte[0x300], frame.artState().residentBank().copy());
            assertArrayEquals(capture.render(false).pixels(), capture.render().pixels());
            capture.step(null); // Main publishes the later service's mapping.
            assertEquals(0, frame.artState().lastFrame());
            capture.step(null); // Service publishes actual decoded player art.
            var image = capture.render();
            assertFalse(Arrays.equals(image.pixels(), capture.render(false).pixels()),
                    "the admitted actor must produce visible pixels through the normal controller renderer");
            var snapshot = mode.getRewindRegistry().capture();
            var nativeActor = frame.captureActor();
            var nativeShared = frame.captureShared();
            var bank = PlayerResidentBanks.capture(actor.getSpriteRenderer());
            var expectedBank = bank.tiles();
            assertArrayEquals(image.pixels(), capture.render().pixels());
            assertEquals(nativeActor, frame.captureActor());
            assertEquals(nativeShared, frame.captureShared(), "render-only calls cannot service a cue or read input");
            for (int i = 0; i < 8; i++) {
                capture.step(new Bk2FrameInput(i, AbstractPlayableSprite.INPUT_RIGHT, 0, false, ""));
            }
            mode.getRewindRegistry().restore(snapshot);
            assertEquals(nativeActor, frame.captureActor());
            assertEquals(nativeShared, frame.captureShared());
            var restored = PlayerResidentBanks.capture(actor.getSpriteRenderer()).tiles();
            for (int i = 0; i < 24; i++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                assertEquals(expectedBank[i].getPixel(x, y), restored[i].getPixel(x, y));
            }
            assertArrayEquals(image.pixels(), capture.render().pixels(), "restore reprojects source camera, pose and complete bank");
            assertArrayEquals(image.pixels(), capture.render().pixels());
            if (Boolean.getBoolean("soniccd.terrain.player.capture")) {
                Path directory = Path.of("target", "soniccd-terrain-player-captures");
                Files.createDirectories(directory);
                ScreenshotCapture.savePNG(image, directory.resolve(variant.name().toLowerCase(Locale.ROOT) + "-r11a-player.png"));
            }
        }
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void everyCdBootReplacesTheAudioOwnerIncludingDisabledSynthesis(CdReleaseVariant variant) throws Exception {
        var source = source(variant);
        var services = EngineServices.current();
        try (var boot = logicBoot()) {
            services.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, true);
            boot.bootSonicCdTerrainPlayer(source);
            assertTrue(boot.hasInstalledHeadlessAudioBackend());
            var first = assertInstanceOf(HeadlessSmpsAudioBackend.class, services.audio().getBackend());
            var frame = assertInstanceOf(CdPlayerFrameRuntime.class, SessionManager.getCurrentWorldSession()
                    .getGameModule().getGameService(PlcLifecycleService.class));
            services.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
            boot.bootSonicCdTerrainPlayer(source);
            assertTrue(frame.closed(), "reboot retires the prior session's native owner");
            assertFalse(services.audio().hasInstalledBackend(first));
            assertFalse(boot.hasInstalledHeadlessAudioBackend());
            assertEquals(NullAudioBackend.class, services.audio().getBackend().getClass());
            assertEquals("scd", services.audio().getAudioProfile().presentationGameId());
            assertNull(services.audio().captureLogicalSnapshot().presentation().activeMusic());
            var currentMode = SessionManager.getCurrentGameplayMode();
            services.configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
            assertThrows(IllegalArgumentException.class, () -> boot.bootSonicCdTerrainPlayer(source));
            assertSame(currentMode, SessionManager.getCurrentGameplayMode(), "bad requests must not retire the current session");
            services.configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        }
    }

    @Test void usaNormalInputMovementJumpPauseAndRenderedReplay() throws Exception {
        var source = source(CdReleaseVariant.USA);
        Assumptions.assumeTrue("surfaceless-egl".equals(System.getProperty("openggf.headless.gl")));
        assertNull(System.getenv("DISPLAY"));
        assertNull(System.getenv("WAYLAND_DISPLAY"));
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.bootSonicCdTerrainPlayer(source);
            var mode = SessionManager.getCurrentGameplayMode();
            var frame = (CdPlayerFrameRuntime) mode.getWorldSession().getGameModule().getGameService(PlcLifecycleService.class);
            var actor = (CdPlayableSonic) capture.player();
            for (int i = 0; i < 3; i++) capture.step(null);
            var checkpoint = mode.getRewindRegistry().capture();
            int initialY = actor.getCentreY();
            var actors = new ArrayList<CdPlayerFrameRuntime.ActorState>();
            var shared = new ArrayList<CdPlayerFrameRuntime.SharedState>();
            var sounds = new ArrayList<java.util.List<CdPlayerFrameRuntime.Sound>>();
            var camera = new ArrayList<String>();
            var rows = new StringBuilder("presentation,native_vint_delta,native_paused,level_frame," + GameplayCaptureSession.stateHeader() + "\n");
            int minY = initialY, maxX = actor.getCentreX();
            boolean airborne = false, landed = false, wasPaused = false;
            for (int i = 1; i <= 132; i++) {
                var input = routeInput(i);
                capture.step(input);
                capture.renderFrame();
                assertFalse(frame.closed(), "ordinary supported route must retain admission at " + i);
                var a = frame.captureActor(); var s = frame.captureShared();
                actors.add(a); shared.add(s); sounds.add(actor.lastSoundRequests());
                camera.add(GameServices.camera().getX() + "," + GameServices.camera().getY());
                minY = Math.min(minY, actor.getCentreY()); maxX = Math.max(maxX, actor.getCentreX());
                if (actor.getAir()) airborne = true;
                else if (airborne) landed = true;
                if (i >= 101 && i <= 106) {
                    assertTrue(frame.paused());
                    if (i > 101) assertEquals(actors.get(i - 2).state(), a.state(), "pause services cannot run the Body");
                    wasPaused = true;
                }
                if (i == 107) assertFalse(frame.paused(), "Start after release resumes through native edge history");
                rows.append(i).append(',').append(s.vIntClock().deltaSinceAdmittedCut()).append(',')
                        .append(frame.paused()).append(',').append(mode.getLevelManager().getFrameCounter()).append(',')
                        .append(capture.stateLine(i + 3, input)).append('\n');
                if (Boolean.getBoolean("soniccd.terrain.player.capture")
                        && (i == 15 || i == 48 || i == 65 || i == 85 || i == 104 || i == 132)) {
                    Path directory = Path.of("target", "soniccd-terrain-player-captures");
                    Files.createDirectories(directory);
                    ScreenshotCapture.savePNG(capture.render(), directory.resolve("usa-route-" + String.format(Locale.ROOT, "%03d", i) + ".png"));
                }
            }
            assertTrue(maxX > 100, "normal held input must move Sonic across actual terrain");
            assertTrue(minY < initialY - 24 && airborne && landed, "ordinary jump must rise and land on decoded terrain");
            assertTrue(wasPaused);
            var finalImage = capture.render();
            mode.getRewindRegistry().restore(checkpoint);
            capture.restoreInputHistory(new Bk2FrameInput(2, 0, 0, false, ""));
            for (int i = 1; i <= 132; i++) {
                capture.step(routeInput(i)); capture.renderFrame();
                var expected = shared.get(i - 1); var actual = frame.captureShared();
                assertEquals(actors.get(i - 1), frame.captureActor(), "complete actor/history replay at " + i);
                assertEquals(expected.environment(), actual.environment());
                assertEquals(expected.paused(), actual.paused());
                assertEquals(expected.vIntClock(), actual.vIntClock());
                assertEquals(expected.artState().lastFrame(), actual.artState().lastFrame());
                assertEquals(expected.artState().dirty(), actual.artState().dirty());
                assertArrayEquals(expected.artState().cpuBuffer().copy(), actual.artState().cpuBuffer().copy());
                assertArrayEquals(expected.artState().residentBank().copy(), actual.artState().residentBank().copy());
                assertEquals(sounds.get(i - 1), actor.lastSoundRequests());
                assertEquals(camera.get(i - 1), GameServices.camera().getX() + "," + GameServices.camera().getY());
            }
            assertArrayEquals(finalImage.pixels(), capture.render().pixels());
            assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
            if (Boolean.getBoolean("soniccd.terrain.player.capture")) {
                Files.writeString(Path.of("target", "soniccd-terrain-player-captures", "usa-route.csv"), rows);
            }
        }
    }

    private static Bk2FrameInput routeInput(int frame) {
        int direction = frame <= 45 || frame >= 108 ? AbstractPlayableSprite.INPUT_RIGHT
                : frame <= 49 ? AbstractPlayableSprite.INPUT_LEFT
                : frame >= 81 && frame <= 92 ? AbstractPlayableSprite.INPUT_DOWN : 0;
        int action = frame >= 46 && frame <= 49 ? 2 : 0; // B held; native tap derives from held history.
        boolean start = frame == 101 || frame == 107;
        return new Bk2FrameInput(frame + 2, direction, action, start,
                "direction=" + direction + ";B=" + (action != 0) + ";Start=" + start);
    }

    @Test void actualCartridgeThenCdRetiresThePriorDriverAndSourceOnTheSameBoot() throws Exception {
        var source = source(CdReleaseVariant.USA);
        String configuredRom = System.getProperty("sonic2.rom.path");
        Assumptions.assumeTrue(configuredRom != null && !configuredRom.isBlank(), "Supply the existing absolute Sonic2 ROM path");
        Path rom = Path.of(configuredRom);
        assertTrue(rom.isAbsolute() && Files.isRegularFile(rom));
        var services = EngineServices.current();
        try (var boot = logicBoot()) {
            services.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, true);
            boot.boot(rom, 0, 0);
            assertEquals(GameId.S2, SessionManager.getCurrentWorldSession().getGameModule().getGameId());
            assertTrue(boot.hasInstalledHeadlessAudioBackend());
            var cartridgeBackend = services.audio().getBackend();
            services.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
            boot.bootSonicCdTerrainPlayer(source);
            assertEquals(GameId.SCD, SessionManager.getCurrentWorldSession().getGameModule().getGameId());
            assertTrue(SessionManager.getCurrentWorldSession().getDataSource().rom().isEmpty());
            assertFalse(services.audio().hasInstalledBackend(cartridgeBackend));
            assertEquals(NullAudioBackend.class, services.audio().getBackend().getClass());
            assertEquals("scd", services.audio().getAudioProfile().presentationGameId());
            assertNull(services.audio().captureLogicalSnapshot().presentation().activeMusic());
        }
    }

    private static HeadlessGameBoot logicBoot() {
        var services = EngineServices.current();
        return new HeadlessGameBoot(320, 224, services, new HeadlessGameBoot.NativeGlLifecycle() {
            @Override public void initialize(HeadlessGameBoot ignored) {
                EngineServices.configure(services);
                services.graphics().initHeadless();
            }
            @Override public void close(HeadlessGameBoot ignored) { }
        });
    }

    private static final class RetirementBackend extends NullAudioBackend {
        private int destroyCalls;
        @Override public void destroy() { destroyCalls++; }
    }

    private static CdGameDataSource source(CdReleaseVariant variant) throws IOException {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Supply soniccd.media.root for authenticated retail media");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        try (var paths = Files.list(Path.of(root).resolve(directory))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            return CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
    }
}
