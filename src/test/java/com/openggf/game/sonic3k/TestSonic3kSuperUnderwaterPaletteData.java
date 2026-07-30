package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies the underwater Super Sonic palette-cycle tables the water data
 * provider selects, so Super Sonic stays gold below the surface instead of
 * reverting to his blue palette.
 *
 * <p>ROM: {@code SuperHyper_PalCycle_SonicApply} (sonic3k.asm:4666-4681).
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kSuperUnderwaterPaletteData {

    /** First cycle frame of each table (3 words), from sonic3k.asm:4855-4890. */
    private static final int[] SURFACE_FIRST_FRAME = {0x0E66, 0x0C42, 0x0822};
    private static final int[] AIZ_ICZ_FIRST_FRAME = {0x0A82, 0x0860, 0x0640};
    private static final int[] HCZ_CNZ_LBZ_FIRST_FRAME = {0x0C66, 0x0A44, 0x0624};
    private static final int[] HYPER_FIRST_FRAME = {0x0EEC, 0x0ECA, 0x0EA8};
    private static final int[] SUPER_TAILS_FIRST_FRAME = {0x00AE, 0x008E, 0x046A};
    private static final int[] SUPER_KNUCKLES_FIRST_FRAME = {0x0A6E, 0x064E, 0x0428};
    private static final int[] KNUCKLES_REVERT_FRAME = {0x064E, 0x020C, 0x0206};

    @Test
    void underwaterCycleTableAddressesMatchRomData() throws IOException {
        Rom rom = GameServices.rom().getRom();

        assertFirstFrame(rom, Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ADDR, SURFACE_FIRST_FRAME);
        assertFirstFrame(rom, Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_UNDERWATER_AIZ_ICZ_ADDR,
                AIZ_ICZ_FIRST_FRAME);
        assertFirstFrame(rom, Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_UNDERWATER_HCZ_CNZ_LBZ_ADDR,
                HCZ_CNZ_LBZ_FIRST_FRAME);
    }

    @Test
    void advancedCharacterCycleTableAddressesMatchRomData() throws IOException {
        Rom rom = GameServices.rom().getRom();

        assertFirstFrame(rom, Sonic3kConstants.PAL_CYCLE_HYPER_SONIC_ADDR, HYPER_FIRST_FRAME);
        assertFirstFrame(rom, Sonic3kConstants.PAL_CYCLE_SUPER_TAILS_ADDR, SUPER_TAILS_FIRST_FRAME);
        assertFirstFrame(rom, Sonic3kConstants.PAL_CYCLE_SUPER_KNUCKLES_ADDR, SUPER_KNUCKLES_FIRST_FRAME);
        assertFirstFrame(rom, Sonic3kConstants.PAL_CYCLE_SUPER_KNUCKLES_REVERT_ADDR,
                KNUCKLES_REVERT_FRAME);
        assertEquals(10, Sonic3kConstants.PAL_CYCLE_SUPER_KNUCKLES_FRAME_COUNT);
    }

    @Test
    void nonSonicRevertSelectsTheExactRomFramesBeforeTerminalPaletteRestore()
            throws IOException {
        RomByteReader reader = RomByteReader.fromRom(GameServices.rom().getRom());
        Sonic3kSuperStateController tails = new Sonic3kSuperStateController(
                new Tails("tails", (short) 0, (short) 0));
        tails.loadRomData(reader);
        assertArrayEquals(new int[] {
                        0x00AE, 0x008E, 0x046A,
                        0x0E66, 0x0C42, 0x0822
                },
                tails.nonSonicRevertWordsForTest(),
                "Tails applies PalCycle_SuperTails frame zero, then the Flicky palette");

        Sonic3kSuperStateController knuckles = new Sonic3kSuperStateController(
                new Knuckles("knuckles", (short) 0, (short) 0));
        knuckles.loadRomData(reader);
        assertArrayEquals(KNUCKLES_REVERT_FRAME,
                knuckles.nonSonicRevertWordsForTest(),
                "Knuckles applies PalCycle_SuperHyperKnucklesRevert");
    }

    @Test
    void underwaterTablesDivergeFromTheSurfaceTableOnlyWhereTheRomSaysSo() throws IOException {
        Rom rom = GameServices.rom().getRom();
        int surfaceAddr = Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ADDR;
        int underwaterAddr = Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_UNDERWATER_AIZ_ICZ_ADDR;
        int frameSize = Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ENTRY_SIZE;

        // Frame 0 is the character's water-tinted base color, so it differs per table.
        assertNotEquals(rom.read16BitAddr(surfaceAddr) & 0xFFFF,
                rom.read16BitAddr(underwaterAddr) & 0xFFFF,
                "underwater frame 0 should be the water-tinted base color");

        // Frames 1-5 (the fade-in ramp to white) are shared byte-for-byte.
        for (int offset = frameSize; offset < 6 * frameSize; offset += 2) {
            assertEquals(rom.read16BitAddr(surfaceAddr + offset) & 0xFFFF,
                    rom.read16BitAddr(underwaterAddr + offset) & 0xFFFF,
                    "fade-in word at offset " + offset + " should match the surface table");
        }
    }

    @Test
    void aizAndIczUseTheGreenTintedTable() {
        Sonic3kWaterDataProvider provider = new Sonic3kWaterDataProvider();

        assertEquals(Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_UNDERWATER_AIZ_ICZ_ADDR,
                provider.getUnderwaterSuperPaletteCycleAddress(Sonic3kZoneIds.ZONE_AIZ, 0));
        assertEquals(Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_UNDERWATER_AIZ_ICZ_ADDR,
                provider.getUnderwaterSuperPaletteCycleAddress(Sonic3kZoneIds.ZONE_ICZ, 1));
    }

    @Test
    void otherWaterZonesUseTheAlternateTable() {
        Sonic3kWaterDataProvider provider = new Sonic3kWaterDataProvider();

        assertEquals(Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_UNDERWATER_HCZ_CNZ_LBZ_ADDR,
                provider.getUnderwaterSuperPaletteCycleAddress(Sonic3kZoneIds.ZONE_HCZ, 0));
        assertEquals(Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_UNDERWATER_HCZ_CNZ_LBZ_ADDR,
                provider.getUnderwaterSuperPaletteCycleAddress(Sonic3kZoneIds.ZONE_CNZ, 1));
        assertEquals(Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_UNDERWATER_HCZ_CNZ_LBZ_ADDR,
                provider.getUnderwaterSuperPaletteCycleAddress(Sonic3kZoneIds.ZONE_LBZ, 1));
    }

    private static void assertFirstFrame(Rom rom, int addr, int[] expected) throws IOException {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], rom.read16BitAddr(addr + i * 2) & 0xFFFF,
                    String.format("word %d at ROM 0x%X", i, addr));
        }
    }
}
