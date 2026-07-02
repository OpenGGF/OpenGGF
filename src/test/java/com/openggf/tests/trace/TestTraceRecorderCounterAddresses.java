package com.openggf.tests.trace;

import com.openggf.trace.*;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceRecorderCounterAddresses {

    private static final Path TOOLS_DIR = Path.of("tools", "bizhawk");

    @Test
    void sonic1RecorderUsesDisassemblyBackedExecutionCounters() throws IOException {
        String script = Files.readString(TOOLS_DIR.resolve("s1_trace_recorder.lua"));

        assertTrue(script.contains("local ADDR_FRAMECOUNT      = 0xFE04"));
        assertTrue(script.contains("local ADDR_VBLA_WORD       = 0xFE0E"));
    }

    @Test
    void sonic2RecorderUsesDisassemblyBackedExecutionCounters() throws IOException {
        String script = Files.readString(TOOLS_DIR.resolve("s2_trace_recorder.lua"));

        assertTrue(script.contains("local ADDR_FRAMECOUNT      = 0xFE04"));
        // Vint_runcount is a LONGWORD at $FFFE0C (s2.constants.asm:1672); the
        // recorder reads the low word at +2 so the CSV column actually changes.
        assertTrue(script.contains("local ADDR_VBLA_WORD       = 0xFE0E"));
    }

    @Test
    void sonic3kRecorderUsesDisassemblyBackedExecutionCounters() throws IOException {
        String script = Files.readString(TOOLS_DIR.resolve("s3k_trace_recorder.lua"));

        assertTrue(script.contains("local ADDR_FRAMECOUNT       = 0xFE08"));
        assertTrue(script.contains("local ADDR_VBLA_WORD        = 0xFE12"));
        assertTrue(script.contains("local ADDR_LAG_FRAME_COUNT  = 0xF628"));
    }
}
