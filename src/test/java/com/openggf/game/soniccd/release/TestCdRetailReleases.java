package com.openggf.game.soniccd.release;

import com.openggf.data.cd.DiscTrack;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdObservedMediaIdentity;
import com.openggf.game.soniccd.media.CdObservedMediaIdentity.FileHash;
import com.openggf.game.soniccd.media.CdObservedMediaIdentity.TrackContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestCdRetailReleases {
    @ParameterizedTest
    @EnumSource(CdReleaseVariant.class)
    void completeIndependentDescriptorsMatchTheKnownRecord(CdReleaseVariant variant) throws Exception {
        var actual = observation(RetailProfileFixtures.tracks(variant));
        var expected = expected(variant);
        assertEquals(35, actual.tracks().size());
        assertEquals(expected.normalizedSha256(), actual.normalizedSha256());
        assertEquals(expected, CdRetailReleases.find(actual).orElseThrow());
        assertEquals(expected, CdRetailReleases.requireRequested(actual, expected.key()));
    }

    @Test void unknownDefinitionNeverUsesAKnownHash() throws Exception {
        var actual = observation(RetailProfileFixtures.tracks(CdReleaseVariant.USA));
        assertTrue(CdRetailReleases.find(new CdObservedMediaIdentity("future-definition", actual.normalizedSha256(),
                actual.cue(), actual.binaryFiles(), actual.originalContainer(), actual.tracks())).isEmpty());
    }

    @Test void dataTrackAndEveryAudioTrackAreRequiredIncludingUsaTrack13() throws Exception {
        var original = RetailProfileFixtures.tracks(CdReleaseVariant.USA);
        // The other four public USA retail records share track 01 and differ at track 13.
        for (int index : List.of(0, 1, 12, 34)) {
            var changed = new ArrayList<>(original);
            var track = changed.get(index);
            changed.set(index, new TrackContent(track.number(), track.mode(), track.discIndex01Frame(),
                    track.programFrames(), track.storedPregapFrames(), track.syntheticPregapFrames(), "0".repeat(64)));
            assertTrue(CdRetailReleases.find(observation(changed)).isEmpty(), "changed track " + track.number());
            if (index == 12) { assertEquals(original.getFirst(), changed.getFirst()); }
        }
    }

    @Test void gapLengthPlacementAndStoredVersusSyntheticRemainDistinct() throws Exception {
        var original = RetailProfileFixtures.tracks(CdReleaseVariant.USA);
        var track = original.get(12);
        List<TrackContent> changes = List.of(
                new TrackContent(13, track.mode(), track.discIndex01Frame() + 1, track.programFrames(),
                        track.storedPregapFrames(), 0, track.storedFramesSha256()),
                new TrackContent(13, track.mode(), track.discIndex01Frame(), track.programFrames() - 1,
                        track.storedPregapFrames() + 1, 0, track.storedFramesSha256()),
                new TrackContent(13, track.mode(), track.discIndex01Frame(), track.programFrames(),
                        0, track.storedPregapFrames(), track.storedFramesSha256()));
        for (var replacement : changes) {
            var changed = new ArrayList<>(original);
            changed.set(12, replacement);
            assertTrue(CdRetailReleases.find(observation(changed)).isEmpty());
        }
    }

    @Test void cookedDataAndMissingOrReorderedTracksDoNotMatch() throws Exception {
        var tracks = new ArrayList<>(RetailProfileFixtures.tracks(CdReleaseVariant.USA));
        var data = tracks.getFirst();
        tracks.set(0, new TrackContent(1, DiscTrack.Mode.MODE1_2048, 0, data.programFrames(), 0, 0, data.storedFramesSha256()));
        assertTrue(CdRetailReleases.find(observation(tracks)).isEmpty());
        tracks = new ArrayList<>(RetailProfileFixtures.tracks(CdReleaseVariant.USA));
        java.util.Collections.swap(tracks, 12, 13);
        assertTrue(CdRetailReleases.find(observation(tracks)).isEmpty());
        tracks.removeLast();
        assertTrue(CdRetailReleases.find(observation(tracks)).isEmpty());
    }

    @Test void wrongForcedKnownKeyAndRegionRejectRatherThanOverrideTheContent() throws Exception {
        var usa = observation(RetailProfileFixtures.tracks(CdReleaseVariant.USA));
        var mismatch = assertThrows(IllegalArgumentException.class,
                () -> CdRetailReleases.requireRequested(usa, CdRetailReleases.JAPAN.key()));
        assertTrue(mismatch.getMessage().contains("redump-1632"));
        assertTrue(mismatch.getMessage().contains("redump-29188"));
        assertThrows(IllegalArgumentException.class, () -> CdRetailReleases.requireRequested(usa, "USA"));
        assertThrows(IllegalArgumentException.class, () -> CdRetailReleases.requireRequested(observation(List.of()),
                CdRetailReleases.USA.key()));
    }

    @Test void cueNamesFormattingAndContainerMetadataDoNotSelectContent() throws Exception {
        var original = observation(RetailProfileFixtures.tracks(CdReleaseVariant.JAPAN));
        var changed = new CdObservedMediaIdentity(original.definition(), original.normalizedSha256(),
                new FileHash("renamed-and-reformatted.cue", 1234, "1".repeat(64)),
                List.of(new FileHash("combined-or-renamed.bin", 9999, "2".repeat(64))),
                Optional.of(new FileHash("different.container", 100, "3".repeat(64))), original.tracks());
        assertEquals(CdRetailReleases.JAPAN, CdRetailReleases.find(changed).orElseThrow());
    }

    @Test void staleKnownHashCannotMaskAlteredOrAbsentDescriptors() throws Exception {
        var known = observation(RetailProfileFixtures.tracks(CdReleaseVariant.USA));
        for (var tracks : List.of(List.<TrackContent>of(), RetailProfileFixtures.tracks(CdReleaseVariant.JAPAN))) {
            var claimed = new CdObservedMediaIdentity(known.definition(), known.normalizedSha256(), known.cue(),
                    List.of(), Optional.empty(), tracks);
            assertTrue(CdRetailReleases.find(claimed).isEmpty());
        }
    }

    @Test void recordConstructionIsAnInternalObservationRatherThanByteAuthentication() throws Exception {
        // Descriptor fixtures contain no media bytes. Complete claimed descriptors can match;
        // production must capture user media instead of trusting externally supplied records.
        var metadataOnly = observation(RetailProfileFixtures.tracks(CdReleaseVariant.USA));
        assertTrue(metadataOnly.binaryFiles().isEmpty());
        assertEquals(CdRetailReleases.USA, CdRetailReleases.find(metadataOnly).orElseThrow());
    }

    @Test void multipleContentRecordsCanShareTheUsaVariantWithoutSharingIdentity() {
        var hypothetical = new CdRetailRelease("test-other-usa-content", CdReleaseVariant.USA,
                "0".repeat(64), URI.create("urn:test:other-usa-content"));
        assertEquals(CdRetailReleases.USA.variant(), hypothetical.variant());
        assertNotEquals(CdRetailReleases.USA.key(), hypothetical.key());
        assertNotEquals(CdRetailReleases.USA, hypothetical);
    }

    private static CdRetailRelease expected(CdReleaseVariant variant) {
        return switch (variant) {
            case USA -> CdRetailReleases.USA;
            case JAPAN -> CdRetailReleases.JAPAN;
            case EUROPE -> CdRetailReleases.EUROPE;
        };
    }

    private static CdObservedMediaIdentity observation(List<TrackContent> tracks) throws Exception {
        // Independent byte-width encoder for descriptor fixtures, not the capture implementation.
        var bytes = new ByteArrayOutputStream();
        writeAscii(bytes, CdObservedMediaIdentity.DEFINITION);
        bytes.writeBytes(ByteBuffer.allocate(4).putInt(tracks.size()).array());
        for (var track : tracks) {
            bytes.writeBytes(ByteBuffer.allocate(4).putInt(track.number()).array());
            writeAscii(bytes, track.mode().name());
            bytes.writeBytes(ByteBuffer.allocate(32).putLong(track.discIndex01Frame()).putLong(track.programFrames())
                    .putLong(track.storedPregapFrames()).putLong(track.syntheticPregapFrames()).array());
            bytes.writeBytes(HexFormat.of().parseHex(track.storedFramesSha256()));
        }
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        return new CdObservedMediaIdentity(CdObservedMediaIdentity.DEFINITION, hash,
                new FileHash("metadata-fixture.cue", 0, "0".repeat(64)), List.of(), Optional.empty(), tracks);
    }

    private static void writeAscii(ByteArrayOutputStream output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        output.writeBytes(ByteBuffer.allocate(2).putShort((short) bytes.length).array());
        output.writeBytes(bytes);
    }
}
