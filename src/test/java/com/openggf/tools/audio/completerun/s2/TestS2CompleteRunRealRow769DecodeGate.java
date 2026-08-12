package com.openggf.tools.audio.completerun.s2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.tools.audio.completerun.s2.S2CompleteRunReferenceRawAdapter.Header;
import com.openggf.tools.audio.completerun.s2.S2CompleteRunReferenceRawAdapter.RawBoundary;
import com.openggf.tools.audio.completerun.s2.S2CompleteRunReferenceRawAdapter.RawFrame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in native proof that real row 769 reaches the strict S2 Java state pipeline. */
class TestS2CompleteRunRealRow769DecodeGate {
    @Test
    @EnabledIfEnvironmentVariable(named = "OPENGGF_S2_COMPLETE_AUDIO_DECODE_GATE", matches = "1")
    void capturesAndDecodesTheExactRealRow769Boundary() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path rom = requiredPath("S2_ROM_PATH");
        Path movie = requiredPath("S2_BK2_PATH");
        Path raw = Files.createTempFile("openggf-s2-row769-", ".jsonl");
        Files.delete(raw);
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    root.resolve("tools/bizhawk-headless/test.sh").toString(),
                    "--filter", "prove the real row 769 raw boundary");
            builder.directory(root.toFile());
            builder.inheritIO();
            builder.environment().put("OPENGGF_S2_COMPLETE_AUDIO_REFERENCE", "1");
            builder.environment().put("OPENGGF_S2_ROW769_RAW_OUTPUT", raw.toString());
            builder.environment().put("S2_ROM_PATH", rom.toString());
            builder.environment().put("S2_BK2_PATH", movie.toString());
            Process capture = builder.start();
            boolean finished = capture.waitFor(Duration.ofMinutes(2).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) capture.destroyForcibly();
            assertTrue(finished, "native row-769 capture timed out");
            assertEquals(0, capture.exitValue(), "native row-769 capture failed");

            var catalog = S2CompleteRunAssetCatalog.load(rom);
            var sink = new DecodeSink(catalog);
            S2CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, sink);
            assertEquals(2, sink.decodedStates);
            assertEquals(1, sink.commits);
            assertEquals(0, sink.aborts);
        } finally {
            Files.deleteIfExists(raw);
        }
    }

    private static Path requiredPath(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        Path path = Path.of(value).toAbsolutePath();
        if (!Files.isRegularFile(path)) throw new IllegalStateException(name + " is not a file: " + path);
        return path;
    }

    private static final class DecodeSink implements S2CompleteRunReferenceRawAdapter.Sink {
        private final S2CompleteRunAssetCatalog catalog;
        private int decodedStates;
        private int commits;
        private int aborts;
        private DecodeSink(S2CompleteRunAssetCatalog catalog) { this.catalog = catalog; }
        @Override public void begin() { }
        @Override public void header(Header value) { }
        @Override public void baseline(RawBoundary value) { validate(value.driverState()); }
        @Override public void frame(RawFrame value) { validate(value.driverState()); }
        @Override public void cutoff(RawBoundary value) { }
        @Override public void commit() { commits++; }
        @Override public void abort() { aborts++; decodedStates = 0; }
        private void validate(byte[] raw) {
            var state = S2CompleteRunStateDecoder.decode(raw, catalog);
            var normalized = S2CompleteRunStateNormalizer.normalizeReference(state, catalog.assets());
            S2CompleteRunAudioProfile.profile().validateState(normalized);
            decodedStates++;
        }
    }
}
