package com.openggf.trace.timing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.trace.TraceMetadata;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the immutable publication evidence for the approved S3K hardware-timing
 * fixtures. Expectations are literals from the reviewed native candidates,
 * never values derived by invoking the recorder.
 */
class TestCommittedHardwareTimingFixtures {

    private static final Path FIXTURE_ROOT = resolveProjectRoot()
            .resolve("src/test/resources/traces/s3k");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void approvedFixturesMatchFrozenPublicationEvidence() throws Exception {
        assertFixture(new ExpectedFixture(
                "aiz1_to_hcz_fullrun",
                "6.37-s3k",
                "f9d5c8245ba38e03fefb10e85c9f7a3f802eecd6e914a716049524f3039b7460", 1273,
                "ad226dd6db810b6e12980e0211fe735782bb2888f5df2bdc865698ae8ac60571", 570548,
                "212ad7191fd5f00bf333886a179a1ba471d7d7c242599da0e9fd8e230c4f5617", 5736502,
                "349894a74ab42d2241f6e87a814e1795a716f276c6b41c2aca4edcf9b65d77e3", 8246,
                38, 0, 361, 20797, 2, 39,
                HardwareServiceBoundary.POST_OBJECTS,
                HardwareServiceBoundary.POST_OBJECTS,
                "sha256:4423f6be47e039925c8575c68ed5eb22e9cba75f2aadd05f1d288d6c9579e723",
                "sha256:05324378670c6afa8c6d99f6e5313d625d2d926e6bc16f25cd9d8d1a5a195bf8"));
        assertFixture(new ExpectedFixture(
                "aiz_completerun",
                "6.37-s3k-completerun",
                "346f4f973fef7977051dd86c621053117a15d64abd01c83363747fd53e4328a4", 1608,
                "c1d80fb93fe3699a16f57e123d45afe2a91ab8075a326352297d540dd01ad343", 771260,
                "37a7953b82798801975ed9bafcc6bc40e1c066361a5f9fa7adb793f22bf8307f", 8210801,
                "a61c169cc98facbdd7aa4af62c7bc0eca89733f2dce695531e787bdf046f89ba", 8909,
                41, 3, 71, 26208, 2, 42,
                HardwareServiceBoundary.POST_OBJECTS,
                HardwareServiceBoundary.VINT_SERVICE,
                "sha256:4423f6be47e039925c8575c68ed5eb22e9cba75f2aadd05f1d288d6c9579e723",
                "sha256:6f27fe001c4a21687a98eb0dc8178340e7434967941c7a2fb7df442285b4c6f0"));
        assertFixture(new ExpectedFixture(
                "hcz_completerun",
                "6.37-s3k-completerun",
                "c710d8223c9d4baedf6acae605bd8e79af29f02acfaf4cd5e09d2202bc074cb4", 1610,
                "470c5aad51f660549128e0b0042ba555c9d7deb1f656496843ea11e8cdde92b4", 871582,
                "2d69b9f47f22995285dc4af838b0a5b41dec2ce67eb206cad3e02f9f4493a975", 10171071,
                "8d7d92b3eb03ceaf4b563b10da9cf4268c27690e0a5625ef429cb9f8c5f0c67e", 9779,
                45, 2, 36, 31461, 43, 87,
                HardwareServiceBoundary.POST_OBJECTS,
                HardwareServiceBoundary.VINT_SERVICE,
                "sha256:f7d726c95e019598b69ed655a53fca44b42967d9361230092ba02e539abfa45f",
                "sha256:f7494191ee9cfd23061afe24051ea2a3ebf06ed397e40004ba9e56be2db9a4ad"));
    }

    private static void assertFixture(ExpectedFixture expected) throws Exception {
        Path fixture = FIXTURE_ROOT.resolve(expected.directory());
        Path metadataPath = fixture.resolve("metadata.json");
        Path timingPath = fixture.resolve("hardware_timing.jsonl");
        JsonNode metadataJson = MAPPER.readTree(metadataPath.toFile());

        assertEquals(7, metadataJson.path("trace_schema").intValue(), expected.directory());
        assertEquals(1, metadataJson.path("hardware_timing_schema").intValue(), expected.directory());
        assertEquals(expected.recorderVersion(), metadataJson.path("lua_script_version").textValue(),
                expected.directory());
        assertFalse(metadataJson.has("run_id"), expected.directory());
        assertFalse(Files.exists(fixture.resolve("run_manifest.json")), expected.directory());

        assertFile(metadataPath, expected.metadataHash(), expected.metadataBytes());
        assertFile(fixture.resolve("physics.csv.gz"), expected.physicsHash(), expected.physicsBytes());
        assertFile(fixture.resolve("aux_state.jsonl.gz"), expected.auxHash(), expected.auxBytes());
        assertFile(timingPath, expected.timingHash(), expected.timingBytes());

        TraceMetadata metadata = TraceMetadata.load(metadataPath);
        List<HardwareCompletionEdge> edges =
                HardwareTimingStreamLoader.load(fixture, metadata).edges();
        assertEquals(expected.eventCount(), edges.size(), expected.directory());
        assertFalse(edges.isEmpty(), expected.directory());
        assertEdge(edges.getFirst(), expected.firstFrame(), expected.firstOrdinal(),
                expected.firstBoundary(),
                expected.firstFingerprint(), expected.directory());
        assertEdge(edges.getLast(), expected.lastFrame(), expected.lastOrdinal(),
                expected.lastBoundary(),
                expected.lastFingerprint(), expected.directory());
        assertTrue(edges.stream().allMatch(edge ->
                        edge.rawFrame() >= 0 && edge.rawFrame() < metadata.traceFrameCount()
                                && (edge.boundary() == HardwareServiceBoundary.VINT_SERVICE
                                    || edge.boundary() == HardwareServiceBoundary.POST_OBJECTS)
                                && edge.kind() == HardwareWorkKind.KOS_MODULE_QUEUE),
                expected.directory());
        assertEquals(expected.vintServiceEventCount(),
                edges.stream().filter(edge ->
                        edge.boundary() == HardwareServiceBoundary.VINT_SERVICE).count(),
                expected.directory());
        assertEquals(expected.eventCount() - expected.vintServiceEventCount(),
                edges.stream().filter(edge ->
                        edge.boundary() == HardwareServiceBoundary.POST_OBJECTS).count(),
                expected.directory());
    }

    private static void assertEdge(HardwareCompletionEdge edge, int frame, long ordinal,
                                   HardwareServiceBoundary boundary,
                                   String fingerprint, String fixture) {
        assertEquals(frame, edge.rawFrame(), fixture);
        assertEquals(ordinal, edge.ordinal(), fixture);
        assertEquals(boundary, edge.boundary(), fixture);
        assertEquals(fingerprint, edge.submissionFingerprint(), fixture);
    }

    private static void assertFile(Path path, String expectedHash, long expectedBytes)
            throws IOException, NoSuchAlgorithmException {
        byte[] content = Files.readAllBytes(path);
        assertEquals(expectedBytes, content.length, path.toString());
        assertEquals(expectedHash,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)),
                path.toString());
    }

    private static Path resolveProjectRoot() {
        String basedir = System.getProperty("project.basedir");
        return basedir == null || basedir.isEmpty()
                ? Paths.get(System.getProperty("user.dir", "."))
                : Paths.get(basedir);
    }

    private record ExpectedFixture(
            String directory,
            String recorderVersion,
            String metadataHash, long metadataBytes,
            String physicsHash, long physicsBytes,
            String auxHash, long auxBytes,
            String timingHash, long timingBytes,
            int eventCount, int vintServiceEventCount,
            int firstFrame, int lastFrame,
            long firstOrdinal, long lastOrdinal,
            HardwareServiceBoundary firstBoundary,
            HardwareServiceBoundary lastBoundary,
            String firstFingerprint, String lastFingerprint) {
    }
}
