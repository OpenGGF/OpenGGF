package com.openggf.data.cd;

import com.openggf.game.soniccd.media.CdObservedMediaIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Synthetic CD-ROM CHDs made by chdman 0.289 from generated BIN/CUE sets (no third-party bytes; the
 * CHD oracle lane's {@code mksynth.py}, 2026-11-14). Disc {@code mixed}: MODE1/2352 track of 26 valid
 * sectors (sector 20 has corrupted Q parity and sector 21 a broken sync, so chdman stores both verbatim);
 * AUDIO tracks with stored INDEX 00 gaps of 2, 3 and 1 frames (track 4 silent, giving SELF hunks); and an
 * AUDIO track with a CUE {@code PREGAP} (synthetic). Disc {@code cooked}: MODE1/2048 then AUDIO. The
 * expected values are the {@code scd-cue-layout-content-v1} observations of the source BIN/CUE sets,
 * read by {@link CueDiscImage}.
 */
class TestChdDiscImage {
    private static final String MIXED_FINGERPRINT = "151e8e023b8df17a2b93899549022ab7f3bfded76d16ddfcf11256402733cbf1";
    private static final List<String> MIXED_TRACKS = List.of(
            "b779a0df8446c3c80324a654b6bd375322b59eac91f75637c7b8904758000ae2",
            "d2112cf003b34f40c414ed3fb938b2644c7adf2f022892b3761bead4e775d25f",
            "83572fe446694e0da49ad2def8fb7817685b6946712bc8a799206fb018e69aa9",
            "e003764dabfb8284c5e243d742745776bc8af8280032f20894add3845d872258",
            "5bc3dd9a74df0bdafde7b28eebbd7dd7023d67255af2dd59533864344a54bdcb");
    private static final String COOKED_FINGERPRINT = "f39228ff26e8fa8aa5f1283cbd0e91aa1e5964b273ccd36bb8648e16c9d2693e";

    @TempDir
    Path temp;

    static Path fixture(String name) throws IOException {
        Path target = Path.of("src/test/resources/cd/chd", name);
        assertTrue(Files.isRegularFile(target), "Missing CHD fixture " + target);
        return target;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"mixed-cdzl.chd", "mixed-cdlz.chd", "mixed-cdfl.chd", "mixed-cdzs.chd",
            "mixed-default.chd", "mixed-none.chd"})
    void everyCodecReproducesTheBinCueObservation(String name) throws IOException {
        try (ChdDiscImage disc = ChdDiscImage.open(fixture(name))) {
            CdObservedMediaIdentity identity = CdObservedMediaIdentity.capture(disc, Optional.empty());
            assertEquals(MIXED_FINGERPRINT, identity.normalizedSha256());
            assertEquals(MIXED_TRACKS, identity.tracks().stream()
                    .map(CdObservedMediaIdentity.TrackContent::storedFramesSha256).toList());
            assertEquals(name.equals("mixed-none.chd"), !disc.verifyDataSha1(),
                    "compressed CHDs carry a raw-data SHA-1; chdman's uncompressed form carries none");
            assertEquals(fixture(name).toRealPath().toString(), identity.cue().path());
            assertEquals(1, identity.binaryFiles().size());
            assertEquals(Files.size(fixture(name)), identity.binaryFiles().getFirst().bytes());
        }
    }

    @Test
    void geometryMatchesTheCueSemantics() throws IOException {
        try (ChdDiscImage disc = ChdDiscImage.open(fixture("mixed-default.chd"))) {
            assertEquals(5, disc.tracks().size());
            DiscTrack data = disc.track(1);
            assertEquals(DiscTrack.Mode.MODE1_2352, data.mode());
            assertEquals(26, data.programFrames());
            assertEquals(0, data.discIndex01Frame());
            DiscTrack two = disc.track(2);
            assertEquals(2, two.filePregapFrames());
            assertEquals(0, two.syntheticPregapFrames());
            assertEquals(5, two.programFrames());
            assertEquals(28, two.discIndex01Frame());
            DiscTrack five = disc.track(5);
            assertEquals(0, five.filePregapFrames());
            assertEquals(2, five.syntheticPregapFrames());
            assertEquals(71, five.discIndex01Frame());
            assertArrayEquals(new byte[2 * 2352], disc.readAudioFrames(5, -2, 2), "synthetic gap is silence");
            assertThrows(IllegalArgumentException.class, () -> disc.readRawFrames(5, -3, 1));
            assertThrows(IllegalArgumentException.class, () -> disc.readRawFrames(2, 0, 6));
            assertThrows(IllegalArgumentException.class, () -> disc.readAudioFrames(1, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> disc.track(6));
            byte[] sector = disc.readRawFrames(1, 16, 1);
            assertEquals(0x00, sector[0]);
            assertEquals((byte) 0xFF, sector[1]);
            assertEquals(1, sector[15], "MODE1 header mode byte");
            assertArrayEquals(java.util.Arrays.copyOfRange(sector, 16, 2064), disc.readMode1Bytes(1, 16 * 2048L, 2048));
            byte[] spanning = disc.readMode1Bytes(1, 3 * 2048L + 100, 2048);
            assertArrayEquals(java.util.Arrays.copyOfRange(disc.readRawFrames(1, 3, 1), 116, 2064),
                    java.util.Arrays.copyOfRange(spanning, 0, 1948));
            assertArrayEquals(java.util.Arrays.copyOfRange(disc.readRawFrames(1, 4, 1), 16, 116),
                    java.util.Arrays.copyOfRange(spanning, 1948, 2048));
            IOException brokenSync = assertThrows(IOException.class, () -> disc.readMode1Bytes(1, 21 * 2048L, 16));
            assertTrue(brokenSync.getMessage().contains("sync"), brokenSync.getMessage());
            assertEquals(List.of("cdlz", "cdzl", "cdfl"), disc.compressors());
        }
    }

    @Test
    void cookedMode1TrackKeepsTwoThousandFortyEightByteFrames() throws IOException {
        try (ChdDiscImage disc = ChdDiscImage.open(fixture("cooked-default.chd"))) {
            assertEquals(DiscTrack.Mode.MODE1_2048, disc.track(1).mode());
            assertEquals(2048, disc.readRawFrames(1, 0, 1).length);
            assertArrayEquals(disc.readRawFrames(1, 16, 1), disc.readMode1Bytes(1, 16 * 2048L, 2048));
            assertEquals(COOKED_FINGERPRINT, CdObservedMediaIdentity.capture(disc, Optional.empty()).normalizedSha256());
        }
    }

    @Test
    void openSelectsTheContainerByContent() throws IOException {
        Path renamed = Files.copy(fixture("mixed-cdzl.chd"), temp.resolve("disc.cue"));
        try (DiscImage disc = DiscImage.open(renamed)) {
            assertInstanceOf(ChdDiscImage.class, disc);
            disc.requireDescriptor(renamed);
            assertThrows(IOException.class, () -> disc.requireDescriptor(fixture("mixed-cdzl.chd")));
        }
        Path cue = temp.resolve("audio.cue");
        Files.write(temp.resolve("a.bin"), new byte[2352 * 3]);
        Files.writeString(cue, "FILE \"a.bin\" BINARY\n  TRACK 01 AUDIO\n    INDEX 01 00:00:00\n");
        try (DiscImage disc = DiscImage.open(cue)) {
            assertInstanceOf(CueDiscImage.class, disc);
        }
    }

    @Test
    void descriptorIsTheTrackMetadata() throws IOException {
        try (ChdDiscImage disc = ChdDiscImage.open(fixture("mixed-cdzl.chd"))) {
            String text = new String(disc.readDescriptorBytes(), java.nio.charset.StandardCharsets.US_ASCII);
            assertTrue(text.startsWith("CHT2TRACK:1 TYPE:MODE1_RAW"), text);
            assertTrue(text.contains("TRACK:5 TYPE:AUDIO SUBTYPE:NONE FRAMES:9 PREGAP:2 PGTYPE:MODE1"), text);
            byte[] copy = disc.readDescriptorBytes();
            copy[0] = 0;
            assertEquals('C', disc.readDescriptorBytes()[0]);
        }
    }

    // --- Untrusted input -------------------------------------------------------------------------------

    @Test
    void refusesOtherVersionsParentsCodecsAndNonCdImages() throws IOException {
        assertRefused(patched(header -> header.putInt(12, 4)), "version 4");
        assertRefused(patched(header -> header.putInt(12, 3)), "version 3");
        assertRefused(patched(header -> header.put(104, (byte) 1)), "parent");
        assertRefused(patched(header -> header.putInt(16, tag("zlib"))), "'zlib'");
        assertRefused(patched(header -> header.putInt(60, 512)), "Not a CD-ROM CHD");
        assertRefused(patched(header -> header.putInt(56, 2448 * 1000)), "Not a CD-ROM CHD");
        assertRefused(patched(header -> header.put(0, (byte) 'X')), "Not a CHD");
        assertRefused(patched(header -> header.put(84, (byte) (header.get(84) ^ 1))), "SHA-1");
    }

    @Test
    void refusesACorruptMapOrHunk() throws IOException {
        byte[] bytes = Files.readAllBytes(fixture("mixed-cdzl.chd"));
        ByteBuffer header = ByteBuffer.wrap(bytes);
        int mapOffset = (int) header.getLong(40);
        byte[] badMap = bytes.clone();
        badMap[mapOffset + 10] ^= 0x55;
        assertRefused(write("bad-map-crc.chd", badMap), "map");
        byte[] badMapBits = bytes.clone();
        badMapBits[mapOffset + 20] ^= 0x10;
        assertThrows(IOException.class, () -> ChdDiscImage.open(write("bad-map.chd", badMapBits)).close());
        long firstHunk = ((header.getShort(mapOffset + 4) & 0xFFFFL) << 32) | Integer.toUnsignedLong(header.getInt(mapOffset + 6));
        byte[] badHunk = bytes.clone();
        badHunk[(int) firstHunk + 40] ^= 0x01;
        try (ChdDiscImage disc = ChdDiscImage.open(write("bad-hunk.chd", badHunk))) {
            IOException failure = assertThrows(IOException.class, () -> disc.readRawFrames(1, 0, 1));
            assertTrue(failure.getMessage().contains("CRC") || failure.getMessage().contains("deflate"),
                    failure.getMessage());
        }
        byte[] parentHunk = Files.readAllBytes(fixture("mixed-none.chd"));
        ByteBuffer none = ByteBuffer.wrap(parentHunk);
        none.putInt((int) none.getLong(40), 0x7FFFFFFF);
        try (ChdDiscImage disc = ChdDiscImage.open(write("far-hunk.chd", parentHunk))) {
            IOException failure = assertThrows(IOException.class, () -> disc.readRawFrames(1, 0, 1));
            assertTrue(failure.getMessage().contains("outside"), failure.getMessage());
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"mixed-cdzl.chd", "mixed-none.chd", "mixed-default.chd"})
    void truncatedFilesFailCleanly(String name) throws IOException {
        byte[] bytes = Files.readAllBytes(fixture(name));
        for (int length : new int[] {0, 8, 15, 16, 100, 123, 124, 140, 400, bytes.length / 3, bytes.length / 2,
                bytes.length - 2000, bytes.length - 1}) {
            Path cut = write("cut-" + length + ".chd", java.util.Arrays.copyOf(bytes, length));
            assertThrows(IOException.class, () -> {
                try (DiscImage disc = DiscImage.open(cut)) {
                    CdObservedMediaIdentity.capture(disc, Optional.empty());
                }
            }, "truncated to " + length);
        }
    }

    /** Random byte flips must surface as {@link IOException}s or an unchanged/changed observation, never a crash. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"mixed-default.chd", "mixed-cdzs.chd", "mixed-cdlz.chd", "mixed-cdfl.chd"})
    void corruptedBytesNeverEscapeAsUncheckedFailures(String name) throws IOException {
        byte[] bytes = Files.readAllBytes(fixture(name));
        Random random = new Random(name.hashCode());
        Path target = temp.resolve("flip.chd");
        for (int round = 0; round < 120; round++) {
            byte[] copy = bytes.clone();
            int flips = 1 + random.nextInt(3);
            for (int i = 0; i < flips; i++) {
                copy[random.nextInt(copy.length)] ^= (byte) (1 << random.nextInt(8));
            }
            Files.write(target, copy);
            try (DiscImage disc = DiscImage.open(target)) {
                CdObservedMediaIdentity.capture(disc, Optional.empty());
            } catch (IOException refused) {
                // Expected for most flips.
            }
        }
    }

    @Test
    void changedFileIsDetected() throws IOException {
        Path copy = Files.copy(fixture("mixed-cdzl.chd"), temp.resolve("changing.chd"));
        try (ChdDiscImage disc = ChdDiscImage.open(copy)) {
            disc.readRawFrames(2, 0, 1);
            Files.write(copy, new byte[] {0}, StandardOpenOption.APPEND);
            IOException changed = assertThrows(IOException.class, () -> disc.readRawFrames(2, 0, 1));
            assertTrue(changed.getMessage().contains("changed"), changed.getMessage());
        }
        Path touched = Files.copy(fixture("mixed-cdzl.chd"), temp.resolve("touched.chd"));
        try (ChdDiscImage disc = ChdDiscImage.open(touched)) {
            Files.setLastModifiedTime(touched, FileTime.fromMillis(1_000_000L));
            assertThrows(IOException.class, () -> disc.sourceFiles().getFirst().read(0, 8));
        }
        try (ChdDiscImage disc = ChdDiscImage.open(fixture("mixed-cdzl.chd"))) {
            disc.close();
            assertThrows(IOException.class, () -> disc.readRawFrames(2, 0, 1));
        }
    }

    @Test
    void sourceFileIsTheWholeChd() throws IOException {
        Path chd = fixture("mixed-cdzs.chd");
        try (ChdDiscImage disc = ChdDiscImage.open(chd)) {
            var source = disc.sourceFiles().getFirst();
            assertEquals(Files.size(chd), source.bytes());
            assertArrayEquals(Files.readAllBytes(chd), source.read(0, (int) source.bytes()));
            assertThrows(IllegalArgumentException.class, () -> source.read(source.bytes() - 1, 2));
        }
    }

    private interface HeaderEdit {
        void apply(ByteBuffer header);
    }

    private Path patched(HeaderEdit edit) throws IOException {
        byte[] bytes = Files.readAllBytes(fixture("mixed-cdzl.chd"));
        edit.apply(ByteBuffer.wrap(bytes));
        return write("patched-" + System.nanoTime() + ".chd", bytes);
    }

    private Path write(String name, byte[] bytes) throws IOException {
        return Files.write(temp.resolve(name), bytes);
    }

    private static void assertRefused(Path chd, String reason) {
        IOException failure = assertThrows(IOException.class, () -> ChdDiscImage.open(chd).close());
        assertTrue(failure.getMessage().contains(reason), "expected '" + reason + "' in: " + failure.getMessage());
    }

    private static int tag(String text) {
        return ByteBuffer.wrap(text.getBytes(java.nio.charset.StandardCharsets.US_ASCII)).getInt();
    }
}
