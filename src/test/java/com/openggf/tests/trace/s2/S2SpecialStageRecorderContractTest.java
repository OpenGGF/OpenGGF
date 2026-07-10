package com.openggf.tests.trace.s2;

import com.openggf.trace.SpecialStageTraceData;
import com.openggf.trace.TraceEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S2SpecialStageRecorderContractTest {

    private static final Path LUA = Path.of("tools", "bizhawk", "s2_ss_trace_recorder.lua");
    private static final Path WORKFLOW =
            Path.of("tools", "bizhawk", "record_s2_level_select_traces.ps1");

    @Test
    void recorderDeclaresBoundedRev01RunObjectsEndAndControlHooks() throws Exception {
        String lua = Files.readString(LUA);

        assertTrue(lua.contains("local LUA_SCRIPT_VERSION = \"1.1-s2ss\""));
        assertTrue(lua.contains("local PC_RUN_OBJECTS_END = 0x15FE4"));
        assertTrue(lua.contains("event.onmemoryexecute"));
        assertTrue(lua.contains("s2ss_run_objects_end"));
        assertTrue(lua.contains("event.unregisterbyname"));
        assertTrue(lua.contains("\"type\":\"run_objects_end\""));
        assertTrue(lua.contains("\"type\":\"control_state\""));
        assertTrue(lua.contains("ADDR_SPECIAL_STAGE_STARTED"));
        assertTrue(lua.contains("last_nonlag_trace_frame"));
        assertTrue(lua.contains("mainmemory.read_u8(ADDR_SPECIAL_STAGE_STARTED) == 0"));
        assertTrue(lua.contains("OGGF_TRACE_OUTPUT_DIR"));
        assertTrue(Files.readString(WORKFLOW).contains("OGGF_TRACE_OUTPUT_DIR"));
    }

    @Test
    void workflowValidatesRequiredSpecialStageAuxFamilies() throws Exception {
        String ps1 = Files.readString(WORKFLOW);

        assertTrue(ps1.contains("control_state"));
        assertTrue(ps1.contains("run_objects_end"));
        assertTrue(ps1.contains("stage_finished"));
        assertTrue(ps1.contains("checkpoint"));
        assertTrue(ps1.contains("message_state"));
    }

    @Test
    void workflowBuildsGitignoredScratchPathFromExistingParent() throws Exception {
        String ps1 = Files.readString(WORKFLOW);

        assertTrue(ps1.contains("$bizhawkToolsDir = Resolve-RepoPath \"tools/bizhawk\""));
        assertTrue(ps1.contains("$traceOutput = [System.IO.Path]::GetFullPath("));
        assertTrue(ps1.contains("Join-Path $bizhawkToolsDir \"trace_output\""));
        assertFalse(ps1.contains(
                "$traceOutput = Resolve-RepoPath \"tools/bizhawk/trace_output\""));
    }

    @Test
    void committedArtifactHasControlTransitionsAndLogicalPassEndCoverage() throws Exception {
        SpecialStageTraceData trace = SpecialStageTraceData.load(
                AbstractS2SpecialStageTraceReplayTest.TRACE_DIRECTORY);

        assertEquals("1.1-s2ss", trace.metadata().luaScriptVersion());
        assertFalse(trace.controlStateTransitions().isEmpty());
        assertEquals(0, trace.controlStateTransitions().get(0).frame());
        assertFalse(trace.controlStateTransitions().get(0).started());
        assertTrue(trace.controlStateTransitions().stream().anyMatch(
                SpecialStageTraceData.ControlStateTransition::started));

        int runObjectsEndCount = 0;
        java.util.Set<Integer> runObjectsEndFrames = new java.util.HashSet<>();
        boolean stageFinished = false;
        boolean checkpoint = false;
        boolean messageState = false;
        for (int frame = 0; frame < trace.frameCount(); frame++) {
            List<TraceEvent> events = trace.getEventsForFrame(frame);
            for (TraceEvent event : events) {
                if (!(event instanceof TraceEvent.StateSnapshot snapshot)) {
                    continue;
                }
                Object type = snapshot.fields().get("type");
                if ("run_objects_end".equals(type)) {
                    runObjectsEndCount++;
                    assertTrue(runObjectsEndFrames.add(frame),
                            "duplicate pass-end snapshot for logical frame " + frame);
                    assertFalse(trace.getFrame(frame).lag(),
                            "pass-end snapshot must be keyed to a non-lag logical frame");
                    for (String required : List.of(
                            "speed_factor", "track_anim", "track_anim_frame",
                            "track_drawing_index", "track_orientation",
                            "track_duration_timer", "current_segment",
                            "player_anim_frame_timer", "rings_togo_bcd",
                            "check_rings_flag", "tails_control_counter",
                            "swap_positions_flag", "sonic_present", "sonic_ss_x",
                            "sonic_ss_x_sub", "sonic_ss_y", "sonic_ss_y_sub",
                            "sonic_ss_z", "sonic_angle", "sonic_routine",
                            "sonic_routine_secondary", "sonic_status", "sonic_anim",
                            "sonic_anim_frame", "sonic_rings_bcd", "sonic_hurt_timer",
                            "sonic_slide_timer", "sonic_flip_timer", "tails_present",
                            "tails_ss_x", "tails_ss_x_sub", "tails_ss_y", "tails_ss_y_sub",
                            "tails_ss_z", "tails_angle", "tails_routine",
                            "tails_routine_secondary", "tails_status", "tails_anim",
                            "tails_anim_frame", "tails_rings_bcd", "tails_hurt_timer",
                            "tails_slide_timer", "tails_flip_timer")) {
                        assertTrue(snapshot.fields().containsKey(required),
                                "run_objects_end frame " + frame + " missing " + required);
                    }
                } else if ("stage_finished".equals(type)) {
                    stageFinished = true;
                } else if ("checkpoint".equals(type)) {
                    checkpoint = true;
                } else if ("message_state".equals(type)) {
                    messageState = true;
                }
            }
        }
        assertTrue(runObjectsEndCount > 1000, "expected broad recurring RunObjects coverage");
        assertTrue(stageFinished);
        assertTrue(checkpoint);
        assertTrue(messageState);
    }
}
