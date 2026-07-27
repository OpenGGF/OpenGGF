package com.openggf.tests.trace;

import com.openggf.trace.TraceRunManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestTraceRunManifest {

    private static final String VALID_MANIFEST = """
        {
          "run_schema": 1,
          "game": "s3k",
          "run_id": "s3k-aiz-gumball-roundtrip",
          "source_bk2": "s3k-aiz-gumball.bk2",
          "rom_checksum": "C5B1C655C19F462ADE0AC4E17A844D10",
          "lua_script_version": "6.30-s3k-completerun",
          "segments": [
            {"dir": "seg00_aiz", "kind": "level", "trace_profile": "complete_run",
             "bk2_frame_offset": 500, "trace_frame_count": 1200, "zone_id": 0, "act": 1},
            {"dir": "seg01_gumball", "kind": "bonus_stage", "trace_profile": "s3k_bonus_stage",
             "bk2_frame_offset": 1900, "trace_frame_count": 800, "zone_id": 19,
             "bonus_stage_type": "gumball"},
            {"dir": "seg02_aiz", "kind": "level", "trace_profile": "complete_run",
             "bk2_frame_offset": 2900, "trace_frame_count": 600, "zone_id": 0, "act": 1}
          ],
          "transitions": [
            {"from_segment": 0, "to_segment": 1, "entry_kind": "starpost_bonus",
             "mode_change_bk2_frame": 1750, "special_bonus_entry_flag": 2,
             "saved_x_pos": 4660, "saved_y_pos": 1024, "last_star_post_hit": 1,
             "rings_before": 25, "emeralds_before": 0},
            {"from_segment": 1, "to_segment": 2, "entry_kind": "stage_exit",
             "mode_change_bk2_frame": 2800, "rings_after": 40, "emeralds_after": 0}
          ]
        }
        """;

    private Path writeRun(Path dir, String manifestJson, String... segmentDirs)
            throws IOException {
        for (String seg : segmentDirs) {
            Path segDir = dir.resolve(seg);
            Files.createDirectories(segDir);
            Files.writeString(segDir.resolve("metadata.json"), "{}");
        }
        Path manifest = dir.resolve("run_manifest.json");
        Files.writeString(manifest, manifestJson);
        return manifest;
    }

    @Test
    void loadsAndValidatesWellFormedManifest(@TempDir Path dir) throws IOException {
        Path manifest = writeRun(dir, VALID_MANIFEST, "seg00_aiz", "seg01_gumball", "seg02_aiz");
        TraceRunManifest run = TraceRunManifest.load(manifest);
        run.validate(dir);
        assertEquals(3, run.segments().size());
        assertEquals("bonus_stage", run.segments().get(1).kind());
        assertEquals("gumball", run.segments().get(1).bonusStageType());
        assertEquals(2, run.transitions().size());
        assertEquals("starpost_bonus", run.transitions().get(0).entryKind());
        assertEquals(2, run.transitions().get(0).specialBonusEntryFlag());
    }

    @Test
    void defaultsMissingExpectedMovieEndModeToUnspecified(@TempDir Path dir) throws IOException {
        TraceRunManifest run = TraceRunManifest.load(
                writeRun(dir, VALID_MANIFEST, "seg00_aiz", "seg01_gumball", "seg02_aiz"));

        assertEquals(TraceRunManifest.ExpectedMovieEndMode.UNSPECIFIED,
                run.expectedMovieEndMode());
    }

    @Test
    void parsesDeclaredExpectedMovieEndModes(@TempDir Path dir) throws IOException {
        String level = VALID_MANIFEST.replace("\"segments\": [",
                "\"expected_movie_end_mode\": \"level\",\n  \"segments\": [");
        String titleScreen = VALID_MANIFEST.replace("\"segments\": [",
                "\"expected_movie_end_mode\": \"title_screen\",\n  \"segments\": [");

        assertEquals(TraceRunManifest.ExpectedMovieEndMode.LEVEL,
                TraceRunManifest.load(writeRun(
                        dir.resolve("level"), level,
                        "seg00_aiz", "seg01_gumball", "seg02_aiz")).expectedMovieEndMode());
        assertEquals(TraceRunManifest.ExpectedMovieEndMode.TITLE_SCREEN,
                TraceRunManifest.load(writeRun(
                        dir.resolve("title-screen"), titleScreen,
                        "seg00_aiz", "seg01_gumball", "seg02_aiz")).expectedMovieEndMode());
    }

    @Test
    void rejectsUnknownAndNonStringExpectedMovieEndModes(@TempDir Path dir) {
        String unknown = VALID_MANIFEST.replace("\"segments\": [",
                "\"expected_movie_end_mode\": \"credits\",\n  \"segments\": [");
        String nonString = VALID_MANIFEST.replace("\"segments\": [",
                "\"expected_movie_end_mode\": 12,\n  \"segments\": [");

        assertThrows(IOException.class, () -> TraceRunManifest.load(writeRun(
                dir.resolve("unknown"), unknown,
                "seg00_aiz", "seg01_gumball", "seg02_aiz")));
        assertThrows(IOException.class, () -> TraceRunManifest.load(writeRun(
                dir.resolve("non-string"), nonString,
                "seg00_aiz", "seg01_gumball", "seg02_aiz")));
    }

    @Test
    void rejectsUnknownSegmentKind(@TempDir Path dir) throws IOException {
        String bad = VALID_MANIFEST.replace("\"kind\": \"bonus_stage\"", "\"kind\": \"casino\"");
        Path manifest = writeRun(dir, bad, "seg00_aiz", "seg01_gumball", "seg02_aiz");
        TraceRunManifest run = TraceRunManifest.load(manifest);
        IllegalStateException ex =
            assertThrows(IllegalStateException.class, () -> run.validate(dir));
        assertTrue(ex.getMessage().contains("casino"), ex.getMessage());
    }

    @Test
    void rejectsNonMonotonicBk2Offsets(@TempDir Path dir) throws IOException {
        String bad = VALID_MANIFEST.replace("\"bk2_frame_offset\": 2900", "\"bk2_frame_offset\": 100");
        Path manifest = writeRun(dir, bad, "seg00_aiz", "seg01_gumball", "seg02_aiz");
        TraceRunManifest run = TraceRunManifest.load(manifest);
        IllegalStateException ex =
            assertThrows(IllegalStateException.class, () -> run.validate(dir));
        assertTrue(ex.getMessage().contains("bk2_frame_offset"), ex.getMessage());
    }

    @Test
    void rejectsDuplicateSegmentDirectories(@TempDir Path dir) throws IOException {
        String bad = VALID_MANIFEST.replace("\"dir\": \"seg02_aiz\"", "\"dir\": \"seg00_aiz\"");
        Path manifest = writeRun(dir, bad, "seg00_aiz", "seg01_gumball");
        TraceRunManifest run = TraceRunManifest.load(manifest);
        IllegalStateException ex =
            assertThrows(IllegalStateException.class, () -> run.validate(dir));
        assertTrue(ex.getMessage().contains("duplicate segment directory"), ex.getMessage());
        assertTrue(ex.getMessage().contains("seg00_aiz"), ex.getMessage());
    }

    @Test
    void rejectsMissingSegmentDir(@TempDir Path dir) throws IOException {
        Path manifest = writeRun(dir, VALID_MANIFEST, "seg00_aiz", "seg02_aiz"); // seg01 missing
        TraceRunManifest run = TraceRunManifest.load(manifest);
        IllegalStateException ex =
            assertThrows(IllegalStateException.class, () -> run.validate(dir));
        assertTrue(ex.getMessage().contains("seg01_gumball"), ex.getMessage());
    }

    @Test
    void rejectsBonusSegmentWithoutType(@TempDir Path dir) throws IOException {
        String bad = VALID_MANIFEST.replace("\"bonus_stage_type\": \"gumball\"", "\"notes\": \"x\"");
        Path manifest = writeRun(dir, bad, "seg00_aiz", "seg01_gumball", "seg02_aiz");
        TraceRunManifest run = TraceRunManifest.load(manifest);
        IllegalStateException ex =
            assertThrows(IllegalStateException.class, () -> run.validate(dir));
        assertTrue(ex.getMessage().contains("bonus_stage_type"), ex.getMessage());
    }

    @Test
    void rejectsTransitionWithBadIndices(@TempDir Path dir) throws IOException {
        String bad = VALID_MANIFEST.replace("\"from_segment\": 1, \"to_segment\": 2",
                                            "\"from_segment\": 1, \"to_segment\": 5");
        Path manifest = writeRun(dir, bad, "seg00_aiz", "seg01_gumball", "seg02_aiz");
        TraceRunManifest run = TraceRunManifest.load(manifest);
        IllegalStateException ex =
            assertThrows(IllegalStateException.class, () -> run.validate(dir));
        assertTrue(ex.getMessage().contains("to_segment"), ex.getMessage());
    }
}
