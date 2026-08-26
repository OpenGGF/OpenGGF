package com.openggf.tools;

import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.trace.TraceExecutionPhase;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    void inputLatchFastForwardRetiresOnlyRealRuntimeWorkAtOwnedBoundaries() {
        GameplayModeContext gameplayMode = mock(GameplayModeContext.class);

        TraceCaptureTool.serviceHeadlessFastForwardHardware(
                gameplayMode, TraceExecutionPhase.ADVANCE_ONLY);

        org.mockito.Mockito.inOrder(gameplayMode).verify(gameplayMode)
                .serviceHardwareTimingBoundary(HardwareServiceBoundary.POST_OBJECTS);
        verify(gameplayMode).serviceHardwareTimingBoundary(
                HardwareServiceBoundary.PRE_MAIN_LOOP);
    }

    @Test
    void ordinaryFastForwardRowsKeepTheirExistingBoundaryOwnership() {
        GameplayModeContext gameplayMode = mock(GameplayModeContext.class);

        TraceCaptureTool.serviceHeadlessFastForwardHardware(
                gameplayMode, TraceExecutionPhase.FULL_LEVEL_FRAME);

        verifyNoInteractions(gameplayMode);
    }

    @Test
    void fastForwardServiceDoesNotCreateRuntimeWork() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/openggf/tools/TraceCaptureTool.java"));
        int service = source.indexOf("serviceHeadlessFastForwardHardware");

        assertTrue(service >= 0);
        assertTrue(source.substring(service, Math.min(source.length(), service + 700))
                        .contains("TraceExecutionPhase.ADVANCE_ONLY"));
        assertFalse(source.substring(service, Math.min(source.length(), service + 700))
                        .contains("queue("));
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
