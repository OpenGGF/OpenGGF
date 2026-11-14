package com.openggf.data.compression;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.resources.CompressionType;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decoding a KosM archive straight from the ROM reader must produce the same
 * bytes as the former path, which copied a window of at least 64 KB of the ROM
 * (capped at 256 KB and at the ROM end) and decoded that copy.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestKosinskiModuledRomReader {
    /** Gumball_8x8_KosM sits near the end of the 4 MB locked-on image. */
    private static final int GUMBALL_BONUS_KOSM = Sonic3kConstants.GUMBALL_8X8_KOSM_ADDR;

    @Test
    void romReaderDecodeMatchesTheWindowedCopyForEveryRegistryArchive() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        RomByteReader reader = RomByteReader.fromRom(rom);
        TreeSet<Integer> addresses = new TreeSet<>();
        for (int zone = 0; zone <= 0x17; zone++) {
            for (int act = 0; act <= 1; act++) {
                for (Sonic3kPlcArtRegistry.StandaloneArtEntry entry
                        : Sonic3kPlcArtRegistry.getPlan(zone, act).standaloneArt()) {
                    if (entry.compression() == CompressionType.KOSINSKI_MODULED) {
                        addresses.add(entry.artAddr());
                    }
                }
            }
        }
        addresses.add(GUMBALL_BONUS_KOSM);
        assertTrue(addresses.size() > 20, "registry KosM archives: " + addresses.size());
        for (int address : addresses) {
            byte[] expected = KosinskiReader.decompressModuled(windowedCopy(rom, address), 0);
            assertArrayEquals(expected, KosinskiReader.decompressModuled(reader, address),
                    String.format("KosM archive at 0x%X", address));
        }
    }

    private static byte[] windowedCopy(Rom rom, int address) throws Exception {
        byte[] header = rom.readBytes(address, 2);
        int fullSize = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        int inputSize = Math.min(Math.max(fullSize + 256, 0x10000), 0x40000);
        inputSize = (int) Math.min(inputSize, rom.getSize() - address);
        return rom.readBytes(address, inputSize);
    }
}
