package com.openggf;

import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/** Real original-ROM boot, scheduler's Engine caller, native movement, registered restore and EGL pictures. */
@Isolated
class TestScheduledNativeGameplay {
    private static final long SECOND = 1_000_000_000L;
    private GameplayCaptureSession session;
    private Engine engine;
    private InputHandler input;
    private int pictures;
    private Method render;
    private long clock;

    @AfterEach void close() {
        try {
            if (engine != null) engine.getGameLoop().closePresence();
            Engine.clearGlobalInstance();
            if (session != null) session.close();
        } finally { Engine.clearGlobalInstance(); }
    }

    private void open(Path rom, int hz, int fps, boolean sound) throws Exception {
        open(rom, hz, fps, sound, false);
    }

    private void open(Path rom, int hz, int fps, boolean sound, boolean rewind) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        session = new GameplayCaptureSession(settings);
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.REGION, hz == 50 ? "PAL" : "NTSC");
        config.setSessionOverride(SonicConfiguration.FPS, fps);
        config.setSessionOverride(SonicConfiguration.P1_A, GLFW_KEY_SPACE);
        config.setSessionOverride(SonicConfiguration.PAUSE_KEY, GLFW_KEY_F9);
        config.setSessionOverride(SonicConfiguration.FRAME_STEP_KEY, GLFW_KEY_F8);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, rewind);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_KEY, GLFW_KEY_F10);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_TAPE_COAST_ENABLED, false);
        if (sound) session.enableAudio();
        session.boot(rom, 0, 0, settings);
        for (int setup = 0; ; setup++) {
            assertTrue(setup < 600, "native level entry/landing must finish");
            if (session.loop().getCurrentGameMode() == GameMode.LEVEL
                    && !GameServices.level().hasPendingFreshLevelTransitionBoundary()
                    && !session.player().isControlLocked() && !session.player().getAir()
                    && !GameServices.fade().isActive()) break;
            session.step(null); session.renderFrame();
        }
        session.loop().closePresence();
        engine = new Engine();
        input = new InputHandler(InputBindingFactory.supplier(config));
        engine.setInputHandler(input);
        var bind = Engine.class.getDeclaredMethod("bindGameplayMode", GameplayModeContext.class);
        bind.setAccessible(true); bind.invoke(engine, SessionManager.getCurrentGameplayMode());
        var reshape = Engine.class.getDeclaredMethod("reshape", int.class, int.class);
        reshape.setAccessible(true); reshape.invoke(engine, 320, 224);
        GameServices.graphics().setProjectionMatrixBuffer(engine.getProjectionMatrixBuffer());
        render = Engine.class.getDeclaredMethod("renderScheduledFrame", boolean.class);
        render.setAccessible(true);
    }

    private LiveFrameRuntime scheduler() {
        return scheduler(() -> { }, DisplayOutput.UNKNOWN);
    }

    /** A known output nominates the display cadence; the speaker counts as regulated. */
    private LiveFrameRuntime scheduler(Runnable swap, DisplayOutput output) {
        return engine.createLiveFrameRuntime(new Engine.LiveEndpoints(capture -> {
            var before = state();
            try { render.invoke(engine, capture); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            assertEquals(before, state(), "real Engine drawing cannot advance native movement/counters");
            pictures++;
        }, swap, interval -> { }, () -> output, () -> output.advertisedRefreshHz() > 0, () -> clock));
    }

    /** Advances the fake monotonic clock by the host interval since the previous iteration. */
    private LiveFrameRuntime.Iteration iterate(LiveFrameRuntime runtime, long elapsedNanos) {
        clock += elapsedNanos;
        return runtime.iterate();
    }

    @ParameterizedTest @RequiresRom(SonicGame.SONIC_2)
    @CsvSource({"50,30", "50,50", "50,60", "50,120", "50,144",
            "60,30", "60,50", "60,60", "60,120", "60,144"})
    void originalS2MovementAndFullRegistryRestoreIgnorePictureSchedule(int hz, int fps) throws Exception {
        open(RomTestUtils.ensureSonic2RomAvailable().toPath(), hz, fps, false);
        compareRestoredRoute(hz, fps, false);
    }

    @ParameterizedTest @RequiresRom(SonicGame.SONIC_3K) @CsvSource({"50,120", "60,30"})
    void originalS3kRouteUsesTheSameProductionAdmissionAndRestore(int hz, int fps) throws Exception {
        open(RomTestUtils.ensureSonic3kRomAvailable().toPath(), hz, fps, false);
        compareRestoredRoute(hz, fps, true);
    }

    private void compareRestoredRoute(int hz, int fps, boolean jitter) throws Exception {
        var runtime = scheduler();
        iterate(runtime, 0); // register the actual scheduler restore invalidation, then capture.
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        var savedAudio = GameServices.audio().captureLogicalSnapshot();
        List<State> expected = new ArrayList<>();
        for (int tick = 0; tick < hz; tick++) {
            input.setLogicalOverride(routeInput(tick));
            engine.advanceFrame();
            expected.add(state());
        }
        assertNotEquals(expected.getFirst().x, expected.getLast().x, "real movement is required");
        registry.restore(saved);
        GameServices.audio().restoreLogicalSnapshot(savedAudio);
        input.clearLogicalOverride();
        List<State> observed = new ArrayList<>();
        engine.getGameLoop().setAudioPresentationProbe(mode -> {
            observed.add(state());
            input.setLogicalOverride(routeInput(observed.size()));
        });
        input.setLogicalOverride(routeInput(0));
        int frame = GameServices.sprites().getFrameCounter();
        iterate(runtime, SECOND); // restoration must invalidate this wall interval without a forward tick.
        assertEquals(frame, GameServices.sprites().getFrameCounter());
        long previous = 0;
        if (jitter) {
            long[] intervals = {1, 7_000_003, 80_000_000, 33_333_333, 900_017};
            int index = 0;
            while (previous < SECOND) {
                long delta = Math.min(intervals[index++ % intervals.length], SECOND - previous);
                iterate(runtime, delta); previous += delta;
                while (runtime.statistics().pendingTicks() > 0) iterate(runtime, 0);
            }
        } else {
            for (int picture = 1; picture <= fps; picture++) {
                long now = (picture * SECOND + fps - 1) / fps;
                iterate(runtime, now - previous); previous = now;
            }
        }
        assertEquals(expected, observed, "every authoritative state after full registered restoration and forward replay");
        assertEquals(hz, runtime.statistics().services());
        assertTrue(pictures > 1);
        var image = ScreenshotCapture.captureFramebuffer(320, 224);
        assertTrue(Arrays.stream(image.pixels()).anyMatch(p -> (p & 0xFFFFFF) != 0), "native scene replaces black");
        assertTrue(Arrays.stream(image.pixels()).anyMatch(p -> p != image.pixels()[0]), "terrain/player scene has varied pixels");
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void originalS2MovementAndRestoreMatchUnderTheDisplayLockedCadence() throws Exception {
        open(RomTestUtils.ensureSonic2RomAvailable().toPath(), 60, 60, false);
        double period = SECOND * 1001.0 / 60_000; // blocking 59.94 Hz vsync
        long[] vblank = {0};
        var runtime = scheduler(() -> {
            vblank[0] = Math.max((long) Math.floor(clock / period) + 1, vblank[0] + 1);
            clock = Math.round(vblank[0] * period);
        }, new DisplayOutput(1, 60));
        var last = runtime.iterate();
        for (int iteration = 0; !"LOCKED".equals(runtime.statistics().cadence()); iteration++) {
            assertTrue(iteration < 600, () -> "never locked: " + runtime.statistics());
            clock = Math.max(clock, last.wakeAtNanos());
            last = runtime.iterate();
        }
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        var savedAudio = GameServices.audio().captureLogicalSnapshot();
        List<State> expected = new ArrayList<>();
        for (int tick = 0; tick < 60; tick++) {
            input.setLogicalOverride(routeInput(tick));
            engine.advanceFrame();
            expected.add(state());
        }
        assertNotEquals(expected.getFirst().x, expected.getLast().x, "real movement is required");
        registry.restore(saved);
        GameServices.audio().restoreLogicalSnapshot(savedAudio);
        List<State> observed = new ArrayList<>();
        engine.getGameLoop().setAudioPresentationProbe(mode -> {
            observed.add(state());
            input.setLogicalOverride(routeInput(observed.size()));
        });
        input.setLogicalOverride(routeInput(0));
        long services = runtime.statistics().services();
        for (int swap = 0; swap < 60; swap++) {
            clock = Math.max(clock, last.wakeAtNanos());
            last = runtime.iterate();
            assertEquals(1, last.services(), "one service per paced swap, including the restore boundary");
        }
        assertEquals(expected, observed, "locked services replay the same native states as direct admission");
        assertEquals(60, runtime.statistics().services() - services);
        assertEquals("LOCKED", runtime.statistics().cadence(), "a restore boundary resets the window, not the lock");
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void actualPausedFrameStepAdmitsOneNativeFrameForcesAPictureAndRetiresReleasedTaps() throws Exception {
        open(RomTestUtils.ensureSonic2RomAvailable().toPath(), 60, 30, false);
        var runtime = scheduler(); iterate(runtime, 0);
        int frame = GameServices.sprites().getFrameCounter();
        input.handleKeyEvent(GLFW_KEY_F9, GLFW_PRESS);
        assertEquals(1, iterate(runtime, 100_000_000).services());
        assertTrue(engine.getGameLoop().isUserPaused());
        assertEquals(frame, GameServices.sprites().getFrameCounter());
        input.handleKeyEvent(GLFW_KEY_F9, GLFW_RELEASE);
        iterate(runtime, 16_666_667);
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_F8, GLFW_PRESS);
        int beforePictures = pictures;
        iterate(runtime, 16_666_667);
        assertEquals(frame + 1, GameServices.sprites().getFrameCounter());
        assertTrue(pictures > beforePictures, "a single-step picture does not wait for the 30 FPS window deadline");
        for (int picture = 0; picture < 4; picture++) iterate(runtime, 16_666_667);
        assertEquals(frame + 1, GameServices.sprites().getFrameCounter(), "held step is acknowledged once");
        input.handleKeyEvent(GLFW_KEY_F8, GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_F9, GLFW_PRESS);
        iterate(runtime, 16_666_667);
        assertFalse(engine.getGameLoop().isUserPaused());
        assertEquals(frame + 2, GameServices.sprites().getFrameCounter());
        assertEquals(0, input.logical().player1().actionPressedMask(), "resume retires paused taps");
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void actualRomSynthesisProducesOneSecondWithNoDeviceAt144Pictures() throws Exception {
        open(RomTestUtils.ensureSonic2RomAvailable().toPath(), 60, 144, true);
        assertInstanceOf(com.openggf.audio.HeadlessSmpsAudioBackend.class, GameServices.audio().getBackend());
        var runtime = scheduler(); iterate(runtime, 0);
        try (var tap = GameServices.audio().beginLiveCaptureAudio(60)) {
            long previous = 0;
            for (int picture = 1; picture <= 144; picture++) {
                long now = (picture * SECOND + 143) / 144;
                iterate(runtime, now - previous); previous = now;
            }
            short[] packet = new short[tap.maxStereoFramesPerPacket() * 2];
            int nonzero = 0;
            for (int tick = 0; tick < 60; tick++) {
                int frames = tap.drainPresentationFrame(packet);
                for (int i = 0; i < frames * 2; i++) if (packet[i] != 0) nonzero++;
            }
            assertEquals(GameServices.audio().outputSampleRate(), tap.totalStereoFrames());
            assertTrue(nonzero > 100, "silence cannot certify ROM synthesis");
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void realRecordingRowsFollowNativeAdmissionRatherThan144FpsPictures(@org.junit.jupiter.api.io.TempDir Path temp)
            throws Exception {
        open(RomTestUtils.ensureSonic2RomAvailable().toPath(), 60, 144, false);
        var recording = new com.openggf.game.recording.UserRecordingSession(
                new com.openggf.game.recording.RecordingLaunchContext("s2", 0, 0, "sonic", List.of(), false, "direct"),
                temp.resolve("scheduled.bk2"));
        var launcher = engine.getGameLoop().userRecordingSessionLauncher;
        var active = launcher.getClass().getDeclaredField("activeRecordingSession");
        active.setAccessible(true); active.set(launcher, recording);
        var runtime = scheduler(); iterate(runtime, 0);
        int frame = GameServices.sprites().getFrameCounter();
        for (int picture = 1; picture <= 144; picture++) {
            long now = (picture * SECOND + 143) / 144;
            long before = ((picture - 1L) * SECOND + 143) / 144;
            iterate(runtime, now - before);
            assertEquals(GameServices.sprites().getFrameCounter() - frame, recording.hudState().frame());
        }
        assertEquals(60, recording.hudState().frame());
        iterate(runtime, 0);
        assertEquals(60, recording.hudState().frame(), "a picture-only interval cannot duplicate a movie row");
        recording.requestStop(com.openggf.game.recording.UserRecordingStopReason.USER_STOPPED);
        assertFalse(recording.hudState().redWarning());
        var movie = new com.openggf.debug.playback.Bk2MovieLoader().load(temp.resolve("scheduled.bk2"));
        assertEquals(60, movie.getFrameCount(), "real BK2 output contains exactly the admitted native rows");
    }

    @ParameterizedTest @RequiresRom(SonicGame.SONIC_2) @CsvSource({"50,30", "60,144"})
    void realHeldRewindKeepsRegionServiceCadenceWithoutForwardRowsAndRebasesOnRelease(int hz, int fps)
            throws Exception {
        open(RomTestUtils.ensureSonic2RomAvailable().toPath(), hz, fps, false, true);
        var runtime = scheduler(); iterate(runtime, 0);
        int right = GameServices.configuration().getInt(SonicConfiguration.RIGHT);
        input.handleKeyEvent(right, GLFW_PRESS);
        for (int tick = 0; tick < 2 * hz; tick++) iterate(runtime, (SECOND + hz - 1) / hz);
        var history = SessionManager.getCurrentGameplayMode().getRewindController();
        assertNotNull(history, "the actual live owner must install native history");
        assertTrue(history.currentFrame() >= hz, "collect genuine forward history before holding rewind");
        int forward = history.currentFrame();
        input.handleKeyEvent(GLFW_KEY_F10, GLFW_PRESS);
        iterate(runtime, (SECOND + hz - 1) / hz);
        assertTrue(GameServices.audio().isReverseAudioOutputActive());
        assertTrue(history.currentFrame() < forward, "the production held-key owner must select an earlier native state");
        long services = runtime.statistics().services();
        var audio = GameServices.audio();
        long forwardPackets = AudioManagerTestDiagnostics.shadowParitySnapshot(audio).forwardFrames();
        try (var tap = audio.beginLiveCaptureAudio(hz)) {
            long previous = 0;
            for (int picture = 1; picture <= fps; picture++) {
                long now = (picture * SECOND + fps - 1) / fps;
                int before = history.currentFrame();
                iterate(runtime, now - previous); previous = now;
                assertTrue(history.currentFrame() <= before, "zero/multiple selected reverse states cannot admit a forward row");
            }
            assertEquals(hz, runtime.statistics().services() - services,
                    "internal reverse restores cannot reset the wall accumulator on every picture");
            assertEquals(forwardPackets, AudioManagerTestDiagnostics.shadowParitySnapshot(audio).forwardFrames());
            short[] pcm = new short[tap.maxStereoFramesPerPacket() * 2];
            for (int tick = 0; tick < hz; tick++) tap.drainPresentationFrame(pcm);
            assertEquals(audio.outputSampleRate(), tap.totalStereoFrames());
        }
        input.handleKeyEvent(GLFW_KEY_F10, GLFW_RELEASE);
        iterate(runtime, (SECOND + hz - 1) / hz);
        assertFalse(audio.isReverseAudioOutputActive(), "the existing release owner commits the selected sound state");
        int afterRelease = history.currentFrame();
        iterate(runtime, 0);
        assertEquals(afterRelease, history.currentFrame(), "old reverse debt cannot become an extra forward row");
    }

    private static LogicalInputSnapshot routeInput(int tick) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8, tick == 0 ? 8 : 0,
                tick >= 12 && tick < 20 ? 1 : 0, tick == 12 ? 1 : 0, false, false), PlayerInputState.neutral());
    }

    private static State state() {
        var player = GameServices.camera().getFocusedSprite();
        return new State(player.getCentreX(), player.getCentreY(), player.getXSubpixelRaw(), player.getYSubpixelRaw(),
                player.getXSpeed(), player.getYSpeed(), player.getGSpeed(), player.getAir(), player.getRolling(),
                player.getAnimationId(), player.getAnimationFrameIndex(), player.getAnimationTick(),
                GameServices.level().getFrameCounter(), GameServices.sprites().getFrameCounter());
    }
    private record State(int x, int y, int subX, int subY, int vx, int vy, int groundSpeed, boolean air,
                         boolean rolling, int animation, int animationFrame, int animationTick, int levelFrame, int spriteFrame) { }
}
