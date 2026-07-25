package com.openggf.debug;

import com.openggf.control.InputHandler;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDebugOverlayManagerReset {

    @Test
    public void testResetStateRestoresToggleDefaults() {
        DebugOverlayManager manager = DebugOverlayManager.getInstance();

        for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
            manager.setEnabled(toggle, !toggle.defaultEnabled());
        }

        manager.resetState();

        for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
            assertEquals(toggle.defaultEnabled(), manager.isEnabled(toggle), "resetState should restore default for " + toggle.name());
        }
    }

    @Test
    public void buildShortcutLines_reusesCachedStringsUntilToggleStateChanges() {
        DebugOverlayManager manager = DebugOverlayManager.getInstance();
        manager.resetState();

        String initialOverlayLine = manager.buildShortcutLines().get(DebugOverlayToggle.OVERLAY.ordinal());
        String repeatedOverlayLine = manager.buildShortcutLines().get(DebugOverlayToggle.OVERLAY.ordinal());

        assertSame(initialOverlayLine, repeatedOverlayLine,
                "shortcut text should be reused when overlay state is unchanged");

        manager.setEnabled(DebugOverlayToggle.OVERLAY, !DebugOverlayToggle.OVERLAY.defaultEnabled());
        String updatedOverlayLine = manager.buildShortcutLines().get(DebugOverlayToggle.OVERLAY.ordinal());

        assertNotSame(initialOverlayLine, updatedOverlayLine,
                "changing a toggle should invalidate the cached shortcut text");
        String expectedState = DebugOverlayToggle.OVERLAY.defaultEnabled() ? "Off" : "On";
        assertTrue(updatedOverlayLine.endsWith(": " + expectedState));

        String repeatedUpdatedLine = manager.buildShortcutLines().get(DebugOverlayToggle.OVERLAY.ordinal());
        assertSame(updatedOverlayLine, repeatedUpdatedLine,
                "updated shortcut text should also be reused until the next toggle change");

        manager.resetState();
    }

    @Test
    public void updateInputIgnoresToggleKeysWhenDebugShortcutsAreDisabled() {
        DebugOverlayManager manager = DebugOverlayManager.getInstance();
        manager.resetState();
        InputHandler input = new InputHandler();
        input.handleKeyEvent(GLFW.GLFW_KEY_F12, GLFW.GLFW_PRESS);

        manager.updateInput(input, false);

        assertFalse(manager.isEnabled(DebugOverlayToggle.OBJECT_ART_VIEWER),
                "F12 must not enable the art viewer when debug shortcuts are disabled");
        manager.resetState();
    }

    @Test
    public void updateInputAllowsPerformanceToggleWhenDebugShortcutsAreDisabled() {
        DebugOverlayManager manager = DebugOverlayManager.getInstance();
        manager.resetState();
        InputHandler input = new InputHandler();
        input.handleKeyEvent(GLFW.GLFW_KEY_P, GLFW.GLFW_PRESS);

        manager.updateInput(input, false);

        assertTrue(manager.isEnabled(DebugOverlayToggle.PERFORMANCE),
                "P must toggle the performance panel even when normal debug shortcuts are disabled");
        manager.resetState();
    }

    /**
     * OBJECT_DEBUG is GLFW_KEY_O and the toggles fired on a bare isKeyPressed, so
     * the SHIFT+O capture default toggled object debug on the same keystroke that
     * started a recording.
     */
    @Test
    public void aModifiedKeystrokeDoesNotToggleAnOverlay() {
        DebugOverlayManager manager = DebugOverlayManager.getInstance();
        manager.resetState();
        boolean before = manager.isEnabled(DebugOverlayToggle.OBJECT_DEBUG);
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_PRESS);
        handler.handleKeyEvent(GLFW.GLFW_KEY_O, GLFW.GLFW_PRESS);

        manager.updateInput(handler, true);

        assertEquals(before, manager.isEnabled(DebugOverlayToggle.OBJECT_DEBUG));
        manager.resetState();
    }

    @Test
    public void anUnmodifiedKeystrokeStillTogglesAnOverlay() {
        DebugOverlayManager manager = DebugOverlayManager.getInstance();
        manager.resetState();
        boolean before = manager.isEnabled(DebugOverlayToggle.PLAYER_PANEL);
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW.GLFW_KEY_F3, GLFW.GLFW_PRESS);

        manager.updateInput(handler, true);

        assertNotEquals(before, manager.isEnabled(DebugOverlayToggle.PLAYER_PANEL));
        manager.resetState();
    }

    /**
     * PERFORMANCE is dispatched above the debugShortcutsEnabled gate, so it needs
     * the same treatment separately. Ctrl+P is the clipboard-copy chord and must
     * not also toggle the overlay -- a pre-existing double-fire.
     */
    @Test
    public void ctrlPCopiesStatsWithoutAlsoTogglingThePerformanceOverlay() {
        DebugOverlayManager manager = DebugOverlayManager.getInstance();
        manager.resetState();
        boolean before = manager.isEnabled(DebugOverlayToggle.PERFORMANCE);
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_PRESS);
        handler.handleKeyEvent(GLFW.GLFW_KEY_P, GLFW.GLFW_PRESS);

        manager.updateInput(handler, true);

        assertEquals(before, manager.isEnabled(DebugOverlayToggle.PERFORMANCE));
        manager.resetState();
    }

    @Test
    public void ringBoundsToggleDoesNotShareTheLevelSelectShortcut() {
        int levelSelectKey = SonicConfigurationService.getInstance().getInt(SonicConfiguration.LEVEL_SELECT_KEY);

        assertFalse(DebugOverlayToggle.RING_BOUNDS.keyCode() == levelSelectKey,
                "Ring-bounds overlay must not share the runtime level-select debug shortcut");
    }
}

