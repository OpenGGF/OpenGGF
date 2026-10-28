package com.openggf.testmode;

import com.openggf.control.InputHandler;
import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.save.SelectedTeam;
import com.openggf.graphics.PixelFont;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.catalog.TraceEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.*;

class TestTracePlaybackFeedback {
    @BeforeEach @AfterEach
    void clearDiagnostics() {
        TraceLaunchStatus.clear();
        TraceRunFailureStatus.clear();
    }

    @Test
    void aSelectedTraceWaitsForPresentedLoadingBeforeIssuingExactlyOneLaunch() {
        PixelFont font = mock(PixelFont.class);
        var feedback = new TracePlaybackFeedback(entry(), font);
        feedback.update(new InputHandler());
        assertEquals(TracePlaybackFeedback.Result.NONE, feedback.consumeResult());
        feedback.render();
        assertTrue(text(font).contains("LOADING TRACE"));
        feedback.update(new InputHandler());
        assertEquals(TracePlaybackFeedback.Result.LAUNCH, feedback.consumeResult());
        feedback.update(new InputHandler());
        assertEquals(TracePlaybackFeedback.Result.NONE, feedback.consumeResult());
    }

    @Test
    void cancellingLoadingNeverIssuesLaunch() {
        var feedback = new TracePlaybackFeedback(entry(), mock(PixelFont.class));
        feedback.render();
        feedback.update(key(GLFW_KEY_ESCAPE));
        assertEquals(TracePlaybackFeedback.Result.BACK, feedback.consumeResult());
        feedback.update(new InputHandler());
        assertEquals(TracePlaybackFeedback.Result.NONE, feedback.consumeResult());
    }

    @Test
    void launchFailureRemainsVisibleUntilAcknowledgedThenReturnsToCommonList() {
        var entry = entry();
        TraceLaunchStatus.record(entry, "hardware_timing.jsonl: invalid ordering");
        PixelFont font = mock(PixelFont.class);
        var feedback = new TracePlaybackFeedback(entry, font);
        feedback.launchFailed();
        feedback.render();
        assertTrue(text(font).contains("TRACE LAUNCH FAILED"));
        assertTrue(text(font).contains("Reason hardware_timing.jsonl: invalid ordering"),
                "Diagnostics draw a muted label column beside the complete value");
        feedback.update(key(GLFW_KEY_ENTER));
        assertTrue(TraceLaunchStatus.current().isEmpty());
        assertEquals(TracePlaybackFeedback.Result.BACK, feedback.consumeResult());
        feedback.update(new InputHandler());
        assertEquals(TracePlaybackFeedback.Result.NONE, feedback.consumeResult());
    }

    @Test
    void returnedWholeRunComparisonFailureNeedsNoSelectedEntry() {
        TraceRunFailureStatus.recordComparison(2, "special_stage/4", "level/CPZ/2", 12345, 12999);
        PixelFont font = mock(PixelFont.class);
        var feedback = new TracePlaybackFeedback(null, font);
        feedback.render();
        assertTrue(text(font).contains("TRACE FAILED"));
        assertTrue(text(font).contains("Expected special_stage/4"));
        assertTrue(text(font).contains("Actual level/CPZ/2"));
        assertTrue(text(font).contains("Cursor 12345 Steps 12999"));
        feedback.update(key(GLFW_KEY_ESCAPE));
        assertTrue(TraceRunFailureStatus.current().isEmpty());
        assertEquals(TracePlaybackFeedback.Result.BACK, feedback.consumeResult());
    }

    @Test
    void readingEveryDiagnosticPagePreservesEveryCharacterAndDoesNotAcknowledge() {
        String reason = "contract violation ".repeat(40) + "final diagnostic";
        TraceLaunchStatus.record(entry(), reason);
        PixelFont font = mock(PixelFont.class);
        var feedback = new TracePlaybackFeedback(null, font);
        StringBuilder body = new StringBuilder();
        for (int page = 0; page < 2; page++) {
            clearInvocations(font);
            feedback.render();
            mockingDetails(font).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("drawText"))
                    .filter(call -> (Integer) call.getArgument(2) >= 67 && (Integer) call.getArgument(2) <= 190)
                    .filter(call -> (Integer) call.getArgument(1) > 16)
                    .forEach(call -> body.append((String) call.getArgument(0)));
            feedback.update(key(GLFW_KEY_RIGHT));
        }
        assertTrue(body.toString().contains(reason), "Every page joins back into the complete value");
        assertTrue(TraceLaunchStatus.current().isPresent());
    }

    @Test
    void controllerBackAcknowledgesWithoutLaunching() {
        TraceRunFailureStatus.recordReason(0, "wrong terminal mode", 99, 100);
        var feedback = new TracePlaybackFeedback(null, null);
        var input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, InputActionMasks.ACTION_C, InputActionMasks.ACTION_C, false, false),
                PlayerInputState.neutral()));
        feedback.update(input);
        assertEquals(TracePlaybackFeedback.Result.BACK, feedback.consumeResult());
        assertTrue(TraceRunFailureStatus.current().isEmpty());
    }

    private static InputHandler key(int key) {
        var input = new InputHandler();
        input.handleKeyEvent(key, GLFW_PRESS);
        return input;
    }

    private static String text(PixelFont font) {
        return mockingDetails(font).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("drawText"))
                .map(call -> (String) call.getArgument(0))
                .collect(java.util.stream.Collectors.joining(" ")).replaceAll("\\s+", " ");
    }

    private static TraceEntry entry() {
        return new TraceEntry(Path.of("entry"), "s1", 0, 0, 100, 0, 0,
                new SelectedTeam("sonic", List.of()), Path.of("entry.bk2"),
                TraceFixtures.metadata("s1", 0, 0));
    }
}
