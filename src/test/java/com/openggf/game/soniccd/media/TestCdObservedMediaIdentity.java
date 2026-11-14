package com.openggf.game.soniccd.media;

import com.openggf.data.cd.CueDiscImage;
import com.openggf.game.soniccd.CdReleaseVariant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestCdObservedMediaIdentity {
    @TempDir Path directory;

    private CdObservedMediaIdentity identity(Path cue) throws IOException {
        try (var disc = CueDiscImage.open(cue)) {
            return CdObservedMediaIdentity.capture(disc, Optional.empty());
        }
    }

    @Test void captureRejectsCueFromAnotherDiscOwner() throws IOException {
        Path first = SyntheticProgramDisc.write(directory.resolve("first"), CdReleaseVariant.USA);
        Path second = SyntheticProgramDisc.write(directory.resolve("second"), CdReleaseVariant.JAPAN);
        Files.writeString(second, "REM different source\n" + Files.readString(second));
        var firstIdentity = identity(first);
        var secondIdentity = identity(second);
        assertNotEquals(firstIdentity.cue().path(), secondIdentity.cue().path());
        assertNotEquals(firstIdentity.cue().sha256(), secondIdentity.cue().sha256());
        assertNotEquals(firstIdentity.binaryFiles().getFirst().sha256(), secondIdentity.binaryFiles().getFirst().sha256());
        assertNotEquals(firstIdentity.normalizedSha256(), secondIdentity.normalizedSha256());
        try (var disc = CueDiscImage.open(first)) {
            assertThrows(IOException.class, () -> CdObservedMediaIdentity.capture(disc, second, Optional.empty()));
            assertEquals(firstIdentity, CdObservedMediaIdentity.capture(disc, first, Optional.empty()));
        }
    }

    @Test void retargetedBinarySymlinkKeepsTheBoundCanonicalSourceAndHash() throws IOException, NoSuchAlgorithmException {
        byte[] first = new byte[2352 * 2];
        byte[] second = new byte[first.length];
        java.util.Arrays.fill(first, (byte) 0x31);
        java.util.Arrays.fill(second, (byte) 0x62);
        Path original = Files.write(directory.resolve("original.bin"), first);
        Path replacement = Files.write(directory.resolve("replacement.bin"), second);
        Path link = Files.createSymbolicLink(directory.resolve("audio.bin"), original.getFileName());
        Path cue = Files.writeString(directory.resolve("disc.cue"),
                "FILE \"audio.bin\" BINARY\n TRACK 01 AUDIO\n INDEX 00 00:00:00\n INDEX 01 00:00:01\n");
        CueDiscImage disc = CueDiscImage.open(cue);
        try {
            var before = CdObservedMediaIdentity.capture(disc, cue, Optional.empty());
            Files.delete(link);
            Files.createSymbolicLink(link, replacement.getFileName());
            var after = CdObservedMediaIdentity.capture(disc, cue, Optional.empty());
            assertEquals(before, after);
            assertEquals(original.toRealPath().toString(), after.binaryFiles().getFirst().path());
            assertEquals(hash(first), after.binaryFiles().getFirst().sha256());
            assertNotEquals(hash(second), after.binaryFiles().getFirst().sha256());
            assertArrayEquals(first, disc.readAudioFrames(1, -1, 2));
            var reopened = identity(cue);
            assertEquals(replacement.toRealPath().toString(), reopened.binaryFiles().getFirst().path());
            assertEquals(hash(second), reopened.binaryFiles().getFirst().sha256());
            assertNotEquals(after.normalizedSha256(), reopened.normalizedSha256());
        } finally {
            disc.close();
        }
        assertThrows(IOException.class, () -> CdObservedMediaIdentity.capture(disc, cue, Optional.empty()));
        assertThrows(IOException.class, () -> CdObservedMediaIdentity.capture(disc, Optional.empty()));
    }

    private static String hash(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test void filenamesCueFormattingAndContainerProvenanceDoNotSelectContentIdentity() throws IOException, NoSuchAlgorithmException {
        Path first = SyntheticProgramDisc.write(directory.resolve("first"), CdReleaseVariant.USA);
        Path second = SyntheticProgramDisc.write(directory.resolve("second"), CdReleaseVariant.USA);
        Files.move(second.getParent().resolve("data.bin"), second.getParent().resolve("OTHER.BIN"));
        Files.move(second.getParent().resolve("audio35.bin"), second.getParent().resolve("aUdIo.BIN"));
        Files.writeString(second, "REM distinct formatting\r\n" + Files.readString(second)
                .replace("data.bin", "OTHER.BIN").replace("audio35.bin", "aUdIo.BIN")
                .replace("\n", "\r\n").replace(" TRACK", "    TRACK"));
        var original = identity(first);
        var renamed = identity(second);
        assertEquals(original.normalizedSha256(), renamed.normalizedSha256());
        assertNotEquals(original.cue().sha256(), renamed.cue().sha256());
        assertEquals(original.binaryFiles().stream().map(CdObservedMediaIdentity.FileHash::sha256).toList(),
                renamed.binaryFiles().stream().map(CdObservedMediaIdentity.FileHash::sha256).toList());
        assertEquals(35, original.tracks().size());
        assertEquals(35, original.binaryFiles().size());
        assertEquals(CdObservedMediaIdentity.DEFINITION, original.definition());
        Path container = Files.writeString(directory.resolve("original.container"), "synthetic provenance");
        try (var disc = CueDiscImage.open(first)) {
            var withContainer = CdObservedMediaIdentity.capture(disc, first, Optional.of(container));
            assertEquals(original.normalizedSha256(), withContainer.normalizedSha256());
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(container))),
                    withContainer.originalContainer().orElseThrow().sha256());
        }
        assertThrows(UnsupportedOperationException.class, () -> original.tracks().clear());
        assertThrows(UnsupportedOperationException.class, () -> original.binaryFiles().clear());
    }

    @Test void audioIncludingStoredGapBytesAndSyntheticGapLayoutChangeIdentity() throws IOException {
        Path cue = SyntheticProgramDisc.write(directory, CdReleaseVariant.JAPAN);
        var original = identity(cue);
        Path audio = directory.resolve("audio35.bin");
        byte[] bytes = Files.readAllBytes(audio);
        bytes[0] ^= 1;
        Files.write(audio, bytes);
        var changedGapContent = identity(cue);
        assertNotEquals(original.normalizedSha256(), changedGapContent.normalizedSha256());
        assertEquals(original.tracks().getFirst(), changedGapContent.tracks().getFirst());
        assertNotEquals(original.tracks().getLast(), changedGapContent.tracks().getLast());
        bytes[0] ^= 1;
        bytes[2352] ^= 1;
        Files.write(audio, bytes);
        assertNotEquals(original.normalizedSha256(), identity(cue).normalizedSha256());
        bytes[2352] ^= 1;
        Files.write(audio, bytes);
        Files.writeString(cue, Files.readString(cue).replace("TRACK 35 AUDIO\n", "TRACK 35 AUDIO\n PREGAP 00:00:01\n"));
        var changedLayout = identity(cue);
        assertNotEquals(original.normalizedSha256(), changedLayout.normalizedSha256());
        assertEquals(original.tracks().getLast().storedFramesSha256(), changedLayout.tracks().getLast().storedFramesSha256());
        assertEquals(1, changedLayout.tracks().getLast().syntheticPregapFrames());
    }

    @Test void fileGroupingIsExcludedButTheStoredTrackGeometryAndBytesArePreserved() throws IOException, NoSuchAlgorithmException {
        Path cue = SyntheticProgramDisc.write(directory, CdReleaseVariant.EUROPE);
        var original = identity(cue);
        Path combined = directory.resolve("combined.bin");
        try (var output = Files.newOutputStream(combined)) {
            Files.copy(directory.resolve("data.bin"), output);
            for (int track = 2; track <= 35; track++) { Files.copy(directory.resolve("audio" + track + ".bin"), output); }
        }
        StringBuilder text = new StringBuilder("FILE \"combined.bin\" BINARY\n TRACK 01 MODE1/2048\n INDEX 01 00:00:00\n");
        for (int track = 2; track <= 35; track++) {
            int start = 512 + (track - 2) * 3;
            text.append(String.format(java.util.Locale.ROOT, " TRACK %02d AUDIO\n INDEX 00 %s\n INDEX 01 %s\n",
                    track, timestamp(start), timestamp(start + 1)));
        }
        Files.writeString(cue, text);
        var grouped = identity(cue);
        assertEquals(original.normalizedSha256(), grouped.normalizedSha256());
        assertEquals(1, grouped.binaryFiles().size());
        assertEquals(original.tracks(), grouped.tracks());
        assertEquals(combined.toRealPath().toString(), grouped.binaryFiles().getFirst().path());
        assertEquals(Files.size(combined), grouped.binaryFiles().getFirst().bytes());
        assertEquals(hash(Files.readAllBytes(combined)), grouped.binaryFiles().getFirst().sha256());
    }

    private String timestamp(int frames) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", frames / 4500, frames / 75 % 60, frames % 75);
    }
}
