package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.SpecialStageTraceData;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.catalog.TraceEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Session-lifecycle integration test for the live special-stage visual trace
 * session. Drives the committed MVP trace through the SAME runtime path the
 * live picker uses per engine frame — {@link TraceSessionLauncher}'s SS API +
 * {@code GameLoop.doEnterSpecialStage} (which {@code finishSpecialStageLaunch}
 * calls) + {@code GameLoop.updateSpecialStageMode()} — and asserts:
 * <ul>
 *   <li>the loop enters {@link GameMode#SPECIAL_STAGE};</li>
 *   <li>each non-lag row steps the provider's {@code update()} exactly once and
 *       each lag row skips it (counts computed from the trace's lag column at
 *       runtime, never hardcoded);</li>
 *   <li>the session ends (fade-out armed) exactly at the recorded
 *       stage-finished frame.</li>
 * </ul>
 *
 * <p>The static {@link TraceSessionLauncher#launch} master-title boot is
 * Engine-level (GLFW / master-title screen) and is exercised by the manual jar
 * smoke instead; this test scopes to the per-frame driving contract, which is
 * where all the SS-specific logic lives.
 */
class TestSpecialStageVisualTraceSession {

    private static final Path TRACE_DIRECTORY =
            Path.of("src", "test", "resources", "traces", "s2", "special_stage");

    @AfterEach
    void tearDown() {
        setStaticActiveSession(null);
        SessionManager.clear();
    }

    @Test
    void visualSessionPacesLagRowsAndEndsAtStageFinished() throws Exception {
        File romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null && Files.exists(Path.of("s2.gen")),
                "s2.gen ROM required for the SS visual-session lifecycle test");

        // Boot headless graphics + ROM fixture + recorded team (mirrors the
        // headless replay harness bootstrap).
        GraphicsManager.getInstance().resetState();
        GraphicsManager.getInstance().initHeadless();
        Rom rom = new Rom();
        rom.open(romFile.getAbsolutePath());
        TestEnvironment.configureRomFixture(rom);
        GraphicsManager.getInstance().initHeadless();

        SpecialStageTraceData trace = SpecialStageTraceData.load(TRACE_DIRECTORY);
        TraceMetadata meta = trace.metadata();
        Path bk2 = TRACE_DIRECTORY.resolve(meta.sourceBk2());
        Bk2Movie movie = new Bk2MovieLoader().load(bk2);
        TraceEntry entry = new TraceEntry(TRACE_DIRECTORY, "s2", 0, 0,
                trace.frameCount(), meta.bk2FrameOffset(), 0, null, bk2, meta);
        TraceSessionLauncher session =
                new TraceSessionLauncher(entry, movie, trace, null);

        int stageFinished = trace.stageFinishedFrame().orElse(trace.frameCount() - 1);
        int expectedLag = 0;
        int expectedNonLag = 0;
        for (int i = 0; i <= stageFinished; i++) {
            if (trace.getFrame(i).lag()) {
                expectedLag++;
            } else {
                expectedNonLag++;
            }
        }

        GameLoop loop = new GameLoop(new InputHandler());
        int ssIndex = meta.specialStageIndex() != null ? meta.specialStageIndex() : 0;
        SpecialStageProvider provider = spy(new Sonic2SpecialStageProvider());

        // Mark active before entering (as finishSpecialStageLaunch does) so
        // GameLoop suppresses SS rewind capture for the session lifetime.
        setStaticActiveSession(session);
        loop.doEnterSpecialStage(provider, ssIndex, true);
        provider.setLagCompensation(0);

        assertEquals(GameMode.SPECIAL_STAGE, loop.getCurrentGameMode(),
                "SS trace session must enter SPECIAL_STAGE mode");

        Method updateSpecialStageMode =
                GameLoop.class.getDeclaredMethod("updateSpecialStageMode");
        updateSpecialStageMode.setAccessible(true);

        int stepped = 0;
        int skipped = 0;
        int safetyCap = trace.frameCount() + 16;
        int iterations = 0;
        while (!fadeStarted(session) && iterations < safetyCap) {
            boolean skip = session.shouldSkipCurrentSpecialStageTick();
            updateSpecialStageMode.invoke(loop);
            if (skip) {
                skipped++;
            } else {
                stepped++;
            }
            iterations++;
        }

        assertTrue(fadeStarted(session),
                "session must arm its fade-out end within the trace length");
        assertEquals(stageFinished, ssCursor(session),
                "session must end exactly at the recorded stage-finished frame");
        assertEquals(expectedNonLag, stepped,
                "stepped frames must equal the trace's non-lag row count through finish");
        assertEquals(expectedLag, skipped,
                "skipped frames must equal the trace's lag row count through finish");
        verify(provider, times(expectedNonLag)).update();
    }

    private static boolean fadeStarted(TraceSessionLauncher session) {
        return (boolean) getField(session, "fadeStarted");
    }

    private static int ssCursor(TraceSessionLauncher session) {
        return (int) getField(session, "ssCursor");
    }

    private static Object getField(Object target, String name) {
        try {
            Field field = TraceSessionLauncher.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
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
}
