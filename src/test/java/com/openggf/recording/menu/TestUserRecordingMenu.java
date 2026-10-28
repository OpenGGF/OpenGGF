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

    private static PixelFont font(List<String> text) {
        return new PixelFont() {
            @Override public void beginMegaBatch() { }
            @Override public void endMegaBatch() { }
            @Override public void drawText(String value, int x, int y, float scale, float r, float g, float b, float a) {
                text.add(value);
                assertTrue(x >= 0 && x + value.length() * (scale < 1 ? 6 : 9) <= 320, value);
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
