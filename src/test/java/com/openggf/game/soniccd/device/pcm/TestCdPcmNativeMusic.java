package com.openggf.game.soniccd.device.pcm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exact source-native Palmtree Panic Past music comparisons through retail Sound Test.
 * Opt in with soniccd.pcm.musicFixtures pointing to route-soundtest-controls-r1.
 * Eight capture and four active native restore blocks cover varied channel masks/pan/envelopes.
 * Music preview, in-level audio, clipping, resampling and physical timing remain separate claims.
 */
class TestCdPcmNativeMusic {
    private static Path root;
    private static JsonNode streams;

    @BeforeAll static void capture() throws IOException {
        String configured = System.getProperty("soniccd.pcm.musicFixtures");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.pcm.musicFixtures for music blocks");
        root = Path.of(configured).toRealPath();
        byte[] bytes = bounded(root.resolve("manifest.json"), 1_000_000);
        assertEquals("a0b1355233f41659545f778f044ae39dfa0de55a5ff7a5a8293070036e27661b", sha(bytes));
        var manifest = new ObjectMapper().readTree(bytes);
        assertEquals("soniccd.pcm-native-capture.v1", manifest.path("schema").asText());
        assertEquals("051d430d3d1b54625f9900c8f152d7f232e06daf", manifest.path("source_pins").path("gpgx").asText());
        assertEquals(8, manifest.path("selected").size());
        streams = manifest.path("streams");
    }

    @ParameterizedTest(name = "music {0} {1}: exact integer samples and complete chip state")
    @CsvSource({"block,7605", "block,11459", "block,13341", "block,19938",
            "block,44351", "block,62330", "block,89261", "block,92597",
            "forward-block,7605", "forward-block,11459", "forward-block,13341", "forward-block,19938"})
    void matchesNativeMusicAndItsActiveRestoredBlocks(String prefix, int ordinal) throws IOException {
        String name = String.format(Locale.ROOT, "%s-%08d.bin.gz", prefix, ordinal);
        byte[] bytes = payload("enabled-1", name);
        assertArrayEquals(bytes, payload("enabled-2", name), "independent cold native repetition");
        if (prefix.equals("forward-block")) {
            String replay = String.format(Locale.ROOT, "replay-block-%08d.bin.gz", ordinal);
            assertArrayEquals(bytes, payload("enabled-1", replay), "complete native active-PCM restore");
            assertArrayEquals(bytes, payload("enabled-2", replay), "repeated native active-PCM restore");
        }
        var block = PcmNativeBlock.parse(bytes);
        assertEquals(ordinal, block.ordinal());
        assertTrue(block.before().chipEnabled());
        assertTrue(block.before().enabledChannelMask() != 0);
        assertTrue(block.samples().stream().allMatch(step -> step.left() != 0 || step.right() != 0));

        var device = new CdPcmDevice();
        device.restoreState(block.before());
        var actual = new ArrayList<CdPcmOutput.Step>();
        int first = Math.min(17, block.samples().size());
        actual.addAll(device.advanceSamples(first).steps());
        var checkpoint = device.captureState();
        var remainder = device.advanceSamples(block.samples().size() - first);
        actual.addAll(remainder.steps());
        assertEquals(block.samples(), actual, "every raw sample, full-width delta and submission flag");
        assertEquals(block.after(), device.captureState(), "all eight channels, registers, outputs and wave RAM");
        var restored = new CdPcmDevice();
        restored.restoreState(checkpoint);
        assertEquals(remainder, restored.advanceSamples(block.samples().size() - first));
        assertEquals(block.after(), restored.captureState(), "Java chip checkpoint replay");
    }

    private static byte[] payload(String mode, String name) throws IOException {
        Path directory = root.resolve(mode).toRealPath();
        Path file = directory.resolve(name).toRealPath();
        assertTrue(directory.startsWith(root) && file.startsWith(directory));
        byte[] compressed = bounded(file, 2 * PcmNativeBlock.BYTES);
        JsonNode pin = streams.path(mode).path(name);
        assertEquals(pin.path("bytes").asLong(-1), compressed.length);
        assertEquals(pin.path("sha256").asText(), sha(compressed), "manifest-bound complete payload");
        try (var input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] data = input.readNBytes(PcmNativeBlock.BYTES + 1);
            assertEquals(PcmNativeBlock.BYTES, data.length);
            return data;
        }
    }

    private static byte[] bounded(Path path, int maximum) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] data = input.readNBytes(maximum + 1);
            assertTrue(data.length <= maximum, "fixture file bound");
            return data;
        }
    }

    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
}
