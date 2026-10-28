package com.openggf.game.sonic3k.audio;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomIdentity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile.*;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic3kAudioResourceProfile {
    @Test
    void nativeMusicNamespaceDoesNotConsumeSfxOrCommands() {
        assertEquals(0x125, SONIC_3.musicId(0x25));
        assertEquals(0x12D, SONIC_3.musicId(0x2D));
        for (int arena = 0x20; arena <= 0x24; arena++) {
            assertEquals(0x100 | arena, SONIC_3.musicId(arena));
        }
        assertEquals(0x25, SONIC_KNUCKLES.musicId(0x25));
        assertEquals(0x0F, SONIC_KNUCKLES.musicId(0x0F));
        assertEquals(0x33, LOCKED_ON.musicId(0x33));
        for (int invalid : new int[] {-1, 0, 0x33, 0xDC, 0xE0, 0x125}) {
            assertThrows(IllegalArgumentException.class, () -> SONIC_3.musicId(invalid));
        }
    }

    @Test
    void logicalCatalogueIdentitySelectsOnlySupportedAudioCartridges() {
        assertSame(SONIC_3, fromLogicalRom(RomIdentity.S3));
        assertSame(SONIC_KNUCKLES, fromLogicalRom(RomIdentity.SK));
        assertSame(LOCKED_ON, fromLogicalRom(RomIdentity.S3K));
        assertThrows(IllegalArgumentException.class, () -> fromLogicalRom(RomIdentity.S2));
        assertThrows(IllegalArgumentException.class, () -> fromLogicalRom(RomIdentity.KIS2));
    }

    @Test
    void actualViewHeadersAndLengthsSelectResourcesIndependentlyOfFilename() {
        try (Rom s3 = image(0x200000, "SONIC THE HEDGEHOG 3", null);
             Rom sk = image(0x200000, "SONIC & KNUCKLES", null);
             Rom combined = image(0x400000, "SONIC & KNUCKLES", "SONIC THE HEDGEHOG 3")) {
            assertSame(SONIC_3, fromRom(s3));
            assertSame(SONIC_KNUCKLES, fromRom(sk));
            assertSame(LOCKED_ON, fromRom(combined));
            // No retained world/module selection may contaminate a later lookup.
            assertSame(SONIC_3, fromRom(s3));
        }
    }

    @Test
    void unrelatedMalformedAndClosedViewsFailExplicitly() {
        try (Rom unrelated = image(0x200000, "SONIC THE HEDGEHOG 2", null);
             Rom wrongUpper = image(0x400000, "SONIC & KNUCKLES", "SONIC THE HEDGEHOG 2");
             Rom wrongSize = image(0x100000, "SONIC THE HEDGEHOG 3", null)) {
            assertThrows(IllegalArgumentException.class, () -> fromRom(unrelated));
            assertThrows(IllegalArgumentException.class, () -> fromRom(wrongUpper));
            assertThrows(IllegalArgumentException.class, () -> fromRom(wrongSize));
        }
        Rom closed = image(0x200000, "SONIC THE HEDGEHOG 3", null);
        closed.close();
        assertThrows(IllegalArgumentException.class, () -> fromRom(closed));
    }

    private static Rom image(int size, String first, String second) {
        byte[] bytes = new byte[size];
        putTitle(bytes, 0, first);
        if (second != null) putTitle(bytes, 0x200000, second);
        return Rom.fromReader(RomByteReader.fromBytes(bytes), "arbitrary-filename.gen");
    }

    private static void putTitle(byte[] bytes, int base, String title) {
        byte[] name = title.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, bytes, base + 0x150, name.length);
    }
}
