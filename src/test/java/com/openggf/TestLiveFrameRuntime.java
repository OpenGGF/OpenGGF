package com.openggf;

import com.openggf.level.PictureSelection;
import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.LegalDisclaimerScreen;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.*;

/** Actual Engine/GameLoop/input/fade/audio caller; substitutes GL resources and picture endpoint only. */
@Isolated
class TestLiveFrameRuntime {
    private static final long SECOND = 1_000_000_000L;
    private Engine engine;
    private InputHandler input;
    private AudioManager audio;
    private ObservedScreen screen;
    private FadeManager fade;
    private int pictures, swaps;
    private long now;
    private com.openggf.configuration.SonicConfigurationService config;
    private DisplayOutput output = DisplayOutput.UNKNOWN;
    private boolean regulated;
    private final List<Integer> intervals = new ArrayList<>();

    @AfterEach void close() {
        if (engine != null) engine.getGameLoop().closePresence();
        Engine.clearGlobalInstance();
        TestEnvironment.resetEngineOnly();
    }

    private LiveFrameRuntime open(int hz, int fps) throws Exception {
        TestEnvironment.resetEngineOnly();
        var original = EngineServices.current();
        config = original.configuration();
        config.setConfigValue(SonicConfiguration.REGION, hz == 50 ? "PAL" : "NTSC");
        config.setConfigValue(SonicConfiguration.FPS, fps);
        config.setConfigValue(SonicConfiguration.P1_A, GLFW_KEY_SPACE);
        config.setConfigValue(SonicConfiguration.PAUSE_KEY, GLFW_KEY_F9);
        config.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, GLFW_KEY_F8);
        audio = original.audio();
        audio.setBackend(new NullAudioBackend());
        audio.resetState();
        var graphics = mock(GraphicsManager.class);
        fade = new FadeManager();
        var pipeline = new UiRenderPipeline(graphics);
        pipeline.setFadeManager(fade);
        when(graphics.getUiRenderPipeline()).thenReturn(pipeline);
        when(graphics.getFadeManager()).thenReturn(fade);
        engine = new Engine(new EngineContext(config, graphics, audio, original.roms(),
                original.profiler(), original.debugOverlay(), original.playbackDebug(),
                original.romDetection(), original.crossGameFeatures()));
        input = new InputHandler(InputBindingFactory.supplier(config));
        engine.setInputHandler(input);
        engine.getGameLoop().setGameMode(GameMode.LEGAL_DISCLAIMER);
        screen = new ObservedScreen();
        field("legalDisclaimerScreen", screen);
        return runtime(capture -> pictures++, () -> swaps++);
    }

    private LiveFrameRuntime runtime(java.util.function.Consumer<LivePictureRequest> render, Runnable swap) {
        return engine.createLiveFrameRuntime(new Engine.LiveEndpoints(render, swap, intervals::add, () -> output,
                () -> regulated, () -> now));
    }

    /** Advances the fake monotonic clock by the host interval since the previous iteration. */
    private LiveFrameRuntime.Iteration iterate(LiveFrameRuntime runtime, long elapsedNanos) {
        now += elapsedNanos;
        return runtime.iterate();
    }

    /** Engine.loop on fake time: each iteration starts at the previous wake deadline. */
    private List<LiveFrameRuntime.Iteration> runUntil(LiveFrameRuntime runtime, LiveFrameRuntime.Iteration last, long end) {
        var iterations = new ArrayList<LiveFrameRuntime.Iteration>();
        while (last.wakeAtNanos() <= end) {
            now = Math.max(now, last.wakeAtNanos());
            last = runtime.iterate();
            iterations.add(last);
        }
        return iterations;
    }

    /** Blocking swap at the display's vblank lattice, honouring the requested swap interval. */
    private final class FakeVsync {
        final double period;
        int interval = 1;
        long lastVblank = Long.MIN_VALUE;
        FakeVsync(double displayHz) { period = SECOND / displayHz; }
        void swap() {
            long next = (long) Math.floor(now / period) + 1;
            if (lastVblank != Long.MIN_VALUE) next = Math.max(next, lastVblank + interval);
            lastVblank = next;
            now = Math.round(next * period);
            swaps++;
        }
    }

    private LiveFrameRuntime paced(FakeVsync vsync, java.util.function.Consumer<LivePictureRequest> render) {
        output = new DisplayOutput(1, (int) Math.round(SECOND / vsync.period));
        regulated = true;
        return engine.createLiveFrameRuntime(new Engine.LiveEndpoints(render, vsync::swap,
                interval -> { intervals.add(interval); vsync.interval = interval; }, () -> output,
                () -> regulated, () -> now));
    }

    private LiveFrameRuntime.Iteration runUntilLocked(LiveFrameRuntime runtime, LiveFrameRuntime.Iteration last) {
        for (int iteration = 0; !"LOCKED".equals(runtime.statistics().cadence()); iteration++) {
            assertTrue(iteration < 600, () -> "never locked: " + runtime.statistics());
            now = Math.max(now, last.wakeAtNanos());
            last = runtime.iterate();
        }
        return last;
    }

    private static LiveFrameRuntime.HostState host(int hz, int fps, Object boundary) {
        return new LiveFrameRuntime.HostState(hz, fps, true, false, boundary, false, false,
                com.openggf.configuration.DisplayPacing.EXACT, DisplayOutput.UNKNOWN, false, false, false, false);
    }

    @ParameterizedTest @CsvSource({"50,30", "50,50", "50,60", "50,120", "50,144",
            "60,30", "60,50", "60,60", "60,120", "60,144"})
    void actualCallerHasIdenticalPerTickStateAndOneSecondOfPcmAcrossPictureSchedules(int hz, int fps)
            throws Exception {
        var runtime = open(hz, fps);
        var initial = runtime.iterate();
        pictures = swaps = 0;
        var expected = new ArrayList<Long>();
        long state = 0;
        for (int tick = 0; tick < hz; tick++) {
            state = state * 17 + tick % 7;
            expected.add(state);
        }
        screen.after = () -> input.setLogicalOverride(sample(screen.samples.size() % 7));
        input.setLogicalOverride(sample(0));
        fade.startFadeFromBlack(null);
        try (var tap = audio.beginLiveCaptureAudio(hz)) {
            var iterations = runUntil(runtime, initial, SECOND);
            assertEquals(expected, screen.states, "all completed real GameLoop samples, not only a final counter");
            assertEquals(hz, screen.samples.size());
            assertEquals(hz, iterations.size(), "the loop wakes on service deadlines, not picture deadlines");
            assertEquals(Math.min(fps, hz), swaps, "pictures follow completed services, thinned by the cap");
            assertEquals(Math.min(fps, hz), pictures, "unchanged state is never redrawn");
            if (fps >= hz) assertTrue(iterations.stream().allMatch(i -> i.services() == 1 && i.presented()),
                    "a service and its picture share one iteration");
            assertEquals(hz, AudioManagerTestDiagnostics.shadowParitySnapshot(audio).presentedFrames());
            short[] packet = new short[2 * tap.maxStereoFramesPerPacket()];
            for (int tick = 0; tick < hz; tick++) tap.drainPresentationFrame(packet);
            assertEquals(48_000, tap.totalStereoFrames(), "region PCM duration cannot follow the display cap");
            assertEquals(0, runtime.statistics().pendingTicks());
            assertEquals(0, runtime.statistics().discardedWallNanos());
        }
    }

    @ParameterizedTest @CsvSource({"60,AUTO,PANEL_RATE", "60,60,FPS_LIMIT", "60,144,PANEL_RATE", "50,AUTO,PANEL_RATE", "0,AUTO,FPS_LIMIT"})
    void liveAutoDiagnosticSeparatesPanelRateFromExplicitLimit(int refresh, String limit, String status) throws Exception {
        var runtime = open(60, 60);
        config.setConfigValue(SonicConfiguration.FPS, limit);
        config.setConfigValue(SonicConfiguration.DISPLAY_INTERPOLATION, true);
        output = new DisplayOutput(1, refresh);
        runtime.iterate();
        iterate(runtime, 16_666_667);
        assertEquals(status, runtime.statistics().interpolation().name());
    }

    @ParameterizedTest @CsvSource({"120,2", "240,4"})
    void autoWithInterpolationOffAllowsMultipleRefreshLock(int refresh, int interval) throws Exception {
        open(60, 60);
        config.setConfigValue(SonicConfiguration.FPS, "AUTO");
        config.setConfigValue(SonicConfiguration.DISPLAY_INTERPOLATION, false);
        var runtime = paced(new FakeVsync(refresh), request -> pictures++);
        runUntilLocked(runtime, runtime.iterate());
        assertEquals(interval, runtime.statistics().swapInterval());
        assertEquals(LiveFrameRuntime.InterpolationStatus.OFF, runtime.statistics().interpolation());
    }

    @Test void firstIterationIsTheSoleSwapIntervalWriter() throws Exception {
        var runtime = open(60, 60);
        assertTrue(intervals.isEmpty());
        runUntil(runtime, runtime.iterate(), SECOND);
        assertEquals(List.of(1), intervals);
    }

    @Test void cap30At60HzPresentsEveryOtherServiceWithoutRedrawingUnchangedState() throws Exception {
        var runtime = open(60, 30);
        var iterations = runUntil(runtime, runtime.iterate(), SECOND);
        assertEquals(60, iterations.size());
        assertTrue(iterations.stream().allMatch(i -> i.services() == 1));
        for (int index = 0; index < iterations.size(); index++) {
            assertEquals(index % 2 == 1, iterations.get(index).presented(), "iteration " + index);
        }
        assertEquals(31, swaps, "the initial picture plus 30 thinned service pictures");
        assertEquals(31, pictures);
    }

    @Test void zeroTickPicturesCannotAcknowledgeTwoReleasedInputTaps() throws Exception {
        var runtime = open(60, 144);
        runtime.iterate();
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
        var early = iterate(runtime, 7_000_000);
        assertEquals(0, early.services());
        assertFalse(early.presented(), "an early wake has no completed service to show");
        assertEquals(0, iterate(runtime, 7_000_000).services());
        assertTrue(screen.samples.isEmpty());
        assertEquals(3, iterate(runtime, 36_000_000).services()); // 50 ms admits three ticks.
        iterate(runtime, 16_666_667);
        assertEquals(List.of(1, 0, 1, 0), screen.samples.stream()
                .map(s -> s.player1().actionPressedMask()).toList());
        assertEquals(List.of(1, 0, 1, 0), screen.samples.stream()
                .map(s -> s.player1().actionHeldMask()).toList());
    }

    @Test void shortStallRetainsDebtAndLongStallBoundsEachRealCallerBatch() throws Exception {
        var runtime = open(60, 30);
        runtime.iterate();
        var stalled = iterate(runtime, 100_000_000);
        assertEquals(4, stalled.services());
        assertEquals(2, runtime.statistics().pendingTicks());
        assertEquals(now, stalled.wakeAtNanos(), "pending services wake immediately");
        assertEquals(2, iterate(runtime, 0).services());
        assertEquals(6, screen.samples.size());
        assertEquals(4, iterate(runtime, 5 * SECOND).services());
        assertEquals(4_750_000_000L, runtime.statistics().discardedWallNanos());
        assertEquals(11, runtime.statistics().pendingTicks());
        for (int batch = 0; batch < 3; batch++) assertTrue(iterate(runtime, 0).services() <= 4);
        assertEquals(21, screen.samples.size(), "discarded wall time never invents skipped semantic identities");
        assertEquals(0, runtime.statistics().pendingTicks());
    }

    @Test void pauseBoundaryStopsCatchUpAndResumeCannotSpendThePausedInterval() throws Exception {
        var runtime = open(60, 144);
        runtime.iterate();
        // The actual host pause owner is active only in gameplay-capable modes. A boot
        // screen update toggles through that owner here, without manufacturing a world.
        screen.after = () -> engine.getGameLoop().toggleUserPause();
        assertEquals(1, iterate(runtime, 100_000_000).services());
        assertTrue(engine.getGameLoop().isUserPaused());
        assertEquals(0, runtime.statistics().pendingTicks());
        screen.after = () -> { };
        engine.getGameLoop().toggleUserPause();
        assertEquals(0, iterate(runtime, 10 * SECOND).services(), "external pause boundary rebases before admission");
        assertEquals(1, iterate(runtime, 16_666_667).services());
        assertEquals(2, screen.samples.size());
    }

    @Test void forcedScenePictureEndsTheBatchBeforeAnotherServiceCanParkIt() throws Exception {
        open(60, 30);
        var capture = mock(GameLoopSuspension.CaptureEffects.class);
        when(capture.captureViewportThumbnail()).thenReturn(new com.openggf.graphics.RgbaImage(1, 1, new int[]{0xFFFFFFFF}));
        when(capture.thumbnailWidth()).thenReturn(1);
        when(capture.thumbnailHeight()).thenReturn(1);
        var owner = engine.getGameLoop().suspension;
        var runtime = runtime(output -> {
            pictures++;
            owner.afterScene(capture);
        }, () -> swaps++);
        runtime.iterate();
        var request = GameLoopSuspension.class.getDeclaredField("suspendThumbnailRequested");
        request.setAccessible(true);
        screen.after = () -> {
            try { request.setBoolean(owner, true); }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        };
        int before = swaps;
        assertEquals(1, iterate(runtime, 100_000_000).services(), "a thumbnail opportunity ends the old batch");
        screen.after = () -> { };
        assertFalse(owner.requiresScenePicture(), "the real after-scene owner consumes its request");
        assertEquals(before + 1, swaps, "the full-size picture is shown before another service can park the world");
        int afterPicture = swaps;
        assertEquals(0, iterate(runtime, 0).services(), "old reserved allowances cannot run after that boundary");
        assertEquals(afterPicture, swaps, "the consumed request is not a second boundary change");
    }

    @Test void sustainedOverloadStillPollsBetweenBoundedRealCallerBatchesAndAdmitsEachTapOnce() throws Exception {
        var runtime = open(60, 30);
        runtime.iterate();
        for (int batch = 0; batch < 20; batch++) {
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
            input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
            assertEquals(4, iterate(runtime, 500_000_000).services());
            var samples = screen.samples.subList(batch * 4, batch * 4 + 4);
            assertEquals(List.of(1, 0, 0, 0), samples.stream().map(x -> x.player1().actionPressedMask()).toList());
            assertTrue(runtime.statistics().pendingTicks() <= 11);
        }
        assertEquals(80, screen.samples.size());
        assertTrue(runtime.statistics().discardedWallNanos() > 8 * SECOND);
    }

    @Test void failedSemanticServiceStopsTheBatchBeforeAnyPictureOrSwap() {
        var owner = mock(LiveFrameRuntime.Owner.class);
        when(owner.hostState()).thenReturn(host(60, 30, "visit"));
        when(owner.draw(any())).thenReturn(true);
        var runtime = new LiveFrameRuntime(owner, () -> now);
        runtime.iterate();
        clearInvocations(owner);
        when(owner.tick(anyBoolean())).thenReturn(false);
        assertEquals(0, iterate(runtime, 100_000_000).services());
        verify(owner, times(1)).tick(anyBoolean());
        verify(owner, never()).draw(any());
        verify(owner, never()).swap();
        assertEquals(0, runtime.statistics().pendingTicks());
    }

    @Test void transitionWorkIsExcludedFromTheNewBoundaryWallAnchor() {
        var owner = mock(LiveFrameRuntime.Owner.class);
        var boundary = new java.util.concurrent.atomic.AtomicReference<>("old");
        long[] work = {SECOND}; // one second spent in the transitioning service
        when(owner.hostState()).thenAnswer(invocation -> host(60, 30, boundary.get()));
        when(owner.draw(any())).thenReturn(true);
        when(owner.tick(anyBoolean())).thenAnswer(invocation -> {
            boundary.set("new");
            now += work[0];
            return true;
        });
        var runtime = new LiveFrameRuntime(owner, () -> now);
        runtime.iterate();

        var transition = iterate(runtime, 100_000_000L);
        assertEquals(1, transition.services());
        assertEquals(0, runtime.statistics().pendingTicks());
        long completed = 1_100_000_000L;
        assertEquals(completed + 16_666_667L, transition.wakeAtNanos());
        work[0] = 5_000_000L;
        now = transition.wakeAtNanos();
        long observed = now;
        var next = runtime.iterate();
        assertEquals(1, next.services());
        assertEquals(0, runtime.statistics().pendingTicks());
        assertEquals(observed + 16_666_667L, next.wakeAtNanos(),
                "ordinary work and swap time remain part of the following interval");
    }

    @Test void focusLossAndRegainInOnePollStillInvalidateOldWallDebt() throws Exception {
        var runtime = open(60, 144);
        runtime.iterate();
        engine.observeWindowActivation();
        assertTrue(Engine.applyWindowActivation(false, input, engine.getGameLoop()::pause, engine.getGameLoop()::resume));
        engine.observeWindowActivation();
        assertFalse(Engine.applyWindowActivation(true, input, engine.getGameLoop()::pause, engine.getGameLoop()::resume));
        assertEquals(0, iterate(runtime, 10 * SECOND).services(), "the final focused boolean alone misses this discontinuity");
        assertEquals(1, iterate(runtime, 16_666_667).services());
    }

    @Test void blockingNearRegionDisplayLocksWithIdenticalPerServiceStateAnd800FramesPerService() throws Exception {
        open(60, 60);
        var vsync = new FakeVsync(60_000.0 / 1001);
        var runtime = paced(vsync, capture -> pictures++);
        screen.after = () -> input.setLogicalOverride(sample(screen.samples.size() % 7));
        input.setLogicalOverride(sample(0));
        try (var tap = audio.beginLiveCaptureAudio(60)) {
            short[] packet = new short[2 * tap.maxStereoFramesPerPacket()];
            int drained = 0;
            var last = runtime.iterate();
            int swapsAtLock = 0, picturesAtLock = 0, locked = 0;
            long servicesAtLock = 0;
            while (locked < 600) {
                for (; drained < screen.samples.size(); drained++) {
                    assertEquals(800, tap.drainPresentationFrame(packet), "region PCM per service, not per display period");
                }
                if (!"LOCKED".equals(runtime.statistics().cadence())) {
                    assertTrue(now < 3 * SECOND, "two good windows lock in about two seconds");
                } else if (locked++ == 0) {
                    swapsAtLock = swaps;
                    picturesAtLock = pictures;
                    servicesAtLock = runtime.statistics().services();
                } else {
                    assertEquals(1, last.services());
                    assertTrue(last.presented());
                }
                now = Math.max(now, last.wakeAtNanos());
                last = runtime.iterate();
            }
            for (; drained < screen.samples.size(); drained++) assertEquals(800, tap.drainPresentationFrame(packet));
            assertEquals(List.of(1), intervals, "interval 1 is written once; the probe needs no other value");
            assertEquals("LOCKED", runtime.statistics().cadence());
            assertEquals("NONE", runtime.statistics().reason());
            assertEquals(600, swaps - swapsAtLock, "exactly one swap per locked service");
            assertEquals(600, pictures - picturesAtLock, "exactly one picture per locked service");
            assertEquals(600, runtime.statistics().services() - servicesAtLock);
            assertEquals(60_000.0 / 1001, runtime.statistics().measuredPresentHz(), 0.01);
            var expected = new ArrayList<Long>();
            long state = 0;
            for (int tick = 0; tick < screen.states.size(); tick++) {
                state = state * 17 + tick % 7;
                expected.add(state);
            }
            assertEquals(expected, screen.states, "tick-indexed state matches the exact schedule's recurrence");
            int services = screen.samples.size();
            assertEquals(services, AudioManagerTestDiagnostics.shadowParitySnapshot(audio).presentedFrames());
            assertEquals(800L * services, tap.totalStereoFrames());
        }
    }

    @Test void exactPacingAndUnregulatedSpeakerNeverProbe() throws Exception {
        open(60, 60);
        var runtime = paced(new FakeVsync(60), capture -> pictures++);
        config.setConfigValue(SonicConfiguration.DISPLAY_PACING, "EXACT");
        runUntil(runtime, runtime.iterate(), 3 * SECOND);
        assertEquals("EXACT", runtime.statistics().cadence());
        assertEquals("PACING_EXACT", runtime.statistics().reason());
        config.setConfigValue(SonicConfiguration.DISPLAY_PACING, "AUTO");
        regulated = false;
        runUntil(runtime, runtime.iterate(), 6 * SECOND);
        assertEquals("EXACT", runtime.statistics().cadence());
        assertEquals("SPEAKER_UNREGULATED", runtime.statistics().reason());
        assertEquals(List.of(1), intervals);
    }

    @Test void nonBlockingSwapRefusesTheProbeWithoutBusyLoopingAndStaysExact() throws Exception {
        open(60, 60);
        output = new DisplayOutput(1, 60);
        regulated = true;
        var runtime = runtime(capture -> pictures++, () -> swaps++);
        var iterations = runUntil(runtime, runtime.iterate(), 2 * SECOND);
        assertEquals("EXACT", runtime.statistics().cadence());
        assertEquals("NOT_BLOCKING", runtime.statistics().reason());
        assertTrue(iterations.size() <= 2 * 60 + 2, "a refused probe returns to service-deadline wakes");
        assertEquals(120, screen.samples.size(), "the refused probe neither added nor dropped services");
    }

    @Test void focusLossWhileLockedWaitsInExactAndRegainReprobes() throws Exception {
        open(60, 60);
        var runtime = paced(new FakeVsync(60), capture -> pictures++);
        runUntilLocked(runtime, runtime.iterate());
        int services = screen.samples.size();
        field("paused", true);
        var unfocused = runtime.iterate();
        assertEquals(0, unfocused.services());
        assertFalse(unfocused.presented());
        assertEquals(now + LiveFrameRuntime.UNFOCUSED_WAIT_NANOS, unfocused.wakeAtNanos());
        assertEquals("EXACT", runtime.statistics().cadence());
        now += 10 * SECOND;
        field("paused", false);
        var regained = runtime.iterate();
        assertEquals(0, regained.services(), "regaining focus rebases without forward admission");
        assertEquals(services, screen.samples.size());
        assertEquals("PROBING", runtime.statistics().cadence());
        runUntilLocked(runtime, regained);
    }

    @Test void forcedPictureWhileLockedIsShownWithItsServiceAndKeepsTheLock() throws Exception {
        open(60, 60);
        var capture = mock(GameLoopSuspension.CaptureEffects.class);
        when(capture.captureViewportThumbnail()).thenReturn(new com.openggf.graphics.RgbaImage(1, 1, new int[]{0xFFFFFFFF}));
        when(capture.thumbnailWidth()).thenReturn(1);
        when(capture.thumbnailHeight()).thenReturn(1);
        var owner = engine.getGameLoop().suspension;
        var runtime = paced(new FakeVsync(60), output -> {
            pictures++;
            owner.afterScene(capture);
        });
        var last = runUntilLocked(runtime, runtime.iterate());
        var request = GameLoopSuspension.class.getDeclaredField("suspendThumbnailRequested");
        request.setAccessible(true);
        screen.after = () -> {
            try { request.setBoolean(owner, true); }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        };
        int before = swaps, picturesBefore = pictures;
        now = Math.max(now, last.wakeAtNanos());
        last = runtime.iterate();
        screen.after = () -> { };
        assertEquals(1, last.services());
        assertFalse(owner.requiresScenePicture(), "the real after-scene owner consumes its request");
        assertEquals(before + 1, swaps, "the picture is shown with its service");
        assertEquals(picturesBefore + 1, pictures, "the forced picture is the window picture");
        now = Math.max(now, last.wakeAtNanos());
        assertEquals(1, runtime.iterate().services(), "the next paced swap carries one service");
        assertEquals("LOCKED", runtime.statistics().cadence(), "a boundary change resets the window, not the lock");
    }

    @Test void deviceOnlyServiceCannotRetireAudioCommandsOrProduceHistory() throws Exception {
        open(60, 144);
        long frame = audio.commandTimeline().currentFrame();
        for (int poll = 0; poll < 20; poll++) com.openggf.audio.AudioManagerInternalAccess.serviceDevice(audio);
        assertEquals(frame, audio.commandTimeline().currentFrame());
        assertEquals(0, AudioManagerTestDiagnostics.shadowParitySnapshot(audio).presentedFrames());
    }

    @Test void focusAndClockDiscontinuitiesRebaseWithoutForwardAdmission() throws Exception {
        var runtime = open(50, 120);
        runtime.iterate();
        field("paused", true);
        var unfocused = iterate(runtime, 20 * SECOND);
        assertEquals(0, unfocused.services());
        assertFalse(unfocused.presented());
        assertEquals(now + LiveFrameRuntime.UNFOCUSED_WAIT_NANOS, unfocused.wakeAtNanos(), "unfocused waits, never spins");
        field("paused", false);
        assertEquals(0, iterate(runtime, 20 * SECOND).services());
        assertEquals(1, iterate(runtime, 20_000_000).services());
        assertEquals(0, iterate(runtime, -1).services());
        assertEquals(1, iterate(runtime, 20_000_000).services());
        assertEquals(2, screen.samples.size());
    }

    @Test void modalHostPassAcknowledgesWithoutAWorldOrPictureAndKeepsRegionAudioSilent() throws Exception {
        var runtime = open(60, 30);
        runtime.iterate();
        var picker = new com.openggf.graphics.shaderlib.DisplayShaderPickerController(
                new com.openggf.graphics.shaderlib.DisplayShaderSelectionModel(List.of(
                        com.openggf.graphics.shaderlib.DisplayShaderPresetRef.OFF)), GLFW_KEY_F6);
        // Constructor shape deliberately comes from the same production fixture as Engine advance.
        field("displayShaderPickerController", picker);
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_PRESS);
        iterate(runtime, 16_666_667);
        assertTrue(screen.samples.isEmpty());
        assertEquals(1, AudioManagerTestDiagnostics.shadowParitySnapshot(audio).silentFrames());
        assertEquals(1, pictures, "the initial picture only: host UI service does not require a swap");
    }

    /** A zero display rate keeps the exact cadence; otherwise a blocking fake vsync locks first. */
    @ParameterizedTest @CsvSource({"50,30,false,0", "50,144,true,0", "60,30,true,0", "60,144,false,0",
            "50,144,true,50", "60,60,false,59.94005994", "60,144,true,59.94005994"})
    void actualScheduledCaptureKeepsOutputTimestampsPicturesAndPcmIndependentOfSwaps(int hz, int fps, boolean deferred,
            double displayHz) throws Exception {
        open(hz, fps);
        int[] sourceSample = {0};
        var encoder = new CollectingEncoder();
        var finalizer = java.util.concurrent.Executors.newSingleThreadExecutor();
        var viewport = new com.openggf.capture.CaptureViewport(0, 0, 1, 1);
        var controller = new com.openggf.capture.LiveCaptureController(new com.openggf.capture.LiveCaptureController.Dependencies(
                audio::beginLiveCaptureAudio, audio::outputSampleRate,
                ignored -> new com.openggf.capture.VideoFrameGrabber() {
                    final java.util.ArrayDeque<byte[]> pending = new java.util.ArrayDeque<>();
                    public int width() { return 1; }
                    public int height() { return 1; }
                    public boolean deferredReadback() { return deferred; }
                    public byte[] grab() { return java.nio.ByteBuffer.allocate(4).putInt(screen.samples.size()).array(); }
                    public void beginReadback() { pending.add(grab()); }
                    public void grabInto(byte[] target) {
                        byte[] pixels = deferred ? pending.remove() : grab();
                        System.arraycopy(pixels, 0, target, 0, target.length);
                    }
                }, (ignored, rate) -> new com.openggf.capture.CaptureRecorder(encoder,
                        com.openggf.capture.BackpressurePolicy.BLOCK, 2, java.nio.file.Path.of("target"), "live-scheduled", "test"),
                finalizer, java.time.Duration.ofSeconds(5)));
        try (controller) {
            field("liveCaptureController", controller);
            field("liveCaptureFrameRate", hz);
            var presentation = new com.openggf.capture.LiveCapturePresentationCoordinator(controller);
            java.util.function.Consumer<LivePictureRequest> render = capture -> presentation.present(viewport, capture.capture(), () -> { }, () -> { });
            boolean paced = displayHz > 0;
            var runtime = paced ? paced(new FakeVsync(displayHz), render) : runtime(render, () -> swaps++);
            var last = runtime.iterate();
            if (paced) last = runUntilLocked(runtime, last);
            int base = screen.samples.size();
            int swapsBefore = swaps;
            long picturesBefore = runtime.statistics().pictures();
            audio.setScenePcmSource((target, frames) -> {
                for (int i = 0; i < frames; i++) {
                    short value = (short) (sourceSample[0]++ % 30_000);
                    target[2 * i] = value; target[2 * i + 1] = (short) -value;
                }
            });
            controller.start(viewport, hz);
            if (paced) {
                for (int service = 0; service < hz; service++) {
                    now = Math.max(now, last.wakeAtNanos());
                    last = runtime.iterate();
                    assertEquals(1, last.services(), "one locked service per paced swap");
                }
                assertEquals("LOCKED", runtime.statistics().cadence());
            } else {
                runUntil(runtime, last, SECOND);
            }
            controller.requestStop(com.openggf.capture.LiveCaptureController.StopReason.USER);
            finalizer.submit(() -> { }).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(com.openggf.capture.LiveCaptureController.State.INACTIVE, controller.state());
            assertNull(controller.lastFailure());
            assertEquals(hz, encoder.fps);
            assertEquals(hz, encoder.frames.size(), "one actual final-viewport picture for every output interval");
            int pcmIndex = 0;
            for (int index = 0; index < hz; index++) {
                var frame = encoder.frames.get(index);
                assertEquals(index, frame.frameIndex(), "CFR timestamp is ordinal / region output Hz");
                assertEquals(base + index + 1, java.nio.ByteBuffer.wrap(frame.rgba()).getInt(), "capture's current semantic picture");
                for (int sample = 0; sample < frame.sampleCount(); sample++) {
                    short value = (short) (pcmIndex++ % 30_000);
                    assertEquals(value, frame.pcm()[2 * sample]);
                    assertEquals((short) -value, frame.pcm()[2 * sample + 1]);
                }
            }
            assertEquals(48_000, pcmIndex, "video end time and PCM duration both span exactly one second");
            assertEquals(48_000, sourceSample[0], "capture/presentation repeats cannot synthesize audio");
            assertEquals(Math.min(fps, hz), swaps - swapsBefore, "capture draws cannot silently raise the window swap rate");
            assertEquals(hz, runtime.statistics().pictures() - picturesBefore,
                    "a coincident window present reuses the capture draw; no capture-only redraws");
        }
    }

    @ParameterizedTest @CsvSource({"50,75", "50,144", "50,165", "60,75", "60,144", "60,165"})
    void interpolatedActualCallerPreservesEveryTickStateAndPcm(int hz, int cap) throws Exception {
        var runtime = open(hz, cap);
        config.setConfigValue(SonicConfiguration.DISPLAY_INTERPOLATION, true);
        screen.after = () -> input.setLogicalOverride(sample(screen.samples.size() % 7));
        input.setLogicalOverride(sample(0));
        try (var tap = audio.beginLiveCaptureAudio(hz)) {
            runUntil(runtime, runtime.iterate(), SECOND);
            var expected = new ArrayList<Long>();
            long state = 0;
            for (int tick = 0; tick < hz; tick++) {
                state = state * 17 + tick % 7;
                expected.add(state);
            }
            assertEquals(expected, screen.states);
            short[] packet = new short[2 * tap.maxStereoFramesPerPacket()];
            for (int tick = 0; tick < hz; tick++) tap.drainPresentationFrame(packet);
            assertEquals(48_000, tap.totalStereoFrames());
            assertTrue(pictures > hz);
        }
    }

    private final class InterpolatedHost implements LiveFrameRuntime.Owner {
        final int hz, cap;
        boolean enabled = true, paused, capture, forced, screenshot;
        Object boundary = "visit";
        int ticks;
        long work;
        final List<LivePictureRequest> requests = new ArrayList<>();
        final List<Boolean> retained = new ArrayList<>();
        InterpolatedHost(int hz, int cap) { this.hz = hz; this.cap = cap; }
        public LiveFrameRuntime.HostState hostState() {
            return new LiveFrameRuntime.HostState(hz, cap, true, paused, boundary, capture, forced,
                    com.openggf.configuration.DisplayPacing.EXACT, DisplayOutput.UNKNOWN, false, enabled, screenshot, false);
        }
        public boolean tick(boolean active) { ticks++; retained.add(active); now += work; return true; }
        public boolean draw(LivePictureRequest picture) { requests.add(picture); screenshot = false; return true; }
        public void swap() { }
        public void swapInterval(int interval) { assertEquals(1, interval); }
        public void serviceDevice() { }
    }

    @ParameterizedTest @CsvSource({"50,75", "50,144", "50,165", "60,75", "60,144", "60,165"})
    void interpolationChangesPicturesWithoutChangingServiceSequence(int hz, int cap) {
        var host = new InterpolatedHost(hz, cap);
        var runtime = new LiveFrameRuntime(host, () -> now);
        runUntil(runtime, runtime.iterate(), SECOND);
        assertEquals(hz, host.ticks);
        assertTrue(host.requests.size() > hz, "render deadlines redraw between services");
        assertEquals("EXACT", runtime.statistics().cadence());
        assertTrue(host.retained.stream().allMatch(Boolean::booleanValue));
        assertTrue(host.requests.stream().anyMatch(p -> p.selection() instanceof PictureSelection.Between));
        assertEquals(LiveFrameRuntime.InterpolationStatus.BLENDING, runtime.statistics().interpolation());
        host.requests.forEach(p -> {
            if (p.selection() instanceof PictureSelection.Between between) {
                assertTrue(between.alpha() >= 0 && between.alpha() < 1);
            }
        });
        now = 0;
        host.enabled = false;
        host.ticks = 0;
        host.requests.clear();
        runtime = new LiveFrameRuntime(host, () -> now);
        runUntil(runtime, runtime.iterate(), SECOND);
        assertEquals(hz, host.ticks);
        assertTrue(host.requests.stream().allMatch(p -> p.selection() instanceof PictureSelection.Latest));
        assertEquals(LiveFrameRuntime.InterpolationStatus.OFF, runtime.statistics().interpolation());
    }

    @Test void interpolationStatusNamesTheFpsLimitAndReportsMeasuredRates() {
        var host = new InterpolatedHost(60, 60);
        var runtime = new LiveFrameRuntime(host, () -> now);
        runUntil(runtime, runtime.iterate(), 3 * SECOND);
        var stats = runtime.statistics();
        assertEquals(LiveFrameRuntime.InterpolationStatus.FPS_LIMIT, stats.interpolation());
        assertEquals(0, stats.blendedPerSecond());
        assertEquals(60, stats.servicesPerSecond(), 1.5);

        now = 0;
        var blending = new LiveFrameRuntime(new InterpolatedHost(60, 144), () -> now);
        runUntil(blending, blending.iterate(), 3 * SECOND);
        var measured = blending.statistics();
        stats = measured;
        assertEquals(60, stats.servicesPerSecond(), 1.5);
        assertTrue(stats.picturesPerSecond() > 60, () -> "render deadlines add pictures: " + measured);
        assertTrue(stats.blendedPerSecond() > 0, () -> "blended pictures are counted: " + measured);
        assertTrue(stats.lastAlpha() >= 0 && stats.lastAlpha() < 1);
    }

    @Test void screenshotBarrierNeverMovesBackwardWithinAServiceInterval() {
        var host = new InterpolatedHost(60, 165);
        var runtime = new LiveFrameRuntime(host, () -> now);
        runtime.iterate();
        iterate(runtime, 16_666_667);
        host.requests.clear();
        iterate(runtime, 2_000_000);
        var first = (PictureSelection.Between) host.requests.getLast().selection();
        iterate(runtime, 6_000_000);
        var second = (PictureSelection.Between) host.requests.getLast().selection();
        assertTrue(second.alpha() > first.alpha());
        host.screenshot = true;
        runtime.iterate();
        assertInstanceOf(PictureSelection.Latest.class, host.requests.getLast().selection());
        iterate(runtime, 6_000_000);
        assertInstanceOf(PictureSelection.Latest.class, host.requests.getLast().selection());
    }

    @Test void exactBarriersAndOverdueWorkNeverBlend() {
        var host = new InterpolatedHost(60, 144);
        var runtime = new LiveFrameRuntime(host, () -> now);
        runtime.iterate();
        host.capture = true;
        iterate(runtime, 16_666_667);
        assertInstanceOf(LivePictureRequest.CaptureLatest.class, host.requests.getLast());
        host.capture = false;
        host.forced = true;
        iterate(runtime, 16_666_667);
        assertInstanceOf(PictureSelection.Latest.class, host.requests.getLast().selection());
        host.forced = false;
        host.paused = true;
        iterate(runtime, 16_666_667);
        assertInstanceOf(PictureSelection.Latest.class, host.requests.getLast().selection());
        host.paused = false;
        host.boundary = "new";
        iterate(runtime, 16_666_667);
        assertInstanceOf(PictureSelection.Latest.class, host.requests.getLast().selection());
        host.work = 20_000_000;
        var result = iterate(runtime, 16_666_667);
        assertInstanceOf(PictureSelection.Latest.class, host.requests.getLast().selection());
        assertTrue(result.wakeAtNanos() <= now, "overdue service work wakes immediately");
    }

    @Test void interpolationAt120HzUsesExactIntervalOneAnd60HzStillLocksWithoutBlends() throws Exception {
        open(60, 144);
        config.setConfigValue(SonicConfiguration.DISPLAY_INTERPOLATION, true);
        var requests = new ArrayList<LivePictureRequest>();
        var runtime = paced(new FakeVsync(120), requests::add);
        runUntil(runtime, runtime.iterate(), 3 * SECOND);
        assertEquals("EXACT", runtime.statistics().cadence());
        assertEquals(List.of(1), intervals);
        assertTrue(requests.stream().anyMatch(p -> p.selection() instanceof PictureSelection.Between));
        requests.clear();
        runtime = paced(new FakeVsync(60), requests::add);
        runUntilLocked(runtime, runtime.iterate());
        assertTrue(requests.stream().allMatch(p -> p.selection() instanceof PictureSelection.Latest));
        assertEquals(1, runtime.statistics().swapInterval());
    }

    @Test void pictureSelectionRejectsInvalidFractions() {
        assertInstanceOf(PictureSelection.Latest.class, PictureSelection.between(1));
        assertThrows(IllegalArgumentException.class, () -> PictureSelection.between(-0.1));
        assertThrows(IllegalArgumentException.class, () -> PictureSelection.between(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new PictureSelection.Between(1));
        assertTrue(new LivePictureRequest.CaptureLatest().capture());
    }

    private static final class CollectingEncoder implements com.openggf.capture.CaptureEncoder {
        final java.util.List<com.openggf.capture.CapturedFrame> frames = new java.util.ArrayList<>();
        int fps;
        public void open(java.nio.file.Path output, int width, int height, int fps, int sampleRate) { this.fps = fps; }
        public void encode(com.openggf.capture.CapturedFrame frame) {
            frames.add(new com.openggf.capture.CapturedFrame(frame.rgba(), frame.width(), frame.height(),
                    java.util.Arrays.copyOf(frame.pcm(), 2 * frame.sampleCount()), frame.sampleCount(), frame.frameIndex()));
        }
        public java.nio.file.Path finish() { return java.nio.file.Path.of("target", "scheduled-test.mkv"); }
        public void abort() { }
    }

    private void field(String name, Object value) throws Exception {
        Field f = Engine.class.getDeclaredField(name); f.setAccessible(true); f.set(engine, value);
    }
    private static LogicalInputSnapshot sample(int action) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0, 0, action, action, false, false), PlayerInputState.neutral());
    }
    private static final class ObservedScreen extends LegalDisclaimerScreen {
        ObservedScreen() { super(new FadeManager()); }
        final List<LogicalInputSnapshot> samples = new ArrayList<>();
        final List<Long> states = new ArrayList<>();
        long state;
        Runnable after = () -> { };
        @Override public void update(InputHandler input) {
            var sample = input.logical(); samples.add(sample);
            state = state * 17 + sample.player1().actionHeldMask(); states.add(state);
            after.run();
        }
        @Override public boolean isDismissed() { return false; }
    }
    private final class MeasuredHost implements LiveFrameRuntime.Owner {
        final int hz, refresh;
        final boolean fixed;
        int ticks, deviceServices;
        boolean focused = true, interpolation, capture;
        long extraWork, swapWait;
        Object boundary = "start";
        final List<Long> returns = new ArrayList<>();
        final List<Long> ticksAt = new ArrayList<>();
        final List<LivePictureRequest> requests = new ArrayList<>();

        MeasuredHost(int hz, int refresh, boolean fixed) {
            this.hz = hz;
            this.refresh = refresh;
            this.fixed = fixed;
        }
        public LiveFrameRuntime.HostState hostState() {
            return new LiveFrameRuntime.HostState(hz, refresh, focused, false, boundary, capture, false,
                    com.openggf.configuration.DisplayPacing.EXACT, new DisplayOutput(1, refresh), true,
                    interpolation, false, false);
        }
        public boolean tick(boolean active) {
            ticksAt.add(now);
            ticks++;
            now += extraWork + (ticks % 2 == 0 ? 2_000_000 : 500_000);
            return true;
        }
        public boolean draw(LivePictureRequest picture) {
            now += 300_000;
            requests.add(picture);
            return true;
        }
        public void swap() {
            if (fixed) now = Math.round((Math.floor(now * (double) refresh / SECOND) + 1) * SECOND / refresh);
            else now += swapWait;
            returns.add(now);
        }
        public void swapInterval(int interval) { assertEquals(1, interval); }
        public void serviceDevice() { deviceServices++; }
    }

    @Test void nonBlockingServicePacedReturnsCannotEstablishVrrOn144Hz() {
        var host = new MeasuredHost(60, 144, false);
        var runtime = new LiveFrameRuntime(host, () -> now);
        runUntil(runtime, runtime.iterate(), 5 * SECOND);
        assertEquals(SwapPacingPolicy.VrrEngagement.UNKNOWN, runtime.statistics().swapPacing().vrr());
        assertEquals(SwapPacingPolicy.Reason.NO_SWAP_WAIT, runtime.statistics().swapPacing().reason());
        assertEquals("vrr UNKNOWN NOWAIT", runtime.statistics().vrrLine());
        assertEquals(host.ticks, host.deviceServices - 1, "non-blocking swaps never start an even-present hold");
    }

    @Test void waylandUnknownWindowOutputUsesAutoCeilingWithoutNominatingALock() throws Exception {
        var runtime = open(60, 60);
        output = new DisplayOutput(1, 0, 144);
        regulated = true;
        config.setConfigValue(SonicConfiguration.FPS, "AUTO");
        runUntil(runtime, runtime.iterate(), 3 * SECOND);
        assertEquals(144, runtime.statistics().pictureCap());
        assertEquals("EXACT", runtime.statistics().cadence());
        assertEquals("REFRESH_UNKNOWN", runtime.statistics().reason());
        assertEquals(SwapPacingPolicy.VrrEngagement.UNKNOWN, runtime.statistics().swapPacing().vrr());
        assertEquals(List.of(1), intervals);
    }

    @ParameterizedTest
    @CsvSource({"50,75", "50,120", "50,144", "50,165", "60,75", "60,120", "60,144", "60,165"})
    void fakeSwapModelsReportTheirEvidenceAndKeepExactServiceCounts(int hz, int refresh) {
        for (boolean fixed : new boolean[] {false, true}) {
            now = 0;
            var host = new MeasuredHost(hz, refresh, fixed);
            if (!fixed) host.swapWait = Math.round(SECOND / (2.0 * refresh));
            var runtime = new LiveFrameRuntime(host, () -> now);
            runUntil(runtime, runtime.iterate(), 5 * SECOND);
            var expected = refresh % hz == 0 ? SwapPacingPolicy.VrrEngagement.UNKNOWN
                    : fixed ? SwapPacingPolicy.VrrEngagement.NOT_DETECTED : SwapPacingPolicy.VrrEngagement.ACTIVE;
            assertEquals(expected, runtime.statistics().swapPacing().vrr(), () -> runtime.statistics().toString());
            assertEquals(5 * hz, host.ticks);
            assertEquals(hz, runtime.statistics().servicesPerSecond(), 1);
            assertEquals(hz, runtime.statistics().measuredPresentHz(), 1,
                    "held swaps also feed the cadence diagnostics");
            assertTrue(host.requests.stream().allMatch(r -> r.selection() instanceof PictureSelection.Latest));
            if (!fixed && refresh % hz != 0) {
                double immediate = intervalSpread(host.returns.subList(5, hz));
                double held = intervalSpread(host.returns.subList(host.returns.size() - hz, host.returns.size()));
                assertTrue(held < immediate / 10, "held " + held + ", immediate " + immediate);
                // Work and presentation holds never move the scheduler's rational service deadlines.
                for (int i = 0; i < host.ticksAt.size(); i++) {
                    assertEquals((long) Math.ceil((i + 1) * (double) SECOND / hz), host.ticksAt.get(i));
                }
                assertTrue(host.deviceServices > host.ticks, "holds return to the input/audio polling loop");
            }
        }
    }

    private static double intervalSpread(List<Long> timestamps) {
        double mean = (timestamps.getLast() - timestamps.getFirst()) / (double) (timestamps.size() - 1);
        double sum = 0;
        for (int i = 1; i < timestamps.size(); i++) {
            double residual = timestamps.get(i) - timestamps.get(i - 1) - mean;
            sum += residual * residual;
        }
        return Math.sqrt(sum / (timestamps.size() - 1));
    }

    @Test void vrrHoldsAreCancelledForInterpolationCaptureFocusAndBoundaries() {
        for (String change : List.of("blend", "capture", "focus", "boundary", "late", "slow")) {
            now = 0;
            var host = new MeasuredHost(50, 120, false);
            host.swapWait = Math.round(SECOND / 240.0);
            var runtime = new LiveFrameRuntime(host, () -> now);
            var last = runtime.iterate();
            // A held picture has been drawn, but has no swap yet.
            for (int i = 0; i < 500; i++) {
                now = Math.max(now, last.wakeAtNanos());
                last = runtime.iterate();
                if (runtime.statistics().swapPacing().vrr() == SwapPacingPolicy.VrrEngagement.ACTIVE
                        && last.services() == 1 && !last.presented()) break;
                assertTrue(i < 499, "no hold");
            }
            int before = host.returns.size(), ticks = host.ticks;
            switch (change) {
                case "blend" -> host.interpolation = true;
                case "capture" -> host.capture = true;
                case "focus" -> host.focused = false;
                case "boundary" -> host.boundary = "changed";
                case "late" -> now += 40_000_000;
                case "slow" -> {
                    now += 20_000_000;
                    host.extraWork = 30_000_000;
                }
            }
            last = runtime.iterate();
            if (change.equals("focus")) assertEquals(before, host.returns.size());
            else if (change.equals("slow")) {
                assertTrue(last.presented());
                assertTrue(last.wakeAtNanos() <= now, "slow tick/draw work cannot hold a swap");
            } else if (change.equals("late")) {
                assertTrue(last.services() >= 2, "late wake services debt before any swap");
                assertTrue(host.ticks > ticks);
                assertTrue(last.presented());
            }
            if (!change.equals("slow")) {
                assertEquals(SwapPacingPolicy.VrrEngagement.UNKNOWN, runtime.statistics().swapPacing().vrr());
            }
            if (change.equals("blend") || change.equals("capture")) {
                runUntil(runtime, last, now + 3 * SECOND);
                assertEquals(SwapPacingPolicy.VrrEngagement.UNKNOWN, runtime.statistics().swapPacing().vrr());
                assertTrue(host.requests.stream().anyMatch(r -> change.equals("capture")
                        ? r.capture() : r.selection() instanceof PictureSelection.Between));
            }
        }
    }

    @Test void rateWindowsExcludeFocusLossRegainAndBoundaryChanges() {
        var host = new MeasuredHost(50, 120, false);
        var runtime = new LiveFrameRuntime(host, () -> now);
        var last = runtime.iterate();
        runUntil(runtime, last, 3 * SECOND);
        assertEquals(50, runtime.statistics().servicesPerSecond(), 1);
        host.focused = false;
        runtime.iterate();
        now += 20 * SECOND;
        runtime.iterate();
        assertEquals(0, runtime.statistics().servicesPerSecond());
        host.focused = true;
        last = runtime.iterate();
        assertEquals(0, runtime.statistics().servicesPerSecond());
        last = runUntil(runtime, last, now + 3 * SECOND).getLast();
        assertEquals(50, runtime.statistics().servicesPerSecond(), 1);
        host.boundary = "resumed";
        runtime.iterate();
        assertEquals(0, runtime.statistics().servicesPerSecond());
        assertEquals(0, runtime.statistics().picturesPerSecond());
        assertEquals(0, runtime.statistics().blendedPerSecond());
    }

    @Test void measuredSwapLinesReachThePanelAndClipboardWithBoundedWidth() throws Exception {
        var runtime = open(50, 120);
        output = new DisplayOutput(1, 120);
        config.setConfigValue(SonicConfiguration.DISPLAY_PACING, "EXACT");
        field("liveFrames", runtime);
        runUntil(runtime, runtime.iterate(), 3 * SECOND);
        var lines = Engine.livePresentationPanelLines();
        assertEquals("swap 20.0+/-0.0ms", lines.get(lines.size() - 2));
        assertEquals("vrr UNKNOWN NOWAIT", lines.getLast());
        var font = new com.openggf.graphics.PixelFont();
        assertTrue(font.measureWidth(lines.get(lines.size() - 2),
                0.5f) <= 85);
        String clipboard = Engine.liveSchedulingDiagnostics();
        assertTrue(clipboard.contains("swap 20.0+/-0.0ms"));
        assertTrue(font.measureWidth(lines.getLast(), 0.5f) <= 85);
        assertTrue(clipboard.contains("vrr UNKNOWN NOWAIT (swap-return inference)"));
    }

}
