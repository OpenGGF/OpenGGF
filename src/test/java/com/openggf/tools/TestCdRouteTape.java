package com.openggf.tools;

import com.openggf.game.soniccd.*;
import com.openggf.route.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestCdRouteTape {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void configuredDiscoveryAndStandardIdentityBindVerifiedDiscContent() throws Exception {
        var path = CdTestMedia.usaCue();
        var identity = RomIdentity.ofFile("scd", path);
        assertEquals(identity, RomIdentity.standard("scd"));
        assertTrue(EngineRouteHosts.fromSystemProperties().has(identity));
        try (var library = com.openggf.mods.scene.host.SceneRomLibrary.openSources(Map.of(), Map.of("scd", path), "scd")) {
            var source = library.source("scd");
            assertNotEquals("", source.identity());
            byte[] bound = ("route-source/v1\0" + source.identity()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(bound)).toUpperCase(), identity.sha1());
        }
    }

    @Test void coverageAllIncludesEveryDiscStageWithoutCertifyingSamples() throws Exception {
        CdTestMedia.usaCue();
        var bytes = new java.io.ByteArrayOutputStream();
        var output = directory.resolve("coverage.json");
        assertEquals(0, StageplayObjectCoverageTool.execute(new String[]{"--game", "all", "--format", "json", "--out", output.toString()}, new java.io.PrintStream(bytes)));
        assertTrue(java.nio.file.Files.readString(output).contains("scd"));
        java.nio.file.Files.delete(output);
        var catalogs = StageplayObjectCoverageTool.collect(java.util.List.of(com.openggf.game.GameId.SCD));
        assertEquals(70, catalogs.size());
        var sample = new StageplayObjectCoverageTool.SampleObservation("scd", 0, 1,
                com.openggf.stageplay.StageplayDocument.ObjectSourceKind.LAYOUT_OBJECT, 0x20, 0,
                com.openggf.stageplay.StageplayDocument.ObjectMode.POSE, "scene.stageplay", "0".repeat(64), 0,
                "0".repeat(40), RomIdentity.ofFile("scd", CdTestMedia.usaCue()).sha1(), java.util.List.of("evidence.png"),
                StageplayObjectCoverageTool.SampleResult.EXCLUDED, java.util.List.of("Admission control only"));
        assertEquals(StageplayObjectCoverageTool.report(catalogs, "revision", java.util.List.of()).groups(),
                StageplayObjectCoverageTool.report(catalogs, "revision", java.util.List.of(sample)).groups());
    }

    @Test void sceneRouteSnapshotReopensSelectedDiscAndRejectsForgedContent() throws Exception {
        var path = CdTestMedia.usaCue();
        var document = com.openggf.stageplay.StageplayDocument.empty("scd", 0, 1, 0, 0, 320, 224);
        var request = new com.openggf.stageplay.route.StageplayRouteRequest("hero", 0, 800, 500, 1);
        var snapshot = com.openggf.stageplay.route.StageplayRouteInputs.captureSources(document, request, directory, Map.of("scd", path));
        assertEquals(snapshot.fingerprint(), com.openggf.stageplay.route.StageplayRouteInputs.captureSources(document, request, directory, Map.of("scd", path)).fingerprint());
        try (var library = com.openggf.stageplay.runtime.StageplayRomTransport.openLibrary(snapshot.romDescriptor(), "scd")) {
            assertTrue(library.source("scd").rom().isEmpty());
            var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(snapshot.romDescriptor());
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path("media").path("scd")).put("identity", "disc:scd:sha256:" + "0".repeat(64));
            assertThrows(java.io.IOException.class, () -> com.openggf.stageplay.runtime.StageplayRomTransport.openLibrary(tree.toString(), "scd"));
        }
        var invalid = java.nio.file.Files.writeString(directory.resolve("invalid.cue"), "not a disc");
        assertThrows(java.io.IOException.class, () -> com.openggf.stageplay.route.StageplayRouteInputs.captureSources(document, request, directory, Map.of("scd", invalid)));
    }

    @Test void recordedRowsBootTheExactPastStageThroughTheDiscPipeline() throws Exception {
        var path = CdTestMedia.usaCue();
        var launch = LaunchContract.coldLevel(RomIdentity.ofFile("scd", path), new LevelId("scd", 0, 1),
                new Roster("sonic", ""), 320, 0);
        var route = RouteArtifact.of(launch, InputTape.of(java.util.List.of(PadRow.NEUTRAL)),
                new Provenance("CD disc route test", "", "", java.util.List.of()));
        try (var world = new RouteReplay(EngineRouteHosts.withRoms(Map.of("scd", path))).open(route)) {
            assertEquals(CdStagePayload.R11B, assertInstanceOf(com.openggf.game.soniccd.level.CdLevel.class,
                    com.openggf.game.GameServices.level().getCurrentLevel()).payload());
            assertEquals(0, world.row());
            world.step();
            assertEquals(1, world.row());
        }
    }
}
