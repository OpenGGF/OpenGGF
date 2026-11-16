package com.openggf.mods.scene.host;

import com.openggf.game.soniccd.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestCdSceneLibrary {
    @Test void workerReopensTheExactVerifiedDiscAndRejectsChangedIdentity() throws Exception {
        var path = CdTestMedia.usaCue();
        String identity;
        try (var disc = CdDiscDataSource.open(path)) { identity = disc.identity(); }
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = json.createObjectNode().put("version", 2);
        root.putNull("cartridges");
        root.putObject("media").putObject("scd").put("path", path.toAbsolutePath().toString()).put("identity", identity);
        try (var library = com.openggf.stageplay.runtime.StageplayRomTransport.openLibrary(root.toString(), "scd")) {
            assertEquals(identity, library.source("scd").identity());
            assertTrue(library.source("scd").rom().isEmpty());
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("media").path("scd")).put("identity", "changed");
        assertThrows(java.io.IOException.class, () ->
                com.openggf.stageplay.runtime.StageplayRomTransport.openLibrary(root.toString(), "scd"));
    }

    @Test void offlineDiscHasOwnedSourceAndExactStageArt() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("scd", CdTestMedia.usaCue()))) {
            assertEquals(java.util.List.of("scd"), library.availableGames());
            assertNull(library.sourceRom("scd"));
            var first = library.program("scd", CdStagePayload.R11A.file().isoName());
            var past = library.program("scd", CdStagePayload.R11B.file().isoName());
            assertNotEquals(first.romSha1(), past.romSha1());
            assertNotNull(first.character("sonic"));
            assertNotNull(past.character("sonic"));
            assertEquals(CdStagePayload.R11B, assertInstanceOf(com.openggf.game.soniccd.level.CdLevel.class,
                    library.level("scd", 0, 1)).payload());
        }
    }
}
