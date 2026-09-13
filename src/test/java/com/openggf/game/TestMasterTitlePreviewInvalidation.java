package com.openggf.game;

import com.openggf.configuration.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestMasterTitlePreviewInvalidation {
    @TempDir Path root;

    /** A header-shaped 512 KiB file the ROM catalogue classifies as Sonic 1; not a ROM. */
    private static byte[] sonic1Shaped(byte fill) {
        byte[] data = new byte[0x80000];
        Arrays.fill(data, fill);
        byte[] title = "SONIC THE               HEDGEHOG".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(title, 0, data, 0x120, title.length);
        return data;
    }

    @Test void unrelatedApplyRetainsDecodedPreviewAndOnlyChangedRomIsRetried() throws Exception {
        var rom = root.resolve("game.gen");
        Files.write(rom, sonic1Shaped((byte) 1));
        Files.setLastModifiedTime(rom, FileTime.fromMillis(1_000_000_000_000L));
        var config = SonicConfigurationService.createStandalone(root);
        config.setConfigValue(SonicConfiguration.SONIC_1_ROM, rom.toString());
        config.setConfigValue(SonicConfiguration.SONIC_2_ROM, root.resolve("missing2").toString());
        config.setConfigValue(SonicConfiguration.SONIC_3K_ROM, root.resolve("missing3").toString());
        config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, root.toString());
        var screen = new MasterTitleScreen(config);
        var field = MasterTitleScreen.class.getDeclaredField("renderer");
        field.setAccessible(true);
        field.set(screen, mock(com.openggf.graphics.TexturedQuadRenderer.class));
        var refresh = MasterTitleScreen.class.getDeclaredMethod("refreshRomPreviews");
        refresh.setAccessible(true);
        try (var decoder = mockStatic(MasterTitleRomPreview.class)) {
            decoder.when(() -> MasterTitleRomPreview.loadSequenceFor(MasterTitleScreen.GameEntry.SONIC_1, rom))
                    .thenReturn(Optional.empty());
            refresh.invoke(screen);
            refresh.invoke(screen);
            decoder.verify(() -> MasterTitleRomPreview.loadSequenceFor(MasterTitleScreen.GameEntry.SONIC_1, rom), times(1));
            Files.write(rom, sonic1Shaped((byte) 2));
            Files.setLastModifiedTime(rom, FileTime.fromMillis(1_000_000_005_000L));
            refresh.invoke(screen);
            decoder.verify(() -> MasterTitleRomPreview.loadSequenceFor(MasterTitleScreen.GameEntry.SONIC_1, rom), times(2));
            Files.delete(rom);
            refresh.invoke(screen);
            decoder.verifyNoMoreInteractions();
        }
    }

    @Test void availabilityComesFromTheCatalogueNotTheConfiguredFilename() throws Exception {
        Files.write(root.resolve("Some Other Name.gen"), sonic1Shaped((byte) 3));
        var config = SonicConfigurationService.createStandalone(root);
        config.setConfigValue(SonicConfiguration.SONIC_1_ROM, "");
        config.setConfigValue(SonicConfiguration.SONIC_2_ROM, root.resolve("missing2").toString());
        config.setConfigValue(SonicConfiguration.SONIC_3K_ROM, root.resolve("missing3").toString());
        config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, root.toString());
        var screen = new MasterTitleScreen(config);
        var refresh = MasterTitleScreen.class.getDeclaredMethod("refreshStockAvailability");
        refresh.setAccessible(true);
        refresh.invoke(screen);
        var available = MasterTitleScreen.class.getDeclaredMethod("isEntryAvailable", MasterTitleEntry.class);
        available.setAccessible(true);
        assertTrue((boolean) available.invoke(screen, new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_1)));
        assertFalse((boolean) available.invoke(screen, new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_2)));
        assertFalse((boolean) available.invoke(screen, new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_3K)));
    }
}
