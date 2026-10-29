package com.openggf.testmode;

import com.openggf.control.InputHandler;
import com.openggf.game.save.SelectedTeam;
import com.openggf.graphics.PixelFont;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.catalog.TraceEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

class TestModeTracePickerLaunchStatus {

    @BeforeEach
    void resetStatus() {
        TraceLaunchStatus.clear();
        TraceRunFailureStatus.clear();
    }

    @AfterEach
    void clearStatus() {
        TraceLaunchStatus.clear();
        TraceRunFailureStatus.clear();
    }

    @Test
    void launchWaitsUntilLoadingScreenHasRendered() {
        PixelFont font = mock(PixelFont.class);
        TestModeTracePicker picker = new TestModeTracePicker(List.of(entry()), font);

        picker.update(inputWith(GLFW_KEY_ENTER));
        assertEquals(TestModeTracePicker.Result.NONE, picker.consumeResult());

        picker.render();
        verifyText(font, "LOADING TRACE...");
        verifyText(font, "s1/entry");

        picker.update(new InputHandler());
        assertEquals(TestModeTracePicker.Result.LAUNCH, picker.consumeResult());
    }

    @Test
    void failedLaunchLeavesDiagnosticVisibleUntilAcknowledged() {
        PixelFont font = mock(PixelFont.class);
        TestModeTracePicker picker = new TestModeTracePicker(List.of(entry()), font);
        TraceLaunchStatus.record(entry(), new IllegalArgumentException(
                "hardware_timing.jsonl: events must use canonical ordering"));

        picker.launchFailed();
        picker.render();

        verifyText(font, "TRACE LAUNCH FAILED");
        verifyText(font, "Trace: s1/entry");
        verifyText(font,
                "Reason: hardware_timing.jsonl: events must use canonical ordering");
        assertTrue(TraceLaunchStatus.current().isPresent());

        picker.update(inputWith(GLFW_KEY_ENTER));

        assertFalse(TraceLaunchStatus.current().isPresent());
        assertEquals(TestModeTracePicker.Result.NONE, picker.consumeResult());
    }

    @Test
    void syntheticRunUsesManifestIdentityInPickerLoadingAndFailureText() {
        PixelFont font = mock(PixelFont.class);
        TraceEntry run = runEntry();
        TestModeTracePicker picker = new TestModeTracePicker(List.of(run), font);

        picker.render();
        // The full-size list row may abbreviate its suffix, but the run ID
        // and segment count must remain identifiable; details retain the full ID.
        verifyText(font, "> RUN s1-complete-run (2");
        verifyText(font, "SELECTED: s1/s1-complete-run");
        clearInvocations(font);
        picker.update(inputWith(GLFW_KEY_ENTER));
        picker.render();
        verifyText(font, "s1/s1-complete-run");
        clearInvocations(font);

        TraceLaunchStatus.record(run, "parser failed");
        picker.launchFailed();
        picker.render();
        verifyText(font, "Trace: s1/s1-complete-run");
    }

    @Test
    void longLaunchDiagnosticRemainsCompleteAcrossDetailPages() {
        String reason = "contract violation ".repeat(40) + "final diagnostic";
        PixelFont font = mock(PixelFont.class);
        TestModeTracePicker picker = new TestModeTracePicker(List.of(entry()), font);
        TraceLaunchStatus.record(entry(), reason);
        List<String> displayedRows = new java.util.ArrayList<>();
        int pages = picker.failurePages();
        assertTrue(pages > 1, "The fixture must exercise a measured page boundary");

        for (int page = 0; page < pages; page++) {
            clearInvocations(font);
            picker.render();
            mockingDetails(font).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("drawText"))
                    .filter(call -> ((Number) call.getArgument(2)).intValue() >= 67
                            && ((Number) call.getArgument(2)).intValue() < 195)
                    .forEach(call -> displayedRows.add((String) call.getArgument(0)));
            picker.update(inputWith(GLFW_KEY_RIGHT));
        }

        // Only whitespace omitted at a visual row boundary may differ. Every other
        // character, including punctuation, interior spaces and the final detail, must survive.
        String rowPattern = displayedRows.stream().map(java.util.regex.Pattern::quote)
                .collect(java.util.stream.Collectors.joining("\\s*"));
        String expectedBody = "Trace: s1/entry\nReason: " + reason;
        assertTrue(java.util.regex.Pattern.compile(rowPattern).matcher(expectedBody).matches(),
                () -> "Measured pages lost or changed diagnostic content: " + displayedRows);
        assertTrue(TraceLaunchStatus.current().isPresent(), "Reading another page is not acknowledgement");
        picker.update(inputWith(GLFW_KEY_ENTER));
        assertFalse(TraceLaunchStatus.current().isPresent());
        assertEquals(TestModeTracePicker.Result.NONE, picker.consumeResult());
    }

    private static void verifyText(PixelFont font, String text) {
        // Each draw call is a separate visual row. Preserve that boundary when
        // looking for words; do not normalize punctuation or canonical counter digits.
        assertTrue(displayedText(font).contains(normalized(text)),
                () -> "Missing displayed diagnostic: " + text + " in " + displayedText(font));
    }

    private static String displayedText(PixelFont font) {
        return normalized(mockingDetails(font).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("drawText"))
                .map(call -> (String) call.getArgument(0))
                .collect(java.util.stream.Collectors.joining(" ")));
    }

    private static String normalized(String text) {
        return text.replaceAll("\\s+", " ");
    }

    private static InputHandler inputWith(int key) {
        InputHandler input = new InputHandler();
        input.handleKeyEvent(key, GLFW_PRESS);
        return input;
    }

    private static TraceEntry entry() {
        SelectedTeam team = new SelectedTeam("sonic", List.of());
        return new TraceEntry(
                Path.of("entry"), "s1", 0, 0, 0, 0, 0, team,
                Path.of("entry.bk2"), metadataStub());
    }

    private static TraceEntry runEntry() {
        TraceRunManifest.Segment first = new TraceRunManifest.Segment(
                "first_completerun", "level", "complete_run",
                0, 1, 0, 1, null, null);
        TraceRunManifest.Segment second = new TraceRunManifest.Segment(
                "second_completerun", "level", "complete_run",
                1, 1, 1, 1, null, null);
        TraceRunManifest manifest = new TraceRunManifest(
                "s1", "s1-complete-run", "s1-complete-run.bk2",
                "checksum", List.of(first, second), List.of());
        return new TraceEntry(
                Path.of("s1"), "s1", 0, 0, 2, 0, 0,
                new SelectedTeam("sonic", List.of()),
                Path.of("s1-complete-run.bk2"), metadataStub(),
                Path.of("s1"), manifest);
    }

    private static TraceMetadata metadataStub() {
        return TraceFixtures.metadata("s1", 0, 0);
    }
}
