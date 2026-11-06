package com.openggf.game.recording.menu;

import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.MenuInput;
import com.openggf.control.PlayerInputState;
import com.openggf.game.recording.*;
import com.openggf.graphics.PixelFont;
import com.openggf.version.BuildIdentity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestUserRecordingMenu {
    @Test
    void opensSelectedOptionsAndExplicitPlayKeepsBothPlaybackToggles() {
        AtomicReference<UserRecordingPlaybackOptions> played = new AtomicReference<>();
        var menu = new UserRecordingMenu("s2", entry(120, RecordingVersionWarning.NONE), null,
                (recording, options) -> played.set(options));
        assertNull(played.get());
        press(menu, GLFW_KEY_UP);
        press(menu, GLFW_KEY_UP);
        press(menu, GLFW_KEY_ENTER);
        press(menu, GLFW_KEY_DOWN);
        press(menu, GLFW_KEY_ENTER);
        press(menu, GLFW_KEY_DOWN);
        press(menu, GLFW_KEY_ENTER);
        assertNotNull(played.get());
        assertEquals(119, played.get().targetFrame());
        assertTrue(played.get().pauseOnDesync());
        assertTrue(played.get().fastForward());
    }

    @Test
    void targetEditorUsesKeyboardAndClampsToMovieRows() {
        AtomicReference<UserRecordingPlaybackOptions> played = new AtomicReference<>();
        var menu = new UserRecordingMenu("s2", entry(120, RecordingVersionWarning.NONE), null,
                (recording, options) -> played.set(options));
        for (int i = 0; i < 3; i++) press(menu, GLFW_KEY_UP);
        press(menu, GLFW_KEY_ENTER);
        for (int i = 0; i < 3; i++) press(menu, GLFW_KEY_BACKSPACE);
        var typing = new InputHandler();
        MenuInput.handleCharEvent(typing, '4');
        MenuInput.handleCharEvent(typing, '2');
        menu.update(typing);
        press(menu, GLFW_KEY_ENTER);
        for (int i = 0; i < 3; i++) press(menu, GLFW_KEY_DOWN);
        press(menu, GLFW_KEY_ENTER);
        assertEquals(42, played.get().targetFrame());
        var state = new UserRecordingMenuState("s2", entry(120, RecordingVersionWarning.NONE));
        state.setTargetFrame(Integer.MAX_VALUE);
        assertEquals(119, state.options().targetFrame());
        state.setTargetFrame(-1);
        assertEquals(0, state.options().targetFrame());
    }

    @Test
    void warningDoesNotPreventPlaybackAndDetailsKeepLaunchIdentity() {
        var warned = entry(240, RecordingVersionWarning.PRERELEASE_BUILD_MISMATCH);
        AtomicReference<UserRecordingEntry> played = new AtomicReference<>();
        var menu = new UserRecordingMenu("s2", warned, null,
                (recording, options) -> played.set(recording));
        assertNotNull(menu.state().warningText());
        assertTrue(menu.state().selectedInfoLines().stream().anyMatch(line -> line.contains("sonic+tails")));
        press(menu, GLFW_KEY_ENTER);
        assertSame(warned, played.get());
    }

    @Test
    void controllerBackClosesOptionsAndDetailsBackReturnsToOptions() {
        var menu = new UserRecordingMenu("s2", entry(120, RecordingVersionWarning.NONE), null, (e, o) -> fail());
        press(menu, GLFW_KEY_DOWN);
        press(menu, GLFW_KEY_ENTER);
        press(menu, GLFW_KEY_ESCAPE);
        assertFalse(menu.consumeCloseRequested());
        var input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, InputActionMasks.ACTION_C, InputActionMasks.ACTION_C, false, false),
                PlayerInputState.neutral()));
        menu.update(input);
        assertTrue(menu.consumeCloseRequested());
        assertFalse(menu.consumeCloseRequested());
    }

    @Test
    void failedPlaybackRetainsDiagnosticAndPermitsAnExplicitRetry() {
        AtomicInteger attempts = new AtomicInteger();
        List<String> text = new ArrayList<>();
        var menu = new UserRecordingMenu("s2", entry(120, RecordingVersionWarning.NONE), font(text), (e, o) -> {
            if (attempts.incrementAndGet() == 1) throw new java.io.IOException("Unreadable movie payload");
        });
        press(menu, GLFW_KEY_ENTER);
        menu.render();
        assertTrue(text.stream().anyMatch(line -> line.contains("Unreadable movie payload")));
        press(menu, GLFW_KEY_ENTER);
        assertEquals(2, attempts.get());
    }

    @Test
    void selectedOptionsEditorAndDetailsRemainInsideNativeBounds() {
        List<String> text = new ArrayList<>();
        var menu = new UserRecordingMenu("s2", entry(120, RecordingVersionWarning.DIRTY_BUILD), font(text), (e, o) -> { });
        menu.render();
        press(menu, GLFW_KEY_DOWN);
        press(menu, GLFW_KEY_ENTER);
        menu.render();
        press(menu, GLFW_KEY_ESCAPE);
        for (int i = 0; i < 4; i++) press(menu, GLFW_KEY_UP);
        press(menu, GLFW_KEY_ENTER);
        menu.render();
        assertFalse(text.isEmpty());
    }

    @Test
    void detailsExposeCompleteFailureAndOptionCopyWithoutPlaybackOrOptionChanges() {
        var played = new java.util.concurrent.atomic.AtomicInteger();
        var draws = new ArrayList<String>();
        var font = new com.openggf.graphics.PixelFont() {
            @Override public void drawText(String text, int x, int y, float scale, float r, float g, float b, float a) { draws.add(text); }
        };
        var menu = new UserRecordingMenu("s2", entry(120, RecordingVersionWarning.NONE), font, (e, o) -> {
            played.incrementAndGet();
            throw new IllegalStateException("Playback recovery guidance ".repeat(35) + "ERROR_SENTINEL");
        });
        press(menu, GLFW_KEY_ENTER);
        assertEquals(1, played.get());
        var options = menu.state().options();
        menu.render();
        assertTrue(draws.stream().anyMatch(text -> text.contains("F1")));
        draws.clear();
        press(menu, org.lwjgl.glfw.GLFW.GLFW_KEY_F1);
        for (int line = 0; line < 80; line++) {
            menu.render(); press(menu, GLFW_KEY_DOWN);
        }
        assertTrue(draws.stream().anyMatch(text -> text.contains("ERROR_SENTINEL")));
        assertTrue(draws.stream().anyMatch(text -> text.contains("Pause on desync")));
        assertTrue(draws.stream().anyMatch(text -> text.contains("Details") && text.contains("warnings")));
        press(menu, GLFW_KEY_ENTER);
        assertEquals(1, played.get(), "Confirm in Details cannot launch playback");
        assertEquals(options, menu.state().options());
        press(menu, org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        assertFalse(menu.consumeCloseRequested(), "Back returns to options before closing the library");
    }

    @Test
    void mouseEditsOptionsAndTheTargetKeypadBeforeLaunchingExactlyOnce() {
        AtomicReference<UserRecordingPlaybackOptions> played = new AtomicReference<>();
        AtomicInteger launches = new AtomicInteger();
        var menu = new UserRecordingMenu("s2", entry(120, RecordingVersionWarning.NONE), font(new ArrayList<>()),
                (recording, options) -> { played.set(options); launches.incrementAndGet(); });
        mouse(menu, 30, 72, true, true, false, 0); // Pause on desync
        mouse(menu, 30, 88, true, true, false, 0); // Fast forward
        assertTrue(menu.state().options().pauseOnDesync());
        assertTrue(menu.state().options().fastForward());
        mouse(menu, 30, 56, true, false, false, 0); // Hover exposes the target's step arrows.
        menu.render();
        mouse(menu, 270, 56, false, true, false, 0);
        assertEquals(59, menu.state().options().targetFrame(), "the drawn left arrow uses the existing 60-frame step");
        mouse(menu, 300, 56, false, true, false, 0);
        assertEquals(119, menu.state().options().targetFrame(), "the drawn right arrow increments");
        mouse(menu, 30, 56, true, true, false, 0); // Target frame -> integer keypad
        menu.render();
        mouse(menu, 135, 155, true, true, false, 0); // Clear
        mouse(menu, 195, 90, true, true, false, 0); // 4
        mouse(menu, 75, 90, true, true, false, 0); // 2
        mouse(menu, 270, 155, true, true, false, 0); // Set
        assertEquals(42, menu.state().options().targetFrame());
        assertNull(played.get(), "editing cannot launch playback");
        mouse(menu, 30, 112, true, true, false, 0); // Play
        mouse(menu, 30, 112, false, false, false, 0); // a held mouse has no second press edge
        assertEquals(1, launches.get());
        assertEquals(42, played.get().targetFrame());
        assertTrue(played.get().pauseOnDesync());
        assertTrue(played.get().fastForward());
    }

    @Test
    void mouseDetailsScrollAndBackReturnThroughOptionsBeforeClosing() {
        var draws = new ArrayList<String>();
        var menu = new UserRecordingMenu("s2", entry(120, RecordingVersionWarning.NONE), font(draws), (e, o) -> fail());
        mouse(menu, 30, 137, true, true, false, 0); // Details and warnings
        mouse(menu, 80, 100, false, false, false, -1000);
        menu.render();
        assertFalse(draws.isEmpty(), "the complete details viewport is drawn after the wheel");
        mouse(menu, 80, 100, false, false, true, 0);
        assertFalse(menu.consumeCloseRequested(), "Back returns to the options");
        mouse(menu, 80, 100, false, false, true, 0);
        assertTrue(menu.consumeCloseRequested());
    }

    private static void mouse(UserRecordingMenu menu, int x, int y, boolean moved, boolean click, boolean back, int wheel) {
        menu.update(new InputHandler(), new com.openggf.game.MasterTitlePointer(x, y, moved, click, back, wheel));
    }

    private static PixelFont font(List<String> text) {
        return new PixelFont() {
            @Override public void beginMegaBatch() { }
            @Override public void endMegaBatch() { }
            @Override public void drawText(String value, int x, int y, float scale, float r, float g, float b, float a) {
                text.add(value);
                assertTrue(x >= 0 && x + com.openggf.graphics.MenuPixelFont.textWidth(value, scale) <= 320, value);
                assertTrue(y >= 0 && y + (scale < 1 ? 8 : 10) <= 224, value);
            }
        };
    }

    private static void press(UserRecordingMenu menu, int key) {
        var input = new InputHandler();
        input.handleKeyEvent(key, GLFW_PRESS);
        menu.update(input);
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
        menu.update(input);
    }

    private static UserRecordingEntry entry(int count, RecordingVersionWarning warning) {
        var created = Instant.parse("2026-06-29T14:30:22Z");
        var manifest = new UserRecordingManifest(UserRecordingManifest.CURRENT_SCHEMA_VERSION,
                "Sonic 2 recording", new BuildIdentity("0.7.prerelease", "abcdef123", false),
                new RecordingLaunchContext("s2", 2, 1, "sonic", List.of("tails"), false, "current-act-fresh-start"),
                UserRecordingSidecarMetadata.everyFrame(), new RecordingDeterminismMetadata(0, null),
                "A", count, UserRecordingStopReason.USER_STOPPED, created);
        return new UserRecordingEntry(Path.of("s2-" + count + ".bk2"), "Sonic 2 recording", manifest,
                count, created, warning, null);
    }
}
