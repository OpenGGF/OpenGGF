package com.openggf.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
class TestEditorWindowChromes {
    @Test
    void engineBuildAloneHasNoExternalWindowChrome() {
        assertNull(EditorWindowChromes.discover(), "Only a separate editor build supplies window chrome");
    }

    @Test
    void engineSelectsBuiltInHudWithoutAnExternalProvider() {
        var hud = new com.openggf.editor.hud.EditorHudRuntime();
        org.junit.jupiter.api.Assertions.assertSame(hud, EditorWindowChromes.select(() -> hud));
    }

    @Test
    void externalServiceProviderReplacesTheHud(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        var service = root.resolve("META-INF/services/" + EditorWindowChrome.class.getName());
        java.nio.file.Files.createDirectories(service.getParent());
        java.nio.file.Files.writeString(service, ExternalChrome.class.getName());
        var thread = Thread.currentThread();
        var previous = thread.getContextClassLoader();
        try (var loader = new java.net.URLClassLoader(new java.net.URL[] {root.toUri().toURL()}, previous)) {
            thread.setContextClassLoader(loader);
            org.junit.jupiter.api.Assertions.assertInstanceOf(ExternalChrome.class,
                    EditorWindowChromes.select(() -> { throw new AssertionError("External chrome must replace the HUD"); }));
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    public static final class ExternalChrome implements EditorWindowChrome {
        public void init(Host host, float framebufferScale) { }
        public boolean setActive(boolean editorFrame) { return editorFrame; }
        public boolean isActive() { return false; }
        public int[] gameViewport(int ww, int wh, int fw, int fh, int lw, int lh) { return null; }
        public void render(int ww, int wh, int fw, int fh) { }
        public void close() { }
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
