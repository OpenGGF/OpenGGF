package com.openggf.game;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.graphics.PixelFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestMenuPathBrowser {
    @TempDir Path directory;

    @Test
    void ownerCancellationStopsLoadingAndIgnoresSubsequentInput() {
        var browser = new MenuPathBrowser(directory.toString());
        browser.cancel();
        browser.cancel();
        InputHandler input = input();
        input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_PRESS);
        input.refreshLogicalSnapshot(); browser.update(input);
        assertFalse(browser.loading());
        assertTrue(browser.cancelled());
        assertNull(browser.selected());
    }

    @Test
    void cancellingAnOutstandingReadNeverPublishesASelection() {
        var browser = new MenuPathBrowser(directory.toString());
        InputHandler input = input();
        input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_PRESS);
        input.refreshLogicalSnapshot(); browser.update(input); input.update();
        assertTrue(browser.cancelled());
        assertNull(browser.selected());
    }

    @Test
    void invalidPathHasReadableDetailsAndCanBeCancelled() {
        var browser = new MenuPathBrowser("bad\0path");
        InputHandler input = input();
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (browser.loading()) { input.refreshLogicalSnapshot(); browser.update(input); input.update(); Thread.onSpinWait(); }
        });
        List<String> text = new ArrayList<>();
        PixelFont font = new PixelFont() {
            @Override public void drawText(String value, int x, int y, float scale, float r, float g, float b, float a) {
                text.add(value);
                assertTrue(x + measureWidth(value, scale) <= 320);
                assertTrue(y + (scale < 1 ? 8 : 10) <= 224);
            }
        };
        browser.render(font, 320);
        assertTrue(text.contains("Cannot open path"));
        input.handleKeyEvent(GLFW_KEY_F1, GLFW_PRESS);
        input.refreshLogicalSnapshot(); browser.update(input); input.update();
        text.clear(); browser.render(font, 320);
        assertTrue(text.stream().anyMatch(line -> line.contains("Cannot read path")));
        assertNull(browser.selected());
    }

    @Test
    void likelyRomFilesArePinnedButEveryFilenameRemainsSelectable() throws Exception {
        Files.writeString(directory.resolve("a.txt"), "unrelated");
        Path rom = Files.writeString(directory.resolve("z.gen"), "ROM candidate");
        var browser = new MenuPathBrowser(directory.toString(), true, "LOCATE", "Choose a file");
        InputHandler input = input();
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (browser.loading()) { input.refreshLogicalSnapshot(); browser.update(input); input.update(); Thread.onSpinWait(); }
        });
        browser.pointer(20, 73, true, true, false, 0);
        input.refreshLogicalSnapshot(); browser.update(input);
        assertEquals(rom.toString(), browser.selected());
        assertTrue(MenuPathBrowser.likelyRom(Path.of("SONIC.BIN")));
        assertFalse(MenuPathBrowser.likelyRom(Path.of("readme.txt")));
        assertEquals("~/Downloads", MenuPathBrowser.friendlyPath(Path.of(System.getProperty("user.home"), "Downloads")));
    }

    @Test
    void shortcutsAreAvailableByKeyboardDirectionsAndPointer() {
        var browser = new MenuPathBrowser(directory.toString(), true, "LOCATE", "Choose a file");
        InputHandler input = input();
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (browser.loading()) { input.refreshLogicalSnapshot(); browser.update(input); input.update(); Thread.onSpinWait(); }
        });
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS); // Move focus to Home.
        input.refreshLogicalSnapshot(); browser.update(input); input.update();
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_PRESS);
        input.refreshLogicalSnapshot(); browser.update(input); input.update();
        input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_RELEASE);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (browser.loading()) { input.refreshLogicalSnapshot(); browser.update(input); input.update(); Thread.onSpinWait(); }
        });
        List<String> text = new ArrayList<>();
        browser.render(new PixelFont() {
            @Override public void drawText(String value, int x, int y, float scale, float r, float g, float b, float a) { text.add(value); }
        }, 320);
        assertTrue(text.contains("~"));
        assertTrue(text.contains("Home"));
        assertTrue(text.contains("Downloads"));
        assertTrue(text.contains("Last folder"));
        browser.cancel();
    }

    @Test
    void externalPathsUseFriendlyBreadcrumbs() {
        assertEquals("Temporary files / games", MenuPathBrowser.friendlyPath(Path.of("/tmp/games")));
        assertEquals("Temporary files", MenuPathBrowser.friendlyPath(Path.of("/tmp")));
        assertEquals("cartridges / games", MenuPathBrowser.friendlyPath(Path.of("/mnt/cartridges/games")));
        assertEquals("Computer", MenuPathBrowser.friendlyPath(Path.of("/")));
    }

    private InputHandler input() {
        return new InputHandler(InputBindingFactory.supplier(SonicConfigurationService.createStandalone(directory)), List::of);
    }
}
