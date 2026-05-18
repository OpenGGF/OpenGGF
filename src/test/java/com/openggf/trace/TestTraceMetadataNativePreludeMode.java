package com.openggf.trace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link TraceMetadata#nativePreludeMode()} derives the
 * bootstrap-comparator eligibility from {@code lua_script_version}. Traces at
 * or after v9.2-s2 were recorded against the post-universal-title-card engine
 * (ADR-1, design spec 2026-05-15); earlier traces are skipped.
 */
public class TestTraceMetadataNativePreludeMode {

    private static String baseMetadata(String luaScriptVersion) {
        String version = luaScriptVersion == null ? "null" : "\"" + luaScriptVersion + "\"";
        return """
            "game": "s1",
            "zone": "ghz",
            "zone_id": 0,
            "act": 1,
            "bk2_frame_offset": 0,
            "trace_frame_count": 1,
            "start_x": "0x0050",
            "start_y": "0x03B0",
            "recording_date": "2026-05-15",
            "lua_script_version": %s,
            "trace_schema": 4,
            "csv_version": 4,
            "rom_checksum": "",
            "notes": ""
            """.formatted(version);
    }

    @Test
    void absentVersionReturnsFalse() throws IOException {
        Path dir = Files.createTempDirectory("trace-meta-no-version");
        Files.writeString(dir.resolve("metadata.json"),
                "{\n" + baseMetadata(null) + "}\n");

        TraceMetadata meta = TraceMetadata.load(dir.resolve("metadata.json"));

        assertFalse(meta.nativePreludeMode(),
                "Absent lua_script_version must yield nativePreludeMode=false");
    }

    @Test
    void legacyVersionReturnsFalse() throws IOException {
        Path dir = Files.createTempDirectory("trace-meta-v9-1");
        Files.writeString(dir.resolve("metadata.json"),
                "{\n" + baseMetadata("9.1-s2") + "}\n");

        TraceMetadata meta = TraceMetadata.load(dir.resolve("metadata.json"));

        assertFalse(meta.nativePreludeMode(),
                "Pre-bootstrap-comparator recorder version 9.1-s2 must yield false");
    }

    @Test
    void minimumEligibleVersionReturnsTrue() throws IOException {
        Path dir = Files.createTempDirectory("trace-meta-v9-2");
        Files.writeString(dir.resolve("metadata.json"),
                "{\n" + baseMetadata("9.2-s2") + "}\n");

        TraceMetadata meta = TraceMetadata.load(dir.resolve("metadata.json"));

        assertTrue(meta.nativePreludeMode(),
                "Recorder version 9.2-s2 (bootstrap-comparator minimum) must yield true");
    }

    @Test
    void laterMinorVersionReturnsTrue() throws IOException {
        Path dir = Files.createTempDirectory("trace-meta-v9-10");
        Files.writeString(dir.resolve("metadata.json"),
                "{\n" + baseMetadata("9.10-s2") + "}\n");

        TraceMetadata meta = TraceMetadata.load(dir.resolve("metadata.json"));

        assertTrue(meta.nativePreludeMode(),
                "Recorder version 9.10-s2 must yield true (numeric, not lexical, compare)");
    }

    @Test
    void laterMajorVersionReturnsTrue() throws IOException {
        Path dir = Files.createTempDirectory("trace-meta-v10");
        Files.writeString(dir.resolve("metadata.json"),
                "{\n" + baseMetadata("10.0-s2") + "}\n");

        TraceMetadata meta = TraceMetadata.load(dir.resolve("metadata.json"));

        assertTrue(meta.nativePreludeMode(),
                "Recorder version 10.0-s2 (later major) must yield true");
    }

    @Test
    void malformedVersionReturnsFalseWithoutException() throws IOException {
        Path dir = Files.createTempDirectory("trace-meta-malformed-version");
        Files.writeString(dir.resolve("metadata.json"),
                "{\n" + baseMetadata("alpha-test") + "}\n");

        Path metadataFile = dir.resolve("metadata.json");
        TraceMetadata meta = assertDoesNotThrow(() -> TraceMetadata.load(metadataFile),
                "Malformed lua_script_version must not throw on load");

        assertFalse(meta.nativePreludeMode(),
                "Malformed (non-numeric) lua_script_version must yield false");
    }

    @Test
    void s1RecorderVersionPrefixReturnsFalse() throws IOException {
        Path dir = Files.createTempDirectory("trace-meta-s1");
        // S1 recorder is on its own version line (no -s2 suffix);
        // bootstrap eligibility only applies to S2 traces for now.
        // But the version-parse logic still resolves major/minor numerically,
        // so an S1 version like "1.0-s1" is well below 9.2 and yields false.
        Files.writeString(dir.resolve("metadata.json"),
                "{\n" + baseMetadata("1.0-s1") + "}\n");

        TraceMetadata meta = TraceMetadata.load(dir.resolve("metadata.json"));

        assertFalse(meta.nativePreludeMode(),
                "S1 recorder versions below 9.2 yield false");
    }
}
