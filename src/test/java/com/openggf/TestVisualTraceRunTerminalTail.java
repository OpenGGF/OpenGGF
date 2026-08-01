package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameMode;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestVisualTraceRunTerminalTail {

    @AfterEach
    void clearActiveSession() {
        setStaticField("activeSession", null);
    }

    @Test
    void appliesEachPhysicalTailRowOnceAndClearsOverrideAfterEveryStep() {
        Bk2Movie movie = new Bk2Movie(
                Path.of("tail.bk2"), "", Map.of(),
                List.of(frame(0, 0), frame(1, 1), frame(2, 2)), 1);
        TraceSessionLauncher session = new TraceSessionLauncher(
                null, movie, List.of(), null);
        TraceRunPlaybackCoordinator coordinator =
                mock(TraceRunPlaybackCoordinator.class);
        when(coordinator.phase()).thenReturn(
                TraceRunPlaybackCoordinator.Phase.TERMINAL_TAIL);
        when(coordinator.finishTerminalTail(GameMode.TITLE_SCREEN))
                .thenReturn(List.of());
        setField(session, "runCoordinator", coordinator);
        setField(session, "runTerminalTail",
                new TraceRunReplayWalker.TerminalMovieTailPlan(
                        1, 2, GameMode.TITLE_SCREEN));
        setField(session, "runTerminalTailRow", 1);
        setStaticField("activeSession", session);
        InputHandler input = new InputHandler();

        TraceSessionLauncher.applyRunTerminalTailInputIfActive(input);
        TraceSessionLauncher.applyRunTerminalTailInputIfActive(input);
        assertTrue(input.hasLogicalOverride());
        assertEquals(1, input.logical().player1().heldMask());

        session.runAdvanceTickIfActive(GameMode.TITLE_SCREEN, 0);
        assertFalse(input.hasLogicalOverride());
        assertEquals(2, getIntField(session, "runTerminalTailRow"));

        TraceSessionLauncher.applyRunTerminalTailInputIfActive(input);
        assertEquals(2, input.logical().player1().heldMask());
        session.runAdvanceTickIfActive(GameMode.TITLE_SCREEN, 0);
        assertFalse(input.hasLogicalOverride());
        assertEquals(3, getIntField(session, "runTerminalTailRow"));
        verify(coordinator).finishTerminalTail(GameMode.TITLE_SCREEN);
    }

    private static Bk2FrameInput frame(int index, int mask) {
        return new Bk2FrameInput(index, mask, 0, false, "");
    }

    private static int getIntField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
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

    private static void setStaticField(String name, Object value) {
        try {
            Field field = TraceSessionLauncher.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
