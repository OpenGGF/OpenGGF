package com.openggf.game;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.graphics.PixelFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class TestMenuRomLocator {
    @TempDir Path directory;

    @Test
    void ownerCancellationCannotPublishOrSaveAfterTheMenuCloses() {
        var config = SonicConfigurationService.createStandalone(directory);
        String original = config.getString(SonicConfiguration.SONIC_1_ROM);
        var locator = new MenuRomLocator(MasterTitleScreen.GameEntry.SONIC_1, config);
        locator.cancel();
        locator.cancel();
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), List::of);
        locator.pointer(20, 91, true, true, false, 0);
        input.refreshLogicalSnapshot(); locator.update(input);
        assertTrue(locator.cancelled());
        assertFalse(locator.completed());
        assertEquals(original, config.getString(SonicConfiguration.SONIC_1_ROM));
    }

    @Test
    void identifiesContentRegardlessOfFilenameAndRejectsWrongGameAndFolders() throws IOException {
        Path image = image("my cartridge.backup", 0x80000, "SONIC THE HEDGEHOG");
        assertEquals(image, MenuRomLocator.validate(MasterTitleScreen.GameEntry.SONIC_1, image));
        assertThrows(MenuRomLocator.IncompatibleImageException.class, () -> MenuRomLocator.validate(MasterTitleScreen.GameEntry.SONIC_2, image));
        var missing = assertThrows(IOException.class, () -> MenuRomLocator.validate(MasterTitleScreen.GameEntry.SONIC_1, directory.resolve("missing")));
        assertFalse(missing instanceof MenuRomLocator.IncompatibleImageException);
        assertThrows(IOException.class, () -> MenuRomLocator.validate(MasterTitleScreen.GameEntry.SONIC_1, directory));
    }

    @Test
    void acceptsLogicalRomInsideLockOnImage() throws IOException {
        Path image = image("cartridge.bin", 0x280000, "SONIC & KNUCKLES");
        byte[] bytes = Files.readAllBytes(image);
        putHeader(bytes, 0x200000, "SONIC THE HEDGEHOG");
        Files.write(image, bytes);
        assertEquals(image, MenuRomLocator.validate(MasterTitleScreen.GameEntry.SONIC_1, image));
        assertThrows(IOException.class, () -> MenuRomLocator.validate(MasterTitleScreen.GameEntry.SONIC_3K, image));
    }

    @Test
    void mouseSelectionSavesValidatedLocationAndCancellationLeavesSettingsAlone() throws IOException {
        Path roms = Files.createDirectory(directory.resolve("roms"));
        Path image = image("my cartridge.backup", 0x80000, "SONIC THE HEDGEHOG");
        image = Files.move(image, roms.resolve(image.getFileName()));
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.SONIC_1_ROM, roms.toString());
        var locator = new MenuRomLocator(MasterTitleScreen.GameEntry.SONIC_1, config);
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), List::of);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (!locator.completed()) {
                // File-only picker: parent folder first, then the only image.
                locator.pointer(20, 91, true, true, false, 0);
                input.refreshLogicalSnapshot(); locator.update(input); input.update();
                Thread.onSpinWait();
            }
        });
        assertEquals(image.toString(), config.getString(SonicConfiguration.SONIC_1_ROM));
        assertEquals(image.toString(), SonicConfigurationService.createStandalone(directory).getString(SonicConfiguration.SONIC_1_ROM));
        var cancelled = new MenuRomLocator(MasterTitleScreen.GameEntry.SONIC_2, config);
        String original = config.getString(SonicConfiguration.SONIC_2_ROM);
        cancelled.pointer(0, 0, false, false, true, 0);
        input.refreshLogicalSnapshot(); cancelled.update(input);
        assertTrue(cancelled.cancelled());
        assertEquals(original, config.getString(SonicConfiguration.SONIC_2_ROM));
    }

    @Test
    void rejectedImageUsesSharedPageAndRetryAndFooterHitTargets() throws Exception {
        Path roms = Files.createDirectory(directory.resolve("roms"));
        Path wrongGame = image("my cartridge.backup", 0x80000, "SONIC THE HEDGEHOG");
        Files.move(wrongGame, roms.resolve(wrongGame.getFileName()));
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.SONIC_2_ROM, roms.toString());
        var locator = new MenuRomLocator(MasterTitleScreen.GameEntry.SONIC_2, config);
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), List::of);
        List<String> text = new ArrayList<>();
        PixelFont font = new PixelFont() {
            @Override public void drawText(String value, int x, int y, float scale, float r, float g, float b, float a) {
                text.add(value);
                assertTrue(x >= 0 && x + value.length() * (scale < 1 ? 6 : 9) <= 320, value);
                assertTrue(y >= 0 && y + (scale < 1 ? 8 : 10) <= 224, value);
            }
        };
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (!text.contains("Choose another file")) {
                locator.pointer(30, 91, true, true, false, 0);
                input.refreshLogicalSnapshot(); locator.update(input); input.update();
                text.clear(); locator.render(font, 320);
                Thread.onSpinWait();
            }
        });
        assertTrue(text.contains("LOCATE SONIC 2"));
        assertTrue(text.contains("This file does not contain Sonic 2."));
        assertTrue(text.contains("my cartridge.backup"));
        assertTrue(text.contains("RETRY"));
        assertTrue(text.contains("BACK"));
        assertFalse(locator.completed());
        // Retry's drawn button and pointer region agree.
        locator.pointer(30, 140, true, true, false, 0);
        input.refreshLogicalSnapshot(); locator.update(input); input.update();
        text.clear(); locator.render(font, 320);
        assertTrue(text.contains("CONTENTS"));
        // The whole shared footer, starting at y=205, is a Back target.
        locator.pointer(20, MenuChrome.FOOTER_Y, true, true, false, 0);
        input.refreshLogicalSnapshot(); locator.update(input); input.update();
        assertTrue(locator.cancelled());
    }

    private Path image(String name, int size, String title) throws IOException {
        byte[] bytes = new byte[size];
        putHeader(bytes, 0, title);
        return Files.write(directory.resolve(name), bytes);
    }

    private static void putHeader(byte[] bytes, int base, String title) {
        byte[] header = title.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, bytes, base + 0x120, header.length);
    }
}
