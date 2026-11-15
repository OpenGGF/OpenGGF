package com.openggf.game.soniccd.media;

import com.openggf.game.soniccd.CdCommand;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestCdProgramAssets {
    @TempDir Path directory;

    @Test void readsNativeFilesSlicesAndCatalogueDependenciesWithoutPromotingBinsToMmd() throws IOException {
        Path cue = SyntheticProgramDisc.write(directory, CdReleaseVariant.USA);
        try (CdProgramAssets assets = CdProgramAssets.open(cue)) {
            assertEquals(CdReleaseVariant.USA, assets.header().observedVariant().orElseThrow());
            assertEquals(127, assets.files().size());
            assertEquals("/R11A__.MMD;1", assets.entry(CdNativeFile.R11A).path());
            var slice = assets.slice(CdNativeFile.R11A, 2040, 40);
            byte[] expected = new byte[40];
            for (int i = 0; i < expected.length; i++) { expected[i] = (byte) ((2040 + i) * 7); }
            assertArrayEquals(expected, slice.read(0, 40));
            try (var stream = slice.openStream()) {
                assertArrayEquals(expected, stream.readAllBytes());
                assertEquals(-1, stream.read());
                assertEquals(0, stream.read(new byte[2], 0, 0));
            }
            assertThrows(IllegalArgumentException.class, () -> slice.read(39, 2));
            assertThrows(IllegalArgumentException.class, () -> assets.slice(CdNativeFile.R11A, Long.MAX_VALUE, 1));
            assertThrows(IllegalArgumentException.class, () -> assets.mmd(CdNativeFile.R1PCM));
            assertEquals(0x200100, assets.mmd(CdNativeFile.R11A).header().entry());
            assertEquals(List.of(CdNativeFile.R33C, CdNativeFile.R3PCM), assets.stage(CdStageKey.parse("R33C"))
                    .stream().map(CdProgramAssets.Component::file).toList());
            assertEquals(CdProgramAssets.Use.CONDITIONAL_PCM, assets.components(CdCommand.R33C).get(1).use());
            assertEquals(List.of(CdNativeFile.R3PCM), assets.components(CdCommand.R3BMUST)
                    .stream().map(CdProgramAssets.Component::file).toList());
            assertEquals(List.of(CdNativeFile.OPENMAIN, CdNativeFile.OPENSUB, CdNativeFile.OPENSTM),
                    assets.components(CdCommand.OPENING).stream().map(CdProgramAssets.Component::file).toList());
            assertEquals(CdProgramAssets.Use.PROGRAM_STREAM, assets.components(CdCommand.OPENING).getLast().use());
            assertEquals(List.of(CdNativeFile.TIMEATKMAIN), assets.components(CdCommand.TIMEATK)
                    .stream().map(CdProgramAssets.Component::file).toList());
            assertTrue(assets.components(CdCommand.STOPCDDA).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> assets.components(CdCommand.INVALID_39));
        }
    }

    @Test void sourceHeaderMatchingIsIndependentOfRequestedVariantAndUnknownStaysUnknown() throws IOException {
        for (CdReleaseVariant variant : CdReleaseVariant.values()) {
            Path cue = SyntheticProgramDisc.write(directory.resolve(variant.name()), variant);
            try (var assets = CdProgramAssets.open(cue, Optional.of(variant), Optional.empty())) {
                assertEquals(variant, assets.header().observedVariant().orElseThrow());
                assertEquals(CdDiscHeader.Match.MATCH, assets.header().match(variant));
                assertEquals(CdDiscHeader.Match.MISMATCH,
                        assets.header().match(variant == CdReleaseVariant.USA ? CdReleaseVariant.JAPAN : CdReleaseVariant.USA));
            }
        }
        Path cue = directory.resolve("USA/disc.cue");
        assertThrows(IOException.class, () -> CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.JAPAN), Optional.empty()));
        byte[] bytes = Files.readAllBytes(directory.resolve("USA/data.bin"));
        bytes[0x50] = '9';
        Files.write(directory.resolve("USA/data.bin"), bytes);
        try (var unknown = CdProgramAssets.open(cue)) {
            assertEquals(CdDiscHeader.Match.UNKNOWN, unknown.header().match(CdReleaseVariant.USA));
            assertTrue(unknown.catalogue().isEmpty());
            assertNotNull(unknown.entry(CdNativeFile.R11A));
            assertThrows(IOException.class, () -> unknown.components(CdCommand.TITLE));
        }
        assertThrows(IOException.class, () -> CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty()));
        assertThrows(IOException.class, () -> CdDiscHeader.parse(new byte[511]));
    }

    @Test void ownerCloseInvalidatesBorrowedStreamsSlicesAndLookupsButRetainsFrozenFacts() throws IOException {
        Path cue = SyntheticProgramDisc.write(directory, CdReleaseVariant.EUROPE);
        CdProgramAssets assets = CdProgramAssets.open(cue);
        var identity = assets.identity();
        var image = assets.mmd(CdNativeFile.R11A);
        var slice = assets.slice(CdNativeFile.R11A, 256, 10);
        var stream = slice.openStream();
        var full = assets.openFile(CdNativeFile.OPENSTM);
        assertTrue(stream.read() >= 0);
        assets.close();
        assets.close();
        assertThrows(IOException.class, stream::read);
        assertThrows(IOException.class, full::read);
        assertThrows(IOException.class, () -> slice.read(0, 1));
        assertThrows(IOException.class, () -> assets.entry(CdNativeFile.R11A));
        assertEquals(identity, assets.identity());
        assertEquals(3000, image.fileBytes());
        stream.close();
        full.close();
        try (var reopened = CdProgramAssets.open(cue)) {
            assertEquals(identity.normalizedSha256(), reopened.identity().normalizedSha256());
        }
    }

    @Test void inputChangesAreDetectedAfterIdentityWasFrozen() throws IOException {
        Path cue = SyntheticProgramDisc.write(directory, CdReleaseVariant.USA);
        try (var assets = CdProgramAssets.open(cue)) {
            String fingerprint = assets.identity().normalizedSha256();
            Files.write(directory.resolve("data.bin"), new byte[2048]);
            assertThrows(IOException.class, () -> assets.read(CdNativeFile.R11A, 0, 1));
            assertEquals(fingerprint, assets.identity().normalizedSha256());
        }
    }
}
