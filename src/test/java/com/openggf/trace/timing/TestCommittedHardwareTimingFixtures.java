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
                "6.34-s3k",
                "bdf8bcbad962e0809e031c5827808a71e8c9e6d5db52a6cbdb0afd6f8a559bb3", 1273,
                "ad226dd6db810b6e12980e0211fe735782bb2888f5df2bdc865698ae8ac60571", 570548,
                "212ad7191fd5f00bf333886a179a1ba471d7d7c242599da0e9fd8e230c4f5617", 5736502,
                "0f99c7aaf110d08930f9d3692546b6de462139b0b90742a185bbbe31ef3bedc9", 8676,
                40, 234, 20797, 0, 39,
                "sha256:4886dc48bbb7024ab01cb53ca38ef91c54fa7e4c756b7f9b97e962984443ae6e",
                "sha256:3efd3361fdb5f24c72d145c5a034cc0e06b3095ae77a819361daf9f8b3749cd2"));
        assertFixture(new ExpectedFixture(
                "aiz_completerun",
                "6.34-s3k-completerun",
                "bc8a4870fd7dc570a47d56b94e466d5b3abf82ddeead91952ec31da80e1fa763", 1608,
                "c1d80fb93fe3699a16f57e123d45afe2a91ab8075a326352297d540dd01ad343", 771260,
                "37a7953b82798801975ed9bafcc6bc40e1c066361a5f9fa7adb793f22bf8307f", 8210801,
                "d6f454521127c1152e78c8c1358fd2c1b02d951d2121e9f64043ae2758a2cf46", 8909,
                41, 71, 26208, 2, 42,
                "sha256:62ae2c996f93959e20921f8e0521643aff09a0ac53ffde016794e184af11c3f5",
                "sha256:a6b40284872912d4ad80d8c08d419e85608262de36e73bd8def787b3ce0a4c2c"));
        assertFixture(new ExpectedFixture(
                "hcz_completerun",
                "6.34-s3k-completerun",
                "0715613b20ed8aac41b116ec3762695105eb02afc3d720e7d6d6b1247e7c333b", 1610,
                "470c5aad51f660549128e0b0042ba555c9d7deb1f656496843ea11e8cdde92b4", 871582,
                "2d69b9f47f22995285dc4af838b0a5b41dec2ce67eb206cad3e02f9f4493a975", 10171071,
                "ff922817c416cca6bc17533ee8bcb93bbdcd84a1da677a387ce9bd5f0af30c1f", 9779,
                45, 36, 31461, 43, 87,
                "sha256:212fcf8f55d8310015dd7e3e916e55948d628c52f48c865e8a47ffd1cc59e585",
                "sha256:eaf9899f2499304b5bdefa359597a05eecc0fa26eed83bde5584c25d2fa20be6"));
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
                expected.firstFingerprint(), expected.directory());
        assertEdge(edges.getLast(), expected.lastFrame(), expected.lastOrdinal(),
                expected.lastFingerprint(), expected.directory());
        assertTrue(edges.stream().allMatch(edge ->
                        edge.rawFrame() >= 0 && edge.rawFrame() < metadata.traceFrameCount()
                                && edge.boundary() == HardwareServiceBoundary.POST_OBJECTS
                                && edge.kind() == HardwareWorkKind.KOS_MODULE_QUEUE),
                expected.directory());
    }

    private static void assertEdge(HardwareCompletionEdge edge, int frame, long ordinal,
                                   String fingerprint, String fixture) {
        assertEquals(frame, edge.rawFrame(), fixture);
        assertEquals(ordinal, edge.ordinal(), fixture);
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
            int eventCount, int firstFrame, int lastFrame,
            long firstOrdinal, long lastOrdinal,
            String firstFingerprint, String lastFingerprint) {
    }
}
