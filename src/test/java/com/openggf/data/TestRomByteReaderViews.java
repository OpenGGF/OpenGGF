package com.openggf.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRomByteReaderViews {

    private static byte[] ramp(int size, int start) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (start + i);
        }
        return data;
    }

    @Test
    void windowRebasesAddressZeroAndRejectsReadsPastItsEnd() {
        RomByteReader whole = RomByteReader.fromBytes(ramp(16, 0));
        RomByteReader window = whole.window(4, 8);

        assertEquals(8, window.size());
        assertEquals(4, window.readU8(0));
        assertEquals(11, window.readU8(7));
        assertEquals(0x0405, window.readU16BE(0));
        assertThrows(IndexOutOfBoundsException.class, () -> window.readU8(8));
        assertThrows(IndexOutOfBoundsException.class, () -> window.readU16BE(7));
        assertThrows(IndexOutOfBoundsException.class, () -> window.readU8(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> window.slice(6, 3));
    }

    @Test
    void windowMustLieInsideItsSource() {
        RomByteReader whole = RomByteReader.fromBytes(ramp(16, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> whole.window(8, 9));
        assertThrows(IndexOutOfBoundsException.class, () -> whole.window(-1, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> whole.window(17, 0));
    }

    @Test
    void nestedWindowsComposeOffsets() {
        RomByteReader whole = RomByteReader.fromBytes(ramp(32, 0));
        RomByteReader inner = whole.window(8, 16).window(4, 4);
        assertEquals(4, inner.size());
        assertArrayEquals(new byte[] {12, 13, 14, 15}, inner.slice(0, 4));
    }

    @Test
    void concatPresentsPartsBackToBackAndAssemblesReadsAcrossTheSeam() {
        RomByteReader first = RomByteReader.fromBytes(new byte[] {0x11, 0x22, 0x33});
        RomByteReader second = RomByteReader.fromBytes(new byte[] {0x44, 0x55, 0x66, 0x77});
        RomByteReader joined = RomByteReader.concat(first, second);

        assertEquals(7, joined.size());
        assertEquals(0x33, joined.readU8(2));
        assertEquals(0x44, joined.readU8(3));
        assertEquals(0x3344, joined.readU16BE(2));
        assertEquals(0x22334455, joined.readU32BE(1));
        assertArrayEquals(new byte[] {0x22, 0x33, 0x44, 0x55, 0x66}, joined.slice(1, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> joined.readU8(7));
        assertThrows(IndexOutOfBoundsException.class, () -> joined.readU32BE(4));
    }

    @Test
    void concatOfThreeWindowsMatchesTheJoinedArray() {
        byte[] a = ramp(10, 0);
        byte[] b = ramp(6, 100);
        byte[] c = ramp(8, 50);
        RomByteReader joined = RomByteReader.concat(
                RomByteReader.fromBytes(a).window(2, 6),
                RomByteReader.fromBytes(b),
                RomByteReader.fromBytes(c).window(1, 7));
        byte[] expected = new byte[6 + 6 + 7];
        System.arraycopy(a, 2, expected, 0, 6);
        System.arraycopy(b, 0, expected, 6, 6);
        System.arraycopy(c, 1, expected, 12, 7);
        assertArrayEquals(expected, joined.slice(0, joined.size()));
        for (int i = 0; i < expected.length; i++) {
            assertEquals(Byte.toUnsignedInt(expected[i]), joined.readU8(i), "byte " + i);
        }
    }

    @Test
    void windowOverConcatKeepsSeamsAndTrimsParts() {
        RomByteReader joined = RomByteReader.concat(
                RomByteReader.fromBytes(ramp(4, 0)),
                RomByteReader.fromBytes(ramp(4, 10)),
                RomByteReader.fromBytes(ramp(4, 20)));
        RomByteReader middle = joined.window(2, 8);
        assertEquals(8, middle.size());
        assertArrayEquals(new byte[] {2, 3, 10, 11, 12, 13, 20, 21}, middle.slice(0, 8));
        assertEquals(0x030A, middle.readU16BE(1));
        assertThrows(IndexOutOfBoundsException.class, () -> middle.readU8(8));

        RomByteReader single = joined.window(4, 4);
        assertArrayEquals(new byte[] {10, 11, 12, 13}, single.slice(0, 4));
    }

    @Test
    void concatOfOneReaderIsThatReaderAndEmptyPartsAreRejected() {
        RomByteReader only = RomByteReader.fromBytes(ramp(4, 0));
        assertSame(only, RomByteReader.concat(only));
        assertThrows(IllegalArgumentException.class, RomByteReader::concat);
        assertThrows(IllegalArgumentException.class,
                () -> RomByteReader.concat(only, RomByteReader.fromBytes(new byte[0])));
        assertThrows(NullPointerException.class, () -> RomByteReader.concat(only, null));
    }

    @Test
    void viewsShareBytesWithoutCopyingButConstructorStillCopies() {
        byte[] source = ramp(8, 0);
        RomByteReader reader = RomByteReader.fromBytes(source);
        source[0] = 99;
        assertEquals(0, reader.readU8(0), "fromBytes must snapshot the caller's array");
        RomByteReader window = reader.window(0, 4);
        assertEquals(0, window.readU8(0));
    }

    @Test
    void pointerTableReadsWorkThroughAWindow() {
        byte[] data = new byte[16];
        data[8] = 0x00;
        data[9] = 0x04; // entry 0: offset 4 from the table base
        RomByteReader window = RomByteReader.fromBytes(data).window(4, 12);
        assertEquals(4 + 4, window.readPointer16(4, 0));
    }

    @Test
    void channelReadsFromTheRequestedOffsetAndReportsAbsolutePositions() throws IOException {
        RomByteReader reader = RomByteReader.concat(
                RomByteReader.fromBytes(ramp(5, 0)), RomByteReader.fromBytes(ramp(5, 50)));
        try (SeekableByteChannel channel = RomChannel.at(reader, 3)) {
            assertEquals(3, channel.position());
            assertEquals(10, channel.size());
            ByteBuffer buffer = ByteBuffer.allocate(4);
            assertEquals(4, channel.read(buffer));
            assertArrayEquals(new byte[] {3, 4, 50, 51}, buffer.array());
            assertEquals(7, channel.position());
            ByteBuffer rest = ByteBuffer.allocate(16);
            assertEquals(3, channel.read(rest));
            assertEquals(-1, channel.read(ByteBuffer.allocate(1)));
            channel.position(9);
            ByteBuffer last = ByteBuffer.allocateDirect(2);
            assertEquals(1, channel.read(last));
            assertEquals(54, last.get(0));
            assertTrue(channel.isOpen());
        }
        assertThrows(IOException.class, () -> RomChannel.at(reader, 11));
        assertThrows(IOException.class, () -> RomChannel.at(reader, -1));
    }

    @Test
    void channelOverAFileBackedRomReadsTheSameBytesAsTheRom(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws IOException {
        java.nio.file.Path file = dir.resolve("image.bin");
        java.nio.file.Files.write(file, ramp(64, 0));
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(file.toString()));
            try (SeekableByteChannel channel = RomChannel.at(rom, 60)) {
                ByteBuffer buffer = ByteBuffer.allocate(8);
                assertEquals(4, channel.read(buffer));
                assertArrayEquals(new byte[] {60, 61, 62, 63}, java.util.Arrays.copyOf(buffer.array(), 4));
            }
            assertEquals(0, rom.getFileChannel().position(),
                    "a RomChannel must never move the shared file channel position");
        }
    }

    @Test
    void inMemoryRomServesReadsRejectsWritesAndHasNoChannel() throws IOException {
        byte[] data = new byte[0x200];
        byte[] title = "SONIC THE HEDGEHOG 2".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(title, 0, data, 0x120, title.length);
        data[0x18E] = 0x12;
        data[0x18F] = 0x34;
        data[0x1F0] = (byte) 0xAB;
        Rom rom = Rom.fromReader(RomByteReader.fromBytes(data), "synthetic");

        assertTrue(rom.isOpen());
        assertTrue(rom.isInMemory());
        assertNull(rom.getFileChannel());
        assertEquals("synthetic", rom.describe());
        assertEquals(0x200, rom.getSize());
        assertEquals("SONIC THE HEDGEHOG 2", rom.readDomesticName());
        assertEquals(0x1234, rom.readChecksum());
        assertEquals((byte) 0xAB, rom.readByte(0x1F0));
        assertEquals(0xAB00, rom.read16BitAddr(0x1F0));
        assertArrayEquals(new byte[] {(byte) 0xAB, 0}, rom.readBytes(0x1F0, 2));
        assertArrayEquals(data, rom.readAllBytes());
        assertSame(RomByteReader.fromRom(rom), RomByteReader.fromRom(rom));
        assertThrows(IOException.class, () -> rom.readBytes(0x1FF, 2));
        assertThrows(IOException.class, () -> rom.write16BitAddr(0, 0));
        assertThrows(IllegalStateException.class, () -> rom.open("anything"));

        rom.close();
        assertFalse(rom.isOpen());
        assertThrows(IOException.class, () -> rom.readBytes(0, 1));
    }
}
