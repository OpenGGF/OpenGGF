package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.GameId;
import com.openggf.stageplay.StageplayDocument.ObjectMode;
import com.openggf.stageplay.StageplayDocument.ObjectSourceKind;
import com.openggf.stageplay.StageplayObjectCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestStageplayObjectCoverageTool {
    @TempDir Path directory;

    @Test
    void completeStockInventoryRetainsEveryContextAndEveryByteIdWhenGrouped() throws Exception {
        var catalogs = StageplayObjectCoverageTool.collect(List.of(GameId.S1, GameId.S2, GameId.S3K));
        var report = StageplayObjectCoverageTool.report(catalogs, "source-sha");
        assertEquals("REGISTRATION_ONLY", report.evidence());
        assertEquals(catalogs.size(), report.sourceContexts());
        assertEquals(256L * catalogs.size(), report.objectIdContextRows());
        var expected = new HashSet<String>();
        for (var catalog : catalogs) expected.add(catalog.gameId() + ":" + catalog.context());
        var actual = new HashSet<String>();
        for (var group : report.groups()) {
            assertEquals(256, group.entries().size());
            for (int id = 0; id < 256; id++) assertEquals(id, group.entries().get(id).objectId());
            for (var context : group.contexts()) assertTrue(actual.add(group.gameId() + ":" + context));
        }
        assertEquals(expected, actual);
        assertEquals(3, report.groups().stream().map(StageplayObjectCoverageTool.Group::gameId).distinct().count());
        var json = new ObjectMapper().readTree(StageplayObjectCoverageTool.json(report));
        assertEquals(report.objectIdContextRows(), json.get("objectIdContextRows").asLong());
        assertEquals("source-sha", json.get("sourceRevision").asText());
        assertTrue(json.get("observations").isEmpty());
        assertTrue(json.get("groups").findValuesAsText("freeRestriction")
                .contains(StageplayObjectCatalog.NATIVE_ARENA_LABEL));
        String markdown = StageplayObjectCoverageTool.markdown(report);
        assertTrue(markdown.contains("Free: native arena position, anchor ignored"));
        assertTrue(markdown.lines().filter(line -> line.startsWith("|56|"))
                .anyMatch(line -> line.contains(StageplayObjectCatalog.NATIVE_ARENA_LABEL) && line.contains("Obj56_Init")));
        assertTrue(markdown.contains("VISUAL_METADATA_MISSING"));
        assertTrue(markdown.contains("Obj56_Init"));
        assertTrue(markdown.contains("|00|"));
        assertTrue(markdown.contains("|FF|"));
        assertArrayEquals(StageplayObjectCoverageTool.json(report), StageplayObjectCoverageTool.json(report));
    }

    @Test
    void sampleResultsKeepExactScopeAndNeverPromoteRegistryRowsOrAuxiliaryVariants() throws Exception {
        var catalogs = StageplayObjectCoverageTool.collect(List.of(GameId.S2));
        var original = StageplayObjectCoverageTool.report(catalogs, "revision");
        var sample = sample(ObjectSourceKind.LAYOUT_OBJECT, 0x56, ObjectMode.POSE,
                StageplayObjectCoverageTool.SampleResult.CAPTURED, List.of("Aircraft appearance held"));
        var animal = sample(ObjectSourceKind.RELEASED_ANIMAL, 0, ObjectMode.PUPPET,
                StageplayObjectCoverageTool.SampleResult.CAPTURED, List.of("Only this native animal selection observed"));
        var excluded = sample(ObjectSourceKind.LAYOUT_OBJECT, 0x56, ObjectMode.PUPPET,
                StageplayObjectCoverageTool.SampleResult.EXCLUDED, List.of("Native graph has not initialized; start Free in its native arena"));
        var report = StageplayObjectCoverageTool.report(catalogs, "revision", List.of(sample, animal, excluded));
        assertEquals(original.groups(), report.groups(), "a sample must not upgrade any exhaustive row or auxiliary entry");
        assertEquals(original.objectIdContextRows(), report.objectIdContextRows());
        assertEquals("REGISTRY_METADATA_WITH_SAMPLE_OBSERVATIONS", report.evidence());
        assertEquals(List.of(sample, animal, excluded), report.observations());
        String markdown = StageplayObjectCoverageTool.markdown(report);
        assertTrue(markdown.contains("POSE / 180 / CAPTURED"));
        assertTrue(markdown.contains("RELEASED_ANIMAL / released-animal"));
        assertTrue(markdown.contains("start Free in its native arena"));
        assertThrows(IllegalArgumentException.class, () -> StageplayObjectCoverageTool.report(
                StageplayObjectCoverageTool.collect(List.of(GameId.S1)), "revision", List.of(sample)));
    }

    @Test
    void observationInputRequiresEveryTypedFieldAndRejectsUnknownOrInvalidClaims() throws Exception {
        var mapper = new ObjectMapper();
        var sample = sample(ObjectSourceKind.LAYOUT_OBJECT, 0x56, ObjectMode.FREE,
                StageplayObjectCoverageTool.SampleResult.CAPTURED, List.of());
        var valid = mapper.valueToTree(new StageplayObjectCoverageTool.ObservationFile(1, List.of(sample)));
        Path input = directory.resolve("observations.json");
        Files.write(input, mapper.writeValueAsBytes(valid));
        assertEquals(List.of(sample), StageplayObjectCoverageTool.readObservations(input));
        var mutations = List.<java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode>>of(
                node -> node.put("unknown", true),
                node -> sampleNode(node).put("unknown", true),
                node -> sampleNode(node).remove("objectId"),
                node -> sampleNode(node).put("frame", -1),
                node -> sampleNode(node).put("frame", 0.5),
                node -> sampleNode(node).put("frame", "180"),
                node -> sampleNode(node).put("mode", "SWIM"),
                node -> sampleNode(node).put("mode", 0),
                node -> sampleNode(node).put("romSha1", "unverified"),
                node -> sampleNode(node).put("sceneSha256", "unverified"),
                node -> sampleNode(node).put("sourceRevision", "dirty"),
                node -> sampleNode(node).put("sourceKind", "RELEASED_ANIMAL"),
                node -> sampleNode(node).put("result", "EXCLUDED"),
                node -> sampleNode(node).putArray("evidencePaths"));
        for (var mutation : mutations) {
            var invalid = (com.fasterxml.jackson.databind.node.ObjectNode) valid.deepCopy();
            mutation.accept(invalid);
            Files.write(input, mapper.writeValueAsBytes(invalid));
            assertThrows(java.io.IOException.class, () -> StageplayObjectCoverageTool.readObservations(input), invalid.toString());
        }
        Files.writeString(input, mapper.writeValueAsString(valid).replace("\"version\":1", "\"version\":1,\"version\":1"));
        assertThrows(java.io.IOException.class, () -> StageplayObjectCoverageTool.readObservations(input));
    }

    @Test
    void commandImportsReviewedSamplesWithoutReplacingTheRegistrationInventory() throws Exception {
        Path observations = directory.resolve("observations.json");
        var sample = sample(ObjectSourceKind.LAYOUT_OBJECT, 0x56, ObjectMode.FREE,
                StageplayObjectCoverageTool.SampleResult.CAPTURED, List.of());
        Files.write(observations, new ObjectMapper().writeValueAsBytes(new StageplayObjectCoverageTool.ObservationFile(1, List.of(sample))));
        Path destination = directory.resolve("observed-coverage.json");
        var output = new ByteArrayOutputStream();
        assertEquals(0, StageplayObjectCoverageTool.execute(new String[]{"--game", "s2", "--out", destination.toString(),
                "--observations", observations.toString()}, new PrintStream(output)));
        var report = new ObjectMapper().readTree(Files.readAllBytes(destination));
        assertEquals("REGISTRY_METADATA_WITH_SAMPLE_OBSERVATIONS", report.path("evidence").asText());
        assertEquals(1, report.path("observations").size());
        assertEquals("CAPTURED", report.path("observations").get(0).path("result").asText());
        assertEquals(256L * report.path("sourceContexts").asInt(), report.path("objectIdContextRows").asLong());
    }

    private static StageplayObjectCoverageTool.SampleObservation sample(ObjectSourceKind sourceKind, int objectId,
            ObjectMode mode, StageplayObjectCoverageTool.SampleResult result, List<String> limitations) {
        return new StageplayObjectCoverageTool.SampleObservation("s2", 0, 1, sourceKind, objectId, 0, mode,
                "/evidence/scene.json", "a".repeat(64), 180, "b".repeat(40), "c".repeat(40),
                List.of("/evidence/still.png", "/evidence/film.mp4"), result, limitations);
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode sampleNode(com.fasterxml.jackson.databind.node.ObjectNode root) {
        return (com.fasterxml.jackson.databind.node.ObjectNode) root.path("observations").get(0);
    }

    @Test
    void commandPublishesOneReportAtomicallyAndRejectsUnknownOrDuplicateFlags() throws Exception {
        var output = new ByteArrayOutputStream();
        var stream = new PrintStream(output);
        Path report = directory.resolve("coverage.json");
        String[] args = {"--game", "s2", "--out", report.toString(), "--source-revision", "sha"};
        assertEquals(0, StageplayObjectCoverageTool.execute(args, stream));
        assertTrue(new ObjectMapper().readTree(output.toByteArray()).get("ok").asBoolean());
        byte[] original = Files.readAllBytes(report);
        assertEquals(2, StageplayObjectCoverageTool.execute(args, stream), "published evidence is never overwritten");
        assertArrayEquals(original, Files.readAllBytes(report));
        assertEquals(2, StageplayObjectCoverageTool.execute(new String[]{"--unknown", "x"}, stream));
        assertEquals(2, StageplayObjectCoverageTool.execute(new String[]{"--out", "one", "--out", "two"}, stream));
        assertEquals(2, StageplayObjectCoverageTool.execute(new String[]{"--out"}, stream));
        assertEquals(2, StageplayObjectCoverageTool.execute(new String[]{"--game", "s2"}, stream));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
