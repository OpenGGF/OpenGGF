package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.SpecialStageTraceData;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.catalog.TraceEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Mirrors the LEVEL-mode playback skip gate for SPECIAL_STAGE mode: when the
 * active trace session marks the current row as a lag frame,
 * {@code GameLoop.updateSpecialStageMode()} must NOT run the provider's
 * {@code update()} that engine frame (the recorded input replaces live input;
 * a lag row advances nothing engine-side). A non-lag row steps the provider
 * exactly once.
 *
 * <p>The session is a real {@link TraceSessionLauncher} built from the
 * committed MVP special-stage trace (loadable without a ROM); the provider is a
 * Mockito mock so we can count {@code update()} calls. The trace's own lag
 * column selects the lag / non-lag row indices at runtime.
 */
class TestGameLoopSpecialStageSkipGate {

    private static final Path TRACE_DIRECTORY =
            Path.of("src", "test", "resources", "traces", "s2", "special_stage");

    private GameLoop loop;
    private SpecialStageProvider provider;
    private TraceSessionLauncher session;
    private int lagFrame;
    private int nonLagFrame;

    @BeforeEach
    void setUp() throws Exception {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());

        SpecialStageTraceData trace = SpecialStageTraceData.load(TRACE_DIRECTORY);
        TraceMetadata meta = trace.metadata();
        Path bk2 = TRACE_DIRECTORY.resolve(meta.sourceBk2());
        Bk2Movie movie = new Bk2MovieLoader().load(bk2);
        TraceEntry entry = new TraceEntry(TRACE_DIRECTORY, "s2", 0, 0,
                trace.frameCount(), meta.bk2FrameOffset(), 0, null, bk2, meta);
        session = new TraceSessionLauncher(entry, movie, trace, null);

        lagFrame = -1;
        nonLagFrame = -1;
        for (int i = 0; i < trace.frameCount() && (lagFrame < 0 || nonLagFrame < 0); i++) {
            boolean lag = trace.getFrame(i).lag();
            if (lag && lagFrame < 0) {
                lagFrame = i;
            } else if (!lag && nonLagFrame < 0) {
                nonLagFrame = i;
            }
        }
        assertTrue(lagFrame >= 0, "MVP trace should contain at least one lag row");
        assertTrue(nonLagFrame >= 0, "MVP trace should contain at least one non-lag row");

        loop = new GameLoop(new InputHandler());
        loop.changeGameModeWithoutRewindBoundary(GameMode.SPECIAL_STAGE);
        provider = mock(SpecialStageProvider.class);
        setField(loop, "activeSpecialStageProvider", provider);
    }

    @AfterEach
    void tearDown() {
        setStaticActiveSession(null);
        SessionManager.clear();
    }

    @Test
    void lagRowSkipsProviderUpdate() throws Exception {
        setStaticActiveSession(session);
        setField(session, "ssCursor", lagFrame);

        invokeUpdateSpecialStageMode();

        verify(provider, never()).update();
    }

    @Test
    void nonLagRowRunsProviderUpdateOnce() throws Exception {
        setStaticActiveSession(session);
        setField(session, "ssCursor", nonLagFrame);

        invokeUpdateSpecialStageMode();

        verify(provider, times(1)).update();
    }

    private void invokeUpdateSpecialStageMode() throws Exception {
        Method method = GameLoop.class.getDeclaredMethod("updateSpecialStageMode");
        method.setAccessible(true);
        method.invoke(loop);
    }

    private static void setStaticActiveSession(TraceSessionLauncher value) {
        try {
            Field field = TraceSessionLauncher.class.getDeclaredField("activeSession");
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
