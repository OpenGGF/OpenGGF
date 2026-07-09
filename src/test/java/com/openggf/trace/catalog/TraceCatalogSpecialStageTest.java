package com.openggf.trace.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD coverage for {@code trace_profile}-aware {@link TraceCatalog} scanning
 * and {@link TraceEntry#displayLabel()}: an {@code s2_special_stage} trace
 * (no zone/act keys) must still scan and must label itself by
 * {@code special_stage_index} rather than zone/act, while an ordinary level
 * trace keeps the existing zone/act-derived label unchanged.
 */
class TraceCatalogSpecialStageTest {

    @Test
    void scanIncludesSpecialStageTraceAlongsideLevelTrace(@TempDir Path tmp) throws Exception {
        writeSpecialStageTrace(tmp.resolve("s2/special_stage"), 2, "ss.bk2");
        writeLevelTrace(tmp.resolve("s2/ehz1"), 0, 1);

        List<TraceEntry> entries = TraceCatalog.scan(tmp);

        assertEquals(2, entries.size());

        TraceEntry ssEntry = entries.stream()
                .filter(e -> "s2_special_stage".equals(e.metadata().traceProfile()))
                .findFirst().orElseThrow(() -> new AssertionError("SS entry not scanned"));
        assertEquals("S2 SPECIAL STAGE 3", ssEntry.displayLabel(),
                "special_stage_index=2 -> displayed as 1-indexed stage 3");

        TraceEntry levelEntry = entries.stream()
                .filter(e -> e != ssEntry)
                .findFirst().orElseThrow(() -> new AssertionError("Level entry not scanned"));
        assertEquals(String.format("Zone: %02X  Act: %d", levelEntry.zone(), levelEntry.act()),
                levelEntry.displayLabel(),
                "level trace label must match the picker's previous zone/act formatting");
    }

    @Test
    void displayLabelNullSpecialStageIndexTreatedAsZero(@TempDir Path tmp) throws Exception {
        writeSpecialStageTrace(tmp.resolve("s2/special_stage"), null, "ss.bk2");

        List<TraceEntry> entries = TraceCatalog.scan(tmp);

        assertEquals(1, entries.size());
        assertEquals("S2 SPECIAL STAGE 1", entries.getFirst().displayLabel());
    }

    private static void writeSpecialStageTrace(Path dir, Integer specialStageIndex, String bk2Name)
            throws Exception {
        Files.createDirectories(dir);
        String ssIndexLine = specialStageIndex != null
                ? ",\n  \"special_stage_index\": " + specialStageIndex
                : "";
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s2",
              "trace_profile": "s2_special_stage",
              "trace_schema": 1,
              "csv_version": 1,
              "bk2_frame_offset": 0,
              "main_character": "sonic",
              "sidekicks": [],
              "source_bk2": "%s"%s
            }
            """.formatted(bk2Name, ssIndexLine));
        Files.writeString(dir.resolve("physics.csv"),
                "0,0,0,0,0,0,0,0,0,0,0\n1,0,0,0,0,0,0,0,0,0,0\n");
        Files.writeString(dir.resolve(bk2Name), "stub");
    }

    private static void writeLevelTrace(Path dir, int zoneId, int act) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("metadata.json"), String.format("""
            {
              "game": "s2",
              "zone": "ZONE",
              "zone_id": %d,
              "act": %d,
              "trace_schema": 3,
              "bk2_frame_offset": 100,
              "pre_trace_osc_frames": 12,
              "main_character": "sonic",
              "sidekicks": []
            }
            """, zoneId, act));
        Files.writeString(dir.resolve("physics.csv"),
                "0,0,0,0,0,0,0,0,0,0,0\n1,0,0,0,0,0,0,0,0,0,0\n");
        Files.writeString(dir.resolve("trace.bk2"), "stub");
    }
}
