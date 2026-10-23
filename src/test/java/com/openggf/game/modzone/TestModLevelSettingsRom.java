package com.openggf.game.modzone;

import com.openggf.data.Rom;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.sonic1.Sonic1ModLevelSettingsProvider;
import com.openggf.game.sonic1.Sonic1WaterDataProvider;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic2.Sonic2ModLevelSettingsProvider;
import com.openggf.game.sonic2.Sonic2WaterDataProvider;
import com.openggf.game.sonic2.scroll.ParallaxTables;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.game.sonic2.scroll.SwScrlEhz;
import com.openggf.game.sonic3k.Sonic3kModLevelSettingsProvider;
import com.openggf.game.sonic3k.Sonic3kWaterDataProvider;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.Palette;
import com.openggf.level.WaterSystem;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TestModLevelSettingsRom {
    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonic1AuthoredWaterUsesLabyrinthRomPaletteAtItsOwnFixedHeight() throws Exception {
        try (Rom rom = open(RomTestUtils.ensureSonic1RomAvailable())) {
            assertWater(new Sonic1ModLevelSettingsProvider(), new Sonic1WaterDataProvider(),
                    Sonic1Constants.ZONE_LZ, 0, rom);
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonic2AuthoredWaterAndScrollUseAquaticPaletteAndNativeEhzRippleTable() throws Exception {
        try (Rom rom = open(RomTestUtils.ensureSonic2RomAvailable())) {
            var provider = new Sonic2ModLevelSettingsProvider();
            assertWater(provider, new Sonic2WaterDataProvider(), Sonic2ZoneConstants.ROM_ZONE_ARZ, 0, rom);
            var settings = ModLevelSettings.DEFAULT.withBackground(new ModLevelSettings.StockScrollSource(0, 1));
            var hosted = provider.create(settings, rom).scroll();
            var nativeHandler = new SwScrlEhz(new ParallaxTables(rom));
            int[] actual = new int[224], expected = new int[224];
            Arrays.fill(actual, 0x13572468);
            Arrays.fill(expected, 0x13572468);
            hosted.init(0, 320, 96);
            hosted.update(actual, 352, 104, 17, 0);
            nativeHandler.update(expected, 352, 104, 17, 1);
            assertArrayEquals(expected, actual);
            assertEquals(0x13572468, actual[222], "The hosted source must retain shipped fixBugs=0 coverage");
            assertEquals(0x13572468, actual[223]);
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kAuthoredWaterUsesHydrocityRomPaletteIncludingKnucklesPatch() throws Exception {
        try (Rom rom = open(RomTestUtils.ensureSonic3kRomAvailable())) {
            assertWater(new Sonic3kModLevelSettingsProvider(), new Sonic3kWaterDataProvider(),
                    Sonic3kZoneIds.ZONE_HCZ, 0, rom);
        }
    }

    private static void assertWater(ModLevelSettingsProvider provider, WaterDataProvider nativeWater,
            int sourceZone, int sourceAct, Rom rom) throws Exception {
        var water = provider.create(ModLevelSettings.DEFAULT.withWaterHeight(0x321), rom).water();
        assertNotNull(water);
        for (var character : PlayerCharacter.values()) {
            assertTrue(water.hasWater(0x40, 9, character));
            assertEquals(0x321, water.getStartingWaterLevel(0x40, 9));
            assertEquals(0, water.getGameplayWaterLevelOffset(0x40, 9));
            assertEquals(0, water.getVisualWaterLevelOffset(0x40, 9));
            assertNull(water.getDynamicHandler(0x40, 9, character));
            Palette[] expected = nativeWater.getUnderwaterPalette(rom, sourceZone, sourceAct, character);
            Palette[] actual = water.getUnderwaterPalette(rom, 0x40, 9, character);
            assertNotNull(expected);
            for (int line = 0; line < 4; line++) assertTrue(expected[line].dataEquals(actual[line]));
            actual[0].getColor(1).r ^= 0x7F;
            Palette[] nextLoad = water.getUnderwaterPalette(rom, 0x40, 9, character);
            assertTrue(expected[0].dataEquals(nextLoad[0]), "Each water load owns independent mutable palettes");
            var system = new WaterSystem();
            system.loadForLevelFromProvider(water, rom, 0x40, 9, character);
            assertTrue(system.hasWater(0x40, 9));
            assertEquals(0x321, system.getWaterLevelY(0x40, 9));
            for (int line = 0; line < 4; line++)
                assertTrue(expected[line].dataEquals(system.getUnderwaterPalette(0x40, 9)[line]));
        }
    }

    private static Rom open(File file) {
        assertNotNull(file);
        Rom rom = new Rom();
        assertTrue(rom.open(file.getAbsolutePath()));
        return rom;
    }
}
