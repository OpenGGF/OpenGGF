package com.openggf.tools.audio.parity.s3k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

/**
 * Integrity contract for the committed S3K sound-driver oracle reference:
 * the gzip fixture's checksum, its metadata sidecar, the source BK2 identity,
 * and the stream's own terminal digest must all agree.
 */
class TestS3kAudioOracleFixtureContract {
    private static final Path FIXTURE_DIR = Path.of("src/test/resources/audio/parity/s3k");
    private static final Path REFERENCE = FIXTURE_DIR.resolve("s3k-aiz1-intro-reference-v1.jsonl.gz");
    private static final Path METADATA = FIXTURE_DIR.resolve("s3k-aiz1-intro-metadata-v1.json");

    @Test
    void committedReferenceMatchesItsMetadataSidecar() throws Exception {
        assertTrue(Files.isRegularFile(REFERENCE), "committed S3K oracle reference is required");
        assertTrue(Files.isRegularFile(METADATA), "fixture metadata sidecar is required");
        JsonNode metadata = new ObjectMapper().readTree(METADATA.toFile());
        assertEquals("openggf.s3k_audio_oracle_reference_fixture.v1",
                metadata.path("schema").asText());
        assertEquals(metadata.path("reference").asText(), REFERENCE.getFileName().toString());
        assertEquals(metadata.path("reference_gzip_sha256").asText(), sha256(REFERENCE));
        assertEquals(metadata.path("reference_uncompressed_sha256").asText(),
                sha256Uncompressed(REFERENCE));

        Path movie = Path.of(metadata.path("movie").path("path").asText());
        assertTrue(Files.isRegularFile(movie), "source BK2 must remain committed: " + movie);
        assertEquals(metadata.path("movie").path("sha256").asText(), sha256(movie));

        int[] ticks = {0};
        S3kAudioReferenceReader.Metadata streamMetadata =
                S3kAudioReferenceReader.read(REFERENCE, tick -> ticks[0]++);
        assertEquals(metadata.path("ticks").asInt(), ticks[0]);
        assertEquals(metadata.path("rom_sha1").asText(), streamMetadata.romSha1());
        assertEquals(metadata.path("movie").path("sha256").asText(), streamMetadata.movieSha256());
        assertEquals(metadata.path("observer").path("core_zst_sha256").asText(),
                streamMetadata.observerCoreSha256());
    }

    @Test
    void driverProjectionExcludes68kBootstrapWritesButKeepsZ80Writes() {
        List<S3kAudioTick> ticks = new ArrayList<>();
        S3kAudioReferenceReader.read(REFERENCE, ticks::add);

        assertTrue(ticks.get(3).writes().isEmpty(),
                "the 68k PSGInitValues bootstrap is outside the Z80 driver oracle");
        assertEquals(0xff, ticks.get(13).writes().get(0).value(),
                "the first Z80 zStopAllSound write must remain in the projection");
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static String sha256Uncompressed(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new GZIPInputStream(Files.newInputStream(path))) {
            byte[] chunk = new byte[64 * 1024];
            int count;
            while ((count = input.read(chunk)) >= 0) {
                digest.update(chunk, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
