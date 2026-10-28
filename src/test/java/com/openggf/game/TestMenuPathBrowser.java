package com.openggf.game;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.graphics.MenuPixelFont;
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
                assertTrue(x + MenuPixelFont.textWidth(value, scale) <= 320);
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
        browser.pointer(20, 91, true, true, false, 0);
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

    @Test
    void breadcrumbsKeepTheAnchorAndNearestFoldersAndElideNamesInTheMiddle() {
        Path home = Path.of(System.getProperty("user.home"));
        assertEquals("~ / Downloads", MenuPathBrowser.breadcrumb(home.resolve("Downloads"), 276));
        Path deep = home.resolve("Documents/preservation/console/roms/Sonic");
        assertEquals("~ / ... / console / roms / Sonic", MenuPathBrowser.breadcrumb(deep, 210));
        Path generated = Path.of("/tmp/master-title-capture12345678901234567890");
        String shown = MenuPathBrowser.breadcrumb(generated, 276);
        assertTrue(shown.startsWith("Temporary files / master-title-"), shown);
        assertTrue(shown.contains("..."), shown);
        assertTrue(shown.endsWith("7890"), shown);
        for (int width = 0; width <= 400; width++) {
            assertTrue(MenuChrome.compactWidth(MenuPathBrowser.breadcrumb(generated, width)) <= width);
            assertTrue(MenuChrome.compactWidth(MenuPathBrowser.breadcrumb(deep, width)) <= width);
        }
        assertEquals("", MenuPathBrowser.breadcrumb(null, 276));
        assertEquals("Computer", MenuPathBrowser.breadcrumb(Path.of("/"), 276));
    }

    @Test
    void emptyFolderExplainsNavigationAndCountsOnlyItsContents() throws Exception {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        var browser = new MenuPathBrowser(empty.toString(), true, "LOCATE", "Choose a file");
        InputHandler input = input();
        awaitLoad(browser, input);
        for (int width : List.of(320, 400)) {
            List<Drawn> lines = render(browser, width);
            assertTrue(lines.stream().anyMatch(line -> line.text().equals("0 ITEMS")));
            assertTrue(lines.stream().anyMatch(line -> line.text().equals("Folder is empty")));
            assertTrue(lines.stream().anyMatch(line -> line.text().equals("Parent folder")));
            assertTrue(lines.stream().anyMatch(line -> line.text().equals("PLACES")));
            assertTrue(lines.stream().anyMatch(line -> line.text().equals("DETAILS")));
            assertTrue(lines.stream().anyMatch(line -> line.text().equals("OPEN")));
        }
        // The location and blank list area are not controls.
        browser.pointer(30, 40, true, true, false, 0);
        input.refreshLogicalSnapshot(); browser.update(input); input.update();
        browser.pointer(30, 140, true, true, false, 0);
        input.refreshLogicalSnapshot(); browser.update(input); input.update();
        assertNull(browser.selected());
        assertFalse(browser.loading());
        browser.pointer(30, 77, true, true, false, 0);
        input.refreshLogicalSnapshot(); browser.update(input); input.update();
        awaitLoad(browser, input);
        assertTrue(render(browser, 320).stream().anyMatch(line -> line.text().equals("empty")));
        browser.cancel();
    }

    @Test
    void romTagsAreHintsAndOtherFilesRemainSelectable() throws Exception {
        Path files = Files.createDirectory(directory.resolve("files"));
        Files.writeString(files.resolve("cartridge.GEN"), "candidate");
        Path other = Files.writeString(files.resolve("cartridge.backup"), "also selectable");
        var browser = new MenuPathBrowser(files.toString(), true, "LOCATE", "Choose any file");
        InputHandler input = input();
        awaitLoad(browser, input);
        List<Drawn> lines = render(browser, 320);
        assertTrue(lines.stream().anyMatch(line -> line.text().equals("2 ITEMS")));
        assertTrue(lines.stream().anyMatch(line -> line.text().equals("ROM?")));
        assertTrue(lines.stream().anyMatch(line -> line.text().equals("cartridge.backup")));
        assertFalse(lines.stream().anyMatch(line -> line.text().equals("Folder is empty")));
        browser.pointer(30, 105, true, false, false, 0);
        input.refreshLogicalSnapshot(); browser.update(input); input.update();
        assertTrue(render(browser, 320).stream().anyMatch(line -> line.text().equals("SELECT")));
        browser.pointer(30, 105, true, true, false, 0);
        input.refreshLogicalSnapshot(); browser.update(input); input.update();
        assertEquals(other.toString(), browser.selected());
    }

    @Test
    void pagedRowsKeepTheirPointerTargetsAndCountAtWideWidths() throws Exception {
        Path files = Files.createDirectory(directory.resolve("many"));
        for (int i = 0; i < 13; i++) Files.writeString(files.resolve("file%02d.gen".formatted(i)), "candidate");
        var browser = new MenuPathBrowser(files.toString(), true, "LOCATE", "Choose a file");
        InputHandler input = input();
        awaitLoad(browser, input);
        render(browser, 400);
        browser.pointer(30, 40, false, false, false, -8);
        input.refreshLogicalSnapshot(); browser.update(input); input.update();
        List<Drawn> lines = render(browser, 400);
        assertTrue(lines.stream().anyMatch(line -> line.text().equals("13 ITEMS")));
        assertEquals("file06.gen", lines.stream().filter(line -> line.y() == 74 && line.x() == 27).findFirst().orElseThrow().text());
        // The scrollbar sits outside the row hit area.
        browser.pointer(385, 77, true, true, false, 0);
        input.refreshLogicalSnapshot(); browser.update(input); input.update();
        assertNull(browser.selected());
        browser.pointer(370, 77, true, true, false, 0);
        input.refreshLogicalSnapshot(); browser.update(input); input.update();
        assertEquals(files.resolve("file06.gen").toString(), browser.selected());
    }

    @Test
    void folderPickerKeepsUseThisFolderAndReportsFilteredEmptyState() throws Exception {
        Path files = Files.createDirectory(directory.resolve("no-subfolders"));
        Files.writeString(files.resolve("readme.txt"), "not a subfolder");
        var browser = MenuPathBrowser.folder(files.toString(), "FOLDER", "Choose a folder");
        InputHandler input = input();
        awaitLoad(browser, input);
        List<Drawn> lines = render(browser, 320);
        assertTrue(lines.stream().anyMatch(line -> line.text().equals("Use this folder")));
        assertTrue(lines.stream().anyMatch(line -> line.text().equals("No subfolders")));
        assertTrue(lines.stream().anyMatch(line -> line.text().equals("0 ITEMS")));
        assertFalse(lines.stream().anyMatch(line -> line.text().equals("readme.txt")));
        browser.pointer(30, 77, true, true, false, 0);
        input.refreshLogicalSnapshot(); browser.update(input); input.update();
        assertEquals(files.toString(), browser.selected());
    }

    @Test
    void unavailablePlaceKeepsItsSpecificErrorInTheEmptyState() throws Exception {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        var browser = new MenuPathBrowser(empty.toString(), true, "LOCATE", "Choose a file");
        InputHandler input = input();
        awaitLoad(browser, input);
        String originalHome = System.getProperty("user.home");
        try {
            // This home has no Downloads directory; no dependence on the developer's folders.
            System.setProperty("user.home", directory.toString());
            browser.pointer(140, 194, true, true, false, 0);
            input.refreshLogicalSnapshot(); browser.update(input); input.update();
            List<Drawn> lines = render(browser, 320);
            assertTrue(lines.stream().anyMatch(line -> line.text().equals("Folder is not available") && line.y() == 119));
            assertFalse(lines.stream().anyMatch(line -> line.text().equals("Cannot read folder")));
            assertFalse(browser.loading());
            assertNull(browser.selected());
        } finally {
            System.setProperty("user.home", originalHome);
            browser.cancel();
        }
    }

    private record Drawn(String text, int x, int y) { }

    private List<Drawn> render(MenuPathBrowser browser, int width) {
        List<Drawn> lines = new ArrayList<>();
        browser.render(new PixelFont() {
            @Override public void drawText(String value, int x, int y, float scale, float r, float g, float b, float a) {
                lines.add(new Drawn(value, x, y));
                assertTrue(x >= 0 && x + MenuPixelFont.textWidth(value, scale) <= width, value);
                assertTrue(y >= 0 && y + (scale < 1 ? 8 : 10) <= 224, value);
                assertTrue(value.chars().allMatch(c -> c >= 32 && c <= 126), value);
            }
        }, width);
        return lines;
    }

    private void awaitLoad(MenuPathBrowser browser, InputHandler input) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (browser.loading()) { input.refreshLogicalSnapshot(); browser.update(input); input.update(); Thread.onSpinWait(); }
        });
    }

    private InputHandler input() {
        return new InputHandler(InputBindingFactory.supplier(SonicConfigurationService.createStandalone(directory)), List::of);
    }
}
