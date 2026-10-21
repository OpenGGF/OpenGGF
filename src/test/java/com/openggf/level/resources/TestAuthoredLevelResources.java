package com.openggf.level.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestAuthoredLevelResources {
    @TempDir Path directory;

    @Test void unknownAndDuplicateFormatProvidersFailBeforeDecoding() {
        var decoder = new AuthoredLevelDecoder() {
            public String format() { return "fixture"; }
            public Decoded decode(byte[] bytes) { throw new AssertionError("Must not decode"); }
        };
        assertSame(decoder, assertDoesNotThrow(() -> AuthoredLevelResources.selectDecoder("fixture", List.of(decoder))));
        assertThrows(IOException.class, () -> AuthoredLevelResources.selectDecoder("missing", List.of(decoder)));
        assertThrows(IOException.class, () -> AuthoredLevelResources.selectDecoder("fixture", List.of(decoder, decoder)));
        assertThrows(IOException.class, () -> AuthoredLevelResources.decode("missing", new byte[0]));
    }

    @Test void encodedBytesAndFileReadsShareTheBound() throws Exception {
        assertThrows(IOException.class, () -> AuthoredLevelResources.decode("ogfproject",
                new byte[AuthoredLevelResources.MAX_ENCODED_BYTES + 1]));
        Path oversized = directory.resolve("large.ogfproject");
        try (var file = new java.io.RandomAccessFile(oversized.toFile(), "rw")) {
            file.setLength((long) AuthoredLevelResources.MAX_ENCODED_BYTES + 1);
        }
        assertThrows(IOException.class, () -> AuthoredLevelResources.read("ogfproject", oversized));
        assertThrows(IOException.class, () -> AuthoredLevelResources.read("ogfproject", directory));
    }
}
