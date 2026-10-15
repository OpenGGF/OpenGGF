package com.openggf.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
class TestEditorWindowChromes {
    @Test
    void engineBuildAloneHasNoWindowChrome() {
        assertNull(EditorWindowChromes.discover(), "Only a separate editor build supplies window chrome");
    }

    @Test
    void classicPropertyKeepsTheInGameChrome() {
        String previous = System.getProperty("openggf.editor.ui");
        try {
            System.setProperty("openggf.editor.ui", "classic");
            assertTrue(EditorWindowChromes.classicRequested());
            assertNull(EditorWindowChromes.discover());
            System.setProperty("openggf.editor.ui", "window");
            assertFalse(EditorWindowChromes.classicRequested());
        } finally {
            if (previous == null) System.clearProperty("openggf.editor.ui");
            else System.setProperty("openggf.editor.ui", previous);
        }
    }

    @Test
    void actionCommandsCanLeaveOutTheInGamePanes() {
        var palette = new EditorCommandPalette();
        int all = palette.actionCommands(action -> { }).size();
        var withoutPanes = palette.actionCommands(action -> { }, action -> !EditorCommandPalette.drivesInGamePanes(action));
        assertTrue(all == EditorInputHandler.Action.values().length);
        assertTrue(withoutPanes.size() == all - 7, "Focus cycling and six library-browse actions are left out");
        assertFalse(withoutPanes.stream().anyMatch(command -> command.label().toLowerCase().contains("browse")));
    }

    @Test
    void chromeDefaultsPassInputThroughAndLeaveThePadsAlone() {
        EditorWindowChrome chrome = new EditorWindowChrome() {
            public void init(Host host, float framebufferScale) { }
            public boolean setActive(boolean editorFrame) { return editorFrame; }
            public boolean isActive() { return false; }
            public int[] gameViewport(int ww, int wh, int fw, int fh, int lw, int lh) { return null; }
            public void render(int ww, int wh, int fw, int fh) { }
            public void close() { }
        };
        assertTrue(chrome.key(0, 0, 0));
        assertTrue(chrome.mouseButton(0, 0));
        assertTrue(chrome.character('a'));
        assertFalse(chrome.update(null));
        assertFalse(chrome.ownsGamepads());
    }
}
