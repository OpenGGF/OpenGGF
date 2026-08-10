package com.openggf;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.replay.TraceReplayFixture;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestTraceSessionLauncherProductionFailureCleanup {

    private GameplayModeContext gameplayMode;
    private GameLoop gameLoop;

    @BeforeEach
    void setUp() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        gameplayMode = SessionManager.getCurrentGameplayMode();
        gameLoop = mock(GameLoop.class);
        installCurrentLoop(gameLoop);
    }

    @AfterEach
    void tearDown() {
        Engine.clearGlobalInstance();
        GameServices.playbackDebug().endSession();
        SessionManager.clear();
    }

    @Test
    void runtimeFailureAbortsAuthorityAndReturnsToTitleWithoutStrictClose() {
        TraceReplayFixture fixture = mock(TraceReplayFixture.class);
        TraceSessionLauncher session = activeSession(fixture);
        RuntimeException primary = new RuntimeException("production body failed");

        TraceSessionLauncher.runProductionIterationIfActive(() -> {
            throw primary;
        });

        assertNull(TraceSessionLauncher.active());
        assertFalse(gameplayMode.isGameplayRuntimeReady());
        verify(fixture).abortHardwareTimingReplayRun();
        verify(gameLoop).setTraceCameraFocusController(null);
        verify(gameLoop).returnToMasterTitle();
        verify(fixture, org.mockito.Mockito.never())
                .closeHardwareTimingReplayRun();
    }

    @Test
    void fatalErrorKeepsCleanupFailureSuppressedAndRethrowsOriginal() {
        TraceReplayFixture fixture = mock(TraceReplayFixture.class);
        IllegalStateException cleanupFailure =
                new IllegalStateException("abort detach failed");
        doThrow(cleanupFailure).when(fixture).abortHardwareTimingReplayRun();
        activeSession(fixture);
        AssertionError primary = new AssertionError("fatal production failure");

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> TraceSessionLauncher.runProductionIterationIfActive(() -> {
                    throw primary;
                }));

        assertSame(primary, thrown);
        assertSame(cleanupFailure, thrown.getSuppressed()[0]);
        assertNull(TraceSessionLauncher.active());
        assertFalse(gameplayMode.isGameplayRuntimeReady());
        verify(gameLoop).returnToMasterTitle();
    }

    @Test
    void postFinishComparisonFailureIsContainedAndAbortsSession() {
        TraceReplayFixture fixture = mock(TraceReplayFixture.class);
        TraceSessionLauncher session = activeSession(fixture);
        armPendingDynamicPublication(session);

        TraceSessionLauncher.runProductionIterationIfActive(() -> { });

        assertNull(TraceSessionLauncher.active());
        assertFalse(gameplayMode.isGameplayRuntimeReady());
        verify(fixture).abortHardwareTimingReplayRun();
        verify(gameLoop).returnToMasterTitle();
    }

    @Test
    void bodyAndPostFinishFailuresKeepHookFailureOnThePrimary() {
        TraceReplayFixture fixture = mock(TraceReplayFixture.class);
        TraceSessionLauncher session = activeSession(fixture);
        armPendingDynamicPublication(session);
        RuntimeException primary = new RuntimeException("production body failed");

        TraceSessionLauncher.runProductionIterationIfActive(() -> {
            throw primary;
        });

        assertSame(IllegalStateException.class,
                primary.getSuppressed()[0].getClass());
        assertNull(TraceSessionLauncher.active());
        verify(fixture).abortHardwareTimingReplayRun();
    }

    private static TraceSessionLauncher activeSession(
            TraceReplayFixture fixture) {
        TraceSessionLauncher session = new TraceSessionLauncher(
                null, null, List.of(),
                TraceReplaySessionBootstrap.snapshotGameplayConfig());
        setField(session, "fixture", fixture);
        setStaticField(TraceSessionLauncher.class, "activeSession", session);
        return session;
    }

    private static void armPendingDynamicPublication(
            TraceSessionLauncher session) {
        TraceData trace = TraceFixtures.trace(
                TraceFixtures.metadataWithDynamicArt("s2", 0, 0, 2),
                List.of(
                        TraceFrame.executionTestFrame(0, 0, 0, 0),
                        TraceFrame.executionTestFrame(1, 1, 1, 1)),
                Map.of(
                        0, List.of(new TraceEvent.DynamicArtTransferState(
                                0, List.of(), List.of())),
                        1, List.of(new TraceEvent.DynamicArtTransferState(
                                1, List.of(), List.of()))));
        LiveTraceComparator comparator = new LiveTraceComparator(
                trace, ToleranceConfig.DEFAULT, 0, () -> null);
        comparator.afterFrameAdvanced(
                new Bk2FrameInput(0, 0, 0, false, "row zero"), false);
        setField(session, "comparator", comparator);
    }

    private static void installCurrentLoop(GameLoop loop) throws Exception {
        Engine engine = mock(Engine.class);
        Field loopField = Engine.class.getDeclaredField("gameLoop");
        loopField.setAccessible(true);
        loopField.set(engine, loop);
        setStaticField(Engine.class, "instance", engine);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setStaticField(
            Class<?> owner, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
