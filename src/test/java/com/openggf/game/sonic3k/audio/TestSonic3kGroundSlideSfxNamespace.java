package com.openggf.game.sonic3k.audio;

import com.openggf.data.Rom;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSfxData;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.tests.FullReset;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Native GroundSlide request namespace and actual loaded ROM target.
 * Origin: Root glide-prelude follow-up to 8272a5402, 2026-10-22.
 * Pinned skdisasm8b2cf3c: sonic3k.constants.asm:1596 and
 * Sound/SFX/7E - Ground Slide.asm (PSG3, transpose0, volume3, formE7).
 * This checks source/header/stream identity, not audible/native waveform parity.
 */
@RequiresRom(SonicGame.SONIC_3K)
@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestSonic3kGroundSlideSfxNamespace {
    @Test
    void nativeRequestLoadsGroundSlideAtItsActualZ80Pointer() throws Exception {
        try (var rom = new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            var profile = new Sonic3kAudioProfile();
            var loader = assertInstanceOf(Sonic3kSmpsLoader.class, profile.createSmpsLoader(rom));
            assertEquals(0x7E, Sonic3kSfx.GROUND_SLIDE.id);
            var loaded = assertInstanceOf(Sonic3kSfxData.class,
                    loader.loadSfx(Sonic3kSfx.GROUND_SLIDE.id));
            assertEquals(0x7E, loaded.getId());

            // Read the shipped pointer independently of the loader's private table.
            // Native queue IDs start at33, not zero or a Sonic2 request boundary.
            byte[] compressed = rom.readBytes(Sonic3kSmpsConstants.Z80_ADDITIONAL_DATA_ADDR, 0x8000);
            byte[] additional = KosinskiReader.decompress(
                    Channels.newChannel(new ByteArrayInputStream(compressed)), false);
            int tableIndex = 0x7E - 0x33;
            assertEquals(0x4B, tableIndex);
            int pointerOffset = Sonic3kSmpsConstants.Z80_SFX_PTR_LIST
                    - Sonic3kSmpsConstants.Z80_GENERAL_PTR_LIST + tableIndex * 2;
            int nativeHeaderPointer = le16(additional, pointerOffset);
            assertTrue(nativeHeaderPointer >= 0x8000 && nativeHeaderPointer <= 0xFFFF);
            int header = nativeHeaderPointer & 0x7FFF;
            byte[] bank = rom.readBytes(Sonic3kSmpsConstants.SFX_BANK_BASE, 0x8000);
            assertArrayEquals(Arrays.copyOfRange(bank, header, header + 10),
                    Arrays.copyOfRange(loaded.getData(), header, header + 10));

            var tracks = loaded.getTrackEntries();
            assertEquals(1, tracks.size(), "Sound_7E_Header declares one PSG3 track");
            assertEquals(1, loaded.getTickMultiplier());
            var track = tracks.getFirst();
            assertEquals(0xC0, track.channelMask(), "native cPSG3");
            assertEquals(0, track.transpose());
            assertEquals(3, track.volume());
            int nativeTrackPointer = le16(bank, header + 6) & 0x7FFF;
            assertEquals(nativeTrackPointer, track.pointer());
            // smpsPSGform E7; smpsModSet 1,1,1,1 (source/target driver3).
            byte[] groundSlideStart = {(byte) 0xF3, (byte) 0xE7,
                    (byte) 0xF0, 1, 1, 1, 1};
            assertArrayEquals(groundSlideStart,
                    Arrays.copyOfRange(bank, nativeTrackPointer, nativeTrackPointer + 7));
            assertArrayEquals(groundSlideStart,
                    Arrays.copyOfRange(loaded.getData(), track.pointer(), track.pointer() + 7));
        }
    }

    private static int le16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }
}
