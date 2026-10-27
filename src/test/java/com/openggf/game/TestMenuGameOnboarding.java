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
        assertTrue(result.notice().contains("Scan limit reached"));
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
        page.pointer(50, 150, true, true, false, 0); tick(page, input); // Scan a folder.
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
        page.pointer(60, 170, true, true, false, 0); tick(page, input);
        assertTrue(page.completed());
    }

    @Test void cancellationCannotSaveAfterOwnerCloses() {
        var config = SonicConfigurationService.createStandalone(directory);
        String original = config.getString(SonicConfiguration.SONIC_1_ROM);
        var page = new MenuGameOnboarding(config);
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), List::of);
        page.pointer(50, 110, true, true, false, 0); tick(page, input);
        page.cancel(); page.cancel();
        page.pointer(30, 73, true, true, false, 0); tick(page, input);
        assertTrue(page.cancelled());
        assertFalse(page.completed());
        assertEquals(original, config.getString(SonicConfiguration.SONIC_1_ROM));
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
