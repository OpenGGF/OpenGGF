package com.openggf;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.ResultsScreen;
import com.openggf.game.SpecialStageEntryPresentationController;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.timeattack.GhostStore;
import com.openggf.game.timeattack.TimeAttackLaunchRequest;
import com.openggf.game.timeattack.TimeAttackRuntime;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.replay.runs.TraceRunFrameDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Caller-level coverage for lifecycle seams retained across the develop/next merge. */
@Isolated
class TestGameLoopFreezeContractWiring {

    @AfterEach
    void tearDown() {
        PlaybackDebugManager playback = GameServices.playbackDebug();
        playback.setFrameObserver(null);
        playback.endSession();
        SessionManager.clear();
    }

    @Test
    void resultsExitFadeRetainsTheFinalWhiteoutAndPreLevelDelay() throws Exception {
        GameplayModeContext gameplay = install(new Sonic1GameModule());
        GameLoop loop = new GameLoop(new InputHandler());
        LevelManager level = mock(LevelManager.class);
        setField(loop, "levelManager", level);
        setField(loop, "activeSpecialStageProvider", mock(SpecialStageProvider.class));
        setField(loop, "resultsScreen", mock(ResultsScreen.class));
        loop.setGameMode(GameMode.SPECIAL_STAGE_RESULTS);

        invoke(loop, "exitResultsScreen");
        for (int frame = 0; frame < 64; frame++) {
            gameplay.getFadeManager().update();
        }

        assertEquals(GameMode.SPECIAL_STAGE_RESULTS, loop.getCurrentGameMode(),
                "the fade callback only latches the exit; it must not load LEVEL inline");
        verify(level, never()).loadZoneAndAct(anyInt(), anyInt());

        invoke(loop, "runSpecialStageResultsIteration");
        int preLevelFadeFrames = gameplay.getWorldSession().getGameModule()
                .getLevelInitProfile().preLevelFadeOutFrames();
        for (int frame = 0; frame < preLevelFadeFrames; frame++) {
            invoke(loop, "runSpecialStageResultsIteration");
        }
        verify(level, never()).loadZoneAndAct(anyInt(), anyInt());

        invoke(loop, "runSpecialStageResultsIteration");

        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        verify(level, times(1)).loadZoneAndAct(0, 0);
    }

    @Test
    void specialStageTerminalBoundaryWaitsForTheClosureBridge() {
        GameplayModeContext gameplay = install(new Sonic2GameModule());
        TraceRunFrameDriver driver = new TraceRunFrameDriver();
        gameplay.installTraceRunFrameDriver(driver);
        SpecialStageProvider provider = mock(SpecialStageProvider.class);
        when(provider.isFinished()).thenReturn(true);
        when(provider.isEmeraldCollected()).thenReturn(true);
        AtomicInteger resultsEntries = new AtomicInteger();

        gameplay.plcFrameLifecycle().runLogicalIteration(() -> { }, frame -> {
            executeDriverRow(driver,
                    new TraceRunFrameDriver.Step(
                            TraceRunFrameDriver.Disposition.SPECIAL_LOCAL,
                            0, true, true),
                    () -> GameLoopSpecialStageLifecycle.update(
                            provider,
                            mock(SonicConfigurationService.class),
                            key -> false,
                            () -> { },
                            () -> { },
                            new InputHandler(),
                            gameplay,
                            frame,
                            null,
                            () -> { },
                            new SpecialStageEntryPresentationController(),
                            gameplay.getFadeManager(),
                            () -> { },
                            () -> false,
                            mock(LiveRewindManager.class),
                            GameMode.SPECIAL_STAGE,
                            ignored -> resultsEntries.incrementAndGet()));
            return null;
        });

        assertEquals(0, resultsEntries.get(),
                "the terminal owner must remain in SPECIAL_STAGE across the deferred row");
        assertTrue(TraceSessionLauncher.commitDeferredRunModeBoundary(
                GameMode.SPECIAL_STAGE, provider,
                ignored -> resultsEntries.incrementAndGet()));
        assertEquals(1, resultsEntries.get(),
                "the closure bridge is the single owner that commits the boundary");
    }

    @Test
    void sharedGapRunsTheFirstSourceRowThenServicesOnlyGapTransitions() throws Exception {
        GameplayModeContext gameplay = install(new Sonic2GameModule());
        LevelManager level = mock(LevelManager.class);
        when(level.consumeTitleCardRequest()).thenReturn(true);
        setField(gameplay, "levelManager", level);
        GameLoop loop = spy(new GameLoop(new InputHandler()));
        doNothing().when(loop).enterTitleCard(anyInt(), anyInt());
        doReturn(false).when(loop).consumeSpecialStageRequestDuringSuppressedRunRow();
        doReturn(false).when(loop).presentPendingTitleCardDuringSuppressedRunRow();
        doReturn(false).when(loop).startPendingRespawnDuringSuppressedRunRow();
        TraceRunFrameDriver driver = new TraceRunFrameDriver();
        gameplay.installTraceRunFrameDriver(driver);
        TraceSessionLauncher.beginDriverOnlyRunTransitionGap();

        executeDriverRow(driver, sharedGapRow(0), loop::step);

        verify(loop, never()).consumeSpecialStageRequestDuringSuppressedRunRow();
        verify(loop, never()).presentPendingTitleCardDuringSuppressedRunRow();
        verify(loop, never()).startPendingRespawnDuringSuppressedRunRow();

        executeDriverRow(driver, sharedGapRow(1), loop::step);

        verify(loop, times(1)).consumeSpecialStageRequestDuringSuppressedRunRow();
        verify(loop, times(1)).presentPendingTitleCardDuringSuppressedRunRow();
        verify(loop, times(1)).startPendingRespawnDuringSuppressedRunRow();
    }

    @Test
    void suppressedLevelRowDoesNotAdvanceOrStartAnInLevelTitleOverlay() throws Exception {
        GameplayModeContext gameplay = install(new Sonic2GameModule());
        LevelManager level = mock(LevelManager.class);
        when(level.consumeTitleCardRequest()).thenReturn(true);
        setField(gameplay, "levelManager", level);
        GameLoop loop = spy(new GameLoop(new InputHandler()));
        doNothing().when(loop).enterTitleCard(anyInt(), anyInt());
        TitleCardProvider titleCard = mock(TitleCardProvider.class);
        when(titleCard.isOverlayActive()).thenReturn(true);
        setField(loop, "titleCardProvider", titleCard);
        PlaybackDebugManager playback = GameServices.playbackDebug();
        playback.startSession(oneFrameMovie(), 0);
        playback.setFrameObserver(new PlaybackDebugManager.PlaybackFrameObserver() {
            @Override
            public boolean shouldSkipGameplayTick(Bk2FrameInput frame) {
                return true;
            }

            @Override
            public void afterFrameAdvanced(Bk2FrameInput frame, boolean wasSkipped) {
            }
        });
        assertTrue(playback.shouldSkipCurrentGameplayTick());

        GameLoopTestStep.invoke(loop, "updateLevelMode",
                new Class<?>[] {boolean.class}, false);

        verify(titleCard, never()).update();
        verify(level, never()).consumeInLevelTitleCardRequest();
    }

    @Test
    void seamlessRequestRaisedAfterAdmissionWaitsForTheNextAdmission() throws Exception {
        GameplayModeContext gameplay = install(new Sonic3kGameModule());
        LevelManager level = mock(LevelManager.class);
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.MUTATE_ONLY).build();
        when(level.consumeSeamlessTransitionRequest()).thenReturn(null, request);
        when(level.consumeTitleCardRequest()).thenReturn(true);
        setField(gameplay, "levelManager", level);
        GameLoop loop = spy(new GameLoop(new InputHandler()));
        doNothing().when(loop).enterTitleCard(anyInt(), anyInt());

        loop.step();

        verify(level, times(1)).consumeSeamlessTransitionRequest();
        verify(level, never()).applySeamlessTransition(request);

        loop.step();

        verify(level, times(2)).consumeSeamlessTransitionRequest();
        verify(level, times(1)).applySeamlessTransition(request);
    }

    @Test
    void timeAttackCrossActSeamlessRouteReturnsToMenuBeforeDestinationApply(
            @TempDir Path tempDir) throws Exception {
        GameplayModeContext gameplay = install(new Sonic3kGameModule());
        LevelManager level = mock(LevelManager.class);
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(0, 1)
                .build();
        when(level.consumeSeamlessTransitionRequest()).thenReturn(request);
        setField(gameplay, "levelManager", level);
        GameLoop loop = new GameLoop(new InputHandler());
        TimeAttackRuntime timeAttack = new TimeAttackRuntime(
                new GhostStore(tempDir.resolve("ghosts")),
                tempDir.resolve("identity"), () -> false);
        timeAttack.armForLaunch(new TimeAttackLaunchRequest(
                "s3k", 0, 0, "sonic", List.of()));
        setField(loop, "timeAttackRuntime", timeAttack);

        loop.step();

        assertFalse(timeAttack.isActive(),
                "cross-act routing must end the attempt before transition application");
        verify(level, never()).applySeamlessTransition(request);
        assertTrue(gameplay.getFadeManager().isActive(),
                "the suppressed destination must route back through the menu fade");
    }

    private static GameplayModeContext install(com.openggf.game.GameModule module) {
        TestEnvironment.configureGameModuleFixture(module);
        return SessionManager.getCurrentGameplayMode();
    }

    private static TraceRunFrameDriver.Step sharedGapRow(int movieRow) {
        return new TraceRunFrameDriver.Step(
                TraceRunFrameDriver.Disposition.SHARED_GAP,
                movieRow, false);
    }

    private static void executeDriverRow(
            TraceRunFrameDriver driver,
            TraceRunFrameDriver.Step step,
            Runnable production) {
        driver.execute(step, new TraceRunFrameDriver.Hooks<Void>() {
            @Override
            public void preparePhysicalRow(TraceRunFrameDriver.Step row) {
            }

            @Override
            public void prepareHardwareTiming(TraceRunFrameDriver.Step row) {
            }

            @Override
            public Void captureBefore(TraceRunFrameDriver.Step row) {
                return null;
            }

            @Override
            public void runProductionLifecycle(TraceRunFrameDriver.Step row) {
                production.run();
            }

            @Override
            public void advancePhysicalRow(TraceRunFrameDriver.Step row) {
            }

            @Override
            public Void captureAfter(TraceRunFrameDriver.Step row) {
                return null;
            }

            @Override
            public void compare(TraceRunFrameDriver.Step row, Void before, Void after) {
            }

            @Override
            public void afterStep(TraceRunFrameDriver.Step row) {
            }
        });
    }

    private static Bk2Movie oneFrameMovie() {
        return new Bk2Movie(
                Path.of("suppressed-title-overlay.bk2"), "", Map.of(),
                List.of(new Bk2FrameInput(0, 0, 0, false, "")), 1);
    }

    private static void invoke(GameLoop loop, String name) throws Exception {
        Method method = GameLoop.class.getDeclaredMethod(name);
        method.setAccessible(true);
        try {
            method.invoke(loop);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) {
                throw exception;
            }
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            throw failure;
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass() == GameLoop.class
                ? GameLoop.class.getDeclaredField(name)
                : findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                return cursor.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through Mockito subclasses and ordinary inheritance.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
