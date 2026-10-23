package com.openggf.game.soniccd.device;

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
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt in with -Dsoniccd.stamp.fixtures=/absolute/capture-root containing manifest.json and run-1/.
 * The six observations begin after 24/2/22/2/22/2 completed lines. This compares continuations,
 * never rerenders those lines, and proves neither submission capture nor scheduling/hardware timing.
 * Native coverage is 32-pixel stamps, a 4096-pixel repeated map, normal priority, orientation zero,
 * and no actual source, trace, or destination wrap. Other modes are constructed tests only.
 * RAM and images remain external; production classes perform no file/disassembly/fixture reads.
 */
class TestCdStampNativeContinuations {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Path run;
    private static JsonNode inventory;
    private static JsonNode comparisons;

    @BeforeAll
    static void loadCapture() throws IOException {
        String configured = System.getProperty("soniccd.stamp.fixtures");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.stamp.fixtures for native comparisons");
        Path root = Path.of(configured).toRealPath();
        JsonNode manifest = JSON.readTree(readBounded(root.resolve("manifest.json"), 1_000_000));
        assertEquals("openggf.soniccd.stamp-fixtures-manifest.v1", manifest.path("schema").asText());
        assertEquals("051d430d3d1b54625f9900c8f152d7f232e06daf", manifest.path("gpgx_source_commit").asText());
        assertEquals(6, manifest.path("fixtures").asInt(-1));
        run = root.resolve("run-1").toRealPath();
        assertTrue(run.startsWith(root));
        byte[] inventoryBytes = readBounded(run.resolve("fixtures.json"), 1_000_000);
        assertEquals(manifest.path("streams_sha256").path("run-1").path("fixtures.json").asText(),
                sha256(inventoryBytes), "manifest-bound inventory");
        inventory = JSON.readTree(inventoryBytes);
        comparisons = manifest.path("exact_comparisons").path("run-1");
        assertTrue(inventory.isArray());
        assertTrue(comparisons.isArray());
        assertEquals(6, inventory.size());
        assertEquals(6, comparisons.size());
    }

    @ParameterizedTest(name = "native continuation {0}: whole Word RAM and complete image")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void matchesEveryNativeEgressByteFromCapturedPartialProgress(int ordinal) throws IOException {
        JsonNode fixture = inventory.get(ordinal - 1);
        assertEquals(ordinal, StampFixtureAdmission.integer(fixture, "ordinal"));
        var captured = StampFixtureAdmission.admit(fixture);
        int[] completed = {24, 2, 22, 2, 22, 2};
        assertEquals(completed[ordinal - 1], captured.completedLines());
        byte[] ingress = ramPayload(fixture, "ingress");
        byte[] expected = ramPayload(fixture, "egress");
        byte[] actual = ingress.clone();
        assertFalse(Arrays.equals(ingress, expected), "capture must contain observed pixel work");
        JsonNode words = fixture.path("request").path("trace_words");
        assertTrue(words.isArray());
        assertEquals(384, words.size());
        int traceBase = StampFixtureAdmission.integer(fixture.path("request"), "trace_base");
        for (int i = 0; i < words.size(); i++) {
            int address = (traceBase + 2 * i) & 0x3ffff;
            assertEquals(words.get(i).intValue(), (Byte.toUnsignedInt(ingress[address]) << 8)
                    | Byte.toUnsignedInt(ingress[address + 1]), "native big-endian trace word " + i);
        }

        // An intermediate snapshot includes memory as well as the immutable semantic cursors.
        var next = CdStampGraphics.executeLines(actual, captured, 7);
        assertEquals(captured.completedLines() + 7, next.completedLines());
        assertEquals(captured.remainingLines() - 7, next.remainingLines());
        byte[] restoredMemory = actual.clone();
        var restored = new CdStampRenderState(next.request(), next.traceByteCursor(), next.imageNibbleCursor(),
                next.completedLines(), next.remainingLines());
        while (next.remainingLines() > 0) { next = CdStampGraphics.executeLines(actual, next, 13); }
        var replayed = CdStampGraphics.executeLines(restoredMemory, restored, Integer.MAX_VALUE);

        assertEquals(96, next.completedLines());
        assertEquals(0, next.remainingLines());
        assertEquals(fixture.path("egress_devices").path("gfx_hw.tracePtr").intValue(), next.traceByteCursor());
        assertEquals(fixture.path("egress_devices").path("gfx_hw.bufferStart").longValue(), next.imageNibbleCursor());
        assertEquals(next, replayed);
        assertArrayEquals(expected, actual, "all 262144 native Word RAM bytes");
        assertArrayEquals(expected, restoredMemory, "restored continuation must reproduce every byte");

        int imageBase = StampFixtureAdmission.integer(fixture, "image_base");
        int imageBytes = StampFixtureAdmission.integer(fixture, "image_bytes");
        byte[] image = Arrays.copyOfRange(actual, imageBase, imageBase + imageBytes);
        assertEquals(12288, image.length);
        assertArrayEquals(Arrays.copyOfRange(expected, imageBase, imageBase + imageBytes), image,
                "complete native image, including completed ingress lines");
        JsonNode comparison = comparisons.get(ordinal - 1);
        assertEquals(ordinal, comparison.path("ordinal").asInt(-1));
        assertEquals(comparison.path("image_sha256").asText(), sha256(image));
        assertArrayEquals(Arrays.copyOfRange(ingress, 0, imageBase), Arrays.copyOfRange(actual, 0, imageBase));
        assertArrayEquals(Arrays.copyOfRange(ingress, imageBase + imageBytes, ingress.length),
                Arrays.copyOfRange(actual, imageBase + imageBytes, actual.length));
        for (int line = 0; line < captured.completedLines(); line++) {
            for (int x = 0; x < 256; x++) {
                int dot = 2 * imageBase + line * 8 + (x / 8) * 768 + x % 8;
                assertEquals(nibble(ingress, dot), nibble(actual, dot), "completed ingress pixels remain untouched");
            }
        }
    }

    private static byte[] ramPayload(JsonNode fixture, String boundary) throws IOException {
        String name = fixture.path(boundary + "_file").asText();
        assertFalse(name.isBlank());
        assertEquals(Path.of(name).getFileName().toString(), name, "payload must stay in its run");
        Path file = run.resolve(name).toRealPath();
        assertTrue(file.startsWith(run));
        assertTrue(Files.size(file) <= 2L * CdStampGraphics.WORD_RAM_BYTES, "compressed payload bound");
        byte[] bytes;
        try (var input = new GZIPInputStream(Files.newInputStream(file))) {
            bytes = input.readNBytes(CdStampGraphics.WORD_RAM_BYTES + 1);
        }
        assertEquals(CdStampGraphics.WORD_RAM_BYTES, bytes.length, "decompressed RAM extent");
        assertEquals(fixture.path(boundary + "_sha256").asText(), sha256(bytes), "native payload hash");
        return bytes;
    }

    private static byte[] readBounded(Path file, int maximum) throws IOException {
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(maximum + 1);
            assertTrue(bytes.length <= maximum, "fixture metadata bound");
            return bytes;
        }
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    private static int nibble(byte[] bytes, int dot) {
        return (Byte.toUnsignedInt(bytes[dot / 2]) >>> ((dot & 1) == 0 ? 4 : 0)) & 15;
    }
}
