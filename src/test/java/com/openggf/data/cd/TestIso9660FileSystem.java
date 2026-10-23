package com.openggf.data.cd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static com.openggf.data.cd.SyntheticCdImages.*;
import static org.junit.jupiter.api.Assertions.*;

class TestIso9660FileSystem {
    @TempDir
    Path directory;

    @Test
    void listsDirectoriesRecursivelyAndResolvesExactAndLatestVersions() throws IOException {
        try (CueDiscImage disc = CueDiscImage.open(writeDisc(directory, iso(), true))) {
            Iso9660FileSystem fs = Iso9660FileSystem.open(disc, 1);
            assertEquals("SYNTHETIC_DISC", fs.volumeIdentifier());
            assertEquals(32, fs.volumeBlocks());
            assertEquals(20, fs.root().extentBlock());
            assertEquals(2 * BLOCK, fs.root().size());
            assertEquals(List.of("/CROSS.BIN;1", "/VERS.TXT;1", "/VERS.TXT;2", "/SUB", "/EMPTY.DAT;1", "/LATER.TXT;1"),
                    fs.listDirectory("/").stream().map(Iso9660FileSystem.Entry::path).toList());
            assertEquals(List.of("/SUB/NESTED.BIN;1"),
                    fs.listDirectory("sub").stream().map(Iso9660FileSystem.Entry::path).toList());
            assertEquals(6, fs.files().size());
            assertTrue(fs.files().stream().noneMatch(Iso9660FileSystem.Entry::directory));
            assertEquals("/VERS.TXT;2", fs.entry("vers.txt").path());
            assertEquals(2, fs.entry("/VERS.TXT;2").version());
            assertArrayEquals(new byte[] {1, 2, 3}, fs.readFile("VERS.TXT;1"));
            assertArrayEquals(new byte[] {4, 5, 6}, fs.readFile("VERS.TXT"));
            assertArrayEquals(new byte[] {8, 7, 6, 5}, fs.readFile("SUB/NESTED.BIN"));
            assertEquals(0, fs.readFile("EMPTY.DAT").length);
            assertThrows(UnsupportedOperationException.class, () -> fs.files().clear());
            assertThrows(IOException.class, () -> fs.entry("MISSING.BIN"));
            assertThrows(IOException.class, () -> fs.listDirectory("CROSS.BIN"));
            assertThrows(IOException.class, () -> fs.readFile("SUB"));
            assertThrows(IllegalArgumentException.class, () -> fs.entry("../CROSS.BIN"));
            assertThrows(IllegalArgumentException.class, () -> fs.entry("SUB//NESTED.BIN"));
        }
    }

    @Test
    void boundedReadsCrossSectorsWithoutExposingAdjacentExtents() throws IOException {
        byte[] bytes = iso();
        try (CueDiscImage disc = CueDiscImage.open(writeDisc(directory, bytes, false))) {
            Iso9660FileSystem fs = Iso9660FileSystem.open(disc, 1);
            assertArrayEquals(Arrays.copyOfRange(bytes, 24 * BLOCK + 2040, 24 * BLOCK + 2080),
                    fs.readFile("CROSS.BIN", 2040, 40));
            assertEquals(0, fs.readFile("CROSS.BIN", 3000, 0).length);
            assertThrows(IllegalArgumentException.class, () -> fs.readFile("CROSS.BIN", 2999, 2));
            assertThrows(IllegalArgumentException.class, () -> fs.readFile("CROSS.BIN", Long.MAX_VALUE, 1));
            assertThrows(IllegalArgumentException.class, () -> fs.readFile("CROSS.BIN", -1, 1));
            assertThrows(IllegalArgumentException.class, () -> fs.readFile("CROSS.BIN", 0, CueDiscImage.MAX_READ_BYTES + 1));
        }
    }

    @Test
    void anEmptyFileCanHaveNoAllocatedExtent() throws IOException {
        byte[] bytes = iso();
        Arrays.fill(bytes, 21 * BLOCK, 22 * BLOCK, (byte) 0);
        directory(bytes, 21, record("ZERO.DAT;1", 0, 0, false));
        try (CueDiscImage disc = CueDiscImage.open(writeDisc(directory, bytes, false))) {
            Iso9660FileSystem fs = Iso9660FileSystem.open(disc, 1);
            assertEquals(0, fs.readFile("ZERO.DAT").length);
            try (InputStream stream = fs.openFile("ZERO.DAT")) {
                assertEquals(-1, stream.read());
            }
        }
    }

    @Test
    void streamsHaveIndependentPositionsAndDoNotOwnTheDisc() throws IOException {
        byte[] bytes = iso();
        try (CueDiscImage disc = CueDiscImage.open(writeDisc(directory, bytes, true))) {
            Iso9660FileSystem fs = Iso9660FileSystem.open(disc, 1);
            InputStream first = fs.openFile("CROSS.BIN");
            try (InputStream second = fs.openFile("CROSS.BIN")) {
                assertEquals(3, first.read());
                assertEquals(3, second.read());
                assertEquals(2040, first.skip(2040));
                assertArrayEquals(Arrays.copyOfRange(bytes, 24 * BLOCK + 2041, 24 * BLOCK + 2061), first.readNBytes(20));
                assertEquals(0, second.skip(-1));
                assertEquals(2999, second.available());
            }
            first.close();
            assertThrows(IOException.class, first::read);
            assertArrayEquals(new byte[] {4, 5, 6}, fs.readFile("VERS.TXT"));
            try (InputStream empty = fs.openFile("EMPTY.DAT")) {
                assertEquals(-1, empty.read());
                assertEquals(0, empty.read(new byte[2], 0, 0));
                assertEquals(0, empty.skip(100));
            }
        }
    }

    @Test
    void discCloseAndMediaChangeInvalidateBufferedStreamsAndMetadata() throws IOException {
        Path cue = writeDisc(directory, iso(), false);
        CueDiscImage disc = CueDiscImage.open(cue);
        Iso9660FileSystem fs = Iso9660FileSystem.open(disc, 1);
        InputStream stream = fs.openFile("CROSS.BIN");
        assertEquals(3, stream.read());
        disc.close();
        assertThrows(IOException.class, stream::read);
        assertThrows(IOException.class, () -> fs.readFile("EMPTY.DAT"));
        assertThrows(IOException.class, fs::files);
        assertThrows(IOException.class, () -> fs.entry("VERS.TXT"));
        stream.close();
        try (CueDiscImage reopened = CueDiscImage.open(cue)) {
            Iso9660FileSystem next = Iso9660FileSystem.open(reopened, 1);
            try (InputStream buffered = next.openFile("CROSS.BIN")) {
                buffered.read();
                Files.write(directory.resolve("data.bin"), new byte[BLOCK]);
                assertThrows(IOException.class, buffered::read);
                assertThrows(IOException.class, () -> next.listDirectory("/"));
            }
        }
    }

    @Test
    void aLargeFileIsStreamedInSmallChunksAndWholeFileReadsAreRejected() throws IOException {
        byte[] bytes = iso();
        long size = (long) CueDiscImage.MAX_READ_BYTES + 8192;
        long blocks = 24 + size / BLOCK;
        both32(bytes, 16 * BLOCK + 80, blocks);
        Arrays.fill(bytes, 20 * BLOCK + 68, 22 * BLOCK, (byte) 0);
        directoryAt(bytes, 20 * BLOCK + 68, record("OPENING.STM;1", 24, size, false));
        Path cue = writeDisc(directory, bytes, false);
        try (FileChannel channel = FileChannel.open(directory.resolve("data.bin"), StandardOpenOption.WRITE)) {
            channel.position(blocks * BLOCK - 1);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {99}));
        }
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            Iso9660FileSystem fs = Iso9660FileSystem.open(disc, 1);
            assertThrows(IOException.class, () -> fs.readFile("OPENING.STM"));
            try (InputStream stream = fs.openFile("OPENING.STM")) {
                assertEquals(size - 1, stream.skip(size - 1));
                assertEquals(99, stream.read());
                assertEquals(-1, stream.read());
                assertEquals(0, stream.read(new byte[1], 0, 0));
            }
        }
    }

    @Test
    void invalidVolumeDescriptorsAndBothEndianFieldsAreRejected() throws IOException {
        List<Consumer<byte[]>> corruptions = List.of(
                b -> b[16 * BLOCK + 1] = 'X',
                b -> b[16 * BLOCK + 6] = 2,
                b -> b[16 * BLOCK + 84] ^= 1,
                b -> both32(b, 16 * BLOCK + 80, 33),
                b -> both32(b, 16 * BLOCK + 80, 17),
                b -> both16(b, 16 * BLOCK + 128, 1024),
                b -> both16(b, 16 * BLOCK + 120, 2),
                b -> both16(b, 16 * BLOCK + 124, 2),
                b -> b[16 * BLOCK + 881] = 2,
                b -> b[17 * BLOCK] = 1,
                b -> b[17 * BLOCK] = 4,
                b -> b[16 * BLOCK + 156 + 25] = 0,
                b -> b[16 * BLOCK + 156 + 33] = 1,
                b -> both32(b, 16 * BLOCK + 156 + 10, 0),
                b -> b[16 * BLOCK + 140] = 32);
        for (int i = 0; i < corruptions.size(); i++) {
            byte[] bytes = iso();
            corruptions.get(i).accept(bytes);
            try (CueDiscImage disc = CueDiscImage.open(writeDisc(directory, bytes, false))) {
                assertThrows(IOException.class, () -> Iso9660FileSystem.open(disc, 1), "corruption " + i);
            }
        }
    }

    @Test
    void invalidDirectoryRecordsExtentsAndUnsupportedFileShapesAreRejected() throws IOException {
        int file = 20 * BLOCK + 68;
        List<Consumer<byte[]>> corruptions = List.of(
                b -> b[file] = 33,
                b -> b[file + 32] = (byte) 250,
                b -> b[file + 1] = 1,
                b -> b[file + 25] = (byte) 0x80,
                b -> b[file + 25] = 4,
                b -> b[file + 26] = 1,
                b -> b[file + 27] = 1,
                b -> both16(b, file + 28, 2),
                b -> b[file + 6] ^= 1,
                b -> both32(b, file + 2, 31),
                b -> both32(b, file + 2, 0xffffffffL),
                b -> both32(b, file + 10, 0xffffffffL),
                b -> b[file + 33] = '/',
                b -> b[file + 43] = '0',
                b -> b[file + 44 + 43] = 1,
                b -> b[20 * BLOCK + 33] = 1,
                b -> both32(b, 20 * BLOCK + 34 + 2, 22),
                b -> b[21 * BLOCK + 80] = 1);
        for (int i = 0; i < corruptions.size(); i++) {
            byte[] bytes = iso();
            corruptions.get(i).accept(bytes);
            try (CueDiscImage disc = CueDiscImage.open(writeDisc(directory, bytes, false))) {
                assertThrows(IOException.class, () -> Iso9660FileSystem.open(disc, 1), "corruption " + i);
            }
        }
    }

    @Test
    void fileVersionsAreNumericBoundedAndUnambiguous() throws IOException {
        byte[] bytes = iso();
        Arrays.fill(bytes, 21 * BLOCK, 22 * BLOCK, (byte) 0);
        directory(bytes, 21, record("PAD.TXT;00002", 28, 5, false));
        try (CueDiscImage disc = CueDiscImage.open(writeDisc(directory, bytes, false))) {
            Iso9660FileSystem fs = Iso9660FileSystem.open(disc, 1);
            assertEquals("/PAD.TXT;2", fs.entry("PAD.TXT;00002").path());
            assertArrayEquals("later".getBytes(java.nio.charset.StandardCharsets.US_ASCII), fs.readFile("PAD.TXT"));
        }
        for (String name : new String[] {"BAD.TXT;0", "BAD.TXT;32768", "BAD.TXT;100000", "BAD.TXT;", "BAD.TXT", "BAD.TXT;-1",
                "VERS.TXT;00002"}) {
            bytes = iso();
            Arrays.fill(bytes, 21 * BLOCK, 22 * BLOCK, (byte) 0);
            directory(bytes, 21, record(name, 28, 5, false));
            try (CueDiscImage disc = CueDiscImage.open(writeDisc(directory, bytes, false))) {
                assertThrows(IOException.class, () -> Iso9660FileSystem.open(disc, 1), name);
            }
        }
    }

    @Test
    void duplicateIdentifiersAndDirectoryCyclesAreRejected() throws IOException {
        for (byte[] bad : new byte[][] {
                record("LOOP", 20, 2 * BLOCK, true), record("ALIAS", 22, BLOCK, true),
                record("VERS.TXT;2", 27, 3, false)}) {
            byte[] bytes = iso();
            Arrays.fill(bytes, 21 * BLOCK, 22 * BLOCK, (byte) 0);
            directory(bytes, 21, bad);
            try (CueDiscImage disc = CueDiscImage.open(writeDisc(directory, bytes, false))) {
                assertThrows(IOException.class, () -> Iso9660FileSystem.open(disc, 1));
            }
        }
    }

    @Test
    void aDirectoryRecordMayNotCrossASectorBoundary() throws IOException {
        byte[] bytes = iso();
        Arrays.fill(bytes, 20 * BLOCK + 68, 22 * BLOCK, (byte) 0);
        int offset = 20 * BLOCK + 68;
        for (int i = 0; i < 47; i++) {
            byte[] entry = record("F%04d.A;1".formatted(i), 24, 1, false);
            directoryAt(bytes, offset, entry);
            offset += entry.length;
        }
        assertEquals(2042, offset - 20 * BLOCK);
        directoryAt(bytes, offset, record("A_VERY_LONG_FILENAME.TXT;1", 24, 1, false));
        try (CueDiscImage disc = CueDiscImage.open(writeDisc(directory, bytes, false))) {
            assertThrows(IOException.class, () -> Iso9660FileSystem.open(disc, 1));
        }
    }

    private static void directoryAt(byte[] bytes, int offset, byte[] record) {
        System.arraycopy(record, 0, bytes, offset, record.length);
    }
}
