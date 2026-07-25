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

        // Level_frame_counter, not the dead-zero Debug_placement_mode at
        // 0xFE08 the recorder read until Lua v6.31-s3k (commit 6564667eb).
        assertTrue(script.contains("local ADDR_FRAMECOUNT       = 0xFE04"));
        // Low word of the ds.l V_int_run_count at 0xFE0C -- the same address
        // the S1/S2 recorders above read -- not Life_count at 0xFE12, which
        // made the vblank_counter column carry lives << 8 until Lua
        // v6.32-s3k (commit f71b5ea44).
        assertTrue(script.contains("local ADDR_VBLA_WORD        = 0xFE0E"));
        assertTrue(script.contains("local ADDR_LAG_FRAME_COUNT  = 0xF628"));
    }

    /**
     * The complete-run recorder carries its OWN copy of these constants.
     * That duplication is what let ADDR_VBLA_WORD stay wrong in both S3K
     * recorders at once: because they agreed, pair-diffing could not surface
     * it. Pinning both halves here means a future edit to one recorder alone
     * fails rather than silently re-opening the divergence.
     */
    @Test
    void sonic3kCompleteRunRecorderUsesTheSameExecutionCounters() throws IOException {
        String script = Files.readString(
                TOOLS_DIR.resolve("s3k_complete_run_recorder.lua"));

        assertTrue(script.contains("local ADDR_FRAMECOUNT       = 0xFE04"));
        assertTrue(script.contains("local ADDR_VBLA_WORD        = 0xFE0E"));
        assertTrue(script.contains("local ADDR_LAG_FRAME_COUNT  = 0xF628"));
    }
}
