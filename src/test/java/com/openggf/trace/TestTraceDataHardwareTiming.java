package com.openggf.trace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceDataHardwareTiming {

    @TempDir
    Path temporaryDirectory;

    @Test
    void metadataOnlyLoadsVersionOneTimingStream() throws IOException {
        Files.writeString(temporaryDirectory.resolve("metadata.json"), """
                {
                  "game": "s3k",
                  "zone": "test",
                  "zone_id": 0,
                  "act": 1,
                  "bk2_frame_offset": 0,
                  "trace_frame_count": 2,
                  "start_x": "0x0000",
                  "start_y": "0x0000",
                  "trace_schema": 7,
                  "hardware_timing_schema": 1
                }
                """);
        Files.writeString(temporaryDirectory.resolve("hardware_timing.jsonl"), """
                {"event":"hardware_work_completed","raw_frame":1,"boundary":"pre_main_loop","kind":"kos_module_queue","ordinal":0,"submission_fingerprint":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                """);

        TraceData trace = TraceData.loadMetadataOnly(temporaryDirectory);

        assertEquals(0, trace.frameCount());
        assertTrue(trace.metadata().hasHardwareTimingStream());
        assertEquals(1, trace.hardwareTimingSchedule().edges().size());
    }

    @Test
    void metadataOnlyRejectsTimingMetadataAndFileInconsistencies() throws IOException {
        Files.writeString(temporaryDirectory.resolve("metadata.json"), """
                {
                  "game": "s3k", "zone": "test", "zone_id": 0, "act": 1,
                  "bk2_frame_offset": 0, "trace_frame_count": 1,
                  "start_x": "0x0000", "start_y": "0x0000", "trace_schema": 7
                }
                """);
        Files.writeString(temporaryDirectory.resolve("hardware_timing.jsonl"), """
                {"event":"hardware_work_completed","raw_frame":0,"boundary":"vint_service","kind":"kos_module_queue","ordinal":0,"submission_fingerprint":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                """);

        Exception missingKey = assertThrows(Exception.class,
                () -> TraceData.loadMetadataOnly(temporaryDirectory));
        assertTrue(missingKey.getMessage().contains("metadata.json"), missingKey::getMessage);
        assertTrue(missingKey.getMessage().contains("hardware_timing_schema"), missingKey::getMessage);

        Files.delete(temporaryDirectory.resolve("hardware_timing.jsonl"));

        Files.writeString(temporaryDirectory.resolve("metadata.json"), """
                {
                  "game": "s3k", "zone": "test", "zone_id": 0, "act": 1,
                  "bk2_frame_offset": 0, "trace_frame_count": 1,
                  "start_x": "0x0000", "start_y": "0x0000", "trace_schema": 7,
                  "hardware_timing_schema": 1
                }
                """);

        Exception missingFile = assertThrows(Exception.class,
                () -> TraceData.loadMetadataOnly(temporaryDirectory));
        assertTrue(missingFile.getMessage().contains("hardware_timing.jsonl"), missingFile::getMessage);
        assertTrue(missingFile.getMessage().contains("hardware_timing_schema"), missingFile::getMessage);
    }

    @Test
    void metadataOnlyLoadsSchemaTwoDirectAndModuleEdges() throws IOException {
        Files.writeString(temporaryDirectory.resolve("metadata.json"), """
                {
                  "game": "s3k", "zone": "test", "zone_id": 0, "act": 1,
                  "bk2_frame_offset": 0, "trace_frame_count": 2,
                  "start_x": "0x0000", "start_y": "0x0000", "trace_schema": 7,
                  "hardware_timing_schema": 2
                }
                """);
        Files.writeString(temporaryDirectory.resolve("hardware_timing.jsonl"), """
                {"event":"hardware_work_completed","raw_frame":0,"boundary":"pre_main_loop","kind":"kos_decompression_queue","ordinal":0,"submission_fingerprint":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                {"event":"hardware_work_completed","raw_frame":1,"boundary":"post_objects","kind":"kos_module_queue","ordinal":0,"submission_fingerprint":"sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}
                """);

        TraceData trace = TraceData.loadMetadataOnly(temporaryDirectory);

        assertEquals(2, trace.hardwareTimingSchedule().schema());
        assertEquals(2, trace.hardwareTimingSchedule().edges().size());
    }
}
