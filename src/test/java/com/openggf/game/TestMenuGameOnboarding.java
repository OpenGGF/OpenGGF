package com.openggf.game;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.RomGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestMenuGameOnboarding {
    @TempDir Path directory;

    @Test void scanningRecognizesAllThreeByContentsIncludingNestedAndUnusualFilenames() throws Exception {
        Path one = image(directory, "renamed.backup", 0x80000, "SONIC THE HEDGEHOG");
        Path nested = Files.createDirectory(directory.resolve("nested"));
        Path two = image(nested, "two.gen", 0x100000, "SONIC THE HEDGEHOG 2");
        Path three = image(nested, "three.bin", 0x400000, "SONIC & KNUCKLES");
        byte[] bytes = Files.readAllBytes(three);
        putHeader(bytes, 0x200000, "SONIC THE HEDGEHOG 3");
        Files.write(three, bytes);
        Files.writeString(directory.resolve("unrelated.txt"), "not a game");
        assertEquals(java.util.Map.of(RomGame.S1, one, RomGame.S2, two, RomGame.S3K, three),
                MenuGameOnboarding.discover(directory, true).games());
        assertEquals(java.util.Map.of(RomGame.S1, one), MenuGameOnboarding.discover(one, false).games());
        assertTrue(MenuGameOnboarding.discover(directory.resolve("unrelated.txt"), false).games().isEmpty());
    }

    @Test void boundedScanPreservesGamesAndReportsIncompleteCoverage() throws Exception {
        // Every file is recognized, so traversal order cannot affect the partial result.
        for (int i = 0; i < 3; i++) image(directory, "one" + i, 0x80000, "SONIC THE HEDGEHOG");
        var result = MenuGameOnboarding.discover(directory, true, 2);
        assertEquals(java.util.Set.of(RomGame.S1), result.games().keySet());
        assertTrue(result.limitReached());
        assertFalse(result.unreadable());
        assertEquals("Stopped early; scan the folder with the rest", result.notice());
    }

    @Test void noticeSaysOnlyWhatTheScanMissedForAllSomeAndNoGames() {
        var all = java.util.Map.of(RomGame.S1, directory, RomGame.S2, directory, RomGame.S3K, directory);
        assertEquals(RomGame.values().length, all.size(), "fixture covers every supported game");
        var some = java.util.Map.of(RomGame.S2, directory);
        java.util.Map<RomGame, Path> none = new java.util.EnumMap<>(RomGame.class);
        for (boolean limit : new boolean[] {false, true}) for (boolean unreadable : new boolean[] {false, true})
            assertNull(new MenuGameOnboarding.DiscoveryResult(all, limit, unreadable).notice(),
                    "every game found: nothing skipped can matter (limit " + limit + ", unreadable " + unreadable + ")");
        assertNull(new MenuGameOnboarding.DiscoveryResult(some, false, false).notice());
        assertNull(new MenuGameOnboarding.DiscoveryResult(none, false, false).notice());
        assertEquals("Stopped early; scan the folder with the rest",
                new MenuGameOnboarding.DiscoveryResult(some, true, false).notice());
        assertEquals("Stopped early; scan the folder that holds them",
                new MenuGameOnboarding.DiscoveryResult(none, true, false).notice());
        assertEquals("Skipped files that could not be read; check access to them",
                new MenuGameOnboarding.DiscoveryResult(some, false, true).notice());
        assertEquals("Some files could not be read; check access to them",
                new MenuGameOnboarding.DiscoveryResult(none, false, true).notice());
        assertEquals("Stopped early, skipped unreadable files; scan the rest",
                new MenuGameOnboarding.DiscoveryResult(some, true, true).notice());
        assertEquals("Stopped early, skipped unreadable files; scan their folder",
                new MenuGameOnboarding.DiscoveryResult(none, true, true).notice());
        for (var games : List.of(some, none)) for (boolean limit : new boolean[] {false, true}) for (boolean unreadable : new boolean[] {false, true}) {
            String notice = new MenuGameOnboarding.DiscoveryResult(games, limit, unreadable).notice();
            if (notice != null) assertTrue(MenuChrome.compactWidth(notice) <= 300, "fits whole at 320: " + notice);
        }
    }

    @Test void depthLimitIsReportedAndMissingFileIsAnAccessFailure() throws Exception {
        Path nested = directory;
        for (int i = 0; i < 9; i++) nested = Files.createDirectory(nested.resolve("nested"));
        image(nested, "one", 0x80000, "SONIC THE HEDGEHOG");
        var result = MenuGameOnboarding.discover(directory, true);
        assertTrue(result.limitReached());
        assertTrue(result.games().isEmpty());
        assertThrows(IOException.class, () -> MenuGameOnboarding.discover(directory.resolve("missing"), false));
    }

    @Test void scanSavesRecognizedGamesAndContinuesOnlyAfterAcknowledgement() throws IOException {
        Path roms = Files.createDirectory(directory.resolve("games"));
        Path one = image(roms, "one.gen", 0x80000, "SONIC THE HEDGEHOG");
        var config = SonicConfigurationService.createStandalone(directory.resolve("settings"));
        config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, roms.toString());
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), List::of);
        var page = new MenuGameOnboarding(config);
        int[] scan = centre(page.layout().choices().get(1));
        page.pointer(scan[0], scan[1], true, true, false, 0); tick(page, input); // Scan a folder, as drawn.
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (!one.toString().equals(config.getString(SonicConfiguration.SONIC_1_ROM))) {
                // The browser's first row ("Use this folder") spans y 70-84 below the location panel.
                page.pointer(30, 76, true, true, false, 0); tick(page, input);
                Thread.onSpinWait();
            }
        });
        assertFalse(page.completed());
        var reloaded = SonicConfigurationService.createStandalone(directory.resolve("settings"));
        assertEquals(one.toString(), reloaded.getString(SonicConfiguration.SONIC_1_ROM));
        int[] next = centre(page.layout().button());
        page.pointer(next[0], next[1], true, true, false, 0); tick(page, input);
        assertTrue(page.completed());
    }

    @Test void cancellationCannotSaveAfterOwnerCloses() {
        var config = SonicConfigurationService.createStandalone(directory);
        String original = config.getString(SonicConfiguration.SONIC_1_ROM);
        var page = new MenuGameOnboarding(config);
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), List::of);
        int[] locate = centre(page.layout().choices().get(0));
        page.pointer(locate[0], locate[1], true, true, false, 0); tick(page, input);
        page.cancel(); page.cancel();
        page.pointer(30, 73, true, true, false, 0); tick(page, input);
        assertTrue(page.cancelled());
        assertFalse(page.completed());
        assertEquals(original, config.getString(SonicConfiguration.SONIC_1_ROM));
    }

    @Test void foundGamesShareOneColumnAndEveryStateIsCentredBetweenHeaderAndFooter() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory.resolve("settings"));
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), List::of);
        for (int width : new int[] {320, 400}) {
            var page = new MenuGameOnboarding(config);
            var rec = new TestHubDialogGeometry.Recorder();
            // The two choices: centred, and the pointer hits the drawn frames.
            page.render(rec.font, width);
            var intro = page.layout();
            assertCentred(rec, intro.top(), intro.choices().get(1).y() + intro.choices().get(1).h() + 2, width);
            for (var box : intro.choices()) assertTrue(rec.fills.stream().anyMatch(f -> f.x() == box.x() && f.y() == box.y()
                    && f.w() == box.w() && f.h() == box.h()), "choice drawn where it is hit: " + box);
            // A message and a scan notice join the group, which stays centred.
            set(page, "message", "No supported games found.");
            set(page, "scanNotice", "Stopped early; scan the folder that holds them");
            rec.clear(); page.render(rec.font, width);
            var withNotice = page.layout();
            assertCentred(rec, withNotice.top(), withNotice.noticeY() + 8, width);
            assertEquals(10, withNotice.noticeY() - withNotice.messageY());
            // Three found games and a notice: one column for checks, badges and names.
            set(page, "found", new java.util.EnumMap<>(java.util.Map.of(RomGame.S1, directory, RomGame.S2, directory,
                    RomGame.S3K, directory)));
            set(page, "message", "3 games added");
            rec.clear(); page.render(rec.font, width);
            var found = page.layout();
            var names = rec.texts(t -> t.text().startsWith("Sonic"));
            assertEquals(3, names.size());
            assertEquals(1, names.stream().map(TestHubDialogGeometry.Text::x).distinct().count(), "names share one column: " + names);
            var badges = rec.fills(f -> f.h() == 10 && f.w() > 10 && f.w() < 30 && f.y() < MenuChrome.FOOTER_Y);
            assertEquals(3, badges.size(), "three badges");
            assertEquals(1, badges.stream().map(TestHubDialogGeometry.Fill::w).distinct().count(), "badges match: " + badges);
            assertEquals(1, badges.stream().map(TestHubDialogGeometry.Fill::x).distinct().count());
            for (int i = 1; i < names.size(); i++) assertEquals(15, names.get(i).y() - names.get(i - 1).y());
            var button = found.button();
            assertCentred(rec, found.top(), button.y() + button.h() + 2, width);
            assertEquals(9, button.y() - 2 - (found.noticeY() + 8), "nine clear rows from the notice to Continue's frame");
            assertTrue(rec.fills.stream().anyMatch(f -> f.x() == button.x() && f.y() == button.y() && f.w() == button.w()
                    && f.h() == 20), "Continue is a 20-row button drawn where it is hit");
            // One found game, no notice: the group shrinks and stays centred.
            set(page, "found", new java.util.EnumMap<>(java.util.Map.of(RomGame.S2, directory)));
            set(page, "scanNotice", null);
            rec.clear(); page.render(rec.font, width);
            var one = page.layout();
            assertCentred(rec, one.top(), one.button().y() + one.button().h() + 2, width);
            assertEquals(9, one.button().y() - 2 - (one.listY() + 9), "nine clear rows from the last badge to Continue's frame");
            int[] next = centre(one.button());
            page.pointer(next[0], next[1], true, true, false, 0); tick(page, input);
            assertTrue(page.completed(), "Continue is the drawn button at " + width);
        }
    }

    @Test void checkingAndScanningKeepOneCentredGroup() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory.resolve("settings"));
        var page = new MenuGameOnboarding(config);
        var gate = new java.util.concurrent.CountDownLatch(1);
        set(page, "reading", directory);
        set(page, "discovery", new MenuLoadTask<MenuGameOnboarding.DiscoveryResult>(() -> { gate.await(); return null; }));
        try {
            for (int width : new int[] {320, 400}) for (boolean scanning : new boolean[] {false, true}) {
                set(page, "scanning", scanning);
                var rec = new TestHubDialogGeometry.Recorder();
                page.render(rec.font, width);
                var top = page.layout().top();
                var status = rec.texts(t -> t.text().endsWith("...")).getFirst();
                var note = rec.texts(t -> t.text().startsWith("Your files")).getFirst();
                assertEquals(top + MenuChrome.ICON_SIZE + 6, status.y(), "six clear rows under the icon");
                assertCentred(rec, top, note.y() + 8, width);
                var meter = rec.fills(f -> f.h() == 2 && f.w() == 120).getFirst();
                assertEquals(6, meter.y() - (status.y() + 12 + 7) - 1, "path to meter");
                assertEquals(8, note.y() - (meter.y() + 2), "meter to the note");
            }
        } finally {
            gate.countDown();
            page.cancel();
        }
    }

    private static void assertCentred(TestHubDialogGeometry.Recorder rec, int top, int end, int width) {
        int above = top - MenuChrome.HEADER_H, below = MenuChrome.FOOTER_Y - end;
        assertTrue(Math.abs(above - below) <= 1, "centred between header and footer at " + width + ": " + above + "/" + below);
        for (var text : rec.texts) {
            if (text.y() >= MenuChrome.FOOTER_Y || text.y() < MenuChrome.HEADER_H) continue;
            assertTrue(text.y() >= top && text.y() + 7 < end, "inside the group: " + text);
        }
    }

    private static void set(MenuGameOnboarding page, String field, Object value) throws Exception {
        var f = MenuGameOnboarding.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(page, value);
    }

    private static int[] centre(MenuGameOnboarding.Box box) {
        return new int[] {box.x() + box.w() / 2, box.y() + box.h() / 2};
    }

    @Test void scanOutcomeReaderKeepsRecoveryTailsAndCannotCompleteOrOpenTheBrowser() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        var page = new MenuGameOnboarding(config);
        var message = MenuGameOnboarding.class.getDeclaredField("message"); message.setAccessible(true);
        message.set(page, "Scan outcome ".repeat(35) + "OUTCOME_SENTINEL");
        var notice = MenuGameOnboarding.class.getDeclaredField("scanNotice"); notice.setAccessible(true);
        notice.set(page, "Access recovery guidance ".repeat(35) + "RECOVERY_SENTINEL");
        var draws = new java.util.ArrayList<String>();
        var font = new com.openggf.graphics.PixelFont() {
            @Override public void drawText(String text, int x, int y, float scale, float r, float g, float b, float a) { draws.add(text); }
        };
        page.render(font, 320);
        assertTrue(draws.contains("F1"));
        draws.clear();
        InputHandler open = new InputHandler(); open.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_F1, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        tick(page, open);
        for (int i = 0; i < 40; i++) {
            page.render(font, 320);
            InputHandler next = new InputHandler(); next.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, org.lwjgl.glfw.GLFW.GLFW_PRESS);
            tick(page, next);
        }
        assertTrue(draws.stream().anyMatch(text -> text.contains("OUTCOME_SENTINEL")));
        assertTrue(draws.stream().anyMatch(text -> text.contains("RECOVERY_SENTINEL")));
        assertFalse(page.completed()); assertFalse(page.cancelled());
        InputHandler close = new InputHandler(); close.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        tick(page, close);
        assertFalse(page.completed()); assertFalse(page.cancelled());
        var browser = MenuGameOnboarding.class.getDeclaredField("browser"); browser.setAccessible(true);
        assertNull(browser.get(page), "Confirm while reading must not choose the focused locate/scan action");
        page.cancel();
    }

    private static void tick(MenuGameOnboarding page, InputHandler input) {
        input.refreshLogicalSnapshot(); page.update(input); input.update();
    }
    private static Path image(Path folder, String name, int size, String title) throws IOException {
        byte[] bytes = new byte[size]; putHeader(bytes, 0, title);
        return Files.write(folder.resolve(name), bytes).toAbsolutePath().normalize();
    }
    private static void putHeader(byte[] bytes, int offset, String title) {
        byte[] header = title.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, bytes, offset + 0x120, header.length);
    }
}
