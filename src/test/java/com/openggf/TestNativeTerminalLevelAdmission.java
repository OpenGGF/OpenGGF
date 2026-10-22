package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.replay.runs.TraceRunFrameDriver;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Actual native dispatch after a comparison lease closes.
 * No trace value bootstraps gameplay; the three BK2 rows are synthetic input.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_1)
class TestNativeTerminalLevelAdmission {
    private static final int HELD = AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP;
    private static final int ACTION_C = 0x04;
    private Harness active;
    private GameLoop activeLoop;

    @AfterEach
    void tearDown() {
        setStaticField(TraceSessionLauncher.class, "activeSession", null);
        try {
            if (active != null) {
                active.mode.clearTraceRunFrameDriver(active.driver);
                invoke(active.session, "detachAndCloseRunPayload", new Class<?>[]{Throwable.class}, (Object) null);
                active.playback.endSession();
            }
        } finally {
            if (activeLoop != null) activeLoop.closePresence();
            activeLoop = null;
            active = null;
            Engine.clearGlobalInstance();
            SessionManager.clear();
        }
    }

    @ParameterizedTest(name = "launcher={0}")
    @ValueSource(booleans = {false, true})
    void terminalLevelRowsExecuteExactlyOneNativePassAndPublishEachPhysicalRow(boolean launcher) {
        Harness h = open(launcher);
        h.closeForTail();
        int body = h.objects.getFrameCounter();
        int vInt = h.objects.getVblaCounter();
        int observations = h.probe.samples.size();

        for (int row = 1; row <= 2; row++) {
            h.step();
            assertEquals(body + row, h.objects.getFrameCounter(), "one ExecuteObjects pass per ordinary tail row");
            assertEquals(vInt + row, h.objects.getVblaCounter(), "the ordinary production pass owns its VInt");
            assertEquals(observations + row, h.probe.samples.size(), "observer object executes once, without an extra tick");
            Sample observed = h.probe.samples.get(observations + row - 1);
            assertEquals(row, observed.movieRow());
            assertEquals(HELD, observed.held(), "driver input reaches the actual object pass");
            assertEquals(row == 1 ? ACTION_C : 0, observed.pressed(), "the held second row cannot repeat the edge");
            assertFalse(observed.ordinaryPlaybackOwner(), "tail input has no second playback owner");
            h.assertPublishedThrough(row);
        }
        assertEquals(0, h.player.getForcedInputMask(), "the synthetic movie is not a scripted control owner");
    }

    @ParameterizedTest(name = "launcher={0}")
    @ValueSource(booleans = {false, true})
    void firstTailInputSurvivesActualBridgeSyncAfterTheNativeClose(boolean launcher) {
        Harness h = open(launcher);
        h.closeForTail();
        int body = h.objects.getFrameCounter();
        int vInt = h.objects.getVblaCounter();
        // Deliberately isolate the real input seam from native body eligibility:
        // the old admission gate must not mask a separate override handoff bug.
        h.drive(TraceRunFrameDriver.Disposition.TERMINAL_TAIL, () -> {
            assertFalse(TraceSessionLauncher.allowsRunLogicalGameplayInput());
            assertFalse(h.playback.isDriving(GameMode.LEVEL));
            assertTrue(h.input.hasLogicalOverride(), "driver installed the absolute-row input first");
            assertEquals(HELD, h.input.logical().player1().heldMask());
            assertEquals(ACTION_C, h.input.logical().player1().actionPressedMask());
            invoke(h.loop, "syncPlaybackInputBridge", new Class<?>[0]);
            assertTrue(h.input.hasLogicalOverride(), "old ordinary owner must not clear its replacement");
            assertEquals(HELD, h.input.logical().player1().heldMask());
            assertEquals(ACTION_C, h.input.logical().player1().actionPressedMask());
        });
        h.assertPublishedThrough(1);
        assertEquals(body, h.objects.getFrameCounter(), "an input-only discriminator never runs gameplay");
        assertEquals(vInt, h.objects.getVblaCounter(), "an input-only discriminator never manufactures VInt");
    }

    @ParameterizedTest(name = "launcher={0}")
    @ValueSource(booleans = {false, true})
    void terminalRowsRetainTheRealNativePauseAdmission(boolean launcher) {
        Harness h = open(launcher);
        h.closeForTail();
        assertTrue(h.mode.getGameStateManager().getLives() > 0, "native PauseGame requires a living campaign");
        h.mode.getGameStateManager().setGamePaused(true);
        int body = h.objects.getFrameCounter();
        int observations = h.probe.samples.size();
        for (int row = 1; row <= 2; row++) {
            h.step();
            assertTrue(h.mode.getGameStateManager().isGamePaused());
            assertEquals(body, h.objects.getFrameCounter(), "PauseGame holds ExecuteObjects");
            assertEquals(observations, h.probe.samples.size());
            h.assertPublishedThrough(row);
        }
    }

    @ParameterizedTest(name = "launcher={0}")
    @ValueSource(booleans = {false, true})
    void terminalRowsConsumeARealEntryRequestAndRetainItsBlockingFade(boolean launcher) {
        Harness h = open(launcher);
        h.closeForTail();
        h.level.requestZoneAndAct(0, 1, false);
        assertFalse(h.level.isLevelInactiveForTransition(), "publication alone has not consumed the request");
        int body = h.objects.getFrameCounter();
        int observations = h.probe.samples.size();
        h.step();
        assertTrue(h.level.isLevelInactiveForTransition(), "ordinary dispatcher consumes the production request");
        assertTrue(h.mode.getFadeManager().hasPendingCompletion());
        assertEquals(body, h.objects.getFrameCounter(), "request consumption precedes native gameplay");
        h.assertPublishedThrough(1);
        int fade = h.mode.getFadeManager().getFrameCount();
        h.step();
        assertTrue(h.mode.getFadeManager().getFrameCount() > fade, "outer fade lifecycle remains live");
        assertTrue(h.level.isLevelInactiveForTransition());
        assertEquals(body, h.objects.getFrameCounter(), "inactive entry still freezes native gameplay");
        assertEquals(observations, h.probe.samples.size());
        h.assertPublishedThrough(2);
    }

    @ParameterizedTest(name = "launcher={0}")
    @ValueSource(booleans = {false, true})
    void sharedGapStillOwnsOnlyItsFirstUnwrittenSourcePass(boolean launcher) {
        Harness h = open(launcher);
        h.closeForGap();
        int body = h.objects.getFrameCounter();
        int observations = h.probe.samples.size();
        h.step();
        assertEquals(body + 1, h.objects.getFrameCounter(), "first gap row still belongs to source main loop");
        assertEquals(observations + 1, h.probe.samples.size());
        h.assertPublishedThrough(1);
        h.step();
        assertEquals(body + 1, h.objects.getFrameCounter(), "later gap row remains a blocking-routine row");
        assertEquals(observations + 1, h.probe.samples.size());
        h.assertPublishedThrough(2);
    }

    private Harness open(boolean launcher) {
        assertNull(TraceSessionLauncher.active());
        Engine.clearGlobalInstance();
        GameServices.playbackDebug().endSession();
        TestEnvironment.configureGameModuleFixture(new Sonic1GameModule());
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        GameplayModeContext mode = fixture.gameplayMode();
        LevelManager level = mode.getLevelManager();
        // A normal positioned headless fixture begins after its initial title.
        // Finish only an actually pending setup authority, before movie row 0;
        // no tail row is retried or followed by a compensating production tick.
        level.consumeTitleCardRequest();
        InputHandler input = new InputHandler();
        GameLoop loop = new GameLoop(input);
        activeLoop = loop;
        loop.setGameplayMode(mode);
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        int setup = 0;
        while (level.hasPendingInitialProcessSpritesPass()) {
            assertTrue(++setup <= 8, "bounded initial setup before the movie");
            loop.step();
        }
        Harness h = new Harness(launcher, mode, level, fixture.sprite(), input, loop);
        active = h;
        int before = h.objects.getFrameCounter();
        h.drive(TraceRunFrameDriver.Disposition.GAMEPLAY_SHARED, loop::step);
        assertEquals(before + 1, h.objects.getFrameCounter(), "prior represented row is a real ordinary pass");
        assertEquals(1, h.playback.getCursorFrame());
        assertTrue(input.hasLogicalOverride(), "ordinary bridge still owns its final source snapshot");
        assertTrue(h.probe.samples.getLast().ordinaryPlaybackOwner(), "the source really used ordinary playback ownership");
        return h;
    }

    private static final class Harness {
        final boolean launcher;
        final GameplayModeContext mode;
        final LevelManager level;
        final AbstractPlayableSprite player;
        final InputHandler input;
        final GameLoop loop;
        final ObjectManager objects;
        final PlaybackDebugManager playback = GameServices.playbackDebug();
        final TraceRunFrameDriver driver = new TraceRunFrameDriver();
        final AtomicReference<TraceRunPlaybackCoordinator.Phase> phase = new AtomicReference<>(
                TraceRunPlaybackCoordinator.Phase.CURRENT_SEGMENT);
        final AtomicInteger payloadCloses = new AtomicInteger();
        final List<Integer> published = new ArrayList<>();
        final DispatchObserver probe;
        final Bk2Movie movie;
        final TraceSessionLauncher session;

        Harness(boolean launcher, GameplayModeContext mode, LevelManager level,
                AbstractPlayableSprite player, InputHandler input, GameLoop loop) {
            this.launcher = launcher;
            this.mode = mode;
            this.level = level;
            this.player = player;
            this.input = input;
            this.loop = loop;
            objects = level.getObjectManager();
            probe = new DispatchObserver(input, playback);
            objects.addDynamicObject(probe);
            movie = new Bk2Movie(Path.of("synthetic-native-terminal-input.bk2"), "", Map.of(), List.of(
                    new Bk2FrameInput(0, 0, 0, false, "neutral source row"),
                    new Bk2FrameInput(1, HELD, ACTION_C, false, "tail press"),
                    new Bk2FrameInput(2, HELD, ACTION_C, false, "tail hold")), 1);
            // This tiny comparison-only lease exists solely to execute the real
            // source-close path. Its dummy row is never compared, bootstrapped,
            // used for scheduling, or supplied to a gameplay owner.
            var trace = TraceFixtures.trace(TraceFixtures.metadata("s1", 0, 0),
                    List.of(TraceFrame.executionTestFrame(0, 0, 0, 0)));
            var segment = new TraceRunManifest.Segment(
                    "synthetic-source", "level", null, 0, trace.frameCount(), 0, 0, null, null);
            session = TestRunPayloads.session(null, movie,
                    List.of(new TraceRunReplayWalker.SegmentPlan(segment, trace, null, null)),
                    null, payloadCloses);
            TraceRunPlaybackCoordinator coordinator = mock(TraceRunPlaybackCoordinator.class);
            when(coordinator.phase()).thenAnswer(ignored -> phase.get());
            when(coordinator.currentSegmentIndex()).thenReturn(0);
            when(coordinator.beforeAdmission(any())).thenReturn(List.of());
            when(coordinator.afterProduction(any())).thenReturn(List.of());
            when(coordinator.afterStep(any())).thenReturn(List.of());
            when(coordinator.finishTerminalTail(any())).thenReturn(List.of());
            setField(session, "runCoordinator", coordinator);
            setField(session, "runFrameDriver", driver);
            mode.installTraceRunFrameDriver(driver);
            playback.startSession(movie, 0);
            if (launcher) {
                Engine engine = mock(Engine.class);
                setField(engine, "gameLoop", loop);
                setStaticField(Engine.class, "instance", engine);
                assertSame(loop, Engine.currentGameLoop(), "launcher reads this owned actual loop");
                setStaticField(TraceSessionLauncher.class, "activeSession", session);
            }
        }

        void closeForTail() {
            phase.set(TraceRunPlaybackCoordinator.Phase.TERMINAL_TAIL);
            invoke(session, "applyRunCoordinatorActions", new Class<?>[]{List.class}, List.of(
                    new TraceRunPlaybackCoordinator.CloseSegment(0),
                    new TraceRunPlaybackCoordinator.BeginTerminalTail(
                            new TraceRunReplayWalker.TerminalMovieTailPlan(1, 2, GameMode.LEVEL))));
            afterClose();
        }

        void closeForGap() {
            phase.set(TraceRunPlaybackCoordinator.Phase.TRANSITION_GAP);
            invoke(session, "applyRunCoordinatorActions", new Class<?>[]{List.class}, List.of(
                    new TraceRunPlaybackCoordinator.CloseSegment(0),
                    new TraceRunPlaybackCoordinator.EnterTransitionGap(0, 1)));
            afterClose();
        }

        private void afterClose() {
            assertEquals(1, payloadCloses.get(), "actual close releases the source lease exactly once");
            assertEquals(1, playback.getCursorFrame(), "closing a comparator does not consume movie input");
            if (!launcher) TraceSessionLauncher.beginDriverOnlyRunTransitionGap();
            // A non-gating observer counts physical publications only. It has no
            // comparison rows and never grants frozen-row/native scheduling.
            playback.setFrameObserver(new PlaybackDebugManager.PlaybackFrameObserver() {
                @Override public boolean shouldSkipGameplayTick(Bk2FrameInput frame) { return false; }
                @Override public void afterFrameAdvanced(Bk2FrameInput frame, boolean skipped) {
                    published.add(frame.frameIndex());
                }
            });
        }

        void step() {
            if (launcher) loop.step();
            else drive(phase.get() == TraceRunPlaybackCoordinator.Phase.TERMINAL_TAIL
                    ? TraceRunFrameDriver.Disposition.TERMINAL_TAIL
                    : TraceRunFrameDriver.Disposition.SHARED_GAP, loop::step);
        }

        void drive(TraceRunFrameDriver.Disposition disposition, Runnable production) {
            if (launcher) {
                // loop.step already enters the production launcher internally;
                // the isolated bridge test deliberately calls this wrapper once.
                if (disposition == TraceRunFrameDriver.Disposition.GAMEPLAY_SHARED) loop.step();
                else TraceSessionLauncher.runProductionIterationIfActive(production, playback::onLevelFrameAdvanced);
                return;
            }
            int row = playback.getCursorFrame();
            driver.execute(new TraceRunFrameDriver.Step(disposition, row, row == 2),
                    new TraceRunFrameDriver.Hooks<Void>() {
                        @Override public void preparePhysicalRow(TraceRunFrameDriver.Step step) {
                            if (disposition != TraceRunFrameDriver.Disposition.GAMEPLAY_SHARED) {
                                input.setLogicalOverride(RecordedInputSnapshots.fromBk2(movie.getFrame(row),
                                        row == 0 ? null : movie.getFrame(row - 1)));
                            }
                        }
                        @Override public void prepareHardwareTiming(TraceRunFrameDriver.Step step) { }
                        @Override public Void captureBefore(TraceRunFrameDriver.Step step) { return null; }
                        @Override public void runProductionLifecycle(TraceRunFrameDriver.Step step) { production.run(); }
                        @Override public void advancePhysicalRow(TraceRunFrameDriver.Step step) { playback.onLevelFrameAdvanced(); }
                        @Override public Void captureAfter(TraceRunFrameDriver.Step step) { return null; }
                        @Override public void compare(TraceRunFrameDriver.Step step, Void before, Void after) { }
                        @Override public void afterStep(TraceRunFrameDriver.Step step) {
                            if (disposition != TraceRunFrameDriver.Disposition.GAMEPLAY_SHARED) input.clearLogicalOverride();
                        }
                    });
        }

        void assertPublishedThrough(int row) {
            assertEquals(row == 1 ? List.of(1) : List.of(1, 2), published, "each tail/gap row has one physical publisher");
            assertEquals(2, playback.getCursorFrame(), "movie cursor is clamped to its last valid row");
            assertEquals(row == 1, playback.isSessionPlaying(), "the final physical publication stops the movie");
            assertEquals(1, payloadCloses.get());
        }
    }

    private record Sample(int movieRow, int held, int pressed, boolean ordinaryPlaybackOwner) { }

    private static final class DispatchObserver extends AbstractObjectInstance {
        final InputHandler input;
        final PlaybackDebugManager playback;
        final List<Sample> samples = new ArrayList<>();
        DispatchObserver(InputHandler input, PlaybackDebugManager playback) {
            super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "NativeTerminalDispatchObserver");
            this.input = input;
            this.playback = playback;
            setRomWorldPositioned(false);
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            samples.add(new Sample(playback.getCursorFrame(), input.logical().player1().heldMask(),
                    input.logical().player1().actionPressedMask(), TraceSessionLauncher.allowsRunLogicalGameplayInput()));
        }
        @Override public boolean isPersistent() { return true; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... arguments) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void setStaticField(Class<?> owner, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
