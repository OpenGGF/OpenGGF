package com.openggf.game.soniccd.device.pcm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt in with soniccd.pcm.fixtures pointing to controls-startup-jumps-r1.
 * These eight source-native blocks cover startup channel 3 and channel stop, including three
 * loops. They do not cover gameplay-active PCM, native active-PCM restoration, multiple-channel
 * clipping, resampling or timing. Test-only state admission never supplies engine gameplay.
 */
class TestCdPcmNativeContinuations {
    private static Path root;
    private static JsonNode streams;

    @BeforeAll static void capture() throws IOException {
        String configured = System.getProperty("soniccd.pcm.fixtures");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.pcm.fixtures for native blocks");
        root = Path.of(configured).toRealPath();
        byte[] bytes = bounded(root.resolve("manifest.json"), 1_000_000);
        assertEquals("0236df320dcb3322a21263b2b771f108269a779bb6712445e75ba36a309141b4", sha(bytes));
        var manifest = new ObjectMapper().readTree(bytes);
        assertEquals("soniccd.pcm-native-capture.v1", manifest.path("schema").asText());
        assertEquals("051d430d3d1b54625f9900c8f152d7f232e06daf", manifest.path("source_pins").path("gpgx").asText());
        assertEquals(8, manifest.path("selected").size());
        streams = manifest.path("streams");
    }

    @ParameterizedTest(name = "source-native PCM block {0}: all samples, state and wave RAM")
    @ValueSource(ints = {3156, 3158, 3254, 3326, 3404, 3419, 3420, 3421})
    void reproducesEveryCapturedIntegerSampleAndCompleteChipState(int ordinal) throws IOException {
        String name = String.format(java.util.Locale.ROOT, "block-%08d.bin.gz", ordinal);
        var block = PcmNativeBlock.parse(payload("enabled-1", name));
        assertEquals(ordinal, block.ordinal());
        assertArrayEquals(payload("enabled-1", name), payload("enabled-2", name), "independent native repetition");
        var device = new CdPcmDevice();
        device.restoreState(block.before());
        assertEquals(block.before(), device.captureState());
        var actual = new ArrayList<CdPcmOutput.Step>();
        int first = Math.min(7, block.samples().size());
        actual.addAll(device.advanceSamples(first).steps());
        var checkpoint = device.captureState();
        var remainder = device.advanceSamples(block.samples().size() - first);
        actual.addAll(remainder.steps());
        assertEquals(block.samples(), actual, "all unscaled integer values and full-width deltas");
        assertEquals(block.after(), device.captureState(), "all channels, registers, selectors, output and 65536 RAM bytes");
        var restored = new CdPcmDevice();
        restored.restoreState(checkpoint);
        assertEquals(remainder, restored.advanceSamples(block.samples().size() - first));
        assertEquals(block.after(), restored.captureState(), "Java chip restoration and forward replay");
        int expectedLoops = ordinal == 3254 || ordinal == 3326 || ordinal == 3404 ? 1 : 0;
        assertEquals(expectedLoops, block.loopMasks().stream().filter(mask -> mask != 0).count());
        if (ordinal == 3421) {
            assertTrue(block.before().chipEnabled());
            assertEquals(0, block.before().enabledChannelMask());
            assertTrue(block.before().leftOutput() != 0);
            assertEquals(0, block.after().leftOutput());
        } else {
            assertEquals(8, block.before().enabledChannelMask());
            assertTrue(block.samples().stream().anyMatch(step -> step.left() != 0 || step.right() != 0));
        }
    }

    private static byte[] payload(String mode, String name) throws IOException {
        Path path = root.resolve(mode).resolve(name).toRealPath();
        assertTrue(path.startsWith(root.resolve(mode).toRealPath()));
        byte[] compressed = bounded(path, 2 * PcmNativeBlock.BYTES);
        JsonNode pin = streams.path(mode).path(name);
        assertEquals(pin.path("bytes").asLong(-1), compressed.length);
        assertEquals(pin.path("sha256").asText(), sha(compressed), "manifest-bound complete payload");
        try (var input = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed))) {
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
