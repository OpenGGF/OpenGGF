package com.openggf.game.sonic1.audio.smps;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openggf.audio.smps.Sonic1SmpsData;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TestSonic1SfxData {

    @Test
    void zeroAddressVoiceUsesTheSameOperatorNormalizationAsS1Voices() {
        byte[] romVectorArea = new byte[50];
        for (int index = 0; index < romVectorArea.length; index++) {
            romVectorArea[index] = (byte) index;
        }
        Sonic1SfxData data = new Sonic1SfxData(new byte[] {0, 0}, 0);
        data.setZeroAddressVoiceBank(romVectorArea);

        byte[] expected = Arrays.copyOfRange(romVectorArea, 25, 50);
        for (int group = 1; group < expected.length; group += 4) {
            byte middle = expected[group + 1];
            expected[group + 1] = expected[group + 2];
            expected[group + 2] = middle;
        }

        assertArrayEquals(expected, data.getZeroAddressFmVoice(1));
    }
    @Test
    void musicSfxAndZeroAddressVoicesPreserveFormatBoundsAndCopyIsolation() {
        byte[] source = new byte[33];
        source[1] = 8; // Relative voice table follows the empty song/SFX header.
        for (int i = 0; i < 25; i++) source[8 + i] = (byte) i;
        byte[] original = source.clone();
        byte[] expected = {0, 1, 3, 2, 4, 5, 7, 6, 8, 9, 11, 10, 12,
                13, 15, 14, 16, 17, 19, 18, 20, 21, 23, 22, 24};
        Sonic1SmpsData music = new Sonic1SmpsData(source);
        Sonic1SfxData sfx = new Sonic1SfxData(source, 0);
        byte[] vectors = Arrays.copyOfRange(source, 8, 33);
        sfx.setZeroAddressVoiceBank(vectors);
        vectors[0] = 99; // The zero-address bank is copied on installation.

        assertArrayEquals(expected, music.getVoice(0));
        assertArrayEquals(expected, sfx.getVoice(0));
        assertArrayEquals(expected, sfx.getZeroAddressFmVoice(0));
        music.getVoice(0)[0] = 99;
        sfx.getVoice(0)[0] = 99;
        sfx.getZeroAddressFmVoice(0)[0] = 99;
        assertArrayEquals(expected, music.getVoice(0));
        assertArrayEquals(expected, sfx.getVoice(0));
        assertArrayEquals(expected, sfx.getZeroAddressFmVoice(0));
        assertArrayEquals(original, source);
        // Preserve historical exception types when offset + 25 overflows the bounds check.
        int overflowId = 85_899_345;
        assertThrows(IllegalArgumentException.class, () -> sfx.getZeroAddressFmVoice(overflowId));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> music.getVoice(overflowId));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> sfx.getVoice(overflowId));
        for (int id : new int[] {-1, 1}) {
            assertNull(music.getVoice(id));
            assertNull(sfx.getVoice(id));
            assertNull(sfx.getZeroAddressFmVoice(id));
        }
    }
}
