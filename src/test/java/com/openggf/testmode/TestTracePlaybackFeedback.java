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
    void mouseCanCancelLoadingBeforeItsAutomaticLaunch() {
        var feedback = new TracePlaybackFeedback(entry(), mock(PixelFont.class));
        feedback.render();
        feedback.update(new InputHandler(), new com.openggf.game.MasterTitlePointer(305, 215, false, true, false, 0));
        assertEquals(TracePlaybackFeedback.Result.BACK, feedback.consumeResult());
        feedback.update(new InputHandler());
        assertEquals(TracePlaybackFeedback.Result.NONE, feedback.consumeResult());
    }

    @Test
    void mousePagesTheCompleteFailureThenReturnsToTheList() {
        TraceLaunchStatus.record(entry(), "A detailed failure row\n".repeat(40) + "ERROR_TAIL");
        PixelFont font = mock(PixelFont.class);
        var feedback = new TracePlaybackFeedback(entry(), font);
        feedback.render();
        clearInvocations(font);
        feedback.update(new InputHandler(), new com.openggf.game.MasterTitlePointer(100, 100, false, false, false, -1000));
        feedback.render();
        assertTrue(text(font).contains("ERROR_TAIL"), text(font));
        feedback.update(new InputHandler(), new com.openggf.game.MasterTitlePointer(305, 215, false, true, false, 0));
        assertEquals(TracePlaybackFeedback.Result.BACK, feedback.consumeResult());
        assertTrue(TraceLaunchStatus.current().isEmpty());
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
        assertTrue(text(font).contains("The trace could not be started"), "a plain headline comes first");
        assertTrue(text(font).contains("Error hardware_timing.jsonl: invalid ordering"),
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

    @Test void tallScriptDiagnosticPagesKeepSeparateRowsAndReachTheEnd() {
        com.openggf.i18n.UiMessages.selectLanguage("bn");
        try {
            String reason = "পূর্ণ বিবরণ ".repeat(80) + "TRACE_TAIL";
            TraceLaunchStatus.record(entry(), reason);
            PixelFont font = mock(PixelFont.class);
            var feedback = new TracePlaybackFeedback(entry(), font);
            StringBuilder seen = new StringBuilder();
            for (int page = 0; page < 100; page++) {
                clearInvocations(font); feedback.render();
                var body = mockingDetails(font).getInvocations().stream()
                        .filter(call -> call.getMethod().getName().equals("drawText"))
                        .filter(call -> (Integer) call.getArgument(2) > 80 && (Integer) call.getArgument(2) < 198)
                        .filter(call -> (Integer) call.getArgument(1) > 16).toList();
                int previous = -100;
                for (var draw : body) {
                    String text = draw.getArgument(0); int y = draw.getArgument(2);
                    var line = com.openggf.graphics.text.HostText.shared().shape(text,
                            com.openggf.graphics.text.TextFace.COMPACT, com.openggf.graphics.text.HostTextContext.current());
                    assertTrue(y-previous >= com.openggf.game.MenuChrome.compactLineHeight());
                    assertTrue(y+line.metrics().cellHeight()+line.metrics().below() <= 198);
                    previous = y; seen.append(text);
                }
                feedback.update(key(GLFW_KEY_RIGHT));
            }
            assertTrue(seen.toString().contains("TRACE_TAIL"));
            assertTrue(TraceLaunchStatus.current().isPresent());
        } finally { com.openggf.i18n.UiMessages.selectLanguage("en"); }
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

    @Test
    void launchHeadlinesFollowTheFailureClassAndKeepTheExactError() {
        var entry = entry();
        record Case(Throwable failure, String headline, String error) { }
        for (var c : List.of(
                new Case(new java.io.UncheckedIOException(new java.io.IOException("trace.bk2: No such file")),
                        "A trace file could not be read", "trace.bk2: No such file"),
                new Case(new IllegalArgumentException("trace_schema 4 is not supported"),
                        "The trace could not be started", "trace_schema 4 is not supported"),
                new Case(new IllegalStateException("A master-title fade is already in progress"),
                        "The trace could not be started", "A master-title fade is already in progress"),
                new Case(new RuntimeException(new java.io.IOException("disk read: permission denied")),
                        "A trace file could not be read", "disk read: permission denied"),
                new Case(new UnsupportedOperationException("x"), "The trace could not be started", "x"),
                new Case(null, "The trace session ended before it started", "The trace session ended before launch completed"))) {
            TraceLaunchStatus.record(entry, c.failure());
            var held = TraceLaunchStatus.current().orElseThrow();
            assertEquals(c.headline(), held.headline(), String.valueOf(c.failure()));
            assertEquals(c.error(), held.reason(), "the searchable error text is unchanged");
        }
    }

    @Test
    void aLongErrorPagesEveryCharacterWithLeftAndRight() {
        var entry = entry();
        StringBuilder reason = new StringBuilder();
        for (int i = 0; i < 40; i++) reason.append("segment_").append(i).append("/hardware_timing.jsonl row ").append(1000 + i).append("; ");
        TraceLaunchStatus.record(entry, new IllegalArgumentException(reason.toString().strip()));
        var feedback = new TracePlaybackFeedback(entry, mock(PixelFont.class));
        feedback.launchFailed();
        StringBuilder shown = new StringBuilder();
        List<String> pages = new java.util.ArrayList<>();
        for (int page = 0; page < 10; page++) {
            PixelFont font = mock(PixelFont.class);
            var view = new TracePlaybackFeedback(entry, font);
            view.launchFailed();
            for (int i = 0; i < page; i++) view.update(key(GLFW_KEY_RIGHT));
            view.render();
            String text = text(font);
            if (!pages.isEmpty() && pages.getLast().equals(text)) break;
            assertTrue(text.contains("The trace could not be started"), "the headline stays on every page");
            pages.add(text);
        }
        assertTrue(pages.size() >= 3, "a long error forces real pages: " + pages.size());
        for (int i = 0; i < pages.size(); i++) {
            assertTrue(pages.get(i).contains("Page " + (i + 1) + " of " + pages.size()), pages.get(i));
            if (i > 0) assertNotEquals(pages.get(i - 1), pages.get(i), "page " + (i + 1) + " differs from the one before");
        }
        String all = String.join(" ", pages).replaceAll("\\s+", "");
        for (int i = 0; i < 40; i++) assertTrue(all.contains("segment_" + i + "/hardware_timing.jsonlrow" + (1000 + i)),
                "every character of the error is on some page: segment " + i);
        feedback.update(key(GLFW_KEY_RIGHT));
        feedback.update(key(GLFW_KEY_LEFT));
        assertFalse(TraceLaunchStatus.current().isEmpty(), "paging never dismisses the failure");
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
