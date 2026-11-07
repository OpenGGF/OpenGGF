package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class TestCdSelectedHscroll {
    @Test void nativePrefixReplacesOnlyTheFirst224Scanlines() {
        byte[] previous = new byte[1024];
        byte[] live = new byte[1024];
        Arrays.fill(previous, (byte) 0xA5);
        Arrays.fill(live, (byte) 0x36);
        previous[0x380] = (byte) 0x81;
        previous[1023] = (byte) 0xFE;
        live[0] = 0x12;
        live[0x37F] = 0x78;
        live[0x380] = 0x24;

        var result = CdSelectedHscroll.copyNativePrefix(CdAssetBytes.copyOf(previous), CdAssetBytes.copyOf(live));

        assertArrayEquals(Arrays.copyOfRange(live, 0, 0x380), result.slice(0, 0x380).copy());
        assertArrayEquals(Arrays.copyOfRange(previous, 0x380, 1024), result.slice(0x380, 128).copy());
        assertEquals(0x78, result.unsignedByte(0x37F));
        assertEquals(0x81, result.unsignedByte(0x380));
        assertEquals(0xFE, result.unsignedByte(1023));
    }

    @Test void copyingDoesNotMutateEitherInputOrExposeWritableOutputStorage() {
        byte[] rawPrevious = new byte[1024];
        byte[] rawLive = new byte[1024];
        Arrays.fill(rawPrevious, (byte) 0xA6);
        Arrays.fill(rawLive, (byte) 0x53);
        var previous = CdAssetBytes.copyOf(rawPrevious);
        var live = CdAssetBytes.copyOf(rawLive);

        var result = CdSelectedHscroll.copyNativePrefix(previous, live);
        byte[] detached = result.copy();
        Arrays.fill(detached, (byte) 0);
        Arrays.fill(rawPrevious, (byte) 0);
        Arrays.fill(rawLive, (byte) 0);

        assertEquals(0xA6, previous.unsignedByte(0));
        assertEquals(0xA6, previous.unsignedByte(1023));
        assertEquals(0x53, live.unsignedByte(0));
        assertEquals(0x53, live.unsignedByte(1023));
        assertEquals(0x53, result.unsignedByte(0));
        assertEquals(0xA6, result.unsignedByte(1023));
        assertThrows(ReadOnlyBufferException.class, () -> result.buffer().put(0, (byte) 0));
    }

    @Test void sameInputCanSupplyBothImagesWithoutChangingItsBytes() {
        byte[] raw = new byte[1024];
        raw[0] = (byte) 0x80;
        raw[0x37F] = (byte) 0x91;
        raw[0x380] = (byte) 0xA2;
        raw[1023] = (byte) 0xB3;
        var image = CdAssetBytes.copyOf(raw);

        assertArrayEquals(raw, CdSelectedHscroll.copyNativePrefix(image, image).copy());
        assertArrayEquals(raw, image.copy());
    }

    @Test void carriedWordsKeepRawSignedLongBitsInBigEndianOrder() {
        int[] words = new int[256];
        words[0] = 0x01234567;
        words[1] = 0x89ABCDEF;
        words[2] = Integer.MIN_VALUE;
        words[3] = -1;
        words[223] = 0xFEDCBA98;
        words[224] = 0x80000001;
        words[255] = 0x76543210;

        var result = CdSelectedHscroll.fromCarriedWords(words);

        assertEquals(1024, result.length());
        assertArrayEquals(HexFormat.of().parseHex("0123456789abcdef80000000ffffffff"), result.slice(0, 16).copy());
        assertArrayEquals(HexFormat.of().parseHex("fedcba9880000001"), result.slice(0x37C, 8).copy());
        assertArrayEquals(HexFormat.of().parseHex("76543210"), result.slice(1020, 4).copy());
    }

    @Test void carriedArrayAndReturnedCopiesCannotMutateThePackedImage() {
        int[] words = new int[256];
        Arrays.fill(words, 0x89ABCDEF);
        var image = CdSelectedHscroll.fromCarriedWords(words);
        Arrays.fill(words, 0);
        byte[] detached = image.copy();
        Arrays.fill(detached, (byte) 0);

        assertEquals(0x89ABCDEFL, image.unsignedLong(0));
        assertEquals(0x89ABCDEFL, image.unsignedLong(1020));
        assertThrows(ReadOnlyBufferException.class, () -> image.buffer().putInt(0));
    }

    @Test void repeatedNativeCopiesRetainAll32DistinctCarriedTailWords() {
        int[] words = new int[256];
        for (int i = 224; i < 256; i++) words[i] = 0x80000000 | (i << 16) | (i << 8) | i;
        byte[] firstLive = new byte[1024];
        byte[] secondLive = new byte[1024];
        Arrays.fill(firstLive, (byte) 0x19);
        Arrays.fill(secondLive, (byte) 0x62);
        var first = CdSelectedHscroll.copyNativePrefix(CdSelectedHscroll.fromCarriedWords(words), CdAssetBytes.copyOf(firstLive));
        var second = CdSelectedHscroll.copyNativePrefix(first, CdAssetBytes.copyOf(secondLive));

        assertEquals(0x19, first.unsignedByte(0x37F));
        assertEquals(0x62, second.unsignedByte(0x37F));
        for (int i = 224; i < 256; i++) {
            assertEquals(Integer.toUnsignedLong(words[i]), first.unsignedLong(i * 4));
            assertEquals(Integer.toUnsignedLong(words[i]), second.unsignedLong(i * 4));
        }
    }

    @Test void prefixCopyRejectsNullAndIncompleteImages() {
        var complete = CdAssetBytes.copyOf(new byte[1024]);
        assertThrows(NullPointerException.class, () -> CdSelectedHscroll.copyNativePrefix(null, complete));
        assertThrows(NullPointerException.class, () -> CdSelectedHscroll.copyNativePrefix(complete, null));
        for (int length : new int[]{0, 896, 1023, 1025}) {
            var incomplete = CdAssetBytes.copyOf(new byte[length]);
            assertThrows(IllegalArgumentException.class, () -> CdSelectedHscroll.copyNativePrefix(incomplete, complete));
            assertThrows(IllegalArgumentException.class, () -> CdSelectedHscroll.copyNativePrefix(complete, incomplete));
        }
    }

    @Test void carriedPackingRejectsNullAndNon256WordImages() {
        assertThrows(NullPointerException.class, () -> CdSelectedHscroll.fromCarriedWords(null));
        for (int length : new int[]{0, 224, 255, 257}) {
            assertThrows(IllegalArgumentException.class, () -> CdSelectedHscroll.fromCarriedWords(new int[length]));
        }
    }
}
