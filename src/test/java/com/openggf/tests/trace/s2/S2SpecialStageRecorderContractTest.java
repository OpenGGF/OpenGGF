package com.openggf.tests.trace.s2;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.SpecialStageInputMapper;
import com.openggf.trace.SpecialStageRunObjectsPassBinder;
import com.openggf.trace.SpecialStageRunObjectsPassBinder.CompletedPass;
import com.openggf.trace.SpecialStageTraceData;
import com.openggf.trace.TraceEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S2SpecialStageRecorderContractTest {

    private static final Path LUA = Path.of("tools", "bizhawk", "s2_ss_trace_recorder.lua");
    private static final Path WORKFLOW =
            Path.of("tools", "bizhawk", "record_s2_level_select_traces.ps1");

    @Test
    void recorderDeclaresBoundedRev01RecurringPassAndControlHooks() throws Exception {
        String lua = Files.readString(LUA);

        // No assertion on the script's declared version string: recorder identity is
        // opaque provenance (hard rule 4 -- `recorder`/`recorder_version` select no
        // replay behaviour, and `lua_script_version` was removed outright). Pinning a
        // version literal gates nothing and re-arms staleness at the next bump; the
        // hook addresses and emitted field names below are the contract that matters.
        assertTrue(lua.contains("local PC_READ_JOYPADS_RETURN = 0x1156"));
        assertTrue(lua.contains("local VINT_S2SS_READ_JOYPADS_RETURN_PC = 0x88E"));
        assertTrue(lua.contains("local CTRL_2_READ_COMPLETE_A0 = 0xF608"));
        assertTrue(lua.contains("local PC_S2SS_POST_RUN_OBJECTS = 0x52B2"));
        assertTrue(lua.contains("event.onmemoryexecute"));
        assertTrue(lua.contains("s2ss_recurring_post_run_objects"));
        assertTrue(lua.contains("s2ss_input_sample"));
        assertFalse(lua.contains("PC_RUN_OBJECTS_END"),
                "Obj59 success bypasses the generic RunObjects_End RTS");
        assertEquals(2, lua.split("event\\.onmemoryexecute\\(", -1).length - 1,
                "BizHawk supports only two simultaneous execute callbacks");
        assertTrue(lua.contains("event.unregisterbyname"));
        assertTrue(lua.contains("\"type\":\"run_objects_end\""));
        assertTrue(lua.contains("\"type\":\"control_state\""));
        assertTrue(lua.contains("ADDR_SPECIAL_STAGE_STARTED"));
        assertTrue(lua.contains("first_eligible_frame"));
        assertTrue(lua.contains("pass_sequence"));
        assertTrue(lua.contains("completion_cursor_frame"));
        assertTrue(lua.contains("input_sample_frame"));
        assertTrue(lua.contains("input_sample_bk2_frame"));
        assertTrue(lua.contains("previous_input_sample_frame"));
        assertTrue(lua.contains("previous_input_sample_bk2_frame"));
        assertTrue(lua.contains("input_sample_sequence"));
        assertTrue(lua.contains("started_at_input_sample"));
        assertTrue(lua.contains("latest_input_sample.started_at_input_sample == 0"));
        assertTrue(lua.contains("vint_s2ss_read_joypads"));
        assertTrue(lua.contains("p1_held"));
        assertTrue(lua.contains("previous_p1_held"));
        assertTrue(lua.contains("mainmemory.read_u8(ADDR_SPECIAL_STAGE_STARTED) == 0"));
        assertTrue(lua.contains("prev_check_rings_flag == 0 and check_rings_flag ~= 0"));
        assertTrue(lua.contains("last_nonlag_trace_frame"));
        assertTrue(lua.contains("publish_pending_finish_pass"));
        assertTrue(lua.contains("\"observed_frame\":%d,\"type\":\"stage_finished\""));
        assertTrue(lua.contains("\"type\":\"results_started\""));
        assertFalse(lua.contains("\"type\":\"stage_finished\",\"slot\""),
                "Obj6F sighting is results start, not the canonical finish boundary");
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
        assertTrue(ps1.contains("$multiPassObservationFrames"));
        assertTrue(ps1.contains("$delayedRunObjectsPassCount"));
        assertTrue(ps1.contains("vint_s2ss_read_joypads"));
        assertTrue(ps1.contains("$previousP1Held"));
        assertTrue(ps1.contains("$terminalFinishPassCount"));
        assertTrue(ps1.contains("$stageFinishedFrame"));
        assertTrue(ps1.contains("$stageFinishedEvents.Count -ne 1"));
        assertTrue(ps1.contains("$resultsStartedEvent"));
        assertTrue(ps1.contains("$firstEligibleAtOrAfterCompletion"));
        assertTrue(ps1.contains("$previousInputBk2Index"));
        assertTrue(ps1.contains("-le 2900"));
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
    void passReplayDerivesInputFromBk2IdentityAndIgnoresAuxHeldDiagnostics() {
        Bk2Movie movie = new Bk2Movie(Path.of("synthetic.bk2"), "", Map.of(), List.of(
                new Bk2FrameInput(0, 0, 0, false, 0, 0, false, "neutral"),
                new Bk2FrameInput(1, 0x08, 0, false, 0x04, 0, false, "right/left")), 0);
        CompletedPass pass = new CompletedPass(
                new TraceEvent.StateSnapshot(1, Map.of()),
                0, 1, 1, 1, 1, 0, 0, 0,
                0xFF, 0xFF, 0xFF, 0xFF);

        SpecialStageInputMapper.MappedInput mapped =
                S2SpecialStageReplayHarness.mappedInputForPass(movie, pass);

        assertEquals(0x08, mapped.p1Held());
        assertEquals(0x08, mapped.p1Pressed());
        assertEquals(0x04, mapped.p2Held());
        assertEquals(0x04, mapped.p2Logical());
    }

    @Test
    void committedArtifactHasControlTransitionsAndLogicalPassEndCoverage() throws Exception {
        SpecialStageTraceData trace = SpecialStageTraceData.load(
                AbstractS2SpecialStageTraceReplayTest.TRACE_DIRECTORY);
        Bk2Movie movie = new Bk2MovieLoader().load(
                AbstractS2SpecialStageTraceReplayTest.TRACE_DIRECTORY.resolve(
                        trace.metadata().sourceBk2()));

        int finishObservation = trace.stageFinishedObservedFrame().orElseThrow();
        new SpecialStageRunObjectsPassBinder(
                trace.runObjectsEndSnapshots(), trace.frameCount(),
                frame -> !trace.getFrame(frame).lag() || frame == finishObservation,
                trace.metadata().bk2FrameOffset(), movie.getFrames());

        // Deliberately no assertion on trace.metadata().recorder()/recorderVersion():
        // per hard rule 4 those fields are opaque provenance and no provenance field
        // selects replay behaviour, so pinning one gates nothing while guaranteeing a
        // red test the next time the recorder is versioned. Assert emitted content.
        assertFalse(trace.controlStateTransitions().isEmpty());
        assertEquals(0, trace.controlStateTransitions().get(0).frame());
        assertFalse(trace.controlStateTransitions().get(0).started());
        assertTrue(trace.controlStateTransitions().stream().anyMatch(
                SpecialStageTraceData.ControlStateTransition::started));

        int runObjectsEndCount = 0;
        int expectedPassSequence = 0;
        int delayedRunObjectsPassCount = 0;
        Map<Integer, Integer> runObjectsPassesByFrame = new HashMap<>();
        List<TraceEvent.StateSnapshot> stageFinished = new ArrayList<>();
        List<TraceEvent.StateSnapshot> checkpoints = new ArrayList<>();
        List<TraceEvent.StateSnapshot> resultsStarted = new ArrayList<>();
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
                    boolean finishPass = frame == finishObservation;
                    assertTrue(!trace.getFrame(frame).lag() || finishPass,
                            "only the terminal pass may bind to its lag finish observation");
                    assertEquals(expectedPassSequence++,
                            ((Number) snapshot.fields().get("pass_sequence")).intValue(),
                            "pass sequence must be contiguous in execution order");
                    assertTrue(((Number) snapshot.fields().get("first_eligible_frame")).intValue()
                                    <= frame,
                            "pass may only bind at or after its first eligible trace row");
                    if (((Number) snapshot.fields().get("completion_cursor_frame")).intValue()
                            < frame) {
                        delayedRunObjectsPassCount++;
                    }
                    int completion = ((Number) snapshot.fields()
                            .get("completion_cursor_frame")).intValue();
                    int firstEligible = completion;
                    while (firstEligible < trace.frameCount()
                            && trace.getFrame(firstEligible).lag()
                            && firstEligible != finishObservation) {
                        firstEligible++;
                    }
                    assertEquals(firstEligible, frame,
                            "pass must bind to the first eligible observation at/after completion");
                    runObjectsPassesByFrame.merge(frame, 1, Integer::sum);
                    for (String required : List.of(
                            "pass_sequence", "first_eligible_frame",
                            "completion_cursor_frame", "input_sample_frame",
                            "input_sample_bk2_frame", "input_sample_sequence",
                            "previous_input_sample_frame",
                            "previous_input_sample_bk2_frame",
                            "input_source", "started_at_input_sample", "p1_held", "p2_held",
                            "previous_p1_held", "previous_p2_held",
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
                    stageFinished.add(snapshot);
                } else if ("checkpoint".equals(type)) {
                    checkpoints.add(snapshot);
                } else if ("results_started".equals(type)) {
                    resultsStarted.add(snapshot);
                } else if ("message_state".equals(type)) {
                    messageState = true;
                }
            }
        }
        long multiPassObservationFrames = runObjectsPassesByFrame.values().stream()
                .filter(count -> count > 1)
                .count();
        // Stated relatively: the per-frame scan must see exactly the passes the
        // accessor exposes. An absolute total would only restate the recorder's
        // coverage, which the pre-start SpecialStage_MainLoop hook
        // (docs/s2disasm/s2.asm:6674-6692) shifted by that half's pass count.
        assertEquals(trace.runObjectsEndSnapshots().size(), runObjectsEndCount);
        // These two characterise the committed fixture: RunObjects can complete more
        // than once between two eligible observations, and a completion can be
        // published later than the row it completed on. Both must stay non-zero or
        // the binder's multi-pass/delay handling is untested; the exact totals move
        // with any regeneration of traces/s2/special_stage.
        assertTrue(multiPassObservationFrames > 0);
        assertTrue(delayedRunObjectsPassCount > 0);
        assertEquals(1, runObjectsPassesByFrame.getOrDefault(finishObservation, 0),
                "raw finish observation must own exactly one terminal pass");
        assertEquals(1, stageFinished.size());
        assertEquals(1, checkpoints.size());
        assertEquals(1, resultsStarted.size());
        TraceEvent.StateSnapshot finish = stageFinished.getFirst();
        TraceEvent.StateSnapshot checkpoint = checkpoints.getFirst();
        assertEquals(checkpoint.frame(),
                ((Number) finish.fields().get("observed_frame")).intValue(),
                "finish marker must retain the raw checkpoint observation");
        assertFalse(trace.getFrame(finish.frame()).lag(),
                "finish marker must be owned by the last logical observation");
        assertEquals(finish.frame(), trace.stageFinishedFrame().orElseThrow());
        assertTrue(trace.getFrame(finishObservation).lag(),
                "the committed finish flag first appears on a lag-labelled raw observation");
        assertEquals(0xff, trace.getFrame(finishObservation).checkRingsFlag());
        TraceEvent.StateSnapshot terminalPass = trace.runObjectsEndSnapshots().stream()
                .max(java.util.Comparator.comparingInt(pass ->
                        ((Number) pass.fields().get("pass_sequence")).intValue()))
                .orElseThrow();
        TraceEvent.StateSnapshot precedingPass = trace.runObjectsEndSnapshots().stream()
                .filter(pass -> ((Number) pass.fields().get("pass_sequence")).intValue()
                        == ((Number) terminalPass.fields().get("pass_sequence")).intValue() - 1)
                .findFirst().orElseThrow();
        assertEquals(finishObservation, terminalPass.frame());
        // The terminal pass is the last one recorded. Stated relatively: the
        // recorder now also covers the pre-start half of SpecialStage_MainLoop
        // (docs/s2disasm/s2.asm:6674-6692), which shifts every absolute
        // sequence number by that half's pass count.
        assertEquals(trace.runObjectsEndSnapshots().size() - 1,
                ((Number) terminalPass.fields().get("pass_sequence")).intValue());
        assertEquals(0xff, ((Number) terminalPass.fields().get("check_rings_flag")).intValue());
        assertEquals(finishObservation,
                ((Number) terminalPass.fields().get("completion_cursor_frame")).intValue());
        assertEquals(((Number) precedingPass.fields().get("input_sample_frame")).intValue(),
                ((Number) terminalPass.fields().get("previous_input_sample_frame")).intValue(),
                "terminal pass must preserve the exact ReadJoypads identity chain");
        assertTrue(((Number) terminalPass.fields().get("input_sample_frame")).intValue()
                        > ((Number) precedingPass.fields().get("input_sample_frame")).intValue(),
                "terminal pass owns a later executed VInt sample");
        assertTrue(resultsStarted.getFirst().frame() > finish.frame());
        assertTrue(trace.frameCount() > resultsStarted.getFirst().frame(),
                "recording must retain the uncompared results tail");
        assertTrue(messageState);
    }

    @Test
    void f915BindsOnlyTheCompletedX58PassAndLagRowF916AddsNoPass() throws Exception {
        SpecialStageTraceData trace = SpecialStageTraceData.load(
                AbstractS2SpecialStageTraceReplayTest.TRACE_DIRECTORY);
        Map<Integer, List<TraceEvent.StateSnapshot>> byFrame = new HashMap<>();
        for (TraceEvent.StateSnapshot pass : trace.runObjectsEndSnapshots()) {
            byFrame.computeIfAbsent(pass.frame(), ignored -> new java.util.ArrayList<>()).add(pass);
        }

        // Frame labels moved by one when e2aa50cd5 corrected a recorder off-by-one
        // and regenerated traces/s2/special_stage: the sampled observation is the
        // non-lag row 915 and the lag row that follows it is 916.
        int observation = 915;
        int followingLagRow = observation + 1;
        assertFalse(trace.getFrame(observation).lag(),
                "the sampled observation must be a logical (non-lag) row");
        assertTrue(trace.getFrame(followingLagRow).lag(),
                "the row under test must be the lag row after that observation");

        List<TraceEvent.StateSnapshot> f915 = byFrame.getOrDefault(observation, List.of());
        assertEquals(1, f915.size());
        // Contiguous with the last pass published before this observation --
        // an absolute sequence number would only restate the recorder's total
        // coverage, which the pre-start hook changed.
        int previousSequence = trace.runObjectsEndSnapshots().stream()
                .filter(pass -> pass.frame() < observation)
                .mapToInt(pass -> ((Number) pass.fields().get("pass_sequence")).intValue())
                .max()
                .orElseThrow();
        assertEquals(previousSequence + 1,
                ((Number) f915.getFirst().fields().get("pass_sequence")).intValue());
        assertEquals(58, ((Number) f915.getFirst().fields().get("sonic_ss_x")).intValue());
        assertTrue(byFrame.getOrDefault(followingLagRow, List.of()).isEmpty(),
                "the following lag row observes no newly completed RunObjects pass");
    }
}
