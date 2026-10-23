package com.openggf.data.cd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.OptionalLong;

import static com.openggf.data.cd.SyntheticCdImages.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCueDiscImage {
    @TempDir
    Path directory;

    @Test
    void rawMode1ReadsStripHeadersAcrossSectorBoundariesAndPreserveRawBytes() throws IOException {
        byte[] cooked = new byte[3 * BLOCK];
        for (int i = 0; i < cooked.length; i++) {
            cooked[i] = (byte) (i * 11);
        }
        Path cue = writeDisc(directory, cooked, true);
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            assertEquals(3, disc.track(1).programFrames());
            assertArrayEquals(Arrays.copyOfRange(cooked, 2039, 4110), disc.readMode1Bytes(1, 2039, 2071));
            assertArrayEquals(Arrays.copyOfRange(raw(cooked), 2352, 4704), disc.readRawFrames(1, 1, 1));
            assertArrayEquals(new byte[0], disc.readMode1Bytes(1, cooked.length, 0));
            assertThrows(UnsupportedOperationException.class, () -> disc.tracks().clear());
            assertThrows(IllegalArgumentException.class, () -> disc.readAudioFrames(1, 0, 1));
        }
    }

    @Test
    void cookedMode1ReadsAreByteExact() throws IOException {
        byte[] cooked = new byte[2 * BLOCK];
        cooked[2047] = 42;
        cooked[2048] = 43;
        try (CueDiscImage disc = CueDiscImage.open(writeDisc(directory, cooked, false))) {
            assertArrayEquals(new byte[] {42, 43}, disc.readMode1Bytes(1, 2047, 2));
            assertArrayEquals(cooked, disc.readRawFrames(1, 0, 2));
        }
    }

    @Test
    void sharedFileAssignsStoredPregapToFollowingTrack() throws IOException {
        byte[] bytes = audioFrames(6);
        Files.write(directory.resolve("shared.bin"), bytes);
        Path cue = cue(directory, """
                FILE "shared.bin" BINARY
                  TRACK 01 AUDIO
                    INDEX 01 00:00:00
                  TRACK 02 AUDIO
                    INDEX 00 00:00:02
                    INDEX 01 00:00:04
                """);
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            assertEquals(2, disc.track(1).programFrames());
            assertEquals(2, disc.track(2).filePregapFrames());
            assertEquals(0, disc.track(2).syntheticPregapFrames());
            assertEquals(4, disc.track(2).discIndex01Frame());
            assertArrayEquals(Arrays.copyOfRange(bytes, 2 * 2352, 6 * 2352), disc.readAudioFrames(2, -2, 4));
            assertThrows(IllegalArgumentException.class, () -> disc.readRawFrames(1, 1, 2));
            assertThrows(IllegalArgumentException.class, () -> disc.readRawFrames(2, -3, 1));
        }
    }

    @Test
    void syntheticAudioGapIsSilenceAndStoredGapKeepsItsSamples() throws IOException {
        byte[] bytes = audioFrames(4);
        Files.write(directory.resolve("audio.bin"), bytes);
        Path cue = cue(directory, """
                FILE "audio.bin" BINARY
                  TRACK 01 AUDIO
                    PREGAP 00:00:02
                    INDEX 00 00:00:00
                    INDEX 01 00:00:01
                """);
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            assertEquals(3, disc.track(1).pregapFrames());
            byte[] expected = new byte[4 * 2352];
            System.arraycopy(bytes, 0, expected, 2 * 2352, 2 * 2352);
            assertArrayEquals(expected, disc.readAudioFrames(1, -3, 4));
        }
    }

    @Test
    void separateFilesAndMixedSizeSharedFileUseTheSameTrackRelativeGeometry() throws IOException {
        byte[] data = new byte[2 * BLOCK];
        data[0] = 23;
        byte[] audio = audioFrames(3);
        byte[] shared = Arrays.copyOf(data, data.length + audio.length);
        System.arraycopy(audio, 0, shared, data.length, audio.length);
        Files.write(directory.resolve("shared.bin"), shared);
        Path cue = cue(directory, """
                FILE "shared.bin" BINARY
                  TRACK 01 MODE1/2048
                    INDEX 01 00:00:00
                  TRACK 02 AUDIO
                    INDEX 00 00:00:02
                    INDEX 01 00:00:03
                """);
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            assertArrayEquals(data, disc.readMode1Bytes(1, 0, data.length));
            assertArrayEquals(audio, disc.readAudioFrames(2, -1, 3));
            assertEquals(2, disc.track(2).programFrames());
            assertEquals(3, disc.track(2).discIndex01Frame());
        }
        Files.write(directory.resolve("data.bin"), data);
        Files.write(directory.resolve("audio.bin"), audio);
        cue = cue(directory, """
                FILE "data.bin" BINARY
                  TRACK 01 MODE1/2048
                    INDEX 01 00:00:00
                FILE "audio.bin" BINARY
                  TRACK 02 AUDIO
                    INDEX 00 00:00:00
                    INDEX 01 00:00:01
                """);
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            assertArrayEquals(audio, disc.readAudioFrames(2, -1, 3));
            assertEquals(3, disc.track(2).discIndex01Frame());
        }
    }

    @Test
    void boundsAndCloseAreEnforcedIncludingEmptyReads() throws IOException {
        Path cue = writeDisc(directory, new byte[BLOCK], false);
        CueDiscImage disc = CueDiscImage.open(cue);
        assertThrows(IllegalArgumentException.class, () -> disc.readMode1Bytes(1, Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> disc.readMode1Bytes(1, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> disc.readMode1Bytes(1, 0, CueDiscImage.MAX_READ_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> disc.readRawFrames(1, 0, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> disc.readRawFrames(1, Long.MIN_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> disc.track(2));
        disc.close();
        disc.close();
        assertThrows(IOException.class, () -> disc.readMode1Bytes(1, 0, 0));
        assertThrows(IOException.class, () -> disc.readRawFrames(1, 0, 0));
        try (CueDiscImage reopened = CueDiscImage.open(cue)) {
            assertEquals(1, reopened.track(1).programFrames());
        }
    }

    @Test
    void changedTruncatedAndReplacedMediaAreRejected() throws IOException {
        Path cue = writeDisc(directory, new byte[2 * BLOCK], false);
        Path bin = directory.resolve("data.bin");
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            Files.setLastModifiedTime(bin, FileTime.fromMillis(Files.getLastModifiedTime(bin).toMillis() + 10000));
            assertThrows(IOException.class, () -> disc.readMode1Bytes(1, 0, 1));
        }
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            Files.write(bin, new byte[BLOCK]);
            assertThrows(IOException.class, () -> disc.readRawFrames(1, 0, 1));
        }
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            Files.move(bin, directory.resolve("old.bin"));
            Files.write(bin, new byte[BLOCK]);
            assertThrows(IOException.class, () -> disc.readMode1Bytes(1, 0, 1));
        }
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            Files.writeString(cue, Files.readString(cue) + "REM changed\n");
            assertThrows(IOException.class, () -> disc.readMode1Bytes(1, 0, 1));
        }
    }

    @Test
    void malformedDirectivesAndGeometryFailAtOpen() throws IOException {
        Files.write(directory.resolve("data.bin"), new byte[4 * BLOCK]);
        String prefix = "FILE \"data.bin\" BINARY\n TRACK 01 MODE1/2048\n";
        String[] malformed = {
                prefix, prefix + " INDEX 01 00:00:00\n INDEX 01 00:00:01\n",
                prefix + " INDEX 01 00:60:00\n", prefix + " INDEX 01 00:00:75\n",
                prefix + " INDEX 01 00:00:01\n", prefix + " INDEX 00 00:00:01\n INDEX 01 00:00:00\n",
                prefix + " INDEX 01 00:00:00\n FLAGS DCP\n", prefix + " INDEX 02 00:00:00\n",
                prefix + " INDEX 01 00:00:00\n POSTGAP 00:00:01\n",
                prefix + " INDEX 01 00:00:00\n PREGAP 00:00:01\n",
                prefix + " INDEX 01 00:00:00\n TRACK 02 MODE1/2048\n INDEX 01 00:00:00\n",
                prefix + " INDEX 01 00:00:00\n TRACK 02 MODE1/2048\n INDEX 01 00:00:05\n",
                prefix + " INDEX 01 00:00:00\n TRACK 03 MODE1/2048\n INDEX 01 00:00:02\n",
                "FILE \"data.bin\" WAVE\n", "FILE \"data.bin\" BINARY\n TRACK 01 MODE2/2352\n INDEX 01 00:00:00\n",
                "FILE \"data.bin\" BINARY\n TRACK 00 AUDIO\n INDEX 01 00:00:00\n",
                prefix + " INDEX 01 00:00:00\n FILE \"data.bin\" BINARY\n",
                "FILE \"missing.bin\" BINARY\n TRACK 01 AUDIO\n INDEX 01 00:00:00\n"
        };
        for (String text : malformed) {
            Path cue = cue(directory, text);
            assertThrows(IOException.class, () -> CueDiscImage.open(cue), text);
        }
        Files.write(directory.resolve("data.bin"), new byte[BLOCK + 1]);
        assertThrows(IOException.class, () -> CueDiscImage.open(cue(directory, prefix + " INDEX 01 00:00:00\n")));
    }

    @Test
    void unsafeNamesAndSymlinkEscapesAreRejected() throws IOException {
        for (String name : new String[] {"../escape.bin", "/tmp/escape.bin", "C:\\escape.bin", "a/../b.bin", "./b.bin"}) {
            Path cue = cue(directory, "FILE \"" + name + "\" BINARY\n TRACK 01 AUDIO\n INDEX 01 00:00:00\n");
            assertThrows(IOException.class, () -> CueDiscImage.open(cue));
        }
        Path outside = Files.createTempFile(directory.getParent(), "cd-outside-", ".bin");
        try {
            Files.write(outside, new byte[2352]);
            try {
                Files.createSymbolicLink(directory.resolve("escape.bin"), outside);
            } catch (UnsupportedOperationException | IOException exception) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symlinks unavailable: " + exception);
            }
            assertThrows(IOException.class, () -> CueDiscImage.open(cue(directory,
                    "FILE \"escape.bin\" BINARY\n TRACK 01 AUDIO\n INDEX 01 00:00:00\n")));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void rawReadsPreserveBadHeadersButMode1ReadsRejectThem() throws IOException {
        byte[] bytes = raw(new byte[BLOCK]);
        bytes[15] = 2;
        Files.write(directory.resolve("raw.bin"), bytes);
        Path cue = cue(directory, "FILE \"raw.bin\" BINARY\n TRACK 01 MODE1/2352\n INDEX 01 00:00:00\n");
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            assertArrayEquals(bytes, disc.readRawFrames(1, 0, 1));
            assertThrows(IOException.class, () -> disc.readMode1Bytes(1, 0, 1));
        }
    }

    @Test
    void syntheticDataPregapHasNoInventedRawRepresentation() throws IOException {
        Files.write(directory.resolve("data.bin"), new byte[BLOCK]);
        Path cue = cue(directory, "FILE \"data.bin\" BINARY\n TRACK 01 MODE1/2048\n PREGAP 00:00:01\n INDEX 01 00:00:00\n");
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            assertThrows(IOException.class, () -> disc.readRawFrames(1, -1, 1));
            assertArrayEquals(new byte[BLOCK], disc.readMode1Bytes(1, 0, BLOCK));
        }
    }

    @Test
    void cueTextLimitsAndInvalidEncodingAreRejected() throws IOException {
        Path cue = cue(directory, "REM " + "x".repeat(4096));
        assertThrows(IOException.class, () -> CueDiscImage.open(cue));
        Files.write(cue, new byte[1024 * 1024 + 1]);
        assertThrows(IOException.class, () -> CueDiscImage.open(cue));
        Files.write(cue, new byte[] {(byte) 0xc3, 0x28});
        assertThrows(IOException.class, () -> CueDiscImage.open(cue));
    }

    @Test
    void immutableTrackGeometryRejectsOverflowAndNegativeComponents() {
        assertThrows(IllegalArgumentException.class, () -> new DiscTrack(1, DiscTrack.Mode.AUDIO,
                "audio.bin", OptionalLong.of(0), 1, Long.MAX_VALUE, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new DiscTrack(1, DiscTrack.Mode.AUDIO,
                "audio.bin", OptionalLong.empty(), 0, 0, Long.MAX_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> new DiscTrack(1, DiscTrack.Mode.AUDIO,
                "audio.bin", OptionalLong.of(2), 1, 0, 1, 0));
    }

    private static byte[] audioFrames(int count) {
        byte[] bytes = new byte[count * 2352];
        for (int i = 0; i < count; i++) {
            Arrays.fill(bytes, i * 2352, (i + 1) * 2352, (byte) (i + 1));
        }
        return bytes;
    }
}
