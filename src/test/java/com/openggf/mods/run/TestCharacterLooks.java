package com.openggf.mods.run;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Palette;
import com.openggf.net.identity.PlayerAppearance;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestCharacterLooks {
    @Test
    void presetsRecolourOnlyTheBodyRampLightestFirst() {
        PlayerAppearance look = CharacterLooks.withBaseColour("s2", "sonic", 0x008);
        assertEquals(0x008, look.baseColour());
        // Ramp {5,4,3,2}: one step lighter on every channel, the base, then darker steps that
        // keep empty channels empty.
        assertEquals(0x22A, look.colourAt(5, -1));
        assertEquals(0x008, look.colourAt(4, -1));
        assertEquals(0x006, look.colourAt(3, -1));
        assertEquals(0x004, look.colourAt(2, -1));
        assertEquals(-1, look.colourAt(6, -1), "eyes, skin and shoes keep their stock colours");
        assertEquals(PlayerAppearance.decode(look.encode()), look, "presets are canonical looks");
        assertArrayEquals(new int[0], CharacterLooks.bodyRamp("s1", "knuckles"));
        assertTrue(CharacterLooks.withBaseColour("s1", "knuckles", 0x00E).palette().isEmpty(),
                "an unknown ramp changes only the name colour");
    }

    /** Every ramp, read from the stock line the engine loads, must get darker step by step. */
    @Test
    void rampsAreLightestFirstInTheRomStockLines() throws Exception {
        checkGame(RomTestUtils.ensureSonic1RomAvailable(), new Sonic1GameModule(), "s1", "sonic");
        checkGame(RomTestUtils.ensureSonic2RomAvailable(), new Sonic2GameModule(), "s2", "sonic", "tails");
        checkGame(RomTestUtils.ensureSonic3kRomAvailable(), new Sonic3kGameModule(), "s3k", "sonic", "tails", "knuckles");
        File s3k = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(s3k != null && s3k.isFile(), "S3K ROM not available");
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(s3k.getAbsolutePath()));
            // Knuckles in Sonic 2 draws with the S2-layout line (Pal_KnuxEndPose).
            Palette line = new Palette();
            line.fromSegaFormat(RomByteReader.fromRom(rom).slice(Sonic3kConstants.KNUCKLES_S2_PALETTE_ADDR, 32));
            assertDarkening("s2", "knuckles", line);
        }
    }

    private static void checkGame(File romFile, GameModule module, String gameId, String... characters)
            throws Exception {
        assumeTrue(romFile != null && romFile.isFile(), gameId + " ROM not available");
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romFile.getAbsolutePath()));
            RomByteReader reader = RomByteReader.fromRom(rom);
            for (String character : characters) {
                Palette line = module.getCrossGameDonorProvider().loadCharacterPalette(reader, character);
                assertNotNull(line, gameId + ":" + character);
                assertDarkening(gameId, character, line);
            }
        }
    }

    private static void assertDarkening(String gameId, String character, Palette line) {
        int[] ramp = CharacterLooks.bodyRamp(gameId, character);
        assertTrue(ramp.length >= 3, gameId + ":" + character + " has a ramp");
        int previous = Integer.MAX_VALUE;
        for (int index : ramp) {
            Palette.Color colour = line.getColor(index);
            int brightness = Byte.toUnsignedInt(colour.r) + Byte.toUnsignedInt(colour.g) + Byte.toUnsignedInt(colour.b);
            assertTrue(brightness < previous, gameId + ":" + character + " idx" + index + " is darker than the step before");
            previous = brightness;
        }
    }
}
