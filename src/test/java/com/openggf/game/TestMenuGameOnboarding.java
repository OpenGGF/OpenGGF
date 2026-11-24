package com.openggf.game;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.MasterTitleScreen.GameEntry;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.game.soniccd.CdReleaseVariant;
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

    @Test void findsCueAndIgnoresUnknownFiles() throws Exception {
        Path cue = directory.resolve("game.cue");
        var usa = CdReleaseVariant.USA;
        byte[] frames = new byte[35 * 2352];
        java.util.Arrays.fill(frames, 1, 11, (byte) 0xff);
        frames[15] = 1; // MODE1/2352 sync and mode bytes in the data sector.
        for (var field : java.util.Map.of(16, usa.discHeaderVolumeId(), 0x50, usa.sourceBuildDate(),
                0x180, usa.productId(), 0x1f0, usa.headerMarket(), 0, "SEGADISCSYSTEM").entrySet()) {
            byte[] value = field.getValue().getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(value, 0, frames, 16 + field.getKey(), value.length);
        }
        Files.write(directory.resolve("tracks.bin"), frames);
        var sheet = new StringBuilder("FILE \"tracks.bin\" BINARY\n  TRACK 01 MODE1/2352\n    INDEX 01 00:00:00\n");
        for (int track = 2; track <= 35; track++)
            sheet.append(String.format(java.util.Locale.ROOT, "  TRACK %02d AUDIO%n    INDEX 01 00:00:%02d%n",
                    track, track - 1));
        Files.writeString(cue, sheet);
        Files.writeString(directory.resolve("unknown.bin"), "not a game");
        Path unknown = Files.writeString(directory.resolve("unknown.chd"), "not a disc");
        assertTrue(MenuGameOnboarding.discover(unknown, false).games().isEmpty());
        Path one = image(directory, "one.gen", 0x80000, "SONIC THE HEDGEHOG");
        var config = scanAndReload(directory, false);
        assertEquals(cue.toString(), config.getString(SonicConfiguration.SONIC_CD_DISC));
        assertEquals(one.toString(), config.getString(SonicConfiguration.SONIC_1_ROM));
    }

    @Test void identifiesRealDiscAtItsExistingPath() throws Exception {
        Path disc = CdTestMedia.usaCue().toAbsolutePath().normalize();
        var result = MenuGameOnboarding.discover(disc, false);
        assertEquals(List.of(disc), result.games().get(GameEntry.SONIC_CD));
        assertEquals(disc.toString(), result.settings().get(SonicConfiguration.SONIC_CD_DISC));
    }

    @Test void splitPairInDifferentSubfoldersIsStillLaunchableAfterReload() throws Exception {
        Path folder = Files.createDirectory(directory.resolve("games"));
        image(folder, "one.gen", 0x80000, "SONIC THE HEDGEHOG");
        Path three = image(Files.createDirectory(folder.resolve("three")), "cartridge.backup",
                0x200000, "SONIC THE HEDGEHOG 3");
        Path knuckles = image(Files.createDirectory(folder.resolve("knuckles")), "cartridge.backup",
                0x200000, "SONIC & KNUCKLES");
        var config = scanAndReload(folder, false);
        var resolved = RomImageCatalogue.forCurrentWorkingDirectory(config).resolve(RomIdentity.S3K).orElseThrow();
        assertTrue(resolved.isComposite());
        assertEquals(folder.toString(), config.getString(SonicConfiguration.ROMS_DIRECTORY));
        assertEquals(List.of(knuckles, three), resolved.parts().stream().map(part -> part.image().path()).toList());
    }

    @Test void tierTwoDumpRemainsAvailableWithASeparateSonic2BaseRom() throws Exception {
        Path folder = Files.createDirectory(directory.resolve("games"));
        image(folder, "one.gen", 0x80000, "SONIC THE HEDGEHOG");
        Path two = image(folder, "two.gen", 0x100000, "SONIC THE HEDGEHOG 2");
        Path kis2 = Files.createDirectory(folder.resolve("lock-on")).resolve("combined.backup");
        var bytes = new byte[0x340000];
        putHeader(bytes, 0, "SONIC & KNUCKLES");
        putHeader(bytes, 0x200000, "SONIC THE HEDGEHOG 2");
        Files.write(kis2, bytes);
        var config = scanAndReload(folder, true);
        config.setConfigValue(SonicConfiguration.SONIC_2_ROM, two.toString());
        var catalogue = RomImageCatalogue.forCurrentWorkingDirectory(config);
        assertEquals(two, catalogue.resolve(RomIdentity.S2).orElseThrow().wholeImagePath().orElseThrow());
        assertEquals(kis2, catalogue.resolve(RomIdentity.KIS2).orElseThrow().wholeImagePath().orElseThrow());
    }

    @Test void recognizesEachRetailRevisionThroughTheSameCartridgeMatcher() throws Exception {
        for (GameEntry game : List.of(GameEntry.SONIC_1, GameEntry.SONIC_2)) {
            for (String suffix : List.of(".rev00.rom.path", ".rom.path")) {
                String property = game == GameEntry.SONIC_1 ? "sonic1" : "sonic2";
                String input = System.getProperty(property + suffix);
                org.junit.jupiter.api.Assumptions.assumeTrue(input != null, "requires " + property + suffix);
                Path path = Path.of(input).toAbsolutePath().normalize();
                assertEquals(java.util.Map.of(game, List.of(path)), MenuGameOnboarding.discover(path, false).games());
            }
        }
    }

    private SonicConfigurationService scanAndReload(Path folder, boolean preferComposite) {
        Path settings = directory.resolve("settings");
        var config = SonicConfigurationService.createStandalone(settings);
        config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, folder.toString());
        config.setConfigValue(SonicConfiguration.ROMS_PREFER_COMPOSITE, preferComposite);
        var input = new InputHandler(InputBindingFactory.supplier(config), List::of);
        var page = new MenuGameOnboarding(config);
        int[] scan = centre(page.layout().choices().get(1));
        page.pointer(scan[0], scan[1], true, true, false, 0);
        tick(page, input);
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                while (page.layout().button() == null) {
                    page.pointer(30, 76, true, true, false, 0);
                    tick(page, input);
                    Thread.sleep(5);
                }
            });
        } finally {
            page.cancel();
        }
        return SonicConfigurationService.createStandalone(settings);
    }

    @Test void scanningRecognizesCartridgesByContentsIncludingNestedAndUnusualFilenames() throws Exception {
        Path one = image(directory, "renamed.backup", 0x80000, "SONIC THE HEDGEHOG");
        Path nested = Files.createDirectory(directory.resolve("nested"));
        Path two = image(nested, "two.gen", 0x100000, "SONIC THE HEDGEHOG 2");
        Path three = image(nested, "three.bin", 0x400000, "SONIC & KNUCKLES");
        byte[] bytes = Files.readAllBytes(three);
        putHeader(bytes, 0x200000, "SONIC THE HEDGEHOG 3");
        Files.write(three, bytes);
        Files.writeString(directory.resolve("unrelated.txt"), "not a game");
        assertEquals(java.util.Map.of(GameEntry.SONIC_1, List.of(one), GameEntry.SONIC_2, List.of(two), GameEntry.SONIC_3K, List.of(three)),
                MenuGameOnboarding.discover(directory, true).games());
        assertEquals(java.util.Map.of(GameEntry.SONIC_1, List.of(one)), MenuGameOnboarding.discover(one, false).games());
        assertTrue(MenuGameOnboarding.discover(directory.resolve("unrelated.txt"), false).games().isEmpty());
    }

    @Test void boundedScanPreservesGamesAndReportsIncompleteCoverage() throws Exception {
        // Every file is recognized, so traversal order cannot affect the partial result.
        for (int i = 0; i < 3; i++) image(directory, "one" + i, 0x80000, "SONIC THE HEDGEHOG");
        var result = MenuGameOnboarding.discover(directory, true, 2);
        assertEquals(java.util.Set.of(GameEntry.SONIC_1), result.games().keySet());
        assertTrue(result.limitReached());
        assertFalse(result.unreadable());
        assertEquals("Stopped early; scan the folder with the rest", result.notice());
    }

    @Test void noticeSaysOnlyWhatTheScanMissedForAllSomeAndNoGames() {
        var all = java.util.Map.of(GameEntry.SONIC_1, List.of(directory), GameEntry.SONIC_2, List.of(directory),
                GameEntry.SONIC_3K, List.of(directory), GameEntry.SONIC_CD, List.of(directory));
        assertEquals(GameEntry.values().length, all.size(), "fixture covers every supported game");
        var some = java.util.Map.of(GameEntry.SONIC_2, List.of(directory));
        java.util.Map<GameEntry, List<Path>> none = java.util.Map.of();
        for (boolean limit : new boolean[] {false, true}) for (boolean unreadable : new boolean[] {false, true})
            assertNull(new MenuGameOnboarding.DiscoveryResult(all, java.util.Map.of(), limit, unreadable).notice(),
                    "every game found: nothing skipped can matter (limit " + limit + ", unreadable " + unreadable + ")");
        assertNull(new MenuGameOnboarding.DiscoveryResult(some, java.util.Map.of(), false, false).notice());
        assertNull(new MenuGameOnboarding.DiscoveryResult(none, java.util.Map.of(), false, false).notice());
        assertEquals("Stopped early; scan the folder with the rest",
                new MenuGameOnboarding.DiscoveryResult(some, java.util.Map.of(), true, false).notice());
        assertEquals("Stopped early; scan the folder that holds them",
                new MenuGameOnboarding.DiscoveryResult(none, java.util.Map.of(), true, false).notice());
        assertEquals("Skipped files that could not be read; check access to them",
                new MenuGameOnboarding.DiscoveryResult(some, java.util.Map.of(), false, true).notice());
        assertEquals("Some files could not be read; check access to them",
                new MenuGameOnboarding.DiscoveryResult(none, java.util.Map.of(), false, true).notice());
        assertEquals("Stopped early, skipped unreadable files; scan the rest",
                new MenuGameOnboarding.DiscoveryResult(some, java.util.Map.of(), true, true).notice());
        assertEquals("Stopped early, skipped unreadable files; scan their folder",
                new MenuGameOnboarding.DiscoveryResult(none, java.util.Map.of(), true, true).notice());
        for (var games : List.of(some, none)) for (boolean limit : new boolean[] {false, true}) for (boolean unreadable : new boolean[] {false, true}) {
            String notice = new MenuGameOnboarding.DiscoveryResult(games, java.util.Map.of(), limit, unreadable).notice();
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
            set(page, "found", new java.util.EnumMap<>(java.util.Map.of(GameEntry.SONIC_1, List.of(directory), GameEntry.SONIC_2, List.of(directory),
                    GameEntry.SONIC_3K, List.of(directory))));
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
            set(page, "found", new java.util.EnumMap<>(java.util.Map.of(GameEntry.SONIC_2, List.of(directory))));
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
