package com.openggf.tools.audio.completerun.s3k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunReferenceRawAdapter.Header;
import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunReferenceRawAdapter.RawBoundary;
import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunReferenceRawAdapter.RawFrame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in native proof that row 810 reaches the full S3K Java state pipeline. */
class TestS3kCompleteRunRealRow810DecodeGate {
    @Test
    @EnabledIfEnvironmentVariable(named = "OPENGGF_S3K_COMPLETE_AUDIO_DECODE_GATE", matches = "1")
    void capturesAndDecodesTheExactRealRow810Boundary() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path rom = requiredPath("S3K_ROM_PATH");
        Path movie = requiredPath("S3K_BK2_PATH");
        Path raw = Files.createTempFile("openggf-s3k-row810-", ".jsonl");
        Files.delete(raw); // The C# publisher deliberately requires a create-new target.
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    root.resolve("tools/bizhawk-headless/test.sh").toString(),
                    "--filter", "prove the real row 810 raw boundary");
            builder.directory(root.toFile());
            builder.inheritIO();
            builder.environment().put("OPENGGF_S3K_COMPLETE_AUDIO_REFERENCE", "1");
            builder.environment().put("OPENGGF_S3K_ROW810_RAW_OUTPUT", raw.toString());
            builder.environment().put("S3K_ROM_PATH", rom.toString());
            builder.environment().put("S3K_BK2_PATH", movie.toString());
            Process capture = builder.start();
            boolean finished = capture.waitFor(
                    Duration.ofMinutes(2).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) capture.destroyForcibly();
            assertTrue(finished, "native row-810 capture timed out");
            assertEquals(0, capture.exitValue(), "native row-810 capture failed");

            var catalog = S3kCompleteRunAssetCatalog.load(rom);
            var sink = new DecodeSink(catalog);
            S3kCompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, sink);
            assertEquals(2, sink.decodedStates);
            assertEquals(1, sink.commits);
            assertEquals(0, sink.aborts);
        } finally {
            Files.deleteIfExists(raw);
        }
    }

    private static Path requiredPath(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required by the opt-in row-810 gate");
        }
        Path path = Path.of(value).toAbsolutePath();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(name + " is not a file: " + path);
        }
        return path;
    }

    private static final class DecodeSink implements S3kCompleteRunReferenceRawAdapter.Sink {
        private final S3kCompleteRunAssetCatalog catalog;
        private int decodedStates;
        private int commits;
        private int aborts;

        private DecodeSink(S3kCompleteRunAssetCatalog catalog) { this.catalog = catalog; }
        @Override public void begin() { }
        @Override public void header(Header value) { }
        @Override public void baseline(RawBoundary value) { validate(value.driverState()); }
        @Override public void frame(RawFrame value) { validate(value.driverState()); }
        @Override public void cutoff(RawBoundary value) { }
        @Override public void commit() { commits++; }
        @Override public void abort() { aborts++; decodedStates = 0; }

        private void validate(byte[] raw) {
            var snapshot = S3kCompleteRunStateDecoder.decode(raw, catalog);
            var normalized = S3kCompleteRunStateNormalizer.normalizeReference(
                    snapshot, catalog.assets());
            S3kCompleteRunAudioProfile.profile().validateState(normalized);
            decodedStates++;
        }
    }
}
