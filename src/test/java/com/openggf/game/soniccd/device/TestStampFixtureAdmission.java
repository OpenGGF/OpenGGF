package com.openggf.game.soniccd.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic metadata only; negative admission checks run without the external commercial payloads. */
class TestStampFixtureAdmission {
    @Test
    void admitsAnExplicitPartialCursorWithoutConstructingAPretendSubmission() throws IOException {
        var state = StampFixtureAdmission.admit(descriptor());
        assertEquals(24, state.completedLines());
        assertEquals(72, state.remainingLines());
        assertEquals(116928, state.traceByteCursor());
        assertEquals(237760, state.imageNibbleCursor());
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            ingress_devices,regs.02,10758
            egress_devices,regs.02,10757
            ingress_devices,regs.02,10762
            egress_devices,regs.02,10761
            ingress_devices,regs.02,10753
            egress_devices,regs.02,10754
            ingress_devices,dmna,0
            egress_devices,dmna,1
            ingress_devices,regs.58,32771
            egress_devices,regs.58,3
            egress_devices,regs.58,32775
            ingress_devices,regs.64,71
            egress_devices,regs.64,1
            egress_devices,regs.5a,32776
            egress_devices,regs.5c,12
            egress_devices,regs.5e,29697
            egress_devices,regs.60,1
            egress_devices,regs.62,255
            egress_devices,regs.66,29186
            ingress_devices,gfx_hw.tracePtr,116920
            egress_devices,gfx_hw.tracePtr,117496
            ingress_devices,gfx_hw.bufferStart,237752
            egress_devices,gfx_hw.bufferStart,238328
            ingress_devices,gfx_hw.dotMask,524287
            ingress_devices,gfx_hw.stampShift,15
            ingress_devices,gfx_hw.mapShift,8
            ingress_devices,gfx_hw.bufferOffset,697
            ingress_devices,gfx_hw.mapPtr,0
            egress_devices,gfx_hw.dotMask,524287
            egress_devices,gfx_hw.stampShift,15
            egress_devices,gfx_hw.mapShift,8
            egress_devices,gfx_hw.bufferOffset,697
            egress_devices,gfx_hw.mapPtr,0
            request,wordram_mode_register,10758
            request,stamp_size_register,3
            request,width,255
            request,map_offset,0
            request,image_start_register,29697
            request,trace_base,116744
            request,cursor,116920
            request,completed_lines,23
            request,remaining_lines,71
            request,reconstructed_total_lines,95
            """)
    void rejectsChangedModesOwnershipPriorityCursorsAndDescriptorMismatches(String object, String field,
                                                                           int value) throws IOException {
        ObjectNode fixture = descriptor();
        ((ObjectNode) fixture.path(object)).put(field, value);
        assertThrows(IllegalArgumentException.class, () -> StampFixtureAdmission.admit(fixture));
    }

    @ParameterizedTest
    @CsvSource({"wordram_bytes,262143", "image_base,118785", "image_bytes,12287",
            "callback_address,10559490", "callback_value,2", "callback_flags,8192"})
    void rejectsIncorrectBoundaryAndExtentFacts(String field, int value) throws IOException {
        ObjectNode fixture = descriptor();
        fixture.put(field, value);
        assertThrows(IllegalArgumentException.class, () -> StampFixtureAdmission.admit(fixture));
    }

    @Test
    void rejectsConsistentlyRelabeled1MAndPriorityInputsRatherThanOnlyTheirMismatch() throws IOException {
        for (int mode : new int[]{10758, 10762}) {
            ObjectNode fixture = descriptor();
            ((ObjectNode) fixture.path("request")).put("wordram_mode_register", mode);
            ((ObjectNode) fixture.path("ingress_devices")).put("regs.02", mode);
            ((ObjectNode) fixture.path("egress_devices")).put("regs.02", mode ^ 3);
            assertThrows(IllegalArgumentException.class, () -> StampFixtureAdmission.admit(fixture));
        }
    }

    @Test
    void rejectsMissingNonnumericAndFullSubmissionClaims() throws IOException {
        ObjectNode missing = descriptor();
        ((ObjectNode) missing.path("ingress_devices")).remove("gfx_hw.tracePtr");
        assertThrows(IllegalArgumentException.class, () -> StampFixtureAdmission.admit(missing));
        ObjectNode nonnumeric = descriptor();
        ((ObjectNode) nonnumeric.path("request")).put("completed_lines", "24");
        assertThrows(IllegalArgumentException.class, () -> StampFixtureAdmission.admit(nonnumeric));
        ObjectNode full = descriptor();
        full.put("observation_kind", "full-submission");
        assertThrows(IllegalArgumentException.class, () -> StampFixtureAdmission.admit(full));
        ObjectNode wrongEncoding = descriptor();
        wrongEncoding.put("wordram_encoding", "native-little-endian-word-storage");
        assertThrows(IllegalArgumentException.class, () -> StampFixtureAdmission.admit(wrongEncoding));
    }

    private static ObjectNode descriptor() throws IOException {
        return (ObjectNode) new ObjectMapper().readTree("""
                {
                  "observation_kind":"partial-device-continuation",
                  "wordram_encoding":"68000-address-order-big-endian", "wordram_bytes":262144,
                  "image_base":118784, "image_bytes":12288,
                  "ingress_boundary":"after FrameAdvance and GetSamplesSync, partial progress",
                  "egress_boundary":"M68K BUS memory-mode read callback, main owner and graphics idle, before image read",
                  "callback_address":10559491, "callback_value":1, "callback_flags":4096,
                  "request":{
                    "trace_base":116736, "cursor":116928, "completed_lines":24, "remaining_lines":72,
                    "reconstructed_total_lines":96, "width":256, "image_start_register":29696,
                    "stamp_size_register":7, "wordram_mode_register":10754, "map_offset":131072
                  },
                  "ingress_devices":{
                    "dmna":1, "regs.02":10754, "regs.58":32775, "regs.5a":32768,
                    "regs.5c":11, "regs.5e":29696, "regs.60":0, "regs.62":256,
                    "regs.64":72, "regs.66":29184,
                    "gfx_hw.tracePtr":116928, "gfx_hw.bufferStart":237760, "gfx_hw.bufferOffset":761,
                    "gfx_hw.dotMask":8388607, "gfx_hw.mapPtr":131072, "gfx_hw.mapShift":7, "gfx_hw.stampShift":16
                  },
                  "egress_devices":{
                    "dmna":0, "regs.02":10753, "regs.58":7, "regs.5a":32768,
                    "regs.5c":11, "regs.5e":29696, "regs.60":0, "regs.62":256,
                    "regs.64":0, "regs.66":29184,
                    "gfx_hw.tracePtr":117504, "gfx_hw.bufferStart":238336, "gfx_hw.bufferOffset":761,
                    "gfx_hw.dotMask":8388607, "gfx_hw.mapPtr":131072, "gfx_hw.mapShift":7, "gfx_hw.stampShift":16
                  }
                }
                """);
    }
}
