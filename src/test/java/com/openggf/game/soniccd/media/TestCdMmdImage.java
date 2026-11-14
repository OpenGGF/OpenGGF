package com.openggf.game.soniccd.media;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

import static org.junit.jupiter.api.Assertions.*;

class TestCdMmdImage {
    static byte[] mmd(int fileBytes, int flags, long origin, int counter, long entry, long hint, long vint) {
        byte[] bytes = new byte[fileBytes];
        ByteBuffer header = ByteBuffer.wrap(bytes);
        header.put((byte) flags).put((byte) 0x9a).putInt((int) origin).putShort((short) counter)
                .putInt((int) entry).putInt((int) hint).putInt((int) vint);
        for (int i = 256; i < bytes.length; i++) { bytes[i] = (byte) i; }
        return bytes;
    }

    @Test void retainsOriginalImageAndMapsOnlyDeclaredRelocatedPayload() throws IOException {
        byte[] bytes = mmd(300, 0xc1, 0xffff2000L, 3, 0xffff2004L, 0xff2008, 0xff200c);
        CdMmdImage image = CdMmdImage.parse(bytes);
        bytes[256] = 99;
        assertEquals(0xffff2000L, image.header().origin());
        assertEquals(0xff2000, CdMmdHeader.nativeAddress(image.header().origin()));
        assertEquals(0xc1, image.header().flags());
        assertEquals(0x9a, image.header().reserved());
        assertEquals(16, image.header().declaredCopyBytes());
        assertEquals(16, image.fileBackedCopyBytes());
        assertEquals(0, image.unresolvedCopyBytes());
        assertTrue(image.header().appliesSubCpuWordRamRequest());
        assertEquals(0xff2008, image.header().horizontalInterrupt());
        assertEquals(0xff200c, image.header().verticalInterrupt());
        assertEquals(0, image.nativeSlice(CdMmdImage.Domain.RELOCATED_COPY, 0xff2000, 1).get());
        assertEquals(4, image.nativeSlice(CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x200104, 1).get());
        assertEquals(256, image.relocatedCopy().orElseThrow().fileSlice().offset());
        assertThrows(IllegalArgumentException.class,
                () -> image.nativeSlice(CdMmdImage.Domain.RELOCATED_COPY, 0xff2010, 1));
        assertThrows(ReadOnlyBufferException.class, () -> image.fileSlice(256, 2).put((byte) 1));
        assertArrayEquals(new byte[] {0, 1, 2}, image.readFile(256, 3));
    }

    @Test void zeroOriginRetainsWholeWordRamAndDoesNotInterpretCounterAsCopySize() throws IOException {
        var image = CdMmdImage.parse(mmd(1024, 0, 0, 65535, 0x200100, 0, 0));
        assertEquals(0, image.header().declaredCopyBytes());
        assertTrue(image.relocatedCopy().isEmpty());
        assertEquals(1024, image.originalWordRam().length());
        assertEquals(65535, image.header().sizeCounter());
        assertFalse(image.header().requestsSubCpuWordRam());
    }

    @Test void zeroEntryReturnsBeforeCopyOrFlagsEvenWithNonzeroOrigin() throws IOException {
        var image = CdMmdImage.parse(mmd(256, 0x40, 0xffffff, 65535, 0, 0xffffffffL, 1));
        assertFalse(image.header().hasEntry());
        assertTrue(image.header().requestsSubCpuWordRam());
        assertFalse(image.header().appliesSubCpuWordRamRequest());
        assertEquals(0xffffffffL, image.header().horizontalInterrupt());
        assertTrue(image.relocatedCopy().isEmpty());
        assertEquals(0, image.unresolvedCopyBytes());
    }

    @Test void exposesRealStyleCopyTailWithoutInventingPadding() throws IOException {
        var image = CdMmdImage.parse(mmd(7514, 0x40, 0xff2000, 2047, 0xff2000, 0, 0));
        assertEquals(8192, image.header().declaredCopyBytes());
        assertEquals(7258, image.fileBackedCopyBytes());
        assertEquals(934, image.unresolvedCopyBytes());
        assertThrows(IllegalArgumentException.class, () -> image.nativeSlice(
                CdMmdImage.Domain.RELOCATED_COPY, 0xff2000 + 7258, 1));
        assertThrows(IllegalArgumentException.class, () -> image.readFile(7514, 1));
    }

    @Test void rejectsTruncationOversizedWordRamCopiesNativeWrapAndOverflowingSlices() throws IOException {
        assertThrows(IOException.class, () -> CdMmdImage.parse(new byte[255]));
        assertThrows(IOException.class, () -> CdMmdImage.parse(new byte[0x40001]));
        assertThrows(IOException.class, () -> CdMmdImage.parse(mmd(256, 0, 0xff2000, 65535, 1, 0, 0)));
        assertThrows(IOException.class, () -> CdMmdImage.parse(mmd(256, 0, 0xfffffe, 0, 1, 0, 0)));
        var image = CdMmdImage.parse(mmd(260, 0, 0xfffffc, 0, 1, 0, 0));
        assertEquals(4, image.fileBackedCopyBytes());
        assertThrows(IllegalArgumentException.class, () -> image.fileSlice(Long.MAX_VALUE, 2));
        assertThrows(IllegalArgumentException.class, () -> image.fileSlice(1, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> image.fileSlice(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> image.nativeSlice(
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0xff200100, 1));
        assertThrows(IllegalArgumentException.class, () -> CdMmdHeader.nativeAddress(0x100000000L));
    }
}
