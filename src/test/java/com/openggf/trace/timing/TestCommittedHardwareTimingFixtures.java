package com.openggf.trace.timing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceRunManifest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the immutable publication evidence for the approved S3K hardware-timing
 * fleet. The TSV expectations are reviewed literals from native candidates,
 * never values derived by invoking the recorder during this test.
 */
class TestCommittedHardwareTimingFixtures {

    private static final Path FIXTURE_ROOT = resolveProjectRoot()
            .resolve("src/test/resources/traces/s3k");
    private static final Path PUBLICATION_MANIFEST =
            FIXTURE_ROOT.resolve("hardware-timing-publication.tsv");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Ownership> EXPECTED_STANDALONE_OWNERSHIP =
            Map.ofEntries(
                    Map.entry("aiz1_to_hcz_fullrun",
                            new Ownership("v637-aiz-hcz-transition",
                                    "aiz1_to_hcz_fullrun")),
                    Map.entry("cnz", new Ownership("v637-standard-cnz", ".")),
                    Map.entry("mgz", new Ownership("v637-standard-mgz", ".")),
                    Map.entry("aiz_completerun",
                            new Ownership("v637-complete-sonic-tails", "aiz")),
                    Map.entry("hcz_completerun",
                            new Ownership("v637-complete-sonic-tails", "hcz")),
                    Map.entry("mgz_completerun",
                            new Ownership("v637-complete-sonic-tails", "mgz")),
                    Map.entry("cnz_completerun",
                            new Ownership("v637-complete-sonic-tails", "cnz")),
                    Map.entry("icz_completerun",
                            new Ownership("v637-complete-sonic-tails", "icz")),
                    Map.entry("lbz_completerun",
                            new Ownership("v637-complete-sonic-tails", "lbz")),
                    Map.entry("mhz_completerun",
                            new Ownership("v637-complete-sonic-tails", "mhz")),
                    Map.entry("fbz_completerun",
                            new Ownership("v637-complete-sonic-tails", "fbz")),
                    Map.entry("soz_completerun",
                            new Ownership("v637-complete-sonic-tails", "soz")),
                    Map.entry("lrz_completerun",
                            new Ownership("v637-complete-sonic-tails", "lrz")),
                    Map.entry("hpz_completerun",
                            new Ownership("v637-complete-sonic-tails", "hpz22")),
                    Map.entry("ssz_completerun",
                            new Ownership("v637-complete-sonic-tails", "hpz")),
                    Map.entry("dez_completerun",
                            new Ownership("v637-complete-sonic-tails", "ssz")),
                    Map.entry("ddz_completerun",
                            new Ownership("v637-complete-sonic-tails", "dez23")),
                    Map.entry("ending_completerun",
                            new Ownership("v637-complete-sonic-tails", "ddz")),
                    Map.entry("bonus_gumball",
                            new Ownership("v637-knuckles-c", "gumball")),
                    Map.entry("bonus_slots",
                            new Ownership("v637-knuckles-c", "slots")),
                    Map.entry("bonus_pachinko",
                            new Ownership("v637-knuckles-c", "pachinko")),
                    Map.entry("special_stage",
                            new Ownership("v637-knuckles-c", "ss")));
    private static final Set<String> EXPECTED_DESTINATIONS = Set.of(
            "aiz1_to_hcz_fullrun",
            "aiz_completerun",
            "bonus_gumball",
            "bonus_pachinko",
            "bonus_slots",
            "cnz",
            "cnz_completerun",
            "ddz_completerun",
            "dez_completerun",
            "ending_completerun",
            "fbz_completerun",
            "hcz_completerun",
            "hpz_completerun",
            "icz_completerun",
            "lbz_completerun",
            "lrz_completerun",
            "mgz",
            "mgz_completerun",
            "mhz_completerun",
            "runs/s3-knux-multibonus-ss/aiz",
            "runs/s3-knux-multibonus-ss/aiz_2",
            "runs/s3-knux-multibonus-ss/aiz_3",
            "runs/s3-knux-multibonus-ss/aiz_4",
            "runs/s3-knux-multibonus-ss/aiz_5",
            "runs/s3-knux-multibonus-ss/gumball",
            "runs/s3-knux-multibonus-ss/gumball_2",
            "runs/s3-knux-multibonus-ss/hcz",
            "runs/s3-knux-multibonus-ss/hcz_2",
            "runs/s3-knux-multibonus-ss/hcz_3",
            "runs/s3-knux-multibonus-ss/hcz_4",
            "runs/s3-knux-multibonus-ss/hcz_5",
            "runs/s3-knux-multibonus-ss/hcz_6",
            "runs/s3-knux-multibonus-ss/mgz",
            "runs/s3-knux-multibonus-ss/mgz_2",
            "runs/s3-knux-multibonus-ss/mgz_3",
            "runs/s3-knux-multibonus-ss/pachinko",
            "runs/s3-knux-multibonus-ss/slots",
            "runs/s3-knux-multibonus-ss/slots_2",
            "runs/s3-knux-multibonus-ss/slots_3",
            "runs/s3-knux-multibonus-ss/slots_4",
            "runs/s3-knux-multibonus-ss/slots_5",
            "runs/s3-knux-multibonus-ss/ss",
            "runs/s3-knux-multibonus-ss/ss_2",
            "runs/s3-knux-multibonus-ss/ss_3",
            "soz_completerun",
            "special_stage",
            "ssz_completerun");

    @Test
    void approvedFleetMatchesFrozenPublicationEvidence() throws Exception {
        Set<String> observedDestinations = new HashSet<>();
        List<RunManifestExpectation> runManifests = new ArrayList<>();

        for (String line : Files.readAllLines(PUBLICATION_MANIFEST)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            switch (fields[0]) {
                case "FIXTURE" -> {
                    ExpectedFixture expected = ExpectedFixture.parse(fields);
                    assertTrue(observedDestinations.add(expected.directory()),
                            "duplicate publication destination " + expected.directory());
                    assertOwnership(expected);
                    assertFixture(expected);
                }
                case "RUN_MANIFEST" ->
                        runManifests.add(RunManifestExpectation.parse(fields));
                default -> throw new IllegalArgumentException(
                        "Unknown publication record " + fields[0]);
            }
        }

        assertEquals(EXPECTED_DESTINATIONS, observedDestinations);
        assertEquals(1, runManifests.size());
        assertRunManifest(runManifests.getFirst());
    }

    private static void assertOwnership(ExpectedFixture expected) {
        Ownership ownership = EXPECTED_STANDALONE_OWNERSHIP.get(
                expected.directory());
        if (ownership == null && expected.directory().startsWith(
                "runs/s3-knux-multibonus-ss/")) {
            ownership = new Ownership(
                    "v637-knuckles-b",
                    Path.of(expected.directory()).getFileName().toString());
        }
        assertEquals(ownership,
                new Ownership(expected.owner(), expected.sourceSegment()),
                expected.directory());
    }

    private static void assertFixture(ExpectedFixture expected) throws Exception {
        Path fixture = FIXTURE_ROOT.resolve(expected.directory());
        Path metadataPath = fixture.resolve("metadata.json");
        JsonNode metadataJson = MAPPER.readTree(metadataPath.toFile());

        assertEquals(expected.traceSchema(), metadataJson.path("trace_schema").intValue(),
                expected.directory());
        assertEquals(expected.hardwareSchema(),
                metadataJson.path("hardware_timing_schema").intValue(),
                expected.directory());
        assertEquals(expected.recorderVersion(),
                metadataJson.path("lua_script_version").textValue(),
                expected.directory());
        assertEquals(expected.frameCount(),
                metadataJson.path("trace_frame_count").intValue(),
                expected.directory());
        if ("v637-knuckles-b".equals(expected.owner())) {
            assertEquals("s3-knux-multibonus-ss",
                    metadataJson.path("run_id").textValue(), expected.directory());
        } else if ("v637-knuckles-c".equals(expected.owner())) {
            assertEquals("s3k-multibonus",
                    metadataJson.path("run_id").textValue(), expected.directory());
        } else {
            assertFalse(metadataJson.has("run_id"), expected.directory());
        }
        assertFalse(Files.exists(fixture.resolve("run_manifest.json")),
                expected.directory());

        assertFile(fixture, expected.metadata());
        assertFile(fixture, expected.physics());
        assertFile(fixture, expected.aux());
        assertFile(fixture, expected.timing());

        TraceMetadata metadata = TraceMetadata.load(metadataPath);
        List<HardwareCompletionEdge> edges =
                HardwareTimingStreamLoader.load(fixture, metadata).edges();
        assertEquals(expected.eventCount(), edges.size(), expected.directory());
        assertFalse(edges.isEmpty(), expected.directory());
        assertEdge(edges.getFirst(), expected.firstEdge(), expected.directory());
        assertEdge(edges.getLast(), expected.lastEdge(), expected.directory());
        assertTrue(edges.stream().allMatch(edge ->
                        edge.rawFrame() >= 0
                                && edge.rawFrame() < metadata.traceFrameCount()
                                && (edge.boundary() == HardwareServiceBoundary.VINT_SERVICE
                                    || edge.boundary() == HardwareServiceBoundary.POST_OBJECTS)
                                && edge.kind() == HardwareWorkKind.KOS_MODULE_QUEUE),
                expected.directory());
        assertEquals(expected.vintServiceEventCount(),
                edges.stream().filter(edge ->
                        edge.boundary() == HardwareServiceBoundary.VINT_SERVICE).count(),
                expected.directory());
        assertEquals(expected.postObjectsEventCount(),
                edges.stream().filter(edge ->
                        edge.boundary() == HardwareServiceBoundary.POST_OBJECTS).count(),
                expected.directory());
    }

    private static void assertRunManifest(RunManifestExpectation expected)
            throws Exception {
        assertEquals("runs/s3-knux-multibonus-ss/run_manifest.json",
                expected.path());
        assertEquals("v637-knuckles-b", expected.owner());
        Path path = FIXTURE_ROOT.resolve(expected.path());
        assertFile(path, expected.hash(), expected.bytes());
        TraceRunManifest manifest = TraceRunManifest.load(path);
        assertEquals(expected.segmentCount(), manifest.segments().size());
        assertEquals(expected.transitionCount(), manifest.transitions().size());
    }

    private static void assertEdge(HardwareCompletionEdge actual,
            EdgeExpectation expected, String fixture) {
        assertEquals(expected.rawFrame(), actual.rawFrame(), fixture);
        assertEquals(expected.ordinal(), actual.ordinal(), fixture);
        assertEquals(expected.boundary(), actual.boundary(), fixture);
        assertEquals(expected.kind(), actual.kind(), fixture);
        assertEquals(expected.fingerprint(), actual.submissionFingerprint(), fixture);
    }

    private static void assertFile(Path directory, FileExpectation expected)
            throws IOException, NoSuchAlgorithmException {
        assertFile(directory.resolve(expected.name()), expected.hash(), expected.bytes());
    }

    private static void assertFile(Path path, String expectedHash, long expectedBytes)
            throws IOException, NoSuchAlgorithmException {
        byte[] content = Files.readAllBytes(path);
        assertEquals(expectedBytes, content.length, path.toString());
        assertEquals(expectedHash,
                HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(content)),
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
            String owner,
            String sourceSegment,
            int traceSchema,
            int hardwareSchema,
            String recorderVersion,
            int frameCount,
            FileExpectation metadata,
            FileExpectation physics,
            FileExpectation aux,
            FileExpectation timing,
            int eventCount,
            int postObjectsEventCount,
            int vintServiceEventCount,
            EdgeExpectation firstEdge,
            EdgeExpectation lastEdge) {

        private static ExpectedFixture parse(String[] fields) {
            assertFieldCount(fields, 17);
            return new ExpectedFixture(
                    fields[1], fields[2], fields[3],
                    Integer.parseInt(fields[4]), Integer.parseInt(fields[5]),
                    fields[6], Integer.parseInt(fields[7]),
                    FileExpectation.parse(fields[8]),
                    FileExpectation.parse(fields[9]),
                    FileExpectation.parse(fields[10]),
                    FileExpectation.parse(fields[11]),
                    Integer.parseInt(fields[12]), Integer.parseInt(fields[13]),
                    Integer.parseInt(fields[14]),
                    EdgeExpectation.parse(fields[15]),
                    EdgeExpectation.parse(fields[16]));
        }
    }

    private record FileExpectation(String name, long bytes, String hash) {
        private static FileExpectation parse(String field) {
            String[] parts = field.split(":", 3);
            assertFieldCount(parts, 3);
            return new FileExpectation(
                    parts[0], Long.parseLong(parts[1]), parts[2]);
        }
    }

    private record Ownership(String owner, String sourceSegment) {
    }

    private record EdgeExpectation(
            int rawFrame,
            long ordinal,
            HardwareServiceBoundary boundary,
            HardwareWorkKind kind,
            String fingerprint) {

        private static EdgeExpectation parse(String field) {
            String[] parts = field.split(":", 5);
            assertFieldCount(parts, 5);
            return new EdgeExpectation(
                    Integer.parseInt(parts[0]), Long.parseLong(parts[1]),
                    HardwareServiceBoundary.fromWireName(parts[2]),
                    HardwareWorkKind.fromWireName(parts[3]), parts[4]);
        }
    }

    private record RunManifestExpectation(
            String path,
            String owner,
            long bytes,
            String hash,
            int segmentCount,
            int transitionCount) {

        private static RunManifestExpectation parse(String[] fields) {
            assertFieldCount(fields, 7);
            return new RunManifestExpectation(
                    fields[1], fields[2], Long.parseLong(fields[3]), fields[4],
                    Integer.parseInt(fields[5]), Integer.parseInt(fields[6]));
        }
    }

    private static void assertFieldCount(String[] fields, int expected) {
        if (fields.length != expected) {
            throw new IllegalArgumentException(
                    "Expected " + expected + " fields, got " + fields.length);
        }
    }
}
