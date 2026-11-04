package com.openggf.trace;

import com.openggf.game.session.LevelEntryProvenance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Recorder-observed level_entry_placement_source parsing and the entry kind it selects. */
class TestLevelEntryPlacementSource {
    @TempDir Path dir;

    private TraceMetadata load(String game, String placement, boolean nativePrelude) throws Exception {
        String extras = nativePrelude ? "[\"native_prelude_bootstrap\"]" : "[]";
        String json = "{\n"
                + "  \"game\": \"" + game + "\",\n"
                + "  \"zone\": \"hcz\",\n"
                + "  \"zone_id\": 1,\n"
                + "  \"act\": 1,\n"
                + "  \"bk2_frame_offset\": 10,\n"
                + "  \"trace_frame_count\": 5,\n"
                + "  \"start_x\": \"0x0100\",\n"
                + "  \"start_y\": \"0x0200\",\n"
                + (placement == null ? "" : "  \"level_entry_placement_source\": " + placement + ",\n")
                + "  \"aux_schema_extras\": " + extras + ",\n"
                + "  \"trace_schema\": 5\n"
                + "}\n";
        Path file = dir.resolve(game + "-" + nativePrelude + "-" + Math.abs(String.valueOf(placement).hashCode()) + ".json");
        Files.writeString(file, json);
        return TraceMetadata.load(file);
    }

    @Test
    void parsesEveryRecorderValueAndKeepsAbsenceDistinct() throws Exception {
        assertNull(load("s3k", null, false).levelEntryPlacementSource());
        assertEquals(LevelEntryPlacementSource.START_TABLE,
                load("s3k", "\"start_table\"", false).levelEntryPlacementSource());
        assertEquals(LevelEntryPlacementSource.STARPOST_RESTORE,
                load("s3k", "\"starpost_restore\"", false).levelEntryPlacementSource());
        assertEquals(LevelEntryPlacementSource.SPECIAL_STAGE_RESTORE,
                load("s3k", "\"special_stage_restore\"", false).levelEntryPlacementSource());
    }

    @Test
    void rejectsUnknownAndNonStringValuesAtLoad() {
        assertThrows(IllegalArgumentException.class, () -> load("s3k", "\"saved2\"", false));
        assertThrows(IllegalArgumentException.class, () -> load("s3k", "1", false));
    }

    @Test
    void missingEvidenceKeepsTheUnclassifiedEntry() throws Exception {
        assertEquals(LevelEntryProvenance.UNCLASSIFIED_ENTRY,
                TraceReplayBootstrap.recordedEntryPlacementProvenance(load("s3k", null, false)));
        assertEquals(LevelEntryProvenance.UNCLASSIFIED_ENTRY,
                TraceReplayBootstrap.recordedEntryPlacementProvenance(load("s2", null, true)));
    }

    @Test
    void s3kSegmentsMapToTheRomBranchPostSpawnBody() throws Exception {
        assertEquals(LevelEntryProvenance.POSITIONED_FRESH_SPAWN,
                TraceReplayBootstrap.recordedEntryPlacementProvenance(
                        load("s3k", "\"start_table\"", false)));
        assertEquals(LevelEntryProvenance.POSITIONED_RETURN_SPAWN,
                TraceReplayBootstrap.recordedEntryPlacementProvenance(
                        load("s3k", "\"starpost_restore\"", false)));
        assertEquals(LevelEntryProvenance.POSITIONED_RETURN_SPAWN,
                TraceReplayBootstrap.recordedEntryPlacementProvenance(
                        load("s3k", "\"special_stage_restore\"", false)));
    }

    @Test
    void nativePreludeStartTableOwnsProductionPlacementWhileS2RestoresStayUnclassified() throws Exception {
        assertEquals(LevelEntryProvenance.NATIVE_LEVEL_START,
                TraceReplayBootstrap.recordedEntryPlacementProvenance(
                        load("s2", "\"start_table\"", true)));
        assertEquals(LevelEntryProvenance.UNCLASSIFIED_ENTRY,
                TraceReplayBootstrap.recordedEntryPlacementProvenance(
                        load("s2", "\"starpost_restore\"", true)));
    }
}
