package com.openggf.tools;

import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.trace.TraceExecutionPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TraceCaptureRuntimeAdmissionTest {
    @Test
    void ordinaryFullRowsArePresentationAdmittedEvenWithoutGameplayBody() {
        TraceReplayDrive.DriveOutcome outcome =
                TraceReplayDrive.DriveOutcome.CONSUMED_WITHOUT_GAMEPLAY;

        assertTrue(TraceCaptureTool.shouldPresentOuterFrame(
                outcome, TraceExecutionPhase.FULL_LEVEL_FRAME));
        assertTrue(TraceCaptureTool.shouldPresentOuterFrame(
                outcome, TraceExecutionPhase.FULL_LEVEL_FRAME_WITH_SIDEKICK_ANIMATION_HELD));
    }

    @Test
    void nonGameplayAndRetryRowsAreNotPresented() {
        TraceReplayDrive.DriveOutcome outcome =
                TraceReplayDrive.DriveOutcome.CONSUMED_WITHOUT_GAMEPLAY;
        assertFalse(TraceCaptureTool.shouldPresentOuterFrame(
                outcome, TraceExecutionPhase.ADVANCE_ONLY));
        assertFalse(TraceCaptureTool.shouldPresentOuterFrame(
                TraceReplayDrive.DriveOutcome.RETRY, TraceExecutionPhase.FULL_LEVEL_FRAME));
    }

    @Test
    void fireClipUsesSemanticRisingAndStopSignals() {
        Sonic3kAIZEvents events = mock(Sonic3kAIZEvents.class);

        assertFalse(TraceCaptureTool.isFireTransitionClipStart(events, false));
        org.mockito.Mockito.when(events.isFireTransitionActive()).thenReturn(true);
        assertTrue(TraceCaptureTool.isFireTransitionClipStart(events, false));
        assertFalse(TraceCaptureTool.isFireTransitionClipStart(events, true));
        org.mockito.Mockito.when(events.isPostFireHazeActive()).thenReturn(true);
        assertTrue(TraceCaptureTool.isFireTransitionClipStop(events));
    }
}
